# FIX PD-CDR User Result Code Kaçağı Uygulama Planı

## Analiz Referansı

`docs/analyse/29.04.2026/FIX-GLB90048674-PD-CDR-USER-RESULT-CODE-KACAGI.md`

## Onaylanan Plan Özeti

Webphone `RESULT_CODE` event'i geldiği hâlde `WebphoneSubscriber` içinde NPE ile abort olduğunda user result code kaybolmayacak. Aktif call bulunamayan fallback branch'i ve aktif call bulunup `processCallResult()` sırasında exception alınan branch birlikte test edilecek. Fallback, önce `assigned_user_id`, yoksa `connected_user_id` ile user doğrulaması yapacak. Uygun kayıt için hem `pd_cdr.user_result_code_id` hem campaign result table `user_result_code` alanı aynı result code ile idempotent tamamlanacak.

## Amaç

Successful `telephony_rc=10` ve doğru user ownership bilgisine sahip çağrılarda, Webphone result-code event'i handler içinde abort olsa bile user result code bilgisinin `pd_cdr` ve campaign result table kayıtlarından kaçmasını engellemek.

## Kapsam Dışı

- Redis channel adı veya Webphone payload shape değişikliği.
- Asterisk AMI/AGI originate contract'ı değişikliği.
- Retry, callback, DNC ve campaign counter davranışlarında geniş refactor.
- Non-success CDR kayıtlarına genel user result code backfill'i.
- Geçmiş production verisini toplu düzeltme script'i.

## Performans Etkisi

- Hot path durumu: Redis `RESULT_CODE` event path'i çağrı başına çalışır.
- Call/event/tick başına ek maliyet: Aktif call bulunduğu normal başarı path'inde ek DB query hedeflenmez. Fallback yalnız result finalization başarısızsa veya aktif call bulunamazsa çalışır.
- IO/scan/lock/serialization/log etkisi: Opened connection loop'u içinde repeated CDR lookup yapılmayacak. Fallback tek CDR lookup ve gerekli ise idempotent CDR/campaign result update ile sınırlanacak.
- Azaltım veya ölçüm notu: Guard'lar `resultCode.id`, `userResultCodeId == null`, successful CDR ve user eşleşmesi ile dar tutulacak. Loglar düşük hacimli skip/apply mesajlarıyla sınırlı olacak.

## TDD Planı

- [x] `WebphoneSubscriberTest` içinde aktif call bulunamayan successful CDR fallback'i için başarısız regression testi.
- [x] `WebphoneSubscriberTest` içinde `processCallResult()` exception verdiğinde durable fallback'in yine çalıştığını kanıtlayan başarısız regression testi.
- [x] Assigned user öncelikli, assigned yoksa connected user fallback doğrulamasını kanıtlayan testler.
- [x] Zaten dolu CDR, user mismatch ve non-success CDR negatif testleri.
- [x] Forcefully-stopped fallback'in campaign result table ile birlikte `pd_cdr.user_result_code_id` alanını da güncellediğini kanıtlayan regression testi.
- [x] `CDRDao#getByInteractionId` assigned/connected fetch ihtiyacı production diff ile sınırlandı; ayrı DAO testi gerekmedi.

## Uygulama Kontrol Listesi

- [x] Onaylanan planın tüm adımları execution plan'a işlendi.
- [x] Etkilenen kod alanlarını ve mevcut test setup'ını hazırla.
- [x] Testleri kırmızı hâle getir.
- [x] En küçük production değişikliğini yap.
- [x] Hedefli testi yeşile çevir.
- [x] Gerekirse küçük refactor yap.
- [x] Refactor sonrası testi tekrar çalıştır.
- [x] Hot path maliyetini plan notuyla doğrula.
- [x] Doküman ve history güncellemesini tamamla.

## Doğrulama

Hedefli komut:

```text
mvn -o -Dtest=WebphoneSubscriberTest test
```

Local tool path varsa `.codex/TOOL-PATHS.local.properties` değerleriyle çalıştırılacak.

Kırmızı doğrulama:

```text
C:/Users/GLB90048674/javatools/maven-bin-dist/bin/mvn.cmd -o -s C:/Users/GLB90048674/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90048674/.m2/repository -f gbpowerdialer/pom.xml -Dtest=WebphoneSubscriberTest test
```

Sonuç: `BUILD FAILURE`; `Tests run: 26, Failures: 2, Errors: 1`. Aktif call bulunamayan fallback CDR'a yazmıyordu; aktif call finalization exception branch'i handler'ı abort ediyordu.

Yeşil doğrulama:

```text
C:/Users/GLB90048674/javatools/maven-bin-dist/bin/mvn.cmd -o -s C:/Users/GLB90048674/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90048674/.m2/repository -f gbpowerdialer/pom.xml -Dtest=WebphoneSubscriberTest test
```

Sonuç: `BUILD SUCCESS`; `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0`.

Forcefully-stopped netleştirme doğrulaması:

```text
C:/Users/GLB90048674/javatools/maven-bin-dist/bin/mvn.cmd -o -s C:/Users/GLB90048674/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90048674/.m2/repository -f gbpowerdialer/pom.xml -Dtest=WebphoneSubscriberTest#givenNoActiveCallAndForcefullyStoppedCdrWhenGetResultCodeMessageThenPersistCdrAndCampaignFallback test
```

İlk sonuç: `BUILD FAILURE`; test CDR `userResultCodeId` alanının null kaldığını gösterdi.

Son doğrulama:

```text
C:/Users/GLB90048674/javatools/maven-bin-dist/bin/mvn.cmd -o -s C:/Users/GLB90048674/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90048674/.m2/repository -f gbpowerdialer/pom.xml -Dtest=WebphoneSubscriberTest test
```

Sonuç: `BUILD SUCCESS`; `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0`.

## Kapanış Kriteri

- [x] Result-code event NPE/exception branch'lerinde user result code kaybı regression testleriyle kilitlendi.
- [x] `pd_cdr.user_result_code_id` ve campaign result table `user_result_code` fallback'i idempotent çalışıyor.
- [x] User guard önce assigned, yoksa connected user kuralını uyguluyor.
- [x] Planlanan hedefli testler çalıştırıldı ve başarılı oldu.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Riskler ve Geri Dönüş

- Yanlış user eşleşmesi farklı agentin result code'unu CDR'a yazabilir; bu nedenle assigned-first guard zorunlu.
- Fallback event path'inde gereksiz DB maliyeti yaratabilir; bu nedenle normal aktif call başarı path'ine ek query eklenmeyecek.
- Production patch sorun çıkarırsa değişiklik `WebphoneSubscriber` fallback ve CDR update boundary'siyle sınırlı olduğu için önceki behavior'a dar revert yapılabilir.

## Tamamlanma Notu

Uygulama tamamlandı. `WebphoneSubscriber` no-active-call ve active-finalization-exception branch'lerinde durable fallback uygular; successful CDR kayıtlarında hem `pd_cdr.user_result_code_id` hem campaign result table `user_result_code` tamamlanır. Forcefully-stopped fallback de campaign-only kalmaz; aynı result code `pd_cdr.user_result_code_id` alanına yazılıp CDR persist edilir. `CDRDao#getByInteractionId` fallback guard için `assignedUser` ve `connectedUser` alanlarını fetch eder.
