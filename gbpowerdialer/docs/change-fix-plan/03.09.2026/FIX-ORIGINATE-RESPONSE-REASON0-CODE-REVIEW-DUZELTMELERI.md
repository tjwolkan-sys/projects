# FIX — originate-response reason0 branch'i: code-review düzeltmeleri

**Branch:** `fix/originate-response-reason0-overwrites-real-result`
**Tarih:** 03.09.2026
**Kapsam:** Mevcut (commit edilmemiş) düzeltmenin paralel code-review'unda çıkan dört bulgunun
giderilmesi (2 CONFIRMED-HIGH veri kaybı + 2 davranışsal). Ana düzeltmenin kendisi (grace period +
cause-16 guard) korunmuştur; bu plan yalnız o düzeltmenin yan etkilerini kapatır. Dördü de
`develop` ile karşılaştırılarak bu branch'e ait olduğu doğrulanmıştır.

## Arka plan

Ana düzeltme, Asterisk'in `OriginateResponse(Reason=0)` event'inin çalma sonrası
cevapsız/meşgul/iptal durumlarında da geldiğini; kanal kurulmuşsa bunun originate reddi
OLMADIĞINI tespit edip RC 907 yazmayı 10 saniyelik grace period'a erteliyor. Review'da bu
düzeltmenin iki noktada kendi çözmeye çalıştığı hatayı (907'nin gerçek sonucu ezmesi) daha dar
pencerelerde geri getirdiği, ayrıca iki davranışsal kusur taşıdığı bulundu.

---

## Sorun 1 — Fallback'in TRC yazımı finalize kontrolüyle atomik değildi

**Kök neden:** `finalizeOriginationFailFallback` giriş kapılarında
`isBusinessResultFinalized()` kontrolü yapıp ARDINDAN `setTelephonyResultCode(907)` yazıyordu.
Finalize latch'i (`markBusinessResultFinalized`) ise `processCallResult` İÇİNDE alınır. Hangup
thread'i latch'i alıp `returnUserToACDEngine` (Redis round-trip) ile `persistFinalCdr`'ın TRC'yi
okuduğu (`:1335`) an arasındayken — onlarca ms'lik gerçek I/O penceresi — fallback araya girip
RC 907 yazabiliyordu. Yani branch'in düzeltmeye çalıştığı hata daha dar bir pencerede geri
geliyordu. Alan `volatile` bile olmadığı için happens-before kenarı da yoktu.

**Neden "latch'i önce al" işe yaramaz:** `markBusinessResultFinalized()` one-shot. Fallback
latch'i tüketirse `processCallResult` içindeki ikinci çağrı `false` dönüp çağrıyı hiç finalize
etmez; ayrıca persist-başarısız `rollbackBusinessResultFinalized()` yolu bozulur.

**Çözüm:** `Call` üzerine, kontrol ile yazımı AYNI monitör altında yapan CAS eklendi:

```java
public synchronized boolean setTelephonyResultCodeIfNotFinalized(TelephonyResultCode trc)
```

Latch BURADA TÜKETİLMEZ; yalnız okunur. Diğer one-shot latch'ler de (`markBusinessResultFinalized`,
`markTelephonySlotReleased`) aynı `Call` monitöründe `synchronized` olduğu için kilit disiplini
bütünlüklüdür. Fallback yarışı kaybederse TRC'ye dokunmadan `INFO` ile çıkar.

Ek olarak `Call.telephonyResultCode` alanı `volatile` yapıldı: alan AMI eventWorker,
`scheduleService`, Redis subscriber, login/logout handler, `CampaignStopJob` ve cache-expiry
thread'lerinden yazılıp 50'den fazla noktadan okunuyor; senkronizasyonsuz düz alanda yazımların
görünürlüğü garanti değildi. Bu, branch'ten ÖNCE de mevcut latent bir sorundu — fallback yeni bir
yazıcı thread (scheduler) eklediği için maruziyeti arttığından birlikte ele alındı.
`Call` JPA-mapped bir entity DEĞİLDİR (`@Entity`/`@Table` yok), dolayısıyla persistence yan etkisi
yoktur; sınıfta `user`, `answeredByUser`, `businessResultFinalized` vb. zaten aynı deseni kullanıyor.

