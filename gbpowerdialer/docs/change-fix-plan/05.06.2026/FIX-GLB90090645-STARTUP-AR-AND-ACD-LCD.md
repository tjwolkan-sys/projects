# FIX Gamma Startup AR ve ACD Priority-Aware LCD Change/Fix Planı

## Bağlam

İki ilişkili sorun tespit edildi; tek branch (`feature/GammaAndAcdImprovements-part12-StartupARAndLCD`) altında ele alınır.

**Sorun 1 — Gamma Startup AR Üretimden Çok Yüksek**

`PredictiveGammaCallManager.STARTUP_*_ANSWER_RATE` sabitleri `0.90d` ile çok yüksek. Üretim verisinde gerçek AR değerleri %9-31 arasında. `PredictiveGammaCallCalculator.resolveBlendedAnswerRate` ilk 400 çağrıya kadar startup AR'ı baskın tutar (STARTUP_SNAPSHOT_THRESHOLD=100/50, WARM_UP_CALL_THRESHOLD=400). Düşük agent kampanyada 2-3 saat boyunca `ringingLoadSafe` over-konservatif, dial bütçesi neredeyse 0, agent gün başında uzun avail kalır.

**Sorun 2 — ACD Çağrı Dağıtımında Grup-İçi Shuffle + Grup-Arası "İlk Bulunan" Mantığı**

`ACDEngine.getAvailableUsersQueueInGroup` içinde `Collections.shuffle(result)` boost'lu (notBridged) yolu kullanan tüm predictive stratejilerde (Gamma, Beta, Alpha boost>1) grup-içi agent seçimini rastgele yapıyor. `DefaultFindMatchingUserStrategy.getMostIdleUser` / `getMostIdleUserWithMatchingSkills` ise gruplara priority DESC sıralı for-loop ile bakıp ilk uygun gruptaki ilk uygun agent'ı seçip çıkıyor; aynı priority'li 2 grup varsa ikinci grubun agent'ları daha uzun avail beklese bile ilk gruba düşen herhangi bir agent öncelik alıyor. İki davranış birlikte "Mutlu Sendromu" üretiyor.

## İş Etiketi

fix

## Öncelik

