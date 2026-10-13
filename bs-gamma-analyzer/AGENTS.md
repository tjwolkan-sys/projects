# bs-gamma-analyzer Agent Rehberi

## Misyon

bs-gamma-analyzer, Bulut Santral Predictive Dialer'ın Gamma algoritmasının davranışını analiz eden bir Streamlit uygulamasıdır. Repo, gerçek üretim dialer'ından gelen event verisinin (Excel, CSV, JSON veya PostgreSQL) Java tarafındaki `PredictiveGammaCallCalculator` formülüyle parity'sini doğrulamak, anomali kategorize etmek, ileri ilişki analizleri sunmak ve Claude tabanlı LLM analizi ile kök neden önerisi üretmek için kullanılır. Bu repo, üretimde çalışan dialer ile senkron formül parity'si gerektirdiği için canlı bir analiz aracı gibi ele alınmalıdır: Gamma formülü, anomali kategorileri, severity eşikleri ve Claude prompt contract'ı korunmadan değişiklik yapılmaz.

Ana hedefler:

- Java tarafındaki Gamma formülü ile Python implementasyonu arasındaki numeric parity'yi koruyarak hata düzeltmek ve dar kapsamlı özellik geliştirmek.
- Her geliştirmede TDD ile ilerlemek; eski ve kırılgan davranış varsa önce karakterizasyon testi yazmak.
- Streamlit session state, mutable global ve cache bağımlılıklarını adım adım azaltmak.
- Monolitik `app.py` dosyasını okunabilir, test edilebilir ve modüler bir mimariye doğru evirmek.

## Ajan Kapsamı

- Bu rehber Codex, Gemini, Claude ve repo üzerinde çalışan diğer ajanlar için ortak çalışma contract'ıdır.
- Bu dokümanda veya `.codex/skills/**/SKILL.md` dosyalarında `Codex` geçen operasyonel kural, köprü rehberleri üzerinden çalışan Gemini/Claude için de `çalışmayı yürüten ajan` anlamına gelir.
- Gemini veya Claude, `Codex` ifadesini kendisini hariç tutan bir kapsam daraltması olarak yorumlamaz.

## Dil Standardı

- `AGENTS.md`, `docs/**/*.md` ve `.codex/skills/**/SKILL.md` dosyaları Türkçe yazılır.
- Harf içeren Markdown dosya adları büyük harfli ve tireli yazılır; örnek: `GAMMA-FORMULA.md`.
- Tarih bazlı history dosyaları `GG.AA.YYYY.md` formatını korur.
- Türkçe karakterler doğru kullanılır: `ç`, `ğ`, `ı`, `İ`, `ö`, `ş`, `ü`.
- Başlıklar ve açıklamalar TDK yazımına uygun, açık ve kısa tutulur.
- İçerikler Türkçe yazılır; ancak yerleşik teknik jargon ve dialer/data jargonu global English haliyle kullanılır. Örnek: predictive dialer, Gamma formülü, anomaly detection, dataframe, session state, prompt, LLM, contract, event, lifecycle, runtime, roadmap, migration, refactor, feature flag, agent, supervisor, inbound, outbound, campaign, call, abandon rate, answer rate.
- Global terimler Türkçe cümle içinde Türkçe ek alabilir: `contract'ı`, `event'i`, `lifecycle'ı`, `prompt'u`, `formula'sı`, `dataframe'i`.
- Yeni skill veya doküman eklendiğinde bu envanter güncellenir.

## Repo Haritası

