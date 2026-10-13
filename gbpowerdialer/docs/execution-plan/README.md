# Geliştirme Uygulama Planları

Bu klasör, kod değişikliği gerektiren her geliştirme için oluşturulan uygulama planlarını tutar. Uygulama planı, kullanıcı tarafından onaylanan nihai planın hangi sırayla uygulanacağını, hangi TDD testlerinin yazılacağını, hangi doğrulamaların çalıştırılacağını ve tamamlanan adımların nasıl işaretleneceğini gösterir.

## Klasör Düzeni

Plan dosyaları gün bazlı klasörlerde tutulur:

```text
docs/execution-plan/GG.AA.YYYY/ETIKET-KIMLIK-KISA-AD.md
```

Örnek:

```text
docs/execution-plan/21.04.2026/FIX-BS-1423-AKTIF-CAGRI-SAYACI-DRIFT.md
```

Analiz ve execution plan dosyalarının kimlik ve kısa adı aynı olmalıdır. Böylece `docs/analyse/GG.AA.YYYY/FIX-BS-1423-X.md` dosyası ile `docs/execution-plan/GG.AA.YYYY/FIX-BS-1423-X.md` dosyası bire bir eşlenir. Dosya adındaki etiket büyük harfli, dosya içindeki iş etiketi küçük harfli yazılır. `KIMLIK` alanı varsa task id, yoksa developer username'idir.

## Plan Akışı

1. Geliştirici tarafından incelenmiş ve onaylanmış analiz dosyasını referans göster.
2. Analiz sonrası geliştiriciyle netleştirilip onaylanan nihai plan özetini plana ekle.
3. Netleşmemiş soru varken veya geliştirici nihai planı onaylamamışken execution plan oluşturma; execution plan analizle paralel açılmaz.
4. Onaylanan planın hiçbir adımını atlamadan TDD test planını yaz.
5. Hot path etkisi varsa `Performans Etkisi` bölümünü doldur.
6. Uygulama adımlarını kontrol listesi olarak yaz.
7. Kapanış kriterini ve hedefli doğrulama komutlarını yaz.
8. Execution plan tamamlandığında geliştiriciye planı incelemesi gerektiğini bildir.
9. Geliştirici onayı alınmadan uygulama veya kod değişikliği başlatma.
10. Her adım tamamlandığında checkbox işaretle.
11. Kod değişikliği plan dışına çıkarsa planı güncelle, geliştiriciye bildir, sonra uygulamaya devam et.
12. Finalde test komutlarını ve sonuçlarını plana işle.
13. Planlanan testler çalıştırılıp başarılı olmadan plan tamamlandı/kapatıldı sayılmaz.

## Kapanış Kuralı

- Execution plan, planlanan testler başarılı olmadan kapatılamaz.
- Test çalıştırılamıyorsa planın doğrulama adımı açık kalır ve blocker net yazılır.
- Uygulama adımları bitmiş olsa bile başarısız veya çalıştırılamayan test varsa plan durumu "doğrulama beklemede" kabul edilir.
- Yalnızca dokümantasyon/skill gibi uygulama testi gerektirmeyen işlerde "uygulama testi gerekli değil" gerekçesi doğrulama bölümüne yazılır.
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
- Call/event/tick başına ek maliyet:
- IO/scan/lock/serialization/log etkisi:
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

## İndeks

- `21.04.2026/README.md`: 21.04.2026 tarihli geliştirme uygulama planları klasörü.
- `27.04.2026/README.md`: 27.04.2026 tarihli geliştirme uygulama planları klasörü.
- `28.04.2026/README.md`: 28.04.2026 tarihli geliştirme uygulama planları klasörü.
- `30.04.2026/README.md`: 30.04.2026 tarihli geliştirme uygulama planları klasörü.
- `29.04.2026/README.md`: 29.04.2026 tarihli geliştirme uygulama planları klasörü.
- `05.05.2026/README.md`: 05.05.2026 tarihli geliştirme uygulama planları klasörü.
- `04.05.2026/README.md`: 04.05.2026 tarihli geliştirme uygulama planları klasörü.
- `03.06.2026/README.md`: 03.06.2026 tarihli geliştirme uygulama planları klasörü.
- `25.06.2026/README.md`: 25.06.2026 tarihli geliştirme uygulama planları klasörü.
- `10.07.2026/README.md`: 10.07.2026 tarihli geliştirme uygulama planları klasörü.
- `07.08.2026/README.md`: 07.08.2026 tarihli geliştirme uygulama planları klasörü.
- `07.09.2026/README.md`: 07.09.2026 tarihli geliştirme uygulama planları klasörü.
- `16.09.2026/README.md`: 16.09.2026 tarihli geliştirme uygulama planları klasörü.

## Güncel Kapsam Notu

30.04.2026 itibarıyla bu klasör yalnız yeni feature işleri için kullanılır ve `docs/analyse` içindeki `FEAT` analiziyle eşleşir. Fix, hotfix, change request, refactor ve feature dışı diğer işler `docs/change-fix-plan` altında tek dosyada analiz detayları ve execution plan bölümüyle yürür. Her plan dosyasında `Açık Sorular` bölümü bulunur; cevaplanmamış açık soru varsa kod değişikliğine başlanmaz.

## Plan Açma Onayı Notu

Sorun kullanıcıyla tartışılıp tespit edildiğinde ve çözüm yönü belli olduğunda ajan execution plan aşamasına geçmeden önce kullanıcıya `Plan aşamasına geçeyim mi?` diye sorar. Yeni feature için bunun `new feature planı` olduğunu belirtir, plansız doğrudan çözüm uygulama seçeneğinin de bulunduğunu söyler ve kendi önerisini kısa gerekçeyle ekler. Kullanıcı plansız uygulamayı seçerse bu klasörde execution plan dosyası açılmaz.
