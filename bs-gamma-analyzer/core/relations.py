import pandas as pd
import numpy as np


# ─────────────────────────────────────────────────────────────
# 1. Xn / rawXn / ringingCount ilişkisi
# ─────────────────────────────────────────────────────────────

def xn_ringing_analysis(df: pd.DataFrame) -> dict:
    """
    rawXn < ringingLoad → Xn sifirlanir (max(0, rawXn - ringingLoad) = 0)
    ringingLoad = ringingCbCount*cbAR + ringingRetryCount*retryAR + ringingFreshCount*freshAR
    Geriye uyum: yeni alanlar yoksa ringing_count kullanilir.
    """
    result = {}

    has_raw = "raw_xn" in df.columns or "raw_xn_expected" in df.columns
    raw_col = "raw_xn" if "raw_xn" in df.columns else "raw_xn_expected"

    if not has_raw:
        result["available"] = False
        return result

    df = df.copy()
    df["_raw"] = pd.to_numeric(df[raw_col], errors="coerce").fillna(0)
    df["_xn"]  = pd.to_numeric(df.get("xn", pd.Series([0]*len(df))),
                                errors="coerce").fillna(0)

    # ringingLoad: yeni alan varsa kullan, yoksa ringing_count ile geriye uyum
    has_load      = "ringing_load" in df.columns
    has_breakdown = all(c in df.columns for c in ["ringing_cb_count", "cb_answer_rate"])

    if has_load:
        df["_ring"] = pd.to_numeric(df["ringing_load"], errors="coerce").fillna(0)
        result["load_source"] = "ringing_load"
    elif has_breakdown:
        cb_ar     = pd.to_numeric(df.get("cb_answer_rate",    0), errors="coerce").fillna(0)
        retry_ar  = pd.to_numeric(df.get("retry_answer_rate", 0), errors="coerce").fillna(0)
        fresh_ar  = pd.to_numeric(df.get("fresh_answer_rate", 0), errors="coerce").fillna(0)
        cb_cnt    = pd.to_numeric(df.get("ringing_cb_count",    0), errors="coerce").fillna(0)
        retry_cnt = pd.to_numeric(df.get("ringing_retry_count", 0), errors="coerce").fillna(0)
        fresh_cnt = pd.to_numeric(df.get("ringing_fresh_count", 0), errors="coerce").fillna(0)
        df["_ring"] = cb_cnt * cb_ar + retry_cnt * retry_ar + fresh_cnt * fresh_ar
        result["load_source"] = "computed_breakdown"
    elif "ringing_count" in df.columns:
        df["_ring"] = pd.to_numeric(df["ringing_count"], errors="coerce").fillna(0)
        result["load_source"] = "ringing_count_legacy"
    else:
        result["available"] = False
        return result

    result["available"] = True
    suppressed = df[df["_raw"] < df["_ring"]].copy()
    normal     = df[df["_raw"] >= df["_ring"]].copy()

    result["total"]            = len(df)
    result["suppressed_count"] = len(suppressed)
    result["normal_count"]     = len(normal)
    result["suppression_rate"] = round(len(suppressed) / len(df) * 100, 2) if len(df) > 0 else 0

    if len(suppressed) > 0:
        suppressed["_excess"] = suppressed["_ring"] - suppressed["_raw"]
        result["avg_excess"]    = round(suppressed["_excess"].mean(), 4)
        result["max_excess"]    = round(suppressed["_excess"].max(), 4)
        result["suppressed_df"] = suppressed
    else:
        result["avg_excess"]    = 0
        result["max_excess"]    = 0
        result["suppressed_df"] = pd.DataFrame()

    result["df"] = df
    return result
def agent_xn_analysis(df: pd.DataFrame) -> dict:
    result = {}
    needed = ["available_agents_count", "incall_agents_count", "total_agents_count"]
    xn_col = "xn"

    missing = [c for c in needed if c not in df.columns]
    if missing or xn_col not in df.columns:
        result["available"] = False
        return result

    result["available"] = True
    df = df.copy()

    for c in needed + [xn_col]:
        df[c] = pd.to_numeric(df[c], errors="coerce").fillna(0)

    # Utilization
    df["utilization_pct"] = np.where(
        df["total_agents_count"] > 0,
        df["incall_agents_count"] / df["total_agents_count"] * 100,
        0
    )

    # Xn / availableAgents oranı — Gamma'nın ne kadar agresif davrandığı
    df["xn_per_agent"] = np.where(
        df["available_agents_count"] > 0,
        df[xn_col] / df["available_agents_count"],
        np.nan
    )

    # Correlation matrix
    corr_cols = ["available_agents_count", "incall_agents_count",
                 "total_agents_count", "utilization_pct", xn_col]
    corr = df[corr_cols].corr()

    # Utilization bucket'ları: 0-20, 20-40, ..., 80-100
    bins   = [0, 20, 40, 60, 80, 100]
    labels = ["0-20%", "20-40%", "40-60%", "60-80%", "80-100%"]
    df["util_bucket"] = pd.cut(
        df["utilization_pct"].clip(0, 100),
        bins=bins, labels=labels, include_lowest=True
    )
    bucket_stats = df.groupby("util_bucket", observed=True)[xn_col].agg(
        ["mean", "median", "std", "count"]
    ).reset_index()
    bucket_stats.columns = ["util_bucket", "xn_mean", "xn_median", "xn_std", "count"]

    result["df"]           = df
    result["corr"]         = corr
    result["bucket_stats"] = bucket_stats
    return result


