# FEAT Agent Callback Reset Stratejisi Uygulama Planı

## Analiz Referansı

`docs/analyse/25.06.2026/FEAT-321997-AGENT-CALLBACK-RESET-STRATEGY.md`

## Onaylanan Plan Özeti

Task `321997` için Callback to Appointer sıfırlama mekanizması, Campaign Form'da karşılıklı-dışlamalı iki stratejiye ayrılır: **Reset by Timer** (legacy, değişmez) ve **Reset by Attempt Count** (yeni). Attempt dalında, sahip Pause/meşgulken kayıt `callback_date += rescheduleDelay` ile ötelenir (arama penceresi dışına düşerse bir sonraki pencere açılışına snap edilir) ve result tablosundaki `callback_attempts` kolonu sunucu tarafında `+1` artırılır; bu erteleme genel `Max Attempts`'i tüketmez. Deneme limitine ulaşıldığında ek delay beklenmeden, **aynı atomik UPDATE'te** `user_id=NULL` + `callback_date=now` + `callback_attempts=0` ile kayıt campaign-wide yapılır ve havuzdaki ilk uygun agent'a CALLBACK önceliğiyle verilir. Sahip tamamen logout ise denemeye/süreye bakılmaksızın anında havuza düşürülür. Kararlar: reschedule-delay yalnız Attempt dalına uygulanır; default Reschedule Delay=20 dk, Convert after=3; convert anında sayaç 0'a sıfırlanır; out-of-window snap aktif; campaign-wide callback CALLBACK tipinde kalır; geçiş manuel.

## Amaç

Randevulu callback'lerin önce sahibi tarafından, adet ve süreyle sınırlı denenmesini; sahibe ulaşılamadığında çağrının kaçırılmadan kampanya havuzuna devredilmesini sağlamak ve mevcut return-to-DB churn / starvation davranışını Attempt stratejisinde gidermek.

## Kapsam Dışı

