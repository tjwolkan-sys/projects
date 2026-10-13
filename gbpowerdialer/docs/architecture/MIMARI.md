# Genel Mimari: gbpowerdialer

## 1) Genel Bakış

`gbpowerdialer`, Bulut Santral ekosisteminde çalışan **üretimde kullanılan bir outbound dialer monolitidir**.  
Tek bir WAR içinde;

- Spring MVC tabanlı API/web katmanı,
- Asterisk ile çağrı başlatma ve event listener motoru,
- Redis tabanlı pub/sub akışı,
- Campaign lifecycle ve çağrı runtime motoru,
- AngularJS yönetim arayüzü

aynı JVM’de birlikte çalışır.

Bu birleşim, geliştirme açısından hızlı görünse de; campaign state, agent state ve call counter gibi kritik bilgilerde **tekil sahibin (authoritative owner) çoğu zaman belirsiz** olmasına yol açar. Bu nedenle mimari yaklaşımımız “contract'ı koru, davranışı bozmadan dar adımlarla değiştir” ekseninde ilerler.

## 2) Mimari Konum ve Sorumluluk Sınırı

Uygulamanın sorumlulukları:

- Kampanya tanımı, başlatma, duraklatma, durdurma, reload/reset davranışları.
- Preview / progressive / predictive çağrı türleri için dialer akışlarının üretimi.
- Asterisk AMI/AGI entegrasyonu üzerinden originate ve event işleme.
- Redis üzerinden user/pause/akış state'i ve çağrı/predictive event paylaşımı.
- SQLite benzeri küçük yardımcı modüller değil; gerçek anlamda:
  - Hibernate + DAO/Service katmanı,
  - dinamik kampanya veri manipülasyonu,
  - sonuç/ranking/log pipeline’ları.
- Spring MVC + AngularJS ile operasyon ekranları ve yönetim operasyonları.

External boundary'ler:

- Inbound kuyruğu yönetimi ve tam Asterisk dialplan modelinin tamamı bu repoda tanımlı değildir.
- Harici sistemlerdeki agent state modeli tamamen bu uygulamanın tek sahibi değildir; uyum katmanı rolü vardır.
- Klasik bir “tek kaynak gerçek” state modelinden ziyade, event + bellek + redis + DB karışımı vardır.

## 3) Teknoloji Çekirdeği ve Runtime Contract'ı

- Java 8 hedefli Spring MVC monolit (WAR).
- Spring Security + OAuth2 ile güvenlik.
- Hibernate + DBCP2 + JTA/transaction yönetimi.
- `asterisk-java` ile AMI/AGI entegrasyonu.
- Redisson ile Redis erişimi ve pub/sub.
- AngularJS 1.x + WRO4J paketleme + JSP/JSP-like şablonlar.
- JUnit4/Mockito/PowerMock ve Karma/Jasmine ile test kapsamı.

Runtime contract'ı:

1. Uygulama tek JVM içinde boot olur.
2. Aynı JVM içinde hem yönetim API’si hem çağrı runtime’ı hem scheduler hem subscriberlar çalışır.
3. Bu nedenle “tek instance” davranışı güvenli, “çok instance” davranışı ise açıkça test edilmesi gereken bir konu olarak ele alınır.

## 4) Paket ve Modül Haritası (yüksek seviyeli)

- `com.powerdialer.controller`  
  - Yönetim ekranı ve servis benzeri endpoint’ler.
- `com.powerdialer.api`  
  - Runtime görünürlük, health, campaign/job metrik endpoint’leri.
- `com.powerdialer.engine`  
  - Dialer runtime (producer/consumer/originator), scheduler, ACDEngine, predictive alt paketleri.
- `com.powerdialer.engine.asterisk`  
  - AMI/AGI bağlantı ve event handling.
- `com.powerdialer.redis`  
  - Publisher, subscriber, event model ve state köprüleri.
- `com.powerdialer.service` / `com.powerdialer.dao` / `com.powerdialer.model`  
  - İş kuralları, veri erişimi, kalıcı model.
- `com.powerdialer.tenant`, `com.powerdialer.utils` benzeri yardımcı katmanlar.
- `com.powerdialer.datamanipulation`  
  - Veri yükleme ve data supplier’lar.
- `com.powerdialer.rds`  
  - Resource Distribution Service entegrasyonu.
