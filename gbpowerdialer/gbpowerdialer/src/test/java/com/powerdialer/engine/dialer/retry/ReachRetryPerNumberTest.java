package com.powerdialer.engine.dialer.retry;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;

public class ReachRetryPerNumberTest {

  private Call call = spy(new Call());
  private Campaign campaign = mock(Campaign.class);
  private JSONObject rawData = mock(JSONObject.class);
  private CallCleaner callCleaner = mock(CallCleaner.class);
  private CallOriginator callOriginator = mock(CallOriginator.class);
  private List<PhoneNumberColumn> numberColumnsInRawData = new ArrayList<>();
  private ReachRetryPerNumber reachRetryPerNumber = new ReachRetryPerNumber();

  @Before
  public void setup() {
    for (int i = 0; i < 2; i++) {
      PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
      phoneNumberColumn.setColNumber("phone_number_" + (i + 1));
      numberColumnsInRawData.add(phoneNumberColumn);
      when(rawData.has("phone_number_" + (i + 1))).thenReturn(true);
    }

    when(rawData.getString("called_number")).thenReturn("number_1");
    when(rawData.getString("phone_number_1")).thenReturn("number_1");
    when(rawData.getString("phone_number_2")).thenReturn("number_2");
    when(rawData.has("retry_history")).thenReturn(true);
    when(rawData.has("number_1")).thenReturn(true);

    when(campaign.getColResultRetryHistory()).thenReturn("retry_history");
    when(campaign.getEnabledPhoneNumberColumns()).thenReturn(numberColumnsInRawData);
    when(campaign.getRetryOveralAttempt()).thenReturn(2);
    when(campaign.getMacroMaxAttempt()).thenReturn(2);

    when(call.getCallOriginator()).thenReturn(callOriginator);
    when(callOriginator.getCallCleaner()).thenReturn(callCleaner);

    call.setCampaign(campaign);
    call.setRawData(rawData);
    call.setCalledNumber("number_1");
  }

  @Test
  public void testOverallRetryLimitIsReached() {
    when(rawData.getString("retry_history")).thenReturn("number_1;number_1;number_2;number_2");
    reachRetryPerNumber.createRetry(call);
    assertEquals(true, reachRetryPerNumber.isOverallRetryLimitReached());
  }

  @Test
  public void testRetryNumber() {
    when(rawData.getString("retry_history")).thenReturn("number_1;number_1");
    reachRetryPerNumber.createRetry(call);
    assertEquals("number_2", call.getRetryNumber());
  }

  @Test
  public void testRetryLimitInMacroIsReached() {
    when(rawData.getString("retry_history")).thenReturn("number_1;number_1;number_2");
    reachRetryPerNumber.createRetry(call);
    assertEquals(true, reachRetryPerNumber.isRetryLimitInMacroReached());
  }

}
