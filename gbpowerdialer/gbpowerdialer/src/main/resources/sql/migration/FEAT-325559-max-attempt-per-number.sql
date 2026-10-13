SET QUOTED_IDENTIFIER ON;
GO

/* ============================================================================
   FEAT-325559 — Numara Basina Azami Arama Limiti (Max Attempt Per Phone Number)
   ----------------------------------------------------------------------------
   ELLE CALISTIRILACAK MIGRATION. Bu repoda Flyway/Liquibase yoktur ve
   hibernate.hbm2ddl.auto kapalidir (applicationContext.xml). Script otomatik
   UYGULANMAZ; deploy sirasinda DBA tarafindan calistirilir. Dialect: SQL Server.

   IKI BOLUM VARDIR VE AYRI VERITABANLARINDA CALISIR:
     BOLUM 1 -> PD ANA DB   (pd_campaign). Bir kez.
     BOLUM 2 -> KAMPANYA DB (tum kampanya result tablolari). Her camp DB'de bir kez.

   BOLUM 2 tablo adi SABITLEMEZ: kampanya result tablolari `complete_date`
   kolonuna sahip olmalariyla tanindigi icin tum tablolar dinamik kesfedilir ve
   kolonu olmayanlara eklenir. Ayni script tekrar calistirilabilir (idempotent).

   CALISTIRMA SIRASI: BOLUM 1 once, BOLUM 2 sonra. Uygulama yeni surumu
   deploy edilmeden once ikisi de tamamlanmis olmalidir; kolonlar nullable ve
   additive oldugu icin eski surum bunlardan etkilenmez.
   ============================================================================ */


/* ============================================================================
   BOLUM 1 - PD ANA DB (pd_campaign)                        [bir kez calisir]
   ============================================================================ */

/* ---- 1.1 max_attempt_per_number: opsiyonel numara basina limit -------------
   NULL veya <= 0 ise OZELLIK KAPALIDIR ve mevcut retry davranisi birebir
   korunur. Genel deneme limiti (retry_overal_attempt) ve macro aralik
   limitinden BAGIMSIZ degerlendirilir; ucu birlikte uygulanir ve kayit
   hangisi once dolarsa durur.
   REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER (retry_strategy = 0) ve
   REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER (retry_strategy = 1) stratejilerinde bu
   alan YOK SAYILIR; iki strateji de numara basina siniri zaten
   retry_overal_attempt ile uyguluyor.
   Alani bosaltmak ozelligin KILL-SWITCH'idir: kod deploy'u veya DDL geri alma
   gerekmez. CALISAN kampanyada bos deger kaydetmeyle aktarilmaz; kampanya
   durdurulup baslatilinca (veya kaydet ve yeniden baslat) eski davranisa
   doner. */
IF COL_LENGTH('dbo.pd_campaign', 'max_attempt_per_number') IS NULL
BEGIN
    ALTER TABLE dbo.pd_campaign ADD max_attempt_per_number INT NULL;
    PRINT 'pd_campaign.max_attempt_per_number eklendi.';
END
ELSE
    PRINT 'pd_campaign.max_attempt_per_number zaten var, atlandi.';
GO

/* ---- 1.2 col_result_close_reason: kapanis nedeni kolonu binding'i ----------
   Kaydin hangi nedenle kapatildigini tutan result tablosu kolonunun fiziksel
   adi. Bos birakilirsa ozellik calismaya devam eder, yalniz kapanis nedeni
   kaydedilmez.
   BOLUM 2 calistirildiktan sonra binding UYGULAMA TARAFINDAN OTOMATIK atanir
   (CampaignDataTypeChecker.applyDefaultHistoryColumnMappings ve
   campaignFormCtrl.setDefaultValuesForUndefinedResultTableColumns); DBA veya
   operatorun elle deger yazmasi GEREKMEZ. Operatorun bilincli sectigi farkli
   bir kolon ezilmez.

   Otomatik atama yalniz kolonun TIPI uygunsa yapilir (VARCHAR/NVARCHAR/NCHAR/
   NVARCHAR2/VARCHAR2/CHARACTER_VARYING). Musterinin tablosunda ayni ada sahip
   ama farkli tipte (ornek INT/DATE) bir kolon varsa binding BOS BIRAKILIR ve
   kampanya normal baslar; yalniz kapanis nedeni yazilmaz. Bu guard olmadan
   boyle bir kolon kampanyayi "campaign.start.wrong.types" ile hic
   baslatilamaz hale getiriyordu (Faz 7 / P1.1). */
