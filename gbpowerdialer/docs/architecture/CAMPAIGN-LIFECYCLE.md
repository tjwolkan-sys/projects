# Kampanya Yaşam Döngüsü

Campaign lifecycle; yönetim ekranı/API, service/DAO katmanı, dialer runtime ve Asterisk originate akışının birleşimidir. Bu alan üretim davranışı açısından en riskli bölümlerden biridir.

## Ana Kavramlar

- `Campaign`: Hem kalıcı kampanya alanlarını hem de birçok geçici runtime counter'ını taşır.
- `CampaignContext`: Çalışan kampanyanın producer, consumer, state ve end-list behavior nesnelerini bir araya getirir.
- `DialerFacade`: Kampanya başlatma, durdurma, duraklatma, reload/reset ve Asterisk originator yönetiminin ana giriş noktasıdır.
- `CampaignStateTemplate` ve state sınıfları: Start, pause ve stop davranışlarını uygular.
- Scheduler sınıfları: Otomatik kampanya başlatma ve zamanlama koşullarını değerlendirir.

## Başlatma Akışı

1. Kullanıcı veya otomasyon kampanya aksiyonu gönderir.
2. `CampaignController` veya ilgili API/service katmanı kampanya aksiyonunu işler.
3. `DialerFacade.startCampaign(Campaign)` çağrılır.
4. Kampanya için mevcut `CampaignContext` varsa durumuna göre devam edilir veya yeniden oluşturulur.
5. Yeni context içinde `CallProducer`, `CallConsumer` ve end-list behavior hazırlanır.
6. Kampanya call queue'ları uygun `CallOriginator` nesnelerine bağlanır.
7. Producer veri çeker, consumer çağrı kuyruğunu doldurur, originator Asterisk'e originate eder.

## Durdurma ve Duraklatma

- `pause`, producer ve consumer tarafında yeni çağrı üretimini durduracak şekilde davranır.
- Manuel (operatör) pause `Campaign.manuallyPaused` runtime işaretini set eder; scheduler (`ScheduledCampaignRunner.shouldStartCampaign`) manuel paused kampanyayı schedule penceresi içinde olsa bile otomatik START etmez. Otomasyon (schedule automation) kaynaklı pause'ta işaret false kalır ve koşul kalkınca auto-resume korunur. İşaret unpause ve stop akışlarında temizlenir; transient olduğu için DB'ye yazılmaz.
- `stop`, kampanyanın bitiş aksiyonuna göre `CampaignStopJob` ve state geçişleriyle işlenir.
- `reload` ve `reset`, runtime context ve counter'ları etkileyebilir; bu yüzden writer/reader analizi yapılmadan değiştirilmemelidir.

## Aksiyon Eşzamanlılığı

