# 27.04.2026 Geliştirme Analizleri

Bu klasör, 27.04.2026 tarihinde açılan geliştirme analizlerini tutar.

## Yeni Dosya Formatı

```text
ETIKET-KIMLIK-KISA-AD.md
```

`KIMLIK` alanı varsa task id, yoksa developer username'idir. Bu klasördeki mevcut dosyalar eski standardı koruyan tarihsel kayıtlardır.

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

- `FIX-DNC-PHONE-EXPIRED-DATE-VALIDATION.md`: DNC telefon numarası expire date doğrulamasındaki eksik backend contract ve tutarsız frontend date parsing davranışı.
