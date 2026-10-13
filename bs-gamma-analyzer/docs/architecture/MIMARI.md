# Üst Seviye Mimari

bs-gamma-analyzer, üretimde çalışan Bulut Santral Predictive Dialer'ından gelen olay (event) verisini analiz eden ve Java tarafındaki Gamma formülüyle parity tutan tek başına çalışan bir Streamlit uygulamasıdır. Uygulama tek bir geliştirici/destek/operasyon kullanıcısı için lokal veya küçük ekip bazında ayağa kalkar; çok kullanıcılı yüksek ölçek hedefi yoktur.

## Modül Haritası

- `app.py`: Streamlit giriş noktası ve sekme orkestrasyonu. Sidebar üzerinden veri kaynağı seçimi, parametre tuning, Anthropic API key alımı ve kampanya filtreleme yapılır. Yüklenen `DataFrame` üzerine sırayla `apply_formula`, `validate`, `detect`, `run_detection` ve ileri ilişki analizleri çağrılır.
- `core/formula.py`: Predictive Gamma formülünün Python implementasyonu. `compute_xn` tek satır için, `apply_formula` tüm DataFrame için çalışır. Mode adları (`bootstrap`, `fallback_zero_talk`, `fallback_nan`, `safety_floor`, `normal`) Java implementasyonunun aynısıdır.
- `core/validator.py`: Gerçek `xn` ile `xn_expected` arasındaki tolerans karşılaştırması (`TOLERANCE`, `TOLERANCE_PCT`) ve match raporu.
- `core/anomaly.py`: Flag tabanlı temel anomali tespiti. Eski katmandır; yeni geliştirmelerde detector katmanı tercih edilir.
- `core/anomaly_detector.py`: Rolling z-score, delta, kategori, severity, precursor/recovery pencereleri ile ileri anomali tespiti. `AnomalyEvent` ve `CategorySummary` dataclass'ları döndürür.
- `core/llm_analyzer.py`: Anthropic Claude entegrasyonu. `AnomalyEvent` üzerinden context build eder, system prompt ile Claude'a gider, JSON yanıtı `AnalysisReport` dataclass'ına parse eder.
- `core/relations.py`: `xn_ringing_analysis`, `agent_xn_analysis`, `cps_cache_analysis`, `xn_stability_analysis` fonksiyonlarıyla metrik ilişkilerini kurar.
- `db/connection.py`: SQLAlchemy `create_engine` factory'si ve `SELECT 1` test helper'ı.
- `db/queries.py`: `predictive_gamma_events` tablosuna read-only kampanya sorguları.
- `charts/`: Plotly figür yardımcıları için ayrılmış klasör.

## Veri Akışı

```
Kullanıcı veri kaynağı seçer (Excel | CSV | JSON | PostgreSQL)
    ↓
[app.py] DataFrame yüklenir → kolonlar camelCase'den snake_case'e normalize edilir
    ↓
[core.formula.apply_formula] xn_expected, mode ve breakdown'lar hesaplanır
    ↓
[core.validator.validate] gerçek xn ile beklenen xn karşılaştırılır → match_status üretilir
    ↓
[core.anomaly.detect] flag bazlı anomaly_flags eklenir
    ↓
[core.anomaly_detector.run_detection] kategori, severity ve precursor/recovery pencereleri üretilir
    ↓
[İsteğe bağlı] core.llm_analyzer.analyze_anomaly Claude analizi üretir
    ↓
Streamlit sekmelerinde Plotly figürleri ve özet tablolar render edilir
```

## Riskli Kavşaklar

- `apply_formula` içindeki mode tetikleme koşulları Java implementasyonu ile birebir parity'ye bağlıdır. Tek bir koşul kayması Java/Python arasında numerik drift yaratır.
- `run_detection` içindeki rolling z-score ve delta hesabı `min_periods=3` gibi varsayımlarla erken event'lerde gürültülü olabilir.
- `precursor_df` ve `recovery_df` her event için DataFrame slice tutar; büyük dataset'lerde memory yükü artar.
- `core/llm_analyzer.py` Claude'un üretebileceği truncated JSON için heuristic onarım yapar; karmaşık yapı bozulduğunda parse başarısız olur.
- `db/queries.py` SQL filtre cümlesini f-string ile örer; parametre sızıntısı olmaması için yalnız `text()` parametre binding'i kullanılır.
- Streamlit `st.session_state["engine"]` ve `st.session_state["df_raw"]` tek session boyunca yaşar; sekmeler arası mutasyon hatası kolayca yayılabilir.

## Hedef Davranış İlkesi

- Java predictive dialer ile Python analizi arasındaki numerik parity korunur.
- Anomaly kategori/severity contract'ı sabit kalır; eşikler değişirse plan dosyasında gerekçe yazılır.
- Streamlit sekme sırası ve sidebar kontrol adları geriye dönük korunur; yeni sekme sona eklenir.
- Persistence katmanı read-only kalır; uygulama dialer DB'sine yazma yapmaz.

## Change Contract

- Mode adları, sabitler, kategori adları, severity skalası, JSON şeması, kolon adları, sekme sırası ve SQL contract'ı geçiş notu olmadan değişmez.
- Yeni kolon, yeni mode veya yeni kategori eklenirken mevcut tüketicilerin davranışı kontrol edilir.
- Java tarafındaki `PredictiveGammaCallCalculator` değişikliği, parity testi olmadan Python tarafına işlenmez.
