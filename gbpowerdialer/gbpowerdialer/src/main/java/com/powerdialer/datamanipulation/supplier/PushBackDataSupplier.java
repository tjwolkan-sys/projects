package com.powerdialer.datamanipulation.supplier;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import java.io.IOException;

import org.springframework.util.CollectionUtils;

/**
 * A <code>PushBackDataSupplier</code> adds functionality to another
 * DataSupplier, namely the ability to "push back" or "unread" one part of data.
 */
public class PushBackDataSupplier<T> extends DataSupplier<T> {

  private Queue<T> pushbackBuffer = new LinkedBlockingQueue<>();

  private final DataSupplier<T> delegateDataSupplier;

  public PushBackDataSupplier(DataSupplier<T> delegateDataSupplier) {
    this.delegateDataSupplier = delegateDataSupplier;
  }

  @Override
  protected T getNextObjectInternal() throws IOException {
    if (!CollectionUtils.isEmpty(pushbackBuffer)) {
      return pushbackBuffer.poll();
    }
    return delegateDataSupplier.getNextObjectInternal();
  }

  public void unread(T data) {
    pushbackBuffer.add(data);
    readCount--;
  }
}
