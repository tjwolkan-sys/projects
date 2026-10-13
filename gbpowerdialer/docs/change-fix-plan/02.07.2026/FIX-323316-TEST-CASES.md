# FIX-323316 — QA Test Case'leri (fetch UpdatedDate ezmesi kaldırma)

- **Task:** 323316 (Parent Feature 289375 — Code Review)
- **İlgili değişiklik:** `CampaignDbOperations.setFetchedDataFlagForCalls` fetch işaretlemesi artık sonuç tablosunun `UpdatedDate` (`col_result_update_date`) kolonunu güncellemiyor.
- **Kapsam alanı:** Kampanya sonuç (camp) tablosu + raporlama `DATE_STATUS_MODIFIED` CASE'i.
- **Ön koşul (ortak):** Test kampanyasında `col_result_update_date = UpdatedDate` eşlenmiş olmalı (rapordaki kolonla aynı fiziksel kolon). Sonuç tablosuna DB'den (SSMS vb.) sorgu atma yetkisi olmalı. Raporun kullandığı CASE:
  ```
  CASE
    WHEN complete_date IS NOT NULL THEN complete_date
    WHEN UpdatedDate  IS NOT NULL THEN UpdatedDate
    WHEN start_date   IS NOT NULL THEN start_date
    ELSE DATA_LOAD_DATE
  END DATE_STATUS_MODIFIED
  ```

---

## TC-323316-1 — Fetch edilip aranmayan kayıt `UpdatedDate` almaz (ana hata / "hayalet kayıt")

- **Öncelik:** 1 (High)
- **Amaç:** Sadece kuyruğa alınan (fetch edilen) ama hiç aranmayan kaydın rapora "o gün işlem görmüş" gibi düşmediğini doğrulamak.
- **Ön koşul:**
  - Kampanyada aranmamış (fresh) kayıtlar var; bu kayıtların `UpdatedDate` değeri başlangıçta NULL (veya bilinen eski bir tarih, ör. `DATA_LOAD_DATE` ile aynı gün).
  - Cache limiti, fetch'in tetiklenmesini sağlayacak şekilde ayarlı.
  - Kampanyada o an aktif/uygun agent olmayabilir ya da concurrent line sınırı düşük tutulur ki fetch edilen bazı kayıtlar aranmadan kalsın.

| Adım | İşlem | Beklenen Sonuç |
|------|-------|----------------|
| 1 | Kampanyayı başlat ve dialer'ın kayıtları cache'e fetch etmesini sağla (kayıt sayısı cache limitini aşsın). | Dialer fetch cycle'ı çalışır; kayıtlar belleğe alınır. |
| 2 | Fetch edilmiş ama HENÜZ ARANMAMIŞ (originate olmamış) bir kaydın sonuç tablosu satırını sorgula: `is_fetched, UpdatedDate, start_date, complete_date`. | `is_fetched` DOLU; `start_date` NULL; `complete_date` NULL. |
| 3 | Aynı kaydın `UpdatedDate` değerini kontrol et. | `UpdatedDate` fetch'ten dolayı **GÜNCELLENMEMİŞ**: NULL veya önceki (yükleme) değeri — fetch işleminin çalıştığı gün/saat DEĞİL. |
| 4 | Raporu (DATE_STATUS_MODIFIED) bu kayıt için çalıştır. | `complete_date` NULL ve `UpdatedDate` NULL olduğundan CASE `start_date`'e (NULL), oradan `DATA_LOAD_DATE`'e düşer. Kayıt **fetch gününe / bugünün raporuna işlenmez**. |

- **Genel Beklenen Sonuç:** Aranmamış kayıt hiçbir arama günü raporuna "işlem görmüş" olarak düşmez.
- **Regresyon notu (düzeltme öncesi hatalı davranış):** Düzeltmeden önce `UpdatedDate` = fetch zaman damgası olurdu; CASE `UpdatedDate`'e düşüp kaydı fetch gününün raporuna sokardı (kullanıcının 29 Haziran şikâyeti).

---

## TC-323316-2 — Gerçekten aranan kayıt `UpdatedDate`'i güncellemeye devam eder (regresyon guard)

- **Öncelik:** 1 (High)
- **Amaç:** Düzeltmenin YALNIZCA fetch yazımını kaldırdığını, gerçek çağrı sonucu yazımını bozmadığını doğrulamak.
- **Ön koşul:** Kampanyada en az bir kayıt gerçekten aranabilecek (uygun agent / predictive originate mevcut).

