# FIX DNC Phone Expired Date Validation

## Bağlam

DNC formunda telefon numarası bazlı `expiredDate` alanı, ilgili numaranın `createDate` değerinden önce olacak şekilde kaydedilebiliyor. Kullanıcı tarafında ayrıca tarih davranışının bazen aynı oturum içinde farklı algılandığı gözlemlendi. İnceleme sırasında bunun son gelen DNC expire priority pull request'i ile düzelip düzelmediği ve güvenli çözüm yönü istendi.

## İş Etiketi

fix

## Etkilenen Alanlar

- `gbpowerdialer/src/main/webapp/pages/dncForm.html`
- `gbpowerdialer/src/main/webapp/pages/dncPhone.html`
- `gbpowerdialer/src/main/webapp/js/controllers/dncFormCtrl.js`
- `gbpowerdialer/src/main/webapp/js/controllers/dncPhoneFormCtrl.js`
- `gbpowerdialer/src/main/webapp/js/controllers/dncPhoneCtrl.js`
- `gbpowerdialer/src/main/webapp/js/app.js`
- `gbpowerdialer/src/main/java/com/powerdialer/service/DNCService.java`
- `gbpowerdialer/src/main/java/com/powerdialer/service/DNCPhoneNumberService.java`
- `gbpowerdialer/src/main/java/com/powerdialer/controller/ExceptionHandlingController.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/DNCEngine.java`
- DNC ile ilgili mevcut testler: `DNCEngineTest`, `DNCValidatorTest`

## Mevcut Davranış

- DNC liste seviyesinde yalnızca `expireDate > now` kontrolü frontend tarafında yapılıyor.
- DNC telefon numarası modalında `expiredDate` alanı kullanıcıya açık, ancak `expiredDate >= createDate` veya `expiredDate >= now` şeklinde bir kural uygulanmıyor.
- Backend tarafında mevcut validation yalnızca `phone expiredDate <= dnc list expireDate` kuralını koruyor.
- Son gelen `DNC Expire Date Priority` değişikliği yalnızca `DNCEngine` runtime cache/filter davranışını etkiliyor; form save contract'ını etkilemiyor.
- Frontend `ngFormatDate` directive'i model tipini normalize etmediği için save öncesi tarih karşılaştırması kullanıcı giriş şekline göre tutarsız davranabiliyor.

## Beklenen Davranış

- Telefon numarası seviyesindeki `expiredDate`, o numaranın efektif eklenme zamanından önce olamamalı.
- Liste seviyesindeki `expireDate`, aktif veya request içinde korunacak telefon numaralarının `expiredDate` değerlerinden küçük olamamalı.
- Aynı kural hem create hem update akışında backend tarafından authoritative şekilde korunmalı.
- Frontend, invalid tarihi mümkün olduğunca erken göstermeli; ancak asıl contract backend'de olmalı.
- Date input davranışı picker ve manuel girişte tutarlı olmalı.

## Kod Okuma Bulguları

- `dncFormCtrl.save()` içinde yalnızca liste `expireDate` için `new Date() < vm.item.expireDate` kontrolü var; telefon numarası satırları için ek validation yok.
- `dncPhoneFormCtrl.validate()` yalnızca duplicate number kontrolü yapıyor.
- Yeni eklenen telefon numaralarının `createDate` değeri frontend'de set ediliyor; backend bunu authoritative olarak üretmiyor, yalnızca kullanıcı alanlarını basıyor.
- `DNCPhoneNumberService.createFromRow()` ve `DNCService.validateListExpireDateInMemory()` yalnızca liste expire date ile numara expire date arasındaki üst sınır ilişkisini kontrol ediyor.
- `ngFormatDate` parser'ı girdiyi olduğu gibi geri döndürüyor; bu nedenle `vm.item.expireDate` bazı akışlarda `Date`, bazı akışlarda string olabilir.
- `DNCService.validateListExpireDateInMemory()` mevcut durumda DB tarafındaki `max expiredDate` değerini `deleted = false` filtresiyle alıyor; aynı request içinde silinecek satırlar hesapta kalabileceği için liste expire date kısaltma akışında false negative risk taşıyor.
- `Merged PR 22648: DNC Expire Date Priority` yalnızca `DNCEngine.collectValidDncNumbers()` içindeki runtime öncelik davranışını değiştiriyor; form validation'a dokunmuyor.

## Riskler

- Backend authoritative validation olmadan frontend tarafında yapılacak tek başına düzeltme bypass edilebilir.
- `createDate` alanını frontend belirlediği için saat farkı, locale veya manuel model manipülasyonu kaynaklı tutarsızlık oluşabilir.
- `DNCService` içindeki liste expire validation'ı, aynı request içinde silinen satırları hâlâ aktif kabul ederse geçerli bir update yanlış reddedilebilir.
- Yeni validation mesajı gerekiyorsa localization ve exception mapping etkilenir.
- DNC import/batch akışları ile UI modal akışlarının aynı contract altında birleştirilmesi gerekir; aksi hâlde bir katman düzelip diğeri kaçak kalır.

## TDD Stratejisi

- Önce service seviyesi karakterizasyon/regression testleri ile mevcut eksik contract görünür hâle getirilecek.
- `DNCService` için liste expire validation'ında aynı request içindeki deleted satırların hesaba katılmaması gereken senaryo test edilecek.
- `DNCPhoneNumberService` veya uygun service boundary için `expiredDate < effectiveCreateDate` durumunda failure üreten regression testi eklenecek.
- Frontend tarafında tam AngularJS unit test zorunlu değil; kısa vadede en kritik contract backend testleriyle kilitlenecek. Gerekirse ikinci adımda `dncPhoneFormCtrl` için Karma/Jasmine test eklenebilir.
- Runtime priority davranışı bu işin ana konusu olmadığı için `DNCEngineTest` yalnızca mevcut davranışın etkilenmediğini doğrulayan dar kapsamlı regression ile genişletilecekse genişletilecek.

## Önerilen Çözüm Yönü

1. Telefon numarası seviyesinde yeni backend contract tanımla:
   - `expiredDate`, efektif create zamanından küçük olamaz.
   - Yeni satırlarda efektif create zamanı backend tarafından `now` veya request içinde atanacak authoritative tarih olmalı.
   - Mevcut satırlarda create date immutable kabul edilerek karşılaştırma ona göre yapılmalı.
2. `DNCService.validateListExpireDateInMemory()` listesel kontrolünü request içindeki deleted satırları dışlayacak şekilde düzelt.
3. Frontend hardening yap:
   - `ngFormatDate` parser'ını gerçek `Date` üretmek veya input'u readonly + picker-only yapmak.
   - `dncPhoneFormCtrl` içine kullanıcı dostu ön validation eklemek.
4. Gerekirse daha açıklayıcı bir validation exception/message ayır:
   - Liste üst sınırı ihlali ile numaranın create date'ten önce expire edilmesi farklı mesajlar verebilir.

## Execution Plan Zorunluluğu

Kod değişikliği gerekiyor. Execution plan `docs/execution-plan/27.04.2026/FIX-DNC-PHONE-EXPIRED-DATE-VALIDATION.md` altında oluşturulacak. Plan, TDD test planını, hedefli doğrulama komutlarını ve başarılı test kapanış kriterini içerecek.
