---
name: bsg-safe-refactor
description: bs-gamma-analyzer'da davranış değiştirmeden, test koruması altında küçük ve güvenli refactor yapmak için. Pure fonksiyon ayrıştırma, modül bölme, isim düzeltme, dependency boundary, deduplication veya `app.py` parçalama işleri için kullan.
---

# bsg-safe-refactor

## Refactor Ön Koşulu

- Refactor davranış değiştirmez. Davranış değişikliği gerekiyorsa ayrı plan ile `feat`, `fix` veya `perf` etiketinde yürür.
- Refactor öncesi hedef koda yönelik testlerin yeşil olduğu doğrulanır.
- Test yoksa önce karakterizasyon testi yazılır (`bsg-characterization-tests`).

## Akış

1. Refactor amacını tek cümleyle yaz.
2. Etkilenecek modülleri çıkar; pure fonksiyon mu, Streamlit UI mu, DB katmanı mı?
3. Mevcut davranış için pytest paketinin çalıştığını doğrula.
4. En küçük bölme/yeniden adlandırma adımını uygula.
5. Pytest paketi yeşil mi tekrar kontrol et.
6. Adımları küçük commit'lerle ayır; mümkünse her adım kendi başına yeşil.
7. Mimari doküman etkileniyorsa aynı iş içinde güncelle.

## Repoya Özel Riskler

- `core/formula.py` Java parity'sine bağlıdır; refactor sırasında numerik davranış değişmemelidir.
- `core/anomaly_detector.py` kategori adları, severity ve precursor/recovery şeması korunmalıdır.
- `core/llm_analyzer.py` system prompt ve `AnalysisReport` alanları kontrat olarak tutulur.
- `db/queries.py` SQL cümleleri kontrat olarak korunur.
- `app.py` sekme sırası ve sidebar key'leri kontrat olarak korunur.
- `st.session_state` anahtarı silmek refactor sayılmaz; bu davranış değişikliğidir.

## Yapılmayacaklar

- Dead code silmek; bunun için `bsg-dead-code-audit` ayrı yürür.
- Davranış düzeltme/iyileştirme; ayrı plan dosyası açılır.
- Yeni kütüphane veya bağımlılık eklemek; `chore`/`build` planı gerektirir.
- Aynı PR/diff içinde davranış değişikliği + refactor karıştırmak.
- Test güncellenirken assertion'ları sessizce düşürmek; karakterizasyon testi davranışı kilitlemeli.
- Sekme sırası, kategori adı, JSON alanı veya SQL kolonunu yeniden adlandırmak.
