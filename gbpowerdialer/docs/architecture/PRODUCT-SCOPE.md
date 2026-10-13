# Ürün Kapsamı

gbpowerdialer, Bulut Santral ekosisteminde outbound arama görevini üstlenen uygulamadır. Ana görevi kampanya verisini işlemek, kampanya tipine göre aranacak çağrıları üretmek, Asterisk üzerinden originate etmek, çağrı sonucunu takip etmek ve operatör/kampanya ekranlarına gerçek zamanlıya yakın durum bilgisi sağlamaktır.

## Bu Reponun Sorumlulukları

- Campaign tanımı, campaign listesi, campaign aksiyonları ve campaign lifecycle.
- Kampanya veri yükleme, okuma, güncelleme, silme ve öncelik yeniden hesaplama akışları.
- Preview, progressive, predictive ve outbound IVR çağrı üretimi.
- Asterisk AMI üzerinden originate ve AMI event işleme.
- Asterisk AGI tarafında abandon timeout ve agent dial işlemlerine destek.
- Agent uygunluk bilgisi, ACD queue'ları ve outbound çağrı ataması.
- Redis pub/sub ile kullanıcı durumu, webphone, call event, predictive event ve RDS konfigürasyon mesajları.
- Hibernate entity, service ve DAO katmanı üzerinden kalıcı veri yönetimi.
- AngularJS tabanlı yönetim ekranları ve Spring MVC endpoint'leri.

## Dış Sınırlar

- Inbound queue yönetimi bu reponun ana sorumluluğu değildir; qengine gibi uygulamalar ayrı değerlendirilmelidir.
- Bu repo bazı inbound/agent state'lerini tüketir veya publish eder, ancak tüm Bulut Santral agent state modelinin tek sahibi değildir.
- Asterisk dialplan ve santral runtime'ı bu repoda tam temsil edilmez; AMI/AGI contract'ları üzerinden temas edilir.
- Redis merkezi bileşen olarak kullanılır, fakat mevcut kodda birçok kritik durum hâlâ JVM belleğinde tutulur.

## Kritik Kullanıcı Akışları

- Kampanya oluşturma, veri yükleme, veri doğrulama ve kampanyayı başlatma.
- Çalışan kampanyayı duraklatma, durdurma, yeniden yükleme veya yeniden başlatma.
- Preview çağrıda agent kabulü ve müşteri bağlantısı.
- Progressive ve predictive çağrıda uygun agent, çağrı sayısı ve hat kapasitesi yönetimi.
- Çağrı sonu sonuç kodu, retry, callback, DNC ve LSC davranışı.
- Operatör ve yönetici ekranlarında kampanya gerçek zamanlı bilgisi.

## İlgili Kod Alanları

- `gbpowerdialer/src/main/java/com/powerdialer/controller/CampaignController.java`
- `gbpowerdialer/src/main/java/com/powerdialer/controller/CampaignDataManipulationController.java`
- `gbpowerdialer/src/main/java/com/powerdialer/api`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/acd`
- `gbpowerdialer/src/main/java/com/powerdialer/redis`
- `gbpowerdialer/src/main/webapp/js`

## Mimari Gerçeklik

Bu uygulama monolit olarak çalışır. Dialer, AMI bağlantısı, Redis subscriber'ları, campaign runtime state'i, agent state'i ve web/API katmanı aynı WAR içinde yaşar. Bu nedenle küçük bir değişiklik, çağrı üretimi, ekran göstergesi ve dış sistem mesajlaşmasını aynı anda etkileyebilir.

## Dönüşüm Açısından Not

Mikroservis dönüşümünde ilk amaç mevcut monoliti hemen bölmek değil, davranışı ölçülebilir hâle getirip external contract'ları kilitlemektir. Özellikle call state, agent state ve campaign counter'ları ayrıştırılmadan yatay ölçek güvenli kabul edilmemelidir.
