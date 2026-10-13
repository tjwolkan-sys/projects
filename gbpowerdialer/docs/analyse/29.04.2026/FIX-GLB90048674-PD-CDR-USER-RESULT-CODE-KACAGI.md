# FIX PD-CDR User Result Code Kaçağı

## Bağlam

AQOS üzerinde ses kaydı, Asterisk `ast_registrar_cdr` üzerinde CDR ve webphone interaction tarafında kayıt bulunmasına rağmen gbpowerdialer'ın `pd_cdr` tablosunda aynı çağrının `user_result_code_id` alanı boş kalıyor.

İncelenen örnek:

- `interaction_id`: `dl-4482d2f7-2439-426a-a9a7-3f6a42ee6f5a`
- `pd_cdr.id`: `13097428`
- `pd_cdr.telephony_rc`: `10`
- `pd_cdr.assigned_user_id`: `9508`
- `pd_cdr.connected_user_id`: `9508`
- `pd_cdr.call_end_time`: `2026-04-28 18:01:41.380`
- `pd_cdr.user_result_code_id`: `NULL`

Webphone result-code event'i loglarda görülmüştür:

```text
2026-04-28 17:51:00.488 ERROR [webphone-event-1] WebphoneSubscriber - Could not handle entity message {"type":"ADD","entityType":"RESULT_CODE","serializableObject":{"callId":"GIVXBSTRGRP01-1777387802.4748788","parentId":"dl-4482d2f7-2439-426a-a9a7-3f6a42ee6f5a","userId":9508,"resultCode":{"id":50,"resultCodeText":"KABUL ETMEDI FATURALARI FARKLI KISI ODUYOR","final":true,"dnc":false}}} java.lang.NullPointerException: null
```

Sonrasında aynı call logout/orphan handling sırasında hâlâ `userResultCode=null` olarak görülmüştür:

```text
2026-04-28 18:01:41.379 INFO [user-event-3] ManagerConnectionDecorator - Handling unfinished call for logout event: Call [... id=dl-4482d2f7-2439-426a-a9a7-3f6a42ee6f5a, telephonyResultCode=TelephonyResultCode [id=10, name=Normal call clearing, description=Answered], userResultCode=null, resultCodeSequence=1, user=User [id=9508, ...], completeDate=null, campaign=Campaign [id=74, name=TC_BIREYSEL_TAHSILAT_WELCOME], orphaned=false, answeredByAgent=true, ...]
```

Ekran görüntülerinde Asterisk ve webphone interaction kayıtlarının aynı parent zincirinde bulunduğu görülüyor. `ast_registrar_cdr` ve webphone interaction tarafındaki bitiş zamanı `2026-04-28 17:50:59` civarında, `pd_cdr.call_end_time` ise `2026-04-28 18:01:41.380`. Bu fark, `pd_cdr` kapanışının gerçek Asterisk/webphone bitişinden sonra delayed result veya cleanup benzeri başka bir path üzerinden yazılmış olabileceğini gösteriyor.

Bu analiz dünkü `saveInitialCDR` 901 hotfix'inin devamındaki yeni kaçak sınıfını inceler. Önceki hotfix CDR satırının AMI hangup sonucu ile kapanmasını sağlamıştı; bu analizde CDR satırı artık var, fakat kullanıcı result code'u CDR'a taşınmıyor.

## İş Etiketi

fix

