# FIX SaveInitialCDR 901 Hotfix Uygulama Planı

## Analiz Referansı

`docs/analyse/28.04.2026/FIX-GLB90048674-SAVEINITIALCDR-901-HOTFIX.md`

## Onaylanan Plan Özeti

Hotfix dar kapsamda uygulanacak. `saveOrUpdate` revert edilmeyecek. `saveInitialCDR` ile erken persist edilmiş CDR satırı, AMI hangup geldiğinde kullanıcı result code beklediği için `processCallResult` ertelense bile gerçek AMI telephony result ile güncellenecek. Cleanup, AMI sonucu önceden set edilmiş bu kayıtları 901 ile ezmeyecek.

## Amaç

`saveInitialCDR` ile açılan `pd_cdr` kayıtlarının AMI hangup sonucu alınmasına rağmen cleanup sırasında 901 ile kapanmasını engellemek.

## Kapsam Dışı

- `saveOrUpdate` davranışını geri almak.
- `saveInitialCDR` içindeki `callEndTime` required DB contract'ını değiştirmek.
- CDR persistence mimarisini async queue veya geniş refactor ile yeniden tasarlamak.
- Campaign counter, retry, DNC veya user result code lifecycle'ını değiştirmek.

## Performans Etkisi

- Hot path durumu: AMI `DialerHangupEvent` hot path.
- Call/event/tick başına ek maliyet: Yalnız `processCallResult` çağrılmayan ve CDR'si erken persist edilmiş branch'te bir ek CDR `saveOrUpdate`.
- IO/scan/lock/serialization/log etkisi: Yeni scan, query, serialization veya verbose log yok. Mevcut CDR update boundary'si kullanılır.
- Azaltım veya ölçüm notu: Değişiklik `cdr.id != null` ve `processCallResult` ertelenen branch ile sınırlandırılır.

## TDD Planı

- [x] `ManagerConnectionDecorator` için, kullanıcı result code beklenen AMI hangup branch'inde CDR snapshot'ın gerçek AMI sonucu ile persist edildiğini kanıtlayan regression testi.
- [x] `CallCleaner` için, AMI sonucu zaten set edilmiş erken persist CDR'nin cleanup sırasında 901 ile ezilmediğini kanıtlayan regression testi.
- [x] `CallCleaner` için, AMI sonucu olmayan cleanup davranışının 901 olarak kaldığını kanıtlayan kontrol testi.

## Uygulama Kontrol Listesi

- [x] Onaylanan planın tüm adımları execution plan'a işlendi.
- [x] Etkilenen kod alanlarını ve test boundary'lerini hazırla.
- [x] Testleri kırmızı hâle getir.
- [x] `ManagerConnectionDecorator.handleDialerHangup` içinde ertelenen result branch'i için CDR hangup snapshot persist et.
- [x] `CallCleaner.clearExpiredCall` içinde mevcut AMI sonucu olan erken persist edilmiş CDR'yi 901 ile overwrite etme.
- [x] Hedefli testleri yeşile çevir.
- [x] Gerekliyse küçük refactor yap.
- [x] Refactor sonrası hedefli testleri tekrar çalıştır.
- [x] Performans etkisi notunu gerçek patch'e göre güncelle.
- [x] History güncellemesini tamamla.

## Doğrulama

Planlanan hedefli doğrulama:

```text
mvn -o -Dtest=SaveInitialCdrHangupResultRegressionTest,CallCleanerTelephonyResultRegressionTest test
```

Yerel Maven komutu `.codex/TOOL-PATHS.local.properties` içindeki `MAVEN_CMD`, `MAVEN_SETTINGS`, `MAVEN_REPO_LOCAL` ve `JAVA_HOME` değerleriyle çalıştırılacak.

Gerçekleşen doğrulama:

1. Standart hedefli Maven komutu denendi:
   - `mvn -o -Dtest=SaveInitialCdrHangupResultRegressionTest,CallCleanerTelephonyResultRegressionTest test`
2. Komut, bu işten bağımsız mevcut gamma test compile hataları nedeniyle `testCompile` aşamasında bloklandı:
   - `PredictiveGammaCallManagerTest`
   - `PredictiveGammaCallCalculatorTest`
3. İzole hedefli doğrulama için test classpath üretildi:
   - `mvn -o -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/test-classpath.txt`
4. Üretim kodu derlendi:
   - `mvn -o -q -DskipTests compile`
5. Yeni testler izole olarak derlendi:
   - `javac -cp "gbpowerdialer/target/classes;<test classpath>" -d gbpowerdialer/target/manual-cdr-hotfix-test-classes SaveInitialCdrHangupResultRegressionTest.java CallCleanerTelephonyResultRegressionTest.java`
6. Kırmızı koşu sonucu:
   - `SaveInitialCdrHangupResultRegressionTest`: `cdrService.saveOrUpdate` çağrılmadığı için failure.
   - `CallCleanerTelephonyResultRegressionTest`: mevcut AMI sonucu 901 ile ezildiği için failure.
7. Hotfix sonrası izole JUnit koşusu:
   - `java -cp "gbpowerdialer/target/manual-cdr-hotfix-test-classes;gbpowerdialer/target/classes;<test classpath>" org.junit.runner.JUnitCore tests.ami.SaveInitialCdrHangupResultRegressionTest tests.ami.CallCleanerTelephonyResultRegressionTest`
   - Sonuç: `OK (3 tests)`

## Kapanış Kriteri

- [x] Planlanan hedefli regression testleri çalıştırıldı ve başarılı oldu.
- [x] Kod değişikliği yalnız CDR hotfix kapsamıyla sınırlı kaldı.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Riskler ve Geri Dönüş

- `handleDialerHangup` hot path olduğu için ek DB update yalnız ertelenen result branch'i ve erken persist edilmiş CDR ile sınırlı tutulur.
- Geri dönüş gerekirse `ManagerConnectionDecorator` snapshot persist çağrısı ve `CallCleaner` overwrite guard'ı birlikte geri alınabilir.

## Tamamlanma Notu

Hotfix uygulandı. AMI hangup sonucu kullanıcı result code beklediği için `processCallResult` ertelenen çağrılarda erken persist edilmiş CDR satırına telephony snapshot yazılıyor. Cleanup, aynı erken persist edilmiş CDR üzerinde AMI sonucu zaten varsa bunu 901 ile ezmiyor; AMI sonucu hiç yoksa mevcut 901 davranışı korunuyor.
