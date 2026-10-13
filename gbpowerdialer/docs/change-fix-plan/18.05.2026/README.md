# 18.05.2026 Change/Fix Planları

- `FIX-GLB90090645-RINGING-LOAD-UCB.md`: Predictive Gamma `ringingLoad` hesabına Binomial UCB (Upper Confidence Bound) ekleyerek düşük AR + büyük ringing havuzu birleşiminde eş zamanlı cevap kümelerinin yarattığı rc78/abandon yükünü azaltma planı. `ringingLoadMean`, `ringingLoadSafe`, `ringingLoadConfidenceK` telemetri alanları aynı PR kapsamında eklendi.
