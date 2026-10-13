---
name: bsg-commit-discipline
description: bs-gamma-analyzer'da commit öncesi diff, test, secret ve ilgisiz değişiklik kontrolü yapmak için. Yeni paket eklenmesi, Anthropic API key sızıntısı, PostgreSQL credential, history append-only kuralı ve Conventional Commit etiketi disiplinine uyum sağlamak gerektiğinde kullan.
---

# bsg-commit-discipline

## Amaç

Commit hazırlığı sırasında diff'i incelemek, test sonucunu doğrulamak, secret/PII sızıntısını engellemek, ilgisiz değişiklikleri ayıklamak ve Conventional Commit standardına uygun mesaj yazmak.

## Akış

1. `git status` ile tüm değişiklikleri çıkar.
2. `git diff` ile satır bazlı incele; ilgisiz değişiklik varsa kullanıcıya raporla.
3. Test çalıştırılması gereken bir değişiklikse `pytest <hedef>` çalıştır.
4. Plan dosyası ile değişiklik kapsamı uyumlu mu doğrula.
5. History kaydı gerekiyorsa `bsg-agent-history` ile günlük dosyaya ekle; mevcut içerik korunur.
6. README/index dosyaları append-only kuralına uygun mu kontrol et.
7. Commit mesajını Conventional Commit standardında yaz.

## Commit Mesajı Standardı

```text
<type>: <kısa Türkçe başlık>

<isteğe bağlı Türkçe gövde>
- madde 1
- madde 2

<isteğe bağlı footer>
```

- `type`: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`, `ci`, `perf`, `revert`.
- Başlık 72 karakteri geçmemeli.
- Gövdede neden ve etkilenen alanlar açıklanır.
- Footer'da plan dosyası referansı veya issue id verilebilir.

Örnekler:

- `fix: gamma formülü bootstrap mode kontrolü düzeltildi`
- `feat: ai analiz sekmesinde recovery sinyali bölümü eklendi`
- `refactor: app.py içindeki abandon analizi sekmesi modüle taşındı`
- `docs: anomaly detection mimari dokümanı güncellendi`

## Kontrol Listesi

- [ ] `git diff` satır satır incelendi.
- [ ] İlgisiz değişiklik yok veya kullanıcıya raporlandı.
- [ ] Anthropic API key, PostgreSQL şifresi, gerçek IP veya müşteri bilgisi sızmadı.
- [ ] `.codex/TOOL-PATHS.local.properties` commit edilmedi.
- [ ] Test sonuçları planda işaretli.
- [ ] Plan dosyası ve doküman güncellendi.
- [ ] History kaydı gerekiyorsa append-only eklendi.
- [ ] Commit mesajı Conventional Commit standardında.

## Yapılmayacaklar

- `--no-verify` veya hook bypass kullanma.
- Aynı commit'te `feat` + `fix` + `refactor` karıştırma.
- Çalışmaya ait olmayan dosyaları stage etme (`git add .` yerine seçici staging).
- Mevcut history kaydını silme/yeniden yazma.
- README/index satırlarını yeniden numaralandırma.
- Otomatik linter sonucu olarak büyük format-only değişiklik üretme; format çalışması ayrı `chore` planında yürür.
