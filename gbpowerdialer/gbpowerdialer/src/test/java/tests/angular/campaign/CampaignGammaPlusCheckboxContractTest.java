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
 * Gamma Plus checkbox'ının frontend + persistence contract'ı.
 *
 * <p>Kilitlenen sözleşme: checkbox yalnız süperadmin'e görünür, yalnız gamma bloğunun içindedir,
 * dört dilde etiketi vardır, DTO ↔ entity isim bazlı Dozer eşleşmesi için iki tarafta da
 * {@code gammaPlus} adıyla durur ve DB kolonu {@code gamma_plus}'tır.
 *
 * <p>Ayrıca migration script'inin doğru tabloyu hedeflediği doğrulanır: kampanya tablosunun adı
 * {@code pd_campaign}'dir ({@code @Table} anotasyonu), repodaki eski migration şablonundaki
 * {@code ALTER TABLE campaign} satırı kopyalanmamalıdır.
 */
public class CampaignGammaPlusCheckboxContractTest {

  @Test
  public void campaignFormShouldContainGammaPlusCheckbox() throws IOException {
    String campaignForm = readProjectFile("src/main/webapp/pages/campaignForm.html");

    assertTrue("Gamma Plus checkbox tanımlı olmalı",
        campaignForm.contains("id=\"campaign.gammaPlus\""));
    assertTrue("Model bağlaması olmalı",
        campaignForm.contains("ng-model=\"campaign.item.gammaPlus\""));

    String block = extractBlock(campaignForm, "id=\"campaign.gammaPlus\"", "/>");
    assertTrue("Checkbox tipinde olmalı", block.contains("type=\"checkbox\""));
    assertFalse("Bayrak opsiyoneldir; ng-required taşımamalı", block.contains("ng-required"));
  }

  @Test
  public void gammaPlusCheckboxShouldBeVisibleOnlyToSuperAdmin() throws IOException {
    String campaignForm = readProjectFile("src/main/webapp/pages/campaignForm.html");

    int guard = campaignForm.indexOf("hasAnyRole(['ROLE_SUPERADMIN'])");
    assertTrue("ROLE_SUPERADMIN guard'ı bulunmalı", guard >= 0);

    int checkbox = campaignForm.indexOf("id=\"campaign.gammaPlus\"");
    assertTrue("Checkbox guard'dan SONRA gelmeli (guard'ın kapsamında)", checkbox > guard);
    assertTrue("Guard checkbox'a yakın olmalı; baska bir blogun guard'i sayilmamali",
        checkbox - guard < 600);
  }

  @Test
  public void gammaPlusCheckboxShouldLiveInsideTheGammaStrategyBlock() throws IOException {
    // Bayrak yalnız gamma stratejisi seçiliyken anlamlı; alpha/beta ekranında görünmemeli.
    String campaignForm = readProjectFile("src/main/webapp/pages/campaignForm.html");

    int gammaBlock = campaignForm.indexOf("campaign.item.predictiveStrategy === 2");
    int checkbox = campaignForm.indexOf("id=\"campaign.gammaPlus\"");

    assertTrue("Gamma bloğu bulunmalı", gammaBlock >= 0);
    assertTrue("Checkbox gamma bloğunun içinde olmalı", checkbox > gammaBlock);
  }

  @Test
  public void allLocalizationFilesShouldContainGammaPlusLabel() throws IOException {
    String[] localizationFiles = {
        "src/main/webapp/js/localization/localization_en.js",
        "src/main/webapp/js/localization/localization_tr.js",
        "src/main/webapp/js/localization/localization_ru.js",
        "src/main/webapp/js/localization/localization_ua.js"
    };
    for (String localizationFile : localizationFiles) {
      String localization = readProjectFile(localizationFile);
      assertTrue("Localization key eksik: " + localizationFile,
          localization.contains("'campaign.gammaPlus'"));
    }
  }

  @Test
  public void formDtoAndEntityShouldExposeGammaPlusUnderTheSameName() throws IOException {
    // Dozer isim bazlı otomatik map'liyor; iki tarafta da aynı property adı şart.
    String dto = readProjectFile("src/main/java/com/powerdialer/dto/CampaignFormDTO.java");
    String entity = readProjectFile("src/main/java/com/powerdialer/model/Campaign.java");

    assertTrue("DTO getter'ı getGammaPlus olmalı", dto.contains("public Boolean getGammaPlus()"));
    assertTrue("DTO setter'ı olmalı", dto.contains("public void setGammaPlus(Boolean gammaPlus)"));
    assertTrue("Entity getter'ı getGammaPlus olmalı", entity.contains("public Boolean getGammaPlus()"));
    assertTrue("Entity setter'ı olmalı", entity.contains("public void setGammaPlus(Boolean gammaPlus)"));
  }

  @Test
  public void entityShouldMapGammaPlusColumnAndDefaultToFalse() throws IOException {
    String entity = readProjectFile("src/main/java/com/powerdialer/model/Campaign.java");

    assertTrue("Kolon eşlemesi gamma_plus olmalı", entity.contains("@Column(name = \"gamma_plus\""));
    assertTrue("NOT NULL kolon için alan başlatması şart; yoksa yeni kampanya insert'i patlar",
        entity.contains("private Boolean gammaPlus = Boolean.FALSE;"));
    assertTrue("Savunmacı okuma helper'ı bulunmalı",
        entity.contains("public boolean isGammaPlusEnabled()"));
    assertTrue("Null daima kapalı sayılmalı",
        entity.contains("Boolean.TRUE.equals(getGammaPlus())"));
  }

  @Test
  public void entityTableNameShouldStayPdCampaign() throws IOException {
    // DB script'i repoda tutulmuyor (elle çalıştırılmak üzere ayrıca teslim edildi).
    // Kolonun ekleneceği tablonun adı buradan doğrulanır: script `pd_campaign` hedeflemelidir.
    String entity = readProjectFile("src/main/java/com/powerdialer/model/Campaign.java");

    assertTrue("Kampanya tablosu pd_campaign olmalı", entity.contains("@Table(name = \"pd_campaign\")"));
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
