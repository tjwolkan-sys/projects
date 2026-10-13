# VS Code Tomcat Debug Standardı

Bu repo VS Code içinde Tomcat üzerinde Java remote debug ile çalışacak şekilde yapılandırılır. Ekip standardı `.vscode` altında tutulur; developer'a göre değişen path ve port değerleri `.codex/TOOL-PATHS.local.properties` içinde kalır.

## Beklenen Extension'lar

`.vscode/extensions.json` aşağıdaki extension'ları önerir:

- `vscjava.vscode-java-pack`: Java language support, debugger, Maven ve test explorer paketi.
- `redhat.vscode-xml`: Spring XML, `web.xml`, `wro.xml`, Hibernate/Dozer XML dosyaları için XML desteği.
- `redhat.vscode-community-server-connector`: Tomcat gibi application server'ları VS Code içinden yönetmek için.
- `ms-vscode.PowerShell`: Windows task script'lerini düzenlemek ve çalıştırmak için.
- `vscjava.vscode-lombok`: Lombok annotation desteği için.

## Tool Path Değerleri

Her developer `.codex/TOOL-PATHS.example.properties` dosyasını `.codex/TOOL-PATHS.local.properties` olarak kopyalar ve kendi local path'leriyle doldurur. VS Code task'ları bu dosyayı okur.

Tomcat debug için beklenen ek değerler:

```properties
CATALINA_HOME=C:/tools/apache-tomcat-9.0.x
TOMCAT_HOME=C:/tools/apache-tomcat-9.0.x
TOMCAT_BASE=
MAVEN_PROFILE=properties-tr-dev
VSCODE_MAVEN_OFFLINE=false
TOMCAT_DEBUG_PORT=5005
TOMCAT_DEBUG_SUSPEND=n
TOMCAT_HTTP_PORT=18080
TOMCAT_SHUTDOWN_PORT=18005
TOMCAT_OPEN_BROWSER=true
TOMCAT_OPEN_BROWSER_DELAY_SECONDS=5
APP_CONTEXT_PATH=gbpowerdialer
```

`.idea/misc.xml` içinde aktif Maven profile `properties-tr-dev` olduğu için VS Code varsayılanı da budur. IntelliJ'de farklı bir profile kullanılıyorsa yalnızca `MAVEN_PROFILE` değeri değiştirilir.

`TOMCAT_BASE` boş bırakılırsa VS Code task'ı `.vscode/.local/tomcat-base` altında generated `CATALINA_BASE` oluşturur. Bu klasör commitlenmez. Tomcat install klasörü değiştirilmez.

`VSCODE_MAVEN_OFFLINE=false` olduğunda VS Code debug task'ı Maven'i online çalıştırır. VPN/Nexus erişimi olan developer ortamında eksik artifact'ler ilk debug denemesinde çözülebilir; IntelliJ'de önce build alma veya Maven reload yapma gerekmemelidir. Codex'in kendi doğrulama komutları yine offline standardını korur.

## Launch Profile'ları

VS Code içinde Tomcat debug için şu launch profile'ları vardır:

- `Tomcat: Build Deploy Debug`: Tomcat'i durdurur, Maven clean package çalıştırır, `gbpowerdialer.war` dosyasını generated Tomcat base `webapps` altına deploy eder, Tomcat'i JDWP debug portuyla başlatır ve debugger attach eder.
- `Tomcat: Restart Existing Deploy Debug`: Maven build ve WAR deploy yapmadan mevcut Tomcat webapps deploy'unu debug portuyla başlatır.
- `Tomcat: Redeploy Existing WAR Debug`: Maven build yapmadan `target/gbpowerdialer.war` dosyasını yeniden deploy eder ve debug portuyla başlatır.
- `Tomcat: Attach Only`: Zaten debug portuyla çalışan local Tomcat JVM'ine attach olur.
- `Tomcat: Remote Attach`: Remote dev/staging JVM'e attach olur; SSH tunnel kullanılıyorsa host `localhost` bırakılır.

Standart URL otomatik açılır:

```text
http://localhost:18080/gbpowerdialer/
```

## Task'lar

`.vscode/tasks.json` şu task'ları sağlar:

- `tomcat: build deploy debug`: `F5` ile kullanılan ana task.
- `tomcat: restart existing deploy debug`: Build ve deploy yapmadan mevcut deploy'u restart eder.
- `tomcat: redeploy existing war debug`: Maven build yapmadan mevcut WAR'ı deploy eder.
- `tomcat: stop`: Local Tomcat'i durdurmayı dener.
- `maven: package war`: Yalnızca WAR build yapar.

