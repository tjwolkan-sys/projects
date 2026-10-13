# Change/Fix Plan Standardı

Bu klasör, yeni feature olmayan kod değişikliği istekleri için tek dosyada analiz detayını ve execution planı tutar. Amaç, fix ve change request işlerinde iki ayrı doküman açmadan root cause, risk, TDD stratejisi, uygulama adımları ve doğrulamayı aynı plan içinde görünür tutmaktır.

## Kapsam

Bu akış şu işler için kullanılır:

- `fix`: Hata düzeltmesi, regression giderme veya hotfix.
- `refactor`: Davranış değiştirmeyen güvenli kod düzenleme.
- `perf`: Performans iyileştirmesi.
- `test`: Test veya test altyapısı değişikliği.
- `build`, `ci`, `chore`, `docs`, `revert`: Kod davranışı veya çalışma standardı etkileyen feature dışı işler.
- Change request işleri; `CR` ayrı etiket değildir, isteğin gerçek niteliğine göre yukarıdaki etiketlerden biri seçilir.

Yeni feature işleri bu klasöre yazılmaz. Yeni feature için iki aşamalı `docs/analyse` ve `docs/execution-plan` akışı kullanılır.

## Klasör Düzeni

```text
docs/change-fix-plan/GG.AA.YYYY/ETIKET-KIMLIK-KISA-AD.md
```

Örnek:

```text
docs/change-fix-plan/30.04.2026/FIX-BS-1423-AKTIF-CAGRI-SAYACI-DRIFT.md
```

## Zorunlu Akış

1. Kullanıcı derdini anlattıktan sonra kısa tespitlerini, risklerini ve çözüm önerini sun.
2. Planlama dokümanı açmadan önce geliştirici onayı al.
3. Mümkünse task id iste; yoksa dosya kimliği olarak developer username'ini kullan.
4. İlgili kodu, testleri ve contract boundary'lerini oku.
5. Tek plan dosyasına analiz detaylarını ve execution planı birlikte yaz.
6. `Açık Sorular` bölümünü her zaman ekle.
7. Açık soru yoksa `Yok` yaz.
8. Cevaplanmamış açık soru varsa planı yazdıktan hemen sonra kullanıcıdan yanıt iste.
9. Cevaplanmamış açık soru varken kod, test, config veya davranış değiştiren doküman değişikliğine başlama.
10. Kullanıcı yanıtlarını plan dosyasına işle, gerekiyorsa uygulama adımlarını güncelle ve sonra kod değişikliğine geç.
11. Tamamlanan adımları checkbox ile işaretle.
12. Planlanan testler başarılı olmadan planı tamamlandı sayma.

## Plan Şablonu

```markdown
# ETIKET Kısa Başlık Change/Fix Planı

## Bağlam

## İş Etiketi

fix | docs | test | refactor | chore | build | ci | perf | revert

## Öncelik

normal | hotfix

## Etkilenen Alanlar

## Mevcut Davranış

## Beklenen Davranış

## Kod Okuma Bulguları

## Root Cause / Değişiklik Gerekçesi

## Riskler

## TDD Stratejisi

## Açık Sorular

- [ ] <cevaplanması gereken soru>

## Execution Plan

- [ ] Onaylanan planın tüm adımları bu dosyaya işlendi.
- [ ] Mevcut davranışı kilitleyen karakterizasyon veya regression testi yaz.
- [ ] Beklenen davranışı kanıtlayan başarısız testi yaz.
- [ ] En küçük üretim kodu değişikliğini yap.
- [ ] Hedefli testi yeşile çevir.
- [ ] Gerekliyse küçük refactor yap.
- [ ] Refactor sonrası testi tekrar çalıştır.
- [ ] Doküman veya history güncellemesini tamamla.

## Performans Etkisi

- Hot path durumu:
- Call/event/tick başına ek maliyet:
- IO/scan/lock/serialization/log etkisi:
- Azaltım veya ölçüm notu:

## Doğrulama

## Kapanış Kriteri

- [ ] Açık sorular cevaplandı veya `Yok` olarak işaretlendi.
- [ ] Planlanan testler çalıştırıldı ve başarılı oldu.
- [ ] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu
```