**Not:** `volatile` yalnız görünürlüğü çözer, atomikliği değil. Yarışı kapatan şey CAS'tir; ikisi
birbirini tamamlar.

## Sorun 2 — `hasErrorCode` genişletmesi ikinci çağıranı da değiştiriyordu

**Kök neden:** `ASTERISK_ERROR_CODES` yalnız ITU cause'larını (0..127) tutar; 907 orada yoktur.
Bu yüzden RC 907 taşıyan çağrıda `shouldFinalizeOnHangup` `false` dönüyor ve hangup gerçek cause'u
yazamıyordu. Ana düzeltme bunu `hasErrorCode` İÇİNE 907 ekleyerek çözmüştü — ancak `hasErrorCode`'un
İKİNCİ bir çağıranı var: `shouldProcessCallResultOnHangup` (`:1123`), koşul
`hasErrorCode(call) && !isPreviewCampaign()`.

**Etki:** preview OLMAYAN + cevaplanmış + RC 907 taşıyan + user result code BEKLEYEN çağrıda
`shouldProcessCallResult` `false → true` dönüyor; çağrı awaiting-user-result kolundan
(`markAwaitingUserResultCode` + `awaitingUserResultCodeCalls`) çıkıp doğrudan finalize ediliyor ve
**agent'ın disposition'ı sessizce kayboluyor**. RC 907'nin CDR id'si (79) ne
`WEBPHONE_RESULT_CODE_RECOVERABLE_TRC_IDS` ne de durable fallback'in başarı kümesinde ({10, 24})
olmadığı için kurtarma da yok.

**Çözüm:** `hasErrorCode` orijinal haline döndürüldü; 907 kontrolü yalnız ihtiyaç duyan yere,
`shouldFinalizeOnHangup`'a taşındı. `Constants.HANGUP_OVERWRITABLE_DIALER_ERROR_CODES` sabiti ve
gerekçe javadoc'u aynen korundu — yalnız tüketildiği yer daraldı. Amaçlanan düzeltme (hangup'ın
907'yi gerçek cause ile düzeltebilmesi) korunur, `:1123` üzerindeki istenmeyen etki sıfırlanır.

---

## Sorun 3 — FCT retry `channel`'ı sıfırlamıyordu

**Kök neden:** `tryFctRetryWithNextCallerId` aynı Call nesnesini yeniden originate ederken
`telephonyResultCode`, `startDate`, `originationDate`, `callerId` alanlarını sıfırlıyor ama
`channel`'ı sıfırlamıyordu (repo genelinde `setChannel(null)` hiç yoktu). `develop`'ta bu
zararsızdı: `channel`'ı okuyan tek yer `clearPreviousRetryDateForOriginatedCall` idi ve orada
bayat değer doğru davranış üretiyordu. Bu branch'in eklediği `isChannelEstablished`
(`channel != null || startDate != null`) alanı **karar verici** hale getirdi: 2. denemenin GERÇEK
originate reddi (kanal hiç kurulmamışken) bayat `channel` yüzünden "kanal kurulmuş" sayılıp
gereksiz yere 10 saniyelik grace period'a giriyordu.

**Çözüm:** `setStartDate(null)` yanına `call.setChannel(null)` eklendi. Sıralama korundu —
`decrementRingingCalls` `getStartDate()` okuduğu için reset bloğu o çağrıdan SONRA kalmalı.

## Sorun 4 — inline kaçış yolu iddia ettiği pencereyi kapatmıyordu

**Kök neden:** `runOriginationFailFallback` "eventWorker kapalıysa iş düşmesin" diye yazılmıştı
ama üç kusuru vardı:

1. `isShutdown()` ön kontrolü **TOCTOU**: kontrolden SONRA shutdown olursa
   `blockingPutRejectedHandler` görevi **sessizce düşürür** (`executor.isShutdown()` -> `return`)
   ve iş kaybolur; çağrı 1 saatlik cache expiry'sine kalır.
2. `RejectedExecutionException` catch'i **ölü kod**: bu handler exception ATMAZ.
3. Kuyruk doluysa handler `getQueue().put()` ile submitter'ı **BLOKLAR**. Submitter tek thread'li
   `scheduleService` olduğu için bu, üç housekeeping job'ını ve TÜM preview timeout'larını
   kilitler — üstelik tam da origination hatalarının kümelendiği AMI backlog anında.

**Çözüm:** görev `AtomicBoolean` one-shot guard ile idempotent yapıldı (iki yol yarışırsa finalize
iki kez çalışmaz). Submit her zaman `execute()` iledir; blocking-put'a hiç düşmemek için kapasite
ÖNCEDEN kontrol edilir (`remainingCapacity() > 0`). Kuyruk doluysa veya havuz kapalıysa iş inline
koşulur; `execute()` kabul ettikten sonra kapanma yarışı olursa one-shot guard sayesinde ikinci bir
`task.run()` ya hiç çalışmamış işi kurtarır ya da zaten çalışmışsa hemen döner.

**İlk denemede yapılan hata ve düzeltilmesi:** çözümün ilk hali görevi `getQueue().offer(task)` ile
DOĞRUDAN kuyruğa koyuyordu. Bu YANLIŞTIR: `ThreadPoolExecutor` core thread'ini yalnız `execute()`
içinde (lazy) yaratır ve üretimde `prestartCoreThread` çağrılmaz. Havuz o ana dek hiç iş almamışsa
`poolSize=0` olur ve kuyruğa konan iş HİÇ ÇALIŞMAZ — çağrı finalize edilmeden 1 saatlik cache
expiry'sine kalırdı. Bu, izole bir JDK 8 probe'u ile birebir doğrulandı
(`offered=true poolSize=0`, 2 saniyede çalışmadı). Mevcut blocking testi worker'ı önceden
`execute` ile ısıttığı için kusuru göremiyordu; havuzu bilerek SOĞUK bırakan
`shouldRunFallbackWhenEventWorkerHasNeverExecutedAnyTaskBefore` testi eklendi ve `offer` sürümüne
karşı kırmızı olduğu doğrulandı.

---

## İKİNCİ REVIEW TURU (commit 269097e8 sonrası)

Commit atıldıktan sonra yapılan paralel code-review'da, grace period'ın KENDİ getirdiği üç kenar
durum bulundu. Üçü de `develop` ile karşılaştırılarak bu branch'e ait olduğu doğrulandı.

## Sorun 5 — İkinci origination-fail birinci timer'ı izlenemez bırakıyordu

**Kök neden:** `Call` tek bir `originationFailFallbackFuture` alanı tutar ve setter eskisini
kontrol etmez. Ancak `handleOriginationFail`'e aynı `callId` ile İKİ bağımsız giriş vardır ve
aralarında dedup yoktur:
- `handleOriginateResponse` — Asterisk `OriginateResponse` event'i (Reason=0)
- `runOriginationFailTask` — Originate action'ın `ManagerError` cevabı

Grace dalı çağrıyı `calls` cache'inden bilerek DÜŞÜRMEDİĞİ için (geciken hangup bulabilsin diye)
ikinci giriş de aynı çağrıya ulaşıp ikinci timer kurar. Birinci timer'ın referansı üzerine yazılır
ve o timer ARTIK İPTAL EDİLEMEZ hale gelir.

