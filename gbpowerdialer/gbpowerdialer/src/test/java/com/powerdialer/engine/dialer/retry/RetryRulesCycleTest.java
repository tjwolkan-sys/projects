package com.powerdialer.engine.dialer.retry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.ResultCode;
import com.powerdialer.model.ResultCodeSchedule;
import com.powerdialer.model.ResultCodeScheduleAdvanced;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.APPLY_TO;
import com.powerdialer.util.Constants.NUMBER_TO_TRY;

/**
 * Functional tests for RetryRulesCycle
 */
public class RetryRulesCycleTest {

  private Call call = spy(new Call());
  private CallCleaner callCleaner = mock(CallCleaner.class);
  private CallOriginator callOriginator = mock(CallOriginator.class);
  private RetryRulesCycle retryRulesCycle = new RetryRulesCycle();

  /**
   * Init mocks
   */
  @Before
  public void init() {
    JSONObject rawData = mock(JSONObject.class);
    when(rawData.getString("called_number")).thenReturn("number_1");
    when(rawData.getInt("rc_sequence")).thenReturn(1);
    when(rawData.has("retry_history")).thenReturn(true);
    when(rawData.has("rc_sequence")).thenReturn(true);

    List<PhoneNumberColumn> numberColumnsInRawData = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
      phoneNumberColumn.setColNumber("phone_number_" + (i + 1));
      numberColumnsInRawData.add(phoneNumberColumn);
      when(rawData.getString("phone_number_" + (i + 1))).thenReturn("number_" + (i + 1));
      when(rawData.has("phone_number_" + (i + 1))).thenReturn(true);
    }

    Campaign campaign = mock(Campaign.class);
    when(campaign.getEnabledPhoneNumberColumns()).thenReturn(numberColumnsInRawData);
    when(campaign.getColResultRetryHistory()).thenReturn("retry_history");
    when(campaign.getColResultRcSequence()).thenReturn("rc_sequence");
    when(campaign.getRetryOveralAttempt()).thenReturn(4);
    when(campaign.getMacroMaxAttempt()).thenReturn(4);

    when(call.getCallOriginator()).thenReturn(callOriginator);
    when(callOriginator.getCallCleaner()).thenReturn(callCleaner);

