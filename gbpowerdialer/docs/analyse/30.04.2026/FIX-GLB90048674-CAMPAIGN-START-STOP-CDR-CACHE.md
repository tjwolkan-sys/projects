# FIX Campaign Start Stop CDR Cache

## Bağlam

30.04.2026 sabahı, gece devreye alınan paket sonrasında agent'lar hatta girdikten sonra bazı agent'ların `Available` durumda kaldığı, çağrı çıkışlarının aksadığı ve campaign stop/start işlemlerinde takılma görüldüğü bildirildi. Gözlenen semptomlar:

- Campaign start denemesinde state kısa süre `STARTING` görünüp `STOPPED` durumuna dönebiliyor.
- Running durumdaki campaign'ler düzenli stop ile durmayabiliyor.
- Stopped durumdaki campaign'ler tekrar açılamayabiliyor.
- Liste remaining/data bilgisi beklenen şekilde görünmeyebiliyor.
- Loglarda aynı zaman aralığında yoğun CDR persistence hatası bulunuyor.

İnceleme `gbpowerdialer.log`, son iki haftalık CDR/campaign lifecycle commit'leri ve ilgili runtime sınıfları üzerinden yapıldı. Dokümanda production log içindeki müşteri, telefon ve ortam IP detayları bilinçli olarak taşınmadı.

## İş Etiketi

fix

## Öncelik

