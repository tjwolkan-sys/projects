# 10.07.2026 Geliştirme Uygulama Planları

Bu klasör 10.07.2026 tarihinde açılan geliştirme uygulama planlarını içerir.

## Planlar

- `FEAT-294608-CAMPAIGN-INFO-ENDPOINT.md`: Harici uygulama için running/idle ayrımı olmadan tüm istenen kampanyalara `CampaignListDTO` döndüren yeni `/api/campaigns/info` endpoint'i; running context'ten, idle DB entity + memento snapshot'ından beslenir. Mevcut `realtimeInfo` (web + API) ve frontend akışı korunur.
