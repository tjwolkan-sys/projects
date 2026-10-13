# FIX-317277 Completion Percent Filtre Ayrımı

- İş Etiketi: fix
- Kimlik: 317277
- Slug: completion-percent-filtre-ayrimi
- Tarih: 2026-05-05
- Öncelik: normal
- Plan Durumu: Faz 1 canlıda; Faz 2 uygulandı; Faz 2 simetri düzeltmesi eklendi (2026-07-01), doğrulama beklemede

## Bağlam

Kampanya grid'inde gösterilen `completionPercent` değeri, kayıt elenmesi (`is_disabled = 1`), attempt limiti dolması veya sadece pasifleştirme gibi durumlarda yüzdeyi yukarı çekmiyor. Saha gözlemine göre bazı kampanyalarda yüzde **%0'da kalıyor**: yapılan kayıt eleme/DNC/pasifleştirme işlemleri `complete_date` set etmediği için "tamamlanmış iş" sayımına dahil değil, dolayısıyla `listRemaining = actualListSize - completed` formülü kalan işi şişiriyor.

Hedef: payda (`actualListSize`) sabit kalsın; pay (`listRemaining`) yalnız "şu an aranabilir" kayıtları yansıtacak şekilde, completed sayımını DNC/elenmiş/limit dolmuş/operatörde olan kayıtları da kapsayacak şekilde genişleterek formülün davranışı düzeltilsin.

## Mevcut Davranış

- `Campaign.java:1769-1773` (`getCompletionPercent`): formül `floor(100 - (listRemainingCurrent * 100 / actualListSize))`. Formül kendi içinde doğru.
- `CallProducer.java:760-765` (`calculateCampaignParameters`): hot path tick başına aşağıdaki DAO çağrılarını yapar.
  - `countActualListSize(campaign)` → payda. Filtreler: yalnız `createWhereFromFilters` (kullanıcı iş kriterleri). `is_disabled` veya `complete_date` filtresi **yok**. Bölüm 2.A ile uyumlu.
  - `calculateListRemaining()` (CallProducer.java:794-795) = `actualListSize - countCompletedCalls(campaign)`.
- `CampaignDbOperations.java:1568-1581` (`countCompletedCalls`):
  ```
  SELECT COUNT(*) FROM <result_table>
  WHERE result.<colResultCompleteDate> IS NOT NULL
  [AND <user_filters>]
  ```
  Yalnız `complete_date IS NOT NULL` olanları sayar. `is_disabled = 1`, `attempts >= max`, `is_fetched IS NOT NULL` durumları "completed" olarak görünmez.
