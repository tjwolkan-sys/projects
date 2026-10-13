package tests.angular.forms;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class DncDateInputsContractTest {

  @Test
  public void dncPhoneModalExpireInputShouldStayReadonlyAndPickerDriven() throws IOException {
    String dncPhoneTemplate = readProjectFile("src/main/webapp/pages/dncPhone.html");

    assertTrue(dncPhoneTemplate.contains("id=\"dnc.phoneExpiredDate\""));
    assertTrue(dncPhoneTemplate.contains("id=\"dnc.phoneExpiredDate\" name=\"dncPhoneExpiredDate\" type=\"text\" class=\"form-control input-sm\" ng-model=\"dncphone.item.expiredDate\" ng-format-date=\"dd/MM/yyyy HH:mm\" readonly"));
  }

  @Test
  public void dncFormExpireInputShouldStayReadonlyAndPickerDriven() throws IOException {
    String dncFormTemplate = readProjectFile("src/main/webapp/pages/dncForm.html");

    assertTrue(dncFormTemplate.contains("id=\"dnc.expireDate\""));
    assertTrue(dncFormTemplate.contains("id=\"dnc.expireDate\" name=\"dncExpireDate\" type=\"text\" class=\"form-control input-sm\" data-ng-model=\"dnc.item.expireDate\" ng-format-date=\"dd/MM/yyyy HH:mm\" readonly"));
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
