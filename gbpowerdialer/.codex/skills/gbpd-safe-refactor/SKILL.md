---
name: gbpd-safe-refactor
description: gbpowerdialer'da davranış değiştirmeden güvenli refactor yapma akışı. Özellikle engine, Asterisk, Redis, Campaign modeli, ACDEngine veya legacy Spring XML boundary'lerinde küçük mekanik refactor gerektiğinde kullan.
---

# gbpd-safe-refactor

## Refactor Ön Koşulu

Refactor, davranış değişikliği değildir. Davranış değişecekse önce `gbpd-tdd-workflow` ve gerekirse `gbpd-characterization-tests` kullan.

## Akış

1. Refactor hedefini tek cümleyle yaz: isim düzeltme, metot çıkarma, dependency boundary, tekrar azaltma, package içi düzenleme.
2. Etkilenen davranışı koruyan hedefli testleri bul.
3. Test yoksa küçük bir karakterizasyon testi ekle.
4. Refactor öncesi hedefli testi çalıştır.
5. En küçük mekanik değişikliği yap.
6. Aynı hedefli testi tekrar çalıştır.
7. Diff'i kontrol et; davranış, config, Redis contract, AMI event veya DB schema değişmediğini doğrula.

## Repoya Özel Riskler

- Spring XML, WRO, JSP/Angular route, Dozer, Hibernate ve reflection kullanımı statik referansları gizleyebilir.
- `@Component` adı, bean adı, property adı ve endpoint path'i refactor sırasında kırılabilir.
- `Campaign` transient alanları ve AMI event akışı davranış değişikliği olmadan taşınmamalıdır.
- Test yoksa "sadece isim değişikliği" bile riskli kabul edilir.

## Yapılmayacaklar

- Refactor patch'inde dead code silme.
- Refactor patch'inde davranış düzeltme.
- Refactor patch'inde bağımlılık yükseltme.
- Refactor patch'inde secret veya ortam dosyası düzenleme.
