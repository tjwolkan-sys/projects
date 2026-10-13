package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Test;

import com.powerdialer.model.Call;
import com.powerdialer.util.Constants;

/**
 * FEAT-325470 Faz 3: yeni AMD turevli sonuclar (113 bad number, 114 not reached, 115 unknown
 * tone) hangup yolunda 900 (AMD) ile birebir ayni uc muameleyi gorur. Karar noktalari id 71'e
 * degil, code tabanli {@link Constants#AMD_FAMILY_TELEPHONY_RC_CODES} kumesine baglidir.
 *
 * <p>Neden onemli: preview kampanyada bu sonuclar awaiting-URC'ye duserse agent saatlerce kilitli
 * kalir; cevaplanmis-ama-agent'siz cagrida TRC 84'e cevrilirse yeni kodlar raporda hic
 * gorunmez ve bad-number kurali tetiklenmez.</p>
 */
public class AmdFamilyHangupFeatureTest {

  private static final int[] NEW_AMD_FAMILY_CODES = {
      Constants.TELEPHONY_RC_AMD_BAD_NUMBER,
      Constants.TELEPHONY_RC_AMD_NOT_REACHED,
      Constants.TELEPHONY_RC_AMD_UNKNOWN_TONE };

  @Test
  public void amdFamilyHangupInPreviewCampaignFinalizesWithoutWaitingForUserResultCode() {
    for (int code : NEW_AMD_FAMILY_CODES) {
      AmdHangupTestHarness harness = new AmdHangupTestHarness();
      Call call = AmdHangupTestHarness.previewCallWaitingForUserResultCode("dl-amd-family-preview-" + code);

      harness.hangup(call, code);

      verify(harness.dbOperations).insertCallResultToDB(call);
      assertThat("code " + code + " finalize", call.isBusinessResultFinalized(), is(true));
      assertThat("code " + code + " awaiting", harness.isAwaitingUserResultCode(call), is(false));
      assertThat(call.getTelephonyResultCode().getCode(), is(code));
    }
  }

  @Test
  public void amdFamilyHangupIsNotOverriddenToCustomerHangupBeforeAgentConnection() {
    for (int code : NEW_AMD_FAMILY_CODES) {
      AmdHangupTestHarness harness = new AmdHangupTestHarness();
      Call call = AmdHangupTestHarness.answeredCallWithoutAgentHandling("dl-amd-family-answered-" + code,
          Constants.PREDICTIVE_STRATEGY.GAMMA);

      harness.hangup(call, code);

      assertThat("code " + code + " persisted TRC", harness.lastPersistedCdr().getTelephonyRC().getCode(), is(code));
      assertThat("code " + code + " call TRC", call.getTelephonyResultCode().getCode(), is(code));
    }
  }

  @Test
  public void answeredAmdFamilyHangupIsReportedAsFailedCallToAlphaBoostManager() {
    for (int code : NEW_AMD_FAMILY_CODES) {
      AmdHangupTestHarness harness = new AmdHangupTestHarness();
      Call call = AmdHangupTestHarness.answeredCallWithoutAgentHandling("dl-amd-family-alpha-" + code,
          Constants.PREDICTIVE_STRATEGY.ALPHA);

      harness.hangup(call, code);

      verify(harness.dynamicBoostManager).onCallFailed(call);
    }
  }

  @Test
  public void userBusyKeepsItsNonAmdBehaviourNextToTheNewCodes() {
    AmdHangupTestHarness preview = new AmdHangupTestHarness();
    Call previewCall = AmdHangupTestHarness.previewCallWaitingForUserResultCode("dl-busy-preview-next-to-family");
    preview.hangup(previewCall, AmdHangupTestHarness.USER_BUSY_CODE);
    verify(preview.dbOperations, never()).insertCallResultToDB(previewCall);
    assertThat(preview.isAwaitingUserResultCode(previewCall), is(true));

    AmdHangupTestHarness answered = new AmdHangupTestHarness();
    Call answeredCall = AmdHangupTestHarness.answeredCallWithoutAgentHandling("dl-busy-answered-next-to-family",
        Constants.PREDICTIVE_STRATEGY.ALPHA);
    answered.hangup(answeredCall, AmdHangupTestHarness.USER_BUSY_CODE);
    assertThat(answered.lastPersistedCdr().getTelephonyRC().getCode(), is(Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT));
    verify(answered.dynamicBoostManager, never()).onCallFailed(answeredCall);
  }

}
