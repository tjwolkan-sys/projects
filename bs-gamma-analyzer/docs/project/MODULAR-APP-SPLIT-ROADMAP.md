# Modüler `app.py` Split Roadmap

bs-gamma-analyzer'ın `app.py` dosyası şu an monolitiktir; sidebar, sekme orkestrasyonu, Plotly figür üretimi ve analiz pipeline orkestrasyonu tek dosyada toplanmıştır. Bu roadmap, davranışı kırmadan dosyayı kademeli olarak modüler yapıya taşımak için takip edilecek adımları listeler.

## Hedef Mimari

- `app.py`: Yalnızca Streamlit giriş noktası ve sekme yönlendirmesi.
- `ui/sidebar.py`: Sidebar kontrolleri ve session state set/get.
- `ui/tabs/<sekme>.py`: Her sekme için ayrı modül (`overview.py`, `abandon.py`, `validation.py`, `charts.py`, `backtesting.py`, `relations.py`, `operational.py`, `ai_analysis.py`, `raw_data.py`).
- `pipeline.py`: Veri yükleme + formula + validator + detector + relations orkestrasyonu.
- `data_sources/<kaynak>.py`: Excel/CSV/JSON/PostgreSQL yükleyiciler ortak interface ile.
- `charts/`: Plotly figür yardımcıları sekmelere dağılmış olarak.

## Yol Haritası Adımları

### 1. Temel Pure-Logic Ayrımı

- `core/` altındaki pure fonksiyonların contract testleri yazılır.
- Mevcut `app.py` davranışı için karakterizasyon testleri alınır (her sekmenin kritik render kararı).

### 2. Veri Kaynağı Yükleyicilerin Soyutlanması

- `data_sources/excel.py`, `data_sources/csv.py`, `data_sources/json.py`, `data_sources/postgres.py` dosyaları açılır.
- Her yükleyici aynı interface ile DataFrame döner.
- Kolon normalizasyonu ortak yardımcı fonksiyona alınır.
- `app.py` yerine `data_sources` paketi çağrılır.

### 3. Pipeline Orkestratörü

- `pipeline.py` modülü açılır.
- `formula → validator → anomaly → detector → relations` çağrıları orkestrator fonksiyonuna alınır.
- `app.py` yalnızca pipeline çıktısını sekmelere geçirir.

### 4. Sidebar Modülü

- `ui/sidebar.py` modülü açılır.
- Mevcut sidebar kontrolleri ve session state set/get adımları taşınır.
- Sidebar test edilebilir hâle getirilir (pure helper'lar ayrılır).

### 5. Sekme Modülleri

- Her sekme için ayrı modül dosyası açılır.
- Sekme sırası ve adı kontrat olarak korunur.
- Plotly figürleri ilgili sekme modülüne taşınır.

### 6. Charts Klasörünün Kullanımı

- `charts/` klasörü figür yardımcılarıyla doldurulur.
- Sekme modülleri figür üretimini `charts.<konu>.build_*` çağrılarıyla yapar.

### 7. AI Analiz Sekmesi

- AI analiz sekmesi `core/llm_analyzer.py` ile aynı arayüzde tutulur.
- Yalnız UI kısmı taşınır; LLM contract'ı değiştirilmez.

## Adım Bazlı Plan Üretimi

Her büyük adım kendi `feat` etiketli iki dosyalı planla yürür:

- `docs/analyse/GG.AA.YYYY/FEAT-MODULAR-APP-<ADIM>.md`
- `docs/execution-plan/GG.AA.YYYY/FEAT-MODULAR-APP-<ADIM>.md`

Her adım sonunda davranışın korunduğu pytest hedefli komutlarla doğrulanır.

## Riskler

- **Sekme indeksi kayması**: Tüketici dokümanlar sekme sırasına bakar; sıra korunmalıdır.
- **Session state anahtarı çakışması**: Sidebar modülü taşınırken anahtar adlarının tüketicide aynı kalması gerekir.
- **Pure-logic referansı bozulması**: `core/` modülleri başka modülden import alıyorsa döngüsel bağımlılık riski oluşur.
- **Test kapsamı yetersizliği**: Karakterizasyon testleri olmadan büyük taşımalar regresyon getirir.

## Yapılmaması Gerekenler

- Davranış değişikliği ile bölme adımının aynı plan dosyasına yerleştirilmesi.
- Sekme adının veya sırasının "iyileştirme" amaçlı değiştirilmesi.
- Yeni paket/dış bağımlılığın bu roadmap kapsamında eklenmesi.
- Java parity bağımlısı `core/formula.py` içinde refactor adımıyla davranış değiştirme.

## Kapanış Kriteri

- `app.py` dosyası 200 satırın altına iner ve yalnız sekme yönlendirmesi içerir.
- `ui/`, `data_sources/`, `pipeline.py` modülleri test edilebilir.
- Mevcut tüm sekmeler eski davranışla aynı sonuç gösterir.
- Karakterizasyon test paketleri yeşil.
