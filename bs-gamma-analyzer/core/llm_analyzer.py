"""
llm_analyzer.py
───────────────
Adım 2 — Claude API Entegrasyonu

Sorumluluklar:
  1. AnomalyEvent → yapılandırılmış context JSON
  2. Sistem prompt + analiz prompt template'leri
  3. Claude API çağrısı
  4. Ham yanıtı parse et → AnalysisReport dataclass
"""

import os
import json
import anthropic
from dataclasses import dataclass, field
from typing import Optional

from core.anomaly_detector import AnomalyEvent, CategorySummary, CATEGORIES


# ─────────────────────────────────────────────────────────────
# Rapor Veri Sınıfı
# ─────────────────────────────────────────────────────────────

@dataclass
class AnalysisReport:
    """Claude'un ürettiği analiz raporunu yapılandırılmış tutar."""
    category:          str
    severity_label:    str
    summary:           str                        # "Ne oldu" — 2-3 cümle
    root_causes:       list[dict]                 # [{rank, cause, explanation}]
    precursor_signals: list[str]                  # öncesindeki uyarı sinyalleri
    recovery_analysis: str                        # recovery nasıl gerçekleşti
    recommendations:   list[dict]                 # [{priority, action, rationale, confidence}]
    watch_signals:     list[str]                  # bir sonraki risk için izlenmesi gerekenler
    raw_response:      str = ""                   # Claude'un ham yanıtı (debug için)
    error:             Optional[str] = None       # hata varsa


# ─────────────────────────────────────────────────────────────
# Sistem Promptu — Bir Kez Tanımlanır
# ─────────────────────────────────────────────────────────────

SYSTEM_PROMPT = """
Sen bir Predictive Dialer uzmanısın. Özellikle Gamma algoritması konusunda derin bilgin var.

## Gamma Algoritması Bilgin

Formül:
  rawXn    = boostRate × (availableAgents + (inCallAgents / avgTalk) × avgRing × CF) × abandonPenalty
  finalXn  = max(0, rawXn − ringingCount)
  boostRate     = 1 / answerRate
  abandonPenalty = max(0.1, 1 − abandonRate × abandonSensitivity)

Kritik dinamikler:
- answerRate düşerse boostRate yükselir → rawXn artar → daha fazla arama → abandon artar → sarmal
- ringingCount > rawXn olduğunda finalXn = 0 → sistem duraklar
- Bootstrap modu: windowSize < requiredWindowSize → güvenlik zemini devreye girer
- CF (correctionFactor) sadece inCallAgents katkısına uygulanır

## Görevin

Sana JSON formatında anomali verisi gelecek. Bu veriyi analiz edeceksin.

## Kurallar

1. ASLA sana verilmeyen sayı üretme — sadece verilen metrikleri yorumla
2. Tüm sayısal ifadeleri verilen veriden türet
3. Önerileri somut ve uygulanabilir yap — "parametreyi ayarla" değil, "CF'yi X'ten Y'ye düşür"
4. Türkçe yanıt ver
5. Belirtilen JSON formatında yanıt ver — başka hiçbir şey ekleme

## Yanıt Formatı

Yanıtını SADECE aşağıdaki JSON formatında ver. Başına veya sonuna hiçbir şey ekleme:

{
  "summary": "Ne oldu — 2-3 cümle, net ve teknik",
  "root_causes": [
    {
      "rank": 1,
      "cause": "Kısa başlık",
      "explanation": "Detaylı açıklama — hangi metrik, nasıl etkiledi"
    }
  ],
  "precursor_signals": [
    "Anomaliden önce gözlemlenen uyarı sinyali 1",
    "Uyarı sinyali 2"
  ],
  "recovery_analysis": "Recovery nasıl gerçekleşti veya gerçekleşmedi — metriklerle açıkla",
  "recommendations": [
    {
      "priority": 1,
      "action": "Somut aksiyon",
      "rationale": "Neden bu aksiyon — hangi metrik bunu destekliyor",
      "confidence": "HIGH / MEDIUM / LOW"
    }
  ],
  "watch_signals": [
    "Bir sonraki anomali riskini önceden gösterebilecek sinyal 1",
    "Sinyal 2"
  ]
}
"""


# ─────────────────────────────────────────────────────────────
# Context Builder — LLM'e Gönderilecek Yapıyı Hazırla
# ─────────────────────────────────────────────────────────────

def _safe_round(val, n=4):
    try:
        return round(float(val), n)
    except Exception:
        return None