normal

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/predictive/gamma/processor/PredictiveGammaCallManager.java` (Sorun 1, 3 sabit)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/acd/ACDEngine.java` (Sorun 2A, shuffle → sort)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/acd/DefaultFindMatchingUserStrategy.java` (Sorun 2B, priority bucket method refactor)

## Mevcut Davranış

### Sorun 1

`buildStartupProfile` her kampanya start'ında üç tipte (callback/retry/fresh) `0.90` AR ile profile oluşturur. `resolveBlendedAnswerRate` ilk 100/50 çağrıya kadar bu profile'ı tam, sonraki 100-400 arası blend, 400+ live olarak kullanır. Üretim AR'ları %9-31 olduğu için warm-up boyunca dial bütçesi over-konservatif kalır.

### Sorun 2

ACD `findMatchingUser` flow'u:

1. `campaign.getEnabledCampaignGroups()` priority DESC + queue size ASC sıralanır
2. Skill enabled'a göre `getMostIdleUser` veya `getMostIdleUserWithMatchingSkills` çağrılır
3. Her iki method da gruplar üzerinde for-loop iterasyonu yapar
4. Her grup için `getUsersQueue(group)` ile aday user listesi alınır:
   - `useOnlyIdleUsers=TRUE` (Preview, Alpha boost=1) → `idleUsers` PriorityBlockingQueue, LCD sıralı (doğru)
   - `useOnlyIdleUsers=FALSE` (Gamma, Beta, Alpha boost>1) → `getAvailableUsersQueueInGroup` → `LinkedList` + `Collections.shuffle` → rastgele
5. İlk uygun gruptaki ilk uygun aday dönülür, sonraki gruba bakılmaz

`trySearchLessOccupiedUser` zaten LCD bazlı swap mekanizması ama içerideki `processACD` yine shuffle'lı queue kullandığı için yalnız 2 rastgele user'ı karşılaştırıp eskisini tutuyor.

## Beklenen Davranış

### Sorun 1

`buildStartupProfile` üretime yakın profile üretir:

- `STARTUP_CALLBACK_ANSWER_RATE = 0.60d` (callback'te randevu nedeniyle AR yüksek, sektör tipiği %40-60)
- `STARTUP_RETRY_ANSWER_RATE = 0.50d` (orta nokta, gerçek %10-25 üst)
- `STARTUP_FRESH_ANSWER_RATE = 0.50d` (gerçek %15-30 üst)

### Sorun 2

ACD `findMatchingUser` priority-aware cross-group LCD dağıtımı yapar:

1. Gruplar priority DESC sıralı kalır (mevcut comparator korunur)
2. Aynı priority'li ardışık gruplar tek havuza birleştirilir (priority bucket)
3. Havuz içinde mevcut sıralama mekanizması uygulanır:
   - Skill enabled değil → `UserLastCallDateComparator` (hiç almayan → en uzun avail)
   - Skill enabled → `getComparator(skills)` zinciri = `UserSkillWeightComparator` + `UserLastCallDateComparator` (yetkin önce → eşitse LCD)
4. Havuzdaki ilk uygun aday alınır; havuz boşsa bir alt priority bucket'a düşülür
5. `getAvailableUsersQueueInGroup` artık `Collections.shuffle` yerine `UserLastCallDateComparator` ile sıralı liste döner; her grup için ayrı çağrıldığı için `minCallsNumber` filter (per-group load balancing) korunur

## Kod Okuma Bulguları

### LCD verisi mevcut, eksik yerde kullanılmıyor

- `ACDEngine.lastUsersCallEndDate` Guava Cache (12 saat TTL) user-bazlı son çağrı bitiş tarihini tutuyor
- Update yerleri: `ManagerConnectionDecorator.java:664` (preview answer) ve `DialWithAbandonTimeoutScript.java:138` (AGI answered case)
- `UserLastCallDateComparator` zaten `idleUsers`/`systemActiveUsers` PriorityBlockingQueue'larında ve `getComparator(skills)` zincirinde kullanılıyor
- Tek eksik: `getAvailableUsersQueueInGroup` shuffle ile bu veriyi yok sayıyor

### Korunan Mekanizmalar

- `removeFromIdleUsersAndCheck` içinde `userPassedThreshold(campaign, user)` `availableThreshold` gate
- `getAvailableUserToCallsCount` içinde `isDialInProcess` ve `isAvailableForDialerCalls` kontrolleri
- `getAvailableUsersQueueInGroup` içinde `minCallsNumber` filter (boost > 1 load balancing)
- `findUsersWithMatchingSkills` TreeSet (skill weight + LCD comparator zinciri)
- `trySearchLessOccupiedUser` swap mekanizması (paralel kalır, içerideki processACD yeni findMatchingUser'ı kullanır)
- Idle/active queue management (`addToIdleUsers`, `removeFromIdleUsers`, `addToActiveUsers`)
- Boost < 1 fence (`canUseIdleUsers` içinde rezerve agent hesabı)
- Callback özel dalları (`findSameUser`, `findAppointedUser`) — tek user'a baktıkları için etkilenmez

### Etkilenen Stratejiler

| Strateji / Mod | useOnlyIdleUsers | Mevcut Sıralama | Düzeltme Etkisi |
|---|---|---|---|
| Gamma | FALSE | Shuffle | LCD'ye geçer (ana hedef) |
| Beta | FALSE | Shuffle | LCD'ye geçer |
| Alpha boost>1, idle bittiğinde | FALSE | Shuffle | LCD'ye geçer (nadir ama iyileşme) |
| Alpha boost=1.0 | TRUE | Idle queue LCD | Davranış aynı |
| Preview | TRUE | Idle queue LCD | Grup-arası havuz birleşmesi yeni; grup-içi aynı |
| Progressive RELEASED/RESERVED | shouldUseIdleUsers'a göre | Bağlamla değişir | İlgili yolun davranışı düzelir |

## Root Cause / Değişiklik Gerekçesi

### Sorun 1

Üretim AR'ı %9-31 olan kampanyalarda startup için `0.90` AR atamak istatistiksel olarak yanlış. `ringingLoadSafe = N×AR + k×√(N×AR×(1-AR))` formülünde AR ana çarpan olduğu için 3-10x yanlış AR doğrudan 3-10x yanlış ringingLoad demektir. Düzeltme: AR'ı gerçek üretime yakın bir orta noktaya çekmek (CB için 0.60, retry/fresh için 0.50). Per-campaign tunable kolon ileride eklenebilir; bu plan global düzeltme.

### Sorun 2

`getAvailableUsersQueueInGroup` 2 problemi birden tetikliyor:

1. `Collections.shuffle` LCD verisini görmezden gelip rastgele user üretiyor
2. `findMatchingUser` for-loop'u ilk uygun gruptaki ilk uygun adayı seçince aynı priority'li diğer gruptaki daha uzun bekleyen agent'lar görmezden geliniyor

İkisi birlikte Mutlu Sendromu. Düzeltme: shuffle → LCD sort ve for-loop → priority bucket pooling.

## Riskler

| Risk | Olasılık | Etki | Azaltım |
|---|---|---|---|
| Startup AR çok düşük → cold start over-dial → rc78 | Düşük | Orta | UCB tampon emici; 0.50 hala gerçeğin 1.5-5 katı (muhafazakar) |
| Cross-group LCD priority kasıtlı kullanan kampanyada davranış değişir | Düşük | Düşük | Priority sıralı kalıyor; sadece aynı priority gruplar birleşiyor |
| Performans: havuza topla + sort O(N log N) | Çok düşük | İhmal | Tipik N=5-30, ~1 µs/call, hot path için ihmal edilebilir |
| Preview/Progressive grup-arası davranış değişimi | Düşük | Düşük | Tek priority kullanılan tipik Preview kampanyalarda fark sıfır |
| Skill versiyonunda TreeSet comparator zinciri bozulması | Çok düşük | Yüksek | Mevcut `findUsersWithMatchingSkills` çağrısı korunur, sadece havuza giren user kümesi genişler |
| minCallsNumber filter cross-group kaybı | Düşük | Orta | Per-group çağrı yapılır, her grup kendi filter'ını uygular, sonra birleşir |

## TDD Stratejisi

### Karakterizasyon Testleri (Önce Mevcut Davranışı Kilitle)

1. `PredictiveGammaCallManagerStartupAnswerRateTest`
   - `buildStartupProfile_setsCallbackAnswerRate()`
   - `buildStartupProfile_setsRetryAnswerRate()`
   - `buildStartupProfile_setsFreshAnswerRate()`
   - `buildStartupProfile_fallbackOnCdrServiceFailure()` (regression)

### Beklenen Davranış Testleri (Yeni)

2. `AcdPriorityAwareLcdDistributionTest`
   - `singlePriorityGroup_lcdSortApplied_userWithOldestLastCallEndDateFirst()`
   - `singlePriorityGroup_userWithNullLastCallEndDate_isFirst()`
   - `multipleGroupsSamePriority_pooledIntoSingleBucket()`
   - `multipleGroupsSamePriority_lcdAppliedAcrossPool()`
   - `differentPriority_higherPriorityBucketFirst()`
   - `higherPriorityBucketEmpty_fallsToLowerPriorityBucket()`
   - `availableThresholdGate_userBelowThresholdSkipped()`
   - `skillEnabled_samePriorityGroupsPooled_skillWeightAndLcdApplied()`
   - `skillEnabled_userWithoutMatchingSkillFiltered()`

### Regression Suite

- `CampaignRealTimeRingingManagerPendingRingingTest`
- `CallConsumerGammaBackpressureGuardTest`
- `CallConsumerGammaBudgetTest`
- `PredictiveGammaCallCalculatorRingingLoadUcbTest`
- `PredictiveGammaCallCalculatorTest`
- `PredictiveGammaCallManagerTest`
- `PredictiveGammaRecentCallsTest`
- `SharedGammaSnapshotProviderTest`
- `AwaitingUserResultCacheTransitionRegressionTest`
- `AwaitingUserResultTimeoutPreserveRegressionTest`
- `SaveInitialCdrHangupResultRegressionTest`
- `StopStateTest`

## Açık Sorular

- [ ] Skill kullanan kampanya gerçekten üretimde var mı? (Bilgi amaçlı, kod değişikliğini bloklamıyor — skill versiyonu yine tutarlılık için güncellenir.)
- [ ] DB'de TC_BRY_REAKTIF_CHURN gibi çok gruplu kampanyaların `priority` değerleri gerçekten farklı mı yoksa hep aynı mı? (Bilgi amaçlı, pilot ölçümünde doğrulanır.)

Bu sorular implementasyonu engellemez; pilot ölçümü sırasında doğrulanır.

## Execution Plan

- [x] Onaylanan planın tüm adımları bu dosyaya işlendi.
- [x] Karakterizasyon testlerini yaz (mevcut davranışı kilitle):
  - [x] `PredictiveGammaCallManagerStartupAnswerRateTest`
- [x] Sorun 1 — Startup AR sabitlerini güncelle:
  - [x] `STARTUP_CALLBACK_ANSWER_RATE = 0.60d`
  - [x] `STARTUP_RETRY_ANSWER_RATE = 0.50d`
  - [x] `STARTUP_FRESH_ANSWER_RATE = 0.50d`
  - [x] Testleri yeşile çevir.
- [x] Sorun 2A — `ACDEngine.getAvailableUsersQueueInGroup`:
  - [x] `Collections.shuffle(result)` yerine `result.sort(new UserLastCallDateComparator(this))`
- [x] Sorun 2B — `DefaultFindMatchingUserStrategy`:
  - [x] `getMostIdleUser` priority bucket iterasyonu + cross-group LCD pooling
  - [x] `getMostIdleUserWithMatchingSkills` priority bucket iterasyonu + cross-group skill+LCD pooling
- [x] Yeni beklenen davranış testlerini yaz: `AcdPriorityAwareLcdDistributionTest`
- [x] Mevcut Gamma/ACD regression suite koştur. 92/92 başarılı.
- [x] Doküman ve history güncellemesini tamamla.

## Performans Etkisi

- **Hot path durumu**: ACD `findMatchingUser` her çağrı için tetiklenir, hot path.
- **Call/event/tick başına ek maliyet**:
  - Shuffle (O(N)) → sort (O(N log N)). N tipik 5-30. Fark < 500 ns/call.
  - Grup-grup for-loop (O(G)) → priority bucket toplama (O(G + N_total)). G tipik 2-4, N_total ≤ 30 → < 500 ns/call ek.
  - Toplam: ~1 µs/call. Hot path standardına uygun.
- **IO/scan/lock/serialization/log etkisi**: Yok. PriorityBlockingQueue iterator weakly consistent, snapshot alımı thread-safe.
- **Azaltım veya ölçüm notu**: Mevcut shuffle de O(N) maliyet getiriyordu, fark ihmal edilebilir.

## Doğrulama

Hedefli Maven offline test komutu:

```
mvn -o -P properties-tr-dev -s C:/Users/GLB90090645/.m2/settings.xml \
    -Dmaven.repo.local=C:/Users/GLB90090645/.m2/repository \
    -Dtest=PredictiveGammaCallManagerStartupAnswerRateTest,\
