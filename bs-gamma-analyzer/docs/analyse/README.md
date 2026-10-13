# Geliştirme Analizleri

Bu klasör, kod değişikliği gerektiren her isteğin önce analiz edilmesi için kullanılır. Amaç, üretimle parity tutan bs-gamma-analyzer davranışını kırmadan önce problemin kapsamını, riskini, test ihtiyacını ve önerilen çözüm yönünü yazılı hâle getirmektir.

## Klasör Düzeni

Analiz dosyaları gün bazlı klasörlerde tutulur:

```text
docs/analyse/GG.AA.YYYY/ETIKET-KIMLIK-KISA-AD.md
```

Örnek:

```text
docs/analyse/07.05.2026/FEAT-BS-1423-XN-STABILITY-DASHBOARD.md
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

## Analiz Akışı

1. Kullanıcı derdini anlattıktan sonra önce kısa tespitlerini, risklerini ve çözüm önerini sun.
2. Ardından geliştiriciye analize geçilip geçilmeyeceğini sor; onay yoksa analiz dosyası açma.
3. Geliştiricinin ek bağlam, kısıt veya öncelik belirtmesini bekle.
4. Mümkünse task id'yi interaktif olarak sor; task id yoksa dosya kimliği olarak developer username'ini kullan.
5. İş etiketini belirle.
6. Gün klasörü yoksa oluştur.
7. Analiz dosyasını `ETIKET-KIMLIK-KISA-AD.md` formatıyla yaz.
8. Kod okumadan varsayım yapma; ilgili modül, kolon, SQL, sekme, session state ve testleri çıkar.
9. TDD stratejisini yaz: karakterizasyon testi mi, regression testi mi, yeni davranış testi mi?
10. Riskleri ve kırılabilecek production contract'larını belirt (Java parity, kategori adları, Claude prompt'u, SQL şeması).
11. Kod değişikliği yapılacaksa execution plan oluşturulacağını belirt.
12. Analiz tamamlandıktan sonra geliştiriciye analizi incelemesi gerektiğini bildir; eksik bağlam varsa analizi güncelle.
13. Execution plan'a geçmeden önce netleşmemiş soruları geliştiriciye interaktif şekilde sor ve planı mesaj üzerinde netleştir.
14. Geliştirici nihai planı onaylamadan execution plan oluşturma.

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

Kod değişikliği gerekiyorsa execution plan `docs/execution-plan/GG.AA.YYYY/ETIKET-KIMLIK-KISA-AD.md` altında, ancak açık sorular kapanıp geliştirici nihai planı onayladıktan sonra oluşturulacak.
```

## Güncel Kapsam Notu

Yeni feature işleri bu klasör + `docs/execution-plan` iki dosyalı akışını kullanır. Fix, hotfix, change request, refactor ve feature dışı diğer işler `docs/change-fix-plan` altında tek dosyada analiz detayları ve execution plan bölümüyle yürür.

## Plan Açma Onayı Notu

Sorun kullanıcıyla tartışılıp tespit edildiğinde ve çözüm yönü belli olduğunda ajan analiz dosyası açmadan önce kullanıcıya `Plan aşamasına geçeyim mi?` diye sorar.
