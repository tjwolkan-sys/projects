---
name: bsg-characterization-tests
description: bs-gamma-analyzer'da belirsiz veya kırılgan mevcut davranışı değiştirmeden önce testle kilitlemek için. Gamma formülü mode'ları, anomaly kategorileri, Claude JSON parse, SQL contract, Streamlit pipeline davranışı veya veri yükleyici kolon normalizasyonu üzerinde değişiklik yapmadan önce kullan.
---

# bsg-characterization-tests

## Akış

1. Davranışı tek cümleyle özetle: ne yapıyor, hangi girdiyi alıyor, hangi çıktıyı döndürüyor.
2. Bilinen örnek girdiyi sabit fixture olarak hazırla (sabit DataFrame, sabit AnomalyEvent, sabit Claude response, sabit SQL satırı).
3. Mevcut davranış için pytest assertion'ları yaz: hangi kolonlar set ediliyor, hangi mode tetikleniyor, hangi severity dönüyor.
4. Test ilk başta yeşil olmalı; çalışan davranışı kilitliyor.
5. Test isimlendirmesinde davranışı tarif et: `test_<konu>_<girdi>_<beklenen_davranış>`.
6. Davranış değişiyorsa karakterizasyon testi silinmez; regression veya feature testine dönüştürülür.

## Öncelikli Alanlar

- `core/formula.py`: Her mode (`bootstrap`, `fallback_zero_talk`, `fallback_nan`, `safety_floor`, `normal`) için sabit girdiyle çıktı kilidi.
- `core/anomaly_detector.py`: Her kategori için sabit fixture; severity ve triggered_by haritası kilitli.
- `core/anomaly.py`: Her flag için minimum tetikleyici girdi.
- `core/llm_analyzer.py`: Claude'un sabit yanıtı için JSON parse, truncated repair ve hata yolları.
- `core/relations.py`: Her ilişki fonksiyonunun çıktı şeması ve hesaplama kilidi.
- `db/queries.py`: SQL string contract'ı (mock engine ile).
- `app.py`: Pure helper'lar (`camel_to_snake`, `load_json`, `_render_report`) için izole test.

## Stil

- Sabit fixture'ı test dosyası başında veya `conftest.py` altında tanımla.
- `pandas.testing.assert_frame_equal` ile beklenen DataFrame kolon ve değerlerini karşılaştır.
- Float karşılaştırmalarda tolerans kullan: `pytest.approx`, `np.allclose`.
- Test ad ve assertion'lar mevcut davranışı tarif eder; "yapılması gereken" davranışı tarif etmez.
- Mock'lar yalnız dış servis için kullanılır (Anthropic, PostgreSQL); pure fonksiyonlar mock'lanmaz.
- Pytest test sınıfı kullanmak zorunlu değildir; düz fonksiyon stili tercih edilir.
