# gbpowerdialer Agent Rehberi

## Misyon

gbpowerdialer, Bulut Santral için üretimde çalışan outbound dialer uygulamasıdır. Bu repo canlı bir telephony sistemi gibi ele alınmalıdır: çağrı akışı, campaign lifecycle, Asterisk AMI/AGI davranışı, Redis contract'ları ve operatör ekranları korunmadan değişiklik yapılmaz.

Ana hedefler:

- Üretim davranışını koruyarak hata düzeltmek ve dar kapsamlı özellik geliştirmek.
- Her geliştirmede TDD ile ilerlemek; eski ve kırılgan davranış varsa önce karakterizasyon testi yazmak.
- JVM içinde saklanan state'i ve yinelenen counter kaynaklarını adım adım azaltmak.
- Uygulamayı strangler pattern ile Docker üzerinde çalışabilen Go mikroservis mimarisine hazırlamak.

## Ajan Kapsamı

- Bu rehber Codex, Gemini, Claude ve repo üzerinde çalışan diğer ajanlar için ortak çalışma contract'ıdır.
- Bu dokümanda veya `.codex/skills/**/SKILL.md` dosyalarında `Codex` geçen operasyonel kural, köprü rehberleri üzerinden çalışan Gemini/Claude için de `çalışmayı yürüten ajan` anlamına gelir.
- Gemini veya Claude, `Codex` ifadesini kendisini hariç tutan bir kapsam daraltması olarak yorumlamaz.

## Dil Standardı

- `AGENTS.md`, `docs/**/*.md` ve `.codex/skills/**/SKILL.md` dosyaları Türkçe yazılır.
- Harf içeren Markdown dosya adları büyük harfli ve tireli yazılır; örnek: `GO-MICROSERVICE-ROADMAP.md`.
- Tarih bazlı history dosyaları `GG.AA.YYYY.md` formatını korur.
- Türkçe karakterler doğru kullanılır: `ç`, `ğ`, `ı`, `İ`, `ö`, `ş`, `ü`.
- Başlıklar ve açıklamalar TDK yazımına uygun, açık ve kısa tutulur.
- İçerikler Türkçe yazılır; ancak yerleşik teknik jargon ve call center/telephony jargonu global English haliyle kullanılır. Örnek: DDD, Domain-Driven Design, bounded context, contract, event, lifecycle, runtime, roadmap, migration, refactor, feature flag, queue, agent, supervisor, inbound, outbound, dialer, predictive, progressive, preview, campaign, call, IVR.
- Global terimler Türkçe cümle içinde Türkçe ek alabilir: `contract'ı`, `event'i`, `lifecycle'ı`, `campaign'i`, `queue'yu`, `call'ı`.
- Yeni skill veya doküman eklendiğinde bu envanter güncellenir.

## Repo Haritası

