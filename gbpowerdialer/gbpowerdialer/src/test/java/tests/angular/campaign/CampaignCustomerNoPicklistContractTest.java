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
 * Call Results tab'indeki opsiyonel "Customer No" kolon mapping dropdown'unun
 * frontend contract'ı: template select'i + tip kısıtlı restrictByType, JS
 * constants tanımları, controller init listesi, dört dildeki localization
 * key'i ve Karma bundle kaydı birlikte kilitlenir (FEAT-318332).
 */
public class CampaignCustomerNoPicklistContractTest {

  @Test
  public void campaignFormShouldContainOptionalCustomerNoDropdown() throws IOException {
    String campaignForm = readProjectFile("src/main/webapp/pages/campaignForm.html");

    assertTrue("Customer No select tanımlı olmalı",
        campaignForm.contains("id=\"campaign.colResultCustomerNo\""));
    assertTrue("Customer No seçenekleri tip kısıtıyla listelenmeli",
        campaignForm.contains("restrictByType : campaign.constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CUSTOMER_NO"));
    assertTrue("Customer No model bağlaması olmalı",
        campaignForm.contains("ng-model=\"campaign.item.colResultCustomerNo\""));

    String customerNoSelectBlock = extractBlock(campaignForm, "id=\"campaign.colResultCustomerNo\"", "</select>");
    assertFalse("Customer No mapping'i opsiyoneldir; ng-required taşımamalı",
        customerNoSelectBlock.contains("ng-required"));
  }

  @Test
  public void jsConstantsShouldDefineCustomerNoColumnAndAllowedTypes() throws IOException {
    String constants = readProjectFile("src/main/webapp/js/constants.js");

    assertTrue(constants.contains("CUSTOMER_NO : 'customer_no'"));
    assertTrue(constants.contains("DATE_ENTERED : 'date_entered'"));
    assertTrue("Customer No için string tip kısıtları tanımlanmalı",
        constants.contains("vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.CUSTOMER_NO]"));
  }

  @Test
  public void campaignFormCtrlShouldOfferSavedCustomerNoMappingInPicklist() throws IOException {
    String controller = readProjectFile("src/main/webapp/js/controllers/campaignFormCtrl.js");

    assertTrue("Kayıtlı colResultCustomerNo mapping'i result kolon listesine eklenmeli",
        controller.contains("vm.item.colResultCustomerNo"));
  }

  @Test
  public void allLocalizationFilesShouldContainCustomerNoLabel() throws IOException {
    String[] localizationFiles = {
        "src/main/webapp/js/localization/localization_en.js",
        "src/main/webapp/js/localization/localization_tr.js",
        "src/main/webapp/js/localization/localization_ru.js",
        "src/main/webapp/js/localization/localization_ua.js"
    };
    for (String localizationFile : localizationFiles) {
      String localization = readProjectFile(localizationFile);
      assertTrue("Localization key eksik: " + localizationFile,
          localization.contains("'campaign.colResultCustomerNo'"));
    }
  }

  @Test
  public void karmaConfigShouldBundleConstantsServiceForSpecs() throws IOException {
    String karmaConfig = readProjectFile("karma.conf.js");

    assertTrue("constants.js Karma bundle'ına eklenmeli (constants.spec.js koşulabilsin)",
        karmaConfig.contains("src/main/webapp/js/constants.js"));
  }

  private String extractBlock(String content, String startMarker, String endMarker) {
    int start = content.indexOf(startMarker);
    assertTrue("Başlangıç işareti bulunamadı: " + startMarker, start >= 0);
    int end = content.indexOf(endMarker, start);
    assertTrue("Bitiş işareti bulunamadı: " + endMarker, end >= 0);
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
