# gbpowerdialer

gbpowerdialer, Bulut Santral ekosisteminde çalışan outbound dialer uygulamasıdır. Temel görevi kampanya verisini okuyup aranacak kişileri belirlemek, çağrıları Asterisk üzerinden başlatmak, çağrı sonucunu takip etmek ve operatör/yönetici ekranlarına gerçek zamanlıya yakın durum bilgisi sağlamaktır.

Bu repo yalnızca bir web arayüzü veya basit bir API değildir. Aynı uygulama içinde campaign lifecycle, dialer runtime, Asterisk AMI/AGI entegrasyonu, Redis pub/sub mesajlaşması, agent uygunluk takibi, sonuç kodu işleme, retry/callback/DNC/LSC davranışları ve AngularJS yönetim ekranları birlikte çalışır.

## Kısa Özet

- Uygulama tipi: Java 8 hedefli Spring MVC monolith, Maven WAR paketi.
- Çalışma modeli: Servlet container üzerinde tek WAR; web/API, scheduler, dialer motoru, Redis subscriber'ları ve Asterisk bağlantıları aynı JVM içinde çalışır.
- Ana kullanıcılar: Operasyon ekipleri, campaign yöneticileri, supervisor'lar ve outbound agent akışlarını yöneten sistemler.
- Frontend: AngularJS 1.x, JSP/HTML template'leri ve WRO4J bundle düzeni.
- Backend: Spring MVC, Hibernate, DAO/service katmanı, Redis, Asterisk AMI/AGI, gRPC/RDS boundary'leri.
- Test yığını: JUnit4, Mockito, PowerMock, Spring test/MockMvc, Karma/Jasmine/angular-mocks.
- Gelecek yönü: Davranışı bozmadan Go mikroservislerine ve Next.js frontend'e strangler pattern ile geçiş.

## Projenin Ana Amacı

gbpowerdialer, outbound call center operasyonunda kampanya bazlı arama üretimini yönetir. Sisteme yüklenen kampanya verileri üzerinden hangi kayıtların, hangi sırayla, hangi stratejiyle ve hangi agent kapasitesine göre aranacağını belirler. Sonra bu çağrıları Asterisk'e originate eder, telephony event'lerini dinler, çağrı sonucu ve agent durumunu günceller.

Bir kampanya çalıştığında uygulama şu sorulara sürekli cevap üretir:

- Kampanyada aranacak fresh, retry veya callback kayıt var mı?
- Kaç çağrı aynı anda üretilebilir?
- Hangi agent uygun, hangi agent başka çağrıda veya pause durumunda?
- Asterisk'e hangi originate action gönderilmeli?
- Çağrı cevaplandı mı, meşgule mi düştü, başarısız mı oldu?
- Çağrı sonucu hangi telephony result code ve user result code ile kapanmalı?
- Bu sonuç retry, callback, DNC, LSC veya raporlama davranışını nasıl etkiler?
- Operasyon ekranlarında hangi campaign/call/agent durumu gösterilmeli?

Bu nedenle uygulama üretimde çalışan telephony sistemi gibi ele alınır. Küçük görünen bir değişiklik bile çağrı üretimi, campaign sayaçları, agent state'i, Redis event'leri ve ekran göstergelerini aynı anda etkileyebilir.

## Önemli Görevler

### Campaign Yönetimi

Kampanyaların oluşturulması, güncellenmesi, başlatılması, duraklatılması, durdurulması, yeniden yüklenmesi ve zamanlanması bu uygulamanın temel sorumlulukları arasındadır.

Özellikle kritik noktalar:

- Campaign start/pause/stop/reload/reset işlemleri.
- Otomatik kampanya zamanlama ve blackout/schedule koşulları.
- Campaign runtime context oluşturma ve temizleme.
- Campaign counter'ları, queue'ları ve memento/restore davranışları.

İlgili alanlar:

- `gbpowerdialer/src/main/java/com/powerdialer/controller/CampaignController.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/scheduler`
- `gbpowerdialer/src/main/java/com/powerdialer/model/Campaign.java`

### Kampanya Verisi ve Kayıt İşleme

