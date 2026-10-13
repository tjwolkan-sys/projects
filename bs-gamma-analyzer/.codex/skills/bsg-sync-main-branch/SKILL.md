---
name: bsg-sync-main-branch
description: bs-gamma-analyzer'da feature/fix local branch'lerini remote `origin/main` ile güvenli sync etmek, PR conflict riskini raporlamak ve onaylı merge/conflict çözümü yapmak için. Sync öncesi geliştiriciye risk raporu sunmak ve onaysız merge yapmamak gerektiğinde kullan.
---

# bsg-sync-main-branch

## Amaç

Local branch'leri `origin/main` ile çakıştırma riskini azaltarak ve geliştirici onayı alınarak senkronize etmek. Pull request açmadan önce conflict riskini görünür kılmak.

## Temel Kurallar

- Çalışma ağacı kirli olabilir; ilgisiz local değişiklikler korunur.
- Sync öncesi mevcut commit'ler ve `git status` çıktıları çıkarılır.
- Geliştirici onayı olmadan merge/rebase yapılmaz.
- Tek taraflı `--strategy=ours` veya `--strategy=theirs` ile blanket conflict çözümü yapılmaz.

## Ön Kontrol

1. `git status`: ilgisiz local değişiklik var mı?
2. `git branch --show-current`: hangi branch üzerindeyiz?
3. `git fetch origin main`: remote referansı tazeledi mi?
4. `git log -1 --oneline origin/main`: remote'ın güncel tepe commit'i.

## Sync Analizi

- **Incoming commits**: `git log <local-branch>..origin/main --oneline`
- **Local commits**: `git log origin/main..<local-branch> --oneline`
- **Etkilenen dosyalar**: `git diff --stat <local-branch>...origin/main`
- **Çakışma riski**: `git merge --no-commit --no-ff origin/main` ile dry-run; sonrasında `git merge --abort`.

## Geliştiriciye Sunulacak Rapor

- Incoming commit özeti (sayı + en kritik dosyalar/modüller).
- Local commit özeti.
- Çakışma riski olan dosyalar listesi.
- Hangi modülleri etkilediği: `core/`, `app.py`, `db/`, `docs/`, `.codex/`.
- Önerilen birleştirme stratejisi: `merge` (varsayılan) veya `rebase` (linear tarih için).
- Sync sonrası test çalıştırma planı: pytest paketleri.

## Merge Akışı

1. Geliştirici onayı al.
2. `git merge origin/main` (veya istenmişse `git rebase origin/main`).
3. Conflict varsa minimum kapsamla çözmeye başla.
4. Çözüm sonrası `git status` temiz mi doğrula.
5. Test çalıştırmayı gerektiren değişiklik varsa pytest çalıştır.
6. Merge commit mesajı kısa ve açıklayıcı tut: `merge: origin/main → <local-branch>`.

## Conflict Çözüm İlkeleri

- Mantık etkilemeyen format/whitespace conflict'leri minimum müdahaleyle çözülür.
- Mantık etkileyen conflict'lerde her iki tarafın niyeti incelenir; gerekirse karakterizasyon testi yazılır.
- AGENTS.md, skill dosyaları, history dosyaları gibi append-only dokümanlarda her iki tarafın eki birlikte korunur.
- History dosyasında tek taraflı silme yapılmaz; çakışma raporlanır.
- `core/formula.py` çakışmasında Java parity riski açıkça belirtilir.
- `core/anomaly_detector.py` çakışmasında kategori adları ve severity skalası karşılaştırılır.
- `db/queries.py` çakışmasında SQL contract'ı kontrol edilir.

## Kapanış

- Sync sonrası `git status` temiz olmalı; ilgisiz değişiklikler korunmuş olmalıdır.
- Anlamlı dosya değişikliği oluştuysa `bsg-agent-history` ile günlük history kaydı eklenir.
- Sync sonucu, conflict özeti ve test özetleri kullanıcıya raporlanır.
- PR açılmasına henüz onay verilmemişse PR komutu çalıştırılmaz.
