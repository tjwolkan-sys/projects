package com.powerdialer.engine.dialer.retry;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class RetryLimitTest {

  private final Call call = spy(new Call());
  private final Campaign campaign = mock(Campaign.class);
  private final CallCleaner callCleaner = mock(CallCleaner.class);
  private final CallOriginator callOriginator = mock(CallOriginator.class);
  private final JSONObject rawData = mock(JSONObject.class);
  private final List<PhoneNumberColumn> numberColumnsInRawData = new ArrayList<>();

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
    when(campaign.getMacroMaxAttempt()).thenReturn(4);

    when(call.getCallOriginator()).thenReturn(callOriginator);
    when(callOriginator.getCallCleaner()).thenReturn(callCleaner);

    call.setCampaign(campaign);
    call.setRawData(rawData);
    call.setCalledNumber("number_1");
  }

  @Test
  public void testOverallRetryLimitIsReachedReachRetryPerNumber() {
    when(rawData.getString("retry_history")).thenReturn("number_1;number_1;number_1;number_1;number_2;number_2;number_2");

    ReachRetryPerNumber retryStrategy = new ReachRetryPerNumber();
    retryStrategy.createRetry(call);

    assertTrue(retryStrategy.isOverallRetryLimitReached());
  }

  @Test
  public void testOverallRetryLimitIsReachedRoundRobinNextCycle() {
    when(rawData.getString("retry_history")).thenReturn("number_1;number_1;number_1;number_1;number_2;number_2;number_2");

    RoundRobinNextCycle retryStrategy = new RoundRobinNextCycle();
    retryStrategy.createRetry(call);

    assertTrue(retryStrategy.isOverallRetryLimitReached());
  }

  @Test
  public void testOverallRetryLimitIsReachedRoundRobinNextNumber() {
    when(rawData.getString("retry_history")).thenReturn("number_1;number_1;number_1;number_1;number_2;number_2;number_2");

    RoundRobinNextNumber retryStrategy = new RoundRobinNextNumber();
    retryStrategy.createRetry(call);

    assertTrue(retryStrategy.isOverallRetryLimitReached());
  }

  @Test
  public void testOverallRetryLimitIsReachedRoundRobinPerCycyle() {
    when(rawData.getString("retry_history"))
        .thenReturn("number_1;number_1;number_1;number_1;number_2;number_2;number_2;number_2;number_1;number_1;number_1;number_1;number_2;number_2;number_2");

    RoundRobinPerCycle retryStrategy = new RoundRobinPerCycle();
    retryStrategy.createRetry(call);

    assertTrue(retryStrategy.isOverallRetryLimitReached());
  }

}
