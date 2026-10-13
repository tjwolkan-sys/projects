# Test Senaryoları

Bu klasör, geliştirmeler için QA/kabul test case setlerini tutar. Her case Given/When/Then ile yazılır ve yanında otomatik test kapsamı (`[Otomatik: <sınıf>]`, `[Otomatik (Karma)]`) veya `[Manuel]` işareti bulunur. Dosya adı standardı `ETIKET-KIMLIK-KISA-AD.md` biçimindedir (analyse/execution-plan ile aynı kimlik).

## Setler

- `FEAT-321997-AGENT-CALLBACK-RESET-STRATEGY.md`: Agent Callback Reset stratejisi (Timer/Attempt-Count) — UI, runtime (attempt/convert/availability/offline owner fetch), snap-to-window, legacy regresyon, sağlıklı-akış regresyon ve migration test case'leri.
- `FEAT-GLB90114534-FCT-KANAL-KAPASITE-GATE.md`: FCT kanal kapasite gate'i — mutlaka test edilmesi gereken 10 kritik senaryo (öteleme, eş zamanlılık, headroom, fail-open, cause 34, release bütünlüğü, TTL, kampanya yaşam döngüsü, QSS hata ayrımı, context süzmesi) + monitor/lokalizasyon/performans ek kontrolleri.
