package tests.dialer;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.json.JSONObject;
import org.junit.Test;

import com.powerdialer.engine.dialer.CallAttemptPolicy;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.util.Constants;

/**
 * Task 327734: Per-cycle round-robin stratejisinde origination attempt'i saymadığı için
 * attempt, başarılı kapanışta tamamlanır. Belirsiz Kayıt (trc 85) da müşteriyle görüşülmüş
 * bir çağrı olduğundan başarılı kapanışla aynı şekilde attempt'ten sayılmalıdır.
 */
public class CallAttemptPolicyAmbiguousTrcAttemptTest {

  private static final Long AMBIGUOUS_TRC_ID = 85L;
  private static final Long ANSWERED_NO_MT_TRC_ID = 84L;

  @Test
  public void shouldCountAttemptForAmbiguousTrcOnPerCycleStrategy() {
    Call call = perCycleCallWithTrc(AMBIGUOUS_TRC_ID);

    CallAttemptPolicy.incrementForSuccessfulCompletionIfNeeded(call);

    assertThat("Belirsiz Kayıt (85) per-cycle stratejide attempt'ten sayılmalı.",
        call.getAttempts(), is(1));
  }

  @Test
  public void shouldNotCountAttemptForNonSuccessTrcOnPerCycleStrategy() {
    Call call = perCycleCallWithTrc(ANSWERED_NO_MT_TRC_ID);

    CallAttemptPolicy.incrementForSuccessfulCompletionIfNeeded(call);

    // getAttempts raw data'dan tembel başlatır; artırılmamış değer 0'dır (null değil).
    assertThat("Başarısız telephony result code attempt'ten sayılmamalı.",
        call.getAttempts(), is(0));
  }

  private Call perCycleCallWithTrc(Long trcId) {
    Campaign campaign = new Campaign();
    campaign.setId(73L);
    campaign.setRetryStrategy(Constants.RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER);
    campaign.setColResultAttempts("attempts");
    campaign.getPhoneNumberColumns().add(enabledColumn("phone1"));
    campaign.getPhoneNumberColumns().add(enabledColumn("phone2"));

    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();
    telephonyResultCode.setId(trcId);
    telephonyResultCode.setCode(trcId.intValue());

    Call call = new Call();
    call.setCampaign(campaign);
    call.setRawData(new JSONObject().put("phone1", "5551000").put("phone2", "5552000"));
    call.setTelephonyResultCode(telephonyResultCode);
    return call;
  }

  private PhoneNumberColumn enabledColumn(String colNumber) {
    PhoneNumberColumn column = new PhoneNumberColumn();
    column.setColNumber(colNumber);
    column.setEnabled(true);
    return column;
  }
}
