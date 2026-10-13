# FEAT AngularJS Kısa ve Orta Vade Test Standardı Uygulama Planı

## Kapsam

Bu plan, AngularJS 1.x için kısa vadeli Java/JUnit contract testleri ile orta vadeli Karma/Jasmine/angular-mocks birim testlerini repo standardı hâline getirir.

## Kontrol Listesi

- [x] AngularJS test yaklaşımı için analiz dokümanı oluştur.
- [x] Karma/Jasmine/angular-mocks bağımlılıklarını `package.json` içine ekle.
- [x] `karma.conf.js` dosyasını üretim AngularJS dosyalarına dokunmadan oluştur.
- [x] İlk gerçek AngularJS service spec dosyasını `alertService` için yaz.
- [x] `AGENTS.md`, skill ve mimari dokümanlarda kısa/orta vade test standardını güncelle.
- [x] Node bağımlılıklarını doğrula ve AngularJS test komutunu çalıştır.
- [x] History ve indeks dokümanlarını sonuçla güncelle.

## Doğrulama Komutları

```powershell
npm.cmd install
npm.cmd run test:angular
mvn -Dtest=CampaignRoutesAndBundleContractTest test
```

## Notlar

- Maven bu ortamda PATH'te yoksa Java contract testleri çalıştırılamaz; sonuç final yanıtta ve history dosyasında açıkça yazılmalıdır.
- Chrome yoksa Karma test koşusu başarısız olabilir; bu durumda altyapı yine korunur, ancak geliştirici ortamı gereksinimi ayrıca raporlanır.

## Sonuç

- `npm.cmd install` başarıyla çalıştı ve `package-lock.json` oluşturuldu.
- `npm.cmd run test:angular` başarıyla çalıştı; Chrome Headless üzerinde 3 spec geçti.
- `mvn -Dtest=CampaignRoutesAndBundleContractTest test` denendi; `mvn` PATH'te olmadığı için çalıştırılamadı.