- `AGENTS.md`: Bu repo için birincil ajan çalışma rehberi ve kural kaynağı.
- `GEMINI.md`: Gemini tabanlı ajanlar için `AGENTS.md` ve `.codex/skills` kullanım köprüsü.
- `CLAUDE.md`: Claude tabanlı ajanlar için `AGENTS.md` ve `.codex/skills` kullanım köprüsü.
- `app.py`: Streamlit giriş noktası, sidebar, sekme yönetimi ve analiz pipeline'ının orkestrasyonu.
- `core/formula.py`: Predictive Gamma formülünün Python implementasyonu; Java `PredictiveGammaCallCalculator` ile parity tutar.
- `core/validator.py`: Gerçek `xn` ile beklenen `xn_expected` arasındaki tolerans karşılaştırması ve match raporu.
- `core/anomaly.py`: Flag tabanlı temel anomali tespiti (eski katman).
- `core/anomaly_detector.py`: Kategoriye, severity'ye ve precursor/recovery penceresine sahip ileri anomali tespiti.
- `core/llm_analyzer.py`: Claude tabanlı kök neden analizi; system prompt, JSON parse ve `AnalysisReport` üretimi.
- `core/relations.py`: `xn`, ringing yükü, agent kullanımı, CPS/cache limit ve `xn` stabilite ilişki analizleri.
- `db/connection.py`: PostgreSQL bağlantısı için SQLAlchemy engine factory ve `SELECT 1` test helper'ı.
- `db/queries.py`: `predictive_gamma_events` tablosuna karşı read-only kampanya ve veri çekme sorguları.
- `charts/`: Plotly grafik yardımcıları için ayrılmış klasör.
- `docs/architecture/`: Mevcut ürün mimarisi, runtime akışları, riskli boundary'ler ve güvenli change contract'ları.
- `docs/README.md`: Doküman üst indeksidir; history dışındaki yeni dokümanlar eklendiğinde güncellenir.
- `docs/development/`: IDE, local build, virtualenv ve debug gibi developer çalışma ortamı standartları.
- `docs/analyse/`: Yeni feature işleri için gün bazlı analiz dosyaları.
- `docs/execution-plan/`: Yeni feature analizinden sonra oluşturulan gün bazlı uygulama planları.
- `docs/change-fix-plan/`: Fix, hotfix, change request, refactor ve feature dışı diğer işler için analiz ve execution plan içeren tek dosyalı gün bazlı planlar.
- `docs/project/`: Proje planları, roadmap ve dönüşüm planları.
- `docs/project/MODULAR-APP-SPLIT-ROADMAP.md`: Monolitik `app.py` dosyasını modüler yapıya evirme roadmap'i.
- `docs/history/`: Ajanlarla yapılan işlerin günlük geçmişi.
- `docs/history/README.md`: History günlük kayıt indeksi ve minimal kayıt standardı.

## Çalışma Kuralları

- Geniş refactor ile hata düzeltmesini aynı değişiklikte karıştırma.
- Her geliştirme TDD ile ilerler: önce başarısız test, sonra en küçük uygulama kodu, sonra güvenli refactor.
- Eski davranış belirsizse önce mevcut davranışı kilitleyen karakterizasyon testi yaz.
- Java `PredictiveGammaCallCalculator` ile `core/formula.py` arasındaki sayısal parity, `WARM_UP_CALL_THRESHOLD`, `REQUIRED_WINDOW_SIZE`, `DEFAULT_CORRECTION_FACTOR`, `DEFAULT_ABANDON_SENSITIVITY`, `bootstrap`/`fallback_zero_talk`/`fallback_nan`/`safety_floor`/`normal` mode adları ve mode tetikleme koşulları geçiş notu olmadan değişmez.
- `predictive_gamma_events` tablo şeması, kolon adları ve `db/queries.py` içindeki SQL contract'ı geçiş notu olmadan değişmez.
- `core/anomaly_detector.py` içindeki kategori adları (`ABANDON_SPIKE`, `ANSWER_RATE_DROP`, `BOOST_INSTABILITY`, `XN_SUPPRESSION`, `AGENT_SHORTAGE`, `HIGH_UTILIZATION`, `TALK_TIME_SPIKE`, `MULTI_METRIC`), severity skalası (1=LOW … 4=CRITICAL), `ROLLING_WINDOW`, `ZSCORE_THRESHOLD`, `DELTA_WINDOW` ve `CONTEXT_WINDOW` sabitleri geçiş notu olmadan değişmez.
- `core/llm_analyzer.py` içindeki Claude system prompt'u, `AnalysisReport` alanları, model adı ve JSON çıktı şeması geçiş notu olmadan değişmez.
- Streamlit `st.session_state` anahtarları (`engine`, `df_raw`, vb.), sidebar kontrol adları ve sekme sırası kırılgan runtime state kabul edilir.
- Pandas DataFrame kolon adları, `camel_to_snake` normalizasyonu ve veri kaynağı yükleme akışı kontrat olarak korunur.
- Yeni static mutable state ekleme; `st.session_state` ihtiyacı varsa anahtarı açıkça dokümante et.
- Mevcut bağımlılıklar (`streamlit`, `pandas`, `numpy`, `plotly`, `anthropic`, `sqlalchemy`, `psycopg2`) ile çözüm ara; yeni kütüphane eklemeden önce geliştiriciye bildir.
- Yeni kütüphane veya yeni paket indirmesi gerçekten zorunluysa önce geliştiriciye bildir; geliştirici VPN'de pip cache/Nexus erişimini güncelleyip VPN'den çıktıktan sonra ajan çalışmaya devam eder.
- Yeni dosyalara secret, gerçek IP, şifre, Anthropic API key, müşteri bilgisi veya ortam credential'ı koyma. Yer tutucu kullan.
- Çalışma ağacı kirli olabilir. İlgisiz yerel değişiklikleri geri alma.
- Streamlit UI değişikliklerinde sekme sırası, sidebar kontrol adları, tab'larda kullanılan kolon adları ve session state anahtarları birlikte kontrol edilir.
- Yeni Streamlit ekranı veya davranış değişikliği de TDD kapsamındadır; en azından pure analiz fonksiyonları için unit test eklenir.

