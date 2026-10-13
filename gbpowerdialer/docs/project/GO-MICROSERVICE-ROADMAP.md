# gbpowerdialer Go ve Mikroservis Yol Haritası

## Amaç

gbpowerdialer, Bulut Santral'in outbound dial görevini üretimde taşıyan kritik Java monolith uygulamasıdır. Bu roadmap'in amacı, uygulamayı canlı sistemi kesmeden önce observable ve test edilebilir hâle getirmek, sonra Go tabanlı, Docker üzerinde çalışabilen ve yatay ölçeklenebilen mikroservis parçalarına ayırmaktır.

Bu doküman stratejik planı tutar. Uygulama geliştirme sırasında uyulacak operasyonel kurallar `AGENTS.md` ve `.codex/skills` altındaki skill dosyalarındadır.

## Mevcut Durum Özeti

- Uygulama Maven `WAR`, Java 8, Spring MVC/XML config, Spring Security, Hibernate, Redisson, Asterisk Java ve AngularJS 1.x üzerinde çalışıyor.
- Asterisk outbound originate ve event handling `engine/asterisk` ve `engine/dialer` paketlerinde yoğunlaşmış durumda.
- Redis merkezi event ve kullanıcı snapshot katmanı gibi kullanılıyor; fakat asıl runtime state'in büyük kısmı JVM yerel belleğinde tutuluyor.
- `DialerFacade.campaignContexts`, `ACDEngine` queue'ları, `CallUpToDateKeeper`, `ManagerConnectionDecorator.calls` ve `Campaign` counter'ları yatay ölçek önündeki ana engeller.
- Agent/çağrı sayıları farklı event yollarında increment/decrement edilerek tutuluyor; bu, inbound/outbound ve Redis/yerel state arasında drift üretiyor.
- Testler mevcut fakat kritik runtime adapter'ları ve state transition'ları için kapsama yetersiz.

## Dönüşüm İlkeleri

- Big-bang rewrite yok. Strangler pattern ile parça parça ayrılacak.
- Önce test, observability ve reconciliation; sonra write ownership değişimi.
- Java monolith bir süre compatibility adapter olarak kalacak.
- Her yeni servis açık contract ile tasarlanacak: API, event schema, ownership, rollback ve health check net olacak.
- Counter'lar mümkünse authoritative state'ten türetilecek; atomic counter gerekiyorsa reconciliation zorunlu olacak.
- Go servisleri Docker/Kubernetes varsayımlarına uygun tasarlanacak: env config, health/readiness, metrics, graceful shutdown, idempotent consumer.

## Faz 0: Güvenlik Zemini

Hedef: mevcut sistemi kırmadan değişiklik yapabilecek zemin oluşturmak.

Teslimatlar:

- `AGENTS.md` ve proje skill envanteri.
- TDD ve karakterizasyon testi standardı.
- Kritik runtime akışları için test öncelik listesi.
- Maven/test komutlarının geliştirici makinelerinde çalışır hâle getirilmesi.
- Secret ve ortam yapılandırması envanteri.

Başarı ölçütü:

- Yeni hata düzeltmelerinde önce test yazılıyor.
- AMI, Redis, campaign lifecycle ve counter değişiklikleri için review checklist var.

## Faz 1: Observability ve Runtime State Envanteri

Hedef: drift'i ölçmek ve state kaynaklarını görünür yapmak.

Teslimatlar:

- Campaign/call/agent runtime snapshot endpoint'leri.
- Stored counter ile calculated state karşılaştırmaları.
- Agent size, active/ringing/in-call count ve queue size için discrepancy raporları.
- Redis event lag, subscriber queue depth, AMI connection health metrikleri.
- `KEYS userData*` kullanımlarının envanteri ve alternatif indeks tasarımı.

Başarı ölçütü:

- Drift olduğunda hangi writer/event yolunun etkilediği görülebiliyor.
- Üretimde read-only health endpoint'leriyle riskli counter'lar izlenebiliyor.

## Faz 2: Authoritative State ve Reconciliation

Hedef: duplicate source-of-truth sorununu azaltmak.

Teslimatlar:

- Call lifecycle için idempotent state transition modeli.
- Agent state için tek owner kararı: Redis index/read-model veya service-owned state.
- Campaign counter'ları için derived read-model veya reconciliation job.
- Redis user index: `liveUsers` benzeri açık set/hash yapısı; `KEYS` yerine `SCAN` veya indeks.
- Event duplicate/out-of-order davranışları için testler.

Başarı ölçütü:

- Counter drift'i otomatik tespit ediliyor ve belirlenen yöntemle düzeltiliyor.
- Yeni counter eklenirken authoritative state tanımı zorunlu hâle geliyor.

## Faz 3: Read-Only Go Sidecar

Hedef: Go servis altyapısını üretim trafiğini etkilemeden sokmak.

İlk aday: `gbpd-runtime-state-readmodel`.

Sorumluluklar:

