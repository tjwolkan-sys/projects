---
name: gbpd-angularjs-tdd
description: gbpowerdialer AngularJS 1.x frontend değişikliklerinde TDD akışı için kullan. Controller, service/factory, route, template binding, localization, ui-grid davranışı veya Next.js migration öncesi karakterizasyon testi gerektiğinde kısa vadeli Java/JUnit contract testleri ve orta vadeli Karma/Jasmine/angular-mocks testlerini uygular.
---

# gbpd-angularjs-tdd

## Amaç

AngularJS tarafındaki her frontend değişikliğini testle başlatmak. Bu repo için iki test katmanı vardır:

- Kısa vade: Route, WRO, template, localization ve backend response shape contract'larını Java/JUnit contract testleriyle koru.
- Orta vade: Saf AngularJS service, factory, controller ve directive davranışlarını Karma, Jasmine ve angular-mocks ile gerçek Angular injector üzerinden test et.

Next.js testleri bu skill'in kapsamı değildir. Next.js migration başladığında ilgili test standardı ayrı ele alınır.

## Kısa Vade Test Kökü

Java/JUnit contract ve karakterizasyon testleri için tercih edilen kök:

```text
gbpowerdialer/src/test/java/tests/angular/<domain>
```

Package formatı:

```text
tests.angular.<domain>
```

Domain örnekleri:

- `routing`
- `bundling`
- `localization`
- `campaign`
- `forms`
- `grid`
- `services`

Test sınıfı formatı:

```text
<EkranVeyaBilesen><Davranis><TestTipi>Test
```

Örnekler:

- `CampaignListRouteCharacterizationTest`
- `CampaignGridColumnsRegressionTest`
- `LocalizationKeysContractTest`
- `WroGlobalBundleOrderContractTest`

## Orta Vade Test Kökü

Gerçek AngularJS birim testleri için tercih edilen kök:

```text
gbpowerdialer/src/test/angular/<domain>
```

Spec dosyası formatı:

```text
<bilesen>.spec.js
```

Örnekler:

- `src/test/angular/services/alertService.spec.js`
- `src/test/angular/controllers/campaignListController.spec.js`
- `src/test/angular/forms/campaignFormValidation.spec.js`

Karma konfigürasyonu `gbpowerdialer/karma.conf.js`, Node bağımlılıkları `gbpowerdialer/package.json` içinde tutulur. AngularJS sürümüyle uyum için `angular-mocks` sürümü üretimdeki AngularJS sürümüyle aynı tutulmalıdır.

## TDD Akışı

1. Sorun ve çözüm yönü netleştiğinde kullanıcıya plan aşamasına geçilip geçilmeyeceğini sor; planlı akış seçildiyse iş tipine uygun planlama dokümanında frontend davranışını ve etkilenen ekranı yaz: yeni feature için `docs/analyse` + `docs/execution-plan`, feature dışı işler için `docs/change-fix-plan`.
2. Test katmanını seç:
   - Route, WRO, template, localization veya backend contract ise Java/JUnit contract testi yaz.
   - Service, factory, controller veya directive davranışı izole edilebiliyorsa Karma/Jasmine spec yaz.
3. Mevcut davranış belirsizse karakterizasyon testi yaz.
4. Yeni veya düzeltilen davranış için önce başarısız test ekle.
5. En küçük AngularJS/JSP/WRO değişikliğini yap.
6. İlgili hedefli testi çalıştır:
   - Java contract: `mvn -Dtest=ClassName test`
   - AngularJS unit: `npm.cmd run test:angular`
7. Planlı akışta plan dosyasındaki `Açık Sorular` bölümünde cevaplanmamış madde varsa kullanıcıdan yanıt almadan AngularJS/JSP/WRO kod değişikliğine geçme.
8. Gerekirse frontend manuel doğrulama adımlarını plana ve `gbpd-agent-history` akışıyla append-only history kaydına ekle.

## Test Türleri

- `Characterization`: Mevcut route, WRO sırası, localization key veya controller/template contract'ını kilitler.
- `Regression`: Bilinen frontend hatasının tekrar oluşmasını engeller.
- `Contract`: Backend JSON alanı, route path, templateUrl veya localization key contract'ını doğrular.
- `Feature`: Yeni davranışın beklenen çıktısını doğrular.
- `Unit`: AngularJS service/factory/controller davranışını angular-mocks injector ile çalıştırır.

## AngularJS Unit Test Notları

- `src/test/angular/setup/angular-app.module.js`, izole spec'ler için `app` modülünü test ortamında kurar.
- Üretim `app.js` tamamıyla yüklenmeden test edilen küçük service/factory dosyaları için bu setup kullanılabilir.
- Route veya gerçek uygulama modülü test edilecekse bağımlılıklar tek tek mock'lanmalı ya da ayrı setup dosyası açılmalıdır.
- Karma testleri WRO bundle üretmez; WRO sırası için Java/JUnit contract testi korunmalıdır.
- Windows PowerShell'de `npm.ps1` execution policy nedeniyle engellenirse `npm.cmd` kullan.

## Kaçınılacaklar

- Gerçek AngularJS davranışı test edilebiliyorken yalnızca metin arama testine güvenme.
- WRO sırasını değiştiren işte Java/JUnit contract testini atlama.
- Angular controller değişikliğini backend contract kontrolü yapmadan bitirme.
- `wro.xml` güncellemesini unutarak yeni JS dosyasını sadece dosya sistemine ekleme.
- Lokalizasyon key'i ekleyip tüm aktif dil dosyalarını güncellememek.

## Final Çıktı

- Yazılan Java/JUnit contract veya Karma/Jasmine spec dosyası.
- Test dosya yolu.
- Çalıştırılan Maven veya `npm.cmd run test:angular` komutu ve sonucu.
- Manuel frontend doğrulama gerekiyorsa adımları ve nedeni.
