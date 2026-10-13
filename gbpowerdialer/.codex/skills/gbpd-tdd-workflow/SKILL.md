---
name: gbpd-tdd-workflow
description: gbpowerdialer'da her hata düzeltmesi ve özellik geliştirmesini TDD ile yürütme akışı. Java backend, AngularJS frontend, legacy hata düzeltmesi, refactor öncesi test, karakterizasyon testi, regression test, contract test, Karma/Jasmine spec veya Maven/JUnit4/Mockito test planı gerektiğinde kullan.
---

# gbpd-tdd-workflow

## Temel Kural

gbpowerdialer'da her geliştirme testle başlar. Legacy davranış belirsizse önce karakterizasyon testi yazılır; istenen yeni davranış için başarısız test eklenir; sonra en küçük üretim kodu değişikliği yapılır.

## Analiz ve Plan Ön Koşulu

1. Kod değişikliği gerektiren istekte önce iş tipini belirle.
2. Sorun ve çözüm yönü netleştiğinde kullanıcıya `Plan aşamasına geçeyim mi?` diye sor; uygun plan türünü açık yaz: yeni feature için `new feature planı`, feature dışı işler için `change-fix planı`.
3. Aynı mesajda plansız doğrudan çözümü uygulama seçeneğini de söyle ve kendi önerini kısa gerekçeyle belirt.
4. Kullanıcı plansız uygulamayı seçerse analiz, execution plan veya change-fix-plan dosyası açma; çözümü etkileyen belirsizlik varsa kod değişikliğinden önce kullanıcıya sor.
5. İş etiketini commit tipi standardına göre belirle: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`, `ci`, `perf` veya `revert`.
6. Kullanıcı planlı ilerlemeyi seçerse yeni feature işleri için önce `docs/analyse/GG.AA.YYYY/FEAT-KIMLIK-KISA-AD.md` altında analiz, ardından `docs/execution-plan/GG.AA.YYYY/FEAT-KIMLIK-KISA-AD.md` altında checkbox'lı execution plan oluştur.
7. Kullanıcı planlı ilerlemeyi seçerse fix, hotfix, change request, refactor, perf, test, build, ci, chore, revert ve feature dışı diğer işler için `docs/change-fix-plan/GG.AA.YYYY/ETIKET-KIMLIK-KISA-AD.md` altında tek dosyada analiz detayları ve execution plan bölümü oluştur.
8. Planlama dokümanında mevcut davranış, beklenen davranış, etkilenen kod alanları, riskler, TDD stratejisi, hedefli doğrulama komutları, kapanış kriteri ve `Açık Sorular` bölümü bulunsun.
9. Plan yazıldıktan sonra `Açık Sorular` bölümünde cevaplanmamış madde varsa bunları kullanıcıya açıkça sor; kullanıcı ayrıca "açık sorular neler" demeden bekleme.
10. Cevaplanmamış açık soru varken kod değişikliğine geçme; yanıtları plana işlemeden üretim kodu, test veya config değiştirme.
11. Planlı akışta kod değişikliğini plan üzerinden yürüt ve tamamlanan adımları işaretle.
12. Plan dışına çıkarsan önce planı güncelle, açık soru doğduysa kullanıcıdan yanıt al, sonra uygulamaya devam et.
13. Planlanan testler çalıştırılıp başarılı olmadan planı tamamlandı/kapatıldı sayma; test çalıştırılamıyorsa plan doğrulama beklemede kalır.

## Red-Green-Refactor Akışı

1. Değişecek davranışı tek cümleyle yaz.
2. En yakın test dosyasını bul; yoksa yeni test dosyasını test dosyası standardına göre aç.
3. Mevcut davranış riskliyse önce karakterizasyon testi ile bugünkü davranışı kilitle.
4. İstenen davranış için başarısız test yaz.
5. Yalnızca testi geçirecek kadar üretim kodu değiştir.
6. Hedefli testi çalıştır:
   - Java/Maven: `mvn -o -Dtest=ClassName test`
   - AngularJS/Karma: `npm.cmd run test:angular`
7. Yeşil olduktan sonra yalnızca güvenli ve küçük refactor yap.
8. Refactor sonrası aynı hedefli testi tekrar çalıştır.

## Yerel Tool Path Ön Koşulu

Maven, Java, Tomcat veya Node gerektiren test/doğrulama çalıştırmadan önce repo kökündeki `.codex/TOOL-PATHS.local.properties` dosyasını kontrol et.

- Dosya varsa `MAVEN_CMD`, `MAVEN_SETTINGS`, `MAVEN_REPO_LOCAL`, `JAVA_HOME`, `CATALINA_HOME`, `TOMCAT_HOME` ve `NPM_CMD` değerlerini kullan.
- `MAVEN_CMD` varsa PATH'teki `mvn` yerine bu binary ile çalıştır.
- `MAVEN_SETTINGS` varsa Maven komutuna `-s <settings.xml>` ekle.
- Maven komutlarına varsayılan olarak `-o` ekle; ajan dependency indirmeyi denemez.
- `MAVEN_REPO_LOCAL` varsa Maven komutuna `-Dmaven.repo.local=<repo>` ekle.
- `JAVA_HOME` varsa komuttan önce ortam değişkenini set et ve `JAVA_HOME/bin` yolunu PATH'in başına al.
- Offline Maven eksik artifact nedeniyle durursa artifact adını ve versiyonunu raporla; geliştiriciden VPN'de `.m2`/Nexus cache'i güncelleyip VPN'den çıktıktan sonra tekrar ajana dönmesini iste.
- Maven `test` yalnızca Surefire provider/local cache eksiği nedeniyle çalışamıyorsa alternatif test runner deneme; eksik artifact'i raporla ve geliştirici cache güncelledikten sonra aynı offline Maven komutunu tekrar çalıştır.
- Local dosya yoksa PATH'teki araçları kullan; araç bulunamazsa bunu doğrulama sonucu olarak açıkça raporla.
- Gerçek path'leri yalnızca `.local.properties` dosyasında tut; şablon ve dokümanlarda yer tutucu kullan.

## Java Test Dosyası Standardı

Mevcut legacy testler `gbpowerdialer/src/test/java/com/powerdialer` altında kalabilir. Yeni testlerde tercih edilen kök:

```text
gbpowerdialer/src/test/java/tests/<domain>
```

Package adı:

```text
tests.<domain>
```

Yeni test sınıfı adı:

```text
<Konu><Davranış><TestTipi>Test
```

Test tipi örnekleri:

- `Characterization`
- `Regression`
- `Contract`
- `Feature`

Örnek dosyalar:

- `gbpowerdialer/src/test/java/tests/dialer/ActiveCallCounterDriftRegressionTest.java`
- `gbpowerdialer/src/test/java/tests/redis/UserSessionMessageContractTest.java`
- `gbpowerdialer/src/test/java/tests/ami/OriginateActionVariablesCharacterizationTest.java`

Mevcut test sınıfı doğrudan ilgili davranışı kapsıyorsa aynı test sınıfı genişletilebilir. Yeni davranış başka domain'e aitse yeni `tests/<domain>` klasörü tercih edilir.

## AngularJS Test Dosyası Standardı

Frontend değişikliklerinde `gbpd-angularjs-tdd` skill'i de kullanılmalıdır.

Kısa vadeli Java/JUnit contract testleri için kök:

```text
gbpowerdialer/src/test/java/tests/angular/<domain>
```

Package adı:

```text
tests.angular.<domain>
```

Orta vadeli gerçek AngularJS unit testleri için kök:

```text
gbpowerdialer/src/test/angular/<domain>
```

Spec dosyası formatı:

```text
<bilesen>.spec.js
```

Frontend domain örnekleri:

- `routing`
- `bundling`
- `localization`
- `campaign`
- `forms`
- `grid`
- `services`

Route, WRO, template ve backend response shape değişikliklerinde Java/JUnit contract testi; service/factory/controller davranışında Karma/Jasmine/angular-mocks testi tercih edilir. İki katman birbirinin yerine geçmez.

## Legacy İstisnaları

- Gerçek AMI, Redis, DB, Eureka, OneAccess veya gRPC bağlantısı kurma.
- Static/private legacy kod test edilemiyorsa önce küçük bir adapter veya pure function boundary ara; gerekirse reflection veya PowerMock kullan.
- Async/race davranışında sleep ile test yazma; executor, clock veya callback kontrol noktası kullan.
- Production bug acilse bile en azından regression test eklemeden davranış değiştirme.

## Test Önceliği

- Counter drift ve state transition bug'ları.
- AMI event routing ve hangup/result akışı.
- Redis payload serialization ve subscriber davranışı.
- Campaign scheduler/start/stop/pause karar mantığı.
- ACD agent matching ve availability hesapları.
- Retry/callback/DNC/LSC yan etkileri.
- AngularJS route, WRO bundle, localization, template binding ve backend response contract'ları.
- AngularJS service, factory ve controller state davranışları.

## Final Çıktı

Her TDD çalışmasının sonunda şunları raporla:

- Yazılan başarısız test.
- Yapılan üretim kodu değişikliği.
- Kısa Test Özeti: çalıştırılan test komutları ve durumları.
- Çalıştırılamayan test varsa nedeni.
- Plan dosyasında işaretlenen adımlar, açık soru durumu ve plan kapanış durumu.
