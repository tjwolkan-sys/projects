SET QUOTED_IDENTIFIER ON;
GO

/* ============================================================================
   FEAT-325470 — AMD Turevli Telephony Result Code'lar (id 86 / 87 / 88)
   Revizyon 05.10.2026: code 913/914/915 -> 113/114/115 (santral karari)
   ----------------------------------------------------------------------------
   ELLE CALISTIRILACAK MIGRATION. Bu repoda Flyway/Liquibase yoktur ve
   hibernate.hbm2ddl.auto kapalidir (applicationContext.xml). Script otomatik
   UYGULANMAZ; deploy sirasinda DBA tarafindan calistirilir. Dialect: SQL Server.

   HEDEF: PD ANA DB, tablo dbo.pd_telephony_rc. Her ortamda (Turkcell, Non-Turkcell,
   PRP/test) BIR KEZ calisir; tekrar calistirilabilir (idempotent). Ilk surumle
   (913/914/915) yazilmis ortamlarda da calistirilir: satirlari yeni koda tasir.

   NE YAPAR: santralin ayristirdigi AMD (answering machine detection) sonuclari icin
   uc satiri hedef duruma getirir. Santral (dialplan) DialerHangup UserEvent'inde
   hangup cause olarak CODE kolonundaki degeri gonderir; dialer haritasi da code ile
   anahtarlanir:
     id 86  code 113  AMD Bad Number (unallocated number)  -> Wrong Number
     id 87  code 114  AMD Not Reached                      -> No answer
     id 88  code 115  AMD Unknown Tone                     -> Answering Machine
   AMD busy (code 17 -> id 11) ve AMD no answer (code 19 -> id 13) MEVCUT satirlarla
   karsilanir; yeni satir gerekmez.

   ADIMLAR:
     0. Cakisma korumasi (iki yonlu). Sorun varsa HICBIR SEY yazilmaz.
     1. Ilk surumun 913/914/915 kodlu satirlari varsa UPDATE ile 113/114/115'e tasinir
        (PRP gbWebphone_test ve S2 gbWebPhone_S2_test boyle).
     2. Satir hic yoksa IDENTITY_INSERT ile acik id'yle eklenir (prod boyle).
     3. Dogrulama SELECT.

   NEDEN IDENTITY_INSERT: id kolonu IDENTITY'dir ve ortamlar arasi atlama yapabilir
   (Non-Turkcell'de "Ambiguous" satiri 10083 almisti). Raporlar ve kart id 86/87/88
   bekledigi icin id'ler acikca yazilir. Uygulama kodu yine de YALNIZ code ile karar
   verir (Constants.TELEPHONY_RC_AMD_*).

   NEDEN 113-115: Q.850'de 112..126 atanmamistir, tabloda bostur ve dialer'in ITU hata
   kumesi (ASTERISK_ERROR_CODES) ile kesismez. ITU cause 86/87/88 tabloda ZATEN dolu
   oldugundan (id 53/54/55) ekrandaki id degerleri code olarak kullanilamaz.

   SONRASI: dialer haritayi startup'ta yukler; script sonrasi uygulama RESTART
   edilmelidir. Startup logunda
   "pd_telephony_rc is missing internal result code mapping" ERROR'u gorulmemelidir.
   Santral 113/114/115 gondermeye ancak restart sonrasinda baslamalidir; eski
   913/914/915 degerleri artik haritada yoktur ve 901 fallback'ine (id 73) duser.
   ============================================================================ */

/* ---- 0a. Cakisma korumasi: id 86/87/88 eski/yeni kod disinda bir code ile doluysa dur */
IF EXISTS (SELECT 1 FROM dbo.pd_telephony_rc WHERE id IN (86, 87, 88) AND code NOT IN (113, 114, 115, 913, 914, 915))
BEGIN
    RAISERROR ('FEAT-325470: dbo.pd_telephony_rc id 86/87/88 beklenmeyen bir code ile dolu; script durduruldu. Satirlari DBA ile inceleyin.', 16, 1);
    RETURN;
END

/* ---- 0b. Cakisma korumasi: 113/114/115 baska bir id'de kullaniliyorsa dur ---------- */
IF EXISTS (SELECT 1 FROM dbo.pd_telephony_rc WHERE code IN (113, 114, 115) AND id NOT IN (86, 87, 88))
BEGIN
    RAISERROR ('FEAT-325470: code 113/114/115 dbo.pd_telephony_rc icinde baska bir id tarafindan kullaniliyor; script durduruldu.', 16, 1);
    RETURN;
END

/* ---- 1. Ilk surumun satirlarini yeni koda tasi (yalniz eski kodluysa dokunur) ------- */
UPDATE dbo.pd_telephony_rc SET code = 113 WHERE id = 86 AND code = 913;
IF @@ROWCOUNT > 0 PRINT 'pd_telephony_rc id 86: code 913 -> 113 guncellendi.';

UPDATE dbo.pd_telephony_rc SET code = 114 WHERE id = 87 AND code = 914;
IF @@ROWCOUNT > 0 PRINT 'pd_telephony_rc id 87: code 914 -> 114 guncellendi.';

UPDATE dbo.pd_telephony_rc SET code = 115 WHERE id = 88 AND code = 915;
IF @@ROWCOUNT > 0 PRINT 'pd_telephony_rc id 88: code 915 -> 115 guncellendi.';

/* ---- 2. Eksik satirlari ekle (acik id) ----------------------------------------------- */
SET IDENTITY_INSERT dbo.pd_telephony_rc ON;

IF NOT EXISTS (SELECT 1 FROM dbo.pd_telephony_rc WHERE id = 86)
BEGIN
    INSERT INTO dbo.pd_telephony_rc (id, name, description, code)
    VALUES (86, N'AMD Bad Number (unallocated number)', N'Wrong Number', 113);
    PRINT 'pd_telephony_rc id 86 / code 113 eklendi.';
END
ELSE
    PRINT 'pd_telephony_rc id 86 zaten var.';

IF NOT EXISTS (SELECT 1 FROM dbo.pd_telephony_rc WHERE id = 87)
BEGIN
    INSERT INTO dbo.pd_telephony_rc (id, name, description, code)
    VALUES (87, N'AMD Not Reached', N'No answer', 114);
    PRINT 'pd_telephony_rc id 87 / code 114 eklendi.';
END
ELSE
    PRINT 'pd_telephony_rc id 87 zaten var.';

IF NOT EXISTS (SELECT 1 FROM dbo.pd_telephony_rc WHERE id = 88)
BEGIN
    INSERT INTO dbo.pd_telephony_rc (id, name, description, code)
    VALUES (88, N'AMD Unknown Tone', N'Answering Machine', 115);
    PRINT 'pd_telephony_rc id 88 / code 115 eklendi.';
END
ELSE
    PRINT 'pd_telephony_rc id 88 zaten var.';

SET IDENTITY_INSERT dbo.pd_telephony_rc OFF;

/* ---- 3. Dogrulama: uc satir id/code esli gorunmeli; 900 (AMD) satiri da listelenir ---- */
SELECT id, code, name, description
FROM dbo.pd_telephony_rc
WHERE code IN (900, 113, 114, 115) OR id IN (86, 87, 88)
ORDER BY id;