- Tüm campaign aksiyonları (`START`, `STOP_REGULAR`, `STOP_FORCEFUL`, `RESTART`, `PAUSE`, `UNPAUSE`, `RELOAD`, `RESET`) `CampaignService.action` üzerinden `CampaignActionCoordinator`'ın campaign bazlı kilidinden geçer. Aynı campaign'in aksiyonları sıralıdır; farklı campaign'lerin aksiyonları birbirini beklemez (FIX-333607; eskiden tek global `synchronized` kilit vardı).
- Kilit transaction'dan önce alınır ve commit'ten sonra bırakılır; aksiyon gövdesi `TransactionTemplate` içinde çalışır. Kilidi bekleyen istek DB bağlantısı tutmaz. `action`/`actionAsync` `@Transactional(propagation = SUPPORTS)` taşır: `GenericService`'in sınıf seviyesi `@Transactional(readOnly = true)` niteliği bu metotlara uygulanmamalıdır (salt okunur transaction kilitten önce açılır ve Hibernate flush'ı kapanır).
- UI/API aksiyonu (`CampaignController`) aynı campaign kilidini en fazla 10 sn bekler; alamazsa `campaign.action.inProgress` hatası döner.
- Scheduler (`ScheduledCampaignRunner`, `AutomaticCampaignExecutor`) ve stop condition tetikleyicileri (`CallOriginator`, `ManagerConnectionDecorator`, `DNCEngine`, `UserLoginLogoutHandler`, `ResourceDistributionServiceSubscriber`) `CampaignService.actionAsync` kullanır: çağıran thread beklemez, aynı campaign + aksiyon için kuyrukta bekleyen iş birleştirilir, aynı campaign'in asenkron işleri gönderim sırasıyla (FIFO) çalışır (scheduler'ın aynı turdaki RESET → START'ı), farklı campaign'ler paralel çalışır; iş çağıranın güvenlik context'iyle çalışır; kilit alınamazsa aksiyon WARN ile bırakılır ve bir sonraki tetiklemede yeniden denenir. `CampaignListener` (restart) senkron kalır.
- Aksiyon 5 sn'yi veya kilit beklemesi 2 sn'yi aşarsa `[CAMPAIGN-ACTION]` WARN logu basılır.
- `ScheduledCampaignRunner`, kuyrukta bekleyen veya çalışan aksiyonu olan campaign'i o turda değerlendirmez (`hasPendingAction`); karar yalnız oturmuş state ile verilir.
- RESTART'ın stop job'ı bitince `CampaignListener` START'ı `startAfterRestart` ile verir; aynı campaign kilidini 10 sn yerine en fazla 5 dk bekler.
- `CampaignStateTemplate.start()` campaign'i predictive manager init'i (ALPHA boost, BETA, GAMMA startup profili) bitene kadar `STARTING` bırakır; `RUNNING` producer/consumer start'ından önce set edilir. Init hata verse de son durum `RUNNING` olur.
- Predictive strateji manager'ları yalnız start'ta ve unpause'ta init edilir. Düz kaydet ve kaydet + reload kaydı `updateContext` ile çalışan context'e init'siz uyguladığı için, formdaki strateji runtime'dakinden farklıysa ve campaign çalışıyor/duraklatılmışsa kayıttan önce `campaign.edit.predictiveStrategy.illegal.state` ile reddeder. Kaydet + restart serbesttir (stop eski stratejiyi kapatır, start yenisini init eder). Strateji göndermeyen form (Tech/NonTech yetkisi) değişiklik sayılmaz. Campaign tipi değişikliği aynı iki yolda kayıttan önce `campaign.edit.type.illegal.state` ile reddedilir; kontrol yalnız `EditCampaign` yetkili kullanıcıda yapılır (Tech/NonTech formu tipi taşımaz, `int` alan 0 gelir).
- GAMMA startup profil sorguları (`CDRDao`) en fazla 10 sn sürer; dünkü ortalamalar campaign başına günde bir kez hesaplanıp `PredictiveGammaCallManager` içinde önbellekte tutulur. ACW önbelleğe yalnız sıfırdan büyükse girer (zaman aşımı 0 döner ve sonraki start'ta yeniden denenir).

## Otomasyon ve Zamanlama

İlgili kod alanları:

- `gbpowerdialer/src/main/java/com/powerdialer/engine/scheduler`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/scheduler/schedulechain`
- `AutomaticCampaignExecutor`
- `ScheduledCampaignRunner`
- `ScheduleAutomationEngine`
- `CampaignSchedulerOrchestrator`

Zamanlama zinciri kampanya tarih, saat, blackout ve otomasyon koşullarını değerlendirir. Bu zincirde yapılacak değişiklikler kampanyaların beklenmedik anda başlamasına veya durmasına neden olabilir.

## Persistence ve Memento

Campaign state'i kalıcı entity alanları ile runtime counter'ları arasında dağılmıştır. `CampaignMementoService` ve ilgili DAO sınıfları campaign state'i restore etme veya kayıt altına alma davranışına temas eder. Runtime counter'ları kalıcı kaynak gibi kullanılmamalıdır.

## Riskli Noktalar

- `Campaign` entity'sindeki `AtomicInteger` counter'lar kalıcı veri değil, runtime state'tir.
- `campaignContexts` sadece mevcut JVM içinde anlamlıdır.
- Campaign restart veya reload sırasında queue, originated call ve counter temizliği drift üretebilir.
- Scheduler, manuel aksiyon ve Redis event aynı kampanyaya yakın zamanda temas ederse race condition oluşabilir.

## Change Contract

- Kampanya aksiyonlarında önce mevcut davranışı karakterizasyon testiyle kilitle.
- State geçişi değişiyorsa start, pause, stop, reload ve reset senaryolarının tümünü kontrol et.
- Counter update ekleme; mümkünse authoritative listeden türet.
- Kampanya context lifecycle değişikliği AMI ve Redis event etkisiyle birlikte incelenir.
