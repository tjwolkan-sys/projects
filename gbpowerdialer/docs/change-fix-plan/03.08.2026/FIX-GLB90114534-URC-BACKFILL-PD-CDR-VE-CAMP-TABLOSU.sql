/* =============================================================================================
   FIX-GLB90114534 | user_result_code backfill: pd_cdr + kampanya sonuç tablosu
   ---------------------------------------------------------------------------------------------
   Tarih      : 03.08.2026
   Ortam      : MS SQL Server (2012+ / CONCAT ve window function gerekir)
   PD ana DB  : gbWebphone_NonTurkcell   (farklı ortamda çalıştırılacaksa DB adını değiştir)
   Tip        : Salt veri düzeltme (data backfill) script'i. Şema DEĞİŞTİRMEZ, DDL içermez.

   AMAÇ
   ----
   Webphone/Asterisk tarafında agent result code girmiş olmasına rağmen
   `pd_cdr.user_result_code_id` alanı NULL kalan başarılı çağrılar (telephony_rc 10/24) için:
     (a) `pd_cdr.user_result_code_id` alanını,
     (b) ilgili KAMPANYA SONUÇ TABLOSUNDAKİ user result code kolonunu
   satır satır, idempotent ve geri izlenebilir şekilde tamamlamak.

   Kök neden analizi bu script'in kapsamı DEĞİLDİR; ilgili analizler:
     - docs/analyse/29.04.2026/FIX-GLB90048674-PD-CDR-USER-RESULT-CODE-KACAGI.md
     - docs/change-fix-plan/07.05.2026/FIX-317516-AWAITING-USER-RESULT-RACE-OVERWRITE.md
   Bu script yalnız geçmiş kayıtları onarır; uygulamanın yeni kayıt üretme davranışını değiştirmez.

   KAMPANYA TABLOSU NEDEN DİNAMİK BULUNUYOR?
   -----------------------------------------
   Kampanya sonuç tablosu adı ve kolon adları kampanya başına konfigüre edilebilir. Bu yüzden
   tek bir statik `UPDATE ... FROM ... JOIN` yazılamaz; `pd_cdr.campaign_id` üzerinden
   `pd_campaign` metadata'sı okunup her satır için ayrı UPDATE üretilir.

   Kod referansları (repo doğrulaması):
     - Sonuç tablosu adı        : pd_campaign.db_result_table
                                  (gbpowerdialer/src/main/java/com/powerdialer/model/Campaign.java:708)
     - Anahtar kolon            : db_list_table = db_result_table ise col_unique_key,
                                  değilse col_result_unique_key
                                  (engine/db/CampaignDbOperations.java:2498 columnToCheckRowExistence)
     - Yazılacak kolon          : pd_campaign.col_result_user_rc  (prod'da genelde user_result_code)
                                  (engine/db/CampaignDbOperations.java:2242-2249)
     - Satır eşleşmesi          : camp.<key_col> = pd_cdr.contact_id
                                  (engine/dialer/CallProducer.java:642 -> cdr.setContactId(call.getUniqueKey()))
                                  (engine/db/CampaignDbOperations.java:2282 WHERE <key_col> = uniqueKey)
     - Aynı değer iki yere      : Uygulamanın kendi durable fallback'i de aynı resultCodeId'yi
                                  hem pd_cdr'a hem kampanya tablosuna yazıyor
                                  (redis/WebphoneSubscriber.java:385 -> dao/impl/CampaignResultDao.java:40)
     - Kampanya DB bağlantısı   : pd_campaign.db_connection_string / db_username / db_password
                                  (kampanya tablosu FARKLI sunucu/DB'de olabilir)

   ÖN KOŞULLAR VE EMNİYET KURALLARI
   --------------------------------
   1. Önce Bölüm 1 (etki analizi) çalıştırılır ve `db_connection_string` kontrol edilir.
      Kampanya tablosu farklı bir instance'ta ise Bölüm 5'in kampanya UPDATE'leri O BAĞLANTIDA
      çalıştırılmalıdır; bu script linked server VARSAYMAZ.
   2. Üretilen tüm UPDATE'ler idempotenttir (`... IS NULL` guard'ı taşır); tekrar çalıştırmak
      dolu bir değeri EZMEZ.
   3. Kampanya sonuç tablosunda kayıt başına TEK satır vardır; retry olduysa o satır SON çağrıyı
      temsil eder. Bu yüzden eski bir çağrının result code'u kampanya tablosuna YAZILMAZ
      (durum = CDR_ONLY_ESKI_CAGRI). Bu bilinçli bir kısıttır, veri bozulmasını engeller.
   4. Bir pd_cdr satırına birden fazla FARKLI result_code_id bağlanabiliyor
      (parentid -> uniqueid -> call_id zinciri 1-N). Bu satırlar otomatik yazılmaz,
      durum = CAKISMA_ELLE_INCELE olarak raporlanır.
      NOT: Orijinal set-based `UPDATE ... FROM JOIN` sorgusunda SQL Server bu durumda
      eşleşmelerden rastgele birini yazar; buradaki ayıklama o davranışı bilinçli olarak kapatır.
   5. Çalıştırma öncesi kampanya sonuç tablolarının yedeği/snapshot'ı alınmalıdır.
   6. Bölüm 2 bir GEÇİCİ TABLO (#urc_fix) doldurur; Bölüm 3-6 aynı SSMS oturumunda,
      aynı pencerede çalıştırılmalıdır (temp table oturum ömürlüdür).
   7. Canlı kampanya saatlerinde toplu çalıştırma önerilmez; kampanya tablosu dialer'ın
      fetch/update hot path'inde kullanılır (bkz. .codex/skills/gbpd-call-flow-performance-guardrails).
   ============================================================================================= */


