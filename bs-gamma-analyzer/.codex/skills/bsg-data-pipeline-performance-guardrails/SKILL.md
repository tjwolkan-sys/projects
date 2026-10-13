---
name: bsg-data-pipeline-performance-guardrails
description: bs-gamma-analyzer'da pandas hot path, anomaly detector loop'u, Plotly figür üretimi, SQL veri çekimi ve Streamlit cache kullanımı üzerinde latency, memory ve throughput maliyetini korumak için. Event başına ek IO/scan/loop/log/allocation oluşturan değişikliklerde kullan.
---

# bsg-data-pipeline-performance-guardrails

## Amaç

Veri yükleme, formül uygulama, anomali tespiti ve görselleştirme sürelerinin tahmin edilebilir kalmasını sağlamak. Büyük dataset'lerde memory'i şişiren değişiklikleri yakalamak.

## Hot Path Sınıflandırması

- **Per-event hot path**: Her satır/eventte çalışan kod. Örn. `compute_xn`, anomali kategori tetikleyicileri, validator karşılaştırması.
- **Per-batch hot path**: Tüm DataFrame üzerinde çalışan kod. Örn. `apply_formula`, `validate`, rolling z-score, `relations.*`.
- **UI render hot path**: Sekme her açıldığında çalışan kod. Plotly figür kurulumu, korelasyon matrisi.
- **External boundary hot path**: PostgreSQL `read_sql_query`, Anthropic `messages.create` çağrısı.

## Yasak Pattern'ler

- `df.apply(<func>, axis=1)` yerine vectorize çözümler aranmıyorsa eklenmesi.
- Satır bazlı `for` loop ile `df.iloc[i]` üzerinden okuma.
- Rolling/aggregation'da yeni kolon eklerken DataFrame'i kopyalamak (`.copy()` zinciri).
- Hot path içinde verbose `print` veya log mesajı.
- Hot path içinde yeni DB query veya HTTP çağrısı.
- Hot path içinde `re.compile` her event başına çalıştırılması.
- Plotly `update_traces` çağrısının figür başına gereksiz tekrarlanması.

## Zorunlu Maliyet Soruları

- Event başına ek allocation veya kopya var mı?
- Memory tüketimi precursor/recovery saklamayla nasıl etkilenir?
- Sekme açılışında çekilen DataFrame boyutu kontrol ediliyor mu?
- Cache invalidation politikası belirgin mi?
- DB sorgusu kampanya/tarih filtresiz mi?

## Tercih Edilen Çözüm Yönleri

- Vectorized pandas/numpy işlemleri.
- `pd.eval`, `numexpr`, `numpy.where` ile kolon hesabı.
- `@st.cache_data` ile pure analiz fonksiyonu cache'leme; `ttl` veya `hash_funcs` doğru ayarlanır.
- DB tarafında kampanya/tarih filtreli, kolon listeli sorgu.
- Plotly figürlerinde gereksiz veri serisi atlama; aggregate edilmiş data tercih edilir.
- Anomali precursor/recovery için DataFrame kopyası yerine indeks aralığı saklamak.

## Root Cause ile İlişki

- Performans için correctness/parity feda edilmez. `bsg-no-workaround-root-cause-engineering` ile birlikte ele alınır.
- Pahalı iş gerekiyorsa cache, batch, owner boundary tartışılır.

## Plan Gereksinimi

Her hot path değişikliği plan dosyasında `Performans Etkisi` bölümünü doldurur:

- Hot path kategorisi (per-event, per-batch, UI render, external).
- Event/batch başına ek maliyet.
- Memory etkisi.
- Cache etkisi.
- Azaltım veya ölçüm notu.

## Done Kriteri

- Hot path değişikliğinde plan içinde `Performans Etkisi` bölümü doldurulmuş.
- Yasak pattern'ler kontrol edilmiş; varsa azaltım uygulanmış.
- Coverage gereken kapsam için `pytest --cov` veya benzer ölçüm yapılmış.
- Streamlit doğrulamasında belirgin yavaşlama yoksa note yazılmış.
