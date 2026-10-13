# FIX AMI Connection Loss Bridged Call Recovery Change/Fix Planı

## Bağlam

30.04.2026 tarihinde üretimde görülen örnekte Powerdialer çağrıyı `telephony_result_code=75` ile AMI connection lost olarak kapatmış; ancak Asterisk, voice record ve Webphone event kayıtları çağrının gerçek MT-müşteri görüşmesine dönüştüğünü gösteriyor.

- Asterisk tarafında `disconnect_initiator=2`, `hangupcause=16`; yani remote party normal kapatma.
- Voice record tarafında `StartTime`, `EndTime`, `AnswerDate` ve `CallEndSide=REMOTE_PARTY` normal.
- Webphone user event akışında `Reserved -> Incall -> Wrapup` zamanları Asterisk ile tutarlı.
- `pd_cdr` ve campaign tablosunda `telephony_result_code=75`, `user_result_code`, `user_id` ve `assigned_user` alanları boş kalmış.

Kullanıcının notuna göre 28.04.2026, 29.04.2026 ve 30.04.2026 tarihlerinde `docs/analyse` altında planlanan veya geliştirilen son düzeltmeler üretimde yok. Bu nedenle değerlendirme, canlıdaki eski davranışı ve mevcut branch'teki son düzeltmelerin bu bug'ı tek başına kapatıp kapatmadığını birlikte dikkate alır.

## İş Etiketi

fix

## Öncelik

hotfix

## Etkilenen Kod Alanları

- `src/main/java/com/powerdialer/engine/CallOriginator.java`
- `src/main/java/com/powerdialer/engine/asterisk/ManagerConnectionDecorator.java`
- `src/main/java/com/powerdialer/redis/WebphoneSubscriber.java`
- `src/main/java/com/powerdialer/engine/CampaignDbOperations.java`
- `src/main/java/com/powerdialer/util/UserPopulationEvaluator.java`
- İlgili AMI, CDR ve Webphone regression testleri

## Mevcut Davranış

AMI bağlantısı `CONNECTED` dışına düştüğünde `CallOriginator.handleConnectionLoss()` tüm aktif çağrılar için aynı cleanup akışını çalıştırıyor. Bu akış `callCleaner.completeCall(call)` çağırıyor, çağrıya AMI connection loss result code'u atıyor, çağrıyı aktif map'lerden çıkarıyor, counter'ları düşürüyor, connection decorator cache'inden siliyor, final CDR/campaign yazıyor ve varsa kullanıcıyı ACD idle havuzuna geri koyuyor.

Bu davranış henüz müşteriye ulaşmamış veya gerçekten sonuçsuz kalmış çağrılar için gerekli. Fakat user ile müşteri bridge olduktan sonra yaşanan kısa AMI connection loss durumunda aynı cleanup çağrıyı erken final kabul ediyor. Sonradan gelen gerçek Asterisk hangup, voice record ve Webphone result event'leri artık aynı in-memory çağrıya ulaşamadığı için işlenemiyor.

`saveCDR` final kayıt sırasında `callEndTime` değerini işlem anındaki saate çekiyor ve başarısız telephony result için `UserPopulationEvaluator` user ownership alanlarını boş bırakıyor. Bu yüzden örnekte gerçek görüşme yaklaşık 11:20:38'de bitmesine rağmen `pd_cdr.call_end_time` 11:18:49 seviyesinde kalıyor; user result code ve user ilişkisi de kayboluyor.

`WebphoneSubscriber` aktif çağrı bulamazsa durable fallback'e düşüyor. Bu fallback başarılı CDR kabulü için yalnız belirli başarılı telephony result code'larını ve user eşleşmesini arıyor. `telephony_result_code=75` ve cleanup sonrası boş user alanları nedeniyle Webphone result code güvenli şekilde yazılamıyor.

## Beklenen Davranış

Connection loss cleanup tamamen kaldırılmayacak. Cleanup yalnız güvenli şekilde final kabul edilebilen çağrılar için mevcut davranışını koruyacak:

- Asterisk'e hiç ulaşmamış originate denemeleri.
- Customer answer veya agent bridge oluşmamış çağrılar.
- LSC, no caller id, preview timeout, campaign stop veya benzeri gerçek terminal failure durumları.
- Aktif lifecycle kanıtı olmayan orphan çağrılar.

Agent ve müşteri arasında gerçek görüşme başladığını gösteren çağrılar ise AMI connection loss anında final 75 ile kapatılmayacak. Bu çağrılar bounded recoverable state'te tutulacak; reconnect sonrası gelen Asterisk hangup/registrar event'i, voice record ve Webphone result code aynı çağrı üzerinde işlemeye devam edebilecek.

İlk güvenli guard şu contract ile sınırlandırılmalı:

- `call.getUser() != null`
- `call.getAnswerDate() != null`
- `call.isAnsweredByUser()` veya `call.getCdr().getCallHandlingStartTime() != null`

Bu guard dışındaki çağrılar mevcut cleanup yolunda kalmalı.

