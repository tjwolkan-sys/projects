---
name: bsg-safe-change
description: bs-gamma-analyzer üretim parity'sini bozmadan küçük ve doğrulanabilir değişiklikleri yapmak için. Java parity'li Gamma formülü, anomaly contract'ı, Claude prompt'u, PostgreSQL şeması, Streamlit session state veya veri kaynağı yükleyici davranışı etkileyen küçük değişiklik için kullan.
---

# bsg-safe-change

## Akış

1. Hangi davranışın değişeceğini tek cümleyle yaz.
2. Etkilenen modüllerin mevcut davranışını koddan oku; varsayım yapma.
3. Tüketici noktaları çıkar: Streamlit sekmeleri, `st.session_state`, downstream analiz fonksiyonları, görselleştirme katmanı.
4. Risk başlığı yap: Java parity, kategori adı, severity, JSON şeması, kolon adı, SQL şeması, sekme sırası.
5. Risk yüksekse `bsg-characterization-tests` skill'ini de çalıştır ve mevcut davranışı kilitle.
6. Değişikliği en küçük yüzeye sığdır; ilgisiz refactor ekleme.
7. Hedefli pytest komutlarıyla doğrula.
8. Doküman ve history etkisini kontrol et.

## Kurallar

- Kırılgan runtime state'i (Streamlit session state, `@st.cache_data`, mutable global) genişletme.
- Yeni mode adı, kategori adı veya severity skalası eklerken downstream tüketicileri kontrol et.
- `predictive_gamma_events` tablosuna yazma yapma; SQL contract'ı korunur.
- Anthropic prompt veya JSON şemasını gerekçesiz değiştirme; `bsg-llm-claude-contract` ile birlikte ele al.
- Yeni paket, yeni veri kaynağı, yeni LLM provider eklemeden önce geliştiriciye bildir.
- Tek değişiklikle birden çok contract'ı (formül + UI + SQL) aynı anda kırma.
- Çalışma ağacındaki ilgisiz değişikliği geri alma; kendi değişiklik kapsamına sadık kal.

## Yararlı Arama Kalıpları

- Gamma formülü tüketicisi: `apply_formula`, `xn_expected`, `mode`.
- Anomaly tüketicisi: `run_detection`, `AnomalyEvent`, `CategorySummary`, `categories`.
- Claude tüketicisi: `analyze_anomaly`, `AnalysisReport`, `system_prompt`.
- SQL tüketicisi: `predictive_gamma_events`, `get_campaign`, `read_sql`.
- Streamlit tüketicisi: `st.session_state`, `st.tabs`, `st.sidebar`, `st.cache_data`.

## Çıktı

- Değiştirilen dosyalar.
- Etkilenen contract listesi (parity, kategori, JSON, SQL, UI).
- Çalıştırılan pytest komutları ve sonuçları.
- Mimari/plan dokümanlarına eklenen güncellemeler.
- Üretim/parity riski varsa açık not.
