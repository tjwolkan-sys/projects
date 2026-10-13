---
name: bsg-streamlit-tdd
description: bs-gamma-analyzer'da Streamlit/UI değişikliklerinde pure analiz unit testleri ile `streamlit.testing.v1` contract testlerini birlikte yürütmek için. Sekme render kararları, sidebar kontrol davranışı, session state mutasyonu ve Plotly figür konfigürasyon testlerinde kullan.
---

# bsg-streamlit-tdd

## Amaç

Streamlit UI değişikliklerinde TDD'yi iki katmanda uygulamak: (1) UI'dan ayrılmış pure analiz fonksiyonları için pytest unit testi, (2) sekme/sidebar render kararları için `streamlit.testing.v1` contract testi.

## Kısa Vade Test Kökü

Pure analiz mantığı UI'dan izole edilerek test edilir:

```text
tests/streamlit/<domain>/test_<konu>.py
```

Domain örnekleri:

- `routing` (sekme yönlendirme)
- `sidebar` (sidebar kontrol mantığı)
- `state` (session state mutasyonu)
- `render` (Plotly figür yardımcıları)
- `pipeline` (UI'dan tetiklenen pipeline orkestrasyonu)

## Orta Vade Test Kökü

`streamlit.testing.v1` API'si ile gerçek sekme davranışı test edilir:

```text
tests/streamlit/contract/test_<sekme>_<davranış>_contract.py
```

Örnekler:

- `test_overview_tab_event_count_metric_contract.py`
- `test_sidebar_data_source_select_session_state_contract.py`
- `test_ai_analysis_tab_render_report_card_contract.py`

## TDD Akışı

1. Değişen UI davranışını tek cümleyle yaz.
2. Davranış pure analiz fonksiyonuna izole edilebilir mi karar ver.
3. İzole edilebilirse `tests/streamlit/<domain>` altında pure unit testi yaz.
4. Sekme render kararı veya session state etkileşimi varsa `streamlit.testing.v1` contract testi yaz.
5. Mock'lanması gereken dış servisi (`anthropic`, SQLAlchemy) sahte yanıtla bağla.
6. Testleri yeşile çıkar; minimum üretim kodu değişikliği yap.
7. Refactor sonrası testleri tekrar çalıştır.

## Test Türleri

- **Characterization**: Mevcut sekme render kararını sabit fixture ile kilitler.
- **Regression**: Geçmişte tespit edilen UI hatasının tekrar dönmediğini kontrol eder.
- **Contract**: `st.session_state` anahtar adı ve tipi, sekme sırası, sidebar key'leri için kontrat testi.
- **Feature**: Yeni sekme veya yeni davranış için başarısız → yeşil → refactor TDD akışı.

## Streamlit Unit Test Notları

- `streamlit.testing.v1.AppTest.from_file("app.py")` ile uygulamayı başlat.
- Etkileşim için `at.sidebar.selectbox(key=...).set_value(...).run()` zinciri kullanılır.
- Render edilen elemanlar için `at.tabs[<index>].markdown[0].value` gibi indeks bazlı kontroller yapılır.
- Anthropic SDK ve SQLAlchemy engine `monkeypatch` ile mock'lanır.
- Headless mod sayesinde tarayıcı gerekmez; testler CI'da çalışır.

## Kaçınılacaklar

- Streamlit testinde gerçek Anthropic API'sine veya gerçek PostgreSQL'e gitmek.
- `time.sleep` ile yarış durumunu beklemek; kontrol edilebilir akış kullan.
- `streamlit.testing` API'sini kullanmadan UI tarafını test edilemez kabul etmek.
- Karakterizasyon testi atılmadan davranış değiştirmek.
- Test fixture'ında gerçek API key veya gerçek DB credential'ı kullanmak.

## Final Çıktı

- Yazılan başarısız UI testi.
- Yapılan üretim kodu değişikliği.
- Pure unit testi ve `streamlit.testing` contract testi durumu.
- Mock kullanılan servislerin listesi.
- Plan dosyasında işaretlenen adımlar ve plan kapanış durumu.
