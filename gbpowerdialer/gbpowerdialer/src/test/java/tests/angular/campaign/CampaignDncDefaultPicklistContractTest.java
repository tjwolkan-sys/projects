package tests.angular.campaign;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class CampaignDncDefaultPicklistContractTest {

  @Test
  public void campaignDncPicklistShouldLockDefaultDncItems() throws IOException {
    String campaignForm = readProjectFile("src/main/webapp/pages/campaignForm.html");

    assertTrue(campaignForm.contains("id=\"campaign.dncs\""));
    assertTrue(campaignForm.contains("data-pick-locked=\"campaign.isDefaultDncItem(item)\""));
  }

  @Test
  public void picklistDirectiveShouldKeepLockedItemsSelected() throws IOException {
    String picklist = readProjectFile("src/main/webapp/js/util/picklist.js");

    assertTrue(picklist.contains("$scope.isPickItemLocked"));
    assertTrue(picklist.contains("lockedDestoptions"));
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
