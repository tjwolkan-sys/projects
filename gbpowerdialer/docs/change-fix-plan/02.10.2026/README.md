# 02.10.2026 Change/Fix Planları

- `FIX-333599-PAYLASILAN-TABLO-MUKERRER-ARAMA.md`: Aynı result tablosunu paylaşan kampanyalarda (KFK_TAHSILAT camp 6 / camp 17) startup DB check cleanup'ının canlı kardeş kampanyanın rezervasyonlarını silmesi ve görüşmedeki kaydın tekrar `Call`'a dönüşüp mükerrer aranması. Çözüm: cleanup'a kardeşin STOP→START anı kesmesi (A) + FIX-330538 guard'ının görüşmedeki çağrıları kapsaması (B). Uygulandı; hedefli testler yeşil, PR hedef dalı bekleniyor.
- `FIX-333607-KAMPANYA-AKSIYON-PARALELLIK-GAMMA-STARTUP.md`: Global `CampaignService.action` kilidinin campaign bazlı kilide çevrilmesi ve GAMMA startup sorgularının sınırlanması (FIX-333607). İndeks satırı 333599 develop sync'i sırasında eklendi.
