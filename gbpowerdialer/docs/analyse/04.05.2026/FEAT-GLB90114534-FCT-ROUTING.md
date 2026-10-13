# FEAT FCT Routing — Liste Tabanlı Caller ID Re-Originate

## Bağlam

FCT (Fixed Cellular Terminal) cihazlarının SIM kanalları dolduğunda Asterisk dialplan'ından `cause 34` (No circuit/channel available) hangup'ı dönüyor ve PowerDialer bu çağrıyı `TRC 25` ile sonlandırıp standart retry zincirine atıyor. Operasyon raporlarına göre günlük yaklaşık 25.000 originate'in 7.000 kadarı bu nedenle başarısız oluyor; bu da bir sonraki retry penceresinde tekrar denenene kadar değerli call slot'u ve agent kapasitesi kaybediliyor.

QSS (Queue Selection Service) tarafına yeni bir `getRouteDetails(RouteDetailsRequest)` gRPC endpoint'i eklendi. Geliştirici tarafından paylaşılan güncel proto'ya göre:

- `RouteDetailsRequest { int64 routingGroupId = 1; string calledNumber = 2; }`
- `RouteDetailEntry { string phoneNumber = 1; string type = 2; int32 attempts = 3; }`
- `RouteDetailsResponse { repeated RouteDetailEntry entries = 1; }`
- `service QueueSelectionService { ... rpc getRouteDetails(RouteDetailsRequest) returns (RouteDetailsResponse); }`

Endpoint, `routingGroupId` ve `calledNumber` çiftine göre sıralı bir `List<RouteDetailEntry>` döner; her entry `phoneNumber` (caller ID), `type` (Manual/Retry/Callback/Dialer First; string serbest alan) ve `attempts` (int32) alanını taşır. PowerDialer'in bu listeyi originate öncesi alıp, ilk caller ID ile originate'i denemesi; `cause 34 → TRC 25` döndüğünde aynı Call için bir sonraki entry ile yeniden originate atması; liste tükenince standart retry akışına TRC 25 ile düşmesi hedefleniyor.

Davranış sadece konfigürasyonla seçilen kampanyalarda çalışacak; geri kalan kampanyalarda mevcut tek caller ID akışı korunacak. Yeni davranış pilot kampanyalarda açılarak yaygınlaştırılacak.

## İş Etiketi

feat

Öncelik: normal

## Etkilenen Alanlar

