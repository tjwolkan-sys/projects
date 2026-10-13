# FIX-323316 — Fetch sırasında UpdatedDate ezilmesinin kaldırılması

- **İş Etiketi:** fix
- **Öncelik:** normal
- **Task:** 323316 (Parent Feature 289375 — Code Review)
- **Branch:** `fix/323316-fetch-updateddate-yazimi` (base: `develop` = `origin/develop` @ ad42ff27; VPN kapalı olduğu için fresh fetch yapılamadı, en son bilinen remote develop kullanıldı)
- **Tarih:** 02.07.2026

## Bağlam

Raporlama, kampanya sonuç (camp) tablosunda "işlem gören kayıt tarihi" için şu CASE'i kullanıyor:

```
CASE
  WHEN complete_date IS NOT NULL THEN complete_date
  WHEN UpdatedDate  IS NOT NULL THEN UpdatedDate
  WHEN start_date   IS NOT NULL THEN start_date
  ELSE DATA_LOAD_DATE
END
```

`UpdatedDate`, bu kampanyada `col_result_update_date` eşlemesiyle fiziksel `UpdatedDate` kolonuna denk geliyor (kullanıcı teyit etti). Bu kolon raporda ayrıca **expire tarihi** için de kullanıldığından CASE'ten çıkarılamıyor.

Sorun: kayıt hiç aranmadığı hâlde raporda "işlem görmüş" gibi görünüp yanlış güne düşüyor. Kök neden, `UpdatedDate` kolonunun **fetch (kuyruğa alma) anında da** güncellenmesidir — arama olmadan. `setFetchedDataFlagForCalls` fetch'te `UpdatedDate = CURRENT_TIMESTAMP` basar ve aynı anda `start_date`/`answer_date`'i NULL'a çeker; `complete_date` de NULL olduğundan rapor CASE'i `UpdatedDate`'e (fetch günü) düşer.

## Mevcut Davranış

`CampaignDbOperations.setFetchedDataFlagForCalls` (`gbpowerdialer/src/main/java/com/powerdialer/engine/db/CampaignDbOperations.java`):

- **UPDATE branch** (satır ~1157-1163): `is_fetched = <now>`, `, <col_result_update_date> = CURRENT_TIMESTAMP` (Oracle/PostgreSQL ve SQLServer/MySQL kollarının ikisinde de).
- Ardından `appendStartAndAnswerDatesReset` ile `start_date = NULL, answer_date = NULL`.
- **INSERT branch** (satır ~1172-1188, yalnız `!hasSameSourceAndResultTable()` iken): yeni sonuç satırına `(is_fetched, <col_result_update_date>, unique_key)` = `(<now>, CURRENT_TIMESTAMP, unique_key)` yazar.

`UpdatedDate`'in diğer yazıcıları (bu iş kapsamı DIŞI, değişmeyecek):
- Gerçek sonuç yazımı `updateCampaignListTableRow` (satır ~2041-2042): gerçek çağrı → meşru.
- Expire/disable job'u `CampaignResultJobService.getSetSql` (satır ~278) ve JIT expire `setFilteredByExpireStatus` (satır ~1473): `[UpdatedDate]=GETDATE()` → meşru expire tarihi.

Kolon değerini **karar için okuyan hiçbir runtime yol yok** (fetch SELECT, cleanup, staleness, ordering); yalnız config-type doğrulaması (`CampaignDataTypeChecker:84`) ve alan-listesi dışlaması (`CampaignService:826`) değere bakmadan referans verir. Tek değer-okuyucu, kayıt listesi ekranındaki `CampaignRecordService.classifyRecord`'tur (aşağıda risk).

## Beklenen Davranış

Fetch (kuyruğa alma), `UpdatedDate`'i **artık güncellemeyecek**. Böylece `UpdatedDate` yalnızca:
- gerçek çağrı sonucu yazımıyla (gerçekten arandı), veya
- expire/disable job'uyla (gerçekten elendi/çıkarıldı)

hareket edecek. Sadece fetch edilip aranmayan kayıt `UpdatedDate` almayacağından rapor CASE'i o kayıt için `start_date` (fetch NULL'a çektiği için) üzerinden `DATA_LOAD_DATE`'e düşecek — hayalet ortadan kalkacak. `is_fetched` yazımı ve `start_date`/`answer_date` NULL sıfırlaması **korunacak** (deneme zamanlaması için gerekli).

## Etkilenen Kod Alanları

- `gbpowerdialer/src/main/java/com/powerdialer/engine/db/CampaignDbOperations.java`
  - `setFetchedDataFlagForCalls` UPDATE branch: `<col_result_update_date> = CURRENT_TIMESTAMP` parçasını kaldır (her iki DB kolu).
  - `setFetchedDataFlagForCalls` INSERT branch: kolon listesinden ve SELECT'ten `<col_result_update_date>` / `CURRENT_TIMESTAMP` parçasını kaldır.
- (Test) `gbpowerdialer/src/test/java/tests/campaign/db/` altında yeni regresyon testi.

## Riskler

