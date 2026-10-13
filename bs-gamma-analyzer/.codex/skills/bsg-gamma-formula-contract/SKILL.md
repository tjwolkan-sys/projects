---
name: bsg-gamma-formula-contract
description: bs-gamma-analyzer'da `core/formula.py` Gamma formülü implementasyonunda Java tarafındaki `PredictiveGammaCallCalculator` ile parity tutarak değişiklik yapmak için. Mode adları, sabitler, ringing yükü hesabı, abandon penalty, fallback davranışları ve numerik tolerans contract'larını koruyan değişikliklerde kullan.
---

# bsg-gamma-formula-contract

## Amaç

`core/formula.py` üzerindeki Gamma formülü Java tarafıyla parity tutar. Java implementasyonu `PredictiveGammaCallCalculator` sınıfında bulunur. Python tarafında değişiklik öncesi Java tarafının değişip değişmediği kontrol edilir; aksi durumda Python tarafında yapılan değişiklik üretimle drift üretir.

## Birlikte Kullanılacak Skill'ler

- `bsg-tdd-workflow`: Her değişiklik testle başlar; mevcut davranış karakterizasyon testiyle kilitlenir.
- `bsg-characterization-tests`: Mode bazlı sabit fixture testleri.
- `bsg-data-pipeline-performance-guardrails`: Per-event hot path değişikliklerinde maliyet kontrolü.

## Önce Oku

- `core/formula.py`: Tüm fonksiyonlar (`compute_xn`, `apply_formula`).
- `core/validator.py`: Tolerans contract'ı (`TOLERANCE`, `TOLERANCE_PCT`).
- `docs/architecture/GAMMA-FORMULA.md`: Mevcut mimari özet.
- Java tarafındaki `PredictiveGammaCallCalculator` (varsa erişim) — mode tetikleme koşulları ve sabitler.

## Kritik Kod Yüzeyi

- `compute_xn(...)` fonksiyon imzası ve dönüş alan adları (`xn_expected`, `raw_xn_expected`, `ringing_load_expected`, `abandon_penalty_expected`, `mode`, `notes`).
- `apply_formula(df, cf_override=None, as_override=None)` parametreleri ve eklediği kolonlar.
- Mode adları: `bootstrap`, `fallback_zero_talk`, `fallback_nan`, `safety_floor`, `normal`.
- Sabitler: `DEFAULT_CORRECTION_FACTOR`, `DEFAULT_ABANDON_SENSITIVITY`, `REQUIRED_WINDOW_SIZE`, `WARM_UP_CALL_THRESHOLD`.
- Backward compatibility: `ringing_count` fallback'i.

## Parity Contract Kontrolü

Değişiklik yapmadan önce şu sorulara cevap ver:

- Java tarafında aynı mantık değişti mi? (Java implementasyonu kaynak referansı verilmeli.)
- Mode tetikleme koşulu Java tarafıyla bire bir aynı mı?
- Sabit değerler aynı mı? (`0.5`, `0.0`, `10`, `400`)
- NaN/Inf yakalama davranışı aynı mı?
- `ringing_count` legacy fallback'i Java tarafında hâlâ var mı?

Cevaplardan biri "hayır" ise plan dosyasında Java tarafına başvurulması gerekiyor; aksi takdirde değişiklik yapılmaz.

## Test Stratejisi

- Her mode için ayrı test dosyası: `tests/formula/test_<mode>_characterization.py`.
- Sabit fixture'larla numerik beklenti kilitlenir.
- Float karşılaştırmalar `pytest.approx` veya `np.isclose` ile yapılır.
- `compute_xn` ve `apply_formula` aynı girdi için aynı çıktı üretir; vectorized ve scalar test paralel tutulur.
- Validator entegrasyon testi: `xn_expected` ile gerçek `xn` arasındaki tolerans kontrolü.

## Arama Kalıpları

- Mode adı kullanımı: `\bmode\b`, `'bootstrap'`, `'normal'`, `'fallback_'`.
- Sabit kullanımı: `DEFAULT_CORRECTION_FACTOR`, `DEFAULT_ABANDON_SENSITIVITY`, `REQUIRED_WINDOW_SIZE`, `WARM_UP_CALL_THRESHOLD`.
- Tüketici noktalar: `apply_formula`, `xn_expected`, `mode` kolonu kullanan UI sekmeleri ve anomaly detector.

## Raporlama

- Değişen davranış için Java referansı (sınıf/method ismi).
- Mode/sabit etkisi.
- Karakterizasyon test sonuçları.
- Validator tolerans drift'i raporu.
- Üretimle parity riski varsa açık not.
