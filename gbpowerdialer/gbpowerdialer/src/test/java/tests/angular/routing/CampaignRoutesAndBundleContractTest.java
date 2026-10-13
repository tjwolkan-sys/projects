package tests.angular.routing;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class CampaignRoutesAndBundleContractTest {

  @Test
  public void campaignListRouteKeepsTemplateControllerAndDefaultRedirectContract() throws IOException {
    String appJs = readProjectFile("src/main/webapp/js/app.js");

    String campaignListRoute = routeBody(appJs, "/campaignList");

    assertTrue(campaignListRoute.contains("templateUrl: 'pages/campaignList'"));
    assertTrue(campaignListRoute.contains("controller: 'campaignCtrl as campaign'"));
    assertTrue(appJs.contains("redirectTo: \"/campaignList\""));
  }

  @Test
  public void wroGlobalBundleKeepsAngularCampaignDependenciesBeforeCampaignController() throws IOException {
    String wroXml = readProjectFile("src/main/webapp/WEB-INF/wro.xml");

    int angular = indexOfRequired(wroXml, "/js/core/angular.min.js");
    int servicesModule = indexOfRequired(wroXml, "/js/services.js");
    int appModule = indexOfRequired(wroXml, "/js/app.js");
    int campaignFactory = indexOfRequired(wroXml, "/js/services/campaignFactory.js");
    int campaignController = indexOfRequired(wroXml, "/js/controllers/campaignCtrl.js");

    assertBefore("Angular core must load before services module", angular, servicesModule);
    assertBefore("Services module must load before app module", servicesModule, appModule);
    assertBefore("App module must load before campaign controller", appModule, campaignController);
    assertBefore("Campaign factory must load before campaign controller", campaignFactory, campaignController);
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

  private void assertBefore(String message, int earlier, int later) {
    assertTrue(message, earlier < later);
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
