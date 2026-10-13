# FIX-321479 connected_user_id NULL Finalize Ezilmesi Change/Fix Planı

- İş Etiketi: fix
- Kimlik: 321479
- Slug: connected-user-id-null-finalize-ezilmesi
- Tarih: 2026-06-17
- Öncelik: hotfix (kullanıcı onayı, 2026-06-17)
- Plan Durumu: Uygulandı ve hedefli regression yeşil (2026-06-17; 87 test, 0 hata)

## Bağlam

Üretimde başarıyla agent'a bağlanıp görüşülmüş (`answeredByAgent=true`, handling start dolu, TRC=10) bir PREDICTIVE çağrıda `pd_cdr.connected_user_id` NULL kaydedildi; aynı satırda `assigned_user_id=7301` korunmuş durumda. Yani aynı başarılı görüşme için ownership alanları tutarsız: assigned dolu, connected boş.

İncelenen vaka (interactionId `dl-4e1ea9a6-...`, campaign 115, type 5 = PREDICTIVE):

| Zaman | Olay | `call.user` | `cdr.connected_user_id` |
|-------|------|-------------|--------------------------|
| 09:53:39–40 | Predictive answer → user 7308 lock=false, 7301 (Zeynep) lock=true; dial/answer/handling start | 7301 | — |
| 09:53:40.745 | `Call is answered by agent` (`answeredByAgent=true`) | 7301 | — |
| 09:53:54.954 | Hangup → `hangup-waiting-user-result`; `persistHangupSnapshotForDelayedResult` koşar, `user=7301` iken `populateCdrUserOwnership` **connected=7301 yazar** | 7301 | **7301** |
| 09:54:09.990 | Webphone `RESULT_CODE`: `id=-1`, `callbackDate` dolu, `callbackType=0` (= `ASSIGN_TO_USER_GROUP`) | 7301 | 7301 |
| 09:54:09.993 | `manageCallback` → `allowOverwriteByWebphone && ASSIGN_TO_USER_GROUP` → **`call.setUser(null)`** (log: "Set null as user") | **null** | 7301 |
| hemen ardından | `processCallResult` → `persistFinalCdr` → `populateCdrUserOwnership` (`call.getUser()==null`) | null | **null** ← ezildi |

Sonuç: agent gerçekten görüşmüş olsa bile `connected_user_id` NULL yazılıyor; raporlama ve ownership tutarlılığı bozuluyor.

## İş Etiketi

fix

## Öncelik

hotfix (üretim veri bütünlüğü / ownership tutarsızlığı; kullanıcı onayı 2026-06-17)

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/util/UserPopulationEvaluator.java`
  - `populateCdrUserOwnership` connectedUser dalı (satır 485-488): "populate edilmeli ama `call.user==null`" durumunda mevcut `cdr.connectedUser` değerini koruyan guard eklenir (assignedUser dalıyla simetri).
- `gbpowerdialer/src/test/java/com/powerdialer/util/UserPopulationEvaluatorTest.java`
  - Yeni regression + karakterizasyon testleri.

## Mevcut Davranış

Tetik zinciri (`WebphoneSubscriber.processActiveCallResultCode`):

```
processActiveCallResultCode (WebphoneSubscriber:298)
  setUserResultCode + cdr.setUserResultCodeId
  if (callbackDate != null && completeDate == null):
      manageCallback (:316)
        → allowOverwriteByWebphone && callbackType==ASSIGN_TO_USER_GROUP
        → call.setUser(null)               [WebphoneSubscriber:631]   ← user temizleniyor
  if (telephonyResultCode != null && completeDate == null):
      processCallResult(call, now) (:325)
        → persistFinalCdr (MCD:1232)
            → populateCdrUserOwnership(call) (MCD:1250)  ← call.getUser()==null ile koşar
