# FIX-GLB90048674 AMI Event-Worker Lag → URC/Abandon Kaybı Change/Fix Planı

- İş Etiketi: fix
- Kimlik: GLB90048674
- Slug: ami-event-worker-lag-urc-abandon-kaybi
- Tarih: 2026-06-18
- Öncelik: hotfix (aktif üretim veri kaybı; **tek planda + tek deploy** ile kapatılacak — kullanıcı kararı 2026-06-18)
- Branch: develop'tan yeni `fix/ami-event-worker-lag` (kullanıcı onayı)
- Plan Durumu: **Uygulandı; superpowers code-review + verification geçti; hedefli regresyon 110/0 yeşil (2026-06-18). Commit/PR + pre-prod/deploy bekliyor.** Tek pass / tek deploy. (B) result-code/URC lag-resilience + (#4) AGI düzeltmesi veri kaybını kapatır; (A) güvenli per-event I/O azaltımı aynı branch'te. Ağır batch-async rewrite kapsam dışı; **hemen ardından ayrı plan + kendi no-loss/order test seti + pre-prod soak ile yapılacak** (kullanıcı kararı 2026-06-18). Uygulama **superpowers TDD + code-review + verification** ile yürütülür (konu kritik).

## Bağlam

16.06.2026 AMI connection-loss kök-neden fix'i (`docs/change-fix-plan/16.06.2026/FIX-GLB90048674-AMI-CONNECTION-LOSS.md`) deploy edildi ve **AMI connection loss = 0** oldu. Ancak **bugüne (18.06.2026) ait** loglar, `pd_cdr`/campaign result tablosunda hâlâ TRC 78/84 abandon kayıtların oluştuğunu ve agent'ın gbwebphone'da girdiği user result code'unun kaybolduğunu gösteriyor. Yani sorun, **AMI bağlantısı sağlıklıyken** devam ediyor.

Kullanıcının paylaştığı 66 interaction id üzerinde yapılan log incelemesi (Elastic `turkcell-2026.06.18`):

- **0/66 kayıt** ne `promoteCdrToSuccessFromWebphoneResultCode` ne de `persistDurableResultCodeFallback` ile kurtarıldı (`Webphone result code recovery applied`=0, `Durable result code fallback applied`=0).
- 50 kez `Skipping durable result code fallback. CDR is not successful`; 44 kez aktif yolda `result code is not set by the user assigned to this call`; 3 kez `call has no assigned user`; 17 kez `ResultCode has been received and set for` (in-memory set ama persist yok); 3 kez promote `No registrar agent answer leg`.

`dl-59cc3a2f-3439-4753-b317-2bb985474854` (campaign 76 TC_KURUMSAL_TAHSILAT) tam zaman çizelgesi:

| Saat | Olay | Thread |
|------|------|--------|
| 09:09:47 | `Set User 9472 (BÜŞRA)` + originate | call-originator |
| 09:09:59 | Müşteri cevapladı; AGI `DialWithAbandonTimeoutScript` 9472'yi locked, dial, **BridgeEnter (müşteri↔9472)** | AGI / ami-event-worker-.16 |
| 09:10:01 | AGI: `dial outcome=NOT_ANSWERED for 9472` → 9472 release → `Set User 9478 (SEDEN)` dial → `CALLER_GONE` → AGI bitti (abandon). **in-memory user artık 9478** | AGI (AJ DaemonPool) |
| 09:10:11 | Agent **9472** result code gönderdi (id=180 HAT KESILDI) → `result code is not set by the user assigned to this call` → fallback `CDR is not successful` | webphone-event |
| **09:10:15–18** | `ami-event-worker-<dialer-ip>-0`, `dateReceived='09:09:59'` olan `ClientPredictiveAnswerEvent`/`AgentPredictiveAnswerEvent`'i **~16 sn geç** işledi → `Could not set answered by user flag` → hangup/retry/abandon finalize | yoğun predictive bağlantı worker'ı |

Aynı dosyada agent-leg bağlantısı (`.16` worker) zamanında işlerken, yoğun predictive sunucusunun (`.43`) worker'ı **13–17 sn geride**: event `dateReceived` ile worker'ın "Event received" log zamanı arasındaki fark (VarSet 09:09:47→09:10:00; PredictiveAnswer 09:09:59→09:10:15; Hangup 09:10:01→09:10:18). `dl-f1e86c64`'te de aynı `.43` worker'da ~17 sn lag gözlendi.

## İş Etiketi

fix

## Öncelik

hotfix (öneri — aktif veri kaybı: agent disposition'ları kayboluyor, çağrılar yanlış abandon sayılıp tekrar aranıyor, campaign result ↔ pd_cdr tutarsızlaşıyor). Nihai öncelik Açık Soru #1 ile kapanır.

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionDecorator.java`
  - `eventWorker` tek-thread worker (`:174`, `:338-339` `ThreadPoolExecutor(1,1)` + `LinkedBlockingQueue(AMI_EVENT_WORKER_QUEUE_CAPACITY)`), `onManagerEvent` (`:610-615`), `processEvent`, queue-depth ölçümü (`:671-672`).
  - Per-event senkron DB I/O işleyen handler'lar: `handleCallAnswer`/`saveCdrOnAnswer`, `handleAgentAnswer`/`AgentPredictiveAnswerEvent` (`Could not set answered by user flag` `:775`), `handleDialerHangup`/`processCallResult`, `insertCallResultToDB`.
- `gbpowerdialer/src/main/java/com/powerdialer/redis/WebphoneSubscriber.java`
  - `processCallWithResultCodeAndUpdateDNC` (`:257`), `shouldSetResultCode` (`:603-615`, volatile `call.user`'a bağlı user-match), `isWebphoneResultCodeRecoveryCandidate` (`:439`, `callAnswerTime==null` ön koşulu `:446`), `persistDurableResultCodeFallback` (`:337`), `isSuccessfulCdrForUserResultCode` (`:404`).
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/fastagi/DialWithAbandonTimeoutScript.java`
  - `DialOutcome.NOT_ANSWERED/CALLER_GONE` (`:90-94`), agent-hunt loop (`:140`), `call.setUser(null)`/user churn (`:278`). FIX-320176 (`05.06.2026`) ile aynı AGI; koordinasyon gerekir.
- `gbpowerdialer/src/main/java/com/powerdialer/util/UserPopulationEvaluator.java`
  - `evaluateAbandoned` (78/84 → abandoned), `shouldPopulateCampUserResultCode` (78/84 → URC null), `allowUrcWithoutMt` istisnası (`:313`).
- `gbpowerdialer/src/main/java/com/powerdialer/util/Constants.java`
  - `AMI_EVENT_WORKER_QUEUE_CAPACITY`, `WEBPHONE_RESULT_CODE_RECOVERABLE_TRC_IDS` (75/78/84).
- Model: `Call` (in-memory `user` + olası "atanan agent kümesi"/answeredByUser bayrakları).
- Testler: `gbpowerdialer/src/test/java/tests/ami/`, `WebphoneSubscriberTest`, `UserPopulationEvaluatorTest`.

## Mevcut Davranış

1. **Worker (16.06 Faz 1):** Her AMI bağlantısı için tek-thread `eventWorker`; `onManagerEvent` ucuz route'u worker'a verir, ağır iş (DB/CDR I/O) worker'da çalışır. Faz 3 (batch/async DB) **bilinçli ertelendiği** için per-event senkron DB I/O hâlâ worker üstünde. Tek thread + senkron DB → predictive burst'te kuyruk birikir → işleme gerçek-zamanın 13–17 sn gerisine düşer (kanıt: Bağlam tablosu). Blocking-put olduğu için event düşmez ama **geç** işlenir.
2. **AGI (`DialWithAbandonTimeoutScript`):** Ayrı `AJ DaemonPool` thread'lerinde, gerçek-zamanlı. Müşteri cevaplayınca agent dener; `NOT_ANSWERED` ise sıradakini dener; `CALLER_GONE` olunca abandon eder. Bu süreçte `call.user`'ı son denenen agent'a sabitler (örnekte 9472→9478). Asterisk kısa süre 9472'yi bridge etse de AGI senkron dial sonucunu `NOT_ANSWERED` görebiliyor (AGI/telephony zamanlama yarışı).
3. **Result code kabulü:** `shouldSetResultCode`, `RC.userId == call.getUser().getId()` (anlık volatile değer) ister. Mismatch → erken çıkış → durable fallback. Fallback `isSuccessfulCdrForUserResultCode` (TRC 10/24 + handlingStart) ister → 78/84'te false. Promote `isWebphoneResultCodeRecoveryCandidate` `callAnswerTime != null` ister (`:446`) → answer event'i lag yüzünden işlenmediyse CDR `callAnswerTime` null → aday değil. Promote'a ulaşan 3 kayıt da `findAgentRegistrarAnswerTime`'da düştü.
4. **Finalize:** Çağrı 78/84 ile finalize olunca `evaluateAbandoned` `abandoned=true`, `shouldPopulateCampUserResultCode` false → `cdr.userResultCodeId=null` (allowUrcWithoutMt hariç).

## Beklenen Davranış

1. Worker, predictive yükte gerçek-zamanı yakalar (lag birkaç saniye mertebesine iner); answer/agent-connect event'leri AGI abandon-timeout penceresi içinde işlenir → `call.user` gerçek bağlanan agent'i yansıtır.
2. Agent'ın result code'u, **anlık** in-memory `call.user`'a değil, çağrının **authoritative agent kanıtına** (çağrı yaşam döngüsünde atanmış agent kümesi ve/veya Asterisk registrar + webphone interaction kanıtı) göre kabul edilir; geç event durumunda bile doğru agent'in kodu kaybolmaz, yanlış agent'a yazılmaz.
3. Gerçek bridge kanıtı bulunan 78/84 çağrılar başarılıya hizalanır (promote); `callAnswerTime` lag'i tek başına promote'u engellemez.
4. Recovery idempotent + retriable: kanıt geç gelirse (read-after-write) tekrar denenir.
5. 16.06 invariant'ları korunur: tek-thread sıra, no-loss, drain-on-shutdown, counter lifecycle, ordering.

## Kod Okuma Bulguları

- `eventWorker` gerçekten tek-thread (`ThreadPoolExecutor(1,1)`), kapasite 50.000, blocking-put. Predictive `.43` bağlantısında bu tek thread saturate oluyor; `.16` (agent-leg) saturate olmuyor → lag bağlantı-başına ve yük-bağımlı.
- 16.06 planı Faz 3'ü (agresif batch/async DB) açıkça erteledi (`@Transactional` + 6+ çağıran + no-loss/order riski). Yani per-event senkron DB I/O worker'da kaldı; lag'in birincil nedeni bu.
- `Could not set answered by user flag` (`MCD:775`), geç gelen `AgentPredictiveAnswerEvent`'in çağrıya agent bağlayamadığının doğrudan kanıtı.
- `There is no such call in the cache for ... AgentCalledByDialerEvent` (geç gelen agent event'i çağrı kaldırıldıktan sonra işleniyor).
- AGI loop ve same-user/caller-hangup davranışı FIX-320176'da kısmen ele alınmış; bu plan AGI'nin **bridge olmuş ama NOT_ANSWERED raporlanan** vakasına ve user-churn'a dokunacaksa o planla çakışmamalı.
- Promote zaten registrar + webphone çapraz-kanıt mimarisine sahip; eksik olan: (a) aktif yolda hiç çağrılmaması, (b) `callAnswerTime` ön koşulunun lag'e duyarlı olması, (c) retry olmaması.

## Root Cause / Değişiklik Gerekçesi

**Birincil (yeni, 16.06 yan etkisi):** Faz 1 tek-thread worker + ertelenen Faz 3 → predictive yükte 13–17 sn event-işleme gecikmesi. 16.06 "bağlantı kopması"nı "event gecikmesi"ne çevirdi; üstelik bağlantı sağlıklı göründüğü için connection-loss recovery yolu da tetiklenmiyor → çağrı bayat state üzerinde tam (yanlış) yaşam döngüsünü tamamlayıp 78/84 abandon oluyor.

**İkincil (bağımsız, lag tarafından büyütülüyor):** (i) AGI abandon-timeout'un gerçek bridge ile yarışı + `call.user` churn'u; (ii) result-code kabulünün volatile `call.user`'a bağlı olması; (iii) promote/fallback ön koşullarının (`callAnswerTime`, registrar leg) lag'e duyarlı olması ve retry olmaması.

Case-specific yama yerine sözleşme: (A) worker gerçek-zamanı yakalasın; (B) result-code kabulü ve URC recovery, anlık volatile state yerine çağrının authoritative agent/bridge kanıtına dayansın ve idempotent/retriable olsun.

## Riskler

- **(A) worker throughput / batch-async DB:** 16.06'nın bilinçle ertelediği yüksek riskli iş; `@Transactional` + paylaşımlı contract + no-loss/order. Yanlış uygulanırsa veri kaybı/sıra bozulması. Çok-thread worker KULLANILMAZ (counter drift, 16.06 invariant'ı).
- **(B) result-code kabul kriterini gevşetmek:** Yanlış agent'a URC yazma riski → authoritative kanıt guard'ı (atanan-agent kümesi / registrar / webphone) şart; yalnız volatile user kalkacaksa yerine daha güçlü kanıt gelmeli.
- **AGI değişikliği** FIX-320176 ile çakışabilir → koordinasyon/ayrı tutma (Açık Soru #4).
- **Counter drift / ordering:** finalize ve worker sırası; 16.06 idempotans bayrakları korunmalı; testle doğrulanmalı.
- **Hot path:** Tüm değişiklikler event/call başına; `Performans Etkisi` zorunlu.
- **Geriye dönük veri:** Bugüne kadar kaybedilen kayıtlar bu planla otomatik düzelmez (Açık Soru #7).

## TDD Stratejisi

- **Karakterizasyon:** Worker geç işlerken (deterministik/gecikmeli executor) result code reddi + 78/84 abandon + URC null mevcut davranışını kilitle.
- **(A) Worker:** Per-event işleme süresi + queue derinliği instrumentation'ı (ölç-önce); backlog senaryosunda işleme sırası/no-loss regresyonu. (Batch/async seçilirse `CdrBatchOrderingNoLossRegressionTest` benzeri sıra+kayıpsızlık testi.)
- **(B) Result-code resilience:** `RC.userId`, çağrının yaşam döngüsünde atanmış agent kümesinde veya registrar/webphone kanıtında varsa kabul; hiçbir kanıtla eşleşmiyorsa reddi koru (yanlış agent yazılmaz). `WebphoneSubscriberTest` genişletilir.
- **(B) Promote:** `callAnswerTime==null` ama registrar+webphone kanıtı varken promote olur; idempotent + retry senaryosu.
- **UserPopulationEvaluator:** 78/84 + authoritative agent kanıtı varken URC korunur/promote akışına girer; kanıt yoksa mevcut kural korunur.
- Testler `tests/ami/`, `WebphoneSubscriberTest`, `UserPopulationEvaluatorTest`; Mockito + constructor boundary; PowerMock yalnız zorunluysa.

## Açık Sorular

Tüm açık sorular kullanıcı tarafından kapatıldı (2026-06-18).

- [x] **#1 Öncelik/sıralama:** Plan **tek pass + tek deploy** ile kapatılacak (faz bölünmesi yok). İç sıralama ajana bırakıldı: (B) result-code/URC lag-resilience ve (#4) AGI düzeltmesi veri kaybını kapatan çekirdek; (A) güvenli worker I/O azaltımı aynı branch/PR'da. Öncelik: hotfix.
- [x] **#2 (A) yaklaşımı:** Ekstra ölçüm/instrumentation **yok**; düzenleme şimdi yapılacak. Ajan kararı: 16.06'da ertelenen ağır/riskli **batch-async DB rewrite KAPSAM DIŞI** (no-loss/order riski + tek-güvenli-deploy hedefi). (A) = 16.06 invariant'larını koruyan **hedefli per-event DB I/O azaltımı** (gereksiz round-trip eliminasyonu); somut azaltımlar handler kodu okunarak TDD sırasında sabitlenir. (B)+(#4) veri kaybını worker hızından bağımsız kapattığı için konservatif (A) güvenlidir.
- [x] **#3 (B) result-code kabul kanıtı:** Öneri kabul. Birincil **(a) çağrının yaşam döngüsünde atanmış agent kümesi** (örnekte 9472 set edilmişti) + **(b) Asterisk registrar** + **(c) webphone interaction** ile güçlendirme. Yanlış-agent guard'ı korunur.
- [x] **#4 AGI race:** **Bu plana dahil.** AGI'nin "bridge oldu ama NOT_ANSWERED" yanlış raporlaması ve `call.user` churn'u bu kapsamda çözülecek; FIX-320176 davranışlarıyla çakışma olmayacak şekilde koordine edilir.
- [x] **#5 Branch:** develop'tan yeni `fix/ami-event-worker-lag`.
- [x] **#6 Interim flag:** `allowUrcWithoutMt`'e **dokunulmayacak.** Kalıcı (B) düzeltmesi URC'yi başarılı-kapatma (promote) yolundan koruduğu için flag gereksiz; flag'i açmak gerçekten başarısız çağrılarda da URC tutar (veri kalitesi riski) ve kampanya genelinde geniş yan etki doğurur. Mevcut davranış korunur.
- [x] **#7 Geriye dönük backfill:** Kapsam dışı; bu ekibin sorumluluğunda değil, aksiyon alınmayacak.

## Execution Plan

- [x] Açık sorular kullanıcı tarafından cevaplandı ve plana işlendi (2026-06-18).
- [x] develop'tan `fix/ami-event-worker-lag` branch'i açıldı (16.06 commit'lerinin develop'ta olduğu doğrulandı).
- [x] (#4) AGI testi (`DialWithAbandonTimeoutScriptTest#testAgentAnsweredViaDialStatusWhenAnsweredFlagLagged`): senkron `${DIALSTATUS}=ANSWER` lagged flag false iken ANSWERED sınıflanır; 1 dial, churn yok, abandon yok. RED→GREEN; 9/9 yeşil (FIX-320176 dahil korundu).
- [x] (B) promote testi (`WebphoneSubscriberTest#givenAmiLossCdrNullCallAnswerTimeWithCrossCheckEvidence...ThenPromoteToSuccess`): `callAnswerTime=null` + registrar+webphone kanıtı → promote. RED→GREEN. Mevcut `...WithoutCallAnswerTime...SkipPromote` karakterizasyonu yeni authoritative gate'e (registrar) göre güncellendi. 48/48 yeşil.
- [x] (A) Worker handler kodu okundu: tek güvenli hedefli azaltım (redundant `getByInteractionId` SELECT) **16.06 Faz 3-A'da zaten uygulanmış** (`markCdrPersistLookupAttempted`, MCD:840). Kalan per-event yazımlar (state başına `insertCallResultToDB`; ayrı `callAnswerTime` vs `callHandlingStartTime` CDR save'leri) güvenle kaldırılamaz — coalesce etmek (a) batch-async = ertelenen iş (kapsam dışı), (b) answer-save'leri birleştirmek (B)'nin düzelttiği `callAnswerTime`-null sorununu geri getirir. **Sonuç: bu deploy = (#4)+(B); worker-throughput batch-async follow-up'ta kalır.** Hot-path'e yeni kod eklenmedi (en güvenli sonuç).
- [x] En küçük üretim kodu değişiklikleri uygulandı (#4 `DialWithAbandonTimeoutScript`, B `WebphoneSubscriber`); A için güvenli ek değişiklik gerekmedi (Faz 3-A zaten yapmış).
- [x] Hedefli testler yeşile çevrildi; AMI/dialer/recovery regresyonu koşuldu (110/0).
- [x] Refactor (paylaşımlı `DIAL_STATUS_VARIABLE` sabiti) sonrası testler tekrar yeşil.
- [x] `gbpd-call-flow-performance-guardrails` gözden geçirildi ve `Performans Etkisi` gerçek değişikliğe göre güncellendi.
- [x] Mimari doküman (`docs/architecture/ASTERISK-AMI-AGI.md`) AGI answer-detection için güncellendi.
- [x] History kaydı (`docs/history/18.06.2026.md`) + indeks güncellendi.
- [x] superpowers code-review + verification-before-completion uygulandı.

## Performans Etkisi

- **Hot path durumu:** Hot path, ek maliyet var ve gerekçesi/azaltımı yazıldı.
- **(#4) AGI answer-detection (cevaplanan çağrı başına):** Asenkron `answeredByUser` flag'i lag'lediğinde, cevaplanan çağrı başına **bir adet senkron `getFullVariable("${DIALSTATUS}")` round-trip** eklenir (AGI thread'i zaten o çağrıya özel; FIX-320176 de dial başına bir DIALSTATUS okuması kabul etmişti). Bu maliyet, eski davranışın ürettiği çok daha pahalı sonucun (ekstra agent dial'ları + abandon + retry + URC kaybı) yerine gelir → net kazanç.
- **(B) webphone result-code yolu (event başına, dar alt küme):** `callAnswerTime` ön filtresi kaldırıldığı için yalnızca `TRC∈{75,78,84}` + `userResultCodeId==null` + aktif cache'te olmayan result-code event'lerinde iki authoritative DB lookup (registrar + webphone) artık bu alt küme için de çalışır. webphone-event thread'inde, event başına bir kez; **AMI hot path değil**. Promote idempotent (`userResultCodeId==null`); yanlış-agent guard'ları (registrar agent leg + per-user webphone talk_time) korunur.
- **(A) AMI worker:** Yeni per-event DB I/O **eklenmedi**; tek güvenli azaltım (redundant `getByInteractionId` SELECT) zaten Faz 3-A'da. Worker throughput'u kökten düşüren batch/async ayrı follow-up'ta.
- **IO/scan/lock/serialization/log etkisi:** Yeni geniş `synchronized` yok; çok-thread yok; yeni AMI hot-path DB query yok; yeni hot-path log yok.
- **Bilinen residual (follow-up):** `hasSuccessfulInteractionStatus` read-after-write yarışı — result-code event'i, ilgili agent'ın `wbp_interaction_status` satırı commit olmadan işlenirse promote bir kez no-op olur (retry yok). Üretim sıralaması genelde güvenli: agent çağrıyı bitirince webphone interaction-status (talk_time) yazılır, disposition (result-code event) ondan SONRA gelir. Bugünkü duruma (0/66 kurtarma) göre kesin iyileşme; bounded retry, batch/async worker follow-up'ında ele alınacak (producer sıralama garantisi orada doğrulanacak).
- **Doğrulama:** Üretim kanıtı: günlük `78/84 abandon + URC dolu` kayıt sayısı düşüşü + worker lag (`dateReceived`↔işlenme) izlenir.

## Doğrulama

`.codex/TOOL-PATHS.local.properties` okunur; Maven `-o` offline + `properties-tr-dev`. Eksik artifact indirilmez, raporlanır.

```text
mvn -o -P properties-tr-dev -Dtest=WebphoneSubscriberTest test
mvn -o -P properties-tr-dev -Dtest=UserPopulationEvaluatorTest test
mvn -o -P properties-tr-dev -Dtest=AwaitingUserResultCacheTransitionRegressionTest,AwaitingUserResultTimeoutPreserveRegressionTest,SaveInitialCdrHangupResultRegressionTest,ConnectionLossBridgedCallRecoveryRegressionTest test
```

(Yeni regression sınıfları eklenince komuta dahil edilir.) Predictive yük lokalde load-test edilemez; worker lag'in asıl doğrulaması pre-prod/prod gözlemidir.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı.
- [x] (#4) AGI, gerçek bridge oluştuğunda (senkron `DIALSTATUS=ANSWER`) abandon etmez ve `call.user`'ı churn etmez; FIX-320176 korundu; testle kilitlendi.
- [x] (B) Agent'ın result code'u, geç event (callAnswerTime null) durumunda bile authoritative kanıtla (registrar + per-user webphone talk_time) kabul edilir; yanlış agent'a yazılmaz; idempotent; testle kilitlendi.
- [x] (A) Worker handler I/O incelendi: güvenli ek azaltım yok (Faz 3-A zaten yaptı); yeni hot-path kodu yok → no-loss/order/counter invariant'ları korundu (110/0 regresyon).
- [ ] (Deploy sonrası) `78/84 abandon + URC dolu` oranı düşüşü ve worker lag üretimde izlenir — geliştiricide.
- [x] Planlanan testler çalıştırıldı ve başarılı oldu (110/0).
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Uygulandı (2026-06-18), branch `fix/ami-event-worker-lag` (develop'tan), **push edilmedi**. superpowers TDD (her değişiklik RED→GREEN) + code-review + verification-before-completion ile yürütüldü.

**Üretim kodu:**
- `DialWithAbandonTimeoutScript.java` (#4): `isAgentAnswered` (senkron `${DIALSTATUS}=ANSWER`) + dial döngüsü `outcome==ANSWERED` çıkışı + `DIAL_STATUS_VARIABLE`/`DIAL_STATUS_ANSWER` sabitleri (`isCallerGone` ile paylaşımlı). Lag'de false-NOT_ANSWERED → agent churn + abandon önlendi; FIX-320176 korundu.
- `WebphoneSubscriber.java` (B): promote candidate'ten `callAnswerTime` ön filtresi kaldırıldı (authoritative registrar + webphone gate'leri korunur); `buildSyntheticSuccessCall` callAnswerTime null iken registrar agent-answer zamanını answer instant olarak kullanır → URC korunur. Idempotent.
- (A): yeni kod yok (tek güvenli azaltım Faz 3-A'da).

**Testler:** `DialWithAbandonTimeoutScriptTest` +1, `WebphoneSubscriberTest` +1 ve 1 karakterizasyon güncellemesi (authoritative gate = registrar).

**Doğrulama:** Offline Maven (JDK 1.8.0_202, `properties-tr-dev`) — 9 sınıf, **110 test, 0 hata** (BUILD SUCCESS).

**Code review (superpowers):** Critical/Important kod bloklayıcı yok. Doc must-fix (perf notu) düzeltildi. Bilinen residual: `hasSuccessfulInteractionStatus` read-after-write (retry yok) → batch-async follow-up'a; üretim sıralaması (interaction-status, disposition'dan önce yazılır) nedeniyle düşük olasılık, bugünkü 0/66'ya göre kesin iyileşme.

**Bekleyen:** Geliştirici tarafında commit/PR + pre-prod doğrulama + prod izleme (78/84+URC oranı, worker lag). Ağır batch-async worker rewrite = ayrı follow-up plan.
