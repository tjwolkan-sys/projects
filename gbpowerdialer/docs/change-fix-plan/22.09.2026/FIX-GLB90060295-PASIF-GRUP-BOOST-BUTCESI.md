# FIX-GLB90060295 — Pasif kampanya grubunun dialing boost originate bütçesine sayılması

- **İş Etiketi:** fix
- **Öncelik:** hotfix (canlı Guvencell abandon şikâyeti; kullanıcı kararı 22.09.2026)
- **Task:** yok (KIMLIK = developer username `GLB90060295`)
- **Branch:** `fix/GLB90060295-pasif-grup-boost-butcesi` (base: `origin/release/6.7.8` @ a2788c2d, 17.09.2026)
- **Tarih:** 22.09.2026
- **Durum:** Uygulama tamamlandı, commit `10d46c99` atıldı ve dal `origin/fix/GLB90060295-pasif-grup-boost-butcesi` olarak push edildi (22.09.2026); release/develop taşıma ekipte, PRP/canlı doğrulama test ekibinde

## Bağlam

22.09.2026, Turkcell seti (dialer host `DIALER_APP_HOST`, deploy = release/6.7.8; kanıt: yalnız 6.7.8'de bulunan `[DUP-FETCH-GUARD]` log işaretçisi). Guvencell kampanyası `GUVENCELL_WIYO_SAGLIKLI_YASAM` (id 164, Progressive Released, boost 0,65) için müşteri şikâyeti: "1 avail agent görünce 4-5 arama çıkıyor, abandon artıyor". Müşterinin ikinci sorusu: pasif/aktif geçişini sürekli yaptıkları için grupları silip yeniden eklemek yerine kalıcı düzeltme istiyorlar.

Kampanyanın grup satırları (`pd_campaign_group`, deleted=0): `GUVENCELL_WIYO_SAGLIKLI_YASAM` aktif; `GUVENCELL_GENEL` **08:35:56'da pasife alınmış** (16 agent login), `GUVENCELL_FKS_CAPRAZ` ve `GUVENCELL_FKS_YENILEME` pasif.

### Kanıt özeti (09:00-09:54, kampanya tipi Progressive Released)

| Ölçüm | Sonuç |
| --- | --- |
| Dial / cevap / abandon | 535 / 284 / 46 (%16) |
| Ring başlangıcında aynı agent'ta zaten çalan çağrı olan oran | 279/397 (%70); tek agent'ta eşzamanlı 9 ringing (09:06:37-45'te 8 sn'de 12 çağrı → 5 abandon) |
| Ring sayısı ≤ floor(0,65 × (aktif + pasif grup avail)) | 436 dial'ın 251'i tam, 176'sı +1/+2 (iki originator thread yarışı) |
| Ring sayısı ≤ floor(0,65 × yalnız aktif grup avail) | 436 dial'ın 4'ü |
| Abandon anında aktif grupta Available agent | 45/46 vakada 0; pasif GENEL grubunda 1-8 |
| Örnek | 09:12:49-54 agent 82365'te 5 ringing; SAGLIKLI avail=1, GENEL avail=6-7 → floor(0,65×8)=5 |

Kaynaklar: `pd_cdr`, `wbp_UserStatusEvents` (agent statü geçmişi), Kibana `turkcell-*` (`Ringing calls incremented ... user=`, `Idle user added/removed`, `[ACD-NO-AGENT]`, `[ACD-NOAVAIL]`). Karıştırıcı etken: operatör aynı pencerede kampanyayı 26 kez güncelledi (09:10 öncesi ring sayıları daha yüksek bir boost'a uyuyor; 09:53-10:04 tip Predictive/0,6; 10:32 tekrar Progressive 1,1). Bug tip değişikliğinden bağımsızdır; ALPHA yolu da aynı metodu kullanır.

## Mevcut Davranış

`ACDEngine.getCampaignAvailableUsersCount` (`gbpowerdialer/src/main/java/com/powerdialer/engine/acd/ACDEngine.java:716-727`) kampanyanın grup listesini `campaign.getCampaignGroups()` ile okur ve **`isEnabled` filtresi uygulamaz**; skill filtresi de yoktur. Yalnız Hibernate `@Where(clause = "deleted = 0")` çalışır; yani "pasif" grup listede kalır, "silinmiş" grup düşer. Metot her grup için `getAvailableUserToCallsCount(group)` (keeper + idle kuyruğu, statü kapılı) sonuçlarını birleştirip sayar.

Bu sayı iki bütçe kapısının tabanıdır:

- `isOriginateBudgetAvailable` (ACDEngine:543-546): PROGRESSIVE_RELEASED ve PREDICTIVE-ALPHA originate fazı; `notBridged < floor(availableUsers × boost)`.
- `isDialingBoostNotReached` (ACDEngine:582-589): boost > 1 iken diğer tipler (`canUseNotBridgedUsers`).

Grup listesini okuyan diğer 6 runtime noktası aktif grup filtresi uygular: `getCampaignIdleUsers`, `getCampaignIdleAndAvailableUsers`, `getAllLoginUsers`, `countCampaignAvailableUsersNow` (ekrandaki **Avail**), `isUserHasMatchingSkillsAndGroup` (notBridged sayımı ve Agent sayısı), `findGammaSymbolicCarrier`. Aday seçimi (`DefaultFindMatchingUserStrategy.findMatchingUser` → `campaign.getEnabledCampaignGroups()`) da yalnız aktif grupları görür.

Sonuç: bütçe tabanı pasif grubun Available agent'larıyla şişer, çağrı ise yalnız aktif grubun agent'ına atanabilir; bütçe kampanya geneli bir tavan olduğu için tüm çağrılar tek aday agent'a yığılır (min-calls dengeleme tek adayla işlemez), müşteri açınca biri bağlanır, kalanı abandon olur.

### Aktif/pasif geçişinin runtime'a yansıması (değişmeyecek, planın dayanağı)

Kampanya formu Gruplar sekmesi (`campaignGroupCtrl.js`) grup satırlarını bellekte düzenler (aktif kutusu; sil → `deleted=true`). Kampanya kaydedilince `PUT campaign/{id}/response` → `CampaignService.updateContext` → `DialerFacade.updateContext` → `CampaignContext.updateCampaign` → `CampaignPropertyCopier.copyShallowRelationships` → `copyOneToManyInPlaceByIdReplace` grup satırlarını taze `enabled` bayrağıyla değiştirir, listede olmayanı (`deleted=1`) çıkarır. Yani fix sonrası pasif/aktif geçişi kampanya kaydıyla anında bütçeye yansır; silip yeniden ekleme gerekmez. Listedeki **Reload** butonu konfig yüklemez (`CampaignStateTemplate.reload` yalnız fetched flag sıfırlar).

## Beklenen Davranış

1. `getCampaignAvailableUsersCount` yalnız **aktif** (`CampaignGroup::isEnabled`) grupların agent'larını sayar; skill açık kampanyada `countCampaignAvailableUsersNow` ile aynı skill filtresini uygular (Açık Soru 2); kullanıcı bazında `distinct` sayar.
2. Pasif grup, pacing açısından "kampanyada yok" sayılır; grup yeniden aktif edilip kampanya kaydedilince agent'ları bütçeye anında girer.
3. `isOriginateBudgetAvailable` ve `isDialingBoostNotReached` bu tanımı miras alır; `floor` semantiği, notBridged sayımı, aday seçimi, `campaign.setAvailableUsersCount(...)` yan etkisi (REST `/api/campaigns/{id}/users/available/count` ve BETA okuyucuları) değişmez.
4. Aktif grubu tek olan kampanyalarda davranış bit-eşit kalır (karakterizasyon testiyle kilitlenir).

## Etkilenen Kod Alanları

- `gbpowerdialer/src/main/java/com/powerdialer/engine/acd/ACDEngine.java` → `getCampaignAvailableUsersCount` (aktif grup + skill filtresi + `distinct`). Tek "avail agent" tanımı için `countCampaignAvailableUsersNow` ile ortak `campaignAvailableUsersStream(campaign)` yardımcı stream'i çıkarılır; ekran sayısı ve bütçe tabanı aynı kaynağı okur. `countCampaignAvailableUsersNow` yalnız null kenar durumlarında (skill seti null iken NPE yerine "şart yok"; `skillWeightPair` null iken NPE yerine "şartı geçemez") farklılaşır, diğer girdilerde bit-eşittir.
- (Test, yeni) `gbpowerdialer/src/test/java/tests/acd/AvailableUsersCountDisabledGroupRegressionTest.java`.
- (Test, mevcut, koşulacak) `tests/acd/ProgressiveReleasedOriginateReservationTest`, `PredictiveAlphaOriginateReservationTest`, `ACDEngineIdleQueueStatusGateTest`, `AcdPriorityAwareLcdDistributionTest`, `CampaignActiveUserCountBatchCharacterizationTest`, `ProgressiveReservedReservationRegressionTest`.

Değişmeyecek: Redis/AMI contract'ları, campaign lifecycle, DB şeması, AngularJS UI, `countCampaignAvailableUsersNow`, aday seçimi.

## Dal ve Canlıya Alım Stratejisi

- Base `origin/release/6.7.8`: Turkcell'de çalışan sürüm; `PR 25715 (325559)` ve `PR 25716 (325470)` hiçbir release dalında yok (`git branch -r --contains` = 0), yalnız develop'ta. Bu iki task'ın testleri tamamlanmadığı için canlıya gitmemeleri gerekiyor; fix release tabanından açılarak izole edildi.
- `engine/acd/` ve `tests/acd/` için `git diff --stat origin/release/6.7.8 origin/develop` boş → aynı commit develop'a çakışmasız taşınabilir (merge veya cherry-pick, Açık Soru 1).
- Akış: fix dalı → PR (release hedefi Açık Soru 1) → deploy → develop'a ileri taşıma. `gbpd-sync-develop-branch` bu işte kullanılmaz (taban release; develop'a taşıma ayrı adım).

## Kapsam Dışı (bilinçli)

- **Cevap anı `%35 boş agent` kapısı:** `canUseIdleUsers` boost<1 iken `idle > ceil(users × (1−boost))` şartını cevap-anı yeniden seçimde de uyguluyor; `answerDate` AMI'den gelmeden (`Call.isActive()` false) idle agent verilmiyor. Ölçüm: 96 `[ACD-NOAVAIL]`'ın 16'sında 1-3 aktif agent Available idi, 15'i 0,4-14,7 sn içinde bağlandı, 1'i abandon. Ayrı iş önerisi (Açık Soru 3).
- İki originator thread'inin (iki Asterisk sunucusu) aynı anda bütçe kontrolü → bütçe zaman zaman +1 aşılır.
- Grup pasife alınırken o grubun agent'larında çalan çağrıların notBridged sayımından düşmesi (birkaç saniyelik geçiş etkisi).
- Çalışan kampanya Predictive'e çevrildiğinde `DynamicBoostManager` verisi olmaması ("Could not obtain campaign boost data" → default boost).
- `docs/history/README.md` dosyasında release/6.7.8'den gelen eski conflict marker artığı (dokunulmadı; geliştiriciye raporlandı).

## Riskler

- **Blast radius:** aynı metot Progressive Released, Predictive-ALPHA ve boost>1 yolunu besler. Pasif grup satırı taşıyan aktif kampanyalar fix sonrası **daha az** çağrı çıkarır (doğru davranış): Turkcell 164 (bu iş), 113 `GUVENCELL_06AKG` (boost 1,4; 368 dial/gün), 115, 160; NonTurkcell 6 `KFK_TAHSILAT` (boost 1,5; 409 dial/gün). GAMMA kampanyaları (73, 79, 80, 81, 153) bu bütçe yolunu kullanmadığı için etkilenmez. Müşteriye "çıkış hızı düştü" algısı için ön bilgilendirme gerekir.
- **Skill filtresi (Açık Soru 2 evet ise):** skill açık kampanyalarda bütçe tabanı daralır; ekrandaki Avail ile aynı tanıma gelir.
- **Canlı tekrar üretilemez:** doğrulama unit test + PRP (gerçek çağrı çıkarır ⚠️) + canlı ölçüm (camp 164, ring/agent ≤ 1) ile yapılır.
- **Release tabanı:** docs/history 6.7.8'de sonraki günleri içermiyor; bu plan ve history kayıtları develop'a taşınırken append-only kural korunur (conflict'te iki taraf da tutulur).

## Performans Etkisi

Hot path: originate ACD'si (çağrı başına, iki originator thread'inde) ve AGI cevap-anı yeniden seçimi (çağrı başına). Sonuç: **Hot path, ek maliyet yok.** `isEnabled` filtresi taranan grup sayısını azaltır; skill filtresi `countCampaignAvailableUsersNow`'da zaten çalışan `containsAll` kontrolüdür (küçük set); `distinct` çağrı başına en fazla onlarca kullanıcı için küçük bir `HashSet` allocation'ıdır. Yeni IO, lock, log veya serialization yok.

## TDD Stratejisi

Şablon: `tests/acd/ACDEngineIdleQueueStatusGateTest` (`@InjectMocks ACDEngine`, `Whitebox` ile grup bazlı `idleUsers` tohumlama) ve `tests/acd/ProgressiveReleasedOriginateReservationTest` (kampanya/grup/keeper kurgusu, `processACD` uçtan uca).

Yeni sınıf: `tests/acd/AvailableUsersCountDisabledGroupRegressionTest`.

1. **Karakterizasyon (mevcut kodda da GREEN):** tek aktif grup, 2 Available agent → sayı 2; keeper'da kayıtlı çağrısı olan Available agent bir kez sayılır.
2. **RED → GREEN (birim):** aktif grup 1 Available + pasif grup 6 Available → sayı **1** (bugün 7).
3. **RED → GREEN (uçtan uca, `processACD`):** PROGRESSIVE_RELEASED, boost 0,65, aktif grupta 1 Available agent, pasif grupta 6 Available → `floor(1 × 0,65) = 0` → `call.getUser() == null` (bugün agent atanıyor). Varyant: aktif grupta 2 Available → ilk çağrı agent alır, keeper'da 1 not-bridged çağrı varken ikinci çağrı almaz.
4. **RED → GREEN (`isDialingBoostNotReached`):** PROGRESSIVE_RESERVED boost 2, aktif grup 1 Available + pasif grup 6 Available, 2 not-bridged çağrı → üçüncü çağrı agent almaz (bugün alıyor).
5. **Açık Soru 2 evet ise:** skill açık kampanya, aktif grupta skill'i eksik Available agent sayılmaz.
6. **Regresyon güvencesi:** yukarıdaki mevcut testler koşulur.

## Uygulama Kontrol Listesi

- [x] Açık sorular cevaplandı ve plana işlendi (22.09.2026).
- [x] Geliştirici planı onayladı (22.09.2026; açık soru cevaplarıyla birlikte uygulama başlatıldı).
- [x] Karakterizasyon testleri yazıldı, mevcut kodda GREEN (3 test: tek aktif grup 2 → 2; keeper+idle aynı agent 1 kez; skill seti boş → şart yok).
- [x] Regression testleri yazıldı, mevcut kodda RED doğrulandı (6 failure: pasif grup 1 yerine **7**, ekran/bütçe eşitliği 2 yerine **8**, skill 1 yerine **2**, üç uçtan uca senaryoda agent atandı).
- [x] `getCampaignAvailableUsersCount` düzeltildi: `countCampaignAvailableUsersNow` ile ortak `campaignAvailableUsersStream` (aktif grup + null-güvenli skill filtresi + `distinct`), yan etki korundu.
- [x] Yeni testler GREEN (`AvailableUsersCountDisabledGroupRegressionTest` 9/9).
- [x] Etkilenen mevcut testler GREEN (7 sınıf, 46/46). Geniş koşu `**/acd/*Test`: `tests.acd` 11 sınıf + legacy `ACDEngineTest` 39/39 yeşil; legacy `DefaultFindMatchingUserStrategyTest` 6/5 kırık **pre-existing** (ACDEngine değişikliği stash'lenip orijinal kodla koşuldu: aynı 5 failure; test 06.07.2026'dan beri güncellenmemiş, strateji 10.08.2026'da değişmiş; 17.09 commit notu da 5 kırık sınıfı kaydediyor).
- [x] History kaydı (uygulama) eklendi (`docs/history/22.09.2026.md`).
- [x] Commit `10d46c99` atıldı ve dal `origin`'a push edildi (22.09.2026, kullanıcı onayıyla); PR/release/develop taşıma ekip tarafından manuel yürütülür (Açık Soru 1 cevabı).
- [ ] PRP doğrulaması (test ekibi): aktif+pasif gruplu kampanyada eşzamanlı ring ≤ floor(aktif avail × boost), agent başına ≤ 1 ringing.
- [ ] Canlı doğrulama (test ekibi / operasyon): camp 164'te ring/agent ≤ 1 ve abandon oranının düşmesi (aynı sorgu seti).

## Hedefli Doğrulama Komutları

`.codex/TOOL-PATHS.local.properties` değerleriyle (MAVEN_CMD, `-s MAVEN_SETTINGS`, `-Dmaven.repo.local`, JAVA_HOME) offline:

- `mvn -o -Dtest=AvailableUsersCountDisabledGroupRegressionTest test`
- `mvn -o -Dtest=ProgressiveReleasedOriginateReservationTest,PredictiveAlphaOriginateReservationTest,ACDEngineIdleQueueStatusGateTest,AcdPriorityAwareLcdDistributionTest,CampaignActiveUserCountBatchCharacterizationTest,ProgressiveReservedReservationRegressionTest test`

Frontend değişikliği yok → Karma/Jasmine koşumu gerekmiyor.

## Doğrulama Sonucu (22.09.2026)

| Koşu | Komut (offline, TOOL-PATHS ile) | Sonuç |
| --- | --- | --- |
| RED (düzeltme öncesi) | `mvn -o -Dtest=AvailableUsersCountDisabledGroupRegressionTest test` | 9 test, **6 failure** (beklenen), 3 karakterizasyon GREEN |
| GREEN (düzeltme sonrası) | aynı sınıf + 6 etkilenen sınıf | **46/46** GREEN, BUILD SUCCESS |
| Geniş ağ | `mvn -o -Dtest=**/acd/*Test,**/acd/**/*Test test` | `tests.acd` 11 sınıf + `ACDEngineTest` 39 GREEN; `DefaultFindMatchingUserStrategyTest` 5 failure (pre-existing, aşağıda) |
| Pre-existing kanıtı | ACDEngine değişikliği `git stash` ile çıkarılıp `-Dtest=DefaultFindMatchingUserStrategyTest` | orijinal kodda da 6 test / **aynı 5 failure**; stash geri alındı |

Değişen dosyalar: `ACDEngine.java` (+47/−21, iki metot + iki private yardımcı), yeni `tests/acd/AvailableUsersCountDisabledGroupRegressionTest.java`, plan ve history dokümanları. Başka üretim dosyasına dokunulmadı.

## Kapanış Kriteri

- Yeni ve etkilenen testler GREEN; üretim kodu değişikliği tek metotla sınırlı.
- PR onaylanan release hedefine ve develop'a alındı.
- PRP'de aktif+pasif gruplu kampanyada bütçe yalnız aktif grubu izliyor.
- Canlıda camp 164'te tek agent'a çoklu ringing yığılması yok; müşteri pasif/aktif geçişini silmeden yapabiliyor.

## Açık Sorular

Tümü 22.09.2026'da kullanıcı tarafından cevaplandı; cevaplanmamış açık soru yok.

1. **Release hedefi ve taşıma:** ~~fix doğrudan `release/6.7.8`'e mi alınacak, yeni `release/6.7.9` mi açılacak? NonTurkcell ve diğer setler de 6.7.8 mi? develop'a taşıma merge mi cherry-pick mi?~~ **Cevap:** release/deploy akışını ekip manuel yönetiyor; plan bu kısma girmez. Tek şart: develop'taki önceki task'lar (325470, 325559) bu fix'in dokunduğu alanlara dokunmamalı. **Doğrulandı:** `git diff --stat origin/release/6.7.8 origin/develop -- gbpowerdialer/src/main/java/com/powerdialer/engine/acd/ gbpowerdialer/src/test/java/tests/acd/` boş; bu fix yalnız `ACDEngine.java` ve `tests/acd/` altına dokunur.
2. **Skill filtresi:** **Cevap: evet, eklenecek.** Bütçe tabanı `countCampaignAvailableUsersNow` ile aynı tanımı kullanır (aktif grup + skill + distinct). Uygulama: ortak bir `campaignAvailableUsersStream(campaign)` yardımcı stream'i; iki metot da bunu kullanır (tek "avail agent" tanımı). Skill kontrolü null-güvenli yazılır: `skillEnabled=true` fakat kampanya skill seti boş/null ise skill şartı yok sayılır (`isUserHasMatchingSkillsAndGroup` ile aynı yorum); `skillWeightPair` null olan agent skill şartını geçemez. Mevcut `countCampaignAvailableUsersNow` yalnız bu NPE kenar durumlarında farklılaşır, diğer tüm girdilerde bit-eşit kalır.
3. **Cevap anı `%35` kapısı:** **Cevap: ayrı iş.** Bu planda dokunulmaz; kapsam dışı bölümünde kalır.
4. **PRP doğrulama kampanyası:** **Cevap:** test ekibi yürütecek. Bu planın kapanışı için ajan tarafında yalnız birim/regresyon testleri; PRP ve canlı doğrulama maddeleri test ekibine devredilir (kontrol listesinde işaretlendi).
