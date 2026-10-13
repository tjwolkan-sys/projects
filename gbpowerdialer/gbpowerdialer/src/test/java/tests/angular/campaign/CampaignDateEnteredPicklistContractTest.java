package tests.angular.campaign;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * Call Results tab'indeki opsiyonel "Veri Giriş Tarihi" (date_entered) kolon
 * mapping dropdown'unun frontend contract'i: template select'i + tarih tipli
 * restrictByType, JS constants tip kisitlari, controller init listesi ve dort
 * dildeki localization key'i birlikte kilitlenir (FEAT-318332 / PR-G).
 *
 * date_entered mapping'i delta-sync'in eligible kampanyalari secmesi icin
 * kullanilir (DncDeltaSyncScheduler); bos birakilirsa kampanya delta-sync'e
 * girmez. Bu nedenle mapping opsiyoneldir ve ng-required tasimaz.
 */
public class CampaignDateEnteredPicklistContractTest {

  @Test
  public void campaignFormShouldContainOptionalDateEnteredDropdown() throws IOException {
    String campaignForm = readProjectFile("src/main/webapp/pages/campaignForm.html");

    assertTrue("Date Entered select tanimli olmali",
        campaignForm.contains("id=\"campaign.colDateEntered\""));
    assertTrue("Date Entered secenekleri tip kisitiyla listelenmeli",
        campaignForm.contains("restrictByType : campaign.constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.DATE_ENTERED"));
    assertTrue("Date Entered model baglamasi olmali",
        campaignForm.contains("ng-model=\"campaign.item.colDateEntered\""));

    String dateEnteredSelectBlock = extractBlock(campaignForm, "id=\"campaign.colDateEntered\"", "</select>");
    assertFalse("Date Entered mapping'i opsiyoneldir; ng-required tasimamali",
        dateEnteredSelectBlock.contains("ng-required"));
  }

  @Test
  public void jsConstantsShouldDefineDateEnteredAllowedDateTypes() throws IOException {
    String constants = readProjectFile("src/main/webapp/js/constants.js");

    assertTrue("DATE_ENTERED varsayilan kolon adi tanimli olmali",
        constants.contains("DATE_ENTERED : 'date_entered'"));
    assertTrue("Date Entered icin tarih tip kisitlari tanimlanmali",
        constants.contains("vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.DATE_ENTERED]"));

    String dateEnteredAllowedTypes = extractBlock(constants,
        "vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.DATE_ENTERED]", ";");
    assertTrue("DATETIME tarih tipi izinli olmali (MS SQL date_entered)",
        dateEnteredAllowedTypes.contains("vm.DB_FIELD_TYPES.DATETIME"));
  }

  @Test
  public void campaignFormCtrlShouldOfferSavedDateEnteredMappingInPicklist() throws IOException {
    String controller = readProjectFile("src/main/webapp/js/controllers/campaignFormCtrl.js");

    assertTrue("Kayitli colDateEntered mapping'i result kolon listesine eklenmeli",
        controller.contains("vm.item.colDateEntered"));
  }

  @Test
  public void allLocalizationFilesShouldContainDateEnteredLabel() throws IOException {
    String[] localizationFiles = {
        "src/main/webapp/js/localization/localization_en.js",
        "src/main/webapp/js/localization/localization_tr.js",
        "src/main/webapp/js/localization/localization_ru.js",
        "src/main/webapp/js/localization/localization_ua.js"
    };
    for (String localizationFile : localizationFiles) {
      String localization = readProjectFile(localizationFile);
      assertTrue("Localization key eksik: " + localizationFile,
          localization.contains("'campaign.colDateEntered'"));
    }
  }

  private String extractBlock(String content, String startMarker, String endMarker) {
    int start = content.indexOf(startMarker);
    assertTrue("Baslangic isareti bulunamadi: " + startMarker, start >= 0);
    int end = content.indexOf(endMarker, start);
    assertTrue("Bitis isareti bulunamadi: " + endMarker, end >= 0);
    return content.substring(start, end);
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
