# FIX-GLB90060295: Originate-Fazı Idle Rezervasyonu Kaynaklı Agent Starvation (PROGRESSIVE_RELEASED + PREDICTIVE-ALPHA)

> Dosya adı işin ilk kapsamını (`PROGRESSIVE_RELEASED`) taşır. 10.08.2026'da geliştirici isteğiyle
> kapsam `PREDICTIVE-ALPHA`'yı da içerecek şekilde genişletildi; dosya adı, history kayıtları ve
> `docs/README.md` referansları bozulmasın diye korundu.

- İş Etiketi: fix
- Öncelik: yüksek (üretim etkisi var; hotfix değil)
- Kimlik: GLB90060295 (task id yok)
- Branch: `fix/GLB90060295-originate-idle-rezervasyon-released-alpha` (`develop` üzerinden açıldı; ALPHA kapsama girdikten sonra `fix/GLB90060295-progressive-released-idle-rezervasyon` adından değiştirildi)
- Tarih: 10.08.2026
- Durum: **Onaylandı** — açık sorular 10.08.2026'da cevaplandı ve plana işlendi (bkz. `Cevaplanan Sorular`); uygulama sürüyor

## Bağlam

Güvencell operasyonu "dialer kişi sayısını doğru hesaplamıyor, arada çağrı çıkışı yavaşlıyor" şikâyetiyle geldi. Supervisor ekranında `GUVENCELL_GENEL` grubunda 5 agent aynı anda `Available` görünürken (In status 00:00:27–00:03:12) dialer bu agent'lara çağrı bağlayamıyor. Operasyon `dialingBoost` değerini 1.01 yapmış, sonuç değişmemiş.

10.08.2026 Elastic logları (`turkcell-*`, 13:00–14:00, 5 agent: 81937, 81938, 81945, 81949, 81950) üzerinden ölçüldü:

| Ölçüm | Değer |
|---|---|
| `ACDEngine.idleUsers` havuzunun tamamen boş olduğu süre | **%75,6** |
| Zaman-ağırlıklı ortalama havuz büyüklüğü | **0,30 agent** |
| Havuzda 4 veya 5 agent görüldüğü süre | 0 saniye |
| Havuzda 3 agent görüldüğü süre | 6,6 saniye (1 saatte) |
| Agent başına havuzda kalma oranı | 81937 %1,2 · 81938 %1,8 · 81945 %1,6 · 81949 %0,8 · 81950 %1,0 |

Agent 81937 için 12:31:46–13:56:25 penceresi hunisi: **117 idle rezervasyonu → 84 originate → 42 müşteri açtı → 43 AGI çalıştı → 30 agent'a Dial → 30 agent cevapladı**, 52 retry planlandı. Ring süresi medyan **10,7 sn**, p90 **34,1 sn**, max **60,8 sn**.

Ekran görüntüsünün alındığı an doğrudan kanıt: 81937 için 13:37:19–13:40:14 arasında **13 ardışık originate** yapılmış, hiçbiri bağlanmamış; agent bu yüzden 2 dk 55 sn `Available` kalmış.

Elenen hipotezler (bu incelemede kanıtla dışlandı):

- `User.calculateTimeInStatus()` NPE'si ve `ACD processing too slow` — ilgili Elastic sorgusu 0 hit döndü.
- Agent'ın idle kuyruğunda kalıcı takılması — `Idle user added`/`Idle user removed` sayıları agent bazında birebir dengeli (67/67, 113/113, 86/86, 53/53, 51/51).
- `dialInProcess`'in boost bütçesini sıfırlaması — `setDialInProcess(true)` yalnız `DialWithAbandonTimeoutScript.java:189`'da, agent'a dial edilirken set ediliyor; originate-fazı rezervasyonunda set edilmiyor.
- 21 adet `[IDLE-GATE]` satırının tamamı `status=Incall/Wrapup/Reserved` → doğru davranış.

## Mevcut Davranış

