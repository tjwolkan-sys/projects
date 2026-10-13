# FIX AGI Agent Dial Same-User Retry Storm Change/Fix Planı

## Bağlam

Progressive/predictive outbound akışında müşteri (client) cevapladıktan sonra Asterisk
`dialWithAbandonTimeout.agi` script'ini tetikler. `DialWithAbandonTimeoutScript`, abandon
penceresi (genellikle 5-15 sn) içinde çağrıyı bir agent'a (user'a) bağlamaya çalışır.

YKB kampanyasında üretim Asterisk loglarında (bkz. `images/resim (3).png`, `images/resim (4).png`)
görülen davranış: aynı agent'a saniyede onlarca kez `Dial` denemesi yapılıyor ve her seferinde
`Caller hung up before dial` dönüyor.

```
09:57:32 Agent 81342 → dial → fail
09:57:33 Agent 81348 → dial → "Caller hung up before dial"
09:57:33 Agent 81348 → dial → "Caller hung up before dial"   ← TEKRAR
09:57:33 Agent 81348 → dial → "Caller hung up before dial"   ← TEKRAR
... 30+ kez tekrar (aynı saniye içinde)
```

Aynı agent'ın (81348) sürekli yeniden seçilmesinin yanında, ayrı bir belirti olarak bazı user'ların
~10 dakika boyunca AVAIL kalması raporlanmıştır. Call distribution (ACDEngine) tarafına ayrı bir iş
kapsamında müdahale edilecektir; bu plan **yalnızca AGI tarafındaki** (`DialWithAbandonTimeoutScript`)
same-user retry storm'unu ve busy-spin'i hedefler.

## İş Etiketi

fix

## Öncelik

