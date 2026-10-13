# Test ve Güvenli Değişiklik

gbpowerdialer üretimde çalışan telephony davranışına sahiptir. Bu nedenle kod değişikliği varsayılan olarak TDD ile ilerler; eski davranış belirsizse önce karakterizasyon testi yazılır.

## Test Yığını

- JUnit4.
- Mockito 3.3.3.
- PowerMock 2.0.9.
- Spring test ve MockMvc.
- Maven Surefire.
- AngularJS unit testleri için Karma, Jasmine, Chrome Headless ve angular-mocks 1.4.4.

## Tercih Edilen Test Sırası

1. Kullanıcı derdini anlattıktan sonra kısa tespitlerini, risklerini ve çözüm yönünü sun; ardından analize geçelim mi diye sor.
2. Onay gelirse mümkünse task id iste; task id yoksa developer username'ini kimlik olarak kullan.
3. Sorun ve çözüm yönü netleştiğinde kullanıcıya `Plan aşamasına geçeyim mi?` diye sor; uygun plan türünü ve plansız doğrudan uygulama seçeneğini belirt.
4. Kullanıcı planlı ilerlemeyi seçerse yeni feature için `docs/analyse/GG.AA.YYYY/FEAT-KIMLIK-KISA-AD.md` altında analiz yaz.
5. Kullanıcı planlı ilerlemeyi seçerse yeni feature kod değişikliği için `docs/execution-plan/GG.AA.YYYY/FEAT-KIMLIK-KISA-AD.md` altında checkbox'lı uygulama planı oluştur.
6. Kullanıcı planlı ilerlemeyi seçerse fix, hotfix, change request, refactor ve feature dışı diğer işler için `docs/change-fix-plan/GG.AA.YYYY/ETIKET-KIMLIK-KISA-AD.md` altında analiz detayları ve execution plan bölümü olan tek dosya oluştur.
7. Kullanıcı plansız doğrudan uygulamayı seçerse plan dosyası açma; çözümü etkileyen belirsizlik varsa kod değişikliğinden önce kullanıcıya sor.
8. Plan dosyasındaki `Açık Sorular` bölümünde cevaplanmamış madde varsa bunları kullanıcıya sor ve yanıt gelmeden kod değişikliğine başlama.
9. Değiştirilecek davranışın mevcut owner sınıfını bul.
10. Riskli legacy kodsa mevcut davranışı karakterizasyon testiyle kilitle.
11. Hata düzeltmesi veya yeni özellik için önce başarısız test yaz.
12. En küçük uygulama kodu değişikliğini yap.
13. Refactor gerekiyorsa test yeşilken küçük adımlarla uygula.
14. Hedefli Maven testini çalıştır.
15. Planlı akışta planlanan testler başarılıysa plan kapanış kriterini işaretle.
16. Sonuç ve çalıştırılamayan test varsa nedeni history kaydına yaz.
17. Final yanıtta kısa Test Özeti sun.

## Test Dosyası Standardı

Mevcut testler `gbpowerdialer/src/test/java/com/powerdialer` altında kalabilir. Yeni testlerde tercih edilen kök:

```text
gbpowerdialer/src/test/java/tests/<domain>
```

Örnek domain'ler:

- `dialer`
- `ami`
- `redis`
- `angular`
- `acd`
- `campaign`
- `scheduler`
- `data`
- `api`

Yeni test sınıfı adı `<Konu><Davranış><TestTipi>Test` formatında yazılır.

Örnekler:

- `tests/dialer/ActiveCallCounterDriftRegressionTest.java`
- `tests/redis/UserSessionMessageContractTest.java`
- `tests/ami/OriginateActionVariablesCharacterizationTest.java`

Mevcut legacy test doğrudan ilgili davranışı zaten kapsıyorsa aynı test sınıfı genişletilebilir; yeni domain klasörü zorunlu değildir.

AngularJS/frontend testleri için tercih edilen alt kök:

```text
gbpowerdialer/src/test/java/tests/angular/<domain>
```

Bu kök kısa vadeli Java/JUnit contract ve karakterizasyon testleri içindir. Route, WRO bundle sırası, template binding, localization key ve backend response shape contract'ları burada korunur.

Frontend domain örnekleri:

- `routing`
- `bundling`
- `localization`
- `campaign`
- `forms`
- `grid`
- `services`