def _df_to_records(df, max_rows=10) -> list:
    """DataFrame'i temiz bir record listesine çevir."""
    if df is None or len(df) == 0:
        return []

    cols_to_drop = [c for c in df.columns if c.startswith("_")]
    clean = df.drop(columns=cols_to_drop, errors="ignore").head(max_rows)

    records = []
    for _, row in clean.iterrows():
        rec = {}
        for col, val in row.items():
            if col == "create_date":
                rec[col] = str(val)
            elif hasattr(val, "item"):        # numpy scalar
                rec[col] = _safe_round(val)
            elif isinstance(val, float):
                rec[col] = _safe_round(val)
            else:
                rec[col] = val
        records.append(rec)
    return records


def build_context(event: AnomalyEvent,
                  campaign_params: Optional[dict] = None,
                  similar_past_count: int = 0) -> str:
    """
    AnomalyEvent → LLM'e gönderilecek yapılandırılmış context string.
    Tüm sayısal hesaplama burada biter — LLM sadece yorumlar.
    """
    cat_meta = CATEGORIES.get(event.category, {})

    context = {
        "anomali_bilgisi": {
            "kategori":        event.category,
            "kategori_label":  cat_meta.get("label", event.category),
            "aciklama":        cat_meta.get("description", ""),
            "severity":        event.severity_label,
            "zaman":           event.timestamp,
            "kampanya":        event.campaign_name,
            "tetikleyiciler":  event.triggered_by,
        },
        "onceki_10_event": {
            "kayitlar": _df_to_records(event.precursor_df),
            "trend_ozeti": {
                col: {
                    "baslangic": stats.get("start"),
                    "son":       stats.get("end"),
                    "trend":     stats.get("trend"),
                    "ortalama":  stats.get("mean"),
                }
                for col, stats in (event.precursor_stats or {}).items()
                if not col.startswith("_")
            }
        },
        "sonraki_10_event": {
            "kayitlar": _df_to_records(event.recovery_df),
            "trend_ozeti": {
                col: {
                    "baslangic": stats.get("start"),
                    "son":       stats.get("end"),
                    "trend":     stats.get("trend"),
                    "ortalama":  stats.get("mean"),
                }
                for col, stats in (event.recovery_stats or {}).items()
                if not col.startswith("_")
            }
        },
    }

    if campaign_params:
        context["kampanya_parametreleri"] = campaign_params

    if similar_past_count > 0:
        context["benzer_gecmis_anomali_sayisi"] = similar_past_count

    return json.dumps(context, ensure_ascii=False, indent=2)


# ─────────────────────────────────────────────────────────────
# Analiz Prompt'u
# ─────────────────────────────────────────────────────────────

def _build_analysis_prompt(context_str: str,
                            user_question: Optional[str] = None) -> str:
    """
    İlk analiz veya takip sorusu için prompt üret.
    user_question varsa → soru modu, yoksa → tam rapor modu.
    """
    if user_question:
        return f"""
Aşağıdaki anomali verisine dayanarak şu soruyu yanıtla:

SORU: {user_question}

ANOMALİ VERİSİ:
{context_str}

Yanıtını JSON formatında ver:
{{
  "summary": "Sorunun kısa özeti",
  "root_causes": [],
  "precursor_signals": [],
  "recovery_analysis": "",
  "recommendations": [
    {{
      "priority": 1,
      "action": "Aksiyon",
      "rationale": "Gerekçe",
      "confidence": "HIGH/MEDIUM/LOW"
    }}
  ],
  "watch_signals": []
}}
"""
    else:
        return f"""
Aşağıdaki anomali verisini tam olarak analiz et ve rapor üret:

{context_str}
"""


# ─────────────────────────────────────────────────────────────
# Claude API Çağrısı
# ─────────────────────────────────────────────────────────────

def _call_claude(api_key: str,
                 prompt: str,
                 model: str = "claude-sonnet-4-6") -> str:
    """
    Claude API'yi çağır, ham yanıt metnini döndür.
    Hata durumunda exception fırlatır.
    """
    client = anthropic.Anthropic(api_key=api_key)
    message = client.messages.create(
        model=model,
        max_tokens=4096,
        system=SYSTEM_PROMPT,
        messages=[
            {"role": "user", "content": prompt}
        ]
    )
    return message.content[0].text


