# Veri Erişimi ve Veritabanı Contract'ı

bs-gamma-analyzer dialer DB'sine yalnız okuma yapan pasif bir tüketicidir. Yazma, schema migration veya transaction yönetimi yoktur.

## Hedef Tablo

- **Tablo**: `predictive_gamma_events`
- **Sahibi**: Bulut Santral Predictive Dialer (Java/Spring tarafı). Bu repo şemayı belirleyemez.
- **Erişim modu**: Read-only.

## Beklenen Kolonlar

`db/queries.py` ve `app.py` aşağıdaki kolonları okur. Liste tam değildir; opsiyonel alanlar mevcut olmadığında akış kırılmaz.

- `campaign_id`, `campaign_name`, `create_date`
- `available_agents_count`, `incall_agents_count`, `total_agents_count`
- `xn`, `raw_xn`
- `ringing_count`, `ringing_cb_count`, `ringing_retry_count`, `ringing_fresh_count`
- `computed_abandon_rate`, `computed_answer_rate`
- `cb_answer_rate`, `retry_answer_rate`, `fresh_answer_rate`
- `avg_talk_time`, `avg_ring_time`
- Opsiyonel: `computed_abandon_penalty`, `call_per_second`, `cache_limit`, `completion_percent`, `campaign_state`, `correction_factor`, vb.

Yeni kolon eklendiğinde `bsg-data-source-loader-contract` ve `bsg-postgres-readonly-contract` skill'leri rehberindedir; mimari doküman aynı iş içinde güncellenir.

## Sorgu Pattern'leri

- `get_campaigns(engine)`: Kampanya listesini ve event sayısını döndürür.
- `get_campaign_data(engine, campaign_id, start_date=None, end_date=None)`: Belirli kampanya için event'leri döndürür.
- `get_all_data(engine, start_date=None, end_date=None)`: Tüm kampanyalara ait event'leri döndürür.

Tüm sorgular SQLAlchemy `text(...)` ile parametre binding'i kullanır. Filtre cümleleri parametre placeholder'ı ile örülür; doğrudan değer eklemesi yapılmaz.

## Bağlantı

- `db/connection.py` `create_engine("postgresql+psycopg2://...")` ile engine kurar.
- `pool_pre_ping=True` ile bağlantı kopukluğu yakalanır.
- Engine `st.session_state["engine"]` içinde tutulur ve oturum boyunca yeniden kullanılır.
- `test_connection(engine)` `SELECT 1` çalıştırır; bağlantı testi sidebar'da kullanıcıya rapor edilir.

## Yazma Yasakları

- `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`, `ALTER` ifadeleri yazılmaz.
- `db/queries.py` veya başka bir modül içinde DDL/DML çalıştırılmaz.
- Schema migration veya tablo oluşturma kodu eklenmez.

## Performans Notları

- Büyük kampanyalarda tarih aralığı kısaltılarak çekim yapılır.
- Tek seferde milyonlarca satır çekmek hem DB hem Streamlit memory için risklidir; `bsg-data-pipeline-performance-guardrails` skill'i hot path uyarılarını verir.
- `read_sql_query` chunked okuma için kullanıma açıktır; ihtiyaç doğduğunda plan dosyasında ele alınır.

## Hata ve Fallback

- DB bağlantısı kurulamazsa kullanıcı sidebar üzerinde bilgilendirilir; uygulama dosya tabanlı kaynaklara fallback yapabilir.
- Engine oluşturma hatası `Exception` ile dışarı bırakılır; çağıran katman (sidebar) `try/except` ile yakalar.

## Change Contract

- Tablo adı, kolon adları ve sorgu cümleleri kontrat olarak korunur.
- Yeni kolon eklenirse mevcut analiz akışları kırılmadan opsiyonel olarak okunur.
- Yeni tablo veya yazma boundary'si önerisi `bsg-postgres-readonly-contract` skill'i ile yeniden değerlendirilir.
