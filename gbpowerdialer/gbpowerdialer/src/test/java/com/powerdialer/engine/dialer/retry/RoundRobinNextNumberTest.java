package com.powerdialer.engine.dialer.retry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

public class RoundRobinNextNumberTest {

  private final Call call = spy(new Call());
  private final Campaign campaign = mock(Campaign.class);
  private final JSONObject rawData = mock(JSONObject.class);
  private final CallCleaner callCleaner = mock(CallCleaner.class);
  private final CallOriginator callOriginator = mock(CallOriginator.class);
  private final List<PhoneNumberColumn> numberColumnsInRawData = new ArrayList<>();
  private final RoundRobinNextNumber retryStrategy = new RoundRobinNextNumber();

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

    when(campaign.getEnabledPhoneNumberColumns()).thenReturn(numberColumnsInRawData);
    when(campaign.getColResultRetryHistory()).thenReturn("retry_history");
    when(campaign.getRetryOveralAttempt()).thenReturn(4);
    when(campaign.getMacroMaxAttempt()).thenReturn(1);

    when(call.getCallOriginator()).thenReturn(callOriginator);
    when(callOriginator.getCallCleaner()).thenReturn(callCleaner);

    call.setCampaign(campaign);
    call.setRawData(rawData);
    call.setCalledNumber("number_1");
  }

  @Test
  public void testOverallRetryLimitIsReached() {
    when(rawData.getString("retry_history")).thenReturn("number_1;number_2");

    retryStrategy.createRetry(call);

    assertFalse(retryStrategy.isOverallRetryLimitReached());
  }

  @Test
  public void testRetryNumber() {
    when(rawData.getString("retry_history")).thenReturn("number_1");

    retryStrategy.createRetry(call);

    assertEquals("number_2", call.getRetryNumber());
  }

}
