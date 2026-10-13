# FIX Paylaşılan Kampanya Tablosunda Mükerrer Arama Change/Fix Planı

## Bağlam

- Task: 333599
- Branch: `fix/333599-paylasilan-tablo-mukerrer-arama` (`origin/release/6.7.9` tabanlı, 6013c57b)
- Vaka (02.10.2026, nonturkcell): campaign 6 `KFK_TAHSILAT`, contact 326361, 09:00–09:11 arasında 6 kez arandı. Altı çağrının hepsi cevaplandı (telephony_rc 10). Temsilciler beş kez URC 53 `RANDEVU_KFK` (final + callback), bir kez URC 37 seçti. Her yeni `Call`, bir önceki çağrı bitmeden üretildi (`pd_cdr.call_start_time` < önceki `call_end_time`).
- Aynı desen son bir ayda 6 iş gününde görüldü; hepsi sabah 09:00 start'ını izleyen ilk ~30 dakikada. Önceki çağrı bitmeden üretilen fazla arama sayıları: 03.09=27, 11.09=48, 17.09=6, 25.09=22, 01.10=16, 02.10=7.
- 17.09'daki FIX-330538 aynı kampanyadaki aynı belirti için cache/callQueue guard'ı ekledi. Ancak kök neden bulunamadı ve guard görüşmede olan (originate edilmiş) çağrıyı kapsamıyor.
- Branch tabanı bilinçli olarak `release/6.7.9` seçildi. develop'taki 325559 ve 325470 commit'leri testte olduğu için canlıya çıkmamalı. Bu dal o commit'leri içermiyor; FIX-330538 ise içeriyor.

## İş Etiketi

fix

## Öncelik

hotfix

## Etkilenen Alanlar

- `engine/dialer/state/StopState.java` — `checkForUnfinishedCalls` (startup DB check)
- `engine/db/CampaignDbOperations.java` — `removeFetchedDataFlagFromUnfinishedCalls`
- `engine/dialer/CampaignContext.java` — kampanyanın STOP'tan çıktığı an (yeni instance alanı)
- `engine/dialer/CallProducer.java` — `refreshActiveUniqueKeys` (FIX-330538 guard kapsamı)
- `docs/architecture/DIALER-RUNTIME.md` — "Mükerrer Fetch Koruması" bölümü

## Mevcut Davranış

1. Aynı result tablosunu paylaşan iki kampanya var: campaign 6 `KFK_TAHSILAT` ve campaign 17 `KFK_TAHSILAT_2_DONEM`. İkisi de `KFK_TAHSILAT` tablosunu kullanıyor. Scheduler ikisini her iş günü 09:00'da aynı saniye içinde start ediyor (`pd_action_log` 01.09–02.10).
2. `startup_db_check=1` olan kampanya STOP→START geçişinde `removeFetchedDataFlagFromUnfinishedCalls` çalıştırıyor:
   `UPDATE <result> SET is_fetched = NULL WHERE complete_date IS NULL AND is_fetched IS NOT NULL AND (start_date IS NULL OR start_date < retry_date OR start_date < callback_date)`.
   Sorgu kampanya filtresi taşımıyor, yani tablonun tamamına uygulanıyor. `reserveRowsForFetch` rezervasyonda `start_date = NULL` yazdığı için, diğer kampanyanın **o anda rezerve ettiği** satırlar da koşula uyuyor.
3. 02.10 akışı:
   - 09:00:16.493 campaign 6 start oldu.
   - 09:00:16.583 campaign 17'nin cleanup'ı çalıştı.
   - 09:00:16.796 campaign 6 ilk 6 satırı rezerve etti.
   - Rezervasyonlar silindi. 09:00:26.8'de aynı satırlar yeniden Fresh olarak çekildi:
     - çağrıda olan 326361 ve 326426 için yeni `Call` üretildi,
     - cache'te olan 326538'i `[DUP-FETCH-GUARD]` yakaladı.
4. Zincir: sonraki adımda bayat kopya devreye giriyor.
   - Final yazım, callback/retry varsa `is_fetched = NULL` yazıyor (`CampaignDbOperations.updateCampaignListTableRow`).
   - Bayat kopya `Call` dial (VarSet, `ManagerConnectionDecorator.handleCallCreation`) ve answer (`handleCallAnswer`) anında `insertCallResultToDB` ile kendi boş durumunu yazıyor: `callback_date = NULL`, `user_result_code = NULL`, `retry_date = NULL`.
   - Satır yeniden Fresh sorgusuna uyuyor ve sıralamada en üstteyse sonraki tick'te tekrar çekiliyor.
   - 326361'de bu döngü 4 kez daha döndü.
5. FIX-330538 guard'ı yalnız `dataQueue` ve consumer `callQueue`'suna bakıyor. Originate edilmiş, cevap bekleyen veya wrap-up'taki çağrı kapsam dışında.

