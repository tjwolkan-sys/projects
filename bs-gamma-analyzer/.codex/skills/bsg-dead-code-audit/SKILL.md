---
name: bsg-dead-code-audit
description: bs-gamma-analyzer'da kod silmeden önce kullanım izlerini, Streamlit sekmesi/sidebar/`st.session_state`/Plotly figür/SQL kolon/Anomaly kategori/Claude prompt referanslarını kanıtlamak için. Aday kullanılmıyor görünse bile dinamik kullanım, kolon adı bağımlılığı ve eski anomaly kategori bağı vardır.
---

# bsg-dead-code-audit

## Ana İlke

Bu skill silme yapmaz; silmeyi destekleyen kanıtı toplar. Statik (`grep`, import zinciri) ve dinamik (Streamlit sekme, kolon adı, SQL string, Anomaly kategori adı, Claude prompt) bağları çıkarılır.

## Akış

1. Aday silme noktasını yaz: fonksiyon, sınıf, sabit, kolon adı, sekme, sidebar key, prompt alanı.
2. Statik tarama yap:
   - `rg <isim>` ile tüm referanslar.
   - Python import zinciri (`from <modul> import <isim>`).
   - Test dosyalarında kullanım.
3. Dinamik tarama yap:
   - Pandas kolon adı olarak kullanımı: `df[<kolon>]`, `df.<kolon>`, `assign(<kolon>=...)`.
   - Streamlit `st.tabs`, `st.session_state[<key>]`, `st.sidebar.<input>(<label>)` referansları.
   - Plotly figür konfigürasyonunda kolon adı geçişi.
   - SQL string içinde kolon/tablo adı.
   - Anomaly kategori adı: `core/anomaly_detector.py` içinde sabit string.
   - Claude prompt veya `AnalysisReport` alan adı.
4. Belge ve roadmap'lerde geçiyor mu kontrol et: `docs/`, `AGENTS.md`, `*.md` taraması.
5. Tüm bulguları aday başlığı altında listele.
6. Risk/öneri yaz: gerçekten dead code mı, yoksa dinamik kullanımı var mı?
7. Silmeye karar verilirse ayrı plan dosyası ile yürür; bu skill yalnız audit raporu sunar.

## Korumalı Alanlar

- Pandas kolon adları ve `camel_to_snake` ile üretilen alternatifler.
- Streamlit sekme adı ve sırası.
- `st.session_state` anahtarları.
- Anomaly kategori adları ve severity sabitleri.
- Claude system prompt ve `AnalysisReport` alanları.
- SQL tablo/kolon adları (`predictive_gamma_events` ve alt kolonları).
- `core/formula.py` Java parity'li sabitler ve mode adları.
- Plotly figürlerinin layout/axis isimleri.

## Çıktı Formatı

```markdown
## Aday: <isim>

### Statik referanslar
- <dosya:satır>

### Dinamik referanslar
- Kolon adı kullanımı: <dosya:satır>
- Streamlit referansı: <dosya:satır>
- SQL string referansı: <dosya:satır>
- Doküman referansı: <dosya:satır>

### Risk
- <silme yapılırsa hangi davranış kırılır>

### Öneri
- Sil / Tut / Daha fazla kanıt gerekli
```