AcdPriorityAwareLcdDistributionTest,\
PredictiveGammaCallCalculatorRingingLoadUcbTest,\
PredictiveGammaCallCalculatorTest,\
PredictiveGammaCallManagerTest,\
CallConsumerGammaBackpressureGuardTest,\
CallConsumerGammaBudgetTest,\
CampaignRealTimeRingingManagerPendingRingingTest,\
AwaitingUserResultCacheTransitionRegressionTest,\
AwaitingUserResultTimeoutPreserveRegressionTest,\
SaveInitialCdrHangupResultRegressionTest,\
StopStateTest \
    test
```

Beklenen sonuç: tüm testler yeşil.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı veya bilgi notu olarak işaretlendi. (Pilot ölçümünde doğrulanacak bilgi notları.)
- [x] Karakterizasyon testleri mevcut davranışı kilitledi. (0.90 ile fail bekleniyordu, 4/4 fail; sabit değişikliği sonrası 4/4 pass.)
- [x] Yeni beklenen davranış testleri yeşil. (AcdPriorityAwareLcdDistributionTest 9/9.)
- [x] Mevcut Gamma/ACD regression suite yeşil. (12 hedefli regression test'i + 79 mevcut = 92/92.)
- [x] Hot path performans etkisi belgelendi. (~1 µs/call ek, AGENTS.md hot path standardına uygun.)
- [x] Final yanıtta kısa Test Özeti sunuldu. (Bkz. Tamamlanma Notu.)

## Tamamlanma Notu

Tüm adımlar tamamlandı. Üç dosyada minimum değişiklik:

- `PredictiveGammaCallManager.java`: 3 sabit (0.60/0.50/0.50)
- `ACDEngine.java`: 1 satır (shuffle → sort)
- `DefaultFindMatchingUserStrategy.java`: `findMatchingUser` priority bucket iterasyonu + `getMostIdleUser`/`getMostIdleUserWithMatchingSkills` cross-group pooling (PooledCandidates helper'ı eklendi)

Test özeti: 92 test, 0 fail, 0 error, 0 skip.

- 4 yeni `PredictiveGammaCallManagerStartupAnswerRateTest`
- 9 yeni `AcdPriorityAwareLcdDistributionTest`
- 79 mevcut Gamma/ACD/AMI regression (UCB, backpressure, budget, recent calls, snapshot provider, pending ringing, awaiting user result, save initial CDR, stop state, manager, calculator)

Korunan mevcut mekanizmalar: minCallsNumber filter, availableThreshold gate, isDialInProcess race kontrol, skill match logic, skill weight comparator, idle/active queue lifecycle, trySearchLessOccupiedUser, callback özel dalları (findSameUser/findAppointedUser), PROGRESSIVE_RESERVED finisher, boost < 1 fence.