1. `ACDEngine.resolveUserFindingContext` (`ACDEngine.java:451-471`) BETA ve GAMMA dışındaki tüm tipleri `canUseIdleUsers` dalına düşürür. Bu dal `PROGRESSIVE_RELEASED`, `PROGRESSIVE_RESERVED`, `PREVIEW_FREE`, `PREVIEW_STRAIGHT` ve `PREDICTIVE`-ALPHA'yı birlikte taşır; `buildUserFindingContext(call, true)` çağırır.
2. `UserFindingContextBuilder.buildUserFindingContext` (`UserFindingContextBuilder.java:23-47`) `PROGRESSIVE_RELEASED`'i `PREDICTIVE` ile aynı `case` içinde tutar (satır 31-34) ve `shouldUseIdleUsers` değerini olduğu gibi geçirir.
3. `DefaultFindMatchingUserStrategy.getUsersQueue` (`DefaultFindMatchingUserStrategy.java:155-157`) `useOnlyIdleUsers=true` iken aday havuzu olarak yalnız `acdEngine.getIdleUsersInGroup(group)` kullanır.
4. `removeFromIdleUsersAndCheck` (`DefaultFindMatchingUserStrategy.java:187-211`) seçilen agent'ı **originate anında** `acdEngine.removeFromIdleUsers(user)` ile havuzdan çıkarır. `isGammaOriginatePhase()` (satır 223-230) bu çıkarmayı atlar, fakat koşulu `campaign.isGammaPredictiveCampaign()` ile kilitlidir.
5. Agent, müşteri çalarken (medyan 10,7 sn, p90 34,1 sn) havuz dışında kalır. Statüsü `Available` olduğu için supervisor ekranı, `countCampaignAvailableUsersNow` ve `availAgentsSize` onu avail sayar; ACD havuzu saymaz. Arayüz ile dialer arasındaki fark buradan doğar.
6. Havuz boşalınca `resolveUserFindingContext` `canUseNotBridgedUsers` → `isDialingBoostNotReached` (`ACDEngine.java:493-501`) dalına düşer: `notBridgedCalls < availableUsers * boost`. 5 agent × 1.01 = **5,05**, yani kampanya toplamda ~5 eşzamanlı çalan çağrı tutabilir. `countCampaignNotBridgedCalls` `answerDate == null` olan çağrıları saydığı için bu 5 slotu, rezervasyonun kendi ürettiği çalan çağrılar doldurur. Kendini besleyen starvation.
7. `dialingBoost` 1.00 → 1.01 değişimi tavanı 5'ten 5,05'e taşır; tam slot sayısı değişmez. Operasyonun gözlemi beklenen sonuçtur.
8. Agent bulunamayınca çağrı `CallOriginator.processCall` (`CallOriginator.java:388-393`) içinde `currentCallQueue.addFirst(call)` ile sessizce kuyruğa geri konur. **Tek satır log yoktur**; sorunun log üzerinden görünmemesinin sebebi budur.
9. `trySearchLessOccupiedUser` (`ACDEngine.java:287-339`) answer fazında yeniden seçim yapar, fakat üç kapısı da GAMMA'ya kilitlidir:
   - `refreshSelection` (satır 299-301): GAMMA → `!ownerBoundCallback`; diğerleri → `lastCallEndDate != null`.
   - `gammaInitialUnavailable` (satır 309-311).
   - NOAVAIL dalı (satır 326-334): yalnız GAMMA'da user `null` bırakılır; diğerlerinde satır 336 `call.setUser(initialUser)` ile kör geri dönüş yapılır.

### PROGRESSIVE_RESERVED neden dokunulmaz

`PROGRESSIVE_RESERVED`'de originate-fazı rezervasyonu hata değil, ürün garantisinin mekanizmasıdır:

- `UserFindingContextBuilder.java:35-42` — `PROGRESSIVE_RESERVED` case'i `finisherAction` ile `UserPauseMessage(userId, true)` publish eder. Agent ACD seçimi anında webphone seviyesinde **pause** edilir.
- `CallUpToDateKeeper.java:153-160` — agent `Available` olup tamamlanmamış çağrısı varken tekrar `UserPauseMessage(true)` gider.
- `ManagerConnectionDecorator.java:923-931` — hangup'ta `UserPauseMessage(false)` ile pause kalkar.
- `UserPopulationEvaluator.java:87-91, 98-102` — `PROGRESSIVE_RESERVED`, `ALWAYS_KEEP_USER_TYPES` ve `ALWAYS_KEEP_ASSIGNED_USER_TYPES` içindedir: çağrı başarısız olsa bile `userId`/`assignedUserId` korunur. Answer fazında agent değişebilir hale gelirse bu CDR sözleşmesi bozulur.
- `ManagerActionFactory.java:48-54` — `ProgressiveReleasedAction` ve `ProgressiveReservedAction` **kod olarak birebir aynıdır** (ikisi de yalnız `setContext` + `setExten`). Ayrım tamamen yukarıdaki davranış kurallarındadır. Bu yüzden "action farklı olduğu için güvenli" kestirmesi yoktur; guard'lar tip bazlı ve açık yazılmalıdır.

## Beklenen Davranış

