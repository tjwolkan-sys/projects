# FIX DNC Phone Expired Date Validation Uygulama Planı

## Analiz Referansı

`docs/analyse/27.04.2026/FIX-DNC-PHONE-EXPIRED-DATE-VALIDATION.md`

## Amaç

DNC telefon numarası seviyesindeki `expiredDate` alanının efektif eklenme zamanından önce kaydedilememesini garanti etmek, liste expire date doğrulamasını request içindeki deleted satırlarla tutarlı hâle getirmek ve frontend date parsing kaynaklı tutarsızlığı azaltmak.

## Kapsam Dışı

- DNC runtime priority mantığını yeniden tasarlamak.
- DNC import ekranlarının UX refactor'ı.
- Tüm legacy date input'ların global normalize edilmesi.

## TDD Planı

- [x] `DNCService` için, request içinde silinen uzun expire'lı satır varken liste expire date kısaltmasının geçerli kabul edilmesini kanıtlayan karakterizasyon/regression testi.
- [x] Telefon numarası `expiredDate` değerinin efektif create zamanından küçük olduğu durumda backend failure üreten başarısız test.
- [x] Geçerli create/update senaryosunun yeşilde kaldığını kanıtlayan regression testi.
- [x] `dncPhoneFormCtrl` davranışı için Karma/Jasmine frontend unit testi.
- [x] DNC date input readonly contract'ı için frontend template contract testi.

## Uygulama Kontrol Listesi

- [x] `DNCService`, `DNCPhoneNumberService`, ilgili DTO/entity ve exception surface etkisini yeniden doğrula.
- [x] Önce backend testlerini kırmızı hâle getir.
- [x] Telefon numarası expire/create contract'ını backend'de authoritative şekilde uygula.
- [x] Liste expire validation'ını request içindeki deleted satırları dışlayacak şekilde düzelt.
- [x] Validation exception/message ayrımını ve localization anahtarlarını güncelle.
- [x] Frontend tarafında date input davranışını tutarlı hâle getiren en küçük değişikliği uygula.
- [x] Hedefli backend testlerini yeşile çevir.
- [x] Hedefli frontend testlerini çalıştır.
- [x] History güncellemesini tamamla.

## Doğrulama

Planlanan hedefli doğrulamalar:

- `mvn -o -Dtest=DNCServiceTest,DNCPhoneNumberServiceTest test`
- `npm.cmd run test:angular`
- `DncDateInputsContractTest` frontend contract doğrulaması

Gerçekleşen doğrulama:

1. Maven hedefli backend test komutu çalıştırıldı.
2. `testCompile` aşaması repo içindeki unrelated mevcut kırıklar nedeniyle bloklandı:
   - `PredictiveGammaCallManagerTest`
   - `PredictiveGammaCallCalculatorTest`
3. Aynı nedenle DNC backend doğrulaması aşağıdaki dar komutlarla izole edildi:
   - `mvn -o -q -f gbpowerdialer/pom.xml dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/test-classpath.txt`
   - `javac -cp "gbpowerdialer/target/classes;<test classpath>" -d gbpowerdialer/target/manual-test-classes gbpowerdialer/src/test/java/com/powerdialer/service/DNCServiceTest.java gbpowerdialer/src/test/java/com/powerdialer/service/DNCPhoneNumberServiceTest.java`
   - `java -cp "gbpowerdialer/target/manual-test-classes;gbpowerdialer/target/classes;<test classpath>" org.junit.runner.JUnitCore com.powerdialer.service.DNCServiceTest com.powerdialer.service.DNCPhoneNumberServiceTest`
4. İzole backend JUnit koşusu sonucu: `OK (5 tests)`
5. AngularJS unit test komutu çalıştırıldı:
   - `npm.cmd run test:angular`
6. Karma sonucu: `TOTAL: 10 SUCCESS`
7. Frontend template contract doğrulaması izole JUnit koşusuyla çalıştırıldı:
   - `javac -cp "gbpowerdialer/target/classes;<test classpath>" -d gbpowerdialer/target/manual-frontend-contract-test-classes gbpowerdialer/src/test/java/tests/angular/forms/DncDateInputsContractTest.java`
   - `java -cp "gbpowerdialer/target/manual-frontend-contract-test-classes;gbpowerdialer/target/classes;<test classpath>" org.junit.runner.JUnitCore tests.angular.forms.DncDateInputsContractTest`
8. Frontend contract sonucu: `OK (2 tests)`

## Kapanış Kriteri

- [x] Planlanan backend contract testleri çalıştırıldı ve başarılı oldu.
- [x] Frontend değişiklikleri için ilgili otomasyon testleri çalıştırıldı ve başarılı oldu.
- [x] Final yanıtta kısa Test Özeti sunulacak.

## Riskler ve Geri Dönüş

- Import akışında expire date alanı gün bazında parse edildiği için doğrulama bug fix'i gün başı sınırı üzerinden uygulanır.
- Repo genelindeki unrelated gamma test kırıkları devam ettiği sürece standart `mvn test` akışı bu iş için güvenilir sinyal üretmez.
- Geri dönüş gerekirse önce frontend readonly/modal hardening geri alınabilir; backend contract fix'i ayrı bırakılabilir.

## Tamamlanma Notu

Plan uygulandı. DNC save/import tarafındaki expire date kontratı backend'de kapatıldı, deleted-row merge davranışı düzeltildi, modal date girişi picker-driven hâle getirildi ve frontend değişiklikleri otomasyon testleriyle doğrulandı.
