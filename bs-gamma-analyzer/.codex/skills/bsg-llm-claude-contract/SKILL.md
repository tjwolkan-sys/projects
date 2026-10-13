---
name: bsg-llm-claude-contract
description: bs-gamma-analyzer'da `core/llm_analyzer.py` üzerinden Claude entegrasyonunu değiştirirken system prompt, JSON çıktı şeması, `AnalysisReport` alan adları, model adı, hata yolları ve truncated JSON onarım davranışını korumak için.
---

# bsg-llm-claude-contract

## Amaç

Claude (Anthropic) entegrasyonu prompt, JSON şeması ve hata yolu kontratıyla çalışır. Streamlit AI Analiz sekmesi `AnalysisReport` alanlarına bakarak render yapar. Contract drift sessizce yanlış görüntü üretir; bu skill prompt/şema/hata yolu değişikliklerinde gerekli korumaları sağlar.

## Birlikte Kullanılacak Skill'ler

- `bsg-tdd-workflow`: Mock yanıt üzerinden parse ve hata yolu testleri.
- `bsg-characterization-tests`: Mevcut prompt/şema davranışının kilidi.
- `bsg-anomaly-detector-contract`: Context build sırasında detector çıktısının format'ı.

## Önce Oku

- `core/llm_analyzer.py`: `AnalysisReport`, `build_context`, `_build_analysis_prompt`, `_call_claude`, `_parse_response`, `_repair_truncated_json`, `analyze_anomaly`.
- `docs/architecture/LLM-CLAUDE-INTEGRATION.md`: Mevcut mimari özet.
- AI Analiz sekmesi render kodu (`app.py`).

## Kritik Kod Yüzeyi

- System prompt metni: Gamma algoritması özeti, JSON çıktı zorunluluğu, Türkçe yanıt isteği, halüsinasyon yasakları.
- `AnalysisReport` alanları: `summary`, `root_causes`, `precursor_signals`, `recovery_analysis`, `recommendations`, `watch_signals`, `raw_response`, `error`.
- Model adı: `core/llm_analyzer.py` içinde sabit varsayılan; sürüm değişikliği plan dosyası ile yürür.
- JSON onarım: `_repair_truncated_json` heuristik tamamlaması.
- Hata yolları: API key eksik, 401, 429, network, parse, format dışı yanıt.

## Contract Kontrolü

Değişiklik öncesi sorulara cevap ver:

- System prompt değişiyor mu? Hangi yan etkileri olur?
- JSON şeması alanı eklenip/silinip/yeniden adlandırılıyor mu?
- `AnalysisReport` alan default'u (örn. `error` boş string mi None mı) değişiyor mu?
- Model adı yükseltiliyor mu? Maliyet/kalite etkisi nedir?
- Truncated JSON heuristic değişiyor mu? Yeni edge case fixture'ı var mı?
- Streaming desteği ekleniyor mu? Mevcut tüm tüketici ağ akışında çalışmaya devam ediyor mu?

## Test Stratejisi

- Mock yanıt fixture'ları: `tests/llm/fixtures/<senaryo>.json`.
- JSON parse contract testi: `test_llm_response_parse_contract.py`.
- Truncated JSON onarımı: `test_llm_truncated_repair_characterization.py`.
- Hata yolu testleri: 401, 429, network exception, format dışı yanıt.
- Context builder testi: `AnomalyEvent` üzerinden context payload'unun beklenen alanlar.
- Gerçek API çağrısı testte yapılmaz; `_call_claude` mock'lanır.

## Arama Kalıpları

- Model adı: `model=`, `claude-`.
- Prompt: `system_prompt`, `user_prompt`, `_build_analysis_prompt`.
- Şema alanı: `summary`, `root_causes`, `precursor_signals`, `recovery_analysis`, `recommendations`, `watch_signals`.
- Hata yolu: `except`, `error=`, `raise`.

## Raporlama

- Hangi alan/şema/prompt değişikliği yapıldı.
- Mevcut AI Analiz sekmesi etkilendi mi?
- Mock fixture'lar güncel.
- Karakterizasyon testleri yeşil.
- API maliyeti/kalitesi etkisi (model değişikliği varsa).
