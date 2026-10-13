# FEAT İlk TDD Karakterizasyon Testleri Uygulama Planı

## Analiz Referansı

`docs/analyse/21.04.2026/FEAT-ILK-TDD-KARAKTERIZASYON-TESTLERI.md`

## Amaç

Java backend ve AngularJS frontend için dar kapsamlı ama kritik ilk TDD karakterizasyon/contract testlerini eklemek.

## Kapsam Dışı

- Üretim kodu değişikliği.
- Yeni JavaScript test runner kurulumu.
- Browser tabanlı e2e test.
- Tüm kampanya veya tüm Angular route envanterini kapsama.

## TDD Planı

- [x] Java runtime counter davranışını karakterizasyon testiyle kilitle.
- [x] AngularJS route ve WRO contract'ını contract testiyle kilitle.
- [x] Hedefli Maven test komutlarını dene veya çalıştırılamıyorsa nedenini yaz.

## Uygulama Kontrol Listesi

- [x] `tests/campaign` altında Java karakterizasyon testini oluştur.
- [x] `tests/angular/routing` altında AngularJS contract testini oluştur.
- [x] Testleri üretim kodu değiştirmeden yaz.
- [x] Hedefli test komutlarını çalıştırmayı dene.
- [x] `docs/analyse/21.04.2026/README.md` indeksini güncelle.
- [x] `docs/execution-plan/21.04.2026/README.md` indeksini güncelle.
- [x] `docs/README.md` indeksini güncelle.
- [x] History kaydını güncelle.

## Doğrulama

Hedefli komutlar:

```text
mvn -Dtest=CampaignRuntimeCountersCharacterizationTest test
mvn -Dtest=CampaignRoutesAndBundleContractTest test
```

## Riskler ve Geri Dönüş

- Maven PATH'te yoksa testler bu ortamda çalıştırılamaz; dosya ve derlenebilirlik gözden geçirilir.
- Testler yalnızca mevcut contract'ı kilitler; bug düzeltmesi içermez.
- Sorun olursa eklenen test dosyaları ve indeks güncellemeleri geri alınabilir.

## Tamamlanma Notu

- `CampaignRuntimeCountersCharacterizationTest` eklendi.
- `CampaignRoutesAndBundleContractTest` eklendi.
- Üretim kodu değiştirilmedi.
- `mvn -Dtest=CampaignRuntimeCountersCharacterizationTest test` denendi; `mvn` PATH'te olmadığı için çalıştırılamadı.
- `mvn -Dtest=CampaignRoutesAndBundleContractTest test` denendi; `mvn` PATH'te olmadığı için çalıştırılamadı.
