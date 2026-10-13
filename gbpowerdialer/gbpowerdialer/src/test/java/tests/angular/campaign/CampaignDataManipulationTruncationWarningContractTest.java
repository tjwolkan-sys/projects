package tests.angular.campaign;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * Task 327143: fetch yanitinda X-Records-Truncated=true geldiginde
 * DataManipulation ekrani kullaniciya kesilme uyarisi gostermelidir;
 * uyari metni dort dilde de tanimli olmalidir.
 */
public class CampaignDataManipulationTruncationWarningContractTest {

  private static final String LOCALIZATION_KEY = "campaignDataManipulation.validation.recordsTruncated";

  @Test
  public void controllerChecksTruncationHeaderAndWarnsUser() throws IOException {
    String controller = readProjectFile("src/main/webapp/js/controllers/campaignDataManipulationAndUpdateCtrl.js");

    assertTrue("fetch yaniti X-Records-Truncated header'ini kontrol etmeli",
        controller.contains("X-Records-Truncated"));
    assertTrue("Kesilme durumunda locMsg uyarisi gosterilmeli",
        controller.contains(LOCALIZATION_KEY));
  }

  @Test
  public void truncationWarningIsLocalizedInAllLanguages() throws IOException {
    String[] localizationFiles = {
        "src/main/webapp/js/localization/localization_tr.js",
        "src/main/webapp/js/localization/localization_en.js",
        "src/main/webapp/js/localization/localization_ru.js",
        "src/main/webapp/js/localization/localization_ua.js"
    };
    for (String file : localizationFiles) {
      String content = readProjectFile(file);
      assertTrue("Localization anahtari eksik: " + file,
          content.contains(LOCALIZATION_KEY));
    }
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
