# FIX API Resource ObjectMapper Context Uygulama Planı

## Kapsam

Bu plan, API resource testlerinde Spring test context açılışını bozan eksik `ObjectMapper` bean contract'ını, Mockito mock bean'lerin legacy `@Autowired` zincirine sokulmasını ve mock lifecycle metodlarının çalıştırılmasını düzeltir. Üretim kodu ve endpoint davranışı değiştirilmez.

## Plan

- [x] Hata kökünü test raporları ve API test context'leri üzerinden incele.
- [x] `fix` analizini oluştur.
- [x] Hedefli API testlerini offline Maven ile kırmızı doğrula.
- [x] API test context'lerine en küçük `ObjectMapper` bean düzeltmesini uygula.
- [x] Mockito mock bean'lerin Spring field autowire zincirini test context'te kapat.
- [x] Resource test context'lerinde mock lifecycle init/destroy çağrılarını kapat.
- [x] Hedefli API testlerini offline Maven ile yeşil doğrula.
- [x] Execution plan, history ve Test Özeti kayıtlarını güncelle.

## Kapanış Kriteri

Bu plan, `CallResourceTest`, `CampaignsResourceTest` ve `UserResourceTest` offline Maven hedefli test koşusunda başarılı olmadan kapatılamaz.

Tam test suite'teki diğer mevcut fail'ler bu planın kapsamı dışındadır ve ayrı analiz/plan konusu yapılmalıdır.
