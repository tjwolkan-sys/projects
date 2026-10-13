SET QUOTED_IDENTIFIER ON;
GO

/* ============================================================================
   FIX-333607 — pd_cdr (campaign_id, call_start_time) index'i
   ----------------------------------------------------------------------------
   ELLE CALISTIRILACAK MIGRATION. Bu repoda Flyway/Liquibase yoktur ve
   hibernate.hbm2ddl.auto kapalidir (applicationContext.xml). Script otomatik
   UYGULANMAZ; deploy oncesi DBA tarafindan calistirilir. Dialect: SQL Server.

   HEDEF: PD ANA DB, tablo dbo.pd_cdr. Her ortamda (Turkcell, Non-Turkcell,
   PRP/test) bir kez calisir; tekrar calistirilabilir (idempotent).

   NEDEN: GAMMA startup profil sorgulari (CDRDao.findGammaStartupAverage*,
   findGammaTodayAnswerStatsByType) gunu campaign_id + call_start_time araligi ile
   filtreler. IX_pd_cdr_call_start_time yalniz call_start_time uzerindedir;
   campaign filtresi residual kalir ve gunun tum campaign satirlari okunur.

   NE YAPAR: Anahtari (campaign_id, call_start_time) ile baslayan bir index
   yoksa ayri bir index olusturur:
     IX_pd_cdr_campaign_id_call_start_time (campaign_id, call_start_time)
       INCLUDE (call_dial_time, call_answer_time, call_handling_start,
                call_end_time, telephony_rc, outdialing_type_id)
   Boyle bir index zaten varsa (02.10.2026'da Turkcell'de DBA
   IX_pd_cdr_call_start_time'i bu anahtarla yeniden olusturdu) hicbir sey yapmaz.

   IX_pd_cdr_call_start_time'A DOKUNMAZ: interaction notifier procedure'leri
   (or. get_abandonedOutbound_call_info, get_abandoned_pd) pd_cdr'i campaign_id
   esitligi olmadan yalniz call_start_time araligiyla okur; o index'in anahtari
   degisirse bu sorgular seek'ini kaybeder.

   DIKKAT: pd_cdr buyuk bir tablodur. Index olusturma yogun olmayan saatte
   yapilmalidir. SQL Server Enterprise ise CREATE INDEX'e WITH (ONLINE = ON)
   eklenebilir.

   GERI ALMA (gerekirse):
     DROP INDEX IX_pd_cdr_campaign_id_call_start_time ON dbo.pd_cdr;
   ============================================================================ */

IF EXISTS (
    SELECT 1
    FROM sys.indexes i
    WHERE i.object_id = OBJECT_ID('dbo.pd_cdr')
      AND EXISTS (SELECT 1 FROM sys.index_columns ic
                  JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                  WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                    AND ic.key_ordinal = 1 AND c.name = 'campaign_id')
      AND EXISTS (SELECT 1 FROM sys.index_columns ic
                  JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                  WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                    AND ic.key_ordinal = 2 AND c.name = 'call_start_time'))
BEGIN
    PRINT 'FIX-333607: pd_cdr uzerinde (campaign_id, call_start_time) anahtarli index zaten var, atlandi.';
END
ELSE
BEGIN
    CREATE NONCLUSTERED INDEX IX_pd_cdr_campaign_id_call_start_time
        ON dbo.pd_cdr (campaign_id ASC, call_start_time ASC)
        INCLUDE (call_dial_time, call_answer_time, call_handling_start, call_end_time, telephony_rc, outdialing_type_id);
    PRINT 'FIX-333607: IX_pd_cdr_campaign_id_call_start_time olusturuldu.';
END
GO
