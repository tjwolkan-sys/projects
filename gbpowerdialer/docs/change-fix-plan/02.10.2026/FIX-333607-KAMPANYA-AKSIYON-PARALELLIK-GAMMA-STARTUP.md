# FIX Kampanya Aksiyon Paralelliği ve GAMMA Startup Sorguları Change/Fix Planı

## Bağlam

02.10.2026 sabahı Turkcell setinde campaign start ve stop işlemleri dakikalarca yanıt vermedi. Operatörler 09:00'da hatta girdiğinde hiçbir campaign start/stop olmuyordu. Elastic (`turkcell-2026.10.02`) ve DBA canlı plan çıktısı ile yapılan incelemenin özeti:

- Tüm campaign aksiyonları (`START`, `STOP_REGULAR`, `STOP_FORCEFUL`, `RESTART`, `PAUSE`, `UNPAUSE`, `RELOAD`, `RESET`) `CampaignService.action()` üzerindeki tek global `synchronized` kilitten sırayla geçiyor.
- GAMMA campaign start'ı bu kilit içinde `pd_cdr` üzerinde startup profil sorguları çalıştırıyor. Sorgular geçmişi büyük campaign'lerde dakikalar sürdü:
  - 08:55:57 → 09:06:32: scheduler'ın C74 start'ı 10,5 dk sürdü ve hata ile fallback'e düştü.
  - 09:07:19 → 09:14:57: C87 start'ı 7,5 dk.
  - 10:23:24 → 10:32:46: C71 start'ı 9 dk 22 sn.
- Bu sürede UI, scheduler ve diğer tetikleyicilerden gelen tüm aksiyonlar kuyrukta bekledi. Tomcat istek thread numaraları `catalina-exec-96`'ya kadar çıktı. Bir campaign'deki sorun bütün campaign'lere yayıldı.
- DBA canlı planında (session 513, `blocking_session_id = 0`, elapsed 67 sn / CPU 0,58 sn) konuşma süresi sorgusu `IX_pd_cdr_campaign_id_call_handling_start` üzerinden campaign'in tüm geçmişini okuyor (40.455 satır), her satır için key lookup yapıyor (31.558) ve gün filtresini (`CAST(c.call_dial_time AS DATE) = (alt sorgu)`) en sona bırakıyordu.
- 6.7.9 deploy'u (yalnız FastAGI havuz boyutu) sorunun kaynağı değildir. 6.7.8 rollback'inde de start'lar 4–7 dk sürdü.
- DBA, `IX_pd_cdr_call_start_time` index'ini `(campaign_id, call_start_time) INCLUDE (call_dial_time, call_answer_time, call_handling_start, call_end_time, telephony_rc, outdialing_type_id)` olarak yeniden oluşturdu. Start/stop takılması bitti, ancak kod tarafında kilit ve sorgu yapısı aynen duruyor.
- Aynı gün action log'da çok sayıda `NullPointerException` görüldü. 214 tanesi promote yolundaki `rawData` NPE'si (promote recovery logu ile 10 dakikalık dilimlerde birebir eşleşti), 128 tanesi campaign context'i olmayan answer event'i, 15 tanesi LSC JSON cevabı, 3 tanesi `CallCleaner` preview kontrolü.

Bu plan; bir campaign'deki sorunun diğer campaign'lerin aksiyonlarını bekletmemesini, hiçbir aksiyonun sınırsız sürmemesini, GAMMA startup sorgularının index'e uygun çalışmasını ve aynı gün görülen NPE'lerin kök nedenlerini kapsar.

## İş Etiketi

fix

## Öncelik

hotfix

## Etkilenen Alanlar

- Campaign lifecycle: `CampaignService.action`, `DialerFacade`, `CampaignContext`, `CampaignStateTemplate`, `CampaignStopJob`, `ScheduledCampaignRunner`, `AutomaticCampaignExecutor`, `CampaignListener`.
- Stop condition tetikleyicileri: `CallOriginator`, `ManagerConnectionDecorator`, `DNCEngine`, `UserLoginLogoutHandler`, `ResourceDistributionServiceSubscriber`.
- Predictive GAMMA: `PredictiveGammaCallManager.buildStartupProfile`, `CDRDao` GAMMA startup sorguları, `ICDRDao`, `CDRService`.
- AMI answer akışı: `ManagerConnectionDecorator.handleCallAnswer`.
- Redis webphone result code akışı: `WebphoneSubscriber` promote yolu, `Call.getRetryNumber`.
- Diğer: `LSCEngine.processJSON`, `CallCleaner.clearExpiredPreviewCallsForUser`, `DBEngine.dataSources`.
- DB: `pd_cdr` index'i (Turkcell'de DBA tarafından oluşturuldu; diğer setler için migration script'i).

## Mevcut Davranış

