# FIX ACD Avail Time Comparator Change/Fix Planı

## Bağlam

`feature/GammaAndAcdImprovements-part12-StartupARAndLCD` branch'inde develop'a merge edilmiş cross-group LCD dağıtım düzeltmesi (commit `574f2127`) sonrası test ekipleri "agent break veya wrapup'tan az önce dönmüş olmasına rağmen yanlış öne çıkıyor" gözlemini raporladı.

Kök neden: cross-group sort `UserLastCallDateComparator` kullanıyor, comparator `user.lastCallEndDate` (son çağrı bitiş tarihi) üzerinden sıralıyor. Çağrı bittikten sonra wrapup (ACW) ve break sürelerinin sayılmaması "uzun avail bekleyen" sinyalini yanıltıcı yapıyor — break/wrapup'tan yeni dönen agent, daha az gerçek avail süresine sahip olmasına rağmen `lastCallEndDate` çok eski olduğu için sıralamanın başında geliyor.

Örnek: Hasan 13:30'da çağrıdan çıktı (`lastCallEndDate=13:30`), 13:32-14:08 break aldı, 14:08'de avail oldu. Mutlu 14:00'da çağrıdan çıktı, 2 dakika wrapup, 14:02'de avail oldu. Saat 14:10. Gerçek avail süresi: Mutlu 8 dk, Hasan 2 dk. LCD comparator: Hasan eski lastCallEndDate → Hasan önce — **yanlış**.

## İş Etiketi

fix

## Öncelik

