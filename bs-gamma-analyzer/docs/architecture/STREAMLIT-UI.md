# Streamlit UI Contract

`app.py`, bs-gamma-analyzer'ın tek UI giriş noktasıdır. Bu doküman sekme yapısını, sidebar kontrollerini, session state anahtarlarını ve değişiklik kurallarını tarif eder.

## Sayfa Yerleşimi

- Sol panel (sidebar): veri kaynağı seçimi, dosya yükleyici / DB form, kampanya/tarih filtresi, Anthropic API key, backtesting parametreleri.
- Üst alan: yüklenen veri için kısa özet ve uyarılar.
- Ana panel: 9 sekmeden oluşan analiz alanı.

## Sekme Sırası

Sekme sırası geriye dönük korunur. Yeni sekme sona eklenir, mevcut sekmelerin sırası değiştirilmez.

1. **Genel Bakış**
2. **Abandon Analizi**
3. **Formül Doğrulama**
4. **Grafikler**
5. **Backtesting**
6. **İleri Analiz**
7. **İleri Analiz 2**
8. **AI Analiz**
9. **Ham Veri**

## Sidebar Kontrolleri

Sidebar kontrol adları kontrat olarak tutulur. UI metni Türkçe olabilir; ancak iç kullanılan key'ler Python kodunda sabit kalır.

- `data_source_select`: Veri kaynağı dropdown'u (`Excel`, `CSV`, `JSON`, `PostgreSQL`).
- `file_upload`: Excel/CSV/JSON modunda dosya yükleyici.
- `pg_host`, `pg_port`, `pg_db`, `pg_user`, `pg_password`: PostgreSQL bağlantı alanları.
- `campaign_select`, `date_range`: DB modunda filtreler.
- `anthropic_api_key`: AI analizi için input.
- `cf_slider`, `as_slider`: Correction Factor ve Abandon Sensitivity slider'ları.

## Session State Anahtarları

`st.session_state` üzerinde tutulan anahtarlar dokümante edilir; yeni anahtar eklendiğinde bu liste güncellenir.

- `engine`: SQLAlchemy engine'i. DB modunda set edilir.
- `df_raw`: Yüklenen ham DataFrame.
- `df_enriched`: Formula/validator/detector çağrılarından sonra zenginleştirilmiş DataFrame.
- `analysis_reports`: Claude analizinden dönen `AnalysisReport` listesi.

Anahtar adları kontrat olarak korunur. Eski anahtar silinmeden önce UI'daki tüm tüketici noktaları temizlenir.

## Cache Kullanımı

- `st.cache_resource`: PostgreSQL engine'i için kullanılabilir.
- `st.cache_data`: Statik dosya tabanlı yüklemeler için kullanılabilir.
- Cache invalidation politikası eklendiğinde plan dosyasında ve bu dokümanda yazılır.

## Hata ve Bilgi Mesajları

- Veri yükleme hataları `st.error` ile ana panelde gösterilir.
- DB bağlantı testi `st.success` veya `st.error` ile sidebar'da gösterilir.
- Eksik kolon uyarıları `st.warning` ile gösterilir; akış kırılmaz.
- Claude hatası ilgili sekmede `st.error` ile gösterilir; pipeline durmaz.

## Plotly Figürleri

- Plotly figürleri `app.py` içinde inline üretilir; `charts/` klasörü kademeli olarak figür yardımcılarıyla doldurulur.
- Figür konfigürasyonu (layout, axis, color) kontrat olarak değil görsel iyileştirme alanı olarak değerlendirilir.

## Riskli Alanlar

- **Sekme sırası kayması**: Tüketici dokümanlar sekme indeksine bakabilir; sıra değişirse downstream rapor bozulur.
- **Session state çakışması**: Aynı anahtar farklı tipte değer aldığında debug zorlaşır; tip kontratı korunur.
- **Cache stale state'i**: `st.cache_data` ile saklanan DataFrame, yeni yüklemede invalidate edilmezse eski veri gösterilir.
- **Figür sayısı**: Çok sayıda Plotly figürü tarayıcı tarafında performans düşürür.

## Change Contract

- Sekme adları ve sırası geçiş notu olmadan değişmez.
- Sidebar kontrol key'leri kontrat olarak korunur.
- Session state anahtarı silmek `bsg-runtime-state-audit` skill'i ile referansları çıkararak yapılır.
- Yeni sekme veya yeni cache eklendiğinde bu doküman ve `STREAMLIT-UI.md` aynı iş içinde güncellenir.
