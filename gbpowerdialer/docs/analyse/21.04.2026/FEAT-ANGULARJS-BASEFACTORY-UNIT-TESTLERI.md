# FEAT AngularJS baseFactory Unit Testleri

## Bağlam

AngularJS için gerçek Karma/Jasmine test altyapısı kurulduktan sonra ikinci test alanı olarak `baseFactory` seçildi. Bu factory, çok sayıda ekran factory'sinin ortak CRUD davranışını sağladığı için küçük bir davranış değişikliği geniş frontend yüzeyini etkileyebilir.

## İş Etiketi

`feat`

## Mevcut Davranış

- `baseFactory`, `save`, `create`, `clone`, `remove`, `setEnabled`, `createNew` ve `hideTenantColumn` davranışlarını prototype üzerinden sağlar.
- Birçok domain factory'si `Object.create(baseFactory.prototype)` ile bu davranışları miras alır.
- Başarılı save/remove/setEnabled işlemleri `alertService` üzerinden kullanıcıya başarı uyarısı ekler.
- `setEnabled`, `<listName>/batch` endpoint'ine id listesi ve enabled parametresiyle PUT isteği gönderir.

## Beklenen Davranış

- `save`, başarılı `_save` sonucunu aynen döndürür ve `disableAlert` yoksa kayıt başarı uyarısı ekler.
- `save`, `disableAlert=true` ile çağrıldığında uyarı eklemez.
- `remove`, başarılı `_remove` sonucunu aynen döndürür ve silme başarı uyarısı ekler.
- `setEnabled`, batch endpoint contract'ını korur ve response body değerini döndürür.

## Etkilenen Alanlar

- `gbpowerdialer/karma.conf.js`
- `gbpowerdialer/src/test/angular/setup/angular-app.module.js`
- `gbpowerdialer/src/test/angular/services/baseFactory.spec.js`
- `docs/analyse/21.04.2026/README.md`
- `docs/execution-plan/21.04.2026/README.md`
- `docs/README.md`
- `docs/history/21.04.2026.md`

## TDD Stratejisi

Bu iş üretim kodunu değiştirmez; mevcut davranışı karakterizasyon unit testleriyle kilitler. Testler gerçek Angular injector, `$q`, `$rootScope`, `$httpBackend`, `alertService` ve `baseFactory` kullanır.

## Riskler

- `baseFactory` çok sayıda factory tarafından kullanıldığı için ileride yapılacak küçük değişiklikler geniş frontend davranışını etkileyebilir.
- `setEnabled` query param sırası AngularJS `$http` tarafından üretildiği için test, mevcut AngularJS 1.4.4 davranışını kilitler.
- `hideTenantColumn` underscore ve role davranışı içerdiği için bu ilk test kapsamına alınmadı; ayrı testte ele alınmalıdır.

## Kabul Kriterleri

- `baseFactory.spec.js` gerçek AngularJS unit testi olarak eklenir.
- `karma.conf.js`, `baseFactory.js` dosyasını spec'lerden önce yükler.
- `npm.cmd run test:angular` başarıyla çalışır.
- Maven erişimi sağlanabilirse Java contract testleri de çalıştırılır.
