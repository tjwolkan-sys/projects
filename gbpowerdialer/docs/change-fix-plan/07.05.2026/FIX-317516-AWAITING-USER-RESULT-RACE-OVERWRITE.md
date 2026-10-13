# FIX-317516 Awaiting User Result Race ve Timeout Overwrite Change/Fix Planı

- İş Etiketi: fix
- Kimlik: 317516
- Slug: awaiting-user-result-race-overwrite
- Tarih: 2026-05-07
- Öncelik: hotfix
- Plan Durumu: Uygulandı ve hedefli regression yeşil (2026-05-07)

## Bağlam

Üretimde başarılı `pd_cdr.telephony_rc=10` ve doğru user ownership alanlarına sahip belirli çağrılarda, webphone tarafında `RESULT_CODE` Redis mesajı gbpowerdialer'a ulaşmasına ve handler'da exception loglanmamasına rağmen `pd_cdr.user_result_code_id` ve campaign result tablosundaki `user_result_code` alanları **boş** kalıyor. 29.04.2026'daki `pd_cdr` user result code kaçağı düzeltmesi NPE/exception kaynaklı kayıpları kapatmıştı; 05.05.2026'daki telephony slot idempotent lifecycle değişikliği ise hangup sonrası user result code bekleyen çağrıların `awaitingUserResultCodeCalls` Guava cache'inde tutulması ve `ORPHANED_CALL_EXPIRE_TIMEOUT` (15 dk) dolduğunda `finalizeAwaitingUserResultCodeCall` ile `processCallResult` üzerinden finalize edilmesi davranışını eklemişti. Bu plan 05.05.2026 değişikliği canlıya alındıktan sonra ortaya çıkan yeni iki kayıp sınıfını ve bu iki kayıp sınıfının yarattığı bir mantık boşluğunu kapatır.

İncelenen örnek (kullanıcının paylaştığı vaka):

- `parentId`: `dl-70b5757a-4530-47b0-8f80-547b332c0002`
- `pd_cdr.contact_id`: `6960`, `pd_cdr.campaign_id`: `71`
- `ast_registrar_cdr` üzerinde başarılı CDR; `wbp_call_result_code` üzerinde 17:56'da result code kaydı mevcut.
- `pd_cdr.telephony_rc`: `10`; call answer ve handling start zamanları dolu.
- `pd_cdr.user_result_code_id`: `NULL`; campaign result tablosunda `user_result_code` `NULL`.

Kanıtlayıcı log zinciri:

| Zaman | Olay | Kaynak |
|-------|------|--------|
| 17:52:25.886 | Call is answered by agent | AMI Reader |
| 17:56:20.890 | HangupRequestEvent | AMI Reader-3 |
| 17:56:20.896 | HangupEvent (cause=16) | AMI Reader-95 |
| 17:56:21.031 | "CDR is saved/updated successfully" | `persistHangupSnapshotForDelayedResult` |
| 17:56:21.259 | "Got message: ... RESULT_CODE id=259, userId=9930, parentId=dl-..." | webphone-event-2 |
| 17:56:21.285 | "Telephony slot released" (ilk release) | `releaseTelephonySlotIfNeeded("hangup-waiting-user-result")` |
| 18:11:25.220 | "Telephony slot already released" | `expired-calls-executor-0` → `releaseTelephonySlotIfNeeded("process-call-result")` |
| 18:11:25.399 | "CDR is saved/updated successfully" (final) | `processCallResult` içindeki `cdrService.saveOrUpdate(cdr)` |
| 18:11:25.426 | "Timed out awaiting user result code call ..." | `finalizeAwaitingUserResultCodeCall` |

İki kritik gözlem:

1. Webphone mesajı (17:56:21.259) ile ilk `Telephony slot released` log'u (17:56:21.285) **arasındaki 26 ms**, `handleDialerHangup` else-branch'inin `clearCall → calls.invalidate` ve `awaitUserResultCodeAfterHangup → awaitingUserResultCodeCalls.put` adımları arasındaki bir yarış penceresine denk geliyor.
2. 18:11:25 civarındaki kayıtlar, çağrının `awaitingUserResultCodeCalls` içinde tam 15 dakika asılı kaldığını ve cache expire-listener'ı tarafından `processCallResult` üzerinden `cdr.userResultCodeId=null` ile kalıcı yazıldığını gösteriyor.

## İş Etiketi

fix

## Öncelik

hotfix

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/util/Constants.java`
  - Yeni `AWAITING_USER_RESULT_EXPIRE_TIMEOUT` sabiti (başlangıç değeri 900000 ms = 15 dk)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionDecorator.java`
  - `handleDialerHangup` else-branch sırası
  - `awaitUserResultCodeAfterHangup`
  - `releaseTelephonySlotIfNeeded` ve `clearCall` davranışı
  - `awaitingUserResultCodeCalls` cache `expireAfterWrite` ve `clearExpiredAwaitingUserResultCodeCalls` scheduler bağlanma noktaları
  - `finalizeAwaitingUserResultCodeCall`
  - `processCallResult` (CDR `userResultCodeId` set davranışı)