def _repair_truncated_json(text: str) -> str:
    """
    max_tokens sınırı nedeniyle kesilmiş JSON'u kurtarmaya çalış.
    """
    stack   = []
    in_str  = False
    escaped = False
    last_complete_pos = 0

    for i, ch in enumerate(text):
        if escaped:
            escaped = False
            continue
        if ch == "\\" and in_str:
            escaped = True
            continue
        if ch == '"' and not escaped:
            in_str = not in_str
            continue
        if in_str:
            continue
        if ch in "{[":
            stack.append(ch)
        elif ch in "}]":
            if stack:
                stack.pop()
                if not stack:
                    last_complete_pos = i + 1

    if not stack:
        return text  # already valid

    repaired = text[:last_complete_pos] if last_complete_pos > 0 else text
    if in_str:
        repaired += '"'
    close_map = {"{": "}", "[": "]"}
    for opener in reversed(stack):
        repaired += close_map.get(opener, "}")
    return repaired


def _parse_response(raw: str, event: AnomalyEvent) -> AnalysisReport:
    """
    Claude'un JSON yanıtını AnalysisReport'a dönüştür.
    Kesilmiş JSON'u otomatik kurtarmaya çalışır.
    """
    text = raw.strip()
    if "```json" in text:
        text = text.split("```json")[1].split("```")[0].strip()
    elif "```" in text:
        text = text.split("```")[1].split("```")[0].strip()

    data = None
    parse_error = None

    # 1. Doğrudan parse
    try:
        data = json.loads(text)
    except json.JSONDecodeError as e1:
        parse_error = e1
        # 2. Truncated repair
        try:
            data = json.loads(_repair_truncated_json(text))
        except json.JSONDecodeError as e2:
            return AnalysisReport(
                category          = event.category,
                severity_label    = event.severity_label,
                summary           = "",
                root_causes       = [],
                precursor_signals = [],
                recovery_analysis = "",
                recommendations   = [],
                watch_signals     = [],
                raw_response      = raw,
                error             = f"JSON parse hatası: {e2}\n\nHam yanıt:\n{raw}",
            )

    return AnalysisReport(
        category          = event.category,
        severity_label    = event.severity_label,
        summary           = data.get("summary", ""),
        root_causes       = data.get("root_causes", []),
        precursor_signals = data.get("precursor_signals", []),
        recovery_analysis = data.get("recovery_analysis", ""),
        recommendations   = data.get("recommendations", []),
        watch_signals     = data.get("watch_signals", []),
        raw_response      = raw,
    )



# ─────────────────────────────────────────────────────────────
# Ana Fonksiyon — Analiz Çalıştır
# ─────────────────────────────────────────────────────────────

def analyze_anomaly(event: AnomalyEvent,
                    api_key: str,
                    campaign_params: Optional[dict] = None,
                    user_question:   Optional[str]  = None,
                    model:           str = "claude-sonnet-4-6") -> AnalysisReport:
    """
    Tek bir AnomalyEvent için Claude analizi çalıştır.

    Args:
        event:            Analiz edilecek anomali event'i
        api_key:          Anthropic API key
        campaign_params:  CF, AS, windowSize gibi kampanya parametreleri (opsiyonel)
        user_question:    Varsa soru modu, yoksa tam rapor modu
        model:            Kullanılacak Claude modeli

    Returns:
        AnalysisReport
    """
    try:
        context_str = build_context(event, campaign_params)
        prompt      = _build_analysis_prompt(context_str, user_question)
        raw         = _call_claude(api_key, prompt, model)
        return _parse_response(raw, event)

    except anthropic.AuthenticationError:
        return AnalysisReport(
            category=event.category, severity_label=event.severity_label,
            summary="", root_causes=[], precursor_signals=[],
            recovery_analysis="", recommendations=[], watch_signals=[],
            error="API key geçersiz. Lütfen doğru bir Anthropic API key girin."
        )
    except anthropic.RateLimitError:
        return AnalysisReport(
            category=event.category, severity_label=event.severity_label,
            summary="", root_causes=[], precursor_signals=[],
            recovery_analysis="", recommendations=[], watch_signals=[],
            error="Rate limit aşıldı. Birkaç saniye bekleyip tekrar deneyin."
        )
    except Exception as e:
        return AnalysisReport(
            category=event.category, severity_label=event.severity_label,
            summary="", root_causes=[], precursor_signals=[],
            recovery_analysis="", recommendations=[], watch_signals=[],
            error=f"Beklenmedik hata: {str(e)}"
        )