IF COL_LENGTH('dbo.pd_campaign', 'col_result_close_reason') IS NULL
BEGIN
    ALTER TABLE dbo.pd_campaign ADD col_result_close_reason NVARCHAR(255) NULL;
    PRINT 'pd_campaign.col_result_close_reason eklendi.';
END
ELSE
    PRINT 'pd_campaign.col_result_close_reason zaten var, atlandi.';
GO


/* ============================================================================
   BOLUM 2 - KAMPANYA DB (tum result tablolari)      [her camp DB'de calisir]
   ============================================================================
   Yazilan degerler (Constants.CLOSE_REASON_*, lokalize EDILMEZ):
     'closing for reached number limit'  -> tum aranabilir numaralarin
                                            bireysel limiti doldu
     'max attempt reached on callback'   -> callback vadesinde limit tukenmis,
                                            cagri tetiklenmedi
     'no callable number available'      -> kayitta aranabilir gecerli numara yok

   Basarili kapanista (musteri yaniti + kesin sonuc kodu) bu kolona HICBIR SEY
   yazilmaz; aksi halde rapor "limit doldu" gibi okunurdu.

   Kolon tipi VARCHAR/NVARCHAR/NCHAR/NVARCHAR2/VARCHAR2/CHARACTER_VARYING
   olmalidir (Constants.RESULT_FIELDS_ALLOWED_TYPES dogrular); aksi halde
   kampanya start'ta "campaign.start.wrong.types" ile reddedilir.

   INDEX EKLENMEZ: close_reason yalniz YAZILIR, arama motoru bu kolona gore
   sorgu yapmaz. Raporlama ihtiyaci dogarsa index ayri olarak degerlendirilir.
   ============================================================================ */

/* ---- 2.1 close_reason kolonu (tum result tablolarina) ----------------------
   Kampanya result tablolari `complete_date` kolonuyla taninir. Kolonu zaten
   olan tablolar atlanir, boylece script tekrar calistirilabilir.

   TABLO KESFININ SINIRI (Faz 7 / P1.2):
   Kesif `complete_date` kolonunun VARLIGINA dayanir; kampanya DB'sinde bu ada
   sahip ALAKASIZ bir tablo varsa (ornek: arsiv/staging/rapor tablolari,
   baska bir uygulamanin tablosu) ona da close_reason kolonu eklenir. Kolon
   nullable oldugu ve uygulama yalniz kampanyaya BAGLI tabloya yazdigi icin
   zararsizdir, ancak beklenmeyen tabloda kolon gormek kafa karistirir.

   Calistirmadan ONCE 2.5 dogrulama sorgusu ile hangi tablolarin kapsama
   girdigi gorulebilir. Kapsam daraltmasi gerekiyorsa (ornek: tablo adi
   prefix'i veya pd_campaign.db_result_table listesiyle esleme) bu bilincli
   bir karardir ve DBA/gelistirici mutabakatiyla eklenir - script kendi
   basina daraltma YAPMAZ, cunku result tablosu adlari kampanya bazinda
   serbesttir ve ana DB bu script'in kostugu DB'den erisilebilir olmayabilir. */
DECLARE @sql NVARCHAR(MAX) = '';
SELECT @sql = @sql + 'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + '.' + QUOTENAME(t.name)
            + ' ADD close_reason NVARCHAR(255) NULL;' + CHAR(13)
FROM sys.tables t JOIN sys.columns c ON t.object_id = c.object_id
WHERE c.name = 'complete_date'
  AND t.object_id NOT IN (SELECT object_id FROM sys.columns WHERE name = 'close_reason');
-- PRINT @sql;
EXEC sp_executesql @sql;
GO

/* ---- 2.2 OPSIYONEL: eksik call_process / cb_history (URETIR, CALISTIRMAZ) --
   Numara basina sayac icin AYRI bir sayac kolonu yoktur; sayim
   retry_history + call_process + cb_history uzerinden yeniden kurulur.
   call_process izi kursor olarak kullanildigi icin varsa sayim TAM, yoksa
   guvenli fallback'e (retry_history + ilk numara) duser ve kaydin ILK
   numarasi limit+1 arama alabilir.

   Bu iki kolon daha eski bir task'a aittir; burada yalniz sayim dogrulugunu
   artirmak icin OPSIYONEL olarak sunulur. Atlanabilir; ozellik yine calisir.

   NEDEN EXEC EDILMEZ (Faz 7 / P1.2):
   Bu blok onceki surumde 'OPSIYONEL / atlanabilir' yaziyor ama kosulsuz
   EXEC ediliyordu. DBA dosyayi butun olarak calistirdigi icin 'atlanabilir'
   pratikte gecersizdi ve etkisi ilan edilenden GENISTI: kolon eklendigi anda
   CampaignDataTypeChecker.applyDefaultHistoryColumnMappings o DB'deki TUM
   kampanyalarda binding'i otomatik atar ve insertCallResultToDB bu kolonlari
   yazmaya baslar. Yani bu isin KAPSAMI DISINDAKI kampanyalarin result yazimi
   da degisirdi. Karar bir insana ait olmalidir; blok 2.4 deseniyle yalnizca
   gerekli ALTER ifadelerini URETIR ve EKRANA BASAR.

   Kolonlari eklemeye karar verilirse: cikan ifadeler gozden gecirilip
   calistirilir; binding uygulama tarafindan otomatik atanir (yalniz kolon
   TIPI uygunsa - bkz. CampaignDataTypeChecker tip guard'i). */
