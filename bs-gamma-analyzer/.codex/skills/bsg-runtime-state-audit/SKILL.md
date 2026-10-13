---
name: bsg-runtime-state-audit
description: bs-gamma-analyzer'da Streamlit `st.session_state`, `@st.cache_data`/`@st.cache_resource`, modul düzeyinde mutable global ve veri yeniden hesaplama akışlarını incelemek için. State drift, cache invalidation hatası, sekme arası tutarsızlık veya çoklu kullanıcı yan etkisi riskinde kullan.
---

# bsg-runtime-state-audit

## Akış

1. Audit kapsamını yaz: hangi state, hangi sekme, hangi cache, hangi global?
2. State'in yazıcı (set) ve okuyucu (get) tüm noktalarını çıkar.
3. Aynı state'i farklı sekmelerin nasıl tükettiğini haritala.
4. Cache decorator'ların (`@st.cache_data`, `@st.cache_resource`) parametrelerini ve TTL/hash davranışını listele.
5. Cache invalidation tetikleyicisi (yeniden yükleme, parametre değişikliği) açık mı kontrol et.
6. Çoklu kullanıcı senaryosunda state'in izole olup olmadığı raporla.
7. Drift veya tutarsızlık varsa kök neden çözümü için `bsg-no-workaround-root-cause-engineering` skill'ini öner.

## Bilinen Hotspot'lar

- `st.session_state["engine"]`: PostgreSQL engine; oturum boyunca tutulur. Kullanıcı bağlantıyı değiştirdiğinde eski engine bırakılmıyor olabilir.
- `st.session_state["df_raw"]`: Ham DataFrame. Yeniden yükleme yapılmadan eski veriyle çalışma riski vardır.
- `st.session_state["df_enriched"]`: Pipeline çıktısı. Backtesting parametre slider'ı değiştiğinde yeniden hesap tetiklenmiyor olabilir.
- `st.session_state["analysis_reports"]`: Claude analizi listesi. Yeniden yükleme yapılmadan eski analizler kalır.
- `@st.cache_data` ile saklanan analiz çıktıları: hash key parametresi `df` dahil olmuyorsa stale data döner.
- Modul düzeyinde global liste/dict: yoktur olması beklenir; varsa drift kaynağı olur.

## Çıktı Formatı

```markdown
## Audit Kapsamı: <state/cache/global>

### Yazıcı Noktalar
- <dosya:satır>: <ne zaman set ediliyor>

### Okuyucu Noktalar
- <dosya:satır>: <hangi sekme tüketiyor>

### Cache Davranışı
- Decorator: `@st.cache_data` / `@st.cache_resource`
- TTL: <varsa>
- Hash key alanları: <fonksiyon parametreleri>
- Invalidation tetikleyici: <açıklama>

### Drift Riski
- <senaryo açıklaması>

### Öneri
- <kök neden çözümü, refactor, doküman güncellemesi>
```

## Yapılmayacaklar

- State'i lokal patch ile sıfırlamak; neden tetikleniyor anlaşılmadan reset eklenmez.
- `st.session_state.clear()` ile geniş kapsamlı silme; kullanıcı kaybedilen veriyi anlayamaz.
- Cache decorator'ı silmek/eklemek için karakterizasyon testi olmadan değişiklik yapmak.
- Yeni global eklemek; mutable state'i `st.session_state` üzerinden yönet.
