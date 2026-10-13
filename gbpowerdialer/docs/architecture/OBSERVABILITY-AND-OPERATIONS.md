# Observability ve Operasyon

Mevcut uygulamada temel observability ağırlıklı olarak log, bazı health endpoint'leri ve runtime debug API'leri üzerinden sağlanır. Distributed trace, metric ve durable event geçmişi mevcut mimaride ana unsur değildir.

## Mevcut Görünürlük

- `logback.xml` ile log yapılandırması.
- `LoggerDecorator` ve modül bazlı log kullanımı.
- `ActuatorController` altında `/actuator/info` endpoint'i.
- `ServiceStatusesController` ile servis durum görünürlüğü.
- `AgentSizeHealthResource` ile agent size tutarsızlığı görünürlüğü.
- `DialerFacadeResource` ve `CallResource` ile runtime context ve çağrı sorguları.
- AMI bağlantı durumu `Server` modeli ve loglar üzerinden izlenebilir.

## Operasyonel Kritik Sinyaller

İleride metric olarak toplanması gereken sinyaller:

- Kampanya bazlı active, ringing, in-call ve originated call sayısı.
- Agent sayısı, uygun agent sayısı ve drift farkı.
- Producer fetch süresi ve fetch edilen kayıt sayısı.
- Consumer queue büyüklüğü ve drain sayısı.
- Originator queue büyüklüğü ve originate başarı/hata sayısı.
- AMI bağlantı durumu, reconnect ve login denemeleri.
- Redis publish/subscribe hata sayısı.
- Event lag ve duplicate event oranı.
- Kampanya stop/reload/reset süreleri.

## Eksikler

- Pub/sub mesajlarının replay edilebilir geçmişi yoktur.
- Counter'ların authoritative reconciliation raporu sınırlıdır.
- Çoklu node için ownership, leader election veya distributed lock standardı net değildir.
- Health endpoint'leri tüm kritik dış bağımlılıkları kapsamayabilir.
- Loglarda correlation id standardı tutarlı görünmüyor.

## Operasyon Contract'ı

- Riskli değişiklikte önce hangi log ve endpoint ile doğrulama yapılacağı belirlenir.
- Yeni hata yakalama kodu sessizce yutma yapmaz; kampanya, call id, server ve user bağlamı loglanır.
- Loglara şifre, token, kişisel veri veya müşteri verisi yazılmaz.
- Health endpoint'i ekleniyorsa readiness ve liveness anlamı ayrı düşünülür.
- Docker hazırlığında graceful shutdown ve drain davranışı gözlemlenebilir hâle getirilir.

## İlk İyileştirme Adayları

- Call lifecycle için correlation id standardı.
- Campaign runtime snapshot endpoint'inin tutarlı ve dokümante hâle getirilmesi.
- Redis publish/subscribe failure metric'leri.
- AMI event ve originate response latency metric'i.
- Agent/call counter drift raporunun düzenli üretilmesi.