1. `PROGRESSIVE_RELEASED` kampanyalarda agent originate fazında idle havuzundan **çıkarılmaz**. Ön atama semboliktir; gerçek seçim ve havuzdan çıkarma müşteri açtığında (answer fazı) yapılır. Böylece `getCampaignIdleUsersCount` gerçek avail agent sayısını gösterir ve supervisor ekranı ile dialer aynı sayıyı görür.
2. Originate fazında aday havuzu `getIdleUsersInGroup` yerine `getAvailableUsersQueueInGroup` olur (`useOnlyIdleUsers=false`); mevcut `minCallsNumber` yük dengeleme filtresi ve `UserAvailTimeComparator` sıralaması korunur.
3. Answer fazında gerçek rezervasyon `DialWithAbandonTimeoutScript.java:146` → `trySearchLessOccupiedUser` → `processACD` zincirinde yapılır; `tryLockUser` (`DialWithAbandonTimeoutScript.java:183`) eşzamanlı answer yarışını bugünkü gibi korur.
4. Answer fazında avail agent bulunamazsa `PROGRESSIVE_RELEASED`'de de user `null` bırakılır; `processDialCommand` döngüsü `waitForFreshUser()` ile gerçekten boşalan agent'ı yakalar. Meşgul olabilecek initial user'a kör geri dönüş yapılmaz.
5. `PROGRESSIVE_RESERVED`, `PREVIEW_FREE`, `PREVIEW_STRAIGHT`, `PREDICTIVE` (ALPHA/BETA/GAMMA) ve `OUTBOUND_IVR` davranışları **bit-eşit** kalır.
6. Agent bulunamayıp çağrı kuyruğa geri konduğunda throttle'lı bir WARN üretilir; aynı durum tekrarlandığında log üzerinden teşhis edilebilir.
7. **PREDICTIVE-ALPHA da aynı kapsama alınır (10.08.2026 geliştirici isteği).** ALPHA'da pacing zaten `DynamicBoostManager`'ın dinamik boost değeriyle yapılıyor; sorun, o boost'un çarpıldığı "avail agent" sayısının rezervasyon yüzünden gerçek değerinden küçük görünmesiydi — `PROGRESSIVE_RELEASED`'de ölçülen starvation'ın aynısı. Bütçe ALPHA için `floor(availableUsers × dinamikBoost)` olarak uygulanır, yani ALPHA'nın kendi pacing'i otorite kalır. **PREDICTIVE-BETA kapsam dışıdır**: bütçesini `PredictiveBetaCallManager` yönetiyor ve bu iş kapsamında incelenmedi (aynı starvation orada da olabilir; ayrı iş).
8. **Over-dial tavanı korunur.** Rezervasyon kalkınca idle sayısı pacing sinyali olmaktan çıktığı için `canUseIdleUsers` kısayolu `PROGRESSIVE_RELEASED` originate fazında kullanılmaz; tavan doğrudan `floor(availableUsers × dialingBoost)` eşzamanlı çalan çağrıdır. Bu olmadan değişiklik agent-orantılı tavanı tamamen kaldırırdı (geriye yalnız CPS/concurrentLines kalırdı) — bkz. `Riskler R3`.

### Davranışın anahtarı: kampanya tipi (feature flag yok)

Geliştirici kararı (10.08.2026): **kodda ayrı bir feature flag mekanizması olmayacak.** GAMMA'da da ayrı bir flag yoktur; davranış kampanyanın kendi konfigürasyonundan türer. Progressive tarafında bunun karşılığı kampanya formundaki **kampanya tipi** seçimidir. Dolayısıyla:

- Yeni `Campaign` alanı, DB kolonu, form alanı veya property eklenmez.
- Tek koşul: `campaign.isGammaPredictiveCampaign() || campaign.isProgressiveReleasedCampaign()`.
- Hangi kampanyanın `PROGRESSIVE_RELEASED` olacağı kampanya yönetiminin kararıdır; bu iş kapsamında değildir.

## Etkilenen Kod Alanları

