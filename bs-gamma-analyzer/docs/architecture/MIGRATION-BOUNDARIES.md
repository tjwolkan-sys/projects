# Ayrılabilir Boundary'ler

bs-gamma-analyzer şu an monolitik bir Streamlit uygulamasıdır. Aşağıda ileride güvenle ayrılabilecek teknik boundary'ler listelenmiştir. Bu doküman hedef tasarım vaadi değildir; mevcut kodun hangi parçalarının zaten net sınırlara sahip olduğunu kaydeder.

## Mevcut Doğal Boundary'ler

- **`core/formula.py`**: Pure fonksiyonlar; UI'dan tamamen bağımsız. Doğrudan paket olarak çıkarılabilir.
- **`core/validator.py`**: Pure fonksiyonlar; pandas DataFrame in/out. Bağımsız test edilebilir.
- **`core/anomaly.py`** ve **`core/anomaly_detector.py`**: Pure fonksiyonlar ve dataclass'lar. UI'a bağımlı değildir.
- **`core/relations.py`**: Pure analiz fonksiyonları. Doğrudan reuse edilebilir.
- **`db/connection.py`** ve **`db/queries.py`**: SQLAlchemy engine ve sorgular; UI'dan bağımsız.
- **`core/llm_analyzer.py`**: Anthropic SDK boundary'sini izole eder. Dış LLM'in değişmesi durumunda yalnız bu modül etkilenir.

## İleride Ayrılabilir Boundary'ler

- **Streamlit UI ve analiz katmanı**: `app.py` içindeki sekme orkestrasyonu zamanla `ui/<sekme>.py` modüllerine bölünebilir; analiz pipeline'ı `pipeline.py` gibi orkestratör fonksiyona alınabilir.
- **Veri kaynağı yükleyiciler**: Excel/CSV/JSON/PostgreSQL parse adımları `data_sources/<kaynak>.py` modüllerine ayrılabilir; her yükleyici ortak bir interface'i (load → DataFrame) sağlar.
- **Plotly figür yardımcıları**: `charts/` klasörü zaten mevcut ama görselleştirme kodu `app.py` içine yayılmıştır; figür fonksiyonları kademeli olarak `charts/` altına taşınabilir.
- **Anomaly detection eşikleri**: `core/anomaly_detector.py` içindeki sabitler (rolling window, zscore threshold, severity cutoff) ileride `config/anomaly.yaml` benzeri bir konfigürasyon dosyasına alınabilir.
- **LLM provider abstraction**: Anthropic dışında bir model eklenmek istenirse `core/llm_analyzer.py` `LLMProvider` interface'i ile kapsülenir.

## Yapılmaması Gereken Ayrımlar

- `core/formula.py` davranışını değiştirecek "iyileştirme" amaçlı ayrımlar Java parity'sini bozar.
- Anomaly kategorilerini ayrı paketlere bölmek mevcut kontratı kırabilir; kategori adları ve severity skalası tek bir modülde tutulur.
- DB engine ile Streamlit session state arasındaki bağı tamamen koparmak çoklu kullanıcı senaryosu olmadığı sürece gereksizdir.

## Roadmap Bağı

Modüler `app.py` bölme adımları `docs/project/MODULAR-APP-SPLIT-ROADMAP.md` dosyasında listelenir. Bu doküman yalnız mevcut kodun nereye kadar bölünebilir olduğunu kaydeder; sıralı plan ve taşınma adımları roadmap'tedir.

## Change Contract

- Bölünme yapılırken davranış değişikliği yapılmaz; davranış değişikliği ayrı bir plan dosyası ile yürür.
- Pure fonksiyon olarak ayrılan modüller test edilmeden taşınmaz.
- Her ayrılma adımında contract testleri öncesinde yazılır (`bsg-characterization-tests`).
