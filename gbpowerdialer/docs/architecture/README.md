# Mevcut Mimari Dokümanları

Bu klasör, gbpowerdialer'ın mevcut mimarisini yaşayan kod tabanına göre özetler. Amaç yeni geliştiriciye sistemi hızlı anlatmak, Codex ile yapılacak işleri güvenli boundary'lere oturtmak ve ileride Go mikroservis dönüşümünde hangi parçanın hangi sorumluluğu taşıdığını görünür kılmaktır.

Bu dokümanlar hedef mimari belgesi değildir. Mevcut davranış koddan çıkarılmıştır; eksik veya yanlış görünen noktalar bulunduğunda ilgili dosya aynı iş içinde güncellenmelidir.

## Okuma Sırası

1. `PRODUCT-SCOPE.md`
2. `TECHNOLOGY-STACK.md`
3. `DEPLOYMENT-AND-RUNTIME.md`
4. `CAMPAIGN-LIFECYCLE.md`
5. `DIALER-RUNTIME.md`
6. `ASTERISK-AMI-AGI.md`
7. `REDIS-EVENTS-AND-STATE.md`
8. `AGENT-ACD-AND-COUNTERS.md`
9. `DATA-PERSISTENCE-AND-DB.md`
10. `API-AND-UI.md`
11. `OBSERVABILITY-AND-OPERATIONS.md`
12. `TESTING-AND-SAFE-CHANGE.md`
13. `MIGRATION-BOUNDARIES.md`

## Dosyalar

- `PRODUCT-SCOPE.md`: Ürünün bu repo içindeki kapsamı ve external boundary'leri.
- `TECHNOLOGY-STACK.md`: Java, Spring, Hibernate, Redis, Asterisk, AngularJS ve test yığını.
- `DEPLOYMENT-AND-RUNTIME.md`: WAR yayınlama modeli, servlet/Spring başlangıcı ve dış bağımlılıklar.
- `CONFIGURATION-AND-SECRETS.md`: Property, Maven profil ve secret yönetimi contract'ı.
- `CAMPAIGN-LIFECYCLE.md`: Kampanya oluşturma, başlatma, durdurma, duraklatma ve zamanlama akışı.
- `DIALER-RUNTIME.md`: Producer, consumer, originator ve çağrı queue'larının çalışma biçimi.
- `ASTERISK-AMI-AGI.md`: AMI bağlantısı, originate, event handling ve AGI boundary'si.
- `REDIS-EVENTS-AND-STATE.md`: Redis channel, pub/sub, user state ve message contract'ları.
- `AGENT-ACD-AND-COUNTERS.md`: Agent seçimi, call counter, yerel bellek ve drift riskleri.
- `MIMARI.md`: Tüm mimarinin üst düzey ve bütünlüklü resmi.
- `DATA-PERSISTENCE-AND-DB.md`: Hibernate, DAO/service katmanı ve kampanya veri operasyonları.
- `API-AND-UI.md`: Spring MVC/API, AngularJS arayüz ve route contract'ları.
- `OBSERVABILITY-AND-OPERATIONS.md`: Log, sağlık endpoint'i, operasyonel görünürlük ve eksikler.
- `TESTING-AND-SAFE-CHANGE.md`: TDD, karakterizasyon testi ve güvenli değişiklik akışı.
- `MIGRATION-BOUNDARIES.md`: Go servislerine ayrılabilecek teknik boundary'ler.

## Güncelleme Standardı

- Kodda mimari davranışı etkileyen her değişiklik ilgili dokümanı da günceller.
- Dokümanlar Türkçe karakterlerle ve TDK yazımına uygun yazılır.
- Gerçek IP, şifre, token, anahtar veya müşteri bilgisi dokümana yazılmaz.
- Davranış belirsizse dokümana kesin hüküm yazılmaz; "açık soru" olarak bırakılır.
- Go dönüşümünün ayrıntılı roadmap'i `docs/project/GO-MICROSERVICE-ROADMAP.md` dosyasında kalır.