| Adım | İşlem | Beklenen Sonuç |
|------|-------|----------------|
| 1 | Kampanyayı başlat ve bir kaydı gerçekten ara (originate). | Çağrı kurulur; kayıt için `start_date` yazılır. |
| 2 | Çağrıyı bir sonuçla sonlandır: müşteri açmaz/meşgul (telephony 11-13) VEYA tamamlanır (complete). | Çağrı sonucu sonuç tablosuna yazılır. |
| 3 | Kaydın satırını sorgula: `UpdatedDate, start_date, telephony_result_code, complete_date, retry_date`. | `UpdatedDate` = çağrı sonucu yazım zamanı (DOLU). `start_date` dolu; `telephony_result_code` set. 11-13 ise `retry_date` dolu + `complete_date` NULL; tamamlandıysa `complete_date` dolu. |
| 4 | Raporu bu kayıt için çalıştır. | Kayıt gerçek işlem gününe doğru atfedilir (varsa `complete_date`; yoksa `UpdatedDate` = gerçek çağrı zamanı). |

- **Genel Beklenen Sonuç:** Gerçek çağrı yapılan kayıtlarda `UpdatedDate` davranışı değişmez; rapor doğru gün atfını yapar.

---

## TC-323316-3 — Expire/disable job'u `UpdatedDate` = expire tarihini yazmaya devam eder (kullanım korunumu)

