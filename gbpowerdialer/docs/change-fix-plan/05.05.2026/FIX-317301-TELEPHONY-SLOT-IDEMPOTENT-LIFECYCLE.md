# FIX Telephony Slot Idempotent Lifecycle Change/Fix Planı

## Bağlam

Üretimde bazı kampanyalarda `concurrentLines` limitine normalden kolay ulaşıldığı ve bu yüzden yeni çağrı çıkmadığı gözlemlendi. İlk incelemede originate kararının `activeCalls` ile değil, `Campaign.canOriginateMoreCalls()` içindeki `callsInProgress` sayımıyla verildiği görüldü.

Son commit geçmişi ve canlı davranış birlikte değerlendirildiğinde riskli sınıf şudur: Hangup veya çevresel sonlanma gelmiş olmasına rağmen çağrı Webphone user result code beklediği için `processCallResult` çalışmıyor; bu nedenle `removeCallInProgress`, `decrementActiveCalls`, `campaign.clearCall` ve call cache cleanup adımları gecikiyor. Bu gecikme telephony slot'u gerçekte bitmiş çağrıların concurrent line ve active call sayaçlarını şişirmesine yol açabiliyor.

04.05.2026 tarihinde yapılan AMI connection loss bridged call recovery geliştirmesi henüz canlıda olmadığı için mevcut canlı semptomun doğrudan sebebi değil. Ancak aynı lifecycle ayrımı yapılmadan canlıya alınırsa, AMI loss sonrası recoverable çağrıların da concurrent slot davranışını etkileyebileceği için bu plan aynı çözüm içinde AMI loss ve Webphone result bekleme akışlarını birlikte ele alır.

## İş Etiketi

fix

## Öncelik