/* =============================================================================================
   BÖLÜM 1 — ETKİ ANALİZİ
   Hangi kampanya, hangi tablo/kolonlar, kaç satır etkileniyor ve hangi DB bağlantısında?
   Üretim öncesi mutlaka çalıştırılır; salt okuma, hiçbir şeyi değiştirmez.
   ============================================================================================= */
DECLARE @sinceDate DATETIME = '2026-08-01';   -- düzeltme penceresinin başlangıcı

SELECT
    CMP.id                                       AS campaign_id,
    CMP.name                                     AS campaign_name,
    CMP.db_result_table                          AS camp_result_table,
    CMP.db_list_table                            AS camp_source_table,
    CASE WHEN CMP.db_list_table = CMP.db_result_table
         THEN CMP.col_unique_key
         ELSE CMP.col_result_unique_key END      AS key_column,          -- camp tablosundaki anahtar kolon
    CMP.col_result_user_rc                       AS user_rc_column,      -- güncellenecek kolon
    CMP.col_result_telephony_rc                  AS telephony_rc_column, -- ek guard için
    CMP.db_connection_string,                                            -- UPDATE'ler BURADA koşulacak
    COUNT(DISTINCT PC.id)                        AS bekleyen_cdr_satiri
FROM [gbWebphone_NonTurkcell].[dbo].[pd_cdr]                     AS PC
INNER JOIN [gbWebphone_NonTurkcell].[dbo].[ast_registrar_cdr]    AS ARC  ON PC.[interaction_id] = ARC.[parentid]
INNER JOIN [gbWebphone_NonTurkcell].[dbo].[wbp_call_result_code] AS WCRC ON ARC.[uniqueid]      = WCRC.[call_id]
INNER JOIN [gbWebphone_NonTurkcell].[dbo].[pd_campaign]          AS CMP  ON CMP.[id]            = PC.[campaign_id]
WHERE PC.[call_start_time]     >= @sinceDate
  AND WCRC.[result_code_id]    IS NOT NULL   -- webphone tarafında result code girilmiş
  AND PC.[user_result_code_id] IS NULL       -- pd_cdr tarafında eksik kalmış (onarım hedefi)
  AND PC.[telephony_rc]        IN (10, 24)   -- yalnız başarılı bağlantı; diğer TRC'lerde URC beklenmez
  AND PC.[contact_id]          IS NOT NULL   -- kampanya kaydına bağlanamayan çağrıları dışla
GROUP BY CMP.id, CMP.name, CMP.db_result_table, CMP.db_list_table,
         CMP.col_unique_key, CMP.col_result_unique_key,
         CMP.col_result_user_rc, CMP.col_result_telephony_rc, CMP.db_connection_string
ORDER BY bekleyen_cdr_satiri DESC;
GO


/* =============================================================================================
   BÖLÜM 2 — ÜRETİCİ: satır satır UPDATE ifadelerini #urc_fix geçici tablosuna yaz
   Salt okuma + temp table; hedef tablolara DOKUNMAZ.
   Aynı oturumda Bölüm 3-6 bu tabloyu kullanır.
   ============================================================================================= */
