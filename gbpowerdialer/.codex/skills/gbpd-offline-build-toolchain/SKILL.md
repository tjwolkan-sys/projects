---
name: gbpd-offline-build-toolchain
description: gbpowerdialer'da Maven/Java/Node/Tomcat tool path, offline Maven, Nexus/VPN cache, Surefire provider, protobuf/WRO/plugin/profile veya npm/Karma build-test teşhisi gerektiğinde kullan. Dependency indirmeden doğrulama komutu kurmak ve cache/toolchain hatalarını gerçek compile/test hatalarından ayırmak için.
---

# gbpd-offline-build-toolchain

## Amaç

Çalışmayı yürüten ajan gbpowerdialer'da build veya test çalıştırırken dependency indirmeyi denemez. Bu skill, local tool path dosyasını kullanarak offline doğrulama komutu kurmak ve hata çıktısını doğru sınıflandırmak içindir.

## Kapsam

- `gbpd-tdd-workflow` yerine geçmez; TDD akışındaki doğrulama ve build/cache teşhis adımını destekler.
- Kod davranışı değişiyorsa `gbpd-tdd-workflow` ve `gbpd-safe-change` ile birlikte kullan.
- POM, Maven profil, plugin, test runner, local `.m2`, Nexus/VPN, npm/Karma veya Tomcat doğrulaması gündeme gelirse kullan.
- Soru-cevap, salt inceleme, planlama, dokümantasyon-only, skill-only veya AGENTS standardı değişikliklerinde build/test komutu çalıştırma; yalnızca gerekli tool path ve yapılandırma dosyalarını oku.
- Build/test komutunu yalnızca kod davranışı değiştiğinde, runtime doğrulaması gerektiğinde veya geliştirici açıkça istediğinde kur. Varsayılan doğrulama hedefli testtir; tam suite geniş risk veya açık istek gerektirir.

## Akış

1. Repo kökündeki `.codex/TOOL-PATHS.local.properties` dosyasını kontrol et.
2. Varsa `MAVEN_CMD`, `MAVEN_SETTINGS`, `MAVEN_REPO_LOCAL`, `MAVEN_PROFILE`, `VSCODE_MAVEN_OFFLINE`, `JAVA_HOME`, `CATALINA_HOME`, `TOMCAT_HOME`, `TOMCAT_BASE`, `TOMCAT_DEBUG_PORT`, `TOMCAT_DEBUG_SUSPEND`, `TOMCAT_HTTP_PORT`, `TOMCAT_SHUTDOWN_PORT`, `TOMCAT_OPEN_BROWSER`, `TOMCAT_OPEN_BROWSER_DELAY_SECONDS`, `APP_CONTEXT_PATH` ve `NPM_CMD` değerlerini kullan.
3. `JAVA_HOME` varsa komuttan önce `JAVA_HOME` set et ve `JAVA_HOME/bin` yolunu `PATH` başına al.
4. Ajan tarafından çalıştırılan Maven doğrulama komutlarını her zaman `-o` offline modda kur.
5. VS Code Tomcat debug task'ları developer tarafından çalıştırılır; `VSCODE_MAVEN_OFFLINE=false` ise bu task'lar online Maven kullanabilir.
6. `MAVEN_SETTINGS` varsa `-s <settings.xml>` ekle.
7. `MAVEN_REPO_LOCAL` varsa `-Dmaven.repo.local=<repo>` ekle.
8. Ajan doğrulamasında dependency, plugin veya artifact indirmeyi deneme.
9. Hata çıktısını compile/test hatası mı yoksa toolchain/cache eksikliği mi diye sınıflandır.

## Standart Komutlar

Hedefli Java testi:

```powershell
<MAVEN_CMD> -o -s <settings.xml> -Dmaven.repo.local=<repo> -Dtest=ClassName test
```

Paketleme:

```powershell
<MAVEN_CMD> -o -DskipTests package
```

Java 17 desteği gerçekten gerekiyorsa:

```powershell
<MAVEN_CMD> -o -Pjava-17-support test
```

AngularJS testi:

```powershell
<NPM_CMD> run test:angular
```

`NPM_CMD` yoksa Windows'ta `npm.cmd run test:angular` tercih et.

## Hata Sınıflandırması

- Test failure: Test runner başlamış, assertion veya test exception oluşmuştur.
- Compile failure: `compile` veya `testCompile` aşaması Java hatasıyla kırılmıştır.
- Dependency cache eksikliği: Normal jar local repository'de yoktur.
- Maven plugin cache eksikliği: Build plugin jar'ı local repository'de yoktur.
- Maven extension eksikliği: Build extension veya OS classifier çözülememiştir.
- Surefire provider eksikliği: Test runner başlamadan Surefire provider bulunamamıştır.
- Protobuf native eksikliği: `protoc` veya `protoc-gen-grpc-java` OS classifier artifact'i yoktur.
- WRO/profile hatası: Package veya özel profile WRO/profil filtreleme yüzeyinde kırılmıştır.
- Settings/mirror hatası: `settings.xml`, mirror, repository id veya local repo path'i kaynaklıdır.
- npm/Karma hatası: `node_modules`, Chrome launcher, Karma config veya npm cache kaynaklıdır.

## VPN ve Cache Handoff

- Eksik artifact varsa ajan indirme komutu çalıştırmaz.
- Geliştiriciye eksik artifact koordinatını, hangi komutta gerektiğini ve neden blokladığını açık yaz.
- Geliştirici VPN'de local `.m2` veya Nexus cache'i güncelleyip VPN'den çıktıktan sonra ajan aynı offline komutu tekrar dener.
- Kullanıcı özellikle isterse VPN'de manuel çalıştıracağı komut önerilebilir; ajan bu komutu çalıştırmaz.
- Surefire provider veya plugin cache eksiğinde custom JUnitCore runner deneme; cache güncellendikten sonra Maven/Surefire ile doğrula.

## npm/Karma Notları

- `package-lock.json` korunur; dependency sürümü keyfi güncellenmez.
- `node_modules` yoksa ajan otomatik `npm install` veya `npm ci` denemez.
- Chrome launcher veya sandbox hatası test failure gibi raporlanmaz; environment/toolchain hatası olarak ayrılır.

## Raporlama Kuralları

- Gerçek local path, internal endpoint, şifre, token veya müşteri bilgisini final rapora taşıma.
- Komut sonucunu özetlerken gerçek gizli değer veya ortam bilgisini kopyalama.
- Test çalışmadıysa "test fail" deme; hangi aşamada runner başlamadan durduğunu yaz.
