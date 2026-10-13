---
name: bsg-modular-app-split
description: bs-gamma-analyzer'da monolitik `app.py` dosyasını modüler `ui/`, `data_sources/` ve `pipeline.py` yapısına sekme/sidebar contract'larını kırmadan, contract testleri ve feature flag'lerle taşımak için. Modüler split adımlarında kullan.
---

# bsg-modular-app-split

## Amaç

Mevcut `app.py` tek dosyalı orkestrasyonunu küçük, test edilebilir modüllere kademeli olarak taşımak. Davranışı kırmadan, sekme sırasını ve session state anahtarlarını koruyarak ilerlemek.

## Migration İlkeleri

- Bir adımda hem davranış değiştir hem refactor yap; iki işi karıştırma.
- Her adım kendi karakterizasyon testini öncesinde yazsın.
- Adım sonunda Streamlit elle açılarak görsel doğrulama yapılır; yapılamadıysa plan dosyasında blocker yazılır.
- Tek seferde tüm sekmeleri taşıma; sekme sekme veya katman katman ilerle.
- Roadmap referansı: `docs/project/MODULAR-APP-SPLIT-ROADMAP.md`.

## Taşınacak Contract'lar

- Sekme adları ve sırası (Genel Bakış → ... → Ham Veri).
- Sidebar kontrol key'leri.
- `st.session_state` anahtar adları (`engine`, `df_raw`, `df_enriched`, `analysis_reports`).
- AI Analiz sekmesinin `_render_report` kartları.
- Plotly figür layout/axis adlarına dokunulmaması.
- Pure analiz fonksiyonlarının `core/` modüllerinden değişmeden taşınması.

## Migration Slice Çıktısı

Her slice için plan dosyası şu çıktıyı içerir:

- Slice kapsamı (hangi sekme veya katman).
- Önceki davranışı kilitleyen karakterizasyon testleri.
- Yeni dosya yapısı ve import grafı.
- `app.py` içindeki çıkarılacak kod blokları ve yeni dosyaya taşınan satırların referansı.
- Davranış değişmediği iddiasını doğrulayan test komutları.
- Görsel doğrulama notu.
- Geri alma planı (revert nasıl yapılır).

## TDD Beklentisi

- Slice öncesi `bsg-characterization-tests` ile mevcut davranış kilitlenir.
- Slice sırasında pure unit testleri yazılır.
- Slice sonrası `streamlit.testing.v1` contract testleri yeşil olmalıdır.
- Refactor adımı sonunda davranış değişikliği var mı diye karakterizasyon testleri tekrar koşulur.

## Yapılmayacaklar

- Sekme adı veya sırasını değiştirmek.
- `st.session_state` anahtarı silmek; eski anahtar deprecation süresince korunur.
- Pure analiz fonksiyonu davranışını "iyileştirme" amaçlı değiştirmek.
- Yeni paket veya yeni LLM provider eklemek.
- Java parity'li kodu (`core/formula.py`) refactor sırasında değiştirmek.
- Roadmap'in dışında "her şeyi tek seferde böl" denemek.

## Çıktı

- Taşınan dosyalar ve yeni dosya yapısı.
- Karakterizasyon ve contract test sonuçları.
- Görsel doğrulama notu.
- Roadmap dosyasında ilgili adımın işaretlenmesi.
- Geri alma planının dokümante edildiği plan dosyası.