hotfix

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/model/Call.java`
- `gbpowerdialer/src/main/java/com/powerdialer/model/Campaign.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionDecorator.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallOriginator.java`
- `gbpowerdialer/src/main/java/com/powerdialer/redis/WebphoneSubscriber.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/CallCleaner.java`
- AMI, Webphone ve counter regression testleri

## Mevcut Davranış

- `Campaign.canOriginateMoreCalls()` yalnız `getCallsInProgressCount() < concurrentLines` sonucuna bakıyor.
- `callsInProgress` originate başarılı olduğunda artıyor, ancak yalnız terminal finalize/cleanup path'lerinde düşüyor.
- `activeCalls` müşteri answer event'i ile artıyor, hangup finalize path'i ile düşüyor.
- Hangup geldiğinde call user result code bekliyorsa `shouldProcessCallResultOnHangup` false dönebiliyor.
- Bu durumda `persistHangupSnapshotForDelayedResult` CDR snapshot yazıyor fakat call telephony slot'tan çıkarılmıyor.
- Webphone result code gelmezse, geç gelirse veya user mismatch nedeniyle işlenmezse call `calls` cache'inde ve `callsInProgress` içinde timeout'a kadar kalabiliyor.
- Bu timeout canlı contract'ta `CALL_EXPIRE_TIMEOUT` ile 1 saate kadar uzayabiliyor.
- Aynı call'a birden fazla event geldiğinde counter decrement ve business finalization idempotent olarak ayrılmadığı için drift riski var.

## Beklenen Davranış

`callsInProgress` ve `activeCalls` yalnız telephony lifecycle'ı temsil etmeli:

- Ringing devam ediyorsa çağrı concurrent slot tüketir.
- Müşteri veya agent konuşması devam ediyorsa çağrı concurrent slot tüketir.
- AMI connection loss nedeniyle çağrının canlı olup olmadığı bilinmiyorsa bounded recovery süresi boyunca slot korunur.
- Hangup geldiği ve telephony end kanıtlandığı anda concurrent slot bırakılır.
- Hangup sonrası yalnız Webphone user result code bekleyen çağrı artık `callsInProgress`, `activeCalls`, `ringingCalls` veya `originatedCalls` üzerinde yük oluşturmaz.
- Business result finalize süreci ayrı pending state üzerinden devam eder.
- Duplicate hangup, duplicate Webphone result veya timeout cleanup aynı counter'ı ikinci kez değiştirmez.

## Kod Okuma Bulguları

- `Campaign.getCallsInProgressCount()` set size döndürür; gate burada.
- `Campaign.hasCallsInProgress()` ise `originatedCalls.size() > 0` kullanır ve stop job bekleme sinyalidir.
- `ManagerConnectionDecorator.doCampaignDecrementationsOnHangup()` hem `removeCallInProgress` hem `decrementActiveCalls`/`decrementRingingCalls` yapar; ancak sadece `processCallResult` çalışırsa devreye girer.
- `ManagerConnectionDecorator.clearCall()` call originator queue, campaign originated cache ve active call cache temizliği yapar.
- `persistHangupSnapshotForDelayedResult` CDR'ı korur fakat runtime telephony slot'unu bırakmaz.
- `WebphoneSubscriber` aktif call'ı yalnız `connectionDecorator.getCalls()` içinde arar; pending result için ayrı runtime cache yoktur.
- 28.04.2026 ve 30.04.2026 düzeltmeleri CDR korunumu açısından doğru yönde, fakat runtime slot lifecycle ayrımını yapmıyor.
- 04.05.2026 AMI recovery çalışması ayrı `recoverableConnectionLossCalls` fikrini ekledi; ancak telephony slot ve business result lifecycle ayrımı olmadan canlıya alınırsa bounded slot tutma davranışı ayrıca netleştirilmeli.

## Root Cause / Değişiklik Gerekçesi

Root cause, telephony lifecycle ile business result lifecycle'ın aynı runtime state üzerinden yönetilmesidir. Hangup geldiğinde telephony çağrı bitmiş olabilir; fakat user result code bekleme sebebiyle business finalize henüz bitmemiştir. Mevcut kod bu iki aşamayı ayırmadığı için business result bekleyen call, telephony slot'u hâlâ doluymuş gibi `callsInProgress`, `originatedCalls` ve bazen `activeCalls` üzerinde kalır.

Bu sınıfı ek decrement ile yamamak doğru değildir. Counter geçişlerinin tek owner boundary'de, idempotent ve lifecycle state'e bağlı olması gerekir.

## Çözüm Yaklaşımı

### 1. Telephony slot release idempotency

`Call` üzerinde transient bir telephony release state'i eklenecek.

Önerilen contract:

- `markTelephonySlotReleased()` ilk çağrıda `true`, sonraki çağrılarda `false` döner.
- `isTelephonySlotReleased()` gözlem amaçlı kullanılabilir.
- Bu state DB entity contract'ına yazılmayacak; runtime lifecycle içindir.

`ManagerConnectionDecorator` veya uygun call lifecycle owner içinde tek method oluşturulacak:

```java
releaseTelephonySlotIfNeeded(Call call, String reason)
```

Bu method ilk release çağrısında:

- `campaign.removeCallInProgress(call)`
- `decrementActiveCalls` veya `decrementRingingCalls`
- `decrementInCallsSize` gerektiğinde
- `call.getCallOriginator().clearCall(call)`
- `campaign.clearCall(call)`
- realtime ringing cleanup
- `CallUpToDateKeeper.unregisterCall(call)`

işlemlerini yapacak. İkinci kez çağrılırsa hiçbir counter değişmeyecek.

### 2. Business finalization idempotency

`Call` üzerinde ikinci bir transient state eklenecek:

- `markBusinessResultFinalized()`
- `isBusinessResultFinalized()`

Bu state:

- callsMade/result code counters
- retry/callback/DNC
- campaign result update
- final CDR save

akışlarının duplicate çalışmasını engellemek için kullanılacak.

### 3. User result bekleyen call'ları ayrı pending cache'e taşıma

Hangup geldiğinde `shouldProcessCallResultOnHangup` false ise:

1. Telephony result code call'a set edilir.
2. `persistHangupSnapshotForDelayedResult` CDR hangup snapshot'ını yazar.
3. `releaseTelephonySlotIfNeeded(call, "hangup-waiting-user-result")` çalışır.
4. Call aktif `calls` cache'inden çıkarılır.
5. Call yeni bounded cache'e taşınır:

```java
awaitingUserResultCodeCalls
```

Bu cache `ORPHANED_CALL_EXPIRE_TIMEOUT` veya ayrıca onaylanacak daha uygun kısa bir timeout ile sınırlı tutulur. Bu planın ilk önerisi mevcut 15 dakikalık `ORPHANED_CALL_EXPIRE_TIMEOUT` contract'ını kullanmaktır.

### 4. Webphone lookup sırasını genişletme

`WebphoneSubscriber` result code geldiğinde şu sırayla call aramalı:

1. `connectionDecorator.getCalls()`
2. `connectionDecorator.getAwaitingUserResultCodeCalls()`
3. `connectionDecorator.getRecoverableConnectionLossCalls()` yalnız güvenli/kanıtlı senaryoda
4. DB durable fallback

Pending cache'ten bulunan call için user result code set edilir, business finalization yapılır, telephony counter release tekrar denenir ama idempotent flag nedeniyle ikinci kez decrement yapılmaz.

### 5. AMI connection loss ayrımı

AMI connection loss için tek kural uygulanmayacak:

- Bridge kanıtı olmayan veya telephony başlamamış çağrılar mevcut safe cleanup davranışında kalır.
- Bridge kanıtı olan ve AMI loss sebebiyle hâlâ canlı olabilecek çağrılar `recoverableConnectionLossCalls` içinde bounded süre tutulur; slot korunur.
- Reconnect sonrası gerçek hangup gelirse slot o anda bırakılır ve user result gerekiyorsa `awaitingUserResultCodeCalls` içine taşınır.
- Webphone result tek başına telephony end kanıtı sayılmayacak; hangup snapshot veya timeout yoksa recoverable call'ın slot'u erken bırakılmayacak.
- Timeout dolarsa idempotent cleanup ile slot bırakılır ve AMI connection loss fallback sonucu final yazılır.

### 6. `originatedCalls` anlamını netleştirme

`originatedCalls`, stop job açısından "telephony tarafında hâlâ beklenmesi gereken çağrı var mı?" sinyaline geri çekilecek. Hangup sonrası yalnız user result code bekleyen call burada kalmayacak. Business result pending state, `awaitingUserResultCodeCalls` ile izlenecek.

## Riskler

- Telephony slot erken bırakılırsa gerçek canlı çağrı varken yeni çağrı çıkarılabilir.
- Slot geç bırakılırsa concurrent line yine şişer.
- Duplicate event akışında idempotency doğru kurulmazsa `activeCalls` negatif/drift olabilir.
- Webphone result pending cache'ten yanlış call'a bağlanırsa user result code yanlış kayda yazılabilir.
- User ACD idle dönüşü telephony release ile yanlış bağlanırsa aynı agent'a erken çağrı atanabilir.
- Stop/pause job, `originatedCalls` anlamı değiştiği için gerçek telephony call varken erken tamamlanmamalı.
- AMI connection loss recovery ile delayed user result cache'i birbirini çift finalize etmemeli.

## TDD Stratejisi

Önce dar regression testleri yazılacak:

- Hangup user result beklediğinde telephony slot release olur, `callsInProgress` ve `activeCalls` düşer.
- Aynı call'a duplicate hangup geldiğinde counter ikinci kez düşmez.
- Webphone result pending cache'teki call'ı finalize eder ve telephony counter'ı tekrar düşürmez.
- Webphone result duplicate gelirse business finalization ikinci kez çalışmaz.
- AMI connection loss bridge kanıtı olan call slot'u bounded recovery süresince korur.
- AMI connection loss sonrası gerçek hangup gelirse slot bırakılır ve user result pending'e taşınır.
- AMI connection loss bridge kanıtı olmayan call mevcut 75 cleanup davranışını korur.
- Stop job, hangup sonrası yalnız user result bekleyen call sebebiyle gereksiz beklemez.

Mevcut testlerden özellikle şu sınıflar korunacak veya genişletilecek:

- `SaveInitialCdrHangupResultRegressionTest`
- `ConnectionLossBridgedCallRecoveryRegressionTest`
- `WebphoneSubscriberTest`
- `CallOriginatorTest`

## Açık Sorular

Yok.

## Execution Plan

- [x] Mevcut canlı semptom, commit geçmişi ve runtime counter owner'ları analiz edildi.
- [x] Plan geliştirici tarafından onaylandı.
- [x] Mevcut drift davranışını gösteren regression testleri yaz.
- [x] `Call` üzerinde telephony slot release ve business finalization idempotency state'lerini ekle.
- [x] `releaseTelephonySlotIfNeeded` owner method'unu ekle ve mevcut finalize/cleanup path'lerinde davranış değiştirmeden kullan.
- [x] Hangup sonrası user result bekleme path'ini `awaitingUserResultCodeCalls` cache'ine taşı.
- [x] Webphone result lookup sırasını active, awaiting-result, recoverable ve DB fallback olacak şekilde güncelle.
- [x] AMI connection loss recovery path'ini telephony end kanıtı ve bounded recovery kurallarına göre uyumla.
- [x] `originatedCalls` cleanup davranışını telephony-open contract'ına göre doğrula.
- [ ] Duplicate event ve timeout senaryoları için idempotency regression testlerini yeşile çevir.
- [x] Hedefli offline Maven testlerini çalıştır.
- [x] Gerekirse mimari dokümanları current behavior olarak güncelle.
- [x] Plan ve history kayıtlarını append-only güncelle.

## Performans Etkisi

- Hot path durumu: AMI hangup, Webphone result ve cleanup path'leri hot path'tir.
- Call/event/tick başına ek maliyet: O(1) atomic/volatile flag check ve O(1) cache lookup.
- IO/scan/lock/serialization/log etkisi: Yeni DB query normal active/pending path'e eklenmemeli. DB fallback yalnız call memory cache'lerinde bulunamadığında çalışmalı. Tam koleksiyon scan veya geniş lock eklenmemeli.
- Azaltım veya ölçüm notu: Idempotent state call objesi üzerinde tutulacağı için event başına maliyet düşük kalır. Pending cache Guava cache ile bounded olmalı; timeout cleanup idempotent olmalı.

## Doğrulama

Doğrulama öncesi `.codex/TOOL-PATHS.local.properties` okunacak. Maven offline çalıştırılacak.

Hedefli komut:

```powershell
mvn -o -Dtest=TelephonySlotLifecycleRegressionTest,SaveInitialCdrHangupResultRegressionTest,ConnectionLossBridgedCallRecoveryRegressionTest,WebphoneSubscriberTest,CallOriginatorTest test
```

Mevcut local tool path değerlerine göre komuta `-s <settings.xml>` ve `-Dmaven.repo.local=<repo>` eklenecek.

## Kapanış Kriteri

- [ ] Açık sorular `Yok` olarak kalır veya yeni soru doğarsa cevap plana işlenir.
- [ ] Hangup sonrası user result bekleyen call concurrent slot ve active call sayacını etkilemez.
- [ ] AMI connection loss'ta canlılığı belirsiz bridged call erken slot dışına alınmaz.
- [ ] Gerçek hangup veya timeout sonrası slot idempotent şekilde bırakılır.
- [ ] Duplicate hangup/result/timeout counter drift üretmez.
- [ ] Webphone result code pending call için kaybolmaz.
- [ ] Hedefli testler yeşil olur veya environment blocker açık yazılır.
- [ ] Final yanıtta Test Özeti sunulur.

## Tamamlanma Notu

Plan onaylandı ve kod/test uygulaması yapıldı. Hedefli Maven doğrulaması başlatıldı ancak offline local Maven cache'te `kr.motd.maven:os-maven-plugin:1.7.0` bulunmadığı için test runner başlamadan environment blocker oluştu. Dependency indirme denenmedi.