- **Hot path:** `setFetchedDataFlagForCalls` fetch hot path'inde. Değişiklik UPDATE/INSERT'ten bir kolon **çıkarır** → maliyet azalır; yeni IO/scan/lock yok. Risk yok.
- **Kayıt sınıflandırma ekranı (`classifyRecord`):** ELIMINATED ayrımı `is_disabled=1` gerektirir; fetch'lenen kayıtlar `is_disabled=0` olduğundan sınıflandırma değişmez. Etki ~yok.
- **Ayrı source/result tablosu kampanyaları (INSERT branch):** Yeni fetch-satırı `UpdatedDate` NULL ile oluşacak. Bu kampanyaların kullanıcının vakasıyla ilgisi yok (onlar aynı-tablo), ama tutarlılık için düzeltiliyor. `UpdatedDate` kolonunun **nullable** olması gerekir (rapor CASE'i ve `classifyRecord` zaten NULL bekliyor; aylık yüklenen kayıtlar başlangıçta NULL) → yüksek güvenle nullable, doğrulama maddesi eklendi.
- **Dış raporlama/monitoring** `UpdatedDate`'in fetch'te ilerlemesine bel bağlıyorsa etkilenir — ki zaten düzeltmek istenen davranış bu.

## Performans Etkisi

Hot path'e (fetch) dokunuluyor. Etki **pozitif/nötr**: her fetch UPDATE'inde ve (ayrı-tablo) INSERT'inde bir kolon ataması eksilir; ek query/IO/serialization/lock/allocation yok. Call/event/tick başına ek maliyet yok.

## TDD Stratejisi

Mevcut kanıtlı desen: `DBEngine` mock + `ArgumentCaptor<String>` ile üretilen SQL yakalanır (`CampaignFreshDataListSizeSqlRegressionTest` örneği).

Yeni test sınıfı: `tests/campaign/db/FetchedDataFlagUpdateDateOmissionRegressionTest`:
1. **RED (düzeltme öncesi):** Ayrı source/result tablolu, `colResultUpdateDate="UpdatedDate"` eşlemeli campaign ile `setFetchedDataFlagForCalls` çağrılır; yakalanan UPDATE SQL'inin `UpdatedDate = CURRENT_TIMESTAMP` **içermediği**, `is_fetched`'in set edildiği ve `start_date = NULL` / `answer_date = NULL` resetlerinin **korunduğu**; INSERT SQL'inin kolon listesinde `UpdatedDate` **bulunmadığı** doğrulanır. Bu testler mevcut kodda başarısız olur.
2. **GREEN:** `setFetchedDataFlagForCalls`'tan `UpdatedDate` yazımları kaldırılır; testler geçer.
3. Regresyon güvencesi: aynı testte `is_fetched` ve start/answer reset'in hâlâ üretildiği assert edilerek davranışın geri kalanı kilitlenir.

## Uygulama Kontrol Listesi

- [x] Yeni regresyon testi yazıldı (RED doğrulandı — fetch UPDATE'i UpdatedDate yazıyordu).
- [x] UPDATE branch'ten `UpdatedDate = CURRENT_TIMESTAMP` kaldırıldı (Oracle/PG + SQLServer/MySQL kolları).
- [x] INSERT branch'ten `UpdatedDate` kolonu + `CURRENT_TIMESTAMP` kaldırıldı.
- [x] Test GREEN (`FetchedDataFlagUpdateDateOmissionRegressionTest` 1/1).
- [x] Etkilenen mevcut testler koşuldu (CallProducerTest 7/7, CampaignFreshDataListSizeSqlRegressionTest 2/2 → toplam 10/10 BUILD SUCCESS).
- [x] History kaydı eklendi (`docs/history/02.07.2026.md`).
- [ ] Commit (kullanıcı onayı bekliyor). Push: VPN kapalı → kullanıcıda.

## Hedefli Doğrulama Komutları

`.codex/TOOL-PATHS.local.properties` okunarak offline:
- `mvn -o -Dtest=FetchedDataFlagUpdateDateOmissionRegressionTest test`
- Etkilenen mevcut: `mvn -o -Dtest=CallProducerTest,CampaignFreshDataListSizeSqlRegressionTest test`

## Kapanış Kriteri

- Fetch SQL'leri (UPDATE + INSERT) `UpdatedDate`/`col_result_update_date` yazımı içermiyor; `is_fetched` ve start/answer reset korunuyor.
- Yeni test + etkilenen mevcut testler PASS.
- `UpdatedDate` yalnız gerçek sonuç yazımı ve expire ile hareket ediyor (kod incelemesiyle teyit).

## Açık Sorular (kapandı)

1. **Kapsam:** ~~INSERT branch de düzeltilsin mi?~~ → Kullanıcı **"ikisi de"** dedi (02.07.2026). UPDATE + INSERT branch birlikte düzeltildi.
2. **Doğrulama — `UpdatedDate` nullable mı?** → Yüksek güvenle evet: rapor CASE'i `WHEN UpdatedDate IS NOT NULL` kontrolü yapıyor ve aylık yüklenen kayıtlar başlangıçta NULL; `classifyRecord` ELIMINATED ayrımı da `updated_date == null` bekliyor. INSERT branch değişikliği (yeni satır `UpdatedDate` NULL) bununla uyumlu. (Fiziksel NOT NULL kısıtı varsa ayrı-tablo kampanyalarında INSERT patlar — üretimde doğrulanması önerilir; kullanıcı vakası aynı-tablo olduğu için UPDATE branch'i kullanır, bu riskten etkilenmez.)
