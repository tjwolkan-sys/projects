/* ============================================================================
   FEAT-318332 - Musteri Bazli DNC (Customer-Scope) - CANLI (PROD) DDL
   Guncel model: "customer_no <-> phone AYRI TABLO" (10.07.2026 refactor sonrasi)
   ----------------------------------------------------------------------------
   Hedef: MS SQL Server. Idempotent'tir (tekrar kosulabilir).
   Prod'da bu alanlarin HICBIRI yok => yalniz ADDITIVE; pd_dnc_pn'e DOKUNULMAZ
   (dnc_type/customer_no prod'a hic girmedi; yeni model bunlari kullanmaz,
   pd_dnc_pn saf telefon block-list olarak kalir).

   >>> CALISTIRMA SIRASI <<<
     1) BOLUM 1 (PD ana DB)  -> YENI KOD DEPLOY'UNDAN ONCE.
     2) Yeni kodu deploy et.
     3) BOLUM 2 (kampanya data DB) -> feature'i KULLANACAK kampanyalar icin
        (3.parti import customer_no/date_entered doldurur).

   Not: test ortaminda uygulanan marker temizligi / dnc_type-customer_no kolon
   DROP / reset adimlari PROD icin GECERSIZDIR (prod temiz baslar).
   ============================================================================ */

SET QUOTED_IDENTIFIER ON;
GO

/* ============================================================================
   BOLUM 1 - PD ANA DB (pd_* tablolari)  [DEPLOY ONKOSULU]
   ============================================================================ */

