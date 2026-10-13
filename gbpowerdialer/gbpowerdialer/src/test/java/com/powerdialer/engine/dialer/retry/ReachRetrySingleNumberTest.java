package com.powerdialer.engine.dialer.retry;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;

@RunWith(MockitoJUnitRunner.class)
public class ReachRetrySingleNumberTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Call call;

  @Mock
  private Campaign campaign;

  @InjectMocks
  private ReachRetrySingleNumber reachRetrySingleNumber;

  @Before
  public void init() {
    lenient().when(campaign.getColResultRetryHistory()).thenReturn("retry_history");
    lenient().when(campaign.getRetryOveralAttempt()).thenReturn(4);
  }

  @Test
  public void shouldReachOveralRetryLimit() {
    when(call.getRetryHistory().size()).thenReturn(4);
    assertTrue(reachRetrySingleNumber.isOverallRetryLimitReached());
  }

  @Test
  public void shouldNotReachOveralRetryLimit() {
    when(call.getRetryHistory().size()).thenReturn(3);
    assertFalse(reachRetrySingleNumber.isOverallRetryLimitReached());
  }

  @Test
  public void shouldSetRetryNumber() {
    String calledNumber = "number_1";
    when(call.getCalledNumber()).thenReturn(calledNumber);
    reachRetrySingleNumber.setRetryNumber();
    verify(call).setRetryNumber(calledNumber);
  }

}