Öncelik: hotfix

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionDecorator.java`
- `gbpowerdialer/src/main/java/com/powerdialer/redis/WebphoneSubscriber.java`
- `gbpowerdialer/src/main/java/com/powerdialer/service/CDRService.java`
- `gbpowerdialer/src/main/java/com/powerdialer/dao/ICDRDao.java`
- `gbpowerdialer/src/main/java/com/powerdialer/dao/impl/CDRDao.java`
- `gbpowerdialer/src/main/java/com/powerdialer/service/CampaignResultService.java`
- `gbpowerdialer/src/main/java/com/powerdialer/dao/impl/CampaignResultDao.java`
- `gbpowerdialer/src/main/java/com/powerdialer/util/UserPopulationEvaluator.java`
- `gbpowerdialer/src/test/java/com/powerdialer/redis/WebphoneSubscriberTest.java`
- Gerekirse yeni `tests/redis` veya `tests/persistence` regression testleri

## Mevcut Davranış

- AMI `DialerHangupEvent` geldiğinde `ManagerConnectionDecorator.handleDialerHangup()` `call.telephonyResultCode` alanını set ediyor.
- Çağrı user result code bekliyorsa `shouldProcessCallResultOnHangup()` false dönebiliyor ve `processCallResult()` erteleniyor.
- Dünkü hotfix ile bu ertelenen branch'te `persistHangupSnapshotForDelayedResult()` çalışıp `pd_cdr.telephony_rc`, `call_end_time`, ownership ve diğer snapshot alanlarını yazıyor.
- Bu anda `call.userResultCode` henüz yoksa `cdr.userResultCodeId` doğal olarak `NULL` kalıyor.
- Sonradan webphone Redis `RESULT_CODE` event'i gelirse `WebphoneSubscriber.processCallWithResultCodeAndUpdateDNC()` aktif `Call` nesnesini `ManagerConnectionDecorator.calls` cache'inde arıyor.
- Aktif `Call` bulunursa ve result code çağrıya atanmış user tarafından gönderilmişse `call.userResultCode` ve `call.cdr.userResultCodeId` set ediliyor; uygun koşulda `processCallResult()` çalışarak CDR'ı final hâle getiriyor.
- Aktif `Call` bulunamazsa fallback yalnız `campaignResultService.updateUserResultCode()` çağırıyor ve o da sadece `telephonyRC.code == TELEPHONE_RC_CAMPAIGN_IS_STOPPED_FORCEFULLY` olduğunda çalışıyor.
- Bu fallback `pd_cdr.user_result_code_id` alanını güncellemiyor.
- Result code event'i aktif call cache'ine yetişmezse, farklı user id ile reddedilirse veya hiç gelmezse `pd_cdr.user_result_code_id` boş kalabiliyor.

## Beklenen Davranış

- Asterisk/webphone/AQOS tarafında tamamlanan ve `pd_cdr` üzerinde başarılı bağlantı olarak görünen çağrılarda user result code event'i geldiyse, `pd_cdr.user_result_code_id` alanı da aynı result code ile tamamlanmalı.
- Result code event'i aktif `Call` cache'inde yakalanamazsa `interaction_id/parentId` ile `pd_cdr` üzerinden kontrollü fallback yapılmalı.
- Fallback, yanlış çağrıya veya yanlış agente result code yazmamalı; en azından `assigned_user_id` veya `connected_user_id` ile `callResultCode.userId` eşleşmesini doğrulamalı.
- Fallback idempotent olmalı; `pd_cdr.user_result_code_id` zaten doluysa overwrite etmemeli veya açık kural olmadan değiştirmemeli.
- Aynı fallback campaign result table `user_result_code` alanını da tamamlamalı; bu iş yalnız `pd_cdr` ile sınırlı kalmamalı.
- Redis payload contract'ı ve channel adı değişmemeli.
- Campaign result table update ve `pd_cdr` update contract'ları ayrıştırılmalı; biri yapıldı diye diğeri güncellenmiş kabul edilmemeli.

## Kod Okuma Bulguları

- `ManagerConnectionDecorator.handleDialerHangup()` içinde user result code beklenen branch'te `processCallResult()` çağrısı ertelenebiliyor ve dünkü hotfix ile sadece CDR hangup snapshot'ı yazılıyor.
- `persistHangupSnapshotForDelayedResult()` `cdr.setUserResultCodeId(call.getUserResultCode() != null ? call.getUserResultCode().getId() : null)` yapıyor; o anda user result code yoksa null persist ediliyor.
- `WebphoneSubscriber.handleResultCodeEntityMessage()` yalnız `parentId` `dl` ile başlıyorsa result code event'ini dialer çağrısı kabul ediyor.
- `WebphoneSubscriber.processCallWithResultCodeAndUpdateDNC()` aktif call bulunduğunda önce `shouldSetResultCode()` ile user eşleşmesini kontrol ediyor.
- Aktif call bulunmadığında mevcut fallback her opened connection iteration'ında tekrar `CDRService.getByInteractionId(parentId)` çağırabilecek şekilde loop içinde duruyor; bu hem gereksiz tekrar query riski taşıyor hem de fallback'i açık bir tekil karar hâline getirmiyor.
- Aktif call bulunmadığında fallback yalnız campaign result table için tasarlanmış; `pd_cdr.user_result_code_id` alanına yazmıyor.
- `CampaignResultDao.updateUserResultCode()` dynamic result table update'i yapıyor; `pd_cdr` update'i için uygun owner değil.
- `CDRService` ve `CDRDao` şu an `getByInteractionId()` sağlıyor, fakat `user_result_code_id` için idempotent update metodu yok.
- `UserPopulationEvaluator.shouldPopulateCdrUserResultCode()` successful connection contract'ını kullanıyor. Örnek kayıtta `assigned_user_id` ve `connected_user_id` dolu, `telephony_rc=10`; bu başarılı bağlantı contract'ına uyuyor gibi görünüyor.
- `UserPopulationEvaluator.populateCdrUserOwnership()` başarılı bağlantı sayılmayan durumlarda `cdr.userResultCodeId` alanını null'layabiliyor. Bu nedenle fallback CDR update'i yapılacaksa aynı business rule ile uyumlu olmalı, fakat mevcut `Call` nesnesi yokken `Call` tabanlı evaluator doğrudan kullanılamaz.
- `CallRemovalListener` cache expiration durumunda `CallCleaner.clearExpiredCall()` çağırıyor. `CALL_EXPIRE_TIMEOUT` 1 saat; incelenen örnekte saat farkı yaklaşık 10 dakika olduğu için bu spesifik kayıt doğrudan Guava cache expiration gibi görünmüyor. Daha kısa bir result-code timer veya webphone/acw kaynaklı delayed close ihtimali ayrıca doğrulanmalı.
- Paylaşılan log, result-code event'inin gbpowerdialer'a ulaştığını ve `WebphoneSubscriber.onMessage()` üst seviye catch'ine düştüğünü kanıtlıyor.
- Aynı log, event payload'ında `parentId`, `userId=9508` ve `resultCode.id=50` alanlarının mevcut olduğunu gösteriyor.
- Orphan/logout logu, result-code event'inden yaklaşık 10 dakika sonra aynı in-memory call'ın hâlâ `userResultCode=null` olduğunu gösteriyor; yani event başarılı şekilde call state'ine işlenmemiş.
- `WebphoneSubscriber.processCallWithResultCodeAndUpdateDNC()` içinde aktif call bulunamazsa `cdr.getTelephonyRC().getCode()` null-safe değil. Bu branch'te CDR veya telephony snapshot henüz hazır değilse NPE üretme riski var.
- Aktif call bulunursa da `connectionDecorator.processCallResult(call, new Date())` geniş call finalization path'ini çalıştırıyor; bu path'te oluşan herhangi bir NPE top-level catch'e düşerse result-code event'i tamamen abort oluyor ve CDR/campaign result fallback çalışmıyor.

## Hipotez

En güçlü hipotez: CDR hangup snapshot'ı user result code gelmeden persist ediliyor. Webphone result-code event'i daha sonra doğru payload ile geliyor, fakat `WebphoneSubscriber` içinde NPE ile abort oluyor. Abort sonrası call state'inde `userResultCode` kalıcı hâle gelmediği için logout/orphan path aynı çağrıyı `userResultCode=null` ile kapatıyor. Mevcut fallback de `pd_cdr.user_result_code_id` ve campaign result table `user_result_code` alanlarını birlikte tamamlamadığı için CDR satırı successful `telephony_rc=10` ve user ownership alanları dolu olmasına rağmen user result code'suz kalıyor.

Alternatif hipotezler:

- Event aktif call üzerinde bulunuyor fakat `processCallResult()` içinde başka bir null-sensitive alan NPE üretiyor.
- Event aktif call üzerinde bulunamıyor ve fallback tarafında CDR/telephony state null olduğu için NPE üretiyor.
- Event ulaşıyor ama `callResultCode.userId` `assigned_user_id/connected_user_id` ile eşleşmediği için reddediliyor. Paylaşılan örnekte userId `9508` olduğu için bu case özelinde düşük ihtimaldir.
- Event ulaşıyor ama `resultCode.id` boş veya deserializer payload shape'i beklenen alanları alamıyor.
- `UserPopulationEvaluator` çağrının bazı varyantlarında `user_result_code_id` alanını sonradan null'lıyor.

## Riskler

- Result code fallback'i yanlış user eşleşmesiyle çalışırsa başka agentin wrap-up code'u CDR'a yazılabilir.
- CDR fallback'i tüm call-not-found durumlarında çalışırsa abandoned, orphaned, no-MT veya sistem result code'lu çağrıları yanlışlıkla user result code'lu gösterebilir.
- `WebphoneSubscriber` Redis listener path'idir; event başına gereksiz DB query veya opened connection sayısı kadar tekrar query eklenmemeli.
- `CampaignResultDao` dinamik result table yazdığı için `pd_cdr` update'i oraya eklenirse owner boundary bulanır.
- `CDRDao.getByInteractionId()` `campaign` ve `telephonyRC` fetch ediyor; fallback için yeterli olabilir, fakat user ownership doğrulaması için `assignedUser` ve `connectedUser` alanlarının fetch/lazy davranışı ayrıca kontrol edilmeli.
- `pd_cdr.user_result_code_id` için doğrudan update eklenirse Hibernate entity state'i ve transaction boundary açık olmalı.
- Bu işte Redis payload shape'i değişmemeli; external webphone contract'ı kırılmamalı.

## Performans Etkisi

Hot path: Evet, Redis `RESULT_CODE` event path'i çağrı başına çalışır.

- Önerilen değişiklikte yeni Redis payload veya channel yok.
- Aktif call bulunduğu normal path'te yeni DB query eklenmemeli.
- Fallback yalnız hiçbir opened connection'da aktif call bulunamadığında tek kez `pd_cdr` lookup/update yapmalı.
- Opened connection loop'u içinde repeated `getByInteractionId()` çalıştırılmamalı.
- Update idempotent olmalı ve sadece `user_result_code_id IS NULL` durumunda yapılmalı; mümkünse gereksiz `saveOrUpdate + full entity flush` yerine hedefli DAO/service metodu değerlendirilmeli.

## TDD Stratejisi

- Mevcut `WebphoneSubscriberTest` genişletilerek aktif call bulunduğunda mevcut davranışın korunduğu regression testi tutulmalı.
- Yeni regression testi:
  - `managerConnectionComposition.getOpenedConnections().values()` aktif call döndürmeyen bir connection listesi verir.
  - `CDRService.getByInteractionId(parentId)` başarılı bağlantı sayılabilecek bir CDR döndürür.
  - `callResultCode.userId`, önce CDR'ın `assignedUser` alanı ile eşleşir; assigned yoksa `connectedUser` alanı ile eşleşir.
  - CDR'ın `userResultCodeId` alanı null'dır.
  - Event işlendiğinde `pd_cdr` user result code'unu güncelleyecek service/DAO boundary çağrılır.
- Negatif regression testleri:
  - CDR bulunamazsa update yapılmaz.
  - CDR'ın `userResultCodeId` alanı zaten doluysa overwrite yapılmaz.
  - `callResultCode.userId` CDR user alanlarıyla eşleşmiyorsa update yapılmaz.
  - `telephony_rc` successful connection değilse update yapılmaz.
- Persistence boundary için `CDRService`/`CDRDao` üzerinde DB gerektirmeyen mock veya query contract testi tercih edilmeli.
- Eğer hedefli DAO update metodu eklenirse test, SQL'in `interaction_id`, `user_result_code_id IS NULL` ve user ownership guard'larını içerdiğini doğrulamalı.

## Önerilen Çözüm Yönü

1. `WebphoneSubscriber.processCallWithResultCodeAndUpdateDNC()` içinde aktif call arama ve CDR fallback kararını ayrıştır.
2. Opened connection loop'u yalnız aktif call aramak için kullanılsın; call bulunamazsa tek seferlik fallback çalışsın.
3. Fallback için yeni küçük bir method ekle:
   - `parentId` ile `CDRService.getByInteractionId(parentId)` veya daha hedefli DAO metodu kullan.
   - CDR yoksa çık.
   - CDR `userResultCodeId` doluysa çık.
   - `callResultCode.resultCode.id` yoksa çık.
   - CDR successful connection değilse çık.
   - `callResultCode.userId`, önce CDR `assignedUser.id` ile kontrol edilir; assigned user yoksa `connectedUser.id` ile kontrol edilir. Eşleşme yoksa çık.
   - Uygunsa `pd_cdr.user_result_code_id` alanını idempotent şekilde güncelle.
   - Uygunsa campaign result table `user_result_code` alanını da aynı result code ile tamamla.
4. Campaign result table update ve `pd_cdr` update ayrı owner method'larıyla yürütülmeli; biri diğerinin yan etkisi kabul edilmemeli.
5. DNC fallback mevcut davranışı korunmalı; DNC result code ise CDR fallback sonrasında da `updateDNCByCdrFallback()` çalışabilmeli.
6. Loglar düşük hacimli ve ayırt edici olmalı:
   - call-not-found fallback applied
   - fallback skipped: cdr missing
   - fallback skipped: user mismatch
   - fallback skipped: non-success CDR

## Açık Sorular ve Netleştirme İhtiyacı

1. Yanıtlandı: Webphone result-code event'i gbpowerdialer loglarına düşmüş. Payload `parentId`, `callId`, `userId=9508`, `resultCode.id=50`, `final=true`, `dnc=false` alanlarını taşıyor; handler `NullPointerException` ile abort ediyor.
2. Yanıtlandı: Webphone tarafı için `wbp_interaction_status` lifecycle kanıtı bu analizde yeterli kabul edilecek; result-code payload logu user result code id için doğrudan kanıt olarak kullanılacak.
3. Yanıtlandı: Fallback hem `pd_cdr.user_result_code_id` hem campaign result table `user_result_code` alanını tamamlamalı.
4. Yanıtlandı: NPE stack trace satırı olmadan ilerlenebilir; execution plan iki olası branch'i de kapsayacak. Bir test aktif call bulunamayan fallback branch'ini, bir test aktif call bulunup `processCallResult()` sırasında exception oluşan branch'i kilitleyecek.
5. Yanıtlandı: User doğrulaması önce `assigned_user_id` ile yapılacak; assigned user yoksa `connected_user_id` fallback olarak kullanılacak.
6. `allowUrcWithoutMt` true olan campaign'lerde non-success CDR için de fallback user result code yazılmalı mı? Öneri: bu hotfix successful CDR ile sınırlı kalsın.

## Execution Plan Zorunluluğu

Kod değişikliği gerekiyor. Execution plan `docs/execution-plan/29.04.2026/FIX-GLB90048674-PD-CDR-USER-RESULT-CODE-KACAGI.md` altında, geliştirici bu analizi inceleyip açık soruları kapattıktan ve nihai planı onayladıktan sonra oluşturulacak. Plan, Redis subscriber regression testlerini, persistence boundary testini, hedefli doğrulama komutlarını ve başarılı test kapanış kriterini içerecek.