## Beklenen Davranış

1. Bir kampanyanın startup cleanup'ı, aynı tabloyu kullanan ve o anda canlı olan (`campaignContexts` içindeki) başka bir kampanyanın, o kampanya canlıya geçtikten sonra yaptığı rezervasyonlara dokunmaz.
   - Canlı kardeş kampanya yoksa davranış birebir bugünkü gibidir.
   - Kardeş kampanya canlıya geçmeden önceki bayat rezervasyonlar (çökme kalıntısı) temizlenmeye devam eder.
2. Producer, görüşmesi sürmekte olan (originate edilmiş, finalize edilmemiş) bir satır için yeni `Call` üretmez. Bu, rezervasyonun hangi nedenle kaybolduğundan bağımsızdır.
3. Callback, retry ve normal fetch akışları değişmez. Final yazıldıktan ve `Call` cache'lerden çıktıktan sonra satır her zamanki gibi tekrar çekilebilir.

## Kod Okuma Bulguları

- `StopState.start()` sırası: `checkForUnfinishedCalls()` → `campaignContexts.put(...)` → `super.start()` (`CampaignStateTemplate.start`: `startedDate` set edilir, producer/consumer başlar). Scheduler start'ları tek thread'te (`campaign-scheduler-0`) sırayla çalışıyor; manuel start ise `catalina-exec` thread'inden geliyor.
- `Campaign.startedDate`, pause sonrası unpause'ta da `CampaignStateTemplate.start` üzerinden güncelleniyor. Bu yüzden kesme anı olarak kullanılamaz: pause öncesi rezervasyonlar bayat sayılırdı. Kesme anı için STOP→START geçişinde set edilen ayrı bir instance alanı gerekiyor.
- Rezervasyon değeri `Utils.convertDateToDouble(now)`; MS SQL için `fetchedFlagLiteral` tırnaklı literal üretiyor. Kesme karşılaştırması aynı literal fonksiyonuyla yapılırsa kolon tipinden bağımsız tutarlı olur.
- Görüşmedeki `Call`'lar `ManagerConnectionDecorator` cache'lerinde duruyor: `calls`, `awaitingUserResultCodeCalls`, `recoverableConnectionLossCalls`, `orphanedCalls`. Hepsinin expiry'si var (`CALL_EXPIRE_TIMEOUT` vb.). `UserLoginLogoutHandler.getCallsByUser` bu cache'leri `getOpenedConnections()` üzerinden tarıyor. `Campaign.callsInProgress` yalnız call id tutuyor ve hangup'ta (URC beklenirken) temizleniyor; bu iş için yetersiz.

## Root Cause / Değişiklik Gerekçesi

- Kök neden (tetik): kampanya kapsamı olmayan startup cleanup, aynı tabloyu kullanan canlı kampanyanın rezervasyonlarını siliyor. İki kampanyanın aynı saniyede start edilmesi her gün yarış fırsatı yaratıyor. `[DUP-FETCH-GUARD]` kayıtlarının yalnız çakışma günlerinde görülmesi yarışın bazı günler isabet ettiğini gösteriyor.
- Büyütücü: üretilen kopya görüşme sırasında da korunmadığı için ara yazımlarıyla önceki çağrının final sonucunu siliyor ve döngüyü besliyor.
- Kalıcı çözüm iki ayaktan oluşuyor:
  - **(A)** tetiği owner boundary'de, yani cleanup'ta kesmek;
  - **(B)** rezervasyon hangi nedenle kaybolursa kaybolsun, görüşmedeki satırın ikinci kez `Call`'a dönüşmesini engellemek.
- Ayar düzeyinde geçici çözüm (deploy beklemeden): campaign 17'de Startup DB Check kapatılabilir. Campaign 6'da bu ayar 18.09'dan beri zaten kapalı.

## Riskler

