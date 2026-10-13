---
name: bsg-data-source-loader-contract
description: bs-gamma-analyzer'da Excel/CSV/JSON/PostgreSQL veri kaynağı yükleyicilerinde kolon normalizasyonu, beklenen kolon adları, esnek JSON parse ve `camel_to_snake` davranışını korumak için. Yeni kaynak eklemek, mevcut yükleyiciyi değiştirmek veya kolon expectation'ı güncellemek gerektiğinde kullan.
---

# bsg-data-source-loader-contract

## Amaç

Pipeline tek bir DataFrame şemasıyla çalışır. Veri kaynağı türü değişse de DataFrame yüklendikten sonra downstream akış aynıdır. Bu skill kolon adlandırma, normalizasyon ve eksik kolon davranışı kontratlarını korur.

## Birlikte Kullanılacak Skill'ler

- `bsg-postgres-readonly-contract`: PostgreSQL kaynağı.
- `bsg-runtime-state-audit`: Yüklenen DataFrame'in `st.session_state` etkileşimi.
- `bsg-data-pipeline-performance-guardrails`: Büyük dosya yüklemede memory etkisi.

## Önce Oku

- `app.py`: `camel_to_snake`, `load_json`, sidebar veri yükleme akışı.
- `db/queries.py`: PostgreSQL kaynağı için sorgu listesi.
- `docs/architecture/DATA-PIPELINE.md`: Pipeline sırası ve kolon beklentileri.

## Kritik Kod Yüzeyi

- `camel_to_snake(name)`: Kolon adı dönüşümü; downstream'de tüm kod snake_case kolonlarla çalışır.
- `load_json(uploaded_file)`: Liste veya dictionary tabanlı esnek JSON parse.
- Excel parse: `pandas.read_excel`, sayfa seçimi.
- CSV parse: `pandas.read_csv`, encoding (UTF-8 varsayılan).
- PostgreSQL parse: `pandas.read_sql_query`.
- Kolon expectation: zorunlu, önerilen, opsiyonel listeleri.

## Contract Kontrolü

- Yeni kaynak ekleniyorsa ortak interface'i (load → DataFrame) sağlıyor mu?
- Kolon adları kaynak fark etmeksizin aynı snake_case format'a dönüyor mu?
- Eksik zorunlu kolon UI'da kullanıcıya nasıl raporlanıyor?
- Encoding/locale fark eden CSV/JSON dosyaları için fallback davranışı belirgin mi?
- `camel_to_snake` yeni kolon adları için doğru sonuç üretiyor mu?

## Test Stratejisi

- `test_camel_to_snake_<senaryo>_contract.py`: Standart, ardışık büyük harf, sayı içeren senaryolar.
- `test_load_json_<format>_characterization.py`: Liste, dict, nested liste senaryoları.
- Excel/CSV/JSON sabit fixture dosyaları `tests/data_loader/fixtures/` altında tutulur.
- Eksik kolon senaryosu: `test_loader_missing_required_column_warning.py`.
- PostgreSQL kaynağı için mock engine ile contract testi.

## Eksik Kolon Davranışı

- Pipeline akışını kırma; downstream fonksiyonlar `KeyError` yerine sessizce eksik bilgiyi raporlasın.
- Kolon adı eksikse Streamlit'te `st.warning` ile kullanıcı bilgilendirilir.
- Opsiyonel kolon eksikliğinde sekme/figür opsiyonel olarak gizlenir veya boş gösterilir.

## Yapılmayacaklar

- Kolon adlarını sessizce yeniden adlandırmak.
- Opsiyonel kolon eksikliğinde silent default değer üretmek (örn. NaN yerine 0).
- Yeni veri kaynağı için snake_case dışında kolon adı kullanmak.
- Excel/CSV/JSON parse için yeni paket eklemeden önce mevcut çözümleri denemeyi atlamak.

## Arama Kalıpları

- Yükleme: `read_excel`, `read_csv`, `read_json`, `read_sql_query`.
- Dönüşüm: `camel_to_snake`, `rename`, `columns =`.
- Validation: `if <kolon> not in df.columns`, `df.columns.tolist()`.

## Raporlama

- Hangi kaynak/kolon/normalizasyon davranışı değişti.
- Pipeline downstream etkisi.
- Eksik kolon senaryolarında UI davranışı.
- Test sonuçları.
- Yeni dosya eklenirken `requirements.txt` etkisi (paket gerekiyorsa).