IF OBJECT_ID('tempdb..#urc_fix') IS NOT NULL DROP TABLE #urc_fix;

DECLARE @sinceDate DATETIME = '2026-08-01';   -- Bölüm 1 ile AYNI pencere olmalı

;WITH src AS (
    /* Onarım adayı pd_cdr satırları.
       Bir pd_cdr satırına birden fazla webphone result code bağlanabildiği için
       result code'u agrege ediyor, distinct sayısını da taşıyoruz (çakışma tespiti). */
    SELECT
        PC.[id]                               AS cdr_id,
        PC.[campaign_id],
        PC.[contact_id],
        PC.[telephony_rc],
        PC.[call_start_time],
        PC.[interaction_id],
        MAX(WCRC.[result_code_id])            AS result_code_id,
        COUNT(DISTINCT WCRC.[result_code_id]) AS distinct_rc_count
    FROM [gbWebphone_NonTurkcell].[dbo].[pd_cdr]                     AS PC
    INNER JOIN [gbWebphone_NonTurkcell].[dbo].[ast_registrar_cdr]    AS ARC  ON PC.[interaction_id] = ARC.[parentid]
    INNER JOIN [gbWebphone_NonTurkcell].[dbo].[wbp_call_result_code] AS WCRC ON ARC.[uniqueid]      = WCRC.[call_id]
    WHERE PC.[call_start_time]     >= @sinceDate
      AND WCRC.[result_code_id]    IS NOT NULL
      AND PC.[user_result_code_id] IS NULL
      AND PC.[telephony_rc]        IN (10, 24)
      AND PC.[contact_id]          IS NOT NULL
      AND PC.[campaign_id]         IS NOT NULL
    GROUP BY PC.[id], PC.[campaign_id], PC.[contact_id], PC.[telephony_rc],
             PC.[call_start_time], PC.[interaction_id]
),
last_call AS (
    /* Kayıt (campaign_id + contact_id) başına SON çağrı zamanı.
       Kampanya sonuç tablosundaki tek satır bu son çağrıyı temsil ettiği için,
       yalnız son çağrının result code'u kampanya tablosuna yazılabilir.
       Dikkat: bu CTE filtrelenmiş adaylar değil, penceredeki TÜM pd_cdr satırları üzerinden
       hesaplanır; aksi halde retry edilmiş bir kaydın eski çağrısı yanlışlıkla "son" görünür. */
    SELECT [campaign_id], [contact_id], MAX([call_start_time]) AS last_call_start
    FROM [gbWebphone_NonTurkcell].[dbo].[pd_cdr]
    WHERE [call_start_time] >= @sinceDate
      AND [contact_id]      IS NOT NULL
      AND [campaign_id]     IS NOT NULL
    GROUP BY [campaign_id], [contact_id]
),
cmp AS (
    /* Kampanya metadata'sı: tablo adı ve kolon adları kampanya başına konfigüre edilebilir. */
    SELECT
        C.[id]                        AS campaign_id,
        C.[name]                      AS campaign_name,
        C.[db_result_table]           AS camp_result_table,
        C.[col_result_user_rc]        AS user_rc_col,
        C.[col_result_telephony_rc]   AS telephony_rc_col,
        C.[db_connection_string],
        CASE WHEN C.[db_list_table] = C.[db_result_table]
             THEN C.[col_unique_key]
             ELSE C.[col_result_unique_key] END AS key_col
    FROM [gbWebphone_NonTurkcell].[dbo].[pd_campaign] AS C
)
SELECT
    S.campaign_id,
    M.campaign_name,
    M.db_connection_string,
    M.camp_result_table,
    M.key_col,
    M.user_rc_col,
    S.cdr_id,
    S.interaction_id,
    S.contact_id,
    S.telephony_rc,
    S.result_code_id,
    S.distinct_rc_count,
    S.call_start_time,

    /* Satırın onarım durumu:
         UYGULANABILIR        : hem pd_cdr hem camp tablosu güvenle güncellenebilir
         CDR_ONLY_ESKI_CAGRI  : yalnız pd_cdr güncellenir; camp satırı daha yeni bir çağrıya ait
         CAKISMA_ELLE_INCELE  : birden fazla farklı result_code_id var, otomatik yazılmaz
         CAMP_KOLON_TANIMSIZ  : kampanyada col_result_user_rc tanımlı değil, camp tarafı atlanır */
    CASE
        WHEN S.distinct_rc_count > 1              THEN 'CAKISMA_ELLE_INCELE'
        WHEN M.user_rc_col IS NULL                THEN 'CAMP_KOLON_TANIMSIZ'
        WHEN S.call_start_time <> LC.last_call_start THEN 'CDR_ONLY_ESKI_CAGRI'
        ELSE 'UYGULANABILIR'
    END AS durum,

    /* (a) pd_cdr satır bazlı UPDATE — PD ana DB'de koşar.
           `user_result_code_id IS NULL` guard'ı idempotentliği sağlar. */
    CASE WHEN S.distinct_rc_count > 1 THEN NULL ELSE
        CONCAT('UPDATE [gbWebphone_NonTurkcell].[dbo].[pd_cdr] SET [user_result_code_id] = ',
               S.result_code_id,
               ' WHERE [id] = ', S.cdr_id,
               ' AND [user_result_code_id] IS NULL;')
    END AS cdr_update_sql,

    /* (b) Kampanya sonuç tablosu satır bazlı UPDATE — kampanyanın KENDİ DB bağlantısında koşar.
           Guard'lar:
             - <user_rc_col> IS NULL            : dolu değeri ezme (idempotent)
             - <telephony_rc_col> = <trc>       : camp satırı gerçekten bu çağrıyı temsil ediyor mu?
           Tablo/kolon adları pd_campaign'den geldiği gibi basılır (şema öneki dahil olabilir). */
    CASE WHEN S.distinct_rc_count > 1
           OR M.user_rc_col IS NULL
           OR S.call_start_time <> LC.last_call_start THEN NULL ELSE
        CONCAT('UPDATE ', M.camp_result_table,
               ' SET ',   M.user_rc_col, ' = ', S.result_code_id,
               ' WHERE ', M.key_col,     ' = ', S.contact_id,
               ' AND ',   M.user_rc_col, ' IS NULL',
               CASE WHEN M.telephony_rc_col IS NULL THEN ''
                    ELSE CONCAT(' AND ', M.telephony_rc_col, ' = ', S.telephony_rc) END,
               ';')
    END AS camp_update_sql