hotfix

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallOriginator.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionDecorator.java`
- `gbpowerdialer/src/main/java/com/powerdialer/service/CDRService.java`
- `gbpowerdialer/src/main/java/com/powerdialer/dao/impl/GenericDao.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CampaignStopJob.java`
- `gbpowerdialer/src/main/java/com/powerdialer/model/Campaign.java`
- `gbpowerdialer/src/main/java/com/powerdialer/redis/WebphoneSubscriber.java`

## Mevcut Davranış

`CallOriginator.processOrigination()` çağrıyı originate ederken önce call'ı server call queue'suna ekliyor, sonra `ManagerConnectionDecorator.originateCall(call)` çağırıyor. `originateCall` içinde `calls.put(call.getId(), call)` yapılıyor ve action Asterisk'e gönderiliyor.

Initial CDR kaydı ise originate sonrasında şu koşula bağlanmış durumda:

```java
if (connectionDecorator.getCalls().getIfPresent(call.getId()) != null) {
  saveInitialCDR(call);
}
```

`saveInitialCDR(call)` çalışırsa CDR üzerinde `callEndTime = new Date()` set ediliyor ve `saveOrUpdate` ile erken CDR satırı açılıyor. Bu erken satır, answer/agent answer/hangup event'lerinde aynı CDR entity'si üzerinden güncelleniyor.

Problemli log örneklerinde `Call in progress added` ve `Originate successfully queued` kayıtları görülüyor; ancak hemen ardından beklenen `CDRService - CDR{interactionId=...} is saved/updated successfully` kaydı yok. Buna rağmen answer event'i call'ı cache'te buluyor ve `ManagerConnectionDecorator.handleCallAnswer()` / `handleAgentAnswer()` içinde `cdrService.saveOrUpdate(call.getCdr())` çağrısı yapılıyor.

Bu noktada `call.getCdr().getId()` henüz oluşmamışsa Hibernate update değil insert davranışına düşüyor. Answer path'inde `callEndTime` set edilmediği için DB `pd_cdr.call_end_time` NOT NULL contract'ı insert'i reddediyor.

## Beklenen Davranış

- Originate başarıyla runtime'a alındığında aynı interaction için tek CDR satırı erken açılmalı.
- Answer ve agent-answer event'leri yeni CDR insert etmemeli; yalnız mevcut/persist edilmiş CDR'yi güncellemeli.
- `call_end_time` alanı early persistence contract'ında boş bırakılmamalı.
- Duplicate CDR önleme local cache varlığına değil, CDR persistence contract'ına dayanmalı.
- Campaign regular stop, tamamlanmış veya cleanup'a girmiş call'ların `callsInProgress` içinde kalması nedeniyle 1 saate kadar beklememeli.

## Kod Okuma Bulguları

### 1. Initial CDR cache guard'ı duplicate çözümü değil

`saveInitialCDR` için eklenen `getCalls().getIfPresent(call.getId())` koşulu commit geçmişinde "sadece başarılı origination sonrası" gerekçesiyle gelmiş görünüyor. Duplicate CDR sorununun asıl çözümü aynı dönemde `save` çağrılarının `saveOrUpdate` yapılmasıyla ele alınmış:

- `handleCallAnswer`: `cdrService.save(...)` -> `saveOrUpdate(...)`
- `handleAgentAnswer`: `cdrService.save(...)` -> `saveOrUpdate(...)`
- `processCallResult`: `cdrService.save(...)` -> `saveOrUpdate(...)`
- `saveInitialCDR`: `cdrService.save(...)` -> `saveOrUpdate(...)`

Bu nedenle cache guard duplicate'i çözmüyor; initial CDR'nin sessizce atlanabileceği bir failure mode yaratıyor.

### 2. Aynı decorator instance'ında cache miss beklenmez

`ManagerConnectionDecorator.originateCall()` içinde ilk işlem `calls.put(call.getId(), call)`. `calls` Guava cache'inin expire süresi `Constants.CALL_EXPIRE_TIMEOUT = 3600000` ms. Problemli örneklerde answer saniyeler içinde geldiği için normal expire açıklaması geçerli değil.

Aynı `ManagerConnectionDecorator` instance'ı üzerinde `put` sonrası hemen yapılan `getIfPresent` null dönüyorsa bu normal cache davranışı değil; owner instance ayrışması, cleanup, reconnect veya runtime paket farkı gibi bir anomaliye işaret eder.

### 3. ManagerConnectionDecorator owner ayrışması mümkün

`CallOriginator.connectionDecorator` mutable bir alan. `setNewConnectionDecoratorIfExists()` içinde aynı server için `ManagerConnectionComposition.openedConnections` map'indeki yeni decorator referansı mevcut originator'a atanıyor ve local `callQueue` yenileniyor.

LSC path'i async executor üzerinden `processOrigination()` çalıştırıyor. Bu sırada originator scheduler thread'i connection loss/reconnect akışında `connectionDecorator` referansını değiştirebilir. Bu durumda call bir decorator cache'ine yazılıp initial CDR kontrolü başka decorator cache'i üzerinden yapılabilir.

Bu ihtimal tüm örnekleri tek başına açıklamayabilir; ancak cache owner'lığının şu anda tek ve immutable bir runtime boundary olmadığını gösterir.

### 4. Restart/redeploy dönemi state drift riskini büyütüyor

Logda problem aralığından hemen önce ve sırasında birden fazla Spring context / DialerEngine başlangıcı görülüyor. Bu durum eski AMI event reader'ları, yeni originator instance'ları ve in-memory cache'ler arasında kısa süreli state ayrışması yaratabilir.

Bu ayrışma sırasında Asterisk/Webphone event'leri eski call'lar için gelmeye devam ederken yeni JVM/Spring context kendi local state'ini oluşturuyor olabilir. Bu, `calls`, `callsInProgress`, agent state ve CDR entity id owner'lığı için riskli bir dönemdir.

### 5. Answer path CDR id yokluğunu ayırt etmiyor

`ManagerConnectionDecorator.handleCallAnswer()` ve `handleAgentAnswer()` içinde `call.getCdr() != null` kontrolü yeterli kabul edilip `saveOrUpdate` çağrılıyor. Ancak `call.getCdr()` in-memory entity'dir; DB'den interaction id ile getirilmiş persisted kayıt anlamına gelmez.

`cdr.id == null` ise answer path yeni insert'e düşebilir. Answer path `callEndTime` set etmediği için bu insert DB contract'ına aykırı olur.

### 6. Campaign regular stop bu drift'ten etkilenir

`CampaignStopJob` regular stop sırasında `callsInProgress` tamamlanmasını bekler ve timeout 1 saattir. Eğer CDR/answer/hangup/result event sıralamasında call cleanup tamamlanmazsa veya `callsInProgress` boşalmazsa campaign stop takılmış gibi görünür.

Logdaki CDR insert hataları answer/agent-answer event thread'lerinde yoğunlaştığı için, call lifecycle state'inin temizlenememesi ve campaign stop beklemesi aynı bug class'ının yan etkisi olabilir.

### 7. Camp tablosu eksik kolon hipotezi bu logda desteklenmedi

İncelenen logda `pd_campaign` fetch sırasında son eklenen alanlardan kaynaklanan bir invalid column hatası görünmedi. Ayrı bir scheduler/mirror akışında farklı kolon hatası var; ancak campaign start/stop ana semptomunu açıklayan güçlü bulgu CDR/cache lifecycle tarafında.

## Riskler

- `saveOrUpdate` doğrudan revert edilirse eski duplicate CDR problemi geri gelebilir.
- Answer path'te körlemesine `callEndTime = new Date()` set etmek semptomu azaltabilir; ancak initial CDR'nin neden oluşmadığını gizler ve CDR zaman contract'ını bulanıklaştırır.
- Initial CDR'yi tekrar originate öncesine almak başarısız originate'lar için istenmeyen erken CDR yaratabilir.
- `connectionDecorator` referansını geniş synchronized bloklarla korumak call hot path'inde throughput ve latency riski doğurabilir.
- `interactionId` bazlı DB lookup answer hot path'ine eklenirse call başına DB maliyeti yaratır; yalnız anomaly branch'ine sınırlandırılmalıdır.
- Restart/redeploy sırasında eski event reader ve yeni context üst üste çalışıyorsa yalnız kod fix'i yeterli olmayabilir; deploy/stop-start operasyon sırası ayrıca doğrulanmalıdır.

## TDD Stratejisi

1. `CallOriginator` için regression/karakterizasyon testi:
   - `originateCall` başarılı olup call runtime'a alındığında initial CDR'nin yazıldığını kanıtla.
   - Cache guard false olduğunda initial CDR'nin sessiz atlanmasının mevcut bug class'ını oluşturduğunu göster.
   - Düzeltme sonrası initial CDR create kararının cache miss'e kırılgan kalmadığını doğrula.

2. `ManagerConnectionDecorator` için regression testi:
   - Answer event'i geldiğinde `cdr.id == null` olan call için normal answer update gibi insert denenmediğini doğrula.
   - Persist edilmiş CDR varsa answer/agent-answer event'lerinin `callEndTime` değerini ezmeden update ettiğini doğrula.

3. CDR service/DAO contract testi:
   - Aynı `interactionId` için duplicate insert yerine tek CDR contract'ı korunmalı.
   - Mümkünse `interactionId` unique contract'ı DB/mapping seviyesinde karakterize edilmeli.

4. Campaign lifecycle regression testi:
   - Result/hangup/answer ordering bozulduğunda `callsInProgress` içinde kalıcı call bırakılmadığı veya regular stop'un beklenen cleanup boundary'sine ulaştığı doğrulanmalı.

## Önerilen Çözüm Yönü

### Kısa vade hotfix

1. Initial CDR create kararını `connectionDecorator.getCalls().getIfPresent(call.getId())` guard'ından çıkarmak veya aynı local decorator snapshot'ı üzerinden deterministik yapmak:

```java
ManagerConnectionDecorator decorator = this.connectionDecorator;
decorator.originateCall(call);

