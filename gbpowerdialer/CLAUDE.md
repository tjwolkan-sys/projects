# Claude Agent Rehberi

Bu dosya Claude tabanlı ajanlar için kısa köprü talimatıdır. gbpowerdialer için birincil ve ayrıntılı kural kaynağı `AGENTS.md` dosyasıdır.

## Öncelik Sırası

1. Önce repo kökündeki `AGENTS.md` dosyasını oku ve ana çalışma sözleşmesi olarak uygula.
2. Bu dosya ile `AGENTS.md` arasında fark varsa `AGENTS.md` esas alınır.
3. Görev bir proje skill'ine uyuyorsa `.codex/skills/<skill-adı>/SKILL.md` dosyasını oku ve ilgili akışı uygula.
4. Birden fazla skill uyuyorsa en dar gerekli skill setini kullan; gereksiz skill yükleme.
5. `AGENTS.md` veya skill dosyalarında `Codex` geçen operasyonel kurallar Claude için de geçerlidir; `Codex` ifadesini `çalışmayı yürüten ajan` olarak yorumla.

## Skill Kullanımı

`.codex/skills` altındaki klasörler bu repo için proje özelinde çalışma akışlarıdır. Claude, görev türüne göre ilgili skill'i dikkate almalıdır.

Örnek eşleşmeler:

- Kod değişikliği, hata düzeltmesi veya özellik geliştirme: `gbpd-tdd-workflow`, `gbpd-safe-change`, `gbpd-no-workaround-root-cause-engineering`
- Offline Maven, Nexus/VPN, tool path veya build/test teşhisi: `gbpd-offline-build-toolchain`
- Kampanya start/pause/stop/reload/reset, scheduler veya çağrı runtime hot path değişikliği: `gbpd-campaign-lifecycle-flow`, `gbpd-call-flow-performance-guardrails`
- Spring MVC/API, DTO, Hibernate, DAO/service veya dinamik SQL değişikliği: `gbpd-api-persistence-contracts`
- Asterisk AMI/AGI originate, event veya hangup/result akışı: `gbpd-ami-event-flow`, `gbpd-call-flow-performance-guardrails`
- Redis channel, payload veya subscriber/publisher değişikliği: `gbpd-redis-contracts`, `gbpd-call-flow-performance-guardrails`
- AngularJS route, controller, service, template veya WRO değişikliği: `gbpd-angularjs-legacy-ui`, `gbpd-angularjs-tdd`
- Commit hazırlığı: `gbpd-commit-discipline`
- Anlamlı iş kaydı: `gbpd-agent-history`

## Temel Kurallar

- Bu repo canlı outbound dialer gibi ele alınır; çağrı akışı, kampanya yaşam döngüsü, AMI/AGI, Redis ve operatör ekranları korunmadan değişiklik yapılmaz.
- Kod değişikliği gerekiyorsa TDD akışı uygulanır; belirsiz legacy davranış önce karakterizasyon testiyle kilitlenir.
- Maven komutları `.codex/TOOL-PATHS.local.properties` dikkate alınarak offline çalıştırılır; Claude dependency indirmeyi denemez.
- Yeni kütüphane veya artifact zorunluysa önce geliştiriciye bildirilir; geliştirici VPN'de cache/Nexus güncellemesi yaptıktan sonra devam edilir.
- `AGENTS.md`, `docs/**/*.md` ve `.codex/skills/**/SKILL.md` dosyaları Türkçe tutulur.
- Gerçek IP, şifre, token, müşteri bilgisi veya credential yeni dosyalara yazılmaz.
- Çalışma ağacı kirli olabilir; ilgisiz değişiklikler geri alınmaz.

## Çıktı Beklentisi

Çalışma sonunda değişen dosyaları ve kısa Test Özeti'ni raporla. Test Özeti, çalıştırılan test komutlarını ve durumlarını içermelidir; test çalıştırılmadıysa nedeni açık yazılmalıdır. Anlamlı dokümantasyon, skill veya kod çalışması yapıldıysa ilgili günlük history dosyasına `gbpd-agent-history` akışıyla append-only kayıt ekle; mevcut history içeriğini koru, eski kayıtları düzenleme, silme veya üzerine yazma. Yeni gün açıldıysa `docs/history/README.md` indeksine link ekle.
