# Next.js Frontend Migration Roadmap

Bu doküman, gbpowerdialer AngularJS 1.x/JSP arayüzünü yaşayan ürünü kesmeden Next.js tabanlı yeni frontend'e taşımak için kısa roadmap'dir. Mevcut AngularJS arayüz legacy kabul edilir; amaç ekranları kontrollü şekilde emekli etmektir.

## Mevcut Durum

- Frontend `gbpowerdialer/src/main/webapp/js` altında AngularJS 1.x ile çalışır.
- Route tanımları `js/app.js` içindedir.
- Controller dosyaları `js/controllers`, service/factory dosyaları `js/services` altındadır.
- Template dosyaları `src/main/webapp/pages` altında JSP ve HTML olarak bulunur.
- Bundle sırası `src/main/webapp/WEB-INF/wro.xml` ile yönetilir.
- Ayrı JavaScript test runner dosyası bu taramada görülmedi.
- Backend Spring MVC/API endpoint'leri mevcut Angular ekranlarıyla sıkı bağlıdır.

## Hedef

- Yeni ekranları Next.js ile geliştirmek.
- Mevcut AngularJS ekranları sayfa veya workflow bazında emekli etmek.
- Backend API contract'larını kırmadan migration yapmak.
- Tüm frontend geliştirmelerini TDD ile desteklemek.
- Route, yetki, CSRF, localization, grid ve form davranışlarını kullanıcıya kesinti yaşatmadan taşımak.

## Faz 0: Envanter ve Güvenlik

- Angular route, controller, service, template ve WRO envanterini çıkar.
- Her route için backend endpoint ve response shape listesini oluştur.
- Kritik ekranları belirle: kampanya listesi, kampanya formu, veri yükleme, server, DNC, LSC, result code, schedule.
- Frontend test stratejisini `gbpowerdialer/src/test/java/tests/angular/<domain>` altında başlat.
- İlk contract testleri route, WRO ve localization contract'ını kilitlesin.

## Faz 1: Contract ve Test Altyapısı

- Backend API response shape için contract testler ekle.
- Angular route/template/controller bağlarını karakterizasyon testleriyle kilitle.
- Lokalizasyon key bütünlüğü için test ekle.
- WRO global bundle sırası için contract test ekle.
- Next.js test altyapısı için karar ver: component test, API client test ve e2e test ayrımı.

## Faz 2: Next.js Shell ve Proxy

- Next.js uygulamasını ayrı deploy edilebilir frontend olarak hazırla.
- Auth/session/CSRF davranışını mevcut backend ile uyumlu çalıştır.
- Route proxy veya feature flag ile kullanıcıları seçili ekranlara yönlendirecek mekanizmayı kur.
- Ortak API client ve hata yönetimi katmanını yaz.
- Tasarım ve localization standardını mevcut Angular ekranlarla karşılaştır.

## Faz 3: İlk Read-only Ekran

- Yazma yan etkisi düşük bir ekran seç.
- Angular ekran davranışını karakterizasyon testleriyle sabitle.
- Aynı endpoint'leri kullanan Next.js ekranını geliştir.
- Shadow veya kısıtlı kullanıcı flag'i ile karşılaştırmalı doğrula.
- Angular ekranını hemen silme; önce kullanım ve hata metriği izle.

## Faz 4: Form ve Grid Ekranları

- Grid kolonları, sorting, filtering, pagination ve export davranışlarını contract olarak yaz.
- Form validation, hata mesajı ve localization davranışını testle.
- Kampanya gibi riskli ekranlarda parçalı taşıma yap; liste, detay, form ve upload ayrı slice olabilir.
- Backend endpoint değişikliği gerekiyorsa işin niteliğine göre etiketlenmiş ayrı analiz aç.

## Faz 5: Angular Emeklilik

- Taşınan ekran için Angular route, controller, service, template ve WRO kayıtlarının kullanımını denetle.
- `gbpd-dead-code-audit` ile silme kanıtı çıkar.
- Feature flag tamamen Next.js'e döndükten sonra Angular dosyalarını kaldır.
- `docs/README.md`, ilgili architecture dokümanı ve history kaydını güncelle.

## Öncelik Sırası

1. Route ve WRO contract testleri.
2. Read-only ve düşük riskli yönetim ekranları.
3. Grid yoğun liste ekranları.
4. Form ve validation yoğun ekranlar.
5. Upload/download ve kampanya veri manipülasyonu.
6. Kampanya runtime ve gerçek zamanlı bilgi ekranları.

## Açık Kararlar

- Next.js uygulaması aynı domain altında mı, ayrı domain altında mı servis edilecek?
- Session ve CSRF token paylaşımı proxy üzerinden mi, BFF üzerinden mi yapılacak?
- Tasarım sistemi mevcut Bootstrap/ui-grid davranışını ne kadar koruyacak?
- E2E test aracı Playwright mı olacak?
- Angular ve Next.js aynı anda çalışırken kullanıcı bazlı mı, tenant bazlı mı feature flag kullanılacak?

## İlk 10 İş

1. Commit tipi etiket standardına göre ilk frontend analiz dosyasını aç.
2. Angular route/controller/template envanterini otomatik veya yarı otomatik çıkar.
3. `tests/angular/routing` altında route characterization testi yaz.
4. `tests/angular/bundling` altında WRO global bundle order testi yaz.
5. `tests/angular/localization` altında localization key contract testi yaz.
6. İlk read-only ekran adayını seç.
7. O ekran için API contract listesini çıkar.
8. Next.js shell için repo/deploy kararını ver.
9. Feature flag veya route proxy kararını yaz.
10. İlk Next.js ekranı için execution plan oluştur.
