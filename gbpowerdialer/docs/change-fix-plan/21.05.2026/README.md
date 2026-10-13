# 21.05.2026 Change/Fix Planları

- `FIX-GLB90090645-GAMMA-CALLQUEUE-BACKPRESSURE.md`: AMI bağlantı kaybı (veya CallOriginator yavaşlama) sırasında Gamma `CallConsumer`'in sınırsız backlog biriktirmesini engelleyen queue threshold guard (`callQueue.size() >= cps` → drain atla). Restore anındaki originate burst'ünü kökten keser; rc78 ve rc84 abandon'un AMI loss kaynaklı kısmını sıfırlar.
