---
name: gbpd-safe-change
description: gbpowerdialer legacy kodunda üretim güvenli değişiklik yapma akışı. Çalışmayı yürüten ajan gbpowerdialer'da hata düzeltmesi veya özellik geliştirirken canlı outbound dial, campaign lifecycle, Asterisk, Redis veya operatör davranışını kırmamalıysa kullan.
---

# gbpd-safe-change

## Akış

1. İstenen değişikliğin en küçük davranış yüzeyini belirle.
2. Kod yazmadan önce mevcut akışı oku: caller'lar, event writer'lar, async worker'lar, persistence ve Redis/AMI yan etkileri.
3. Hedef alanın riskli runtime state'e dokunup dokunmadığını kontrol et: `Campaign` counter'ları, `DialerFacade.campaignContexts`, `ACDEngine` queue'ları, `ManagerConnectionDecorator.calls`, `CallUpToDateKeeper`.
4. Davranış kırılgan veya belirsizse önce karakterizasyon testi yaz.
5. Değişikliği küçük tut. Cleanup, biçimlendirme ve davranış değişikliğini aynı patch içinde karıştırma.
6. En dar anlamlı test komutuyla doğrula. Maven yoksa bunu açıkça belirt.
7. Sonuçta üretim riski, değişen davranış ve kalan doğrulama boşluklarını yaz.

## Kurallar

- Redis channel adları, AMI variable/event adları, result code mapping'leri ve profil property adları migration planı olmadan değişmez.
- Yeni static mutable state ekleme.
- Legacy kodda yeni kütüphane ekleme; önce mevcut bağımlılıkları, JDK'yı ve küçük local adapter/pure function boundary'lerini kullan.
- Yeni kütüphane veya yeni Maven artifact'i gerçekten zorunluysa kod yazmadan önce geliştiriciye gerekçesiyle bildir; geliştirici VPN'de Maven cache/Nexus güncellemesini yaptıktan sonra devam et.
- Increment/decrement counter mantığını başka bir truth source ile değiştiriyorsan migration ve reconciliation yolunu açık yaz.
- Geniş yeniden yazım yerine adapter, test edilebilir boundary ve küçük davranış değişikliği tercih et.
- Görev özellikle Java 17 profiliyle ilgili değilse Java 8 uyumluluğunu koru.

## Yararlı Arama Kalıpları

- `rg -n "increment|decrement|set.*Size|activeCalls|ringingCalls|agentsSize" gbpowerdialer/src/main/java`
- `rg -n "REDIS_CHANNEL|publishMessage|subscribe|MessageListener" gbpowerdialer/src/main/java`
- `rg -n "originateCall|onManagerEvent|DialerHangup|VarSetEvent|ORIGINID" gbpowerdialer/src/main/java`
