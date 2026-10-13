# Test ve Güvenli Değişiklik Akışı

bs-gamma-analyzer'da değişiklik TDD ile yürütülür. Mevcut repo henüz test içermez; ilk geliştirmelerde test kökü açılır ve karakterizasyon testleriyle mevcut davranış kilitlenir.

## Test Kökü

- **Hedef kök**: `tests/`
- **Domain klasörleri**:
  - `tests/formula/`
  - `tests/validator/`
  - `tests/anomaly/`
  - `tests/relations/`
  - `tests/llm/`
  - `tests/db/`
  - `tests/streamlit/`
  - `tests/data_loader/`
- **Test dosyası adı**: `test_<konu>_<davranış>_<test_tipi>.py`
- **Test tipleri**: `characterization`, `regression`, `contract`, `feature`.

## TDD Akışı

1. Değişecek davranışı tek cümleyle yaz.
2. En yakın test dosyasını bul; yoksa domain klasöründe yenisini aç.
3. Mevcut davranış riskliyse önce karakterizasyon testi ile bugünkü davranışı kilitle.
4. İstenen davranış için başarısız test yaz.
5. Yalnız testi geçirecek kadar üretim kodu değiştir.
6. Hedefli testi çalıştır: `pytest tests/<domain>/test_<konu>.py`.
7. Yeşil olduktan sonra yalnız güvenli ve küçük refactor yap.
8. Refactor sonrası aynı hedefli testi tekrar çalıştır.

## Güvenli Değişiklik Pratikleri

- Java parity'sine bağlı kod (`core/formula.py`) değişiyorsa Java tarafında değişiklik olup olmadığı kontrol edilir.
- Kategori, severity, JSON şeması veya kolon adı kontratları korunur.
- Streamlit `st.session_state` anahtarları için yeni anahtar eklendiğinde dokümana yazılır; eski anahtarlar silinmez.
- `st.cache_data` ve `st.cache_resource` kullanımı eklenirken cache'in ne zaman geçersizleşeceği plan dosyasında belirtilir.

## Test Önceliği

- Gamma formülü mode geçişleri ve numerik parity.
- Anomaly kategori sınırları, severity skalası ve precursor/recovery şeması.
- Validator tolerans davranışı.
- `db/queries.py` SQL contract'ı (mock engine veya in-memory SQLite gibi izolasyonla).
- Veri kaynağı yükleyici kolon normalizasyonu.
- Claude analizi JSON parse ve hata yolu.
- Streamlit kontrolleri için pure analiz fonksiyonu izolasyonu.

## Test Yazılamayan Durumlar

- Tam Streamlit context gerektiren etkileşimler için testin yerine `streamlit.testing.v1` kullanılır veya pure logic ayrılır.
- Anthropic API çağrısı testte gerçek API'ye gitmez; mock response veya sabit fixture kullanılır.
- DB testleri gerçek üretim DB'sine bağlanmaz; mock engine veya yerel test DB'si tercih edilir.

## Doğrulama Komutları

- Hedefli test: `pytest tests/<domain>/test_<konu>.py -k <pattern>`
- Modül testi: `pytest tests/<domain>`
- Tam test: `pytest`
- Coverage gerekirse: `pytest --cov=core --cov=db`

## Change Contract

- Yeni test eklenirken mevcut testlerin pas etmediği doğrulanır.
- Test silmek `test` etiketli ayrı plan ile yürür ve gerekçe yazılır.
- Karakterizasyon testleri silinmez; davranış değiştiğinde regression veya feature testi olarak yeniden yazılır.
