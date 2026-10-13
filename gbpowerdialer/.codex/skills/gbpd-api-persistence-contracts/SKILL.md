---
name: gbpd-api-persistence-contracts
description: gbpowerdialer Spring MVC/API, request/response DTO, Dozer mapping, GenericController/GenericService/GenericDao, service/DAO, Hibernate entity, transaction, CampaignDbOperations, DBEngine, CampaignRecordService, CampaignDataManipulationService veya dinamik SQL/persistence contract'ı değişirken kullan.
---

# gbpd-api-persistence-contracts

## Amaç

API ve persistence değişiklikleri sadece Java katmanını etkilemez; AngularJS field kullanımı, JSON response shape, Dozer mapping, Hibernate entity, transaction boundary ve kampanya veri tabloları birlikte kırılabilir. Bu skill, bu contract'ları küçük ve testli değişikliklerle korumak içindir.

## Birlikte Kullanılacak Skill'ler

- Kod değişikliği varsa önce `gbpd-tdd-workflow`.
- Eski davranış belirsizse `gbpd-characterization-tests`.
- Frontend endpoint veya field kullanımı etkileniyorsa `gbpd-angularjs-legacy-ui` ve `gbpd-angularjs-tdd`.
- Dinamik kampanya verisi çağrı lifecycle'ına bağlıysa `gbpd-campaign-lifecycle-flow`.
- Redis payload veya dış event DTO'su etkileniyorsa `gbpd-redis-contracts`.

## Önce Oku

1. `docs/architecture/API-AND-UI.md`
2. `docs/architecture/DATA-PERSISTENCE-AND-DB.md`
3. Gerekirse `docs/architecture/CONFIGURATION-AND-SECRETS.md`

## Kritik Kod Yüzeyi

- `gbpowerdialer/src/main/java/com/powerdialer/controller`
- `gbpowerdialer/src/main/java/com/powerdialer/api`
- `gbpowerdialer/src/main/java/com/powerdialer/request`
- `gbpowerdialer/src/main/java/com/powerdialer/response`
- `gbpowerdialer/src/main/java/com/powerdialer/dto`
- `gbpowerdialer/src/main/java/com/powerdialer/service`
- `gbpowerdialer/src/main/java/com/powerdialer/dao`
- `gbpowerdialer/src/main/java/com/powerdialer/model`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/db`

## API Contract Kontrolü

- Endpoint path, HTTP method, status code, content type ve auth beklentisini değiştirmeden önce caller'ları çıkar.
- Request field adı, zorunluluk, default değer ve null davranışını kontrol et.
- Response JSON field adı, tip, boş değer ve liste/object shape değişikliklerini frontend kullanımıyla birlikte ara.
- Controller exception handling ve error response davranışını bozma.
- Existing AngularJS factory/service endpoint kullanımı varsa aynı değişiklikte WRO/template/controller etkisini kontrol et.

## Persistence Contract Kontrolü

- Entity alanı değişirse Hibernate mapping, Dozer mapping, JSON response ve frontend field kullanımını birlikte kontrol et.
- Service/DAO değişikliğinde transaction boundary, lazy loading, session lifecycle ve side effect sırasını çıkar.
- `GenericController`, `GenericService` veya `GenericDao` değişiyorsa tüm türeyen sınıflara etkisini ara.
- `CampaignDbOperations`, `DBEngine`, `CampaignRecordService` veya `CampaignDataManipulationService` değişiyorsa dinamik SQL, tablo/kolon adı escape'i ve SQL Server/Oracle farklarını kontrol et.
- Prepared statement parametresi ile identifier/table/column escape sorumluluğunu karıştırma.
- Upload, import, result update ve campaign data manipulation akışlarında bellek ve uzun transaction riskini not et.

## Test Stratejisi

1. Yeni veya değişen endpoint için MockMvc/JUnit4 contract veya regression testi tercih et.
2. DTO/JSON shape değişiyorsa response field contract testini ekle.
3. Dynamic SQL değişiyorsa query builder seviyesinde küçük karakterizasyon veya regression testi yaz.
4. DAO/service transaction davranışı DB gerektiriyorsa gerçek DB bağlantısı kurmadan test edilebilir pure function veya mock boundary ara.
5. Frontend field kullanımı etkileniyorsa Java/JUnit Angular contract testi veya Karma/Jasmine spec ekle.

## Arama Kalıpları

- `rg -n "@RequestMapping|@GetMapping|@PostMapping|ResponseBody|ResponseEntity" gbpowerdialer/src/main/java/com/powerdialer`
- `rg -n "extends GenericController|extends GenericService|extends GenericDao" gbpowerdialer/src/main/java/com/powerdialer`
- `rg -n "Dozer|Mapper|mapping|dto|request|response" gbpowerdialer/src/main/java gbpowerdialer/src/main/webapp/js`
- `rg -n "CampaignDbOperations|DBEngine|executeQuery|executeUpdate|PreparedStatement|create table|alter table" gbpowerdialer/src/main/java/com/powerdialer`

## Raporlama

- Değişen API/persistence contract'ını ve uyumluluk etkisini yaz.
- Frontend, DTO, mapping, transaction ve dynamic SQL kontrolünden hangilerinin yapıldığını belirt.
- Test çalıştırılamadıysa bunun test failure mı yoksa offline toolchain/cache engeli mi olduğunu ayır.
