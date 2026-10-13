/* =============================================================================================
   CHORE-GLB90114534 | Kampanya tabloları: koddaki zorunlu kolonlara göre eksik kolon DDL'i
   GENERIC SÜRÜM — DB'deki TÜM kampanya tablolarını otomatik tespit eder ve hepsine uygular.
   ---------------------------------------------------------------------------------------------
   Tarih   : 21.09.2026
   Ortam   : MS SQL Server 2012+ (CONCAT / IIF kullanılmaz, yalnız + ve CASE)
   DB      : gbWebPhone_NonTurkcell_Camp (Ziraat kampanya tabloları). Başka DB için USE satırını değiştirin.
   Kapsam  : Aşağıdaki iki sistem kolonuna sahip TÜM kullanıcı tabloları kampanya tablosu sayılır:
               complete_date + is_fetched
             İsteğe bağlı ad filtresi (@tableNameLike) ve hariç listesi Bölüm 0'da.
   Tip     : Bölüm 1-2 yalnız ADDITIVE DDL (ALTER TABLE ADD ... NULL / CREATE INDEX). Veri değiştirmez,
             mevcut kolona dokunmaz, yalnız EKSİK olanı ekler; tekrar çalıştırılabilir (idempotent).
             Bölüm 3 karar gerektiren düzeltmeleri YALNIZ ÜRETİR (PRINT), asla çalıştırmaz.
             Bölüm 4 opsiyonel özellik kolonları (yorumda). Bölüm 5 salt okuma doğrulama raporu.
             EK A Ziraat örneği için tam tablo şablonu; EK B müşterinin SSMS UPDATE şablonu (referans).

   ÇALIŞTIRMA SIRASI (aynı SSMS oturumu, aynı pencere; temp tablolar oturum ömürlüdür)
   ----------------------------------------------------------------------------------
   1) Bölüm 0'da dry_run = 1 bırak. Bölüm 0 → 1 → 2 → 3 → 5'i sırayla çalıştır.
      Çıktı: tespit edilen tablo listesi, üretilen ALTER/CREATE ifadeleri (ÖNİZLEME) ve Bölüm 5 raporu.
   2) Listeyi gözden geçir. Kampanya olmayan bir tablo yakalandıysa Bölüm 0'daki hariç listesine ekle.
   3) Bölüm 0'da dry_run = 0 yap; Bölüm 0 → 1 → 2'yi tekrar çalıştır (uygulama). Bölüm 5 ile doğrula.
   4) Bölüm 3 çıktısındaki ifadeler DBA/geliştirici kararıyla, gerekiyorsa elle çalıştırılır.

   KAYNAK (repo doğrulaması)
   ------------------------
   - Varsayılan result kolonları ve MS SQL tipleri:
       engine/db/CampaignDbOperations.createDefaultResultTable / addDefaultResultColumnsToSourceTable
   - Kampanya start doğrulaması (kolon tipi):
       service/CampaignDataTypeChecker.checkColumnsTypes + util/Constants.RESULT_FIELDS_ALLOWED_TYPES
       -> uyumsuz tipte 'campaign.start.wrong.types' ile start reddedilir.
   - Kampanya start doğrulaması (index):
       service/CampaignDataTypeChecker.checkUniqueKeyIndex -> col_unique_key kolonunda index yoksa
       'campaign.start.missing.index' ile start reddedilir.
   - Kampanya konfigürasyonundan BAĞIMSIZ, sabit adla yazılan kolonlar (Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES):
       callback_type, Customer_Name, Customer_Surname, relation_type, relation_type_text,
       called_group_id, is_disabled  (CampaignDbOperations.updateCampaignListTableRow / insertIntoCampaignResultTable)
       [UpdatedUser], [UpdatedDate]  (CampaignDbOperations.setFilteredByExpireStatus, CampaignResultJobService.getSetSql)
   - cb_history / call_process: fiziksel tabloda varsa kampanya metadata'sında mapping boş olsa bile
       CampaignDataTypeChecker.applyDefaultHistoryColumnMappings start'ta otomatik bağlar. Ek konfigürasyon gerekmez.

   EMNİYET
   -------
   1. Bölüm 1 nullable kolon ekler; default'suz nullable kolon ekleme SQL Server'da metadata işlemidir
      (tablo yeniden yazılmaz). Kısa Sch-M kilidi alınır; mesai dışı ve kampanyalar durdurulmuşken önerilir.
   2. Bölüm 2 index oluşturur; büyük tabloda uzun sürebilir. Kampanyalar DURDURULMUŞKEN ve mesai dışı.
   3. Çalıştırma öncesi ilgili tabloların yedeği/snapshot'ı alınmalıdır.
   4. Üretilen her ifade bağımsız çalıştırılır (TRY/CATCH); bir tablodaki hata diğerlerini durdurmaz.
   ============================================================================================= */

USE [gbWebPhone_NonTurkcell_Camp];
GO
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
GO

/* =============================================================================================
   BÖLÜM 0 — PARAMETRELER, KAMPANYA TABLOSU TESPİTİ ve YARDIMCI ÇALIŞTIRICI
   ============================================================================================= */
IF OBJECT_ID('tempdb..#pd_params') IS NOT NULL DROP TABLE #pd_params;
IF OBJECT_ID('tempdb..#pd_tables') IS NOT NULL DROP TABLE #pd_tables;
IF OBJECT_ID('tempdb..#pd_ddl')    IS NOT NULL DROP TABLE #pd_ddl;
IF OBJECT_ID('tempdb..#pd_run_ddl') IS NOT NULL DROP PROCEDURE #pd_run_ddl;
GO

/* ---- 0.1 Parametreler ---------------------------------------------------------------------- */
CREATE TABLE #pd_params (dry_run BIT NOT NULL);
INSERT INTO #pd_params (dry_run) VALUES (1);        -- 1 = yalnız PRINT (ÖNİZLEME)   0 = EXEC (UYGULA)

DECLARE @tableNameLike NVARCHAR(256) = N'%';        -- örn. N'ZIRAAT%'. Tüm kampanya tabloları için N'%'.

/* ---- 0.2 Kampanya tablosu tespiti ----------------------------------------------------------- */
CREATE TABLE #pd_tables (
    object_id   INT           NOT NULL PRIMARY KEY,
    schema_name SYSNAME       NOT NULL,
    table_name  SYSNAME       NOT NULL,
    full_name   NVARCHAR(600) NOT NULL
);

INSERT INTO #pd_tables (object_id, schema_name, table_name, full_name)
SELECT t.object_id, s.name, t.name, QUOTENAME(s.name) + '.' + QUOTENAME(t.name)
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
WHERE t.is_ms_shipped = 0
  AND t.name LIKE @tableNameLike
  AND EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = 'complete_date')
  AND EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = 'is_fetched')
  AND t.name NOT IN (
        N'ORNEK_HARIC_TUTULACAK_TABLO'               -- kampanya olmayan ama marker kolonları taşıyan tablolar buraya
  );

