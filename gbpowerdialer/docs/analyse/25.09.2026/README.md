# 25.09.2026 Geliştirme Analizleri

Bu klasör 25.09.2026 tarihinde açılan geliştirme analizlerini içerir.

## Analizler

- `FEAT-GLB90114534-CDR-RAPOR-EKRANI.md`: Options altında kampanya bazlı `pd_cdr` rapor ekranı; server-side ui-grid, whitelist'li native SQL, NOLOCK/timeout/pencere/semaphore yük frenleri, streaming CSV export ve DBA index teslimatı analizi.
- `FEAT-GLB90114534-CDR-RAPOR-EKRANI-DDL.sql`: `pd_cdr (campaign_id, call_start_time)` index'i için idempotent DDL, ortam kontrol sorguları ve seek doğrulama şablonu.
