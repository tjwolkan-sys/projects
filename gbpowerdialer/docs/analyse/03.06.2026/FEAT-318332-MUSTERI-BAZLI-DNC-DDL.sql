/* ============================================================================
   [!] GUNCEL DEGIL - PROD ICIN GECERSIZ (10.07.2026 refactor sonrasi)
   ----------------------------------------------------------------------------
   Bu dosyanin BOLUM 1.1'i (pd_dnc_pn'e dnc_type + customer_no ekleme +
   phone_number nullable + 2 index) 10.07.2026 "customer_no AYRI TABLO"
   refactor'u ile GECERSIZ oldu: bu kolonlar kaldirildi, iliski pd_dnc_customer
   tablosuna tasindi, DNC_TYPE -> DNC_SCOPE rename edildi.
   CANLI/PROD icin GUNCEL DDL:
     docs/change-fix-plan/10.07.2026/FEAT-318332-DNC-PROD-DDL.sql
   Bu dosya yalnizca tarihsel kayit + test-ortami gecmisi icin saklanir.
   ============================================================================ */

/* ============================================================================
   FEAT-318332 - Musteri Bazli DNC (Customer-Scope DNC) DDL Script'i
   ----------------------------------------------------------------------------
   Hedef: MS SQL Server (PD ana DB'si, orn. gbWebPhone_test)
   Idempotent'tir: tekrar kosulabilir; var olan kolon/tablo/index atlanir.

   ONEMLI: BOLUM 1 PD uygulamasinin yeni surumu deploy edilmeden ONCE
   kosulmalidir (Hibernate entity'leri yeni kolonlari map eder; kolonlar
   yoksa uygulama acilisinda/DNC akislarinda SQL hatasi olusur).
   ============================================================================ */

SET QUOTED_IDENTIFIER ON;
GO

/* ============================================================================
   BOLUM 1 - PD ANA DB (pd_* tablolari)  [DEPLOY ONKOSULU]
   ============================================================================ */

/* ---- 1.1 pd_dnc_pn: dnc_type + customer_no + phone_number nullable -------- */

-- 0=PHONE (mevcut tum kayitlar), 1=CUSTOMER (musteri marker'i)
IF COL_LENGTH('dbo.pd_dnc_pn', 'dnc_type') IS NULL
BEGIN
    ALTER TABLE dbo.pd_dnc_pn
        ADD dnc_type INT NOT NULL CONSTRAINT DF_pd_dnc_pn_dnc_type DEFAULT 0;
    PRINT 'pd_dnc_pn.dnc_type eklendi (default 0 = PHONE).';
END
GO

IF COL_LENGTH('dbo.pd_dnc_pn', 'customer_no') IS NULL
BEGIN
    ALTER TABLE dbo.pd_dnc_pn
        ADD customer_no NVARCHAR(255) NULL;
    PRINT 'pd_dnc_pn.customer_no eklendi.';
END
GO

-- CUSTOMER marker kayitlari telefon tasimaz; phone_number nullable yapilir.
-- NOT: phone_number uzerinde mevcut bir index/constraint varsa ALTER COLUMN
-- hata verir - o durumda ilgili index gecici DROP/CREATE edilmelidir.
-- NOT: ALTER COLUMN collation belirtmez; kolon collation'i DB default'undan
-- farkliysa once mevcut collation tespit edilip ifadeye eklenmelidir.
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'pd_dnc_pn'
             AND COLUMN_NAME = 'phone_number' AND IS_NULLABLE = 'NO')
BEGIN
    ALTER TABLE dbo.pd_dnc_pn
        ALTER COLUMN phone_number NVARCHAR(255) NULL;
    PRINT 'pd_dnc_pn.phone_number nullable yapildi.';
END
GO