DECLARE @script NVARCHAR(MAX) = '';
SELECT @script = @script + 'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + '.' + QUOTENAME(t.name)
              + ' ADD call_process NVARCHAR(MAX) NULL;' + CHAR(13)
FROM sys.tables t JOIN sys.columns c ON t.object_id = c.object_id
WHERE c.name = 'complete_date'
  AND t.object_id NOT IN (SELECT object_id FROM sys.columns WHERE name = 'call_process');

SELECT @script = @script + 'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + '.' + QUOTENAME(t.name)
              + ' ADD cb_history NVARCHAR(MAX) NULL;' + CHAR(13)
FROM sys.tables t JOIN sys.columns c ON t.object_id = c.object_id
WHERE c.name = 'complete_date'
  AND t.object_id NOT IN (SELECT object_id FROM sys.columns WHERE name = 'cb_history');

IF LEN(@script) = 0
    PRINT '2.2: call_process ve cb_history tum result tablolarinda mevcut, yapilacak is yok.';
ELSE
BEGIN
    PRINT '--- 2.2 OPSIYONEL: ASAGIDAKI IFADELER OTOMATIK CALISTIRILMADI ---';
    PRINT '--- Eklenmesine karar verilirse bu is kapsami disindaki kampanyalarin ---';
    PRINT '--- result yaziminin da degisecegi bilinerek elle calistirilmalidir.   ---';
    PRINT @script;
END
GO

/* ---- 2.3 ZORUNLU ON KONTROL: gecmis kolonlarinin kapasitesi ----------------
   Numara basina sayac retry_history STRING'ININ KENDISIDIR: her arama ~12
   karakter ekler (11 haneli numara + ayrac). NVARCHAR(255) bir kolon ~21
   aramadan sonra dolar ve SQL Server bunu SESSIZCE KIRPMAZ, UPDATE'i
   "String or binary data would be truncated" ile DUSURUR. O UPDATE
   insertCallResultToDB'nin tek buyuk UPDATE'i oldugu icin TUM CAGRI SONUCU
   YAZIMI KAYBOLUR ve hata uygulama tarafinda catch icinde yutulur.

   Genel deneme limiti yukseltilmeden ONCE bu sorgu calistirilmalidir.
   CHARACTER_MAXIMUM_LENGTH = -1  -> NVARCHAR(MAX), sorun yok.
   Sinirli bir deger donen her satir icin 2.4'teki script uretilir. */
SELECT
    t.name                         AS table_name,
    c.name                         AS column_name,
    ty.name                        AS data_type,
    c.max_length                   AS max_length_bytes,
    CASE WHEN c.max_length = -1 THEN 'MAX - sorun yok'
         ELSE 'SINIRLI - 2.4 ile yukseltilmeli' END AS durum
FROM sys.tables t
JOIN sys.columns c  ON c.object_id = t.object_id
JOIN sys.types  ty  ON ty.user_type_id = c.user_type_id
WHERE c.name IN ('retry_history', 'call_process', 'cb_history')
  AND EXISTS (SELECT 1 FROM sys.columns cc
              WHERE cc.object_id = t.object_id AND cc.name = 'complete_date')
  AND c.max_length <> -1
ORDER BY t.name, c.name;
GO

/* ---- 2.4 KAPASITE YUKSELTME SCRIPT'I (URETIR, CALISTIRMAZ) -----------------
   2.3 satir donduyse asagidaki blok gerekli ALTER COLUMN ifadelerini URETIR ve
   yalnizca EKRANA BASAR. Bilincli olarak EXEC EDILMEZ:

   NVARCHAR(MAX)'a gecis metadata-only bir islem DEGILDIR; buyuk kampanya
   tablolarinda tablo yeniden yazilabilir ve uzun sureli kilit olusabilir. Bu
   yuzden DBA cikan ifadeleri gozden gecirip uygun bakim penceresinde kendisi
   calistirmalidir.

   Kolonun mevcut NULL/NOT NULL durumu korunur. */
