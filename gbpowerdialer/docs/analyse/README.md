# Geliştirme Analizleri

Bu klasör, kod değişikliği gerektiren her isteğin önce analiz edilmesi için kullanılır. Amaç, üretimde çalışan gbpowerdialer davranışını kırmadan önce problemin kapsamını, riskini, test ihtiyacını ve önerilen çözüm yönünü yazılı hâle getirmektir.

## Klasör Düzeni

Analiz dosyaları gün bazlı klasörlerde tutulur:

```text
docs/analyse/GG.AA.YYYY/ETIKET-KIMLIK-KISA-AD.md
```

Örnek:

```text
docs/analyse/21.04.2026/FIX-BS-1423-AKTIF-CAGRI-SAYACI-DRIFT.md
```

## Etiketler

- `feat`: Yeni özellik veya kullanıcıya görünen yeni davranış.
- `fix`: Hata düzeltmesi, regresyon giderme veya acil üretim düzeltmesi.
- `docs`: Dokümantasyon, AGENTS veya skill metni değişikliği.
- `test`: Test ekleme veya test altyapısı değişikliği.
- `refactor`: Davranış değiştirmeyen kod düzenleme.
- `chore`: Bakım, repo düzeni veya geliştirici iş akışı değişikliği.
- `build`: Derleme, bağımlılık veya paketleme değişikliği.
- `ci`: CI/CD yapılandırması değişikliği.
- `perf`: Performans iyileştirmesi.
- `revert`: Önceki değişikliği geri alma.

Etiketi Codex belirler; belirsizlik varsa analiz dosyasında açıkça yazar. Dosya adında etiket büyük harfli kullanılır, analiz içindeki `İş Etiketi` alanında küçük harfli yazılır. `KIMLIK` alanı varsa task id, yoksa developer username'idir. `BUG` artık `fix` olarak karşılanır. `HF` ayrı etiket değildir; aciliyet `Öncelik: hotfix` notuyla belirtilir. `CR` ayrı etiket değildir; isteğin gerçek niteliğine göre commit tipi seçilir.

## Analiz Akışı

1. Kullanıcı derdini anlattıktan sonra önce kısa tespitlerini, risklerini ve çözüm önerini sun.
2. Ardından geliştiriciye analize geçilip geçilmeyeceğini sor; onay yoksa analiz dosyası açma.
3. Geliştiricinin ek bağlam, kısıt veya öncelik belirtmesini bekle.
4. Mümkünse task id'yi interaktif olarak sor; task id yoksa dosya kimliği olarak developer username'ini kullan.
5. İş etiketini belirle: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`, `ci`, `perf` veya `revert`.
6. Gün klasörü yoksa oluştur.
7. Analiz dosyasını `ETIKET-KIMLIK-KISA-AD.md` formatıyla yaz.
8. Kod okumadan varsayım yapma; ilgili sınıf, endpoint, Redis channel, AMI event, DB alanı ve testleri çıkar.
9. TDD stratejisini yaz: karakterizasyon testi mi, regression testi mi, yeni davranış testi mi?
10. Riskleri ve kırılabilecek production contract'larını belirt.
11. Kod değişikliği yapılacaksa execution plan oluşturulacağını belirt.
12. Analiz tamamlandıktan sonra geliştiriciye analizi incelemesi gerektiğini bildir; eksik bağlam varsa analizi güncelle.
13. Execution plan'a geçmeden önce netleşmemiş soruları geliştiriciye interaktif şekilde sor ve planı mesaj üzerinde netleştir.
14. Geliştirici nihai planı onaylamadan execution plan oluşturma; execution plan analizle paralel açılmaz.

Kod değişikliği gerektiren geliştirme, geliştirici tarafından incelenmiş analiz ve geliştiriciyle netleştirilip onaylanmış nihai plan sonrasında `docs/execution-plan` altında execution plan oluşturulmadan uygulanmaz. Execution plan, analiz dosyasındaki `KIMLIK` ile aynı kimliği taşır, onaylanan planın hiçbir adımını atlamayan TDD test planını, hedefli doğrulama komutlarını ve planın kapanış kriterini içerir. Planlanan testler çalıştırılıp başarılı olmadan execution plan kapatılamaz.

## Analiz Şablonu

```markdown
# ETIKET Kısa Başlık

## Bağlam

## İş Etiketi

feat | fix | docs | test | refactor | chore | build | ci | perf | revert

## Etkilenen Alanlar

## Mevcut Davranış

## Beklenen Davranış

## Kod Okuma Bulguları

## Riskler

## TDD Stratejisi

## Önerilen Çözüm Yönü

## Açık Sorular ve Netleştirme İhtiyacı

## Execution Plan Zorunluluğu

Kod değişikliği gerekiyorsa execution plan `docs/execution-plan/GG.AA.YYYY/ETIKET-KIMLIK-KISA-AD.md` altında, ancak açık sorular kapanıp geliştirici nihai planı onayladıktan sonra oluşturulacak. Plan, TDD test planını ve başarılı test kapanış kriterini içerecek.
```

## İndeks

- `21.04.2026/README.md`: 21.04.2026 tarihli geliştirme analizleri klasörü.
- `27.04.2026/README.md`: 27.04.2026 tarihli geliştirme analizleri klasörü.
- `28.04.2026/README.md`: 28.04.2026 tarihli geliştirme analizleri klasörü.
- `29.04.2026/README.md`: 29.04.2026 tarihli geliştirme analizleri klasörü.
- `30.04.2026/README.md`: 30.04.2026 tarihli geliştirme analizleri klasörü.
- `29.04.2026/README.md`: 29.04.2026 tarihli geliştirme analizleri klasörü.
- `05.05.2026/README.md`: 05.05.2026 tarihli geliştirme analizleri klasörü.
- `04.05.2026/README.md`: 04.05.2026 tarihli geliştirme analizleri klasörü.
- `03.06.2026/README.md`: 03.06.2026 tarihli geliştirme analizleri klasörü.
- `25.06.2026/README.md`: 25.06.2026 tarihli geliştirme analizleri klasörü.
- `07.08.2026/README.md`: 07.08.2026 tarihli geliştirme analizleri klasörü.
- `07.09.2026/README.md`: 07.09.2026 tarihli geliştirme analizleri klasörü.
- `16.09.2026/README.md`: 16.09.2026 tarihli geliştirme analizleri klasörü.

## Güncel Kapsam Notu

30.04.2026 itibarıyla bu klasör yalnız yeni feature işleri için kullanılır. Fix, hotfix, change request, refactor ve feature dışı diğer işler `docs/change-fix-plan` altında tek dosyada analiz detayları ve execution plan bölümüyle yürür.

## Plan Açma Onayı Notu

Sorun kullanıcıyla tartışılıp tespit edildiğinde ve çözüm yönü belli olduğunda ajan analiz dosyası açmadan önce kullanıcıya `Plan aşamasına geçeyim mi?` diye sorar. Mesajda bunun yeni feature için `new feature planı` olduğunu belirtir, plansız doğrudan çözüm uygulama seçeneğinin de bulunduğunu söyler ve kendi önerisini kısa gerekçeyle ekler. Kullanıcı plansız uygulamayı seçerse bu klasörde analiz dosyası açılmaz.
