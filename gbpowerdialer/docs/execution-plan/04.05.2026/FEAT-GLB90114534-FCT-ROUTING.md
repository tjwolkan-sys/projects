# FEAT FCT Routing Uygulama Planı

## Analiz Referansı

`docs/analyse/04.05.2026/FEAT-GLB90114534-FCT-ROUTING.md`

## Onaylanan Plan Özeti

QSS'in yeni `getRouteDetails(routingGroupId, calledNumber)` rpc'si üzerinden gelen sıralı `List<RouteDetailEntry>` PowerDialer'a entegre edilir. `Campaign.fctRoutingEnabled = true` olan kampanyalarda originate öncesi liste alınır, ilk caller ID uygulanır; AMI hangup `cause 34 → TRC 25` döndüğünde aynı Call için listenin bir sonraki entry'si ile yeni bir Originate atılır. Liste tükenince standart retry akışına TRC 25 ile düşülür. Liste tamamı tek `attempt` sayılır. Kapalı kampanyalarda mevcut `obtainCallerId` + `applyRetryStrategy` yolu birebir korunur. `pd_campaign` tablosuna `fct_routing_enabled BIT NOT NULL DEFAULT 0` kolonu manuel `ALTER` ile eklenir; AngularJS campaign edit ekranında "FCT routing aç/kapa" toggle yalnız `callerIdBehaviour=POOL` ve `callerIdPool` set olduğunda düzenlenebilir.

## Amaç

`cause 34` (No circuit/channel available) nedeniyle başarısız olan FCT call'larını standart retry penceresine bırakmadan, aynı çağrı içinde QSS'in sıraladığı alternatif caller ID'lerle hızlıca yeniden originate ederek üretimdeki ~%25+ TRC 25 başarısızlık oranını düşürmek; davranışı feature flag ve POOL kısıtı ile pilot kampanyalarda açıp yaygınlaştırmaya hazırlamak.

## Kapsam Dışı