- `gbpowerdialer/src/main/proto/resource-distribution-service.proto` — repo içinde tutulan proto dosyası. Mevcut hâlinde `RouteDetailsRequest`, `RouteDetailEntry`, `RouteDetailsResponse` mesajları ve `getRouteDetails` rpc'si **yok**; geliştiricinin paylaştığı proto bundle'ıyla bu dosya güncellenecek ve `protobuf-maven-plugin` Maven generate-sources fazında stub'ı yeniden üretecek. Bu nedenle ek Nexus artifact bağımlılığı gerekmez; build sırasında protoc üzerinden generate yeterli olur.
- `gbpowerdialer/src/main/java/com/powerdialer/rds/ResourceDistributionServiceFacade.java` — `obtainCallerId` yanı sıra yeni `getRouteDetails(...)` facade method'u (request mapping, hata yönetimi, log).
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/CallerIdManager.java` — list-based caller ID akışı için yeni method veya yardımcı (mevcut `obtainCallerId(call)` davranışı kırılmadan).
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallOriginator.java` — `pollCallsFromConsumerAndOriginate` içinde feature flag'e göre liste alma ve ilk entry ile originate akışı.
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionDecorator.java` — `handleDialerHangup` içinde TRC 25 (cause 34) tespit edildiğinde `applyRetryStrategy` yerine list-based re-originate koluna ayrım.
- `gbpowerdialer/src/main/java/com/powerdialer/model/Campaign.java` — yeni `fctRoutingEnabled` Hibernate property'si (`@Column(name = "fct_routing_enabled")`) ve getter/setter.
- `gbpowerdialer/src/main/java/com/powerdialer/model/Call.java` — list-based denemeyi takip etmek için runtime/transient cursor alanı (DB persist edilmez, mevcut `attempts` korunur).
- `gbpowerdialer/src/main/webapp/WEB-INF/...` ve `gbpowerdialer/src/main/webapp/js/...` — campaign edit ekranında "FCT routing aç/kapa" toggle (AngularJS controller, factory/service, template, localization).
- `gbpowerdialer/src/main/webapp/WEB-INF/wro.xml` — yeni AngularJS dosyaları eklenirse bundle sırası.
- DB migration — `pd_campaign` tablosuna `fct_routing_enabled BIT NOT NULL DEFAULT 0` (veya repo standardına uygun varsayılan).
- `gbpowerdialer/src/test/java/tests/...` ve `gbpowerdialer/src/test/angular/...` — karakterizasyon, regression ve yeni davranış testleri.

## Mevcut Davranış

- `CallOriginator.pollCallsFromConsumerAndOriginate` (CallOriginator.java:162) her tick'te tek call için tek caller ID alır: `call.setCallerId(callerIdManager.obtainCallerId(call))` (CallOriginator.java:174).
- `CallerIdManager.obtainCallerId` POOL davranışında `ResourceDistributionServiceFacade.obtainCallerId(...)` ile QSS `route(...)` endpoint'ine tek bir caller ID için soru sorar; tek `String` döner. FIXED/DYNAMIC/DIAL_PLAN_DEFINED kolları liste mantığından bağımsızdır.
- `processOrigination` (CallOriginator.java:346) campaign counter'larını (`addToOriginatedCalls`), AMI Originate'i (`connectionDecorator.originateCall(call)`), initial CDR'yi (`saveInitialCDR`) ve dial event'i (`publishDialerCallDialEvent`) tek seferde tetikler.
- `incrementAttempts` originate'ten **önce** çağrılır (CallOriginator.java:171); yani originate atılır atılmaz attempts artar.
- TRC 25 senkron originate dönüşünde değil, asenkron AMI hangup'ında oluşur: `ManagerConnectionDecorator.handleDialerHangup` (ManagerConnectionDecorator.java:536) `event.getHangupCause()` üzerinden `ituToTelephonyResultCodeRelations.get(34)` çevirimiyle TRC 25 set eder, `processCallResult` çağrılır, oradan `applyRetryStrategy` çağrılarak call standart retry'a yazılır.
- `Campaign` entity'sinde FCT routing kavramı henüz yok; mevcut `callerIdBehaviour`, `callerIdPool` (RoutingGroup) ve `retryCallerIdBehavior` alanları list-based akışı kapsamıyor.
- Call POJO'sunda caller ID iteration state'i tutacak alan yok; `Call` Hibernate-persist değil (Hibernate annotation'ı yok), runtime alan eklemek migration gerektirmez.
- `ResourceDistributionServiceFacade` yalnızca `route(...)` ve `deleteProvisionHistory(...)` çağırıyor; `getRouteDetails` proto/stub'ı generated kaynaklarda **bulunmuyor** (taranan: `target/generated-sources/protobuf/.../QueueSelectionServiceGrpc.java`). Repo'daki `src/main/proto/resource-distribution-service.proto` dosyasında da `getRouteDetails` rpc'si ve ilgili mesajlar tanımlı değil; yeni proto güncellemesi bu dosyaya yazılmadan önce build mevcut stub'la çalışmaya devam eder.
- Geliştirici tarafından paylaşılan güncel proto'da `RouteDetailsRequest`, `RouteDetailEntry`, `RouteDetailsResponse` ve `getRouteDetails` rpc'si tanımlı; `RouteDetailEntry.type` enum değil **string** alandır (Manual/Retry/Callback/Dialer First gibi değerleri serbest taşır), `RouteDetailEntry.attempts` `int32`'dir.

## Beklenen Davranış

- `Campaign.fctRoutingEnabled = true` ve `callerIdBehaviour = POOL` olan kampanyalarda originate öncesi `getRouteDetails(routingGroupId, calledNumber)` çağrılır (called number `Call.calledNumber` üzerinden taşınır); sıralı liste Call üzerine `caller ID cursor` olarak yerleştirilir. İlk entry `call.setCallerId(...)` ile uygulanır ve mevcut originate akışı çalışır.
- AMI hangup'ında `cause 34 → TRC 25` tespit edildiğinde `applyRetryStrategy` yerine cursor'dan **bir sonraki entry** alınır; aynı Call için yeni AMI ActionId üretilerek tekrar Originate atılır. Sonraki entry başarılı olursa veya cause 34 dışı bir sonuçla biterse iteration durur.
- Liste tükendiğinde (ya da QSS yanıtı boş geldiğinde) call standart retry akışına **TRC 25 ile** düşer; mevcut `applyRetryStrategy` çalışır.
- `fctRoutingEnabled = false` veya `callerIdBehaviour ≠ POOL` olan kampanyalarda mevcut tek caller ID akışı (mevcut `obtainCallerId` ve mevcut TRC 25 → `applyRetryStrategy` yolu) **birebir korunur**.
- Liste-retry sırasındaki tekrarlar **tek attempt** sayılır: `call.attempts` ve predictive boost girdileri liste tamamında tek deneme olarak yansır. CDR ve campaign counter side effect'leri double counting'e sebep olmaz.
- `publishDialerCallDialEvent` dial event'inin liste-retry'da yeniden gönderilip gönderilmediği davranış değişikliği listesinin dışındadır (geliştirici notu: bu event hâlihazırda tüketilmediği için akışı bozmaz, status quo korunur).
- AngularJS campaign edit ekranında "FCT routing aç/kapa" toggle'ı görüntülenir; kullanıcı `fctRoutingEnabled` alanını değiştirip kaydedebilir. UI varsayılanı `false` (backward compatible).
- DB migration `fct_routing_enabled` kolonunu `false` default'la ekler; mevcut kampanyaların davranışı değişmez.

## Kod Okuma Bulguları

- `CallOriginator.processOrigination` originate sonrası `connectionDecorator.getCalls().getIfPresent(call.getId())` ile cache'i kontrol ederek `saveInitialCDR` çağırıyor; cache key `call.getId()` (origin id), AMI ActionId değil. Liste-retry'da call.id stabil kaldığı için cache eviction kontrolü mevcut akışla uyumlu.
- `ManagerConnectionDecorator.handleDialerHangup` `processCallResult` çağrıldıktan sonra `clearCall`/`unregisterCall`-benzeri eviction yapıyor; liste-retry tetiklenecekse bu eviction **atılmamalı**, yoksa yeni Originate event'leri gelmeden cache temizlenir. Bu noktayı atlatmak için yeni "list-retry tetiklendi" branch'inin `processCallResult` öncesi davranışı kesmesi gerekiyor.
- `CallOriginator.callsCountUnderLSCProcessing`, `Server.maxLines`, `Server.cpsLimit` ve `delayOrigination(...)` mantığı CPS pacing'i kontrol ediyor; liste-retry yeni bir originate slot'u tüketmediği (aynı call'a yeni Originate) için bu sayaçları **artırmamak** gerekir.
- `Campaign` predictive counter'ları (`activeCallsSize`, `ringingCalls`, `inCallsSize`, `addToOriginatedCalls`) hangup ile decrement, originate ile increment ediliyor; liste-retry'da bu çiftin balance'ı korunmalı (bir Originate'den önce decrement olduysa yeni Originate öncesi yine increment dengeli olur). `gbpd-runtime-state-audit` kapsamında ayrıntılı tablo plan aşamasında çıkarılacak.
- `Call` POJO'su `@Entity` değil; `attempts`, `retryHistory` gibi alanlar runtime üzerinde tutuluyor ve DB'ye `pd_call`/`pd_cdr` üzerinden farklı yollarla yansıyor. Yeni `routeDetailsCursor` benzeri bir alan eklemek migration gerektirmez.
- `ResourceDistributionServiceFacade.init` Eureka tabanlı channel kuruyor; aynı channel üzerinden `getRouteDetails` blocking stub method'u eklemek doğal uzantı. Stub'ın generated sınıflarda olmaması proto bundle güncellemesini zorunlu kılıyor.
- `CallerIdManager.obtainCallerId` POOL dışındaki davranışlarda (FIXED, DYNAMIC, DIAL_PLAN_DEFINED) `ResourceDistributionServiceFacade`'i çağırmıyor; FCT routing yalnız POOL kolu için anlamlı, yeni akış POOL'a ek bir branch olarak eklenmeli.
- `ManagerConnectionDecorator` `calls` LoadingCache'i `call.getId()` keyli; AMI ActionId stabilize edilirse veya yeniden atanırsa cache'de yer değişimi olmaz. Liste-retry yeni bir AMI ActionId üretmek zorunda; bu yeni ActionId ile `originateCall` çağrılmadan önce eski response handler'larının yetim event üretmemesi için decoration paterni izlenmeli.

## Riskler

1. **Hot path latency**: AMI hangup → re-originate döngüsü kötü senaryoda liste boyu kadar (örn. 5 entry × cause 34 turnaround) çağrı süresini uzatabilir; predictive pacing ve agent kullanım oranı etkilenir. Plan aşamasında `Performans Etkisi` kısmı zorunlu, hot path'te yeni DB query, koleksiyon scan veya geniş `synchronized` blok eklenmemeli.
2. **Predictive boost (alpha/beta) bozulması**: Liste-retry her başarısız denemenin "fail" sayılması alpha/beta dispatcher'ları ve `dynamicBoostManager` pacing'ini şaşırtabilir. Liste tamamı tek attempt olarak yansıtılmalı; predictive event publish ve counter increment akışları buna göre kapatılmalı.
3. **Counter drift**: `Campaign.addToOriginatedCalls`, `ringingCalls`, `activeCallsSize`, `incrementAttempts`, `incrementResultCodeSequence` çağrılarının liste-retry sırasındaki idempotency'si korunmalı; aksi durumda `gbpowerdialer/src/main/java/com/powerdialer/model/Campaign.java` üzerindeki kırılgan transient counter'larda drift oluşur.
4. **AMI cache/eviction tutarlılığı**: `ManagerConnectionDecorator.calls` ve `CallUpToDateKeeper` listeleri yeni Originate öncesi temizlenirse yeni event'ler yetim kalır; kalırsa eski hangup event'i double process olabilir. Liste-retry branch'i eviction yapmamalı, yeni Originate aynı call.id ile devam etmeli.
5. **gRPC bağımlılığı**: `getRouteDetails` proto/stub'ı henüz repo'da yok. Build sırasında Nexus üzerinden gbqueueselectionservice proto artifact'ı güncel olmalı; offline build başarısızlığı durumunda geliştirici VPN'de cache güncellemesi yapana kadar değişiklik yarım kalır (`gbpd-offline-build-toolchain`).
6. **DB migration ve Hibernate cache**: `fct_routing_enabled` kolonu ekleme migration'ı senkron uygulanmalı; rollback senaryosunda kolon nullable/default false olmazsa eski WAR çalışmaz. Default `false` ile backward compat sağlanır.
7. **AngularJS UI ve localization**: Toggle eklenmesi WRO bundle sırasını, route/template/localization dosyalarını etkiler. AngularJS testleri (`npm.cmd run test:angular`) plan kapanışı için zorunlu.
8. **cause 34 ambiguity**: Asterisk dialplan tarafında cause 34 yalnız FCT capacity exhaust dışında başka durumlarda da dönebilir (örn. trunk konfigürasyon hatası, geçici provider sıkışıklığı). Yanlış pozitiflerde liste-retry boşa kanal tüketebilir; plan aşamasında dialplan/telephony tarafıyla teyit gerekiyor.
9. **Redis ve operatör ekran event'leri**: Hangup ve dial event'leri operatör ekranlarına ve dashboard stats akışına gidebilir; liste-retry sırasında çoklu hangup/dial publish'in tüketici tarafına etkisi `gbpd-redis-contracts` kapsamında değerlendirilmeli.
10. **Geri dönüş yolu**: Liste tükenince TRC 25 ile standart retry'a düşülürken `incrementResultCodeSequence`, `applyRetryStrategy` ve CDR kapatma akışı bir kez ve doğru zamanda tetiklenmeli; liste-retry sırasında bu çağrılar bilerek atlandığı için exhaust noktasında doğru tetikleme zorunlu.

## TDD Stratejisi

### Karakterizasyon (önce mevcut davranışı kilitle)

- `gbpowerdialer/src/test/java/tests/dialer/originator/<...>CharacterizationTest`: `fctRoutingEnabled=false` (varsayılan) olan kampanyada `obtainCallerId` tek String dönüşü ve `processOrigination` side effect'lerinin (Originate, addToOriginatedCalls, saveInitialCDR, publishDialEvent) mevcut davranışla aynı olduğunu doğrula.
- `gbpowerdialer/src/test/java/tests/asterisk/ami/<...>HangupCharacterizationTest`: `cause 34 → TRC 25` mevcut akışında `applyRetryStrategy` çağrıldığını ve campaign counter'larının dengelendiğini doğrula. Bu test FCT akışı eklenmeden önce yazılır ve geçer hâlde tutulur.

### Yeni davranış (TDD ile küçük adım)

1. `ResourceDistributionServiceFacadeTest` — `getRouteDetails(...)` blocking stub mock'lanır, sıralı liste döndüğü, hata durumunda boş liste/`null` davranışı doğrulanır (mevcut `obtainCallerId` testleri ile aynı paterni izler).
2. `RouteDetailsCursorTest` (yeni yardımcı sınıf veya helper) — `next()` semantics, exhaust durumu, idempotent `current()` davranışı.
3. `CallerIdManagerFctRoutingTest` — `fctRoutingEnabled=true` ve `callerIdBehaviour=POOL` olan call için `getRouteDetails` çağrıldığını, ilk entry'nin Call'a uygulandığını ve cursor'un Call üzerine yerleştiğini doğrular. Diğer behaviour'larda davranış değişmediği regression olarak doğrulanır.
4. `CallOriginatorFctRoutingTest` — `pollCallsFromConsumerAndOriginate` içinde feature flag açıkken liste alındığını, kapalıyken `obtainCallerId` çağrıldığını, `incrementAttempts`'in bir kere arttığını doğrular.
5. `ManagerConnectionDecoratorFctRoutingHangupTest` — `cause 34 → TRC 25` hangup'ında cursor'da bir sonraki entry varsa `applyRetryStrategy` çağrılmadığını, yeni Originate atıldığını, counter'ların dengelendiğini doğrular. Cursor exhaust olursa standart `applyRetryStrategy` çağrıldığını doğrular.
6. `Cause34OtherScenariosRegressionTest` — `fctRoutingEnabled=false` kampanyada cause 34 mevcut TRC 25 → `applyRetryStrategy` davranışı korunur.
7. AngularJS contract testi (`src/test/java/tests/angular/campaign/...`) — campaign edit JSON shape'inde `fctRoutingEnabled` alanı bulunduğu doğrulanır.
8. AngularJS Karma/Jasmine spec (`src/test/angular/campaign/<...>.spec.js`) — campaign edit controller'da toggle binding'i, default false ve save sırasında doğru DTO oluşturulduğu doğrulanır.

### Test çalıştırma

- Hedefli Java testleri: `mvn -o -Dtest=<TestClass> test` (`.codex/TOOL-PATHS.local.properties`'tan Maven path'i okunur).
- AngularJS testleri: `npm.cmd run test:angular` (NPM_CMD varsa local path).
- Tam suite plan onayı sırasında karar verilir; varsayılan kapsam hedefli çalıştırma.

## Önerilen Çözüm Yönü

1. **Proto + facade**: Repo'daki `src/main/proto/resource-distribution-service.proto` geliştiricinin paylaştığı yeni hâliyle güncellenir. Maven build (`mvn -o test-compile`) `protobuf-maven-plugin` ile stub'ı regenerate eder. Sonrasında `ResourceDistributionServiceFacade`'e `getRouteDetails(Long routingGroupId, String calledNumber)` facade method'u eklenir; başarısız gRPC çağrısında boş liste döndürülür, log seviyesi mevcut `obtainCallerId` ile uyumlu tutulur. Mevcut `Constants.RETRY_CALLER_ID_BEHAVIOR` ve `Uniqueness` enum'larıyla yeni request alakasız olduğu için ek mapping gerekmez.
2. **Kampanya property + DB migration**: `Campaign.fctRoutingEnabled` boolean property'si, `pd_campaign.fct_routing_enabled BIT NOT NULL DEFAULT 0` migration ile eklenir. Hibernate mapping ve DTO/JSON serialization güncellenir.
3. **Cursor modeli**: Yeni `RouteDetailsCursor` (POJO) Call'a transient field olarak yerleştirilir; ilk Originate öncesi populate edilir. Cursor `hasNext()` ve `next()` ile yönetilir; cursor null ise list-based akış devre dışıdır.
4. **CallerIdManager genişletmesi**: `obtainRouteDetailsCursor(Call)` benzeri yeni method POOL davranışında list akışını kurar; mevcut `obtainCallerId(Call)` davranışı kırılmaz, FIXED/DYNAMIC/DIAL_PLAN_DEFINED ve `fctRoutingEnabled=false` kollarında değişmez.
5. **CallOriginator entegrasyonu**: `pollCallsFromConsumerAndOriginate` içinde feature flag açıksa cursor doldurulur ve ilk entry uygulanır; aksi hâlde mevcut `obtainCallerId` çalışır. `incrementAttempts` tek seferde kalır (originate öncesi gibi).
6. **ManagerConnectionDecorator branch**: `handleDialerHangup` içinde `cause 34 → TRC 25` tespiti yeni private method'a (`tryListRetry(call)`) yönlendirilir. Cursor varsa ve `hasNext()` ise: counter'lar dengelenir, yeni AMI ActionId ile Originate atılır, `processCallResult` ve `applyRetryStrategy` **çağrılmaz**, eviction yapılmaz. Cursor yoksa veya exhaust olduysa mevcut `processCallResult` akışı çalışır.
7. **Counter dengesi**: `addToOriginatedCalls`, `ringingCalls`, `activeCallsSize` decrement-increment çiftleri liste-retry'da çiftleştirilir; `attempts` ve `resultCodeSequence` yalnız listede ilk Originate'te artar; CDR side effect'leri liste exhaust'unda final TRC ile kapanır.
8. **AngularJS UI**: Campaign edit ekranı template, controller, factory ve localization güncellenir; toggle disabled state'i yalnız `callerIdBehaviour=POOL` ve `callerIdPool` set edildiğinde aktif olur (UX onayıyla netleşir).
9. **DB migration scripti**: Repo'da kullanılan migration mekanizması (Flyway/Liquibase yoksa manual SQL standardı) plan aşamasında doğrulanır ve uygulanır.
10. **Test piramidi**: Karakterizasyon → unit (facade, cursor, manager) → integration tarzı (CallOriginator + ManagerConnectionDecorator) → AngularJS contract + Karma. Her seviye `gbpd-tdd-workflow` adımıyla yürür.

## Performans Etkisi

Hot path: Evet — `pollCallsFromConsumerAndOriginate` ve `handleDialerHangup` her ikisi de hot path.

- Originate başına eklenen IO: `getRouteDetails` çağrısı **mevcut** `obtainCallerId` (yine bir gRPC `route` çağrısı) yerine geçer; dolayısıyla per-originate gRPC RTT sayısı değişmez. Liste boyutu büyüse de tek RPC olur.
- Hangup başına eklenen iş: Cursor `hasNext()` kontrolü ve cause 34 ise yeni Originate. Yeni DB query, geniş `synchronized` blok veya tam koleksiyon scan'ı **eklenmez**.
- Liste-retry her tetiklendiğinde aynı call için Asterisk Originate işlem maliyeti tekrarlanır; bu kabul edilen davranıştır ve hedefin kendisi (TRC 25 başarısızlığını listede sönümlemek). Worst case: liste boyu kadar Originate + hangup turu.
- Predictive pacing'e tek attempt olarak yansıdığı için alpha/beta boost girdileri bozulmaz; CPS pacing `delayOrigination` üzerinden CallOriginator döngüsünde korunur.
- Log seviyesi: liste-retry transition'larında düşük hacimli `info`/`debug` log standardı; hot path log spam'ı yok.

Sonuç: gRPC ve AMI Originate maliyeti hedefe göre kabul edilebilir. Plan aşamasında tablo ile her aksiyonun added cost/saved cost dengesi netleştirilir.

## Açık Sorular ve Netleştirme İhtiyacı

1. **Proto bundle** — KAPALI: Geliştiricinin paylaştığı yeni proto, execution plan'ın ilk uygulama adımında repo'daki `src/main/proto/resource-distribution-service.proto` dosyasına yazılır; mevcut `route`, `getTargets`, `deleteProvisionHistory` rpc'leri korunarak yeni mesaj/rpc'ler eklenir. Maven build sırasında `protobuf-maven-plugin` stub'ı yerel olarak regenerate eder (Nexus üzerinden ek artifact gerekmez). Plan aşamasında `mvn -o test-compile` ile stub generation doğrulanır.
2. **DB migration mekanizması** — KAPALI: Repo'da Flyway/Liquibase yok; manuel SQL standardı kullanılıyor. Execution plan kapsamında geliştirici tarafında çalıştırılacak `ALTER TABLE pd_campaign ADD fct_routing_enabled BIT NOT NULL DEFAULT 0;` (veya repo'daki mevcut `ALTER` SQL kalıbına uygun) komutu hazırlanır ve plan içinde dosya/komut olarak verilir. Hibernate mapping `@Column(name = "fct_routing_enabled", nullable = false)` ile uyumlu olur.
3. **Toggle UI kısıtı** — KAPALI: AngularJS campaign edit ekranındaki "FCT routing aç/kapa" toggle yalnız `callerIdBehaviour=POOL` ve `callerIdPool` atanmış kampanyada **düzenlenebilir** olur. Diğer durumlarda toggle disabled gösterilir veya gizlenir; backend tarafında flag yine kayıtlı tutulur ama POOL dışı durumlarda akış bypass edilir. Final UX detayı (disabled vs hidden) plan aşamasında karar verilecek dar bir UX seçimi.
4. **cause 34 ambiguity** — KAPALI: Telephony tarafından teyit edildi; cause 34 FCT exhaust dışı durumlarda gelmediği için ek filtre/ trunk pattern gerekmez. Akış doğrudan TRC 25 (cause 34) tetiklendiğinde liste-retry'a girer.
5. **`RouteDetailEntry.type` ve `attempts` alanları** — KAPALI: PowerDialer iteration sırasında bu alanları **kullanmaz** ve QSS sıralamasını koruyarak sırayla dener. `type` (string) ve `attempts` (int32) alanları yalnız log/observability için tutulur. `type` enum değil string olduğu için PowerDialer tarafında değer doğrulaması yapılmaz, olduğu gibi log'a düşer.
6. **Liste-retry sırasında Redis dial event** — KAPALI: `publishDialerCallDialEvent` davranışı status quo (mevcut event tüketilmiyor). `publishCallEndEvent` ve dashboard stats akışı için de plan aşamasında status quo doğrulanır; davranış değiştirilmez.
7. **Predictive event akışı** — Plan aşamasına bırakıldı: `dynamicBoostManager.onCallAnsweredByAgent` ve beta/gamma manager'ların liste-retry'dan etkilenmediğini garanti etmek için plan içinde ilgili publish/subscribe noktaları test/uygulama checklist'ine eklenir.
8. **CDR `saveInitialCDR`** — KAPALI: Status quo korunur; mevcut `saveOrUpdate` davranışı (28.04.2026 hotfix kapsamında uygulanan) liste-retry'larında çoklu satır oluşmasını engelliyor. Liste-retry tetiklenmediği zamanki CDR snapshot davranışı aynen kalır; ek özel CDR yazımı eklenmez.

## Execution Plan Zorunluluğu

Kod değişikliği gerekiyor. Execution plan, geliştirici bu analizi inceleyip yukarıdaki açık soruları kapadıktan ve nihai planı onayladıktan sonra `docs/execution-plan/04.05.2026/FEAT-GLB90114534-FCT-ROUTING.md` altında oluşturulacak. Plan; karakterizasyon ve yeni davranış testlerini, uygulama adımlarını (proto + facade + cursor + Campaign property + DB migration + CallOriginator/ManagerConnectionDecorator entegrasyonu + AngularJS UI), `Performans Etkisi` tablosunu, doğrulama komutlarını (`mvn -o -Dtest=... test`, `npm.cmd run test:angular`) ve test başarısı şartlı kapanış kriterini içerecek.
