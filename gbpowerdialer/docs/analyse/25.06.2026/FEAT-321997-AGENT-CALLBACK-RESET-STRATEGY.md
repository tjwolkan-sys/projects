# FEAT Agent Callback Reset Stratejisi (Timer / Attempt Count)

## Bağlam

Task `321997` kapsamında, `callbackToAppointer` (randevuyu alan / kaydı son işleyen agent'a ait callback) senaryosunda mevcut tek mekanizma zamana dayalı sıfırlamadır (`appointerReset` + `appointerResetTimeout`). Üretimde bu ayar kapalı (`appointerReset=FALSE`, `appointerResetTimeout=3600`) olduğundan timeout fiilen ölüdür: randevulu callback'in sahibi müsait değilken kayıt sahiplikten hiç çıkmaz.

Sonuç olarak callback-only listelerde (fresh tükenmiş, retry yok; ör. mesai sonrası 18:00 için kurulan callback'ler aynı anda vadeye girince) iki başarısızlık rejimi gözlenir:

- **Rejim A — sessiz durma:** Sahibi offline/aktif sette olmayan randevulu callback'ler hiç fetch edilmez (üretici `user_id IS NULL OR user_id IN (aktif)` filtresi + `appointerReset=FALSE` ile kaçış yok); cache boşalır, dialer susar ama UI "callback kaldı" gösterir.
- **Rejim B — churn / throughput çöküşü:** Sahibi online-ama-meşgulken callback fetch edilir, ama ACD ataması yalnız idle kuyruğa baktığından (`findAppointedUser`) atanamaz; `processCall` kaydı DB'ye geri sekitir (`returnCallToDb` yalnız `is_fetched=NULL` yapar), üretici sonraki tick'te tekrar çeker → sonsuz churn. Müsait olan ama sahip olmayan agent'lara bu callback'ler asla teklif edilmez.

Üretim logu rejimi doğruluyor: `Originate starvation ... slots=25 originated=0 queued=100 idleAgents=2`. Sorun GAMMA ve ALPHA predictive stratejilerinin ikisinde de görüldü; çünkü randevulu callback predictive overdial yoluna (`resolveUserFindingContext`) hiç girmiyor.

İş birimi talebi: randevulu aramalar **önce sahibi tarafından, adet ve süreyle sınırlı** denensin; sahibe ulaşılamıyorsa çağrı kaçırılmadan **kampanya geneline (havuza)** devredilsin. Bu davranış eski ININ sistemindeki "Agent Callbacks" panelinden geliyor (`images/callback.png`).

## İş Etiketi

`feat`

`Öncelik: hotfix` — üretimde aktif starvation'ı gideren bir özellik olduğu için aciliyetlidir.

## Etkilenen Alanlar

Backend:

- `gbpowerdialer/src/main/java/com/powerdialer/util/Constants.java` — `DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_ATTEMPTS` + `RESULT_FIELDS_ALLOWED_TYPES` girişi; `APPOINTER_RESET_STRATEGY` (TIMER/ATTEMPT_COUNT) enum.
- `gbpowerdialer/src/main/java/com/powerdialer/model/Campaign.java` — yeni alanlar: `appointerResetStrategy`, `agentCbRescheduleDelayMin`, `agentCbConvertAfterAttempts`, `colResultCallbackAttempts` (mevcut `appointerReset`/`appointerResetTimeout` korunur).
- `gbpowerdialer/src/main/java/com/powerdialer/model/Call.java` — `callbackAttempts` ailesi (mevcut `attempts` ailesi klonu, açık null-guard).
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallOriginator.java` — `processCall` appointed dalı (`:327-337`) → tek `handleAppointedOwnerUnavailable` chokepoint.
- `gbpowerdialer/src/main/java/com/powerdialer/engine/db/CampaignDbOperations.java` — yeni `rescheduleAppointedCallback[Async]`; `addCallbackCondition` (`:881-898`) strateji dallanması; `insertIntoCampaignResultTable` (`:2128`) bozuk-tırnak düzeltmesi.
- `gbpowerdialer/src/main/java/com/powerdialer/engine/acd/ACDEngine.java` — logout/pause ayrımı için login-presence kaynağı (`getAllLoginUsers`/`systemActiveUsers`) tabanlı yardımcı; `isCampaignHasOnlineUser` bu karar için yeterli değil.
- `gbpowerdialer/src/main/java/com/powerdialer/service/CampaignDataTypeChecker.java` — yeni kolon tip doğrulaması (`:84` checkColumn satırı).
- `gbpowerdialer/src/main/java/com/powerdialer/dto/CampaignFormDTO.java` ve `util/CampaignPropertyCopier.java` — yeni alanların DTO/copier taşınması (Dozer isim-eşli).
- `gbpowerdialer/src/main/java/com/powerdialer/redis/event/interactionnotifier/dto/CampaignInDTO.java` ve `mapper/CampaignInMapper.java` — yeni alanları ekle; `appointerReset`/`appointerResetTimeout` wire-contract'tan **silinmez**.

Frontend (AngularJS):

- `gbpowerdialer/src/main/webapp/js/constants.js` — enum + tip haritası client mirror'ı.
- `gbpowerdialer/src/main/webapp/pages/campaignForm.html` — Timer/Attempt seçici + karşılıklı freeze + 4 alan + `colResultCallbackAttempts` select klonu.
- `gbpowerdialer/src/main/webapp/js/controllers/campaignFormCtrl.js` — binding, `initializedResultColumns` (`:111-114`), iki default-atama noktası (`:449`, `:1170`), freeze (ng-disabled) mantığı.
- `gbpowerdialer/src/main/webapp/js/services/campaignFactory.js` — yeni alan default'ları (`:84-90`).
- `gbpowerdialer/src/main/webapp/js/localization/localization_{en,tr,ru,ua}.js` — 4 dosya, key'ler.
- `gbpowerdialer/src/main/webapp/WEB-INF/wro.xml` — bundle sıra kontrolü.

Test:

- `gbpowerdialer/src/test/java/tests/...` — karakterizasyon + regression (appointer churn, reschedule UPDATE, `callback_attempts` hydration, logout/pause dalı).
- `CallOriginatorTest`, `CallProducerTest`, `CallbackResultPersistenceRegressionTest`, `CampaignInMapperTest`, `CampaignsResourceTest` — mevcut pinning testlerin güncellenmesi.
- `gbpowerdialer/src/test/angular/...` — campaign form freeze davranışı + Java/JUnit frontend contract testi.

Migration:

- Config DB için elle `ALTER TABLE` (5 kolon); müşteri result tablosunda `callback_attempts` fiziksel kolonu (onboarding adımı). Geçiş **manuel** yapılacak (mevcut kampanyalar otomatik dönüştürülmez).

## Mevcut Davranış

- `Campaign.canOriginateMoreCalls()` = `callsInProgressCount < concurrentLines` (`Campaign.java:2096`); randevulu callback bu sayacı artırmaz (originate olmadığı için), yani throttle değil.
- `CallProducer` callback'i önce çeker; `addCallbackCondition` `callback_date <= now` + `appointerReset` açıksa `OR callback_date <= now-resetTimeout` ekler (`CampaignDbOperations.java:881-898`). `appointerReset=FALSE` iken kaçış yok; sahip aktif sette değilse satır fetch edilmez (`CallProducer.getCallBacksCount` 0 döner, `:446-456`).
- `ACDEngine.processACD` randevulu callback'i `findAppointedUser`'a yönlendirir; bu yalnız `getCampaignIdleUsers`'i tarar (`:408-413`). Sahip idle değilse `user=null`.
- `CallOriginator.processCall` (`:304-347`): sahip offline ise (`:327-331`) veya online-ama-`user==null` ise (`:334-337`) `returnCallToDb` (`:375-381`) — yalnız `is_fetched=NULL`. `callback_date <= now` kaldığından üretici hemen tekrar çeker → churn.
- `Call.isAppointerTimedOut()` yalnız `appointerReset=TRUE` iken `true` olabilir (`Call.java:813-821`); kapalıyken kilit hiç açılmaz.
- `CallProducerConsumerTemplate.cleanQueue` yalnız FRESH ve RETRY'ı evict eder, CALLBACK'i asla (`:95-124`).

## Beklenen Davranış

### Ekran (Campaign Form → General)

- `Callback to Appointer` **veya** `Allow Callback Type by Webphone` (`allowOverwriteByWebphone`) en az biri aktif olmadan sıfırlama dallanma alanları aktifleşmez.
- İki dallı, karşılıklı dışlamalı seçim:
  - **Reset Appointer by Timer:** `Appointer Reset Timeout (s)` aktif; `Reschedule Delay` + `Convert to campaign-wide ... after X attempts` görünür ama **disabled (freeze)**.
  - **Reset Appointer by Attempt Count:** `Reschedule Delay (minute)` + `Convert to campaign-wide ... after X attempts` aktif; `Reset Appointer by Timer` + `Appointer Reset Timeout (s)` disabled (freeze).

### Çalışma zamanı

- "Agent bulunamadı" = sahip **Pause / Logout / başka çağrıda (meşgul/in-call/ringing)**. Bu durumda dış arama **kesinlikle başlatılmaz**.
- **Strateji = TIMER:** davranış aynen legacy kalır (değişmez). Karar: reschedule-delay yalnız Attempt dalına uygulanır; Timer dalı timeout'a kadar mevcut return-to-DB churn'ünü korur (bilinçli kabul edilen tradeoff).
- **Strateji = ATTEMPT_COUNT:**
  - Sahip Pause/meşgul ise: dış arama yapılmaz; `callback_date` kullanıcı tanımlı `Reschedule Delay` kadar (ör. 20 dk) ileri ötelenir ve **agent bazlı iç sayaç (`callback_attempts`) +1** artırılır. Bu erteleme genel `Max Attempts`'i **tüketmez**.
  - İç sayaç tanımlı limite (ör. 3) ulaştığı an, sahip hâlâ bulunamıyorsa: **ek delay beklenmeden, o anda** kayıt "Campaign-wide" yapılır (`user_id=NULL`, `callback_date=now`) ve havuzdaki ilk uygun agent'a en yüksek öncelikle verilir.
  - **Logout istisnası:** sahip tamamen logout ise; bekleme süresine ve kalan deneme sayısına bakılmaksızın kayıt **anında** havuza düşürülür.
  - Havuzdan bir agent'a atanıp gerçek dış arama başladığı anda genel `Max Attempts` devreye girer (mevcut `CallAttemptPolicy.incrementForOrigination`).

### Kalıcılık ve ölçek (zorunlu)

- İç sayaç JVM'de değil, result tablosunda `callback_attempts` kolonunda tutulur; her fetch'te `rawData`'dan okunduğu için return-to-DB ve node restart'ı atlatır. Yeni static state yok.
- Reschedule + convert tek atomik UPDATE ile, sayım ve release kararı **sunucu tarafında** (SQL CASE) yapılır; iki node yarışında lost-update guard (`AND callback_attempts = expectedOld`) ile idempotent.

## Kod Okuma Bulguları

- **Chokepoint:** `CallOriginator.processCall` appointed dalı (`:318-340`), `acdEngine.processACD` sonrası "sahip atanamadı"yı gören tek nokta. Eski iki `returnCallToDb` çıkışı `handleAppointedOwnerUnavailable` ile tek metoda indirgenir; ayarlar boşsa eski davranış korunur (geriye uyum).
- **Release mekanizması:** `user_id=NULL`, sahipliği bırakmanın yetkili yolu. `addAgentsIdsCondition` (`:900-909`) `user_id IS NULL` kolu zaten herhangi agent'a açıyor; `Call.getUserIdAppointedToCallback()` (`:348-354`) null dönünce appointed dalı atlanır. `callback_type` bu iş için **kozmetik** — `user_id=NULL` yeterli.
- **`callback_type`'a dokunulmaz:** `CallProducer.java:599` `CALLBACKTYPE.valueOf(...)` bilinmeyen/sayısal değerde fırlatıp satırı düşürür; yeni hiçbir yol `callback_type` yazmaz (explicit non-action). Bu alan zaten `fromAny` ile sertleştirilebilir.
- **Sayaç kolonu:** mevcut `ATTEMPTS` ("attempts") telefon retry sayacı (`appendPerCycleAttemptsLimitToRetryQuery`, `:952-967`) — yeniden kullanılırsa retry limiti bozulur. Ayrı `CALLBACK_ATTEMPTS` kolonu zorunlu. Tip whitelist `Constants.java:1146` (`ATTEMPTS` eşi); start doğrulaması `CampaignDataTypeChecker.java:84`.
- **Yazım yolu:** `updateCampaignListTableRow` (`:1824-1952`) zaten `callback_date`, `user_id`, `is_fetched`'i tek atomik UPDATE'te `uniqueKey` ile yazıyor; `setFilteredByDncListStatus` (`:1424-1443`) `user_id=NULL` idiyomu için precedent; `removeFetchedDataFlagFromCallsAsync` (`:1519-1570`) deadlock-retry sarmalayıcı şablonu.
- **Tarih:** `callback_date` OLE serial double'dır (`Utils.convertDateToDouble`, `:883,888`). Yeni tarih mutlaka bu dönüşümden geçer.
- **Logout/Pause ayrımı:** `systemActiveUsers` (login presence, `ACDEngine.java:59`) `idleUsers`'tan ayrı; `User.getStatus().isAvailableForDialerCalls()` (`User.java:211`) mevcut. `getAllLoginUsers` (`:539-551`) `systemActiveUsers` tabanlı. **`isCampaignHasOnlineUser` (`:480-484`) bu karar için yeterli değil** — idle ∪ çağrı-türevli registered'a baktığından, molada olup aktif çağrısı olmayan agent'ı "offline" sanar → erken havuza düşürür. Karar `systemActiveUsers`/login-presence kaynağından verilmeli.
- **Call-window:** `processCall` `!isNowFitCallTimeRange()` kontrolü ACD'den önce (`:305-308`) `returnCallToDb` çağırıyor; rescheduled `callback_date` arama penceresi dışına düşerse pencere sınırında churn yeniden doğar.
- **Contract yüzeyi:** Redis `CampaignInDTO`/`CampaignInMapper` `appointerReset`/`appointerResetTimeout` taşıyor; `CampaignInMapperTest` (`:95-103`) pinning test. Dozer isim-eşli auto-map (isim tutmazsa sessiz veri kaybı). UI: `campaignFormCtrl.js` `initializedResultColumns` + iki default noktası + select klonu + 4 localization dosyası. `CampaignMemento` yalnız runtime counter taşıyor; yeni config alanları bilinçli olarak mementoya **girmez**.

## Riskler

- **Sayaç çift yazımı / drift:** `callback_attempts`'in tek yazarı reschedule UPDATE olmalı; `insertCallResultToDB`'ye emit eklenmez (çift sayım + `:2128` bozuk-tırnak riski). Hep sunucuda `+1`, asla client değeriyle.
- **Lost-update (iki node):** convert kararı SQL CASE ile sunucuda; `AND callback_attempts = expectedOld` idempotency guard; `is_fetched=NULL` koşulsuz.
- **Logout/Pause yanlış sınıflama:** yanlış kaynak (call-türevli registered) kullanılırsa moladaki agent logout sanılıp erken havuza düşer; login-presence kaynağı kullanılmalı.
- **Out-of-window reschedule churn:** Attempt dalında reschedule penceresi aşarsa churn geri gelebilir; bu kenar durumun ele alınışı Açık Sorular'da netleştirilecek.
- **Timer dalı churn'ü:** kararla Timer dalı değiştirilmiyor; bu dalda churn timeout'a kadar sürer (kabul edildi).
- **Contract eksikliği:** `CampaignDataTypeChecker` satırı atlanırsa kolon tipi start'ta doğrulanmaz → hot path'te `rawData.getInt` patlar. Redis DTO/mapper ve `constants.js` mirror'ı atlanırsa contract/picklist kırılır.
- **Migration:** geçiş manuel; mevcut kampanyalar dokunulmadan kalır, strateji kampanya bazında elle seçilir. Yanlışlıkla otomatik dönüştürme yapılmamalı.

## Performans Etkisi

`Hot path, ek maliyet yok (net azalma).` `CallOriginator.processCall` ve `CallProducer` fetch çağrı/tick başına hot path'tir. Yeni yol, appointed-fail başına eskiden de var olan **bir** async UPDATE (`returnCallToDb`) yerine **bir** atomik UPDATE çalıştırır; ek IO yok. Kritik fark: `callback_date` ileri ötelendiği için satır her tick yerine reschedule kadansında tekrar çekilir → **toplam DB yazımı ve re-fetch yükü ciddi azalır**, head-of-line block çözülür. `isCampaignHasOnlineUser` çağrısı tekildir (mevcut çağrı değiştirilir, eklenmez); yeni full-collection scan yoktur. Logout/Pause ayrımı için login-presence sorgusu `getCampaignIdleUsers` ile aynı maliyet sınıfındadır.

## TDD Stratejisi

- Önce karakterizasyon: mevcut appointer churn davranışını (return-to-DB, `is_fetched=NULL`) ve `appointerReset=FALSE` kilidini test ile kilitle.
- `rescheduleAppointedCallback` SQL contract'ı: tarih OLE double dönüşümü, sunucu tarafı `+1`, CASE ile convert (`user_id=NULL`, `callback_date=now`), `expectedOld` guard — mock `DBEngine`/private helper pattern'iyle, gerçek DB açmadan.
- `processCall` chokepoint senaryoları: sahip offline (logout) → anında havuz; sahip pause/meşgul → reschedule+say; sahip idle → normal originate; Timer stratejisi → davranış değişmemeli (regression).
- `callback_attempts` hydration (`Call`): lazy, `rawData`'dan, null-guard'lı.
- Contract: `CampaignInMapperTest` + `MockFactory` yeni alanlar; `CampaignsResourceTest` round-trip; `CampaignDataTypeChecker` yeni kolon doğrulaması.
- Frontend: `npm.cmd run test:angular` ile form freeze/karşılıklı kilit davranışı; Java/JUnit frontend contract testi (yeni input + select + localization key).

## Önerilen Çözüm Yönü

1. `Constants`: `CALLBACK_ATTEMPTS` kolon adı + tip whitelist; `APPOINTER_RESET_STRATEGY` enum (TIMER/ATTEMPT_COUNT).
2. `Campaign` + `CampaignFormDTO` + `CampaignPropertyCopier`: 4 config alanı + `colResultCallbackAttempts` (isim-eşli getter/setter).
3. `Call`: `callbackAttempts` ailesi (lazy hydration, null-guard).
4. `ACDEngine`: logout vs pause/meşgul ayrımı için login-presence yardımcısı.
5. `CampaignDbOperations`: `rescheduleAppointedCallback[Async]` (tek atomik CASE'li UPDATE); `addCallbackCondition` strateji dallanması; `:2128` bozuk-tırnak düzeltmesi.
6. `CallOriginator.processCall`: `handleAppointedOwnerUnavailable` chokepoint (TIMER → eski yol; ATTEMPT → reschedule/convert + logout dalı).
7. `CampaignDataTypeChecker`: yeni kolon tip doğrulaması.
8. Redis `CampaignInDTO`/`CampaignInMapper`: yeni alanları ekle, eskileri koru.
9. UI: `campaignForm.html` toggle + freeze + alanlar + select; `campaignFormCtrl.js` binding/default/initializedResultColumns; `campaignFactory.js` default; 4 localization dosyası; `wro.xml` kontrol.
10. Migration scriptleri (config DB ALTER + result-table onboarding notu).
11. Hedefli Java testleri + frontend testleri ile davranış ve contract'ı kilitle.

## Açık Sorular ve Netleştirme İhtiyacı

Tüm açık sorular geliştirici tarafından 25.06.2026'da yanıtlandı; karar netleşti:

1. **Convert anında `callback_attempts`:** Aynı atomik UPDATE'te `0`'a sıfırlanır — campaign-wide faz temiz başlasın.
2. **UI default değerleri:** Reschedule Delay = `20` dk, Convert after = `3` deneme.
3. **Out-of-window reschedule:** Attempt dalında rescheduled `callback_date` arama penceresi dışına düşerse **bir sonraki pencere açılışına snap** edilir (auto-schedule davranışı kapsama girer). Mevcut `isNowFitCallTimeRange` (`Call.java:459-474`) timezone matematiği temel alınır.
4. **Campaign-wide'a dönen callback tipi:** **CALLBACK olarak kalır** (kuyrukta öncelik önde, `CallTypeComparator`); normal akışa dönmez.

## Execution Plan Zorunluluğu

Kod değişikliği gerekiyor. Execution plan `docs/execution-plan/25.06.2026/FEAT-321997-AGENT-CALLBACK-RESET-STRATEGY.md` altında, bu analiz incelenip yukarıdaki açık sorular kapanıp geliştirici nihai planı onayladıktan sonra oluşturulacak. Plan, TDD test planını, hedefli doğrulama komutlarını, `Performans Etkisi` bölümünü ve başarılı test kapanış kriterini içerecek; onaylanan planın hiçbir adımı atlanmadan yürütülecek.