- `AGENTS.md`: Bu repo için birincil ajan çalışma rehberi ve kural kaynağı.
- `GEMINI.md`: Gemini tabanlı ajanlar için `AGENTS.md` ve `.codex/skills` kullanım köprüsü.
- `CLAUDE.md`: Claude tabanlı ajanlar için `AGENTS.md` ve `.codex/skills` kullanım köprüsü.
- `gbpowerdialer/pom.xml`: Maven WAR derlemesi, Java 8 hedefi, profiller, bağımlılık sürümleri, kaynak filtreleme.
- `gbpowerdialer/src/main/webapp/WEB-INF`: Spring XML, `web.xml`, güvenlik yapılandırması, WRO varlık yapılandırması.
- `gbpowerdialer/src/main/java/com/powerdialer/engine`: dialer runtime, ACD, scheduler, state, DB yardımcıları, DNC/LSC.
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk`: Asterisk AMI/AGI action, bağlantı ve event akışları.
- `gbpowerdialer/src/main/java/com/powerdialer/redis`: Redis pub/sub, kullanıcı/webphone subscriber'ları, çağrı ve predictive publisher'ları.
- `gbpowerdialer/src/main/java/com/powerdialer/model`: Hibernate entity'leri ve kritik geçici runtime counter'ları.
- `gbpowerdialer/src/main/java/com/powerdialer/service`, `dao`, `controller`, `api`: CRUD, REST/API ve persistence boundary'leri.
- `gbpowerdialer/src/main/webapp/js`: AngularJS 1.x arayüzü, route, controller, service/factory ve vendor frontend varlıkları.
- `gbpowerdialer/src/main/webapp/pages`: AngularJS route'larının kullandığı JSP/HTML template dosyaları.
- `gbpowerdialer/src/main/webapp/WEB-INF/wro.xml`: AngularJS ve vendor JS bundle sırası.
- `gbpowerdialer/package.json`: AngularJS 1.x için Karma/Jasmine test bağımlılıkları ve Node test komutları.
- `gbpowerdialer/karma.conf.js`: AngularJS unit spec dosyalarını çalıştıran Karma konfigürasyonu.
- `gbpowerdialer/src/test/java`: JUnit4, Mockito, PowerMock ve Spring testleri.
- `gbpowerdialer/src/test/java/tests`: Yeni TDD testleri için domain bazlı test kökü.
- `gbpowerdialer/src/test/java/tests/angular`: AngularJS route, WRO, template ve backend contract'ları için Java/JUnit contract test kökü.
- `gbpowerdialer/src/test/angular`: AngularJS service/factory/controller davranışları için Karma/Jasmine spec kökü.
- `docs/architecture/`: Mevcut ürün mimarisi, runtime akışları, riskli boundary'ler ve güvenli change contract'ları.
- `docs/README.md`: Doküman üst indeksidir; history dışındaki yeni dokümanlar eklendiğinde güncellenir.
- `docs/development/`: IDE, local build ve debug gibi developer çalışma ortamı standartları.
- `docs/analyse/`: Yeni feature işleri için gün bazlı analiz dosyaları.
- `docs/execution-plan/`: Yeni feature analizinden sonra oluşturulan gün bazlı uygulama planları.
- `docs/change-fix-plan/`: Fix, hotfix, change request, refactor ve feature dışı diğer işler için analiz ve execution plan içeren tek dosyalı gün bazlı planlar.
- `docs/project/`: Proje planları, roadmap ve dönüşüm planları.
- `docs/project/GO-MICROSERVICE-ROADMAP.md`: Go ve mikroservis dönüşüm roadmap'i.
- `docs/project/NEXTJS-FRONTEND-MIGRATION-ROADMAP.md`: AngularJS legacy arayüzden Next.js'e geçiş roadmap'i.
- `docs/history/`: Ajanlarla yapılan işlerin günlük geçmişi.
- `docs/history/README.md`: History günlük kayıt indeksi ve minimal kayıt standardı.

## Çalışma Kuralları

- Geniş refactor ile hata düzeltmesini aynı değişiklikte karıştırma.
- Her geliştirme TDD ile ilerler: önce başarısız test, sonra en küçük uygulama kodu, sonra güvenli refactor.
- Eski davranış belirsizse önce mevcut davranışı kilitleyen karakterizasyon testi yaz.
- Redis channel adları, AMI event adları, result code eşlemeleri, campaign state transition'ları ve profil property adları geçiş notu olmadan değişmez.
- `Campaign` geçici counter'ları, `DialerFacade.campaignContexts`, `ACDEngine` queue'ları, `CallUpToDateKeeper` ve `ManagerConnectionDecorator.calls` kırılgan runtime state kabul edilir.
- Call lifecycle koduna dokunmadan önce ilgili alanın veya counter'ın tüm writer ve reader noktalarını çıkar.
- Increment/decrement counter'larını tek gerçek kaynak gibi kullanma; authoritative state'ten türetilebiliyorsa türetilmiş değeri tercih et.
- Yeni static mutable state ekleme.
- Legacy kodda yeni kütüphane ekleme; mevcut bağımlılıklar ve standart JDK imkânlarıyla çözüm ara.
- Yeni kütüphane veya yeni artifact indirmesi gerçekten zorunluysa önce geliştiriciye bildir; geliştirici VPN'de Maven cache/Nexus erişimini güncelleyip VPN'den çıktıktan sonra ajan çalışmaya devam eder.
- Yeni dosyalara secret, gerçek IP, şifre veya ortam credential'ı koyma. Yer tutucu kullan.
- Çalışma ağacı kirli olabilir. İlgisiz yerel değişiklikleri geri alma.
- AngularJS frontend değişikliklerinde route, controller, service/factory, template, localization, WRO sırası ve test katmanı birlikte kontrol edilir.
- Yeni frontend ekran veya davranış değişikliği de TDD kapsamındadır.
- Frontend kodu değiştiyse yalnızca backend testleriyle plan kapatılamaz; ilgili frontend test katmanı da koşulup sonuç raporlanır.

## Çağrı Akışı Performans Standardı

- AMI/AGI originate, hangup/result event handling, Redis call event publish/subscribe, ACD agent seçimi, campaign producer/consumer/originator loop'ları ve runtime counter/state güncellemeleri hot path kabul edilir.
- Hot path değişikliklerinde call başına, event başına veya scheduler tick başına eklenen IO, collection scan, lock, serialization, parsing, allocation ve log maliyeti açıkça değerlendirilir.
- Hot path içinde yeni DB query, blocking network çağrısı, tam koleksiyon taraması, geniş `synchronized` blok, exception-driven control flow veya verbose payload log'u eklenmez.
- Correctness ve root cause çözümü performans gerekçesiyle atlanmaz; pahalı iş gerekiyorsa owner boundary, cache, precompute, bounded async veya batch akışı değerlendirilir.
- Hot path'e dokunan her plan içinde `Performans Etkisi` bölümü bulunur.

## Yerel Tool Path Standardı

- Çalışmayı yürüten ajan Maven, Java, Tomcat veya Node gerektiren doğrulama çalıştırmadan önce `.codex/TOOL-PATHS.local.properties` dosyasını kontrol eder.
- `.codex/TOOL-PATHS.local.properties` geliştiriciye özeldir, gerçek lokal path içerebilir ve commitlenmez.
- Commitlenen şablon `.codex/TOOL-PATHS.example.properties` dosyasıdır; yeni developer bu dosyayı kopyalayıp kendi path'leriyle local dosyayı doldurur.
- Local dosyada `MAVEN_CMD` varsa PATH'teki `mvn` yerine bu binary kullanılır. Ekip standardında IntelliJ'e bağımlı olmamak için standalone Maven veya PATH'teki `mvn.cmd` tercih edilir.
- Local dosyada `MAVEN_SETTINGS` varsa Maven komutları `-s <settings.xml>` ile çalıştırılır.
- Maven komutları ajan tarafından varsayılan olarak `-o` offline modda çalıştırılır; ajan dependency indirmeyi denemez.
- VS Code Tomcat debug task'ları developer tarafından çalıştırıldığı için `VSCODE_MAVEN_OFFLINE=false` ise Maven online çalışabilir ve VPN/Nexus üzerinden eksik artifact çözebilir. Bu kural ajanın doğrulama komutlarını offline çalıştırma kuralını değiştirmez.
- Local dosyada `MAVEN_REPO_LOCAL` varsa Maven komutlarına `-Dmaven.repo.local=<repo>` eklenir.
- Local dosyada `JAVA_HOME` varsa Maven/Tomcat komutundan önce `JAVA_HOME` set edilir ve `JAVA_HOME/bin` PATH'in başına alınır.
- Local dosyada `CATALINA_HOME` veya `TOMCAT_HOME` varsa Tomcat gerektiren manuel doğrulamada bu değerler kullanılır.
- Local dosyada `MAVEN_PROFILE` varsa VS Code Tomcat debug task'ları Maven package sırasında bu profile'ı kullanır; yoksa repo standardı `properties-tr-dev` kabul edilir.
- Local dosyada `TOMCAT_BASE`, `TOMCAT_DEBUG_PORT`, `TOMCAT_DEBUG_SUSPEND`, `TOMCAT_HTTP_PORT`, `TOMCAT_SHUTDOWN_PORT`, `TOMCAT_OPEN_BROWSER` veya `APP_CONTEXT_PATH` varsa VS Code Tomcat debug task'ları bu değerleri kullanır.
- VS Code Tomcat debug task'ları varsayılan olarak `.vscode/.local/tomcat-base` altında generated `CATALINA_BASE` kullanır; böylece developer'ın Tomcat install klasörü değiştirilmez ve local base commitlenmez.
- Local dosyada `NPM_CMD` varsa AngularJS testlerinde PATH'teki `npm.cmd` yerine bu binary kullanılır.
- Local tool path dosyalarına şifre, token, gerçek müşteri bilgisi veya ortam credential'ı yazılmaz; credential gerekiyorsa Maven `settings.xml` içinde developer'ın kendi güvenli lokal standardı kullanılır.

## Geliştirme Analizi, Plan Netleştirme ve Execution Plan Standardı

- Kod değişikliği gerektiren her istekte ajan önce kullanıcının anlattığı derdi kendi tespitleri, riskleri ve çözüm önerileriyle kısa şekilde çerçeveler.
- Kullanıcıyla tartışma sonunda sorun tespit edilmiş ve çözüm yönü belli olmuşsa ajan şu kararı kullanıcıya açıkça sorar: `Plan aşamasına geçeyim mi?`
- Bu soruda ajan hangi plan türünün uygun olduğunu belirtir: yeni feature ise `new feature planı`, fix/hotfix/change request/refactor/perf gibi işler için `change-fix planı`.
- Aynı mesajda plansız doğrudan çözüm uygulama seçeneğinin de olduğunu belirtir: kullanıcı plansız sadece çözümü yaptırmak isterse analiz, execution plan veya change-fix-plan dosyası açılmaz.
- Ajan kendi önerisini kısa gerekçeyle ekler; üretim riski, call lifecycle, Redis, AMI, campaign lifecycle, persistence veya AngularJS contract etkisi varsa öneri varsayılan olarak planlı ilerlemektir.
- Kullanıcı planlı ilerlemeyi onaylamadan analiz, execution plan veya change-fix-plan dosyası açılmaz.
- Kullanıcı plansız uygulamayı seçerse plan dosyası açma; çözümü etkileyen cevaplanmamış bir belirsizlik varsa kod değişikliğinden önce bunu yine kullanıcıya sor.
- Planlama onayı veya plansız uygulama onayı geldiğinde ajan mümkünse task id'yi interaktif olarak sorar; task id yoksa dosya kimliği olarak developer username'ini kullanır.
- İş etiketi ajan tarafından commit tipi standardına göre belirlenir: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`, `ci`, `perf` veya `revert`.
- Yeni feature işleri yalnız `feat` etiketiyle yürür ve iki dosyalı akışı kullanır: önce `docs/analyse/GG.AA.YYYY/FEAT-KIMLIK-KISA-AD.md`, sonra `docs/execution-plan/GG.AA.YYYY/FEAT-KIMLIK-KISA-AD.md`.
- Fix, hotfix, change request, refactor, perf, test, build, ci, chore, revert ve feature dışı diğer kod değişiklikleri `docs/change-fix-plan/GG.AA.YYYY/ETIKET-KIMLIK-KISA-AD.md` altında tek dosyada analiz detayları ve execution plan bölümüyle yürür.
- Dosya adında etiket büyük harfli yazılır: `FIX-BS-1423-AKTIF-CAGRI-SAYACI-DRIFT.md`; dosya içindeki `İş Etiketi` alanında küçük harfli yazılır: `fix`.
- `KIMLIK` alanı varsa task id, yoksa developer username'idir. Örnek: `FIX-BS-1423-...` veya `FIX-GLB90048674-...`.
- Eski `BUG` karşılığı `fix` etiketidir. `HF` ayrı etiket değildir; acil üretim düzeltmesi `fix` etiketiyle açılır ve planda `Öncelik: hotfix` olarak belirtilir. `CR` ayrı etiket değildir; isteğin niteliğine göre `feat`, `fix`, `refactor`, `chore` veya uygun başka commit tipi seçilir.
- Her planlama dokümanında bağlam, mevcut davranış, beklenen davranış, etkilenen kod alanları, riskler, TDD stratejisi, uygulama kontrol listesi, hedefli doğrulama komutları, kapanış kriteri ve `Açık Sorular` bölümü bulunur.
- Plan dosyası oluşturulduktan veya güncellendikten sonra `Açık Sorular` bölümünde cevaplanmamış madde varsa ajan bu soruları kendiliğinden kullanıcıya sorar; kullanıcının "açık sorular neler" diye sormasını beklemez.
- Cevaplanmamış açık soru varken kod, test, config veya davranış değiştiren doküman uygulamasına başlanmaz. Plan durumu `Açık soru bekliyor` kabul edilir.
- Kullanıcı açık soruları cevapladığında ajan yanıtları ilgili plan dosyasına işler, gerekiyorsa planı günceller ve ancak bundan sonra uygulama/kod değişikliğine geçer.
- Yeni feature akışında execution plan analizle paralel oluşturulmaz; analiz incelenmeden ve nihai plan netleşmeden execution plan açılmaz.
- Planlı akış seçildiyse her plan dosyası oluşturulduktan sonra ajan geliştiriciye planı incelemesi gerektiğini bildirir; geliştirici planı onaylamadan uygulama/kod değişikliği başlamaz.
- Plan üzerinden tamamlanan her adım plan dosyasında işaretlenir.
- Plan dışına çıkılması gerekiyorsa önce plan güncellenir, açık soru doğduysa kullanıcıdan yanıt alınır, sonra kod değişikliği yapılır.
- Planlanan testler çalıştırılıp başarılı olmadan plan tamamlandı/kapatıldı sayılmaz; test çalıştırılamıyorsa plan doğrulama beklemede kalır ve blocker açık yazılır.
- AngularJS, JSP/HTML template, WRO veya localization içeren frontend değişikliklerinde plan, ilgili frontend otomasyon testi (`npm.cmd run test:angular` ve/veya uygun frontend contract testi) çalıştırılmadan kapatılamaz; environment blocker varsa açık yazılır.