/* ---- 1.1 pd_dnc_customer: customer_no <-> phone iliski tablosu (YENI) ------ */
IF OBJECT_ID('dbo.pd_dnc_customer', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.pd_dnc_customer (
        id           BIGINT IDENTITY(1,1) NOT NULL
                         CONSTRAINT PK_pd_dnc_customer PRIMARY KEY,
        dnc_list_id  BIGINT        NOT NULL,
        customer_no  NVARCHAR(255) NOT NULL,
        phone_number NVARCHAR(255) NOT NULL,
        reason       INT           NULL,
        enabled      BIT           NOT NULL CONSTRAINT DF_pd_dnc_customer_enabled DEFAULT 1,
        deleted      BIT           NOT NULL CONSTRAINT DF_pd_dnc_customer_deleted DEFAULT 0,
        create_date  DATETIME      NULL,
        create_user  BIGINT        NULL,
        update_date  DATETIME      NULL,
        update_user  BIGINT        NULL,
        CONSTRAINT FK_pd_dnc_customer_list FOREIGN KEY (dnc_list_id)
            REFERENCES dbo.pd_dnc_list(id)
    );
    CREATE UNIQUE INDEX UX_pd_dnc_customer_list_cust_phone
        ON dbo.pd_dnc_customer (dnc_list_id, customer_no, phone_number) WHERE deleted = 0;
    CREATE INDEX IX_pd_dnc_customer_list_cust
        ON dbo.pd_dnc_customer (dnc_list_id, customer_no);
    PRINT 'pd_dnc_customer olusturuldu.';
END
GO

/* ---- 1.1b pd_dnc_customer: BaseEntity update_* kolonlari (idempotent) ------
   BaseEntity update_date + update_user'i persistent map eder; Hibernate'in
   statik INSERT'i (@DynamicInsert yok) bu kolonlari da yazar. Eksik olurlarsa
   INSERT "Invalid column name 'update_date'" ile patlar => pd_dnc_customer
   HIC dolmaz (pd_dnc_pn once yazildigi icin dolu gorunur, iliski tablosu bos
   kalir). Tablo daha once update_* olmadan olusturulmus olabilecegi icin
   ayrica idempotent eklenir. */
IF COL_LENGTH('dbo.pd_dnc_customer', 'update_date') IS NULL
BEGIN
    ALTER TABLE dbo.pd_dnc_customer ADD update_date DATETIME NULL;
    PRINT 'pd_dnc_customer.update_date eklendi.';
END
GO
IF COL_LENGTH('dbo.pd_dnc_customer', 'update_user') IS NULL
BEGIN
    ALTER TABLE dbo.pd_dnc_customer ADD update_user BIGINT NULL;
    PRINT 'pd_dnc_customer.update_user eklendi.';
END
GO

/* ---- 1.2 pd_campaign: kolon mapping alanlari (Campaign entity map eder) ---- */
IF COL_LENGTH('dbo.pd_campaign', 'col_result_customer_no') IS NULL
BEGIN
    ALTER TABLE dbo.pd_campaign ADD col_result_customer_no NVARCHAR(255) NULL;
    PRINT 'pd_campaign.col_result_customer_no eklendi.';
END
GO
IF COL_LENGTH('dbo.pd_campaign', 'col_date_entered') IS NULL
BEGIN
    ALTER TABLE dbo.pd_campaign ADD col_date_entered NVARCHAR(255) NULL;
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
    CREATE NONCLUSTERED INDEX IX_pd_dnc_sync_log_campaign_status
        ON dbo.pd_dnc_sync_log (campaign_id, status) INCLUDE (max_date_entered);
    PRINT 'pd_dnc_sync_log olusturuldu.';
END
GO

-- >>> pd_dnc_pn'e KOLON/INDEX EKLENMEZ. Saf telefon block-list olarak kalir. <<<

/* ============================================================================
   BOLUM 2 - KAMPANYA DATA TABLOLARI (kampanya-bazli / opsiyonel)
   ----------------------------------------------------------------------------
   Kampanya DB'sinde (PD DB'sinden farkli sunucuda olabilir) calistirilir.
   Yalnizca customer-scope DNC ve/veya delta-sync KULLANACAK kampanyalarin data
   tablolari icin gereklidir; kolonlari 3.parti import doldurur.
   NOTLAR:
   - Once EXEC yerine PRINT @sql ile uretilen ifadeleri gozden gecirin.
   - Kolon adlari kampanya formundaki mapping'lerle ("Customer No" ve
     "Veri Giris Tarihi") birebir eslesmelidir; toplu script standart adlari
     (customer_no / date_entered) kullanir.
   - Ayrik source/result kurulumunda date_entered SOURCE tabloda beklenir
     (delta-sync src tarafindan okur) -> o kampanyalarin SOURCE tablosuna
     kolon/index ELLE eklenmelidir. Source=result tek tablo icin bu script yeterli.
   - complete_date iceren kampanya-DISI tablolar varsa WHERE'e haric tutma ekleyin.
   - Buyuk tablolarda index olusturma uzun surer; mesai disi calistirin.
   ============================================================================ */

/* ---- 2.1 customer_no kolonu ---------------------------------------------- */
DECLARE @sql NVARCHAR(MAX) = '';
SELECT @sql = @sql + 'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + '.' + QUOTENAME(t.name)
            + ' ADD customer_no NVARCHAR(255) NULL;' + CHAR(13)
FROM sys.tables t JOIN sys.columns c ON t.object_id = c.object_id
WHERE c.name = 'complete_date'
  AND t.object_id NOT IN (SELECT object_id FROM sys.columns WHERE name = 'customer_no');
-- PRINT @sql;
EXEC sp_executesql @sql;
GO

/* ---- 2.2 date_entered kolonu --------------------------------------------- */
DECLARE @sql NVARCHAR(MAX) = '';
SELECT @sql = @sql + 'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + '.' + QUOTENAME(t.name)
            + ' ADD date_entered DATETIME NULL;' + CHAR(13)
FROM sys.tables t JOIN sys.columns c ON t.object_id = c.object_id
WHERE c.name = 'complete_date'
  AND t.object_id NOT IN (SELECT object_id FROM sys.columns WHERE name = 'date_entered');
-- PRINT @sql;
EXEC sp_executesql @sql;
GO

/* ---- 2.3 customer_no index'i --------------------------------------------- */
DECLARE @sql NVARCHAR(MAX) = '';
SELECT @sql = @sql + 'CREATE NONCLUSTERED INDEX ' + QUOTENAME('IX_' + t.name + '_customer_no')
            + ' ON ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + '.' + QUOTENAME(t.name) + ' (customer_no);' + CHAR(13)
FROM sys.tables t JOIN sys.columns c ON t.object_id = c.object_id AND c.name = 'customer_no'
WHERE EXISTS (SELECT 1 FROM sys.columns cc WHERE cc.object_id = t.object_id AND cc.name = 'complete_date')
  AND NOT EXISTS (SELECT 1 FROM sys.indexes i WHERE i.object_id = t.object_id AND i.name = 'IX_' + t.name + '_customer_no');
-- PRINT @sql;
EXEC sp_executesql @sql;
GO

/* ---- 2.4 date_entered index'i -------------------------------------------- */
DECLARE @sql NVARCHAR(MAX) = '';
SELECT @sql = @sql + 'CREATE NONCLUSTERED INDEX ' + QUOTENAME('IX_' + t.name + '_date_entered')
            + ' ON ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + '.' + QUOTENAME(t.name) + ' (date_entered);' + CHAR(13)
FROM sys.tables t JOIN sys.columns c ON t.object_id = c.object_id AND c.name = 'date_entered'
WHERE EXISTS (SELECT 1 FROM sys.columns cc WHERE cc.object_id = t.object_id AND cc.name = 'complete_date')
  AND NOT EXISTS (SELECT 1 FROM sys.indexes i WHERE i.object_id = t.object_id AND i.name = 'IX_' + t.name + '_date_entered');
-- PRINT @sql;
EXEC sp_executesql @sql;
GO

/* ============================================================================
   GERI ALMA (gerekirse; hepsi additive, guvenle birakilabilir)
   ----------------------------------------------------------------------------
   DROP TABLE dbo.pd_dnc_customer;
   DROP TABLE dbo.pd_dnc_sync_log;
   ALTER TABLE dbo.pd_campaign DROP COLUMN col_result_customer_no, col_date_entered;
   -- pd_dnc_pn'e dokunulmadigi icin geri alinacak bir sey yoktur.
   ============================================================================ */
