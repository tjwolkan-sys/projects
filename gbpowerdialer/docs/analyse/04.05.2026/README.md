# 04.05.2026 Geliştirme Analizleri

Bu klasör, 04.05.2026 tarihinde açılan geliştirme analizlerini tutar.

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

- `FEAT-GLB90114534-FCT-ROUTING.md`: FCT (Fixed Cellular Terminal) için QSS `getRouteDetails` listesini kullanarak `cause 34 → TRC 25` durumunda aynı çağrı içinde sıradaki caller ID ile yeniden originate atan, kampanya bazlı feature flag'le yönetilen list-based routing analizi.