-- Aktif PHONE revalidation sorgusu icin (dnc_type=PHONE AND phone_number IN ...)
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_pd_dnc_pn_type_phone'
                 AND object_id = OBJECT_ID('dbo.pd_dnc_pn'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_pd_dnc_pn_type_phone
        ON dbo.pd_dnc_pn (dnc_list_id, dnc_type, phone_number)
        WHERE deleted = 0;
    PRINT 'IX_pd_dnc_pn_type_phone olusturuldu.';
END
GO

-- Aktif CUSTOMER marker lookup'i icin (dnc_type=CUSTOMER AND customer_no IN ...)
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_pd_dnc_pn_type_customer'
                 AND object_id = OBJECT_ID('dbo.pd_dnc_pn'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_pd_dnc_pn_type_customer
        ON dbo.pd_dnc_pn (dnc_list_id, dnc_type, customer_no)
        WHERE deleted = 0;
    PRINT 'IX_pd_dnc_pn_type_customer olusturuldu.';
END
GO

/* ---- 1.2 pd_campaign: kolon mapping alanlari ------------------------------ */

-- Call Results tab'indeki opsiyonel "Customer No" kolon mapping'i
IF COL_LENGTH('dbo.pd_campaign', 'col_result_customer_no') IS NULL
BEGIN
    ALTER TABLE dbo.pd_campaign
        ADD col_result_customer_no NVARCHAR(255) NULL;
    PRINT 'pd_campaign.col_result_customer_no eklendi.';
END
GO

-- Delta-sync icin opsiyonel date_entered kolon mapping'i (bossa kampanya
-- delta-sync kapsamina girmez)
IF COL_LENGTH('dbo.pd_campaign', 'col_date_entered') IS NULL
BEGIN
    ALTER TABLE dbo.pd_campaign
        ADD col_date_entered NVARCHAR(255) NULL;
    PRINT 'pd_campaign.col_date_entered eklendi.';
END
GO

/* ---- 1.3 pd_dnc_sync_log: delta-sync watermark/izleme tablosu ------------- */

IF OBJECT_ID('dbo.pd_dnc_sync_log', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.pd_dnc_sync_log (
        id               BIGINT IDENTITY(1,1) NOT NULL
                             CONSTRAINT PK_pd_dnc_sync_log PRIMARY KEY,
        campaign_id      BIGINT        NOT NULL,
        max_date_entered DATETIME      NULL,   -- son BASARILI pencerenin MAX(date_entered) watermark'i
        started_at       DATETIME2     NOT NULL,
        finished_at      DATETIME2     NULL,
        status           NVARCHAR(50)  NOT NULL, -- SUCCESS / FAILED
        rows_blocked     INT           NULL,
        error_message    NVARCHAR(500) NULL
    );
    PRINT 'pd_dnc_sync_log olusturuldu.';
END
GO

-- Watermark okuma sorgusu icin:
-- SELECT MAX(max_date_entered) WHERE campaign_id = ? AND status = 'SUCCESS'
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_pd_dnc_sync_log_campaign_status'
                 AND object_id = OBJECT_ID('dbo.pd_dnc_sync_log'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_pd_dnc_sync_log_campaign_status
        ON dbo.pd_dnc_sync_log (campaign_id, status)
        INCLUDE (max_date_entered);
    PRINT 'IX_pd_dnc_sync_log_campaign_status olusturuldu.';
END
GO

/* ============================================================================
   BOLUM 2 - KAMPANYA DATA TABLOLARI (TOPLU - ekip pattern'i)
   ----------------------------------------------------------------------------
   Kampanya DB'sinde calistirilir (PD DB'sinden farkli sunucuda olabilir).
   Ekip standardina uygun olarak "complete_date kolonu olan tablo = kampanya
   data tablosudur" ayrimiyla TUM kampanya tablolarina toplu uygulanir;
   kolonu/index'i zaten olan tablolar atlanir (idempotent).

   NOTLAR:
   - Once EXEC satirini yorumlayip PRINT @sql ile uretilen ifadeleri gozden
     gecirmeniz onerilir.
   - Kolon adlari serbesttir; kampanya formundaki mapping'lerle ("Customer
     No" ve date_entered mapping'i) birebir eslesmelidir. Toplu script
     standart adlari (customer_no / date_entered) kullanir.
   - date_entered v1'de DATETIME tipindedir ve import sureci tarafindan
     yukleme aninda doldurulmalidir.
   - DIKKAT (ayrik source/result kurulumu): complete_date RESULT tablosunda
     bulunur. Source ve result tablolari AYRI olan kampanyalarda date_entered
     SOURCE tabloda beklenir (PD delta-sync'i src tarafindan okur); o
     kampanyalarin source tablolarina kolon/index ELLE eklenmelidir.
     Source=result tek tablo (yaygin kurulum) icin bu script yeterlidir.
   - complete_date kolonu iceren uygulama-ici (kampanya olmayan) tablolar
     varsa WHERE'e harici tutma kosulu ekleyin.
   - Buyuk tablolarda index olusturma uzun surebilir; mesai disi calistirin.
   - PD'nin kendi olusturdugu YENI default source tablolarina customer_no
     uygulama tarafindan otomatik eklenir; bu script mevcut tablolar icindir.
   ============================================================================ */

/* ---- 2.1 customer_no kolonu (3. parti import dolduracak) ------------------ */
DECLARE @sql NVARCHAR(MAX) = '';

SELECT
  @sql = @sql + 'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + '.' + QUOTENAME(t.name)
              + ' ADD customer_no NVARCHAR(255) NULL;' + CHAR(13)
FROM
  sys.tables t
  JOIN sys.columns c ON t.object_id = c.object_id
WHERE
  c.name = 'complete_date'
  AND t.object_id NOT IN (
    SELECT object_id
    FROM sys.columns
    WHERE name = 'customer_no'
  );

-- PRINT @sql;
EXEC sp_executesql @sql;
GO

/* ---- 2.2 date_entered kolonu (import yukleme aninda dolduracak) ------------ */
DECLARE @sql NVARCHAR(MAX) = '';

SELECT
  @sql = @sql + 'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + '.' + QUOTENAME(t.name)
              + ' ADD date_entered DATETIME NULL;' + CHAR(13)
FROM
  sys.tables t
  JOIN sys.columns c ON t.object_id = c.object_id
WHERE
  c.name = 'complete_date'
  AND t.object_id NOT IN (
    SELECT object_id
    FROM sys.columns
    WHERE name = 'date_entered'
  );

-- PRINT @sql;
EXEC sp_executesql @sql;
GO

/* ---- 2.3 customer_no index'i (expansion SELECT'i + sibling UPDATE icin) ---- */
DECLARE @sql NVARCHAR(MAX) = '';

SELECT
  @sql = @sql + 'CREATE NONCLUSTERED INDEX ' + QUOTENAME('IX_' + t.name + '_customer_no')
              + ' ON ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + '.' + QUOTENAME(t.name)
              + ' (customer_no);' + CHAR(13)
FROM
  sys.tables t
  JOIN sys.columns c ON t.object_id = c.object_id AND c.name = 'customer_no'
WHERE
  EXISTS (SELECT 1 FROM sys.columns cc
          WHERE cc.object_id = t.object_id AND cc.name = 'complete_date')
  AND NOT EXISTS (SELECT 1 FROM sys.indexes i
                  WHERE i.object_id = t.object_id
                    AND i.name = 'IX_' + t.name + '_customer_no');

-- PRINT @sql;
EXEC sp_executesql @sql;
GO

/* ---- 2.4 date_entered index'i (delta-sync MAX probe + pencere taramasi) ---- */
DECLARE @sql NVARCHAR(MAX) = '';

SELECT
  @sql = @sql + 'CREATE NONCLUSTERED INDEX ' + QUOTENAME('IX_' + t.name + '_date_entered')
              + ' ON ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + '.' + QUOTENAME(t.name)
              + ' (date_entered);' + CHAR(13)
FROM
  sys.tables t
  JOIN sys.columns c ON t.object_id = c.object_id AND c.name = 'date_entered'
WHERE
  EXISTS (SELECT 1 FROM sys.columns cc
          WHERE cc.object_id = t.object_id AND cc.name = 'complete_date')
  AND NOT EXISTS (SELECT 1 FROM sys.indexes i
                  WHERE i.object_id = t.object_id
                    AND i.name = 'IX_' + t.name + '_date_entered');

-- PRINT @sql;
EXEC sp_executesql @sql;
GO

/* ============================================================================
   GERI ALMA (gerekirse) - BOLUM 1
   ----------------------------------------------------------------------------
   Uygulamanin ESKI surumu calisirken yeni kolonlar zararsizdir (eski entity
   map etmez); bu yuzden rollback genellikle gerekmez. Tam geri alma icin:

   DROP INDEX IX_pd_dnc_pn_type_customer ON dbo.pd_dnc_pn;
   DROP INDEX IX_pd_dnc_pn_type_phone ON dbo.pd_dnc_pn;
   ALTER TABLE dbo.pd_dnc_pn DROP CONSTRAINT DF_pd_dnc_pn_dnc_type;
   ALTER TABLE dbo.pd_dnc_pn DROP COLUMN dnc_type, customer_no;
   -- phone_number NOT NULL'a geri cekilmeden once CUSTOMER marker kayitlari
   -- silinmis olmalidir:
   --   DELETE FROM dbo.pd_dnc_pn WHERE phone_number IS NULL;
   --   ALTER TABLE dbo.pd_dnc_pn ALTER COLUMN phone_number NVARCHAR(255) NOT NULL;
   ALTER TABLE dbo.pd_campaign DROP COLUMN col_result_customer_no, col_date_entered;
   DROP TABLE dbo.pd_dnc_sync_log;
   ============================================================================ */
