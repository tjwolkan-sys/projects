# Change/Fix Plan Standardı

Bu klasör, yeni feature olmayan kod değişikliği istekleri için tek dosyada analiz detayını ve execution planı tutar.

## Kapsam

Bu akış şu işler için kullanılır:

- `fix`: Hata düzeltmesi, regression giderme veya hotfix.
- `refactor`: Davranış değiştirmeyen güvenli kod düzenleme.
- `perf`: Performans iyileştirmesi.
- `test`: Test veya test altyapısı değişikliği.
- `build`, `ci`, `chore`, `docs`, `revert`: Kod davranışı etkileyen feature dışı işler.

Yeni feature işleri bu klasöre yazılmaz.

## Klasör Düzeni

```text
docs/change-fix-plan/GG.AA.YYYY/ETIKET-KIMLIK-KISA-AD.md
```

## Zorunlu Akış

1. Kullanıcı derdini anlattıktan sonra kısa tespitlerini, risklerini ve çözüm önerini sun.
2. Planlama dokümanı açmadan önce geliştirici onayı al.
3. Mümkünse task id iste; yoksa dosya kimliği olarak developer username'ini kullan.
4. İlgili kodu, testleri ve contract boundary'lerini oku (Java parity, kategori adları, Claude prompt'u, SQL şeması).
5. Tek plan dosyasına analiz detaylarını ve execution planı birlikte yaz.
6. `Açık Sorular` bölümünü her zaman ekle; yoksa `Yok` yaz.
7. Cevaplanmamış açık soru varsa planı yazdıktan hemen sonra kullanıcıdan yanıt iste.
8. Cevaplanmamış açık soru varken kod değişikliğine başlama.
9. Kullanıcı yanıtlarını plan dosyasına işle, gerekiyorsa uygulama adımlarını güncelle ve sonra kod değişikliğine geç.
10. Tamamlanan adımları checkbox ile işaretle.
11. Planlanan testler başarılı olmadan planı tamamlandı sayma.

## Plan Şablonu

```markdown
# ETIKET Kısa Başlık Change/Fix Planı

## Bağlam

## İş Etiketi

fix | docs | test | refactor | chore | build | ci | perf | revert

## Öncelik

normal | hotfix

## Etkilenen Alanlar

## Mevcut Davranış

## Beklenen Davranış

## Kod Okuma Bulguları

## Root Cause / Değişiklik Gerekçesi

## Riskler

## TDD Stratejisi

## Açık Sorular

- [ ] <cevaplanması gereken soru>

## Execution Plan

- [ ] Onaylanan planın tüm adımları bu dosyaya işlendi.
- [ ] Mevcut davranışı kilitleyen karakterizasyon veya regression testi yaz.
- [ ] Beklenen davranışı kanıtlayan başarısız testi yaz.
- [ ] En küçük üretim kodu değişikliğini yap.
- [ ] Hedefli testi yeşile çevir.
- [ ] Gerekliyse küçük refactor yap.
- [ ] Refactor sonrası testi tekrar çalıştır.
- [ ] Doküman veya history güncellemesini tamamla.

## Performans Etkisi

- Hot path durumu:
- Event başına ek maliyet:
- Pandas iterasyonu / scan / allocation etkisi:
- Memory etkisi:
- Azaltım veya ölçüm notu:

## Doğrulama

## Kapanış Kriteri

- [ ] Açık sorular cevaplandı veya `Yok` olarak işaretlendi.
- [ ] Planlanan testler çalıştırıldı ve başarılı oldu.
- [ ] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu
```