# ─────────────────────────────────────────────────────────────
# 3. CPS / Cache Limit etkisi
# ─────────────────────────────────────────────────────────────

def cps_cache_analysis(df: pd.DataFrame) -> dict:
    result = {}
    xn_col = "xn"

    if xn_col not in df.columns:
        result["available"] = False
        return result

    result["available"] = True
    df = df.copy()
    df[xn_col] = pd.to_numeric(df[xn_col], errors="coerce").fillna(0)

    # CPS analizi
    has_cps = "call_per_second" in df.columns
    if has_cps:
        df["call_per_second"] = pd.to_numeric(df["call_per_second"], errors="coerce")
        df["xn_vs_cps_ratio"] = np.where(
            df["call_per_second"] > 0,
            df[xn_col] / df["call_per_second"],
            np.nan
        )
        # Xn CPS'i aştığı anlar — sistem CPS'e takılıyor
        df["xn_capped_by_cps"] = (
            df[xn_col] >= df["call_per_second"]
        ) & df["call_per_second"].notna()

        cps_stats = df.groupby("call_per_second")[xn_col].agg(
            ["mean", "max", "count"]
        ).reset_index()
        result["cps_stats"]       = cps_stats
        result["xn_capped_count"] = int(df["xn_capped_by_cps"].sum())
        result["xn_capped_rate"]  = round(
            df["xn_capped_by_cps"].sum() / len(df) * 100, 2
        )
    result["has_cps"] = has_cps

    # Cache limit analizi
    has_cache = "cache_limit" in df.columns
    if has_cache:
        df["cache_limit"] = pd.to_numeric(df["cache_limit"], errors="coerce")

        # ringing / cache_limit oranı — queue doluluk baskısı
        if "ringing_count" in df.columns:
            df["ringing_count"] = pd.to_numeric(df["ringing_count"], errors="coerce").fillna(0)
            df["ringing_cache_ratio"] = np.where(
                df["cache_limit"] > 0,
                df["ringing_count"] / df["cache_limit"],
                np.nan
            )

        cache_stats = df.groupby("cache_limit")[xn_col].agg(
            ["mean", "max", "count"]
        ).reset_index()
        result["cache_stats"] = cache_stats
    result["has_cache"] = has_cache

    result["df"] = df
    return result


# ─────────────────────────────────────────────────────────────
# 4. Xn stability (smoothness) analizi
# ─────────────────────────────────────────────────────────────

def xn_stability_analysis(df: pd.DataFrame) -> dict:
    result = {}
    xn_col = "xn"

    if xn_col not in df.columns:
        result["available"] = False
        return result

    result["available"] = True
    df = df.copy()
    df[xn_col] = pd.to_numeric(df[xn_col], errors="coerce").fillna(0)

    # Delta Xn (event başına değişim)
    df["xn_delta"]     = df[xn_col].diff().abs()
    df["xn_delta_pct"] = df["xn_delta"] / df[xn_col].replace(0, np.nan) * 100

    # Rolling stats (20 event pencere)
    df["xn_rolling_mean"] = df[xn_col].rolling(20, min_periods=1).mean()
    df["xn_rolling_std"]  = df[xn_col].rolling(20, min_periods=1).std().fillna(0)

    # Spike tespiti: Xn > rolling_mean + 2*std
    df["is_spike"] = df[xn_col] > (df["xn_rolling_mean"] + 2 * df["xn_rolling_std"])
    df["is_drop"]  = (df[xn_col] == 0) & (df["xn_rolling_mean"] > 0)

    # Sürekli sıfır run'ları (Xn=0 kaç event boyunca devam etti?)
    df["xn_zero"] = (df[xn_col] == 0).astype(int)
    df["zero_run_id"] = (df["xn_zero"] != df["xn_zero"].shift()).cumsum()
    zero_runs = df[df["xn_zero"] == 1].groupby("zero_run_id").size()

    result["df"]             = df
    result["spike_count"]    = int(df["is_spike"].sum())
    result["drop_count"]     = int(df["is_drop"].sum())
    result["avg_delta"]      = round(df["xn_delta"].mean(), 4)
    result["max_delta"]      = round(df["xn_delta"].max(), 4)
    result["cv"]             = round(
        df[xn_col].std() / df[xn_col].mean() * 100
        if df[xn_col].mean() > 0 else 0, 2
    )  # Coefficient of variation — volatilite özeti
    result["zero_runs"]      = zero_runs
    result["longest_zero_run"] = int(zero_runs.max()) if len(zero_runs) > 0 else 0

    return result
