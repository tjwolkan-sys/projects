# Geliştirme Uygulama Planları

Bu klasör, kod değişikliği gerektiren her geliştirme için oluşturulan uygulama planlarını tutar.

## Klasör Düzeni

```text
docs/execution-plan/GG.AA.YYYY/ETIKET-KIMLIK-KISA-AD.md
```

Analiz ve execution plan dosyalarının kimlik ve kısa adı aynı olmalıdır.

## Plan Akışı

1. Geliştirici tarafından incelenmiş ve onaylanmış analiz dosyasını referans göster.
2. Analiz sonrası geliştiriciyle netleştirilip onaylanan nihai plan özetini plana ekle.
3. Netleşmemiş soru varken veya geliştirici nihai planı onaylamamışken execution plan oluşturma.
4. Onaylanan planın hiçbir adımını atlamadan TDD test planını yaz.
5. Hot path etkisi varsa `Performans Etkisi` bölümünü doldur.
6. Uygulama adımlarını kontrol listesi olarak yaz.
7. Kapanış kriterini ve hedefli doğrulama komutlarını yaz.
8. Execution plan tamamlandığında geliştiriciye planı incelemesi gerektiğini bildir.
9. Her adım tamamlandığında checkbox işaretle.
10. Kod değişikliği plan dışına çıkarsa planı güncelle, geliştiriciye bildir, sonra uygulamaya devam et.
11. Finalde test komutlarını ve sonuçlarını plana işle.

## Kapanış Kuralı

- Execution plan, planlanan testler başarılı olmadan kapatılamaz.
- Test çalıştırılamıyorsa planın doğrulama adımı açık kalır.
- Yalnızca dokümantasyon/skill gibi uygulama testi gerektirmeyen işlerde "uygulama testi gerekli değil" gerekçesi yazılır.
- Final yanıtta her zaman kısa Test Özeti verilir.

## Plan Şablonu

```markdown
# ETIKET Kısa Başlık Uygulama Planı

## Analiz Referansı

`docs/analyse/GG.AA.YYYY/ETIKET-KIMLIK-KISA-AD.md`

## Onaylanan Plan Özeti

## Amaç

## Kapsam Dışı

## Performans Etkisi

- Hot path durumu:
- Event başına ek maliyet:
- Pandas iterasyonu / scan / allocation etkisi:
- Memory etkisi (precursor/recovery, cache):
- Azaltım veya ölçüm notu:

## TDD Planı

- [ ] Mevcut davranışı kilitleyen karakterizasyon testi.
- [ ] Beklenen davranışı kanıtlayan başarısız test.
- [ ] Regression testi.

## Uygulama Kontrol Listesi

- [ ] Onaylanan planın tüm adımları execution plan'a işlendi.
- [ ] Etkilenen kod alanlarını hazırla.
- [ ] Testi kırmızı hâle getir.
- [ ] En küçük üretim kodu değişikliğini yap.
- [ ] Hedefli testi yeşile çevir.
- [ ] Gerekliyse küçük refactor yap.
- [ ] Refactor sonrası testi tekrar çalıştır.
- [ ] Hot path varsa performans etkisini doğrula veya maliyet notunu tamamla.
- [ ] Doküman veya history güncellemesini tamamla.

## Doğrulama

## Kapanış Kriteri

- [ ] Planlanan testler çalıştırıldı ve başarılı oldu.
- [ ] Final yanıtta kısa Test Özeti sunuldu.

## Riskler ve Geri Dönüş

## Tamamlanma Notu
```

## Güncel Kapsam Notu

Bu klasör yalnız yeni feature işleri için kullanılır ve `docs/analyse` içindeki `FEAT` analiziyle eşleşir. Diğer işler `docs/change-fix-plan` altında tutulur.