## TDD Standardı

- Yeni özellik, hata düzeltmesi ve güvenli refactor çalışmaları `gbpd-tdd-workflow` ile başlar.
- Kırılgan legacy davranışlarda `gbpd-characterization-tests` ile mevcut davranış önce kilitlenir.
- Yeni testler mümkün olduğunda `gbpowerdialer/src/test/java/tests/<domain>` altında açılır; mevcut legacy test doğrudan ilgiliyse aynı dosya genişletilebilir.
- AngularJS route, WRO, template, localization ve backend response shape contract testleri `gbpowerdialer/src/test/java/tests/angular/<domain>` altında açılır.
- AngularJS service/factory/controller birim testleri `gbpowerdialer/src/test/angular/<domain>/<bilesen>.spec.js` altında açılır ve `npm.cmd run test:angular` ile çalıştırılır.
- Yeni test sınıfı formatı `<Konu><Davranış><TestTipi>Test` şeklindedir. Örnek: `ActiveCallCounterDriftRegressionTest`.
- Test yazılamayan durumlarda neden açıkça belirtilir ve en yakın doğrulama yöntemi uygulanır.
- Kod değişikliği sonunda yazılan test, çalıştırılan komut ve sonuç raporlanır.
- Her çalışma sonunda çalıştırılan testler ve durumları kısa bir "Test Özeti" olarak sunulur; test çalıştırılmadıysa nedeni aynı özet içinde yazılır.
- Soru-cevap, salt inceleme, planlama, dokümantasyon-only, skill-only veya AGENTS standardı değişikliklerinde Maven/NPM testleri çalıştırılmaz; Test Özeti'nde `Test çalıştırılmadı; kod davranışı değişmedi.` yazılır.
- Kod davranışı değiştiğinde sadece etkilenen kapsamın hedefli testleri çalıştırılır. Tam test suite yalnızca geniş kapsamlı risk, ortak sözleşme değişikliği veya geliştirici isteği olduğunda çalıştırılır.

