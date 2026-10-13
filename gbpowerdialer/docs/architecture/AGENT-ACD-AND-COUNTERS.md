# Agent, ACD ve Counter'lar

Agent state ve campaign counter'ları, yatay ölçek önündeki ana engellerden biridir. Bu alanda aynı kavram birden fazla yerde tutulur: Redis, ACD local queue, `Campaign` runtime counter'ları, `CallUpToDateKeeper` ve ekran/API response'ları.

## ACD Bileşenleri

- `ACDEngine`: Group bazlı idle ve system active user queue'larını yönetir.
- `UserHolder`: Kullanıcı state'ini uygulama içinde tutan yardımcı katmandır.
- `UserLoginLogoutHandler`: Login/logout davranışını işler.
- `CallUpToDateKeeper`: Kullanıcıya bağlı güncel çağrı listesini tutar.
- `DefaultFindMatchingUserStrategy`: Uygun agent bulma stratejisini uygular.
- `UserSubscriber`: Redis user current status ve session mesajlarını ACD tarafına taşır.

## Campaign Counter'ları

`Campaign` entity'sinde runtime için kullanılan birçok geçici alan vardır:

- `agentsSize`
- `availAgentsSize`
- `activeCalls`
- `liveCalls`
- `uniqueLiveCalls`
- `ringingCalls`
- `ringingCbCalls`
- `ringingRetryCalls`
- `ringingFreshCalls`
- `inCalls`
- `originatedCalls`
- `callsInProgress`
- `callsByUserResultCode`
- `callsByTelephonyResultCode`

Bu alanlar kalıcı entity üzerinde dursa da runtime state'tir. Çoklu node senaryosunda otomatik olarak ortak gerçek kaynağa dönüşmez.

## Yardımcı Tutarlılık Sınıfları

- `AgentSizeConsistencyChecker`: Çalışan kampanyalarda agent sayısı tutarlılığını kontrol etmeye çalışır.
- `StatisticSynchronizer`: Çağrı kayıtlarından counter'ları düzeltmeye yönelik senkronizasyon yapar.
- `ActiveCallsCounter`: Aktif çağrı sayısını hesaplamada kullanılır.

Bu sınıfların varlığı, counter drift'inin bilinen bir risk olduğunu gösterir. Yeni çözümde bu sınıflar semptomu gizlemek yerine authoritative state design'ına giden ara katman olarak ele alınmalıdır.

## Drift Kaynakları

- Agent status Redis'ten gelirken ACD queue başka bir anda güncellenebilir.
- Çağrı AMI event'i ve user session mesajı farklı sırada gelebilir.
- Increment/decrement çağrıları kaçarsa counter kalıcı olarak sapabilir.
- Runtime context reload/reset sırasında eski çağrı veya agent state'i temizlenmeyebilir.
- Inbound ve outbound uygulamalar aynı agent için farklı state penceresine sahip olabilir.

## İlk Audit Matrisi

Counter veya agent davranışı değişmeden önce her alan için şu tablo çıkarılmalıdır:

- Alan adı.
- Writer noktaları.
- Reader noktaları.
- Tetikleyen event veya kullanıcı aksiyonu.
- Kaynak sistem.
- Hata durumunda düzeltme mekanizması.
- Ekran/API etkisi.

## Hedef Davranış İlkesi

- Counter'lar mümkün olduğunda türetilmiş değer olmalıdır.
- Authoritative state tek bir yerde sahiplenilmelidir.
- Redis pub/sub sadece event transport kabul edilmeli; kalıcı doğruluk için ayrıca state store veya event log tasarlanmalıdır.
- Aynı agent veya çağrı için idempotent update kuralı oluşturulmalıdır.
- Node kimliği ve ownership bilgisi açıkça tutulmalıdır.

## Change Contract

- Bu alandaki her işte `gbpd-runtime-state-audit` kullanılmalıdır.
- Counter düzeltmesi yapılacaksa önce regression veya karakterizasyon testi yazılır.
- Yeni increment/decrement eklemek son seçenek kabul edilir.
- Inbound/outbound farkı incelenirken Redis, ACD ve Campaign okuyucuları birlikte karşılaştırılır.
