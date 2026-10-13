# Dialer Çalışma Zamanı

Dialer runtime, kampanyadan çağrı üretip Asterisk'e originate edilene kadar olan ana akıştır. Mevcut tasarım yoğun biçimde yerel bellek, queue ve counter kullanır.

## Ana Bileşenler

- `DialerFacade`: Kampanya context'leri ve Asterisk call originator'ları yönetir.
- `CampaignContext`: Bir kampanyanın producer, consumer ve state nesnelerini taşır.
- `CallProducer`: Kampanya veritabanından callback, retry ve fresh çağrı adaylarını çeker.
- `CallConsumer`: Producer data queue'sundan çağrı queue'suna drain eder.
- `CallOriginator`: Server bazlı çalışır, kampanya queue'larından çağrı alıp AMI originate gönderir.
- `CallProducerConsumerTemplate`: Producer ve consumer için ortak çalışma davranışını sağlar.
- `RetryStrategy` ve end-list behavior sınıfları: Retry ve liste sonu davranışını belirler.

## Temel Çağrı Akışı

1. `CallProducer`, kampanya veri kaynağından aranacak kayıtları alır.
2. Kayıtlar `Call` nesnelerine dönüştürülür; callback, retry ve fresh ayrımı yapılır.
3. Producer data queue'suna eklenen çağrılar consumer tarafından kampanya call queue'suna taşınır.
4. `CallOriginator`, kampanya queue'larından çağrı seçer.
5. `ManagerActionFactory`, çağrı tipine göre AMI `OriginateAction` üretir.
6. `ManagerConnectionDecorator`, originate action'ı Asterisk'e gönderir veya preview gibi durumlarda Redis'e publish eder.
7. AMI event, originate response, AGI ve user event akışları call state'i ve campaign counter'larını günceller.

## Retry ve Numara Seçimi

Retry kararının tamamı tek chokepoint'ten akar: `RetryStrategy.createRetry(Call)`. Çağrı noktaları `ManagerConnectionDecorator.manageRetries` (normal hangup), `CallOriginator.cancelCall` (LSC/originate iptali), `CallOriginator.cleanUpCallsOnConnectionLoss` (AMI bağlantı kaybı) ve `CallCleaner.clearExpiredCall`'dır. `RetryStrategyFactory` altı stratejiden birini üretir ve strateji nesnesi her retry için yeniden oluşturulur.

`createRetry` kontrol sırası, sıranın kendisi davranışın parçası olduğu için değiştirilmemelidir:

1. **Başarılı kapanış** — müşteri yanıtladı (TRC 16) ve sonuç kodu retry'a izin vermiyorsa kayıt derhal ve kalıcı kapanır. `RetryRulesCycle` için `isCallAnswered()` aynı zamanda `resultCodeSchedule`'ı dolduran yan etkiyi taşır; bu yüzden her zaman ilk çağrılan olmalıdır.
2. **Genel deneme limiti** — `retryOveralAttempt`; `countAllCallTypes` açıkken `Call.attempts`, kapalıyken `retryHistory` uzunluğu üzerinden.
3. **Numara başına azami arama limiti** — opsiyonel `Campaign.maxAttemptPerNumber`.
4. **Kullanılmayan numara (AMD bad number, code 113)** — aranan numara bu karar için geçersizdir; halkada sonraki uygun numaraya geçilir, aday yoksa kayıt kapanır. Yalnız bu dalda stratejinin kendi numara seçimi (`setRetryNumber`) atlanır; zaman hesabı stratejinin normal hesabıdır.

### Numara başına azami arama limiti

Kayıttaki her tekil telefon numarasının kampanya süresince alabileceği mutlak azami arama sayısıdır. Genel limitten **türetilmez**, onunla birlikte uygulanır; kayıt hangisi önce dolarsa durur. Alan boş veya `<= 0` iken özellik kapalıdır ve retry davranışı birebir eski hâlindedir — kampanyada alanı boşaltmak özelliğin kill-switch'idir, kod deploy'u gerektirmez. Çalışan kampanyada boş değer kaydetmeyle aktarılmaz (`CampaignContext.updateCampaign` → `CampaignPropertyCopier` null değeri atlar); kampanya durdurulup başlatılınca ya da "kaydet ve yeniden başlat" ile devreye girer.

