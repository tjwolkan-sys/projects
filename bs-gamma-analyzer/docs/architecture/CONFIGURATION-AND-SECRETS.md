# Konfigürasyon ve Secret Yönetimi

bs-gamma-analyzer minimum konfigürasyon yüzeyi tutar. Tüm hassas bilgi runtime'da kullanıcıdan alınır ve repo içinde saklanmaz.

## Konfigürasyon Yüzeyi

- **Veri kaynağı seçimi**: Sidebar dropdown'u ile Excel/CSV/JSON/PostgreSQL.
- **PostgreSQL bağlantısı**: Sidebar'da host, port, dbname, user, password alanları.
- **Anthropic API key**: Sidebar'da `type="password"` ile alınan input.
- **Backtesting parametreleri**: Sidebar slider'ları ile Correction Factor ve Abandon Sensitivity.
- **Kampanya/tarih filtresi**: DB modunda dropdown ve date input.

## Secret Yönetimi Kuralları

- API key, DB şifresi ve kullanıcı adı yalnız `st.session_state` içinde tutulur.
- `st.session_state` Streamlit süreci kapandığında silinir; secret repo'ya yazılmaz.
- Sidebar'a girilen secret hiçbir log mesajında, exception trace'inde veya export dosyasında yer almaz.
- Yeni dosya eklenirken (skill, doküman, kod) gerçek değer yazılmaz; yer tutucu kullanılır.
- `.codex/TOOL-PATHS.local.properties` dosyasına da secret yazılmaz; yalnız host, port, kullanıcı adı, bağlantı şablonu yer alır.

## Environment Variable Kullanımı

- Repo şu an environment variable'a bağımlı değildir.
- Geliştirici makinesinde `ANTHROPIC_API_KEY` veya `POSTGRES_*` benzeri env değişkenleri tanımlıysa, bu yalnız geliştirici tarafında işe yarar; uygulama otomatik okumaz.
- Otomatik env okuma eklemek isteniyorsa `bsg-llm-claude-contract` ve `bsg-postgres-readonly-contract` skill'leri kapsamı genişletilir ve UI fallback davranışı dokümante edilir.

## Streamlit Config

- `.streamlit/config.toml` mevcut değildir; eklendiğinde tema, port veya yetkilendirme ayarları burada toplanır.
- Eklenirse repo'ya yalnızca yer tutucu/varsayılan değerler işlenir; hassas bilgi commitlenmez.

## Tool Path Konfigürasyonu

- `.codex/TOOL-PATHS.example.properties`: Şablon, repo'ya commit edilir.
- `.codex/TOOL-PATHS.local.properties`: Geliştiriciye özeldir, gitignore kapsamındadır.
- `bsg-offline-python-toolchain` skill'i bu dosyalardaki anahtarların nasıl kullanılacağını tarif eder.

## Change Contract

- Secret okuma stratejisi değişiyorsa (env, OS keyring, vault) plan dosyasında yeni akış yazılır ve eski sidebar input fallback olarak korunur.
- Sidebar input adları ve sırası geriye dönük korunur; yeni alan sona eklenir.
- Yeni hassas alan eklendiğinde session state anahtarı dokümante edilir ve `STREAMLIT-UI.md` güncellenir.