Recoverable çağrı için AMI reconnect sonrası gerçek terminal event gelirse nihai telephony result Asterisk gerçeğine göre yazılmalı; Webphone result code ve user ownership korunmalı. Bounded süre içinde gerçek event gelmezse kontrollü fallback ile AMI connection loss final sonucu yazılmalı, ancak bu fallback idempotent olmalı ve counter/user state'i iki kez bozmamalı.

Recovery timeout için kodda zaten bulunan `Constants.ORPHANED_CALL_EXPIRE_TIMEOUT` contract'ı kullanılacak; bu değer 15 dakikadır.

## Kod Okuma Bulguları

- `CallOriginator.cleanUpCallsOnConnectionLoss()` şu an bridge kurulmuş çağrıyı da final 75 kabul ediyor ve cache/counter/user state'i dağıtıyor.
- `ManagerConnectionDecorator.handleDialerHangup()` çağrıyı `calls` veya `orphanedCalls` içinde bulamazsa sonradan gelen normal hangup'ı işlemeden çıkıyor.
- Aynı method sadece boş telephony result veya Asterisk error code için overwrite izni veriyor; AMI connection loss kodu bu kapsamda olmadığı için final 75 sonradan normal hangup ile düzelmiyor.
- `WebphoneSubscriber.processCallWithResultCodeAndUpdateDNC()` önce aktif call map'ini arıyor; cleanup çağrıyı sildiği için normal result path'i kaybediliyor.
- `WebphoneSubscriber.isSuccessfulCdrForUserResultCode()` 75 result code'lu CDR'ı durable fallback için başarılı kabul etmiyor.
- 28.04.2026 commit'i delayed hangup'ın 901 ile ezilmesini engelliyor, ancak 75 cleanup davranışını değiştirmiyor.
- 29.04.2026 commit'i Webphone durable fallback'i güçlendiriyor, ancak cleanup'ın user alanlarını boşalttığı 75 senaryosunda yeterli değil.
- 30.04.2026 commit'i initial CDR ve missing answer repair tarafını iyileştiriyor, ancak connection loss cleanup bloğunu değiştirmiyor.

## Root Cause

AMI connection loss bir transport/connection lifecycle olayıyken mevcut kod bunu her aktif çağrı için terminal call result gibi ele alıyor. Bridge kurulmuş çağrıda authoritative call lifecycle artık sadece AMI connection state'ten ibaret değil; Asterisk hangup, voice record ve Webphone user event'leri aynı çağrının gerçek son durumunu üretmeye devam ediyor. Cleanup bu correlation state'ini erken yok ettiği için sonradan gelen doğru event'ler kayda yansımıyor.

## Çözüm Yaklaşımı

1. Connection loss sırasında çağrıları ikiye ayır:
   - `safeToFinalizeOnConnectionLoss`
   - `recoverableAfterConnectionLoss`
2. Recoverable çağrıda final 75 yazma, `completeCall` çalıştırma, çağrıyı decorator cache'inden silme, counter'ları düşürme ve user'ı ACD idle'a geri verme.
3. Recoverable çağrıyı mevcut call registry'de veya bounded orphan/recovery registry'de tut. Reconnect sonrası gerçek hangup ve Webphone result bu çağrıya ulaşabilmeli.
4. `ManagerConnectionDecorator` gerçek hangup geldiğinde recoverable/provisional state'i nihai Asterisk sonucuyla kapatabilmeli.
5. `WebphoneSubscriber` aktif/recoverable çağrıyı bulduğunda user result code'u normal path ile yazmalı. Durable fallback'in 75'i geniş kabul etmesi ancak güçlü user/call eşleşmesi varsa ayrıca değerlendirilmelidir.
6. Bounded timeout dolarsa mevcut cleanup semantiğine yakın bir fallback çalışmalı; fakat idempotent counter ve user ownership korunumu testle kilitlenmeli.

## Cleanup Koruma Sınırı

Bozulmaması gereken cleanup davranışları:

- Gerçekten originate edilememiş veya Asterisk'e ulaşmamış çağrılar 75 ile kapanmaya devam etmeli.
- Customer answer oluşmamış ringing/active çağrılar connection loss sonrası sonsuza kadar sistemde kalmamalı.
- Campaign stop veya terminal failure akışları recoverable olarak yanlış sınıflandırılmamalı.
- Counter decrement ve `callsInProgress` temizliği safe-to-finalize çağrılarda aynı kalmalı.
- ACD idle havuzuna user ekleme sadece gerçekten çağrıdan ayrılmış user için yapılmalı; recoverable bridge çağrıda erken idle yapılmamalı.

## Riskler

- Guard fazla geniş olursa gerçekten kaybolmuş çağrılar runtime state'te gereksiz kalabilir.
- Guard fazla dar olursa gerçek MT görüşmeleri yine 75 ile kapanır.
- Counter decrement iki kez çalışırsa active/ringing/in-call sayıları drift eder.
- User erken idle'a alınırsa aynı MT'ye yeni çağrı verilebilir.
- Reconnect sonrası yeni `ManagerConnectionDecorator` çağrı cache'ini taşımıyorsa gerçek hangup yine orphan olur.
- Webphone durable fallback fazla gevşetilirse yanlış user result code başka çağrıya yazılabilir.

