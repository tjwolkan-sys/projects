# Mevcut Mimari Dokümanları

Bu klasör, bs-gamma-analyzer'ın mevcut mimarisini yaşayan kod tabanına göre özetler. Amaç yeni geliştiriciye sistemi hızlı anlatmak, ajanlarla yapılacak işleri güvenli boundary'lere oturtmak ve ileride ne tür parçalara ayrılabileceğini görünür kılmaktır.

Bu dokümanlar hedef mimari belgesi değildir. Mevcut davranış koddan çıkarılmıştır; eksik veya yanlış görünen noktalar bulunduğunda ilgili dosya aynı iş içinde güncellenmelidir.

## Okuma Sırası

1. `MIMARI.md`
2. `PRODUCT-SCOPE.md`
3. `TECHNOLOGY-STACK.md`
4. `DEPLOYMENT-AND-RUNTIME.md`
5. `CONFIGURATION-AND-SECRETS.md`
6. `API-AND-UI.md`
7. `DATA-PIPELINE.md`
8. `GAMMA-FORMULA.md`
9. `ANOMALY-DETECTION.md`
10. `LLM-CLAUDE-INTEGRATION.md`
11. `STREAMLIT-UI.md`
12. `DATA-PERSISTENCE-AND-DB.md`
13. `OBSERVABILITY-AND-OPERATIONS.md`
14. `TESTING-AND-SAFE-CHANGE.md`
15. `MIGRATION-BOUNDARIES.md`

## Dosyalar

- `MIMARI.md`: Üst seviye mimari özet ve modül haritası.
- `PRODUCT-SCOPE.md`: Ürünün bu repo içindeki kapsamı ve external boundary'leri.
- `TECHNOLOGY-STACK.md`: Python, Streamlit, pandas, plotly, PostgreSQL ve Anthropic SDK.
- `DEPLOYMENT-AND-RUNTIME.md`: Streamlit yayınlama modeli, başlangıç ve dış bağımlılıklar.
- `CONFIGURATION-AND-SECRETS.md`: Sidebar input, environment variable ve secret yönetimi contract'ı.
- `API-AND-UI.md`: Streamlit sekmeleri, sidebar kontrolleri ve dış erişim noktaları.
- `DATA-PERSISTENCE-AND-DB.md`: `predictive_gamma_events` tablosu, SQLAlchemy engine ve sorgu pattern'leri.
- `OBSERVABILITY-AND-OPERATIONS.md`: Streamlit log, hata gösterimi ve operasyonel boşluklar.
- `TESTING-AND-SAFE-CHANGE.md`: TDD, karakterizasyon testi ve güvenli değişiklik akışı.
- `MIGRATION-BOUNDARIES.md`: İleride ayrılabilecek teknik boundary'ler.
- `GAMMA-FORMULA.md`: `core/formula.py` Gamma formülü ve Java parity'si.
- `ANOMALY-DETECTION.md`: `core/anomaly_detector.py` ve `core/anomaly.py` kategori/severity contract'ı.
- `STREAMLIT-UI.md`: `app.py` sekmeleri, sidebar ve session state contract'ı.
- `DATA-PIPELINE.md`: Veri kaynağı yükleme, kolon normalizasyonu ve analiz akışı.
- `LLM-CLAUDE-INTEGRATION.md`: `core/llm_analyzer.py` Claude entegrasyonu.

## Güncelleme Standardı

- Kodda mimari davranışı etkileyen her değişiklik ilgili dokümanı da günceller.
- Dokümanlar Türkçe karakterlerle ve TDK yazımına uygun yazılır.
- Gerçek IP, şifre, token, anahtar veya müşteri bilgisi dokümana yazılmaz.
- Davranış belirsizse dokümana kesin hüküm yazılmaz; "açık soru" olarak bırakılır.
- İlerideki transformasyon planı `docs/project/MODULAR-APP-SPLIT-ROADMAP.md` dosyasında kalır.