INTO #urc_fix
FROM src AS S
INNER JOIN last_call AS LC ON LC.[campaign_id] = S.[campaign_id]
                          AND LC.[contact_id]  = S.[contact_id]
INNER JOIN cmp       AS M  ON M.campaign_id    = S.campaign_id;

/* Üretim özeti */
SELECT durum, COUNT(*) AS satir_sayisi
FROM #urc_fix
GROUP BY durum
ORDER BY satir_sayisi DESC;
GO


/* =============================================================================================
   BÖLÜM 3 — ÖNİZLEME VE ÇAKIŞMA RAPORU
   Script'i çalıştırmadan önce üretilen ifadelerin gözle doğrulanması için.
   ============================================================================================= */

-- 3.1 Uygulanabilir satırlar (her iki UPDATE de üretildi)
SELECT campaign_id, campaign_name, db_connection_string, cdr_id, contact_id,
       result_code_id, telephony_rc, cdr_update_sql, camp_update_sql
FROM #urc_fix
WHERE durum = 'UYGULANABILIR'
ORDER BY campaign_id, contact_id, cdr_id;

-- 3.2 Yalnız pd_cdr onarılacak satırlar (kayıt sonradan tekrar aranmış; camp satırı yeni çağrıya ait)
SELECT campaign_id, cdr_id, contact_id, call_start_time, result_code_id, cdr_update_sql
FROM #urc_fix
WHERE durum = 'CDR_ONLY_ESKI_CAGRI'
ORDER BY campaign_id, contact_id, call_start_time;

-- 3.3 Çakışmalar — aynı pd_cdr satırına birden fazla farklı result_code_id bağlı.
--     Doğru kodu seçmek için wbp_call_result_code detayına elle bakılmalı.
SELECT F.campaign_id, F.cdr_id, F.interaction_id, F.contact_id, F.distinct_rc_count
FROM #urc_fix AS F
WHERE F.durum = 'CAKISMA_ELLE_INCELE'
ORDER BY F.distinct_rc_count DESC, F.campaign_id, F.cdr_id;

