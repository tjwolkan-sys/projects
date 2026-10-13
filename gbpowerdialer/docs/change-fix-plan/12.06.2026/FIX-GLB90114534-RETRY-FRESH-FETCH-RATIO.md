# FIX Retry/Fresh Fetch Ratio Bozulması Change/Fix Planı

## Bağlam

`TFS_WELCOME` kampanyasında `Retry Ratio (%) = 1` olmasına rağmen producer cache'ine fresh yerine ağırlıklı olarak retry kaydı giriyor. Geliştiricinin camp tablosu sorgusunda `is_fetched` dolu 145 satırdan yalnız ~50 tanesi fresh, ~95 tanesi retry kaydı çıkıyor. Beklenen davranış: %1 retry oranıyla cache'in büyük çoğunluğu fresh olmalı.

Müşteri gözlemi: retry stratejisini değiştirince fresh'ler aranmaya başlıyor.

- Sorunlu strateji: ekrandaki "Round-robin dial with overall retry limit, schedule next **number**" (ordinal 2 → enum `ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER` → `RoundRobinNextNumber`).
- Çalışan strateji: "Round-robin dial with overall retry limit, schedule next **cycle**" (ordinal 3 → enum `ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_CYCLE` → `RoundRobinNextCycle`).

Kampanya ek ayarları: `allowCallback = true`, retry schedule aktif.

İlgili resimler: `images/resim (5).png`, `images/resim (6).png`, `images/resim (7).png`, `images/resim (9).png`.

İki konfigürasyon karşılaştırması:

| Ayar | resim (5) – TFS_WELCOME | resim (9) – referans/diğer config |
|---|---|---|
| Retry Strategy | overall retry limit (truncated) | overall retry limit (truncated) |
| Overall Attempts | 6 | 10 |
| Macro Interval (h) | 8 | 3 |
| Max Attempts in Macro | 6 | 5 |
| **Retry Ratio (%)** | **1** | **15** |
| Retry Delay (m) | 30 | 30 |
| Result code Retry Amount / Interval | 2 / 30 | 10 / 60 |
| Enable Retry Scheduling | (crop'ta görünmüyor) | **✓ işaretli** |

Notlar:
- `resim (9)`'daki **"Enable Retry Scheduling" checkbox'ı = kodda `Campaign.retryScheduleEnabled`** flag'ıdır ve burada açıktır. Bu flag açıkken fresh FETCH sorgusu `AND retry_date IS NULL` koşulunu ekler (`fetchDataForProducerFresh:722-723`); analizin dayandığı koşul budur. TFS_WELCOME crop'unda bu checkbox görünmediği için durumu Açık Sorular #6'da teyit ediyoruz.
- **Hata mekanizması retry ratio değerinden bağımsızdır.** Ratio 1 olsun 15 olsun, producer retry'i retry PATH'inde o yüzde ile kapar; fakat `retry_date NULL + attempts>0` retry-record satırları fresh PATH'inden sızdığında kapak atlanır. Ratio 1'de sapma daha çarpıcı görünür (beklenen ~%99 fresh, gözlenen ~%34), ratio 15'te aynı mekanizma daha az göze çarpar.

## İş Etiketi

fix

## Öncelik

normal

(Üretimde çalışan outbound dialer davranışını etkiliyor; outage değil ama dialing verimliliğini ve müşteri SLA'sını bozuyor. Call flow + persistence hot path olduğu için riskli alan kabul edilir.)

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallProducer.java` (fetch ratio kapağı, fresh/retry/callback fetch akışı, `isRetryCDR`)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/db/CampaignDbOperations.java` (`fetchDataForProducerFresh`, `fetchDataForProducerRetries`, `countFreshDataListSize`, `updateCampaignListTableRow`, `removeFetchedDataFlagFromCalls`, `removeFetchedDataFlagFromUnfinishedCalls`)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionDecorator.java` (**birincil**: `processCallResult` + one-shot `markBusinessResultFinalized`, `persistFinalCdr` (pd_cdr-only), `runCallResultSideEffects`→`insertCallResultToDB`, `handleOriginateResponse`/`handleOriginationFail`; ikincil: `clearPreviousRetryDateForOriginatedCall`, `manageRetries`, ring/answer ara-persist'leri `handleCallCreation`/`handleCallAnswer`)
- `gbpowerdialer/src/main/java/com/powerdialer/model/Call.java` (`markBusinessResultFinalized`/`rollbackBusinessResultFinalized` one-shot bayrağı; `call_process` kodları `F/R/CB`)
- `gbpowerdialer/src/main/java/com/powerdialer/model/Call.java` (`retryDate` alanı raw'dan yüklenmiyor; `retryHistory` yükleniyor)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/retry/RoundRobinNextNumber.java`, `RoundRobinNextCycle.java`, `RoundRobinPerCycle.java`, `RetryStrategy.java` (retry_date hesaplama farkı)
- `gbpowerdialer/src/test/java/...` (karakterizasyon/regression testleri)

## Mevcut Davranış

1. **Producer retry'i %1 ile sınırlıyor — retry path'inde.** `CallProducer.fetchCallsWithoutSegmentation` (`:362-378`): `retryQuantity = (retryRatio * remainDataToFetch) / 100`. Segmentasyonlu yolda da aynı kapak var (`fetchCallsWithSegmentation:310`). Yani retry **path'inden** çekilen kayıt gerçekten ~%1. Demek ki cache'e dolan retry'lerin çoğu bu path'ten gelmiyor.

2. **"Fresh" tanımı fetch ve count sorgularında tutarsız.**
   - Fresh **FETCH** (`fetchDataForProducerFresh:720-723`): bir satırı yalnız `complete_date IS NULL`, `fetched_flag IS NULL`, (retryScheduleEnabled ise) `retry_date IS NULL`, (allowCallback ise) `callback_date IS NULL` ile fresh sayar. `attempts` ve `retry_history`'ye **bakmaz**.
   - Fresh **COUNT** (`countFreshDataListSize:1576-1580`): fresh'i `retry_history` boş **VE** `attempts IN (null,'',0)` ile tanımlar.
   - `isRetryCDR` (`CallProducer:644-653`): `attempts>0` veya `retry_history`/`retry_date` doluysa call'ı RETRY raporlar — fetch'te "FRESH" etiketi alsa bile.

   ⇒ `retry_date = NULL` ama `attempts > 0` olan bir satır, fresh FETCH sorgusuna takılır (retry-ratio kapağına uğramaz), cache'e FRESH callType ile girer, ama veride retry kaydıdır ve retry olarak raporlanır.

3. **`retry_date`, attempts korunurken NULL'a düşebiliyor.**
   - `Call.retryDate` raw data'dan yüklenmiyor (`Call.java:296-301`), oysa `retryHistory` raw'dan lazy-load ediliyor (`:585-594`). Tekrar çekilen retry satırı bellekte `retryDate = null` başlar.
   - `clearPreviousRetryDateForOriginatedCall` (`ManagerConnectionDecorator:1087-1091`) originate edilen çağrıda `retryDate = null` yapar.
   - Persist (`updateCampaignListTableRow:1812-1818`) `call.getRetryDate()` null ise DB'ye `retry_date = NULL` yazar; ama `attempts` (`:1864`) ve `retry_history` (`:1870`) yine yazılır.
   - Ring (`handleCallCreation:474`) ve answer (`handleCallAnswer:517`) anındaki ara-persist'ler `retryDate` null iken çalışır → in-flight satıra `retry_date = NULL` yazar.

4. **`fetched_flag` temizliği retry/callback tarihine bağlı.** Persist'te `fetched_flag` yalnız `retry_date` veya `callback_date` non-null ise temizlenir (`:1820-1822`). Reset/startup yolları (`removeFetchedDataFlagFromCalls:1442`, `removeFetchedDataFlagFromUnfinishedCalls:1425`) `fetched_flag`'i temizlerken `retry_date`'e dokunmaz.

   ⇒ Ara-persist'i `retry_date`'i NULL'a düşürmüş in-flight bir retry satırı, pause/stop/startup-check ile `fetched_flag` temizlenince `complete_date = NULL + retry_date = NULL + attempts > 0` (geçersiz durum) haline gelir. Bu satır fresh FETCH sorgusuna takılır → retry-ratio kapağı atlanır.

## Beklenen Davranış

- `retryRatio = 1` iken producer cache'i ~%99 fresh, ~%1 retry olmalı; retry-record satırlar fresh path'inden cache'e sızmamalı.
- Denenmiş (attempts>0 / retry_history dolu) ve tamamlanmamış bir satır asla "fresh" olarak sınıflanmamalı; ya geçerli bir `retry_date` ile retry havuzunda olmalı ya da `complete_date` ile tamamlanmış olmalı.
- `complete_date = NULL + retry_date = NULL + attempts > 0` geçersiz durumu hiçbir akışta üretilip persist edilmemeli; üretilse bile bu satırlar fresh'e sınıflanmamalı ve dialable kalmalı (orphan olmamalı).
- Strateji değişimi (`NextNumber` ↔ `NextCycle`) fresh/retry oranını değiştirmemeli; ikisi de retryRatio'ya uymalı.

## Kod Okuma Bulguları

- Producer fetch sırası: önce callback (`remainDataToFetch` kadar), sonra retry (`retryRatio%`), sonra fresh (kalan). `allowCallback=true` olduğundan callback ilk sırada cache yer kaplayabilir; ancak şikayet retry odaklı.
- `NextNumber` ile `NextCycle` arasındaki tek davranış farkı `calculatePlannedRetryDate`:
  - `RoundRobinNextNumber` (base `RetryStrategy:105-113`): `retry_date = now + retryDelay` (resimde 30 dk), her retry **gelecekte**.
  - `RoundRobinNextCycle` (`RoundRobinPerCycle:42-51`): cycle içi `retry_date = now` (hemen due), yalnız cycle sonunda `+retryDelay`.
  - `isOverallRetryLimitReached` ve `setRetryNumber` iki sınıfta da aynı; tamamlama kararı aynı.
- `createRetry` (`RetryStrategy:39-69`) her zaman ya `retry_date` ya `complete_date` set eder; tek başına geçersiz durum üretmez. Geçersiz durum, `createRetry` dışı persist yolları + reset kombinasyonundan doğuyor.
- Reset metotları yalnız `fetched_flag = NULL` yazar; `retry_date`/`attempts`'ı düzeltmez.

## Kanıt (12.06.2026 — camp tablosu + pd_cdr + log)

İki somut örnek (`campaign_id=74`, `TC_BIREYSEL_TAHSILAT_WELCOME`, `ListID=20260601072245`):

| | contact 723383 (resim 10/11) | contact 743536 (resim 12/13) |
|---|---|---|
| pd_cdr | answer + handling + call_end var, **trc=10** (cevaplandı, 2026-06-09) | dial var, answer NULL, call_end var, **trc=11** (cevapsız, 2026-06-12 10:36) |
| camp satırı | complete NULL, retry NULL, callback NULL, **trc NULL**, **ATTEMPTS=1**, **call_process='F'**, is_fetched SET | aynı: complete/retry/callback/trc NULL, ATTEMPTS=1, call_process='F', is_fetched SET |

Kritik bulgular:
- **`call_process='F'` = `CALL_PROCESS_FRESH` (`Call.java:90`).** Yani bu takılı satırlar **fresh** (ilk deneme) aramalardır, retry değil. ATTEMPTS=1 olduğu için "retry" gibi görünüyorlar ama değiller.
- **pd_cdr finalize edilmiş, camp list tablosu edilmemiş.** Camp satırı yalnız ring/answer ara-persist'inin (`handleCallCreation:474`, `handleCallAnswer:517`) yazdığı `attempts=1 + call_process='F'` durumunu taşıyor; `telephony_result_code`/`complete_date`/`retry_date` hiç yazılmamış ve `is_fetched` set kalmış.
- Log: `dl-05c60752-...` için `Origination has been failed for call ... with reason code '0'` ve memory'de hesaplanan ama DB'ye yazılmayan `retryDate=46183.46284748842 → 2026-06-12 11:06:30`.

**Sonuç:** Kullanıcının gördüğü "is_fetched setinde 95 retry" tablosunun asıl kaynağı fetch ratio değil; **finalize edilememiş, `is_fetched` set kalmış takılı satırların birikmesi.** Bu satırlar (a) `is_fetched IS NOT NULL` olduğu için ne fresh ne retry sorgusuna düşer → bir daha aranmaz, (b) `complete_date` set olmadığı için tamamlanmaz → kalıcı olarak dolaşımdan çıkar. Zamanla fresh havuzu eridikçe dialer ağırlıklı olarak (gerçekten `retry_date` ile zamanlanmış) retry'leri arar → müşterinin "fresh yerine retry arıyor" gözlemi.

### Log zaman çizelgesi (dl-05c60752-..., contact 743536, 2026-06-12)

- `10:26:29.238` producer fetch (`Got production number 2235`) → camp'a `is_fetched` set, fresh.
- `10:36:23.8–24.0` originate (`Originate successfully queued` = aksiyon kuyruğa alındı, success), user 9517 atandı, **initial CDR saved** (`10:36:24.016`).
- `10:36:23.979` `Ringing calls incremented` → `handleCallCreation` (VarSet/ORIGINID) çalıştı → **ring ara-persist'i**: camp'a `attempts=1`, `call_process='F'`, `retry_date=NULL`, trc NULL, `is_fetched` set yazıldı (clear edilmedi, çünkü retry/callback tarihi yok).
- `10:36:29.9` `DialerHangupEvent` (User busy, cause 17) → `processCallResult`:
  - `10:36:29.984` **final pd_cdr saved** (`persistFinalCdr`, trc=11) → `markBusinessResultFinalized=true`.
  - `10:36:30.023` `RetryStrategy - Retry scheduled` (memory'de `retryDate=46183.46... → 11:06:30`), `Telephony slot released ... due to process-call-result`, `Incrementations/Decrementations processed`.
  - Hemen ardından `runCallResultSideEffects:949` → `insertCallResultToDB` (camp) çalışmalıydı. **Ama camp güncellenmedi** (retry_date NULL, trc NULL, is_fetched set kaldı = `10:36:23.979` ring durumu).
- `10:36:33.440` **geç/duplike `OriginateResponseEvent reason='0' response='Failure'`** (Local/AMD kanalı; aynı actionId) → `handleOriginateResponse` → `Origination has been failed ... reason '0'` → `handleOriginationFail`. Çağrı zaten `markBusinessResultFinalized=true` olduğundan (ya da cache'ten düştüğünden) **camp finalize tekrar denenmedi**.

Yani pd_cdr ve retry memory'de doğru hesaplandı; **kayıp olan tek şey camp `insertCallResultToDB` finalize'ı.**

**KANITLANDI (DB error logu):** `10:36:33` ERROR — `Query execution failed` ile başarısız olan UPDATE tam da camp finalize'ı:
```sql
UPDATE TC_BIREYSEL_TAHSILAT_WELCOME SET callback_date=NULL, called_group_id='10056',
  callback_number=NULL, called_number='05301457186', retry_date='46183.46284748842',
  is_fetched=NULL, retry_number='05301457186', start_date=46183.44194420139,
  telephony_result_code='11', user_id=NULL, user_result_code=NULL, rc_sequence='1',
  ATTEMPTS='1', UpdatedDate=CURRENT_TIMESTAMP, retry_history='05301457186', call_process='F;R'
WHERE id=743536
```
Bu UPDATE doğru içeriğe sahip (retry_date set, `is_fetched=NULL`, trc=11, `call_process='F;R'`) — başarılı olsaydı satır düzgün zamanlanmış retry olurdu. Camp'ta `call_process='F'` kalması da bunu doğruluyor: başarısız UPDATE 'F;R' yazacaktı, commit olmadığı için ring-persist'in 'F'si kaldı.

**Exact exception (KANITLANDI):**
```
2026-06-12 10:36:33 ERROR - Transaction (Process ID 104) was deadlocked on lock resources
with another process and has been chosen as the deadlock victim. Rerun the transaction.
```
SQL Server **deadlock (hata 1205)**. Camp finalize UPDATE'i deadlock kurbanı seçilip rollback edildi. SQL Server'ın kendisi *"Rerun the transaction"* diyor → bu **transient, yeniden-denenebilir** bir hata. Exception `insertCallResultToDB`'nin `try/catch`'inde yutuldu (`CampaignDbOperations:1703-1705`), çağrı-id içermediği için çağrı-id'li aramada görünmedi, ve one-shot `businessResultFinalized` bayrağı yüzünden **yeniden denenmedi**.

**Deadlock grafiği (resim 15 + XML, 2026-06-12 14:37:43) — kök contention KANITLANDI:**
- `spid 78` (victim): page `353186`'da U lock tutar, page `366259` için U bekler → `UPDATE ... WHERE id=747813` (finalize).
- `spid 87`: page `366259`'da X lock tutar, page `353186` için U bekler → `UPDATE ... WHERE id=712552` (finalize).
- Her iki page de **aynı obje** (`associatedObjectId=72057594043170816`, `TC_BIREYSEL_TAHSILAT_WELCOME`); isolation `read uncommitted`.

İki **eş zamanlı call-finalize UPDATE'i**, aynı tablonun **farklı page'lerinde PAGE-lock** ile çapraz kilitleniyor. Tek satırlık `WHERE id=?` UPDATE'inin birden çok page'i U-lock'layıp scan etmesi, **`id` lookup kolonunda kullanılabilir index olmadığını** (heap/clustered seek yerine page-scan) gösterir. Yani deadlock'un kök frekans nedeni **camp tablosunda `id` (colUniqueKey/colResultUniqueKey) üzerinde index eksikliği**: her finalize tabloyu tarayıp page U-lock'ları alıyor, iki eş zamanlı tarama birbirinin page'ini bekleyince deadlock oluyor.

Bu nedenle çözüm iki katmanlı (ikisi birbirini tamamlar):
- **App (dayanıklılık/correctness):** deadlock'a karşı finalize rerun + fallback (aşağıdaki birincil fix). Deadlock olsa bile finalize kaybolmaz.
- **DB (kök frekans azaltımı — DBA):** `id` üzerine index → finalize UPDATE **seek** yapar, sadece hedef satırın page/row lock'unu alır, eş zamanlı farklı-id finalize'ları çakışmaz → deadlock pratikte biter. Ayrıca `rowExistsInResultTableByCallUniqueKey` ve tüm per-call UPDATE/SELECT'leri hızlandırır.

### Startup recovery boşluğu

`removeFetchedDataFlagFromUnfinishedCalls` (`CampaignDbOperations:1425`) startup'ta `is_fetched`'i temizler ama koşulu `(start_date IS NULL OR start_date < retry_date OR start_date < callback_date)`. Takılı satırda `retry_date` ve `callback_date` NULL olduğundan `start_date < NULL` = NULL (false) → **bu satırlar recovery'ye yakalanmaz**, kalıcı takılı kalır. Mevcut self-heal bu durumu kurtarmıyor.

## Root Cause / Değişiklik Gerekçesi

### Birincil kök neden: camp list tablosu finalize kaybı (persist asimetrisi)

`processCallResult` (`ManagerConnectionDecorator:896-924`) FIX-320173 ile **persist-first** çalışıyor: önce `persistFinalCdr` (yalnız **pd_cdr**, `:987-1032`) commit ediliyor ve `markBusinessResultFinalized` one-shot bayrağı set ediliyor; pd_cdr başarısız olursa bayrak geri alınıp tekrar deneniyor. **Ama camp list tablosu finalize'ı (`insertCallResultToDB`) bu garantinin dışında**, `runCallResultSideEffects:949`'da pd_cdr commit'inden ve bayraktan SONRA çalışan bir side-effect. `insertCallResultToDB` kendi exception'ını yutup logluyor (`CampaignDbOperations:1703-1705`). Dolayısıyla camp update başarısız olur / etki etmezse:
- pd_cdr sonucu içeriyor, ama camp satırı ara-persist durumunda takılı kalıyor (`attempts=1`, `call_process='F'`, sonuç yok, `is_fetched` set),
- `businessResultFinalized` bayrağı geri alınmıyor; sonraki hangup bacağı / expiry / RC mesajı `processCallResult`'a girince bayrak `false` döndürüp erken çıkıyor → camp finalize **bir daha denenmiyor**,
- `is_fetched` yalnız retry/callback tarihi yazılırken temizlendiği için (`:1820`) set kalıyor.

Tetikleyici, kanıtta origination failure (`reason 0`) ile koreledir: `handleOriginationFail` (`:1286-1294`) çağrı `calls` cache'inde yoksa **hiçbir şey yapmıyor** (camp finalize yok, `is_fetched` temizliği yok); `handleExceptionDuringOrigination`'ın aksine (`:1301-1310`, fetched flag'i temizler). Ayrıca gerçek hangup + geç gelen `OriginateResponse` failure (reason 0) ikilisi, ikinci `processCallResult`'ın one-shot bayrakla erken çıkmasına yol açabilir.

### İkincil katman (önceki analiz — hâlâ geçerli ama baskın değil):

1. **Owner boundary (writer) tarafı:** `retry_date` yaşam döngüsü, denenmiş-ama-bekleyen bir satırın `retry_date`'ini koruyamıyor. `Call.retryDate` raw'dan yüklenmediği, `clearPreviousRetryDateForOriginatedCall` nulladığı ve ara-persist'ler `retry_date = NULL` yazdığı için DB'de geçici/kalıcı olarak `attempts>0 + retry_date NULL + complete NULL` geçersiz durumu oluşabiliyor. `fetched_flag` reset'i bu satırı fresh-uygun hale getiriyor.

2. **Query (reader) tarafı:** Fresh FETCH sorgusu fresh'i yalnız `retry_date IS NULL` ile tanımlayıp `attempts`/`retry_history`'yi yok sayıyor (count sorgusuyla tutarsız). Bu yüzden geçersiz-durum satırları fresh path'inden cache'e girip retry-ratio kapağını atlıyor.

`NextNumber`'ın `NextCycle`'dan daha kötü olması (hipotez): `NextNumber` her denemeyi `now+retryDelay` ile geleceğe park ettiği için uzun ömürlü ve kalabalık bir "gelecek tarihli retry" havuzu birikiyor; bu satırlar dial/reset döngülerinden geçtikçe geçersiz duruma düşme penceresi genişliyor. `NextCycle` cycle içi retry'leri `now` ile hemen tükettiğinden pencere dar. Bu hipotez müşterinin camp tablosu anlık görüntüsüyle doğrulanacak (Açık Sorular #1).

Çözüm önceliği (gbpd-no-workaround-root-cause-engineering): **(1) Birincil** — camp list tablosu finalize'ını pd_cdr ile aynı güvenceye almak: ya finalize'ı one-shot bayraktan/persist-first sözleşmesinden ÖNCE garanti etmek, ya da camp finalize başarısız olduğunda `businessResultFinalized` bayrağını geri alıp tekrar denenebilir bırakmak; `handleOriginationFail`'in `calls` cache'inde olmayan çağrıda da `is_fetched` temizliği + finalize yapması. **(2) İkincil/savunma** — fresh/retry sorgu tutarlılığı ve `retry_date` yaşam döngüsü, hâlihazırda bozulmuş canlı satırların doğru yola sokulması için. Birincil düzeltme kök nedeni keser; ikincil düzeltme defense-in-depth ve mevcut kirli veriyi toparlar.

## Riskler

- **Hot path:** Producer fetch loop ve AMI hangup persist hot path. Query değişiklikleri yalnız basit kolon predikatı eklemeli; yeni IO, tam tarama, lock veya verbose log eklenmemeli.
- **Orphan riski:** Fresh sorgusuna `attempts=0` koşulu eklenirse, `retry_date NULL + attempts>0` satırları artık ne fresh ne retry sorgusuna düşer → dialable kalmaları için retry sorgusu/lifecycle birlikte düzeltilmeli. Aksi halde "hiç aranmayan" satırlar oluşur.
- **Mevcut canlı veri:** Üretimde hâlihazırda geçersiz duruma düşmüş satırlar var. Sadece writer tarafını düzeltmek geçmiş kayıtları kurtarmaz; reader/lifecycle tarafı bu satırları doğru yola (retry) sokmalı.
- **Strateji semantiği:** `retry_date` hesaplama veya retry sorgu koşulu değişirse `NextCycle`, `RoundRobinPerCycle`, `ReachRetry*`, `RETRY_RULES_MANAGES_CYCLE` ve `countAllCallTypes` davranışları bozulmamalı.
- **Callback etkileşimi:** `allowCallback=true` olduğundan callback fetch'i ve `callback_date` koşulları regresyon kapsamında tutulmalı.
- Redis/AMI/result-code contract adları ve campaign state transition'ları değişmemeli.

## Seçilen Çözüm (onaylandı: Deadlock retry + fallback + ek kapsam)

Kullanıcı onayı: birincil = **Deadlock-aware retry + güvenli fallback**; ek kapsam = **canlı takılı veriyi kurtar + startup-recovery boşluğu + handleOriginationFail sağlamlaştır**. İkincil fetch-ratio sorgu işi **descope**. Branch = `develop`, kimlik = `GLB90114534`.

1. **Birincil — `CampaignDbOperations.insertCallResultToDB` deadlock-aware retry + fallback:**
   - UPDATE/insert SQL Server deadlock (hata 1205 / mesajda "deadlock") alırsa bounded retry (örn. `FINALIZE_MAX_ATTEMPTS=3`, kısa backoff ~25-50ms). Deadlock kurbanı rollback edildiği için rerun çoğunlukla başarılı (SQL Server "Rerun the transaction").
   - Tüm denemeler tükenirse fallback: çağrı **tamamlanmamışsa** (`call.getCompleteDate()==null`) `removeFetchedDataFlagFromCall(call)` ile `is_fetched` temizlenir → satır dolaşıma döner (fresh olarak yeniden çekilir), takılı kalmaz. Tamamlanmış çağrıda yeniden-arama riskinden kaçınmak için `is_fetched` set bırakılır (reconciliation/startup toparlar).
   - `isDeadlock(Throwable)` helper: cause zincirinde `SQLException.getErrorCode()==1205` veya mesajda "deadlock".
2. **Ek — startup-recovery boşluğu:** `removeFetchedDataFlagFromUnfinishedCalls` (`:1425`) WHERE koşuluna, `retry_date`/`callback_date` NULL ama denenmiş+eski takılı satırları da kapsayacak madde eklenir (`start_date IS NOT NULL AND retry_date IS NULL AND callback_date IS NULL` gibi), `is_disabled`/`complete_date` zaten korunuyor.
3. **Ek — `handleOriginationFail` sağlamlaştırma:** çağrı `calls` cache'inde yoksa da `is_fetched` temizliği yapılır (spurious reason-0 satırı takılı bırakmasın).
4. **Ek — canlı takılı veri:** acil kurtarma SQL'i (aşağıda) ile mevcut takılı satırlar dolaşıma döndürülür.

### TDD

1. **Birincil için (yeni `CampaignDbResultPersistDeadlockRetryRegressionTest`):** `CampaignDbOperations` mock `DBEngine` ile kurulur.
   - Kırmızı: `executeUpdate` ilk çağrıda deadlock `SQLException(errorCode=1205)` fırlatır → mevcut kodda 1 kez denenip yutulur (camp güncellenmez).
   - Yeşil: retry ile 2. denemede başarılı olur (`executeUpdate` 2 kez çağrılır, sonuç persist edilir).
   - Tüm denemeler deadlock: tamamlanmamış çağrıda `removeFetchedDataFlagFromCall` çağrılır; tamamlanmış çağrıda çağrılmaz.
   - Deadlock olmayan exception (örn. conversion): retry YOK, hemen fallback/yutma (transient olmayan hatada boşuna rerun yapma).
2. **Startup-recovery testi:** `removeFetchedDataFlagFromUnfinishedCalls` query string'inin takılı-satır koşulunu içerdiğini doğrula (gerçek DB yok).
3. **Regresyon (yeşil kalmalı):** mevcut `ProcessCallResultPersistFirstRegressionTest`, `RoundRobinNextNumberTest`, `RoundRobinNextCycleTest`, `RoundRobinPerCycleTest`, `RetryLimitTest`, `CallProducerTest`.

## Acil Kurtarma (canlı takılı veri — TFS_WELCOME)

Uygulama deploy edilmeden de mevcut takılı kontakları hemen dolaşıma döndürmek için (düşük yük/maintenance penceresinde çalıştırılması önerilir; bu satırlar fresh olarak yeniden aranacaktır):

```sql
-- 1) Önce SAY (kaç satır takılı):
SELECT COUNT(*) FROM TC_BIREYSEL_TAHSILAT_WELCOME WITH (NOLOCK)
WHERE complete_date IS NULL AND is_fetched IS NOT NULL
  AND retry_date IS NULL AND callback_date IS NULL AND start_date IS NOT NULL;

-- 2) Dolaşıma geri sok (producer fresh olarak yeniden çeker):
UPDATE TC_BIREYSEL_TAHSILAT_WELCOME SET is_fetched = NULL
WHERE complete_date IS NULL AND is_fetched IS NOT NULL
  AND retry_date IS NULL AND callback_date IS NULL AND start_date IS NOT NULL;
```
Not: `start_date IS NOT NULL` = aranmış ama finalize edilmemiş. Bu satırların bazıları (örn. trc=10 cevaplanan) gerçekte tamamlanmış olabilir; pd_cdr'da sonuç var ama camp'a yazılamadığı için yeniden aranırlar. Kabul edilebilir değilse koşul daraltılabilir.

## DB-side kök frekans azaltımı (DBA — deadlock'u önemli ölçüde azaltır)

Deadlock grafiği `id` üzerinde index olmadığını (finalize UPDATE'in scan yaptığını) gösterdi. Önce teşhis, sonra index:

```sql
-- 1) TEŞHİS: id üzerinde index var mı?
EXEC sp_helpindex 'TC_BIREYSEL_TAHSILAT_WELCOME';
-- (clustered PK veya nonclustered index 'id' kolonunu kapsıyor mu?)

-- 2) Yoksa: lookup kolonuna unique index ekle → finalize UPDATE seek yapar, page-scan deadlock'u biter
CREATE UNIQUE NONCLUSTERED INDEX IX_TC_BIREYSEL_TAHSILAT_WELCOME_id
ON TC_BIREYSEL_TAHSILAT_WELCOME (id);
-- Üretimde ONLINE seçeneği (Enterprise): WITH (ONLINE = ON)
```

Notlar:
- `id` zaten clustered PK ise bu gereksiz; o durumda deadlock başka bir hot index'in page-lock'undan geliyordur → ilgili index'lerde `ALTER INDEX ... SET (ALLOW_PAGE_LOCKS = OFF)` (row-lock'a zorlar) değerlendirilir.
- App retry'i bu azaltımdan bağımsız olarak gerekir (residual deadlock + correctness güvencesi). İkisi birlikte: index frekansı düşürür, retry kalanını güvenle yutar.
- Uzun vade: app'in camp result tablosu kurarken (`CreateSourceTableRequest`/result table setup) unique-key index'ini garanti etmesi ayrı bir iyileştirme olarak değerlendirilebilir.

## Açık Sorular

- [x] **(#0 — camp finalize kaybı) CEVAPLANDI/KANITLANDI.** `10:36:33` `Query execution failed` ile başarısız olan UPDATE tam da camp finalize'ı (yukarı bkz.). Kök neden kesin: camp finalize UPDATE'i DB tarafında başarısız (deadlock/timeout ile uyumlu), yutuldu ve one-shot bayrak yüzünden tekrar denenmedi. (Opsiyonel ek bilgi: `Query execution failed`'in altındaki SQLException metni — deadlock mı, lock timeout mı, conversion mı — ikincil fix detaylarını netleştirir ama birincil fix'i değiştirmez.)
- [ ] **(#1 — kök neden doğrulaması)** Müşterinin camp tablosu anlık görüntüsünde, `is_fetched` dolu 95 "retry" satırının kaçı `retry_date IS NULL`, kaçı `retry_date IS NOT NULL`? Kaçında `complete_date IS NOT NULL`? (Leak path'ini, `fetched_flag`'in tamamlamada temizlenmemesi gürültüsünden ayırır; fix'in writer-only mu yoksa reader+writer mı olacağını belirler.) Örnek sorgu:
  ```sql
  SELECT
    SUM(CASE WHEN retry_date IS NULL AND complete_date IS NULL AND attempts > 0 THEN 1 ELSE 0 END) AS invalid_attempted,
    SUM(CASE WHEN retry_date IS NOT NULL AND complete_date IS NULL THEN 1 ELSE 0 END) AS scheduled_retry,
    SUM(CASE WHEN complete_date IS NOT NULL THEN 1 ELSE 0 END) AS completed_but_fetched
  FROM <camp_result_table>
  WHERE is_fetched IS NOT NULL;
  ```
- [ ] **(#2)** TFS_WELCOME'da `startupDbCheck` açık mı ve kampanya schedule automation ile sık pause/stop oluyor mu? (Reset tetikleyicisini doğrular.)
- [ ] **(#3)** TFS_WELCOME'da `applyFilterRetries` ve segmentation filter (`Filter.ratio`) aktif mi? (Producer'ın segmentasyonlu mu segmentasyonsuz mu yoldan geçtiğini ve hangi fetch dalının düzeltileceğini belirler.)
- [ ] **(#4 — kapsam)** `fetched_flag`'in tamamlanan (`complete_date` set) satırlarda temizlenmemesi de bu iş kapsamında düzeltilsin mi, yoksa yalnız fresh/retry oranı mı? (`is_fetched` sayacı bu yüzden de şişiyor olabilir.)
- [ ] **(#5)** Kalıcı çözüm için `develop` üzerinde mi commit edilecek yoksa ayrı bir fix branch/task id mi açılacak? Task id verilirse dosya adı `FIX-<TASKID>-...` olarak güncellenecek.
- [ ] **(#6)** TFS_WELCOME'da "Enable Retry Scheduling" (`retryScheduleEnabled`) işaretli mi? (`resim (5)` crop'unda görünmüyor; `resim (9)`'da açık.) Kapalıysa fresh FETCH sorgusu `retry_date IS NULL` filtresini hiç eklemez ve `retry_date` dolu retry'ler de fresh'e karışır — bu, leak mekanizmasını ve fix yönünü değiştirir.
- [ ] **(#7)** `resim (9)` TFS_WELCOME'ın güncel/değiştirilmiş hâli mi, yoksa karşılaştırma için ayrı bir kampanya mı? `Retry Ratio=15` olan bu konfigürasyonda da aynı fresh/retry sapması gözleniyor mu, yoksa bu "doğru çalışan" referans mı?

## Execution Plan

- [x] Onaylanan planın tüm adımları bu dosyaya işlendi (kök neden kanıtlandı; yaklaşım onaylandı).
- [ ] Geliştirici planı inceledi/onayladı (kod öncesi son onay).
- [ ] `CampaignDbResultPersistDeadlockRetryRegressionTest` kırmızı aşaması yazıldı (deadlock 1205 → mevcut kod yutuyor).
- [ ] `insertCallResultToDB`'ye deadlock-aware retry + `isDeadlock` helper + tamamlanmamış-çağrı fallback eklendi (test yeşil).
- [ ] Deadlock-olmayan exception'da retry yapılmadığı testle korundu.
- [ ] `removeFetchedDataFlagFromUnfinishedCalls` startup-recovery boşluğu kapatıldı + query string testi.
- [ ] `handleOriginationFail` cache-miss durumunda `is_fetched` temizleyecek şekilde sağlamlaştırıldı.
- [ ] Acil kurtarma SQL'i geliştiriciye iletildi (canlı takılı veri).
- [ ] Regresyon testleri yeşil (`ProcessCallResultPersistFirstRegressionTest`, `RoundRobin*Test`, `RetryLimitTest`, `CallProducerTest`).
- [ ] Mimari doküman (`docs/architecture/DIALER-RUNTIME.md` finalize/persist bölümü) ve history güncellendi.

## Performans Etkisi

- Hot path durumu: `processCallResult` → `insertCallResultToDB`, AMI reader thread (`ManagerConnection-*-Reader`) üzerinde çalışır; hot path.
- Call/event/tick başına ek maliyet: **Başarı yolunda sıfır ek maliyet** (retry yalnız deadlock exception yakalanınca devreye girer). Deadlock nadir; devreye girdiğinde en fazla `(FINALIZE_MAX_ATTEMPTS-1)` kez kısa backoff (toplam ~50-100ms) reader thread'i bloklar.
- IO/scan/lock/serialization/log etkisi: Yeni sürekli IO yok; yalnız failure-path'inde aynı UPDATE rerun edilir (SQL Server'ın önerdiği davranış). Fallback tek bir küçük `is_fetched=NULL` UPDATE'i.
- Azaltım/ölçüm: Backoff küçük tutulur; deadlock retry sayısı WARN ile loglanır (gözlem). Gerekirse deadlock kök contention'ı (producer count/fetch vs finalize) ayrı DB-tuning işinde ele alınır.

## Doğrulama

Planlanan hedefli komut (`.codex/TOOL-PATHS.local.properties`: MAVEN_CMD, MAVEN_SETTINGS, MAVEN_REPO_LOCAL=.m2/repository, JAVA_HOME=jdk8u432, offline):

```powershell
& "C:/javatools/maven/apache-maven-3.9.15/bin/mvn.cmd" -o -s "C:/Users/GLB90114534/.m2/settings.xml" `
  -Dmaven.repo.local="C:/Users/GLB90114534/.m2/repository" `
  -Dtest=CampaignDbResultPersistDeadlockRetryRegressionTest,ProcessCallResultPersistFirstRegressionTest,RoundRobinNextNumberTest,RoundRobinNextCycleTest,RoundRobinPerCycleTest,RetryLimitTest,CallProducerTest test
```

(`JAVA_HOME` komut öncesi `jdk8u432` set edilir. Gerçek DB bağlantısı kurulmaz; `DBEngine` mock'lanır.)

## Kapanış Kriteri

- [ ] Açık sorular cevaplandı veya `Yok` olarak işaretlendi.
- [ ] Fresh/retry sınıflama tutarlılığı ve geçersiz-durum engellemesi testle kanıtlandı.
- [ ] Planlanan testler çalıştırıldı ve başarılı oldu.
- [ ] Kod değişikliği DB/Redis/AMI/result-code contract adı ve campaign state transition değiştirmeden tamamlandı.
- [ ] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

(Uygulama tamamlandığında doldurulacak.)
