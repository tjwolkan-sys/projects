-- =====================================================================================
-- FEAT-321997 — Agent Callback Reset Stratejisi (Timer / Attempt Count)
-- =====================================================================================
-- ELLE ÇALIŞTIRILACAK MIGRATION. Bu repoda Flyway/Liquibase yoktur ve hibernate.hbm2ddl.auto
-- kapalıdır (applicationContext.xml). Bu script otomatik UYGULANMAZ; deploy sırasında DBA/geliştirici
-- tarafından ilgili veritabanlarında elle çalıştırılır. Dialect: SQL Server.
--
-- İki ayrı veritabanı vardır:
--   (1) gbpowerdialer CONFIG DB  -> aşağıdaki ALTER TABLE campaign (4 kolon). Tüm kampanyalar için bir kez.
--   (2) MÜŞTERİ RESULT TABLOSU   -> ATTEMPT_COUNT stratejisi seçilen kampanyaların result tablosuna
--       integer bir "callback_attempts" kolonu eklenir (per-customer onboarding adımı). Kolonun fiziksel
--       adı kampanyada `col_result_callback_attempts` alanına yazılır. Aşağıda örnek verilmiştir.
-- =====================================================================================

-- ---------------------------------------------------------------------------
-- (1) CONFIG DB — campaign tablosu (bir kez, tüm kampanyalar için)
-- ---------------------------------------------------------------------------
-- appointer_reset_strategy: NULL/0 = TIMER (legacy), 1 = ATTEMPT_COUNT (Constants.APPOINTER_RESET_STRATEGY ordinal).
-- NULL bırakılan mevcut kampanyalar legacy TIMER davranışını korur (geçiş manuel).
ALTER TABLE campaign ADD appointer_reset_strategy INT NULL;

-- Attempt dalı: sahip müsait değilken callback_date'in öteleneceği süre (dakika). UI default = 20.
ALTER TABLE campaign ADD agent_cb_reschedule_delay_min INT NULL;

-- Attempt dalı: sahibe ulaşılamayınca kaç denemeden sonra campaign-wide'a (havuza) devredileceği. UI default = 3.
ALTER TABLE campaign ADD agent_cb_convert_after_attempts INT NULL;

-- Result tablosundaki agent-callback deneme sayacı kolonunun fiziksel adı (binding). Telefon retry sayacı
-- olan col_result_attempts'ten AYRIDIR. ATTEMPT_COUNT kampanyaları için zorunlu; aksi halde sayım yapılamaz.
ALTER TABLE campaign ADD col_result_callback_attempts NVARCHAR(255) NULL;

-- ---------------------------------------------------------------------------
-- (2) MÜŞTERİ RESULT TABLOSU — yalnız ATTEMPT_COUNT seçilen kampanyalar için (per-customer)
-- ---------------------------------------------------------------------------
-- Örnek (gerçek tablo/kolon adı kampanya konfigürasyonuna göre değişir):
--   ALTER TABLE <dbResultTable> ADD callback_attempts INT NULL;
--   -- ardından kampanyada: col_result_callback_attempts = 'callback_attempts'
-- Kolon tipi INT/INTEGER/BIGINT/NUMBER olmalıdır (Constants.RESULT_FIELDS_ALLOWED_TYPES doğrular);
-- aksi halde kampanya start'ta "campaign.start.wrong.types" ile reddedilir.