- **Atlama:** `RetryStrategy.processRetryNumberWithDNC()` predicate'i `DNC || numaraBaşınaLimitDoldu` şeklindedir ve altı stratejinin tamamı ortak template'ten geçer. Yürüyüş `advanceRetryNumberPastIneligible` ile en fazla bir tam turla sınırlıdır; aday kalmazsa `NoEligibleRetryNumberException` fırlatılır (03.08.2026 sonsuz döngü vakasının koruma katmanı).
- **Erken kayıt kapatma:** aranabilir tüm numaralar bireysel limitini doldurmuşsa genel limit dolmamış olsa dahi kayıt kapanır. Guard `createRetry` içinde tek noktada uygulanır; kendi limit mantığını taşıyan `RetryRulesCycle` dahil muaf olmayan tüm stratejiler bu sayede kapsanır.
- **Muafiyet:** `REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER` ve `REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER` stratejileri numara başına sınırı zaten `retryOveralAttempt` ile uygular; bu iki stratejide yeni alan yok sayılır ve UI'da freeze edilir. Karar tek yerde, `Campaign.isPerNumberAttemptLimitActive` içindedir; producer ve retry stratejileri bunu okur, iki strateji sınıfı ayrıca `isPerNumberLimitActive()` override'ıyla alanı yok sayar. Tek numara stratejisi bu yüzden limit dolunca diğer numaralara geçmez; aynı numarayı genel limite kadar arar.
- **Sayaç kaynağı:** ayrı sayaç kolonu yoktur; sayım `Call.getDialedNumberSequence()` ile `call_process` izi kursor olarak kullanılarak `retry_history` ve `cb_history` üzerinden yeniden kurulur (bkz. `DATA-PERSISTENCE-AND-DB.md`). Harita karar başına bir kez hesaplanır ve strateji instance'ında tutulur.

### Kullanılmayan numara kuralı (FEAT-325470)

Santralin AMD ile ayrıştırdığı "kullanılmayan numara" sonucu `pd_telephony_rc.code = 113` (id 86) olarak gelir. Bu sonuçta aranan numaraya bir daha retry kurulmaz:

