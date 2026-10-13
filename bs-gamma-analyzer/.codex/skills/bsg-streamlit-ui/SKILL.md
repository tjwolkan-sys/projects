---
name: bsg-streamlit-ui
description: bs-gamma-analyzer'da `app.py` Streamlit sekme, sidebar, session state ve Plotly figür değişikliklerini güvenli yapmak için. Sekme sırası, sidebar kontrol adı, `st.session_state` anahtarı, AI Analiz render kartları veya Plotly figür konfigürasyonu üzerinde çalışırken kullan.
---

# bsg-streamlit-ui

## Amaç

Streamlit UI değişikliklerini sekme sırası, sidebar key'leri, session state anahtarları ve render kontratlarını koruyarak yapmak. UI değişikliği plan dosyası ile yürür; davranış değişikliği ile karıştırılmaz.

## Repo Gerçekliği

- `app.py` tek dosya hâlinde sekme orkestrasyonu, sidebar ve render mantığını barındırır.
- Modüler bölme `docs/project/MODULAR-APP-SPLIT-ROADMAP.md` altında planlıdır; aktif refactor sırasında sekme adı/sırası korunur.
- Sekme sırası: Genel Bakış → Abandon Analizi → Formül Doğrulama → Grafikler → Backtesting → İleri Analiz → İleri Analiz 2 → AI Analiz → Ham Veri.
- Sidebar kontrolleri: veri kaynağı seçimi, dosya yükleyici / DB form, kampanya/tarih filtresi, Anthropic API key, backtesting slider'ları.

## Çalışma Akışı

1. Değişiklik kapsamını çıkar: hangi sekme, hangi sidebar kontrolü, hangi figür?
2. Etkilenen `st.session_state` anahtarlarını çıkar.
3. UI metni Türkçe olabilir; iç kullanılan key'ler kod tarafında sabit kalır.
4. Sekme sırasını koru; yeni sekme sona eklenir.
5. Sidebar kontrol key'leri kontrat olarak korunur; yeni kontrol sona eklenir.
6. Plotly figür konfigürasyonunda layout/axis tutarlılığı korunur.
7. Hata mesajları kullanıcıya `st.error`, `st.warning`, `st.info` ile gösterilir.
8. Pure analiz fonksiyonları UI'dan ayrı tutulur; test edilebilir kalır.

## Dikkat Edilecek Contract'lar

- Sekme adları ve sırası geriye dönük korunur.
- Sidebar kontrol key'leri Python kodunda sabit kalır; UI metni Türkçe değişebilir.
- `st.session_state` anahtarları silinmez; eklenirken dokümante edilir.
- Cache decorator (`@st.cache_data`, `@st.cache_resource`) parametreleri belirgin yazılır.
- AI Analiz sekmesinin render kartları `_render_report` fonksiyonu üzerinden gider; alan adları `AnalysisReport` ile birebirdir.
- Plotly figürünün col/index referansı snake_case kolon adlarına bağlıdır.

## Yararlı Arama Kalıpları

- Sekme: `st.tabs`, `tab1, tab2`, `with tab`.
- Sidebar: `st.sidebar`, `st.sidebar.text_input`, `st.sidebar.selectbox`, `st.sidebar.file_uploader`, `st.sidebar.slider`.
- Session state: `st.session_state[`, `st.session_state.`.
- Cache: `@st.cache_data`, `@st.cache_resource`.
- Render: `st.plotly_chart`, `st.dataframe`, `st.metric`, `st.markdown`.

## Çıktı

- Değiştirilen sekme/sidebar/state.
- Yeni anahtar varsa dokümante edildi.
- Plotly figür konfigürasyon değişikliği varsa görsel etkisi açıklandı.
- `streamlit.testing` veya pure analiz testleri yeşil.
- `STREAMLIT-UI.md` mimari dokümanı güncel.
