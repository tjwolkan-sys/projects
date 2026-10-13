package tests.angular.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.powerdialer.util.Constants;

/**
 * Dialing options ekranındaki {@code mohClassCallingParty} alanının campaign type contract'ı.
 *
 * <p>Alan, dialer müşteriyi önce arayıp sonra agent'ı bağladığı tiplerde anlamlıdır; calling party
 * agent beklerken MOH duyar. Bu akış PROGRESSIVE_RESERVED(0) ve PROGRESSIVE_RELEASED(1) yanında
 * PREDICTIVE(5) için de geçerlidir. Kilitlenen sözleşme: alan yalnız bu üç type'ta enabled'dır,
 * PREVIEW_FREE(2), PREVIEW_STRAIGHT(3) ve OUTBOUND_IVR(4) için disabled kalır ve
 * {@code EditCampaign} permission guard'ı korunur.
 */
public class CampaignMohClassPredictiveEnabledContractTest {

  private static final String CAMPAIGN_FORM = "src/main/webapp/pages/campaignForm.html";

  @Test
  public void predictiveCampaignTypeShouldStayOrdinalFive() {
    // UI numerik type değeri entity ordinal'ine bağlıdır; enum sırası değişirse bu test önce patlar.
    assertEquals("PREDICTIVE ordinal'i 5 olmalı", 5, Constants.CAMPAIGN_TYPE.PREDICTIVE.ordinal());
    assertEquals("PROGRESSIVE_RESERVED ordinal'i 0 olmalı", 0,
        Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED.ordinal());
    assertEquals("PROGRESSIVE_RELEASED ordinal'i 1 olmalı", 1,
        Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED.ordinal());
  }

  @Test
  public void mohClassInputShouldBeEnabledForProgressiveAndPredictiveTypes() throws IOException {
    String ngDisabled = readMohClassNgDisabledExpression();

    Set<Integer> enabledTypes = extractComparedCampaignTypes(ngDisabled);

    Set<Integer> expected = new TreeSet<Integer>();
    expected.add(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED.ordinal());
    expected.add(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED.ordinal());
    expected.add(Constants.CAMPAIGN_TYPE.PREDICTIVE.ordinal());

    assertEquals("mohClass alanı yalnız progressive ve predictive type'larda enabled olmalı",
        expected, new TreeSet<Integer>(enabledTypes));
  }

  @Test
  public void mohClassInputShouldKeepEditCampaignPermissionGuard() throws IOException {
    String ngDisabled = readMohClassNgDisabledExpression();

    assertTrue("EditCampaign permission guard'ı korunmalı",
        ngDisabled.contains("!canEditSelectedCampaignByPermission(campaign.item, 'EditCampaign')"));
  }

  /**
   * ng-disabled ifadesindeki {@code campaign.item.type != N} karşılaştırmalarını toplar.
   * Bu ifade "şu type'ların hiçbiri değilse disable et" biçiminde yazıldığı için
   * toplanan küme enabled type kümesidir.
   */
  private Set<Integer> extractComparedCampaignTypes(String ngDisabled) {
    Set<Integer> types = new LinkedHashSet<Integer>();
    Matcher matcher = Pattern.compile("campaign\\.item\\.type\\s*!==?\\s*(\\d+)").matcher(ngDisabled);
    while (matcher.find()) {
      types.add(Integer.valueOf(matcher.group(1)));
    }
    assertTrue("ng-disabled ifadesinde campaign type karşılaştırması bulunmalı", !types.isEmpty());
    return types;
  }

  private String readMohClassNgDisabledExpression() throws IOException {
    String campaignForm = readProjectFile(CAMPAIGN_FORM);

    int inputStart = campaignForm.indexOf("id=\"campaign.mohClassCallingParty\"");
    assertTrue("mohClassCallingParty input'u bulunmalı", inputStart >= 0);

    int inputEnd = campaignForm.indexOf("/>", inputStart);
    assertTrue("mohClassCallingParty input'u kapanmalı", inputEnd >= 0);

    String inputTag = campaignForm.substring(inputStart, inputEnd);
    assertTrue("Model bağlaması olmalı",
        inputTag.contains("ng-model=\"campaign.item.mohClassCallingParty\""));

    Matcher matcher = Pattern.compile("ng-disabled=\"([^\"]*)\"").matcher(inputTag);
    assertTrue("mohClassCallingParty input'unda ng-disabled bulunmalı", matcher.find());
    return matcher.group(1);
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