## Veri Hattı Performans Standardı

- Veri yükleme (Excel/CSV/JSON/PostgreSQL), kolon normalizasyonu, `apply_formula`, `validate`, `run_detection`, `relations.*` çağrıları ve Plotly figür üretimi hot path kabul edilir.
- Hot path değişikliklerinde event sayısı başına eklenen pandas iterasyonu, `apply` çağrısı, Python loop'u, regex, allocation, log ve memory copy maliyeti açıkça değerlendirilir.
- Hot path içinde yeni DB query, blocking network çağrısı, satır bazlı `for` loop'u, gereksiz DataFrame kopyalama, geniş `apply(axis=1)` taraması veya verbose payload log'u eklenmez.
- Anomali tespiti ve `relations.*` fonksiyonları büyük dataset'lerde memory'i şişirebilir; `precursor_df`/`recovery_df` benzeri context pencereleri için memory etkisi raporlanır.
- Correctness ve root cause çözümü performans gerekçesiyle atlanmaz; pahalı iş gerekiyorsa vectorization, `@st.cache_data`, batch akışı veya owner boundary değerlendirilir.
- Hot path'e dokunan her plan içinde `Performans Etkisi` bölümü bulunur.

## Yerel Tool Path Standardı

- Çalışmayı yürüten ajan Python, pip, virtualenv, Streamlit veya psycopg2 gerektiren doğrulama çalıştırmadan önce `.codex/TOOL-PATHS.local.properties` dosyasını kontrol eder.
- `.codex/TOOL-PATHS.local.properties` geliştiriciye özeldir, gerçek lokal path içerebilir ve commitlenmez.
- Commitlenen şablon `.codex/TOOL-PATHS.example.properties` dosyasıdır; yeni developer bu dosyayı kopyalayıp kendi path'leriyle local dosyayı doldurur.
- Local dosyada `PYTHON_CMD` varsa PATH'teki `python` yerine bu binary kullanılır. Ekip standardında IDE'ye bağımlı olmamak için standalone Python veya PATH'teki `python.exe` tercih edilir.
- Local dosyada `VENV_DIR` varsa Python komutları öncesinde virtualenv aktive edilir; aktivasyon mümkün değilse `VENV_DIR/Scripts/python.exe` doğrudan kullanılır.
- Local dosyada `PIP_INDEX_URL` veya `PIP_EXTRA_INDEX_URL` varsa pip kurulum komutları bu indeksleri kullanır.
- Pip komutları ajan tarafından varsayılan olarak `--no-index` veya `--no-deps --no-build-isolation` ile offline modda çalıştırılır; ajan dependency indirmeyi denemez.
- VS Code task'ları geliştirici tarafından çalıştırıldığı için `VSCODE_PIP_OFFLINE=false` ise pip online çalışabilir ve VPN/Nexus üzerinden eksik artifact çözebilir. Bu kural ajanın doğrulama komutlarını offline çalıştırma kuralını değiştirmez.
- Local dosyada `PIP_CACHE_DIR` varsa pip komutlarına `--cache-dir <dir>` eklenir.
- Local dosyada `STREAMLIT_CMD` varsa Streamlit doğrulamasında PATH yerine bu binary kullanılır.
- Local dosyada `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER` varsa local doğrulamalar bu değerleri kullanır; şifre yer tutucu olarak kalır ve ajan tarafından doldurulmaz.
- Local tool path dosyalarına şifre, token, gerçek müşteri bilgisi, Anthropic API key veya ortam credential'ı yazılmaz; credential gerekiyorsa OS keyring veya developer'ın kendi güvenli lokal standardı kullanılır.