hotfix

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/fastagi/DialWithAbandonTimeoutScript.java` (birincil)
- `gbpowerdialer/src/test/java/com/powerdialer/engine/asterisk/fastagi/DialWithAbandonTimeoutScriptTest.java`
- (Salt okuma referansı, bu planda DEĞİŞTİRİLMEZ) `ACDEngine`, `DefaultFindMatchingUserStrategy`,
  `UserLastCallDateComparator`, `Call`, `ManagerConnectionDecorator`

Not: Kullanıcı kararıyla kapsam AGI-only'dir. `ACDEngine.processACD` imzası ve call distribution
semantiği bu planda değiştirilmez; phantom-available user quarantine (10 dk avail kökü) ayrı
ACDEngine işine bırakılır.

## Mevcut Davranış

AGI bean'i singleton'dır (`applicationContext.xml`, `SimpleMappingStrategy` →
`dialWithAbandonTimeout`); tek instance tüm eşzamanlı AGI çağrılarını işler ve `lockedUsers`
alanı **global lock haritası** gibi davranır (farklı çağrıların aynı user'ı aynı anda dial
etmesini engeller).

`processDialCommand` (DialWithAbandonTimeoutScript.java:96-115):

```java
acdEngine.trySearchLessOccupiedUser(call);   // ilk user seçimi/değişimi
trySendDialCommand(call, channelIp);          // agent'a dial (sendCommand BLOCKING)
while (!call.isAnsweredByUser() && call.getTelephonyResultCode() == null) {
  if (isAbandonTimeoutExceed(call, startTime)) { processAbandonTimeout(call); break; }
  else if (doACDProcessing(call) && !isAbandonTimeoutExceed(...)) {
    trySendDialCommand(call, channelIp);       // yeni user'a tekrar dial
  }
}
```

`trySendDialCommand` (:123-149): user'ı kilitle → `DialCommand` gönder (bloklar) → cevaplanmadıysa
ve user hâlâ online+available ise `addToIdleUsers(call.getUser())` ile idle'a geri koy (:139-142)
→ kilidi bırak.

Sonuç olarak döngü; iki asenkron sinyale (`isAnsweredByUser`, `telephonyResultCode`) polling yapan,
dial sonucunu okumayan, "bu çağrı için kimi denediğini" hatırlamayan ve denemeler arası bekleme
içermeyen bir busy-loop'tur.

## Beklenen Davranış

1. Müşteri (caller) kapandıysa AGI bunu döngü içinde **doğrudan** tespit edip aynı saniyede tekrar
   tekrar dial etmeyi bırakmalı; mevcut hangup akışının nihai telephony result'ı yazmasına izin
   vermeli.
2. Bir agent'a cevapsız dial yapıldıysa, **aynı çağrı (aynı customer-answered penceresi) içinde**
   o agent'a kontrolsüz şekilde yeniden dial yapılmamalı; döngü taze (henüz denenmemiş) bir agent
   seçmeli.
3. Döngü hiçbir koşulda saniyede onlarca-yüzlerce iterasyon dönerek CPU ve log flood üretmemeli;
   deneme sayısı ve/veya iterasyon hızı sınırlı olmalı.
4. Mevcut başarılı akış (ilk dial'da agent cevaplıyor) ve mevcut abandon akışı (timeout aşımı,
   IVR'lı/IVR'sız) davranışı korunmalı.
5. Cross-call davranış korunmalı: başarısız bir agent başka bir çağrı için idle havuzuna geri
   dönebilmeli (bu plan yalnızca aynı çağrı içindeki tekrarı engeller).

## Kod Okuma Bulguları

- **(H1) Same-user retry:** `:139-142` başarısız dial sonrası user'ı koşulsuz idle'a geri ekler.
  `DefaultFindMatchingUserStrategy.getMostIdleUser` (:101-115) idle queue'nun head'ini alır; queue
  `PriorityBlockingQueue` + `UserLastCallDateComparator`. Hiç çağrı tamamlamamış user'ın
  `lastCallEndDate`'i null olduğundan comparator `-1` döner (`UserLastCallDateComparator.java:20-22`)
  ve user her zaman head'de kalır. `updateUserLastCallEndDate` yalnız cevaplanınca çağrılır (:138).
  Böylece aynı user (81348) seç → fail → idle'a geri → tekrar seç döngüsüne girer. Döngünün
  per-call "denenen user" hafızası yoktur.
- **(H2) Caller hangup tespiti yok:** Döngü koşulu yalnız `isAnsweredByUser()` ve
  `telephonyResultCode != null`'a bakar. Bu iki alan AMI event thread'inde asenkron set edilir
  (`ManagerConnectionDecorator` answered ≈ :540, TRC ≈ :681); AGI thread'ine göre lag'lidir.
  `sendCommand(DialCommand)` dönüşü (`AgiReply` / `${DIALSTATUS}`) `:136`'da atılır; script "Caller
  hung up before dial" sonucunu okumadığı için müşteri kapandığını anlayıp döngüden çıkamaz.
- **(H3) Busy-loop:** Döngüde bekleme veya deneme üst sınırı yoktur. Dial anında fail dönünce
  abandon penceresi boyunca tam hızla döner. Ayrıca `doACDProcessing` hiç user bulamayınca
  `call.setUser(null)` olur (`ACDEngine:313`), `false` döner ve döngü gövdesi hiçbir şey yapmadan
  yine tam hızla döner (dial atmadan spin).
- **(H4, ikincil) answered race:** `Call.setAnsweredByUser(String)` (:677-679) yalnız dial edilen
  user'ın username'i eşleşirse `true` yapar; döngü user'ı değiştirince geç gelen answer event'i
  eşleşmeyebilir. Bu planın kapsamında doğrudan düzeltilmez, riskler bölümünde izlenir.

## Root Cause / Değişiklik Gerekçesi

AGI döngüsü, asenkron set edilen iki sinyale polling yapan; dial sonucunu okumayan; per-call
hafızasız; backoff'suz bir busy-loop'tur. Başarısız dial'de user idle'a geri konunca comparator onu
(lastCallEndDate=null) tekrar head'e taşır ve aynı user abandon penceresi dolana dek sınırsız
denenir. Müşteri kapansa bile döngü bu durumu okumadığından "Caller hung up before dial" storm'u
oluşur.

## Çözüm Yaklaşımı (AGI-only, Y1 + Y2 + Y3)

Tüm değişiklik `DialWithAbandonTimeoutScript` içinde, JVM tarafında, ACD distribution semantiğine
dokunmadan yapılır.

**Y1 — Per-call "denenen user" exclusion seti:**
- `service()`/`processDialCommand` invocation'ına LOCAL bir `Set<Long> triedUsers` eklenir (paylaşılan
  instance alanı `lockedUsers` DEĞİL; her AGI çağrısı için yeni set).
- Bir agent'a cevapsız dial tamamlandığında `triedUsers.add(user.getId())`.
- `doACDProcessing` ile seçilen user `triedUsers` içindeyse o agent'a tekrar dial edilmez. ACD
  seçim sırasında user'ı idle'dan çıkardığı için, tried user yeniden seçilirse script onu idle'a
  geri verir (`addToIdleUsers`) ve "taze aday yok" kabul eder. Böylece cross-call kullanım korunur,
  ama aynı çağrı içinde aynı agent'a dial storm'u olmaz.

**Y2 — Dial sonucunu oku, caller-hangup'ta erken çık:**
- `trySendDialCommand` dial sonrası `${DIALSTATUS}` okur (`getFullVariable`) ve/veya `sendCommand`
  çağrısı çevresinde `AgiHangupException` yakalanır.
- Müşteri kanalının gittiğini gösteren durum (AgiHangupException ve/veya caller-side DIALSTATUS,
  örn. CANCEL) tespit edilirse döngü **anında** kırılır. Yeni TRC uydurulmaz; mevcut hangup akışının
  (`ManagerConnectionDecorator`) nihai telephony result'ı (ör. mevcut `CUSTOMER_HANGUP_BEFORE_AGENT`
  / abandoned) yazmasına izin verilir.
- DIALSTATUS / AgiHangupException'ın bu senaryodaki gerçek değeri karakterizasyon testiyle
  kilitlenecek ve YKB loglarıyla teyit edilecektir (gerçek Asterisk davranışı kodda doğrulanamıyor;
  bu açıkça izlenir).

**Y3 — Bounded attempts + backoff:**
- Taze aday bulunamadığında (hiç idle user yok veya yalnız tried user'lar kaldı) döngü busy-spin
  yapmaz; **~100 ms aralıkla** abandon penceresine kadar yoklar (SR2 kararı). Böylece abandon
  penceresi içinde boşalan bir agent yakalanabilir, CPU/log flood ise kesilir.
- Çağrı başına dial denemesi doğal olarak distinct available agent sayısıyla sınırlanır (SR1: aynı
  çağrıda tekrar yok). Tüm taze agent'lar denendiyse döngü 100 ms yoklamayla abandon timeout'a kadar
  bekler ve süre dolunca mevcut `processAbandonTimeout` akışına düşer.

Mevcut `processAbandonTimeout` (IVR'lı/IVR'sız) ve ilk-dial başarılı yolu olduğu gibi korunur.

## Riskler

- **Erken abandon:** Y1 exclusion'ı fazla katı olursa, transient (anlık meşgul) bir agent tek
  başarısızlıkta dışlanıp abandon penceresinde yeniden denenmeyebilir. Politika Açık Sorular'da
  netleşecek (varsayılan: storm'u kesmek için aynı çağrıda tekrar yok).
- **Caller-hangup yanlış tespiti:** DIALSTATUS/AgiHangupException eşlemesi yanlışsa, gerçekten
  ulaşılabilir bir agent'a yapılan dial yanlışlıkla "caller gone" sayılıp erken çıkış yapılabilir.
  Karakterizasyon testi + log teyidi ile sınırlandırılır.
- **Bekleme süresi:** Y3 backoff'u fazla uzun olursa abandon penceresi içinde gerçekten boşalan bir
  agent'a bağlanma şansı azalır; fazla kısa olursa spin tam kesilmez. Değer Açık Sorular'da.
- **Cross-call etkileşim:** Başarısız agent'ın idle'a geri dönüşü korunduğu için, eşzamanlı başka
  bir çağrı aynı agent'ı seçebilir (farklı müşteri için doğru davranış); bu plan bunu değiştirmez.
- **(H4) answered race** bu planda kapatılmaz; user switch'i azaldığı için pratikte etkisi düşer,
  ancak tamamen giderilmez.
- **Lock etkileşimi:** `tryLockUser` başarısız olursa (başka çağrı aynı user'ı kilitlemiş) mevcut
  kodda user idle'a geri konmadan kaybolabilir; yeni akış bu user'ı da `triedUsers`/taze-aday
  mantığıyla tutarlı ele almalı (regression testiyle korunacak).

## TDD Stratejisi

Önce mevcut davranışı koruyan ve yeni davranışı kanıtlayan testler yazılır (mevcut
`DialWithAbandonTimeoutScriptTest` genişletilir):

Korunan (regression):
- İlk dial'da agent cevaplıyorsa tek `sendCommand` ve idle'a geri ekleme yapılmaz (mevcut
  `testSuccessfulService`).
- İlk dial fail, ikinci dial farklı user ile cevaplanıyorsa iki `sendCommand` (mevcut
  `testSuccessfulSecondDial`, gerekirse yeni akışa uyarlanır).
- Abandon timeout aşımı IVR'sız → tek dial + `hangup` (mevcut `testAbandonTimeoutExceedWithoutIvr`).
- Abandon timeout aşımı IVR'lı → IVR dial (mevcut `testAbandonTimeoutExceedWithIvr`).

Yeni (kırmızı → yeşil):
- Aynı user iki kez seçilse bile aynı çağrı içinde o user'a **ikinci dial atılmaz** (same-user retry
  reddi).
- Caller hangup sinyali (AgiHangupException ve/veya caller-side DIALSTATUS) geldiğinde döngü
  **erken çıkar**, ek dial atılmaz ve yeni TRC üretilmez.
- Taze aday yokken döngü sınırlı sayıda iterasyon yapar / busy-spin etmez (deneme/iterasyon üst
  sınırı veya bekleme davranışı kanıtlanır).

## Açık Sorular

- [x] **SR1 (retry politikası):** Aynı çağrıda tekrar yok. Bir agent'a tek cevapsız dial sonrası o
  agent o customer-answered penceresi için tamamen dışlanır; başka çağrılar için idle havuzunda
  kalır.
- [x] **SR2 (taze aday yokken davranış):** Doğrudan abandon'a düşülmez; ~100 ms aralıkla abandon
  timeout'a kadar yoklanır (boşalan agent'ı yakalamak için).
- [x] **SR3 (caller-hangup sinyali):** Savunmacı tespit ile ilerlenir — hem `${DIALSTATUS}` okunur
  hem `AgiHangupException` yakalanır; gerçek davranış karakterizasyon testiyle kilitlenir.

## Netleşen Kararlar

- SR1: Per-call `triedUsers` seti agent'ı tek cevapsız dial sonrası o çağrı için dışlar. Cross-call
  davranış korunur (agent başka çağrılar için idle'a geri döner).
- SR2: Taze aday tükendiğinde döngü ~100 ms `wait`/`sleep` aralığıyla abandon penceresine kadar
  yoklar; süre dolunca mevcut `processAbandonTimeout` akışı çalışır. Busy-spin kaldırılır.
- SR3: Caller-hangup tespiti savunmacı yapılır (`${DIALSTATUS}` + `AgiHangupException`); gerçek
  Asterisk değeri karakterizasyon testiyle ve gerekirse YKB log teyidiyle doğrulanır. Erken çıkışta
  yeni TRC üretilmez; mevcut hangup akışı nihai sonucu yazar.

## Execution Plan

- [x] Onaylanan planın tüm adımları bu dosyaya işlendi (Açık Sorular cevapları dahil).
- [x] Mevcut davranışı kilitleyen karakterizasyon/regression testleri yazıldı/korundu.
- [x] Beklenen davranışı kanıtlayan testler yazıldı (same-user retry reddi, caller-hangup
  erken çıkış, busy-spin yok / bounded poll).
- [x] En küçük üretim kodu değişikliği yapıldı (per-call `triedUsers`, dial sonucu okuma/erken
  çıkış, bounded loop/backoff).
- [x] Hedefli testler yeşile çevrildi.
- [x] Gerekliyse küçük refactor yapıldı ve testler tekrar çalıştırıldı.
- [x] İlgili mimari doküman (`docs/architecture/ASTERISK-AMI-AGI.md`) AGI sınırı bölümünde current
  behavior olarak güncellendi.
- [x] History append-only kaydı eklendi.

## Performans Etkisi

- **Hot path durumu:** Evet — her cevaplanan çağrı bu AGI script'inden geçer.
- **Call/event/tick başına ek maliyet:** Invocation başına bir `Set<Long>` (küçük). Her dial
  denemesi sonrası bir `${DIALSTATUS}` okuma (1 ek AGI round-trip) eklenir; ancak deneme sayısı
  artık bounded olduğu için toplam maliyet düşer.
- **IO/scan/lock/serialization/log etkisi:** Yeni DB query / blocking network / geniş koleksiyon
  taraması eklenmez. Mevcut `processACD` scan'i zaten vardı; deneme sayısı azaldığı için scan sayısı
  da azalır. Log hacmi (saniyede onlarca "Caller hung up before dial") büyük ölçüde düşer.
- **Azaltım/ölçüm notu:** Net etki CPU ve log flood'unda **azalma**dır. Eklenen bekleme (varsa) AGI
  thread'ini bloklar ama bu thread zaten o çağrıya tahsislidir; sistem genelinde CPU'yu serbest
  bırakır.

## Doğrulama

Doğrulama öncesi `.codex/TOOL-PATHS.local.properties` okunacak; Maven offline (`-o`) çalıştırılacak.

```powershell
mvn -o -Dtest=DialWithAbandonTimeoutScriptTest test
```

Gerekirse ilgili AMI/AGI regression bundle:

```powershell
mvn -o "-Dtest=DialWithAbandonTimeoutScriptTest,tests.ami.*RegressionTest" test
```

Test sınıf/komut detayları uygulama sırasında local tool path'e göre netleştirilebilir.

## Kapanış Kriteri

- [x] Açık sorular (SR1-SR3) cevaplandı ve plana işlendi.
- [x] Aynı çağrı içinde same-user retry storm'u oluşmadığı testle kanıtlandı
  (`testSameFailingAgentIsDialedOnlyOnce` → tek dial).
- [x] Caller hangup'ta döngü erken çıkıyor ve yeni TRC üretilmiyor; mevcut hangup akışı sonucu
  yazıyor (`testCallerHangupViaDialStatusStopsDialing`, `testCallerHangupViaHangupExceptionStopsDialing`).
- [x] Busy-spin giderildi (`testNoFreshAgentPollsBoundedThenAbandons` → `processACD` atMost 20).
- [x] Mevcut başarılı/abandon akış davranışları regression testleriyle korundu (7/7 AGI + 49/49 AMI
  bundle).
- [x] Hedefli Maven testleri offline yeşil oldu.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Tamamlandı. AGI-only kapsam (Y1+Y2+Y3) TDD ile uygulandı; `DialWithAbandonTimeoutScript` çağrı-bazlı
`triedUsers` exclusion, savunmacı caller-gone tespiti ve 100 ms bounded poll ile güncellendi.
Hedefli offline Maven testleri başarılı: `DialWithAbandonTimeoutScriptTest` 7/7;
`DialWithAbandonTimeoutScriptTest,tests.ami.*RegressionTest` 49/49. ACD distribution semantiği ve
phantom-available quarantine (10 dk avail kökü) kapsam dışı bırakıldı; ayrı ACDEngine işine kalır.
Branch: `fix/320176-agi-agent-dial-same-user-retry-storm`.
