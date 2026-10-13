package tests.angular.campaign;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class CampaignFreshDataMonitoringContractTest {

  @Test
  public void campaignGridShowsFreshDataListSizeNearRetryAndCallbackMetrics() throws IOException {
    String controller = readProjectFile("src/main/webapp/js/controllers/campaignCtrl.js");

    int listRemaining = indexOfRequired(controller, "field : 'listRemaining'");
    int retryList = indexOfRequired(controller, "field : 'retryListSize'");
    int callbackList = indexOfRequired(controller, "field : 'callbackListSize'");
    int freshData = indexOfRequired(controller, "field : 'freshDataListSize'");

    assertTrue(listRemaining < freshData);
    assertTrue(freshData < retryList);
    assertTrue(retryList < callbackList);
    assertTrue(controller.contains("displayName : locMsg['campaign.freshDataListSize']"));
    assertTrue(controller.contains("campaign.freshDataListSize = 0;"));
  }

  @Test
  public void allLocalizationFilesContainFreshDataLabel() throws IOException {
    assertTrue(readProjectFile("src/main/webapp/js/localization/localization_tr.js").contains("'campaign.freshDataListSize'"));
    assertTrue(readProjectFile("src/main/webapp/js/localization/localization_en.js").contains("'campaign.freshDataListSize'"));
    assertTrue(readProjectFile("src/main/webapp/js/localization/localization_ru.js").contains("'campaign.freshDataListSize'"));
    assertTrue(readProjectFile("src/main/webapp/js/localization/localization_ua.js").contains("'campaign.freshDataListSize'"));
  }

  private int indexOfRequired(String content, String text) {
    int index = content.indexOf(text);
    assertTrue("Missing expected text: " + text, index >= 0);
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