## History Standardı

- History kaydı yalnızca çalışmayı yürüten ajan bu görev kapsamında repo dosyalarında değişiklik yaptığı durumlarda eklenir.
- Dosya değişikliği; kod, test, doküman, skill, konfigürasyon veya benzeri dosyalarda ekleme, silme veya güncelleme yapılmasıdır.
- Soru-cevap, salt inceleme, planlama, brainstorming, `git log`/`rg`/dosya okuma gibi read-only işlerde history kaydı eklenmez.
- Çalışma ağacında kullanıcıya veya önceki görevlere ait ilgisiz değişiklikler varsa bunlar history kaydı tetiklemez.
- Ajan kaynaklı dosya değişikliği varsa ilgili `docs/history/GG.AA.YYYY.md` günlük dosyasına append-only kayıt eklenir.
- Günün history dosyası yoksa yalnız ajan kaynaklı dosya değişikliği durumunda oluşturulur ve yalnız bu durumda `docs/history/README.md` indeksine eklenir.
- History kaydı başlığı `### YYYY-MM-DD HH:mm:ss Z | KIMLIK | slug` formatında yazılır.
- History içindeki `KIMLIK` alanı varsa task id, yoksa developer username'idir.
- Her history kaydında `Kapsam`, `Değişiklikler` ve `Doğrulama` alanları zorunludur.
- Mevcut meşru history kayıtları silinmez veya yeniden numaralandırılmaz; yalnız sona yeni kayıt eklenir.
- Önceki kayıt başka developer, başka ajan veya başka task'a aitse hiçbir koşulda düzenlenmez, silinmez veya üzerine yazılmaz.
- Günlük history dosyasında mevcut içerik kaybı, eski satır silen diff veya tek taraflı `ours/theirs` conflict çözümü riski varsa işlem durdurulur ve geliştiriciye raporlanır.
- Eski history kaydında düzeltme gerekiyorsa mevcut kayıt değiştirilmez; yeni append-only kayıtla düzeltme notu eklenir.
- Kod yazmadan önce ilgili tarihli veya konuya yakın geçmiş kayıtları kontrol edilir.

