---
name: gbpd-commit-discipline
description: gbpowerdialer'da commit hazırlığı ve değişiklik hijyeni için kullan. Diff kontrolü, README/index/history append-only kontrolü, ilgisiz değişiklik ayırma, test sonucu raporlama, secret/IP sızıntısı kontrolü, Türkçe ve tip etiketli commit mesajı hazırlığı gerektiğinde kullan.
---

# gbpd-commit-discipline

## Amaç

Commit, yalnızca ilişkili ve doğrulanmış değişiklikleri içermelidir. Bu skill commit atmadan önce çalışma ağacını temiz anlamaya ve riskleri görünür yapmaya yarar.

## Akış

1. `git status --short` ile değişen dosyaları listele.
2. Her dosyanın bu iş ile ilişkisini kontrol et.
3. İlgisiz kullanıcı değişikliklerine dokunma ve onları commit kapsamına alma.
4. `git diff` ile davranış, config, secret ve format dışı değişiklikleri incele.
5. README, index ve history dosyalarında append-only dışı satır silme, toplu yeniden yazım veya yeniden sıralama olup olmadığını ayrıca kontrol et.
6. Test çıktısını veya test çalıştırılamadıysa nedenini kaydet.
7. Commit mesajının tip etiketini yapılan işin niteliğine göre seç.
8. Commit mesajını Türkçe ve açıklayıcı olacak şekilde şu formatta öner:

```text
<tip>: <Türkçe kısa ve açıklayıcı başlık>

- Yapılan ana değişiklik
- Test/doğrulama
- Risk veya migration notu
```

## Commit Mesajı Standardı

- Commit başlığı zorunlu olarak `<tip>: <başlık>` formatında başlar.
- Tip etiketi İngilizce Conventional Commit türüdür; başlık ve gövde Türkçe yazılır.
- Scope yalnızca anlam katıyorsa kullanılır: `fix(ami): ...`, `feat(redis): ...`.
- Başlık emir kipinde, kısa ve özgül olmalıdır; `fix`, `update`, `düzenleme` gibi belirsiz başlık önerme.
- Commit gövdesi yapılan değişikliği, çalıştırılan testi veya doğrulamayı ve varsa riski Türkçe özetlemelidir.

Yaygın tip etiketleri:

- `feat`: Yeni özellik veya kullanıcıya görünen yeni davranış.
- `fix`: Hata düzeltmesi veya regresyon giderme.
- `docs`: Dokümantasyon, AGENTS veya skill metni değişikliği.
- `test`: Test ekleme veya test altyapısı değişikliği.
- `refactor`: Davranış değiştirmeyen kod düzenleme.
- `chore`: Bakım, repo düzeni veya geliştirici iş akışı değişikliği.
- `build`: Derleme, bağımlılık veya paketleme değişikliği.
- `ci`: CI/CD yapılandırması değişikliği.
- `perf`: Performans iyileştirmesi.
- `revert`: Önceki commit'i geri alma.

Örnek:

```text
docs: commit mesajı standardını netleştir

- Commit başlığı için tip etiketi zorunluluğu eklendi.
- Türkçe ve açıklayıcı gövde beklentisi yazıldı.
- Doğrulama: Skill dosyası gözden geçirildi.
```

## Kontrol Listesi

- Secret, gerçek IP, şifre, token veya credential eklendi mi?
- `.codex/skills` dışındaki `.codex` çalışma dosyaları yanlışlıkla izleniyor mu?
- Roadmap, history ve AGENTS güncellemeleri gerekiyorsa yapıldı mı?
- Yeni feature dışı kod değişikliğinde kullanıcı planlı akışı seçtiyse `docs/change-fix-plan` kullanıldı mı; plansız uygulamayı seçtiyse plan dosyası açılmadı mı?
- Planlı akışta açık soru varsa kod değişikliği bloke edildi mi?
- `docs/README.md`, `docs/**/README.md`, `docs/history/*.md` ve `docs/history/README.md` diff'leri append-only mi?
- README/index/history diff'inde eski satır silme, tüm dosyayı yeniden üretme veya toplu sıralama değişikliği var mı?
- Yeni skill eklendiyse AGENTS envanteri güncellendi mi?
- Kod değiştiyse TDD veya karakterizasyon testi var mı?
- Commit önerisi tip etiketiyle başlıyor, Türkçe ve açıklayıcı mı?

## Yapılmayacaklar

- Kullanıcı açıkça istemedikçe commit atma.
- İlgisiz dosyaları staging'e alma.
- Başarısız veya çalıştırılmamış testleri başarılı gibi yazma.
- Etiketsiz, İngilizce veya belirsiz commit mesajı önerme.
- README/index/history dosyalarında geliştirici onayı olmadan eski satır silme, yeniden sıralama veya komple dosya değiştirme yapma.
