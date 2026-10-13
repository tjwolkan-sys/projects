"""
anomaly_detector.py
───────────────────
Adım 1 — Anormali Tespiti ve Kategorizasyon Motoru

Akış:
  1. Rolling z-score ile her metrikte sapma hesapla
  2. Rate-of-change (delta) ile ani değişimleri yakala
  3. Multi-metric korelasyon — birden fazla sinyal aynı anda mı?
  4. Her anomaliye kategori + severity ata
  5. Precursor (önceki N event) ve Recovery (sonraki N event) penceresini çıkar
  6. Kategorize edilmiş anomali listesini döndür
"""

import pandas as pd
import numpy as np
from dataclasses import dataclass, field
from typing import Optional


# ─────────────────────────────────────────────────────────────
# Sabitler
# ─────────────────────────────────────────────────────────────

ROLLING_WINDOW     = 20    # z-score için kayan pencere
ZSCORE_THRESHOLD   = 2.5   # kaç sigma → anormal
DELTA_WINDOW       = 3     # rate-of-change için kaç event
CONTEXT_WINDOW     = 10    # precursor / recovery için kaç event

# Kategori tanımları — her kategorinin tetikleyici koşulları
CATEGORIES = {
    "ABANDON_SPIKE": {
        "label":       "Abandon Spike",
        "description": "Abandon rate ani olarak yükseldi",
        "color":       "#FF4444",
        "icon":        "🔴",
    },
    "ANSWER_RATE_DROP": {
        "label":       "Answer Rate Drop",
        "description": "Answer rate ani olarak düştü",
        "color":       "#FF8800",
        "icon":        "🟠",
    },
    "BOOST_INSTABILITY": {
        "label":       "Boost Rate Instability",
        "description": "Boost rate anormal yükseldi — sarmal riski",
        "color":       "#FFCC00",
        "icon":        "🟡",
    },
    "XN_SUPPRESSION": {
        "label":       "Xn Suppression",
        "description": "rawXn < ringingCount — Xn sıfırlandı",
        "color":       "#AA00FF",
        "icon":        "🟣",
    },
    "AGENT_SHORTAGE": {
        "label":       "Agent Shortage",
        "description": "Available agent sayısı kritik seviyeye düştü",
        "color":       "#0088FF",
        "icon":        "🔵",
    },
    "HIGH_UTILIZATION": {
        "label":       "High Agent Utilization",
        "description": "Agent utilization %80 üzerinde",
        "color":       "#00AAAA",
        "icon":        "🩵",
    },
    "TALK_TIME_SPIKE": {
        "label":       "Avg Talk Time Spike",
        "description": "Ortalama konuşma süresi anormal arttı",
        "color":       "#FF66AA",
        "icon":        "🩷",
    },
    "MULTI_METRIC":  {
        "label":       "Multi-Metric Anomaly",
        "description": "3+ metrik aynı anda anormal — kritik",
        "color":       "#CC0000",
        "icon":        "🚨",
    },
}

SEVERITY_LEVELS = {
    1: "LOW",
    2: "MEDIUM",
    3: "HIGH",
    4: "CRITICAL",
}


# ─────────────────────────────────────────────────────────────
# Veri sınıfı
# ─────────────────────────────────────────────────────────────

@dataclass
class AnomalyEvent:
    """Tek bir anomali event'ini temsil eder."""
    index:          int
    category:       str
    severity:       int                    # 1-4
    severity_label: str
    timestamp:      Optional[str]
    campaign_name:  Optional[str]

    # Tetikleyici metrikler
    triggered_by:   dict = field(default_factory=dict)

    # Bağlam pencereleri
    precursor_df:   Optional[pd.DataFrame] = None
    recovery_df:    Optional[pd.DataFrame] = None

    # Özet istatistikler
    precursor_stats: dict = field(default_factory=dict)
    recovery_stats:  dict = field(default_factory=dict)


@dataclass
class CategorySummary:
    """Bir kategorideki tüm anomalilerin özeti."""
    category:    str
    label:       str
    description: str
    color:       str
    icon:        str
    count:       int
    severity_distribution: dict = field(default_factory=dict)  # {CRITICAL:2, HIGH:5, ...}
    events:      list = field(default_factory=list)             # List[AnomalyEvent]


# ─────────────────────────────────────────────────────────────
# Yardımcı: rolling z-score
# ─────────────────────────────────────────────────────────────

