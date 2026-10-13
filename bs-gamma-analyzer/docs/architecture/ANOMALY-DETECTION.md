# Anomali Tespiti

bs-gamma-analyzer, predictive dialer event akışındaki anomalileri iki katmanda tespit eder: `core/anomaly.py` flag tabanlı temel katman ve `core/anomaly_detector.py` kategorize edilmiş ileri katman. İleri katman ana çıktıdır; flag katmanı geriye dönük tüketicilere destek olur.

## Ana Kavramlar

- **`anomaly_flags`**: Flag tabanlı katmanın bir satıra eklediği virgüllü flag listesi.
- **`AnomalyEvent`**: İleri katmanın ürettiği dataclass; kategori, severity, triggered_by metrik haritası ve precursor/recovery DataFrame'lerini içerir.
- **`CategorySummary`**: Aynı kategoriye ait olayların severity dağılımı ve toplu istatistikleri.
- **Severity skalası**: 1=LOW, 2=MEDIUM, 3=HIGH, 4=CRITICAL.

## Flag Tabanlı Katman (`core/anomaly.py`)

Aşağıdaki flag'leri üretir:

- `XN_ZERO_WITH_AGENTS`: `xn = 0` olmasına rağmen `available_agents > 0`.
- `XN_SPIKE`: Z-score > 3 olan outlier `xn`.
- `ZERO_ANSWER_RATE`: Tüm answer rate alanları 0 ama window dolu.
- `XN_WITH_NO_AGENTS`: `xn > 0` ama `available_agents = 0`.
- `RINGING_LOAD_EXCEEDS_RAWXN`: Ringing yükü `raw_xn` değerini aşıyor.
- `INCALL_EXCEEDS_TOTAL`: Veri bütünlüğü hatası.

Flag katmanı `has_anomaly` boolean'ı ve `anomaly_summary(df)` istatistiklerini döner.

## Kategorize Katman (`core/anomaly_detector.py`)

`run_detection(df)` aşağıdaki adımlardan oluşur:

1. **Feature engineering**: rolling z-score, delta (rate-of-change), türetilmiş kolonlar.
2. **Kategori tespiti**: Her kategori kendi tetikleyici metriklerine bakar.
3. **Severity atama**: Eşik değerlerine göre 1–4 arası seviye verilir.
4. **Pencere üretimi**: Her olay için `±CONTEXT_WINDOW` event'lik precursor ve recovery DataFrame'leri.
5. **İstatistik**: Kategori bazında trend ve yoğunluk özetleri.

### Kategoriler

- `ABANDON_SPIKE`: Abandon rate > 3% veya delta > 3%.
- `ANSWER_RATE_DROP`: Answer rate düşüşü > 10% veya z-score > 2.5.
- `BOOST_INSTABILITY`: Boost rate > 5 (legacy; Java'da boost rate kaldırıldığı için artık nadiren tetiklenir).
- `XN_SUPPRESSION`: `raw_xn < ringing_count`.
- `AGENT_SHORTAGE`: `available_agents = 0` veya keskin düşüş.
- `HIGH_UTILIZATION`: Agent utilization ≥ %80.
- `TALK_TIME_SPIKE`: Avg talk time > 2.5σ.
- `MULTI_METRIC`: Aynı event'te 3 ve üzeri kategori tetiklendiğinde compound olay.

### Sabitler

- `ROLLING_WINDOW = 20`: Z-score için kayan pencere uzunluğu.
- `ZSCORE_THRESHOLD = 2.5`: Genel detection eşiği.
- `DELTA_WINDOW = 3`: Rate-of-change için lookback.
- `CONTEXT_WINDOW = 10`: Precursor/recovery pencere uzunluğu.

## Çıktı Şeması

`run_detection(df)` aşağıdaki sözlüğü döner:

- `enriched_df`: Türetilmiş kolonların eklendiği DataFrame.
- `anomaly_events`: `AnomalyEvent` listesi.
- `categories`: `Dict[str, CategorySummary]`.
- `stats`: Genel sayım/yoğunluk metrikleri.

`AnomalyEvent` alanları kontrat olarak korunur: `category`, `severity`, `triggered_by`, `event_index`, `event_row`, `precursor_df`, `recovery_df`.

## Riskli Alanlar

- **Eşik değişikliği**: Sabitler değiştirildiğinde tüm kategoriler için karakterizasyon testi yeniden alınmalıdır.
- **`min_periods` etkisi**: Rolling z-score erken event'lerde gürültülü olabilir; ilk 20 event filtrelenirken false-negative gözlenebilir.
- **Memory yükü**: Her olay için `precursor_df`/`recovery_df` saklanır; binlerce olayda memory hızla artar.
- **Kategori adı bağımlılığı**: LLM analizörü, görselleştirme katmanı ve UI rapor sekmesi kategori adlarına bakar; ad değişirse downstream kırılır.
- **`BOOST_INSTABILITY` legacy'si**: Java tarafında boost rate kaldırılmıştır; kategori boş kalsa da silinmez (geriye dönük rapor görünümü için).

## Doğrulama Stratejisi

- Her kategori için sabit fixture içeren karakterizasyon testi: `tests/anomaly/test_<kategori>_characterization.py`.
- Severity skalası için boundary test'leri.
- Precursor/recovery boyutu için pencere kontratı testi.
- Stats çıktısının kontrat alan adları için contract test.

## Change Contract

- Kategori adları ve severity skalası geçiş notu olmadan değişmez.
- Yeni kategori eklenirken mevcut downstream tüketiciler kontrol edilir.
- Eşik değiştirilirken plan dosyasında etki ve karakterizasyon testleri belirtilir.
- `precursor_df` ve `recovery_df` kontratı geriye dönük korunur; uzunluk değişimi memory etkisi raporu ister.
