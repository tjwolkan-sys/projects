# gbpowerdialer Doküman İndeksi

Bu klasör, gbpowerdialer için mimari özetleri, proje planlarını ve Codex çalışma geçmişini tutar. Dokümanlar yaşayan repo gerçeğine göre güncellenir; yeni bir doküman eklendiğinde bu indeks de aynı iş içinde güncellenmelidir.

## Genel Standart

- Harf içeren Markdown dosya adları büyük harfli ve tireli yazılır.
- Klasör adları küçük harfli kalır.
- Dokümanlar Türkçe karakterlerle ve TDK yazımına uygun yazılır.
- İçerikler Türkçe yazılır; ancak yerleşik teknik jargon ve call center/telephony jargonu global English haliyle kullanılır.
- Gerçek IP, şifre, token, anahtar veya müşteri bilgisi dokümanlara yazılmaz.
- Mimari davranışı etkileyen kod değişikliklerinde ilgili mimari doküman güncellenir.
- Proje planı veya roadmap niteliğindeki dokümanlar `project` altında tutulur.
- IDE, local build ve debug standartları `development` altında tutulur.
- Kod değişikliği gerektiren analizler `analyse`, onaylanan uygulama planları `execution-plan` altında tutulur.
- Günlük history kayıtları günlük dosyalara append-only yazılır; `history/README.md` yalnız yeni gün açıldığında güncellenir.

## Mimari Dokümanlar

`architecture` klasörü mevcut sistemin nasıl çalıştığını, kritik boundary'lerini ve güvenli change contract'larını açıklar.

- `architecture/README.md`: Mimari dokümanların okuma sırası ve güncelleme standardı.
- `architecture/PRODUCT-SCOPE.md`: Ürünün bu repo içindeki kapsamı ve external boundary'leri.
- `architecture/TECHNOLOGY-STACK.md`: Java, Spring, Hibernate, Redis, Asterisk, AngularJS ve test yığını.
- `architecture/DEPLOYMENT-AND-RUNTIME.md`: WAR yayınlama modeli, servlet/Spring başlangıcı ve dış bağımlılıklar.
- `architecture/CONFIGURATION-AND-SECRETS.md`: Property, Maven profil ve secret yönetimi contract'ı.
- `architecture/CAMPAIGN-LIFECYCLE.md`: Campaign oluşturma, başlatma, durdurma, duraklatma ve scheduler akışı.
- `architecture/DIALER-RUNTIME.md`: Producer, consumer, originator ve çağrı kuyruklarının çalışma biçimi.
- `architecture/MIMARI.md`: Tüm mimarinin üst düzey genel özeti.
- `architecture/ASTERISK-AMI-AGI.md`: AMI bağlantısı, originate, event handling ve AGI boundary'si.
- `architecture/REDIS-EVENTS-AND-STATE.md`: Redis channel, pub/sub, user state ve message contract'ları.
- `architecture/AGENT-ACD-AND-COUNTERS.md`: Agent seçimi, çağrı sayacı, yerel bellek ve drift riskleri.
- `architecture/DATA-PERSISTENCE-AND-DB.md`: Hibernate, DAO/service katmanı ve kampanya veri operasyonları.
- `architecture/API-AND-UI.md`: Spring MVC/API, AngularJS arayüz ve route contract'ları.
- `architecture/OBSERVABILITY-AND-OPERATIONS.md`: Log, health endpoint, operasyonel observability ve eksikler.
- `architecture/TESTING-AND-SAFE-CHANGE.md`: TDD, karakterizasyon testi ve güvenli değişiklik akışı.
- `architecture/MIGRATION-BOUNDARIES.md`: Go servislerine ayrılabilecek teknik boundary'ler.

## Proje Planları

`project` klasörü mevcut sistemi dönüştürme, parçalara ayırma ve ileriye dönük proje planlarını tutar. Bu klasördeki dokümanlar hedef ve plan içerir; mevcut mimari gerçeği anlatan belgeler `architecture` altında kalır.

- `project/GO-MICROSERVICE-ROADMAP.md`: Java monolith'ten Go mikroservis mimarisine güvenli migration roadmap'i.
- `project/NEXTJS-FRONTEND-MIGRATION-ROADMAP.md`: AngularJS legacy arayüzden Next.js frontend'e güvenli migration roadmap'i.

## Geliştirme Ortamı

`development` klasörü IDE, local build, Tomcat debug ve developer workflow standartlarını tutar.