- `com.powerdialer/ src/main/webapp`  
  - `js/app.js`, controller/factory seti, localization ve `pages/*`.

## 5) Başlatma ve Yaşam Döngüsü: Uygulama Akışı

### 5.1 JVM Başlatma

1. `web.xml` üzerinden servlet context’i yüklenir.
2. `ContextLoaderListener` ile root Spring context açılır.
3. XML bean tanımlarıyla datasource, Hibernate, Redis, WRO, AMI/AGI yapılandırmaları kurulur.
4. `web.xml` + security + dispatcher ile istek zinciri hazırlanır.
5. `StartupListener` benzeri başlangıç davranışlarıyla runtime bileşenleri canlanır.

### 5.2 Servis Sorumluluklarının İlk Eşleştirilmesi

- API çağrıları: controller/resource → service → dao → DB.
- Gerçek zamanlı call event'leri: AMI/AGI/Redis'den beslenir ve runtime counter'larına yansır.
- Frontend: AngularJS route/servisleri API response şekillerine sıkı bağlıdır.

## 6) Temel Altyapı Akışı: Bir Çağrı Döngüsü

Çağrı üretimi genel olarak şu sırayla ilerler:

1. Kampanya runtime’ı `CallProducer` ile aday listeyi çeker.
2. Kayıtlar `Call` domain objelerine dönüştürülür ve producer-consumer kuyruğundan `CallConsumer` tarafından alttaki dispatch zincirine taşınır.
3. `CallOriginator`, server/location ve kampanya kuyruğuna göre originate adayını seçer.
4. `ManagerActionFactory` çağrı tipine göre AMI action üretir.
5. `ManagerConnectionDecorator` ilgili originate action’ı gönderir.
6. AMI response ve event dizisi tamamlanınca runtime counter'ları ve state güncellenir.
7. Sonuçlar persisted metrik/sicil alanlarına, Redis event’lerine ve UI endpoint’lerine yansır.

Bu akışın kırılma noktaları:

- producer veri bütünlüğü (özellikle retry listeleri),
- queue kapasite ve backpressure davranışı,
- event ordering (özellikle originate yanıtı ve event sırası),
- session ve çağrı id eşleşmesi.

## 7) Kampanya Yaşam Döngüsü

Kampanya akışı; yalnızca durum değişimi değil, runtime state’ini de etkiler:

- `start`:
  - `DialerFacade.startCampaign(...)`
  - context oluşturma / yeniden kullanma
  - producer/consumer/queue başlatma
- `pause`:
  - yeni originate üretiminin kısıtlanması ve mevcut akışın güvenli yavaşlatılması
- `stop`:
  - durdurma zincirleri, `CampaignStopJob`, context kapanışı
- `reload/reset`:
  - context, counter ve queue temizliği; en riskli adımlar arasındadır.

Önemli not:

- Kampanya `Entity` içinde kalıcı alanların yanında runtime alanlar da olduğu için “DB’den state’ini alır gibi düşünülemeyecek” alanlar vardır.
- `CampaignMemento*` benzeri bileşenlerle restore/checkpoint davranışı var; bu alanlar da ayrı doğrulama gerektirir.

## 8) Predictive/Pipeline Katmanları

Mevcut predictive tarafı alpha-beta-gamma ayrıştırmasıyla çalışır:

- `engine/predictive/alpha` benzeri hesaplama/decision akışları (mevcut kodda ilgili paketler üzerinden takip edilmeli),
- `engine/predictive/beta` ile event/worker/dispatcher zinciri,
- `engine/predictive/gamma` ile call kalibrasyon ve snapshot/izleme modelleri.

Bu katmanlar genel olarak:

- Agent kapasitesi,
- çağrı başarı/cevap oranları,
- campaign queue basıncı

gibi metriklere dayanır. Redis event publish/subscribe, predictive döngüyü besleyen ana kanallardan biridir.

## 9) Asterisk Entegrasyonu (Dış Sınır Katmanı)

- `ManagerConnectionComposition` ve `ManagerConnectionDecorator` AMI bağlantı yönetimi ve event callback state’ini taşır.
- `ManagerActionFactory` ile çağrı tipine uygun originate payload’ı üretilir.
- `AbstractOriginateAction` sınıfları action parametreleri, context ve değişkenleri belirler.
- `CustomAgiServerThread` ile AGI tarafı başlatılır; `DialWithAbandonTimeoutScript` bazı zamanlarda kritik fallback noktasıdır.

