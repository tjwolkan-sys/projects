# 28.04.2026 Geliştirme Analizleri

Bu klasör, 28.04.2026 tarihinde açılan geliştirme analizlerini tutar.

## Yeni Dosya Formatı

```text
ETIKET-KIMLIK-KISA-AD.md
```

`KIMLIK` alanı varsa task id, yoksa developer username'idir.

Geçerli etiketler:

- `FEAT`
- `FIX`
- `DOCS`
- `TEST`
- `REFACTOR`
- `CHORE`
- `BUILD`
- `CI`
- `PERF`
- `REVERT`

## Analizler

- `FIX-GLB90048674-SAVEINITIALCDR-901-HOTFIX.md`: `saveInitialCDR` ile erken açılan CDR kayıtlarının AMI hangup sonucuyla güncellenmeden cleanup sırasında 901 ile kapanması için hotfix analizi.
