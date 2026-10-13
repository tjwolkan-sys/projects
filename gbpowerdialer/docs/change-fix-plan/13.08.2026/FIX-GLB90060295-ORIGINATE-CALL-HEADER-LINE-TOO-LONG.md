# FIX Originate AMI Call Header Line Too Long Change/Fix Planı

## Bağlam

Turkcell (`gbWebphone_Turkcell`) 12.08.2026'da iki şikayet iletti: (1) `pd_cdr`'da `telephony_rc = 79` kayıtları için **çift satır**, (2) bu kayıtlarda `outgoing_call_id` boş ve **çağrı makinalarda bulunmuyor**. Etkilenen kampanyalar `85 TC_BIREYSEL_TAHSILAT_TFS_INTERTECH` ve `74` (`campaign_type = 5` PREDICTIVE, `outdialing_type_id = 3` SCHEDULED_RETRY).

Canlı `pd_telephony_rc` dump'ı ile doğrulandı: `pd_cdr.telephony_rc` kolonu **id** tutar ve `id 79 = code 907 = "Originate action error"`. Yani bu kayıtlarda Asterisk, dialer'ın gönderdiği Originate action'ını **reddetmiştir**; kanal hiç açılmamıştır. `outgoing_call_id` boşluğu bunun doğal sonucudur (kolonu dialer zaten hiç yazmıyor).

Günlük trend (camp 85, `telephony_rc = 79`):

| gün | satır | tekil çağrı | fazla satır |
| --- | --- | --- | --- |
| 27.07 | 2 | 2 | 0 |
| 28.07 | 2 | 2 | 0 |
| 04.08 | 15 | 11 | 4 |
| 05.08 | 35 | 26 | 9 |
| 07.08 | 47 | 29 | 18 |
| 10.08 | 693 | 454 | 239 |
| 11.08 | 822 | 539 | 283 |
| 12.08 | 2594 | 1818 | 776 |

Kibana (`turkcell-2026.08.12`, `dl-0506af54-d7bd-4289-b470-09e6a6f10889`) tam zinciri verdi:

```text
15:23:47.153  [lsc-executor-10.1.2.3-1022]        LSCEngine - LSC(s) result is OK
15:23:47.154  [lsc-executor-10.1.2.3-1022]        Campaign - Call in progress added
15:23:47.154  [Asterisk-Java ManagerConnection-31-Reader-0]
              ManagerError: actionId='dl-0506af54-…'; message='Failed to parse message: line too long'; response='Error'
15:23:47.155  [ami-event-worker-10.1.2.3-0]       Origination failed for call dl-0506af54-…
15:23:47.194  [lsc-executor-10.1.2.3-1022]        CDRService - CDR{…} is saved/updated successfully   <- INSERT #1
15:23:47.210  [ami-event-worker-10.1.2.3-0]       CDRService - CDR{…} is saved/updated successfully   <- INSERT #2
15:23:47.248  [ami-event-worker-10.1.2.3-0]       RetryStrategy - Retry scheduled
```

`"Failed to parse message: line too long"` metni `asterisk-java-2.0.3.gb.jar` içinde **yoktur** (tüm class dosyaları tarandı); mesaj Asterisk'ten gelir ve AMI oturumunun satır tamponunun (1024 bayt) aşıldığını bildirir.

Bu plan **yalnız F1**'i kapsar: Originate action'a sızan `call:` başlığının kaldırılması. Çift `pd_cdr` satırına yol açan `saveInitialCDR` ∥ `persistFinalCdr` yarışı (F2) ve `retryHistory` büyümesi / TRC 907'nin attempt muafiyeti (F3) developer kararıyla **ayrı işler** olarak ele alınacaktır.

## İş Etiketi

fix

## Öncelik

hotfix

