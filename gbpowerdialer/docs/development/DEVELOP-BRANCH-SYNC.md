# Develop Branch Sync Standardı

Bu doküman, feature/fix branch'lerinin PR öncesinde remote `origin/develop` ile güvenli sync edilmesi için ekip standardını tarif eder. Amaç conflict'leri PR ekranında değil, developer'ın local branch'i üzerinde kontrollü şekilde görmek ve çözmektir.

## Ne Zaman Kullanılır

- PR açmadan önce branch uzun süre `develop` gerisinde kaldıysa.
- PR'da conflict göründüyse.
- Aynı alanlarda başka PR'ların merge edildiği biliniyorsa.
- Büyük feature branch'i birkaç gün açık kalacaksa ara ara sync gerekiyorsa.

## Standart Yaklaşım

- Sync işlemi current feature/fix branch üzerinde yapılır.
- Remote kaynak `origin/develop` olur.
- Varsayılan strateji `merge` olur; rebase ekip standardı değildir.
- `develop` branch'e commit atılmaz, local `develop` push edilmez.
- Force push kullanılmaz.
- Working tree temiz değilse sync başlatılmaz.

Önerilen merge komutu:

```powershell
git merge --no-ff origin/develop
```

Bu yaklaşım branch history içinde develop sync noktasını görünür tutar ve conflict çözümünü merge commit içinde saklar.

## Onay Kapıları

Sync akışı iki ayrı onay kapısına sahiptir:

1. Merge öncesi sync raporu onayı.
2. Conflict çıkarsa conflict çözüm planı onayı.

İlk raporda current branch, incoming develop commit'leri, local branch commit'leri, dosya kesişimleri ve conflict riski görünür olmalıdır. Onay verilmeden merge başlatılmaz.

Conflict çıkarsa Codex önce conflict dosyalarını inceler, dosya bazlı çözüm stratejisini yazar ve developer onayından sonra edit yapar.

## Beklenen Rapor Formatı

Sync öncesi raporda şu bilgiler yer alır:

- Current branch.
- Remote branch: `origin/develop`.
- Local-only commit sayısı ve kısa commit listesi.
- Incoming develop commit sayısı ve kısa commit listesi.
- Local branch ile develop tarafında değişen ortak dosyalar.
- Conflict beklenip beklenmediği.
- Önerilen merge ve doğrulama planı.

## Conflict Çözüm Standardı

- Blanket `ours` veya `theirs` çözümü kullanılmaz.
- İki tarafın davranışı mümkün olduğunca korunur.
- AMI, Redis, campaign lifecycle, AngularJS, persistence veya runtime state alanlarında ilgili project skill de devreye alınır.
- Code conflict çözümü davranışı etkiliyorsa hedefli test çalıştırılır.
- Docs-only conflict için test çalıştırılmaz; `git diff --check` yeterlidir.

## Commit Mesajı

Conflict yoksa `git merge --no-ff origin/develop` merge commit'i yeterlidir. Conflict çözüldüyse çözüm aynı merge commit içinde tamamlanır.

Örnek merge commit başlığı:

```text
Merge remote-tracking branch 'origin/develop' into <branch-name>
```

Gerekirse Türkçe açıklama gövdesi eklenebilir:

```text
- origin/develop değişiklikleri local branch ile sync edildi.
- Conflict çözümü <alan> dosyalarında yapıldı.
- Doğrulama: <komut veya neden çalıştırılmadı>.
```

## Skill Kullanımı

Bu akış için Codex project skill'i:

```text
gbpd-sync-develop-branch
```

Developer "branch'imi develop ile sync et", "PR conflict'lerini localde çöz", "origin/develop'i branch'ime al" veya benzer bir talep yazdığında bu skill kullanılmalıdır.