/* ---- 0.3 Üretilen DDL ifadeleri havuzu ------------------------------------------------------ */
CREATE TABLE #pd_ddl (
    seq       INT IDENTITY(1,1) PRIMARY KEY,
    bolum     NVARCHAR(10)  NOT NULL,
    full_name NVARCHAR(600) NOT NULL,
    stmt      NVARCHAR(MAX) NOT NULL
);

SELECT full_name AS tespit_edilen_kampanya_tablosu FROM #pd_tables ORDER BY full_name;
PRINT 'Tespit edilen kampanya tablosu sayısı: ' + CAST((SELECT COUNT(*) FROM #pd_tables) AS NVARCHAR(10));
IF NOT EXISTS (SELECT 1 FROM #pd_tables)
    PRINT 'UYARI: Kampanya tablosu bulunamadı. USE satırını, @tableNameLike filtresini ve marker kolonlarını kontrol edin.';
GO

/* ---- 0.4 Yardımcı çalıştırıcı: #pd_ddl içindeki ifadeleri PRINT eder veya EXEC eder ----------- */
CREATE PROCEDURE #pd_run_ddl
    @bolum       NVARCHAR(10),
    @force_print BIT = 0            -- 1: dry_run ne olursa olsun yalnız PRINT (Bölüm 3 için)
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @dryRun BIT = (SELECT TOP 1 dry_run FROM #pd_params);
    IF @force_print = 1 SET @dryRun = 1;

    DECLARE @stmt NVARCHAR(MAX), @tbl NVARCHAR(600), @ok INT = 0, @err INT = 0, @cnt INT = 0;

    DECLARE cur CURSOR LOCAL FAST_FORWARD FOR
        SELECT full_name, stmt FROM #pd_ddl WHERE bolum = @bolum ORDER BY seq;
    OPEN cur;
    FETCH NEXT FROM cur INTO @tbl, @stmt;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @cnt += 1;
        IF @dryRun = 1
            PRINT @stmt;
        ELSE
        BEGIN
            BEGIN TRY
                EXEC sp_executesql @stmt;
                SET @ok += 1;
                PRINT 'OK   : ' + @stmt;
            END TRY
            BEGIN CATCH
                SET @err += 1;
                PRINT 'HATA : ' + @stmt + '  -> ' + ERROR_MESSAGE();
            END CATCH
        END
        FETCH NEXT FROM cur INTO @tbl, @stmt;
    END
    CLOSE cur; DEALLOCATE cur;

    IF @cnt = 0
        PRINT 'Bölüm ' + @bolum + ': yapılacak işlem yok (tüm tablolar uyumlu).';
    ELSE IF @dryRun = 1
        PRINT 'Bölüm ' + @bolum + ': ' + CAST(@cnt AS NVARCHAR(10)) + ' ifade üretildi (ÖNİZLEME, çalıştırılmadı).';
    ELSE
        PRINT 'Bölüm ' + @bolum + ': tamamlandı. başarılı=' + CAST(@ok AS NVARCHAR(10)) + ' hata=' + CAST(@err AS NVARCHAR(10));
END
GO


/* =============================================================================================
   BÖLÜM 1 — ZORUNLU EKSİK KOLONLAR (tüm kampanya tablolarına)
   ---------------------------------------------------------------------------------------------
   Kolon listesi kod tarafından sabit adla yazılan veya default result şablonunun parçası olan
   kolonlardır. Her tablo için yalnız EKSİK olan kolonlara ALTER TABLE ADD üretilir.

   cb_history          NVARCHAR(MAX) default result şablonu; callback geçmişi. Tabloda varsa start'ta otomatik bağlanır.
   call_process        NVARCHAR(MAX) default result şablonu; çağrı süreç izi F/R/CB. Tabloda varsa otomatik bağlanır.
   callback_type       NVARCHAR(50)  kod SABİT adla ve enum ADI (string) yazar
                                     (CampaignDbOperations: CALLBACK_TYPE.getValue() + " = '" + call.getCallbackType() + "'");
                                     okuma CallProducer -> CALLBACKTYPE.valueOf(<string>). Tip doğrulaması string ailesi.
                                     Not: createDefaultResultTable INT üretir; kod içi tutarsızlık, string yazım nedeniyle INT KULLANILMAZ.
                                     Kolon eksikse agent callback disposition girildiğinde result UPDATE
                                     "Invalid column name 'callback_type'" ile düşer.
   Customer_Name       NVARCHAR(255) contact anomaly report akışı (WebphoneSubscriber.handleAnomalyReportEntityMessage ->
   Customer_Surname    NVARCHAR(255) insertCallResultToDB) bu 4 kolonu SABİT adla yazar; yoksa o çağrının result yazımı düşer.
   relation_type       INT           RESULT_FIELDS_ALLOWED_TYPES.RELATION_TYPE: BIT/TINYINT/SMALLINT/INTEGER; kod '1' gibi sayısal string yazar.
   relation_type_text  NVARCHAR(255)
   ============================================================================================= */
DECLARE @required TABLE (sira INT, kolon SYSNAME, ddl_tip NVARCHAR(50));
INSERT INTO @required (sira, kolon, ddl_tip) VALUES
    (1, 'cb_history',         'NVARCHAR(MAX)'),
    (2, 'call_process',       'NVARCHAR(MAX)'),
    (3, 'callback_type',      'NVARCHAR(50)'),
    (4, 'Customer_Name',      'NVARCHAR(255)'),
    (5, 'Customer_Surname',   'NVARCHAR(255)'),
    (6, 'relation_type',      'INT'),
    (7, 'relation_type_text', 'NVARCHAR(255)');

DELETE FROM #pd_ddl WHERE bolum = '1';

INSERT INTO #pd_ddl (bolum, full_name, stmt)
SELECT '1', t.full_name,
       'ALTER TABLE ' + t.full_name + ' ADD ' + QUOTENAME(r.kolon) + ' ' + r.ddl_tip + ' NULL;'
FROM #pd_tables t
CROSS JOIN @required r
WHERE NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = r.kolon)
ORDER BY t.full_name, r.sira;

EXEC #pd_run_ddl @bolum = '1';
GO


/* =============================================================================================
   BÖLÜM 2 — UNIQUE KEY INDEX'İ (start doğrulaması: campaign.start.missing.index)
   ---------------------------------------------------------------------------------------------
   CampaignDataTypeChecker.checkUniqueKeyIndex, kampanyanın col_unique_key kolonunda herhangi bir
   index (sys.index_columns, herhangi bir pozisyon) arar; yoksa kampanya BAŞLATILAMAZ.
   Anahtar kolon adayı tablo başına şu öncelikle seçilir: unique_key, yoksa id.
   İkisi de yoksa tablo uyarı olarak listelenir (kampanyada bağlı kolonu elle kontrol edin).
   Büyük tabloda uzun sürer; kampanyalar durdurulmuş ve mesai dışı çalıştırın.
   ============================================================================================= */
DELETE FROM #pd_ddl WHERE bolum = '2';

;WITH aday AS (
    SELECT t.object_id, t.full_name, t.table_name, c.name AS kolon,
           ROW_NUMBER() OVER (PARTITION BY t.object_id
                              ORDER BY CASE c.name WHEN 'unique_key' THEN 1 ELSE 2 END) AS rn
    FROM #pd_tables t
    JOIN sys.columns c ON c.object_id = t.object_id AND c.name IN ('unique_key', 'id')
)
INSERT INTO #pd_ddl (bolum, full_name, stmt)
SELECT '2', a.full_name,
       'CREATE NONCLUSTERED INDEX ' + QUOTENAME('IX_' + a.table_name + '_' + a.kolon)
       + ' ON ' + a.full_name + ' (' + QUOTENAME(a.kolon) + ');'
FROM aday a
WHERE a.rn = 1
  AND NOT EXISTS (
        SELECT 1
        FROM sys.index_columns ic
        JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
        WHERE ic.object_id = a.object_id AND c.name = a.kolon
  )
ORDER BY a.full_name;

/* Anahtar adayı bulunamayan tablolar */
SELECT t.full_name AS anahtar_kolonu_bulunamayan_tablo
FROM #pd_tables t
WHERE NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name IN ('unique_key', 'id'))
ORDER BY t.full_name;

EXEC #pd_run_ddl @bolum = '2';
GO


/* =============================================================================================
   BÖLÜM 3 — KARAR GEREKTİREN UYUMSUZLUKLAR (YALNIZ ÜRETİLİR; ASLA OTOMATİK ÇALIŞTIRILMAZ)
   ---------------------------------------------------------------------------------------------
   Çıktıdaki ifadeler DBA/geliştirici onayıyla, tablo bazında gözden geçirilip elle çalıştırılır.

   3.1 callback_attemps -> callback_attempts (yazım hatası)
       Kodun varsayılan adı callback_attempts (Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_ATTEMPTS);
       kampanyada col_result_callback_attempts ile bağlanır; yalnız ATTEMPT_COUNT reset stratejisi
       (FEAT-321997) kullanılıyorsa gerekir. Alternatif: kolonu bırakıp kampanyada 'callback_attemps' bağlamak.
   3.2 UpdatedDate -> DATETIME
       col_result_update_date kolonu DATETIME/TIMESTAMP olmalıdır (RESULT_FIELDS_ALLOWED_TYPES.UPDATE_DATE);
       FLOAT bağlanırsa start 'campaign.start.wrong.types' verir. Expire akışları ayrıca kolon adını SABİT
       yazar: [UpdatedDate] = GETDATE() (setFilteredByExpireStatus) ve [UpdatedDate] = ? (CampaignResultJobService,
       java.sql.Timestamp -> datetime2). datetime2 -> float dönüşümü SQL Server'da izinli değildir; expire job'ı
       "Operand type clash" ile düşer. Mevcut float değerler SQL Server gün-sayısı semantiğindeyse DATETIME'a
       dönüştürülür; kolon bir index/constraint içindeyse önce kaldırılır.
   3.3 unique_key INT -> BIGINT
       RESULT_FIELDS_ALLOWED_TYPES.UNIQUE_KEY yalnız BIGINT/NUMBER kabul eder; INT tip start'ta
       'campaign.start.wrong.types' üretir. Kolona bağlı index/PK/UNIQUE constraint'ler ALTER COLUMN öncesi
       DROP edilip sonrasında tanımına göre yeniden CREATE edilir (bağımlılar aşağıda listelenir).
       IDENTITY kolonda ve çok büyük tabloda tablo yeniden oluşturma gerekebilir; mesai dışı planlayın.
   ============================================================================================= */
DELETE FROM #pd_ddl WHERE bolum = '3';

/* 3.1 */
INSERT INTO #pd_ddl (bolum, full_name, stmt)
SELECT '3', t.full_name,
       'EXEC sp_rename ''' + t.full_name + '.[callback_attemps]'', ''callback_attempts'', ''COLUMN'';'
       + '   -- 3.1 yazım hatası düzeltmesi'
FROM #pd_tables t
WHERE EXISTS     (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = 'callback_attemps')
  AND NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = 'callback_attempts')
ORDER BY t.full_name;

/* 3.2 */
INSERT INTO #pd_ddl (bolum, full_name, stmt)
SELECT '3', t.full_name,
       'ALTER TABLE ' + t.full_name + ' ALTER COLUMN [UpdatedDate] DATETIME '
       + CASE WHEN c.is_nullable = 1 THEN 'NULL' ELSE 'NOT NULL' END + ';'
       + '   -- 3.2 mevcut tip: ' + ty.name
FROM #pd_tables t
JOIN sys.columns c ON c.object_id = t.object_id AND c.name = 'UpdatedDate'
JOIN sys.types  ty ON ty.user_type_id = c.user_type_id
WHERE ty.name NOT IN ('datetime', 'datetime2', 'smalldatetime')
ORDER BY t.full_name;

/* 3.3 */
;WITH aday AS (
    SELECT t.object_id, t.full_name, c.name AS kolon, c.is_nullable, c.is_identity, ty.name AS tip,
           ROW_NUMBER() OVER (PARTITION BY t.object_id
                              ORDER BY CASE c.name WHEN 'unique_key' THEN 1 ELSE 2 END) AS rn
    FROM #pd_tables t
    JOIN sys.columns c ON c.object_id = t.object_id AND c.name IN ('unique_key', 'id')
    JOIN sys.types  ty ON ty.user_type_id = c.user_type_id
),
dar AS (
    SELECT * FROM aday WHERE rn = 1 AND tip IN ('int', 'smallint', 'tinyint')
)
INSERT INTO #pd_ddl (bolum, full_name, stmt)
SELECT '3', d.full_name, x.stmt
FROM dar d
CROSS APPLY (
    /* bağımlı index / constraint listesi */
    SELECT '-- 3.3 ' + d.full_name + '.' + QUOTENAME(d.kolon) + ' bağımlı index: ' + QUOTENAME(i.name)
           + CASE WHEN i.is_primary_key = 1 THEN ' (PRIMARY KEY constraint)'
                  WHEN i.is_unique_constraint = 1 THEN ' (UNIQUE constraint)'
                  ELSE '' END
           + ' -> ALTER COLUMN öncesi DROP, sonrası tanımına göre yeniden CREATE' AS stmt, 1 AS sira
    FROM sys.indexes i
    JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
    JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
    WHERE i.object_id = d.object_id AND c.name = d.kolon
    UNION ALL
    SELECT 'ALTER TABLE ' + d.full_name + ' ALTER COLUMN ' + QUOTENAME(d.kolon) + ' BIGINT '
           + CASE WHEN d.is_nullable = 1 THEN 'NULL' ELSE 'NOT NULL' END + ';'
           + '   -- 3.3 mevcut tip: ' + d.tip
           + CASE WHEN d.is_identity = 1 THEN ' (IDENTITY kolon; büyük tabloda yeniden oluşturma gerekebilir)' ELSE '' END, 2
) x
ORDER BY d.full_name, x.sira;

EXEC #pd_run_ddl @bolum = '3', @force_print = 1;
GO


/* =============================================================================================
   BÖLÜM 4 — OPSİYONEL ÖZELLİK KOLONLARI (yalnız ilgili özellik kullanılacaksa; YORUMDA)
   ---------------------------------------------------------------------------------------------
   Açmak için ilgili bloğun yorumunu kaldırın; Bölüm 1 ile aynı çalıştırıcıyı kullanır (dry_run'a uyar).
   4.1 Müşteri bazlı DNC / delta-sync (FEAT-318332): customer_no NVARCHAR(255), date_entered DATETIME.
       Mevcut müşteri kolonları (örn. MUSTERINO, DATA_LOAD_DATE) kampanyada col_result_customer_no /
       col_date_entered ile de bağlanabilir; standart ad istenirse aşağıdaki blok kullanılır.
   4.2 Skill bazlı yönlendirme (skillEnabled): specific_skill_name NVARCHAR(255).
   4.3 Telefon numarası bazlı timezone: timezone_offset_1..3 INT (call_from_time_N / call_till_time_N varsa).
   ============================================================================================= */
/*
DECLARE @optional TABLE (sira INT, kolon SYSNAME, ddl_tip NVARCHAR(50));
INSERT INTO @optional (sira, kolon, ddl_tip) VALUES
    (1, 'customer_no',         'NVARCHAR(255)'),   -- 4.1
    (2, 'date_entered',        'DATETIME'),        -- 4.1
    (3, 'specific_skill_name', 'NVARCHAR(255)'),   -- 4.2
    (4, 'timezone_offset_1',   'INT'),             -- 4.3
    (5, 'timezone_offset_2',   'INT'),             -- 4.3
    (6, 'timezone_offset_3',   'INT');             -- 4.3

DELETE FROM #pd_ddl WHERE bolum = '4';
INSERT INTO #pd_ddl (bolum, full_name, stmt)
SELECT '4', t.full_name,
       'ALTER TABLE ' + t.full_name + ' ADD ' + QUOTENAME(o.kolon) + ' ' + o.ddl_tip + ' NULL;'
FROM #pd_tables t
CROSS JOIN @optional o
WHERE NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = o.kolon)
ORDER BY t.full_name, o.sira;
EXEC #pd_run_ddl @bolum = '4';
GO
*/


/* =============================================================================================
   BÖLÜM 5 — DOĞRULAMA RAPORU (salt okuma; öncesi/sonrası çalıştırılabilir)
   ---------------------------------------------------------------------------------------------
   Beklenen kolon listesi ve kabul edilen tip aileleri Constants.RESULT_FIELDS_ALLOWED_TYPES'tan (MS SQL)
   türetilmiştir. Yalnız OK olmayan satırlar listelenir. Bölüm 1-2 sonrası yalnız Bölüm 3'teki bilinçli
   kararlar (callback_attempts, UpdatedDate, unique_key) ve kampanyaya göre değişebilen adlar
   (ListID, unique_key/id) EKSIK / TIP FARKI görünmelidir.
   ============================================================================================= */
;WITH beklenen(kolon, kabul_edilen_tipler, kaynak) AS (
    SELECT * FROM (VALUES
        ('unique_key',            'bigint',                         'col_unique_key (start tip kontrolü yalnız BIGINT; kampanya id kolonunu kullanıyorsa yoksay)'),
        ('start_date',            'float',                          'col_result_start_date'),
        ('answer_date',           'float',                          'col_result_answer_date'),
        ('complete_date',         'float',                          'col_result_complete_date'),
        ('called_number',         'nvarchar,varchar,nchar',         'col_result_called_number'),
        ('telephony_result_code', 'int,bigint',                     'col_result_telephony_rc'),
        ('user_result_code',      'bigint',                         'col_result_user_rc'),
        ('rc_sequence',           'int,bigint',                     'col_result_rc_sequence'),
        ('user_id',               'bigint',                         'col_result_user_id'),
        ('callback_number',       'nvarchar,varchar,nchar',         'col_result_callback_number'),
        ('callback_date',         'float',                          'col_result_callback_date'),
        ('retry_number',          'nvarchar,varchar,nchar',         'col_result_retry_number'),
        ('retry_history',         'nvarchar,varchar,nchar',         'col_result_retry_history'),
        ('cb_history',            'nvarchar,varchar,nchar',         'col_result_cb_history (tabloda varsa otomatik bağlanır)'),
        ('call_process',          'nvarchar,varchar,nchar',         'col_result_call_process (tabloda varsa otomatik bağlanır)'),
        ('retry_date',            'float',                          'col_result_retry_date'),
        ('is_fetched',            'float',                          'col_result_fetched_data_flag (zorunlu)'),
        ('dnc_numbers',           'nvarchar,varchar,nchar',         'col_result_dnc_numbers'),
        ('attempts',              'int,bigint',                     'col_result_attempts (ATTEMPTS ile eşleşir)'),
        ('callback_attempts',     'int,bigint',                     'col_result_callback_attempts (opsiyonel; callback_attemps yazım hatası -> Bölüm 3.1)'),
        ('response_values',       'nvarchar,varchar,nchar',         'col_result_response_values'),
        ('callback_type',         'nvarchar,varchar,nchar',         'SABİT AD (kod yazar)'),
        ('Customer_Name',         'nvarchar,varchar,nchar',         'SABİT AD (anomaly report)'),
        ('Customer_Surname',      'nvarchar,varchar,nchar',         'SABİT AD (anomaly report)'),
        ('relation_type',         'int,bit,tinyint,smallint',       'SABİT AD (anomaly report)'),
        ('relation_type_text',    'nvarchar,varchar,nchar',         'SABİT AD (anomaly report)'),
        ('called_group_id',       'int,bigint',                     'SABİT AD (kod yazar)'),
        ('is_disabled',           'int,bigint',                     'SABİT AD (kod yazar; expire/eliminate)'),
        ('UpdatedUser',           'nvarchar,varchar,bigint,int',    'SABİT AD (expire akışları; kod bigint yazar)'),
        ('UpdatedDate',           'datetime,datetime2,smalldatetime','SABİT AD (expire akışları GETDATE()/Timestamp yazar) -> Bölüm 3.2'),
        ('call_from_time',        'float',                          'col_call_from_time'),
        ('call_till_time',        'float',                          'col_call_to_time'),
        ('timezone_offset',       'int,bigint',                     'col_timezone_offset'),
        ('ListID',                'nvarchar,varchar,nchar',         'Data Manipulation projeksiyonu (opsiyonel)')
    ) v(kolon, kabul_edilen_tipler, kaynak)
)
SELECT
    t.full_name              AS kampanya_tablosu,
    b.kolon                  AS beklenen_kolon,
    b.kabul_edilen_tipler,
    ty.name                  AS mevcut_tip,
    d.durum,
    b.kaynak
FROM #pd_tables t
CROSS JOIN beklenen b
LEFT JOIN sys.columns c ON c.object_id = t.object_id AND c.name = b.kolon
LEFT JOIN sys.types  ty ON ty.user_type_id = c.user_type_id
CROSS APPLY (
    SELECT CASE
             WHEN c.name IS NULL THEN 'EKSIK'
             WHEN ',' + b.kabul_edilen_tipler + ',' LIKE '%,' + ty.name + ',%' THEN 'OK'
             ELSE 'TIP FARKI'
           END AS durum
) d
WHERE d.durum <> 'OK'
ORDER BY t.full_name, CASE d.durum WHEN 'EKSIK' THEN 0 ELSE 1 END, b.kolon;

/* Tablo başına özet */
;WITH beklenen(kolon) AS (
    SELECT * FROM (VALUES
        ('unique_key'), ('start_date'), ('answer_date'), ('complete_date'), ('called_number'),
        ('telephony_result_code'), ('user_result_code'), ('rc_sequence'), ('user_id'), ('callback_number'),
        ('callback_date'), ('retry_number'), ('retry_history'), ('cb_history'), ('call_process'),
        ('retry_date'), ('is_fetched'), ('dnc_numbers'), ('attempts'), ('callback_attempts'),
        ('response_values'), ('callback_type'), ('Customer_Name'), ('Customer_Surname'), ('relation_type'),
        ('relation_type_text'), ('called_group_id'), ('is_disabled'), ('UpdatedUser'), ('UpdatedDate'),
        ('call_from_time'), ('call_till_time'), ('timezone_offset'), ('ListID')
    ) v(kolon)
)
SELECT t.full_name AS kampanya_tablosu,
       SUM(CASE WHEN c.name IS NULL THEN 1 ELSE 0 END) AS eksik_kolon_sayisi,
       COUNT(*)                                        AS beklenen_kolon_sayisi
FROM #pd_tables t
CROSS JOIN beklenen b
LEFT JOIN sys.columns c ON c.object_id = t.object_id AND c.name = b.kolon
GROUP BY t.full_name
ORDER BY eksik_kolon_sayisi DESC, t.full_name;

/* Anahtar kolon index doğrulaması: her tabloda anahtar adayı (unique_key / id) için index görünmeli */
;WITH aday AS (
    SELECT t.object_id, t.full_name, c.name AS kolon,
           ROW_NUMBER() OVER (PARTITION BY t.object_id
                              ORDER BY CASE c.name WHEN 'unique_key' THEN 1 ELSE 2 END) AS rn
    FROM #pd_tables t
    JOIN sys.columns c ON c.object_id = t.object_id AND c.name IN ('unique_key', 'id')
)
SELECT a.full_name AS kampanya_tablosu, a.kolon AS anahtar_kolon,
       STUFF((SELECT ', ' + i.name
              FROM sys.indexes i
              JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
              JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
              WHERE i.object_id = a.object_id AND c.name = a.kolon
              FOR XML PATH('')), 1, 2, '') AS index_ler,
       CASE WHEN EXISTS (SELECT 1
                         FROM sys.index_columns ic
                         JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                         WHERE ic.object_id = a.object_id AND c.name = a.kolon)
            THEN 'OK' ELSE 'INDEX YOK (start reddedilir)' END AS durum
FROM aday a
WHERE a.rn = 1
ORDER BY durum DESC, a.full_name;
GO


/* =============================================================================================
   GERİ ALMA (Bölüm 1-2; hepsi additive, güvenle bırakılabilir)
   ---------------------------------------------------------------------------------------------
   Aşağıdaki üretici, Bölüm 1-2'nin eklediği kolon ve index'ler için DROP ifadeleri üretir (yalnız PRINT).
   Kolonların bu script'ten ÖNCE de var olduğu tablolarda veri kaybı riski vardır; tablo bazında gözden geçirin.

   SELECT 'ALTER TABLE ' + t.full_name + ' DROP COLUMN ' + QUOTENAME(c.name) + ';'
   FROM #pd_tables t JOIN sys.columns c ON c.object_id = t.object_id
   WHERE c.name IN ('cb_history','call_process','callback_type','Customer_Name','Customer_Surname','relation_type','relation_type_text');

   SELECT 'DROP INDEX ' + QUOTENAME(i.name) + ' ON ' + t.full_name + ';'
   FROM #pd_tables t JOIN sys.indexes i ON i.object_id = t.object_id
   WHERE i.name LIKE 'IX[_]' + t.table_name + '[_]unique[_]key' OR i.name LIKE 'IX[_]' + t.table_name + '[_]id';
   ============================================================================================= */


/* =============================================================================================
   EK A — ÖRNEK TAM TABLO ŞABLONU (Ziraat: dbo.ZIRAAT_DINAMIKBANK_TAHSILAT; mevcut kolonlar + eklenen kolonlar)
   ---------------------------------------------------------------------------------------------
   Müşterinin paylaştığı mevcut kolon listesi (ad ve tip) birebir korunmuş, sonuna koddaki zorunlu
   eksik kolonlar eklenmiştir. Tablo ZATEN VARSA bu bölüm hiçbir şey yapmaz (OBJECT_ID guard'ı).
   Yeni ortam/tablo kurulumunda tam şablon olarak kullanılır; tablo adını değiştirerek çoğaltılabilir.
   NOT: Paylaşılan UPDATE şablonunda nullability, PK, IDENTITY ve DEFAULT bilgisi yoktur; tüm
   kolonlar NULL yazıldı. Yeni kurulumda anahtar kolon (unique_key) NOT NULL + IDENTITY/PK olarak
   müşterinin gerçek DDL'ine göre düzenlenmelidir. "bkz. Bölüm 3.x" işaretli kolonlar karar bekler.
   ============================================================================================= */
IF OBJECT_ID('dbo.ZIRAAT_DINAMIKBANK_TAHSILAT', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.ZIRAAT_DINAMIKBANK_TAHSILAT (
        /* ---- Müşteri iş alanları (mevcut) ---- */
        [CAMPAIGN_NAME]            NVARCHAR(255) NULL,
        [ListID]                   NVARCHAR(100) NULL,
        [DATA_LOAD_DATE]           DATETIME      NULL,
        [DATE_STATUS_MODIFIED]     DATETIME      NULL,
        [ELENME_NEDENI]            NVARCHAR(500) NULL,
        [CEP_TEL]                  NVARCHAR(50)  NULL,
        [IS_TEL]                   NVARCHAR(50)  NULL,
        [RESULT_CODE]              NVARCHAR(150) NULL,
        [EV_TEL]                   NVARCHAR(50)  NULL,
        [SUCCESSRESULT]            FLOAT         NULL,
        [SIRANO]                   NVARCHAR(50)  NULL,
        [MUSTERINO]                NVARCHAR(50)  NULL,
        [URUN_KD]                  NVARCHAR(20)  NULL,
        [URUNDEGER]                NVARCHAR(50)  NULL,
        [TAKSIT_TR]                NVARCHAR(30)  NULL,
        [SUBE_KD]                  NVARCHAR(20)  NULL,
        [SUBE_KD_ADI]              NVARCHAR(250) NULL,
        [SUBEIL]                   NVARCHAR(50)  NULL,
        [SUBEILCE]                 NVARCHAR(50)  NULL,
        [TEL1]                     NVARCHAR(20)  NULL,
        [TEL2]                     NVARCHAR(20)  NULL,
        [TEL3]                     NVARCHAR(20)  NULL,
        [TEL4]                     NVARCHAR(20)  NULL,
        [TEL5]                     NVARCHAR(20)  NULL,
        [TEL6]                     NVARCHAR(20)  NULL,
        [TEL7]                     NVARCHAR(20)  NULL,
        [TEL8]                     NVARCHAR(20)  NULL,
        [TEL9]                     NVARCHAR(20)  NULL,
        [TEL10]                    NVARCHAR(20)  NULL,
        [TEL11]                    NVARCHAR(20)  NULL,
        [TEL12]                    NVARCHAR(20)  NULL,
        [TEL13]                    NVARCHAR(20)  NULL,
        [TEL14]                    NVARCHAR(20)  NULL,
        [TEL15]                    NVARCHAR(20)  NULL,
        [EVTEL]                    NVARCHAR(20)  NULL,
        [ISTEL]                    NVARCHAR(20)  NULL,
        [ARANACAK_TRH]             NVARCHAR(30)  NULL,
        [BABAADI]                  NVARCHAR(50)  NULL,
        [DOGUM_TRH]                NVARCHAR(30)  NULL,
        [KAMPANYANO]               NVARCHAR(10)  NULL,
        [KAMPANYA_AD]              NVARCHAR(100) NULL,
        [GERCEKTUZEL_DRM]          NVARCHAR(10)  NULL,
        [BORCTUTAR]                NVARCHAR(50)  NULL,
        [KURALONCELIK]             INT           NULL,
        [KAMPANYAONCELIK]          INT           NULL,
        [MUSTERI_VEKIL_ADSOYAD]    NVARCHAR(150) NULL,
        [VEKILTIP_KD]              NVARCHAR(50)  NULL,
        [FIRMAADI]                 NVARCHAR(250) NULL,
        [ILKGECIKME_TRH]           NVARCHAR(30)  NULL,
        [DATANO]                   NVARCHAR(50)  NULL,
        [ADRESIL]                  NVARCHAR(100) NULL,
        [ADRESILCE]                NVARCHAR(100) NULL,
        [SIRALAMA_SCR]             INT           NULL,
        [GGS]                      INT           NULL,
        [CALLING_PARTY_NUMBER]     NVARCHAR(20)  NULL,
        [ACIKLAMA]                 NVARCHAR(300) NULL,
        [YuklemeTarihi]            DATETIME      NULL,
        [KAMPANYA_ADI]             NVARCHAR(150) NULL,
        [ARAMA_MIKTARI]            NVARCHAR(50)  NULL,
        [ALT_SONUC_KODU]           NVARCHAR(150) NULL,
        [Aranabilirmi]             BIT           NULL,
        [PRE_CAGRI_DENEMESI]       INT           NULL,
        [PRE_SON_ARAMAMI]          INT           NULL,
        [PRE_TUR_SAYISI]           INT           NULL,
        [DIS_CAGRI_DENEMESI]       INT           NULL,
        [DIS_SON_ARAMAMI]          INT           NULL,
        [DIS_TUR_SAYISI]           INT           NULL,
        [ArchivePartLogId]         INT           NULL,
        [RANDEVU_TARIHI]           DATETIME      NULL,
        [KUFUR_HAKARET_TEHDIT]     BIT           NULL,
        [IVR_ANKET_ISTEK_DURUMU]   NVARCHAR(16)  NULL,
        [AnketSonucKodu]           NVARCHAR(50)  NULL,
        [YapilandirmaDurum]        NVARCHAR(50)  NULL,
        [CALLING_PARTY_NUMBER_2]   NVARCHAR(50)  NULL,
        [CALLING_PARTY_NUMBER_3]   NVARCHAR(500) NULL,
        [CALLING_PARTY_NUMBER_4]   NVARCHAR(500) NULL,
        [CALLING_PARTY_NUMBER_5]   NVARCHAR(500) NULL,
        [CALLING_PARTY_NUMBER_6]   NVARCHAR(500) NULL,
        [CALLING_PARTY_NUMBER_7]   NVARCHAR(500) NULL,
        [CALLING_PARTY_NUMBER_8]   NVARCHAR(500) NULL,
        [CALLING_PARTY_NUMBER_9]   NVARCHAR(500) NULL,
        [CALLING_PARTY_NUMBER_10]  NVARCHAR(500) NULL,
        [MUSTERI_NO_SYS]           NVARCHAR(500) NULL,
        [KUFUR_HAKARET_TEHDIT_SYS] NVARCHAR(500) NULL,
        /* ---- PowerDialer sistem kolonları (mevcut) ---- */
        [user_id]                  BIGINT        NULL,
        [user_result_code]         BIGINT        NULL,
        [IsActive]                 BIT           NULL,
        [answer_date]              FLOAT         NULL,
        [call_from_time]           FLOAT         NULL,
        [call_from_time_1]         FLOAT         NULL,
        [call_from_time_2]         FLOAT         NULL,
        [call_from_time_3]         FLOAT         NULL,
        [call_till_time]           FLOAT         NULL,
        [call_till_time_1]         FLOAT         NULL,
        [call_till_time_2]         FLOAT         NULL,
        [call_till_time_3]         FLOAT         NULL,
        [callback_date]            FLOAT         NULL,
        [complete_date]            FLOAT         NULL,
        [is_fetched]               FLOAT         NULL,
        [retry_date]               FLOAT         NULL,
        [start_date]               FLOAT         NULL,
        [UpdatedDate]              FLOAT         NULL,   -- bkz. Bölüm 3.2: kod DATETIME bekler
        [rc_sequence]              INT           NULL,
        [retry_total]              INT           NULL,
        [retry_total_macro]        INT           NULL,
        [telephony_result_code]    INT           NULL,
        [timezone_offset]          INT           NULL,
        [unique_key]               INT           NULL,   -- bkz. Bölüm 3.3: kod BIGINT bekler; yeni kurulumda NOT NULL + PK/IDENTITY
        [is_disabled]              INT           NULL,
        [callback_number]          NVARCHAR(255) NULL,
        [called_number]            NVARCHAR(255) NULL,
        [dnc_numbers]              NVARCHAR(255) NULL,
        [retry_number]             NVARCHAR(255) NULL,
        [ReservedInfo]             NVARCHAR(255) NULL,
        [ReservedInfo2]            NVARCHAR(255) NULL,
        [Filter]                   NVARCHAR(255) NULL,
        [retry_history]            NVARCHAR(MAX) NULL,
        [multiple_sort]            BIT           NULL,
        [ATTEMPTS]                 INT           NULL,
        [UpdatedUser]              NVARCHAR(255) NULL,
        [Arama_Oncelik]            NVARCHAR(255) NULL,
        [Updated_Date]             DATETIME      NULL,
        [response_values]          NVARCHAR(4000) NULL,
        [called_group_id]          BIGINT        NULL,
        [expire_date]              DATETIME      NULL,
        [callback_attemps]         INT           NULL,   -- bkz. Bölüm 3.1: kodun adı callback_attempts
        /* ---- Bu script ile EKLENEN zorunlu kolonlar (Bölüm 1) ---- */
        [cb_history]               NVARCHAR(MAX) NULL,
        [call_process]             NVARCHAR(MAX) NULL,
        [callback_type]            NVARCHAR(50)  NULL,
        [Customer_Name]            NVARCHAR(255) NULL,
        [Customer_Surname]         NVARCHAR(255) NULL,
        [relation_type]            INT           NULL,
        [relation_type_text]       NVARCHAR(255) NULL
    );
    CREATE NONCLUSTERED INDEX IX_ZIRAAT_DINAMIKBANK_TAHSILAT_unique_key
        ON dbo.ZIRAAT_DINAMIKBANK_TAHSILAT (unique_key);
    PRINT 'ZIRAAT_DINAMIKBANK_TAHSILAT tam şablonla oluşturuldu.';
END
ELSE
    PRINT 'ZIRAAT_DINAMIKBANK_TAHSILAT zaten var; EK A atlandı (eksikler Bölüm 1-2 ile tamamlanır).';
GO


/* =============================================================================================
   EK B — SSMS UPDATE ŞABLONU (müşterinin paylaştığı mevcut şablon + eklenen kolonlar) — REFERANS
   ---------------------------------------------------------------------------------------------
   Çalıştırılabilir değildir; <kolon, tip,> yer tutucuları SSMS "Script Table as > UPDATE To"
   çıktısıdır. Bölüm 1 sonrası tablonun tam kolon listesini tek bakışta göstermek için eklenmiştir.
   Sondaki 7 kolon bu script ile eklenen kolonlardır.

UPDATE [dbo].[ZIRAAT_DINAMIKBANK_TAHSILAT]
   SET [CAMPAIGN_NAME] = <CAMPAIGN_NAME, nvarchar(255),>
      ,[ListID] = <ListID, nvarchar(100),>
      ,[DATA_LOAD_DATE] = <DATA_LOAD_DATE, datetime,>
      ,[DATE_STATUS_MODIFIED] = <DATE_STATUS_MODIFIED, datetime,>
      ,[ELENME_NEDENI] = <ELENME_NEDENI, nvarchar(500),>
      ,[CEP_TEL] = <CEP_TEL, nvarchar(50),>
      ,[IS_TEL] = <IS_TEL, nvarchar(50),>
      ,[RESULT_CODE] = <RESULT_CODE, nvarchar(150),>
      ,[EV_TEL] = <EV_TEL, nvarchar(50),>
      ,[SUCCESSRESULT] = <SUCCESSRESULT, float,>
      ,[SIRANO] = <SIRANO, nvarchar(50),>
      ,[MUSTERINO] = <MUSTERINO, nvarchar(50),>
      ,[URUN_KD] = <URUN_KD, nvarchar(20),>
      ,[URUNDEGER] = <URUNDEGER, nvarchar(50),>
      ,[TAKSIT_TR] = <TAKSIT_TR, nvarchar(30),>
      ,[SUBE_KD] = <SUBE_KD, nvarchar(20),>
      ,[SUBE_KD_ADI] = <SUBE_KD_ADI, nvarchar(250),>
      ,[SUBEIL] = <SUBEIL, nvarchar(50),>
      ,[SUBEILCE] = <SUBEILCE, nvarchar(50),>
      ,[TEL1] = <TEL1, nvarchar(20),>
      ,[TEL2] = <TEL2, nvarchar(20),>
      ,[TEL3] = <TEL3, nvarchar(20),>
      ,[TEL4] = <TEL4, nvarchar(20),>
      ,[TEL5] = <TEL5, nvarchar(20),>
      ,[TEL6] = <TEL6, nvarchar(20),>
      ,[TEL7] = <TEL7, nvarchar(20),>
      ,[TEL8] = <TEL8, nvarchar(20),>
      ,[TEL9] = <TEL9, nvarchar(20),>
      ,[TEL10] = <TEL10, nvarchar(20),>
      ,[TEL11] = <TEL11, nvarchar(20),>
      ,[TEL12] = <TEL12, nvarchar(20),>
      ,[TEL13] = <TEL13, nvarchar(20),>
      ,[TEL14] = <TEL14, nvarchar(20),>
      ,[TEL15] = <TEL15, nvarchar(20),>
      ,[EVTEL] = <EVTEL, nvarchar(20),>
      ,[ISTEL] = <ISTEL, nvarchar(20),>
      ,[ARANACAK_TRH] = <ARANACAK_TRH, nvarchar(30),>
      ,[BABAADI] = <BABAADI, nvarchar(50),>
      ,[DOGUM_TRH] = <DOGUM_TRH, nvarchar(30),>
      ,[KAMPANYANO] = <KAMPANYANO, nvarchar(10),>
      ,[KAMPANYA_AD] = <KAMPANYA_AD, nvarchar(100),>
      ,[GERCEKTUZEL_DRM] = <GERCEKTUZEL_DRM, nvarchar(10),>
      ,[BORCTUTAR] = <BORCTUTAR, nvarchar(50),>
      ,[KURALONCELIK] = <KURALONCELIK, int,>
      ,[KAMPANYAONCELIK] = <KAMPANYAONCELIK, int,>
      ,[MUSTERI_VEKIL_ADSOYAD] = <MUSTERI_VEKIL_ADSOYAD, nvarchar(150),>
      ,[VEKILTIP_KD] = <VEKILTIP_KD, nvarchar(50),>
      ,[FIRMAADI] = <FIRMAADI, nvarchar(250),>
      ,[ILKGECIKME_TRH] = <ILKGECIKME_TRH, nvarchar(30),>
      ,[DATANO] = <DATANO, nvarchar(50),>
      ,[ADRESIL] = <ADRESIL, nvarchar(100),>
      ,[ADRESILCE] = <ADRESILCE, nvarchar(100),>
      ,[SIRALAMA_SCR] = <SIRALAMA_SCR, int,>
      ,[GGS] = <GGS, int,>
      ,[CALLING_PARTY_NUMBER] = <CALLING_PARTY_NUMBER, nvarchar(20),>
      ,[ACIKLAMA] = <ACIKLAMA, nvarchar(300),>
      ,[YuklemeTarihi] = <YuklemeTarihi, datetime,>
      ,[KAMPANYA_ADI] = <KAMPANYA_ADI, nvarchar(150),>
      ,[ARAMA_MIKTARI] = <ARAMA_MIKTARI, nvarchar(50),>
      ,[ALT_SONUC_KODU] = <ALT_SONUC_KODU, nvarchar(150),>
      ,[Aranabilirmi] = <Aranabilirmi, bit,>
      ,[PRE_CAGRI_DENEMESI] = <PRE_CAGRI_DENEMESI, int,>
      ,[PRE_SON_ARAMAMI] = <PRE_SON_ARAMAMI, int,>
      ,[PRE_TUR_SAYISI] = <PRE_TUR_SAYISI, int,>
      ,[DIS_CAGRI_DENEMESI] = <DIS_CAGRI_DENEMESI, int,>
      ,[DIS_SON_ARAMAMI] = <DIS_SON_ARAMAMI, int,>
      ,[DIS_TUR_SAYISI] = <DIS_TUR_SAYISI, int,>
      ,[ArchivePartLogId] = <ArchivePartLogId, int,>
      ,[RANDEVU_TARIHI] = <RANDEVU_TARIHI, datetime,>
      ,[KUFUR_HAKARET_TEHDIT] = <KUFUR_HAKARET_TEHDIT, bit,>
      ,[IVR_ANKET_ISTEK_DURUMU] = <IVR_ANKET_ISTEK_DURUMU, nvarchar(16),>
      ,[AnketSonucKodu] = <AnketSonucKodu, nvarchar(50),>
      ,[YapilandirmaDurum] = <YapilandirmaDurum, nvarchar(50),>
      ,[CALLING_PARTY_NUMBER_2] = <CALLING_PARTY_NUMBER_2, nvarchar(50),>
      ,[CALLING_PARTY_NUMBER_3] = <CALLING_PARTY_NUMBER_3, nvarchar(500),>
      ,[CALLING_PARTY_NUMBER_4] = <CALLING_PARTY_NUMBER_4, nvarchar(500),>
      ,[CALLING_PARTY_NUMBER_5] = <CALLING_PARTY_NUMBER_5, nvarchar(500),>
      ,[CALLING_PARTY_NUMBER_6] = <CALLING_PARTY_NUMBER_6, nvarchar(500),>
      ,[CALLING_PARTY_NUMBER_7] = <CALLING_PARTY_NUMBER_7, nvarchar(500),>
      ,[CALLING_PARTY_NUMBER_8] = <CALLING_PARTY_NUMBER_8, nvarchar(500),>
      ,[CALLING_PARTY_NUMBER_9] = <CALLING_PARTY_NUMBER_9, nvarchar(500),>
      ,[CALLING_PARTY_NUMBER_10] = <CALLING_PARTY_NUMBER_10, nvarchar(500),>
      ,[MUSTERI_NO_SYS] = <MUSTERI_NO_SYS, nvarchar(500),>
      ,[KUFUR_HAKARET_TEHDIT_SYS] = <KUFUR_HAKARET_TEHDIT_SYS, nvarchar(500),>
      ,[user_id] = <user_id, bigint,>
      ,[user_result_code] = <user_result_code, bigint,>
      ,[IsActive] = <IsActive, bit,>
      ,[answer_date] = <answer_date, float,>
      ,[call_from_time] = <call_from_time, float,>
      ,[call_from_time_1] = <call_from_time_1, float,>
      ,[call_from_time_2] = <call_from_time_2, float,>
      ,[call_from_time_3] = <call_from_time_3, float,>
      ,[call_till_time] = <call_till_time, float,>
      ,[call_till_time_1] = <call_till_time_1, float,>
      ,[call_till_time_2] = <call_till_time_2, float,>
      ,[call_till_time_3] = <call_till_time_3, float,>
      ,[callback_date] = <callback_date, float,>
      ,[complete_date] = <complete_date, float,>
      ,[is_fetched] = <is_fetched, float,>
      ,[retry_date] = <retry_date, float,>
      ,[start_date] = <start_date, float,>
      ,[UpdatedDate] = <UpdatedDate, float,>
      ,[rc_sequence] = <rc_sequence, int,>
      ,[retry_total] = <retry_total, int,>
      ,[retry_total_macro] = <retry_total_macro, int,>
      ,[telephony_result_code] = <telephony_result_code, int,>
      ,[timezone_offset] = <timezone_offset, int,>
      ,[unique_key] = <unique_key, int,>
      ,[is_disabled] = <is_disabled, int,>
      ,[callback_number] = <callback_number, nvarchar(255),>
      ,[called_number] = <called_number, nvarchar(255),>
      ,[dnc_numbers] = <dnc_numbers, nvarchar(255),>
      ,[retry_number] = <retry_number, nvarchar(255),>
      ,[ReservedInfo] = <ReservedInfo, nvarchar(255),>
      ,[ReservedInfo2] = <ReservedInfo2, nvarchar(255),>
      ,[Filter] = <Filter, nvarchar(255),>
      ,[retry_history] = <retry_history, nvarchar(max),>
      ,[multiple_sort] = <multiple_sort, bit,>
      ,[ATTEMPTS] = <ATTEMPTS, int,>
      ,[UpdatedUser] = <UpdatedUser, nvarchar(255),>
      ,[Arama_Oncelik] = <Arama_Oncelik, nvarchar(255),>
      ,[Updated_Date] = <Updated_Date, datetime,>
      ,[response_values] = <response_values, nvarchar(4000),>
      ,[called_group_id] = <called_group_id, bigint,>
      ,[expire_date] = <expire_date, datetime,>
      ,[callback_attemps] = <callback_attemps, int,>
      -- ---- Bu script ile eklenen kolonlar (Bölüm 1) ----
      ,[cb_history] = <cb_history, nvarchar(max),>
      ,[call_process] = <call_process, nvarchar(max),>
      ,[callback_type] = <callback_type, nvarchar(50),>
      ,[Customer_Name] = <Customer_Name, nvarchar(255),>
      ,[Customer_Surname] = <Customer_Surname, nvarchar(255),>
      ,[relation_type] = <relation_type, int,>
      ,[relation_type_text] = <relation_type_text, nvarchar(255),>
 WHERE <Search Conditions,,>
GO
   ============================================================================================= */
