---
name: gbpd-nextjs-migration-slice
description: gbpowerdialer AngularJS/JSP legacy arayüzünü Next.js'e güvenli taşımak için kullan. Strangler pattern, sayfa bazlı migration, route proxy, API contract, design parity, shadow UI, feature flag, Next.js test planı veya Angular ekranını emekli etmeden yeni frontend çıkarmak gerektiğinde.
---

# gbpd-nextjs-migration-slice

## Amaç

AngularJS/JSP frontend'i yaşayan ürünü kesmeden Next.js'e parça parça taşımak. Bu skill roadmap tutmaz; roadmap `docs/project/NEXTJS-FRONTEND-MIGRATION-ROADMAP.md` dosyasındadır.

## Migration İlkeleri

1. Sayfa veya workflow bazlı küçük slice seç.
2. Mevcut Angular route, controller, template, factory ve backend endpoint contract'ını çıkar.
3. Önce Angular davranışını testle veya contract dokümanıyla kilitle.
4. Next.js tarafını aynı API contract'ına bağla; backend davranışı aynı kalır.
5. İlk sürüm read-only veya shadow UI olabilir.
6. Feature flag veya route proxy ile kontrollü geçiş yap.
7. Angular ekranı emekli edilmeden önce kullanıcı rolü, CSRF/auth, localization ve grid/form davranışı karşılaştırılır.
8. Cutover sonrası Angular route, WRO kaydı ve kullanılmayan JS/template dosyaları dead-code audit ile kaldırılır.

## Taşınacak Contract'lar

- Route path ve navigation davranışı.
- Backend endpoint path, method, request ve response shape.
- Form validation ve hata gösterimi.
- Localization key ve çoklu dil davranışı.
- Grid kolonları, sorting, filtering, pagination ve export davranışı.
- CSRF, session timeout, yetki ve error redirect davranışı.
- Upload/download akışları.

## Next.js Slice Çıktısı

Her slice için şunlar yazılır:

- Angular kaynakları: route, controller, service, template, WRO kaydı.
- API contract listesi.
- Next.js route ve component boundary'si.
- TDD test planı.
- Shadow veya feature flag stratejisi.
- Rollback planı.
- Angular emeklilik koşulu.

## TDD Beklentisi

- Angular tarafı karakterizasyon testiyle kilitlenir.
- Next.js tarafı için component, route veya API client testi planlanır.
- Backend contract değişmiyorsa contract testi değişmemelidir.
- Kullanıcı planlı akışı seçtiyse yeni Next.js test altyapısı kurulana kadar migration slice üretim kodu yazmadan önce test altyapısı planı ilgili `docs/execution-plan` veya `docs/change-fix-plan` dosyasında görünür olmalıdır.
- Planlı akışta plan dosyasında cevaplanmamış açık soru varsa Next.js veya AngularJS cutover koduna başlanmaz.

## Kaçınılacaklar

- Birden fazla büyük Angular ekranını tek slice içinde taşımak.
- API response shape'i frontend migration sırasında sessizce değiştirmek.
- Angular ve Next.js ekranlarını aynı anda farklı iş kurallarıyla yaşatmak.
- Angular dosyalarını kullanım kanıtı çıkarmadan silmek.