Kampanya verisinin yüklenmesi, filtrelenmesi, güncellenmesi ve sonuç tablolarıyla ilişkilendirilmesi yine bu repo içindedir. Excel/CSV benzeri veri yükleme, campaign source/result tabloları, priority hesaplama, retry listeleri ve callback kayıtları bu akışa dahildir.

Özellikle kritik noktalar:

- Fresh data, retry data ve callback listelerinin doğru ayrılması.
- DNC ve LSC filtrelerinin çağrı üretiminden önce doğru uygulanması.
- Campaign result tablolarında telephony/user result code alanlarının tutarlı kalması.
- Attempts, retry limit ve callback davranışının strategy contract'larına uygun ilerlemesi.

İlgili alanlar:

- `gbpowerdialer/src/main/java/com/powerdialer/controller/CampaignDataManipulationController.java`
- `gbpowerdialer/src/main/java/com/powerdialer/datamanipulation`
- `gbpowerdialer/src/main/java/com/powerdialer/service`
- `gbpowerdialer/src/main/java/com/powerdialer/dao`

### Çağrı Üretimi ve Dialer Runtime

Dialer runtime, kampanya verisinden çağrı adayı üretir, adayları queue'lara taşır ve uygun zamanda Asterisk'e originate eder.

Temel akış:

1. `CallProducer`, kampanya veritabanından aranacak kayıtları çeker.
2. Kayıtlar `Call` domain objelerine dönüştürülür.
3. `CallConsumer`, producer queue'sundaki çağrıları campaign call queue'suna taşır.
4. `CallOriginator`, server/location/queue durumuna göre originate edilecek çağrıyı seçer.
5. `ManagerActionFactory`, çağrı tipine uygun AMI action üretir.
6. `ManagerConnectionDecorator`, action'ı Asterisk'e gönderir.
7. AMI, AGI, Redis ve webphone event'leri çağrının sonucunu tamamlar.

Bu alan yüksek risklidir. Queue kapasitesi, duplicate originate, retry davranışı, counter drift'i ve event ordering problemleri doğrudan üretim çağrı akışını etkileyebilir.

İlgili alanlar:

- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/strategy`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/boost`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/predictive`

### Arama Tipleri

Uygulama birden fazla outbound arama biçimini destekler:

- Preview: Agent çağrıyı önceden görür ve aksiyon alır.
- Progressive: Sistem uygun agent kapasitesine göre çağrı üretir.
- Predictive: Sistem agent kapasitesi, cevaplanma oranı ve campaign basıncına göre daha dinamik çağrı üretir.
- Outbound IVR: Agent yerine IVR akışının kullanıldığı outbound senaryoları desteklenir.

Bu tiplerin her biri Asterisk action, Redis event, agent state, counter ve sonuç kodu davranışı açısından farklı contract'lara sahiptir.

### Asterisk AMI/AGI Entegrasyonu

gbpowerdialer çağrı başlatmak ve telephony event'lerini takip etmek için Asterisk ile AMI/AGI üzerinden konuşur.

Özellikle kritik noktalar:

- AMI connection açma ve bağlantı durumunu izleme.
- Çağrı tipine göre originate action üretme.
- AMI variable adlarını Asterisk dialplan/AGI contract'ına uygun tutma.
- Hangup, answer, bridge, failure ve custom event'leri doğru çağrı ile eşleştirme.
- Connection loss durumunda köprü kurulmuş çağrıları erken başarısız kapatmama.
- AGI script'leri ile abandon timeout ve agent dial davranışına destek verme.

İlgili alanlar:

- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/action`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/event`

### Redis Event ve State Paylaşımı

Redis bu uygulamada yalnız cache değildir. User/session state, webphone mesajları, call event'leri, predictive event'ler ve dış sistem bildirimleri Redis pub/sub üzerinden taşınır.

Özellikle kritik noktalar:

- Redis channel adları dış sistemlerle contract'tır.
- Message DTO alan adları ve tipleri geriye uyumluluk gerektirir.
- Pub/sub mesajları tek başına authoritative state kabul edilmez.
- JVM içindeki campaign/agent state ile Redis'teki görünüm drift üretebilir.

İlgili alanlar:

- `gbpowerdialer/src/main/java/com/powerdialer/redis`
- `gbpowerdialer/src/main/java/com/powerdialer/redis/message`
- `gbpowerdialer/src/main/java/com/powerdialer/redis/event`

### Agent, ACD ve Counter Yönetimi

Uygulama outbound çağrıların uygun agent'lara yönlendirilmesi için agent state ve ACD queue'larını takip eder.

Özellikle kritik noktalar:

- Agent uygunluk bilgisi.
- Idle, active, pause ve in-call gibi durumların yorumlanması.
- Campaign active/ringing/in-call/calls-in-progress counter'ları.
- `CallUpToDateKeeper` ve benzeri JVM içi canlı çağrı listeleri.
- Redis user state ile JVM içi agent state arasında tutarlılık.

İlgili alanlar:

- `gbpowerdialer/src/main/java/com/powerdialer/engine/acd`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/user`
- `gbpowerdialer/src/main/java/com/powerdialer/model`

