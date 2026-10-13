# 21.04.2026 Geliştirme Analizleri

Bu klasör, 21.04.2026 tarihinde açılan geliştirme analizlerini tutar.

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

- `FEAT-ILK-TDD-KARAKTERIZASYON-TESTLERI.md`: Java backend ve AngularJS frontend için ilk dar kapsamlı karakterizasyon/contract testleri.
- `FEAT-ANGULARJS-KISA-ORTA-VADE-TEST-STANDARDI.md`: AngularJS 1.x için kısa vadeli Java/JUnit contract ve orta vadeli Karma/Jasmine test standardı.
- `FEAT-ANGULARJS-BASEFACTORY-UNIT-TESTLERI.md`: AngularJS ortak `baseFactory` davranışı için gerçek Karma/Jasmine unit testleri.
- `FIX-API-RESOURCE-OBJECTMAPPER-CONTEXT.md`: API resource test context'lerinde eksik `ObjectMapper` bean hatası.
- `FIX-TAM-JAVA-SUITE-KALAN-HATALAR.md`: Tam Maven suite'te API context düzeltmesi sonrası kalan legacy test failure/error kümeleri.