## Mimari Doküman Standardı

- Mevcut mimari özeti `docs/architecture/` altında tutulur.
- Kod değişikliği mimari davranışı etkiliyorsa aynı iş içinde ilgili mimari doküman güncellenir.
- Mimari dokümanlar hedef design vaadi değil, mevcut kodun ve güvenli boundary'lerin kaydıdır.
- Proje planları `docs/project/` altında tutulur.
- Yeni feature analizleri `docs/analyse/`, feature uygulama planları `docs/execution-plan/`, fix/change/refactor ve diğer feature dışı planlar `docs/change-fix-plan/` altında tutulur.
- Go mikroservis dönüşüm planı ayrı tutulur; roadmap bilgisi `docs/project/GO-MICROSERVICE-ROADMAP.md` dosyasına yazılır.
- Next.js frontend dönüşüm planı ayrı tutulur; roadmap bilgisi `docs/project/NEXTJS-FRONTEND-MIGRATION-ROADMAP.md` dosyasına yazılır.
- Yeni history günü açıldığında `docs/history/README.md`, history dışındaki yeni dokümanlarda `docs/README.md` indeksi güncellenir.
- Mimari dokümanlarda gerçek IP, şifre, token, anahtar veya müşteri bilgisi kullanılmaz.

## Doğrulama