- `development/README.md`: Geliştirme ortamı dokümanlarının kısa indeksi.
- `development/DEVELOP-BRANCH-SYNC.md`: Feature/fix branch'lerini PR öncesi remote `origin/develop` ile güvenli sync etme standardı.
- `development/VS-CODE-DEVELOPER-SETUP.md`: Yeni developer için VS Code, Maven binary, Tomcat ve local tool path kurulum adımları.
- `development/VS-CODE-TOMCAT-DEBUG.md`: VS Code ile Maven WAR build, Tomcat deploy ve Java attach debug standardı.

## Geliştirme Analizleri

`analyse` klasörü kod değişikliği gerektiren istekler için önce analiz yazılmasını sağlar. Analiz dosyaları gün bazlı klasörlerde commit tipi standardındaki etiketlerle tutulur: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`, `ci`, `perf`, `revert`. Yeni dosya adı standardı `ETIKET-KIMLIK-KISA-AD.md` biçimindedir; `KIMLIK` varsa task id, yoksa developer username'idir. Analizden önce kısa tespitler ve çözüm önerisi paylaşılır, execution plan'a geçmeden önce netleşmemiş sorular interaktif şekilde kapatılır ve plan kullanıcı tarafından onaylanır.

- `analyse/README.md`: Analiz akışı, etiketler ve analiz şablonu.
- `analyse/21.04.2026/README.md`: 21.04.2026 tarihli analiz klasörü.
- `analyse/27.04.2026/README.md`: 27.04.2026 tarihli analiz klasörü.
- `analyse/28.04.2026/README.md`: 28.04.2026 tarihli analiz klasörü.
- `analyse/30.04.2026/README.md`: 30.04.2026 tarihli analiz klasörü.
- `analyse/29.04.2026/README.md`: 29.04.2026 tarihli analiz klasörü.
- `analyse/05.05.2026/README.md`: 05.05.2026 tarihli analiz klasörü.
- `analyse/07.09.2026/README.md`: 07.09.2026 tarihli analiz klasörü.
- `analyse/21.04.2026/FEAT-ILK-TDD-KARAKTERIZASYON-TESTLERI.md`: Java backend ve AngularJS frontend için ilk karakterizasyon/contract testleri analizi.
- `analyse/21.04.2026/FEAT-ANGULARJS-KISA-ORTA-VADE-TEST-STANDARDI.md`: AngularJS 1.x için kısa vadeli contract ve orta vadeli gerçek AngularJS test standardı analizi.
- `analyse/21.04.2026/FEAT-ANGULARJS-BASEFACTORY-UNIT-TESTLERI.md`: AngularJS ortak `baseFactory` davranışı için gerçek unit test analizi.
- `analyse/21.04.2026/FIX-API-RESOURCE-OBJECTMAPPER-CONTEXT.md`: API resource test context'lerinde eksik `ObjectMapper` bean hatası analizi.
- `analyse/21.04.2026/FIX-TAM-JAVA-SUITE-KALAN-HATALAR.md`: Tam Maven suite'te kalan legacy test failure/error kümeleri analizi.
- `analyse/27.04.2026/FIX-DNC-PHONE-EXPIRED-DATE-VALIDATION.md`: DNC telefon numarası expire date doğrulaması, create date contract'ı ve frontend date parsing tutarsızlığı analizi.
- `analyse/28.04.2026/FIX-GLB90048674-SAVEINITIALCDR-901-HOTFIX.md`: `saveInitialCDR` ile erken açılan CDR kayıtlarının AMI hangup sonucuyla güncellenmeden cleanup sırasında 901 ile kapanması için hotfix analizi.
- `analyse/30.04.2026/FIX-GLB90048674-CAMPAIGN-START-STOP-CDR-CACHE.md`: Campaign start/stop takılması, missing initial CDR ve `ManagerConnectionDecorator` cache owner'lığı analizi.
- `analyse/29.04.2026/FIX-GLB90048674-PD-CDR-USER-RESULT-CODE-KACAGI.md`: Webphone/Asterisk/AQOS tarafında tamamlanan başarılı çağrılarda `pd_cdr.user_result_code_id` alanının boş kalması için kök neden analizi.
- `analyse/05.05.2026/FEAT-283218-FRESH-DATA-MONITORING.md`: Monitoring ekranında fresh data sayısının retry ve callback listeleri yanında gösterilmesi analizi.
- `analyse/07.08.2026/README.md`: 07.08.2026 tarihli analiz klasörü.
- `analyse/07.08.2026/FEAT-GLB90114534-FCT-KANAL-KAPASITE-GATE.md`: FCT eşzamanlı kanal kapasitesinin numara bazlı sayılıp dolu FCT'ye originate atılmamasını sağlayan gate, `cause 34` cooldown'u, TTL self-healing release muhasebesi ve canlı FCT Kanal Monitörü ekranı analizi.
- `analyse/07.09.2026/FEAT-325559-NUMARA-BASINA-AZAMI-ARAMA-LIMITI.md`: Retry stratejilerine opsiyonel `Max Attempt Per Phone Number` parametresi, numara bazlı dinamik atlama, erken kayıt kapatma ve callback-limit senkronizasyonu analizi.
- `analyse/16.09.2026/README.md`: 16.09.2026 tarihli analiz klasörü.
- `analyse/16.09.2026/FEAT-325470-AMD-SONUC-KODLARI-RETRY-STRATEJISI.md`: AMD türevli yeni telephony result code'ların (86/87/88) retry stratejilerine entegrasyonu, bad number'da sonraki numaraya geçiş/kapatma kuralı ve `pd_telephony_rc.code` tabanlı santral cause contract'ı analizi.

## Geliştirme Uygulama Planları

`execution-plan` klasörü geliştirici tarafından onaylanan analiz ve nihai plan netleştirmesinden sonra oluşturulan checkbox'lı uygulama planlarını tutar. Yeni dosya adı standardı `ETIKET-KIMLIK-KISA-AD.md` biçimindedir ve analyze dosyasıyla aynı kimliği taşır. Execution plan analizle paralel açılmaz; kod değişikliği plan üzerinden ilerler ve tamamlanan adımlar plan dosyasında işaretlenir.

- `execution-plan/README.md`: Execution plan akışı ve plan şablonu.
- `execution-plan/21.04.2026/README.md`: 21.04.2026 tarihli uygulama planı klasörü.
- `execution-plan/27.04.2026/README.md`: 27.04.2026 tarihli uygulama planı klasörü.
- `execution-plan/28.04.2026/README.md`: 28.04.2026 tarihli uygulama planı klasörü.
- `execution-plan/30.04.2026/README.md`: 30.04.2026 tarihli uygulama planı klasörü.
- `execution-plan/29.04.2026/README.md`: 29.04.2026 tarihli uygulama planı klasörü.
- `execution-plan/05.05.2026/README.md`: 05.05.2026 tarihli uygulama planı klasörü.
- `execution-plan/07.09.2026/README.md`: 07.09.2026 tarihli uygulama planı klasörü.
- `execution-plan/16.09.2026/README.md`: 16.09.2026 tarihli uygulama planı klasörü.
- `execution-plan/21.04.2026/FEAT-ILK-TDD-KARAKTERIZASYON-TESTLERI.md`: İlk Java ve AngularJS TDD testleri uygulama planı.
- `execution-plan/21.04.2026/FEAT-ANGULARJS-KISA-ORTA-VADE-TEST-STANDARDI.md`: AngularJS 1.x kısa/orta vade test standardı uygulama planı.
- `execution-plan/21.04.2026/FEAT-ANGULARJS-BASEFACTORY-UNIT-TESTLERI.md`: AngularJS ortak `baseFactory` unit test uygulama planı.
- `execution-plan/21.04.2026/FIX-API-RESOURCE-OBJECTMAPPER-CONTEXT.md`: API resource test context'lerinde eksik `ObjectMapper` bean düzeltmesi uygulama planı.
- `execution-plan/21.04.2026/FIX-TAM-JAVA-SUITE-KALAN-HATALAR.md`: Tam Maven suite'te kalan legacy test failure/error kümeleri uygulama planı.
- `execution-plan/27.04.2026/FIX-DNC-PHONE-EXPIRED-DATE-VALIDATION.md`: DNC telefon numarası expire date contract düzeltmesi için TDD ve uygulama planı.
- `execution-plan/28.04.2026/FIX-GLB90048674-SAVEINITIALCDR-901-HOTFIX.md`: `saveInitialCDR` ile erken açılan CDR kayıtlarının AMI hangup sonucu cleanup tarafından 901 ile ezilmesini engelleyen hotfix uygulama planı.
- `execution-plan/30.04.2026/FIX-GLB90048674-CAMPAIGN-START-STOP-CDR-CACHE.md`: Campaign start/stop takılması, missing initial CDR ve `ManagerConnectionDecorator` cache owner'lığı düzeltmesi uygulama planı.
- `execution-plan/29.04.2026/FIX-GLB90048674-PD-CDR-USER-RESULT-CODE-KACAGI.md`: Webphone `RESULT_CODE` event'i NPE ile abort olduğunda `pd_cdr` ve campaign result table user result code alanlarının kaybolmasını engelleyen hotfix uygulama planı.
- `execution-plan/05.05.2026/FEAT-283218-FRESH-DATA-MONITORING.md`: Monitoring ekranında fresh data sayısının retry ve callback listeleri yanında gösterilmesi uygulama planı.
- `execution-plan/07.08.2026/README.md`: 07.08.2026 tarihli uygulama planı klasörü.
- `execution-plan/07.08.2026/FEAT-GLB90114534-FCT-KANAL-KAPASITE-GATE.md`: `RouteDetailEntry.capacity`, `FctChannelRegistry`, originate öncesi kanal rezervasyonu, `cause 34` cooldown'u, release funnel bağlantıları, `GET /fctChannel/snapshot` ve AngularJS FCT Kanal Monitörü ekranı uygulama planı.
- `execution-plan/07.09.2026/FEAT-325559-NUMARA-BASINA-AZAMI-ARAMA-LIMITI.md`: Opsiyonel `Max Attempt Per Phone Number` parametresinin 6 retry stratejisine entegrasyonu, numara bazlı atlama, erken kayıt kapatma ve kapanış nedeni binding'i için 5 fazlı TDD uygulama planı.
- `execution-plan/16.09.2026/FEAT-325470-AMD-SONUC-KODLARI-RETRY-STRATEJISI.md`: AMD türevli üç yeni telephony result code (86/87/88, code 913/914/915), `code` tabanlı AMD ailesi kontrolleri ve 6 stratejide bad-number "sonraki numaraya geç veya kapat" kuralı için 5 fazlı TDD uygulama planı.

## History

`history` klasörü Codex ile yapılan anlamlı işleri gün bazlı kaydeder. Günlük dosyalar repo standardı gereği `history/GG.AA.YYYY.md` formatını korur; kayıtlar append-only yazılır ve başlıkta `timestamp | task-id-or-username | slug` kullanılır. `history/README.md` yalnız yeni gün dosyası açıldığında güncellenir.

- `history/README.md`: History kayıt standardı ve günlük dosya indeksi.
- `history/21.04.2026.md`: 21.04.2026 tarihli history kayıtları.
- `history/22.04.2026.md`: 22.04.2026 tarihli history kayıtları.
- `history/27.04.2026.md`: 27.04.2026 tarihli history kayıtları.

## Güncel Planlama Notu

30.04.2026 itibarıyla yeni feature işleri `analyse` + `execution-plan` iki dosyalı akışını kullanır. Fix, hotfix, change request, refactor ve feature dışı diğer işler `change-fix-plan` altında tek dosyada analiz detayları ve execution plan bölümüyle yürür.

- `change-fix-plan/README.md`: Feature dışı kod değişiklikleri için tek dosyalı analiz ve execution plan standardı.
- `change-fix-plan/04.05.2026/README.md`: 04.05.2026 tarihli change/fix plan klasörü.
- `change-fix-plan/04.05.2026/FIX-GLB90048674-AMI-CONNECTION-LOSS-BRIDGED-CALL-RECOVERY.md`: AMI connection loss sonrası bridge kurulmuş çağrıların 75 ile erken kapanmasını engelleme planı.
- `change-fix-plan/05.05.2026/README.md`: 05.05.2026 tarihli change/fix plan klasörü.
- `change-fix-plan/05.05.2026/FIX-317301-TELEPHONY-SLOT-IDEMPOTENT-LIFECYCLE.md`: Hangup sonrası user result code bekleyen ve AMI connection loss çevresindeki çağrıların `activeCalls`/`callsInProgress` drift'ini engelleme planı.
- `change-fix-plan/30.04.2026/FIX-GLB90048674-STRATEJI-ATTEMPTS-SAYACI.md`: PowerDialer retry stratejilerine göre camp table `attempts` artış kuralı change/fix planı.

## Plan Açma Seçimi Notu

Sorun tespit edilip çözüm yönü netleştiğinde ajan kullanıcıya plan aşamasına geçilip geçilmeyeceğini sorar, uygun plan türünü belirtir ve plansız doğrudan uygulama seçeneğini de açıkça sunar. Kullanıcı plansız uygulamayı seçerse `analyse`, `execution-plan` veya `change-fix-plan` dosyası açılmaz.

## Change/Fix Plan Günlükleri

- `change-fix-plan/30.04.2026/README.md`: 30.04.2026 tarihinde açılan change/fix planları.
- `change-fix-plan/30.04.2026/FIX-GLB90048674-DATA-MANIPULATION-NULL-KOLON-FILTRESI.md`: PowerDialer Data Manipulation ekranında null kolon koşulu ile kayıt getirme düzeltmesi.
- `change-fix-plan/07.05.2026/README.md`: 07.05.2026 tarihli change/fix plan klasörü.
- `change-fix-plan/07.05.2026/FIX-317516-AWAITING-USER-RESULT-RACE-OVERWRITE.md`: Hangup sonrası `awaitingUserResultCodeCalls` cache'ine geçiş yarış penceresi, awaiting-timeout `processCallResult` overwrite davranışı ve `shouldSetResultCode=false` durumunda durable fallback boşluğunun birlikte ele alınması planı.
- `change-fix-plan/05.06.2026/FIX-320173-TELEPHONY-RC-NULL-FINALIZE-KAYBI.md`: `pd_cdr.telephony_rc` NULL kalan kayıtların iki kök nedeninin (logout-orphan yolunun pd_cdr persist etmemesi; `processCallResult`'ın persist'e ulaşamadan kesilmesi) persist-first finalize sözleşmesiyle kalıcı çözümü planı.
- `change-fix-plan/05.06.2026/README.md`: 05.06.2026 tarihli change/fix plan klasörü.
- `change-fix-plan/05.06.2026/FIX-GLB90090645-STARTUP-AR-AND-ACD-LCD.md`: Gamma startup AR sabitlerini üretim AR'larına yaklaştırma (0.90 → 0.50/0.60) ve ACD agent dağıtımında grup-içi shuffle yerine LCD sıralı + aynı priority'li grupları tek havuza birleştirme planı.
- `change-fix-plan/05.06.2026/FIX-GLB90090645-ACD-AVAIL-TIME-COMPARATOR.md`: Cross-group ACD sort'u ve `shouldSwitchUser` swap kararı `user.statusSetDate` üzerinden çalışacak şekilde düzeltilir; `lastCallEndDate` wrapup/break sürelerini saymadığı için yanlış sıralama vermesi sorununu giderir.
- `change-fix-plan/17.06.2026/FIX-321479-CONNECTED-USER-ID-NULL-FINALIZE-EZILMESI.md`: Başarılı agent görüşmesinde `ASSIGN_TO_USER_GROUP` callback'i `call.user`'ı finalize öncesi temizlediğinde `pd_cdr.connected_user_id`'nin NULL'a ezilmesini, `UserPopulationEvaluator` connectedUser dalına assignedUser ile simetrik koruma guard'ı ekleyerek gideren plan.
- `change-fix-plan/10.08.2026/README.md`: 10.08.2026 tarihli change/fix plan klasörü.
- `change-fix-plan/10.08.2026/FIX-GLB90060295-PROGRESSIVE-RELEASED-IDLE-REZERVASYON-STARVATION.md`: Agent'ın originate fazında idle havuzdan çıkarılıp müşteri çalarken rezerve tutulması sonucu oluşan agent starvation'ın (idle havuz zamanın %75,6'sında boş, ortalama 0,30 agent) düzeltilmesi planı. Kapsam `PROGRESSIVE_RELEASED` ve `PREDICTIVE-ALPHA`; `PROGRESSIVE_RESERVED`, `PREVIEW_*` ve `PREDICTIVE-BETA` kapsam dışıdır.
- `change-fix-plan/11.08.2026/FIX-324856-CAMPAIGN-FORM-GRID-SAYISAL-SIRALAMA.md`: Campaign Form List Source Phone Numbers grid'inde `Sort` kolonunun alfabetik sıralanmasını (asc `0,1,10,11,12`) ve aynı defekti taşıyan 6 kardeş grid'i gideren plan. Kök neden: columnDef `type` bildirmediği için ui-grid 3.2.6 tipi grid'in ilk boş veri turunda `string` damgalıyor ve `if (!colDef.type)` guard'ı yüzünden gerçek veri geldiğinde yeniden hesaplamıyor.
- `change-fix-plan/08.09.2026/README.md`: 08.09.2026 tarihli change/fix plan klasörü.
- `change-fix-plan/08.09.2026/FIX-GLB90114534-FCT-CALLERID-CONTEXT-VE-LIST-RETRY.md`: FCT routing akışında devralınan üç sorunun planı: caller ID seçim context'inin (`retryCallerIdBehavior`/attempt/callType/utilizer) QSS'e gönderilmemesi, `getRouteDetails` gRPC hatasının boş listeden ayırt edilemeyip çağrıyı retry'sız yakması ve cause 34 in-call list-retry'ının sınırsız olması. PR 23029 revert'i bağımlılık analiziyle reddedildi.
- `change-fix-plan/01.10.2026/FIX-333467-EXPIRE-JOB-SCHEDULER-GECIKMESI.md`: Turkcell dialer'ında expire ve mirror cron'larının ortak scheduler thread'indeki `UserSynchronizer` fixed-rate birikimi yüzünden günlerce kaymasının düzeltilmesi; `RedisUtil.keys()` SCAN COUNT 10 → 1000 ve `UserSynchronizer` `fixedRate` → `fixedDelay`.
- `change-fix-plan/02.10.2026/FIX-333607-KAMPANYA-AKSIYON-PARALELLIK-GAMMA-STARTUP.md`: Tek global `CampaignService.action` kilidi ve kilit içinde sınırsız süren GAMMA startup `pd_cdr` sorguları yüzünden bir campaign'deki sorunun tüm campaign'lerin start/stop/pause aksiyonlarını kilitlemesini (02.10.2026 Turkcell) gideren plan: campaign bazlı kilit, asenkron scheduler/stop condition dağıtımı, startup sorgu timeout'u ve önbelleği, index'e uygun sorgu yazımı, aynı gün görülen NPE'lerin düzeltilmesi. Uygulandı (05.10.2026, iki regresyon doğrulama turu dâhil).
- `change-fix-plan/02.10.2026/README.md`: 02.10.2026 tarihli change/fix plan klasörü.
- `change-fix-plan/02.10.2026/FIX-333599-PAYLASILAN-TABLO-MUKERRER-ARAMA.md`: Aynı result tablosunu paylaşan kampanyalarda startup DB check cleanup'ının kardeş kampanyanın rezervasyonlarını silmesi ve görüşmedeki kaydın mükerrer aranması düzeltme planı.
- `change-fix-plan/05.10.2026/README.md`: 05.10.2026 tarihli change/fix plan klasörü.
- `change-fix-plan/05.10.2026/FIX-333599-PARALEL-START-CLEANUP-YARISI.md`: FIX-333607 sonrası paralel campaign start'ında paylaşılan tablo startup cleanup kesmesinin yarışı düzeltme planı.

## Test Senaryoları

`test-cases` klasörü geliştirmeler için QA/kabul test case setlerini tutar (Given/When/Then + otomatik/manuel kapsam işareti).

- `test-cases/README.md`: Test case seti standardı ve indeks.
- `test-cases/FEAT-321997-AGENT-CALLBACK-RESET-STRATEGY.md`: Agent Callback Reset stratejisi (Timer/Attempt-Count) test case seti.
- `test-cases/FEAT-GLB90114534-FCT-KANAL-KAPASITE-GATE.md`: FCT kanal kapasite gate'i için mutlaka test edilmesi gereken 10 kritik senaryo (öteleme, eş zamanlılık, headroom, fail-open, cause 34, release bütünlüğü, TTL, kampanya yaşam döngüsü, QSS hata ayrımı, context süzmesi) ve monitor/lokalizasyon/performans ek kontrolleri.

## Sunumlar

`presentation` klasörü geliştirmelerin ekip/PO ile paylaşılabilir sunum çıktılarını tutar. Depoya giren `.pptx` ikili olduğu ve diff'i okunamadığı için her sunumun yanında onu üreten script bulunur; sunum elle düzenlenmez, script'te değiştirilip yeniden üretilir.

- `presentation/README.md`: Sunum standardı ve indeks.
- `presentation/09.09.2026/FCT-KANAL-KAPASITE-GATE.pptx`: FCT kanal kapasite gate'i sunumu (25 slayt); problem, sorumluluk ayrımı, originate akışı, cause 34 davranışı, release/TTL, kampanya yaşam döngüsü, QSS hata ayrımı, monitor ekranı, konfigürasyon ve 10 kritik test senaryosu.

## Kök Proje Dokümanı

- `../README.md`: gbpowerdialer ürün kapsamını, ana görevlerini, mimari görünümünü, güvenli geliştirme standardını ve yeni başlayanlar için okuma sırasını açıklayan proje README dosyası.
