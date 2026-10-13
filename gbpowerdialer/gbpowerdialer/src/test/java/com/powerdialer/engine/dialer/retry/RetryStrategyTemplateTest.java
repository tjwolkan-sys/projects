package com.powerdialer.engine.dialer.retry;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Sets;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.ResultCode;
import com.powerdialer.model.ResultCodeSchedule;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.APPLY_TO;

@RunWith(MockitoJUnitRunner.class)
public class RetryStrategyTemplateTest {

  @Spy
  private Call call;

  @Mock
  private CallOriginator callOriginator;

  @Mock
  private CallCleaner callCleaner;

  @Before
  public void setUp() {
    when(call.getCallOriginator()).thenReturn(callOriginator);
    when(callOriginator.getCallCleaner()).thenReturn(callCleaner);
  }

  @Test
  public void givenCallWithReachRetryPerNumberWhenNumberAddedToDNCThenTakeNext() {
    call = createDumbCall();
    call.setCalledNumber("FIRST_PHONE_NUMBER");
    call.setRawData(new JSONObject("{'phone_number_1' : 'FIRST_PHONE_NUMBER', 'phone_number_2' : 'SECOND_PHONE_NUMBER'}"));
    call.setDNCNumbers(Sets.newHashSet("FIRST_PHONE_NUMBER"));

    RetryStrategy retryStrategyTemplate = new ReachRetryPerNumber();
    retryStrategyTemplate.createRetry(call);
    assertThat(call.getRetryNumber(), is(equalTo("SECOND_PHONE_NUMBER")));
  }

  @Test
  public void givenCallWithReachRetrySingleNumberWhenNumberAddedToDNCThenComplete() {
    call = createDumbCall();
    call.setCalledNumber("FIRST_PHONE_NUMBER");
    call.setRawData(new JSONObject("{'phone_number_1' : 'FIRST_PHONE_NUMBER'}"));
    call.setDNCNumbers(Sets.newHashSet("FIRST_PHONE_NUMBER"));

    RetryStrategy retryStrategyTemplate = new ReachRetrySingleNumber();
    retryStrategyTemplate.createRetry(call);
    verify(callCleaner).completeCall(call);
  }

  @Test
  public void givenCallWithRetryRulesCycleWhenNumberAddedToDNCThenTakeNext() {
    call = createDumbCall();
    call.setCalledNumber("FIRST_PHONE_NUMBER");
    call.setRawData(new JSONObject("{'phone_number_1' : 'FIRST_PHONE_NUMBER', 'phone_number_2' : 'SECOND_PHONE_NUMBER', 'retry_history' : 'FIRST_PHONE_NUMBER'}"));
    call.setDNCNumbers(Sets.newHashSet("FIRST_PHONE_NUMBER"));
    call.setResultCodeSequence(1);

    RetryStrategy retryStrategyTemplate = new RetryRulesCycle();
    retryStrategyTemplate.createRetry(call);
    assertThat(call.getRetryNumber(), is(equalTo("SECOND_PHONE_NUMBER")));
  }

  @Test
  public void givenCallWithRoundRobinNextNumberWhenNumberAddedToDNCThenTakeNext() {
    call = createDumbCall();
    call.setCalledNumber("SECOND_PHONE_NUMBER");
    call.setRawData(new JSONObject("{'phone_number_1' : 'FIRST_PHONE_NUMBER', 'phone_number_2' : 'SECOND_PHONE_NUMBER', 'retry_history' : 'FIRST_PHONE_NUMBER;SECOND_PHONE_NUMBER'}"));
    call.setDNCNumbers(Sets.newHashSet("FIRST_PHONE_NUMBER"));

    RetryStrategy retryStrategyTemplate = new RoundRobinNextNumber();
    retryStrategyTemplate.createRetry(call);
    assertThat(call.getRetryNumber(), is(equalTo("SECOND_PHONE_NUMBER")));
  }

  @Test
  public void givenCallWithRoundRobinPerCycleWhenNumberAddedToDNCThenTakeNext() {
    call = createDumbCall();
    call.setCalledNumber("SECOND_PHONE_NUMBER");
    call.setRawData(new JSONObject("{'phone_number_1' : 'FIRST_PHONE_NUMBER', 'phone_number_2' : 'SECOND_PHONE_NUMBER', 'retry_history' : 'FIRST_PHONE_NUMBER;SECOND_PHONE_NUMBER'}"));
    call.setDNCNumbers(Sets.newHashSet("FIRST_PHONE_NUMBER"));

    RetryStrategy retryStrategyTemplate = new RoundRobinPerCycle();
    retryStrategyTemplate.createRetry(call);
    assertThat(call.getRetryNumber(), is(equalTo("SECOND_PHONE_NUMBER")));
  }

  private Call createDumbCall() {
    call.setId("dl-callId");
    call.setCampaign(createDumbCampaign());
    call.setUserResultCode(createDumbResultCode());
    call.setTelephonyResultCode(createDumbTelephonyResultCode());
    return call;
  }

  private Campaign createDumbCampaign() {
    Campaign campaign = new Campaign();
    campaign.setRetryOveralAttempt(4);
    campaign.setMacroInterval(1);
    campaign.setRetryDelay(10D);
    campaign.setMacroMaxAttempt(2);
    campaign.setPhoneNumberColumns(createDumbPhoneNumberColumns());
    campaign.setColResultDNCNumbers("dnc_numbers");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setResultCodeSchedules(Arrays.asList(createDumbResultCodeSchedule()));
    return campaign;
  }

  private ResultCodeSchedule createDumbResultCodeSchedule() {
    ResultCodeSchedule resultCodeSchedule = new ResultCodeSchedule();
    resultCodeSchedule.setRetryAmount(1);
    resultCodeSchedule.setEnabled(true);
    resultCodeSchedule.setApplyTo(APPLY_TO.RETRIES_AND_CALLBACKS);
    resultCodeSchedule.setRetryInterval(10D);
    resultCodeSchedule.setTelephonyResultCode(createDumbTelephonyResultCode());
    resultCodeSchedule.setMacroCycle(true);
    return resultCodeSchedule;
  }

  private List<PhoneNumberColumn> createDumbPhoneNumberColumns() {
    PhoneNumberColumn numberColumn = new PhoneNumberColumn();
    numberColumn.setEnabled(true);
    numberColumn.setColNumber("phone_number_1");

    PhoneNumberColumn numberColumn2 = new PhoneNumberColumn();
    numberColumn2.setEnabled(true);
    numberColumn2.setColNumber("phone_number_2");

    return Arrays.asList(numberColumn, numberColumn2);
  }

  private ResultCode createDumbResultCode() {
    ResultCode resultCode = new ResultCode();
    resultCode.setDncIdentifier(true);
    resultCode.setId(1L);
    return resultCode;
  }

  private TelephonyResultCode createDumbTelephonyResultCode() {
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();
    telephonyResultCode.setCode(Constants.TELEPHONY_RC_NORMAL_CLEARING);
    telephonyResultCode.setId(1L);
    return telephonyResultCode;
  }

}