-- 3.4 Çakışan satırların ham detayı (hangi call_id hangi result code'u taşıyor?)
SELECT F.cdr_id, F.interaction_id, ARC.[uniqueid], WCRC.[call_id], WCRC.[result_code_id]
FROM #urc_fix AS F
INNER JOIN [gbWebphone_NonTurkcell].[dbo].[ast_registrar_cdr]    AS ARC  ON ARC.[parentid] = F.interaction_id
INNER JOIN [gbWebphone_NonTurkcell].[dbo].[wbp_call_result_code] AS WCRC ON WCRC.[call_id] = ARC.[uniqueid]
WHERE F.durum = 'CAKISMA_ELLE_INCELE'
ORDER BY F.cdr_id, WCRC.[result_code_id];

-- 3.5 Kampanya kolonu tanımsız olanlar (camp tarafı yapılandırma eksik; DBA/config kontrolü ister)
SELECT DISTINCT campaign_id, campaign_name, camp_result_table
FROM #urc_fix
WHERE durum = 'CAMP_KOLON_TANIMSIZ';
GO


/* =============================================================================================
   BÖLÜM 4 — ÇALIŞTIRILACAK SCRIPT ÇIKTISINI ALMA (önerilen yol)
   Çıktı kopyalanıp gözden geçirildikten sonra ilgili bağlantıda çalıştırılır.
   SSMS: "Results to Text" (Ctrl+T) + Query Options > Results > Text > Maximum characters 8192.
   ============================================================================================= */

-- 4.1 PD ana DB'de çalıştırılacak pd_cdr UPDATE'leri
SELECT cdr_update_sql
FROM #urc_fix
WHERE cdr_update_sql IS NOT NULL
ORDER BY campaign_id, cdr_id;

-- 4.2 Kampanya DB'sinde çalıştırılacak camp UPDATE'leri (bağlantı bazlı gruplanmış)
--     Aynı db_connection_string değerine sahip blokları TEK bir oturumda koştur.
SELECT db_connection_string, campaign_id, camp_result_table, camp_update_sql
FROM #urc_fix
WHERE camp_update_sql IS NOT NULL
ORDER BY db_connection_string, campaign_id, contact_id;
GO


/* =============================================================================================
   BÖLÜM 5 — OPSİYONEL: aynı instance içindeyse otomatik uygulama
   ÖN KOŞUL: Bölüm 1'deki db_connection_string bu instance'ı gösteriyor olmalı.
             Farklı sunucu ise BU BÖLÜMÜ ÇALIŞTIRMA; Bölüm 4 çıktısını ilgili sunucuda koştur.
   Cursor bilinçli tercih: her satır bağımsız, hatalı bir satır diğerlerini durdurmasın.
   ============================================================================================= */

/* 5.1 Kampanya sonuç tablosu UPDATE'leri */
DECLARE @sql NVARCHAR(MAX), @ok INT = 0, @err INT = 0;

DECLARE campCur CURSOR LOCAL FAST_FORWARD FOR
    SELECT camp_update_sql FROM #urc_fix
    WHERE durum = 'UYGULANABILIR' AND camp_update_sql IS NOT NULL;

OPEN campCur;
FETCH NEXT FROM campCur INTO @sql;
WHILE @@FETCH_STATUS = 0
BEGIN
    BEGIN TRY
        EXEC sp_executesql @sql;
        SET @ok = @ok + 1;
    END TRY
    BEGIN CATCH
        SET @err = @err + 1;
        PRINT CONCAT('CAMP HATA: ', @sql, ' -> ', ERROR_MESSAGE());
    END CATCH
    FETCH NEXT FROM campCur INTO @sql;
END
CLOSE campCur; DEALLOCATE campCur;
PRINT CONCAT('CAMP tamamlandi. basarili=', @ok, ' hata=', @err);
GO

/* 5.2 pd_cdr UPDATE'leri
       NOT: Kampanya tablosu ile pd_cdr'i tutarlı bırakmak için önce 5.1, sonra 5.2 koşulur.
       Guard'lar sayesinde iki bölüm arasında kesinti olsa bile tekrar çalıştırma güvenlidir. */
DECLARE @sql2 NVARCHAR(MAX), @ok2 INT = 0, @err2 INT = 0;

DECLARE cdrCur CURSOR LOCAL FAST_FORWARD FOR
    SELECT cdr_update_sql FROM #urc_fix
    WHERE cdr_update_sql IS NOT NULL;

OPEN cdrCur;
FETCH NEXT FROM cdrCur INTO @sql2;
WHILE @@FETCH_STATUS = 0
BEGIN
    BEGIN TRY
        EXEC sp_executesql @sql2;
        SET @ok2 = @ok2 + 1;
    END TRY
    BEGIN CATCH
        SET @err2 = @err2 + 1;
        PRINT CONCAT('CDR HATA: ', @sql2, ' -> ', ERROR_MESSAGE());
    END CATCH
    FETCH NEXT FROM cdrCur INTO @sql2;
END
CLOSE cdrCur; DEALLOCATE cdrCur;
PRINT CONCAT('CDR tamamlandi. basarili=', @ok2, ' hata=', @err2);
GO


/* =============================================================================================
   BÖLÜM 6 — DOĞRULAMA (öncesi/sonrası)
   ============================================================================================= */

-- 6.1 pd_cdr tarafında kalan eksik satır sayısı. Backfill sonrası yalnız
--     CAKISMA_ELLE_INCELE satırları kalmalıdır.
DECLARE @sinceDate DATETIME = '2026-08-01';

SELECT COUNT(DISTINCT PC.[id]) AS kalan_eksik_cdr
FROM [gbWebphone_NonTurkcell].[dbo].[pd_cdr]                     AS PC
INNER JOIN [gbWebphone_NonTurkcell].[dbo].[ast_registrar_cdr]    AS ARC  ON PC.[interaction_id] = ARC.[parentid]
INNER JOIN [gbWebphone_NonTurkcell].[dbo].[wbp_call_result_code] AS WCRC ON ARC.[uniqueid]      = WCRC.[call_id]
WHERE PC.[call_start_time]     >= @sinceDate
  AND WCRC.[result_code_id]    IS NOT NULL
  AND PC.[user_result_code_id] IS NULL
  AND PC.[telephony_rc]        IN (10, 24)
  AND PC.[contact_id]          IS NOT NULL;

-- 6.2 Kampanya tarafı doğrulaması: aşağıdaki üretici, camp tablosunda kolon adları dinamik
--     olduğu için SELECT ifadeleri üretir. Çıktıyı kampanya DB'sinde çalıştırıp
--     kalan_eksik_camp = 0 (veya beklenen artık) olduğunu doğrula.
SELECT DISTINCT
    db_connection_string,
    campaign_id,
    CONCAT('SELECT ''', camp_result_table, ''' AS tablo, COUNT(*) AS kalan_eksik_camp FROM ',
           camp_result_table,
           ' WHERE ', user_rc_col, ' IS NULL AND ', key_col, ' IN (',
           STUFF((SELECT ',' + CAST(X.contact_id AS NVARCHAR(30))
                  FROM #urc_fix AS X
                  WHERE X.campaign_id = F.campaign_id AND X.durum = 'UYGULANABILIR'
                  FOR XML PATH('')), 1, 1, ''),
           ');') AS camp_verify_sql
FROM #urc_fix AS F
WHERE F.durum = 'UYGULANABILIR'
  AND F.user_rc_col IS NOT NULL;
GO

/* =============================================================================================
   BİLİNEN SINIRLAR
   -----------------------------------------------------------------------------------------
   1. `MAX(WCRC.result_code_id)` yalnız tek result code'lu satırlarda kullanılır; çakışmalı
      satırlar bilinçli olarak dışarıda bırakılır (bkz. Bölüm 3.3 / 3.4).
   2. Kampanya sonuç tablosunun `rc_sequence`, `attempts`, `update_date` gibi diğer kolonları
      bu script ile DEĞİŞTİRİLMEZ. Yalnız user result code kolonu tamamlanır.
   3. `db_result_table` değeri DB'de şema önekiyle (`dbo.X`) veya köşeli parantezli saklanabilir;
      üretici bu değeri olduğu gibi basar. Çıktının ilk satırları gözle kontrol edilmelidir.
   4. Kaynak ve sonuç tablosu aynı olan kampanyalarda (`db_list_table = db_result_table`)
      anahtar kolon `col_unique_key`'dir; üretici bunu otomatik seçer.
   5. Script yalnız `@sinceDate` sonrası çağrılara bakar. Pencere genişletilirse `last_call`
      CTE'sinin penceresi de aynı değerle genişler (aynı değişken kullanılır).
   6. Bölüm 6.2 doğrulama üreticisi `IN (...)` listesini FOR XML PATH ile kurar; kampanya başına
      çok sayıda satır varsa üretilen ifade uzayabilir. Bu durumda kampanya başına parça parça
      (örn. contact_id aralığı ile) doğrulama yapılmalıdır.
   ============================================================================================= */