- `gbpowerdialer/src/main/java/com/powerdialer/redis/WebphoneSubscriber.java`
  - `processCallWithResultCodeAndUpdateDNC` içindeki `shouldSetResultCode=false` dalı
- `gbpowerdialer/src/test/java/com/powerdialer/redis/WebphoneSubscriberTest.java` (mevcut suite genişletilir)
- `gbpowerdialer/src/test/java/tests/<domain>` altında yeni dar regression test sınıf(lar)ı

## Mevcut Davranış

`ManagerConnectionDecorator.handleDialerHangup` else-branch'i (kullanıcı result code beklenen yol):

```
1) persistHangupSnapshotForDelayedResult(call, dateReceived);
2) releaseTelephonySlotIfNeeded(call, "hangup-waiting-user-result");
   → clearCall(call):
       - call.getCallOriginator().clearCall(call)
       - call.getCampaign().clearCall(call)
       - calls.invalidate(call.getId())                 ← `calls`'tan çıkarılır
       - recoverableConnectionLossCalls.invalidate(call.getId())
       - awaitingUserResultCodeCalls.invalidate(call.getId())  ← henüz konmadığı için no-op
       - removeRingingCall, unregisterCall
   → markTelephonySlotReleased() = true
   → doCampaignDecrementationsOnHangup(call)
   → log "Telephony slot released ... due to hangup-waiting-user-result"
3) awaitUserResultCodeAfterHangup(call):
   - calls.invalidate(call.getId())                     ← no-op
   - recoverableConnectionLossCalls.invalidate(call.getId())  ← no-op
   - awaitingUserResultCodeCalls.put(call.getId(), call) ← şimdi awaiting cache'e konur
```

`WebphoneSubscriber.findActiveOrRecoverableCall` lookup sırası:

1. `connectionDecorator.getCalls()`
2. `awaitingUserResultCodeCalls`
3. `recoverableConnectionLossCalls`

Yarış: 2. adımdaki `calls.invalidate` ile 3. adımdaki `awaitingUserResultCodeCalls.put` arasında çağrı **hiçbir cache'te bulunmaz**. Webphone subscriber tam bu pencerede lookup yaparsa null döner, `processCallWithResultCodeAndUpdateDNC` fallback'a düşer.

`finalizeAwaitingUserResultCodeCall` (line 1197-1208):

```java
if (call == null || call.getCompleteDate() != null || call.isBusinessResultFinalized()) {
  return;
}
Date completeDate = getAwaitingUserResultCodeCompletionDate(call);
processCallResult(call, completeDate);
LOG.warn("Timed out awaiting user result code call " + call + " on " + server, ...);
```

`processCallResult` (line 816-832) CDR persist:

```java
CDR cdr = call.getCdr();
cdr.setUserResultCodeId(call.getUserResultCode() != null ? call.getUserResultCode().getId() : null);
...
cdrService.saveOrUpdate(cdr);
```

Yani `call.userResultCode` null ise `cdr.userResultCodeId` **koşulsuz null'a yazılır** ve persist edilir. `dbOperations.insertCallResultToDB(call)` (line 803) da campaign result tablosunu aynı in-memory null user result code ile günceller. 21:259'daki webphone fallback DB'ye yazsaydı bile, 18:11:25'teki bu save tüm değeri null'a ezerdi.

`WebphoneSubscriber.processCallWithResultCodeAndUpdateDNC` (line 252-267):

```java
for (...connectionDecorator) {
  Call call = findActiveOrRecoverableCall(connectionDecorator, callResultCode.getParentId());
  if (call != null) {
    if (shouldSetResultCode(call, callResultCode)) {
      processActiveCallResultCode(connectionDecorator, call, callResultCode);
    }
    return;  // ← shouldSetResultCode=false ise fallback'a HİÇ geçmez
  }
}
CDR cdr = persistDurableResultCodeFallback(callResultCode, null, false);
...
```

`shouldSetResultCode=false` dönen üç nedenden birinde (`call.getUserResultCode() != null`, `call.getUser() == null`, `call.getUser().getId() != resultCode.getUserId()`) ikinci ve üçüncü durumlar fallback ile birlikte değerlendirilebilirken bu erken `return` fallback'ı tamamen devre dışı bırakıyor.

## Beklenen Davranış

(A) Yarış penceresi: Hangup sonrası user result code bekleyen çağrı, webphone subscriber'ın ulaşabileceği en az bir cache'te kesintisiz mevcut olmalı. `findActiveOrRecoverableCall` lookup'ı `calls` veya `awaitingUserResultCodeCalls`'tan birine her zaman erişebilmeli.

(B) Awaiting-timeout overwrite: `awaitingUserResultCodeCalls` 15 dk timeout ile `processCallResult` çağrıldığında, in-memory `call.userResultCode` null bile olsa, DB'deki `pd_cdr.user_result_code_id` ve campaign result tablosundaki `user_result_code` alanları zaten doluysa **ezilmemeli**. Telephony alanları (telephonyRC, callEndTime, ownership, abandoned, sequence, kolon order) yine doğru tutulmalı; ama webphone fallback uygulanmışsa user result code overwrite edilmemeli.

