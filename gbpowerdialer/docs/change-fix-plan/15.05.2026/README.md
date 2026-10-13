# 15.05.2026 Change/Fix Planları

- `FIX-GLB90090645-GAMMA-PENDING-RINGING-CACHE.md`: Predictive Gamma `ringingLoad` hesabının Asterisk AMI VarSetEvent gecikmesinden (1–2 sn) etkilenmesi ve uçuştaki çağrıların sayılmadığı pencere sonucu over-dial yaşanması sorununu, `CampaignRealTimeRingingManager` içine kısa ömürlü pending ringing buffer ekleyerek kapatma planı.