Contract açısından kritik alanlar:

- AMI variable isimleri,
- event sınıf ve alan eşlemeleri,
- AGI’de beklenen SIP/timeout davranışı,
- originate/answer/hangup sıralarının tek node’daki mantığı.

Her değişiklikte contract test ve karakterizasyon yaklaşımı önerilir.

## 10) Redis ve Message Contract

Redis’in rolü yalnızca cache değil, servisler arası yüksek gecikmeye toleranslı bir contract kanalıdır.

### 10.1 Ana Channel/İşlev İlişkisi

- `channelGBPowerDialer`: genel runtime ve dialer event'leri
- `channelGBInteractionNotifier`: dış bildirim/report hattı
- `channelCallEvent`: çağrı sonucu/sinyalizasyonu
- `channelPredictiveEvent`: predictive event akışı
- `channelUserCurrentStatus`, `channelUserSession`, `channelUserPause`: user state
- `channelQssConfiguration`: config dağıtımı
- webphone/agent state channel'ları: cihaz ve operator ara yüzleri için kritik

### 10.2 Publisher/Subscriber Haritası

- Publisher:
  - `CallEventPublisher`
  - `PredictivePublisher`
  - `InteractionNotifierPublisher`
  - çeşitli yardımcı publish metodları (`RedisUtil`)
- Subscriber:
  - `UserSubscriber`
  - `WebphoneSubscriber`
  - `ResourceDistributionServiceSubscriber`
  - listener tabanlı status takip sınıfları

### 10.3 Risk Modeli

Redis’e gelen mesajlar kaybolabilir veya farklı sırada gelecektir; bu yüzden:

- kritik state yalnızca Redis’e güvenilerek türetilmemeli,
- event idempotency ve reconcile mantığı ayrı tasarlanmalı,
- counter'lar için event kaynaklı refresh / verification gerekli.

## 11) ACD ve Agent Davranışı

- `ACDEngine`, `UserHolder`, `UserLoginLogoutHandler` agent dağıtımının bellekteki çekirdekleridir.
- Uygun agent bulma stratejisi (`DefaultFindMatchingUserStrategy`) ve queue yönetimi runtime’de çalışır.
- `CallUpToDateKeeper`, kullanıcıya ait canlı çağrı listesini tutar.
- Agent sayısı, uygunluk ve çağrı atama mantığı birden fazla yerde temsil edilir (Redis + ACDEngine + Campaign state + API response).

Bu nedenle yeni geliştirmede:

- writer/reader matrisi olmadan bir alan değiştirilemez,
- `agent size`, `active/ringing/in-call` drift’i hedefli izlenmelidir,
- state’in “tek gerçek kaynak”tan okunamaması kabul edilerek düzeltme tasarımı yapılmalıdır.

## 12) Persistence Katmanı ve Operasyonel Contract

### 12.1 Data Flow

- Controller/API → service → dao → Hibernate → DB.
- Çoğu kritik tablo normalized entity ile taşınır, ancak kampanya runtime alanları entity içi kalıcı yapıda bulunabilir.

### 12.2 Dinamik Veri İşleme

`campaign data manipulation` tarafında:

- dosya yükleme (Excel/CSV vb.),
- filtreleme + tip doğrulama,
- öncelik/öznitelik tekrar hesaplama,
- source/result tablolarına yazma

bir arada bulunur. Bu alanlarda uzun iş yükü ve batch davranışı riskini hesaba katmak gerekir.

### 12.3 Sonuç ve Kayıt Katmanı

- CDR/sonuç/code tabloları,
- campaign job log ve job result log,
- admin/action log zincirleri

call lifecycle'ın geri dönüp kontrol edilen tarafını oluşturur.

## 13) API ve Frontend Katmanı

- Spring MVC controller ve API resource’ları çoğunlukla aynı veri kontratını farklı zamanlarda farklı seviyede kullanır.
- AngularJS servisleri (factory) endpoint path, field name ve tip değişimine çok duyarlıdır.
- WRO bundle ve route sırası, frontend davranışını etkiler.
- `src/test/java/tests/angular` ve `src/test/angular` paketleri bu katmanın güvenli değişim referanslarıdır.

Ek işlevsel alanlar:

