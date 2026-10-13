# FIX Paralel Kampanya Start'ında Paylaşılan Tablo Cleanup Yarışı Change/Fix Planı

## Bağlam

- Task: 333599 (devam işi). İlk plan: `docs/change-fix-plan/02.10.2026/FIX-333599-PAYLASILAN-TABLO-MUKERRER-ARAMA.md`.
- Branch: `fix/333599-paylasilan-tablo-mukerrer-arama` (`origin/release/6.7.9` tabanlı, `f627b133`).
- Tetik: 05.10.2026'da develop'a PR 25890 (FIX-333607, `1967def4`) girdi.
  - `CampaignService.action` üzerindeki global `synchronized` kaldırıldı; yerine `CampaignActionCoordinator` ile campaign bazlı kilit geldi.
  - Scheduler (`ScheduledCampaignRunner`, `AutomaticCampaignExecutor`) ve stop condition artık `actionAsync` ile 8 thread'lik havuza iş bırakıyor.
  - Sonuç: farklı campaign'lerin START'ları artık **paralel** çalışıyor.
- FIX-333599 (A) şu varsayımla yazıldı: "Scheduler start'ları tek thread'te sırayla çalışıyor" (ilk plan, Kod Okuma Bulguları). Paralel start'ta (A)'nın garantisi zayıflıyor. FIX-333607 planı da global kilidin kalkmasıyla ortak yapılarda yarış çıkabileceğini not etmiş. Ancak 333599 o sırada develop'ta olmadığı için paylaşılan tablo cleanup'ı orada ele alınmadı.
- Paket durumu: 333607 bu haftaki pakete giriyor. Paket ister develop'un tamamı, ister `release/6.7.9` tabanlı olsun, 333599 ile 333607 aynı pakette olacak.
- Canlıdaki kapsam (05.10.2026, salt okuma `pd_campaign` sorgusu): aynı result tablosunu paylaşıp `startup_db_check` açık olan gruplar:
  - NonTurkcell: campaign 63/64 (ikisi de açık), campaign 123/124 (ikisi de açık), campaign 51/54/55/56 grubu (biri açık), `KFK_TAHSILAT_TEST` grubu (1/14/19/20; biri açık).
  - Turkcell: `TEST`.
  - `KFK_TAHSILAT` (6, 12, 17) için `startup_db_check` şu an kapalı (02.10 geçici önlemi).

## İş Etiketi

fix

## Öncelik

hotfix. 333607 ile aynı pakette canlıya çıkacağı için paket kesilmeden önce kapanmalı.

## Etkilenen Alanlar

- `engine/dialer/state/StopState.java`: `checkForUnfinishedCalls` (startup DB check).
- `engine/dialer/SharedResultTableSiblings.java`: değişmesi beklenmiyor; kardeş anı hesabı aynı kalır.
- `engine/db/CampaignDbOperations.java`: değişmesi beklenmiyor; `fetchedBefore` parametresi zaten var.
- Testler: `tests/dialer/state/StartupCleanupSiblingCutoffRegressionTest`, `tests/campaign/db/StartupCleanupSharedTableScopeRegressionTest`.
- `docs/architecture/DIALER-RUNTIME.md`: "Mükerrer Fetch Koruması" bölümü.

## Mevcut Davranış (333599 ve 333607 birlikte)

1. `StopState.start()` sırası:
   1. kontroller
   2. `restoreCampaignFromMemento`
   3. `checkStopCondition`
   4. **`checkForUnfinishedCalls`**
   5. `calculateOnlineAgentsSum`
   6. `addCallQueueToAsteriskCallQueues`
   7. `checkIfExpireJobShouldContinue`
   8. **`registerAsLive`** (`liveSince` + `campaignContexts.put`)
   9. `super.start()` (producer başlar, ilk rezervasyon yapılır)