(C) `shouldSetResultCode=false` fallback boşluğu: Aktif call cache'inde call bulundu fakat `shouldSetResultCode` false döndüyse, durable fallback yine uygulama şansı bulmalı. Fallback'ın kendi user-match guard'ı (`isResultCodeUserMatchesCdr`) bu durumda da geçerli kalmalı; yanlış agent'ın result code'u CDR'a yazılmamalı. Fallback yalnız `call.userResultCode` zaten dolu olduğu için reddedilen durumda değil; user mismatch veya assigned user yokluğu durumunda da CDR'da assigned/connected user ile result code user'ı eşleşiyorsa idempotent biçimde uygulanmalı.

Diğer çıkış kriterleri:

- Webphone Redis payload contract'ı, channel adı, `RESULT_CODE` mesajının yapısı değişmemeli.
- AMI hangup hot path'inde yeni DB query veya yeni geniş `synchronized` blok eklenmemeli.
- Counter (callsInProgress, activeCalls, ringingCalls, originatedCalls) lifecycle'ı korunmalı; release ikinci kez çağrılırsa decrement tekrarlanmamalı.
- 29.04.2026 ve 30.04.2026 düzeltmelerinde belirlenen `UserPopulationEvaluator`, force-stopped campaign fallback ve assigned-user-first guard kuralları korunmalı.

## Kod Okuma Bulguları

- `releaseTelephonySlotIfNeeded("hangup-waiting-user-result")` ilk olarak `clearCall` çağırır; `clearCall` hem `calls.invalidate` hem `awaitingUserResultCodeCalls.invalidate` yapar. `awaitUserResultCodeAfterHangup` ardından `awaitingUserResultCodeCalls.put` çağırır. Bu sıra atomic değil; webphone-event thread'i araya girerse her iki cache'te de bulamaz.
- `Call.markTelephonySlotReleased` ve `Call.markBusinessResultFinalized` `synchronized` flag'leridir; idempotency'yi güvene alır. Ancak cache'lere erişim aynı kilit altında değil; cache transition'ı için ek bir guard gerekir.
- `processCallResult` (line 782-840) işin sonunda CDR alanlarını her zaman in-memory `call`'tan türetir ve `cdrService.saveOrUpdate(cdr)` çağırır. Bu yöntem normal akışta doğru; awaiting-timeout dalı için **risklidir**, çünkü o anda doğru kaynak in-memory call değil DB CDR olabilir.
- `dbOperations.insertCallResultToDB(call)` (campaign result tablosu) da aynı in-memory `call`'tan türetilir; aynı overwrite riski campaign result tablosu için de geçerlidir.
- `WebphoneSubscriber.persistDurableResultCodeFallback` halihazırda assigned-user-first user match guard'ı (`isResultCodeUserMatchesCdr`), successful CDR guard'ı (`isSuccessfulCdrForUserResultCode`) ve forcefully-stopped campaign özel dalını içeriyor. Aynı method `processCallWithResultCodeAndUpdateDNC` içinde `shouldSetResultCode=false` durumunda **çağrılmıyor**; bu boşluk doğrudan kapatılabilir.
- `recoverableConnectionLossCalls` cache'i kullanan AMI loss recovery yolu için ayrı bir cleanup metodu (`finalizeRecoverableConnectionLossCall`) var; oradaki davranış bu plan kapsamı dışıdır ve değiştirilmeyecektir.
- `awaitingUserResultCodeCalls` Guava cache `expireAfterWrite(ORPHANED_CALL_EXPIRE_TIMEOUT, ms)` ile yapılandırılmış (15 dk). Webphone result code mesajı geç gelse bile bu pencere içinde gelirse (örnek vakada 26 ms sonra geldi) yakalanması gerekirdi.

## Root Cause / Değişiklik Gerekçesi

Bir sınıf hata yerine, biri diğerinin etkisini gizleyen iki yapısal kusur birlikte var:

1. **Cache transition atomic değil**: `handleDialerHangup` else-branch'inde call önce `calls`'tan çıkarılıyor, sonra `awaitingUserResultCodeCalls`'a konuyor. Bu iki adım arasındaki yarış penceresinde webphone result code mesajı gelirse `findActiveOrRecoverableCall` null döner.
2. **`processCallResult` awaiting-timeout dalı için yanlış kaynak kullanıyor**: Awaiting-timeout durumunda DB'deki CDR/campaign result değerleri (varsa fallback ile yazılmış) in-memory `call.userResultCode == null` üzerinden geri null'a ezilir. Bu, webphone fallback'in uyguladığı kazanımı silen bir overwrite yoludur.

Bu iki sınıf birlikte, webphone fallback'in başarılı çalıştığı senaryolarda bile sonucu kaybediyor. Plan iki sınıfı birden ele almazsa düzeltme tek başına yetersiz kalır:

- Yalnız (A) çözülürse: webphone subscriber call'ı bulup `processActiveCallResultCode` çalıştırabilir; ama `processCallResult` içinde herhangi bir exception oluşur veya call awaiting cache'e zaten geçtiyse, awaiting timeout 15 dk sonra yine `processCallResult` ile null overwrite eder.
- Yalnız (B) çözülürse: yarış penceresinde fallback uygulanan CDR korunur; ama webphone'un call'ı bulup user result code'u in-memory call'a bağlama akışı yine kırık kalır ve sonradan in-memory state diğer akışlardan tutarsız davranabilir.
- (C) ise mevcut bir mantık boşluğu: aktif call bulunsa bile `shouldSetResultCode=false` durumunda durable fallback hiç çalışmıyor. (A) ve (B) bu boşluğu kapatmaz.