if (decorator.getCalls().getIfPresent(call.getId()) != null) {
  saveInitialCDR(call, decorator);
}
```

Bu snapshot tek başına yeterli olmayabilir; daha doğru kısa vade yaklaşım, `originateCall` sonucunu explicit success/failure contract'ına çevirmek ve CDR create kararını buna bağlamaktır.

2. Answer/agent-answer path'te `cdr.id == null` guard'ı eklemek:
   - `cdr.id != null` ise mevcut CDR update edilir.
   - `cdr.id == null` ise normal answer update yapılmaz; explicit missing-initial-CDR branch'i çalışır.
   - Bu branch önce `interactionId` ile mevcut CDR arayabilir.
   - Kayıt yoksa initial CDR contract'ı tamamlanarak insert yapılmalı ve `callEndTime` DB contract'ına uygun set edilmelidir.
   - Bu anomaly branch'i net loglanmalıdır; sessiz fallback olmamalıdır.

3. Duplicate CDR önleme için local cache değil CDR contract owner'ı kullanılmalı:
   - `interactionId` bazlı tekil kayıt varsayımı netleştirilmeli.
   - DB unique index varsa DAO bu contract'a göre davranmalı.
   - DB unique index yoksa execution plan sırasında eklenip eklenmeyeceği ayrıca kararlaştırılmalı.

### Orta vade

- `CallOriginator.connectionDecorator` mutable owner'lığı daraltılmalı.
- Originator, originate işlemi boyunca aynı decorator snapshot'ı ile çalışmalı.
- Reconnect/restart döneminde eski event reader ve yeni context'in üst üste çalışmasını azaltacak shutdown/reconnect contract'ı yazılmalı.
- `callsInProgress`, `calls`, `originatedCalls` ve CDR persistence id owner'lığı tek lifecycle contract dokümanına bağlanmalı.

## Açık Sorular ve Netleştirme İhtiyacı

Geliştirici netleştirmesi:

- Production DB'de `pd_cdr.interaction_id` için unique index/constraint var.
- 30.04.2026 sabahı 09:31, 09:35 ve 09:37 civarındaki Spring context başlangıçları gerçek restart/redeploy.
- Hotfix yönü onaylandı: `originateCall` sonucunu explicit success/failure contract'ına çevirmek, initial CDR create kararını cache guard'dan çıkarmak ve answer/agent-answer path'te `cdr.id == null` için missing-initial-CDR repair branch'i eklemek.

Blocking açık soru kalmadı. `saveInitialCDR` guard'ının geçmiş duplicate gerekçesi execution plan içinde davranışa göre testlenecek; duplicate önleme owner'ı local cache değil `interactionId` tekillik contract'ı kabul edilecek.

## Execution Plan Zorunluluğu

Kod değişikliği gerekiyorsa execution plan `docs/execution-plan/30.04.2026/FIX-GLB90048674-CAMPAIGN-START-STOP-CDR-CACHE.md` altında, ancak açık sorular kapanıp geliştirici nihai planı onayladıktan sonra oluşturulacak. Plan, TDD test planını, hedefli Maven doğrulama komutlarını ve başarılı test kapanış kriterini içerecek.
