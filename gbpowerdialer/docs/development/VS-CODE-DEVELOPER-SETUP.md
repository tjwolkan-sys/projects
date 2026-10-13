# VS Code Developer Setup

Bu doküman, gbpowerdialer'ı IntelliJ run configuration bağımlılığı olmadan VS Code üzerinde Tomcat debug ile çalıştırmak için developer kurulum adımlarını tarif eder.

## Amaç

- Developer ilk VS Code debug denemesinde Maven generated source, WAR package, Tomcat deploy ve Java attach debug akışını çalıştırabilsin.
- IntelliJ'de önce build alma, Maven reload yapma veya IntelliJ içindeki Maven binary'ye bağımlı kalma zorunluluğu olmasın.
- Developer'a özel path ve port değerleri commitlenmeyen `.codex/TOOL-PATHS.local.properties` içinde kalsın.

## Gerekli Kurulumlar

1. VS Code önerilen extension'ları kur.

   Repo açıldığında `.vscode/extensions.json` aşağıdaki extension'ları önerir:

   - `vscjava.vscode-java-pack`
   - `redhat.vscode-xml`
   - `redhat.vscode-community-server-connector`
   - `ms-vscode.PowerShell`
   - `vscjava.vscode-lombok`

2. Apache Maven binary distribution indir.

   Resmî download sayfası:

   ```text
   https://maven.apache.org/download.cgi
   ```

   İndirilecek paket binary zip olmalıdır:

   ```text
   apache-maven-3.9.14-bin.zip
   ```

   Source zip kullanılmaz. Zip açıldıktan sonra beklenen dosya:

   ```text
   C:/javatools/maven/apache-maven-3.9.14/bin/mvn.cmd
   ```

3. Java 8 JDK path'ini hazırla.

   Örnek:

   ```text
   C:/Program Files/Java/jdk1.8.0_202
   ```

4. Tomcat 9 path'ini hazırla.

   Örnek:

   ```text
   C:/javatools/tomcat/apache-tomcat-9.0.117
   ```

5. Gerekliyse Node/NPM path'ini hazırla.

   Örnek:

   ```text
   C:/Program Files/nodejs/npm.cmd
   ```

## Local Tool Path Dosyası

Repo içinde şablon dosyayı local dosyaya kopyala:

```text
.codex/TOOL-PATHS.example.properties
```

Hedef local dosya:

```text
.codex/TOOL-PATHS.local.properties
```

Bu dosya developer'a özeldir ve commitlenmez.

Örnek içerik:

```properties
MAVEN_CMD=C:/javatools/maven/apache-maven-3.9.14/bin/mvn.cmd
MAVEN_SETTINGS=C:/Users/<kullanici>/.m2/settings.xml
MAVEN_REPO_LOCAL=C:/Users/<kullanici>/.m2/repository
JAVA_HOME=C:/Program Files/Java/jdk1.8.0_202
NPM_CMD=C:/Program Files/nodejs/npm.cmd

CATALINA_HOME=C:/javatools/tomcat/apache-tomcat-9.0.117
TOMCAT_HOME=C:/javatools/tomcat/apache-tomcat-9.0.117

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

## VS Code Debug Çalıştırma

1. VS Code ile repo root klasörünü aç.
2. Command Palette üzerinden `Developer: Reload Window` çalıştır.
3. Run and Debug panelinden şu profile'ı seç:

   ```text
   Tomcat: Build Deploy Debug
   ```

4. `F5` ile debug başlat.

Başarılı akışta:

- Maven `clean package` çalışır.
- Testler çalışmaz ve test kaynakları compile edilmez.
- WAR generated Tomcat base altına deploy edilir.
- Tomcat debug portuyla başlar.
- VS Code Java debugger attach olur.
- Browser birkaç saniye gecikmeyle açılır.

Uygulama URL'i:

```text
http://localhost:18080/gbpowerdialer/
```

## Portlar

- `18080`: VS Code Tomcat HTTP portu.
- `18005`: VS Code Tomcat shutdown portu.
- `5005`: Java JDWP debug portu.

`5005` browser portu değildir. Browser kontrolü için `18080` ve context path kullanılır.

IntelliJ/Tomcat akışı `8080` üzerinde kalabilir. VS Code debug akışı ayrı port seti kullandığı için IntelliJ ile çakışmamalıdır.

## Generated Dosyalar

VS Code task'ı Tomcat install klasörünü değiştirmez. Generated `CATALINA_BASE` repo altında oluşturulur:

```text
.vscode/.local/tomcat-base
```

Bu klasör commitlenmez. İçinde Tomcat conf, logs, deployed WAR, exploded webapp ve work çıktıları olabilir.

Commit'e girmemesi gereken local/generated alanlar:

```text
.codex/TOOL-PATHS.local.properties
.vscode/.local/
.idea/
gbpowerdialer/target/
gbpowerdialer/node_modules/
```

## Maven Online/Offline Davranışı

VS Code debug task'ı developer tarafından çalıştırılır. VPN/Nexus erişimi varsa online Maven kullanılabilir:

```properties
VSCODE_MAVEN_OFFLINE=false
```

Bu durumda eksik artifact'ler ilk debug denemesinde Nexus üzerinden çözülebilir.

Codex'in kendi doğrulama komutları ayrı standarda tabidir ve varsayılan olarak offline çalışır. Bu ayrım özellikle korunur.

## Ekip Mesajı

Ekip ile kısa duyuru olarak şu metin paylaşılabilir:

```text
gbpowerdialer için VS Code Tomcat debug akışı repo standardına alındı.

Apache Maven binary distribution indirip local path'inizi .codex/TOOL-PATHS.local.properties içinde MAVEN_CMD olarak tanımlayın.

Örnek:
MAVEN_CMD=C:/javatools/maven/apache-maven-3.9.14/bin/mvn.cmd

Tomcat, Java 8, Maven settings ve local repository path'lerinizi aynı dosyada doldurun.

VS Code'da repo root'u açıp Developer: Reload Window çalıştırın.
Run and Debug profile:
Tomcat: Build Deploy Debug

Uygulama açılış URL'i:
http://localhost:18080/gbpowerdialer/

5005 Java debug portudur, browser portu değildir.
IntelliJ build/reload bağımlılığı yoktur.
```
