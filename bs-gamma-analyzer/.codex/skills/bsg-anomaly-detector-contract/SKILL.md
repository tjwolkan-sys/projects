---
name: bsg-anomaly-detector-contract
description: bs-gamma-analyzer'da `core/anomaly_detector.py` ve `core/anomaly.py` üzerinde anomaly kategori adları, severity skalası, rolling pencere/zscore eşikleri, precursor/recovery şeması ve `AnomalyEvent`/`CategorySummary` dataclass alanlarını koruyarak değişiklik yapmak için.
---

# bsg-anomaly-detector-contract

## Amaç

Anomaly detector çıktısı (kategori, severity, triggered_by, precursor/recovery DataFrame'leri) hem Streamlit UI hem Claude analizörü tarafından tüketilir. Contract drift downstream'de sessiz hatalar üretir. Bu skill kategori, severity ve şema kontratlarını koruyarak değişiklik yapmayı sağlar.

## Birlikte Kullanılacak Skill'ler

- `bsg-tdd-workflow`: Her kategori için karakterizasyon testi.
- `bsg-characterization-tests`: Severity sınırı ve trigger eşiklerinin kilidi.
- `bsg-data-pipeline-performance-guardrails`: Per-event ve per-batch hot path maliyeti.

## Önce Oku

- `core/anomaly_detector.py`: Tüm fonksiyonlar ve sabitler.
- `core/anomaly.py`: Flag tabanlı temel katman.
- `docs/architecture/ANOMALY-DETECTION.md`: Mevcut mimari özet.
- Detector çıktısını tüketen yerler: `app.py` AI Analiz sekmesi, `core/llm_analyzer.py`.

## Kritik Kod Yüzeyi

- Kategori adları: `ABANDON_SPIKE`, `ANSWER_RATE_DROP`, `BOOST_INSTABILITY`, `XN_SUPPRESSION`, `AGENT_SHORTAGE`, `HIGH_UTILIZATION`, `TALK_TIME_SPIKE`, `MULTI_METRIC`.
- Severity skalası: 1=LOW, 2=MEDIUM, 3=HIGH, 4=CRITICAL.
- Sabitler: `ROLLING_WINDOW`, `ZSCORE_THRESHOLD`, `DELTA_WINDOW`, `CONTEXT_WINDOW`.
- Dataclass'lar: `AnomalyEvent` (alan: `category`, `severity`, `triggered_by`, `event_index`, `event_row`, `precursor_df`, `recovery_df`), `CategorySummary`.
- Flag tabanlı kategoriler (`anomaly.py`): `XN_ZERO_WITH_AGENTS`, `XN_SPIKE`, `ZERO_ANSWER_RATE`, `XN_WITH_NO_AGENTS`, `RINGING_LOAD_EXCEEDS_RAWXN`, `INCALL_EXCEEDS_TOTAL`.

## Contract Kontrolü

Değişiklik öncesi soruları cevapla:

- Kategori adı değişiyor mu? (Drift riski var; downstream tüketicide kontrol gerekir.)
- Severity skalası 1–4 dışına çıkıyor mu? (UI render mantığı bu skalaya bağlı.)
- `precursor_df`/`recovery_df` boyutu değişiyor mu? (Memory etkisi.)
- `triggered_by` haritasının anahtarları değişiyor mu? (Claude prompt context'i etkilenir.)
- Yeni kategori ekleniyorsa flag tabanlı katmanla ilişkisi kuruldu mu?
- `BOOST_INSTABILITY` legacy davranışı korunuyor mu? (Java'da kaldırıldığı için boş kalsa bile kategori silinmez.)

## Test Stratejisi

- Her kategori için sabit fixture testi: `tests/anomaly/test_<kategori>_<davranış>_characterization.py`.
- Severity sınır testleri: `tests/anomaly/test_<kategori>_severity_boundary.py`.
- `MULTI_METRIC` compound testi: 3+ kategori tetiklendiğinde compound üretiliyor.
- Precursor/recovery boyut testi: `len(precursor_df) <= CONTEXT_WINDOW`.
- `run_detection` çıktı şeması contract testi: `enriched_df`, `anomaly_events`, `categories`, `stats` anahtarları.

## Arama Kalıpları

- Kategori adı: `'ABANDON_SPIKE'`, `'XN_SUPPRESSION'`, vb.
- Severity okuma: `event.severity`, `severity ==`, `severity in`.
- Precursor/recovery: `precursor_df`, `recovery_df`, `event_row`.
- Sabit kullanımı: `ROLLING_WINDOW`, `ZSCORE_THRESHOLD`, `CONTEXT_WINDOW`.

## Raporlama

- Hangi kategori/severity/sabit değişti.
- Downstream tüketicilerin etkisi (UI sekmesi, Claude prompt, görselleştirme).
- Karakterizasyon test sonuçları.
- Memory ve performans etkisi.
- Kategori adı yeni eklendiyse `ANOMALY-DETECTION.md` ve `AGENTS.md` skill envanteri güncel.
