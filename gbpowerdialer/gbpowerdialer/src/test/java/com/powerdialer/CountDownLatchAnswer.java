package com.powerdialer;

import java.util.concurrent.CountDownLatch;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Answer with CountDownLatch which count down on answer. This answer might be
 * used to ged rid of Thread.sleep() in test methods
 */
public class CountDownLatchAnswer implements Answer<Object> {

  private final CountDownLatch latch;
  private final Object response;

  public CountDownLatchAnswer(CountDownLatch latch, Object response) {
    super();
    this.latch = latch;
    this.response = response;
  }

  @Override
  public Object answer(InvocationOnMock invocation) throws Throwable {
    latch.countDown();
    return response;
  }

}
