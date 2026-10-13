-- ============================================================================
-- FEAT-GLB90114534-CDR-RAPOR-EKRANI  |  DBA teslimatı (MS SQL Server)
-- Amaç : /cdr/list ve /cdr/export sorgularının pd_cdr üzerinde
--        (campaign_id, call_start_time) index seek ile çalışması.
-- Sıra : 1) Test ortamı  2) Üretim, yalnız bakım penceresinde veya ONLINE = ON ile.
-- Not  : pd_cdr çağrı hot path'inde yazılır; ONLINE olmayan index oluşturma
--        yazımları bloklar. Script idempotenttir.
-- ============================================================================

-- 0) Ortam kontrolleri (yalnız okuma) --------------------------------------

-- 0a) Edition: ONLINE index yalnız Enterprise / Developer / Azure SQL'de desteklenir.
SELECT SERVERPROPERTY('Edition') AS edition, SERVERPROPERTY('ProductVersion') AS product_version;

-- 0b) RCSI ve snapshot isolation durumu (uygulama NOLOCK kullanır; bilgi amaçlı).
SELECT name,
       is_read_committed_snapshot_on,
       snapshot_isolation_state_desc
FROM sys.databases
WHERE name = DB_NAME();

-- 0c) pd_cdr mevcut index'leri ve anahtar kolonları.
SELECT i.name            AS index_name,
       i.type_desc,
       i.is_unique,
       ic.key_ordinal,
       c.name            AS column_name,
       ic.is_included_column
FROM sys.indexes i
JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns c        ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.object_id = OBJECT_ID('dbo.pd_cdr')
ORDER BY i.name, ic.is_included_column, ic.key_ordinal;

-- 0d) Satır sayısı ve tablo boyutu (index oluşturma süresini kestirmek için).
SELECT SUM(p.rows) AS row_count
FROM sys.partitions p
WHERE p.object_id = OBJECT_ID('dbo.pd_cdr') AND p.index_id IN (0, 1);

-- 1) Index oluşturma (idempotent) ------------------------------------------
-- Dar anahtar: campaign_id hiç değişmez, call_start_time produce anında set edilip
-- güncellenmez. Bakım maliyeti yalnız INSERT'te oluşur.

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'IX_pd_cdr_campaign_start'
      AND object_id = OBJECT_ID('dbo.pd_cdr')
)
BEGIN
    -- Enterprise / Developer / Azure SQL:
    CREATE NONCLUSTERED INDEX IX_pd_cdr_campaign_start
        ON dbo.pd_cdr (campaign_id, call_start_time)
        WITH (ONLINE = ON, SORT_IN_TEMPDB = ON);

    -- Standard / Express (ONLINE desteklenmez) — yalnız bakım penceresinde:
    -- CREATE NONCLUSTERED INDEX IX_pd_cdr_campaign_start
    --     ON dbo.pd_cdr (campaign_id, call_start_time)
    --     WITH (ONLINE = OFF, SORT_IN_TEMPDB = ON);
END;

-- 2) İstatistik tazeleme (opsiyonel, bakım penceresinde) -------------------
-- UPDATE STATISTICS dbo.pd_cdr IX_pd_cdr_campaign_start WITH FULLSCAN;

-- 3) Doğrulama: sayfa sorgusu index seek ile mi çalışıyor? ------------------
-- Parametreleri test ortamındaki gerçek bir kampanya id'si ve tarih aralığıyla doldurun.
-- Beklenen: "Index Seek (IX_pd_cdr_campaign_start)" + Key Lookup; Clustered Index Scan olmamalı.
/*
SET STATISTICS IO ON;
SET STATISTICS TIME ON;

DECLARE @campaignId BIGINT = <KAMPANYA_ID>;
DECLARE @from DATETIME = '<YYYY-MM-DD 00:00:00>';
DECLARE @till DATETIME = '<YYYY-MM-DD 00:00:00>';

SELECT c.id, c.interaction_id, c.contact_number, c.call_start_time, c.call_end_time, c.telephony_rc
FROM dbo.pd_cdr c WITH (NOLOCK)
WHERE c.campaign_id = @campaignId
  AND c.call_start_time >= @from
  AND c.call_start_time <  @till
ORDER BY c.call_start_time DESC, c.id DESC
OFFSET 0 ROWS FETCH NEXT 100 ROWS ONLY;

-- Sınırlı sayım (pencere sınırı 10.000):
SELECT COUNT(*) AS bounded_count
FROM (
    SELECT TOP (10001) 1 AS x
    FROM dbo.pd_cdr c WITH (NOLOCK)
    WHERE c.campaign_id = @campaignId
      AND c.call_start_time >= @from
      AND c.call_start_time <  @till
) t;

SET STATISTICS IO OFF;
SET STATISTICS TIME OFF;
*/

-- 4) Geri alma ---------------------------------------------------------------
-- DROP INDEX IX_pd_cdr_campaign_start ON dbo.pd_cdr;
