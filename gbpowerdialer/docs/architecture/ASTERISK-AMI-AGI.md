# Asterisk AMI ve AGI

Asterisk entegrasyonu bu uygulamanın en kritik external boundary'sidir. AMI bağlantısı, originate action üretimi, event handling ve AGI script'leri aynı call lifecycle'ın farklı parçalarını tamamlar.

## AMI Bağlantı Sahipliği

- `ManagerConnectionComposition`, etkin server kayıtları için AMI bağlantısı oluşturur.
- `openedConnections`, hostname bazlı `ManagerConnectionDecorator` nesnelerini tutar.
- Server AMI port, kullanıcı adı, şifre ve timeout değerleri server property'lerinden okunur.
- Bağlantı durumu `Server` modeli üzerine yansıtılır.
- Her `ManagerConnectionDecorator` aynı Asterisk'e **iki ayrı `AutonomousManagerConnection`** açar (FIX-GLB90048674): `eventConnection` (listener, tüm event'leri dinler) ve `commandConnection` (`eventMask="off"`, originate/hangup action'ları buradan gider). Amaç: komut yolunu event yükünden izole etmek; event-siz komut bağlantısının reader'ı boşta kaldığı için ping ve komut cevapları her zaman anında okunur (referans tasarım: gbwebphone).
- `getManagerConnectionState()` ve connection-loss/recovery state semantiği `eventConnection` üzerinden yürür (authoritative); recovery davranışı değişmemiştir. Yalnız `commandConnection` düşerse originate `sendAction` exception alır, mevcut `handleExceptionDuringOrigination` yolu çağrıyı temiz şekilde recycle eder (loss yok); asterisk-java autonomous reconnect iyileştirir.

## Originate Akışı

1. `CallOriginator`, originate edilecek `Call` nesnesini seçer.
2. `ManagerActionFactory.generateCall(Call)` çağrı tipine göre action üretir.
3. `AbstractOriginateAction` alt sınıfları AMI değişkenlerini ve channel/context bilgilerini hazırlar.
4. `ManagerConnectionDecorator.originate(Call)` action'ı gönderir.
5. Bazı akışlarda action bilgisi Redis'e publish edilir.
6. AMI response ve event'ler çağrı sonucunu temizleme, retry ve counter update davranışlarını tetikler.

## Originate Action Tipleri

İlgili sınıflar:

- `PreviewFreeAction`
- `PreviewStraightAction`
- `ProgressiveReservedAction`
- `ProgressiveReleasedAction`
- `OutboundIVRAction`
- `OriginateActionDataContainer`

AMI variable adları `Constants.MANAGER_ACTION_VARIABLE` altında tutulur. Bu adlar Asterisk dialplan, AGI ve event parsing ile contract niteliğindedir.

## Event Handling

`ManagerConnectionDecorator`, Asterisk Java `ManagerEventListener` olarak AMI event'lerini alır. İlgili event sınıfları:

- `AgentCalledByDialerEvent`
- `AgentPredictiveAnswerEvent`
- `AniDefinitionEvent`
- `ClientPredictiveAnswerEvent`
- `DialerHangupEvent`
- `DisconnectEvent`
- `HardphoneAnswerEvent`
- `VariablesEvent`

Event işleme çağrı sonucu, campaign counter'ları, Redis call event'leri ve agent state'i üzerinde side effect üretebilir.

### DialerHangup cause contract'ı ve AMD ailesi (FEAT-325470)

- `DialerHangupEvent.hangupCause` değeri **`pd_telephony_rc.code`** kolonuyla eşlenir (`ManagerConnectionComposition.createItuToTelephonyResultCodeRelations`, harita startup'ta bir kez yüklenir). Tablonun `id` kolonu contract'ın parçası değildir; ekranlarda görünen id (ör. 11, 13, 86) santralin göndereceği değer olarak yorumlanmaz. Eşleşmeyen cause WARN loglanır ve code 901 (`CALL_EXPIRED_CLEARING`) fallback'ine düşer.
- ITU cause'lar (0..127) dışında dialer'ın dahili 9xx serisi vardır: 900 AMD, 901..911 finalize/fallback kodları, 912 Ambiguous. Ayrıştırılmış AMD sonuçları ise Q.850'de atanmamış 113..115 aralığındadır: **113 AMD Bad Number** (id 86), **114 AMD Not Reached** (id 87), **115 AMD Unknown Tone** (id 88). Santral bu sonuçları bu code'larla gönderir; AMD busy ve AMD no answer ise mevcut ITU code 17 (id 11) ve 19 (id 13) ile gelir. İlk sürümde (16.09.2026) 913/914/915 kullanılmıştı; 05.10.2026 santral kararıyla 113/114/115 oldu ve migration script'i eski satırları yeni koda taşır.
- **AMD ailesi** (`Constants.AMD_FAMILY_TELEPHONY_RC_CODES` = 900/113/114/115) hangup yolunda tek muamele görür: awaiting-user-result-code cache'ine düşmez (`shouldProcessCallResultOnHangup`), TRC 84 "customer hangup before agent connection" override'ından muaftır (`applyCustomerHangupBeforeAgentConnectionOverride`) ve predictive'de NO_CONNECTION sayılır (`processCallResult`). Karar `code` ile verilir; eski kod yalnız id 71'i tanıyordu.
- Zorunlu dahili code'lar `ManagerConnectionComposition.requiredInternalTelephonyResultCodes()` ile startup'ta doğrulanır; eksik satır ERROR loglanır, açılış engellenmez. Yeni satır eklemek için `gbpowerdialer/src/main/resources/sql/migration/FEAT-325470-amd-telephony-result-codes.sql` emsali izlenir ve uygulama restart edilir (bkz. `DATA-PERSISTENCE-AND-DB.md`).

## Event İşleme Thread Modeli (FIX-GLB90048674)

Geçmişte `onManagerEvent` ve originate response callback işi (DB/CDR yazımları dâhil) doğrudan AMI **reader thread'inde** senkron çalışıyordu. Predictive yükte reader tıkanınca ping/komut cevapları zamanında okunamıyor, keepalive bağlantıyı "ölü" sayıyor ve/veya Asterisk yavaş tüketiciyi atıyordu → kronik **AMI connection loss**. Çözüm: bu iş reader thread'inden alınıp decorator başına ayrı bir worker'a taşındı.

- `onManagerEvent` artık event'i **decorator başına tek-thread'li, bounded (`Constants.AMI_EVENT_WORKER_QUEUE_CAPACITY`) bir worker'a** (`eventWorker`) devreder ve hemen döner; reader thread serbest kalır. Originate response callback'inin DB-ağır `handleOriginationFail` kısmı da aynı worker'a verilir.
- **Sıra:** worker tek-thread + FIFO olduğu için event'ler ve callback geldikleri sırayla, serileştirilmiş işlenir (çok-thread havuz kullanılmaz; sayaç/CDR sırası korunur).
- **Kayıpsızlık:** kuyruk dolunca custom `RejectedExecutionHandler` submit eden thread'i **bloklar (drop yok, CallerRuns yok)**. Kapanışta (`logoff`/`preDestroy`) ve decorator-replace'te worker **drain edilir** (`shutdown()` + `awaitTermination`, asla `shutdownNow()`), bekleyen DB yazımları uçmaz.
- **Dayanıklılık:** worker task gövdesi handler exception'ını yakalayıp `LOG.error` ile loglar; böylece tek worker thread'i ölmez ve hata app log'una düşer (asterisk-java'nın eski "swallow-and-continue" davranışı korunur).
- Test edilebilirlik için `eventWorker` default'u **inline** çalışır; gerçek async worker yalnız production `createConnection`'da, `login()`'den önce kurulur.
- Reader-thread maliyetini düşürmek için answer path'inde gereksiz `getByInteractionId` SELECT'i çağrı başına bir kez ile sınırlanır (`Call.markCdrPersistLookupAttempted`); yalnız bir READ elenir, yazım sırası/sayısı korunur.

## Telephony Slot ve Business Result Ayrımı

`ManagerConnectionDecorator`, hangup sonrası telephony cleanup ile business result finalization adımlarını aynı lifecycle kabul etmez. Hangup geldiğinde call user result code bekliyor olsa bile telephony slot idempotent olarak bırakılır:

- `callsInProgress`, `activeCalls`, `ringingCalls`, `inCalls` ve `originatedCalls` telephony tarafında hâlâ açık çağrı olup olmadığını temsil eder.
- Hangup sonrası yalnız Webphone user result code bekleyen çağrı bu sayaçlarda ve originated cache'te tutulmaz.
- Bu çağrılar bounded `awaitingUserResultCodeCalls` cache'inde bekler.
- Webphone result lookup sırası active calls, awaiting user result calls, recoverable connection loss calls ve son olarak DB fallback şeklindedir.
- `Call` üzerindeki transient idempotency flag'leri aynı call için telephony slot release ve business result finalize işlemlerinin duplicate event veya timeout ile ikinci kez çalışmasını engeller.

## AMI Connection Loss Recovery

`CallOriginator`, AMI connection loss gördüğünde tüm çağrıları aynı terminal cleanup yoluna sokmaz. Customer answer ve agent bridge kanıtı olan çağrılar `ManagerConnectionDecorator` içindeki recoverable connection loss cache'ine taşınır. Bu cache `Constants.ORPHANED_CALL_EXPIRE_TIMEOUT` değeriyle 15 dakika yaşar.

Recoverable çağrı için:

- `telephony_result_code=75` hemen yazılmaz.
- Campaign counter'ları, `callsInProgress`, user idle dönüşü ve final CDR write erken çalıştırılmaz.
- `DialerHangupEvent`, `WebphoneSubscriber` result code akışı ve AGI/originated call lookup bu çağrıyı recoverable cache'te bulabilir.
- Gerçek hangup veya Webphone result gelirse çağrı normal lifecycle üzerinden kapanır.
- 15 dakikalık timeout sonunda hâlâ final event gelmemişse bounded fallback cleanup çalışır; mevcut telephony result yoksa AMI connection loss sonucu atanır.

Bridge kanıtı olmayan ringing/originate çağrılar, preview/IVR başlamamış akışlar ve terminal failure durumları eski connection loss cleanup davranışında kalır.

## AGI Sınırı

- `CustomAgiServerThread`, Asterisk Java AGI server'ı başlatır.
- `DialWithAbandonTimeoutScript`, AGI isteğiyle gelen çağrıyı `ManagerConnectionComposition.getOriginatedCall` üzerinden bulur.
- AGI tarafı dial komutu, SIP header, abandon timeout ve agent-called event davranışlarına temas eder.
- Bean `SimpleMappingStrategy` ile tek (singleton) instance olarak map'lenir; `service()` eşzamanlı AGI çağrıları tarafından paylaşılır. `lockedUsers` alanı bu yüzden global lock haritasıdır ve farklı çağrıların aynı agent'ı aynı anda dial etmesini engeller.

### Agent Bridge Döngüsü

Müşteri (client) cevapladıktan sonra script, çağrıyı abandon penceresi içinde bir agent'a bağlamaya çalışır. Döngü şu invariant'lar altında çalışır:

- Çağrı-bazlı `triedUsers` seti: bir agent'a yapılan dial cevapsız kalırsa (veya lock/availability nedeniyle atlanırsa) o agent bu customer-answered penceresi için dışlanır ve tekrar dial edilmez. Bu, aynı agent'ın kontrolsüz yeniden seçilmesini (same-user retry storm) engeller.
- Dışlanan agent dial anında idle havuzuna geri eklenmez; script bittiğinde (`finally`) online + available + `!dialInProcess` guard'ıyla idle havuzuna geri verilir. Yani agent başka çağrılar için en geç bu çağrı penceresi sonunda yeniden uygundur.
- Müşteri kanalının gittiği tespit edilirse (Dial `AgiHangupException`, caller-side `DIALSTATUS=CANCEL` veya `telephonyResultCode` set edilmiş) döngü anında durur. Yeni telephony result code üretilmez; nihai sonucu normal hangup akışı (`ManagerConnectionDecorator` / `DialerHangupEvent`) yazar.
- Agent'ın cevapladığı, Asterisk'in **senkron `DIALSTATUS=ANSWER`** sonucundan tespit edilir (`isAgentAnswered`). Eski davranış yalnız asenkron `AgentPredictiveAnswer` event'inin set ettiği `answeredByUser` bayrağına bakıyordu; AMI event-worker yük altında geciktiğinde bayrak Dial dönerken henüz set olmadığından gerçek cevap yanlışlıkla `NOT_ANSWERED` sayılıp `call.user` churn'ü + abandon üretiyordu (FIX-GLB90048674). Artık `DIALSTATUS=ANSWER` görülürse döngü o agent ile biter ve `call.user` korunur; bayrak set olduysa o da onurlandırılır.
- Taze (henüz denenmemiş) agent yokken döngü busy-spin yapmaz; kısa aralıklı (100 ms) yoklama ile abandon timeout'a kadar bekler, agent answer/`telephonyResultCode`/caller-gone/abandon koşullarından biri oluşunca çıkar.
- Abandon timeout aşılırsa mevcut `processAbandonTimeout` akışı (abandon IVR varsa IVR dial, yoksa hangup) korunur.

## Riskli Noktalar

- AMI event sırası ve originate response sırası her zaman beklenen sırada gelmeyebilir.
- `ManagerConnectionDecorator` içindeki callback/cache state'i yerel JVM'e bağlıdır.
- Asterisk event'inde gelen call id, originated call cache'inde bulunamazsa çağrı akışı eksik kalabilir.
- AMI değişken adı değişikliği dialplan/AGI ile uyumsuzluk oluşturabilir.
- Result code mapping değişikliği raporlama ve retry davranışını etkileyebilir.

## Change Contract

- AMI veya AGI değişikliklerinde `gbpd-ami-event-flow` kullanılmalıdır.
- Originate variable adı, event adı veya result mapping değişirse contract test veya karakterizasyon testi yazılır.
- Asterisk'e gönderilen action serialize edilip eski/yeni farkı gözlemlenir.
- Gerçek AMI bağlantısı olmadan yapılan testler davranışın tamamını kanıtlamaz; en azından adapter seviyesinde event örnekleriyle doğrulama yapılır.