- `CampaignService.action()` (`CampaignService.java:1055`) `@Transactional public synchronized` tanımlı. Spring proxy önce transaction açıyor (HibernateTransactionManager, JDBC bağlantısı alınır), sonra thread kilidi bekliyor. Bekleyen her istek ana DB havuzundan (DBCP2, `maxTotal=200`) bağlantı tutuyor. Kilit, transaction commit'inden önce bırakıldığı için sıradaki istek commit edilmemiş campaign satırını görebiliyor (gün içinde görülen `StaleObjectStateException`).
- `action()` 12 farklı yerden çağrılıyor: UI (`CampaignController`), scheduler (`ScheduledCampaignRunner:169,183,193,203`, `AutomaticCampaignExecutor:71,73,83`), restart listener (`CampaignListener:28`) ve stop condition (`CallOriginator:670`, `ManagerConnectionDecorator:1439`, `DNCEngine:767`, `UserLoginLogoutHandler:225`, `ResourceDistributionServiceSubscriber:86`). Stop condition çağrıları AMI event worker, originate ve Redis thread'lerinden senkron yapılıyor; kilit tutulurken bu thread'ler de bekler.
- `ScheduledCampaignRunner` tek thread'de tüm campaign'leri sırayla senkron işliyor; bir campaign'in uzun start'ı tüm zamanlanmış aksiyonları durduruyor (02.10.2026'da C74 start'ı boyunca C75 ve diğerleri bekledi).
- `PredictiveGammaCallManager.buildStartupProfile` (`:440`) beş sorgu çalıştırıyor: konuşma ortalaması, çalma ortalaması (Gamma Plus'a göre iki varyanttan biri), ACW ortalaması, bugünkü cevap oranı. Yalnız ACW sorgusunda 30 sn timeout var (`CDRDao:24`); diğerleri sınırsız. Herhangi bir sorgu hata verirse tüm profil varsayılanlara düşüyor.
- Konuşma ve çalma sorguları (`CDRDao:198,229,265`) günü `CAST(c.call_dial_time AS DATE) = (SELECT TOP 1 CAST(call_handling_start AS DATE) ...)` ile filtreliyor. `call_dial_time` indexsiz olduğu için campaign'in tüm geçmişi okunuyor. Son çalışma günü alt sorgusu her sorguda tekrar çalışıyor.
- Aynı gün içinde campaign yeniden start edildiğinde dünkü ortalamalar baştan hesaplanıyor (02.10.2026'da C71, C85, C91, C134 birden fazla kez).
- `CampaignStateTemplate.start()` (`:40`) campaign'i ilk adımda `RUNNING` yapıyor; GAMMA init ve producer/consumer start'ı sonra geliyor. Ekran start sürerken `RUNNING` gösteriyor ama veri çekme ve arama henüz başlamamış oluyor.
- Çalışan campaign'de predictive strateji UI'dan değiştirilebiliyor; GAMMA manager yalnız start'ta init edildiği için ALPHA→GAMMA geçişinde `allowedDialNow` sıfır dönüyor (01.10.2026 olayı).
- `CampaignContext` (`:53`) her context için yeni bir single-thread stop executor açıyor ve kapatmıyor; her stop'ta bir daemon thread sızıyor.
- `DBEngine.dataSources` (`:44`) senkronize olmayan `HashMap`; eşzamanlı datasource oluşturma (02.10.2026 `InstanceAlreadyExistsException: DataSource=campaign91`) zaten görülüyor.
- Promote yolu (`WebphoneSubscriber:477`, sentetik çağrı `:567`) `rawData` taşımayan sentetik `Call` ile `insertCallResultToDB` çağırıyor; `Call.setRetryNumberFromRawData` (`Call.java:326`) `rawData.has(...)` ile NPE atıyor. `pd_cdr` güncellenmiş oluyor, campaign liste tablosu güncellenmiyor (02.10.2026'da 214 kayıt). Sık tekrarlandığı için JVM stack'i kırpıyor; action log'da mesajsız `java.lang.NullPointerException` görünüyor.
- `ManagerConnectionDecorator.handleCallAnswer` (`:758,768`) `getCampaignContexts().get(campaignId).getCampaign()` çağırıyor; context yoksa (campaign stop edilmiş) NPE ile answer işleme yarıda kalıyor (02.10.2026 10:05'te 121 + gün içinde 7).
- `LSCEngine.processJSON` (`:589`) reply key cevapta yoksa `JsonPath.read(...)` null dönüyor, `toString()` NPE atıyor; catch bloğu ERROR loglayıp `FAIL` dönüyor.
- `CallCleaner.clearExpiredPreviewCallsForUser` (`:93`) `originationDate` null kontrolünden önce `Utils.convertDoubleToLongDate(call.getOriginationDate())` çağırıyor; null tarihte NPE.

## Beklenen Davranış

- Aynı campaign'in aksiyonları sıralı kalır (Bug #5637 koruması), farklı campaign'lerin aksiyonları birbirini beklemez.
- Kilit transaction'dan önce alınır ve commit'ten sonra bırakılır; kilit bekleyen istek DB bağlantısı tutmaz.
- Aynı campaign'e gelen ikinci istek sınırlı süre bekler; alamazsa açık bir hata ile döner, thread birikmez.
- Scheduler meşgul campaign'i o tur atlar ve diğer campaign'leri işler; uzun bir aksiyon scheduler'ı bloklamaz.
- Stop condition tetikleyicileri (AMI, originate, DNC, RDS, logout) aksiyonu asenkron bırakır; event ve originate thread'leri beklemez.
- GAMMA startup sorgularının her biri en fazla 10 sn sürer; zaman aşımında WARN log basılır ve yalnız o değer varsayılana düşer (ACW için mevcut 0 fallback korunur).
- Dünkü profil değerleri campaign ve tarih bazında bellekte tutulur; aynı gün yeniden start'ta ağır sorgular çalışmaz.
- Son çalışma günü bir kez hesaplanır; konuşma ve çalma ortalamaları tek sorguda, `call_start_time` aralığıyla ve yeni index ile alınır.
- Campaign GAMMA init tamamlanana kadar `STARTING` görünür; `RUNNING` start bitince set edilir.
- Çalışan veya duraklatılmış campaign'de predictive strateji değişikliği backend'de reddedilir.
- Promote yolu NPE olmadan campaign liste tablosunu günceller; answer event'i context yokken exception üretmez; LSC eksik reply key ve preview null tarih NPE üretmez.
- Uzun aksiyonlar ve kilit beklemeleri loglarda görünür olur.

## Kod Okuma Bulguları

- `CampaignService.action` `@Transactional` + `synchronized`; `proxy-target-class=true` CGLIB proxy'si transaction'ı monitörden önce açar (`applicationContext.xml:72,75`).
- `DialerFacade` start/stop/pause/unpause/reload/reset metotları yalnız kendi campaign'inin `campaignContexts` girdisi üzerinde check-then-act yapıyor; kampanyalar arası ortak yapılar (`campaignContexts`, `asteriskCallOriginators` `ConcurrentHashMap`; `CallOriginator.callQueues` `CopyOnWriteArrayList`; DNC/LSC `runningCampaigns` synchronized set; GAMMA map'leri `ConcurrentHashMap`) thread-safe. İstisna `DBEngine.dataSources`.
- Global kilit 2017'de (`3c767461`, Bug #5637 "Campaign make calls even if stopped") aynı campaign'e eşzamanlı start/stop yarışını önlemek için eklenmiş; campaign bazlı kilit bu korumayı sağlar.
- Regular stop beklemesi (`CampaignStopJob.waitCallsInProgressCompletion`, en fazla 1 saat) campaign başına ayrı executor'da çalışıyor; global kilidi tutmuyor.
- `CAMPAIGN_STATE.RUNNING` okuyucuları (11 nokta): `CallConsumer:58`, `CallOriginator:378`, `CallProducer:202`, `DialerFacade:350,455,475`, `SharedGammaSnapshotProvider:191`, `AutomaticCampaignExecutor:65`, `ScheduledCampaignRunner:147`, `CampaignRecordService:1321`. `shutdownCampaign` ve `pauseCampaign` `RUNNING` bekliyor; campaign bazlı kilit sayesinde start ile çakışmazlar.
- GAMMA runtime ACW yenilemesi (`PredictiveGammaCallManager:560`, `findGammaCurrentAverageAcwTime`) webphone event thread'inde 30 sn timeout'lu sorgu çalıştırıyor (02.10.2026 10:32:24 `webphone-event-1`).
- Turkcell `pd_cdr` index'leri (DBA listesi): `IX_pd_cdr_call_start_time (campaign_id, call_start_time) INCLUDE (call_dial_time, call_answer_time, call_handling_start, call_end_time, telephony_rc, outdialing_type_id)`, `IX_pd_cdr_campaign_id (campaign_id, contact_id)`, `IX_pd_cdr_campaign_id_call_handling_start (campaign_id, call_handling_start)`, `IX_pd_cdr_interaction_id`, `IX_pd_cdr_tenant_id`.
- `findCdrForGammaPrePopulationByType`, `findSuccessfulCdrForGammaPrePopulation`, `findRingCdrForGammaPrePopulation` hiçbir yerden çağrılmıyor.
- `updateCampaignListTableRow` retry number kolonunu yalnız `getRetryNumber() != null` iken yazıyor; `rawData` okuyan diğer noktaların bir kısmı zaten null-safe (`:2440`).
- Bugünkü cevap oranı sorgusu `call_start_time` aralığı kullanıyor; yeni index ile kod değişmeden seek'e dönmesi bekleniyor (index sonrası hâlâ ~28 sn ölçüldü, DBA doğrulaması bekleniyor).

## Root Cause / Değişiklik Gerekçesi

1. Tek global kilit, tek bir yavaş aksiyonun bütün set'in campaign aksiyonlarını durdurmasına yol açıyor. Kilidin transaction içinde beklenmesi DB bağlantı havuzunu da tüketme riski taşıyor.
2. Scheduler ve stop condition tetikleyicileri aksiyonu senkron çağırdığı için yayılma alanı UI'ın ötesine, scheduler ve AMI/Redis thread'lerine kadar uzanıyor.
3. GAMMA startup sorguları index'e uygun yazılmamış (indexsiz kolonda `CAST` ile gün filtresi) ve süre sınırı olmadan kilit içinde çalışıyor. Süre campaign geçmişiyle büyüyor; DB'de herhangi bir yavaşlık doğrudan tüm aksiyonları kilitliyor.
4. NPE'ler savunmasız null erişimlerinden geliyor: sentetik çağrıda `rawData` yok, stop sonrası context yok, LSC cevabında key yok, preview çağrıda origination tarihi yok.

## Riskler

- Global kilit kaldırılınca daha önce kilidin dolaylı koruduğu ortak yapılarda yarış ortaya çıkabilir. Bilinen tek senkronize olmayan yapı `DBEngine.dataSources`; aynı iş içinde düzeltilir. Uygulama sırasında start/stop yolunun dokunduğu diğer paylaşılan yapılar tekrar taranır.
- `@Transactional` kaldırılıp `TransactionTemplate` kullanılırken iç `@Transactional` metotların (`updateStoppedDate`, `updateStopType`, `getInitializedCampaign`) propagation davranışı korunmalı.
- `STARTING` durumunun daha uzun görünmesi UI, API, Interaction Notifier (`CampaignInDTO`) ve scheduler karar mantığını etkileyebilir; `RUNNING` okuyucuları tek tek doğrulanır.
- Scheduler ve stop condition aksiyonlarının asenkron hale gelmesi sıra ve tekrar semantiğini değiştirir; aynı campaign için in-flight aksiyon birleştirilmezse tekrar tetiklemeler oluşabilir.
- Sorgu yeniden yazımında gün filtresi `call_dial_time` yerine `call_start_time` ile yapılır; yalnız gece yarısına denk gelen çağrılarda fark oluşur, ortalamaya etkisi ihmal edilebilir. Yeni index olmayan setlerde sorgu yavaş kalabilir; migration script'i deploy öncesi uygulanmalıdır.
- Develop dalında 6.7.9'da olmayan 8 PR ve iki manuel migration (`FEAT-325559`, `FEAT-325470`) var; sürüm yolu seçimi deploy ön koşullarını belirler.

## TDD Stratejisi

Önce mevcut davranışı kilitleyen karakterizasyon testleri, sonra başarısız regression testleri, ardından en küçük üretim kodu. Gerçek DB, AMI veya Redis kullanılmaz; executor, latch ve mock boundary'leri kullanılır, sleep'e dayalı test yazılmaz.

- `tests/campaign/CampaignActionPerCampaignLockRegressionTest`:
  - Farklı iki campaign'in aksiyonu paralel ilerler (A'nın start'ı latch ile bekletilirken B'nin stop'u tamamlanır).
  - Aynı campaign'in aksiyonları sıralı kalır.
  - Kilit süre aşımında `CampaignIllegalStateException` döner.
  - Kilit transaction açılmadan önce alınır ve commit'ten sonra bırakılır (`TransactionTemplate` mock sırası).
  - Aynı thread içinde iç içe aksiyon kilitlenmez.
- `tests/campaign/CampaignActionAsyncDispatchRegressionTest`: stop condition çağrısı çağıran thread'i bloklamaz; aynı campaign için in-flight aksiyon birleştirilir; meşgul campaign scheduler turunda atlanır.
- `ScheduledCampaignRunnerTest` genişletilir: bir campaign'in uzun aksiyonu diğer campaign'lerin değerlendirmesini geciktirmez.
- `tests/predictive/GammaStartupQueryTimeoutRegressionTest`: bir startup sorgusu zaman aşımına düşünce WARN loglanır, yalnız o değer varsayılana düşer, diğerleri korunur; ACW fallback 0 kalır.
- `tests/predictive/GammaStartupProfileDailyCacheRegressionTest`: aynı gün ikinci start'ta dünkü profil sorguları çalışmaz; tarih değişince yeniden hesaplanır; bugünkü cevap oranı her start'ta canlı okunur.
- `tests/persistence/GammaStartupQueryShapeContractTest`: konuşma ve çalma sorgusu tek sorgudur, `call_start_time` aralığı kullanır, `CAST(c.call_dial_time AS DATE)` içermez, timeout uygulanır; son çalışma günü sorgusu `call_handling_start < CAST(GETDATE() AS DATE)` biçimindedir.
- `StartStateTest` genişletilir: GAMMA init tamamlanana kadar state `STARTING`, sonra `RUNNING`.
- `tests/campaign/PredictiveStrategyChangeGuardRegressionTest`: çalışan/duraklatılmış campaign'de strateji değişikliği reddedilir, stopped campaign'de kabul edilir, strateji değişmeyen update etkilenmez.
- `WebphoneSubscriberTest` (mevcut 4 promote testi): promote sonrası `pd_cdr` güncellenir, campaign liste tablosu için `insertCallResultToDB` çağrılmaz (açık soru 7, seçenek a).
- `tests/ami/AnswerEventWithoutCampaignContextRegressionTest`: context yokken answer event'i exception üretmez, CDR answer zamanı ve sonuç yazımı devam eder.
- `LSCEngineTest` genişletilir: reply key cevapta yoksa NPE oluşmaz, sonuç `FAIL` kalır, WARN basılır.
- `tests/persistence/GammaAcwQueryTimeoutContractTest`: startup ve runtime ACW sorguları 10 sn timeout ile çalışır; timeout'ta WARN basılır ve `0.0` döner (mevcut fallback korunur).
- `tests/ami/CallCleanerPreviewOriginationNullRegressionTest`: `originationDate` null olan çağrıda NPE oluşmaz.
- `tests/dialer/DbEngineDataSourceConcurrencyRegressionTest`: aynı campaign için eşzamanlı datasource isteği tek datasource üretir.

## Açık Sorular

- [x] 1. Kilit bekleme süresi 10 sn; süre dolunca kullanıcıya uyarı verilir. Yanıt (02.10.2026): evet. Yeni mesaj anahtarı `campaign.action.inProgress` mevcut `campaign.edit.illegal.state` ile aynı yerde, `ValidationMessages*.properties` (varsayılan, `tr_TR`, `ru_RU`, `ua_UA`) dosyalarına eklenir; mesaj backend'den döndüğü için AngularJS dosyası değişmez.
- [x] 2. Sürüm yolu. Yanıt (02.10.2026): fix develop üzerinden çıkacak (dal `origin/develop`'tan alındı). Deploy ön koşulları: develop'taki `FEAT-325559` ve `FEAT-325470` manuel migration'ları ile bu işin index migration script'i her sette uygulamadan önce çalıştırılır.
- [x] 3. LSC eksik reply key. Yanıt (02.10.2026): mevcut davranış korunur (`FAIL`), farklı davranış oluşturulmaz; yalnız NPE/ERROR yerine anlamlı WARN basılır.
- [x] 4. Strateji değişikliği engeli. Yanıt (02.10.2026): yalnız backend; kaydetme reddedilir, mevcut hata gösterimi kullanılır.
- [x] 5. GAMMA runtime ACW yenilemesi. Yanıt (02.10.2026): timeout 10 sn'ye indirilir ve timeout'ta WARN basılır (startup ve runtime ACW sorguları aynı `GAMMA_ACW_QUERY_TIMEOUT_SECONDS` sabitini kullanıyor).
- [x] 6. Runtime ACW yenilemesi zaman aşımına düşünce `executeGammaAcwAverageQuery` `0.0` döndürüyor ve `refreshAcwIfNeeded` campaign'in mevcut `avgAcw` değerini 0 ile eziyor. Yanıt (02.10.2026): mevcut davranış korunur (0 kalır); yalnız timeout 10 sn'ye iner ve WARN basılır.
- [x] 7. Promote yolunda liste tablosu nasıl güncellensin? Sentetik çağrı gerçek çağrının durumunu (deneme sayısı, retry/callback, tamamlanma) taşımıyor. `updateCampaignListTableRow` `attempts`, `retry_date = NULL` ve `callback_date = NULL` alanlarını koşulsuz, `complete_date`'i ise yalnız doluysa yazıyor. Tüm `rawData` NPE'leri susturulursa satır `attempts = 0`, retry/callback temizlenmiş ve tamamlanmamış hale gelir; fresh sorgusu bu kaydı yeniden çekip müşteriyi tekrar arar. Bugünkü NPE bu update'i engellediği için şu an satıra hiç dokunulmuyor. Seçenekler: (a) sentetik çağrıda liste tablosu update'i atlanır, NPE yerine WARN basılır (bugünkü veri sonucu korunur, gürültü kalkar); (b) yalnız sonuç alanları (`user_result_code`, `telephony_result_code`, `user_id`) yazılır, zamanlama alanlarına dokunulmaz; (c) kayıt tamamlanmış olarak işaretlenir (`complete_date` + sonuç alanları), retry/callback temizlenir. Yanıt: (a). Sentetik çağrı hem update hem insert yolunda `getResultCodeSequence`'te NPE atıyordu, yani liste tablosuna hiç yazılamıyordu; promote yolundan liste yazımı kaldırıldı, yerine WARN basılıyor. Veri sonucu bugünküyle aynı.

## Execution Plan

### Hazırlık

- [x] Açık sorular cevaplandı ve plana işlendi.
- [x] Geliştirici planı onayladı (02.10.2026, "planı yapalım hadi başla" ve açık soru yanıtları).
- [x] `docs/architecture/CAMPAIGN-LIFECYCLE.md` ve `docs/architecture/DIALER-RUNTIME.md` okundu; aksiyon tetikleyicileri haritası çıkarıldı.

### A. Paralellik

- [x] A1. `CampaignService.action` aksiyon dağıtımı `CampaignServiceActionLockRegressionTest` ile kilitlendi (START → `startCampaign`, STOP → `shutdownCampaign`, transaction sırası).
- [x] A2. Campaign bazlı kilit `CampaignActionCoordinator` sınıfında (`ConcurrentMap<Long, ReentrantLock>`, instance alanı); `CampaignService.action` üzerindeki `synchronized` ve `@Transactional` kaldırıldı, gövde `TransactionTemplate` içinde çalışıyor; kilit transaction'dan önce alınıyor, commit sonrası bırakılıyor.
- [x] A3. Kilit 10 sn içinde alınamazsa `CampaignIllegalStateException("campaign.action.inProgress")`; anahtar dört `ValidationMessages*.properties` dosyasına eklendi; bekleme ve süre aşımı WARN loglanıyor.
- [x] A4. Asenkron dağıtım `CampaignService.actionAsync` → `CampaignActionCoordinator.submit`: 8 thread'lik havuz, 1000'lik kuyruk, campaign + aksiyon bazında in-flight birleştirme; kilit 10 sn'de alınamazsa aksiyon WARN ile bırakılır (scheduler bir sonraki turda, stop condition sonraki çağrıda yeniden tetikler).
- [x] A5. `ScheduledCampaignRunner` ve `AutomaticCampaignExecutor` `actionAsync` kullanıyor; karar mantığı ve `manuallyPaused` davranışı değişmedi.
- [x] A6. Stop condition çağrıları (`CallOriginator`, `ManagerConnectionDecorator`, `DNCEngine`, `UserLoginLogoutHandler`, `ResourceDistributionServiceSubscriber`) `actionAsync` kullanıyor.
- [x] A7. `CampaignListener` senkron kaldı (`StopConditionReachedException`/`DateMismatchException` sözleşmesi); restart'ta stop job aksiyon kilidini tutmadığı için çakışma yok. UI (`CampaignController`) ve `cancelRestart` senkron.
- [x] A8. Plandan sapma: stop executor kapatılmadı (restart akışında aynı context'e sonradan stop işi gelebiliyor, kapatılırsa reddedilir). Bunun yerine tek thread'li, FIFO, boşta 60 sn sonra thread'ini bırakan `ThreadPoolExecutor` kullanıldı (`CampaignContext.createCampaignStopExecutor`).
- [x] A9. `DBEngine.dataSources` `ConcurrentHashMap`; datasource oluşturma campaign bazlı kilitle tekil; null sahip (geçici datasource) okumaları korundu; mevcut `containsKey` → `get` semantiği korundu (`DBEngineTest` 35/35).
- [x] A10. Teşhis logu: aksiyon 5 sn'yi veya kilit beklemesi 2 sn'yi aşarsa `[CAMPAIGN-ACTION]` WARN.

### B. Süre Sınırı ve Önbellek

- [x] B1. GAMMA startup sorgularına (konuşma, çalma, cevaplanan çalma, bugünkü cevap oranı) 10 sn timeout; zaman aşımında `[GAMMA] cid=... <sorgu> timed out after 10 seconds` WARN; ortalamalar `null` (startup varsayılanı), bugünkü cevap oranı boş liste (startup sabitleri) döner.
- [x] B2. `buildStartupProfile` sorgu bazında fallback yapıyor; bir sorgunun hatası yalnız o değeri varsayılana düşürüyor.
- [x] B3. Dünkü profil (konuşma, çalma, ACW) campaign başına, tarih ve Gamma Plus bayrağıyla önbellekte; tarih değişince yeniden hesaplanıyor; konuşma veya çalma değeri yoksa önbelleğe alınmıyor; bugünkü cevap oranı her start'ta canlı okunuyor.
- [x] B4. `GAMMA_ACW_QUERY_TIMEOUT_SECONDS` 30 → 10 sn (startup ve runtime); timeout WARN'ı ve 0 fallback'i korundu.

### C. GAMMA Sorguları

- [x] C1/C2. Plandan sapma: sorgular birleştirilmedi; mevcut `CDRService` metot sözleşmesi ve onu mock'layan 4 test sınıfı korundu. Her ortalama sorgusu son çalışma gününü türetilmiş tabloda bir kez okuyor (`call_handling_start < CAST(GETDATE() AS DATE) ORDER BY call_handling_start DESC`, `IX_pd_cdr_campaign_id_call_handling_start` backward seek) ve günü `c.call_start_time >= lw.working_day AND c.call_start_time < DATEADD(day, 1, lw.working_day)` aralığıyla filtreliyor (`(campaign_id, call_start_time)` index'i). `CAST(c.call_dial_time AS DATE)` kaldırıldı. B3 önbelleği sayesinde sorgular campaign başına günde bir kez çalışıyor; birleştirmenin ek kazancı yok.
- [x] C3. Kullanılmayan prepopulation sorguları (`CDRDao`/`ICDRDao` 3 metot, `CDRService` 5 sarmalayıcı) kaldırıldı; repo genelinde referans yoktu.
- [x] C4. `src/main/resources/sql/migration/FIX-333607-pd-cdr-campaign-call-start-index.sql`: idempotent; anahtarı `(campaign_id, call_start_time)` ile başlayan index yoksa ayrı `IX_pd_cdr_campaign_id_call_start_time (campaign_id, call_start_time) INCLUDE (...)` oluşturur, varsa (Turkcell'de DBA'nın yeniden oluşturduğu `IX_pd_cdr_call_start_time`) atlar. `IX_pd_cdr_call_start_time`'a dokunmaz: interaction notifier procedure'leri (`get_abandonedOutbound_call_info`, `get_abandoned_pd`) `pd_cdr`'ı campaign eşitliği olmadan yalnız `call_start_time` aralığıyla okur.

### D. Doğruluk ve Sağlamlık

- [x] D1. `CampaignStateTemplate.start()`: `RUNNING` predictive init'ten sonra, producer/consumer'dan önce `finally` içinde set ediliyor; init hata verse de son durum eskisi gibi `RUNNING` (regular stop çalışmaya devam eder).
- [x] D2. Strateji engeli (`CampaignService.checkPredictiveStrategyChangeAllowed(Long, CampaignFormDTO)`): değişikliği runtime'a init'siz uygulayan iki kaydetme yolu (düz kaydet `PUT /campaign/{id}/response` ve kaydet + reload) kayıttan önce çağırır; formdaki strateji runtime'daki (init edilmiş) stratejiden farklıysa ve campaign çalışıyor/duraklatılmışsa `campaign.edit.predictiveStrategy.illegal.state` döner. Kaydet + restart engellenmez (stop eski stratejiyi kapatır, start yenisini init eder). Form strateji göndermiyorsa (Tech/NonTech yetkisi) değişiklik yok sayılır. Yalnız backend. İlk sürümde kontrol `merge` içindeydi; E6'da taşındı.
- [x] D3. `WebphoneSubscriber.promoteCdrToSuccessFromWebphoneResultCode` sentetik çağrıyla `insertCallResultToDB` çağırmıyor; `Webphone result code recovery: campaign list row is not updated for synthetic call` WARN'ı basılıyor. `pd_cdr` promote'u değişmedi. `Call.setRetryNumberFromRawData` değişikliği geri alındı (gerek kalmadı). "Açık AMI bağlantısı yoksa promote'u atla" koşulu davranış değişmesin diye korundu.
- [x] D4. `ManagerConnectionDecorator.handleCallAnswer`: context yoksa context sayaçları atlanıyor, CDR answer zamanı, sonuç yazımı ve customer answer event'i devam ediyor; dosyadaki üçüncü context erişimi (`updateCallCampaignIfNeeded`) zaten null-safe.
- [x] D5. `LSCEngine.processJSON`: reply değeri null ise sonuç `FAIL` (mevcut davranış), NPE/ERROR yerine `[LSC] ... reply key ... has no value in response` WARN.
- [x] D6. `CallCleaner.clearExpiredPreviewCallsForUser`: `originationDate` null ise çağrı atlanıyor, dönüşüm null kontrolünden sonra.

### E. Regresyon Doğrulama Turu

Tüm diff, testlerin göremeyeceği riskler (Spring proxy/transaction, async sıralama, sorgu anlamı, index etkisi) için gözden geçirildi. Bulunan ve düzeltilen sorunlar:

- [x] E1. `GenericService` sınıf seviyesinde `@Transactional(readOnly = true)` taşıyor; `action` üzerindeki metot seviyesi `@Transactional` kaldırılınca proxy kilitten önce salt okunur transaction açıyordu ve içteki `TransactionTemplate` buna katılıyordu (Hibernate flush MANUAL). `action`/`actionAsync` `@Transactional(propagation = SUPPORTS)` aldı. Test: `CampaignServiceActionLockRegressionTest.actionEntryPointsDoNotInheritReadOnlyClassLevelTransaction` (önce kırmızıydı).
- [x] E2. `ScheduledCampaignRunner` ve `AutomaticCampaignExecutor` aynı turda RESET ardından START gönderiyor; 8 thread'lik havuzda START kilidi önce alabiliyordu. `CampaignActionCoordinator.submit` campaign başına sıralı kuyruk (`MoreExecutors.newSequentialExecutor`) kullanıyor; aynı campaign FIFO, farklı campaign'ler paralel. Test: `submittedActionsOfSameCampaignRunInSubmissionOrderEvenIfPoolRunsLastFirst` (önce kırmızıydı).
- [x] E3. Asenkron aksiyon çağıranın güvenlik context'iyle çalışıyor (`DelegatingSecurityContextRunnable`); action log kullanıcısı senkron çağrıdaki gibi kalıyor. Test: `actionAsyncRunsWithCallerSecurityContext` (önce kırmızıydı).
- [x] E4. Migration `IX_pd_cdr_call_start_time`'ın anahtarını değiştiriyordu; interaction notifier procedure'leri `pd_cdr`'ı campaign eşitliği olmadan `call_start_time` aralığıyla okuduğu için seek'lerini kaybederdi. Script ayrı index oluşturacak şekilde değiştirildi (C4).
- [x] E5. LSC WARN'ı diğer LSC logları gibi `LOG_MODULE.LSC` ile basılıyor (action log'da görünür).

Kontrol edilip sorun bulunmayanlar: `start()` sırasında state okuyan bileşen yok; strateji engeli enum karşılaştırıyor ve UI alanı yalnız kullanıcı değiştirirse farklı gönderiyor; `call_start_time` her CDR'da `CallProducer`'da dolduruluyor (gün filtresi farkı yalnız gece yarısı sınırında); önbellek anahtarı sorgu girdilerini (campaign, gammaPlus) kapsıyor; SQL Server `HY008` Hibernate 5.3.22'de `QueryTimeoutException` olur ve transaction'ı rollback-only işaretlemez; `DBEngine` map'ine null anahtar/değer girmiyor; async'e geçen stop-condition çağıranlarının hiçbiri senkron sonuca bakmıyor; LSC sonucu eskisi gibi FAIL.

### F. İkinci Regresyon Doğrulama Turu (05.10.2026)

Bulunan ve düzeltilen sorunlar:

- [x] E6. Strateji engeli `merge` içindeydi: (1) Tech/NonTech yetkili kullanıcıların formu izinli olmayan alanları null gönderir (`PermissionUtil.canEditCampaign`), strateji null geldiği için çalışan predictive campaign'de bu kullanıcıların her kaydı reddedilirdi; (2) strateji `col*`/`db*` alanlarıyla birlikte değişince UI kaydet + restart'ı seçer, restart stratejiyi doğru init ettiği halde engellenirdi. Kontrol iki endpoint'e taşındı, runtime stratejisiyle karşılaştırılıyor, null strateji değişiklik sayılmıyor. Testler: `PredictiveStrategyChangeGuardRegressionTest` (null strateji dâhil 6 test), `CampaignSaveStrategyGuardRoutingRegressionTest` (4 test).
- [x] E7. GAMMA startup ACW sorgusu zaman aşımında 0 döner; günlük önbellek bu 0'ı gün boyu tutuyordu ve eskiden her start'ta yeniden denenen ACW denenmiyordu. ACW önbelleğe yalnız sıfırdan büyükse girer; değilse sonraki start yalnız ACW'yi yeniden sorgular. Test: `acwTimeoutFallbackIsNotCachedAndIsRetriedOnNextStart`.
- [x] E8. Scheduler aksiyonları asenkron olduğu için 60 sn'lik bir sonraki tur önceki turun aksiyonu bitmeden bayat state görebiliyordu (toplu start'ta RESET → START kuyruktayken ikinci bir RESET). `ScheduledCampaignRunner` bekleyen/çalışan aksiyonu olan campaign'i o tur değerlendirmez (`CampaignService.hasPendingAction`). Schedule handler'ları yalnız zaman penceresine göre bayrak üretir, tur atlamak olay kaybettirmez. Testler: `campaignWithPendingActionIsNotEvaluatedThisTick`, `hasPendingActionCoversQueuedRunningAndSynchronousActions`.
- [x] E9. RESTART'ın stop job'ı bitince `CampaignListener` START'ı aynı campaign kilidini 10 sn bekliyordu; eskiden global kilitte süresiz beklerdi, sınır aşılırsa campaign STOPPING'de kalırdı. `CampaignService.startAfterRestart` 5 dk bekler. Testler: `CampaignListenerRestartFollowUpRegressionTest`, `runExclusiveWithLongerWaitOutlastsDefaultWait`, `startAfterRestartStartsCampaign`.
- [x] E10. `hasPendingAction`, `checkPredictiveStrategyChangeAllowed`, `startAfterRestart` `@Transactional(propagation = SUPPORTS)`: sınıf seviyesi readOnly niteliği her scheduler turunda campaign başına DB bağlantısı açmasın.
- [x] E11. `CampaignStateTemplate.java` indekste karışık satır sonlu; değişmeyen 43 satırın satır sonu orijinal haline döndürüldü (commit diff'i yalnız gerçek değişiklik).

Kontrol edilip sorun bulunmayanlar: tek `CampaignService` bean'i (DispatcherServlet alt context'inde component-scan yok), dolayısıyla kilitler paylaşılıyor; `doAction` gövdesi eski `action` gövdesiyle birebir; `synchronized` yalnız `action`'daydı; yeni mesaj anahtarları `LocaleService` üzerinden 4 dilde çözülüyor ve `ArgumentativeException` handler'ı ile 400 döner; promote sentetik çağrısı UI'da zorunlu olan attempts kolonunda (`getAttempts`) SQL'den önce NPE atıyordu, yani liste satırı yazılamıyordu (attempts kolonu olmadan API ile kurulmuş campaign istisna olabilir); `cancelRestart` ve `restartRunningCampaign` transaction davranışı eskisiyle aynı; çalışan campaign'e ikinci START ve STOP no-op; Guava `SequentialExecutor` reddedilen işi kuyruktan çıkarıp istisnayı iletir; `AutomaticCampaignExecutor.iterateCampaigns` üretimde çağrılmıyor.

Kalan riskler (kod dışı karar):

- ACW sorguları (`wbp_interaction_status`, `wbp_user_status`) Turkcell index listesine göre bileşik index'ten yararlanamıyor. Son 10 günde runtime ACW sorgusu 30 sn'yi hiç aşmadı, startup ACW iki kez aştı; 10 sn'yi ne sıklıkla aşacağı bilinmiyor. Zaman aşımında ACW 0 olur (açık soru 5 ve 6 kararı). Deploy sonrası `[GAMMA] ... ACW query timed out after 10 seconds` WARN'ı izlenmeli; sıksa timeout veya index ele alınmalı.
- Toplu start'ta 8 thread'lik havuz dolarsa stop condition STOP'u kuyrukta bekler; bu sürede campaign birkaç çağrı daha çıkarabilir (eskiden originator thread'i global kilitte bloke oluyordu).

### G. Campaign Tipi Değişikliği Engeli (05.10.2026)

Geri bildirim: D2 engeli yalnız predictive stratejiler arasındaki geçişte uyarı veriyor; campaign tipleri arasındaki geçişte (ör. PREDICTIVE ↔ PROGRESSIVE/PREVIEW) de uyarı vermeli.

- [x] G1. `adminLogsFactory.campaignMustBeRestarted` tip alanını restart gerektiren alan saymaz; çalışan/duraklatılmış campaign'de tip değişikliği düz kaydet ile gider ve `updateContext` yeni tipi init'siz runtime'a uygular. `CampaignService.checkCampaignTypeChangeAllowed(Long, CampaignFormDTO, boolean)` formdaki tipi runtime tipiyle karşılaştırır, farklıysa `campaign.edit.type.illegal.state` (4 dil) döner. Düz kaydet ve kaydet + reload endpoint'leri kayıttan önce önce tip, sonra strateji kontrolünü çağırır (`CampaignController.checkRuntimeReinitChangeAllowed`); kaydet + restart serbest.
- [x] G2. `CampaignListDTO.type` `int`; gönderilmeyen tip 0 (PROGRESSIVE_RESERVED) gelir. Tech/NonTech formu tipi taşımaz, `CampaignMergingAspect` tipi korur. Kontrol yalnız `EditCampaign` yetkili kullanıcıda yapılır (`typeEditable`).
- [x] G3. Testler: yeni `CampaignTypeChangeGuardRegressionTest` (7), `CampaignSaveStrategyGuardRoutingRegressionTest` (+2, mevcut testler tip kontrolü sırasıyla genişletildi).

### Kapanış

- [x] Hedefli testler çalıştırıldı ve yeşil.
- [x] `docs/architecture/CAMPAIGN-LIFECYCLE.md` kilit ve asenkron dağıtım davranışıyla güncellendi ("Aksiyon Eşzamanlılığı" bölümü).
- [x] History kaydı eklendi.
- [x] Commit öncesi `gbpd-commit-discipline` kontrolü yapıldı; 05.10.2026'da geliştirici onayıyla commit ve push edildi (`fix/333607-campaign-start-stop`).

## Performans Etkisi

- Hot path durumu: Stop condition çağrıları AMI event worker, originate, DNC ve Redis thread'lerinde; `handleCallAnswer` AMI event worker'da; `CallCleaner` user status event'inde; `LSCEngine` lsc-executor'da çalışıyor.
- Call/event/tick başına ek maliyet: Stop condition senkron aksiyon yerine kuyruğa bırakılır (nadir ve daha ucuz). `handleCallAnswer` ve `CallCleaner` için tek null kontrolü. LSC için ek maliyet yok.
- IO/scan/lock/serialization/log etkisi: Global monitör kalkar; `ConcurrentHashMap` lookup + campaign başına `ReentrantLock`. Hot path'e yeni DB sorgusu, tam koleksiyon taraması veya geniş lock eklenmez. GAMMA startup'ta sorgu sayısı azalır ve her sorgu index seek'e iner; aynı gün yeniden start'ta sıfır sorgu. Yeni WARN logları yalnız eşik aşımında basılır.
- Azaltım veya ölçüm notu: Canlı doğrulama A10 WARN logları ve start süreleriyle yapılır. 02.10.2026 sabahki start süreleri (C74 10,5 dk, C87 7,5 dk, C71 9 dk 22 sn) referans alınır.

## Doğrulama

Komutlar `.codex/TOOL-PATHS.local.properties` değerleriyle (`JAVA_HOME`, `MAVEN_CMD`, `MAVEN_SETTINGS`, `MAVEN_REPO_LOCAL`) offline çalıştırılır:

```text
mvn -o -s <MAVEN_SETTINGS> -Dmaven.repo.local=<MAVEN_REPO_LOCAL> -Dtest=CampaignActionCoordinatorRegressionTest,CampaignServiceActionLockRegressionTest,ScheduledCampaignRunnerTest,StopConditionCheckerTest,CampaignStopExecutorIdleThreadRegressionTest,CampaignContextTest,CampaignStopJobTest,DbEngineDataSourceConcurrencyRegressionTest,DBEngineTest test
mvn -o -s <MAVEN_SETTINGS> -Dmaven.repo.local=<MAVEN_REPO_LOCAL> -Dtest=GammaStartupQueryShapeContractTest,GammaAcwQueryTimeoutRegressionTest,GammaStartupProfileDailyCacheRegressionTest,PredictiveGammaCallManagerTest,PredictiveGammaCallManagerStartupAnswerRateTest,GammaTalkWindowResultCodeTest,PredictiveGammaAnswerTimeSamplingTest test
mvn -o -s <MAVEN_SETTINGS> -Dmaven.repo.local=<MAVEN_REPO_LOCAL> -Dtest=CampaignStartingStateUntilPredictiveInitRegressionTest,StartStateTest,PauseStateTest,DialerFacadeTest,PredictiveStrategyChangeGuardRegressionTest,AmiEventDispatchCharacterizationRegressionTest,LSCEngineTest,CallCleanerPreviewOriginationNullRegressionTest,UserSubscriberTest,WebphoneSubscriberTest,CampaignSaveStrategyGuardRoutingRegressionTest,CampaignListenerRestartFollowUpRegressionTest test
```

AngularJS dosyası değişmez (açık soru 1 ve 4 yanıtları); frontend testi gerekmez.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı veya `Yok` olarak işaretlendi.
- [x] Planlanan testler çalıştırıldı ve başarılı oldu.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Plan oluşturuldu. 02.10.2026: açık sorular 1–6 yanıtlandı; A, B, C, D1, D2, D4–D6 uygulandı ve hedefli testleri yeşil. Açık soru 7 (a) yanıtıyla D3 uygulandı. Regresyon doğrulama turlarında (E, F) 11 sorun bulunup düzeltildi. Tam paket (`-Dtest=!CustomRoleHierarchyTest`) kırmızı listesi temiz `HEAD` ile birebir aynı. `StopStateTest` içindeki 3 hata (PowerMock `NotAMockException`) temiz `HEAD`'de de var; bu işle ilgisiz.
