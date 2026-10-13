# FEAT AngularJS Kısa ve Orta Vade Test Standardı

## Bağlam

AngularJS 1.x tarafında daha önce yalnızca Java/JUnit tabanlı route, WRO ve template contract testleri planlanmıştı. Bu yaklaşım kısa vadede yararlı olsa da gerçek AngularJS service, factory, controller ve directive davranışını çalıştırmaz. Üretimdeki AngularJS ekranlar legacy kabul edildiği için Next.js migration ayrı bir konu olarak kalmalı; bugünkü ihtiyaç, mevcut AngularJS koduna dokunurken kısa ve orta vadede güvenilir test standardı kurmaktır.

## İş Etiketi

`feat`

## Mevcut Davranış

- Java backend için Maven/JUnit4 testleri vardır.
- AngularJS kaynakları `src/main/webapp/js` altında, WRO bundle sırası ise `src/main/webapp/WEB-INF/wro.xml` içinde tutulur.
- AngularJS için repo içinde Karma, Jasmine veya angular-mocks tabanlı gerçek JavaScript test koşusu yoktur.
- Önceki AngularJS testi route ve WRO contract'ını Java/JUnit ile metin tabanlı kilitlemiştir.

## Beklenen Davranış

- Kısa vadede route, WRO, template, localization ve backend response shape contract'ları Java/JUnit contract testleriyle korunur.
- Orta vadede saf AngularJS service/factory/controller davranışı Karma, Jasmine ve angular-mocks ile gerçek Angular injector üzerinden test edilir.
- Yeni AngularJS değişikliklerinde hangi test katmanının kullanılacağı `AGENTS.md`, skill ve mimari dokümanda net görünür.
- Next.js migration testleri bu kapsamın dışında kalır; Next.js geliştirme başladığında ayrı standardı açılır.

## Etkilenen Alanlar

- `gbpowerdialer/package.json`
- `gbpowerdialer/karma.conf.js`
- `gbpowerdialer/src/test/angular`
- `.codex/skills/gbpd-angularjs-tdd/SKILL.md`
- `.codex/skills/gbpd-tdd-workflow/SKILL.md`
- `AGENTS.md`
- `docs/architecture/TESTING-AND-SAFE-CHANGE.md`
- `docs/architecture/API-AND-UI.md`
- `docs/README.md`

## TDD Stratejisi

İlk gerçek AngularJS test alanı olarak `alertService` seçildi. Bu servis üretim endpoint'i, DOM veya WRO çalıştırması gerektirmeden Angular injector ile test edilebilir. İlk testler transient/permanent alert state ayrımını ve kapatma davranışını kilitler.

## Riskler

- Karma koşusu Chrome gerektirir; geliştirici makinesinde Chrome yoksa test komutu çalışmayabilir.
- `npm.ps1` PowerShell execution policy nedeniyle engellenebilir; Windows ortamında `npm.cmd` kullanılmalıdır.
- AngularJS 1.4.4 ile uyum için `angular-mocks` sürümü 1.4.4 olarak sabitlenmelidir.
- Karma testleri WRO bundle üretmez; WRO sırası için Java/JUnit contract testi korunmalıdır.

## Kabul Kriterleri

- `package.json` ve `karma.conf.js` repo içinde eklenir.
- İlk gerçek AngularJS spec dosyası `src/test/angular/services` altında bulunur.
- `npm.cmd run test:angular` komutu denenir ve sonucu raporlanır.
- Skill, agent ve mimari dokümanlarda kısa/orta vade test ayrımı yer alır.
