# FIX Awaiting User Result Code Authoritative Recovery Change/Fix Planı

## Bağlam

Müşteri 30.07–06.08.2026 aralığında `pd_cdr.telephony_rc = 10` (Normal call clearing / Answered) olmasına rağmen `pd_cdr.user_result_code_id` NULL kalan kayıtlar raporladı. Sorgu `pd_cdr × ast_registrar_cdr × wbp_call_result_code` join'i olduğu için raporlanan her kayıtta agent tarafında **girilmiş bir result code mevcut**; kayıp pd_cdr yazımında.

Raporlanan 4 kayıt tek tek incelendi (SQL + Elastic `turkcell-2026.08.04/05/06`). Sonuç: **3 gerçek kayıp + 1 sorgu zamanlaması false-positive**. Gerçek kayıpların kök nedeni tek değil, **iki ayrı problem**:

| Kayıt (interaction_id) | Kampanya / contact | RC − call_end | Sonuç |
| --- | --- | --- | --- |
| `dl-e65d932e-…` (30.07) | 85 / 234124 | +0,2 sn | Redis result code mesajı dialer'a **hiç ulaşmadı** |
| `dl-86774f5e-…` (04.08) | 64 / 494053 | +15 dk 2 sn | **Yazma yarışı**: durable fallback URC=159 yazdı, awaiting-timeout finalize 139 ms sonra NULL ile ezdi |
| `dl-f913b97a-…` (05.08) | 74 / 919877 | +0,2 sn | Redis result code mesajı dialer'a **hiç ulaşmadı** |
| `dl-1603013a-…` (06.08) | 64 / 500397 | +2 dk | **Bug değil**: RC alındı, `processCallResult` pd_cdr'a yazdı; müşteri sorgusu yazımdan ~121 ms önce çalışmış |

Ayırt edici kanıt kampanya result tablosudur: durable fallback çalıştığında `updateCampaignResultCodeFallback` kampanya tablosuna da yazar. Yarış vakasında (`contact 494053`) kampanya tablosunda `user_result_code = 159` **dolu**, pd_cdr NULL. Kayıp-mesaj vakalarında (`919877`, `234124`) kampanya tablosunda da NULL ve satırın tek dokunuşu `UpdatedDate = hangup + 15 dk` (awaiting-timeout finalize'ı). 30.07 kaydının Elastic index'i retention nedeniyle silinmiş olmasına rağmen bu imza sayesinde sınıflandırılabildi.

Aynı dönemin Elastic sayaçları (04/05/06.08): `Could not set result code` = **0/0/0**, `connection loss` = **0/0/0**, `Timed out awaiting user result code call` = **138/104/57**, `Durable result code fallback applied` = **73/52/49**. Yani Haziran'ın iki büyük ailesi (AMI flap kaynaklı trc=75 ve agent-churn kaynaklı null-pencere) bu dönemde **hiç görünmüyor**; kalan kayıp yalnız awaiting-user-result yolunda.

Bu plan, 07.05.2026 tarihli `FIX-317516-AWAITING-USER-RESULT-RACE-OVERWRITE` planının doğrudan devamıdır. O planın (B) maddesi awaiting-timeout overwrite'ı için **B2 (DB read önce, in-memory'ye bağla)** kararını uygulamıştı; bu fix o kararın kapsamadığı iki boşluğu kapatır: (1) okuma kaynağı yalnız `pd_cdr` olduğu için Redis mesajı hiç gelmemişse telafi yolu yok, (2) read-then-write penceresi senkronize değil.

## İş Etiketi

fix

## Öncelik

normal