2. `checkForUnfinishedCalls` kesmeyi yalnız `campaignContexts` içinde **görünen** kardeşlerden hesaplıyor. Kardeş görünmüyorsa kesme `null` olur ve tablo geneline filtresiz UPDATE çalışır.
3. 333607 öncesinde start'lar global kilitle sıralıydı. Kardeş ya tamamen canlıya geçmişti (görünür, kesme uygulanır) ya da henüz start'a girmemişti (rezervasyonu yok). Arada bir durum yoktu.
4. 333607 sonrasında, aynı saniyedeki scheduled start'ta şu yarış oluşabiliyor (örnek: camp 6 ve camp 17):
   - t0: camp 17'nin `checkForUnfinishedCalls` adımı kardeşleri tarıyor. camp 6 henüz `registerAsLive` yapmamış, bu yüzden kesme yok.
   - t1: camp 6 `registerAsLive` ve `super.start()` adımlarını geçiyor; producer ilk batch'i rezerve ediyor. 02.10 vakasında start ile ilk rezervasyon arası ~300 ms.
   - t2: camp 17'nin filtresiz UPDATE'i (`complete_date IS NULL AND is_fetched IS NOT NULL ...`) camp 6'nın t1 rezervasyonlarını henüz taramadıysa siliyor.
   - Pencere, camp 17'nin tarama anından UPDATE'in bitişine kadar sürüyor. Büyük tabloda UPDATE süresi ve paralel start'ların DB yükü pencereyi büyütüyor.
5. Etki:
   - (B) ve FIX-330538 guard'ı aynı campaign içinde ikinci `Call` üretimini engellemeye devam ediyor. Bu yüzden 02.10'daki mükerrer arama zinciri büyük olasılıkla oluşmaz.
   - Kalan etkiler:
     - `[DUP-FETCH-GUARD]` gürültüsü ve `TOP (n)` slot kaybı.
     - Filtreleri örtüşen kardeşlerde, rezervasyonu silinen satırı kardeş de çekebilir. Guard başka campaign'in çağrısını bilinçli olarak görmüyor.
     - (A)'nın dokümante ettiği garanti geçerliliğini yitiriyor.

## Beklenen Davranış

1. Startup cleanup, **kendi başladığı andan sonra** yapılmış hiçbir rezervasyona dokunmaz. Bu, kardeşin o anda görünür olup olmamasından bağımsızdır.
2. Görünür kardeş varsa mevcut kural korunur: kardeşin canlıya geçişinden sonraki rezervasyonlara dokunulmaz.
3. Kesme = `min(cleanup başlangıç anı, en erken kardeş liveSince)`. Cleanup başlangıç anı kardeş taramasından **önce** alınır.
4. Kampanyanın önceki koşudan kalan kendi bayat rezervasyonları her durumda temizlenir. Bu koşuda kampanya henüz rezervasyon yapmamıştır, çünkü producer `super.start()` sonrasında başlar.

## Kod Okuma Bulguları

- Kardeş, ancak `registerAsLive` sonrasında rezervasyon yapabilir; producer `super.start()` içinde başlar.
- `campaignContexts` bir `ConcurrentHashMap` (`DialerFacade:67`). Bu yüzden başka thread'deki `put`, sonraki `values()` taramasında görünür.
- Neden yeterli olduğu: kardeşin bir rezervasyonu r anında yapılmış olsun.
  - r ≥ cleanupStart ise: kesme ≤ cleanupStart ≤ r, yani rezervasyon korunur.
  - r < cleanupStart ise: kardeş r'den önce kaydolmuştur (liveSince ≤ r < cleanupStart). Kayıt taramadan önce olduğu için kardeş görünürdür; kesme ≤ liveSince ≤ r, yani rezervasyon korunur.
  - Rezervasyon literal'i (`Utils.convertDateToDouble(now)`) ile kesme aynı JVM saatini kullanır. Karşılaştırma `<` olduğu için aynı andaki rezervasyon da korunur.