| Dosya | Değişiklik |
|---|---|
| `gbpowerdialer/src/main/java/com/powerdialer/model/Campaign.java` (~2196) | Yeni `@Transient isProgressiveReleasedCampaign()` |
| `gbpowerdialer/src/main/java/com/powerdialer/engine/acd/OriginatePhase.java` (YENİ) | Originate-fazı kararının tek kaynağı (package-private helper) |
| `gbpowerdialer/src/main/java/com/powerdialer/engine/acd/UserFindingContextBuilder.java:31-34` | `PROGRESSIVE_RELEASED`'i `PREDICTIVE` case'inden ayır; originate fazında `shouldUseIdleUsers=false` zorla |
| `gbpowerdialer/src/main/java/com/powerdialer/engine/acd/DefaultFindMatchingUserStrategy.java:201, 223-230` | `isGammaOriginatePhase()` → `OriginatePhase` helper'ına devir |
| `gbpowerdialer/src/main/java/com/powerdialer/engine/acd/ACDEngine.java` | `resolveUserFindingContext`'e `PROGRESSIVE_RELEASED` originate dalı + yeni `isOriginateBudgetAvailable`; answer fazı guard'ları `PROGRESSIVE_RELEASED`'i de kapsar; `[GAMMA-SWAP]`/`[GAMMA-NOAVAIL]` → `[ACD-SWAP]`/`[ACD-NOAVAIL]` |
| `gbpowerdialer/src/main/java/com/powerdialer/model/Call.java` | `getUserIdAppointedToCallback` ve `isAppointerTimedOut` içindeki `Boolean` unboxing NPE'lerinin null-safe hale getirilmesi (uygulama sırasında ortaya çıktı, bkz. Doğrulama Sonucu) |
| `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallOriginator.java` | Throttle'lı `[ACD-NO-AGENT]` WARN |
| `gbpowerdialer/src/test/java/tests/acd/` | 4 yeni test sınıfı |
| `gbpowerdialer/src/test/java/com/powerdialer/engine/acd/ACDEngineTest.java`, `tests/acd/CallbackAssignmentRequiresAvailableAgentTest.java` | Eski davranışı kilitleyen karakterizasyon testlerinin güncellenmesi |

## Kapsam Dışı

Bu pakete **alınmayacak**, bu iş kapandıktan sonra ayrı plan olarak açılacak kalemler (geliştirici kararı 10.08.2026):