## Geliştirme Analizi, Plan Netleştirme ve Execution Plan Standardı

- Kod değişikliği gerektiren her istekte ajan önce kullanıcının anlattığı derdi kendi tespitleri, riskleri ve çözüm önerileriyle kısa şekilde çerçeveler.
- Kullanıcıyla tartışma sonunda sorun tespit edilmiş ve çözüm yönü belli olmuşsa ajan şu kararı kullanıcıya açıkça sorar: `Plan aşamasına geçeyim mi?`
- Bu soruda ajan hangi plan türünün uygun olduğunu belirtir: yeni feature ise `new feature planı`, fix/hotfix/change request/refactor/perf gibi işler için `change-fix planı`.
- Aynı mesajda plansız doğrudan çözüm uygulama seçeneğinin de olduğunu belirtir: kullanıcı plansız sadece çözümü yaptırmak isterse analiz, execution plan veya change-fix-plan dosyası açılmaz.
- Ajan kendi önerisini kısa gerekçeyle ekler; Java parity riski, anomaly contract'ı, Claude prompt'u, Streamlit session state, persistence şeması veya veri yükleme contract'ı etkisi varsa öneri varsayılan olarak planlı ilerlemektir.
- Kullanıcı planlı ilerlemeyi onaylamadan analiz, execution plan veya change-fix-plan dosyası açılmaz.
- Kullanıcı plansız uygulamayı seçerse plan dosyası açma; çözümü etkileyen cevaplanmamış bir belirsizlik varsa kod değişikliğinden önce bunu yine kullanıcıya sor.
- Planlama onayı veya plansız uygulama onayı geldiğinde ajan mümkünse task id'yi interaktif olarak sorar; task id yoksa dosya kimliği olarak developer username'ini kullanır.
- İş etiketi ajan tarafından commit tipi standardına göre belirlenir: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`, `ci`, `perf` veya `revert`.
- Yeni feature işleri yalnız `feat` etiketiyle yürür ve iki dosyalı akışı kullanır: önce `docs/analyse/GG.AA.YYYY/FEAT-KIMLIK-KISA-AD.md`, sonra `docs/execution-plan/GG.AA.YYYY/FEAT-KIMLIK-KISA-AD.md`.
- Fix, hotfix, change request, refactor, perf, test, build, ci, chore, revert ve feature dışı diğer kod değişiklikleri `docs/change-fix-plan/GG.AA.YYYY/ETIKET-KIMLIK-KISA-AD.md` altında tek dosyada analiz detayları ve execution plan bölümüyle yürür.
- Dosya adında etiket büyük harfli yazılır: `FIX-BS-1423-GAMMA-FORMULA-PARITY-DRIFT.md`; dosya içindeki `İş Etiketi` alanında küçük harfli yazılır: `fix`.
- `KIMLIK` alanı varsa task id, yoksa developer username'idir. Örnek: `FIX-BS-1423-...` veya `FIX-GLB90114534-...`.
- Eski `BUG` karşılığı `fix` etiketidir. `HF` ayrı etiket değildir; acil üretim düzeltmesi `fix` etiketiyle açılır ve planda `Öncelik: hotfix` olarak belirtilir. `CR` ayrı etiket değildir; isteğin niteliğine göre `feat`, `fix`, `refactor`, `chore` veya uygun başka commit tipi seçilir.
- Her planlama dokümanında bağlam, mevcut davranış, beklenen davranış, etkilenen kod alanları, riskler, TDD stratejisi, uygulama kontrol listesi, hedefli doğrulama komutları, kapanış kriteri ve `Açık Sorular` bölümü bulunur.
- Plan dosyası oluşturulduktan veya güncellendikten sonra `Açık Sorular` bölümünde cevaplanmamış madde varsa ajan bu soruları kendiliğinden kullanıcıya sorar; kullanıcının "açık sorular neler" diye sormasını beklemez.
- Cevaplanmamış açık soru varken kod, test, config veya davranış değiştiren doküman uygulamasına başlanmaz. Plan durumu `Açık soru bekliyor` kabul edilir.
- Kullanıcı açık soruları cevapladığında ajan yanıtları ilgili plan dosyasına işler, gerekiyorsa planı günceller ve ancak bundan sonra uygulama/kod değişikliğine geçer.
- Yeni feature akışında execution plan analizle paralel oluşturulmaz; analiz incelenmeden ve nihai plan netleşmeden execution plan açılmaz.
- Planlı akış seçildiyse her plan dosyası oluşturulduktan sonra ajan geliştiriciye planı incelemesi gerektiğini bildirir; geliştirici planı onaylamadan uygulama/kod değişikliği başlamaz.
- Plan üzerinden tamamlanan her adım plan dosyasında işaretlenir.
- Plan dışına çıkılması gerekiyorsa önce plan güncellenir, açık soru doğduysa kullanıcıdan yanıt alınır, sonra kod değişikliği yapılır.
- Planlanan testler çalıştırılıp başarılı olmadan plan tamamlandı/kapatıldı sayılmaz; test çalıştırılamıyorsa plan doğrulama beklemede kalır ve blocker açık yazılır.
- Streamlit UI veya core analiz kodu değiştiyse plan, ilgili pytest paketi (`pytest tests/<domain>`) çalıştırılmadan kapatılamaz; environment blocker varsa açık yazılır.

## TDD Standardı

- Yeni özellik, hata düzeltmesi ve güvenli refactor çalışmaları `bsg-tdd-workflow` ile başlar.
- Kırılgan legacy davranışlarda `bsg-characterization-tests` ile mevcut davranış önce kilitlenir.
- Yeni testler `tests/<domain>/test_<konu>.py` altında açılır; mevcut test dosyası doğrudan ilgiliyse aynı dosya genişletilebilir.
- Streamlit sekme veya UI davranış testleri `tests/streamlit/<domain>/test_<konu>.py` altında açılır ve mümkün olduğunda pure analiz fonksiyonu izole edilerek test edilir.
- Yeni test dosyası adı `test_<konu>_<davranış>_<test_tipi>.py` formatındadır. Örnek: `test_formula_bootstrap_characterization.py`.
- Test yazılamayan durumlarda neden açıkça belirtilir ve en yakın doğrulama yöntemi uygulanır.
- Kod değişikliği sonunda yazılan test, çalıştırılan komut ve sonuç raporlanır.
- Her çalışma sonunda çalıştırılan testler ve durumları kısa bir "Test Özeti" olarak sunulur; test çalıştırılmadıysa nedeni aynı özet içinde yazılır.
- Soru-cevap, salt inceleme, planlama, dokümantasyon-only, skill-only veya AGENTS standardı değişikliklerinde pytest çalıştırılmaz; Test Özeti'nde `Test çalıştırılmadı; kod davranışı değişmedi.` yazılır.
- Kod davranışı değiştiğinde sadece etkilenen kapsamın hedefli testleri çalıştırılır. Tam test suite yalnızca geniş kapsamlı risk, ortak sözleşme değişikliği veya geliştirici isteği olduğunda çalıştırılır.

## History Standardı

- History kaydı yalnızca çalışmayı yürüten ajan bu görev kapsamında repo dosyalarında değişiklik yaptığı durumlarda eklenir.
- Dosya değişikliği; kod, test, doküman, skill, konfigürasyon veya benzeri dosyalarda ekleme, silme veya güncelleme yapılmasıdır.
- Soru-cevap, salt inceleme, planlama, brainstorming, `git log`/`rg`/dosya okuma gibi read-only işlerde history kaydı eklenmez.
- Çalışma ağacında kullanıcıya veya önceki görevlere ait ilgisiz değişiklikler varsa bunlar history kaydı tetiklemez.
- Ajan kaynaklı dosya değişikliği varsa ilgili `docs/history/GG.AA.YYYY.md` günlük dosyasına append-only kayıt eklenir.
- Günün history dosyası yoksa yalnız ajan kaynaklı dosya değişikliği durumunda oluşturulur ve yalnız bu durumda `docs/history/README.md` indeksine eklenir.
- History kaydı başlığı `### YYYY-MM-DD HH:mm:ss Z | KIMLIK | slug` formatında yazılır.
- History içindeki `KIMLIK` alanı varsa task id, yoksa developer username'idir.
- Her history kaydında `Kapsam`, `Değişiklikler` ve `Doğrulama` alanları zorunludur.
- Mevcut meşru history kayıtları silinmez veya yeniden numaralandırılmaz; yalnız sona yeni kayıt eklenir.
- Önceki kayıt başka developer, başka ajan veya başka task'a aitse hiçbir koşulda düzenlenmez, silinmez veya üzerine yazılmaz.
- Günlük history dosyasında mevcut içerik kaybı, eski satır silen diff veya tek taraflı `ours/theirs` conflict çözümü riski varsa işlem durdurulur ve geliştiriciye raporlanır.
- Eski history kaydında düzeltme gerekiyorsa mevcut kayıt değiştirilmez; yeni append-only kayıtla düzeltme notu eklenir.
- Kod yazmadan önce ilgili tarihli veya konuya yakın geçmiş kayıtları kontrol edilir.