hotfix

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/acd/UserAvailTimeComparator.java` (yeni)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/acd/ACDEngine.java` (3 yer: `getAvailableUsersQueueInGroup` sort, `shouldSwitchUser` swap kararı, `getComparator(skills)` skill chain tie-breaker)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/acd/DefaultFindMatchingUserStrategy.java` (1 yer: `getMostIdleUser` cross-group sort)

## Mevcut Davranış

`UserLastCallDateComparator` `ACDEngine.lastUsersCallEndDate` Guava cache'inden `user.lastCallEndDate` lookup yapıyor; çağrı bitince güncellenen tarih sıralamada referans. Cross-group havuz ve `getAvailableUsersQueueInGroup` (boost'lu predictive notBridged yolu) sort sonrası bu comparator'ı kullanıyor. `trySearchLessOccupiedUser` içinden çağrılan `shouldSwitchUser` da swap kararını aynı `lastCallEndDate` üzerinden veriyor. Wrapup ve break süreleri sayılmadığı için pratik avail süresi yanlış gösteriliyor.

## Beklenen Davranış

Cross-group sort, API endpoint döndüğü liste ve `shouldSwitchUser` kararı `user.statusSetDate` üzerinden çalışsın. `statusSetDate` user'ın mevcut status'a girdiği an; ACD zaten yalnız `AVAILABLE` agent'ları sıralamaya alır (`isAvailableForDialerCalls()` filtresi), dolayısıyla `statusSetDate` = avail oluş anı = gerçek avail başlangıcı. Wrapup/break süreleri otomatik olarak hariç tutulmuş olur.

## Kod Okuma Bulguları

### `statusSetDate` sinyali zaten sistemde güvenilir kabul ediliyor

`DefaultFindMatchingUserStrategy.userPassedThreshold` `user.calculateTimeInStatus() >= campaign.getAvailableThreshold()` kontrolü yapar; `calculateTimeInStatus = (now − statusSetDate)/1000`. Yani mevcut threshold gate aynı veriyi kullanır — sıralama için de kullanmak tutarlı.

### `statusSetDate` write/read'leri

- Yazma yerleri: `UserSubscriber` (Redis status değişimi), `UserDeserializer` (JSON deserialize), User entity yaratımı. Status her değişiminde güncellenir (wrapup → break → available geçişleri dahil).
- Bellekte hazır user field; ek I/O yok, ~1 ns erişim.

### `UserLastCallDateComparator` aynı kalır

Cache (`lastUsersCallEndDate`) ve comparator dokunulmaz. Paralel kullanıcıları:
- `idleUsers` PriorityBlockingQueue internal sort (Preview/Progressive idle path)
- `systemActiveUsers` PriorityBlockingQueue internal sort
- Skill TreeSet zinciri (`getComparator(skills)` skill weight + LCD tie-breaker)

Bu yerlerde mevcut davranış korunur; cache dolma akışı (`updateUserLastCallEndDate`) etkilenmez.

## Root Cause / Değişiklik Gerekçesi

`lastCallEndDate` tek başına "avail süresi" sinyali değildir; çağrı bittikten sonraki non-available zamanları (wrapup, break, çevrimdışı) saymaz. Gerçek avail süresi `now − statusSetDate` (status AVAILABLE iken). Cross-group sort'unda kullanılması gereken sinyal `statusSetDate`'tir. `lastCallEndDate` paralel mekanizmalar için (idle queue internal sort gibi) anlamlı kalır ama avail dağıtım kararında değil.

## Riskler

| Risk | Olasılık | Etki | Azaltım |
|---|---|---|---|
| `statusSetDate` null senaryosu (yeni user) | Çok düşük | NPE | Comparator null-safe; user lifecycle'da statusSetDate her zaman set ediliyor |
| `shouldSwitchUser` davranış değişimi | Düşük | Düşük | `shouldSwitchUser` private, tek caller (`trySearchLessOccupiedUser`); pratik olarak cross-group LCD sonrası swap zaten nadir tetiklenir |
| Skill TreeSet zincirinde LCD tie-breaker hala lastCallEndDate | Çok düşük | İhmal | Skill weight dominant, LCD tie-breaker'ın pratik etkisi sıfıra yakın |
| idleUsers/systemActiveUsers queue internal sort eski | Düşük | Yok | Bizim cross-group sort bu queue'lardan iterate edip yeniden sıralar; internal sort override edilir |

## TDD Stratejisi

### Mevcut Testlerin Güncellemesi

`AcdPriorityAwareLcdDistributionTest` (9 test) `statusSetDate` üzerinden çalışacak şekilde güncellenir:
- `setLastCallEnd` çağrıları kaldırılır (yeni comparator okumuyor)
- `user(id, name, secondsInStatus)` parametresi gerçek avail süresini ifade edecek şekilde set edilir
- `singlePriorityGroup_userWithNullLastCallEndDate_isFirst` → `singlePriorityGroup_freshLoginWithLongAvailTime_isFirst` olarak yeniden anlamlandırılır (statusSetDate null pratik olarak olmaz; "yeni login uzun avail" senaryosu)

### Regression Suite

- `PredictiveGammaCallManagerStartupAnswerRateTest` (4)
- `AcdPriorityAwareLcdDistributionTest` (9, güncellenmiş)
- `PredictiveGammaCallCalculatorRingingLoadUcbTest`
- `PredictiveGammaCallCalculatorTest`
- `PredictiveGammaCallManagerTest`
- `PredictiveGammaRecentCallsTest`
- `SharedGammaSnapshotProviderTest`
- `CallConsumerGammaBackpressureGuardTest`
- `CallConsumerGammaBudgetTest`
- `CampaignRealTimeRingingManagerPendingRingingTest`
- `AwaitingUserResultCacheTransitionRegressionTest`
- `AwaitingUserResultTimeoutPreserveRegressionTest`
- `SaveInitialCdrHangupResultRegressionTest`
- `StopStateTest`

Toplam 92 test bozulmamalı.

## Açık Sorular

Yok. Test ekibi gözlemi ve kod incelemesi birlikte ele alındı; karar `statusSetDate` kullanımına netleştirilmiş durumda.

## Execution Plan

- [x] `UserAvailTimeComparator` sınıfını ekle (statusSetDate üzerinden, null-safe, deterministik ID tie-break).
- [x] `ACDEngine.getAvailableUsersQueueInGroup` `UserLastCallDateComparator` → `UserAvailTimeComparator`.
- [x] `DefaultFindMatchingUserStrategy.getMostIdleUser` `UserLastCallDateComparator` → `UserAvailTimeComparator`.
- [x] `ACDEngine.shouldSwitchUser` `lastCallEndDate` → `statusSetDate` karşılaştırması.
- [x] `ACDEngine.getComparator(skills)` skill TreeSet tie-breaker `UserLastCallDateComparator` → `UserAvailTimeComparator` (skill weight DESC hâlâ dominant; sadece eşit yetkinlikte gerçek avail süresine bakar).
- [x] `AcdPriorityAwareLcdDistributionTest` testlerini `statusSetDate` üzerinden çalışacak şekilde güncelle.
- [x] Hedefli regression suite koş.
- [x] Doküman ve history güncellemesini tamamla.

## Performans Etkisi

- **Hot path durumu**: ACD `findMatchingUser` her çağrıda; sort hot path.
- **Call/event/tick başına ek maliyet**: `user.getStatusSetDate()` direkt field read (~1 ns), `lastUsersCallEndDate.getIfPresent` cache lookup'ı (~10-50 ns) yerine geçer. **Daha hızlı**, ihmal edilebilir kazanım.
- **IO/scan/lock/serialization/log etkisi**: Yok.
- **Azaltım veya ölçüm notu**: Mevcut sort big-O aynı; comparator field erişimi cache lookup'tan hızlı.

## Doğrulama

```
mvn -o -P properties-tr-dev -s C:/Users/GLB90090645/.m2/settings.xml \
    -Dmaven.repo.local=C:/Users/GLB90090645/.m2/repository \
    -Dtest=PredictiveGammaCallManagerStartupAnswerRateTest,\
