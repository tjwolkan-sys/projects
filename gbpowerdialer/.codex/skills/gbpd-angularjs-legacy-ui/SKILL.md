---
name: gbpd-angularjs-legacy-ui
description: gbpowerdialer AngularJS 1.x, JSP/HTML page, WRO asset bundle ve Spring MVC frontend değişikliklerinde kullan. src/main/webapp/js/app.js route, controllers, services, pages, localization, ui-grid, WRO sırası veya eski Angular ekran davranışı değişirken production UI contract'ını kırmadan ilerlemek için.
---

# gbpd-angularjs-legacy-ui

## Amaç

AngularJS 1.x arayüzünde küçük ve güvenli değişiklik yapmak. Bu skill, mevcut JSP/HTML template, Angular controller/service, route ve WRO bundle düzenini bozmadan çalışmayı tarif eder.

## Repo Gerçekliği

- AngularJS uygulaması `gbpowerdialer/src/main/webapp/js/app.js` içinde `app` modülüyle başlar.
- Ortak service modülü `gbpowerdialer/src/main/webapp/js/services.js` dosyasında `app.services` olarak tanımlıdır.
- Controller dosyaları `gbpowerdialer/src/main/webapp/js/controllers` altındadır.
- Factory/service dosyaları `gbpowerdialer/src/main/webapp/js/services` altındadır.
- Template dosyaları `gbpowerdialer/src/main/webapp/pages` altında JSP veya HTML olarak bulunur.
- Bundle sırası `gbpowerdialer/src/main/webapp/WEB-INF/wro.xml` içindeki `global` group ile belirlenir.
- Lokalizasyon dosyaları `gbpowerdialer/src/main/webapp/js/localization` altındadır.

## Çalışma Akışı

1. Sorun ve çözüm yönü netleştiğinde kullanıcıya plan aşamasına geçilip geçilmeyeceğini sor; planlı akış seçildiyse iş tipine göre planlama dokümanını oluştur: yeni feature ise `docs/analyse` ve `docs/execution-plan`, fix/change/refactor gibi feature dışı iş ise `docs/change-fix-plan`.
2. İlgili route'u `app.js` içinde bul.
3. Route'un controller, template ve backend endpoint bağımlılıklarını çıkar.
4. Controller değişiyorsa ilgili factory/service kullanımını ara.
5. Yeni JS dosyası ekleniyorsa `wro.xml` sırasını güncelle.
6. Template değişiyorsa JSP/HTML, controller alias ve localization key etkisini kontrol et.
7. Form veya grid değişiyorsa backend response shape ve Angular field adlarını birlikte doğrula.
8. Değişiklik TDD ile desteklenir; frontend test standardı için `gbpd-angularjs-tdd` kullan.

## Dikkat Edilecek Contract'lar

- Route path ve `templateUrl` geriye uyumluluk notu olmadan değiştirilmez.
- Controller alias değişirse template kullanımları birlikte güncellenir.
- WRO sırası bozulursa Angular modül veya factory henüz yüklenmeden kullanılabilir.
- `ui-grid` kolon adı, field adı ve backend JSON alanı birlikte değerlendirilir.
- Lokalizasyon key'i silinmeden önce tüm dil dosyalarında kullanım aranır.
- JSP içinde Spring Security, CSRF veya server-side include etkisi varsa sadece Angular açısından değerlendirme yapma.

## Yararlı Arama Kalıpları

```text
rg -n "routeProvider|templateUrl|controller:" gbpowerdialer/src/main/webapp/js/app.js
rg -n "controllerName|factoryName|fieldName" gbpowerdialer/src/main/webapp/js gbpowerdialer/src/main/webapp/pages
rg -n "js/controllers|js/services|wro" gbpowerdialer/src/main/webapp/WEB-INF/wro.xml
rg -n "localizationKey" gbpowerdialer/src/main/webapp/js/localization gbpowerdialer/src/main/webapp/pages
```

## Çıktı

- Etkilenen route/controller/template/service listesi.
- WRO veya localization güncellemesi gerekiyorsa notu.
- TDD test dosyası ve çalıştırılan doğrulama komutu.
- Plan dosyası varsa tamamlanan checkbox ve açık soru durumu.
