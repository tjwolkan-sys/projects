---
name: bsg-tdd-workflow
description: bs-gamma-analyzer'da her hata düzeltmesi ve özellik geliştirmesini TDD ile yürütme akışı. Python/pandas core mantığı, Streamlit UI, anomali tespiti, Claude entegrasyonu, PostgreSQL queries, legacy hata düzeltmesi, refactor öncesi test, karakterizasyon testi, regression test, contract test veya pytest paketi gerektiğinde kullan.
---

# bsg-tdd-workflow

## Temel Kural

bs-gamma-analyzer'da her geliştirme testle başlar. Legacy davranış belirsizse önce karakterizasyon testi yazılır; istenen yeni davranış için başarısız test eklenir; sonra en küçük üretim kodu değişikliği yapılır.

## Analiz ve Plan Ön Koşulu

1. Kod değişikliği gerektiren istekte önce iş tipini belirle.
2. Sorun ve çözüm yönü netleştiğinde kullanıcıya `Plan aşamasına geçeyim mi?` diye sor; uygun plan türünü açık yaz: yeni feature için `new feature planı`, feature dışı işler için `change-fix planı`.
3. Aynı mesajda plansız doğrudan çözümü uygulama seçeneğini de söyle ve kendi önerini kısa gerekçeyle belirt.
4. Kullanıcı plansız uygulamayı seçerse analiz, execution plan veya change-fix-plan dosyası açma; çözümü etkileyen belirsizlik varsa kod değişikliğinden önce kullanıcıya sor.
5. İş etiketini commit tipi standardına göre belirle: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`, `ci`, `perf` veya `revert`.
6. Kullanıcı planlı ilerlemeyi seçerse yeni feature işleri için önce `docs/analyse/GG.AA.YYYY/FEAT-KIMLIK-KISA-AD.md` altında analiz, ardından `docs/execution-plan/GG.AA.YYYY/FEAT-KIMLIK-KISA-AD.md` altında checkbox'lı execution plan oluştur.
7. Kullanıcı planlı ilerlemeyi seçerse fix, hotfix, change request, refactor, perf, test, build, ci, chore, revert ve feature dışı diğer işler için `docs/change-fix-plan/GG.AA.YYYY/ETIKET-KIMLIK-KISA-AD.md` altında tek dosyada analiz detayları ve execution plan bölümü oluştur.
8. Planlama dokümanında mevcut davranış, beklenen davranış, etkilenen kod alanları, riskler, TDD stratejisi, hedefli doğrulama komutları, kapanış kriteri ve `Açık Sorular` bölümü bulunsun.
9. Plan yazıldıktan sonra `Açık Sorular` bölümünde cevaplanmamış madde varsa bunları kullanıcıya açıkça sor; kullanıcı ayrıca "açık sorular neler" demeden bekleme.
10. Cevaplanmamış açık soru varken kod değişikliğine geçme; yanıtları plana işlemeden üretim kodu, test veya config değiştirme.
11. Planlı akışta kod değişikliğini plan üzerinden yürüt ve tamamlanan adımları işaretle.
12. Plan dışına çıkarsan önce planı güncelle, açık soru doğduysa kullanıcıdan yanıt al, sonra uygulamaya devam et.
13. Planlanan testler çalıştırılıp başarılı olmadan planı tamamlandı/kapatıldı sayma; test çalıştırılamıyorsa plan doğrulama beklemede kalır.

## Red-Green-Refactor Akışı

1. Değişecek davranışı tek cümleyle yaz.
2. En yakın test dosyasını bul; yoksa yeni test dosyasını test dosyası standardına göre aç.
3. Mevcut davranış riskliyse önce karakterizasyon testi ile bugünkü davranışı kilitle.
4. İstenen davranış için başarısız test yaz.
5. Yalnızca testi geçirecek kadar üretim kodu değiştir.
6. Hedefli testi çalıştır:
   - Hedefli pytest: `pytest tests/<domain>/test_<konu>.py -k <pattern>`
   - Domain paketi: `pytest tests/<domain>`
7. Yeşil olduktan sonra yalnızca güvenli ve küçük refactor yap.
8. Refactor sonrası aynı hedefli testi tekrar çalıştır.

## Yerel Tool Path Ön Koşulu

Python, pip, virtualenv, Streamlit veya pytest gerektiren test/doğrulama çalıştırmadan önce repo kökündeki `.codex/TOOL-PATHS.local.properties` dosyasını kontrol et.

- Dosya varsa `PYTHON_CMD`, `VENV_DIR`, `PIP_CACHE_DIR`, `PIP_INDEX_URL`, `PIP_EXTRA_INDEX_URL`, `STREAMLIT_CMD`, `PYTEST_CMD` ve `POSTGRES_*` değerlerini kullan.
- `PYTHON_CMD` varsa PATH'teki `python` yerine bu binary ile çalıştır.
- `VENV_DIR` varsa `VENV_DIR/Scripts/python.exe` (Linux/macOS'ta `bin/python`) kullanılır.
- Pip komutlarına varsayılan olarak `--no-index` veya `--no-deps --no-build-isolation` ekle; ajan dependency indirmeyi denemez.
- `PIP_CACHE_DIR` varsa pip komutuna `--cache-dir <dir>` ekle.
- Offline pip eksik paket nedeniyle durursa paket adını ve versiyonunu raporla; geliştiriciden VPN'de pip cache veya internal index'i güncelleyip VPN'den çıktıktan sonra tekrar ajana dönmesini iste.
- Pytest yalnızca virtualenv eksiği veya cache eksiği nedeniyle çalışamıyorsa alternatif test runner deneme; eksik paketi raporla ve geliştirici cache güncelledikten sonra aynı offline pip kurulumu tekrar dene.
- Local dosya yoksa PATH'teki araçları kullan; araç bulunamazsa bunu doğrulama sonucu olarak açıkça raporla.
- Gerçek path'leri yalnızca `.local.properties` dosyasında tut; şablon ve dokümanlarda yer tutucu kullan.
- Anthropic API key veya PostgreSQL şifresi tool path dosyasına yazılmaz.

## Test Dosyası Standardı

Test kökü:

```text
tests/<domain>/
```

Domain örnekleri:

- `formula`
- `validator`
- `anomaly`
- `relations`
- `llm`
- `db`
- `streamlit`
- `data_loader`

Test dosyası adı:

```text
test_<konu>_<davranış>_<test_tipi>.py
```

Test tipi örnekleri:

- `characterization`
- `regression`
- `contract`
- `feature`

Örnek dosyalar:

- `tests/formula/test_formula_bootstrap_characterization.py`
- `tests/anomaly/test_abandon_spike_severity_regression.py`
- `tests/llm/test_claude_response_truncation_contract.py`
- `tests/db/test_get_campaign_data_filter_contract.py`

Mevcut test dosyası doğrudan ilgili davranışı kapsıyorsa aynı dosya genişletilebilir. Yeni davranış başka domain'e aitse yeni `tests/<domain>/` klasörü tercih edilir.

## Streamlit Test Notları

- Streamlit context gerektiren testler için pure analiz fonksiyonu izole edilir; test mümkün olduğunda `core/` modüllerine yöneltilir.
- Sekme render davranışı doğrudan test edilemiyorsa `streamlit.testing.v1` API'si veya UI'dan ayrılmış helper fonksiyonu kullanılır.
- `st.session_state` üzerinden yapılan etkileşim davranışları için sahte session state objesi enjekte edilir.

## Legacy İstisnaları

- Gerçek PostgreSQL, Anthropic API veya production veri kaynağına test sırasında bağlanma; sabit fixture veya mock kullan.
- `apply_formula`, `validate`, `run_detection` ve `relations.*` fonksiyonları için sabit DataFrame fixture'ları tercih et.
- Async/race davranışında `time.sleep` ile test yazma; kontrol edilebilir clock veya callback noktası kullan.
- Production bug acilse bile en azından regression test eklemeden davranış değiştirme.

## Test Önceliği

- Gamma formülü mode geçişleri ve numerik parity.
- Anomaly kategori sınırları, severity skalası ve precursor/recovery şeması.
- Validator tolerans davranışı.
- `db/queries.py` SQL contract'ı.
- Veri kaynağı yükleyici kolon normalizasyonu.
- Claude analizi JSON parse ve hata yolu.
- Streamlit kontrolleri için pure analiz fonksiyonu izolasyonu.

## Final Çıktı

Her TDD çalışmasının sonunda şunları raporla:

- Yazılan başarısız test.
- Yapılan üretim kodu değişikliği.
- Kısa Test Özeti: çalıştırılan pytest komutları ve durumları.
- Çalıştırılamayan test varsa nedeni.
- Plan dosyasında işaretlenen adımlar, açık soru durumu ve plan kapanış durumu.