Üretimde aktif ve büyüyen bir arıza: 12.08'de tek kampanyada 1818 çağrı hiç kurulamadı, etkilenen kontaklar eşiği aştıktan sonra **kalıcı olarak aranamaz** hale geliyor ve her denemede 2 `pd_cdr` satırı yazılıyor. Trend 4 Ağustos'tan bu yana ~170 kat arttı.

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/actions/AbstractOriginateAction.java` — `getCall()` erişim belirleyicisi (tek satır).
- `gbpowerdialer/src/test/java/tests/ami/OriginateActionAmiLineLengthRegressionTest.java` — yeni regression testi.

Bu planda **değişmeyecek** alanlar:

- `AbstractOriginateAction.setInstanceVariables()` içindeki hiçbir `setVariable(...)` çağrısı; dialplan/AGI'nin okuduğu tüm kanal değişkenleri (`ORIGINID`, `PARENTID`, `CONTACTID`, `CAMPAIGNID`, `DIALED_NUMBER`, `AGENT_*` …) aynen kalır.
- `ManagerActionFactory` ve kampanya tipi bazlı action alt sınıflarının `build(...)` davranışı.
- `ManagerConnectionDecorator.originateCall` / `handleOriginationFail` / `processCallResult` akışı.
- `Call.toString()` (log çıktısı olarak kalır; yalnız AMI'ye gitmemesi sağlanır).
- Redis preview originate yolu (`OriginateActionDataContainer` / `OriginateActionDataContainerSerializer` ayrı sınıflardır, kendi `getCall()`'ları vardır).

## Mevcut Davranış

1. `CallOriginator.processOrigination` → `ManagerConnectionDecorator.originateCall` → `ManagerActionFactory.generateCall(call)` kampanya tipine göre `AbstractOriginateAction` alt sınıfını üretir.
2. `AbstractOriginateAction` (2018'den beri) `Call` referansını taşır ve `public Call getCall()` ile alt sınıflara açar. Alt sınıflar bunu action alanlarını kurmak için kullanır (`ProgressiveReleasedAction:24 setExten(getCall().getUser().getUsername())` vb.).
3. `commandConnection.sendAction(...)` → `ManagerWriterImpl.sendAction` → `ActionBuilderImpl.buildAction(action)` action'ı AMI metnine çevirir.
4. `buildAction`, `ReflectionUtil.getGetters(action.getClass())` ile **tüm public, parametresiz, adı `get`/`is` ile başlayan** metotları toplar ve `appendGetters` her birini bir AMI başlığı yapar. Atlanan üyeler yalnızca `{class, action, actionid, attributes}`.
5. Sonuç: her Originate'e `call: Call [ … ]` satırı eklenir. `Call.toString()` içinde `retryHistory` de bulunur ve bu liste her retry'de bir numara daha büyür.
6. `retryHistory`, `campaign.getColResultRetryHistory()` kolonundan `;` ayraçlı okunur (`Call.java:669`) ve DB'ye geri yazılır; yani **günler boyunca birikir**.
7. Satır 1024 baytı aşınca Asterisk `Response: Error / Message: 'Failed to parse message: line too long'` döner → `SendActionCallbackWrapper.onResponse` `ManagerError` görür → `eventWorker` → `handleOriginationFail` → TRC 907 (id 79) → `processCallResult`.
8. TRC 907 `Constants.TELEPHONY_RESULT_CODES_EXCLUDED_FROM_ATTEMPTS` içinde olduğu için `Call.restoreAttemptsBeforeOriginationForNonAttemptResult()` attempt sayacını geri alır; `updateRetryHistory()` ise geri alınmaz. Kontak yeniden retry'ye girer, `retryHistory` +1 büyür, satır +13 bayt uzar ve bir sonraki deneme de kesin olarak reddedilir.

Canlı log'dan ölçüm (`dl-0506af54-…`):

| ölçüm | bayt |
| --- | --- |
| `Call.toString()` | 1258 |
| `"call: "` + üstü = tek AMI satırı | **1264** |
| Asterisk satır limiti | **1024** |
| `retryHistory` dışı sabit kısım | 785 |
| `retryHistory` (37 kayıt × ~13 bayt) | 479 |

Eşik: sabit kısım 785 bayt olduğunda **~18-19 retryHistory kaydı**. Sabit kısım `user=User[id, username, firstname, lastname, status]` ve `campaign=Campaign[id, name]` içerdiği için agent/kampanya adı uzunluğuna göre birkaç kayıt oynar; aynı kontak kısa isimli agent'la geçip uzun isimliyle reddedilebilir.

## Beklenen Davranış

1. Originate action'ın AMI temsilinde `call:` başlığı **hiç üretilmez**.
2. Gönderilen AMI satırlarının uzunluğu çağrı verisiyle (retry geçmişi, DNC listesi, agent/kampanya adı, çağrı state'i) **ölçeklenmez**; `retryHistory` ne kadar uzun olursa olsun originate reddedilmez.
3. Dialplan/AGI'nin ihtiyaç duyduğu kanal değişkenleri aynen gönderilmeye devam eder.
4. Alt sınıflar `getCall()`'ı kullanmaya devam eder; kampanya tipi bazlı action kurulumu değişmez.

## Kod Okuma Bulguları

`asterisk-java-2.0.3.gb.jar` bytecode'u (javap) üzerinden doğrulandı:

- `ReflectionUtil.getGetters(Class)`: `clazz.getMethods()` (**yalnız public**) → ad `get`/`is` ile başlamıyorsa atlanır → parametreli metot atlanır → kalanlar `toLowerCase()` anahtarla map'e konur. Yani `getCall()` üç koşulu da sağladığı için "call" anahtarıyla haritaya giriyor.
- `ActionBuilderImpl` ctor: `membersToIgnore = {"class", "action", "actionid", "attributes"}` — "call" listede yok.
- `ActionBuilderImpl.appendGetters`: `membersToIgnore.contains(name)` → atla; `value == null` → atla; `value instanceof Class` → atla; `value instanceof Map` → `appendMap`; `value instanceof String` → `appendString`; **aksi hâlde `appendString(sb, name, value.toString())`**. `Call` bu son dala düşüyor.
- `ActionBuilderImpl.appendMap`: `targetVersion.isAtLeast(ASTERISK_1_2)` ise `appendMap12` (girdi başına ayrı satır), değilse `appendMap10` (tek satır, `|` ile birleşik). `targetVersion` **null ise NPE** — testte `setTargetVersion(...)` çağrılmalı. Üretimde Asterisk ≥ 1.2 olduğu için `variable:` satırları girdi başına ayrıdır ve kısadır; tek uzun satır `call:`'dır.
- `ManagerWriterImpl.sendAction` **synchronized**; `ActionBuilderImpl` yalnız yerel `StringBuilder` kullanır (instance field'ları `logger`, `targetVersion`, `membersToIgnore`). Dolayısıyla eşzamanlı yazımdan kaynaklanan stream bozulması hipotezi **elendi**; uzun satır tek bir action'ın kendi içeriğidir.
- Jar içinde `"line too long"` string'i yok → hata Asterisk kaynaklı.
- `AbstractOriginateAction` dışında AMI action hiyerarşisinde fazladan getter **yoktur** (`OriginateActionDataContainer` AMI action değildir, Redis preview yolunda kullanılır).
- `AbstractOriginateAction.getCall()` çağıranlarının tamamı aynı pakettedir: `OutboundIVRAction:24`, `PreviewFreeAction:26,28,29,30,31`, `ProgressiveReleasedAction:24`, `ProgressiveReservedAction:24`. Paket dışından çağıran yoktur; `OriginateActionSerializer` `getCall()` kullanmaz.
- `Constants.MANAGER_ACTION_VARIABLE` listesi 2022'den beri değişmemiş; `ATTACHED_DATA` Originate'e set edilmiyor (attached data Redis'e gidiyor, `ManagerConnectionDecorator.sendAttachedDataToRedis`).
- AGI tarafı çağrıyı yalnız kanal değişkeninden tanır: `DialWithAbandonTimeoutScript:109 channel.getVariable(ORIGIN_ID)`. `call` başlığını okuyan hiçbir yer yok; Asterisk `Variable:` dışındaki bilinmeyen başlıkları kanal değişkenine çevirmez, sessizce yoksayar.

## Root Cause / Değişiklik Gerekçesi

asterisk-java'nın action serileştirmesi **sabit alan listesi değil, refleksiyon** tabanlıdır ve örtük sözleşmesi şudur: *action sınıfları saf DTO'dur; public getter'ları AMI parametreleriyle bire bir eşleşir.* `AbstractOriginateAction.getCall()` iç plumbing amacıyla eklenmiş, fakat public getter olduğu için kütüphane açısından "gönderilecek alan" hâline gelmiştir. Kimse bu veriyi göndermeyi tasarlamadı; davranış kolaylık getter'ı ile kütüphane refleksiyonunun çarpışmasından doğdu.

Asterisk bilinmeyen başlığı yoksaydığı için hata yıllarca belirti üretmedi. `retryHistory` biriktikçe satır 1024 baytı aşınca latent defect üretim arızasına dönüştü. Düzeltme, kütüphanenin sözleşmesini geri kurar: getter iç kullanımda kalır, refleksiyonun görüş alanından çıkar.

## Riskler

- **Alt sınıf erişimi:** `getCall()` `protected` yapılırsa yalnız aynı paket/alt sınıflar erişebilir. Tüm çağıranların aynı pakette olduğu doğrulandı; yine de derleme hedefli test koşusuyla kanıtlanacak.
- **Gönderilen alan kaybı:** Yanlışlıkla başka bir başlığın da düşmesi. Test, fix sonrası zorunlu kanal değişkenlerinin (`ORIGINID`, `PARENTID`, `CONTACTID`, `CAMPAIGNID`, `DIALED_NUMBER`) hâlâ üretildiğini açıkça doğrulayacak.
- **Serileştirme modu farkı:** `appendMap10` (Asterisk < 1.2) modunda `variable:` tek satırdır. Üretimde geçerli değil; yine de test invariant'ı her iki modda da kontrol edilecek.
- **Kapsam yanılgısı:** Bu fix çift `pd_cdr` satırının **yarışını** çözmez; yalnız 79 üreten tetikleyiciyi ortadan kaldırdığı için pratikte çift satır akışını durdurur. 10/78 ailesindeki yarış F2'ye kadar açık kalır. Planın kapsamı bilinçli olarak dardır.
- **Geri dönüşü olmayan veri:** Bu fix, sahte denemelerle retry bütçesi tüketilmiş kontakları geri getirmez (F3 / veri operasyonu konusu).

## TDD Stratejisi

Yeni test: `gbpowerdialer/src/test/java/tests/ami/OriginateActionAmiLineLengthRegressionTest` (JUnit4).

1. **(kırmızı) Satır uzunluğu invariant'ı:** 40 kayıtlık `retryHistory`, gerçekçi agent/kampanya adları içeren `Call` ile `ManagerActionFactory.generateCall(...)` çağrılır; üretilen action, gerçek `ActionBuilderImpl` üzerinden (`Class.forName` + `setAccessible` ile paket-private ctor) AMI metnine çevrilir ve **hiçbir satırın 1023 baytı aşmadığı** doğrulanır. Fix öncesi kırmızı (ölçülen 1264 bayt), sonrası yeşil. `setTargetVersion` hem `ASTERISK_13` hem `ASTERISK_1_0` için koşulur.
2. **(kırmızı) Mekanizma testi:** `ReflectionUtil.getGetters(<action sınıfı>)` çıktısı `"call"` anahtarını **içermemeli** — beş action tipi için (progressive released/reserved, preview free/straight, outbound IVR).
3. **(yeşil kalmalı) Kayıpsızlık testi:** Fix sonrası üretilen AMI metni `ORIGINID`, `PARENTID`, `CONTACTID`, `CAMPAIGNID`, `DIALED_NUMBER` değişkenlerini ve `Channel` / `Context` / `Exten` alanlarını hâlâ içermeli. Bu test fix öncesi de yeşildir; amacı over-removal'ı engellemektir.
4. **Mevcut regression'lar kırmızıya düşmemeli:** `ManagerConnectionDecoratorFctRoutingHangupTest`, `AmiCommandConnectionSplitRegressionTest`, `InitialCdrOriginationRegressionTest`, `AmiEventDispatchCharacterizationRegressionTest`.

## Açık Sorular

- [x] **#1 Hedef branch / backport:** **Sadece develop** (2026-08-13, developer kararı). Fix `fix/GLB90060295-originate-call-header-line-too-long` branch'inde kalır; release dalına cherry-pick hazırlanmaz, merge/release yönetimi developer'dadır.

Karara bağlanmış, soru olarak açılmayan noktalar:

- **Uygulama biçimi:** `public Call getCall()` → `protected Call getCall()`. Yeniden adlandırma (`originatingCall()`) da aynı sonucu verir ancak 8 çağrı yerini değiştirir; `protected` en dar diff olduğu için tercih edildi.
- **F2 ve F3 kapsam dışı:** developer kararıyla ayrı işler.

## Execution Plan

- [x] Onaylanan planın tüm adımları bu dosyaya işlendi.
- [x] Açık soru #1 cevaplandı ve plana işlendi.
- [x] Mevcut davranışı kilitleyen regression testi yazıldı (`shouldStillSendDialplanVariablesAndChannelFields` — fix öncesi yeşil, over-removal koruması).
- [x] Beklenen davranışı kanıtlayan başarısız testler yazıldı. RED: `shouldKeepEveryAmiLineWithinAsteriskLimitWhenRetryHistoryIsLong` **1272 bayt** (sınır 1023, ihlal eden satır `call: Call [object=…]`) ve `shouldNotExposeCallAsAmiSerializableGetter`. Koşu: `Tests run: 3, Failures: 2`.
- [x] En küçük üretim kodu değişikliği yapıldı (`AbstractOriginateAction.getCall()` → `protected`; simetri için kullanılmayan `setCall` da `protected`).
- [x] Hedefli testler yeşile çevrildi (17/17).
- [x] Gerekliyse küçük refactor yapıldı ve testler tekrar çalıştırıldı (ek refactor gerekmedi).
- [x] Doküman ve history güncellemesi tamamlandı.

## Performans Etkisi

- **Hot path durumu:** Evet — `originateCall` her dial denemesinde çalışır. Değişiklik hot path'e maliyet **eklemez, kaldırır**.
- **Call/event/tick başına ek maliyet:** Originate başına bir refleksiyon `Method.invoke` + `Call.toString()` çağrısı ve ~1,2 KB'lık String allocation'ı ortadan kalkar. Yeni IO, lock, query veya scan yok.
- **IO/scan/lock/serialization/log etkisi:** AMI socket'ine yazılan bayt sayısı originate başına ~1,2 KB azalır; Asterisk tarafında satır parse maliyeti düşer. Log davranışı değişmez (`Call.toString()` log satırlarında aynen kalır).
- **Azaltım veya ölçüm notu:** Deploy sonrası Kibana'da `"line too long"` sayacı 0'a inmeli; günlük TRC 79 sayısı baseline'a (~0-2/gün) dönmeli.

## Doğrulama

`.codex/TOOL-PATHS.local.properties` okunduktan sonra offline hedefli test:

```text
mvn -o -s <settings> -Dmaven.repo.local=<repo> -f gbpowerdialer/pom.xml -Dtest=OriginateActionAmiLineLengthRegressionTest,ManagerConnectionDecoratorFctRoutingHangupTest,AmiCommandConnectionSplitRegressionTest,InitialCdrOriginationRegressionTest,AmiEventDispatchCharacterizationRegressionTest test
```

Deploy sonrası canlı doğrulama imzaları:

- Kibana `message:"line too long"` → boş.
- `SELECT CAST(call_dial_time AS DATE), COUNT(*), COUNT(DISTINCT interaction_id) FROM pd_cdr WHERE campaign_id = 85 AND telephony_rc = 79 GROUP BY …` → günlük satır sayısı baseline'a dönmeli.
- Uzun `retry_history` taşıyan kontakların artık gerçekten aranabildiği (`call_answer_time` dolu veya gerçek ITU cause'lu TRC) doğrulanmalı.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı veya `Yok` olarak işaretlendi.
- [x] Planlanan testler çalıştırıldı ve başarılı oldu.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Uygulama tamamlandı (2026-08-13). Branch: `fix/GLB90060295-originate-call-header-line-too-long`, base `36cb076d` (develop, "Merged PR 24984: grid-filtre-kolon-state-kaybi"). Açık soru #1 kararı gereği release dalına cherry-pick hazırlanmadı; merge/release yönetimi developer'da.

Üretim kodu değişikliği (tek dosya):

- `engine/asterisk/actions/AbstractOriginateAction.java`: `getCall()` ve (kullanılmayan) `setCall(...)` `public` → `protected`. Erişim paket içinde kaldığı için dört action alt sınıfı (`OutboundIVRAction`, `PreviewFreeAction`, `ProgressiveReleasedAction`, `ProgressiveReservedAction`) etkilenmedi; `mvn test` tüm main + test kaynaklarını derlediği ve BUILD SUCCESS verdiği için paket dışı çağıran olmadığı derleyiciyle de kanıtlandı. Getter'ın neden public olmaması gerektiği sınıfın içine Javadoc olarak yazıldı ki ileride "eksik görünüp" geri açılmasın.

Test değişikliği:

- Yeni: `tests/ami/OriginateActionAmiLineLengthRegressionTest` (4 test). Gerçek `ActionBuilderImpl` refleksiyonla örneklenip (`Class.forName` + `setAccessible`; sınıf paket-private) action AMI metnine çevrilir, satırlar `\r\n` ile bölünür ve her satırın **UTF-8 bayt** uzunluğu ölçülür (Türkçe karakterler 2 bayt olduğu için karakter değil bayt sayılır). Invariant hem `ASTERISK_13` (girdi başına ayrı `variable:` satırı) hem `ASTERISK_1_0` (tek satır, `|` ile birleşik) serileştirme modunda ve dört action tipinde koşar. Dördüncü test (`fixtureShouldStayWorstCaseSoThatLineLimitAssertionIsNotVacuous`) invariant'ın boşa dönmesini engeller: fixture küçültülürse (ör. `RETRY_HISTORY_SIZE` düşerse) kırmızıya döner. Fixture üretim vakasının **uzunluk profilini** taşır fakat AGENTS gereği gerçek müşteri/agent verisi içermez; telefon, contact id, agent ad/soyad/username, kampanya adı ve interaction id aynı karakter uzunluğunda placeholder'larla kurulur (40 kayıtlık iki numaralı rotasyonlu `retryHistory` dâhil).

Doğrulama:

```text
mvn -o -s <settings> -Dmaven.repo.local=<repo> -f gbpowerdialer/pom.xml -Dtest=OriginateActionAmiLineLengthRegressionTest,ManagerConnectionDecoratorFctRoutingHangupTest,AmiCommandConnectionSplitRegressionTest,InitialCdrOriginationRegressionTest,AmiEventDispatchCharacterizationRegressionTest test
```

- RED (fix öncesi): `Tests run: 3, Failures: 2` — satır uzunluğu **1272 bayt** (sınır 1023), ihlal eden satır `call: Call [object=com.powerdialer.model.Call@…, id=dl-0506af54-…]`; getter testi de kırmızı. Kayıpsızlık testi fix öncesi **yeşil** (over-removal koruması amacına uygun).
- GREEN (fix sonrası): hedefli 5 sınıf **18/18**, `BUILD SUCCESS` (fixture placeholder'a çevrildikten sonra tekrar koşuldu; non-vacuity guard'ı fixture'ın hâlâ limiti aşacak büyüklükte olduğunu doğruluyor).
- Geniş koşu `-Dtest='tests.**'`: **435 test, 1 failure + 1 error** — ikisi de pre-existing baseline (`LogbackAsyncAppenderConfigContractTest.rootLoggerWritesThroughAsyncAppendersOnly`, PR 24843 konsol appender çıkarma işinden kalan güncellenmemiş contract testi; `UnmappedHangupCauseFallbackRegressionTest.shouldFallbackToExpiredClearingWhenHangupCauseIsNotNumeric`, `ManagerConnectionDecorator` ham `Integer.parseInt` defekti). Bu iki hata 06.08.2026 kaydındaki baseline ile birebir aynı; yeni kırık yok.
- Offline Maven 3.9.14 + JDK 1.8.0_202, `.codex/TOOL-PATHS.local.properties` üzerinden.

Deploy sonrası izlenecekler: Kibana `message:"line too long"` sayacı 0'a inmeli; camp 85/74 için günlük `telephony_rc = 79` satır sayısı baseline'a (~0-2/gün) dönmeli; uzun `retry_history` taşıyan kontakların gerçekten aranabildiği doğrulanmalı.

Kapsam dışı kalanlar (developer kararıyla ayrı iş): F2 = `saveInitialCDR` ∥ `persistFinalCdr` transient-CDR insert yarışı (10/78 ailesinde çift satır üretmeye devam eder); F3 = `retryHistory` büyümesi ve TRC 907'nin attempt muafiyeti; sahte denemelerle retry bütçesi tükenmiş kontakların geri kazanılması (veri operasyonu); mevcut çift `pd_cdr` satırlarının temizliği.