- (A) Kesme anı çok yeni seçilirse bayat (çökme kalıntısı) rezervasyonlar temizlenmez; satır cache/expire'a kadar takılı kalır. Bu yüzden kesme anı kardeş kampanyanın STOP→START anı olarak alınıyor: o andan önceki rezervasyonlar kardeşe ait canlı rezervasyon olamaz.
- (A) Kardeş eşleştirmesi `dbConnectionString` + `dbResultTable` eşitliğiyle yapılacak. Farklı yazılmış ama aynı DB'yi gösteren bağlantı dizesi eşleşmezse bugünkü davranışa düşülür; regresyon yok, koruma yok.
- (A) Farklı dialer node'larında çalışan kampanyalar aynı tabloyu paylaşırsa `campaignContexts` onları görmez. Kapsam dışı; dokümana not edilecek.
- (B) Guard, görüşmedeki satırı her tick yeniden SELECT edip atlar. `TOP (n)` içinde bir slot tüketir ve tick başına tek `[DUP-FETCH-GUARD]` satırı/ActionLog üretir. Davranış FIX-330538 ile aynı; görüşme bitince kendiliğinden kalkar.
- (B) `orphanedCalls`/awaiting cache'inde kalan `Call`, o satırın yeniden çekilmesini cache expiry'sine kadar geciktirebilir. Bu kabul edilebilir: o sürede müşteriyle aynı kayıt üzerinde canlı veya sonuçlanmamış bir çağrı vardır.
- (B) Tarama tüm bağlantılardaki canlı çağrılar üzerinde yapılır. Yalnız fetch yolunda (cache'te yer varken) ve tick başına bir kez çalışır.

## TDD Stratejisi

1. Karakterizasyon testleri:
   - Kardeş kampanya yokken `removeFetchedDataFlagFromUnfinishedCalls` SQL'i bugünküyle birebir aynı kalır.
   - Startup DB Check kapalıyken cleanup çağrılmaz.
2. Kırmızı testler (A):
   - Kardeş kampanya aynı tabloyla canlıysa cleanup SQL'i `AND is_fetched < <kardeşin canlıya geçiş anı literal'i>` taşır.
   - Farklı tablo, farklı bağlantı veya self durumunda kesme uygulanmaz.
   - Birden çok kardeş varsa en erken an kullanılır.
3. Kırmızı testler (B):
   - Satırın `Call`'u `calls`, awaiting, recoverable veya orphaned cache'inde ise DB satırı yeniden döndürse bile yeni `Call` üretilmez ve rezervasyon UPDATE'i gönderilmez.
   - Başka kampanyanın aynı uniqueKey'li çağrısı guard'ı tetiklemez.
   - Cache'ten çıkan satır yeniden çekilebilir.
4. Test sınıfları:
   - `tests/campaign/db/StartupCleanupSharedTableScopeRegressionTest`
   - `tests/dialer/state/StartupCleanupSiblingCutoffRegressionTest`
   - mevcut `tests/dialer/CallProducerDuplicateFetchGuardRegressionTest` genişletilir.

## Açık Sorular

- [x] S1: Üçüncü ayak bu hotfix'e girsin mi? Bu ayak, bayat bir `Call`'un dial/answer/final yazımının başka bir çağrının sonucunu ezmesini satır bazlı bir token'la (ör. yazımda `is_fetched = <kendi rezervasyonu>` şartı) engeller. Öneri: **hayır**. (A)+(B) ile kopya `Call` oluşamaz hale geliyor. Token, result yazım contract'ını ve tüm ara yazım yollarını değiştirir; ayrı task olarak ele alınmalı.
  - **Yanıt (02.10.2026):** Öneri kabul edildi. Token ayağı bu hotfix'e girmez, ayrı task olarak ele alınacak.
- [x] S2: PR hedef dalı (haftaya canlıya çıkacak release dalı). Kod için blocker değil; yalnız PR adımını bekletir.
  - **Yanıt (02.10.2026):** Hedef dal henüz belli değil; arada devreye alınacak ve alınmayacak geliştirmeler var. Kodlamaya engel değil. Branch `release/6.7.9` tabanında kalır; PR hedefi sonra kararlaştırılacak.

## Execution Plan

- [x] Açık sorular cevaplandı ve plana işlendi.
- [x] Karakterizasyon: cleanup SQL'i (kardeş yokken) ve Startup DB Check kapalı davranışı kilitlendi.
- [x] (A) Kırmızı: kardeşli cleanup kesme testi ve StopState kardeş seçimi testi.
- [x] (A) `CampaignContext`'e STOP→START anı alanı (`liveSince`) eklendi; `StopState.start` içinde `campaignContexts.put` öncesinde set ediliyor.
- [x] (A) `removeFetchedDataFlagFromUnfinishedCalls(campaign, cutoff)` eklendi; `cutoff != null` iken `AND is_fetched < literal`. Mevcut imza kardeşsiz davranışı korur.
- [x] (A) `StopState.checkForUnfinishedCalls`: aynı bağlantı + result tablosunu kullanan diğer context'lerin en erken `liveSince` değeri kesme olarak geçiliyor; kesme uygulandığında tek INFO satırı yazılıyor.
- [x] (B) Kırmızı: görüşmedeki `Call` guard testleri.
- [x] (B) `CallProducer.refreshActiveUniqueKeys`, bu kampanyanın MCD cache'lerindeki `Call`'larının uniqueKey'lerini de topluyor. Uygulamada her producer tick başında çalışır, çünkü callback yönetim yolu (kuyruk doluyken) da fetch yapar ve aynı guard'ı kullanır.
- [x] Hedefli testler yeşil; ilgili mevcut testler (`CallProducerTest`, `FetchedFlagReservationGuardRegressionTest`, `StopStateTest`) koşuldu.
- [x] `docs/architecture/DIALER-RUNTIME.md` "Mükerrer Fetch Koruması" güncellendi.
- [x] History kaydı eklendi.

## Performans Etkisi

- Hot path durumu:
  - (A) hot path dışında: kampanya STOP→START anında bir kez çalışıyor, ek DB sorgusu yok, yalnız mevcut UPDATE'e bir koşul ekleniyor.
  - (B) her producer tick'inde (5 sn; fetch ve callback yönetim yolu ortak), call/event hot path'inde değil.
- Call/event/tick başına ek maliyet:
  - (B) tick başına node'daki canlı çağrı sayısı kadar tek geçiş. Kilit yok; Guava cache `asMap().values()` weakly consistent.
  - Call ve event başına ek maliyet yok.
- IO/scan/lock/serialization/log etkisi: yeni IO yok. Guard tetiklenirse tick başına tek WARN/ActionLog (mevcut davranış).
- Azaltım veya ölçüm notu: tarama node'daki canlı çağrı sayısıyla sınırlı ve tick başına bir kez yapılır. Gerekirse sonraki adımda yalnız DB satır döndürdüğünde tembel hesaplanabilir.

## Doğrulama

`.codex/TOOL-PATHS.local.properties` ile offline Maven:

```text
mvn -o -Dtest=StartupCleanupSharedTableScopeRegressionTest,StartupCleanupSiblingCutoffRegressionTest,CallProducerDuplicateFetchGuardRegressionTest,FetchedFlagReservationGuardRegressionTest,CallProducerTest,StopStateTest test
```

Not: `StopStateTest` içinde 3 pre-existing `NotAMockException` hatası FIX-330538 kaydında raporlanmış; sonuç ona göre değerlendirilecek.

Canlı doğrulama (deploy sonrası, ertesi gün 09:00):
- `pd_action_log`'da campaign 17 cleanup satırı görülmeli;
- `[DUP-FETCH-GUARD]` görülmemeli;
- campaign 6 `pd_cdr`'da önceki çağrı bitmeden üretilen çağrı sayısı 0 olmalı.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı veya `Yok` olarak işaretlendi.
- [x] Planlanan testler çalıştırıldı ve başarılı oldu.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

02.10.2026 — Uygulandı (commit atılmadı; PR hedef dalı kararı bekleniyor).

- (A):
  - `CampaignContext.liveSince` + `markLive` eklendi.
  - `StopState.registerAsLive` haritaya eklemeden önce bu anı işaretliyor.
  - Yeni `SharedResultTableSiblings.earliestLiveSince` aynı bağlantı ve result tablosunu kullanan canlı kardeşlerin en erken anını buluyor. Anı bilinmeyen kardeş için `Date(0)` döner; böylece hiçbir rezervasyon silinmez.
  - `CampaignDbOperations.removeFetchedDataFlagFromUnfinishedCalls(campaign, fetchedBefore)` kesme varsa `AND is_fetched < <literal>` ekliyor. Eski imza kesmesiz çağrı olarak korundu.
- (B):
  - `ManagerConnectionDecorator.collectInFlightUniqueKeys` dört cache'i tarıyor.
  - `CallProducer.refreshActiveUniqueKeys` bunu açık tüm bağlantılar için çağırıyor.
  - `[DUP-FETCH-GUARD]` öneki korundu; mesaj metnine "or is in flight" eklendi.
- Testler:
  - Yeni: `StartupCleanupSharedTableScopeRegressionTest` (4), `StartupCleanupSiblingCutoffRegressionTest` (9), `InFlightCallUniqueKeysRegressionTest` (2).
  - Genişletilen: `CallProducerDuplicateFetchGuardRegressionTest` (+2).
  - `StopStateTest` cleanup doğrulamaları yeni imzaya geçirildi; kardeşsiz durumda kesme `null` olarak doğrulanıyor.
  - Kırmızı aşama: iskelet API ile 11 beklenen hata görüldü.
- Doğrulama:
  - Hedefli set: 57 test, yalnız 3 pre-existing `StopStateTest` `NotAMockException` hatası (baseline'da da var).
  - Geniş set (`tests/**`, `engine/dialer/**`, `engine/asterisk/**`, `DNCEngineTest`): 843 test, 1 failure + 4 error. Hepsi FIX-330538 kaydındaki pre-existing hatalar (StopStateTest 3, UnmappedHangupCauseFallback 1, LogbackAsyncAppenderConfigContract 1).
- Kalan:
  - PR hedef dalı.
  - develop'a merge (`CallProducer`'da 325559 kaynaklı conflict beklenir).
  - Canlı doğrulama (ertesi gün 09:00 `pd_action_log` ve `pd_cdr` çakışma sayısı).
  - Token ayağı ayrı task.