Build task'ı `-Dmaven.test.skip=true clean package` kullanır. Maven online/offline modu `VSCODE_MAVEN_OFFLINE` değerinden gelir. Bu debug akışı kod davranışı doğrulaması değildir; testler yalnızca kod davranışı değiştiğinde ilgili kapsamda çalıştırılır.

## IntelliJ Bağımsızlığı

VS Code debug akışı IntelliJ run configuration veya IntelliJ Maven reload'a bağlı değildir. Doğru tool path değerleri verildiğinde ilk debug denemesi:

- Maven ile generated source'ları üretir.
- WAR paketini oluşturur.
- Generated `CATALINA_BASE` altında WAR deploy eder.
- Tomcat'i VS Code'a ayrılmış portlarla başlatır.
- Browser'ı VS Code HTTP portunda açar.

Ekip standardında `MAVEN_CMD` için standalone Maven binary veya PATH'teki `mvn.cmd` tercih edilir. Developer geçici olarak IntelliJ içindeki Maven binary yolunu kullanabilir; ancak repo standardı IntelliJ'e bağımlı değildir.

Bu akış standalone Maven 3.9.14, Java 8, Tomcat 9.0.117, `properties-tr-dev` profile, HTTP `18080`, shutdown `18005` ve debug `5005` değerleriyle local VS Code debug senaryosunda doğrulanmıştır.

## Windows Protobuf Temp Klasörü

`protobuf-maven-plugin` Windows üzerinde `target/protoc-dependencies` altındaki geçici klasörleri temizlerken dosya kilidine takılabilir. Bu genellikle çalışan Tomcat, IDE indexer veya file watcher kaynaklıdır.

VS Code standardı bu riski azaltmak için:

- Ana debug task'ında Maven build'den önce Tomcat'i durdurur.
- Build öncesi yalnızca `gbpowerdialer/target/protoc-dependencies` klasörünü güvenli path kontrolüyle temizlemeyi dener.
- VS Code watcher ve search ayarlarında `target`, `node_modules` ve `coverage` klasörlerini exclude eder.
- Java language server resource filter listesinde `target` bulunmaz; `target/generated-sources/protobuf` altındaki gRPC kaynakları IDE classpath'ine girebilmelidir.

Bu hata daha önce oluştuysa VS Code ayarlarının devreye girmesi için `Developer: Reload Window` çalıştırıp task'ı tekrar dene.

## Shutdown Port Çakışması

Tomcat default shutdown portu `8005` bazı Windows ortamlarında başka bir process veya System tarafından tutulabilir. Bu durumda Tomcat HTTP portunu kısa süre açıp sonra kendini kapatır ve browser'da `8080` erişilemez.

VS Code standardı bu riski azaltmak için:

- Tomcat install klasörü yerine generated `CATALINA_BASE` kullanır.
- Generated `server.xml` içinde shutdown portu default `18005` olarak ayarlar.
- HTTP portu default `18080`, debug portu default `5005` olarak kalır.
- HTTP portu hazır olana kadar task bitmez; port hazır olunca browser `http://localhost:18080/gbpowerdialer/` adresini birkaç saniye gecikmeyle açar. Bu gecikme VS Code Java debugger'ın attach olması için bırakılır.

Port çakışması varsa `.codex/TOOL-PATHS.local.properties` içinde `TOMCAT_HTTP_PORT`, `TOMCAT_SHUTDOWN_PORT` veya `TOMCAT_DEBUG_PORT` değiştirilebilir.

## Güvenlik ve Local Değerler

- `.vscode` içindeki dosyalar ekip standardıdır ve commitlenir.
- `.codex/TOOL-PATHS.local.properties` commitlenmez.
- Developer'a özel Java/Maven path değerleri global VS Code User Settings'e yazılacaksa bunun başka Java projelerini etkileyebileceği unutulmaz; varsayılan akış repo ayarları ve `.codex/TOOL-PATHS.local.properties` üzerinden yürütülür.
- Gerçek credential, müşteri bilgisi veya ortam secret'ı VS Code ayarlarına yazılmaz.
- Tomcat deploy temizliği yalnızca generated `CATALINA_BASE/webapps` klasörü altındaki `APP_CONTEXT_PATH` hedefini siler.
