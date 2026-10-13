# API ve Arayüz

Uygulama Spring MVC controller'ları, REST resource sınıfları ve AngularJS tabanlı web arayüzünden oluşur. Eski arayüz ile API response şekilleri sıkı bağlı olabilir; bu nedenle controller dönüş tipleri değiştirilirken frontend etkisi kontrol edilmelidir.

## Web Katmanı

- `web.xml`, `DispatcherServlet` için `/` mapping'i kullanır.
- `dispatcher-servlet.xml` MVC yapılandırmasını taşır.
- `security.xml`, güvenlik kurallarını tanımlar.
- `wro.xml`, frontend varlık paketlerini tanımlar.
- `src/main/webapp/js`, AngularJS uygulama kodlarını ve vendor varlıklarını içerir.

## Controller Alanları

- `com.powerdialer.controller`: Yönetim ekranı ve klasik MVC/REST endpoint'leri.
- `com.powerdialer.api`: Daha doğrudan runtime veya health amaçlı JSON endpoint'leri.
- `controller/advice`: Controller özel hata yakalama davranışları.

## Öne Çıkan API Resource'ları

- `CampaignResource`: Kampanya listesi, realtime info ve filter stats.
- `CallResource`: Kampanya, server veya call id bazlı çağrı sorguları.
- `DialerFacadeResource`: Runtime context ve call originator görünürlüğü.
- `ACDEngineResource`: Group, campaign ve user bazlı agent state sorguları.
- `AgentSizeHealthResource`: Agent size tutarsızlığına yönelik health görünürlüğü.
- `UserResource`: Kullanıcı sorguları.

## Öne Çıkan MVC Controller'ları

- `CampaignController`
- `CampaignDataManipulationController`
- `CampaignRecordController`
- `QueueController`
- `ServerController`
- `SkillController`
- `ResultCodeController`
- `TelephonyResultCodeController`
- `DNCController`
- `LSCController`
- `FctChannelMonitorController`

## FCT Kanal Monitörü

`Options → FCT Kanal Monitörü` ekranı, dialer içindeki `FctChannelRegistry` durumunu salt okunur biçimde gösterir.

- Endpoint: `GET /fctChannel/snapshot`, yetki `DialerAdministration` (`@PreAuthorize`), `security.xml` içinde `/fctChannel/**` girdisi. Menü görünürlüğü `ROLE_SUPERADMIN`.
- Yanıt runtime state döndürür ve **tek JVM'e özeldir**: değerler o node'un kendi rezervasyonlarıdır, distributed toplam değildir. Snapshot `fct.channel.snapshot.cache.ms` süresince cache'lenir.
- Yanıtta müşteri verisi taşınmaz: yalnız kendi FCT numaraları, dialer interaction id'leri (`callId`) ve zaman damgaları bulunur; `calledNumber` taşınmaz.
- Frontend: route `/fctChannelMonitor`, template `pages/fctChannelMonitor.html`, controller `js/controllers/fctChannelMonitorCtrl.js`, factory `js/services/fctChannelFactory.js`, WRO `global` grubunda factory controller'dan önce yüklenir.
- Ekran 1/2/5 saniyelik polling yapar; periyodik render'da listeler `track by` ile yerinde güncellenir (yeniden çizim ve animasyon sıfırlanması olmaz). `staleWarnMs` eşiğini geçen rezervasyon yaşı kırmızı gösterilir ("kaçmış release adayı").

## Frontend Bağımlılıkları

AngularJS tarafı endpoint path, field adı ve response shape değişikliklerine duyarlıdır. WRO paketleri nedeniyle JavaScript dosyası ekleme veya taşıma sadece dosya değişikliği değildir; `wro.xml` ve build etkisi de kontrol edilmelidir.

Frontend file boundary'leri:

- `src/main/webapp/js/app.js`: AngularJS modül, route ve directive tanımları.
- `src/main/webapp/js/controllers`: Ekran controller'ları.
- `src/main/webapp/js/services`: Factory/service katmanı.
- `src/main/webapp/pages`: JSP/HTML template dosyaları.
- `src/main/webapp/js/localization`: Dil dosyaları.
- `src/main/webapp/WEB-INF/wro.xml`: Bundle sırası.
- `src/test/java/tests/angular`: Route, WRO, template ve backend response shape için Java/JUnit contract testleri.
- `src/test/angular`: AngularJS service, factory ve controller davranışları için Karma/Jasmine spec dosyaları.

AngularJS frontend legacy kabul edilir. Yeni frontend hedefi Next.js'tir; roadmap `docs/project/NEXTJS-FRONTEND-MIGRATION-ROADMAP.md` altında tutulur.

## Güvenlik

Spring Security ve OAuth2 client filter zinciri web katmanına uygulanır. Login, CSRF, permission ve session davranışları controller değişikliklerinde göz önünde bulundurulmalıdır.

## Change Contract

- Endpoint path değişikliği geriye uyumluluk planı olmadan yapılmaz.
- Response field adı veya tip değişiyorsa AngularJS kullanım noktaları `rg` ile aranır.
- Yeni API ekleniyorsa controller testi veya MockMvc testi tercih edilir.
- Runtime state dönen endpoint'lerde local JVM verisi ile distributed state farkı dokümante edilir.
- UI değişikliğinde WRO, browser cache ve eski route etkisi kontrol edilir.
- AngularJS route, WRO, template ve backend contract'ı değişiyorsa Java/JUnit contract testi açılır.
- AngularJS service, factory veya controller davranışı değişiyorsa Karma/Jasmine/angular-mocks spec dosyası açılır.
- Next.js'e taşınacak ekranlarda önce Angular route/controller/template/API contract'ı kilitlenir.