1. **`ACDEngine.getCampaignAvailableUsersCount` (`ACDEngine.java:627-638`) eksik `isEnabled` filtresi.** Diğer tüm sayım metotları `CampaignGroup::isEnabled` uygular, bu uygulamaz. Düzeltilmesi `isDialingBoostNotReached` bütçesini **düşürür** ve `PROGRESSIVE_RESERVED` ile `PREDICTIVE`-ALPHA'yı da etkiler.
2. **Müşteri açtı, agent'a hiç bağlanmadı vakası.** 81937'nin 43 AGI çalışmasının 30'unda `Sending Dial command` var; 13'ünde yok, 10'unda AGI 50 ms'den kısa sürüp çıkmış. Kök nedeni bu incelemede **kanıtlanmadı**.
3. **`trySearchLessOccupiedUser`'ın bugün `PROGRESSIVE_RESERVED`'de de çalışıp answer anında agent değiştirebilmesi.** `lastCallEndDate != null` iken devreye giriyor ve "User is changed" logunu üretiyor. RESERVED'in "aynı agent" garantisi ve `UserPopulationEvaluator` kuralıyla çelişiyor görünüyor; **etkisi ölçülmedi**.
4. `dialingBoost`'un semantiği ve kampanya formundaki davranışı.
5. **Performans ikinci adımı** (`getCallsByUserGroup` için grup bazlı kısa TTL'li snapshot cache) — ölçüm sonucuna bırakıldı (bkz. `Performans Etkisi`).

## Riskler

- **R1 — Görev 2 ve 3 ayrılamaz.** `removeFromIdleUsersAndCheck` satır 210 dönüşü `(userIsIdle || !useOnlyIdleUsers) && isUserPassedThreshold`. Yalnız originate-fazı çıkarmasını atlarsak `userIsIdle=false` ve `useOnlyIdleUsers=true` kalır → metot `false` döner → agent reddedilir → **kampanya hiç çağrı çıkarmaz**. İki değişiklik tek commit'te gitmeli; TDD'de bunu yakalayan test zorunlu.
- **R2 — Guard'ın yanlış yazılması.** `canUseIdleUsers` dalı 5 farklı kampanya tipini taşıyor. Guard "gamma değilse" veya "progressive ise" biçiminde yazılırsa `PROGRESSIVE_RESERVED`, `PREVIEW_*` ve `PREDICTIVE`-ALPHA da davranış değiştirir. Guard yalnız `isProgressiveReleasedCampaign()` olmalı.
- **R3 — Over-dial / abandon (uygulama sırasında bulundu ve kapatıldı).** İlk uygulamada rezervasyon kalkınca idle havuz ring boyunca dolu kaldığı için `canUseIdleUsers` hep `true` dönüyor, `resultUserFindingContext` ilk eşleşen dalda döndüğü için `dialingBoost` bütçesi **hiç değerlendirilmiyordu**; agent-orantılı over-dial tavanı tamamen kalkıyor, geriye yalnız CPS/concurrentLines kalıyordu. `isOriginateBudgetAvailable` ile kapatıldı: tavan `floor(availableUsers × boost)`.
  - **Davranış farkı (bilinçli):** eski `isDialingBoostNotReached` ham çarpımla (`notBridged < available × boost`) kıyaslıyordu; kesirli boost'ta bu tavanı bir çağrı şişiriyordu (5 agent × 1.01 = 5,05 → 6 eşzamanlı). `floor` ile 1.01 dürüstçe "agent başına 1" (5 eşzamanlı) anlamına gelir. Yani **boost 1.01 throughput'u artırmaz**; artırmak için boost anlamlı biçimde yükseltilmelidir. Bu, operasyona açıkça iletilmelidir.
  - Agent havuzda kaldığı için aynı agent birden fazla çalan çağrının adayı olabilir; iki müşteri aynı anda açarsa biri agent bulamaz. Korumalar: answer fazındaki `tryLockUser`, `excludeForThisCall`, `waitForFreshUser` ve `processAbandonTimeout` bugün de devrededir (GAMMA aynı modelde çalışıyor). Deploy sonrası abandon oranı yine de ölçülmeli.
- **R4 — Owner-bound callback.** Appointed / same-user callback'lerde sahip agent originate'te rezerve edilmeye devam etmeli. `!call.isOwnerBoundCallback()` koşulu `OriginatePhase` helper'ında aynen korunur.
- **R5 — Flag yok, geri dönüş yolu revert'tir.** Feature flag olmadığı için değişiklik deploy edildiği setteki **tüm** `PROGRESSIVE_RELEASED` kampanyaları etkiler. Kampanyayı `PROGRESSIVE_RESERVED` yapmak geri alma yolu **değildir** (semantiği farklıdır: agent pause + CDR user koruması). Sorun çıkarsa geri dönüş yolu commit revert + redeploy'dur. Kademeli açılış kararı kampanya yönetimindedir.
- **R6 — Log etiketi değişikliği.** `[GAMMA-SWAP]` → `[ACD-SWAP]`, `[GAMMA-NOAVAIL]` → `[ACD-NOAVAIL]`. Bu satırlar artık `PROGRESSIVE_RELEASED` için de üretildiğinden "GAMMA" öneki yanıltıcı olurdu. Log metni contract değildir (contract listesi: Redis channel, AMI event, result code, campaign state transition, profil property); ancak bu etiketleri kullanan kayıtlı Elastic sorguları varsa güncellenmelidir.
- **Genel:** Redis channel adları, AMI event adları, result code eşlemeleri, campaign state transition'ları ve profil property adları değişmez. Yeni kütüphane eklenmez. Java 8 source/target korunur. Yeni static mutable state eklenmez.

## Performans Etkisi

Sonuç: **hot path'te ölçülebilir ek maliyet var, kabul kararı deploy sonrası ölçümle verilecek.**

- Frekans: `PROGRESSIVE_RELEASED` originate başına 1 kez. Ölçülen üretim frekansı: tek agent için 85 dakikada 84 originate; 5 agent'lık kampanyada kabaca 5 originate/dk.
- **Ekler:** originate fazında aday havuzu `getIdleUsersInGroup` (`ConcurrentHashMap.get`, O(1)) yerine `getAvailableUsersQueueInGroup` olur. Bu, `getAvailableUserToCallsCount` üzerinden `CallUpToDateKeeper.getCallsByUserGroup(group)` çağırır; o da `calls` (`ConcurrentLinkedQueue<Call>`) koleksiyonunun **tamamını** tarar ve ardından `LinkedList.sort` yapar. Maliyet O(aktif çağrı sayısı) + O(n log n).
- **Kaldırır:** originate başına bir `PriorityBlockingQueue.remove` (O(n)) ve ona bağlı log satırı.
- GAMMA bu yolu zaten kullandığı için yeni bir risk sınıfı değildir; fakat GAMMA'nın originate frekansı bu kampanyalarınkinden farklıdır. Doğrulamada `ACD processing too slow` (`ACDEngine.java:398-400`, eşik 100 ms) uyarısının sayısı deploy öncesi/sonrası kıyaslanacak.
- İkinci adım (grup bazlı ≤500 ms TTL'li snapshot) bu pakete **dâhil değildir**; ölçüm gerektirirse ayrı görev olarak açılır.

## TDD Stratejisi

Önce başarısız/karakterize eden test, sonra en küçük üretim kodu değişikliği, sonra güvenli refactor. Gerçek AMI/Redis/DB bağlantısı kurulmaz; Mockito + PowerMock `Whitebox` ile mevcut `tests/acd` deseni kullanılır.

| Test sınıfı | Doğruladığı davranış |
|---|---|
| `ProgressiveReleasedOriginateReservationTest` | Originate fazında `removeFromIdleUsers` çağrılmaz, agent havuzda kalır **ve** `processACD` yine non-null user atar (R1'i yakalar); `buildUserFindingContext(call, false)` ile çağrılır. Answer fazında (`answerDate != null`) çıkarma yapılır. Owner-bound callback'te çıkarma yapılır (R4). |
| `ProgressiveReservedReservationRegressionTest` | `PROGRESSIVE_RESERVED` originate fazında `removeFromIdleUsers` çağırır, `buildUserFindingContext(call, true)` alır ve `UserPauseMessage(true)` finisher'ı korunur. Aynı korumalar `PREVIEW_FREE`, `PREVIEW_STRAIGHT` ve `PREDICTIVE`-ALPHA için de doğrulanır (R2'yi yakalar). |
| `AcdAnswerPhaseReselectionScopeTest` | `PROGRESSIVE_RELEASED`: `lastCallEndDate == null` iken de reselect çalışır; avail yoksa user `null` kalır. `PROGRESSIVE_RESERVED`: eski davranış (initial user'a dönüş) korunur. GAMMA: bit-eşit. |

## Uygulama Kontrol Listesi (Execution Plan)

Sıra bağlayıcıdır. Görev 2 ve 3 aynı commit'te gider (R1).

### Görev 0 — Hazırlık

- [x] `Açık Sorular` cevaplandı ve plana işlendi
- [x] Geliştirici planı onayladı
- [x] `fix/GLB90060295-progressive-released-idle-rezervasyon` branch'i `develop` üzerinden açıldı

### Görev 1 — Tip yardımcısı

- [x] `Campaign.java`: `@Transient public boolean isProgressiveReleasedCampaign()` eklendi
- [x] Yeni `com/powerdialer/engine/acd/OriginatePhase.java` — originate-fazı kararının tek kaynağı: (GAMMA **veya** `PROGRESSIVE_RELEASED`) **ve** `!call.isOwnerBoundCallback()` **ve** `originationDate == null` **ve** `startDate == null` **ve** `answerDate == null`

### Görev 2 — Originate fazında havuz seçimi (R1: Görev 3 ile aynı commit)

- [x] Test: `ProgressiveReservedReservationRegressionTest` (karakterizasyon; kırmızı turda da 6/6 yeşil kaldı)
- [x] Test: `ProgressiveReleasedOriginateReservationTest` (kırmızı turda 2 hata verdi — bkz. Doğrulama)
- [x] `UserFindingContextBuilder`: `PROGRESSIVE_RELEASED` `PREDICTIVE` case'inden ayrıldı; `OriginatePhase` doğruysa `shouldUseIdleUsers=false`. `PREDICTIVE` ve `PROGRESSIVE_RESERVED` case'leri değişmedi

### Görev 3 — Originate fazında rezervasyonu atla

- [x] `DefaultFindMatchingUserStrategy`: `isGammaOriginatePhase()` kaldırıldı, koşul `OriginatePhase` helper'ına devredildi; "SORUN 1 / 1A" açıklaması korundu ve `PROGRESSIVE_RELEASED` gerekçesi eklendi
- [x] Görev 2 + 3 testleri yeşil

### Görev 4 — Answer fazı guard'ları

- [x] Test: `AcdAnswerPhaseReselectionScopeTest` (kırmızı turda 1 hata verdi)
- [x] `ACDEngine.trySearchLessOccupiedUser`: `symbolicPreAssignment` değişkeni eklendi (GAMMA **veya** `PROGRESSIVE_RELEASED`); `refreshSelection`, `initialUnavailable` (eski `gammaInitialUnavailable`) ve NOAVAIL dalı bu değişkene bağlandı
- [x] Log etiketleri `[GAMMA-SWAP]` → `[ACD-SWAP]`, `[GAMMA-NOAVAIL]` → `[ACD-NOAVAIL]` (R6)
- [x] `PROGRESSIVE_RESERVED` ve diğer tipler için eski davranışın korunduğu regresyon testleri yeşil

### Görev 5 — Sessiz yolun loglanması

- [x] `CallOriginator.processCall`: agent bulunamayınca kampanya başına 10 sn throttle'lı `[ACD-NO-AGENT]` WARN. Throttle state'i `ConcurrentMap` instance alanı; yeni static mutable state yok. `LoggerDecorator` argümansız formu kullanıldı (argümanlı form senkron ActionLog DB insert yapıyor)

### Görev 4b — Over-dial tavanının korunması (uygulama sırasında eklendi, bkz. R3)

- [x] `ACDEngine.resolveUserFindingContext`: `PROGRESSIVE_RELEASED` + originate fazı için ayrı dal; `canUseIdleUsers` kısayolu atlanır, tavan `isOriginateBudgetAvailable` ile uygulanır
- [x] Yeni `isOriginateBudgetAvailable`: `notBridgedCalls < floor(availableUsers × dialingBoost)`; `boost < 1` semantiği (agent boşta bırak) ve `boost = 1` (agent başına 1) korunur
- [x] Test: `ProgressiveReleasedOriginateReservationTest.originateBudgetCeilingStillAppliesWhenReservationIsSkipped` / `originateProceedsWhenBudgetIsAvailable`

### Görev 4c — Null-safety (uygulama sırasında ortaya çıktı)

- [x] `Call.getUserIdAppointedToCallback` ve `Call.isAppointerTimedOut`: `Boolean` unboxing NPE'leri `Boolean.TRUE.equals` ile kapatıldı. `OriginatePhase` artık her `PROGRESSIVE_RELEASED` çağrısında `isOwnerBoundCallback()` çağırdığı için bu latent NPE ACD karar yoluna taşınıyordu (`CallbackAssignmentRequiresAvailableAgentTest` bunu yakaladı). Aynı ifadedeki `getAllowCallback()` zaten bu idiomu kullanıyordu

### Görev 5b — PREDICTIVE-ALPHA'ya genişletme (10.08.2026 geliştirici isteği)

- [x] `Campaign.isAlphaPredictiveCampaign()`
- [x] `OriginatePhase`: kapsam GAMMA + `PROGRESSIVE_RELEASED` + ALPHA; tip kümesi `isSymbolicPreAssignmentCampaign(Campaign)` olarak ayrıldı (answer fazı guard'ları faz kontrolü olmadan bunu kullanır)
- [x] `UserFindingContextBuilder` `PREDICTIVE` case: `shouldUseIdleUsers && !OriginatePhase.isWithoutReservation(call)`. BETA/GAMMA'ya `ACDEngine` zaten `false` gönderdiği için onlarda etkisiz
- [x] `ACDEngine.resolveUserFindingContext`: originate dalı tip-özel olmaktan çıkarılıp `OriginatePhase.isWithoutReservation(call)` koşuluna bağlandı (BETA/GAMMA üstteki dallarda dönüyor)
- [x] `isOriginateBudgetAvailable` boost kaynağı yeni `resolveEffectiveBoost` ile `getDialingBoost` contract'ına hizalandı: PREDICTIVE → `DynamicBoostManager`, diğer tipler → `campaign.dialingBoost` (null-safe)
- [x] `trySearchLessOccupiedUser`: `symbolicPreAssignment` artık `OriginatePhase.isSymbolicPreAssignmentCampaign`
- [x] Testler: yeni `PredictiveAlphaOriginateReservationTest` (5); `OriginatePhaseScopeTest` ALPHA kapsam-içi + `isSymbolicPreAssignmentCampaign` kümesi; `AcdAnswerPhaseReselectionScopeTest`e ALPHA (null kalır) ve BETA (değişmez) vakaları; `ProgressiveReservedReservationRegressionTest`teki ALPHA vakası BETA'ya çevrildi

### Görev 6 — Doğrulama ve kapanış

- [x] Hedefli testler yeşil (bkz. Doğrulama Sonucu)
- [x] Mevcut karakterizasyon testlerinin güncellenmesi: `ACDEngineTest`in iki testi varsayılan `PROGRESSIVE_RELEASED` tipiyle eski davranışı kilitliyordu; kapsam kaybı olmasın diye `PROGRESSIVE_RESERVED`'e taşındı ve yeni testlere referans verildi
- [ ] `gbpd-commit-discipline` ile diff/secret/ilgisiz değişiklik kontrolü
- [x] History kaydı `docs/history/10.08.2026.md` dosyasına append edilir

## Hedefli Doğrulama Komutları

Komutlardan önce `.codex/TOOL-PATHS.local.properties` okunur; Maven offline çalıştırılır.

```
mvn -o -Dtest=ProgressiveReleasedOriginateReservationTest test
mvn -o -Dtest=ProgressiveReservedReservationRegressionTest test
mvn -o -Dtest=AcdAnswerPhaseReselectionScopeTest test
mvn -o -Dtest=ProgressiveReleased*Test,ProgressiveReserved*Test,Acd*Test,ACDEngine*Test,DefaultFindMatchingUserStrategyTest,CallbackAssignmentRequiresAvailableAgentTest test
```

Frontend değişikliği yoktur; `npm.cmd run test:angular` bu iş kapsamında gerekmez.

### Üretim doğrulaması (deploy sonrası)

1. Aynı Elastic sorgusuyla idle havuz doluluğu yeniden ölçülür. Beklenen: "havuz boş" oranı %75'ten belirgin düşer, zaman-ağırlıklı ortalama 0,30'dan agent sayısına yaklaşır.
2. `Idle user removed` satırının originate anında **kaybolması**, answer anında görünmesi (GAMMA'daki 1A doğrulamasının aynısı).
3. `/api/health/agentSize/campaign/{id}`: `idleUsersCount` ile `availableUsersCount` arasındaki farkın kapanması.
4. Abandon oranı ve `ACD processing too slow` sayısı deploy öncesi/sonrası kıyaslanır (R3, `Performans Etkisi`).

## Kapanış Kriteri

- Görev 1-5 tamamlandı, tüm hedefli testler yeşil.
- `PROGRESSIVE_RESERVED`, `PREVIEW_FREE`, `PREVIEW_STRAIGHT`, `PREDICTIVE` (ALPHA/BETA/GAMMA) ve `OUTBOUND_IVR` için regresyon testleri yeşil.
- History kaydı eklendi.
- Üretim doğrulaması maddeleri deploy sonrası ölçülüp raporlandı.

## Doğrulama Sonucu (10.08.2026)

**Kırmızı tur** (yalnız `Campaign.isProgressiveReleasedCampaign()` + `OriginatePhase` eklenmiş, wiring yok):

- `OriginatePhaseScopeTest` 7/7 yeşil (saf kapsam mantığı)
- `ProgressiveReservedReservationRegressionTest` 6/6 yeşil (karakterizasyon; mevcut davranış kilitlendi)
- `ProgressiveReleasedOriginateReservationTest` 2 hata: agent originate anında havuzdan çıkarılıyor, kampanya idle sayısı 1 → 0
- `AcdAnswerPhaseReselectionScopeTest` 1 hata: meşgul initial user'a kör dönüş

**Yeşil tur** (PROGRESSIVE_RELEASED kapsamı): hedefli kapsamda 528 test, 9 hata — hepsi pre-existing.

**Yeşil tur** (ALPHA genişlemesi dâhil, nihai): hedefli kapsamda **540 test, 9 hata, 0 error**. Dokuzunun tamamı **pre-existing**; değişiklik öncesi `develop` durumuyla (kod stash'lenip yeni dosyalar geçici olarak çıkarılarak) birebir aynı sonucu verdiği doğrulandı:

- `com.powerdialer.engine.acd.DefaultFindMatchingUserStrategyTest` — 5 hata. Baseline'da da 5. Sebep bu iş kapsamında araştırılmadı; test kurulumunda agent'lara `Status` verilmediği için `addToIdleUsers` idle-gate'ten dönüyor olması muhtemel (ayrı iş).
- `com.powerdialer.service.CampaignResultJobServiceTest` — 4 hata (SQL tarih karşılaştırması). Baseline'da da 4. Bu değişiklikle ilgisiz.

Bu iş kapsamında kırılan ve düzeltilen testler:

- `ACDEngineTest.testProcessACD` → `PROGRESSIVE_RELEASED` originate fazında beklenen argüman `true` yerine `false`; idle-only havuzun korunduğu tip için `testProcessACDForReservedType` eklendi.
- `ACDEngineTest.trySearchLessOccupiedUser_nonGamma_*` (2 test) → `PROGRESSIVE_RESERVED`'e taşındı, adları `reservedType*` oldu, yeni testlere referans verildi.
- `CallbackAssignmentRequiresAvailableAgentTest.sameUserCallbackConvertedToPoolRoutesToCampaignWideWhenUserIdNull` → beklenen argüman `false`. Testin asıl kilitlediği davranış (user_id NULL callback campaign-wide'a düşer) değişmedi.

**Blocker:** tam test suite (`mvn -o test`) 166 test sınıfında (939 test) takıldı ve 15+ dakika ilerlemedi; ortam bağımlı asılı bir test var. Durduruldu. Bu yüzden tam suite sonucu **raporlanamadı**; doğrulama yukarıdaki hedefli kapsamla yapıldı. Takılan sınıfın tespiti ayrı iş.

**Kalan:** `gbpd-commit-discipline` kontrolü ve commit; deploy sonrası üretim doğrulaması.

## Cevaplanan Sorular (10.08.2026)

1. **Kampanya tipi teyidi** → Kampanya `PROGRESSIVE_RELEASED`.
2. **Flag mekanizması** → Kodda feature flag olmayacak; davranış kampanya bazında, tıpkı GAMMA'daki gibi kampanyanın kendi tipinden türeyecek. Yeni `Campaign` alanı/DB kolonu/property eklenmez.
3. **İlk hangi sette açılacağı** → Kampanya yönetiminin kararı; bu iş kapsamında değil. Değişiklikler uygulanır.
4. **Kapsam dışı kalemler** → Bu iş kapandıktan sonra ayrı plan olarak açılacak.
5. **Performans ikinci adımı** → Ölçüm sonucuna bırakıldı; plana alınmadı.