- Sabitler ve kolonlar:
  - `is_disabled` sabit kolon adı: `Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.DISABLED("is_disabled")`, disabled değeri `Constants.DISABLED = 1`.
  - Attempts kolonu campaign-configurable: `campaign.getColResultAttempts()`.
  - Fetched flag kolonu campaign-configurable: `campaign.getColResultFetchedDataFlag()`.
  - Max attempts: Java-side `campaign.getRetryOveralAttempt()` (`ReachRetryPerNumber.java:19`'da `call.getAttempts() >= campaign.getRetryOveralAttempt()` karşılaştırması var, fakat bu yalnız `getCountAllCallTypes()` true iken). Diğer retry stratejilerinde max threshold farklı olabilir.

## Beklenen Davranış

`countCompletedCalls` SQL'i, "şu an aranabilir olmayan" tüm kayıtları completed sayacak şekilde genişletilir. Pseudocode:

```
WHERE (
    result.<colResultCompleteDate> IS NOT NULL
    OR result.<is_disabled, escape if Oracle/PostgreSQL> = 1
    OR result.<colResultFetchedDataFlag> IS NOT NULL
    [OR result.<colResultAttempts> >= :maxAttempts]   -- yalnız countAllCallTypes=true VE getRetryOveralAttempt()>0 ise eklenir
)
[AND <user_filters>]
```

Multi-DB notu: `is_disabled` sabit kolon adı statik enum üzerinden alındığı için `@EscapeSqlNames` aspect Campaign proxy'sine takılmaz; Oracle ve PostgreSQL case-sensitive identifier davranışı için `shouldEscapeNames(dbType)` true ise `DISABLED.getValue(true)` çağrısıyla `"is_disabled"` olarak çift tırnaklı yazılır. MSSQL/MySQL için tırnaksız raw kullanılır. Diğer kolonlar Campaign üzerinden geldiği için aspect tarafından zaten escape edilir.

Bu sayede:
- DNC/eleme: `is_disabled = 1` → completed sayılır → kalan iş azalır → yüzde artar.
- Attempt limiti dolması: yalnız `countAllCallTypes=true` ve `getRetryOveralAttempt()>0` ise `attempts >= max` → completed sayılır → yüzde artar. Diğer retry stratejilerinde bu koşul SQL'e eklenmez (soru 1, kullanıcı cevabı: 1a).
- Operatörde/queue'da bekleyen kayıt: `is_fetched IS NOT NULL` → kalan iş sayılmaz, completed sayılır (kullanıcı teyidi: "operatörde/queue'da bekliyor; kalan iş olarak sayılmamalı"). Operatör fetch döngüsüyle yüzde dalgalanır; bu davranış kabul edildi.
- Randevu sıfırlama: Tüm bayraklar temizlenirse kayıt yeniden completed dışına çıkar → yüzde düşer.

`actualListSize` (payda) ve formül koduna dokunulmaz. Backfill yapılmaz; ileri tarihli kampanya tick'lerinden itibaren yeni davranış geçerli (soru 4 cevabı: kod değişikliği yeterli).

## Etkilenen Kod Alanları

- `gbpowerdialer/src/main/java/com/powerdialer/engine/db/CampaignDbOperations.java` — `countCompletedCalls` metodu (yalnız bu metot değişir).
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CallProducer.java` — yalnız okuyucu, davranış değişimi metot çağrıları üzerinden alınır; kod değişmez.
- Test: `gbpowerdialer/src/test/java/tests/<domain>` altında yeni test sınıfı.

`Campaign.java`, `CampaignListDTO.java`, `CampaignMemento*`, `campaignCtrl.js`, localization dosyaları **değişmez** — semantik değişiklik formül seviyesinde değil DAO seviyesinde tutuluyor; `listRemaining` ve `actualListSize` alan anlamı aynı kalıyor (frontend'de görünüm değişmiyor).

## Riskler

- **Hot path performansı**: `countCompletedCalls` campaign tick'inde çağrılır. Yeni `OR` zinciri index kullanımını bozarsa büyük result tablolarında full scan riski. Etki: per-tick latency artışı, predictive boost ve scheduler'a yansır. Mitigation: yeni filtre setinin müşteri ortamlarında index uyumluluğunun planlamada doğrulanması, gerekirse `OR` yerine `UNION ALL`/alt sorgu opsiyonu değerlendirilmesi.
- **`getRetryOveralAttempt()` semantiği**: Bu değer yalnız `countAllCallTypes=true` retry stratejisinde meaningful. Diğer durumlarda (`ReachRetryPerType` vb.) max threshold farklı olabilir veya kavramsal olarak yok olabilir. Yanlış uygulanırsa kayıt prematüre completed sayılır.
- **Null/disabled column yokluğu**: Bazı eski kampanya schema'larında `is_disabled` veya attempts kolonu olmayabilir. SQL hatasına dönmemesi için defensive null/exists kontrolü gerekebilir.
- **`is_fetched` dalgalanması**: Operatör fetch döngüsü tick başına yüzde oynamasına yol açar; kullanıcı kabul etti ama UI/raporlama tarafında geri dönüş gelirse plan revize edilebilir.
- **Geçmiş veri (CampaignMemento) tutarsızlığı**: Eski snapshot'lar eski formüle göre yazılmış, yeni snapshot'lar yeni formüle göre yazılacak. Geçmiş raporlar ile yeni raporlar arasında discontinuity olur — soru 5'te kullanıcı kabul etti.

## Performans Etkisi

`countCompletedCalls` campaign producer tick'inde çağrıldığı için `gbpd-call-flow-performance-guardrails` kapsamında değerlendirildi:
- Eklenen WHERE ifadesi 4 OR koşulu içerir; üçü kolon-equality / IS NOT NULL kontrolüdür, biri parametrize edilmiş `>=` karşılaştırmasıdır.
- Yeni IO veya network çağrısı eklenmez, allocation aynı, log etkisi yok.
- Müşteri DB index'lerinin (`is_disabled`, `complete_date`, `attempts`, `is_fetched`) bu OR kompozisyonuna uygun olup olmadığı plan uygulanmadan önce DBA tarafından doğrulanmalı veya plan içinde alternatif sorgu yapısı değerlendirilmeli.
- Tick frekansı düşük (saniyede tek hane) → mutlak overhead küçük, ancak büyük kampanya tablolarında query plan değişimi kümülatif olabilir.

## TDD Stratejisi

Yeni test dosyası: `gbpowerdialer/src/test/java/tests/campaign/db/CountCompletedCallsFilterExpansionTest.java` (mevcut yapıda `tests/<domain>` standardına uygun, AGENTS.md satır 139).

Test senaryoları (DAO seviyesi, dbEngine mock'lanır; SQL string assertion):
- Yalnız `complete_date` set olan kayıt → completed sayılır (regresyon, eski davranış korunur).
- Yalnız `is_disabled = 1` kayıt → completed sayılır.
- Yalnız `is_fetched IS NOT NULL` kayıt → completed sayılır.
- `countAllCallTypes=true` ve `getRetryOveralAttempt()>0` ise SQL `attempts >= ?` koşulunu içerir; max threshold parametre olarak verilir.
- `countAllCallTypes=false` ise SQL'de attempts koşulu **bulunmaz**.
- `getRetryOveralAttempt()` null veya 0 ise SQL'de attempts koşulu bulunmaz.
- `createWhereFromFilters` boş → yeni `WHERE (...)` doğru başlar, fazladan AND yok.
- Oracle connection string → `is_disabled` kolonu `"is_disabled"` olarak escape edilir.
- PostgreSQL connection string → `is_disabled` kolonu `"is_disabled"` olarak escape edilir.
- MSSQL connection string → `is_disabled` raw (tırnaksız) yazılır.

`Campaign.getCompletionPercent` formülü değişmediği için ayrı unit test eklenmez (kapsam dışı).

## Uygulama Kontrol Listesi

- [x] Açık sorular cevaplanmadan kod yazılmaz. (Tüm sorular kullanıcı tarafından kapatıldı.)
- [x] Test sınıfı oluşturulur. (`gbpowerdialer/src/test/java/tests/campaign/db/CountCompletedCallsFilterExpansionTest.java` — 11 senaryo: 8 davranış + 3 multi-DB escape kontrolü.)
- [x] `CampaignDbOperations.countCompletedCalls` SQL'i genişletildi.
- [ ] Yeni testler yeşile döndürülür (`mvn -o -Dtest=CountCompletedCallsFilterExpansionTest test`). **BLOCKER**: offline Maven cache içinde `org.apache.maven.surefire:surefire-junit4:jar:3.1.2` artifact'ı yok; surefire provider başlatılamadığı için test runtime'ı çalışamıyor. Test sınıfı ve main kod offline `test-compile` ile temiz derlendi (760 main + 99 test source). Geliştirici VPN'de cache'i güncellediğinde aynı offline komut yeniden denenecek.
- [ ] Mevcut `CampaignResultJobServiceTest` ve ilgili regression testleri kontrol edilir. (Aynı surefire blocker.)
- [x] `CallProducer` davranışı değişmediği halde `gbpd-call-flow-performance-guardrails` kontrol listesi gözden geçirildi: yeni IO yok, yeni network yok, yeni allocation/log yok; SQL string oluşumuna 3-4 OR koşulu eklendi. Tick frekansı düşük olduğundan mutlak overhead küçük; index riski açıkça kabul edildi (Açık Sorular #2).
- [x] Plan dosyasının "Plan Durumu" alanı güncellendi.
- [x] History kaydı `docs/history/05.05.2026.md` dosyasına append-only eklendi.

## Hedefli Doğrulama Komutları

- `mvn -o -Dtest=CountCompletedCallsFilterExpansionTest test`
- (regresyon) `mvn -o -Dtest=CampaignResultJobServiceTest test`
- (kapsamlı regresyon, gerekirse) `mvn -o -Dtest=CampaignDb*Test test`

`.codex/TOOL-PATHS.local.properties` öncelikli kullanılır; eksik artifact varsa offline modda hata raporlanır, indirme denenmez.

## Kapanış Kriteri

- Yeni testler ve mevcut hedefli regresyon testleri yeşil.
- `countCompletedCalls` SQL'i 4 yeni koşulu kapsıyor ve `createWhereFromFilters` boş/dolu durumlarında doğru çalışıyor.
- Plan açık sorularının tamamı kapanmış.
- Performans riski plan içinde değerlendirilmiş, gerekiyorsa DBA onayı alınmış.

## Açık Sorular

Tüm açık sorular kapatıldı (2026-05-05).

1. **Max attempts SQL ifadesi** — Cevap: **(a)** Yalnız `countAllCallTypes=true` ve `getRetryOveralAttempt() > 0` ise OR koşulu SQL'e eklenir; aksi halde attempts koşulu SQL'e dahil edilmez.
2. **DB index uyumluluğu** — Cevap: Index riski kabul edildi. DBA onayı beklenmiyor; gerekirse ileri tarihte performans iyileştirmesi ayrı bir iş olarak ele alınır.
3. **Eski schema güvenliği** — Cevap: `is_disabled` kolonu mevcut schema'da var. Defensive `if column exists` kontrolü gerekmez; SQL koşulsuz inşa edilir.
4. **CampaignMemento snapshot discontinuity** — Cevap: Sadece kod değişikliği yeterli; raporlama tarafına ayrı bildirim yapılmaz.

---

## Faz 2: Payda Statü Filtre Ayrımı (2026-06-29)

### Tetikleyici

Faz 1 canlıya alındıktan sonra müşteri saha testi (TURKCELL_T_SATIS, campaign_id=79) `completionPercent` değerinin `%25`-`%29` aralığında takılı kaldığını raporladı. Canlı veri analizi (`pd_campaign_memento`, `pd_filter`, tablo dağılımı) Bölüm 1'deki orijinal "%0 takılı" bug'ının payda boyutunda devam ettiğini gösterdi:

- Kampanyaya tanımlı filter'lar arasında `is_disabled = 0` (ID 180 ve 426, duplicate olarak iki kez tanımlı) var.
- `createWhereFromFilters` bu filter'ları hem payda (`countActualListSize`) hem pay (`countCompletedCalls`) SQL'lerine ekliyor.
- Bir kayıt elendiğinde (`is_disabled = 1`) payda'dan ve "kalan iş"ten eş zamanlı düşüyor → oran sabit kalıyor.

Müşteri geri bildirimi: *"is_disabled alanından bağımsız olması gerek hesaplaması gerekir, başka sebeplerden dolayı da is_disabled değerini değiştirebiliyorlar."* Çözüm Faz 2'de aynı branch (`bug/317277-completion-percent-calculation`) ve aynı task (317277) altında ele alınır.

### Mevcut Davranış (Faz 1 sonrası)

- `countCompletedCalls` (pay) statü kolonlarını içerecek şekilde genişletildi (Faz 1).
- `countActualListSize` (payda) hâlâ `createWhereFromFilters(campaign)` ile **tüm enabled filter'ları** uyguluyor; statü kolonu hedefleyen filter'lar payda'yı küçültüyor.

### Beklenen Davranış

`countActualListSize` SQL'i sadece "iş kriteri" filter'larını uygular; statü kolonlarını hedefleyen filter'lar otomatik olarak SQL'den ayıklanır. `countCompletedCalls`, `countRetryListSize`, `countCallbackListSize` ve fetch SQL'leri tüm filter'ları (statü dahil) kullanmaya devam eder — orada filter'ların aynen uygulanması doğru davranıştır.

### Statü Kolon Tanımı

Bir filter, `field.fieldName` (case-insensitive, trim'li) aşağıdaki kolonlardan birine eşleşirse "statü filter" kabul edilir ve payda SQL'inden hariç tutulur (kullanıcı yanıtı: Seçenek 2, geniş liste):

- `Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.DISABLED.getValue()` → "is_disabled" (sabit)
- `campaign.getColResultCompleteDate()` (default: "complete_date", campaign-configurable)
- `campaign.getColResultFetchedDataFlag()` (default: "is_fetched", campaign-configurable)
- `campaign.getColResultAttempts()` (default: "attempts", campaign-configurable)
- `campaign.getColResultCallbackDate()` (default: "callback_date", campaign-configurable)
- `campaign.getColResultRetryDate()` (default: "retry_date", campaign-configurable)

### Etkilenen Kod Alanları (Faz 2)

- `gbpowerdialer/src/main/java/com/powerdialer/engine/db/FilterUtils.java` — yeni `createWhereFromFilters(Campaign, boolean excludeStatusFilters)` overload + `isStatusFilter` ve `getStatusFieldNames` helper.
- `gbpowerdialer/src/main/java/com/powerdialer/engine/db/CampaignDbOperations.java` — `countActualListSize` yeni overload'ı `excludeStatusFilters=true` ile çağırır. Diğer DAO method'ları **dokunulmaz**.
- Yeni test: `gbpowerdialer/src/test/java/tests/campaign/db/CountActualListSizeStatusFilterIsolationTest.java`.

### Riskler

- **Yanlış pozitif eşleşme**: Müşteri kampanyalarında nadiren statü kolon adıyla aynı isimde iş alanı tanımlanmış olabilir (örn. müşteri-tanımlı "attempts" iş kolonu). Düşük olasılık, ama oluşursa o kolonu hedefleyen iş filtreleri payda'ya yansımaz. Mitigation: statü kolon listesi merkezi tutulur; gerekirse override mekanizması ileride eklenir.
- **Geri uyumluluk**: Mevcut kampanyaların `actualListSize` değeri yeni davranışla artar (statü filter'ları artık küçültmediği için). Geçmiş `pd_campaign_memento` snapshot'ları eski formülle yazılmış, yeni snapshot'lar yeni formülle yazılır — raporlama tarafında discontinuity oluşur. Faz 1'deki kabulle aynı (sadece kod değişikliği yeterli).
- **Performans**: Payda sorgusu filter sayısı azalır → SQL daha küçülür, etkisi nötr veya pozitif.

### TDD Stratejisi

Yeni test sınıfı `gbpowerdialer/src/test/java/tests/campaign/db/CountActualListSizeStatusFilterIsolationTest.java`:

- Statü filter listesi her bir kolon için ayıklanır: `is_disabled`, `complete_date`, `is_fetched`, `attempts`, `callback_date`, `retry_date`.
- Field adı eşleşmesi case-insensitive (`IS_DISABLED` ile `is_disabled` aynı).
- Field adı eşleşmesi trim'li (`" is_disabled "` ile `is_disabled` aynı).
- Configurable kolon adı: campaign'in özel `colResultCompleteDate` değeri ayıklanır.
- Karışık filter listesi: statü olmayan filter'lar SQL'de kalır, statü filter'lar SQL'den çıkar.
- Sadece statü filter'ları (TURKCELL_T_SATIS senaryosu): `WHERE` clause boş, payda full tablo döner.
- Duplicate statü filter'lar (180+426 senaryosu): iki tanesi de SQL dışı kalır.
- Regresyon: `CountCompletedCallsFilterExpansionTest` Faz 1 testleri hâlâ yeşil — `countCompletedCalls` tüm filter'ları kullanmaya devam etmeli.

### Uygulama Kontrol Listesi (Faz 2)

- [ ] `FilterUtils.createWhereFromFilters(Campaign, boolean)` overload eklenir.
- [ ] `isStatusFilter` + `getStatusFieldNames` helper'ları eklenir.
- [ ] `CampaignDbOperations.countActualListSize` yeni overload'ı çağırır.
- [ ] Yeni test sınıfı eklenir (7+ senaryo).
- [ ] `mvn -o test-compile` yeşil.
- [ ] `mvn -o -Dtest=CountActualListSizeStatusFilterIsolationTest test` (cache hazırsa).
- [ ] `CountCompletedCallsFilterExpansionTest` hâlâ yeşil (regresyon).
- [ ] Plan ve history güncellenir.

### Hedefli Doğrulama Komutları (Faz 2)

- `mvn -o -Dtest=CountActualListSizeStatusFilterIsolationTest test`
- `mvn -o -Dtest=CountCompletedCallsFilterExpansionTest test` (regresyon)
- `mvn -o -Dtest=CampaignDb*Test,CountActualListSizeStatusFilterIsolationTest test`

### Kapanış Kriteri (Faz 2)

- Statü filter'ları payda SQL'inden çıkmış, iş filter'ları SQL'de.
- Faz 1 regresyon testleri yeşil.
- Plan ve history güncel.
- Müşteri saha doğrulaması için canlıya alınmaya hazır PR.

### Açık Sorular (Faz 2)

Tüm açık sorular kullanıcı tarafından kapatıldı (2026-06-29).

1. **Statü kolon listesi** → Cevap: **Seçenek 2 — geniş liste** (`is_disabled`, `complete_date`, `is_fetched`, `attempts`, `callback_date`, `retry_date`).
2. **`countAllCallTypes` gate** → Faz 2 kapsamı dışı; ayrı bir branch'te ele alındı.

---

## Faz 2 Simetri Düzeltmesi (2026-07-01)

### Tetikleyici

Müşteri canlıya alma öncesi endişe: *"paydadan da çıkarırsak elenen kayıtların completion rate etkisi hiç olmayacak gibi, beklenenden daha yüksek completion rate olacağından endişeleniyorum."*

Sayısal analiz gösterdi ki müşterinin sezgisi doğru bir asimetriyi işaret ediyor — fakat yönü tersine: `countCompletedCalls` **hâlâ tüm kullanıcı filter'larını uyguluyordu** ve user filter `is_disabled = 0`, completion predicate'in `is_disabled = 1` dalıyla çeliştiği için Faz 1'in DNC/eliminated kazancı silinip atılıyordu. Sonuç: payda büyür, completed büyümez → yüzde beklenenden **düşer**.

### Sayısal Örnek

100 kayıtlı kampanya, filter `is_disabled = 0`, 20 disabled + 30 complete_date + 50 aktif:

| Durum | Payda | Completed | Yüzde |
|---|---:|---:|---:|
| Faz 1 canlıda | 80 | 30 (is_disabled=1 filter çelişkisi) | %37 |
| Faz 2 (simetrisiz) | 100 | 30 | **%30 (düşüş)** |
| Faz 2 + simetri (bu düzeltme) | 100 | 50 | **%50 (ideal)** |

### Değişiklik

- `CampaignDbOperations.countCompletedCalls`: `createWhereFromFilters(campaign)` → `createWhereFromFilters(campaign, true)`. `FilterUtils` yeni overload'ı pay tarafında da devreye alındı.
- Javadoc simetri gerekçesini açıkladı.
- `CountCompletedCallsFilterExpansionTest`: 3 yeni regresyon (statü filter strip, karışık strip, TURKCELL duplicate).
- `CountActualListSizeStatusFilterIsolationTest.countCompletedCallsStillUsesAllFilters` → `countCompletedCallsAppliesSameStatusFilterIsolation`. Yeni davranışı doğrular.

### Doğrulama

- `mvn -o test-compile` → BUILD SUCCESS (777 main + 146 test source).
- Test runtime `surefire-junit4:3.1.2` cache blocker (Faz 1'den beri devam eden ortam sorunu, VPN cache güncellemesi sonrası çalıştırılacak).