AcdPriorityAwareLcdDistributionTest,\
PredictiveGammaCallCalculatorRingingLoadUcbTest,\
PredictiveGammaCallCalculatorTest,\
PredictiveGammaCallManagerTest,\
PredictiveGammaRecentCallsTest,\
SharedGammaSnapshotProviderTest,\
CallConsumerGammaBackpressureGuardTest,\
CallConsumerGammaBudgetTest,\
CampaignRealTimeRingingManagerPendingRingingTest,\
AwaitingUserResultCacheTransitionRegressionTest,\
AwaitingUserResultTimeoutPreserveRegressionTest,\
SaveInitialCdrHangupResultRegressionTest,\
StopStateTest \
    test
```

Beklenen: 92/92 başarılı.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı veya `Yok` olarak işaretlendi.
- [x] Karakterizasyon testleri statusSetDate'e göre güncellendi.
- [x] Mevcut Gamma/ACD regression suite yeşil (92/92).
- [x] Hot path performans etkisi belgelendi.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

`UserAvailTimeComparator` sınıfı eklendi (~50 satır). ACD'de dört yerde değişiklik:
1. `ACDEngine.getAvailableUsersQueueInGroup` (1 satır)
2. `DefaultFindMatchingUserStrategy.getMostIdleUser` (1 satır)
3. `ACDEngine.shouldSwitchUser` (3 satır, statusSetDate karşılaştırması)
4. `ACDEngine.getComparator(skills)` (1 satır, skill chain tie-breaker)

Mevcut `UserLastCallDateComparator` ve `lastUsersCallEndDate` cache korundu — idle/active queue internal sort'u ve `trySearchLessOccupiedUser` entry gate'i eski mantıkta devam eder. Internal sort'ların dağıtım kararına etkisi yoktur (bizim cross-group sort override eder); entry gate iş mantığı (yeni login agent swap dışı) korundu.

Test sonucu: 92/92 başarılı. Hot path ek maliyeti negatif (field read cache lookup'tan hızlı).
