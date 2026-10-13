# Redis Event ve State Contract'ları

Redis, bu uygulamada hem pub/sub mesajlaşması hem de bazı kullanıcı/session state işlemleri için kullanılır. Ancak mevcut mimaride Redis tek başına authoritative state değildir; birçok kritik durum hâlâ Java süreci içinde tutulur.

## Redis Erişim Katmanı

- `RedisUtil`, Redisson client'ını sarmalar.
- `RTopic` üzerinden publish/subscribe yapılır.
- `RBucket` ve `RMap` ile key-value ve map erişimi sağlanır.
- JSON serileştirme için Jackson `ObjectMapper` kullanılır.

## Channel Envanteri

`Constants.REDIS_CHANNEL` altında görülen channel adları:

- `channelGBWebPhone`
- `channelGBPowerDialer`
- `channelUserCurrentStatus`
- `channelUserSession`
- `channelUserPause`
- `channelGBPowerDialerEntity`
- `channelQssConfiguration`
- `channelCallEvent`
- `channelPredictiveEvent`
- `channelGBInteractionNotifier`

Bu adlar dış uygulamalarla contract'tır; migration notu ve consumer analizi olmadan değiştirilmemelidir.

## Subscriber'lar

- `UserSubscriber`: Kullanıcı current status ve session mesajlarını tüketir.
- `WebphoneSubscriber`: Webphone mesajlarını tüketir.
- `ResourceDistributionServiceSubscriber`: RDS/QSS konfigürasyon mesajlarını tüketir.
- `SubscriptionStatusListener`: Subscribe/unsubscribe durumlarını loglar.

## Publisher'lar

- `CallEventPublisher`: Dialer call event mesajlarını publish eder.
- `PredictivePublisher`: Predictive alpha/beta/gamma event mesajlarını publish eder.
- `InteractionNotifierPublisher`: Dialer call report DTO'sunu zenginleştirip interaction notifier channel'ına gönderir.
- `RedisUtil.publishUserPauseMessage`: User pause mesajlarını publish eder.

## Mesaj DTO'ları

İlgili paketler:

- `gbpowerdialer/src/main/java/com/powerdialer/redis/message`
- `gbpowerdialer/src/main/java/com/powerdialer/redis/event`
- `gbpowerdialer/src/main/java/com/powerdialer/redis/event/dialercall`
- `gbpowerdialer/src/main/java/com/powerdialer/redis/event/predictive`
- `gbpowerdialer/src/main/java/com/powerdialer/redis/event/interactionnotifier`

DTO alanları JSON üzerinden taşındığı için alan adı, tip ve boş değer davranışı dış tüketicilerle uyumluluk açısından önemlidir.

## State ve Tutarlılık

Mevcut sistemde Redis'te görünen agent/session state, Java belleğindeki ACD state ve campaign counter'ları aynı bilgiye farklı pencerelerden bakabilir. Bu nedenle "Redis'teki sayı doğru" veya "Campaign counter doğru" varsayımı tek başına güvenli değildir.

Özellikle şu alanlarda drift riski yüksektir:

- Aktif çağrı sayısı.
- Ringing call sayısı.
- In-call sayısı.
- Agent sayısı ve uygun agent sayısı.
- User session ve current status.
- Preview accept ve call end event sırası.

## Change Contract

- Redis channel veya DTO değişikliğinde `gbpd-redis-contracts` kullanılmalıdır.
- Yeni mesaj alanı geriye uyumlu olmalı; eski tüketiciler alan yokmuş gibi çalışabilmelidir.
- Pub/sub mesajı kaybolabilir; kritik state için replay veya reconciliation stratejisi ayrıca tasarlanmalıdır.
- Counter düzeltmesi gerekiyorsa increment/decrement yerine authoritative event/list kaynaklı türetme hedeflenir.
- Publisher identity olarak kullanılan node bilgisi migration sırasında açıkça standartlaştırılır.