**Failure senaryosu:** ManagerError t=0.1s'de timer A kurar, OriginateResponse t=0.2s'de timer B
kurar (A kayıp), t=0.3s'de cause 34 hangup FCT retry'i tetikler, `originateCall` yalnız B'yi iptal
eder. t=10.1s'de A ateşler; çağrı o anda canlı ve 2. denemesi çalıyordur. Fallback'in tüm kapıları
geçilir (FCT retry `markTelephonySlotReleased` çağırmaz, `originateCall` çağrıyı cache'e geri koyar)
ve CANLI çağrıya RC 907 yazılıp finalize edilir. Bu, `cancelOriginationFailFallbackFuture`'ın
önlemek için yazıldığı hatanın ta kendisidir.

**Çözüm:** `scheduleOriginationFailFallback` başına `call.cancelOriginationFailFallbackFuture()`.
Metot null-safe ve idempotenttir.

**Atomiklik notu:** cancel+set atomik değildir, ancak her iki fail yolu da aynı TEK THREAD'li
`eventWorker` üzerinden geçtiği için serileşir; timer sızması yoktur (doğrulandı).

## Sorun 6 — Forceful campaign stop beş kapının hiçbirini kapatmıyordu

**Kök neden:** Fallback'in varsayımı "çağrı başka yoldan bitirildiyse bir kapı beni durdurur"dur.
Bu yalnız `processCallResult` üzerinden giden yollar için geçerlidir.
`CampaignStopJob.clearCallAndSaveResultToDb` o yoldan GİTMEZ: `clearCall` + TRC 902 +
`CallCleaner.completeCall` + `insertCallResultToDB` + `saveCDR` yapar. `completeCall` ise yalnız
`setCompleteDate` + `callerIdManager.onCallComplete` çağırır.

Sonuç: `markBusinessResultFinalized` YOK (kapı 1 açık), `markAwaitingUserResultCode` YOK (kapı 2),
`markTelephonySlotReleased` YOK (kapı 3 — `releaseTelephonySlotIfNeeded` yerine düz `clearCall`),
çalan çağrı olduğu için agent kapısı da açık (kapı 4). Son kapı (`getCallForHangup`) non-preview'da
`calls.invalidate` sayesinde kapanır, ancak PREVIEW kampanyada + `userResultCode == null` iken
`clearCall` cache'i bilerek invalidate etmediği için O DA AÇIK kalır.

**Failure senaryosu (preview + forceful stop):** t=1s grace timer kurulur, t=3s kampanya forceful
stop edilir (TRC 902, CDR persist), t=11s fallback tüm kapıları geçip 902'yi RC 907 ile ezer;
`insertCallResultToDB` + `saveCDR` İKİNCİ kez koşar, `doCampaignDecrementationsOnHangup` durmuş
kampanyanın sayaçlarına ikinci kez uygulanır, `failDate` 11 saniye eskidir.

**Çözüm:** `finalizeOriginationFailFallback`'e ikinci kapı olarak `call.getCompleteDate() != null`.
Bu kapı kod tabanında ZATEN YERLEŞİK desendir — `finalizeRecoverableConnectionLossCall` ve
`finalizeAwaitingUserResultCodeCall` ikisi de aynı kontrolü yapar; fallback'teki eksiklik onlarla
hizalandı. Tek satır, `completeCall`'dan geçen TÜM yolları kapatır: forceful stop,
`UserLoginLogoutHandler.finishCall` (logout/orphan), `CallCleaner` expiry.

**Meşru vakayı engelliyor mu?** Hayır (doğrulandı). `setCompleteDate` yazıcıları tarandı:
`RetryStrategy` -> `completeCall` yolu `runCallResultSideEffects` içindedir, yani latch KAPANDIKTAN
SONRA çalışır. Grace period içindeki (henüz finalize olmamış) çağrıda `completeDate` set olamaz.
`cancelCall`'ın üç çağıranı (LSC bloke, LSC timeout, callerId yok) originate YAPILMADAN ÖNCE
çalışır; kanal yoktur, grace period'a hiç girilmez.

## Sorun 7 — Kendi çözümümüzdeki TOCTOU (üçüncü deneme)

Bu maddenin üç denemesi vardır; süreç şeffaflığı için tamamı kayda geçirilmiştir.

**Deneme 1 — `getQueue().offer()`:** YANLIŞ. `ThreadPoolExecutor` core thread'ini yalnız
`execute()` içinde LAZY yaratır ve üretimde `prestartCoreThread` çağrılmaz. Havuz hiç iş almamışsa
`poolSize=0` olur ve kuyruğa konan iş HİÇ ÇALIŞMAZ. İzole JDK 8 probe'u ile doğrulandı
(`offered=true poolSize=0`, 2 saniyede çalışmadı).

**Deneme 2 — `remainingCapacity() > 0` + `execute()`:** TOCTOU. Kontrol ile `execute` arasında AMI
reader kuyruğu doldurabilir; `blockingPutRejectedHandler` reddetmez, `getQueue().put()` ile
submitter'ı BLOKLAR. Submitter tek thread'li `scheduleService` olduğundan bloklanırsa TÜM preview
timeout'ları ve üç cache expiry job'ı (özellikle awaiting-user-result 15dk kurtarması) durur.

**Deneme 3 (mevcut) — `prestartCoreThread()` + `getQueue().offer()`:** iki tuzağı birden kapatır.
Üretim ayarında (core=1, max=1, blocking-put handler) izole probe ile doğrulandı:
`prestartCoreThread()=true poolSize=1`, soğuk havuzda `offer` sonrası iş ÇALIŞTI, ikinci prestart
`false` döndü (idempotent, fazla thread yok), kuyruk dolu iken `offer` 0 ms'de reddetti (bloklamadı).

---

## Bu planın kapsamı DIŞINDA bırakılanlar (review'da çıkan diğer bulgular)

Bunlar bilinçli olarak yapılmadı; ayrı karar/iş kalemi:

1. **Grace period 10sn** — ölçüm p90 1.9sn / max 4.6sn. Trunk düşerse N eşzamanlı hata N slotu 10sn
   tutar; N >= `concurrentLines` ise kampanya 10sn durur. **Önerilen:** 5sn (hâlâ max'ın ~2 katı).
   Kullanıcı kararına bırakıldı.
2. **`cancelOriginationFailFallbackFuture` javadoc'u yanlış** — "finalize edildiğinde de çağrılır"
   diyor; tek çağıran `originateCall`.
3. **Yetim javadoc** — `isChannelEstablished`'ı anlatan blok yanlış metodun üstünde.
4. **Yeni `LOG.warn`** — fallback'in kendi tasarımı gereği her geç hangup'ta tetiklenir;
   INFO daha uygun.
5. **Pre-existing (`develop`'tan gelir, dokunulmadı):** `getId() == TELEPHONY_RC_ORIGINATE_ACTION_ERROR`
   id/code karışımı (907 bir `code`; ilgili satırın id'si 79) ve `handleDialerHangup` içindeki
   korumasız `Integer.parseInt`.

---

## Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `model/Call.java` | `telephonyResultCode` → `volatile`; `setTelephonyResultCodeIfNotFinalized` CAS eklendi |
| `engine/asterisk/ami/ManagerConnectionDecorator.java` | `hasErrorCode` eski haline döndü; 907 kontrolü `shouldFinalizeOnHangup`'a taşındı; fallback CAS kullanıyor; FCT retry `setChannel(null)`; `scheduleOriginationFailFallback` eski timer'ı iptal ediyor; `finalizeOriginationFailFallback`'e `getCompleteDate()` kapısı; `runOriginationFailFallback` `prestartCoreThread()` + bloklamayan `offer` + one-shot guard; `java.util.concurrent` import sırası düzeltildi |
| `test/.../OriginateResponseFailureEstablishedCallRegressionTest.java` | 11 yeni test (17 → 28); `baseCall` gövdesi `seedCall(Call)` olarak ayrıldı |

## Test özeti

RED/GREEN doğrulaması yapıldı — düzeltmeler tek tek geri alındığında ilgili testler kırmızıya döndü:

- `shouldStillWaitForUserResultCodeWhenAnsweredCallCarriesOriginateActionError` — sorun 2'yi kilitler
- `shouldNotOverwriteRealResultWhenCallGetsFinalizedDuringGracePeriod` — sorun 1'i kilitler.
  Yarış, giriş kapıları GEÇİLDİKTEN SONRA / TRC yazımından ÖNCE latch'i alan bir `Call` alt sınıfı
  ile gerçek sıralamasında kurulur (ilk denemede latch'i baştan set etmek yalnız mevcut erken
  return'ü ölçüyordu, CAS'i değil — test buna göre düzeltildi).
- `shouldAllowFallbackToWriteResultWhenCallIsNotFinalized` — CAS'in ters yönü; düzeltmenin
  fallback'i tamamen devre dışı bırakmadığını doğrular.
- `shouldClearChannelOnFctRetrySoNextGenuineFailureIsNotTreatedAsEstablished` — sorun 3'ü kilitler.
- `shouldNotBlockSchedulerThreadWhenEventWorkerQueueIsFull` — sorun 4'ün blocking-put kolunu
  kilitler; düzeltmesiz koşu 2.8sn yerine 7.7sn sürüyor (submitter thread fiilen bloklanıyor).
- `shouldRunFallbackInlineWhenRealWorkerIsShutdownAndSilentlyDropsTask` — gerçek
  `ThreadPoolExecutor` + gerçek `blockingPutRejectedHandler` ile kapalı-worker kolu. NOT: bu test
  eski kodda da geçer (eski `isShutdown()` kontrolü zaten-kapalı vakayı yakalıyordu); asıl
  kapatılan TOCTOU yarışı deterministik olarak test edilemediği için kod incelemesiyle
  gerekçelendirilmiştir.
- `shouldCancelPreviousFallbackWhenSecondOriginationFailArrivesForSameCall` — sorun 5'i kilitler.
- `shouldSkipFallbackWhenCallWasAlreadyCompletedByForcefulCampaignStop` — sorun 6'yı kilitler.
- `shouldNotBlockWhenWorkerQueueIsFullAndWorkerIsRunning` — sorun 7'nin bloklamama invariantını
  korur. NOT: bu test eski kodda da geçer; TOCTOU bir yarış penceresi olduğu için deterministik
  tetiklenemez. Yarışın kapandığı, üretim ayarındaki izole JDK 8 probe'u ile doğrulanmıştır
  (soğuk havuzda prestart+offer işi çalıştırdı; kuyruk dolu iken offer 0 ms'de reddetti).
- `shouldRunFallbackWhenEventWorkerHasNeverExecutedAnyTaskBefore` — SOĞUK havuzda (poolSize=0)
  fallback'in gerçekten çalıştığını kilitler. Kendi ilk çözümümüzün (`getQueue().offer`) lazy
  core-thread kusurunu yakalar; o sürüme karşı kırmızı olduğu doğrulandı.

Komutlar (offline, JDK 8):

```
mvn -o -f gbpowerdialer/pom.xml test -Dtest=OriginateResponseFailureEstablishedCallRegressionTest
  → 28/28 GREEN

mvn -o -f gbpowerdialer/pom.xml test \
  -Dtest='*Ami*,*Hangup*,*Originate*,*AwaitingUser*,*Cdr*,*Fct*,*Webphone*,*Acd*,*Preview*,*Drain*,*Worker*,*Routing*'
  → 223 test, 1 hata (pre-existing baseline)
```

**Pre-existing baseline hatası:** `UnmappedHangupCauseFallbackRegressionTest.
shouldFallbackToExpiredClearingWhenHangupCauseIsNotNumeric` — `handleDialerHangup:956`
`Integer.parseInt` korumasız çağrılıyor, sayısal olmayan cause `NumberFormatException` atıyor
(dosyada güvenli `parseHangupCause` helper'ı zaten var ama bu satırda kullanılmıyor). Branch
değişiklikleri stash'lenip `develop` üzerinde koşularak bu hatanın **bu branch'ten kaynaklanmadığı**
doğrulandı. Ayrı bir iş kalemi.