## Plan Dosyaları

- `04.05.2026/README.md`: 04.05.2026 tarihli change/fix plan klasörü.
- `04.05.2026/FIX-GLB90048674-AMI-CONNECTION-LOSS-BRIDGED-CALL-RECOVERY.md`: AMI connection loss sonrası bridge kurulmuş çağrıların 75 ile erken kapanmasını engelleme planı.
- `05.05.2026/README.md`: 05.05.2026 tarihli change/fix plan klasörü.
- `05.05.2026/FIX-317301-TELEPHONY-SLOT-IDEMPOTENT-LIFECYCLE.md`: Hangup sonrası user result code bekleyen ve AMI connection loss çevresindeki çağrıların `activeCalls`/`callsInProgress` drift'ini engelleme planı.
- `07.05.2026/README.md`: 07.05.2026 tarihli change/fix plan klasörü.
- `07.05.2026/FIX-317516-AWAITING-USER-RESULT-RACE-OVERWRITE.md`: Hangup sonrası `awaitingUserResultCodeCalls` cache'ine geçiş yarış penceresi, awaiting-timeout `processCallResult` overwrite davranışı ve `shouldSetResultCode=false` durumunda durable fallback boşluğunun birlikte ele alınması planı.
- `15.05.2026/README.md`: 15.05.2026 tarihli change/fix plan klasörü.
- `15.05.2026/FIX-GLB90090645-GAMMA-PENDING-RINGING-CACHE.md`: Predictive Gamma ringingLoad sayımının AMI VarSetEvent gecikmesinden etkilenmesini önlemek için `CampaignRealTimeRingingManager`'a kısa ömürlü pending ringing buffer eklenmesi planı.
- `18.05.2026/README.md`: 18.05.2026 tarihli change/fix plan klasörü.
- `18.05.2026/FIX-GLB90090645-RINGING-LOAD-UCB.md`: Predictive Gamma `ringingLoad` hesabına Binomial UCB tamponu (`k=1.645`) ekleyerek düşük AR + büyük ringing havuzunda eş zamanlı cevap kümelerinden gelen rc78/abandon'u azaltma planı; telemetri için `ringingLoadMean`, `ringingLoadSafe`, `ringingLoadConfidenceK` alanları aynı PR'da eklendi.
- `21.05.2026/README.md`: 21.05.2026 tarihli change/fix plan klasörü.
- `21.05.2026/FIX-GLB90090645-GAMMA-CALLQUEUE-BACKPRESSURE.md`: AMI bağlantı kaybı sırasında Gamma `CallConsumer`'in `callQueue`'yu CPS limitinin üzerinde biriktirmesini engelleyen queue threshold guard. Restore anındaki originate burst'ünü kökten keser; rc78/rc84 abandon'un AMI loss kaynaklı kısmını sıfırlar. Yeni telemetri alanı eklenmedi (mevcut `drainedCallCount + queueSize` deseni yeterli).
- `04.06.2026/README.md`: 04.06.2026 tarihli change/fix plan klasörü.
- `04.06.2026/FIX-GLB90115753-UNIQUEIDENTIFIER-DB-FIELD-TYPE.md`: SQL Server `uniqueidentifier` tipini `Constants.DB_FIELD_TYPES` enum'una eklemek; `CampaignDataTypeChecker` ve `CampaignService.getTableColumnsWithTypes` üzerindeki `ParseException` gürültüsünü gidermek ve `FilterUtils` üzerinden dinamik SQL filter tırnaklamasını tamamlamak.
- `05.06.2026/README.md`: 05.06.2026 tarihli change/fix plan klasörü.
- `05.06.2026/FIX-320173-TELEPHONY-RC-NULL-FINALIZE-KAYBI.md`: `pd_cdr.telephony_rc` NULL kalan kayıtların iki kök nedeninin (agent logout-orphan yolunun pd_cdr persist etmemesi; `processCallResult`'ın yan etki exception'ıyla persist'e ulaşamadan kesilmesi) persist-first finalize sözleşmesi, hangup skip-guard'ı ve dl-id'li yan etki izolasyonuyla kalıcı çözümü planı.
- `05.06.2026/FIX-GLB90090645-STARTUP-AR-AND-ACD-LCD.md`: Gamma startup AR sabitlerini üretim AR'larına yaklaştırma (0.90 → 0.50/0.60) ve ACD agent dağıtımında grup-içi shuffle yerine LCD sıralı + aynı priority'li grupları tek havuza birleştirme planı.
- `05.06.2026/README.md`: 05.06.2026 tarihli change/fix plan klasörü.
- `05.06.2026/FIX-320176-AGI-AGENT-DIAL-SAME-USER-RETRY-STORM.md`: Müşteri cevapladıktan sonra agent'a bağlamayı deneyen `DialWithAbandonTimeoutScript` AGI döngüsündeki same-user retry storm ve "Caller hung up before dial" busy-spin'ini gideren AGI-only düzeltme planı (per-call denenen-user exclusion + dial sonucu okuma/caller-hangup erken çıkış + bounded loop/backoff). ACD distribution semantiği ve phantom-available quarantine bu planda değiştirilmez.
- `05.06.2026/FIX-GLB90090645-ACD-AVAIL-TIME-COMPARATOR.md`: Cross-group ACD sort'u ve `shouldSwitchUser` swap kararı `user.statusSetDate` (gerçek avail süresi) üzerinden çalışacak şekilde düzeltildi; `lastCallEndDate` wrapup/break sürelerini saymadığı için break'ten dönen agent'i yanlış öne çıkarıyordu.
- `16.06.2026/README.md`: 16.06.2026 tarihli change/fix plan klasörü.
- `16.06.2026/FIX-GLB90048674-AMI-CONNECTION-LOSS.md`: AMI connection loss kök-neden fix'i; event/callback işini reader thread'inden bağlantı başına tek-thread bounded worker'a alma (Faz 1), originate/hangup'ı event'siz command bağlantısına ayırma (Faz 2) ve answer/hangup CDR round-trip azaltma (Faz 3-A); Faz 3-B agresif batch/async DB bilinçle ertelendi.
- `18.06.2026/README.md`: 18.06.2026 tarihli change/fix plan klasörü.
- `18.06.2026/FIX-GLB90048674-AMI-EVENT-WORKER-LAG-URC-ABANDON-KAYBI.md`: 16.06 fix deploy edilip AMI loss sıfırlandıktan sonra da süren TRC 78/84 abandon + user result code kaybı; kök neden Faz 1 tek-thread event-worker'ının yoğun predictive bağlantıda 13–17 sn geride kalması (per-event senkron DB I/O + ertelenen Faz 3) ve AGI/event yarışı; result-code/URC recovery'nin volatile `call.user` + lag'e duyarlı promote ön koşullarına bağlı olması. İki eksenli çözüm önerisi (worker throughput + authoritative-kanıt tabanlı idempotent recovery). Açık soru bekliyor.
- `23.06.2026/README.md`: 23.06.2026 tarihli change/fix plan klasörü.
- `23.06.2026/FIX-321876-SCHEDULER-MANUEL-PAUSE-RESTART.md`: Schedule penceresi içinde manuel pause yapılan kampanyanın `ScheduledCampaignRunner` tarafından dakikada bir tekrar START edilmesini engelleme planı. Kök neden: yeni chain scheduler `shouldStartCampaign`'a PAUSED'ı startable olarak ekledi (eski dormant `AutomaticCampaignExecutor` dışlıyordu) ve `CampaignService.action` PAUSE case'i UI'dan gelen `MANUAL` sinyalini düşürüyor. Çözüm (Yaklaşım A): type-safe `manuallyPaused` işareti; scheduler PAUSED'ı yalnız otomasyon pause'da başlatır; işaret unpause/start/stop'ta temizlenir. Otomasyon-pause auto-resume korunur. Açık soru bekliyor.
- `10.07.2026/README.md`: 10.07.2026 tarihli change/fix plan klasörü.
- `06.08.2026/README.md`: 06.08.2026 tarihli change/fix plan klasörü.
- `06.08.2026/FIX-GLB90060295-AWAITING-URC-AUTHORITATIVE-RECOVERY.md`: `telephony_rc = 10` olup `user_result_code_id` NULL kalan kayıtların iki kök nedeninin kalıcı çözümü planı. Kök nedenler: (1) agent result code'unun Redis mesajı dialer'a hiç ulaşmıyor ve dialer webphone result code tablosunu authoritative kaynak olarak hiç okumuyor; (2) durable fallback ile awaiting-timeout finalize'ı aynı `pd_cdr` satırını senkronizasyonsuz güncelliyor, finalize URC'yi in-memory'den türettiği için fallback'in yazdığı değeri NULL ile eziyor. Çözüm: awaiting-timeout finalize'ında `ast_registrar_cdr` + `wbp_call_result_code` üzerinden authoritative lookup (evaluator gate + user-match guard'lı, hot path dışı) ve `persistFinalCdr` null-ezme koruması. FIX-317516 (07.05.2026) B2 kararının kapsamadığı iki boşluğu kapatır. Açık soru bekliyor.
- `10.07.2026/REFACTOR-318332-DNC-MUSTERI-ILISKI-TABLOSU.md`: FEAT-318332 customer-scope DNC depolamasını yeniden yapılandırma; customer_no↔phone ilişkisini yeni `pd_dnc_customer` tablosuna taşıma, `pd_dnc_pn`'i saf telefon block-list'e indirme (CUSTOMER marker + atıl `dnc_type`/`customer_no` kolonları kaldırılır), delta-sync'i yeni tablodan besleme. Dial-time engelleme davranışı korunur. Açık sorular (FK/tip, mevcut test verisi migration vs reset, phone_number NOT NULL, DNC_TYPE enum) bekliyor.
- `13.08.2026/README.md`: 13.08.2026 tarihli change/fix plan klasörü.
- `13.08.2026/FIX-GLB90060295-ORIGINATE-CALL-HEADER-LINE-TOO-LONG.md`: Turkcell camp 85/74'te `telephony_rc = 79` (`code 907` Originate action error) kayıtlarının 04.08.2026 sonrası ~170 kat artmasının ve bu kayıtlarda çağrının hiç kurulmamasının kalıcı çözümü planı. Kök neden: `AbstractOriginateAction.getCall()` public getter olduğu için asterisk-java `ReflectionUtil.getGetters` + `ActionBuilderImpl.appendGetters` zinciri tüm `Call` nesnesini `call:` AMI başlığına yazıyor; `retryHistory` her retry'de büyüdüğü ve result tablosunda kalıcı olduğu için satır Asterisk'in 1024 baytlık AMI satır limitini aşıyor (canlı log'dan ölçülen 1264 bayt, eşik ~19 retry kaydı) ve Asterisk originate'i `'Failed to parse message: line too long'` ile reddediyor; TRC 907 attempt'ten muaf olduğu için kontak sonsuza dek boşa retry'ye giriyor. Çözüm: getter'ı refleksiyonun görüş alanından çıkarmak (`public` → `protected`, tek satır) + hiçbir AMI satırının 1023 baytı aşmadığını kilitleyen regression testi. Çift `pd_cdr` satırı yarışı (F2) ve retryHistory/attempt muafiyeti (F3) kapsam dışı. Açık soru bekliyor.
- `11.08.2026/FIX-324856-CAMPAIGN-FORM-GRID-SAYISAL-SIRALAMA.md`: Campaign Form → List Source → Phone Numbers grid'inde `Sort` kolonunun alfabetik sıralanması (asc `0,1,10,11,12`; desc `9,8,7,6,5`) ve aynı defekt sınıfını taşıyan 6 kardeş grid. Kök neden: columnDef'ler `type` bildirmediği için ui-grid 3.2.6 `assignTypes()`, `GridOptions.initialize` kaynaklı ilk boş veri turunda tipi `string` damgalar ve `if (!colDef.type)` guard'ı yüzünden gerçek veri geldiğinde yeniden hesaplamaz; `rowSorter` bu yüzden `sortAlpha` seçer. Çözüm: 7 controller / 15 columnDef'te `type : 'number'` bildirimi (`sortFieldCtrl.js` referans deseni). Açık sorular cevaplandı; plan onayı bekliyor.
- `03.09.2026/README.md`: 03.09.2026 tarihli change/fix plan klasörü.
- `03.09.2026/FIX-ORIGINATE-RESPONSE-REASON0-CODE-REVIEW-DUZELTMELERI.md`: `fix/originate-response-reason0-overwrites-real-result` branch'inin paralel code-review'unda çıkan dört bulgunun giderilmesi (2 CONFIRMED-HIGH veri kaybı + 2 davranışsal). (1) Fallback'in TRC yazımı finalize kontrolüyle atomik değildi; hangup `processCallResult` içinde latch'i aldıktan sonra, `persistFinalCdr` TRC'yi okumadan önceki Redis round-trip penceresinde fallback araya girip RC 907 yazabiliyordu — yani branch'in düzeltmeye çalıştığı hata daha dar bir pencerede geri geliyordu. Çözüm: `Call.setTelephonyResultCodeIfNotFinalized` CAS'i (kontrol + yazım aynı monitörde, one-shot latch tüketilmeden) ve `telephonyResultCode` alanının `volatile` yapılması. (2) 907'nin `hasErrorCode`'a eklenmesi, diff'te tartışılmayan ikinci çağıran `shouldProcessCallResultOnHangup`'ı da değiştirip preview olmayan + cevaplanmış çağrıyı awaiting-user-result kolundan çıkarıyor ve agent disposition'ını sessizce kaybettiriyordu; 907 kontrolü yalnız `shouldFinalizeOnHangup`'a taşındı. (3) FCT retry `channel`'ı sıfırlamadığı için 2. denemenin gerçek originate reddi bayat channel yüzünden gereksiz 10sn grace period'a giriyordu; `setChannel(null)` eklendi. (4) Inline kaçış yolunun `isShutdown()` ön kontrolü TOCTOU, `RejectedExecutionException` catch'i ölü kod ve kuyruk doluyken tek thread'li `scheduleService`'i bloklar durumdaydı; one-shot guard + kapasite ön-kontrollü `execute` ile değiştirildi (ilk denenen `getQueue().offer` yaklaşımı `ThreadPoolExecutor`'ın lazy core-thread davranışı nedeniyle soğuk havuzda işi hiç çalıştırmıyordu; probe ile doğrulanıp düzeltildi). Tamamlandı (03.09.2026): RED/GREEN doğrulandı, 24/24 GREEN, geniş koşu 219 testte yalnız 1 pre-existing baseline hatası. İkinci review turu (03.09.2026): grace period'in kendi getirdigi uc kenar durum daha bulunup duzeltildi — (5) ayni callId icin ikinci origination-fail birinci timer'i izlenemez birakiyordu, (6) forceful campaign stop bes kapinin hicbirini kapatmadigi icin preview kampanyada TRC 902 uzerine 907 yaziliyordu, (7) kendi cozumumuzdeki TOCTOU/lazy-core-thread kusuru `prestartCoreThread` + bloklamayan `offer` ile giderildi. 28/28 GREEN, genis kosu 223 testte yalniz 1 pre-existing baseline hatasi.
- `08.09.2026/README.md`: 08.09.2026 tarihli change/fix plan klasörü.
- `08.09.2026/FIX-GLB90114534-FCT-CALLERID-CONTEXT-VE-LIST-RETRY.md`: FCT routing akışında devralınan üç sorun. (1) `obtainRouteDetailsCursor` QSS'e `retryCallerIdBehavior`/attempt/callType/utilizer göndermediği için caller ID seçim iş kuralı sessizce devre dışı; (2) `getRouteDetails` gRPC hatasında da boş liste döndüğü için geçici QSS kesintisinde çağrı retry'sız TRC 910 ile yakılıyor; (3) cause 34 in-call list-retry'ı PD tarafında sınırsız ve `originateCall=false` kolu testsiz. PR 23029 revert'i bağımlılık analiziyle reddedildi (`cb60d1e7` mevcut branch'in atası, 26 dosya sembolünü kullanıyor, sonrasında develop'a 155 commit girdi). Açık soru bekliyor.

## Plan Açma Onayı Notu

Sorun kullanıcıyla tartışılıp tespit edildiğinde ve çözüm yönü belli olduğunda ajan plan dosyası açmadan önce kullanıcıya `Plan aşamasına geçeyim mi?` diye sorar. Mesajda bunun `change-fix planı` olduğunu belirtir, plansız doğrudan çözüm uygulama seçeneğinin de bulunduğunu söyler ve kendi önerisini kısa gerekçeyle ekler. Kullanıcı plansız uygulamayı seçerse bu klasörde dosya açılmaz.

## Plan İndeksi

- `30.04.2026/README.md`: 30.04.2026 tarihinde açılan change/fix planları.
- `30.04.2026/FIX-GLB90048674-DATA-MANIPULATION-NULL-KOLON-FILTRESI.md`: PowerDialer Data Manipulation ekranında null kolon koşulu ile kayıt getirme düzeltmesi.
- `22.09.2026/FIX-GLB90060295-PASIF-GRUP-BOOST-BUTCESI.md`: Pasif (enabled=0) kampanya grubu agent'larının dialing boost originate bütçesine sayılması (Guvencell camp 164 tek agent'a çoklu ringing/abandon) düzeltmesi.
- `01.10.2026/FIX-333467-EXPIRE-JOB-SCHEDULER-GECIKMESI.md`: Turkcell dialer'ında expire ve mirror cron'larının ortak scheduler thread'indeki `UserSynchronizer` fixed-rate birikimi yüzünden günlerce kaymasının düzeltilmesi; `RedisUtil.keys()` SCAN COUNT 10 → 1000 ve `UserSynchronizer` `fixedRate` → `fixedDelay`.
- `02.10.2026/FIX-333607-KAMPANYA-AKSIYON-PARALELLIK-GAMMA-STARTUP.md`: Global campaign aksiyon kilidi ve GAMMA startup sorguları nedeniyle start/stop/pause aksiyonlarının tüm campaign'lerde kilitlenmesini gideren plan (campaign bazlı kilit, asenkron dağıtım, sorgu timeout/önbellek/index uyumu, NPE düzeltmeleri). Uygulandı (05.10.2026).
- `02.10.2026/README.md`: 02.10.2026 tarihinde açılan change/fix planları.
- `02.10.2026/FIX-333599-PAYLASILAN-TABLO-MUKERRER-ARAMA.md`: Paylaşılan kampanya tablosunda startup cleanup kaynaklı mükerrer arama ve callback silinmesi düzeltme planı (hotfix, release/6.7.9 tabanlı).
- `05.10.2026/README.md`: 05.10.2026 tarihinde açılan change/fix planları.
- `05.10.2026/FIX-333599-PARALEL-START-CLEANUP-YARISI.md`: Paralel campaign start (FIX-333607) altında paylaşılan tablo startup cleanup kesmesinin yarışı düzeltme planı (hotfix, release/6.7.9 tabanlı dal).