## Çözüm Yaklaşımı

### (A) Cache transition atomic'liği

`handleDialerHangup` else-branch sırası şu hâle getirilecek (in-flight pencere kapanır):

```
1) persistHangupSnapshotForDelayedResult(call, dateReceived)
2) awaitingUserResultCodeCalls.put(call.getId(), call)        ← BEFORE clearCall
3) releaseTelephonySlotIfNeeded(call, "hangup-waiting-user-result")
   → clearCall içinde reason="hangup-waiting-user-result" (veya eşdeğer state guard)
     iken awaitingUserResultCodeCalls.invalidate çağrılmaz; geri kalan tüm
     temizlikler (calls/recoverable/ringing/upToDateKeeper/originator) korunur.
```

Karar: **(a) Atomic sıra + clearCall skip** (Açık Soru #1 cevabı). Eski `awaitUserResultCodeAfterHangup` private helper metodu kaldırıldı; atomic transfer doğrudan else-branch içinde `markAwaitingUserResultCode + awaitingUserResultCodeCalls.put` ile yapılır. clearCall'ın awaiting cache'e dokunmaması bu sıra ile yarış penceresini fiilen kapatır.

Hedef invariant: çağrı `handleDialerHangup` else-branch'ine girdikten sonra her noktada en az bir cache'te (önce hem `calls` hem `awaitingUserResultCodeCalls`'ta, release sonrası yalnız `awaitingUserResultCodeCalls`'ta) tutulur.

Ek dikkat: `clearCall`'ın diğer çağrı noktalarında (preview timeout, origination fail, processCallResult, finalizeRecoverableConnectionLossCall, finalizeAwaitingUserResultCodeCall) mevcut `awaitingUserResultCodeCalls.invalidate` davranışı değişmez. Yalnız hangup-waiting-user-result yolu skip uygular; reason parametresinin string karşılaştırması yerine `Call` üzerine eklenecek dar bir transient state veya enum reason bayrağı tercih edilecek (uygulamada string compare yerine type-safe bir signal seçilir).

### (B) Awaiting-timeout overwrite guard

Karar: **(B2) DB read önce, in-memory'ye bağla** (Açık Soru #2 cevabı).

`finalizeAwaitingUserResultCodeCall` şu sırayla çalışacak:

```
1) (yeni) cdrService.getByInteractionId(call.getId()) ile DB CDR oku.
2) DB CDR mevcutsa ve userResultCodeId dolu ise, in-memory call.userResultCode
   bu id'ye karşılık gelen UserResultCode ile (ya da doğrudan
   cdr.userResultCodeId alanına bağlama yoluyla) doldurulur.
3) Date completeDate = getAwaitingUserResultCodeCompletionDate(call);
4) processCallResult(call, completeDate);
5) LOG.warn("Timed out awaiting user result code call ...")
```

Adım 2'nin uygulama detayı: (i) `UserResultCodeService` veya benzeri bir DAO ile id → entity çözümü gerekli mi, yoksa yalnız `cdr.userResultCodeId` set edip in-memory `call.userResultCode` null bırakmak yeterli mi sorusu kod yazım sırasında netleşecek. Tercih: in-memory `cdr.userResultCodeId`'yi DB değerine eşitleyip `call.userResultCode` set'ini ek DB lookup gerektirmeyecek şekilde bırakmak; `processCallResult`'ın CDR persist satırı (line 817) `call.userResultCode != null ? .id : null` yapısına dokunulmayacaksa, alternatif olarak persist satırı `cdr.getUserResultCodeId() != null ? cdr.getUserResultCodeId() : (call.getUserResultCode() != null ? .id : null)` şeklinde dar bir guard ile genişletilebilir.

Aynı koruma `dbOperations.insertCallResultToDB(call)` (campaign result tablosu) için de geçerli olmalı. `insertCallResultToDB` in-memory `call`'tan üretiyorsa, in-memory CDR/CallResultCode set'i ile dolaylı olarak doğru değer akar; aksi hâlde campaign result update path'inde de aynı dar guard düşünülecektir.

Force-stopped campaign özel dalı (29.04.2026 hotfix) ile çakışma yoktur (Açık Soru #4 cevabı): force-stopped çağrılar `shouldProcessCallResultOnHangup` true dönerek hangup anında finalize olur ve `awaitingUserResultCodeCalls`'a hiç girmezler.

Hot path etkisi: yalnız awaiting-timeout cleanup yolunda çalışır, çağrı başına bir kez ve cleanup hot path olmadığı için kabul edilebilir maliyet. Mevcut aynı yolda zaten `cdrService.saveOrUpdate(cdr)` çalıştırılıyor.

### (D) Timeout sabit ayrımı (davranış değişikliği yok)

Mevcut `awaitingUserResultCodeCalls` cache `Constants.ORPHANED_CALL_EXPIRE_TIMEOUT` (15 dk) sabitini paylaşıyor. Ancak orphanedCalls, recoverableConnectionLossCalls ve awaitingUserResultCodeCalls semantik olarak farklı lifecycle'lara sahip; aynı sabiti paylaşmaları ileride bir cache'in süresini ayarlamayı diğerlerini de etkilemeden imkânsızlaştırıyor.

Karar: Yeni sabit `Constants.AWAITING_USER_RESULT_EXPIRE_TIMEOUT` eklenir; başlangıç değeri **mevcut 15 dk = 900000 ms** olarak korunur (davranış değişmez). `awaitingUserResultCodeCalls` Guava cache `.expireAfterWrite` ve `clearExpiredAwaitingUserResultCodeCalls` scheduler initialDelay'i bu yeni sabite bağlanır. `clearExpiredAwaitingUserResultCodeCalls` scheduler period'u mevcut `CALLBACK_EXPIRE_TIMEOUT` (1 dk) ile aynı kalır.

Bu refactor:

- Geç gelen webphone result code mesajları zaten cache dışında durable fallback üzerinden yakalandığı için 15 dk değeri yeterli kabul ediliyor (kullanıcı kararı).
- Yeni sabit, ileride awaiting süresini orphan/recoverable cache'lerden bağımsız ayarlama imkânı verir; bu plan kapsamında yalnız sabit ekleme + bağlantı değişikliği yapılır, değer aynı kalır.
- Davranış değişikliği olmadığı için mevcut `WebphoneSubscriberTest`/`TelephonySlotLifecycleRegressionTest` regression senaryoları yeni sabit bağlanmasını da kapsayabilir; ekstra test yazılmaz.

### (C) `shouldSetResultCode=false` fallback boşluğu

Karar: **Sadece `call.getUser()==null` ve user mismatch** (Açık Soru #3 cevabı).

`WebphoneSubscriber.processCallWithResultCodeAndUpdateDNC` davranışı şu hâle getirilecek:

```
for (...connectionDecorator) {
  Call call = findActiveOrRecoverableCall(connectionDecorator, parentId);
  if (call != null) {
    if (call.getUserResultCode() != null) {
      // in-memory zaten dolu — duplicate event veya yarış sonrası ek mesaj.
      // Erken return korunur; fallback denenmez.
      return;
    }
    if (shouldSetResultCode(call, callResultCode)) {
      processActiveCallResultCode(...);
      return;
    }
    // Buraya düşersek: call.getUser() == null veya userId mismatch.
    // Fallback denenmeli; fallback kendi user-match guard'ıyla yanlış agent'ı engeller.
    break;  // for döngüsünden çık ve fallback'a düş
  }
}
CDR cdr = persistDurableResultCodeFallback(callResultCode, null, false);
...
```

Fallback'ın mevcut guard'ları korunur:

- `isResultCodeUserMatchesCdr`: assigned-first, sonra connected user kontrolü.
- `isSuccessfulCdrForUserResultCode`: telephonyRC=10/24 + `callHandlingStartTime != null`.
- `targetCdr.userResultCodeId == null`: zaten dolu CDR overwrite edilmez.

Bu davranışla:

- "Call bulundu, in-memory zaten userResultCode dolu" durumu: mevcut davranış (erken return) korunur, gereksiz DB SELECT yok.
- "Call bulundu, user yok / user mismatch" durumu: fallback denenir, doğru agent eşleşmesi varsa CDR + campaign result tamamlanır.
- "Call bulunamadı" durumu: mevcut davranış (fallback) korunur.

### Test stratejisi (özet)

- Webphone-event/AMI thread arası yarış penceresi için karakterizasyon testi (cache transition senaryosu): bir test, AMI hangup else-branch'i `awaitUserResultCodeAfterHangup` çağırmadan ÖNCE `findActiveOrRecoverableCall` çağrıldığında bile call'ın `awaitingUserResultCodeCalls` veya `calls`'tan birinde bulunabildiğini garanti eder.
- Awaiting-timeout overwrite koruma testi: DB'de `pd_cdr.user_result_code_id` dolu iken `finalizeAwaitingUserResultCodeCall` çalıştığında alan korunur (regression).
- Awaiting-timeout no-result test: DB'de de `user_result_code_id` boş, in-memory `call.userResultCode` boş ise mevcut davranış korunur (telephonyRC ve diğer alanlar yazılır, user_result_code_id null kalır).
- `shouldSetResultCode=false` fallback testi: assigned user yok ama CDR connectedUser ile result code userId eşleşiyorsa fallback CDR'ı günceller.
- `shouldSetResultCode=false` user mismatch testi: result code userId hiçbir CDR user alanı ile eşleşmiyorsa fallback skip eder, CDR null kalır.
- `call.userResultCode != null` fast-path testi: ek fallback denenmez, mevcut davranış korunur.

## Riskler

- **Hot path counter drift**: AMI Reader thread'inde else-branch sıra değişimi yanlış uygulanırsa `callsInProgress`/`activeCalls` çift decrement veya hiç decrement edilmeyebilir. `markTelephonySlotReleased` flag'i bu riski azaltır; testler counter sayımlarını da kapsamalı.
- **Yanlış call'a result code yazımı**: (C) kapsamı genişletilirken fallback'ın user-match guard'ı kalkmamalı. Aksi hâlde yanlış agent'ın wrap-up code'u CDR'a yazılabilir.
- **Awaiting-timeout overwrite guard'ın force-stopped fallback'i etkilemesi**: 29.04.2026 hotfix'inde `forcefullyStoppedCampaignFallback` ayrı bir akış. (B) yalnız in-memory `call.userResultCode == null` iken aktive olduğu için bu akışla çakışmamalı; testlerle teyit edilecek.
- **Ek DB SELECT (B2 uygulanırsa)**: Yalnız awaiting-timeout cleanup yolunda çalışır, çağrı başına bir kez ve hot path değildir; etkisi düşük. Ölçüm için varolan `expired-calls-executor-0` log düzeyi yeterli.
- **Webphone subscriber thread kontamine olma riski**: `shouldSetResultCode=false` fallback yolu `persistDurableResultCodeFallback` zaten subscriber thread'inde çalışıyor; ek thread switch yok.
- **AwaitingUserResultCodeCallRemovalListener davranış değişikliği**: Eğer (B2) seçilirse listener içinde DB read eklenir; cache eviction thread'i (Guava removal) IO hâline gelir. Mitigation: temel iş `expired-calls-executor-0` scheduledExecutor üzerinden tetiklendiği için bu thread bağlamı zaten IO'ya uygun (mevcut `cdrService.saveOrUpdate` da burada çalışıyor).

## TDD Stratejisi

Yeni test sınıfı hedefleri (mevcut yapıya uyum):

- `gbpowerdialer/src/test/java/tests/asterisk/AwaitingUserResultCacheTransitionRegressionTest.java`: AMI thread tarafında else-branch çalışırken `findActiveOrRecoverableCall` her noktada call'ı bulur invariant'ı.
- `gbpowerdialer/src/test/java/tests/asterisk/AwaitingUserResultTimeoutPreserveRegressionTest.java`: awaiting-timeout cleanup, DB CDR'da dolu `user_result_code_id` ve campaign result `user_result_code` alanlarını ezmez; in-memory hâlinde de boş ise telephony alanları yazılır ama user result code alanları null'a yazılmaz.
- `gbpowerdialer/src/test/java/com/powerdialer/redis/WebphoneSubscriberTest.java`: mevcut suite genişletilir; `shouldSetResultCode=false` ile `assignedUser==null/connectedUser match`, user mismatch ve `userResultCode!=null` senaryoları için yeni test methodları eklenir.

Test isimlendirmesi `<Konu><Davranış><TestTipi>Test` standardına uygun. Testler PowerMock gerektirmemeli; mevcut Mockito ve constructor injection ile kurulmalı.

Karakterizasyon testleri belirsiz davranışları kilitler; uygulama testleri beklenen davranışı doğrular.

## Açık Sorular

Tüm açık sorular kapandı (2026-05-07).

- [x] **(A) uygulama tercihi** — Cevap: **(a) Atomic sıra + clearCall skip**. `handleDialerHangup` else-branch sırası `persistHangupSnapshotForDelayedResult → awaitingUserResultCodeCalls.put → releaseTelephonySlotIfNeeded` olarak güncellenir; `clearCall` içinde `reason="hangup-waiting-user-result"` (veya eşdeğer state guard) iken `awaitingUserResultCodeCalls.invalidate` skip edilir. In-flight pencere fiilen kapanır.
- [x] **(B) uygulama tercihi** — Cevap: **(B2) DB read önce, in-memory'ye bağla**. `finalizeAwaitingUserResultCodeCall` içinde `processCallResult`'tan ÖNCE `cdrService.getByInteractionId(call.getId())` çağrısıyla DB'deki `userResultCodeId` okunur; doluysa in-memory `call.userResultCode` (ve gerekirse `cdr.userResultCodeId`) bu değerle bağlanır. Awaiting-timeout cleanup hot path olmadığı için ek tek SELECT kabul edilebilir; campaign result tablosu da aynı in-memory değerden türetildiği için doğal kapsanır.
- [x] **(C) kapsamı** — Cevap: **Sadece `call.getUser()==null` ve user mismatch**. `WebphoneSubscriber.processCallWithResultCodeAndUpdateDNC` içinde aktif call bulunup `shouldSetResultCode=false` döndüğünde, `call.getUserResultCode()!=null` ise erken `return` korunur (in-memory zaten dolu); `call.getUser()==null` veya user mismatch durumlarında durable fallback denenir. Fallback'ın kendi user-match guard'ı (assigned-first, sonra connected) yanlış agent'a yazımı engeller.
- [x] **(B) ile force-stopped fallback etkileşimi** — Cevap: Etkilemez; teyit edildi. Force-stopped çağrılar `shouldProcessCallResultOnHangup` true dönerek hangup anında `processCallResult` üzerinden finalize olur ve `awaitingUserResultCodeCalls`'a girmez. Awaiting-timeout dalı yalnız hangup-waiting-user-result yolundan girilen çağrılar için aktive olduğu için force-stopped fallback ile çakışmaz. Regression testlerinde bu davranış da kilitlenecek.
- [x] **Test kapsam tercihi** — Cevap: Ayrı sınıflar. Yeni testler `AwaitingUserResultCacheTransitionRegressionTest` ve `AwaitingUserResultTimeoutPreserveRegressionTest` adıyla `gbpowerdialer/src/test/java/tests/asterisk/` altında açılır; mevcut `WebphoneSubscriberTest` `shouldSetResultCode=false` fallback kapsamı için yeni method'larla genişletilir.
- [x] **Hotfix önceliği teyidi** — Cevap: **Hotfix**. Plan canlı veri kaybı sınıfını kapattığı için hotfix etiketi ve önceliğiyle ilerlenir; offline Maven test sonrası bir sonraki release window'una çıkar.
- [x] **Awaiting cache timeout süresi ve sabit ayrımı** — Cevap: Yeni sabit `Constants.AWAITING_USER_RESULT_EXPIRE_TIMEOUT` eklenir; başlangıç değeri mevcut 15 dk (900000 ms) olarak korunur; `awaitingUserResultCodeCalls` cache ve `clearExpiredAwaitingUserResultCodeCalls` scheduler bu yeni sabite bağlanır. Geç gelen webphone result code mesajları durable fallback üzerinden yakalandığı için 15 dk yeterli kabul edildi; semantik ayrım ileride bağımsız tuning imkânı sağlar.

## Execution Plan

- [x] Onaylanan planın tüm adımları bu dosyaya işlendi.
- [x] Açık sorular kullanıcı tarafından kapatıldı ve plan güncellendi.
- [x] Mevcut davranışı kilitleyen karakterizasyon testleri yazıldı (yarış penceresi, awaiting-timeout overwrite, `shouldSetResultCode=false`).
- [x] Beklenen davranışı kanıtlayan testler yazıldı.
- [x] (A) `ManagerConnectionDecorator` else-branch sırası ve cache transition atomic'liği değişikliği uygulandı; `Call.markAwaitingUserResultCode/clearAwaitingUserResultCode/isAwaitingUserResultCode` flag'leri eklendi.
- [x] (B2) `finalizeAwaitingUserResultCodeCall` öncesi `cdrService.getByInteractionId` ile DB CDR.userResultCodeId hidrasyonu uygulandı; in-memory `call.userResultCode` ve `cdr.userResultCodeId` synthetic ResultCode (id + finalStatus=true) ile bağlanır.
- [x] (C) `WebphoneSubscriber.processCallWithResultCodeAndUpdateDNC` içindeki `shouldSetResultCode=false` fallback boşluğu kapatıldı; `userResultCode!=null` durumunda erken return korundu, user-yok/mismatch durumunda durable fallback denenir.
- [x] (D) `Constants.AWAITING_USER_RESULT_EXPIRE_TIMEOUT` (15 dk) sabiti eklendi; `awaitingUserResultCodeCalls` cache ve `clearExpiredAwaitingUserResultCodeCalls` scheduler bu sabite bağlandı.
- [x] Hedefli testler yeşile çevrildi.
- [x] Refactor sonrası testler tekrar çalıştırıldı.
- [x] `gbpd-call-flow-performance-guardrails` kontrol listesi (counter drift, hot path IO, log volume) gözden geçirildi ve plan içinde notlandı; AMI hangup hot path'inde yeni IO yok, awaiting-timeout cleanup'ta tek `getByInteractionId` SELECT eklendi (cleanup yolu, hot path değil).
- [ ] Mimari doküman (`docs/architecture/ASTERISK-AMI-AGI.md`) güncellemesi takip görevi olarak bırakıldı; davranış değişikliği yalnız awaiting-cache transition ve timeout cleanup boundary'sinde olduğu için ayrı bir doc-only iş ile yapılabilir.
- [x] History kaydı `docs/history/07.05.2026.md` dosyasına append-only eklendi; yeni gün açılması nedeniyle `docs/history/README.md` ve `docs/README.md` indeksleri güncellendi.

## Performans Etkisi

- Hot path durumu: AMI hangup ve Webphone result code path'leri hot path'tir. Awaiting-timeout cleanup hot path değildir (15 dk Guava expire-listener üzerinden tetiklenir).
- Call/event/tick başına ek maliyet: (A) için yalnız atomic flag/cache put sırası değişimi; ek IO yok. (C) için aktif call bulunmasa bile yapılan tek `getByInteractionId` çağrısının kapsamı yalnız `shouldSetResultCode=false` dalına eklenir; mevcut "active call yok" yoluyla aynı maliyettedir. (B) seçimi (B2) ise awaiting-timeout cleanup başına tek ek SELECT; cleanup yolu hot path değildir.
- IO/scan/lock/serialization/log etkisi: Yeni geniş `synchronized` blok yok. Yeni log mesajları düşük hacimli skip/apply mesajlarıyla sınırlı.
- Azaltım veya ölçüm notu: AMI Reader thread'i tek; cache transition adımları O(1) Guava cache put/invalidate ve atomic flag check'ten oluşur. Webphone subscriber yolu mevcut fallback davranışını korur; yeni dallanma O(1) guard kontrolüdür.

## Doğrulama

Doğrulama öncesi `.codex/TOOL-PATHS.local.properties` okunur; Maven offline çalıştırılır; eksik artifact varsa indirme denenmez.

Hedefli komutlar:

```text
mvn -o -Dtest=WebphoneSubscriberTest test
mvn -o -Dtest=AwaitingUserResultCacheTransitionRegressionTest test
mvn -o -Dtest=AwaitingUserResultTimeoutPreserveRegressionTest test
mvn -o -Dtest=TelephonySlotLifecycleRegressionTest test
mvn -o -Dtest=SaveInitialCdrHangupResultRegressionTest test
```

Toplu hedefli regression:

```text
mvn -o -Dtest=WebphoneSubscriberTest,AwaitingUserResultCacheTransitionRegressionTest,AwaitingUserResultTimeoutPreserveRegressionTest,TelephonySlotLifecycleRegressionTest,SaveInitialCdrHangupResultRegressionTest test
```

Local tool path varsa komuta `-s <settings.xml>` ve `-Dmaven.repo.local=<repo>` eklenecek. AngularJS frontend etkisi yoktur; Karma testleri kapsam dışıdır.

## Kapanış Kriteri

- [ ] Açık sorular kullanıcı tarafından cevaplandı veya `Yok` olarak işaretlendi.
- [ ] Yarış penceresi kapatıldı; webphone result code mesajı her zaman aktif veya awaiting cache'te call'ı bulur.
- [ ] Awaiting-timeout cleanup, DB'deki dolu user result code alanlarını ezmez.
- [ ] `shouldSetResultCode=false` durumunda durable fallback uygun guard'larla denenir.
- [ ] Counter (callsInProgress, activeCalls, ringingCalls, originatedCalls) lifecycle'ı korunur.
- [ ] Planlanan testler çalıştırıldı ve başarılı oldu (veya environment blocker açık raporlandı).
- [ ] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Uygulama tamamlandı (2026-05-07). Üretim kodu değişiklikleri:

- `Constants.java`: `AWAITING_USER_RESULT_EXPIRE_TIMEOUT = 900000` ms sabiti eklendi.
- `Call.java`: `awaitingUserResultCode` transient volatile flag'i ve `markAwaitingUserResultCode`/`clearAwaitingUserResultCode`/`isAwaitingUserResultCode` API eklendi.
- `ManagerConnectionDecorator.java`:
  - `awaitingUserResultCodeCalls` cache ve `clearExpiredAwaitingUserResultCodeCalls` scheduler yeni sabite bağlandı.
  - `handleDialerHangup` else-branch sırası: persistHangupSnapshotForDelayedResult → `markAwaitingUserResultCode + awaitingUserResultCodeCalls.put` → `releaseTelephonySlotIfNeeded`. In-flight pencere kapatıldı.
  - Eski `awaitUserResultCodeAfterHangup` private helper metodu kaldırıldı (atomic transfer doğrudan else-branch'te yapıldığı için artık çağrılmıyordu).
  - `clearCall`: `call.isAwaitingUserResultCode()` iken `awaitingUserResultCodeCalls.invalidate` skip edilir.
  - `processCallResult`: `markBusinessResultFinalized` sonrası `clearAwaitingUserResultCode` çağrılır (clearCall awaiting cache'i normal şekilde invalidate eder).
  - `finalizeAwaitingUserResultCodeCall`: `processCallResult`'tan ÖNCE `hydrateUserResultCodeFromDurableFallback` ile DB CDR.userResultCodeId in-memory `call.userResultCode` ve `cdr.userResultCodeId`'ye bağlanır. Synthetic `ResultCode(id, finalStatus=true)` retry kararını "no retry" yönünde tutar.
- `WebphoneSubscriber.java`: `processCallWithResultCodeAndUpdateDNC` içinde `shouldSetResultCode=false` durumunda `userResultCode!=null` ise erken return; `getUser()==null` veya user mismatch durumunda durable fallback denenir.

Test değişiklikleri:

- Yeni: `tests/ami/AwaitingUserResultCacheTransitionRegressionTest` (2 senaryo).
- Yeni: `tests/ami/AwaitingUserResultTimeoutPreserveRegressionTest` (3 senaryo, `MockitoJUnitRunner.Silent` strictness).
- Genişletildi: `WebphoneSubscriberTest` — 4 yeni senaryo (`call.user==null` + connectedUser match fallback, user mismatch + assignedUser match fallback, hiçbir CDR user'ı match etmiyor → skip, `userResultCode!=null` early return).

Doğrulama:

```text
mvn -o -s C:/Users/GLB90114534/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90114534/.m2/repository -f gbpowerdialer/pom.xml -Dtest=WebphoneSubscriberTest,AwaitingUserResultCacheTransitionRegressionTest,AwaitingUserResultTimeoutPreserveRegressionTest,SaveInitialCdrHangupResultRegressionTest,ConnectionLossBridgedCallRecoveryRegressionTest,InitialCdrOriginationRegressionTest,CallCleanerTelephonyResultRegressionTest test
```

Sonuç: `BUILD SUCCESS`; `Tests run: 54, Failures: 0, Errors: 0, Skipped: 0`. Toplam süre ~35s. Lokal `.codex/TOOL-PATHS.local.properties` üzerinden JDK 8 (Temurin 1.8.0_432) + Maven 3.9.15 offline ile koşturuldu.
