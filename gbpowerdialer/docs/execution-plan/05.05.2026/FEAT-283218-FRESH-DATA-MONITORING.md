# FEAT Fresh Data Monitoring Uygulama Planı

## Analiz Referansı

`docs/analyse/05.05.2026/FEAT-283218-FRESH-DATA-MONITORING.md`

## Onaylanan Plan Özeti

Task `283218` için monitoring ekranına `Fresh Data` metriği eklenecek. Yeni alan backend runtime counter olarak üretilecek, `realtimeInfo` DTO response'una taşınacak ve AngularJS campaign grid'inde retry/callback listeleri yanında gösterilecek.

## Amaç

Hiç aranmamış fresh data sayısını `List Remaining` toplamından ayrı görünür kılmak.

## Kapsam Dışı

- Campaign fetch/originate seçim algoritmasını değiştirmek.
- Retry veya callback liste tanımını değiştirmek.
- Yeni DB kolon migration'ı yapmak.
- Monitoring polling interval veya permission modelini değiştirmek.

## Performans Etkisi

- Hot path durumu: Campaign producer liste metriklerini güncellerken ek `COUNT(*)` sorgusu çalışacak.
- Call/event/tick başına ek maliyet: Call/event başına maliyet eklenmeyecek; mevcut periyodik liste sayımı akışına bir DB count eklenecek.
- IO/scan/lock/serialization/log etkisi: Java tarafında collection scan, lock, serialization veya verbose log eklenmeyecek.
- Azaltım veya ölçüm notu: Sorgu mevcut retry/callback count pattern'ini kullanacak; polling endpoint'i doğrudan DB sorgusu çalıştırmayacak, runtime `Campaign` alanını DTO'ya map edecek.

## TDD Planı

- [x] Fresh data SQL koşulunu DB bağlantısı açmadan regression test ile kilitle.
- [x] `Campaign` -> `CampaignListDTO` mapping'inde `freshDataListSize` alanını başarısız testle kanıtla.
- [x] `realtimeInfo` JSON response'unda `freshDataListSize` alanını regression testle doğrula.
- [x] AngularJS campaign grid/localization contract'ını uygun hedefli testle doğrula.

## Uygulama Kontrol Listesi

- [x] Onaylanan planın tüm adımları execution plan'a işlendi.
- [x] Etkilenen kod alanlarını hazırla.
- [x] Testi kırmızı hâle getir.
- [x] En küçük üretim kodu değişikliğini yap.
- [ ] Hedefli testi yeşile çevir.
- [x] Gerekliyse küçük refactor yap.
- [ ] Refactor sonrası testi tekrar çalıştır.
- [x] Hot path performans etkisi notunu güncelle.
- [x] Doküman veya history güncellemesini tamamla.

## Doğrulama

Planlanan hedefli komutlar:

```text
mvn -o -Dtest=CampaignFreshDataListSizeSqlRegressionTest,CampaignRuntimeCountersCharacterizationTest,CampaignsResourceTest test
npm.cmd run test:angular
```

Local tool path varsa `.codex/TOOL-PATHS.local.properties` içindeki Maven/JDK/NPM ayarları kullanılacak.

Sonuç:

- `mvn -o -Dtest=CampaignFreshDataListSizeSqlRegressionTest,CampaignRuntimeCountersCharacterizationTest,CampaignsResourceTest,CampaignFreshDataMonitoringContractTest test` çalıştırılamadı; `.codex/TOOL-PATHS.local.properties` yok ve PATH içinde `mvn` bulunmuyor.
- `npm run test:angular` çalıştırıldı; `karma: Permission denied` hatasıyla başladıktan önce durdu. Sandbox dışında tekrar denendi, aynı hata alındı.
- `node node_modules/karma/bin/karma start karma.conf.js --single-run` denendi; `node_modules/karma` bulunmadığı için çalıştırılamadı.
- `git -c core.whitespace=-blank-at-eol,-cr-at-eol diff --check` dokunulan dosyalar için temiz. Standart `git diff --check`, CRLF kullanılan mevcut Java dosyalarında eklenen satırları `trailing whitespace` olarak raporluyor.

## Kapanış Kriteri

- [ ] Planlanan testler çalıştırıldı ve başarılı oldu.
- [x] Test çalıştırılamadıysa environment blocker açık yazıldı.
- [ ] Final yanıtta kısa Test Özeti sunuldu.

## Riskler ve Geri Dönüş

- Fresh data sayımı beklenmeyen DB maliyeti yaratırsa değişiklik tek bir counter, query ve UI kolonu üzerinden geri alınabilir.
- Response'a yalnız yeni field eklendiği için mevcut client contract'ları kırılmamalı.
- SQL koşulu yanlışsa retry/callback listeleri etkilenmeden yalnız fresh data metriği hatalı görünür; query regression testi bu riski azaltacak.

## Açık Sorular

Açık soru yok.

## Tamamlanma Notu

Uygulama tamamlandı. Plan doğrulama beklemede; Maven binary ve Karma/node_modules environment blocker'ları kapandıktan sonra hedefli testler tekrar çalıştırılmalı.
