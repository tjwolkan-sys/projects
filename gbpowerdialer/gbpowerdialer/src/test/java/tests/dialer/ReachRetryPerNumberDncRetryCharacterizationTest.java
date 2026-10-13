package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.retry.ReachRetryPerNumber;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;

/**
 * 03.08.2026 webphone-event sonsuz dongu vakasi oncesi mevcut SAGLAM davranisi
 * kilitler: legacy guard'in DNC farkindaligi, countAllCallTypes global attempt
 * limiti ve DNC atlama yuruyusunun numara secim sirasi fix oncesi/sonrasi ayni
 * kalmalidir.
 */
public class ReachRetryPerNumberDncRetryCharacterizationTest {

  private Call call = spy(new Call());
  private Campaign campaign = mock(Campaign.class);
  private JSONObject rawData = mock(JSONObject.class);
  private CallCleaner callCleaner = mock(CallCleaner.class);
  private CallOriginator callOriginator = mock(CallOriginator.class);
  private List<PhoneNumberColumn> numberColumns = new ArrayList<>();
  private ReachRetryPerNumber strategy = new ReachRetryPerNumber();

  @Before
  public void setup() {
    when(campaign.getColResultRetryHistory()).thenReturn("retry_history");
    when(campaign.getEnabledPhoneNumberColumns()).thenReturn(numberColumns);
    when(campaign.getRetryOveralAttempt()).thenReturn(2);
    when(campaign.getMacroMaxAttempt()).thenReturn(5);

    when(call.getCallOriginator()).thenReturn(callOriginator);
    when(callOriginator.getCallCleaner()).thenReturn(callCleaner);

    call.setCampaign(campaign);
    call.setRawData(rawData);
  }

  private void addPhoneColumn(String columnName, String phoneNumber) {
    PhoneNumberColumn column = new PhoneNumberColumn();
    column.setColNumber(columnName);
    numberColumns.add(column);
    when(rawData.has(columnName)).thenReturn(true);
    when(rawData.getString(columnName)).thenReturn(phoneNumber);
  }

  private void setRetryHistory(String history) {
    when(rawData.has("retry_history")).thenReturn(true);
    when(rawData.getString("retry_history")).thenReturn(history);
  }

  @Test
  public void legacyGuardCompletesCallWhenAllNumbersAreInDnc() {
    addPhoneColumn("phone_number_1", "number_1");
    addPhoneColumn("phone_number_2", "number_2");
    call.setCalledNumber("number_1");
    call.setDNCNumbers(new HashSet<>(Arrays.asList("number_1", "number_2")));

    strategy.createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
  }

  @Test
  public void legacyGuardSchedulesRetryToDncFreeNumber() {
    addPhoneColumn("phone_number_1", "number_1");
    addPhoneColumn("phone_number_2", "number_2");
    call.setCalledNumber("number_1");
    call.setDNCNumbers(new HashSet<>(Arrays.asList("number_1")));
    setRetryHistory("number_1");

    strategy.createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
    assertNotNull(call.getRetryDate());
  }

  @Test
  public void countAllCallTypesCompletesCallWhenGlobalAttemptLimitReached() {
    addPhoneColumn("phone_number_1", "number_1");
    call.setCalledNumber("number_1");
    when(campaign.getCountAllCallTypes()).thenReturn(true);
    doReturn(2).when(call).getAttempts();

    strategy.createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
  }

  @Test
  public void countAllCallTypesKeepsPerNumberRotationWhenEligibleNumberExists() {
    addPhoneColumn("phone_number_1", "number_1");
    addPhoneColumn("phone_number_2", "number_2");
    call.setCalledNumber("number_1");
    when(campaign.getCountAllCallTypes()).thenReturn(true);
    doReturn(1).when(call).getAttempts();
    setRetryHistory("number_1;number_1");

    strategy.createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
    assertNotNull(call.getRetryDate());
  }

  @Test
  public void dncSkipWalkLandsOnFirstEligibleNumberInColumnOrder() {
    addPhoneColumn("phone_number_1", "number_1");
    addPhoneColumn("phone_number_2", "number_2");
    addPhoneColumn("phone_number_3", "number_3");
    call.setCalledNumber("number_1");
    call.setDNCNumbers(new HashSet<>(Arrays.asList("number_1", "number_2")));

    strategy.createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_3", call.getRetryNumber());
    assertTrue(call.getRetryHistory().contains("number_3"));
  }

}