Örnekler:

- `tests/angular/routing/CampaignRoutesCharacterizationTest.java`
- `tests/angular/bundling/WroGlobalBundleOrderContractTest.java`
- `tests/angular/localization/LocalizationKeysContractTest.java`

Gerçek AngularJS service, factory, controller veya directive davranışı için orta vadeli test kökü:

```text
gbpowerdialer/src/test/angular/<domain>
```

Spec dosyaları `<bilesen>.spec.js` formatında yazılır ve Karma/Jasmine/angular-mocks ile çalıştırılır.

Örnekler:

- `src/test/angular/services/alertService.spec.js`
- `src/test/angular/controllers/campaignListController.spec.js`

AngularJS unit test komutu:

```powershell
npm.cmd run test:angular
```

PowerShell'de `npm.ps1` execution policy nedeniyle engellenirse `npm.cmd` kullanılmalıdır.

## Offline Cache Hataları

VPN kullanılamayan Codex ortamında Maven dependency indirme denenmez. `mvn -o test` Surefire provider, Maven plugin veya başka bir local cache eksiği nedeniyle test runner başlamadan durursa bu test failure olarak raporlanmaz. Eksik artifact açık yazılır; geliştirici VPN'de cache'i güncelledikten sonra aynı offline Maven komutu tekrar denenir.

## Yüksek Riskli Test Alanları

- `DialerFacade`, `CampaignContext`, `CallProducer`, `CallConsumer`, `CallOriginator`.
- `ManagerConnectionComposition`, `ManagerConnectionDecorator`, `ManagerActionFactory`.
- AMI event sınıfları ve AGI script'leri.
- `RedisUtil`, subscriber ve publisher sınıfları.
- `ACDEngine`, `UserHolder`, `CallUpToDateKeeper`.
- `Campaign` runtime counter'ları.
- Scheduler chain ve otomatik kampanya başlatma.
- Retry ve end-list behavior stratejileri.

## Test Yazılamayan Durumlar

Bazı alanlar static state, private constructor, dış AMI bağlantısı veya Spring XML bağı nedeniyle zor test edilebilir. Bu durumda:

- Neden test yazılamadığı açıkça yazılır.
- En yakın adapter veya pure function seviyesi bulunur.
- PowerMock son seçenek olarak kullanılır.
- Manuel doğrulama adımları netleştirilir.
- Sonraki refactor için test edilebilir küçük boundary önerilir.

## Safe Change Contract

- Hata düzeltmesi ile geniş refactor aynı değişiklikte karıştırılmaz.
- Kod değişikliği önce kısa tespit ve çözüm önerisi mesajıyla başlar; analiz yalnız kullanıcı onayından sonra açılır.
- Kullanıcı planlı ilerlemeyi seçtiyse kod değişikliği ilgili plan dosyası olmadan uygulanmaz: yeni feature için `docs/execution-plan`, feature dışı işler için `docs/change-fix-plan`.
- Kullanıcı plansız doğrudan çözümü seçtiyse plan dosyası açılmaz.
- Plan, TDD test planı ve hedefli doğrulama komutlarını içerir.
- Plan dosyasında cevaplanmamış açık soru varsa kod değişikliğine geçilmez.
- Planlanan testler çalıştırılıp başarılı olmadan plan kapatılamaz; başarısız veya çalıştırılamayan test varsa doğrulama beklemede kalır.
- Her çalışma sonunda çalıştırılan testler ve durumları kısa Test Özeti olarak sunulur.
- Redis channel, AMI variable, endpoint path ve DTO shape geriye uyumluluk notu olmadan değişmez.
- Runtime counter davranışı değiştirilmeden önce writer/reader matrisi çıkarılır.
- Yeni feature flag gerekiyorsa default üretim davranışını korur.
- AngularJS contract testleri `src/test/java/tests/angular`, gerçek AngularJS unit testleri `src/test/angular` altında tutulur.
- Commit öncesi diff, secret ve ilgisiz dosya kontrolü yapılır.

## İlgili Skill'ler

- `gbpd-tdd-workflow`
- `gbpd-angularjs-tdd`
- `gbpd-angularjs-legacy-ui`
- `gbpd-characterization-tests`
- `gbpd-safe-change`
- `gbpd-safe-refactor`
- `gbpd-runtime-state-audit`
- `gbpd-commit-discipline`