- Ek kazanım: farklı dialer node'unda çalışan kardeşin, cleanup başladıktan sonraki rezervasyonları da korunur. Bu, ilk planda kapsam dışı bırakılan bir boşluktu. Node saatleri farklıysa koruma kısmi kalır.
- 333607'nin `ManagerConnectionDecorator` değişikliği (context kaldırıldıktan sonra gelen answer'da sayaçları atlamak) ve `CampaignStateTemplate`'teki RUNNING sırası değişikliği, 333599'un (A) ve (B) ayaklarıyla etkileşmiyor. (A) state'e değil, harita üyeliğine bakıyor; (B) MCD cache'lerini tarıyor.

## Root Cause / Değişiklik Gerekçesi

- Kök neden: (A)'nın kesmesi "görünür kardeş" bilgisine dayanıyor. Bu görünürlük ancak start'lar sıralıyken rezervasyon zamanını kapsıyordu.
- Owner boundary cleanup'ın kendisi. Cleanup, kendi başlangıcından sonraki rezervasyonların bayat olamayacağını kardeş bilgisine ihtiyaç duymadan bilir.

## Değerlendirilen Alternatifler

- **Kesmeyi yalnız aynı tabloyu kullanan başka kampanya tanımlıysa uygulamak** (`pd_campaign` sorgusu). Kardeşsiz durumda SQL birebir aynı kalır. Ancak start'a DB sorgusu ve bağlantı dizesi eşleştirmesi ekler. Bkz. S1.
- **Aynı result tablosunun start'larını kilitlemek** (connection + table anahtarlı kilit). 333607'nin coordinator'ına dokunur, start'ları yeniden sıraya sokar (333607'nin amacına ters) ve cross-node durumu kapsamaz. Önerilmez.
- **Riski (B) guard'ına bırakmak.** (A)'nın garantisi kalkar ve filtreleri örtüşen kardeşlerde çapraz alım riski sürer. Önerilmez.

## Riskler

- Kardeşsiz durumda da SQL değişir: `AND <fetched flag kolonu> < <cleanup anı literal'i>` her zaman eklenir. Önceki koşudan kalan bayat rezervasyonların hepsi geçmiş tarihli olduğu için temizlenmeye devam eder.
- Fetched flag kolonunda tarih-double dışında bir değer varsa (elle yazılmış legacy flag, farklı format) karşılaştırma sonucu değişebilir. Uygulamadan önce canlı camp DB'lerinde salt okuma örneklem kontrolü yapılır.
- Saat geri giderse (NTP düzeltmesi), cleanup anından "ileri" tarihli bir bayat rezervasyon temizlenmez ve satır expire/reset'e kadar takılı kalır. Olasılığı düşük.
- Mevcut `startupCleanupWithoutSiblingHasNoCutoff` testi bilinçli olarak değişir; kardeş yokken kesme artık `null` değildir.

## TDD Stratejisi

1. Kırmızı (yarış): Kardeş tarama anında haritada yok. Kardeş, `removeFetchedDataFlagFromUnfinishedCalls` çağrısı sırasında haritaya ekleniyor ("taramadan sonra kayıt" senaryosu). Beklenen: kesme `null` değil ve `start` öncesi/sonrası alınan zaman damgalarının arasında.
2. Kırmızı (min kuralı):
   - Kardeşin liveSince'i cleanup anından önceyse kesme = kardeşin liveSince'i.
   - Kardeş yoksa kesme = cleanup anı.
3. Kırmızı (sıra): Cleanup anı, kardeş taramasından (`values()`) önce alınmalı. Sahte zaman kaynağı ile `values()` çağrısında saat ilerletilir ve kesmenin ilerletilmiş değer olmadığı doğrulanır.
4. Güncellenecek: `startupCleanupWithoutSiblingHasNoCutoff` → `startupCleanupWithoutSiblingCutsAtCleanupStart`.
5. Değişmeden yeşil kalması gerekenler: `StartupCleanupSharedTableScopeRegressionTest` (SQL literal üretimi) ve "startup DB check kapalıyken statement yok" testi.
6. Sleep'e dayalı test yazılmaz. Saat için küçük ve paket içi bir zaman kaynağı seam'i kullanılır; repoda uygun bir clock deseni varsa o tercih edilir.
7. 333607 ile entegrasyon: develop ile sync sonrasında hedefli setin yanında `CampaignActionCoordinatorRegressionTest` ve `CampaignServiceActionLockRegressionTest` de koşulur.

## Uygulama Kontrol Listesi

- [x] Açık sorular cevaplandı ve plana işlendi.
- [x] Canlı camp DB'lerinde fetched flag değer formatı için salt okuma örneklem kontrolü yapıldı (bkz. Ön Kontrol Sonucu).
- [x] Kırmızı testler yazıldı ve beklenen nedenle kırmızı görüldü.
- [x] `StopState.checkForUnfinishedCalls` güncellendi: cleanup anı → kardeş taraması → min kesme. Log satırı kesmenin kaynağını (cleanup anı / kardeş) yazar.
- [x] Hedefli testler yeşil.
- [x] `docs/architecture/DIALER-RUNTIME.md` "Mükerrer Fetch Koruması" bölümü güncellendi (paralel start ve cross-node notu).
- [x] develop ile sync yapıldı (onaylı, `gbpd-sync-develop-branch`); merge sonrası hedefli testler ve 333607 testleri koşuldu.
- [x] History kaydı eklendi.

## Performans Etkisi

- Hot path durumu: hot path dışında. Yalnız STOP→START anında ve startup DB check açıksa bir kez çalışır.
- Call/event/tick başına ek maliyet: yok.
- IO/scan/lock/serialization/log etkisi:
  - Yeni sorgu yok; mevcut UPDATE'e her zaman tek bir `AND ... < literal` koşulu eklenir. Seçiciliği artırır; kolon indeksli değilse tarama planı aynı kalır.
  - Ek kilit yok. Ek allocation olarak yalnız bir `Date` nesnesi.

## Doğrulama

`.codex/TOOL-PATHS.local.properties` ile offline Maven:

```text
mvn -o -Dtest=StartupCleanupSiblingCutoffRegressionTest,StartupCleanupSharedTableScopeRegressionTest,StopStateTest,CallProducerDuplicateFetchGuardRegressionTest,InFlightCallUniqueKeysRegressionTest test
```

develop ile sync sonrasında ek olarak:

```text
mvn -o -Dtest=CampaignActionCoordinatorRegressionTest,CampaignServiceActionLockRegressionTest,StartupCleanupSiblingCutoffRegressionTest,StopStateTest test
```

Not: `StopStateTest` içindeki 3 `NotAMockException` hatası pre-existing; sonuç buna göre değerlendirilir.

Canlı doğrulama (deploy sonrası): paylaşılan tabloda startup DB check açık grupların (63/64, 123/124) aynı dakikadaki start'ında `[DUP-FETCH-GUARD]` görülmemeli ve cleanup log satırında kesme görünmeli.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı veya `Yok` olarak işaretlendi.
- [x] Planlanan testler çalıştırıldı ve başarılı oldu. (Bu dalda ve develop sync sonrasında; bkz. Sync Notu.)
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

05.10.2026 — Bu daldaki uygulama tamamlandı (commit atılmadı). develop sync ve sync sonrası testler onay bekliyor.

- Üretim kodu (`StopState`):
  - Zaman kaynağı constructor üzerinden alınıyor: `StopState(CampaignContext)` sistem saatini (`Date::new`) verir, `StopState(CampaignContext, Supplier<Date>)` testler içindir. Yeni static state yok.
  - `checkForUnfinishedCalls`: önce cleanup anı alınıyor, sonra kardeşler taranıyor; kesme = min(cleanup anı, kardeş anı) ve artık her zaman geçiliyor. Log satırı `Only reservations made before <kesme> are released` biçiminde, kesme kardeşten geldiyse `(live campaign(s) share the result table)` ekiyle.
  - `registerAsLive` aynı zaman kaynağını kullanıyor (davranış aynı).
  - `CampaignDbOperations` ve `SharedResultTableSiblings` değişmedi.
- Testler:
  - `StartupCleanupSiblingCutoffRegressionTest`: `startupCleanupWithoutSiblingHasNoCutoff` kaldırıldı; yerine 7 test eklendi (`startupCleanupWithoutVisibleSiblingCutsAtCleanupStart`, `productionConstructorCutsAtCurrentTime`, `siblingGoingLiveWhileCleanupRunsKeepsItsReservations`, `siblingLiveAfterCleanupStartDoesNotPushCutoffForward`, `siblingLiveBeforeCleanupStartGivesItsLiveSince`, `siblingWithUnknownLiveSinceStillReleasesNothing`, `cleanupStartIsTakenBeforeScanningSiblings`). Toplam 15 test.
  - `StopStateTest.givenStartupDbCheckEnabledWhenStartThenCleanFetchedFlagBeforeStateChange`: kesme beklentisi `isNull()` → `notNull()`.
  - Kırmızı aşama (iskelet constructor ile): 6 beklenen hata (5 yeni test + `StopStateTest` matcher'ı); iki karakterizasyon testi baştan yeşil.
- Doğrulama:
  - Hedefli set (`StartupCleanupSiblingCutoffRegressionTest`, `StartupCleanupSharedTableScopeRegressionTest`, `StopStateTest`, `CallProducerDuplicateFetchGuardRegressionTest`, `InFlightCallUniqueKeysRegressionTest`): 46 test, 0 failure, 3 error.
  - Geniş set (`tests/dialer/state/**`, `com/powerdialer/engine/dialer/state/**`, `tests/campaign/db/**`, `tests/dialer/CallProducer*`): 102 test, 0 failure, 3 error.
  - Kalan 3 error, `StopStateTest`'teki pre-existing `NotAMockException` hatalarıdır (`shouldStartWithSchedule`, `shouldNotStartIfScheduleIsNotForToday`, `shouldNotStartWhenScheduleFromIsAfterTill`; `LocalDateTime` static mock'u). 02.10 kaydındaki baseline ile aynı.
- Doküman: `DIALER-RUNTIME.md` "Mükerrer Fetch Koruması" kesme kuralı güncellendi; aynı bölümde 02.10'dan kalan yarım ve tekrar eden paragraf ("FIX-333599 ile iki katman genişletildi: ... cachelerinden") kaldırıldı.
- Kalan: develop sync (onaylı) + sync sonrası testler, commit/push, PR (hedef develop).

## Sync Notu

05.10.2026 — `origin/develop` (`b1ddd555`, FIX-333607 dahil) dala merge edildi.

- Commit'ler: fix `412816f8`, merge `b6ea3298` (`--no-ff`). Dal develop'un 0 commit gerisinde.
- Kod çakışması yok: `StopState`, `CampaignContext`, `ManagerConnectionDecorator`, `CallProducer` ve `CampaignDbOperations` otomatik birleşti. FastAGI değişikliği iki tarafta birebir aynı olduğu için sorunsuz geçti.
- 5 docs conflict'i append-only çözüldü; 3-way kontrolle satır kaybı olmadığı doğrulandı:
  - `docs/history/02.10.2026.md` ve `05.10.2026.md` (add/add): develop içeriği aynen kaldı, 333599 kayıtları sona eklendi.
  - `docs/README.md`, `docs/change-fix-plan/README.md`, `docs/history/README.md`: iki tarafın eklediği satırların birleşimi alındı. `history/README.md`'deki eski conflict marker satırlarını develop zaten silmişti; 3-way merge bu silmeyi uyguladı.
- Merge sonrası `pom.xml` sürümü 6.7.9 (dal `release/6.7.9` sürüm artışını taşıyor).
- Doğrulama (offline Maven, JDK 8):
  - Hedefli set (+ `CampaignActionCoordinatorRegressionTest`, `CampaignServiceActionLockRegressionTest`, `CustomAgiServerThreadPoolSizeTest`): 70 test, 0 failure; yalnız 3 pre-existing `StopStateTest` error.
  - Geniş set (`tests/**`, `com/powerdialer/engine/dialer/**`, `com/powerdialer/engine/asterisk/**`, `DNCEngineTest`), temiz build: 1251 test, 1 failure + 4 error. Saf `origin/develop` baseline'ı (temiz worktree): 1228 test, aynı 5 hata. Merge kaynaklı yeni hata yok.
  - İlk geniş koşuda 8 FCT/route-details testi `NoSuchMethodError` (`RouteDetailEntry$Builder`) ile düştü. Neden: çalışma dizinindeki eski `target/` protobuf build çıktısı. `mvn -o clean` sonrası 4 sınıf (39 test) geçti ve temiz geniş koşuda hata tekrarlanmadı.
- Kalan: push ve PR (hedef develop), geliştirici onayıyla.

## Açık Sorular

- [x] S1: Kesme her zaman mı uygulansın, yoksa yalnız aynı tabloyu kullanan başka bir kampanya tanımlıysa mı? Öneri: **her zaman**. Daha basit, start'a sorgu eklemiyor ve cross-node kardeşi de kapsıyor. Alternatifin tek getirisi, kardeşsiz SQL'in birebir aynı kalması.
  - **Yanıt (05.10.2026):** Öneri kabul edildi; kesme her zaman uygulanır.
- [x] S2: Uygulama sırası. Öneri: fix bu dalda (6.7.9 tabanı) yapılsın, ardından develop ile sync edilip PR develop'a açılsın. Fix 333607 koduna bağımlı olmadığı için iki paket seçeneği de açık kalır; 6.7.9 tabanlı paket seçilirse `f627b133` ve yeni commit cherry-pick edilir.
  - **Yanıt (05.10.2026):** Uygun. Sync (merge) ve PR adımları öncesinde `gbpd-sync-develop-branch` raporu sunulup ayrıca onay alınır.
- [x] S3: Dosya kimliği olarak 333599 kullanıldı (ilk işin devamı). Bu iş için ayrı bir task id varsa dosya adı ve history kimliği ona göre güncellenir.
  - **Yanıt (05.10.2026):** 333599 doğru; task bu.

## Ön Kontrol Sonucu (05.10.2026)

- Fetched flag kolonu uygulama tarafından `FLOAT`/`DOUBLE PRECISION` olmaya zorlanıyor (`Constants`, `CampaignDataTypeChecker`; aksi halde campaign start `campaign.start.wrong.types` ile reddedilir). Bu yüzden `< literal` karşılaştırması sayısal yapılıyor, string karşılaştırması riski yok.
- Dialer bu kolona yalnız rezervasyon anını (`reserveRowsForFetch`, `Utils.convertDateToDouble(now)`) ya da `NULL` yazıyor (final/retry yazımı, callback reschedule). Gelecek tarihli değer üreten bir yol yok.
- Canlı salt okuma örneklemi (startup DB check açık kampanyaların erişilebilen result tabloları):
  - Turkcell camp DB'de 4 tablo, NonTurkcell camp DB'de 7 tablo; toplam ~1,14 milyon dolu `is_fetched` satırı.
  - Gelecek tarihli (`>= GETDATE()`) değer: **0**. Anormal küçük değer: **0**. Tüm değerler `datetime`'a dönüştürülebiliyor (`convertDateToDouble` ile SQL Server float tarih biçimi aynı).
- Sonuç: kardeşsiz durumda her zaman eklenen `< cleanup anı` koşulu, bugün temizlenen bayat rezervasyonların hiçbirini dışarıda bırakmıyor.
