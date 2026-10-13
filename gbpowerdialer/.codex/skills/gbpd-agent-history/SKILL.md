---
name: "History İşlem Günlüğü Senkronizasyonu"
description: "Çalışmayı yürüten ajanın repo içinde dosya ekleme, silme veya değiştirme yaptığı görevlerden sonra append-only history kaydı oluşturma, README/index dosyalarını append-only güncelleme ve yalnız yeni gün açıldığında docs/history/README.md indeksini güncelleme standardını tanımlar. Soru-cevap, salt inceleme ve planlama gibi read-only işlerde kullanılmaz."
slug: "agents-worklog"
version: "1.0.0"
language: "tr"
tags: ["agents", "documentation", "workflow", "project-memory"]
---

# History İşlem Günlüğü Senkronizasyonu

## Amaç

Codex, Gemini, Claude veya başka bir ajanın repo içinde dosya ekleme, silme veya değiştirme yaptığı görevlerden sonra proje hafızasını güncel tutmak için ilgili günün history dosyasına append-only kayıt eklemek, README/index dosyalarını append-only korumak ve yalnız yeni gün açıldığında `docs/history/README.md` indeksini güncellemek.

## Ajan Kapsamı

- Bu skill tüm ajanlar için geçerlidir; `Codex` geçen eski repo kuralları `çalışmayı yürüten ajan` olarak yorumlanır.
- Gemini veya Claude bu skill'i kullanırken eski ajan adıyla yazılmış ifadeleri kendisini hariç tutan bir kapsam daraltması olarak yorumlamaz.

## Not

Bu repoda günlük history dosyaları mevcut repo standardı nedeniyle `docs/history/GG.AA.YYYY.md` adıyla tutulur. Kayıt gövdesi minimal şablonla yazılır.

## Ne Zaman Kullanılır

- Çalışmayı yürüten ajan bu görev kapsamında repo dosyalarında değişiklik yaptıysa.
- Kod, test, doküman, skill, konfigürasyon veya benzeri bir dosya eklendiyse, silindiyse veya güncellendiyse.
- Analiz, execution plan, change-fix-plan, mimari doküman veya doğrulama sonucu dosyaya yazıldıysa.
- Değişiklik davranışsal olmasa bile çalışma ağacında ajan kaynaklı dosya diff'i oluştuysa.

## Ne Zaman Kullanılmaz

- Soru-cevap, salt inceleme, planlama, brainstorming veya yönlendirme cevaplarında.
- `git log`, `rg`, dosya okuma, komut çıktısı inceleme gibi read-only işlerde.
- Kod/doküman/dosya değişikliği yapılmadan test veya doğrulama sonucu sadece kullanıcıya raporlandıysa.
- Çalışma ağacındaki değişiklikler çalışmayı yürüten ajanın bu görevde yaptığı değişiklikler değilse.

## Zorunlu Akış

1. Görev tamamlanınca çalışmayı yürüten ajanın bu görevde repo dosyalarında değişiklik yapıp yapmadığını kontrol et.
2. Ajan kaynaklı dosya değişikliği yoksa history kaydı ekleme.
3. Ajan kaynaklı dosya değişikliği varsa günün tarihine karşılık gelen `docs/history/GG.AA.YYYY.md` dosyasını oku, mevcut içeriği koru ve dosyanın sonuna yeni kayıt ekle.
4. O güne ait dosya yoksa oluştur ve `docs/history/README.md` indeksine yalnız yeni gün linkini ekle; mevcut linkleri silme, yeniden sıralama veya dosyayı baştan üretme.
5. Kayıtta şu alanları zorunlu yaz:
   - Kapsam
   - Değişiklikler
   - Doğrulama
6. Başlıkta `timestamp | task-id-or-username | slug` kullan.
7. Mevcut meşru kayıtları silme, düzenleme, üzerine yazma veya numara düzeltme yapma; sadece sona yeni kayıt ekle.
8. Eski kayıtta düzeltme gerekiyorsa eski kaydı değiştirme; yeni append-only kayıtla düzeltme notu ekle.

## README ve İndeks Append-Only Kuralı

- `docs/README.md`, `docs/**/README.md`, `docs/history/README.md` ve benzeri indeks dosyaları append-only kabul edilir.
- Bu dosyalarda yeni analiz, execution plan, change-fix-plan, mimari doküman, history günü veya klasör linki gerekiyorsa önce mevcut dosyayı oku ve yalnız ilgili yeni satırı ekle.
- Mevcut satırları silme, toplu yeniden sıralama, başlıkları yeniden yazma, tüm dosyayı şablondan tekrar üretme veya format-only düzenleme yapma.
- Eski indeks satırı hatalı görünüyorsa mevcut satırı değiştirme; önce geliştirici onayı iste veya ayrı append-only düzeltme notu ekle.
- README/index diff'inde eski satır silindiği, büyük blok değiştiği veya dosyanın tamamı yeniden yazılmış gibi göründüğü anda işlemi durdur ve overwrite riski olarak raporla.

## Kayıt Şablonu

```md
### YYYY-MM-DD HH:mm:ss +03:00 | BS-1423 | dnc-expire-date-validation
- Kapsam: <modül/ekran/servis>
- Değişiklikler:
  - <madde 1>
  - <madde 2>
- Doğrulama: <test komutu sonucu>
```

Task id yoksa username kullan:

```md
### YYYY-MM-DD HH:mm:ss +03:00 | GLB90048674 | dnc-expire-date-validation
- Kapsam: <modül/ekran/servis>
- Değişiklikler:
  - <madde 1>
  - <madde 2>
- Doğrulama: <test komutu sonucu>
```

## Kurallar

- Kısa ve tekrar etmeyen yazım kullan.
- Hardcoded kişisel not ekleme.
- Yapılmayan işi yapılmış gibi yazma.
- Doğrulama çalıştırılmadıysa açıkça belirt: `Doğrulama: çalıştırılmadı`.
- Read-only soru-cevap veya inceleme kayıtlarını history'ye yazma.
- Kayıt append-only tutulur; eski başlıkları yeniden numaralandırma, eski kayıtları yeniden yazma.
- Önceki kayıt başka developer, başka ajan veya başka task'a aitse hiçbir koşulda düzenlenmez, silinmez veya üzerine yazılmaz.
- Mevcut günlük history dosyası yeniden üretilmez; dosya içeriği korunur ve yeni kayıt yalnız dosyanın sonuna eklenir.
- README/index dosyaları da yeniden üretilmez; mevcut içerik korunur ve yalnız yeni indeks satırı eklenir.
- Merge veya rebase conflict'i sırasında `ours`/`theirs` ile tek taraflı history seçimi yapılmaz; iki taraftaki meşru kayıtlar korunur.
- History diff'inde eski satır silindiği görülürse işlemi durdur ve geliştiriciye overwrite riski olarak raporla.

## Done Kriteri

- Ajan kaynaklı dosya değişikliği varsa ilgili günlük history dosyası güncel bir append-only işlem kaydı içeriyor.
- Ajan kaynaklı dosya değişikliği yoksa history dosyası değiştirilmemiş kalıyor.
- README/index dosyası güncellendiyse diff yalnız yeni satır ekliyor; eski satır silmiyor veya dosyayı yeniden üretmiyor.
- Kayıt formatı standart alanları taşıyor.
- Eklenen kayıt aynı işin tekrar değerlendirilmesini önleyecek kadar net bağlam içeriyor.
