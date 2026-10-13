# bs-gamma-analyzer Doküman İndeksi

Bu klasör, bs-gamma-analyzer için mimari özetleri, proje planlarını, geliştirme analizlerini, uygulama planlarını ve ajan çalışma geçmişini tutar. Dokümanlar yaşayan repo gerçeğine göre güncellenir; yeni bir doküman eklendiğinde bu indeks de aynı iş içinde güncellenmelidir.

## Genel Standart

- Harf içeren Markdown dosya adları büyük harfli ve tireli yazılır.
- Klasör adları küçük harfli kalır.
- Dokümanlar Türkçe karakterlerle ve TDK yazımına uygun yazılır.
- İçerikler Türkçe yazılır; ancak yerleşik teknik jargon global English haliyle kullanılır.
- Gerçek IP, şifre, token, Anthropic API key, anahtar veya müşteri bilgisi dokümanlara yazılmaz.
- Mimari davranışı etkileyen kod değişikliklerinde ilgili mimari doküman güncellenir.
- Proje planı veya roadmap niteliğindeki dokümanlar `project` altında tutulur.
- IDE, local build ve debug standartları `development` altında tutulur.
- Kod değişikliği gerektiren analizler `analyse`, onaylanan uygulama planları `execution-plan` altında tutulur.
- Feature dışı işlerin tek dosyalı planları `change-fix-plan` altında tutulur.
- Günlük history kayıtları günlük dosyalara append-only yazılır; `history/README.md` yalnız yeni gün açıldığında güncellenir.

## Mimari Dokümanlar

`architecture` klasörü mevcut sistemin nasıl çalıştığını, kritik boundary'lerini ve güvenli change contract'larını açıklar.

- `architecture/README.md`: Mimari dokümanların okuma sırası ve güncelleme standardı.
- `architecture/MIMARI.md`: Üst seviye mimari özet ve modül haritası.
- `architecture/PRODUCT-SCOPE.md`: Ürünün bu repo içindeki kapsamı ve external boundary'leri.
- `architecture/TECHNOLOGY-STACK.md`: Python, Streamlit, pandas, plotly, PostgreSQL ve Anthropic SDK yığını.
- `architecture/DEPLOYMENT-AND-RUNTIME.md`: Streamlit yayınlama modeli, başlangıç komutu ve dış bağımlılıklar.
- `architecture/CONFIGURATION-AND-SECRETS.md`: Sidebar input'u, environment variable, config dosyası ve secret yönetimi contract'ı.
- `architecture/API-AND-UI.md`: Streamlit sekmeleri, sidebar kontrolleri ve dış erişim noktaları.
- `architecture/DATA-PERSISTENCE-AND-DB.md`: PostgreSQL `predictive_gamma_events` şeması, SQLAlchemy engine ve sorgu pattern'leri.
- `architecture/OBSERVABILITY-AND-OPERATIONS.md`: Streamlit log, hata gösterimi ve operasyonel observability boşlukları.
- `architecture/TESTING-AND-SAFE-CHANGE.md`: TDD, karakterizasyon testi ve güvenli değişiklik akışı.
- `architecture/MIGRATION-BOUNDARIES.md`: İleride ayrılabilecek teknik boundary'ler ve modüler `app.py` hedefi.
- `architecture/GAMMA-FORMULA.md`: `core/formula.py` içindeki Gamma formülü, mode'lar ve Java parity'si.
- `architecture/ANOMALY-DETECTION.md`: `core/anomaly_detector.py` ve `core/anomaly.py` içindeki kategori, severity ve precursor/recovery contract'ı.
- `architecture/STREAMLIT-UI.md`: `app.py` içindeki sekme yapısı, sidebar ve session state contract'ı.
- `architecture/DATA-PIPELINE.md`: Excel/CSV/JSON/PostgreSQL yükleme, kolon normalizasyonu ve analiz pipeline'ı.
- `architecture/LLM-CLAUDE-INTEGRATION.md`: `core/llm_analyzer.py` Claude system prompt, JSON şeması ve hata yolları.

## Proje Planları

`project` klasörü mevcut sistemi dönüştürme, parçalara ayırma ve ileriye dönük proje planlarını tutar.

- `project/MODULAR-APP-SPLIT-ROADMAP.md`: Monolitik `app.py` dosyasını okunabilir, test edilebilir modüllere ayırma roadmap'i.

## Geliştirme Ortamı

`development` klasörü IDE, local build, debug ve developer workflow standartlarını tutar.

- `development/README.md`: Geliştirme ortamı dokümanlarının kısa indeksi.
- `development/LOCAL-SETUP.md`: Yeni developer için Python, virtualenv, pip ve Postgres erişim kurulumu.
- `development/VS-CODE-DEVELOPER-SETUP.md`: VS Code üzerinde Streamlit ve pytest debug ayarları.
- `development/MAIN-BRANCH-SYNC.md`: `origin/main` ile güvenli sync ve PR conflict riski raporlama akışı.

## Geliştirme Analizleri

`analyse` klasörü kod değişikliği gerektiren istekler için önce analiz yazılmasını sağlar.

- `analyse/README.md`: Analiz akışı, etiketler ve analiz şablonu.

## Geliştirme Uygulama Planları

`execution-plan` klasörü geliştirici tarafından onaylanan analiz ve nihai plan netleştirmesinden sonra oluşturulan checkbox'lı uygulama planlarını tutar.

- `execution-plan/README.md`: Execution plan akışı ve plan şablonu.

## Change/Fix Planlar

`change-fix-plan` klasörü yeni feature olmayan kod değişikliğini planlar.

- `change-fix-plan/README.md`: Feature dışı kod değişiklikleri için tek dosyalı analiz ve execution plan standardı.

## History

`history` klasörü ajanlarla yapılan anlamlı işleri gün bazlı kaydeder.

- `history/README.md`: History kayıt standardı ve günlük dosya indeksi.
