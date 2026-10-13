# `origin/main` Sync Standardı

bs-gamma-analyzer'da varsayılan branch `main`'dir. Feature/fix branch'leri zaman zaman `origin/main` ile sync edilir; bu dosya güvenli sync akışını tanımlar.

## Önkoşul

- Çalışma ağacı temiz olmayabilir; ilgisiz değişiklikler korunur.
- Sync öncesi mevcut branch ve mevcut commit'ler `git status` ve `git log --oneline` ile çıkarılır.
- Local'de `origin/main` referansının güncelliği için `git fetch origin main` yapılır.

## Sync Analizi

1. **Incoming commits**: `git log <local-branch>..origin/main --oneline`.
2. **Local-only commits**: `git log origin/main..<local-branch> --oneline`.
3. **Etkilenen dosyalar**: `git diff --stat <local-branch>...origin/main`.
4. **Conflict riski**: `git merge --no-commit --no-ff origin/main` (sonrasında `git merge --abort`) ile dry-run.

## Geliştiriciye Sunulacak Rapor

Sync öncesi geliştiriciye şu rapor verilir:

- Incoming commit özeti (sayı + en kritik dosyalar).
- Local commit özeti.
- Çakışma riski olan dosyalar listesi.
- Önerilen birleştirme stratejisi (`merge` veya `rebase`).
- Sync sonrası test çalıştırma planı.

Geliştirici onayı olmadan merge/rebase yapılmaz.

## Merge Akışı

1. Geliştirici onayı alındıktan sonra `git merge origin/main` yapılır.
2. Conflict çıkarsa minimum kapsamda çözülür; tek taraflı `ours/theirs` tercih edilmez.
3. Conflict mantık etkiliyorsa ilgili pytest komutları çalıştırılır.
4. Merge commit mesajı kısa ve açıklayıcı tutulur.

## Conflict Çözüm İlkeleri

- Mantık etkilemeyen format/whitespace conflict'leri minimum müdahaleyle çözülür.
- Mantık etkileyen conflict'lerde her iki tarafın niyeti incelenir; gerekirse karakterizasyon testi yazılır.
- AGENTS.md, skill dosyaları, history dosyaları gibi append-only dokümanlarda her iki tarafın eki birlikte korunur.
- History dosyalarında tek taraflı silme yapılmaz; çakışma raporlanır.

## Test Doğrulama

- Conflict mantık etkilemediyse pytest gerekli değildir; doğrulama notu plan/history'ye yazılır.
- Conflict mantık etkilediyse ilgili modülün hedefli pytest paketi çalıştırılır.
- Tam suite yalnız geniş kapsamlı conflict durumunda çalıştırılır.

## Kapanış

- Sync sonrası `git status` temiz olmalı; ilgisiz değişiklikler korunmuş olmalıdır.
- Anlamlı dosya değişikliği oluştuysa `bsg-agent-history` ile günlük history kaydı eklenir.
- Sync sonucu ve test özetleri kullanıcıya raporlanır.