- localization (tr/en/ru/ua),
- authorization ve kullanıcı oturumu,
- operasyon ekranları için realtime veri sunumu.

## 14) Observability ve Operasyon

Mevcut durumda en güçlü araçlar log ve belirlenmiş birkaç health/resource endpoint’idir:

- logback tabanlı loglama,
- `ActuatorController`,
- runtime status endpoint’leri (`DialerFacadeResource`, `CallResource`, `ServiceStatusesController`, `AgentSizeHealthResource` vb.).

Hedeflenen görünürlükte eksik kalanlar:

- event correlation ID standardı,
- publish/consume latency metrikleri,
- duplicate call event ve event-lag raporlaması,
- multi-node ownership haritası ve drift raporlarının kalıcılaştırılması.

## 15) Ölçeklenebilirlik ve Çoklu Node Riskleri

Tek node tasarımı bazı alanlarda güçlüdür, çoklu node’de riskleri vardır:

- aynı kampanyanın eş zamanlı fetch/originate tekrarı,
- counter drift'i (originated/active/ringing/in-call),
- AMI event sırası farklılığı,
- farklı node’da farklı producer tüketimi.

Bu nedenle yatay ölçek geçişinde ilk katmanlar:

- dağıtık ownership tanımı,
- distributed lock/reconcile,
- idempotent originate ve event yeniden oynatma stratejileri,
- migration bridge yaklaşımı olmalıdır.

## 16) Güvenli Değişiklik Çerçevesi

Bu mimaride kritik alanlar için zorunlu süreç:

- sorun ve çözüm yönü netleşince kullanıcıya plan aşamasına geçilip geçilmeyeceğini ve uygun plan türünü sormak,
- kullanıcı planlı akışı seçerse yeni feature için `docs/analyse/GG.AA.YYYY/` altında analiz ve `docs/execution-plan/GG.AA.YYYY/` altında execution plan,
- kullanıcı planlı akışı seçerse fix, hotfix, change request, refactor ve feature dışı işler için `docs/change-fix-plan/GG.AA.YYYY/` altında analiz ve execution plan içeren tek dosya,
- kullanıcı plansız doğrudan uygulamayı seçerse plan dosyası açmamak,
- planlı akışta plan dosyasında cevaplanmamış açık soru varsa kod değişikliğini başlatmamak,
- hedefli test ve karakterizasyon,
- öncelikle davranışı dar bir testle güvenceye almak,
- kodu en küçük değişiklik ile ilerletmek.

Özellikle etki alanları:

- `DialerFacade`, `CampaignContext`, `CallOriginator`,
- AMI/AGI sınıfları,
- Redis publish/subscribe DTO ve channel contract'ı,
- counter ve ACD akışları.

## 17) Gelecek Bölünme (Go/Mikroservis) Perspektifi

Mevcut mimariyi mikroservislere taşımak için en güvenli başlangıç,
mimari davranışı kesmek değil; önce boundary'leri netleştirerek **contract'ı dondurmaktır**:

- campaign state/projection ayrımı,
- agent state projection,
- AMI gateway izolasyonu,
- Redis bridge katmanı ile geriye uyumlu yayın formatı,
- reporting/interactions akışının ayrı bir service boundary olarak çıkarılması.

## 18) Bağlı Mimari Dokümanlar

Bu dosya, mevcut mimari dokümantasyonun üst düzey özeti olarak okunmalıdır. Ayrıntılar için:

- `architecture/PRODUCT-SCOPE.md`
- `architecture/TECHNOLOGY-STACK.md`
- `architecture/DEPLOYMENT-AND-RUNTIME.md`
- `architecture/CAMPAIGN-LIFECYCLE.md`
- `architecture/DIALER-RUNTIME.md`
- `architecture/ASTERISK-AMI-AGI.md`
- `architecture/REDIS-EVENTS-AND-STATE.md`
- `architecture/AGENT-ACD-AND-COUNTERS.md`
- `architecture/DATA-PERSISTENCE-AND-DB.md`
- `architecture/API-AND-UI.md`
- `architecture/OBSERVABILITY-AND-OPERATIONS.md`
- `architecture/TESTING-AND-SAFE-CHANGE.md`
- `architecture/MIGRATION-BOUNDARIES.md`
- `architecture/CONFIGURATION-AND-SECRETS.md`
