package tests.angular.campaign;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class CampaignDataManipulationNullFilterContractTest {

  @Test
  public void filterOperatorDropdownContainsNullCheckOptionsAndDisablesValueInput() throws IOException {
    String template = readProjectFile("src/main/webapp/pages/campaignDataManipulationAndUpdate.jsp");

    assertTrue(template.contains("<option value=\"ISNULL\">IS NULL</option>"));
    assertTrue(template.contains("<option value=\"ISNOTNULL\">IS NOT NULL</option>"));
    assertTrue(template.contains("ng-disabled=\"ctrl.isNullCondition(f.condition)\""));
  }

  @Test
  public void controllerUsesConditionContractForNullFilterPayload() throws IOException {
    String controller = readProjectFile("src/main/webapp/js/controllers/campaignDataManipulationAndUpdateCtrl.js");

    assertTrue(controller.contains("ctrl.isNullCondition = function (condition)"));
    assertTrue(controller.contains("condition === 'ISNULL' || condition === 'ISNOTNULL'"));
    assertTrue(controller.contains("ctrl.filterRows.push({ field: '', condition: 'EQUAL', value: '' });"));
    assertTrue(controller.contains("value: ctrl.isNullCondition(f.condition) ? null : f.value"));
    assertFalse(controller.contains("f.operator === 'IS_NULL'"));
    assertFalse(controller.contains("f.operator === 'IS_NOT_NULL'"));
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
