# Migration Sınırları

Bu belge roadmap değildir. Go mikroservis dönüşümü için mevcut monolit içinde doğal ayrışma boundary'lerini tarif eder. Ayrıntılı plan `docs/project/GO-MICROSERVICE-ROADMAP.md` dosyasında tutulur.

## Ayrılabilecek Teknik Sınırlar

### Asterisk Gateway

AMI bağlantısı, originate action üretimi ve AMI event normalleştirme sorumluluğu zamanla ayrı bir gateway servisine taşınabilir. İlk adım, Java içindeki `ManagerConnectionDecorator` davranışını contract testleriyle kilitlemek ve action/event formatını açık contract'a çevirmektir.

### Call State Projection

Active, ringing, in-call ve originated call state'i increment/decrement yerine event kaynaklı projection olarak modellenebilir. İlk sürüm shadow projection olmalı; Java davranışını değiştirmeden mevcut counter'larla fark raporlamalıdır.

### Agent State Projection

Redis user session/current status, ACD idle queue ve campaign agent counter tek bir projection contract'ına bağlanmalıdır. Bu parça inbound/outbound veri ikiliğini azaltmak için ayrı ele alınmalıdır.

### Redis Contract Bridge

Redis channel ve DTO'lar external system contract'ıdır. Go servisleri doğrudan eski channel'lara yazmadan önce bridge/adapter ile eski ve yeni formatı birlikte desteklemelidir.

### Campaign Orchestrator

Start, pause, stop, reload, reset ve scheduler chain davranışı ileride ayrı orchestration servisine taşınabilir. Bu parça taşınmadan önce kampanya ownership ve distributed lock stratejisi netleşmelidir.

### Data Import ve Campaign Data API

Kampanya veri yükleme, streaming import ve source/result table operasyonları ayrı servis adayıdır. Bu alan çağrı runtime'ından ayrılabilir; ancak DB dialect ve dinamik tablo contract'ları testle korunmalıdır.

### Reporting ve Interaction Notifier

Call report enrichment ve interaction notifier publish akışı ayrı projection/reporting servisine dönüşebilir. Önce DTO contract'ı ve publish idempotency kuralı oluşturulmalıdır.

## Taşıma İlkeleri

- İlk taşıma read-only veya shadow-run olmalıdır.
- Java monolit sahipliği bırakılmadan Go servisi yazma sahibi yapılmaz.
- Her yeni servis idempotent event işleme kuralı taşır.
- Redis pub/sub mesajı kaybolabilir kabul edilir; kritik state için kalıcı store veya replay mekanizması planlanır.
- Her servis Docker üzerinde health check, graceful shutdown ve configuration/secret standardıyla gelir.

## Öncelik Sinyali

İlk ayrılacak parça en az çağrı kesme riski taşıyan ama en çok görünürlük sağlayan parça olmalıdır. Bu nedenle call state projection ve agent/counter drift raporu, doğrudan originate owner'lığını taşımadan önce daha güvenli bir başlangıçtır.
