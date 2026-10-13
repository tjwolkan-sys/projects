# Local Geliştirme Kurulumu

bs-gamma-analyzer minimum bağımlılığa sahip bir Streamlit uygulamasıdır. Geliştirici makinesinde Python 3.11+ ve pip yeterlidir. PostgreSQL erişimi ve Anthropic API key opsiyoneldir.

## Önkoşullar

- Python 3.11 veya üzeri.
- pip (Python ile birlikte).
- Git.
- (Opsiyonel) PostgreSQL erişimi: `predictive_gamma_events` tablosuna read-only kullanıcı.
- (Opsiyonel) Anthropic API key: AI analizi sekmesi için.

## Repo Klonlama

```powershell
git clone <repo-url> bs-gamma-analyzer
cd bs-gamma-analyzer
```

## Virtualenv

Önerilen yaklaşım `.venv` klasörü altında izole environment kurmaktır:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

Linux/macOS için:

```bash
python3 -m venv .venv
source .venv/bin/activate
```

## Bağımlılıklar

Repoda henüz `requirements.txt` yoktur. Dosya eklendiğinde aşağıdaki gibi kurulur:

```powershell
pip install -r requirements.txt
```

Şu an manuel kurulum için minimum paket seti:

```powershell
pip install streamlit pandas numpy plotly anthropic sqlalchemy psycopg2-binary
```

Sürüm pinleme `requirements.txt` ile yapılacaktır; eklendiğinde `TECHNOLOGY-STACK.md` içindeki liste tazelenir.

## Tool Path Yapılandırması

`.codex/TOOL-PATHS.example.properties` dosyasını kopyalayıp `.codex/TOOL-PATHS.local.properties` olarak doldur:

```powershell
copy .codex\TOOL-PATHS.example.properties .codex\TOOL-PATHS.local.properties
```

Linux/macOS için `cp` kullanılır. Local dosya gitignore kapsamındadır ve commitlenmez.

## Streamlit Çalıştırma

```powershell
streamlit run app.py
```

Tarayıcı `http://localhost:8501` adresine gider. Port değişikliği için `--server.port <port>` parametresi eklenir.

## PostgreSQL Erişimi

DB modunda sidebar'da host, port, dbname, user, password girilir. Erişim test edilirken `bsg-postgres-readonly-contract` skill'i kapsamı kullanılır.

Yerel bir test DB'sine bağlanmak için:

- `POSTGRES_HOST=localhost`
- `POSTGRES_PORT=5432`
- `POSTGRES_DB=gamma_test`
- `POSTGRES_USER=gamma_ro`

Şifre yer tutucu kalır; gerçek şifre OS keyring veya geliştirici notu üzerinden girilir, repo dosyalarına yazılmaz.

## Anthropic API Key

API key sidebar'a girilir; runtime boyunca `st.session_state` içinde tutulur, repo'ya yazılmaz. Geliştirici dilerse çevre değişkeni veya yerel password manager üzerinden hatırlama yapabilir; ancak otomatik okuma davranışı eklenmek istenirse `bsg-llm-claude-contract` skill'i kapsamı genişletilir.

## Pytest

Test kökü ilk geliştirmeyle birlikte oluşur. Çalıştırma:

```powershell
pytest
pytest tests/formula
pytest tests/anomaly -k "abandon_spike"
```

## Sık Sorulan Sorunlar

- **`ModuleNotFoundError`**: virtualenv aktive edilmemiş olabilir; aktivasyonu kontrol et.
- **`psycopg2` build hatası**: `psycopg2-binary` paketi tercih edilir.
- **Streamlit yeniden yüklemiyor**: tarayıcıyı yenile veya süreçi yeniden başlat.

## Change Contract

- Kurulum adımı değişiyorsa bu dosya aynı iş içinde güncellenir.
- Yeni paket eklendiğinde minimum paket seti tazelenir.
- Yeni env değişkeni varsayımı eklenmeden önce `CONFIGURATION-AND-SECRETS.md` ile birlikte ele alınır.
