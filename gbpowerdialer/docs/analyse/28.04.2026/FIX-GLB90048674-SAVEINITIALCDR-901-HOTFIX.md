# FIX SaveInitialCDR 901 Hotfix

## Bağlam

`saveInitialCDR` ile `pd_cdr` satırı erken açılan çağrılarda kayıtların gerçek AMI sonucuyla kapanması beklenirken bazı kayıtlar cleanup sırasında 901 ile kapanıyor. Problem, özellikle `CDRService.java`, `ManagerConnectionDecorator.java` ve `CallOriginator.java` içinde `save` çağrılarının `saveOrUpdate` yapılmasından sonra görünür hâle geldi. İncelenen commit'ler:

- `5b918f94a8145d40be2404abc4829e05d1056b84`
- `5e99e9a1e6f59fa4bb0234e05beeacc1e917c2bd`

Bu analiz hotfix kapsamındadır. Amaç geniş refactor yapmadan, `saveInitialCDR` ile erken persist edilmiş CDR satırlarının AMI hangup sonucunu kaçırmamasını sağlamak ve cleanup'ın gerçek AMI sonucunu 901 ile ezmesini engellemektir.

`callEndTime` alanının `saveInitialCDR` içinde set edilmesi mevcut DB contract'ı gereği bilinçli davranış kabul edilmiştir; bu analizde değiştirilmez.

## İş Etiketi

fix