- **Reset by Timer** dalının davranışını değiştirmek (legacy aynen korunur; bu dalda timeout'a kadar mevcut churn sürer — bilinçli kabul).
- Sahipsiz/generic callback ve normal fresh/retry akışı.
- Predictive ALPHA/BETA/GAMMA budget/overdial mantığı (değişiklik bunun üstünde, chokepoint'tedir).
- Retry stratejisi ve genel `Max Attempts` semantiği.
- "Remove from cache if not called within X" (ININ #4) cache eviction — bu sürüm kapsamı dışı (opsiyonel Faz-2); reschedule zaten `callback_date`'i ilerlettiği için churn ana kaynağı kapanıyor.
- Mevcut kampanyaların otomatik migration'ı (geçiş manuel, kampanya bazında).

## Performans Etkisi

- **Hot path durumu:** Evet — `CallOriginator.processCall` (çağrı başına) ve `CallProducer` fetch (tick başına) hot path'tir.
- **Call/event/tick başına ek maliyet:** Yok / net azalma. Appointed-fail başına eskiden de bir async UPDATE (`returnCallToDb`) vardı; yerine bir atomik UPDATE gelir. `callback_date` ötelendiği için satır her tick yerine reschedule kadansında çekilir → toplam DB yazımı ve re-fetch yükü azalır.
- **IO/scan/lock/serialization/log etkisi:** Yeni full-collection scan, yeni per-event DB query veya geniş lock yok. `isCampaignHasOnlineUser`/login-presence çağrısı tekildir (mevcut çağrı değiştirilir, eklenmez). Snap hesabı `rawData` üzerinde saf aritmetiktir.
- **Azaltım veya ölçüm notu:** Reschedule UPDATE `removeFetchedDataFlagFromCallsAsync` deadlock-retry/backoff şablonunu kullanır; `AND callback_attempts = expectedOld` guard ile iki-node lost-update idempotent. Sayacın tek yazarı bu UPDATE'tir (`insertCallResultToDB`'ye emit eklenmez).

## TDD Planı

- [x] **Karakterizasyon:** legacy/TIMER yol (`returnCallToDb`) korunduğu `CallOriginatorTest` + `CallOriginatorAppointedCallbackResetTest` (TIMER dalı) ile kilitlendi.
- [x] **Reschedule SQL contract (regression):** `AppointedCallbackRescheduleSqlRegressionTest` (3 senaryo: forceConvert, CASE+guard, kolon-yok degraded) — ArgumentCaptor ile UPDATE SQL doğrulandı.
- [x] **`callback_attempts` hydration:** `CallCallbackAttemptsHydrationTest` (3) — kolon set/null/rawData-yok.
- [x] **processCall senaryoları:** `CallOriginatorAppointedCallbackResetTest` (3) — logout→forceConvert, pause/meşgul→reschedule, TIMER→legacy; idle→originate mevcut `CallOriginatorTest`'te; eşik-aşımı convert SQL regression CASE testinde.
- [x] **Logout/Pause kaynak ayrımı:** `CallOriginatorAppointedCallbackResetTest` `isCampaignHasLoggedInUser` true/false ile.
- [x] **Auto-schedule snap:** `CallRescheduledCallbackDateSnapTest` (3) — OFF→base, pencere içi→base, pencere dışı→ileri snap.
- [x] **Tip doğrulama:** `CampaignDataTypeChecker:84` `CALLBACK_ATTEMPTS` kontrolü eklendi (mevcut `ATTEMPTS` doğrulama deseninin birebir aynısı). Ayrı birim test eklenmedi; aynı kod yolu `CampaignsResourceTest` start akışında çalışır.
- [x] **Contract pinning:** `CampaignInMapperTest` (15) + `CampaignsResourceTest` (3) yeni alanlarla yeşil; `appointerReset`/`appointerResetTimeout` korundu.
- [ ] **Frontend (Karma/Jasmine):** `campaignFormResetStrategy.spec.js` yazıldı (freeze truth-table + constants). KOŞULAMADI — `node_modules` yok (blocker; bkz. Doğrulama).

## Uygulama Kontrol Listesi

- [x] Onaylanan planın tüm adımları execution plan'a işlendi.
- [x] `Constants`: `CALLBACK_ATTEMPTS` kolon adı + `RESULT_FIELDS_ALLOWED_TYPES` girişi; `APPOINTER_RESET_STRATEGY` (TIMER/ATTEMPT_COUNT) enum.
- [x] `Campaign` + `CampaignFormDTO`: yeni alanlar (isim-eşli getter/setter). `CampaignPropertyCopier` reflection tabanlı olduğundan değişiklik gerekmedi (yeni alanları otomatik kopyalar).
- [x] `Call`: `callbackAttempts` ailesi (lazy hydration, açık null-guard) + `computeRescheduledCallbackDate` snap.
- [x] `CampaignDataTypeChecker`: yeni kolon tip doğrulaması.
- [x] `ACDEngine`: logout vs pause/meşgul ayrımı için `isCampaignHasLoggedInUser` (login-presence).
- [x] `CampaignDbOperations`: `rescheduleAppointedCallback[Async]` (tek atomik CASE'li UPDATE + window snap); `addCallbackCondition` strateji guard'ı; `:2128` bozuk-tırnak düzeltmesi.
- [x] `CallOriginator.processCall`: `handleAppointedOwnerUnavailable` chokepoint (TIMER → eski yol; ATTEMPT → reschedule/convert + logout dalı).
- [x] Redis `CampaignInDTO`/`CampaignInMapper`: yeni alanlar eklendi, `appointerReset`/`appointerResetTimeout` korundu.
- [x] UI: `campaignForm.html` toggle+freeze+alanlar+select; `campaignFormCtrl.js`; `campaignFactory.js` default (20/3); 4 localization dosyası; `constants.js` mirror; `wro.xml` (yeni dosya yok, sıra değişmedi).
- [x] Migration: `gbpowerdialer/src/main/resources/sql/migration/FEAT-321997-...sql` (config DB 4 kolon + result-table onboarding notu).
- [x] TDD planındaki Java testleri kırmızıdan yeşile çevrildi (Karma blocker hariç).
- [x] Hot path performans etkisi doğrulandı (ek IO yok, net azalma — bkz. Performans Etkisi).
- [x] Doküman ve history güncellemesi tamamlandı.

## Doğrulama

Çalıştırılan komutlar (`.codex/TOOL-PATHS.local.properties` ile offline JDK8 + Maven 3.9.15):

```text
mvn -o -s settings.xml -Dmaven.repo.local=... -f gbpowerdialer/pom.xml -DskipTests clean compile
mvn -o ... -Dtest=CallCallbackAttemptsHydrationTest,CallRescheduledCallbackDateSnapTest,AppointedCallbackRescheduleSqlRegressionTest,CallOriginatorAppointedCallbackResetTest test
mvn -o ... -Dtest=CallOriginatorTest,CallProducerTest,CampaignInMapperTest,CallbackResultPersistenceRegressionTest test
mvn -o ... -Dtest=CampaignsResourceTest test
npm.cmd run test:angular   (KOŞULAMADI)
```

Sonuç:
- `clean compile`: **BUILD SUCCESS** (777 main kaynak).
- Yeni 4 test sınıfı: **12/12 PASS**.
- Etkilenen mevcut testler (CallOriginatorTest, CallProducerTest, CampaignInMapperTest, CallbackResultPersistenceRegressionTest, CampaignsResourceTest): **PASS**. `CallOriginatorTest`'te legacy yolun artık `isCampaignHasOnlineUser` çağırmaması nedeniyle gereksiz kalan 2 stub kaldırıldı (davranış korundu).
- `npm.cmd run test:angular`: **KOŞULAMADI** — blocker: `gbpowerdialer/node_modules` yok (karma/jasmine/angular-mocks/ChromeHeadless kurulu değil); offline kuralı gereği indirilmedi. `campaignFormResetStrategy.spec.js` yazıldı; geliştirici VPN'de `npm ci` sonrası koşulmalı.
- Kapsam dışı/önceden RED: `CampaignDbResultPersistDeadlockRetryRegressionTest` (untracked WIP, başka task FIX-GLB90114534; `insertCallResultToDB` deadlock-retry ve recovery SQL henüz uygulanmamış). Bu task'ın dokunmadığı metotlar; değişikliklerimizden ÖNCE de RED.

## Kapanış Kriteri

- [x] Planlanan Java testleri çalıştırıldı ve başarılı oldu (12 yeni + etkilenen mevcut testler yeşil).
- [x] Test çalıştırılamayan kısım (Karma frontend) için environment blocker açık yazıldı.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Riskler ve Geri Dönüş

- Strateji enum'u kampanya bazında olduğu için risk izole; yanlış davranışta kampanya `Reset by Timer`'a alınarak legacy davranışa dönülür.
- `callback_attempts` tek yazarlı ve sunucu tarafı `+1` olduğundan drift riski düşük; `expectedOld` guard iki-node yarışını idempotent yapar.
- `user_id=NULL` release geri alınamaz; release öncesi `user_id`'yi "son agent" olarak kullanan raporlama varsa kontrol edilmeli.
- Snap hesabı yanlışsa yalnız Attempt dalı etkilenir; Timer dalı ve normal akış korunur. SQL/snap regression testleri bu riski azaltır.
- Contract yüzeyi (CampaignDataTypeChecker, constants.js mirror, Redis DTO/mapper) atlanırsa build/contract/picklist kırılır; kontrol listesi bu adımları zorunlu kılar.

## Açık Sorular

Açık soru yok. Dört açık soru analiz dosyasında 25.06.2026'da kapatıldı (sıfırla=0, default 20/3, out-of-window snap, campaign-wide CALLBACK kalır).

## Tamamlanma Notu

Uygulama `feat/321997-agent-callback-reset-strategy` branch'inde tamamlandı (backend + Redis contract + migration + AngularJS UI + Java testleri). Backend offline derlendi ve hedefli Java testleri yeşil (12 yeni + etkilenen mevcut testler). Kalan açık doğrulama yalnız **Karma frontend** (environment blocker: `node_modules` yok) — plan bu yönüyle "frontend doğrulama beklemede". Geliştirici VPN'de `npm ci` sonrası `npm.cmd run test:angular` ile `campaignFormResetStrategy.spec.js` doğrulanmalı. Değişiklikler commit edilmedi; commit/PR hijyeni (gerekirse develop'a rebase) geliştiriciye bırakıldı.

Deploy notu: config DB ALTER (4 kolon) ve ATTEMPT_COUNT kampanyaları için result-table `callback_attempts` kolonu elle uygulanmalı (bkz. migration SQL). Geçiş manuel: mevcut kampanyalar dokunulmadan TIMER (legacy) davranışında kalır; sorun yaşanan kampanyalar "Reset by Attempt Count"a alınır.

Adversarial code-review (5 boyut: sağlıklı-akış, hot-path, SQL, snap, contract) sonucu: **sağlıklı/legacy/fresh/retry/callback akışı korunmuş — regresyon yok**; hot-path eşit veya daha ucuz; SQL doğru ve locale-güvenli. Review'da çıkan tek gerçek bulgu (MEDIUM, yalnız ATTEMPT modunu etkiler): `!canCount` alt-durumunda guard'sız `callback_attempts+1` artışı → kaldırıldı (bu alt-durumda sayaca dokunulmuyor). Fix sonrası hedefli testler 38/38 PASS. Operasyonel öneri (LOW): ATTEMPT modunda `agentCbRescheduleDelayMin` pozitif kalmalı (factory default 20); 0/boş bırakılırsa convert eşiğe kadar (≤3 deneme) UPDATE frekansı kısa süre yüksek olur (kendini sınırlar). Snap mantığı `CallRescheduledCallbackDateSnapTest` (3) ile kilitlendi (snap reviewer placeholder döndü; test kapsamı geçerli).

## Ek Değişiklikler (kullanıcı talebi, 26.06.2026)

İmplementasyon sonrası üç ek talep uygulandı (aynı branch):

1. **Schedule bitiş saati uyarısı (soft):** ATTEMPT_COUNT seçili + kampanyada günlük schedule (stop saati) tanımlı değilken (`!schedules || schedules.length==0`) form'da uyarı gösterilir ("günlük bitiş saati tanımlayın"). `campaignForm.html` inline `text-warning` + `campaign.attemptStrategy.scheduleEndTimeRequired` localization. Bloklamaz.
2. **callback_attempts kolonu zorunluluğu (hard):** ATTEMPT_COUNT seçili + callback gate aktifken `colResultCallbackAttempts` select'i `ng-required` → kolon eşlenmeden form `$invalid` → kaydetme engellenir (mevcut `if(!isValid) return`). Ayrıca inline `text-danger` uyarı + `campaign.attemptStrategy.callbackAttemptsRequired` localization sebebi açıklar. (Backend start-guard eklenmedi; UI save-block "seçtirme" gereksinimini karşılıyor, backend zaten kolonsuz durumda yalnız reschedule'a düşüyor.)
3. **Callback ataması availability kontrolü (tüm callback'ler):** `ACDEngine.findAppointedUser` ve `findSameUser` artık `getCampaignIdleUsers` yerine `getCampaignIdleAndAvailableUsers` üzerinde döner — sahip agent yalnız MT'si AVAILABLE (`isAvailableForDialerCalls() && !dialInProcess`) iken callback alır; molada/ACW ise alamaz (callback başka yola: ATTEMPT'te reschedule, TIMER'da return-to-DB). Bu yalnız attempt feature'ına değil, appointer + webphone same-user callback atamalarının hepsine uygulanır.

Doğrulama: yeni `tests/acd/CallbackAssignmentRequiresAvailableAgentTest` (4: appointer/same-user × available/mola) PASS; ilgili tüm hedefli testler **60/60 PASS** (offline Maven). 4 localization JS `node --check` OK. Karma yine blocker. NOT: `ACDEngineTest`/`DefaultFindMatchingUserStrategyTest` içindeki 3 sıralama testi (`status=null` comparator) **önceden flaky/RED** — `ACDEngine` committed haline alınıp koşulduğunda da patladığı stash testiyle kanıtlandı; bu işin değişiklikleriyle ilgisiz, dokunulmadı.

## Tek-tip (uniform) deneme modeli + offline owner fetch (kullanıcı talebi, 26.06.2026 - 2)

Kullanıcı senaryosu (owner denemeler arasında logout olur ve ertesi gün hiç dönmezse) mevcut tasarımda bir boşluk açığa çıkardı: offline owner'ın callback'i üretici tarafından hiç fetch edilmiyordu (owner `user_id IN (online)` filtresinde yok) → ne sayaç ilerliyor ne havuza düşüyordu (Rejim A offline owner için sürüyordu). Ayrıca "logout → anında havuza" yalnız dar bir yarışta tetikleniyordu. Talep: callback zamanı geldiğinde owner AVAILABLE değilse (online-meşgul **veya** offline farketmez) her seferinde `attempt +1`; eşikte (N) havuza — tek-tip.

Değişiklikler:

1. **Üretici fetch (Req 1):** `CallProducer.getCallBacksCount` ve `fetchRawDataCallbacks` artık ATTEMPT_COUNT modunda owner aktif-kullanıcı filtresini (`user_id IN (online)`) UYGULAMAZ (`&& !campaign.isAppointerResetByAttempt()` guard). Owner offline/işten-çıkmış olsa bile vadesi gelen appointer callback çekilir → originator'a ulaşır. TIMER/legacy filtre aynen korunur.
2. **Uniform attempt (Req 2):** `CallOriginator.processCall` ATTEMPT dalı artık `forceConvert`/`isCampaignHasLoggedInUser` (logout→anında) kullanmaz; sahip AVAILABLE değilse (online-meşgul veya offline) tek-tip `handleAppointedOwnerUnavailable(call)` → `rescheduleAppointedCallback` her çağrıda `attempt +1` + reschedule, eşikte campaign-wide. `CampaignDbOperations.rescheduleAppointedCallback[Async]` ve `handleAppointedOwnerUnavailable`'dan `forceConvert` parametresi kaldırıldı. Kullanılmayan `ACDEngine.isCampaignHasLoggedInUser` silindi. (Bu, daha önceki "logout → anında havuza" kararını tek-tip attempt-counting ile DEĞİŞTİRİR.)

Sonuç: offline owner'ın callback'i her reschedule periyodunda (delay) +1 sayılır; sayaç eşiğe ulaşınca `user_id=NULL` ile havuza devredilir (owner hiç dönmese bile). Owner döner ve AVAILABLE olursa kaldığı sayaçtan devam ederek ona bağlanır.

Doğrulama: `CallProducerTest` yeni 2 test (`attemptModeFetchesCallbacksWithoutOwnerGate`, `timerModeAppliesOwnerGate`); `CallOriginatorAppointedCallbackResetTest` ve `AppointedCallbackRescheduleSqlRegressionTest` uniform modele güncellendi. İlgili tüm hedefli testler **67/67 PASS** (offline Maven). Karma yine blocker; flaky sıralama testleri hâlâ kapsam dışı.
