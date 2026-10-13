# FIX Campaign Start Stop CDR Cache Uygulama Planı

## Analiz Referansı

`docs/analyse/30.04.2026/FIX-GLB90048674-CAMPAIGN-START-STOP-CDR-CACHE.md`

## Onaylanan Plan Özeti

Hotfix dar kapsamda uygulanacak. `saveOrUpdate` revert edilmeyecek. `pd_cdr.interaction_id` production DB'de unique contract'a sahip olduğu için duplicate CDR önleme local cache guard'ına dayandırılmayacak. `CallOriginator` initial CDR create kararını `connectionDecorator.getCalls().getIfPresent(call.getId())` kontrolünden çıkaracak ve originate sonucunu explicit success/failure contract'ıyla ifade edecek. `ManagerConnectionDecorator` answer/agent-answer path'lerinde `cdr.id == null` durumunu normal update gibi insert'e düşürmeyecek; missing initial CDR branch'i interactionId üzerinden kontrollü repair yapacak.

## Amaç

- Başarılı originate sonrası initial CDR'nin sessizce atlanmasını engellemek.
- Answer/agent-answer anında `call_end_time NULL` insert hatasını kapatmak.
- Duplicate CDR problemini cache varlığı yerine `interactionId` tekillik contract'ıyla uyumlu çözmek.
- Campaign regular stop'un kalıcı `callsInProgress` ve CDR lifecycle drift'i nedeniyle takılma riskini azaltmak.

## Kapsam Dışı

- `saveOrUpdate` davranışını toplu revert etmek.
- `pd_cdr.call_end_time` DB contract'ını değiştirmek.
- Campaign start/stop state machine'ini geniş refactor etmek.
- AMI reconnect/redeploy operasyon standardını bu hotfix içinde yeniden tasarlamak.
- CDR persistence'ı async queue veya yeni altyapıya taşımak.

## Performans Etkisi

- Hot path durumu: `CallOriginator.processOrigination`, AMI answer/agent-answer event path'i ve campaign stop cleanup hot path kabul edilir.
- Call/event/tick başına ek maliyet: Normal persisted CDR update path'inde yeni DB lookup olmayacak. Ek interactionId lookup yalnız `cdr.id == null` anomaly branch'inde çalışacak.
- IO/scan/lock/serialization/log etkisi: Yeni tam koleksiyon taraması veya geniş lock eklenmeyecek. Anomaly branch'inde tekil indexed lookup ve sınırlı warn log kabul edilir.
- Azaltım veya ölçüm notu: `interactionId` unique index olduğu için lookup bounded kabul edilir. Normal path `cdr.id != null` kısa devre ile mevcut update maliyetinde kalır.

## TDD Planı

- [x] `CallOriginator` için başarılı originate sonrası initial CDR'nin cache guard'a bağlı kalmadan yazıldığını kanıtlayan regression testi.
- [x] `CallOriginator` için originate action başarısız/exception branch'inde initial CDR yazılmadığını kanıtlayan kontrol testi.
- [x] `ManagerConnectionDecorator` için `cdr.id == null` answer event'inde normal `saveOrUpdate` insert denenmediğini kanıtlayan regression testi.
- [x] `ManagerConnectionDecorator` için `cdr.id == null` ve DB'de interactionId ile mevcut CDR varsa mevcut CDR'nin `callEndTime` korunarak answer alanlarıyla güncellendiğini kanıtlayan test.
- [x] `ManagerConnectionDecorator` için `cdr.id == null` ve DB'de interactionId yoksa missing initial CDR repair'in DB contract'ına uygun initial CDR oluşturduğunu kanıtlayan test.
- [x] Persist edilmiş CDR path'inde answer/agent-answer event'lerinin `callEndTime` değerini ezmediğini kanıtlayan regression testi.
- [x] Campaign/callsInProgress için, final cleanup çalıştığında regular stop'u bloke edecek kalıcı call bırakılmadığını mevcut uygun boundary'de karakterize eden test.

## Uygulama Kontrol Listesi

