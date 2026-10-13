---
name: gbpd-call-flow-performance-guardrails
description: gbpowerdialer'da çağrı akışı hot path performansını koruma akışı. AMI/AGI originate, hangup/result event handling, Redis call event publish/subscribe, ACD agent seçimi, campaign producer/consumer/originator loop'ları, scheduler tick, runtime counter/state veya çağrı başına/event başına çalışan parser/mapper/validation değişirken kullan.
---

# gbpd-call-flow-performance-guardrails

## Amaç

Çağrı akışında latency ve throughput bütçesini korumak. Bu skill genel performans optimizasyonu için değil, üretim çağrı yolunda call başına, event başına veya scheduler tick başına çalışan kod değişikliklerinde maliyeti görünür yapmak için kullanılır.

## Hot Path Sınıflandırması

Değişiklikten önce frekansı yaz:

- Call başına
- AMI/Redis event başına
- Campaign tick veya producer/consumer cycle başına
- Agent seçimi veya queue pop başına
- Batch/admin/API akışı

Batch/admin/API akışı hot path değilse bunu açık yaz. Hot path ise aşağıdaki kontrol listesi zorunludur.

## Yasak Pattern'ler

- Call/event başına yeni DB query eklemek
- Event handler içinde blocking network veya Redis round-trip zinciri kurmak
- O(1) akışı liste/map scan ile O(n) yapmak
- Campaign, agent, user veya call koleksiyonunu her event'te tam taramak
- Date parsing, regex, JSON serialize/deserialize veya reflection işini her event'te tekrarlamak
- Global lock, geniş `synchronized` veya uzun süre tutulan shared lock eklemek
- Exception'ı normal kontrol akışı gibi kullanmak
- Üretim hot path'e verbose log veya büyük payload log'u koymak
- Counter düzeltmesi için her event'te pahalı reconciliation yapmak

## Zorunlu Maliyet Soruları

1. Bu değişiklik hot path'te mi?
2. En kötü durumda saniyede kaç kez çalışabilir?
3. Yeni IO, lock, allocation, serialization veya collection scan ekliyor mu?
4. Mevcut algoritmik maliyeti değiştiriyor mu?
5. Aynı veri önceden hesaplanabilir, cache'lenebilir veya event dışına alınabilir mi?
6. Correctness için gereken çözüm daha düşük maliyetli owner boundary'de uygulanabilir mi?
7. Performans etkisi test, ölçüm, log analizi veya açık maliyet notuyla doğrulandı mı?

## Tercih Edilen Çözüm Yönleri

- Hot path'te O(1) lookup, immutable snapshot veya önceden hazırlanmış map kullan.
- Pahalı iş gerekiyorsa event handler dışına, bounded async boundary'ye veya batch akışına taşı.
- Cache kullanılıyorsa invalidation owner'ını ve stale-data riskini yaz.
- Parse/normalize işlemini downstream consumer'larda tekrarlamak yerine boundary adapter'da bir kez yap.
- Counter veya state düzeltmesinde authoritative source'u netleştir; mümkünse türetilmiş değer kullan.
- Log gerekiyorsa sampling, rate limit veya debug flag ile üretim maliyetini sınırla.

## Root Cause ile İlişki

Performans gerekçesiyle root cause atlanmaz. `gbpd-no-workaround-root-cause-engineering` ile birlikte kullanıldığında sıra şudur:

1. Root cause ve owner boundary bulunur.
2. Contract-level çözüm tasarlanır.
3. Çözümün hot path maliyeti hesaplanır.
4. Pahalı parça varsa event dışına, cache'e veya batch akışına alınır.
5. Regression testi ve performans etkisi notu birlikte yazılır.

## Plan Gereksinimi

Hot path'e dokunan her `docs/execution-plan` veya `docs/change-fix-plan` dosyasında `Performans Etkisi` bölümü bulunur ve şu sonuçlardan biri açık yazılır:

- `Hot path değil`
- `Hot path, ek maliyet yok`
- `Hot path, ek maliyet var ve gerekçesi/azaltımı yazıldı`
- `Ölçüm veya doğrulama beklemede`

## Done Kriteri

- Hot path frekansı ve maliyet sınıfı yazılmış
- Yeni IO, scan, lock, allocation ve serialization maliyeti değerlendirilmiş
- Correctness çözümü performans için workaround'a çevrilmemiş
- Gerekirse performans azaltımı uygulanmış
- Regression test veya hedefli doğrulama yanında performans etkisi raporlanmış
