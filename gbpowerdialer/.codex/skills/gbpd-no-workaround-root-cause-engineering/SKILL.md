---
name: gbpd-no-workaround-root-cause-engineering
description: gbpowerdialer'da bug fix, runtime hata, data/contract drift, counter uyuşmazlığı veya UI/backend validation ayrışmasında workaround yerine root-cause-first ve contract seviyesinde çözüm uygulama akışı. AMI/AGI, Redis, campaign/runtime state, API/DTO/persistence veya AngularJS form akışında case-specific patch, silent fallback ya da tekil hack riski varsa kullan.
---

# gbpd-no-workaround-root-cause-engineering

## Amaç

Tekil semptomu yamamak yerine semptomu üreten sistemik kusuru kapatmak. Aynı bug class'ının başka varyantlarda tekrar etmesini engellemek.

## Ne Zaman Kullanılır

- Bug fix istendiğinde
- Runtime hata veya state drift görüldüğünde
- Null, boş payload, schema, mapper veya DTO contract problemi olduğunda
- AngularJS frontend ile backend authoritative validation ayrıştığında
- Redis message, AMI/AGI event veya campaign lifecycle akışında tekil case hack riski olduğunda

## Yasak Pattern'ler

- Tek bir input varyantı için lokal `if/else` ile semptomu susturmak
- `if (x == null) return`, `if (!x) default`, `catch (Exception e) {}` benzeri sessiz fallback
- Hatanın kaynağını düzeltmeden yalnız controller, yalnız JSP/AngularJS veya yalnız tool/prompt seviyesinde patch atmak
- Counter drift'i gerçek owner'ı bulmadan ek increment/decrement ile kozmetik kapatmak
- Tek campaign, tek agent, tek call state'i veya tek payload shape'i için özel-case yazmak
- Testi yeşil yapmak için geçici patch, skip veya daraltılmış assertion eklemek

## Root Cause Akışı

1. Semptomu yaz: hata mesajı, bozuk state, yanlış sayaç veya kırık kullanıcı akışı ne?
2. Owner boundary'yi bul: validation, mapping, persistence, event parsing, runtime state veya UI hangi katmanda authoritative?
3. Writer/reader zincirini çıkar: bu state'i kim üretiyor, kim dönüştürüyor, kim tüketiyor?
4. Aynı kusurun başka nerelerde tekrar edeceğini ara: benzer controller, benzer event, benzer DTO, benzer counter, benzer campaign akışı.
5. Kalıcı çözümü owner boundary'de uygula: contract, validation, parser, mapper veya state owner düzeyinde kapat.
6. Çözümün hangi contract'ı netleştirdiğini yaz.
7. Regression test ile aynı bug class'ını kilitle.

## Repo Özel Kurallar

- Frontend validation yardımcı katmandır; authoritative kural backend service veya uygun contract owner'ında kapanır.
- Redis message veya AMI event normalize edilecekse bu iş boundary parser/adapter katmanında yapılır; downstream consumer'lara dağılmaz.
- Counter veya runtime state sorunu varsa önce authoritative source belirlenir; mümkünse türetilmiş değer tercih edilir.
- `Campaign`, `DialerFacade.campaignContexts`, `ACDEngine`, `CallUpToDateKeeper` ve AMI/Redis event akışlarında semptomu değil writer/reader tutarsızlığını çöz.
- Bir bug birden fazla skill boundary'sine dokunuyorsa bu skill'i ilgili domain skill'iyle birlikte kullan:
  - `gbpd-ami-event-flow`
  - `gbpd-redis-contracts`
  - `gbpd-api-persistence-contracts`
  - `gbpd-angularjs-legacy-ui`
  - `gbpd-runtime-state-audit`
  - `gbpd-tdd-workflow`

## Fail-Closed Yorumu

Beklenmeyen durum sessizce tolere edilmez. Ancak fail-closed, tüm sistemi kör durdurmak anlamına gelmez.

- Bozuk input, bozuk event veya kontrat dışı state explicit reject edilir veya kontrollü exception ile görünür kılınır.
- Sessiz default ile devam edilmez.
- Etkilenen request, record, call veya campaign unit'i kirli state üretmeden durdurulur.
- Shared runtime state sessizce bozulmaz.

## Done Kriteri

- Workaround veya silent fallback yok
- Root cause owner boundary'de kapatılmış
- Benzer bug class'ı için tarama yapılmış
- Contract seviyesinde çözüm net
- Regression veya karakterizasyon testi eklenmiş

## Yararlı Arama Kalıpları

- `rg -n "catch \\(Exception|catch \\(.*\\) \\{|return null|defaultValue|fallback" gbpowerdialer/src/main/java src/main/webapp/js`
- `rg -n "increment|decrement|activeCalls|ringingCalls|agentsSize|callsSize" gbpowerdialer/src/main/java`
- `rg -n "REDIS_CHANNEL|publishMessage|subscribe|MessageListener|VarSetEvent|Hangup|Originate" gbpowerdialer/src/main/java`
- `rg -n "expiredDate|createDate|validation|ngModel|ngFormatDate" gbpowerdialer/src/main/java src/main/webapp`