### Yönetim Ekranları ve API

Operasyon ekipleri campaign, agent, çağrı, result code, DNC, LSC, queue, server ve monitoring gibi işleri AngularJS tabanlı arayüzlerden yönetir. Bu arayüzler Spring MVC controller'ları ve API resource'larıyla sıkı bağlıdır.

Özellikle kritik noktalar:

- Endpoint path ve response field adları frontend contract'ıdır.
- AngularJS route, controller, service/factory, template ve localization dosyaları birlikte değerlendirilir.
- WRO bundle sırası JavaScript davranışını etkileyebilir.
- Yeni frontend hedefi Next.js olsa da mevcut üretim ekranları AngularJS 1.x üzerindedir.

İlgili alanlar:

- `gbpowerdialer/src/main/java/com/powerdialer/controller`
- `gbpowerdialer/src/main/java/com/powerdialer/api`
- `gbpowerdialer/src/main/webapp/js`
- `gbpowerdialer/src/main/webapp/pages`
- `gbpowerdialer/src/main/webapp/WEB-INF/wro.xml`

## Mimari Görünüm

Mevcut sistem monolith olarak çalışır. Tek WAR içinde şu parçalar birlikte yaşar:

- Spring MVC web/API katmanı.
- Hibernate DAO/service/persistence katmanı.
- Dialer producer/consumer/originator runtime'ı.
- Campaign scheduler ve lifecycle motoru.
- Asterisk AMI connection ve AGI boundary'si.
- Redis publisher/subscriber katmanı.
- AngularJS 1.x yönetim arayüzü.

Bu yapı tek node kullanımında hızlı ve doğrudan bir çalışma modeli sağlar. Ancak çoklu node veya mikroservis dönüşümü hedeflendiğinde JVM içi state, campaign ownership, duplicate originate, counter drift ve Redis event ordering gibi konular açıkça çözülmelidir.

## Kritik Runtime State'ler

Aşağıdaki alanlar üretim davranışı açısından yüksek risklidir:

- `DialerFacade.campaignContexts`
- `DialerFacade.asteriskCallOriginators`
- `ManagerConnectionComposition.openedConnections`
- `ManagerConnectionDecorator` içindeki originated/recoverable call cache'leri
- `Campaign` üzerindeki geçici counter ve call set'leri
- `ACDEngine` agent queue'ları
- `CallUpToDateKeeper` canlı çağrı listesi
- Predictive manager map'leri

Bu alanlara dokunmadan önce tüm writer ve reader noktaları çıkarılmalıdır. Yeni increment/decrement eklemek yerine mümkün olduğunda authoritative state'ten türetilen değer tercih edilmelidir.

## Teknoloji Yığını

Backend:

- Java 8
- Spring Framework 5.3.x
- Spring MVC
- Spring Security ve OAuth2
- Hibernate, Hibernate Envers, Hibernate Validator
- Apache Commons DBCP2
- Dozer
- Jackson
- Maven WAR paketleme

Telephony ve messaging:

- Asterisk Java ile AMI/AGI
- Redisson ile Redis erişimi
- gRPC/reactor-grpc ile RDS boundary'si
- Eureka client bağımlılıkları, ortam profiline göre

