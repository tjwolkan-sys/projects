---
name: gbpd-characterization-tests
description: gbpowerdialer legacy davranışını riskli değişikliklerden önce testle kilitleme akışı. JUnit4, Mockito, PowerMock, Spring MockMvc, retry logic, scheduler chain, AMI adapter, Redis publisher, OAuth/RDS boundary veya kırılgan private/static kodlarda kullan.
---

# gbpd-characterization-tests

## Akış

1. Aynı package veya davranış için yakındaki testleri bul.
2. Önce mevcut davranışı değiştirmeden testle kilitle.
3. Mümkünse plain JUnit4 ve Mockito kullan.
4. Controller veya context davranışı testleniyorsa Spring test desteği kullan.
5. Static/private construction daha basit test boundary'sini engelliyorsa PowerMock kullan; yeni kodda PowerMock ihtiyacını büyütme.
6. Testleri deterministik tut: gerçek Redis, AMI, DB, Eureka, OneAccess, dashboard veya gRPC çağrısı yapma.
7. Hedefli komut tercih et: `mvn -Dtest=ClassName test`.

## Öncelikli Alanlar

- `ManagerConnectionComposition`: property parsing, connection kararları, timeout fallback, originated call lookup.
- `ManagerConnectionDecorator`: AMI event routing, call cache, hangup/result akışı.
- `CampaignSchedulerOrchestrator` ve schedule chain sırası.
- `UserSubscriber`, `WebphoneSubscriber` ve Redis serialization contract'ları.
- `ACDEngine`: user availability, group/skill matching, idle/active queue davranışı.
- `CampaignStopJob`: stop/wait/forceful stop davranışı.
- `ResourceDistributionServiceFacade`, OneAccess converter/service ve predictive dispatcher'lar.

## Stil

- Test ismini implementation detayına göre değil, davranışa göre ver.
- Entity fixture'larında yalnızca branch için gereken alanları set et.
- Testte sleep kullanma; mümkünse clock veya executor üzerinden kontrol edilebilir test boundary'si ekle.
- Legacy private state için yalnızca testte reflection kullanmak, üretim API'sini test için genişletmekten daha iyidir.
