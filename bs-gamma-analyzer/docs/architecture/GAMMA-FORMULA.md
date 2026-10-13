# Gamma Formülü

`core/formula.py`, Bulut Santral predictive dialer'ın Java tarafındaki `PredictiveGammaCallCalculator` sınıfının davranışsal Python kopyasıdır. Bu doküman, formülün hangi girdilerle hangi çıktıyı ürettiğini, mode geçişlerini ve sabitlerin hangi rolü taşıdığını tarif eder.

## Ana Kavramlar

- **`xn`**: Gerçek dialer tarafından bir sonraki tick'te yapılan çağrı sayısı.
- **`xn_expected`**: Python tarafında formül uygulanarak hesaplanan beklenen çağrı sayısı.
- **`raw_xn`**: Ringing yükü çıkarılmadan önceki ham hesap.
- **`ringing_load`**: Bekleyen call back / retry / fresh çağrılarının ağırlıklı toplamı.
- **`abandon_penalty`**: Abandon rate üzerinden hesaplanan baskılayıcı katsayı.
- **`mode`**: Hangi hesaplama dalına girildiğini gösteren string (`bootstrap`, `fallback_zero_talk`, `fallback_nan`, `safety_floor`, `normal`).

## Sabitler

- `DEFAULT_CORRECTION_FACTOR = 0.5`: `inCallAgents / avgTalk * avgRing` çarpanına uygulanan düzeltme.
- `DEFAULT_ABANDON_SENSITIVITY = 0.0`: Abandon penalty üzerindeki ağırlık. UI üzerinden override edilebilir.
- `REQUIRED_WINDOW_SIZE = 10`: Bootstrap eşiği. `successfulWindow < 10` olduğunda bootstrap moduna düşer.
- `WARM_UP_CALL_THRESHOLD = 400`: Toplam çağrı sayısı bu eşiğin altındayken correction factor sıfırlanır.

## Formülün Genel Yapısı

```
abandonPenalty = max(0.1, 1.0 - abandonRate * abandonSensitivity)
rawXn = (availableAgents + (inCallAgents / avgTalk) * avgRing * CF) * abandonPenalty
ringingLoad = ringingCbCount * cbAR + ringingRetryCount * retryAR + ringingFreshCount * freshAR
finalXn = max(0.0, rawXn - ringingLoad)
```

Hesabın bütün dalları yukarıdaki temel kalıbı paylaşır; mode geçişleri yalnızca özel durumlarda devreye girer.

## Mode Geçişleri

- `bootstrap`: `successfulWindow < REQUIRED_WINDOW_SIZE`. Erken aşamada güvenli alt sınır uygulanır.
- `fallback_zero_talk`: `avgTalkTime <= 0`. Bölünme hatasını önlemek için 0 döner.
- `fallback_nan`: `rawXn` NaN veya Inf olduğunda fallback değer döner.
- `safety_floor`: Hesaplanan `xn = 0` ama `ringing = 0` ve `inCall = 0` durumunda `availableAgents` taban olarak kullanılır.
- `normal`: Yukarıdaki özel dallara girmeyen olağan hesap.

## Backward Compatibility Notları

- Eski event'lerde `ringing_cb_count`, `ringing_retry_count`, `ringing_fresh_count` üçü de 0 olabilir. Bu durumda kod fallback olarak `ringing_count` alanını kullanır.
- `boost_rate` Java tarafında kaldırılmıştır; Python kodu hâlâ alanı okuyor olabilir ancak hesaplamada ağırlığı yoktur. Yeni geliştirmeler bu alana güvenmemelidir.

## Riskli Alanlar

- **Java parity drift'i**: Java tarafında bir koşul değişirse Python tarafı parity testi olmadan güncellenmemelidir.
- **Float hassasiyeti**: Java/Python float aritmetiği mikro farklar üretir. Karşılaştırma `validator.py` üzerinden tolerans (`TOLERANCE`, `TOLERANCE_PCT`) ile yapılır.
- **NaN/Inf yakalama**: `rawXn` Inf/NaN olduğunda fallback yoksa pipeline aşağı doğru kırılır.
- **Mode adlarının tüketicisi**: Anomaly detector, görselleştirme ve raporlama mode adlarına bakar; isim değişirse downstream'de regression olur.

## Doğrulama Stratejisi

- `core/validator.py` üzerinden gerçek `xn` ile `xn_expected` arasındaki tolerans kontrolü.
- Karakterizasyon testleri `tests/formula/test_formula_<mode>_characterization.py` altında her mode için ayrı sabit girdiler kullanır.
- Regression testi yazıldığında Java tarafından sağlanmış örnek payload kullanılır.

## Change Contract

- Sabit isimleri ve değerleri Java tarafıyla parity'de tutulur.
- Mode adları geçiş notu olmadan değişmez; eklenecek yeni mode listeye eklenir.
- `compute_xn` ve `apply_formula` imzaları geriye dönük korunur; yeni parametre opsiyonel default ile eklenir.
- Hesap dalları değişiyorsa parity testi ve karakterizasyon testi olmadan plan kapatılmaz.
