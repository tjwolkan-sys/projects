# Teknoloji Yığını

Bu belge mevcut kod tabanındaki teknoloji yığınını özetler. Sürüm bilgileri `gbpowerdialer/pom.xml` ve web yapılandırmalarından çıkarılmıştır.

## Backend

- Java 8 hedefiyle derlenen Maven WAR uygulaması.
- Spring Framework 5.3.30.
- Spring MVC, XML tabanlı Spring context ve `DispatcherServlet`.
- Spring Security 4.0.2 ve Spring Security OAuth2 2.0.10.
- Hibernate Core ve Hibernate Envers 5.3.22.Final.
- Hibernate Validator 5.4.3.Final.
- Apache Commons DBCP2 ile JDBC bağlantı havuzu.
- Dozer ile nesne eşleme.
- Jackson 2.9.7 ile JSON/XML serileştirme.

## Telephony ve Mesajlaşma

- `asterisk-java` 2.0.3.gb ile AMI ve AGI entegrasyonu.
- Redisson 3.23.3 ile Redis pub/sub, bucket ve map erişimi.
- gRPC 1.48.1 ve reactor-grpc ile Resource Distribution Service boundary'si.
- Netflix Eureka client/core bağımlılıkları; bazı Maven profillerinde aktif ediliyor.

## Frontend

- AngularJS 1.x tabanlı eski arayüz varlıkları.
- WRO4J ile JavaScript/CSS paketleme.
- Spring MVC controller'ları ve JSP/static webapp düzeni.
- AngularJS unit testleri için Karma, Jasmine, Chrome Headless ve angular-mocks 1.4.4.

## Test

- JUnit4.
- Mockito 3.3.3.
- PowerMock 2.0.9.
- Spring test ve MockMvc kullanımı.
- AngularJS tarafında `npm.cmd run test:angular` ile çalışan Karma/Jasmine test koşusu.

## Yapılandırma

- `gbpowerdialer/src/main/webapp/WEB-INF/applicationContext.xml`: Spring bean, datasource, Hibernate, Dozer, WRO, AGI mapping.
- `gbpowerdialer/src/main/webapp/WEB-INF/web.xml`: Servlet, listener ve filter zinciri.
- `gbpowerdialer/src/main/webapp/WEB-INF/security.xml`: Güvenlik zinciri.
- `gbpowerdialer/src/main/resources/*.properties`: Uygulama, Redis, Hibernate, Eureka, OneAccess, mail ve log yapılandırmaları.
- `gbpowerdialer/pom.xml`: Maven profilleri ve ortam bazlı property değerleri.

## Riskli Noktalar

- Java 8 ve AngularJS 1.x yeni geliştirme için eski bir taban oluşturur.
- `asterisk-java` bağımlılığı özel sürüm gibi görünüyor; yükseltme veya değiştirme davranış testi olmadan yapılmamalıdır.
- Maven profillerinde ortam bilgileri ve secret'a benzeyen değerler bulunabilir; yeni dokümana veya teste gerçek değer taşınmamalıdır.
- Dockerfile veya compose dosyası bu taramada bulunmadı; paketleme şu an WAR merkezli kabul edilmelidir.
- Spring XML, component-scan, reflection ve WRO nedeniyle kullanılmayan kod analizi yalnızca statik aramayla güvenilir değildir.