- **Numara geçişi:** `RetryStrategy.switchRetryNumberAfterUnreachable()` `colSort` sıralı dolu kolonları aranan numaradan sonraki konumdan başlayarak en fazla bir tur tarar; aranan numaranın kendisi olmayan ve `isNumberIneligible` (DNC + aktifse numara başına limit) olmayan ilk numara retry numarası olur. Aranan numara kolonlarda yoksa (kolon dışı callback numarası) tarama baştan başlar; bekleyen callback planı mevcut callback→retry dönüşümüyle aynı şekilde temizlenir.
- **Kapanış:** aday yoksa kayıt `completeCall` ile kapanır ve `Call.closeReason = all numbers unreachable` yazılır (yalnız `col_result_close_reason` binding'i eşliyse result tablosuna iner). Bu dal istisna fırlatmaz; `NoEligibleRetryNumberException` sigortası diğer dallarda korunur.
- **Zaman:** dal yalnız numarayı belirler; tarih stratejinin normal hesabından gelir (`RetryRulesCycle`'da 113 için schedule satırı varsa onun interval'i, yoksa retry delay; diğer stratejilerde retry delay ve macro kuralı).
- **Kalıcılık yok:** geçersizlik yalnız o karardaki aranan numara içindir. Birden fazla numarası da 113 alan kayıtta halka ilk numaraya geri dönebilir; döngü genel attempt limiti ve numara başına limitle sınırlıdır (bilinçli kabul, bkz. `docs/analyse/16.09.2026/FEAT-325470-AMD-SONUC-KODLARI-RETRY-STRATEJISI.md` S5).
- **Predicate'e eklenmez:** geçersizlik `isNumberIneligible`'a bilerek eklenmemiştir; aksi hâlde 325559'un `noEligibleNumberLeft()` guard'ı bad-number kaynaklı kapanışı "limit doldu" nedeniyle etiketlerdi.
- **Diğer AMD sonuçları:** 114 (not reached) ve 115 (unknown tone) özel dal almaz; code 16 dışındaki her TRC gibi normal retry akışına girer.

### Producer tarafındaki numara seçimi

`CallProducer.resolveCalledNumber(Call)` aranacak numarayı belirler: öncelik sırası callback > retry > telefon kolonları ve argümanlar tembel değerlendirilir. Fresh seçimde `selectFreshNumber` colSort'a göre ilk dolu **ve limitini doldurmamış** kolonu alır; böylece geçmişi olan bir kayıt havuza fresh olarak döndüğünde limitli numaralar hiç denenmeden atlanır.

Aranabilir numara bulunamayan kayıt (tüm kolonlar boş, tüm numaralar limitli veya callback numarası limitli) `Call`'a dönüştürülmez ve `CampaignDbOperations.setClosedWithoutDialing` ile neden bazında batch olarak kapatılır. Bu davranış aynı zamanda eski bir sızıntıyı kapatır: numara çıkarımı başarısız olan kayıt `setFetchedDataFlagForCalls`'ın listesine hiç girmediği için `is_fetched` de işaretlenmiyordu ve her producer tick'inde yeniden SELECT edilip yeniden loglanıyordu.

## FCT Kanal Kapasite Gate'i

FCT routing açık (`Campaign.fctRoutingEnabled`) ve `callerIdBehaviour = POOL` olan kampanyalarda, QSS `getRouteDetails` yanıtı her entry için `capacity` (FCT'nin toplam eşzamanlı kanal sayısı) taşır. Bir numara = bir FCT olduğu için kapasite anahtarı doğrudan telefon numarasıdır.

- `FctChannelRegistry` (`engine/dialer/routing`): uygulama geneli tek `@Component`. Numara bazlı in-flight `callId` set'i, cooldown ve bounded olay log'u tutar. Rezervasyonlar sayaç değil set olduğu için sayısal drift yapısal olarak oluşmaz; kaçan release `fct.channel.ttl.ms` sonunda prune edilir ve `PRUNE` olayı olarak raporlanır. TTL, `Constants.CALL_EXPIRE_TIMEOUT`'tan sonra devreye girecek şekilde seçilir; aksi hâlde konuşma hâlindeki uzun çağrının kanalı erken serbest bırakılır. Kalıp olarak `AgentDialLockRegistry`'nin kapasitesi N olan hâlidir.
- `FctChannelGate` (`engine/dialer/routing`): çağrı akışıyla registry arasındaki tek karar noktası. Rezervasyon `CallOriginator.processOrigination` başında (LSC sonrası, AMI send öncesi) alınır; cursor üzerinde dolu veya cooldown'daki entry'ler atlanır.
- Uygun kanal yoksa çağrı iptal edilmez: kampanya pause defer primitive'i (`cleanupCallBeforeDeferredOrigination` + kuyruğun başına koyma) ile attempt geri alınır ve bir sonraki tick'te yeniden denenir. `fct.channel.defer.limit` aşılırsa mevcut `TELEPHONE_RC_NO_CALLERID_AVAILABLE` akışına düşülür.
- `cause 34` hangup'ında numaranın rezervasyonu bırakılır ve numara `fct.channel.cooldown.ms` süresince cooldown'a alınır. Aynı çağrı içinde sıradaki FCT ile yeniden Originate atma davranışı `fct.channel.list.retry.max` ile sınırlıdır (**varsayılan 1**, 30.09.2026'da 0'dan yükseltildi: Asterisk `h` guard'ı ara bacağı yan etkisiz hâle getirdiği için sıradaki FCT'ye tek geçiş hakkı makul görüldü). Sınır aşılsa da release + cooldown uygulanır — "bu FCT dolu" bilgisi diğer çağrılar için korunur. `fct.channel.gate.enabled=false` iken bu sınır uygulanmaz ve 04.05 FCT routing davranışı birebir geri gelir.

### `HAS_NEXT` sözleşmesi ve re-originate zinciri

Gate yolundaki her Originate `Variable: HAS_NEXT=0|1` taşır. Asterisk `[outbound-predictive-fct]` context'inin `h` extension'ı bunu okur:

```text
GotoIf($["${HAS_NEXT}" = "1" & ("${DIALSTATUS}" = "CONGESTION" | "${DIALSTATUS}" = "CHANUNAVAIL")]?intermediate)
```

`HAS_NEXT=1` olan ve circuit reddiyle düşen bacakta yalnız `DialerHangup` UserEvent'i gönderilir; `Disconnect`, ODBC hangup update, hangup_data.log, EXEC hook ve qos **atlanır**. Değişken boş veya `0` ise eski davranış korunur. Asterisk listeyi bilmez; her Originate tek caller ID taşır, zinciri dialer yürütür.

- Değer kaynağı `FctChannelGate.hasNextCallerId`: cursor'da rezerve edilebilir bir FCT adayı **veya** kullanılabilir sabit hat fallback'i varsa `1`. Adaylık kontrolü cooldown'a bakar, kapasite doluluğuna bakmaz (doluluk originate ile hangup arasında değişir, rezervasyon denemesi olmadan kesin bilinemez).
- **Risk simetrik değildir**: `HAS_NEXT=1` gönderip re-originate etmemek Asterisk tarafındaki hangup kaydını tamamen kaybettirir; tersi yalnızca eski davranıştır. Bu yüzden şüphede `0` üretilir ve `hasNextCallerId` gerçek karar yoluyla aynı yardımcılardan türetilir. Ayrışma `FctChannelGateHasNextTest`'teki eşdeğerlik testiyle kilitlenir.
- Kill switch (`fct.channel.gate.enabled=false`) açıkken değişken **hiç gönderilmez** — dialplan'da boş değer 04.05.2026 davranışına denk geldiği için tek property iki tarafı birden eski hâline döndürür.
- N FCT + M sabit hat denemesi **tek attempt**tir: `CallAttemptPolicy.incrementForOrigination` yalnız `CallOriginator.pollCallsFromConsumerAndOriginate`'te çalışır. `originationDate` de ilk bacaktan korunur; eskiden re-originate öncesi null'landığı ve alanın tek yazarı `processOrigination` olduğu için `durationFromOrigination` 0'da donuyordu (30.09.2026 bugfix).

### Trunk seçimi (`DIALER_TRUNK`)

Çağrının hangi trunk'tan çıkacağı numaraya göre değişir. Bu bilgi QSS'te numaranın tanımıyla birlikte durur (`RouteDetailEntry.trunk`, numara başına sabit ve call type'tan bağımsız) ve PD onu yorumlamadan `Variable: DIALER_TRUNK` olarak Asterisk'e iletir; dialplan boş/eksik değeri varsayılan trunk sayar.

- Trunk, caller ID ile **aynı noktada** (`FctChannelGate.tryAcquire`) `Call.fctTrunk`'a yazılır. İkisi aynı QSS entry'sinin alanlarıdır; ayrışmaları çağrıyı yanlış trunk'tan çıkarır. cause 34 sonrası sıradaki FCT'ye geçildiğinde trunk da o entry'ninki olur.
- **Sabit hat fallback'inde trunk temizlenir** (`tryFixedLineFallback` → `setFctTrunk(null)`): sabit hat `Campaign.callerId`'den gelir ve QSS'te tanımlı değildir; bayat trunk taşınırsa çağrı yanlış trunk'tan çıkar.
- Değer AMI başlığına yazıldığı için PD'de ikinci bir format kapısı vardır (`^[A-Za-z0-9_.-]{1,64}$`). Satır sonu veya `:` içeren bir değer AMI protokolünü bozar ve aynı bağlantıdaki sonraki action'ları da etkiler. Geçersiz değerde trunk düşürülür ve WARN basılır; çağrı varsayılan trunk'a gider, kaybedilmez.
- **Kill switch'e bağlı DEĞİLDİR.** `fct.channel.gate.enabled=false` kapasite saymayı durdurur ama caller ID yine QSS entry'sinden seçilir; trunk'ı o anda susturmak bütün FCT çağrılarını varsayılan trunk'a kaydırırdı. Kill switch kapasite gate'ini geri alır, trunk seçimini değil. (`HAS_NEXT` bunun aksine kill switch'e bağlıdır.)
- `getRouteDetails` isteği **yalnız `groupId`** taşır. QSS sözleşmesi grup bazlıdır; aranan numara seçime girmez. Çağrı tipine/denemeye göre süzmenin tüketen tarafta (`RouteDetailsFilter`) yapılmasının sebebi budur.
- Trunk, registry'ye de **doğrulanmış hâliyle** yazılır (`tryAcquire(..., trunk, ...)`) ve snapshot'tan FCT Kanal Monitörü'ne çıkar. Tek kaynak kullanılmasının sebebi ekranın gösterdiği değerin AMI'ye giden değerden ayrışamamasıdır: format kapısına takılan bir trunk ekranda da "varsayılan" görünür. Operatör hangi numaranın hangi trunk'tan çıktığını AMI/log debug'ı olmadan görür — `DIALER_TRUNK` PD loglarında zaten görünmez (`manager.conf` `channelvars` listesine bağlıdır).

### Sabit hat fallback'i

FCT listesi tükendiğinde (veya in-call retry sınırı dolduğunda) çağrı TRC 25 ile kapatılmaz; kampanyanın sabit hat caller ID'siyle son bir kez originate edilir.

- Kaynak `Campaign.callerId`'dir. Bu kolon POOL kampanyalarda daha önce UI'da hiç gösterilmiyordu; FCT routing açıkken ayrı bir etiketle (`campaign.fctFixedLineCallerId`) gösterilir. **Boş bırakılması "fallback kapalı" demektir** — güvenli varsayılan.
- Bütçe `fct.fixed.line.retry.max`'tir ve `fct.channel.list.retry.max`'ten **ayrıdır**: sabit hat FCT kapasitesiyle ilgili olmadığı için FCT retry'ı 0'a çekilse bile fallback çalışır.
- Sabit hat için registry'de **rezervasyon tutulmaz** ve numara girdisi açılmaz (kapasite kavramı yok); yalnız `FALLBACK_FIXED_LINE` feed olayı üretilir. Sıradaki FCT'ye geçişte de `REORIGINATE` olayı yazılır.
- Fallback da tükendiğinde standart `processCallResult` zinciri (TRC 25 → retry) değişmeden çalışır.
- QSS route listesi çağrının tipine ve deneme sayısına göre süzülür (`RouteDetailsFilter`). Gerekçe: eski `obtainCallerId` yolu QSS'e `CT_PD_<callType>` ve `AT_<attempt>` gönderip seçimi QSS'e bırakıyordu; `getRouteDetails` yalnız `(routingGroupId, calledNumber)` aldığı için bu context kayboluyordu. Süzme üç katmanlı fail-open'dır: boş alan değeri, tanınmayan QSS sözlüğü ve süzmenin tüm listeyi elemesi durumlarında çağrı caller ID'siz bırakılmaz. **Sınır**: bu süzme `retryCallerIdBehavior` (UNIQUE/SAME) semantiğini geri kazandırmaz; o semantik QSS provision history'sini gerektirir ve tam parite için `RouteDetailsRequest`'e context alanlarının eklenmesi gerekir.
- QSS'e erişilemediğinde (gRPC channel yok veya çağrı hata attı) `RouteDetailsUnavailableException` fırlatılır ve çağrı **retry'a izin verilerek** iptal edilir. Bu, "grup/route yok" (boş liste → `NoAvailablePoolCallerIdException` → retry'sız iptal) durumundan bilinçli olarak ayrıdır: geçici bir QSS/Eureka kesintisinde o an originate edilen çağrılar kalıcı olarak kaybedilmez.
- Release noktaları: `releaseTelephonySlotIfNeeded`, `CallCleaner.completeCall`, `CallCleaner.clearExpiredCall`, `handlePreviewTimeout`, `handleExceptionDuringOrigination`, cause 34 list-retry ve TTL prune. Release owner kontrollü (`(numara, callId)`) ve idempotenttir.
- Rezervasyonun sahibi çağrıdır, kampanya değildir: campaign start/pause/reset/reload registry'ye dokunmaz. Yalnız `CampaignStopJob.lastCleanUpChecks` içinde kampanya bazlı safety-net süpürmesi yapılır.
- Preview kampanyalar gate kapsamı dışındadır (originate'i webphone atar); FCT routing liste iterasyonu preview'da korunur.
- `fct.channel.gate.enabled = false` veya `capacity <= 0` durumunda gate pasiftir (fail-open) ve davranış FCT routing'in kapasite öncesi hâline döner. Registry doluluğu bu durumda da izlenmeye devam eder.
- Registry JVM içi olduğu için restart'ta boşalır; kısa süre kapasite boş görünür, cooldown ve `fct.channel.headroom.percent` bu pencereyi absorbe eder. Startup'ta bir kez cold start WARN'u basılır.

## Queue ve Kapasite

`CallConsumer`, campaign concurrent lines ve soft limit gibi değerlerle queue büyüklüğünü denetler. `CallOriginator`, server/location uygunluğuna göre farklı kampanya queue'larından çağrı tüketebilir. Bu nedenle queue davranışı yalnızca tek sınıfta değil, producer, consumer, originator ve campaign state birlikte incelenerek değiştirilmelidir.

## Mükerrer Fetch Koruması

Aynı kaydın iki kez `Call` olup iki kez aranmasına karşı iki katman vardır (FIX-330538):

- DB rezervasyonu: `CampaignDbOperations.reserveRowsForFetch`, fetch edilen satırları `is_fetched = <now>` ile yalnız `is_fetched IS NULL` koşuluyla kapar (optimistic guard). Etkilenen satır sayısı batch'ten küçükse bu fetch'in işaretini taşıyan satırlar tek bir yeniden-okuma ile saptanır ve rezervasyonu alınamayan uniqueKey'ler çağırana döner; `CallProducer` bu satırlar için `Call` üretmez. Fail-open kuralı: yeniden-okuma hiçbir satırı doğrulayamazsa (kolon tipi/literal dönüşümü/hatalı rowcount kaynaklı güvensizlik) batch bugünkü davranışla korunur ve hiçbir satır düşürülmez; düşürme yalnız yeniden-okuma en az bir satırı doğruladığında yapılır. Böylece bu koruma hiçbir tabloda kampanyayı durduramaz.
- In-memory guard: `CallProducer`, her fetch tick'inin başında `dataQueue` ve consumer `callQueue`'sundaki canlı `Call`'ların uniqueKey'lerini runtime state'ten türetir (`activeUniqueKeys`); DB aynı satırı yeniden döndürse bile canlı kopyası olan satır için yeni `Call` üretilmez. Set her tick'te sıfırdan kurulur ve tick sonunda temizlenir; ayrı bir release noktası yoktur, reload/stop drain'i ve normal retry akışı etkilenmez. `Campaign.originatedCalls` expiry'siz olduğu için bilinçli olarak kapsam dışıdır.

Her iki katman da yalnız anomali yolunda ek iş yapar ve tick başına tek satırla (tek ActionLog kaydı) raporlanır. Kalıcı kayıt kaybına yol açabilecek yön (satır DB'de fetched ama cache'de yok) her iki katmanda da bilinçli olarak engellenir.

FIX-333599 ile iki genişletme yapıldı:

- **In-memory guard görüşmedeki çağrıları da kapsar.** `ManagerConnectionDecorator.collectInFlightUniqueKeys`, açık her bağlantının `calls`, `awaitingUserResultCodeCalls`, `recoverableConnectionLossCalls` ve `orphanedCalls` cache'lerinden kampanyaya ait uniqueKey'leri toplar. Dört cache'in de expiry'si olduğu için takılan bir çağrı satırı kalıcı olarak kilitlemez.
  - Gerekçe: bayat bir kopya `Call`, dial (`handleCallCreation`) ve answer (`handleCallAnswer`) anındaki `insertCallResultToDB` ara yazımıyla önceki çağrının `callback_date` ve `user_result_code` değerlerini siler. Satır yeniden Fresh sorgusuna uyar ve döngü kendini besler.
  - Vaka: 02.10.2026'da contact 326361 11 dakikada 6 kez arandı.
- **Startup DB check cleanup kardeş kampanyanın rezervasyonlarını korur.** Cleanup (`StopState.checkForUnfinishedCalls` → `removeFetchedDataFlagFromUnfinishedCalls`) kampanya filtresi taşımaz, tablo geneline uygulanır.
  - Yalnız kesmeden önceki rezervasyonlar temizlenir: `AND is_fetched < <literal>`. Kesme = min(cleanup'ın başladığı an, aynı bağlantı ve result tablosunu kullanan canlı kardeşlerin (`SharedResultTableSiblings`) en erken STOP→START anı).
  - Kardeş anı `CampaignContext.liveSince` alanında tutulur; `StopState.registerAsLive` içinde context haritaya girmeden önce set edilir. `startedDate` bu iş için kullanılmaz, çünkü unpause'ta da güncellenir.
  - Cleanup anı kardeş taramasından önce alınır (05.10.2026). FIX-333607'den beri farklı kampanyaların start'ları paralel çalışır; kardeş bu taramadan sonra canlıya geçebilir. Kardeş ancak `registerAsLive` sonrasında rezervasyon yapabildiği için, taramada görünmeyen kardeşin rezervasyonları cleanup anından sonradır ve korunur.
  - Kardeş yokken de kesme uygulanır. Kampanya bu koşuda henüz rezervasyon yapmadığından (producer `super.start()` ile başlar) önceki koşudan kalan rezervasyonların hepsi cleanup anından eskidir ve temizlenmeye devam eder.
  - Sınır: farklı dialer node'larında çalışan kampanyalar `campaignContexts`'te görünmez. Onların cleanup anından sonraki rezervasyonları korunur, önceki rezervasyonları korunmaz; node saatleri farklıysa koruma kısmidir.

## Predictive Alanı

Kodda alpha, beta ve gamma predictive akışları ayrı paketlerde bulunur:

- `gbpowerdialer/src/main/java/com/powerdialer/engine/boost`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/predictive/beta`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/predictive/gamma`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/predictive/dispatcher`
- `gbpowerdialer/src/main/java/com/powerdialer/redis/event/predictive`

Bu alanlar campaign counter'ları, agent kapasitesi, ring/answer oranları ve Redis predictive event mesajlarıyla bağlantılıdır.

## Yerel Bellek Bağımlılıkları

- `DialerFacade.campaignContexts`
- `DialerFacade.asteriskCallOriginators`
- `FctChannelRegistry` numara bazlı in-flight kanal rezervasyonları (TTL ile self-healing; restart'ta boşalır)
- `Campaign.originatedCalls`
- `Campaign.callsInProgress`
- `Campaign` active/ringing/in-call/live/retry counter'ları
- Predictive manager map'leri
- Originator executor ve queue state'i

## Yatay Ölçek Riski

Birden fazla node aynı kampanya veya server üzerinde çalışırsa:

- Aynı veri iki kez fetch edilebilir.
- Aynı çağrı iki kez originate edilebilir.
- Counter'lar node bazında farklılaşabilir.
- Redis event'leri farklı node'larda aynı state'i farklı sırada güncelleyebilir.
- Asterisk callback veya event tek node'a bağlı state'i bulamayabilir.

## Change Contract

- Queue davranışı değiştirilmeden önce producer, consumer ve originator birlikte test edilir.
- Çağrı seçme veya retry davranışı değişirse aynı çağrının tekrar originate edilmemesi testle korunur.
- `createRetry` kontrol sırası (başarılı kapanış → genel limit → numara başına limit → kullanılmayan numara → stratejinin numara seçimi) korunur; `isCallAnswered()` yan etkisi nedeniyle her zaman ilk değerlendirilir. Kullanılmayan numara dalı değişirse `RetryStrategyBadNumberNextNumberFeatureTest` (6 strateji) ve `RetryStrategyAmdFamilyBaselineCharacterizationTest` birlikte koşulur.
- Numara eligibility predicate'i altı stratejinin tamamını etkiler. Değişiklikten önce `RetryStrategySixStrategiesBaselineCharacterizationTest` ile mevcut davranış kilitlenir ve `MaxAttemptPerNumberFeatureOffRegressionTest` ile alan boşken çıktının aynı kaldığı doğrulanır.
- Numara yürüyüşüne yeni koşul eklenirken bounded-walk sigortası (`advanceRetryNumberPastIneligible`) kaldırılmaz; `RetryDncExhaustionInfiniteLoopRegressionTest` silinmez.
- Runtime counter'lar için increment/decrement eklemek yerine çağrı listesi veya event log gibi kaynaklardan türetme tercih edilir.
- Yeni Go servis denemesi önce shadow-read veya shadow-calculate olarak çalışmalıdır.