Frontend:

- AngularJS 1.x
- JSP/HTML template'leri
- WRO4J JavaScript/CSS paketleme
- Karma/Jasmine/angular-mocks test altyapısı

Test:

- JUnit4
- Mockito
- PowerMock
- Spring test ve MockMvc
- Maven Surefire
- Karma/Jasmine

## Dizin Haritası

```text
.
├── AGENTS.md
├── CLAUDE.md
├── GEMINI.md
├── README.md
├── docs/
│   ├── architecture/
│   ├── project/
│   ├── development/
│   ├── analyse/
│   ├── execution-plan/
│   ├── change-fix-plan/
│   └── history/
└── gbpowerdialer/
    ├── pom.xml
    ├── package.json
    ├── karma.conf.js
    └── src/
        ├── main/java/com/powerdialer/
        ├── main/resources/
        ├── main/webapp/
        ├── test/java/
        └── test/angular/
```

Öne çıkan kod klasörleri:

- `controller`: Yönetim ekranları ve MVC/REST endpoint'leri.
- `api`: Runtime görünürlük, health ve JSON endpoint'leri.
- `service` / `dao`: İş kuralları ve veri erişim katmanı.
- `model`: Hibernate entity'leri ve bazı runtime state alanları.
- `engine`: Dialer runtime, scheduler, ACD, predictive ve Asterisk akışları.
- `redis`: Redis publisher/subscriber, message ve event contract'ları.
- `datamanipulation`: Kampanya veri yükleme ve işleme davranışları.
- `rds`: Resource Distribution Service boundary'si.
- `src/main/webapp/js`: AngularJS uygulama kodu.
- `src/main/webapp/pages`: AngularJS route'larının kullandığı template dosyaları.

## Yerel Geliştirme

Bu repo Maven ve Node gerektiren doğrulamalarda yerel tool path standardını kullanır.

1. Kişisel tool path dosyanızı hazırlayın:

```powershell
Copy-Item .codex/TOOL-PATHS.example.properties .codex/TOOL-PATHS.local.properties
```

2. `.codex/TOOL-PATHS.local.properties` dosyasında kendi Maven, Java, settings, local repo, Tomcat ve npm path'lerinizi tanımlayın.

3. Maven komutları ajanlar tarafından varsayılan olarak offline çalıştırılır. Eksik artifact varsa dependency indirme denenmez; geliştirici VPN/Nexus erişimiyle local cache'i güncelledikten sonra aynı offline komut tekrar çalıştırılır.

Sık kullanılan komutlar:

```powershell
cd gbpowerdialer
mvn -o -DskipTests package
mvn -o -Dtest=ClassName test
npm.cmd run test:angular
```

Ortamda özel Maven settings veya local repository gerekiyorsa `.codex/TOOL-PATHS.local.properties` içindeki değerlere göre komutlara `-s <settings.xml>` ve `-Dmaven.repo.local=<repo>` eklenir.

## Test ve Güvenli Değişiklik Standardı

Kod davranışı değiştiren işler TDD ile yürür:

1. Mevcut davranış belirsizse karakterizasyon testi yazılır.
2. Hata düzeltmesi veya yeni özellik için önce başarısız test eklenir.
3. En küçük üretim kodu değişikliği yapılır.
4. Gerekirse test yeşilken küçük refactor uygulanır.
5. Etkilenen hedefli Maven veya AngularJS testleri çalıştırılır.
6. Test sonucu final yanıtta ve gerekiyorsa history kaydında belirtilir.

Özellikle AMI, Redis, campaign lifecycle, runtime counter, ACD ve AngularJS contract değişikliklerinde test kapsamı dar tutulsa bile doğru owner boundary'sinde olmalıdır.

Dokümantasyon-only değişikliklerde Maven/NPM testleri çalıştırılmaz. Dosya ve diff kontrolü yeterlidir.

## Plan ve Dokümantasyon Akışı

Kod değişikliği gerektiren işlerde kullanıcı derdi önce teknik olarak çerçevelenir. Sorun ve çözüm yönü netleştiğinde planlı mı plansız mı ilerlenmek istendiği sorulur.