## Mimari Doküman Standardı

- Mevcut mimari özeti `docs/architecture/` altında tutulur.
- Kod değişikliği mimari davranışı etkiliyorsa aynı iş içinde ilgili mimari doküman güncellenir.
- Mimari dokümanlar hedef design vaadi değil, mevcut kodun ve güvenli boundary'lerin kaydıdır.
- Proje planları `docs/project/` altında tutulur.
- Yeni feature analizleri `docs/analyse/`, feature uygulama planları `docs/execution-plan/`, fix/change/refactor ve diğer feature dışı planlar `docs/change-fix-plan/` altında tutulur.
- Modüler `app.py` dönüşüm planı ayrı tutulur; roadmap bilgisi `docs/project/MODULAR-APP-SPLIT-ROADMAP.md` dosyasına yazılır.
- Yeni history günü açıldığında `docs/history/README.md`, history dışındaki yeni dokümanlarda `docs/README.md` indeksi güncellenir.
- Mimari dokümanlarda gerçek IP, şifre, token, anahtar veya müşteri bilgisi kullanılmaz.

## Doğrulama

Doğrulama komutu işin türüne göre seçilir. Soru-cevap, salt inceleme, planlama, dokümantasyon-only, skill-only veya AGENTS standardı değişikliklerinde pytest/Streamlit testleri çalıştırılmaz; dosya/diff kontrolü yeterlidir. Kod davranışı değiştiğinde ilgili hedefli testler çalıştırılır.

