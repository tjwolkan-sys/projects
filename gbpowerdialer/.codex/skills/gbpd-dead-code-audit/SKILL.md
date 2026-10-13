---
name: gbpd-dead-code-audit
description: gbpowerdialer'da kullanılmayan kodu silmeden önce kanıta dayalı analiz yapma akışı. Spring XML, component-scan, JSP, AngularJS route, WRO, Redis channel, AMI event, Hibernate/Dozer mapping veya reflection nedeniyle statik aramanın yetersiz kalabileceği durumlarda kullan.
---

# gbpd-dead-code-audit

## Ana İlke

Bu skill kod silmek için değil, silmenin güvenli olup olmadığını kanıtlamak için kullanılır. Kanıt yoksa silme yapılmaz.

## Akış

1. Silinmesi düşünülen sınıf, metot, endpoint, DTO, property veya asset'i netleştir.
2. Statik kullanım ara: `rg`, import, bean adı, endpoint path'i, string referansı, test referansı.
3. Dinamik kullanım ara: Spring XML, component-scan, reflection, Dozer, Hibernate mapping, WRO, JSP, Angular route, Redis channel, AMI event registration.
4. Üretim kontratı olup olmadığını kontrol et: REST API, Redis payload, DB column, result code, AMI variable, external system callback.
5. Kullanım bulunamadıysa hedefli test veya smoke doğrulama planı yaz.
6. Silme yapılacaksa ayrı patch ve ayrı commit olarak yap.
7. Silme sonrası hedefli testleri çalıştır ve diff'i özellikle config/contract açısından incele.

## Korumalı Alanlar

- `Constants.REDIS_CHANNEL`, result code sabitleri, AMI event class'ları.
- Entity, DTO ve mapper sınıfları.
- JSP/Angular controller/service dosyaları.
- Spring XML bean adları ve property placeholder'ları.
- `WEB-INF/wro.xml` içindeki asset listeleri.
- `src/main/resources` altındaki profile/property dosyaları.

## Çıktı Formatı

- İncelenen aday.
- Bulunan referanslar.
- Dinamik kullanım riski.
- Silme önerisi: güvenli, riskli veya silme.
- Gerekli test/doğrulama.