- Yeni feature işleri: `docs/analyse/GG.AA.YYYY/` ve `docs/execution-plan/GG.AA.YYYY/`.
- Fix, hotfix, refactor, perf, test, build, chore ve benzeri işler: `docs/change-fix-plan/GG.AA.YYYY/`.
- Ajan kaynaklı dosya değişikliği olan işlerin kapanışında: `docs/history/GG.AA.YYYY.md`.

Mevcut mimari dokümanlar `docs/architecture/` altındadır. Yeni başlayan biri için önerilen okuma sırası:

1. `docs/architecture/PRODUCT-SCOPE.md`
2. `docs/architecture/MIMARI.md`
3. `docs/architecture/DIALER-RUNTIME.md`
4. `docs/architecture/CAMPAIGN-LIFECYCLE.md`
5. `docs/architecture/ASTERISK-AMI-AGI.md`
6. `docs/architecture/REDIS-EVENTS-AND-STATE.md`
7. `docs/architecture/API-AND-UI.md`
8. `docs/architecture/TESTING-AND-SAFE-CHANGE.md`

## Güvenlik ve Secret Yönetimi

Yeni dokümana, teste veya örnek dosyaya gerçek IP, şifre, token, müşteri bilgisi veya ortam credential'ı yazılmamalıdır. Mevcut legacy yapılandırmalarda ortam bilgisi veya secret'a benzeyen değerler bulunabilir; bunlar yeni içeriklere taşınmamalı, gerekiyorsa placeholder ile anlatılmalıdır.

Local developer path ve credential bilgileri commitlenen dosyalara yazılmaz. Kişisel değerler `.codex/TOOL-PATHS.local.properties` veya developer'ın kendi güvenli lokal standardında tutulur.

## Migration Yönü

Bu repo için uzun vadeli dönüşüm hedefi mevcut davranışı bozmadan monolith'i daha net bounded context'lere ayırmaktır.

- Go mikroservis dönüşümü için ana rehber: `docs/project/GO-MICROSERVICE-ROADMAP.md`
- Next.js frontend dönüşümü için ana rehber: `docs/project/NEXTJS-FRONTEND-MIGRATION-ROADMAP.md`

İlk hedef monolith'i aceleyle bölmek değil, external contract'ları ve kritik runtime davranışlarını testlerle kilitlemektir. Özellikle call state, agent state, campaign ownership, Redis message contract ve Asterisk boundary'leri netleşmeden yatay ölçek güvenli kabul edilmemelidir.

## Terimler

- AMI: Asterisk Manager Interface; çağrı başlatma ve event dinleme için kullanılır.
- AGI: Asterisk Gateway Interface; Asterisk dialplan tarafından uygulama script'lerine geçiş sağlar.
- Campaign: Aranacak kayıtları, stratejiyi, zamanlamayı ve runtime state'i taşıyan kampanya.
- ACD: Agent seçimi ve queue yönetimi davranışlarının bulunduğu dağıtım katmanı.
- Originate: Asterisk'e yeni çağrı başlatma isteği gönderme.
- Fresh data: Henüz aranacak normal kampanya kayıtları.
- Retry: Daha önce denenmiş ve stratejiye göre tekrar aranabilecek kayıtlar.
- Callback: Belirli bir zamanda tekrar aranması gereken kayıtlar.
- DNC: Do Not Call; aranmayacak numara/listeler.
- LSC: List Suppression Control; liste bazlı suppression/filtre davranışları.
- TRC: Telephony result code; çağrının telephony sonucunu temsil eder.
- URC: User result code; agent veya webphone tarafından seçilen iş sonucunu temsil eder.

## Kısa Uyarı

gbpowerdialer canlı outbound dialer davranışı taşıyan bir sistemdir. AMI/AGI, Redis, campaign lifecycle, runtime counter, ACD, retry/callback ve AngularJS contract değişiklikleri küçük görünse bile üretim çağrı akışını etkileyebilir. Bu alanlarda değişiklik yapılırken önce mevcut contract okunmalı, mümkünse testle kilitlenmeli ve en küçük güvenli değişiklik tercih edilmelidir.