```

`UserPopulationEvaluator.populateCdrUserOwnership` içindeki asimetri:

- assignedUser dalı (satır 476-483) — koruma var:
  ```java
  if (shouldPopulateCdrAssignedUserId(call)) {
    if (user != null) { cdr.setAssignedUser(user); }
    // else: keep existing cdr.assignedUser untouched
  } else { cdr.setAssignedUser(null); }
  ```
  → `user==null` + populate=true iken mevcut değer (7301) korunur.

- connectedUser dalı (satır 485-488) — koruma YOK:
  ```java
  cdr.setConnectedUser(shouldPopulateCdrConnectedUserId(call) ? user : null);
  ```
  → Vaka'da `shouldPopulateCdrConnectedUserId(call)=true` (TRC=10, answerDate var, handlingStart var → başarılı bağlantı), ama `user==null` olduğundan `setConnectedUser(null)` çalışır ve hangup snapshot'ta yazılmış 7301 ezilir.

## Beklenen Davranış

1. Başarılı bağlantı (`shouldPopulateCdrConnectedUserId(call)=true`) varken `call.user==null` ise, daha önce persist edilmiş `cdr.connectedUser` değeri korunur (ezilmez). assignedUser dalıyla simetri sağlanır.
2. `call.user` dolu ve başarılı bağlantı → `connectedUser = call.user` (mevcut davranış, değişmez).
3. Başarısız bağlantı / explicit failure (TRC 84, handling-yok TRC 10/24) → `connectedUser` NULL'lanır (mevcut davranış, değişmez); guard yalnız populate=true durumunda korur, stale değeri korumaz.
4. Vaka'daki başarılı çağrı, `ASSIGN_TO_USER_GROUP` callback'i ile user temizlense bile `connected_user_id=7301` olarak kalır; `assigned_user_id` ile tutarlı olur.
5. Redis channel/payload, AMI event adları, result code eşlemeleri, campaign state geçişleri ve counter lifecycle değişmez.

## Kod Okuma Bulguları

- `CAMPAIGN_TYPE` enum (0-index): 0 PROGRESSIVE_RESERVED, 1 PROGRESSIVE_RELEASED, 2 PREVIEW_FREE, 3 PREVIEW_STRAIGHT, 4 OUTBOUND_IVR, **5 PREDICTIVE** → vaka PREDICTIVE.
- `CALLBACKTYPE.ASSIGN_TO_USER_GROUP(0)` → result code mesajındaki `callbackType=0` bu tipe denk; `manageCallback`'in `setUser(null)` koşulu sağlanıyor.
- Hangup `hangup-waiting-user-result` yolunda (`MCD:933-944`) `processCallResult` yerine `persistHangupSnapshotForDelayedResult` (`MCD:937`) koşar; orada `user=7301` iken `populateCdrUserOwnership` (`MCD:1099`) connected=7301 persist eder. Yani NULL bir "hiç yazılmadı" değil, "yazıldı sonra ezildi" durumudur → "mevcut değeri koru" guard'ı doğru çözümdür.
- `populateCdrUserOwnership` başındaki `groupId` mantığı (satır 464-470) zaten "user null + assigned korunur" durumunda groupId'yi korur (vaka'da `group_id=35` korunmuş); connectedUser guard'ı bu felsefeyle hizalanır.
- `populateCdrUserOwnership` çağıranları: `WebphoneSubscriber:543` (synthetic recovery — user dolu), `MCD:1099` (snapshot), `MCD:1250` (final persist), `DNCEngine:524`, `CallOriginator:474/506`. Guard tüm geç-finalize yollarında güvenlidir; synthetic recovery call'unda user her zaman set edildiği için davranış değişmez.

## Root Cause / Değişiklik Gerekçesi

Çözüm case-specific yama değil, owner boundary'de (`UserPopulationEvaluator`) eksik kalan sözleşmenin tamamlanmasıdır: "populate edilmesi gereken ownership alanı, geç finalize'da `call.user` temizlenmişse mevcut persist edilmiş değeri ezmez." Bu kural assignedUser için bilinçli olarak eklenmiş (kod yorumu: *"a late hangup processing with a cleared call.user does not erase it"*); connectedUser dalı aynı korumadan yoksun bırakılmış. Fix bu asimetriyi giderir ve `connected_user_id`'yi `call.user`'ı geç temizleyen tüm yollara karşı (yalnız bu vaka değil) korur.

## Çözüm Yaklaşımı

`populateCdrUserOwnership` connectedUser dalı assignedUser ile simetrik hâle getirilir:

```java
// Set connectedUser based on connection outcome only (independent of campaign type).
// When population is allowed, prefer the call-level user; if absent, preserve the value
// already persisted on the CDR (e.g. from the delayed hangup snapshot) so that a late
// result-code finalize with a cleared call.user (ASSIGN_TO_USER_GROUP callback reassignment)
// does not erase the connected user of a successful agent handling.
if (shouldPopulateCdrConnectedUserId(call)) {
  if (user != null) {
    cdr.setConnectedUser(user);
  }
  // else: keep existing cdr.connectedUser untouched
} else {
  cdr.setConnectedUser(null);
}
```

`else { cdr.setConnectedUser(null); }` korunur → başarısız bağlantıda stale connected user temizlenmeye devam eder.

### Kapsam dışı (nota düşüldü)

- `processCallResult:1152`'deki `returnUserToACDEngine` çağrısı, `call.user==null` olduğu için bu vakada atlanıyor (agent ACD'ye dönüşü başka yoldan gerçekleşmiş; logda 7301 sonda `Available`). Bu ayrı bir konu; bu fix kapsamında değiştirilmez, riskli olduğu için ayrı değerlendirilir.
- Geçmiş üretim verisi backfill'i (Açık Soru #2) bu planın varsayılan kapsamında değildir.

## Riskler

- **Over-preserve riski:** Guard yalnız `shouldPopulateCdrConnectedUserId(call)=true` iken korur. Başarısız/explicit-failure durumunda `else` dalı NULL'lamaya devam ettiği için stale connected user sızıntısı olmaz; bu, karakterizasyon testiyle kilitlenir.
- **Stale/yanlış connected user:** "Mevcut değeri koru" yalnızca daha önce doğru persist edilmiş değeri korur (snapshot user=call.user iken yazıldığı için doğru agent). Farklı agent'a yazılma riski yok.
- **Hot path:** Redis RESULT_CODE + AMI finalize hot path; değişiklik saf in-memory dal, yeni DB query/IO/lock/serialization yok.
- **Geri dönüş:** Değişiklik tek metodun tek dalıyla sınırlı; sorun çıkarsa dar revert yapılabilir.

## TDD Stratejisi

`UserPopulationEvaluatorTest` (mevcut `buildCall` helper'ı + `User`/CDR set ile genişletilir):

- (Kırmızı→Yeşil) `populateCdrUserOwnership_successfulButCallUserCleared_preservesExistingConnectedUser`: PREDICTIVE, TRC=10, answered+handling, `cdr.setConnectedUser(7301)` ve `cdr.setAssignedUser(7301)` önceden set, `call.setUser(null)` → çağrı sonrası `cdr.getConnectedUser()` hâlâ 7301 (assignedUser da 7301 korunur).
- (Karakterizasyon) `populateCdrUserOwnership_successfulWithCallUser_setsConnectedUser`: `call.user=7301`, başarılı bağlantı → `cdr.getConnectedUser()==7301`.
- (Karakterizasyon) `populateCdrUserOwnership_notSuccessfulWithStaleConnected_nullifiesConnectedUser`: handling-yok TRC 10 (veya TRC 84), `cdr.connectedUser` önceden dolu, `call.user=null` → `cdr.getConnectedUser()==null` (guard stale değeri korumaz).
- Mevcut suite (özellikle satır 384-420 `populateCdrUserOwnership*`) regression koruması olarak birlikte koşulur.

## Uygulama Kontrol Listesi

- [x] Açık sorular kullanıcı tarafından cevaplandı ve plan güncellendi.
- [x] Mevcut davranışı kilitleyen karakterizasyon testleri yazıldı (2 test; başarısız bağlantıda NULL'lama + user dolu davranışı korunuyor).
- [x] Beklenen davranışı kanıtlayan regression testi yazıldı ve kırmızı doğrulandı (`...preservesExistingConnectedUser`, ilk koşumda Failures: 1).
- [x] En küçük üretim değişikliği (`populateCdrUserOwnership` connectedUser guard) uygulandı.
- [x] Hedefli testler yeşile çevrildi (UserPopulationEvaluatorTest 26/0/0).
- [x] Refactor gerekmedi; değişiklik tek dalla sınırlı kaldı.
- [x] Hot path maliyeti plan notuyla doğrulandı (yeni IO/query/lock yok; tek in-memory dal).
- [x] History kaydı (`docs/history/17.06.2026.md`) ve indeks güncellemesi tamamlandı.

## Hedefli Doğrulama Komutları

Doğrulama öncesi `.codex/TOOL-PATHS.local.properties` okunur; Maven `-o` offline çalıştırılır; eksik artifact varsa indirme denenmez, raporlanır.

```text
mvn -o -Dtest=UserPopulationEvaluatorTest test
```

Gerekirse geniş ownership/finalize regression'ı:

```text
mvn -o -Dtest=UserPopulationEvaluatorTest,ProcessCallResultPersistFirstRegressionTest,SaveInitialCdrHangupResultRegressionTest,WebphoneSubscriberTest test
```

AngularJS frontend etkisi yoktur; Karma testleri kapsam dışıdır.

## Performans Etkisi

- Hot path durumu: Redis `RESULT_CODE` event path'i + AMI finalize hot path'tir.
- Call/event/tick başına ek maliyet: Yeni DB query/IO YOK. Değişiklik tek `if/else` dalı; `shouldPopulateCdrConnectedUserId` zaten çağrılıyordu (ek hesap yok).
- IO/scan/lock/serialization/log etkisi: Yeni synchronized blok, koleksiyon taraması veya log yok.
- Azaltım/ölçüm notu: Deploy sonrası izleme — `pd_cdr` üzerinde `connected_user_id IS NULL AND assigned_user_id IS NOT NULL AND telephony_rc IN (10,24) AND call_handling_start IS NOT NULL` sayımının düşmesi.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı ve plan güncellendi.
- [x] Başarılı bağlantıda `call.user==null` iken connected user korunuyor; başarısızda NULL'lanıyor (testlerle kanıtlı).
- [x] `assigned_user_id` ile `connected_user_id` başarılı görüşmelerde tutarlı.
- [x] Mevcut ownership davranışları regression'da korunuyor (87 test yeşil).
- [x] Planlanan hedefli testler çalıştırıldı ve başarılı oldu.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Uygulama tamamlandı (2026-06-17). Üretim kodu değişikliği:

- `UserPopulationEvaluator.populateCdrUserOwnership` connectedUser dalı assignedUser ile simetrik hâle getirildi: `shouldPopulateCdrConnectedUserId(call)=true` ve `call.user==null` iken mevcut `cdr.connectedUser` korunur (ezilmez); `call.user` dolu ise set edilir; başarılı değilse NULL'lanır. Böylece geç result-code finalize'ında `ASSIGN_TO_USER_GROUP` callback reassignment ile `call.user` temizlense bile başarılı agent görüşmesinin connected user'ı kaybolmaz.

Test değişikliği (`UserPopulationEvaluatorTest`, +3 test → 26 toplam):

- `populateCdrUserOwnership_successfulButCallUserCleared_preservesExistingConnectedUser` (regression; fix öncesi Failures: 1).
- `populateCdrUserOwnership_successfulWithCallUser_setsConnectedUser` (karakterizasyon).
- `populateCdrUserOwnership_notSuccessfulWithStaleConnected_nullifiesConnectedUser` (karakterizasyon; guard'ın yalnız başarılı bağlantıda koruduğunu kilitler).

Doğrulama (offline, JDK 1.8.0_432 + Maven 3.9.15, `.codex/TOOL-PATHS.local.properties`):

```text
mvn -o -Dtest=UserPopulationEvaluatorTest,ProcessCallResultPersistFirstRegressionTest,SaveInitialCdrHangupResultRegressionTest,WebphoneSubscriberTest test
```

Sonuç: `BUILD SUCCESS`; `Tests run: 87, Failures: 0, Errors: 0, Skipped: 0` (WebphoneSubscriberTest 47, UserPopulationEvaluatorTest 26, SaveInitialCdrHangupResultRegressionTest 11, ProcessCallResultPersistFirstRegressionTest 3).

Deploy sonrası izleme: `pd_cdr` üzerinde `connected_user_id IS NULL AND assigned_user_id IS NOT NULL AND telephony_rc IN (10,24) AND call_handling_start IS NOT NULL` sayımının düşmesi.

## Açık Sorular

Tüm açık sorular kullanıcı tarafından kapatıldı (2026-06-17).

- [x] **#1 Test kapsamı** — Cevap: **Sadece evaluator birim testi**. `UserPopulationEvaluatorTest` regression + karakterizasyon testleri kök nedeni kilitler; uçtan-uca entegrasyon testi bu kapsamda eklenmez.
- [x] **#2 Geçmiş veri (backfill)** — Cevap: **Yalnız forward-fix**. Etkilenen eski kayıtlar için backfill bu işe dahil değildir; gerekirse ayrı iş açılır.
- [x] **#3 Tetikleyici kapsam** — Cevap: **Başka senaryo yok**. Kapsam kök neden + tüm geç-finalize yollarını kapatan evaluator guard'ıyla sınırlı kalır.
