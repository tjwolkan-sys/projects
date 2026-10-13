---
name: gbpd-ami-event-flow
description: gbpowerdialer Asterisk AMI/AGI originate ve event handling değişikliklerinde güvenli çalışma akışı. ManagerConnectionComposition, ManagerConnectionDecorator, ManagerActionFactory, CallOriginator, AMI event class'ları, AGI script'leri, preview/progressive/predictive originate veya hangup/result handling değişirken kullan.
---

# gbpd-ami-event-flow

## Akış

1. Değişiklikten önce call lifecycle'ı haritala: queue -> originate -> AMI/Redis action -> VarSet/answer/agent/hangup/originate response -> DB/CDR/result.
2. `ORIGINID` ve parent id korelasyonunu koru.
3. Preview ve non-preview path'leri birlikte kontrol et; preview Redis üzerinden, non-preview AMI `sendAction` üzerinden ilerler.
4. Counter transition'larını takip et: ringing, active, in-call, calls-in-progress, originated cache.
5. Dokunulan branch için retry, result code, DNC, callback, LSC ve predictive yan etkilerini kontrol et.
6. Originate, event routing veya hangup/result hot path'i etkileniyorsa `gbpd-call-flow-performance-guardrails` ile call/event başına maliyeti yaz.
7. Mümkünse generated action, event routing ve fallback cleanup için karakterizasyon testi ekle.

## Kritik Dosyalar

- `engine/dialer/CallOriginator.java`
- `engine/asterisk/ami/ManagerConnectionComposition.java`
- `engine/asterisk/ami/ManagerConnectionDecorator.java`
- `engine/asterisk/actions/ManagerActionFactory.java`
- `engine/asterisk/fastagi/DialWithAbandonTimeoutScript.java`
- `engine/asterisk/events/*`

## Kırmaman Gerekenler

- AMI event class registration.
- `ORIGINID` call id mapping.
- Preview timeout cleanup.
- CDR save ve campaign result update sırası.
- Progressive reserved campaign'lerde user pause/reserve davranışı.
- AMI event handler içinde blocking IO, tam koleksiyon taraması ve verbose payload log'u.