def _rolling_zscore(series: pd.Series, window: int = ROLLING_WINDOW) -> pd.Series:
    """Her nokta için kendi geçmişine göre z-score hesapla."""
    roll_mean = series.rolling(window, min_periods=3).mean()
    roll_std  = series.rolling(window, min_periods=3).std()
    z = (series - roll_mean) / roll_std.replace(0, np.nan)
    return z.fillna(0)


def _delta(series: pd.Series, window: int = DELTA_WINDOW) -> pd.Series:
    """N event öncesiyle fark (rate of change)."""
    return series.diff(window).fillna(0)


# ─────────────────────────────────────────────────────────────
# Adım 1 — Feature Engineering
# ─────────────────────────────────────────────────────────────

def _engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    Her satır için z-score, delta ve türetilmiş kolonlar ekle.
    Orijinal veri değişmez.
    """
    df = df.copy()

    numeric_cols = [
        "computed_abandon_rate",
        "computed_answer_rate",
        "computed_boost_rate",
        "xn",
        "avg_talk_time",
        "avg_ring_time",
        "available_agents_count",
        "incall_agents_count",
        "total_agents_count",
    ]

    for col in numeric_cols:
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce").fillna(0)
            df[f"_z_{col}"]     = _rolling_zscore(df[col])
            df[f"_delta_{col}"] = _delta(df[col])

    # Xn suppression flag
    if "raw_xn" in df.columns and "ringing_count" in df.columns:
        df["raw_xn"]       = pd.to_numeric(df["raw_xn"],       errors="coerce").fillna(0)
        df["ringing_count"]= pd.to_numeric(df["ringing_count"], errors="coerce").fillna(0)
        df["_xn_suppressed"] = df["raw_xn"] < df["ringing_count"]
    else:
        df["_xn_suppressed"] = False

    # Agent utilization
    if "incall_agents_count" in df.columns and "total_agents_count" in df.columns:
        df["_utilization"] = np.where(
            df["total_agents_count"] > 0,
            df["incall_agents_count"] / df["total_agents_count"] * 100,
            0
        )
    else:
        df["_utilization"] = 0

    return df


# ─────────────────────────────────────────────────────────────
# Adım 2 — Kategori Tespiti
# ─────────────────────────────────────────────────────────────

def _detect_categories(row: pd.Series, df: pd.DataFrame, idx: int) -> list:
    """
    Tek bir event için hangi kategoriler tetiklendi?
    Her kategori için (category_key, severity, triggered_by) döndürür.
    """
    detections = []
    z = ZSCORE_THRESHOLD

    # ── ABANDON_SPIKE ─────────────────────────────────────────
    if "computed_abandon_rate" in row.index:
        ab_z     = abs(row.get("_z_computed_abandon_rate", 0))
        ab_delta = row.get("_delta_computed_abandon_rate", 0)
        ab_val   = row.get("computed_abandon_rate", 0)

        if ab_z > z or ab_delta > 0.03:           # 3+ sigma VEYA +%3 ani artış
            severity = 4 if (ab_z > 4 or ab_val > 0.10) else \
                       3 if (ab_z > 3 or ab_val > 0.05) else \
                       2 if (ab_z > z or ab_val > 0.03) else 1
            detections.append((
                "ABANDON_SPIKE", severity,
                {
                    "abandon_rate":       round(ab_val * 100, 2),
                    "z_score":            round(ab_z, 2),
                    "delta":              round(ab_delta * 100, 2),
                    "threshold_pct":      3.0,
                }
            ))

    # ── ANSWER_RATE_DROP ──────────────────────────────────────
    if "computed_answer_rate" in row.index:
        ar_z     = abs(row.get("_z_computed_answer_rate", 0))
        ar_delta = row.get("_delta_computed_answer_rate", 0)
        ar_val   = row.get("computed_answer_rate", 0)

        if ar_z > z or ar_delta < -0.10:          # ani %10 düşüş
            severity = 4 if ar_val < 0.30 else \
                       3 if ar_val < 0.50 else \
                       2 if ar_val < 0.70 else 1
            detections.append((
                "ANSWER_RATE_DROP", severity,
                {
                    "answer_rate":   round(ar_val * 100, 2),
                    "z_score":       round(ar_z, 2),
                    "delta":         round(ar_delta * 100, 2),
                }
            ))

    # ── BOOST_INSTABILITY ─────────────────────────────────────
    if "computed_boost_rate" in row.index:
        br_z   = abs(row.get("_z_computed_boost_rate", 0))
        br_val = row.get("computed_boost_rate", 0)
        br_delta = row.get("_delta_computed_boost_rate", 0)

        if br_z > z or br_val > 5.0:
            severity = 4 if br_val > 10 else \
                       3 if br_val > 5  else \
                       2 if br_val > 3  else 1
            detections.append((
                "BOOST_INSTABILITY", severity,
                {
                    "boost_rate": round(br_val, 4),
                    "z_score":    round(br_z, 2),
                    "delta":      round(br_delta, 4),
                }
            ))

    # ── XN_SUPPRESSION ────────────────────────────────────────
    if row.get("_xn_suppressed", False):
        raw_xn   = row.get("raw_xn", 0)
        ringing  = row.get("ringing_count", 0)
        excess   = ringing - raw_xn
        severity = 4 if excess > 10 else \
                   3 if excess > 5  else \
                   2 if excess > 2  else 1
        detections.append((
            "XN_SUPPRESSION", severity,
            {
                "raw_xn":        round(raw_xn, 4),
                "ringing_count": round(ringing, 4),
                "excess":        round(excess, 4),
            }
        ))

    # ── AGENT_SHORTAGE ────────────────────────────────────────
    if "available_agents_count" in row.index:
        av_val = row.get("available_agents_count", 0)
        av_z   = abs(row.get("_z_available_agents_count", 0))

        if av_val == 0 or (av_z > z and row.get("_delta_available_agents_count", 0) < -2):
            severity = 4 if av_val == 0 else \
                       3 if av_val <= 2 else 2
            detections.append((
                "AGENT_SHORTAGE", severity,
                {
                    "available_agents": int(av_val),
                    "z_score":          round(av_z, 2),
                }
            ))

    # ── HIGH_UTILIZATION ──────────────────────────────────────
    util = row.get("_utilization", 0)
    if util >= 80:
        severity = 4 if util >= 98 else \
                   3 if util >= 90 else \
                   2 if util >= 85 else 1
        detections.append((
            "HIGH_UTILIZATION", severity,
            {
                "utilization_pct": round(util, 1),
                "incall_agents":   int(row.get("incall_agents_count", 0)),
                "total_agents":    int(row.get("total_agents_count", 0)),
            }
        ))

    # ── TALK_TIME_SPIKE ───────────────────────────────────────
    if "avg_talk_time" in row.index:
        tt_z   = abs(row.get("_z_avg_talk_time", 0))
        tt_val = row.get("avg_talk_time", 0)
        if tt_z > z:
            severity = 3 if tt_z > 4 else 2
            detections.append((
                "TALK_TIME_SPIKE", severity,
                {
                    "avg_talk_time": round(tt_val, 1),
                    "z_score":       round(tt_z, 2),
                }
            ))

    # ── MULTI_METRIC — 3+ kategori aynı anda ─────────────────
    if len(detections) >= 3:
        max_sev = max(d[1] for d in detections)
        detections.append((
            "MULTI_METRIC", min(max_sev + 1, 4),
            {
                "triggered_categories": [d[0] for d in detections],
                "count":                len(detections),
            }
        ))

    return detections


# ─────────────────────────────────────────────────────────────
# Adım 3 — Precursor & Recovery penceresi
# ─────────────────────────────────────────────────────────────

_CONTEXT_COLS = [
    "computed_abandon_rate", "computed_answer_rate", "computed_boost_rate",
    "xn", "raw_xn", "ringing_count",
    "available_agents_count", "incall_agents_count",
    "avg_talk_time", "avg_ring_time",
    "create_date", "campaign_name",
]


def _extract_context(df: pd.DataFrame, idx: int, window: int = CONTEXT_WINDOW):
    """Anomali event'i çevresindeki N event'i döndür."""
    cols = [c for c in _CONTEXT_COLS if c in df.columns]

    pre_start  = max(0, idx - window)
    post_end   = min(len(df), idx + window + 1)

    precursor = df.iloc[pre_start:idx][cols].copy()
    recovery  = df.iloc[idx+1:post_end][cols].copy()

    return precursor, recovery


def _context_stats(context_df: pd.DataFrame) -> dict:
    """Bağlam penceresi için özet istatistikler."""
    stats = {}
    numeric = context_df.select_dtypes(include=[np.number])
    for col in numeric.columns:
        if col.startswith("_"):
            continue
        stats[col] = {
            "start":  round(float(numeric[col].iloc[0]),  4) if len(numeric) > 0 else None,
            "end":    round(float(numeric[col].iloc[-1]), 4) if len(numeric) > 0 else None,
            "mean":   round(float(numeric[col].mean()),   4),
            "trend":  "rising"   if numeric[col].is_monotonic_increasing else
                      "falling"  if numeric[col].is_monotonic_decreasing else
                      "volatile",
        }
    return stats


# ─────────────────────────────────────────────────────────────
# Ana Fonksiyon
# ─────────────────────────────────────────────────────────────

def run_detection(df: pd.DataFrame,
                  context_window: int = CONTEXT_WINDOW) -> dict:
    """
    Tüm pipeline'ı çalıştır.

    Returns:
        {
          "enriched_df":    pd.DataFrame,         # feature kolonları eklenmiş
          "anomaly_events": List[AnomalyEvent],   # tüm anomali event'leri
          "categories":     Dict[str, CategorySummary],  # kategori bazlı özet
          "stats":          dict,                 # genel istatistikler
        }
    """
    if df is None or len(df) == 0:
        return {"enriched_df": df, "anomaly_events": [], "categories": {}, "stats": {}}

    # 1. Feature engineering
    edf = _engineer_features(df)

    # 2. Her event için kategori tespiti
    all_events: list[AnomalyEvent] = []

    for idx, row in edf.iterrows():
        detections = _detect_categories(row, edf, idx)
        if not detections:
            continue

        # Precursor & Recovery
        pre_df, rec_df = _extract_context(edf, idx, context_window)
        pre_stats = _context_stats(pre_df)
        rec_stats = _context_stats(rec_df)

        # Timestamp & campaign
        ts   = str(row.get("create_date", ""))  if "create_date"   in row.index else ""
        camp = str(row.get("campaign_name", "")) if "campaign_name" in row.index else ""

        for cat_key, severity, triggered_by in detections:
            ev = AnomalyEvent(
                index          = int(idx),
                category       = cat_key,
                severity       = severity,
                severity_label = SEVERITY_LEVELS.get(severity, "LOW"),
                timestamp      = ts,
                campaign_name  = camp,
                triggered_by   = triggered_by,
                precursor_df   = pre_df,
                recovery_df    = rec_df,
                precursor_stats= pre_stats,
                recovery_stats = rec_stats,
            )
            all_events.append(ev)

    # 3. Kategorize et
    categories: dict[str, CategorySummary] = {}
    for ev in all_events:
        cat = ev.category
        if cat not in categories:
            meta = CATEGORIES.get(cat, {
                "label": cat, "description": "", "color": "#888", "icon": "⚪"
            })
            categories[cat] = CategorySummary(
                category    = cat,
                label       = meta["label"],
                description = meta["description"],
                color       = meta["color"],
                icon        = meta["icon"],
                count       = 0,
                severity_distribution = {"LOW": 0, "MEDIUM": 0, "HIGH": 0, "CRITICAL": 0},
                events      = [],
            )
        categories[cat].count += 1
        categories[cat].severity_distribution[ev.severity_label] += 1
        categories[cat].events.append(ev)

    # 4. Genel istatistikler
    total_anomaly_events = len(set(ev.index for ev in all_events))
    stats = {
        "total_events":         len(df),
        "total_anomaly_events": total_anomaly_events,
        "anomaly_rate_pct":     round(total_anomaly_events / len(df) * 100, 2) if len(df) > 0 else 0,
        "total_detections":     len(all_events),
        "category_count":       len(categories),
        "critical_count":       sum(1 for ev in all_events if ev.severity == 4),
        "high_count":           sum(1 for ev in all_events if ev.severity == 3),
    }

    # Kategorileri severity'ye göre sırala (kritik önce)
    categories = dict(
        sorted(categories.items(),
               key=lambda x: (
                   -x[1].severity_distribution.get("CRITICAL", 0),
                   -x[1].severity_distribution.get("HIGH", 0),
                   -x[1].count
               ))
    )

    return {
        "enriched_df":    edf,
        "anomaly_events": all_events,
        "categories":     categories,
        "stats":          stats,
    }