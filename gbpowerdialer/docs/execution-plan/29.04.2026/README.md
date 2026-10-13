# 29.04.2026 Geliştirme Uygulama Planları

Bu klasör, 29.04.2026 tarihinde onaylanan geliştirme uygulama planlarını tutar.

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

## Planlar

- `FIX-GLB90048674-PD-CDR-USER-RESULT-CODE-KACAGI.md`: Webphone `RESULT_CODE` event'i NPE ile abort olduğunda `pd_cdr` ve campaign result table user result code alanlarının kaybolmasını engelleyen hotfix uygulama planı.
