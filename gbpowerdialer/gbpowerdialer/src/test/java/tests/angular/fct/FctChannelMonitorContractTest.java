package tests.angular.fct;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * FCT Kanal Monitörü ekranının AngularJS sözleşmesi: route, JS yükleme sırası (hem WRO bundle hem
 * production dışı {@code pages/sources.jsp} yolu), template, Options menü girdisi, Türkçe/İngilizce
 * localization anahtarları ve yanıp sönmeyi engelleyen {@code track by} kullanımı.
 */
public class FctChannelMonitorContractTest {

  /**
   * i18n anahtarları bu iki dil için zorunlu tutulur. {@code i18n} filtresi anahtarı {@code locMsg}
   * içinde bulamazsa anahtarın kendisini döndürür (app.js), yani eksik anahtar ekranda ham metin
   * olarak görünür — bu test o durumu build zamanında yakalar.
   */
  private static final String[] REQUIRED_LOCALIZATION_FILES = { "localization_tr.js", "localization_en.js" };

  private static final Pattern I18N_KEY_PATTERN = Pattern.compile("'([^']+)'\\s*\\|\\s*i18n");

  @Test
  public void routeShouldBindMonitorTemplateAndController() throws IOException {
    String appJs = readProjectFile("src/main/webapp/js/app.js");

    String route = routeBody(appJs, "/fctChannelMonitor");

    assertTrue(route.contains("templateUrl: 'pages/fctChannelMonitor.html'"));
    assertTrue(route.contains("controller: 'fctChannelMonitorCtrl as fctMonitor'"));
  }

  @Test
  public void wroBundleShouldLoadFactoryBeforeController() throws IOException {
    String wroXml = readProjectFile("src/main/webapp/WEB-INF/wro.xml");

    int appModule = indexOfRequired(wroXml, "/js/app.js");
    int factory = indexOfRequired(wroXml, "/js/services/fctChannelFactory.js");
    int controller = indexOfRequired(wroXml, "/js/controllers/fctChannelMonitorCtrl.js");

    assertTrue("app module controller'dan önce yüklenmeli", appModule < controller);
    assertTrue("factory controller'dan önce yüklenmeli", factory < controller);
  }

  /**
   * Production dışı stage'de (geliştirici ortamı) index.jsp WRO bundle yerine pages/sources.jsp'yi
   * include eder ve JS dosyaları tek tek buradan yüklenir. Dosya burada eksikse controller hiç
   * yüklenmez ve template ham {{...}} olarak render edilir; wro.xml'i güncellemek tek başına yetmez.
   */
  @Test
  public void sourcesJspShouldLoadMonitorScriptsForNonProductionStage() throws IOException {
    String sourcesJsp = readProjectFile("src/main/webapp/pages/sources.jsp");

    int factory = indexOfRequired(sourcesJsp, "js/services/fctChannelFactory.js");
    int controller = indexOfRequired(sourcesJsp, "js/controllers/fctChannelMonitorCtrl.js");

    assertTrue("factory controller'dan önce yüklenmeli", factory < controller);
    assertTrue("factory cache-busting parametresiyle yüklenmeli",
        sourcesJsp.contains("js/services/fctChannelFactory.js?v=${appConfig['project.version']}"));
    assertTrue("controller cache-busting parametresiyle yüklenmeli",
        sourcesJsp.contains("js/controllers/fctChannelMonitorCtrl.js?v=${appConfig['project.version']}"));
  }

  /**
   * Localization dosyaları cache-busting olmadan yüklenirse tarayıcı eski locMsg kopyasını servis
   * eder ve yeni eklenen anahtarlar ekranda ham anahtar olarak görünür.
   */
  @Test
  public void localizationScriptsShouldBeCacheBusted() throws IOException {
    String indexJsp = readProjectFile("src/main/webapp/index.jsp");

    for (String fileName : new String[] { "localization_tr.js", "localization_en.js",
        "localization_ru.js", "localization_ua.js" }) {
      assertTrue("Localization dosyası ?v= ile yüklenmeli: " + fileName,
          indexJsp.contains("js/localization/" + fileName + "?v=${appConfig['project.version']}"));
    }
  }

  @Test
  public void optionsMenuShouldExposeMonitorScreen() throws IOException {
    String indexJsp = readProjectFile("src/main/webapp/index.jsp");

    assertTrue("Options menüsünde monitor bağlantısı olmalı", indexJsp.contains("href=\"#fctChannelMonitor\""));
    assertTrue("menü etiketi localization anahtarını kullanmalı",
        indexJsp.contains("{{'menu.fct.channel.monitor' | i18n}}"));
    assertTrue("route aktiflik listesine eklenmeli", indexJsp.contains("isActive('/fctChannelMonitor')"));
  }

  @Test
  public void templateShouldUseTrackByToAvoidRerenderFlicker() throws IOException {
    String template = readProjectFile("src/main/webapp/pages/fctChannelMonitor.html");

    assertTrue("numara satırları phoneNumber ile track edilmeli",
        template.contains("entry in fctMonitor.visibleEntries track by entry.phoneNumber"));
    assertTrue("lambalar index ile track edilmeli",
        template.contains("lamp in entry.lamps track by $index"));
    assertTrue("feed olayları id ile track edilmeli",
        template.contains("event in fctMonitor.visibleEvents track by event.id"));
    assertTrue("in-flight yaş chip'leri callId ile track edilmeli",
        template.contains("chip in entry.ageChips track by chip.callId"));
    assertTrue("QSS type alanı virgüllü çok değerli geldiği için badge'ler token bazlı render edilmeli",
        template.contains("token in entry.typeTokens track by token"));
  }

