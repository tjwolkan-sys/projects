# 29.04.2026 Geliştirme Analizleri

Bu klasör, 29.04.2026 tarihinde açılan geliştirme analizlerini tutar.

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

- `FIX-GLB90048674-PD-CDR-USER-RESULT-CODE-KACAGI.md`: Webphone/Asterisk/AQOS tarafında tamamlanan başarılı çağrılarda `pd_cdr.user_result_code_id` alanının boş kalması için kök neden analizi.
