# Arayüz ve Dış Erişim Noktaları

bs-gamma-analyzer'ın dış arayüzü Streamlit tarayıcı UI'sıdır. REST API yayınlamaz, dış servis çağrısı yalnız Anthropic Claude ve PostgreSQL hedefine yapılır.

## Streamlit Sekmeleri

`app.py` sırayla şu sekmeleri render eder. Sekme sırası geriye dönük korunur; yeni sekme sona eklenir.

1. **Genel Bakış**: Toplam event sayısı, kampanya dağılımı, mode breakdown'u, agent dağılımı.
2. **Abandon Analizi**: Abandon rate trendi, penalty hesabı, raw `xn` baskılanması.
3. **Formül Doğrulama**: Gerçek `xn` ile `xn_expected` arasındaki match dağılımı, scatter ve mismatch tablosu.
4. **Grafikler**: `xn` zaman serisi, agent utilization, korelasyon matrisi.
5. **Backtesting**: Sidebar'daki CF/AS parametreleri ile alternatif `xn_expected` simülasyonu.
6. **İleri Analiz**: `core/relations.py` çıktıları (xn/ringing, agent/util, CPS/cache, xn stabilite).
7. **İleri Analiz 2**: Operasyonel metrikler (agent count, call count, window size).
8. **AI Analiz**: Claude tabanlı kök neden raporu (`AnalysisReport` çıktısının render'ı).
9. **Ham Veri**: Zenginleştirilmiş DataFrame'in CSV export'u.

## Sidebar Kontrolleri

Sidebar üzerindeki kontrol adları kontrat olarak korunur. Yeni kontrol sona eklenir.

- Veri kaynağı seçimi (`Excel`, `CSV`, `JSON`, `PostgreSQL`).
- Dosya yükleyici (Excel/CSV/JSON modunda).
- PostgreSQL bağlantı formu (host, port, dbname, user, password).
- Kampanya seçici ve tarih aralığı (DB modunda).
- Anthropic API key input'u.
- Backtesting parametre slider'ları (CF, AS).

## Dış Servis Çağrıları

- **Anthropic Claude**: HTTPS üzerinden `messages.create` çağrısı. Model adı `core/llm_analyzer.py` içinde sabittir; sadece `bsg-llm-claude-contract` skill'i ile değiştirilir.
- **PostgreSQL**: SQLAlchemy + psycopg2 üzerinden TCP. Yalnız read-only sorgular yapılır (bkz. `DATA-PERSISTENCE-AND-DB.md`).

## Yayınlanan REST/HTTP API

- bs-gamma-analyzer kendi REST API'sini yayınlamaz. Streamlit'in iç HTTP/WebSocket trafiği yalnız tarayıcı ile süreç arasındaki UI iletişimidir.

## Hata Akışı

- Veri yükleme hataları Streamlit hata kutusu ile gösterilir; UI çökmemesi için try/except blokları kritik yerlerde tutulur.
- Claude API hataları `AnalysisReport.error` alanına yazılır ve UI'da kullanıcıya gösterilir.
- DB bağlantı hataları sidebar'da bilgi mesajıyla gösterilir; engine `st.session_state` içine yazılmaz.

## Change Contract

- Sekme adları ve sırası geriye dönük korunur; gerekirse yeni sekme sona eklenir.
- Sidebar kontrol adları ve `st.session_state` anahtarları kontrat olarak tutulur.
- Yeni dış servis çağrısı eklemek (yeni LLM, yeni DB, yeni HTTP) plan dosyasında ayrıca gerekçe ve fallback yazılarak yapılır.