  @Test
  public void templateShouldSummarizeAttemptsAndWarnWhenCapacityIsMissing() throws IOException {
    String template = readProjectFile("src/main/webapp/pages/fctChannelMonitor.html");

    assertTrue("QSS attempts alanı AT_ yokken 0..99 ile dolduğu için ham basılmamalı",
        template.contains("entry.attemptsSummary"));
    assertTrue("hiçbir FCT'de capacity yoksa gate'in pasif olduğu operatöre gösterilmeli",
        template.contains("fctMonitor.capacityMissing"));
  }

  /**
   * Trunk kolonu, {@code DIALER_TRUNK} olarak Asterisk'e gönderilen değeri ekrandan görmeyi sağlar;
   * olmadığında hangi numaranın hangi trunk'tan çıktığını anlamak için AMI/log debug gerekir. QSS
   * trunk göndermediğinde boş hücre yerine "varsayılan" rozeti basılır, aksi hâlde eksik veri ile
   * varsayılan trunk ayırt edilemez.
   */
  @Test
  public void templateShouldExposeTrunkColumnWithDefaultBadge() throws IOException {
    String template = readProjectFile("src/main/webapp/pages/fctChannelMonitor.html");

    assertTrue("trunk kolonu başlığı olmalı", template.contains("{{'fct.monitor.col.trunk' | i18n}}"));
    assertTrue("trunk değeri basılmalı", template.contains("{{entry.trunk}}"));
    assertTrue("trunk boşken varsayılan rozeti gösterilmeli",
        template.contains("{{'fct.monitor.trunk.default' | i18n}}"));
    assertTrue("varsayılan rozeti açıklamalı tooltip taşımalı",
        template.contains("{{'fct.monitor.trunk.default.hint' | i18n}}"));
  }

  @Test
  public void templateShouldRespectReducedMotionAndHorizontalScroll() throws IOException {
    String template = readProjectFile("src/main/webapp/pages/fctChannelMonitor.html");

    assertTrue("prefers-reduced-motion desteklenmeli", template.contains("prefers-reduced-motion: reduce"));
    assertTrue("geniş tablo kendi içinde yatay scroll almalı", template.contains("overflow-x: auto"));
    assertTrue("focus-visible görünür olmalı", template.contains(":focus-visible"));
  }

  @Test
  public void factoryShouldSkipErrorRedirectOnPolling() throws IOException {
    String factory = readProjectFile("src/main/webapp/js/services/fctChannelFactory.js");

    assertTrue(factory.contains("fctChannel/snapshot"));
    assertTrue("polling hatası /error route'una yönlendirmemeli", factory.contains("skipErrorRedirect"));
  }

  /**
   * Template'te kullanılan HER i18n anahtarı Türkçe ve İngilizce dosyalarda tanımlı olmalıdır.
   * Anahtar listesi template'ten türetilir; böylece ekrana yeni bir metin eklenip localization
   * atlandığında test kendiliğinden kırılır (elle güncellenen anahtar listesi tutulmaz).
   */
  @Test
  public void localizationFilesShouldDefineEveryKeyUsedInTemplate() throws IOException {
    String template = readProjectFile("src/main/webapp/pages/fctChannelMonitor.html");
    Set<String> usedKeys = i18nKeysIn(template);
    usedKeys.add("menu.fct.channel.monitor");

    assertTrue("Template'te i18n anahtarı bulunamadı, regex bozulmuş olabilir", usedKeys.size() > 30);

    for (String fileName : REQUIRED_LOCALIZATION_FILES) {
      String content = readProjectFile("src/main/webapp/js/localization/" + fileName);
      for (String key : usedKeys) {
        assertTrue("Eksik localization anahtarı: " + key + " (" + fileName + ")",
            content.contains("'" + key + "'"));
      }
    }
  }

  private Set<String> i18nKeysIn(String template) {
    Set<String> keys = new LinkedHashSet<>();
    Matcher matcher = I18N_KEY_PATTERN.matcher(template);
    while (matcher.find()) {
      keys.add(matcher.group(1));
    }
    return keys;
  }

  private String routeBody(String appJs, String route) {
    Pattern routePattern = Pattern.compile("\\.when\\(\\s*'" + Pattern.quote(route) + "'\\s*,\\s*\\{(.*?)\\}\\)", Pattern.DOTALL);
    Matcher matcher = routePattern.matcher(appJs);
    assertTrue("Missing AngularJS route: " + route, matcher.find());
    return matcher.group(1);
  }

  private int indexOfRequired(String content, String text) {
    int index = content.indexOf(text);
    assertTrue("Missing expected WRO entry: " + text, index >= 0);
    return index;
  }

  private String readProjectFile(String relativePath) throws IOException {
    Path path = Paths.get(relativePath);
    if (!Files.exists(path)) {
      path = Paths.get("gbpowerdialer").resolve(relativePath);
    }
    assertTrue("Missing project file: " + relativePath, Files.exists(path));
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }
}