Hotfix değil: hacim 7 günde 3 kayıt ve iki vakada disposition kampanya result tablosunda ya da webphone tablolarında hâlâ mevcut. Ancak `pd_cdr` üzerinden raporlama yapan taraf için kayıp geri dönüşsüz olduğundan iş bir sonraki release trenine alınmalıdır.

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionDecorator.java`
  - `hydrateUserResultCodeFromDurableFallback` (authoritative kaynak eklenir)
  - `persistFinalCdr` (in-memory CDR üzerindeki URC'nin null'a düşürülmesi engellenir)
- `gbpowerdialer/src/main/java/com/powerdialer/dao/ICDRDao.java` + `dao/impl/CDRDao.java` (yeni read-only lookup)
- `gbpowerdialer/src/main/java/com/powerdialer/service/CDRService.java` (yeni `@Transactional(readOnly = true)` delegasyon)
- `gbpowerdialer/src/test/java/tests/ami/**` (yeni regression testleri)

Bu planda **değişmeyecek** alanlar:

- `handleDialerHangup` finalize/awaiting karar zinciri ve `shouldProcessCallResultOnHangup` gate'leri.
- `UserPopulationEvaluator` ownership/URC gate'leri (trc 78/84 nulling davranışı kasıtlıdır, korunur).
- Redis channel adları, `CallResultCode` payload contract'ı, `WebphoneSubscriber` gate'leri.
- Kampanya result tablosu yazım deseni (`insertCallResultToDB` mevcut akışta zaten `runCallResultSideEffects` içinde çağrılır).

## Mevcut Davranış

Awaiting yolu (agent'ın result code'u beklenen çağrı):

1. `handleDialerHangup` → `shouldProcessCallResultOnHangup` false → `persistHangupSnapshotForDelayedResult` pd_cdr'a `telephony_rc` + `call_end_time` yazar, `user_result_code_id` **NULL** kalır (`MCD:1108` in-memory URC null olduğu için).
2. Çağrı `awaitingUserResultCodeCalls` cache'ine konur (`MCD:953`, TTL `Constants.AWAITING_USER_RESULT_EXPIRE_TIMEOUT` = 900000 ms).
3. Agent result code girer → webphone Redis'e publish eder → `WebphoneSubscriber.handleResultCodeEntityMessage` (`WS:236` `... is received`) → çağrı cache'te bulunursa `processActiveCallResultCode` → `processCallResult` → `persistFinalCdr` URC'yi yazar. Bu **mutlu yol** ve 06.08 kaydında birebir çalıştığı doğrulandı.
4. Çağrı cache'te bulunamazsa `persistDurableResultCodeFallback` (`WS:282`) pd_cdr'ı DB'den okur, guard'lardan geçerse URC'yi pd_cdr'a ve kampanya tablosuna yazar.
5. 15 dk içinde result code gelmezse Guava eviction → `finalizeAwaitingUserResultCodeCall` (`MCD:1678`) → `hydrateUserResultCodeFromDurableFallback` (`MCD:1698`) → `processCallResult` → `persistFinalCdr`.

İki kayıp mekanizması:

**(1) Redis result code mesajı hiç ulaşmıyor.** `dl-f913b97a` için Elastic'te 75 log satırı var, `WebphoneSubscriber` satırı **sıfır**. `LOG.info(callResultCode + " is received")` (`WS:236`) parse edilen her `RESULT_CODE` mesajı için koşulsuz basılır; `onMessage` catch'inin `Could not handle entity message` ERROR'u da yok. `webphoneEventExecutor` (`ExecutorConfig:25`) core 4 / max 8 / queue 10000 / **CallerRunsPolicy** olduğu için kuyruk dolsa bile mesaj atılmaz. Aynı dakikada 46, saatlerce ortalama 41 result code sorunsuz işlenmiş (dakika histogramında boş dakika yok) → subscriber ayakta, tek mesaj kaybı. Dialer tarafında webphone'un result code tablosunu **authoritative kaynak olarak okuyan hiçbir yol yok**: `hydrateUserResultCodeFromDurableFallback` yalnız kendi tablosuna (`pd_cdr`) bakar. Sonuç: 15 dk sonunda `persistFinalCdr` URC'yi NULL yazar, çağrı cache'lerden çıkar, telafi yolu kalmaz.

**(2) Yazma yarışı.** `dl-86774f5e` için Elastic zinciri:

```text
15:47:20.779  [ami-event-worker-…-32-0]  CDR saved            <- delayed hangup snapshot (trc=10, URC null)
15:47:20.808  [ami-event-worker-…-32-0]  "Hangup for:"        <- awaiting cache, TTL 15 dk
16:02:23.005  [webphone-event-1]         "... is received"    <- agent RC=159 (TTL bitişine ~2 sn kala)
16:02:23.205  [webphone-event-1]         CDR saved            <- durable fallback pd_cdr'a URC=159 YAZDI
16:02:23.344  [ami-event-worker-…-32-0]  CDR saved            <- awaiting-timeout finalize AYNI satıra URC=NULL yazdı
16:02:23.506  [ami-event-worker-…-32-0]  WARN "Timed out awaiting user result code call … userResultCode=null"
16:02:23.933  [webphone-event-1]         "Durable result code fallback applied"
```

`persistFinalCdr` (`MCD:1242`) URC'yi in-memory'den türetir:

```java
Long preservedUserResultCodeId = call.getUserResultCode() != null
    ? call.getUserResultCode().getId() : cdr.getUserResultCodeId();
cdr.setUserResultCodeId(call.getUserResultCode() != null ? call.getUserResultCode().getId() : null);
UserPopulationEvaluator.populateCdrUserOwnership(call);
if (preserveUserResultCode && preservedUserResultCodeId != null) { ... }
```

`hydrate` DB'yi fallback commit'inden **önce** okuduğu için in-memory URC null kaldı; `preserveUserResultCode=false` olduğundan üçüncü satır `null` yazdı ve fallback'in yazdığı 159 ezildi. Yarış tesadüf değil **yapısal**: TTL dolduktan sonra gelen result code mesajı `findActiveOrRecoverableCall` → `awaitingUserResultCodeCalls.getIfPresent(...)` çağrısıyla Guava eviction'ını **kendisi tetikler**; yani aynı tetikle fallback ve finalize eşzamanlı koşar.

Kampanya result tablosunda değerin doğru kalması da bunu doğrular: fallback'in kampanya yazımı ayrı statement olduğu için ezilmedi, yalnız pd_cdr ezildi.

## Beklenen Davranış

1. Awaiting-timeout finalize'ı çağrıyı kapatmadan önce result code'u **authoritative kaynaktan** son bir kez arar: `ast_registrar_cdr` (agent bacağı) → `wbp_call_result_code`. Bulunur ve result code'u giren kullanıcı çağrının kullanıcısıyla eşleşirse in-memory `call.userResultCode` + `cdr.userResultCodeId` bu değere bağlanır; mevcut `processCallResult` akışı hem `pd_cdr`'a hem kampanya result tablosuna yazar.
2. Authoritative kaynakta result code yoksa veya kullanıcı eşleşmezse davranış bugünle **aynı** kalır (URC null, retry/complete kararı değişmez).
3. `persistFinalCdr`, in-memory CDR üzerinde zaten dolu olan bir `user_result_code_id`'yi `call.userResultCode` null olduğu için NULL'a düşürmez. `UserPopulationEvaluator`'ın başarısız çağrılarda URC'yi null'lama yetkisi **korunur**.
4. Lookup, `UserPopulationEvaluator.shouldPopulateCdrUserResultCode(call)` false ise hiç yapılmaz; yani evaluator zaten null'layacaksa gereksiz DB erişimi eklenmez.

## Kod Okuma Bulguları

- `MCD:953` awaiting cache'e giriş tek noktadır; `awaitUserResultCodeAfterHangup` helper'ı FIX-317516'da kaldırıldı.
- `MCD:1100-1120` `persistHangupSnapshotForDelayedResult` snapshot'ı URC'yi in-memory değerden yazar (awaiting'e girişte null).
- `MCD:1678-1690` `finalizeAwaitingUserResultCodeCall`: `completeDate != null || isBusinessResultFinalized()` ise erken döner (mutlu yol sonrası ikinci finalize yok).
- `MCD:1698` `hydrateUserResultCodeFromDurableFallback` yalnız `cdrService.getByInteractionId` kullanır; bulduğunda synthetic `ResultCode(id, finalStatus=true)` üretir (retry kararını "no retry" yönünde tutar).
- `MCD:1215` `runCallResultSideEffects` içinde `dbOperations.insertCallResultToDB(call)` çağrılır → recovered URC kampanya result tablosuna da akar, ek kod gerekmez.
- `MCD:1242` `persistFinalCdr` zaten `preservedUserResultCodeId` hesaplıyor ancak yalnız `preserveUserResultCode=true` iken geri yazıyor.
- `WS:236/239` receipt ve işlem sonu logları; `WS:341-389` durable fallback gövdesi (`isSuccessfulCdrForUserResultCode` trc∈{10,24}+handling; `isResultCodeUserMatchesCdr` assigned-first).
- `CDRDao:48/64` mevcut authoritative sorgu deseni: native SQL + `WITH (NOLOCK)` + `setTimeout(WEBPHONE_RECOVERY_QUERY_TIMEOUT_SECONDS)`; `ICDRDao` → `CDRDao` → `CDRService` (`@Transactional(readOnly = true)`) zinciri.
- `Constants:98` `AWAITING_USER_RESULT_EXPIRE_TIMEOUT = 900000`.
- `UserPopulationEvaluator:428/350/307/333` gate zinciri: `connected_user_id` dolu kalmışsa URC nulling çalışmamıştır; raporlanan 4 kaydın hepsinde `connected_user_id` dolu (müşteri sorgusundaki inner join gereği) → evaluator bu vakalarda suçsuz.
- `release/6.6.9` (canlı) kaynağı doğrulandı: `persistFinalCdr:1232`, `hydrateUserResultCodeFromDurableFallback:1688`, `persistHangupSnapshotForDelayedResult:1090` ve tüm log satırları develop ile aynı.

## Root Cause / Değişiklik Gerekçesi

1. **Authoritative kaynak eksikliği:** Agent'ın girdiği result code'un tek gerçek kaynağı webphone tablolarıdır; dialer bu değeri yalnız Redis pub/sub üzerinden öğrenir. Redis fire-and-forget olduğu için tek mesaj kaybı disposition'ı kalıcı kaybettirir. Telafi noktası zaten var (`hydrate`), fakat yanlış kaynağa (kendi tablosuna) bakıyor.
2. **Read-then-write yarışı:** Aynı `pd_cdr` satırını iki thread (webphone durable fallback ve awaiting-timeout finalize) senkronizasyonsuz güncelliyor; finalize tarafı URC'yi in-memory'den türettiği için "bilgim yok" durumunu "değer yok" diye yazıyor.

(1)'in düzeltmesi (2)'yi de kapatır: yarış vakasında webphone result code satırı (16:02:22.920) finalize'ın okuma anından (~16:02:23.3) **önce** oluşmuştu; authoritative lookup değeri bulur ve finalize doğru değerle persist eder. (3) numaralı `persistFinalCdr` guard'ı ise IO eklemeyen savunma katmanıdır.

## Çözüm Yaklaşımı

### (A) Authoritative webphone result code lookup — birincil

`ICDRDao`/`CDRDao`/`CDRService` zincirine mevcut desenle yeni read-only sorgu eklenir:

```java
Long findWebphoneUserResultCodeId(String parentId, Long userId);
```

```sql
SELECT TOP 1 WCRC.result_code_id
FROM ast_registrar_cdr ARC WITH (NOLOCK)
INNER JOIN wbp_call_result_code WCRC WITH (NOLOCK) ON ARC.uniqueid = WCRC.call_id
WHERE ARC.parentid = :parentId AND WCRC.user_id = :userId AND WCRC.result_code_id IS NOT NULL
ORDER BY WCRC.create_date DESC
```

`hydrateUserResultCodeFromDurableFallback` sırası:

1. Mevcut `pd_cdr` kontrolü (değişmez).
2. pd_cdr'da değer yoksa: `UserPopulationEvaluator.shouldPopulateCdrUserResultCode(call)` false ise **çık** (evaluator nasılsa null'layacak, IO eklemeyiz).
3. Kullanıcı kimliği çözümlenir: `call.getUser()` → yoksa `cdr.getAssignedUser()` → yoksa `cdr.getConnectedUser()`. Hiçbiri yoksa **çık** (yanlış agent'a disposition yazma riski alınmaz).
4. `findWebphoneUserResultCodeId(call.getId(), userId)` çağrılır; değer varsa mevcut synthetic `ResultCode(id, finalStatus=true)` deseniyle in-memory'ye bağlanır ve WARN/INFO ile izlenebilir tek satır log basılır.
5. `processCallResult` mevcut akışıyla pd_cdr + kampanya result tablosunu yazar.

`user_id` kolon adı ve `wbp_call_result_code` şeması müşteri ortamında `INFORMATION_SCHEMA` çıktısıyla doğrulanmıştır (`id, result_code_id, create_date, user_id, group_id, call_id, session_id, details, set_by_user`).

### (B) `persistFinalCdr` null-ezme koruması — savunma katmanı, IO'suz

`cdr.setUserResultCodeId(call.getUserResultCode() != null ? ... : null)` satırı `preservedUserResultCodeId` kullanacak şekilde daraltılır: in-memory CDR'da zaten dolu bir URC varsa null'a düşürülmez. `populateCdrUserOwnership` çağrısı ve onun başarısız çağrılarda URC'yi null'lama yetkisi aynen kalır; yani trc 78/84 davranışı değişmez.

### (C) Yarış serileştirme — bu planda kapsam dışı önerisi

Per-`Call` monitor ile `finalizeAwaitingUserResultCodeCall` ve webphone result code işlemenin karşılıklı dışlanması mümkündür (yarışan iki thread aynı JVM'de; log'larda `Call@27ebf736` nesne kimliği ikisinde de aynı). (A) uygulandığında gözlenen kayıp ortadan kalktığı için bu madde açık soru olarak bırakılır; eklenecekse lock yalnız bu iki çağıran metotta alınır, `handleDialerHangup` hot path'i lock görmez.

## Riskler

- **Retry/complete kararı değişir:** Recovered URC synthetic `finalStatus=true` ile bağlandığı için `isResultCodeAllowsRetries` "no retry" yönünde karar verir. Bu, FIX-317516'nın hâlihazırda uyguladığı desendir ve doğru yöndür (agent disposition girmiştir), fakat bugün retry alan bir kayıt yarın almayabilir. Regression testiyle açıkça kilitlenecek.
- **Yanlış agent'ın disposition'ı:** Kullanıcı eşleşmesi zorunlu tutulmadan yazım yapılırsa hatalı veri üretir. (A) adım 3 bu riski kapatır; test senaryosu eklenecek.
- **Webphone tablo şeması bağımlılığı:** `ast_registrar_cdr` / `wbp_call_result_code` dialer'ın sahibi olmadığı tablolardır. Mevcut `CDRDao` deseni (native SQL + timeout + hata durumunda davranışı değiştirmeyen fallback) korunacak; sorgu hatası finalize'ı bozmayacak.
- **DNC/callback yan etkileri uygulanmaz:** Recovered değer yalnız id'dir; `isDncIdentifier`, `callbackDate` gibi alanlar bilinmez. Bu bilinçli kapsam kararı olarak yazılır (Açık Soru #2).
- **Kayıp mesajın kökü çözülmez:** (A) kaybı telafi eder; result code mesajının neden düştüğü webphone/Redis tarafında ayrı iştir.

## TDD Stratejisi

1. `tests/ami/AwaitingUserResultAuthoritativeRecoveryRegressionTest` (yeni, JUnit4 + Mockito):
   - **(kırmızı)** awaiting-timeout finalize; `pd_cdr` URC null, authoritative lookup RC id döndürüyor, kullanıcı eşleşiyor → `persistFinalCdr` sonrası `cdr.getUserResultCodeId()` dolu.
   - Lookup null → davranış değişmez (URC null, mevcut testlerle uyumlu).
   - Kullanıcı çözümlenemiyor (`call.user`, assigned, connected hepsi null) → lookup **hiç çağrılmaz**, URC null kalır.
   - `shouldPopulateCdrUserResultCode(call)` false (örn. trc 84) → lookup **hiç çağrılmaz** (gereksiz IO yok).
   - Lookup exception atıyor → finalize kesilmez, URC null kalır.
2. `tests/ami/FinalCdrUserResultCodePreserveRegressionTest` (yeni):
   - in-memory `cdr.userResultCodeId` dolu + `call.userResultCode` null + trc=10 → persist URC'yi korur.
   - Aynı kurulum trc=84 ile → `populateCdrUserOwnership` null'lar (mevcut kasıtlı davranış korunur).
3. Mevcut regression'lar kırmızıya düşmemeli: `AwaitingUserResultTimeoutPreserveRegressionTest`, `AwaitingUserResultCacheTransitionRegressionTest`, `WebphoneSubscriberTest`, `SaveInitialCdrHangupResultRegressionTest`, `CallCleanerTelephonyResultRegressionTest`.

## Açık Sorular

Tümü cevaplandı (2026-08-06, developer kararı):

- [x] **#1 (C) maddesi — per-`Call` monitor ile yarış serileştirme:** **Hayır.** (A) uygulandığında finalize doğru değeri bulduğu için gözlenen kayıp ortadan kalkıyor; call lifecycle'a yeni senkronizasyon yüzeyi eklenmiyor. (C) bu planın kapsamı dışında, ayrı iş olarak izlenecek.
- [x] **#2 Recovered result code'un yan etkileri:** **Hayır.** Lookup yalnız result code id'sini döndürdüğü için DNC güncellemesi ve callback oluşturma tetiklenmeyecek; kapsam `pd_cdr` + kampanya result tablosu tutarlılığıyla sınırlı.
- [x] **#3 Diğer finalize yolları:** **Yalnız awaiting-timeout.** `finalizeRecoverableConnectionLossCall` ve `clearExpiredCall` bu planın dışında; conn-loss için hazır `dde409d6` (`fix/GLB90060295-conn-loss-urc-koruma`) branch'i, call-expiry (trc 73) ayrı kök neden olarak beklemede.
- [x] **#4 Hedef branch:** Fix `develop`'a gidecek; merge/release yönetimi developer'da. Ajan `fix/GLB90060295-awaiting-urc-authoritative-recovery` branch'i üzerinde çalışmaya devam eder.

## Execution Plan

- [x] Onaylanan planın tüm adımları bu dosyaya işlendi.
- [x] Açık sorular cevaplandı ve plana işlendi.
- [x] Mevcut davranışı kilitleyen karakterizasyon/regression testleri yazıldı (`AwaitingUserResultAuthoritativeRecoveryRegressionTest` lookup-null/skip senaryoları ve `FinalCdrUserResultCodePreserveRegressionTest` trc=84 senaryosu).
- [x] Beklenen davranışı kanıtlayan başarısız testler yazıldı (authoritative recovery + preserve senaryoları). RED-1: `findWebphoneUserResultCodeId` yokken test-compile hatası. RED-2: seam eklendikten sonra 10 testin 3'ü davranışsal olarak kırmızı.
- [x] `ICDRDao.findWebphoneUserResultCodeId` + `CDRDao` native sorgu implementasyonu eklendi (timeout guard'lı).
- [x] `CDRService` `@Transactional(readOnly = true)` delegasyonu eklendi.
- [x] `hydrateUserResultCodeFromDurableFallback` authoritative lookup adımıyla genişletildi (guard sırası: pd_cdr → evaluator gate → user çözümü → lookup).
- [x] `persistFinalCdr` null-ezme koruması uygulandı.
- [x] Hedefli testler yeşile çevrildi (11 yeni test dâhil 92/92).
- [x] Gerekliyse küçük refactor yapıldı ve testler tekrar çalıştırıldı (`findPersistedUserResultCodeId` ayrıştırması + pozitif-id boundary guard'ı).
- [x] Doküman/history güncellemesi tamamlandı.

### Plan dışına çıkılan iki nokta (uygulama sırasında ortaya çıktı, gerekçeli)

1. **Pozitif-id boundary guard'ı eklendi.** İlk GREEN denemesinde `AwaitingUserResultTimeoutPreserveRegressionTest.shouldKeepCdrUserResultCodeNullWhenDbAndInMemoryAreBothEmpty` kırmızıya döndü: `user_result_code_id` NULL yerine **0** yazıldı. Sebep, Mockito'nun stub'lanmamış `Long` dönüşü için `0L` üretmesiydi; fakat aynı değer dialer'ın sahibi olmadığı bir tablodan da gelebileceği için bu bir gerçek sağlamlık boşluğu kabul edildi. `findAuthoritativeWebphoneUserResultCodeId` artık `resultCodeId <= 0` değerini yok sayar; davranış `shouldIgnoreNonPositiveWebphoneResultCodeId` testiyle kilitlendi.
2. **Mevcut FIX-317516 testinin fixture'ı açıklaştırıldı.** Aynı testte `findWebphoneUserResultCodeId` artık akışın parçası olduğu için "hiçbir kaynakta result code yok" senaryosu mock default'una bırakılmayıp açıkça `null` stub'landı. Assertion ve testin amacı değişmedi; yalnız yeni collaborator fixture'a eklendi.

## Performans Etkisi

- **Hot path durumu:** `persistFinalCdr` değişikliği → `Hot path, ek maliyet yok` (yalnız mevcut in-memory değerin kullanımı; IO/lock/allocation eklenmiyor). Authoritative lookup → `Hot path değil` (awaiting-timeout cleanup yolu).
- **Call/event/tick başına ek maliyet:** Hangup ve result code hot path'lerine **0** ek sorgu. Lookup yalnız awaiting TTL dolduğunda çalışır; Elastic sayacına göre üst sınır ~138 çağrı/gün (≈0,0016/sn).
- **IO/scan/lock/serialization/log etkisi:** `TOP 1` + `ORDER BY create_date DESC` tek satır native sorgu, `WITH (NOLOCK)`, mevcut `WEBPHONE_RECOVERY_QUERY_TIMEOUT_SECONDS = 10` guard'ı. Yeni lock yok, yeni serialization yok, çağrı başına tek satır log.
- **Azaltım veya ölçüm notu:** Lookup öncesi iki in-memory guard (evaluator gate + kullanıcı çözümü) gereksiz sorguları eler. `ast_registrar_cdr.parentid` üzerinde index gerekliliği doğrulanacak; yoksa DBA'ya index önerisi ayrı madde olarak iletilir.

## Doğrulama

`.codex/TOOL-PATHS.local.properties` okunduktan sonra offline hedefli test:

```text
mvn -o -f gbpowerdialer/pom.xml -Dtest=AwaitingUserResultAuthoritativeRecoveryRegressionTest,FinalCdrUserResultCodePreserveRegressionTest,AwaitingUserResultTimeoutPreserveRegressionTest,AwaitingUserResultCacheTransitionRegressionTest,WebphoneSubscriberTest,SaveInitialCdrHangupResultRegressionTest,CallCleanerTelephonyResultRegressionTest test
```

Deploy sonrası canlı doğrulama imzaları:

- Müşteri sorgusu (`telephony_rc IN (10,24)` + `user_result_code_id IS NULL` + webphone'da result code var) günlük sayımı → 0'a inmeli. Sorguya `AND PC.call_end_time < DATEADD(MINUTE, -20, GETDATE())` eklenmesi yanlış alarmı (06.08'deki gibi) engeller.
- Yeni recovery log satırının günlük sayısı ≈ bugün kaybedilen kayıt sayısı olmalı.
- `Timed out awaiting user result code call` hacmi değişmemeli (bu fix awaiting sayısını azaltmaz, sonucunu düzeltir).

## Kapanış Kriteri

- [x] Açık sorular cevaplandı veya `Yok` olarak işaretlendi.
- [x] Planlanan testler çalıştırıldı ve başarılı oldu.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Uygulama tamamlandı (2026-08-06). Branch: `fix/GLB90060295-awaiting-urc-authoritative-recovery`, base `95eb08ca` ("Merged PR 24924: Data Manipulation bug-fix" — develop 13:22'de fast-forward pull edilmişti; planın ilk yazımındaki `770ef8a3` referansı o pull'dan önceki HEAD'di ve yanlıştı, `770ef8a3` yeni base'in atasıdır). Merge/release yönetimi developer'da.

Üretim kodu değişiklikleri:

- `ICDRDao` / `CDRDao`: yeni `findWebphoneUserResultCodeId(parentId, userId)`. `ast_registrar_cdr` (agent bacağı) ile `wbp_call_result_code` join'i üzerinden `TOP 1 ... ORDER BY create_date DESC`, `WITH (NOLOCK)`, mevcut `WEBPHONE_RECOVERY_QUERY_TIMEOUT_SECONDS = 10` guard'ı. `userId` filtresi zorunlu.
- `CDRService`: `@Transactional(readOnly = true)` delegasyon.
- `ManagerConnectionDecorator.hydrateUserResultCodeFromDurableFallback`: `pd_cdr` okuması `findPersistedUserResultCodeId`'ye ayrıştırıldı; değer yoksa `findAuthoritativeWebphoneUserResultCodeId` devreye girer. Guard sırası: evaluator gate (`shouldPopulateCdrUserResultCode`) → result code sahibi çözümü (`call.user` → `cdr.assignedUser` → `cdr.connectedUser`) → DB lookup → pozitif-id kontrolü. Bulunan değer mevcut synthetic `ResultCode(id, finalStatus=true)` deseniyle in-memory'ye bağlanır; `processCallResult` hem `pd_cdr`'ı hem kampanya result tablosunu yazar.
- `ManagerConnectionDecorator.persistFinalCdr`: URC artık `preservedUserResultCodeId` ile set edilir; in-memory CDR'da dolu olan değer `call.userResultCode` null olduğu için NULL'a düşürülmez. `populateCdrUserOwnership`'in başarısız çağrılarda URC'yi bastırma yetkisi aynen korunur.

Test değişiklikleri:

- Yeni: `tests/ami/AwaitingUserResultAuthoritativeRecoveryRegressionTest` (8 senaryo: recovery, pd_cdr önceliği, lookup-null, geçersiz id, evaluator-skip, sahip-çözülemedi-skip, CDR assignedUser'dan sahip çözümü, lookup exception'ında finalize'ın sürmesi).
- Yeni: `tests/ami/FinalCdrUserResultCodePreserveRegressionTest` (3 senaryo: in-memory CDR değerinin korunması, TRC 84'te bastırmanın korunması, canlı agent result code'unun önceliği).
- Genişletildi: `tests/ami/AwaitingUserResultTimeoutPreserveRegressionTest` — yeni collaborator için açık `null` stub (assertion değişmedi).

Doğrulama:

```text
mvn -o -s <settings> -Dmaven.repo.local=<repo> -f gbpowerdialer/pom.xml -Dtest=AwaitingUserResultAuthoritativeRecoveryRegressionTest,FinalCdrUserResultCodePreserveRegressionTest,AwaitingUserResultTimeoutPreserveRegressionTest,AwaitingUserResultCacheTransitionRegressionTest,WebphoneSubscriberTest,ProcessCallResultPersistFirstRegressionTest,LogoutOrphanCdrPersistRegressionTest,ConnectionLossBridgedCallRecoveryRegressionTest,SaveInitialCdrHangupResultRegressionTest,CallCleanerTelephonyResultRegressionTest,CdrPersistRoundTripReductionRegressionTest,InitialCdrOriginationRegressionTest test
```

Sonuç: `BUILD SUCCESS`, `Tests run: 92, Failures: 0, Errors: 0`. `persistFinalCdr` ortak finalize sözleşmesi olduğu için `-Dtest='tests.**'` ile geniş koşu da yapıldı: `Tests run: 371, Failures: 1, Errors: 1` — iki hata da **pre-existing baseline**: `UnmappedHangupCauseFallbackRegressionTest` (`ManagerConnectionDecorator:933` ham `Integer.parseInt`; satır `origin/develop`'ta birebir aynı, bu fix'in diff'i o bölgeye dokunmuyor — FCT merge'inden gelen ayrı defect) ve `LogbackAsyncAppenderConfigContractTest` (root'tan konsol appender çıkarma işinden kalan güncellenmemiş contract testi). Yeni kırık yok. Maven offline, `.codex/TOOL-PATHS.local.properties` üzerinden JDK 8 (1.8.0_202) + Maven 3.9.14.

Kapsam dışı kalanlar (izlenecek): kayıp Redis result code mesajının webphone/publish tarafındaki kök nedeni; per-`Call` monitor ile yarış serileştirme (Açık Soru #1); `finalizeRecoverableConnectionLossCall` + `clearExpiredCall` yollarına aynı lookup'ın taşınması (Açık Soru #3, `dde409d6` branch'i ile birlikte değerlendirilecek).