Doğrulama komutu işin türüne göre seçilir. Soru-cevap, salt inceleme, planlama, dokümantasyon-only, skill-only veya AGENTS standardı değişikliklerinde Maven/NPM testleri çalıştırılmaz; dosya/diff kontrolü yeterlidir. Kod davranışı değiştiğinde ilgili hedefli testler çalıştırılır.

Doğrulama komutlarından önce `.codex/TOOL-PATHS.local.properties` okunur ve varsa Maven/JDK/settings/Tomcat/Node path'leri oradan alınır. Local dosya yoksa PATH ve standart developer ortamı kullanılır. Maven komutları ajan tarafından `-o` ile offline çalıştırılır; eksik artifact varsa indirme denenmez, eksik artifact geliştiriciye raporlanır. Maven mevcut olduğunda tercih edilen komutlar:

- Hedefli test: `mvn -o -Dtest=ClassName test`
- Paketleme: `mvn -o -DskipTests package`
- Tam test: `mvn -o test`
- Yalnızca gerekli olduğunda Java 17 profili: `mvn -o -Pjava-17-support test`
- AngularJS unit testleri: `npm.cmd run test:angular`

Maven test runner, Surefire provider veya başka bir local cache eksiği nedeniyle başlamazsa ajan alternatif dependency indirme veya custom test runner çalıştırma denemesi yapmaz. Eksik artifact açık raporlanır; geliştirici VPN'de cache'i güncelledikten sonra aynı offline Maven komutu tekrar denenir.