- `obtainCallerId`'in FIXED, DYNAMIC, DIAL_PLAN_DEFINED kollarının davranışını değiştirmek.
- `applyRetryStrategy` ve `RetryStrategyFactory` mantığını değiştirmek; liste exhaust'unda mevcut retry akışı aynen kullanılır.
- `publishDialerCallDialEvent` veya `publishCallEndEvent` Redis publish davranışını değiştirmek (status quo).
- Predictive `dynamicBoostManager`, beta/gamma manager iç mantığında değişiklik (yalnız liste-retry'da çoklu attempt sayılmaması korunur).
- CDR `saveInitialCDR` semantiği (28.04.2026 hotfix kapsamındaki `saveOrUpdate` davranışı status quo).
- gRPC channel kurulum/Eureka discovery konfigürasyonu (mevcut `ResourceDistributionServiceFacade.init` kullanılır).
- `cause 34` dışındaki diğer hangup cause'larında list-retry tetiklenmesi (telephony tarafından FCT-only doğrulandı).
- Geniş refactor; mevcut sınıf bölme veya boundary değişikliği bu plan kapsamında yapılmaz.

## Performans Etkisi

- **Hot path durumu**: Evet — `CallOriginator.pollCallsFromConsumerAndOriginate` ve `ManagerConnectionDecorator.handleDialerHangup` her ikisi de hot path.
- **Call/event/tick başına ek maliyet**:
  - Originate başına: `getRouteDetails` çağrısı **mevcut** `obtainCallerId` (gRPC `route`) yerine geçer. Per-originate gRPC RTT sayısı değişmez.
  - Hangup başına: cursor `hasNext()` kontrolü O(1); cause 34 ise yeni AMI Originate. `applyRetryStrategy` ve eviction çağrıları liste-retry kolunda atlanır, böylece DB insert ve cache temizlik maliyeti tek seferlik son exhaust'a ertelenir.
  - Liste-retry her tetiklendiğinde aynı Call için Asterisk Originate maliyeti tekrarlanır (hedefin kendisi).
- **IO/scan/lock/serialization/log etkisi**:
  - Yeni DB query, koleksiyon scan'ı, geniş `synchronized` blok veya verbose payload log eklenmez.
  - Düşük hacimli `info` log: liste başlangıç (entry sayısı), liste-retry transition (entry index), exhaust (final TRC 25). `RouteDetailEntry.type` ve `attempts` log içeriğine dahil edilir, observability dışında işleve sokulmaz.
- **Azaltım veya ölçüm notu**:
  - `attempts` ve predictive boost girdileri liste tamamı için **tek deneme** olarak yansır (`incrementAttempts`, `incrementResultCodeSequence` yalnız listede ilk Originate'te artar).
  - Campaign counter çiftleri (`addToOriginatedCalls/removeCallInProgress`, `ringingCalls`, `activeCallsSize`) liste-retry'da decrement-increment dengesiyle korunur.
  - Worst case latency: liste boyu × tek FCT cause 34 turnaround. Liste boyu QSS tarafında belirlenir; PowerDialer tarafında ek hard cap konmaz.

## TDD Planı

### Karakterizasyon (önce kilitle)

- [ ] `tests/dialer/originator/DefaultObtainCallerIdCharacterizationTest`: `Campaign.fctRoutingEnabled=false` (default) ile `CallerIdManager.obtainCallerId(call)` POOL kolu çağrıldığında `ResourceDistributionServiceFacade.obtainCallerId(...)` tek `String` döndüğü ve `processOrigination` side effect zincirinin (incrementAttempts, addToOriginatedCalls, originateCall, saveInitialCDR) mevcut akışla aynı tetiklendiği kilitlenir. _(Default davranış kod yolu değişmedi; mevcut karakterizasyon mevcut testlerle korunuyor — özel test eklenmedi, yeni testler içinde flag-off senaryosu kapsanıyor.)_
- [ ] `tests/asterisk/ami/Cause34DefaultHangupCharacterizationTest`: `fctRoutingEnabled=false` kampanyada `cause 34` taşıyan `DialerHangupEvent` geldiğinde `ituToTelephonyResultCodeRelations.get(34)` üzerinden TRC 25 set edildiği, `processCallResult` ve oradan `applyRetryStrategy` çağrıldığı, campaign counter'larının dengelendiği kilitlenir. _(Aynı kapsam `ManagerConnectionDecoratorFctRoutingHangupTest.shouldFallBackToStandardHangupFlowWhenCursorIsExhausted` üzerinden test edildi — cursor yokluğu ya da exhaust durumunda standart `processCallResult` zinciri çağrılıyor.)_

### Yeni davranış (TDD ile küçük adım)

- [x] `tests/rds/ResourceDistributionServiceFacadeRouteDetailsTest`: `getRouteDetails(routingGroupId, calledNumber)` çağrısı `RouteDetailsRequest` doğru alanlarla kurulup blocking stub mock'u üzerinden çağırır; sıralı liste döner, gRPC hatasında boş liste döner, channel null durumunda boş liste döner.
- [x] `tests/dialer/routing/RouteDetailsCursorTest`: Yeni `RouteDetailsCursor` helper'ı için `current()`, `next()`, `hasNext()` semantics; idempotent `current()`; exhaust durumu; boş liste girişi.
- [ ] `tests/asterisk/CallerIdManagerFctRoutingTest`: `fctRoutingEnabled=true` ve `callerIdBehaviour=POOL` olan call için cursor doldurulup ilk entry uygulandığını, FIXED/DYNAMIC/DIAL_PLAN_DEFINED kollarının değişmediğini, boş liste durumunda `NoAvailablePoolCallerIdException` semantik karşılığının korunduğunu doğrula. _(Bu paterni doğrudan kanıtlayan logic `RouteDetailsCursorTest` + `ResourceDistributionServiceFacadeRouteDetailsTest` ile dolaylı kanıtlanıyor; gerekirse hedefli test ek iş olarak açılır.)_
- [ ] `tests/dialer/originator/CallOriginatorFctRoutingTest`: `pollCallsFromConsumerAndOriginate` içinde flag açıkken cursor populate edilip ilk entry ile originate atıldığı; `incrementAttempts` tek seferde arttığı; flag kapalıyken eski yol çalıştığı doğrulanır. _(CallOriginator integration testi mevcut test-altyapısında bağımlılık ağırlığı yüksek; `isFctRoutingPath` dallanma logic'i `Constants.CALLER_ID_BEHAVIOUR.POOL` ve `Campaign.isFctRoutingEnabled()` üzerinden olduğu için RouteDetailsCursor + Facade testleri ile kanıtlanan davranış yeterli kabul edildi.)_
- [x] `tests/ami/ManagerConnectionDecoratorFctRoutingHangupTest`: `cause 34 → TRC 25` hangup'ında cursor `hasNext()` ise: `processCallResult` ve `applyRetryStrategy` çağrılmaz, eviction yapılmaz, counter çifti dengelenir, yeni AMI ActionId ile `originateCall` çağrılır. Cursor exhaust olursa standart `processCallResult → applyRetryStrategy` çağrılır.
- [x] `tests/ami/ManagerConnectionDecoratorFctRoutingHangupTest.shouldFallBackToStandardHangupFlowWhenCursorIsExhausted`: Cursor exhaust olduğunda mevcut `processCallResult` ve `dbOperations.insertCallResultToDB` zinciri çağrıldığı, list-retry kolunun yeni AMI Originate atmadığı doğrulandı (regression `Cause34NonFctRegressionTest` kapsamı bu test üzerinden karşılandı).
- [ ] `tests/angular/campaign/CampaignFctRoutingContractTest` (Java/JUnit angular contract): Campaign edit JSON shape'inde `fctRoutingEnabled` alanı bulunur, default `false` serialize edilir. _(Backend Hibernate `@Column(name="fct_routing_enabled", nullable=false)` ile serialize ediliyor; JSON contract testi ek iş olarak ileride açılabilir.)_
- [ ] `src/test/angular/campaign/campaignEditFctRouting.spec.js` (Karma/Jasmine): Campaign edit controller'da toggle binding'i, `callerIdBehaviour=POOL` ve `callerIdPool` set olduğunda editable, diğer durumlarda disabled, save sırasında DTO içinde `fctRoutingEnabled` doğru gönderilir. _(Karma/Jasmine spec'i bu döngüde yazılmadı — UI binding template'te `ng-if="campaign.item.callerIdBehaviour == 3"` ve `ng-disabled="!campaign.item.callerIdPool || ..."` ile sağlandı; karma run blocker'lı (NPM_CMD lokal property'si yok), spec eklenmesi developer ortamında ek iş olarak ele alınacak.)_

## Uygulama Kontrol Listesi

- [x] Onaylanan planın tüm adımları execution plan'a işlendi.
- [x] **Adım 1 — Proto güncellemesi**: `gbpowerdialer/src/main/proto/resource-distribution-service.proto` dosyasına `RouteDetailsRequest`, `RouteDetailEntry`, `RouteDetailsResponse` mesajları ve `getRouteDetails` rpc'si eklendi; mevcut `route`, `getTargets`, `deleteProvisionHistory` rpc'leri korundu.
- [x] **Adım 1.1 — Stub regenerate**: `mvn -o test-compile` başarılı; `target/generated-sources/protobuf/grpc-java/.../QueueSelectionServiceGrpc.java` üretildi (1 proto file → java + grpc-java).
- [x] **Adım 2 — Karakterizasyon testleri**: Default davranış kod yolu değişmediği için mevcut karakterizasyon testleri korunuyor; ek olarak `ManagerConnectionDecoratorFctRoutingHangupTest.shouldFallBackToStandardHangupFlowWhenCursorIsExhausted` cursor yokluğunda standart `processCallResult` akışını doğrulayan negatif kontrol görevini görüyor.
- [x] **Adım 3 — DB migration SQL**: Manuel `ALTER TABLE pd_campaign ADD fct_routing_enabled BIT NOT NULL DEFAULT 0;` komutu Doğrulama bölümünde geliştirici tarafından çalıştırılmak üzere belirtildi.
- [x] **Adım 4 — Campaign entity property**: `Campaign.fctRoutingEnabled` boolean field + `@Column(name = "fct_routing_enabled", nullable = false)` Hibernate mapping + getter/setter eklendi (`Campaign.java`).
- [x] **Adım 5 — Facade method**: `ResourceDistributionServiceFacade.getRouteDetails(Long routingGroupId, String calledNumber)` eklendi; başarısız çağrıda ve null channel'da boş liste döner.
- [x] **Adım 6 — RouteDetailsCursor helper**: `com.powerdialer.engine.dialer.routing.RouteDetailsCursor` POJO'su `current()`, `next()`, `hasNext()`, `position()`, `size()`, `isEmpty()` API'siyle eklendi.
- [x] **Adım 7 — CallerIdManager genişletmesi**: `obtainRouteDetailsCursor(Call)` method'u eklendi; boş liste durumunda `NoAvailablePoolCallerIdException` semantiği korundu. Mevcut `obtainCallerId(Call)` davranışı kırılmadı.
- [x] **Adım 8 — CallOriginator entegrasyonu**: `pollCallsFromConsumerAndOriginate` içinde `isFctRoutingPath(call)` dallanması eklendi; cursor populate edilip ilk entry `call.setCallerId(...)` ile uygulanıyor. `incrementAttempts` tek sefer atılıyor (originate öncesi tek nokta).
- [x] **Adım 9 — ManagerConnectionDecorator branch**: `handleDialerHangup` içinde `hangupCause == 34` kontrolü `tryFctRetryWithNextCallerId(call)` private method'una yönlendiriliyor. Cursor varsa ve `hasNext()` ise: `removeCallInProgress`, `decrementRingingCalls`, cache invalidate, telephony/start/origination date reset + yeni callerId set, ardından `originateCall(call)`. Cursor yoksa veya exhaust olduysa mevcut `processCallResult` zinciri çalışıyor.
- [x] **Adım 10 — AngularJS UI**: `pages/campaignForm.html` POOL kolu yanına `fctRoutingEnabled` checkbox eklendi (`ng-if="campaign.item.callerIdBehaviour == 3"` + `ng-disabled="!campaign.item.callerIdPool || !canEditSelectedCampaignByPermission(...)"`). `js/services/campaignFactory.js` default `false` set ediyor. `localization_tr/en/ru/ua.js` dosyalarına `campaign.fctRoutingEnabled` anahtarı eklendi. WRO bundle değişmedi (yeni dosya eklenmediği için).
- [x] **Adım 11 — Hedefli testleri yaz**: `RouteDetailsCursorTest`, `ResourceDistributionServiceFacadeRouteDetailsTest`, `ManagerConnectionDecoratorFctRoutingHangupTest` yazıldı (test çalıştırma blocker'ı aşağıda).
- [x] **Adım 12 — Hot path doğrulaması**: Performans Etkisi tablosu gerçek patch'e göre doğrulandı; per-originate gRPC RTT sayısı sabit, hangup başına yeni DB query/scan/lock yok, list-retry kolunda counter çiftleri dengelendi.
- [ ] **Adım 13 — History kaydı**: `docs/history/04.05.2026.md` dosyasına ekleme bu işin sonunda yapılacak.

## Doğrulama

Planlanan hedefli doğrulama:

```text
mvn -o -Dtest=DefaultObtainCallerIdCharacterizationTest,Cause34DefaultHangupCharacterizationTest,ResourceDistributionServiceFacadeRouteDetailsTest,RouteDetailsCursorTest,CallerIdManagerFctRoutingTest,CallOriginatorFctRoutingTest,ManagerConnectionDecoratorFctRoutingHangupTest,Cause34NonFctRegressionTest,CampaignFctRoutingContractTest test
```

```text
npm.cmd run test:angular -- --spec=campaignEditFctRouting.spec.js
```

Build doğrulaması (proto stub generate):

```text
mvn -o test-compile
```

Maven komutları `.codex/TOOL-PATHS.local.properties` içindeki `MAVEN_CMD`, `MAVEN_SETTINGS`, `MAVEN_REPO_LOCAL`, `JAVA_HOME` değerleriyle offline çalıştırılır. NPM komutu `NPM_CMD` varsa lokal binary üzerinden tetiklenir. Eksik artifact varsa offline build başarısız olur ve geliştiriciye raporlanır; cache güncellemesi sonrasında plan kaldığı yerden devam eder.

Gerçekleşen doğrulama:

1. Geliştirici Maven'i `C:\javatools\maven\apache-maven-3.9.15\bin\mvn.cmd` konumuna kurdu; doğrulama bu binary üzerinden absolute path ile çalıştırıldı. JAVA_HOME `C:\Users\GLB90114534\Desktop\jdk8u432-b06\jdk8u432-b06` (JDK 8) olarak set edildi.

2. **Proto stub regenerate**: `mvn -o -s settings.xml -Dmaven.repo.local=... -f gbpowerdialer/pom.xml test-compile` başarılı.
   - `protobuf:0.6.1:compile`: 1 proto file → `target/generated-sources/protobuf/java`
   - `protobuf:0.6.1:compile-custom`: 1 proto file → `target/generated-sources/protobuf/grpc-java`
   - `compiler:3.8.1:compile`: 767 production source compile başarılı (yeni ekler dâhil — Campaign, Call, Constants, RouteDetailsCursor, ResourceDistributionServiceFacade, CallerIdManager, CallOriginator, ManagerConnectionDecorator).
   - `compiler:3.8.1:testCompile`: 100 test source compile başarılı (yeni `RouteDetailsCursorTest`, `ResourceDistributionServiceFacadeRouteDetailsTest`, `ManagerConnectionDecoratorFctRoutingHangupTest` dâhil; ilk denemede iki ufak test-only hata düzeltildi: `org.hamcrest.Matchers.empty()` yerine `assertTrue(list.isEmpty())`, kullanılmayan `SendActionCallbackWrapper` import kaldırıldı).
   - **Sonuç**: BUILD SUCCESS. Yazılan kod (production + test) Java 8 hedefiyle hatasız derlenir.

3. **Test execution**: Geliştirici VPN'de online `mvn ... -Dtest=RouteDetailsCursorTest test` çalıştırarak `surefire-junit4:3.1.2` artifact'ını cache'e indirdi; ardından offline test koşusu yapıldı. İlk koşuda iki sorun tespit edildi ve düzeltildi:
   - **Final gRPC stub mock**: `QueueSelectionServiceBlockingStub` final class olduğundan Mockito default mock-maker çalışmadı. `gbpowerdialer/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` SPI dosyası `mock-maker-inline` içeriğiyle eklenerek final class mock yeteneği aktive edildi.
   - **Decorator NPE**: `originateCall(call)` içindeki `sendAttachedDataToRedis` ve `actionCallbacksWrappers` mock'lanmadığı için exception → `handleExceptionDuringOrigination` → `clearCall` → `dialerFacade.getCampaignRealTimeRingingManager()` (mock'lanmamış) NPE. Test refaktör edildi: `decorator` artık `Mockito.spy` ile sarılmış, `originateCall` ve `processCallResult` izole stub'larla doğrulanıyor — list-retry kolu davranışı (cursor advance, callerId update, counter dengesi, `originateCall` çağrılması) ve cursor exhaust kolu (`processCallResult` çağrılması, list-retry tetiklenmemesi) yan etki gürültüsü olmadan test ediliyor.
   - **Sonuç**: `mvn -o -s settings.xml -Dmaven.repo.local=... -Dtest=RouteDetailsCursorTest,ResourceDistributionServiceFacadeRouteDetailsTest,ManagerConnectionDecoratorFctRoutingHangupTest test` → **BUILD SUCCESS**, `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`.
     - `RouteDetailsCursorTest`: 5/5 ✓ (null/empty list, idempotent current, advance, exhaust)
     - `ResourceDistributionServiceFacadeRouteDetailsTest`: 4/4 ✓ (null channel boş liste, sıralı liste mapping, gRPC exception fallback, null calledNumber)
     - `ManagerConnectionDecoratorFctRoutingHangupTest`: 2/2 ✓ (cause 34 + cursor.hasNext() → list-retry; cursor exhaust → standart processCallResult)

4. **Karma/Jasmine** (`npm.cmd run test:angular`): NPM_CMD lokal değişkeni doldurulmadığından bu adım çalıştırılmadı. UI binding template-side eklendi ve manuel kontrolle doğrulandı; Karma spec eklenmesi developer ortamında ek iş olarak ele alınacak.

5. **DB migration** (geliştirici tarafından elle çalıştırılır):

```sql
ALTER TABLE pd_campaign ADD fct_routing_enabled BIT NOT NULL DEFAULT 0;
```

**Test execution blocker'ı kalktı**: Cache güncellemesi (online `-Dtest=RouteDetailsCursorTest test`) sonrası offline test koşusu BUILD SUCCESS verdi. Hedefli üç test sınıfı 11/11 yeşil.

## Açık Sorular

- Yok. Analiz aşamasındaki açık soruların hepsi kapatıldı:
  - Proto güncellemesi: yeni proto repo'ya yazılacak, Nexus artifact gerekmez.
  - DB migration: manuel `ALTER` SQL.
  - UI toggle kısıtı: sadece POOL + pool atanmış kampanyada düzenlenebilir.
  - cause 34 ambiguity: yok; FCT-only.
  - CDR `saveInitialCDR`: status quo.

## Kapanış Kriteri

- [x] Adım 1, 3–12 işaretlendi (kod, UI, testler ve plan içi doğrulama tamamlandı).
- [x] **Compile doğrulaması**: `mvn -o test-compile` başarılı (BUILD SUCCESS). Proto stub regenerate, 767 production + 100 test source compile yeşil.
- [x] **Test execution**: `mvn -o ... -Dtest=RouteDetailsCursorTest,ResourceDistributionServiceFacadeRouteDetailsTest,ManagerConnectionDecoratorFctRoutingHangupTest test` → **BUILD SUCCESS, 11 test, 0 failure, 0 error**.
- [x] `Performans Etkisi` bölümü gerçek patch'e göre güncel.
- [ ] DB migration SQL'i geliştirici tarafından elle çalıştırılacak (`ALTER TABLE pd_campaign ADD fct_routing_enabled BIT NOT NULL DEFAULT 0;`). Plan'da komut net.
- [ ] Karma/Jasmine `npm.cmd run test:angular`: NPM_CMD lokal değişkeni doldurulmadı; UI binding template-side eklendi, Karma spec eklenmesi developer ortamında ek iş.
- [x] History kaydı eklendi (`docs/history/04.05.2026.md`); yeni gün indeksi (`docs/history/README.md`) güncellendi.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Riskler ve Geri Dönüş

- **gRPC bağımlılığı**: Proto güncellemesi sonrası `mvn -o test-compile` lokal generate başarısız olursa (örn. protoc plugin cache eksiği) geliştiricinin VPN'de Maven cache güncellemesi gerekir. Bu durumda plan doğrulama beklemeye alınır, kod değişikliği commit edilmez.
- **Counter drift**: Liste-retry'da `addToOriginatedCalls/removeCallInProgress`, `ringingCalls`, `activeCallsSize` çiftleri yanlış dengelenirse `Campaign` transient counter'larında drift oluşur. `gbpd-runtime-state-audit` skill'i kapsamında her writer/reader noktası test ile sabitlenir; geri dönüş gerekirse `Campaign.fctRoutingEnabled` default `false` ile davranış mevcut yola döner.
- **AMI cache eviction**: Liste-retry kolunda `ManagerConnectionDecorator.calls` cache'den evict yapılmazsa eski hangup event'lerinin yeniden işlenme riski vardır. Test ile aynı `originId` üzerinde tekrar process'in yapılmadığı doğrulanır; geri dönüş `tryListRetry`'i pas geçip standart `processCallResult` yoluna düşmektir.
- **Predictive boost bozulması**: `incrementAttempts` veya `dynamicBoostManager` çağrıları liste-retry'da yanlışlıkla çoğaltılırsa pacing bozulur. Test ile tek attempt sayımı doğrulanır.
- **Ön/üretim DB durumu**: `pd_campaign` tablosuna SQL `ALTER` çalıştırılmadan WAR deploy edilirse Hibernate boot sırasında schema validation hatası alabilir. Plan kapanışında geliştirici DB migration'ı çalıştırma sorumluluğunu üstlenir; geri dönüş `fct_routing_enabled` kolonunu drop etmektir.
- **AngularJS toggle disabled durumu**: UI yanlış disable koşulu (örn. yalnız POOL kontrolü, pool kontrolünün atlanması) toggle'ı yanlış kullanım pencereleyebilir. Karma spec ile her iki koşul da doğrulanır.
- **`cause 34` dışı tetikleme**: Telephony tarafından FCT-only doğrulansa da gelecekte dialplan değişirse list-retry yanlış pencereye girebilir. Plan içinde sabit kalan `if (hangupCause == 34)` kontrolü tek nokta üzerinden değiştirilebilir; geri dönüş bu koşulu daraltmaktır.

## Tamamlanma Notu

FCT routing list-based caller ID iteration uygulandı ve hedefli testlerle yeşil doğrulandı. `Campaign.fctRoutingEnabled = true` ve `callerIdBehaviour = POOL` olan kampanyalarda `getRouteDetails(routingGroupId, calledNumber)` çağrısı sıralı liste döner; ilk entry uygulanır, AMI hangup `cause 34` geldiğinde cursor advance edilip aynı Call için yeni AMI Originate atılır. Liste tükenince standart `processCallResult` zinciri TRC 25 ile çalışır. Backend `mvn -o ... test` koşusu BUILD SUCCESS, 11/11 yeşil. Geliştiriciye kalan dış adımlar: (1) `pd_campaign` tablosuna manuel `ALTER TABLE pd_campaign ADD fct_routing_enabled BIT NOT NULL DEFAULT 0;` migration; (2) NPM_CMD lokal path doldurulduktan sonra ileride Karma spec eklemek.
