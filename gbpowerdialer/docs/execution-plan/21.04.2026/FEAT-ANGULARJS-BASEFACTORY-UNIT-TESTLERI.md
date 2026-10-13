# FEAT AngularJS baseFactory Unit Testleri Uygulama Planı

## Kapsam

Bu plan, AngularJS ortak CRUD davranışını sağlayan `baseFactory` için gerçek Karma/Jasmine unit testleri ekler ve Maven doğrulamasını lokal araçla denemeyi kapsar.

## Kontrol Listesi

- [x] `baseFactory` davranışı ve bağımlılıkları incelendi.
- [x] Analiz dokümanı oluşturuldu.
- [x] `karma.conf.js` içine `baseFactory.js` üretim dosyası eklendi.
- [x] Test setup dosyasına `locMsg` test değeri eklendi.
- [x] `baseFactory.spec.js` içinde save, remove ve setEnabled davranışları kilitlendi.
- [x] AngularJS test komutu çalıştırıldı.
- [x] Lokal Maven aracı hazırlandı.
- [x] Java contract testleri Maven/Surefire engeline rağmen JUnitCore ile çalıştırıldı.
- [x] History ve indeks dokümanları güncellendi.

## Doğrulama Komutları

```powershell
npm.cmd run test:angular
.codex\tools\apache-maven-3.9.9\bin\mvn.cmd -Dtest=CampaignRuntimeCountersCharacterizationTest,CampaignRoutesAndBundleContractTest test
```

## Notlar

- Lokal Maven `.codex/tools` altında tutulur ve git'e alınmaz.
- Java 8 bu makinede mevcuttur.

## Sonuç

- `npm.cmd run test:angular` başarıyla çalıştı; Chrome Headless üzerinde 7 spec geçti.
- Apache Maven 3.9.9 `.codex/tools/apache-maven-3.9.9` altına indirildi ve JDK 8 ile doğrulandı.
- Maven compile ve testCompile aşamaları JDK 8 ile geçti.
- Maven Surefire koşusu kurumsal repository/mirror bağımlılıkları nedeniyle tamamlanamadı.
- `CampaignRoutesAndBundleContractTest`, doğrudan JUnitCore ile çalıştı; 2 test geçti.
- `CampaignRuntimeCountersCharacterizationTest`, doğrudan JUnitCore ile çalıştı; 3 test geçti.