    call.setCampaign(campaign);
    call.setRawData(rawData);
    call.setCalledNumber("number_1");
    call.setRetryHistory(new ArrayList<>(Arrays.asList("number_1")));
  }

  /**
   * Test creating retry for call with a resultCode provided from user
   */
  @Test
  public void shouldCreateRetryUsingUserResultCode() {
    ResultCode userResultCode = new ResultCode();

    ResultCodeSchedule resultCodeSchedule = new ResultCodeSchedule();
    resultCodeSchedule.setEnabled(true);
    resultCodeSchedule.setRetryAmount(1);
    resultCodeSchedule.setResultCode(userResultCode);
    resultCodeSchedule.setApplyTo(APPLY_TO.RETRIES);

    when(call.getCampaign().getResultCodeSchedules()).thenReturn(Arrays.asList(resultCodeSchedule));
    when(call.getUserResultCode()).thenReturn(userResultCode);

    retryRulesCycle.createRetry(call);
    assertNotNull(call.getRetryNumber());
    assertNotNull(call.getRetryDate());
  }

  /**
   * Test creating retry for call with a telephony result code
   */
  @Test
  public void shouldCreateRetryUsingTelephonyResultCode() {
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();
    telephonyResultCode.setCode(16);
    telephonyResultCode.setName("Normal Clearing");

    ResultCodeSchedule resultCodeSchedule = new ResultCodeSchedule();
    resultCodeSchedule.setEnabled(true);
    resultCodeSchedule.setRetryAmount(1);
    resultCodeSchedule.setTelephonyResultCode(telephonyResultCode);
    resultCodeSchedule.setApplyTo(APPLY_TO.RETRIES);

    when(call.getCampaign().getResultCodeSchedules()).thenReturn(Arrays.asList(resultCodeSchedule));
    when(call.getTelephonyResultCode()).thenReturn(telephonyResultCode);

    retryRulesCycle.createRetry(call);
    assertNotNull(call.getRetryNumber());
    assertNotNull(call.getRetryDate());
  }

  /**
   * Test creating retry for call without user or telephony result code
   */
  @Test
  public void shouldCreateRetryWithoutResultCodeSchedule() {
    retryRulesCycle.createRetry(call);
    assertNotNull(call.getRetryNumber());
    assertNotNull(call.getRetryDate());
  }

  /**
   * Test setting complete date for a succesfull call
   */
  @Test
  public void shouldSetCompleteDateForSuccesfullCall() {
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();
    telephonyResultCode.setCode(Constants.TELEPHONY_RC_NORMAL_CLEARING);

    when(call.getTelephonyResultCode()).thenReturn(telephonyResultCode);

    retryRulesCycle.createRetry(call);
    verify(callCleaner).completeCall(call);
  }

  /**
   * Test creating retry with result code schedule advanced using next number
   */
  @Test
  public void shouldCreateRetryUsingNextNumber() {
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();

    ResultCodeScheduleAdvanced resultCodeScheduleAdvanced = new ResultCodeScheduleAdvanced();
    resultCodeScheduleAdvanced.setEnabled(true);
    resultCodeScheduleAdvanced.setDialAttempt(1);
    resultCodeScheduleAdvanced.setNumberToTry(NUMBER_TO_TRY.NEXT);

    ResultCodeSchedule resultCodeSchedule = new ResultCodeSchedule();
    resultCodeSchedule.setEnabled(true);
    resultCodeSchedule.setTelephonyResultCode(telephonyResultCode);
    resultCodeSchedule.setApplyTo(APPLY_TO.RETRIES);
    resultCodeSchedule.setRetryAmount(1);
    resultCodeSchedule.setAdvancedIntervalEnabled(true);
    resultCodeSchedule.setResultCodeScheduleAdvanceds(Arrays.asList(resultCodeScheduleAdvanced));

    when(call.getCampaign().getResultCodeSchedules()).thenReturn(Arrays.asList(resultCodeSchedule));
    when(call.getTelephonyResultCode()).thenReturn(telephonyResultCode);

    retryRulesCycle.createRetry(call);
    assertEquals("number_2", call.getRetryNumber());
    assertNotNull(call.getRetryDate());
  }

  /**
   * Test creating retry with result code schedule advanced using previous
   * number
   */
  @Test
  public void shouldCreateRetryUsingPreviousNumber() {
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();

    ResultCodeScheduleAdvanced resultCodeScheduleAdvanced = new ResultCodeScheduleAdvanced();
    resultCodeScheduleAdvanced.setEnabled(true);
    resultCodeScheduleAdvanced.setDialAttempt(1);
    resultCodeScheduleAdvanced.setNumberToTry(NUMBER_TO_TRY.PREVIOUS);

    ResultCodeSchedule resultCodeSchedule = new ResultCodeSchedule();
    resultCodeSchedule.setEnabled(true);
    resultCodeSchedule.setTelephonyResultCode(telephonyResultCode);
    resultCodeSchedule.setApplyTo(APPLY_TO.RETRIES_AND_CALLBACKS);
    resultCodeSchedule.setRetryAmount(1);
    resultCodeSchedule.setAdvancedIntervalEnabled(true);
    resultCodeSchedule.setResultCodeScheduleAdvanceds(Arrays.asList(resultCodeScheduleAdvanced));

    when(call.getCampaign().getResultCodeSchedules()).thenReturn(Arrays.asList(resultCodeSchedule));
    when(call.getTelephonyResultCode()).thenReturn(telephonyResultCode);
    call.setCalledNumber("number_2");

    retryRulesCycle.createRetry(call);
    assertEquals("number_1", call.getRetryNumber());
    assertNotNull(call.getRetryDate());
  }

}
