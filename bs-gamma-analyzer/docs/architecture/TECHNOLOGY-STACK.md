# Teknoloji Yığını

bs-gamma-analyzer Python tabanlıdır ve dış paket sayısı kasıtlı olarak küçük tutulur. Yeni paket eklenmeden önce mevcut yığında çözüm aranır.

## Çekirdek Bağımlılıklar

- **Python**: 3.11 veya üzeri. Veri sınıfları (`@dataclass`), tip ipuçları ve modern pandas API'leri kullanılır.
- **Streamlit**: UI framework'ü. Tek başına çalışır; tab, sidebar, session state, file uploader, plotly figure render bileşenleri kullanılır.
- **pandas**: DataFrame ile veri yükleme, normalizasyon, formül uygulama, validation ve anomali tespiti.
- **numpy**: Vektörel hesap, NaN/Inf kontrolü, rolling istatistikler.
- **plotly (express + graph_objects)**: Sekmelerde interaktif grafikler.
- **anthropic**: Claude API SDK'sı. `core/llm_analyzer.py` üzerinden kullanılır.
- **SQLAlchemy**: PostgreSQL bağlantısı için `create_engine`. ORM kullanılmaz; pandas `read_sql_query` ile sorgular yürütülür.
- **psycopg2 / psycopg2-binary**: PostgreSQL driver'ı. SQLAlchemy URL şemasında `postgresql+psycopg2` kullanılır.

## Standart Kütüphane Kullanımı

- `dataclasses`: `AnomalyEvent`, `CategorySummary`, `AnalysisReport`.
- `json`: Claude yanıtı parse, payload serialization.
- `re`: Kolon normalizasyonu (`camel_to_snake`).
- `os`, `sys`: Path ekleme, env değişkeni okuma (kapsam çok dar).

## Test Framework'ü

- Hedef test runner: **pytest**.
- Beklenen test kökü: `tests/`. Mevcut repoda test bulunmuyor; yeni geliştirme TDD ile başladığında bu kök oluşur.
- Pure analiz fonksiyonları için `pandas.testing.assert_frame_equal` ve numerik tolerans testleri tercih edilir.
- Streamlit gerektiren testlerde `streamlit.testing.v1` veya pure logic ayrımı kullanılır.

## Build/Paketleme

- Repo bir Python paketi olarak yayınlanmaz; doğrudan `streamlit run app.py` ile çalıştırılır.
- `requirements.txt` mevcut değildir; eklendiğinde minimum bağımlılık sürümleri burada da güncellenir.
- `pyproject.toml` veya `setup.py` yoktur.

## Java Parity Bağımlılığı

- `core/formula.py` Java tarafındaki `PredictiveGammaCallCalculator` sınıfının davranışsal kopyasıdır. Java tarafında değişiklik olduğunda Python tarafının da aynı parity ile güncellenmesi gerekir.
- Sabit isimleri (`DEFAULT_CORRECTION_FACTOR`, `DEFAULT_ABANDON_SENSITIVITY`, `WARM_UP_CALL_THRESHOLD`, `REQUIRED_WINDOW_SIZE`) Java tarafıyla isim ve değer olarak hizalanır.

## Yeni Paket Ekleme Kuralı

- Yeni paket eklemeden önce mevcut paketler ile çözüm aranır.
- Paket gerçekten zorunluysa önce geliştiriciye bildirilir; geliştirici VPN'de pip cache veya internal index'i günceller.
- Paket eklendiğinde `requirements.txt` veya muadili dosya güncellenir; `TECHNOLOGY-STACK.md` listesi de aynı iş içinde tazelenir.

## Change Contract

- Paket eklemek/çıkarmak `chore` veya `build` etiketli plan ile yapılır.
- Sürüm yükseltmek davranışı etkileyebilir; karakterizasyon testi olmadan minor sürüm bile yükseltilmez.
- Java parity bağımlılığı sabit kalır.