- Java/Redis/DB kaynaklarından runtime snapshot okumak.
- Campaign/call/agent state'i normalize etmek.
- Metrics ve reconciliation raporu üretmek.
- Write yapmadan Java monolith ile veri tutarlılığını karşılaştırmak.

Başarı ölçütü:

- Go servis Docker image olarak çalışır.
- Health/readiness/metrics hazırdır.
- Java ile aynı snapshot'ı kabul edilebilir farkla üretebilir.

## Faz 4: Redis Contract Bridge

Hedef: Redis pub/sub ve message contract'larını versioned, test edilebilir hâle getirmek.

Teslimatlar:

- Mevcut channel ve payload schema'larının dokümanı.
- Java payload örnekleriyle Go contract testleri.
- Idempotent event consumer standardı.
- Versioned event envelope tasarımı.
- Backward compatible publish/subscribe adapter'ı.

Başarı ölçütü:

- Go servisleri Java Redis mesajlarını bozmayacak şekilde okuyabiliyor.
- Yeni mesaj tipleri versioned schema ile ekleniyor.

## Faz 5: Asterisk Gateway Extraction

Hedef: AMI/AGI protokol detaylarını ayrı bir service boundary'ye almak.

Servis adayı: `gbpd-asterisk-gateway`.

Sorumluluklar:

- AMI connection lifecycle.
- Originate command kabul etmek.
- Asterisk event'lerini normalize call event'lerine çevirmek.
- Connection health, retry, timeout ve callback tracking.

Geçiş sırası:

1. Java `ManagerConnectionDecorator` event'lerini shadow publish et.
2. Go gateway aynı event'leri read-only izlesin.
3. Originate command'larını dual-run olmayan shadow path ile doğrula.
4. Tek bir kampanya/server shard'ı için controlled cutover yap.
5. Rollback için Java originate path'i feature flag ile aktif kalmaya devam etsin.

Başarı ölçütü:

- `ORIGINID`/parent id korelasyonu bozulmadan event normalize ediliyor.
- Gateway restart olduğunda call state kaybı kontrollü şekilde reconcile ediliyor.

## Faz 6: Scheduler ve Campaign Orchestrator

Hedef: start/pause/stop/restart kararlarını Java UI/CRUD katmanından ayırmak.

Servis adayı: `gbpd-campaign-orchestrator`.

Sorumluluklar:

- Campaign schedule evaluation.
- Start/stop/pause command generation.
- Stop condition ve blackout/automation kararlarını event olarak üretmek.
- Java monolith ile command contract'ı üzerinden çalışmak.

Başarı ölçütü:

- Java scheduler chain davranışı karakterizasyon testleriyle kilitlenmiş olur.
- Go orchestrator önce dry-run raporlar, sonra controlled command publish eder.

## Faz 7: Queue Producer ve Predictive Servisleri

Hedef: call selection, queue fill ve predictive hesapları service-owned hâle getirmek.

Aday servisler:

- `gbpd-call-selection`: campaign DB'den aranacak kayıtları seçer, retry/callback/priority kurallarını uygular.
- `gbpd-predictive-engine`: alpha/beta/gamma hesaplarını stateless veya shard-owned state modeliyle yapar.

Başarı ölçütü:

- Queue ownership kampanya veya shard bazında tek servistedir.
- Predictive state restart/rebalance durumunda reconcile edilebilir.

## Docker ve Operasyon Standardı

Her yeni Go servis için minimum beklenti:

- `Dockerfile` ve yerel `docker-compose` profili.
- Env tabanlı config; secret dosyaya yazılmaz.
- `/healthz`, `/readyz`, `/metrics`.
- Structured JSON log.
- Graceful shutdown ve in-flight event drain.
- Redis/DB/AMI bağlantıları için timeout ve retry policy.
- Contract test ve unit test pipeline'ı.

## İlk Uygulanacak 10 İş

1. Maven/test ortamlarını standartlaştır.
2. `Campaign` counter writer/reader matrisini dokümante et.
3. Agent size drift için reconciliation stratejisini seç.
4. `InteractionNotifierPublisher` gibi static mutable state risklerini temizle.
5. Redis `KEYS userData*` kullanımlarını indeks/SCAN tasarımına al.
6. AMI event flow için karakterizasyon testleri ekle.
7. Redis payload contract testlerini ekle.
8. Runtime read-model Go sidecar için API/event taslağı çıkar.
9. Docker runtime standardını repo içinde dokümante et.
10. Controlled pilot kampanya/server shard'ı seç.

## Açık Kararlar

- Authoritative agent state Redis'te mi, yeni runtime state servisinde mi tutulacak?
- Asterisk gateway ownership server bazında mı, tenant/campaign bazında mı partition edilecek?
- Existing DB schema ne kadar süre shared database olarak kalacak?
- Event bus Redis pub/sub olarak mı kalacak, yoksa Kafka/Rabbit benzeri durable stream'e mi evrilecek?
- Java UI/CRUD katmanı ne kadar süre monolith içinde kalacak?
