# Yapılandırma ve Gizli Bilgiler

Bu uygulamada yapılandırma Maven profilleri, resource property dosyaları ve Spring XML üzerinden taşınır. Üretim güvenliği için yeni doküman, test veya örnek dosyalara gerçek ortam bilgisi yazılmamalıdır.

## Kaynaklar

- `gbpowerdialer/pom.xml`: Maven profilleri, uygulama adı, build property'leri, ortam bazlı değerler.
- `gbpowerdialer/src/main/resources/application.properties`: Uygulama ana property'leri.
- `gbpowerdialer/src/main/resources/hibernate.properties`: Veritabanı ve Hibernate ayarları.
- `gbpowerdialer/src/main/resources/redis.properties`: Redis bağlantı ayarları.
- `gbpowerdialer/src/main/resources/eureka-client.properties`: Eureka client ayarları.
- `gbpowerdialer/src/main/resources/oneaccess.properties`: OAuth/OneAccess ayarları.
- `gbpowerdialer/src/main/resources/mail.properties`: Mail ayarları.
- `gbpowerdialer/src/main/resources/logback.xml`: Log yapılandırması.

## Dikkat Edilecek Noktalar

- Maven profilleri içinde ortam endpoint'leri, iç ağ adresleri ve secret'a benzeyen değerler olabilir.
- Dokümanlara gerçek IP, gerçek kullanıcı adı, şifre, token, key veya müşteri bilgisi yazılmaz.
- Yeni örneklerde `example.local`, `127.0.0.1`, `CHANGE_ME` veya ortam değişkeni adı kullanılır.
- Secret niteliğindeki değerler koda sabitlenmez; container ortamında environment variable, secret store veya dış configuration provider ile verilir.
- Property adı değiştirilmeden önce o property'nin `@Value`, XML placeholder ve Maven filtering kullanım noktaları `rg` ile çıkarılır.

## Change Contract

- Property silmeden önce tüm Maven profilleri ve `src/main/resources` dosyaları kontrol edilir.
- Default değer ekleniyorsa üretim davranışını değiştirmeyecek biçimde seçilir.
- Yeni property için dokümanda amaç, beklenen format ve güvenli örnek değer belirtilir.
- Secret migration yapılacaksa önce eski ve yeni okuma yöntemi aynı anda desteklenir, sonra eski yöntem ayrı bir değişiklikle kaldırılır.
- Config değişikliği çağrı akışını etkiliyorsa TDD veya karakterizasyon testiyle korunur.

## Go ve Docker İçin Beklenen Standart

Go servislerine ayrılan her yeni parça şu contract'ı taşımalıdır:

- Configuration, dosya içine gömülü gerçek değer değil, environment variable veya secret provider üzerinden okunur.
- Local development için `.env.example` gibi gerçek değer içermeyen örnek sağlanır.
- Container health check, readiness, liveness ve graceful shutdown configuration ile yönetilir.
- Java monolith ile paylaşılan Redis channel veya AMI değişkenleri açıkça versiyonlanır.
