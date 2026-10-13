package com.powerdialer.engine.dialer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.powerdialer.model.Call;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * Abstract class that holds common objects/actions for producer and consumer
 */
public abstract class CallProducerConsumerTemplate implements RunnableAction {

  protected final ScheduledExecutorService scheduledExecutorService;
  protected ScheduledFuture scheduledFuture;
  protected BlockingQueue<Call> dataQueue;
  protected CampaignContext campaignContext;

  protected abstract void calculateCampaignParameters();

  public abstract void setRowFetchedFlagsToNull();

  protected abstract LoggerDecorator getLog();

  public CallProducerConsumerTemplate(CampaignContext campaignContext) {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true)
        .setNameFormat("producer-consumer-executor-%d")
        .build();
    scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
    this.campaignContext = campaignContext;
  }

  public BlockingQueue<Call> getDataQueue() {
    return dataQueue;
  }

  public CampaignContext getCampaignContext() {
    return campaignContext;
  }

  /**
   * Start execution of this class
   */
  public final void start() {
    Thread thread = new Thread(this);
    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Cancel execution of scheduledFuture, let existing task to finish
   */
  public final void pause() {
    scheduledFuture.cancel(false);
  }

  /**
   * Shutdown scheduled service and set flags for fetched rows back to null
   */
  public final void stop() {
    List<Runnable> runnables = scheduledExecutorService.shutdownNow();
    setRowFetchedFlagsToNull();
    calculateCampaignParameters();
    if (runnables.size() > 1) {
      LoggerDecorator log = getLog();
      log.warn("Executor for " + campaignContext.getCampaign() + " is shutdown but there are " + runnables.size() + " tasks awaiting execution");
    }
  }

  /**
   * Convert given blockingQueue data to list
   * @return list of calls
   */
  protected List<Call> convertFromBlockingQueueToList(BlockingQueue<Call> blockingQueue) {
    List<Call> calls = new ArrayList<>();
    Call call = blockingQueue.poll();
    while (call != null) {
      calls.add(call);
      call = blockingQueue.poll();
    }
    return calls;
  }

  protected int cleanQueue(BlockingQueue<Call> queue, int callsToRemove, int queueLimit) {
    List<Call> callList = new ArrayList<>(queue);
    Collections.reverse(callList);
    int deletedCallsCount = 0;
    int callIndexFresh = 0;
    while (queue.size() + callsToRemove > queueLimit && callList.size() - 1 > callIndexFresh) {
      deletedCallsCount += offerRemoveCall(queue, callList.get(callIndexFresh), Constants.CALL_TYPES.FRESH);
      callIndexFresh++;
    }
    int callIndexRetry = 0;
    while (queue.size() + callsToRemove > queueLimit && callList.size() - 1 > callIndexRetry) {
      deletedCallsCount += offerRemoveCall(queue, callList.get(callIndexRetry), Constants.CALL_TYPES.RETRY);
      callIndexRetry++;
    }
    return deletedCallsCount;
  }

  private int offerRemoveCall(BlockingQueue<Call> queue, Call call, Constants.CALL_TYPES callType) {
    boolean isRemoved = false;
    if (call.getCallType().equals(callType)) {
      isRemoved = queue.remove(call);
      try {
        if (isRemoved)
          campaignContext.getDialerFacade().getCampaignDbOperations().removeFetchedDataFlagFromCall(call);
      } catch (SQLException ex) {
        ex.printStackTrace();
      }
    }
    return isRemoved ? 1 : 0;
  }

}
