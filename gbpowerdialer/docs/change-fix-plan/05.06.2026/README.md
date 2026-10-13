# 05.06.2026 Change/Fix Planları

- `FIX-320173-TELEPHONY-RC-NULL-FINALIZE-KAYBI.md`: `pd_cdr.telephony_rc` NULL kalan kayıtların iki kök nedeninin (agent logout-orphan yolunun pd_cdr persist etmemesi; `processCallResult`'ın yan etki exception'ıyla persist'e ulaşamadan kesilmesi) persist-first finalize sözleşmesi, skip-guard ve görünürlük sarmalamasıyla kalıcı çözümü.
- `FIX-GLB90090645-STARTUP-AR-AND-ACD-LCD.md`: Gamma startup AR sabitlerini gerçek üretim AR değerlerine yaklaştırma (0.90 → 0.50/0.60) ve ACD agent dağıtımında grup-içi shuffle yerine LCD sıralı + aynı priority'li grupları tek havuza birleştirme planı. Mutlu Sendromu olarak adlandırılan "uzun avail bekleyen agent'a çağrı gitmemesi" sorununu boost'lu predictive kampanyalarda kökten çözer.
- `FIX-320176-AGI-AGENT-DIAL-SAME-USER-RETRY-STORM.md`: Müşteri cevapladıktan sonra agent'a bağlamayı
  deneyen `DialWithAbandonTimeoutScript` AGI döngüsünde aynı agent'a kontrolsüz tekrar dial
  (same-user retry storm) ve "Caller hung up before dial" busy-spin'ini gideren AGI-only düzeltme
  planı. Kapsam: per-call denenen-user exclusion, dial sonucu okuma + caller-hangup erken çıkış,
  bounded loop/backoff. ACD distribution semantiği ve phantom-available quarantine bu planda
  değiştirilmez (ayrı ACDEngine işi).
- `FIX-GLB90090645-ACD-AVAIL-TIME-COMPARATOR.md`: Cross-group ACD sort'u ve `shouldSwitchUser` swap kararını `user.lastCallEndDate` yerine `user.statusSetDate` üzerinden çalışacak şekilde düzeltir. lastCallEndDate çağrı bittikten sonraki wrapup/break sürelerini saymadığı için break/wrapup'tan yeni dönen agent yanlış öne çıkıyordu; statusSetDate AVAILABLE status başlangıcı olduğu için gerçek avail süresi sinyalidir.
