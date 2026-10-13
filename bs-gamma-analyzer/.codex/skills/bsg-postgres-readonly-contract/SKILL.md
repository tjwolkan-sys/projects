---
name: bsg-postgres-readonly-contract
description: bs-gamma-analyzer'da `db/connection.py` ve `db/queries.py` üzerinden `predictive_gamma_events` tablosuna read-only erişim contract'ını koruyarak değişiklik yapmak için. SQLAlchemy engine, parametre binding, tablo şeması ve okuma sorguları üzerinde değişiklikte kullan.
---

# bsg-postgres-readonly-contract

## Amaç

Bu repo dialer DB'sine yazma yapmaz. `db/queries.py` yalnız okuma sorguları içerir. Tablo adı, kolon adları, filtre parametreleri ve SQL string contract'ı bu sınırı korumak için takip edilir.

## Birlikte Kullanılacak Skill'ler

- `bsg-tdd-workflow`: SQL contract testleri.
- `bsg-characterization-tests`: Mevcut sorgu davranışının kilidi.
- `bsg-data-pipeline-performance-guardrails`: Filtresiz çekim ve büyük dataset riski.

## Önce Oku

- `db/connection.py`: `get_engine`, `test_connection`.
- `db/queries.py`: `get_campaigns`, `get_campaign_data`, `get_all_data`.
- `docs/architecture/DATA-PERSISTENCE-AND-DB.md`: Şema beklentisi.
- Sorguları çağıran yerler: `app.py` sidebar veri yükleme akışı.

## Kritik Kod Yüzeyi

- Tablo adı: `predictive_gamma_events`.
- Kolon adları: bkz. `DATA-PERSISTENCE-AND-DB.md`.
- SQLAlchemy URL şeması: `postgresql+psycopg2://...`.
- `text(...)` parametre binding'i ve SQL string'i.
- `pool_pre_ping=True` bağlantı sağlık kontrolü.
- `read_sql_query` ile pandas DataFrame dönüşü.

## Yazma Yasakları

- `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`, `ALTER`, `CREATE` ifadeleri yazılmaz.
- Schema migration kodu eklenmez.
- DDL/DML çalıştırılması yalnız test fixture'larında, mock engine ile yapılır.

## Contract Kontrolü

- Tablo veya kolon adı değişiyor mu?
- Yeni filtre parametresi ekleniyorsa SQL injection güvenliği parametre binding ile sağlanıyor mu?
- F-string ile SQL örme yerine `:param` placeholder kullanılıyor mu?
- Yeni sorgu eklenince `read_sql_query` chunked seçeneği veya kolon listesi belirlendi mi?
- Bağlantı pooling ayarı değiştiriliyor mu?

## Test Stratejisi

- Mock SQLAlchemy engine ile contract testi: SQL string ve parametre haritası kontrolü.
- Filtre kombinasyonları için boundary testleri (`start_date`, `end_date`, sadece biri, ikisi birden, hiçbiri).
- Kolon listesinde eksik kolon olduğunda DataFrame'in graceful degrade ettiği test.
- `test_connection` fonksiyonu için `SELECT 1` sonucu testi.
- Üretim DB'sine bağlanılmaz; integration testi opsiyonel ve `tests/db/integration/` altında ayrılır.

## Performans Notları

- Büyük tablolarda kampanya/tarih filtresi olmadan çekim yapılmaz.
- Çok satırlı çekimlerde kolon listesi sınırlanır; `SELECT *` yerine ihtiyaç duyulan kolonlar yazılır.
- `chunksize` ile chunked okuma ihtiyaç doğduğunda plan dosyasında ele alınır.

## Arama Kalıpları

- Tablo adı: `predictive_gamma_events`.
- SQL string: `text(`, `read_sql_query`.
- Engine kullanımı: `get_engine`, `st.session_state["engine"]`.
- Kolon adı: `available_agents_count`, `xn`, `raw_xn`, `ringing_*`, `*_answer_rate`.

## Raporlama

- Hangi sorgu/kolon/filtre değişti.
- Mevcut UI sekmelerine etkisi.
- Test sonucu.
- Yazma yasağı korunuyor mu doğrulaması.
- Performans riski (filtresiz çekim, kolon eksikliği) raporu.