Doğrulama komutlarından önce `.codex/TOOL-PATHS.local.properties` okunur ve varsa Python/virtualenv/pip cache/Streamlit/Postgres path'leri oradan alınır. Local dosya yoksa PATH ve standart developer ortamı kullanılır. Pip komutları ajan tarafından `--no-index` ile offline çalıştırılır; eksik artifact varsa indirme denenmez, eksik artifact geliştiriciye raporlanır. Python mevcut olduğunda tercih edilen komutlar:

- Hedefli test: `pytest tests/<domain>/test_<konu>.py -k <pattern>`
- Modül testi: `pytest tests/<domain>`
- Tam test: `pytest`
- Coverage gerekirse: `pytest --cov=core --cov=db`
- Streamlit doğrulama (manuel): `streamlit run app.py` (yalnız geliştirici tarafında)

Pytest test runner cache veya virtualenv eksiği nedeniyle başlamazsa ajan alternatif dependency indirme veya custom test runner çalıştırma denemesi yapmaz. Eksik paket açık raporlanır; geliştirici VPN'de cache'i güncelledikten sonra aynı offline pip kurulumu tekrar denenir.

Riskli runtime değişikliklerinde önce hedefli test ekle. Yeni testlerde mümkünse pure fonksiyon boundary'leri ve `pandas.testing.assert_frame_equal` gibi tolerans kontrolleri kullan. Streamlit context gerektiren testlerde `streamlit.testing` veya pure logic ayrımı tercih edilir.

## Yüksek Riskli Alanlar

- Gamma formülü parity'si: `core/formula.py`, `compute_xn`, `apply_formula`, mode tetikleme koşulları, sabitler.
- Anomaly contract'ı: `core/anomaly_detector.py`, kategori adları, severity skalası, rolling window/zscore eşikleri, `precursor_df`/`recovery_df` oluşturma.
- LLM contract'ı: `core/llm_analyzer.py`, system prompt, JSON şeması, `AnalysisReport` alanları, model adı.
- Persistence contract'ı: `db/queries.py`, `predictive_gamma_events` tablosu, kolon adları, filtre parametreleri.
- Veri yükleme contract'ı: `app.py` içindeki Excel/CSV/JSON parse, `camel_to_snake` normalizasyonu, kolon expectation'ları.
- Streamlit runtime: sidebar kontrol adları, sekme sırası, `st.session_state` anahtarları.
- Validator tolerans contract'ı: `core/validator.py`, `TOLERANCE`, `TOLERANCE_PCT`, `match_status`.
- İlişki analizleri: `core/relations.py`, `xn_ringing_analysis`, `agent_xn_analysis`, `cps_cache_analysis`, `xn_stability_analysis` çıktı şeması.

