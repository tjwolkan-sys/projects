# Yayınlama ve Çalışma Zamanı

Mevcut uygulama Maven ile WAR olarak paketlenen, servlet container üzerinde çalışan bir Java web uygulamasıdır. Çalışma zamanı içinde web/API katmanı, dialer motoru, Asterisk bağlantıları, Redis subscriber'ları ve zamanlayıcılar aynı süreçte çalışır.

## Başlangıç Akışı

- `web.xml`, `DispatcherServlet` ve Spring root context'i başlatır.
- `ContextLoaderListener` Spring uygulama context'ini yükler.
- `StartupListener` uygulama açılışında ek başlangıç davranışları çalıştırır.
- `applicationContext.xml`, `com.powerdialer` altında component-scan yapar.
- Property placeholder; `application.properties`, `redis.properties`, `hibernate.properties`, `oneaccess.properties`, `mail.properties` ve `eureka-client.properties` dosyalarını okur.
- Hibernate `SessionFactory`, transaction manager, `RestTemplate`, Dozer mapper, WRO filter ve AGI mapping bean'leri Spring context içinde kurulur.

## Servlet ve Filter Zinciri

- `DispatcherServlet` HTTP isteklerini Spring MVC controller'larına yönlendirir.
- `CharacterEncodingFilter` karakter kodlamasını yönetir.
- `WebResourceOptimizer` WRO varlıklarını servis eder.
- `MultipartFilter` dosya yükleme akışlarında devrededir.
- `springSecurityFilterChain` kimlik doğrulama ve yetkilendirme katmanını uygular.
- `oauth2ClientContextFilter` OAuth2 client context'i için kullanılır.

## Dış Bağımlılıklar

- Ana uygulama veritabanı.
- Kampanya kaynak ve sonuç tablolarına erişilen veritabanı bağlantıları.
- Redis.
- Asterisk AMI ve AGI bağlantıları.
- OneAccess OAuth boundary'si.
- Resource Distribution Service ve Eureka; ortam profiline göre devreye girebilir.
- Mail ve dış REST çağrıları.

## Çalışma Zamanı Sahipliği

Aşağıdaki durumlar tek JVM içinde tutulduğu için yatay ölçek açısından yüksek risklidir:

- `DialerFacade.campaignContexts`
- `DialerFacade.asteriskCallOriginators`
- `ManagerConnectionComposition.openedConnections`
- `ManagerConnectionDecorator` içindeki originated call cache'i
- `Campaign` üstündeki geçici counter'lar ve call set'leri
- `ACDEngine` idle/system active user queue'ları
- `CallUpToDateKeeper` çağrı listesi
- Predictive alpha/beta/gamma manager'ların yerel map'leri

## Docker Hazırlık Durumu

Bu taramada repo içinde `Dockerfile`, `docker-compose` veya Kubernetes/Helm tanımı bulunmadı. Dockerizasyon yapılırken ilk hedef yalnızca WAR'ı container içine koymak olmamalıdır; dış bağımlılık, health check, graceful shutdown, profil seçimi, secret aktarımı ve tek JVM state varsayımları birlikte ele alınmalıdır.

## Operasyonel Riskler

- Aynı campaign birden fazla node üzerinde çalışırsa çağrı üretimi ve counter update çiftlenebilir.
- Redis merkezi olsa bile pub/sub mesajları tek başına authoritative state sağlamaz.
- AMI event ordering, originate callback ve hangup/result code akışı tek node varsayımıyla yazılmış olabilir.
- Uygulama kapanırken queued/originated çağrıların nasıl temizlendiği özellikle doğrulanmalıdır.
