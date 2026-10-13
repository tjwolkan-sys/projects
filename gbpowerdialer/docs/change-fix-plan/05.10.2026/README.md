# 05.10.2026 Change/Fix Planları

- `FIX-333599-PARALEL-START-CLEANUP-YARISI.md`: FIX-333607 ile campaign start'ları paralel hale gelince FIX-333599 (A) kardeş kesmesinin, kardeş henüz görünür değilken boş kalması ve paylaşılan tablodaki taze rezervasyonların silinebilmesi. Çözüm: kesme = min(cleanup başlangıç anı, kardeş liveSince), her zaman uygulanır. Uygulandı; hedefli testler yeşil, develop sync bekliyor.