- [x] Onaylanan planın tüm adımları execution plan'a işlendi.
- [x] `.codex/TOOL-PATHS.local.properties` kontrol edilerek hedefli test komutları hazırlandı.
- [x] `CallOriginator` testlerinde originate success/failure ve initial CDR create contract'ı kırmızı hâle getirildi.
- [x] `ManagerConnectionDecorator` testlerinde missing initial CDR answer/agent-answer contract'ı kırmızı hâle getirildi.
- [x] `CallOriginator.processOrigination()` içinde `connectionDecorator` local snapshot'ı ve explicit originate sonucu contract'ı uygulandı.
- [x] `ManagerConnectionDecorator.originateCall()` başarı/başarısızlık sonucunu çağırana açık döndürecek şekilde dar kapsamda düzenlendi.
- [x] `saveInitialCDR` çağrısı cache guard yerine explicit originate sonucuna bağlandı.
- [x] Answer/agent-answer CDR update işlemi `cdr.id != null` normal path ve `cdr.id == null` missing-initial-CDR path olarak ayrıldı.
- [x] Missing initial CDR branch'i interactionId ile mevcut CDR'yi bulacak, yoksa DB contract'ına uygun initial CDR repair yapacak şekilde eklendi.
- [x] Duplicate CDR riskine karşı interactionId unique contract'ı ve DAO davranışı testle kilitlendi.
- [x] Hedefli testler yeşile çevrildi.
- [x] Gerekirse küçük refactor yapıldı ve testler tekrar çalıştırıldı.
- [x] Performans etkisi ve anomaly log sınırları gerçek patch'e göre güncellendi.
- [x] Execution plan checkbox'ları uygulama sırasında güncellendi.
- [x] History kaydı tamamlandı.

## Doğrulama

Çalıştırılan hedefli doğrulama:

```text
mvn -o -s C:/Users/GLB90048674/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90048674/.m2/repository -Dtest=CallOriginatorTest,InitialCdrOriginationRegressionTest,SaveInitialCdrHangupResultRegressionTest test
```

Sonuç: Başarılı. 19 test çalıştı, failure/error yok.

## Kapanış Kriteri

- [x] Initial CDR create kararı cache guard'a bağlı değil.
- [x] Answer/agent-answer path `cdr.id == null` durumunda `call_end_time NULL` insert'e düşmüyor.
- [x] Duplicate CDR contract'ı `interactionId` tekilliğiyle uyumlu.
- [x] Campaign regular stop'u bloke eden kalıcı `callsInProgress` regresyonu için hedefli test veya karakterizasyon var.
- [x] Planlanan hedefli testler çalıştırıldı ve başarılı oldu.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Riskler ve Geri Dönüş

- Risk: Missing initial CDR repair branch'i hot path'e DB lookup ekleyebilir. Azaltım: yalnız `cdr.id == null` anomaly durumunda çalışacak.
- Risk: Originate failure contract'ı yanlış kurulursa başarısız call için CDR eksikliği veya fazla CDR oluşabilir. Azaltım: success/failure testleri birlikte yazılacak.
- Risk: Duplicate CDR tekrar ederse unique constraint exception üretir. Azaltım: interactionId bazlı mevcut kayıt lookup ve idempotent update testle kilitlenecek.
- Geri dönüş: `CallOriginator` originate result contract değişikliği ve `ManagerConnectionDecorator` missing CDR guard'ı birlikte geri alınabilir; ancak `saveOrUpdate` toplu revert edilmeyecek.

## Tamamlanma Notu

Uygulama tamamlandı. `CallOriginator` initial CDR kararını cache okumasından çıkardı ve `ManagerConnectionDecorator.originateCall()` sonucuna bağladı. Answer/agent-answer path'lerinde `cdr.id == null` anomalisinde önce `interactionId` ile mevcut CDR aranıyor; kayıt yoksa initial CDR snapshot'ı `callEndTime` boş bırakılmadan tamamlanıyor. Final cleanup CDR save exception'ında campaign progress state'in temiz kaldığı regression testle kilitlendi.