## Performans Etkisi

Bu değişiklik call lifecycle hot path'e dokunur. Çözüm per-event DB query, geniş collection scan, blocking network call veya verbose payload log eklememelidir. Connection loss zaten olağan dışı bir path olduğu için sınıflandırma çağrı başına O(1) field kontrolüyle yapılmalıdır. Webphone normal path'i aktif/recoverable map lookup ile sınırlı kalmalı; durable DB fallback yalnız aktif çağrı bulunamadığında çalışmalıdır. Timeout cleanup bounded ve idempotent olmalıdır.

## TDD Stratejisi

Önce regression testleri kırmızı yazılacak:

- Bridge kurulmuş çağrı AMI connection loss yaşadığında final 75 ile CDR/campaign yazılmamalı.
- Bridge kurulmamış ringing/originate çağrı connection loss sonrası mevcut 75 cleanup davranışını korumalı.
- Recoverable çağrı reconnect sonrası gerçek hangup ile normal telephony result ve doğru `callEndTime` üretmeli.
- Recoverable çağrı için Webphone user result code, çağrı aktif/recoverable registry'de bulunduğunda işlenmeli.
- Timeout fallback çalıştığında counter, `callsInProgress`, user idle ve DB final write idempotent kalmalı.
- 28.04.2026, 29.04.2026 ve 30.04.2026 düzeltmelerindeki regression testler aynı paket içinde yeşil kalmalı.

## Hedefli Doğrulama Komutları

Doğrulama öncesi `.codex/TOOL-PATHS.local.properties` okunacak ve Maven komutları offline çalıştırılacak.

```powershell
mvn -o -Dtest=ConnectionLossBridgedCallRecoveryRegressionTest test
mvn -o -Dtest=CallOriginatorTest,ManagerConnectionDecoratorTest,WebphoneSubscriberTest test
```

Test sınıf adları uygulama sırasında mevcut test yapısına göre netleştirilebilir.

Uygulama sonunda çalıştırılan hedefli komut:

```powershell
mvn -o -s C:/Users/GLB90048674/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90048674/.m2/repository -Dtest=ConnectionLossBridgedCallRecoveryRegressionTest,SaveInitialCdrHangupResultRegressionTest,WebphoneSubscriberTest,CallOriginatorTest test
```

Sonuç: başarılı, 50 test yeşil.

## Execution Plan

- [x] Üretim örneği, mevcut kod davranışı ve son tarihli canlıda olmayan geliştirmeler birlikte analiz edildi.
- [x] Açık sorular kullanıcıyla netleştirildi.
- [x] Bridge kurulmuş çağrıyı safe-to-finalize çağrıdan ayıran karakterizasyon/regression testleri yazıldı.
- [x] `CallOriginator` connection loss cleanup akışı iki sınıfa ayrıldı.
- [x] Recoverable çağrı registry/correlation state'i reconnect sonrası event processing'e uygun tutuldu.
- [x] `ManagerConnectionDecorator` gerçek hangup geldiğinde recoverable çağrıyı final sonuca taşıyacak şekilde güncellendi.
- [x] `WebphoneSubscriber` aktif/recoverable çağrı üzerinden user result code'u işleyecek şekilde güncellendi; durable fallback contract'ı korunarak testlendi.
- [x] Timeout fallback ve idempotent cleanup testlendi.
- [x] Hedefli Maven testleri offline çalıştırıldı.
- [x] İlgili mimari doküman current behavior olarak güncellendi.
- [x] Plan dosyasında tamamlanan adımlar işaretlendi ve history append-only kaydı güncellendi.

## Açık Sorular

Yok.

## Netleşen Kararlar

- Recovery timeout için kodda zaten bulunan `Constants.ORPHANED_CALL_EXPIRE_TIMEOUT` contract'ı kullanılacak; bu değer 15 dakikadır. Ek property açılmadı.
- İlk kapsam yalnız agent-customer bridge kanıtı olan Powerdialer çağrıları olacak. `user + answerDate + answeredByUser/callHandlingStartTime` guard'ı dışındaki preview, IVR ve başlamamış çağrılar mevcut cleanup davranışında kalacak.
- Canlıya alınacak paket 28.04.2026, 29.04.2026 ve 30.04.2026 tarihli önceki düzeltmeleri prerequisite olarak içerecek; bu bug onların üzerine ek hotfix olarak ele alınacak.

## Kapanış Kriteri

- Açık sorular cevaplanmış ve plan onaylanmış olacak.
- Bridge kurulmuş çağrı connection loss sonrası erken 75 ile kapanmayacak.
- Gerçekten cleanup gereken çağrılar mevcut davranışı koruyacak.
- Reconnect sonrası Asterisk hangup ve Webphone result code aynı çağrı için işlenebilecek.
- Counter, ACD idle ve DB write idempotency regression testlerle korunacak.
- Hedefli Maven testleri offline yeşil olacak veya environment blocker açık yazılacak.

## Tamamlanma Notu

Tamamlandı. Kod değişikliği TDD ile uygulandı; hedefli offline Maven testleri başarılı.
