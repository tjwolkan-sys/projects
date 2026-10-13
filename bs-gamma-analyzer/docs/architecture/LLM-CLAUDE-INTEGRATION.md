# Claude (Anthropic) Entegrasyonu

`core/llm_analyzer.py`, anomaly detector tarafından üretilen `AnomalyEvent` üzerinden Claude'a kök neden analizi sorar ve dönen JSON yanıtı `AnalysisReport` dataclass'ına parse eder. Bu doküman entegrasyonun contract'ını ve hata yollarını tarif eder.

## Bileşenler

- **`AnalysisReport`**: Claude'un yanıtının normalize edilmiş hâli. Alanlar: `summary`, `root_causes`, `precursor_signals`, `recovery_analysis`, `recommendations`, `watch_signals`, `raw_response`, `error`.
- **`build_context(event, campaign_params)`**: `AnomalyEvent` üzerinden Claude'a gidecek JSON context'i hazırlar.
- **`_build_analysis_prompt(context_str, user_question)`**: System prompt + user mesajını birleştirir.
- **`_call_claude(api_key, prompt, model)`**: Anthropic SDK üzerinden HTTP çağrısı yapar.
- **`_parse_response(raw, event)`**: Yanıt içindeki JSON bloğunu çıkarır, gerekirse `_repair_truncated_json` ile onarır.
- **`analyze_anomaly(event, api_key, ...)`**: Üst düzey orkestratör; auth, rate-limit ve diğer hataları `AnalysisReport.error` alanına yazar.

## System Prompt Contract

System prompt sabittir ve şu özelliklere sahiptir:

- Gamma algoritmasının temellerini özetler.
- Çıktının JSON formatında verilmesini ister.
- Yanıtı Türkçe ister.
- Hayali sayı/uydurma yorumdan kaçınmasını ister.

System prompt değişiyorsa `bsg-llm-claude-contract` skill'i ile gerekçe yazılır ve karakterizasyon testi alınır. Prompt değişimi yanıt JSON şemasını da etkileyebilir; her iki taraf birlikte güncellenir.

## JSON Çıktı Şeması

Claude'dan beklenen alanlar `AnalysisReport` ile bire bir eşleşir. Alan adları kontrat olarak korunur:

- `summary`
- `root_causes` (liste)
- `precursor_signals` (liste)
- `recovery_analysis`
- `recommendations` (liste)
- `watch_signals` (liste)

Alan eksikse parse hatası olarak değil, ilgili alan boş kalarak ele alınır. Yeni alan eklendiğinde mevcut tüketiciler kontrol edilir.

## Model Adı

- Model adı `core/llm_analyzer.py` içinde varsayılan olarak sabittir.
- Model güncelleme planı `bsg-llm-claude-contract` skill'i altında yürür; karakterizasyon testi olmadan model adı değiştirilmez.
- `claude-haiku-*`, `claude-sonnet-*`, `claude-opus-*` ailelerine geçişler maliyet ve yanıt kalitesi etkisini de plan dosyasına taşır.

## Hata Yolları

- **API key eksik**: `analyze_anomaly` çağrısı `AnalysisReport(error="...")` döner.
- **Yetkisiz / 401**: Hata mesajı kullanıcıya gösterilir; raw response saklanmaz.
- **Rate limit / 429**: Yeniden deneme mekanizması yoktur; kullanıcıya bilgilendirme verilir.
- **Network hatası**: Exception yakalanır ve `error` alanına yazılır.
- **Truncated JSON**: `_repair_truncated_json` ile kapanmamış parantezler tahminen kapatılır; başarısızsa raw response saklanır.
- **Format dışı yanıt**: JSON parse başarısızsa raw text korunur, alanlar boş kalır.

## Veri Sınırları

- Claude'a gönderilen context `AnomalyEvent.precursor_df` ve `recovery_df` üzerinden örneklenir; tüm dataset gönderilmez.
- Hassas bilgi (kampanya adı, kullanıcı adı, gerçek IP) gönderilmez; yer tutucu/anonim alan kullanılır.
- API key sidebar üzerinden alınır ve hiçbir log mesajına yazılmaz.

## Riskli Alanlar

- **Prompt drift**: Sürekli küçük prompt değişiklikleri yanıt kalitesini sessizce düşürebilir.
- **JSON şeması drift'i**: Claude yanıtı schema dışına çıktığında parse hatası alanı boş bırakır; downstream sekme sessizce yanlış görüntü verebilir.
- **Memory ve cost**: Her olay için ayrı çağrı yapmak hızla maliyet üretir; batch desteği plan dosyasında değerlendirilebilir.
- **Streaming yokluğu**: Yanıt tamamı bellekte tutulur; çok büyük analizlerde response süresi uzar.

## Doğrulama Stratejisi

- `tests/llm/test_llm_<konu>_contract.py` altında JSON parse, truncated repair, error path testleri.
- Mock `_call_claude` ile sabit yanıt fixture'ları kullanılır; gerçek API'ye gitmez.
- Prompt değişikliği geldiğinde önce karakterizasyon test'i alınır.

## Change Contract

- System prompt, JSON şeması ve `AnalysisReport` alan adları geçiş notu olmadan değişmez.
- Yeni alan opsiyonel olarak eklenir; mevcut UI tüketicileri etkilenmemelidir.
- Yeni LLM provider eklemek için `LLMProvider` interface'i öngörülebilir; ancak ayrı plan ile yürür.
- Model adı değişikliği plan dosyasında maliyet ve kalite etkisini de listeler.