## Skill Envanteri

Görev uyduğunda `.codex/skills` altındaki proje skill'lerini kullan:

- `bsg-tdd-workflow`: Hata düzeltmesi, yeni özellik ve refactor dâhil tüm kod geliştirmelerini TDD ile yürütmek için.
- `bsg-offline-python-toolchain`: Python/pip/virtualenv/Streamlit tool path, offline pip, cache, test runner ve Postgres erişim teşhisleri için.
- `bsg-safe-change`: Üretimde Java ile parity tutan davranışı bozmadan küçük ve doğrulanabilir değişiklikleri yapmak için.
- `bsg-no-workaround-root-cause-engineering`: Bug fix, formül drift'i, anomaly false-positive ve veri tutarsızlıklarında case-specific patch yerine owner boundary'de kalıcı çözüm uygulamak için.
- `bsg-data-pipeline-performance-guardrails`: Pandas hot path, anomali tespiti, ilişki analizleri ve Plotly figür üretiminde latency ve memory maliyetini korumak için.
- `bsg-characterization-tests`: Belirsiz veya kırılgan mevcut davranışı değiştirmeden önce testle kilitlemek için.
- `bsg-runtime-state-audit`: Streamlit session state, mutable global, `@st.cache_data` ve veri yeniden hesaplama akışlarını incelemek için.
- `bsg-gamma-formula-contract`: Java `PredictiveGammaCallCalculator` ile Python `core/formula.py` arasındaki parity'i koruyarak formül üzerinde değişiklik yapmak için.
- `bsg-anomaly-detector-contract`: Anomaly kategori adları, severity skalası, rolling pencere/zscore eşikleri ve precursor/recovery şemasını koruyarak değişiklik yapmak için.
- `bsg-llm-claude-contract`: Claude system prompt, JSON şeması, `AnalysisReport` alanları, model adı ve hata yolları üzerinde güvenli değişiklik yapmak için.
- `bsg-postgres-readonly-contract`: `predictive_gamma_events` şeması, SQLAlchemy engine ve `db/queries.py` SQL contract'larını koruyarak persistence değişikliği yapmak için.
- `bsg-data-source-loader-contract`: Excel/CSV/JSON/PostgreSQL veri kaynağı yükleme akışı, kolon normalizasyonu ve kolon expectation'larını korumak için.
- `bsg-streamlit-ui`: Streamlit sekme, sidebar, session state ve Plotly figür değişikliklerini güvenli yapmak için.
- `bsg-streamlit-tdd`: Streamlit/UI değişikliklerinde pure analiz unit testleri ve `streamlit.testing` contract testleriyle ilerlemek için.
- `bsg-modular-app-split`: Monolitik `app.py` dosyasını modüler yapıya, contract testlerle ve feature flag'lerle taşımak için.
- `bsg-safe-refactor`: Davranış değiştirmeden, test koruması altında küçük ve güvenli refactor yapmak için.
- `bsg-dead-code-audit`: Kod silmeden önce kullanım izlerini, Streamlit/sidebar/SQL/Plotly bağlarını kanıtlamak için.
- `bsg-commit-discipline`: Commit öncesi diff, test, secret ve ilgisiz değişiklik kontrolü yapmak için.
- `bsg-sync-main-branch`: Feature/fix local branch'lerini remote `origin/main` ile güvenli sync etmek, PR conflict riskini raporlamak ve onaylı merge/conflict çözümü yapmak için.
- `bsg-agent-history`: Ajan kaynaklı dosya değişikliği olan işleri append-only history formatıyla kaydetmek ve yeni gün açıldığında history indeksini güncellemek için.

## Migration Notu

Modüler `app.py` dönüşüm roadmap'i `docs/project/MODULAR-APP-SPLIT-ROADMAP.md` dosyasında tutulur. Skill dosyaları roadmap saklamaz; skill'ler yalnızca geliştirme sırasında nasıl güvenli davranılacağını tarif eder.
