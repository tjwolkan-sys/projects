package com.powerdialer;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Mockito {@link Answer} with delay before returning answer. Can be used for
 * mocking slow methods
 */
public class DelayedAnswer<T> implements Answer<T> {

  private final T objectToReturn;
  private final int delay;

  public DelayedAnswer(T objectToReturn,
                       int delay) {
    this.objectToReturn = objectToReturn;
    this.delay = delay;
  }

  @Override
  public T answer(InvocationOnMock invocation) throws Throwable {
    Thread.sleep(delay);
    return objectToReturn;
  }
}