Riskli runtime değişikliklerinde önce hedefli test ekle. Yeni testlerde mümkünse Mockito ve constructor üzerinden test edilebilir boundary'ler kullan. PowerMock yalnızca legacy static/private construction zorunlu kılıyorsa kullan.

## Yüksek Riskli Alanlar

- AMI lifecycle: `ManagerConnectionComposition`, `ManagerConnectionDecorator`, `ManagerActionFactory`, Asterisk event class'ları, AGI script'leri.
- Campaign lifecycle: `DialerFacade`, `CampaignContext`, `CampaignStateTemplate`, `CampaignStopJob`, scheduler chain.
- Runtime counter'lar: `Campaign` active/ringing/in-call/agent counter'ları ve memento restore/reset akışları.
- Agent state: `ACDEngine`, `UserHolder`, `UserSubscriber`, `UserLoginLogoutHandler`, `CallUpToDateKeeper`.
- Redis contract'ları: `Constants.REDIS_CHANNEL`, message DTO'ları, publisher/subscriber serialization.
- Predictive logic: alpha dynamic boost, beta/gamma manager'lar, dispatcher'lar, yerel map'ler.
- External boundary'ler: OneAccess OAuth, RDS/gRPC, dashboard stats API, kampanya DB bağlantıları.

## Skill Envanteri

Görev uyduğunda `.codex/skills` altındaki proje skill'lerini kullan:

- `gbpd-tdd-workflow`: Hata düzeltmesi, yeni özellik ve refactor dâhil tüm kod geliştirmelerini TDD ile yürütmek için.
- `gbpd-offline-build-toolchain`: Maven/Java/Node/Tomcat tool path, offline build-test, Nexus/VPN cache, Surefire provider, protobuf/WRO/plugin/profile ve npm/Karma teşhisleri için.
- `gbpd-safe-change`: Üretimde çalışan davranışı bozmadan küçük ve doğrulanabilir legacy değişiklikleri yapmak için.
- `gbpd-no-workaround-root-cause-engineering`: Bug fix, runtime hata, state drift ve validation/payload tutarsızlıklarında case-specific patch yerine owner boundary'de kalıcı çözüm uygulamak için.
- `gbpd-call-flow-performance-guardrails`: AMI/Redis/campaign/runtime çağrı hot path değişikliklerinde latency ve throughput maliyetini korumak için.
- `gbpd-characterization-tests`: Belirsiz veya kırılgan mevcut davranışı değiştirmeden önce testle kilitlemek için.
- `gbpd-runtime-state-audit`: Yerel bellek, counter drift'i, duplicate source-of-truth ve yatay ölçek engellerini incelemek için.
- `gbpd-elastic-devtools-log-query`: Log ihtiyacı olduğunda çalışan sete göre index prefix'i hatırlatılarak Elastic DevTools'a yapıştırılabilir geçerli sorgu hazırlamak için.
- `gbpd-campaign-lifecycle-flow`: Kampanya start/pause/stop/reload/reset, scheduler, memento ve stop job akışlarını güvenli değiştirmek için.
- `gbpd-ami-event-flow`: Asterisk AMI/AGI originate, event routing, hangup ve result handling değişiklikleri için.
- `gbpd-redis-contracts`: Redis channel, message contract, kullanıcı/session state ve pub/sub değişiklikleri için.
- `gbpd-api-persistence-contracts`: Spring MVC/API, DTO/request/response, Dozer, service/DAO, Hibernate, transaction ve dinamik SQL/persistence contract'ları için.
- `gbpd-go-migration-slice`: Java davranışını kırmadan Go servisine strangler/adapter tekniğiyle migration yapmak için.
- `gbpd-angularjs-legacy-ui`: AngularJS 1.x, JSP/HTML template, WRO, route, controller ve service/factory değişikliklerini güvenli yapmak için.
- `gbpd-angularjs-tdd`: AngularJS/frontend değişikliklerinde kısa vadeli Java/JUnit contract testlerini ve orta vadeli Karma/Jasmine/angular-mocks unit testlerini yürütmek için.
- `gbpd-nextjs-migration-slice`: AngularJS legacy ekranlarını Next.js'e sayfa veya workflow bazlı, feature flag ve contract testlerle taşımak için.
- `gbpd-safe-refactor`: Davranış değiştirmeden, test koruması altında küçük ve güvenli refactor yapmak için.
- `gbpd-dead-code-audit`: Kod silmeden önce kullanım izlerini, Spring/XML/Redis/JSP/Angular/DB bağlarını kanıtlamak için.
- `gbpd-commit-discipline`: Commit öncesi diff, test, secret ve ilgisiz değişiklik kontrolü yapmak için.
- `gbpd-sync-develop-branch`: Feature/fix local branch'lerini remote `origin/develop` ile güvenli sync etmek, PR conflict riskini raporlamak ve onaylı merge/conflict çözümü yapmak için.
- `gbpd-agent-history`: Ajan kaynaklı dosya değişikliği olan işleri append-only history formatıyla kaydetmek ve yeni gün açıldığında history indeksini güncellemek için.

## Migration Notu

Go ve mikroservis roadmap'i `docs/project/GO-MICROSERVICE-ROADMAP.md` dosyasında tutulur. Skill dosyaları roadmap saklamaz; skill'ler yalnızca geliştirme sırasında nasıl güvenli davranılacağını tarif eder.

Next.js frontend roadmap'i `docs/project/NEXTJS-FRONTEND-MIGRATION-ROADMAP.md` dosyasında tutulur. AngularJS legacy skill'leri ve Next.js migration skill'i roadmap saklamaz; geliştirme sırasında güvenli çalışma tekniğini tarif eder.
