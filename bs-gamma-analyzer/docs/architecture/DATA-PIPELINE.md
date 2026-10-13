# Veri Hattı

bs-gamma-analyzer, veri kaynağı türünden bağımsız tek bir pandas DataFrame üzerinde analiz yapar. Bu doküman yükleme, normalizasyon, hesaplama ve görselleştirme katmanlarını sırayla anlatır.

## Veri Kaynakları

- **Excel (.xlsx, .xls)**: `pandas.read_excel`. İlk sayfa varsayılır; çoklu sayfa varsa sayfa seçimi sidebar'da yapılır.
- **CSV**: `pandas.read_csv`. UTF-8 varsayılır.
- **JSON**: Liste veya dictionary tabanlı esnek formatlar `app.py` içindeki `load_json` ile parse edilir.
- **PostgreSQL**: `db/queries.py` üzerinden `pandas.read_sql_query`.

## Kolon Normalizasyonu

- Tüm kolon adları camelCase'den snake_case'e dönüştürülür (`camel_to_snake`).
- Whitespace kırpılır, ileri özel karakter yoktur.
- Beklenen kolonlar bulunmuyorsa pipeline akışı kırılmaz; `KeyError` yerine eksik kolon bilgisi UI'da raporlanır.

## Pipeline Sırası

1. **Yükleme**: Kaynaktan ham DataFrame elde edilir → `df_raw`.
2. **Normalizasyon**: Kolon adları snake_case yapılır.
3. **`apply_formula(df, cf_override, as_override)`**: `xn_expected`, `raw_xn_expected`, `ringing_load_expected`, `abandon_penalty_expected`, `mode` kolonları eklenir.
4. **`validate(df)`**: `xn_diff`, `xn_diff_abs`, `xn_diff_pct`, `is_match`, `match_status` kolonları eklenir.
5. **`anomaly.detect(df)`**: `anomaly_flags`, `has_anomaly` kolonları eklenir.
6. **`anomaly_detector.run_detection(df)`**: `enriched_df`, `anomaly_events`, `categories`, `stats` döner.
7. **`relations.*`**: Çoklu metrik ilişki analizleri çalıştırılır.
8. **AI analizi (opsiyonel)**: Seçilen olay için `core.llm_analyzer.analyze_anomaly` çağrılır.
9. **Görselleştirme**: Plotly figürleri ve özet tabloları sekmelerde render edilir.
10. **Export (opsiyonel)**: Ham veri sekmesinde CSV download düğmesi ile DataFrame indirilir.

## Memory ve Performans Notları

- Büyük dataset'ler için `precursor_df`/`recovery_df` saklama memory'i hızla artırır; analiz fonksiyonlarının iç slice mantığı `bsg-data-pipeline-performance-guardrails` skill'i kapsamındadır.
- Plotly figür sayısı sekme bazında sınırlanır; bir sekmede 10+ figür olduğunda toolbar yavaşlar.
- DB modunda kampanya/tarih filtresi olmadan çekim yapmak önerilmez.

## Kolon Beklentileri

Pipeline aşağıdaki kolonları okur. Eksik olduğunda akış degrade olur, kırılmaz.

- Zorunlu: `available_agents_count`, `incall_agents_count`, `xn`, `raw_xn`, `avg_talk_time`, `avg_ring_time`.
- Önerilen: `ringing_cb_count`, `ringing_retry_count`, `ringing_fresh_count`, `cb_answer_rate`, `retry_answer_rate`, `fresh_answer_rate`, `computed_abandon_rate`, `successful_window_size`.
- Opsiyonel: `call_per_second`, `cache_limit`, `completion_percent`, `campaign_state`, `correction_factor`.

## Hata Akışı

- Yükleme hatası: `st.error` ile UI'da gösterilir, pipeline başlamaz.
- Normalizasyon hatası: Yeni kolon adı uyarısı verilir; bilinen kolonlar yine de işlenir.
- Formül hatası: NaN/Inf yakalanır ve `fallback_nan` mode'una düşer.
- Detector hatası: Olay üretilmediğinde sekme boş gösterilir; UI uyarı verir.

## Change Contract

- Yeni veri kaynağı eklemek `bsg-data-source-loader-contract` skill'i altında yürür.
- Pipeline sırası geriye dönük korunur; yeni adım sona eklenir.
- Yeni kolon beklentisi eklendiğinde mevcut export ve görselleştirmenin etkisi raporlanır.
- Memory ve performans bütçesi `bsg-data-pipeline-performance-guardrails` ile yönetilir.
