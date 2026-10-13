# Gemini Agent Rehberi

Bu dosya Gemini tabanlı ajanlar için kısa köprü talimatıdır. bs-gamma-analyzer için birincil ve ayrıntılı kural kaynağı `AGENTS.md` dosyasıdır.

## Öncelik Sırası

1. Önce repo kökündeki `AGENTS.md` dosyasını oku ve ana çalışma sözleşmesi olarak uygula.
2. Bu dosya ile `AGENTS.md` arasında fark varsa `AGENTS.md` esas alınır.
3. Görev bir proje skill'ine uyuyorsa `.codex/skills/<skill-adı>/SKILL.md` dosyasını oku ve ilgili akışı uygula.
4. Birden fazla skill uyuyorsa en dar gerekli skill setini kullan; gereksiz skill yükleme.
5. `AGENTS.md` veya skill dosyalarında `Codex` geçen operasyonel kurallar Gemini için de geçerlidir; `Codex` ifadesini `çalışmayı yürüten ajan` olarak yorumla.

## Skill Kullanımı

`.codex/skills` altındaki klasörler bu repo için proje özelinde çalışma akışlarıdır. Gemini, görev türüne göre ilgili skill'i dikkate almalıdır.

Örnek eşleşmeler:

- Kod değişikliği, hata düzeltmesi veya özellik geliştirme: `bsg-tdd-workflow`, `bsg-safe-change`, `bsg-no-workaround-root-cause-engineering`
- Offline Python, pip cache, virtualenv veya Streamlit/Postgres test teşhisi: `bsg-offline-python-toolchain`
- Gamma formülü, mode geçişleri, sabit eşikler veya Java parity'si: `bsg-gamma-formula-contract`, `bsg-data-pipeline-performance-guardrails`
- Anomaly kategori, severity, rolling pencere veya precursor/recovery değişikliği: `bsg-anomaly-detector-contract`, `bsg-data-pipeline-performance-guardrails`
- Claude prompt, JSON şeması veya `AnalysisReport` alanı değişikliği: `bsg-llm-claude-contract`
- PostgreSQL şeması, SQLAlchemy engine veya `db/queries.py` SQL değişikliği: `bsg-postgres-readonly-contract`
- Excel/CSV/JSON yükleme, kolon normalizasyonu veya `camel_to_snake` davranışı: `bsg-data-source-loader-contract`
- Streamlit sekme, sidebar, session state veya Plotly figür değişikliği: `bsg-streamlit-ui`, `bsg-streamlit-tdd`
- Commit hazırlığı: `bsg-commit-discipline`
- Anlamlı iş kaydı: `bsg-agent-history`

## Temel Kurallar

- Bu repo Java predictive dialer ile parity tutan canlı bir analiz aracı gibi ele alınır; Gamma formülü, anomaly contract'ı, Claude prompt'u, PostgreSQL şeması ve Streamlit runtime contract'ları korunmadan değişiklik yapılmaz.
- Kod değişikliği gerekiyorsa TDD akışı uygulanır; belirsiz legacy davranış önce karakterizasyon testiyle kilitlenir.
- Pip komutları `.codex/TOOL-PATHS.local.properties` dikkate alınarak offline çalıştırılır; Gemini dependency indirmeyi denemez.
- Yeni kütüphane veya artifact zorunluysa önce geliştiriciye bildirilir; geliştirici VPN'de cache/Nexus güncellemesi yaptıktan sonra devam edilir.
- `AGENTS.md`, `docs/**/*.md` ve `.codex/skills/**/SKILL.md` dosyaları Türkçe tutulur.
- Gerçek IP, şifre, token, Anthropic API key, müşteri bilgisi veya credential yeni dosyalara yazılmaz.
- Çalışma ağacı kirli olabilir; ilgisiz değişiklikler geri alınmaz.

## Çıktı Beklentisi

Çalışma sonunda değişen dosyaları ve kısa Test Özeti'ni raporla. Test Özeti, çalıştırılan test komutlarını ve durumlarını içermelidir; test çalıştırılmadıysa nedeni açık yazılmalıdır. Anlamlı dokümantasyon, skill veya kod çalışması yapıldıysa ilgili günlük history dosyasına `bsg-agent-history` akışıyla append-only kayıt ekle; mevcut history içeriğini koru, eski kayıtları düzenleme, silme veya üzerine yazma. Yeni gün açıldıysa `docs/history/README.md` indeksine link ekle.