DECLARE @script NVARCHAR(MAX) = '';
SELECT @script = @script + 'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + '.' + QUOTENAME(t.name)
              + ' ALTER COLUMN ' + QUOTENAME(c.name) + ' NVARCHAR(MAX) '
              + CASE WHEN c.is_nullable = 1 THEN 'NULL' ELSE 'NOT NULL' END + ';' + CHAR(13)
FROM sys.tables t
JOIN sys.columns c ON c.object_id = t.object_id
WHERE c.name IN ('retry_history', 'call_process', 'cb_history')
  AND EXISTS (SELECT 1 FROM sys.columns cc
              WHERE cc.object_id = t.object_id AND cc.name = 'complete_date')
  AND c.max_length <> -1;

IF LEN(@script) = 0
    PRINT 'Kapasite yukseltmesi gerekmiyor; tum gecmis kolonlari NVARCHAR(MAX).';
ELSE
BEGIN
    PRINT '--- ASAGIDAKI IFADELER GOZDEN GECIRILIP BAKIM PENCERESINDE ELLE CALISTIRILMALIDIR ---';
    PRINT @script;
END
GO

/* ---- 2.5 DOGRULAMA: kolonlar yerinde mi ------------------------------------
   Beklenen: her result tablosu icin has_close_reason = 1. call_process ve
   cb_history 2.2 uygulanmadiysa 0 olabilir (ozellik yine calisir; sayim
   fallback'e duser).

   Bu sorgu 2.1 ile AYNI kesif kuralini kullanir (`complete_date` kolonu olan
   tablolar), dolayisiyla ciktisi 2.1'in KAPSAMINI da gosterir. Listede
   kampanya result tablosu olmadigini bildiginiz bir tablo varsa 2.1 ona da
   kolon eklemis demektir - zararsizdir (nullable, uygulama yazmaz) ama
   kapsam daraltmasi konusulmasi gerekir. */
SELECT
    t.name AS table_name,
    MAX(CASE WHEN c.name = 'close_reason'   THEN 1 ELSE 0 END) AS has_close_reason,
    MAX(CASE WHEN c.name = 'call_process'   THEN 1 ELSE 0 END) AS has_call_process,
    MAX(CASE WHEN c.name = 'cb_history'     THEN 1 ELSE 0 END) AS has_cb_history,
    MAX(CASE WHEN c.name = 'retry_history'  THEN 1 ELSE 0 END) AS has_retry_history
FROM sys.tables t
JOIN sys.columns c ON c.object_id = t.object_id
WHERE EXISTS (SELECT 1 FROM sys.columns cc
              WHERE cc.object_id = t.object_id AND cc.name = 'complete_date')
GROUP BY t.name
ORDER BY t.name;
GO


/* ============================================================================
   GERI ALMA
   ----------------------------------------------------------------------------
   Ozelligi kapatmak icin DDL geri almaya GEREK YOKTUR: kampanyada
   max_attempt_per_number alanini bosaltmak (NULL) davranisi aninda eski haline
   dondurur ve kod deploy'u gerektirmez. Bu, ozelligin birincil kill-switch'idir.

   Kolonlarin tamamen kaldirilmasi gerekiyorsa (yalniz feature geri cekilirse),
   ONCE uygulamanin yeni surumunun geri alindigindan emin olunmalidir.

   -- PD ANA DB:
   -- ALTER TABLE dbo.pd_campaign DROP COLUMN max_attempt_per_number, col_result_close_reason;

   -- KAMPANYA DB (tum result tablolarindan close_reason):
   -- DECLARE @sql NVARCHAR(MAX) = '';
   -- SELECT @sql = @sql + 'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + '.' + QUOTENAME(t.name)
   --             + ' DROP COLUMN close_reason;' + CHAR(13)
   -- FROM sys.tables t JOIN sys.columns c ON t.object_id = c.object_id
   -- WHERE c.name = 'close_reason'
   --   AND EXISTS (SELECT 1 FROM sys.columns cc
   --               WHERE cc.object_id = t.object_id AND cc.name = 'complete_date');
   -- EXEC sp_executesql @sql;

   -- call_process / cb_history KALDIRILMAZ: onlar daha eski bir task'a aittir ve
   -- baska akislar tarafindan kullanilir.
   ============================================================================ */