- **Öncelik:** 2 (Medium)
- **Amaç:** `UpdatedDate` rapordan çıkarılmadığı için expire tarihi kullanımının korunduğunu doğrulamak (fetch fix'i bu kullanımı bozmamalı).
- **Ön koşul:** Kampanya için bir veri-manipülasyon / expire JobConfig tanımlı (filtreye uyan kayıtları `is_disabled=1` yapıp expire user + tarih yazar). Filtreye uyan, aranmamış en az bir kayıt olsun.

| Adım | İşlem | Beklenen Sonuç |
|------|-------|----------------|
| 1 | Expire job'unun çalışmasını tetikle (veya zamanlanmış çalışmasını bekle). | Job filtreye uyan kayıtları işler. |
| 2 | Expire edilen bir kaydın satırını sorgula: `is_disabled, UpdatedUser, UpdatedDate, complete_date`. | `is_disabled = 1`; `UpdatedUser` = job'un expire user'ı; `UpdatedDate` = job'un çalıştığı tarih (DOLU); `complete_date` NULL. |
| 3 | Raporu bu kayıt için çalıştır. | `complete_date` NULL olduğundan CASE `UpdatedDate`'e (expire tarihi) düşer. Kayıt "işlem gören kayıt tarihi" olarak **expire tarihini** alır. |

- **Genel Beklenen Sonuç:** Expire edilen kayıtlar raporda expire tarihiyle görünmeye devam eder. `UpdatedDate`'in expire tarihi olarak kullanımı **korunur** — bu, `UpdatedDate`'i CASE'ten çıkarmak yerine fetch yazımını kaldırma tercihinin doğrulamasıdır.

---

## TC-323316-4 — Callback yaşam döngüsü: yeniden fetch `UpdatedDate`'i ezmez, gerçek çağrı kolonları dolar

- **Öncelik:** 1 (High)
- **Amaç:** Randevulu (callback) bir kaydın uçtan uca akışında, callback kaydı YENİDEN FETCH edildiğinde (henüz aranmadan beklerken) `UpdatedDate`'in güncellenmediğini; buna karşın gerçek çağrı olaylarında (`start_date`, `complete_date`) ve gerçek sonuç yazımlarında (`UpdatedDate`) kolonların doğru dolduğunu doğrulamak.
- **Ön koşul:**
  - Kampanyada callback açık (`allowCallback`), `col_result_update_date = UpdatedDate`, `col_result_callback_date = callback_date` eşlenmiş.
  - En az bir uygun agent var (çağrı client+agent bağlanabilsin).
  - Test kaydının `UpdatedDate`, `start_date`, `answer_date`, `complete_date`, `callback_date` değerleri başlangıçta NULL (fresh kayıt).

### Aşama bazlı beklenen kolon durumu

| Aşama | İşlem | is_fetched | start_date | answer_date | complete_date | callback_date | **UpdatedDate** |
|-------|-------|:---------:|:---------:|:-----------:|:-------------:|:-------------:|:---------------:|
| A | İlk çağrı: originate → client + agent bağlandı | DOLU | DOLU (1. arama) | DOLU | NULL | NULL | (henüz sonuç yazılmadı) |
| B | Agent kapattı ve **2 dk sonrasına callback kurdu** (disposition) | **NULL** | DOLU | DOLU | NULL | now+2dk | **DOLU = disposition anı** (gerçek sonuç yazımı — meşru) |
| C | Callback zamanı gelince **kayıt yeniden FETCH edildi**, henüz arama start olmadı (bekliyor) | **DOLU** | **NULL** (reset) | **NULL** (reset) | NULL | now+2dk | **DEĞİŞMEDİ = hâlâ B'deki disposition anı** ⟵ KİLİT |
| D | Callback araması çıktı, başarılı, tamamlandı | DOLU | DOLU (2. arama) | DOLU | **DOLU** | (tüketildi) | **DOLU = tamamlanma anı** (gerçek sonuç yazımı — meşru) |

| Adım | İşlem | Beklenen Sonuç |
|------|-------|----------------|
| 1 | Kampanyayı başlat; test kaydını ara (originate), client ve agent'ı bağla. | Çağrı kurulur; `start_date` ve `answer_date` dolar. |
| 2 | Agent görüşmeyi bitirip çağrıyı kapatsın ve **2 dk sonrasına callback kursun**. Sonuç tablosu satırını sorgula: `is_fetched, callback_date, complete_date, UpdatedDate`. | `callback_date` = now+2dk; `is_fetched` = **NULL** (callback kurulunca fetch bayrağı sıfırlanır); `complete_date` = NULL; `UpdatedDate` = disposition (sonuç yazım) anı — **DOLU** (gerçek agent sonucu; beklenen davranış). |
| 3 | Callback zamanı gelince dialer kaydı yeniden fetch etsin (henüz originate ETMESİN — bekleme anını yakala). Satırı sorgula: `is_fetched, start_date, answer_date, complete_date, UpdatedDate`. | `is_fetched` = **DOLU**; `start_date` = **NULL** (yeniden fetch reset eder); `answer_date` = **NULL**; `complete_date` = NULL. **`UpdatedDate` DEĞİŞMEMİŞ** — Adım 2'deki disposition anıyla AYNI; yeniden fetch günü/saati DEĞİL. ⟵ **Bu testin ana doğrulaması.** |
| 4 | Callback araması çıksın (originate), başarıyla tamamlansın. Satırı sorgula: `start_date, answer_date, complete_date, UpdatedDate, telephony_result_code`. | `start_date` = 2. arama zamanı (yeniden dolar); `answer_date` dolar; `complete_date` = **DOLU**; `telephony_result_code` set; `UpdatedDate` = tamamlanma anı — **DOLU** (gerçek sonuç yazımı). |
| 5 | Raporu (DATE_STATUS_MODIFIED) bu kayıt için çalıştır. | `complete_date` dolu olduğundan CASE `complete_date`'e düşer; kayıt gerçek tamamlanma gününe atfedilir. Hiçbir aşamada kayıt "yeniden fetch günü"ne yanlış düşmez. |

- **Genel Beklenen Sonuç:** Callback akışında `UpdatedDate` yalnızca gerçek agent sonucu (Adım 2 disposition) ve gerçek tamamlanma (Adım 4) anlarında hareket eder. **Yeniden fetch (Adım 3) `UpdatedDate`'i ASLA güncellemez**; `is_fetched` dolsa bile kayıt fetch anına atfedilmez.
- **Regresyon notu (düzeltme öncesi hatalı davranış):** Düzeltmeden önce Adım 3'teki yeniden fetch `UpdatedDate`'i fetch anına güncellerdi; `complete_date` NULL, `start_date` fetch tarafından NULL'landığı için rapor CASE'i `UpdatedDate`'e (yeniden fetch günü) düşer ve callback kaydı **hiç arama yokken yeniden fetch gününe** "işlem görmüş" gibi yansırdı.

---

## Notlar
- Otomatik regresyon (birim test) tarafı `FetchedDataFlagUpdateDateOmissionRegressionTest` ile karşılanmıştır (fetch SQL'leri `UpdatedDate` yazmaz; `is_fetched` + start/answer reset korunur). Yukarıdaki 3 senaryo uçtan uca (DB + rapor) manuel doğrulamadır.
- Ayrı source/result tablolu kampanyalarda (INSERT branch) yeni fetch satırı `UpdatedDate` NULL ile oluşur; TC-1 aynı mantıkla o kampanyalarda da geçerlidir. `UpdatedDate` kolonu NOT NULL kısıtlı ise ayrı-tablo INSERT'i doğrulanmalı (kullanıcının vakası aynı-tablo olduğu için UPDATE branch'ini kullanır, bu riskten etkilenmez).