Öncelik: hotfix

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallOriginator.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionDecorator.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/CallCleaner.java`
- `gbpowerdialer/src/main/java/com/powerdialer/service/CDRService.java`
- `gbpowerdialer/src/main/java/com/powerdialer/dao/impl/GenericDao.java`
- `gbpowerdialer/src/main/java/com/powerdialer/redis/subscriber/WebphoneSubscriber.java`
- `gbpowerdialer/src/test/java/tests/...` altında eklenecek hedefli regression testleri

## Mevcut Davranış

- `CallOriginator.processOrigination()` başarılı origination sonrası `saveInitialCDR(call)` çağırıyor.
- `saveInitialCDR` aynı `CDR` nesnesini `saveOrUpdate` ile erken persist ediyor.
- `ManagerConnectionDecorator.handleDialerHangup()` AMI `DialerHangupEvent` geldiğinde `call.telephonyResultCode` alanını gerçek AMI mapping sonucu ile set ediyor.
- Çağrı kullanıcı result code bekliyorsa `callIsWaitingForUserResultCode(call)` true dönüyor ve `processCallResult(call, event.getDateReceived())` çağrılmıyor.
- Bu branch'te CDR satırı AMI hangup sonucu ile güncellenmiyor.
- Kullanıcı result code hiç gelmezse çağrı cache'te kalıyor.
- `CallCleaner.clearExpiredCall()` çağrıyı cleanup ederken mevcut `call.telephonyResultCode` değerini koşulsuz 901 ile eziyor ve `saveCDR(call)` ile CDR'yi 901 olarak kapatıyor.

## Beklenen Davranış

- `saveInitialCDR` ile erken açılmış CDR satırı, AMI `DialerHangupEvent` geldiği anda aynı `interactionId`/aynı `CDR` nesnesi üzerinden gerçek `telephonyRC` ve event zamanı ile güncellenmeli.
- Kullanıcı result code bekleniyorsa `processCallResult` yine ertelenebilir; ancak CDR'nin telephony tarafındaki final sonucu cleanup'a bırakılmamalı.
- Cleanup yalnızca gerçekten AMI sonucu alınmamış çağrıları 901 ile kapatmalı.
- AMI sonucu önceden alınmış ve erken persist edilmiş CDR satırında 901, gerçek AMI sonucunu overwrite etmemeli.
- `saveOrUpdate` duplicate CDR problemini çözmek için bırakılmalı; hotfix doğrudan `save` revert'i yapmamalı.

## Kod Okuma Bulguları

- `GenericDao.saveOrUpdate()` `saveOrUpdate(o)` sonrası `flush()` çağırıyor. Bu, `CDRService.saveOrUpdate()` kullanan AMI event path'lerinde senkron DB yazımı anlamına geliyor.
- `5e99e9a1` değişikliği duplicate CDR riskini azaltmak için CDR yazımlarını `saveOrUpdate` yapmış görünüyor. Bu değişikliği doğrudan geri almak eski çoklama problemini geri getirebilir.
- `saveOrUpdate + flush`, DB lock veya yavaşlık varsa AMI event işleme süresini artırabilir. Bu durum problemi büyütebilir; ancak 901'in doğrudan mantıksal sebebi, AMI hangup sonucunun kullanıcı result code beklenen branch'te CDR'ye yazılmaması ve cleanup'ın sonradan 901 ile overwrite etmesidir.
- `handleDialerHangup()` içinde `processCallResult` çağrılmadığı durumda CDR snapshot persist eden bir fallback yok.
- `CallCleaner.clearExpiredCall()` mevcut telephony result code dolu olsa bile 901 set ediyor.
- `WebphoneSubscriber.processCallWithResultCodeAndUpdateDNC()` kullanıcı result code geldiğinde `processCallResult` çağırabiliyor; kullanıcı result code gelmezse bu akış çalışmıyor.
- `saveInitialCDR` ile persist edilmiş kayıtlar `cdr.id != null` üzerinden ayırt edilebilir. Hotfix etkisi bu kayıtlarla sınırlandırılabilir.

## Riskler

- `handleDialerHangup` AMI hot path'tedir; her event'te yeni query, collection scan veya geniş log eklenmemeli.
- CDR güncellemesi zaten mevcut `saveOrUpdate` maliyetini taşıyor; hotfix en fazla problemli branch'te bir ek update yapmalı.
- `processCallResult` akışı tetiklenmeden CDR snapshot yazılacağı için campaign counter, retry, DNC ve user result code davranışı değiştirilmemeli.
- `CallCleaner` içinde 901 overwrite davranışını tamamen kaldırmak eski cleanup contract'ını bozabilir. Bu nedenle koruma erken persist edilmiş ve AMI sonucu zaten set edilmiş kayıtlarla sınırlanmalı.
- `saveOrUpdate` revert'i hotfix değildir; duplicate CDR regresyonu doğurabilir.

## TDD Stratejisi

- `ManagerConnectionDecorator` için regression testi:
  - `saveInitialCDR` ile persist edilmiş CDR'ye sahip, answer almış ve kullanıcı result code bekleyen bir call hazırlanır.
  - `DialerHangupEvent` işlendiğinde `processCallResult` koşulu false kalır.
  - Buna rağmen aynı CDR'nin gerçek AMI `telephonyRC` ve event zamanı ile `saveOrUpdate` edildiği doğrulanır.
- `CallCleaner` için regression testi:
  - Persist edilmiş CDR'ye sahip ve `telephonyResultCode` daha önce AMI'dan set edilmiş call cleanup'a girer.
  - Cleanup sonucunda telephony result code 901 ile ezilmez.
- Kontrol testi:
  - AMI sonucu hiç olmayan call cleanup'a girerse mevcut 901 davranışı korunur.
- Testler hedefli JUnit/Mockito kapsamıyla tutulur; tam suite yalnızca hedefli testlerden sonra gerekirse çalıştırılır.

## Önerilen Çözüm Yönü

1. `ManagerConnectionDecorator.handleDialerHangup()` içinde `processCallResult` çağrısı yapılacak mı bilgisi boolean olarak hesaplanır.
2. `processCallResult` çağrılmayacaksa ve call erken persist edilmiş CDR'ye sahipse küçük bir private method ile CDR hangup snapshot'ı yazılır:
   - `cdr.setTelephonyRC(call.getTelephonyResultCode())`
   - `cdr.setCallEndTime(event.getDateReceived())`
   - `cdr.setServer(server)`
   - `cdr.setRcSequence(call.getResultCodeSequence())`
   - mevcut user ownership ve filter alanları bozulmadan korunur; gerekliyse mevcut populate helper kullanılır.
3. `CallCleaner.clearExpiredCall()` içinde 901 set etmeden önce erken persist edilmiş CDR + mevcut AMI telephony result kontrol edilir:
   - AMI sonucu yoksa mevcut 901 davranışı korunur.
   - AMI sonucu varsa 901 overwrite edilmez ve `saveCDR` mevcut sonucu yazar.
4. `saveOrUpdate` revert edilmez. Hotfix sonrasında ayrı ölçüm ihtiyacı kalırsa `CDRService.saveOrUpdate` için threshold bazlı süre log'u ayrı iş olarak ele alınır.
5. Eksik correlation teşhisi için `handleDialerHangup` içinde empty `OriginId` veya call-not-found path'lerine düşük hacimli warn log eklenmesi değerlendirilebilir; hotfix'in zorunlu parçası değildir.

## Performans Etkisi

Hot path: Evet, AMI `DialerHangupEvent` başına çalışır.

- Yeni collection scan yok.
- Yeni DB query yok.
- Problemli branch'te mevcut CDR nesnesine bir ek `saveOrUpdate` yapılır.
- Ek update yalnız `processCallResult` çağrılmayan ve CDR'si erken persist edilmiş çağrılarla sınırlanır.
- Cleanup tarafındaki değişiklik event hot path'te değildir; sadece mevcut 901 overwrite kararını daraltır.

Sonuç: Hot path'te sınırlı ek DB update vardır; 901 veri kaybını engellemek için kabul edilebilir. Daha geniş performans iyileştirmesi bu hotfix kapsamına alınmaz.

## Açık Sorular ve Netleştirme İhtiyacı

- Hotfix davranışı yalnız `cdr.id != null` olan erken persist edilmiş kayıtlarla mı sınırlandırılacak? Öneri: Evet.
- `handleDialerHangup` içinde persist edilen snapshot `rcSequence` artırmalı mı? Öneri: Hayır; `processCallResult` çalışmadığı için retry/result lifecycle ilerletilmemeli, sadece CDR telephony snapshot güncellenmeli.
- Empty `OriginId` ve call-not-found logları bu hotfix'e dahil edilsin mi? Öneri: Kod davranışı değiştirmediği için dahil edilebilir, ancak acil hotfix patch'ini küçük tutmak için ayrı bırakılabilir.

## Execution Plan Zorunluluğu

Kod değişikliği gerekiyor. Execution plan `docs/execution-plan/28.04.2026/FIX-GLB90048674-SAVEINITIALCDR-901-HOTFIX.md` altında, geliştirici bu analizi ve önerilen dar hotfix planını onayladıktan sonra oluşturulacak. Plan, hedefli regression testlerini, uygulama adımlarını, doğrulama komutlarını ve başarılı test kapanış kriterini içerecek.
