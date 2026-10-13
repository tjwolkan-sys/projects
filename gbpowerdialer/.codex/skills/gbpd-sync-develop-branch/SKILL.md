---
name: gbpd-sync-develop-branch
description: gbpowerdialer'da feature/fix gibi local çalışma branch'lerini remote origin/develop ile güvenli sync etmek için kullan. PR öncesi develop değişikliklerini almak, conflict riskini raporlamak, onaylı merge yapmak, conflict çözüm planı sunmak, merge commit'i tamamlamak veya develop senkronizasyonu gerektiğinde kullan.
---

# gbpd-sync-develop-branch

## Amaç

PR aşamasındaki conflict yükünü azaltmak için current local branch'e `origin/develop` değişikliklerini kontrollü şekilde merge eder. Bu skill, sync öncesi risk raporu üretir; conflict varsa çözüm stratejisini yazmadan önce geliştirici onayı ister.

## Temel Kurallar

- `develop` branch'e geçme, `develop` üzerinde commit atma veya `develop` push etme.
- Current branch üzerinde çalış; detached HEAD veya `develop` üzerindeysen işlemi durdur.
- Working tree temiz değilse otomatik stash, commit veya revert yapma; önce durumu raporla.
- `git reset --hard`, `git push --force`, blanket `ours/theirs` çözümü ve onaysız conflict edit'i kullanma.
- Merge öncesi geliştiriciye sync raporu sun ve açık onay almadan `git merge` çalıştırma.
- Conflict çıktıysa dosya bazlı çözüm planını sun ve açık onay almadan conflict marker edit'i yapma.

## Ön Kontrol

1. Current branch'i öğren:

   ```powershell
   git branch --show-current
   ```

2. Working tree durumunu kontrol et:

   ```powershell
   git status --short
   ```

3. Remote tanımını kontrol et:

   ```powershell
   git remote -v
   ```

4. Current branch `develop` ise veya working tree'de uncommitted değişiklik varsa sync'i başlatma. Geliştiriciye önce branch/çalışma ağacı durumunu raporla.

## Sync Analizi

1. Remote develop'i güncelle:

   ```powershell
   git fetch origin develop
   ```

2. Ahead/behind sayısını çıkar:

   ```powershell
   git rev-list --left-right --count HEAD...origin/develop
   ```

   İlk sayı current branch'e özel commit sayısıdır. İkinci sayı `origin/develop` tarafındaki incoming commit sayısıdır.

3. Incoming develop commit'lerini listele:

   ```powershell
   git log --oneline --decorate HEAD..origin/develop
   ```

4. Local branch commit'lerini listele:

   ```powershell
   git log --oneline --decorate origin/develop..HEAD
   ```

5. Olası conflict riskini dosya kesişimiyle çıkar:

   ```powershell
   $mergeBase = git merge-base HEAD origin/develop
   git diff --name-only "$mergeBase..HEAD"
   git diff --name-only "$mergeBase..origin/develop"
   ```

   İki listedeki ortak dosyalar conflict riski olarak raporlanır. Ortak dosya olmaması conflict olmayacağını garanti etmez; sadece riskin düşük olduğunu gösterir.

## Geliştiriciye Sunulacak Rapor

Merge öncesi rapor şu bilgileri içermelidir:

- Current branch.
- Remote branch: `origin/develop`.
- Local-only commit sayısı ve kısa commit listesi.
- Incoming develop commit sayısı ve kısa commit listesi.
- Kesişen dosyalar ve conflict riski.
- Önerilen işlem: `git merge --no-ff origin/develop`.
- Test/doğrulama planı: docs-only ise `git diff --check`; code conflict çözülürse ilgili hedefli testler.

Geliştirici onayı yoksa merge başlatılmaz.

## Merge Akışı

1. Onaydan sonra merge çalıştır:

   ```powershell
   git merge --no-ff origin/develop
   ```

2. Conflict yoksa merge commit tamamlanır. Ek commit atma; merge commit hash'ini ve doğrulama sonucunu raporla.

3. Conflict varsa işlemi durdurup conflict dosyalarını listele:

   ```powershell
   git diff --name-only --diff-filter=U
   ```

4. Conflict dosyalarını oku, alan bazlı çözüm planı hazırla ve geliştirici onayı iste.

5. Onaydan sonra minimal conflict çözümü yap, dosyaları stage et ve merge commit'i tamamla:

   ```powershell
   git add <resolved-files>
   git commit
   ```

## Conflict Çözüm İlkeleri

- Aynı davranışı koruyan iki değişiklik varsa iki tarafı da kapsayan en dar çözümü uygula.
- Blanket `ours` veya `theirs` kullanma; ancak tek dosyada açıkça gerekmekteyse nedeni raporla ve onay al.
- AMI, Redis, campaign lifecycle, AngularJS route/WRO, persistence contract veya runtime state dosyalarında ilgili project skill'i de kullan.
- Conflict çözümü uygulama davranışını etkiliyorsa hedefli test çalıştır. Docs-only conflict'te test çalıştırma; `git diff --check` yeterlidir.
- Kullanıcının veya başka developer'ın ilgisiz local değişikliklerini revert etme.

## Kapanış

- `git status --short` ile merge sonrası durumu raporla.
- Çalıştırılan doğrulamaları ve çalıştırılmayan testlerin nedenini açık yaz.
- Anlamlı sync/conflict çözümü yapıldıysa `gbpd-agent-history` akışıyla `docs/history/GG.AA.YYYY.md` dosyasına append-only kısa kayıt ekle.
