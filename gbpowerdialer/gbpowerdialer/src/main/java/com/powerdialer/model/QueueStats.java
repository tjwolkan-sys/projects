package com.powerdialer.model;

/**
 * Holder for queue related statistics
 */
public class QueueStats {

  private String queueName;

  private int abandonedCallsCount;
  private int availableForCallUsersCount;
  private int callsFinishedByAgentCount;
  private int callsFinishedByCustomerCount;
  private double clientsInQueueSum;
  private int handledCallsCount;
  private int handledCallsInSLCount;
  private double inQueueTimeAvg;
  private double inQueueTimeAvgAbandon;
  private double inQueueTimeAvgCurrent;
  private double inQueueTimeMax;
  private double inQueueTimeMaxAbandon;
  private double inQueueTimeMaxCurrent;
  private double inQueueTimeMin;
  private double inQueueTimeMinAbandon;
  private double inQueueTimeMinCurrent;
  private double inQueueTimeSum;
  private double inQueueTimeSumAbandon;
  private double inQueueTimeSumCurrent;
  private int inSystemUsersCount;
  private int inSystemUsersCountWithCallAutoAccept;
  private double incallTimeAvg;
  private double incallTimeMax;
  private double incallTimeMin;
  private double incallTimeSum;
  private int incallUsersCount;
  private double onHoldTimeAvg;
  private double onHoldTimeMax;
  private double onHoldTimeMin;
  private double onHoldTimeSum;
  private int onholdUsersCount;
  private int receivedCallsCount;
  private int rejectedCallsCount;
  private double relaxTimeAvg;
  private double relaxTimeMax;
  private double relaxTimeMin;
  private double relaxTimeSum;
  private int relaxUsersCount;
  private int ringingDeclinedEventsCount;
  private int ringingIgnoredEventsCount;
  private double ringingTimeAvg;
  private double ringingTimeMax;
  private double ringingTimeMin;
  private double ringingTimeSum;
  private int ringingUsersCount;
  private int shortAbandonedCallsCount;
  private int transferredCallsCount;
  private int transferredCallsCountCurrent;
  private double transferredCallsDurationAvg;
  private double transferredCallsDurationMax;
  private double transferredCallsDurationMin;
  private double transferredCallsDurationSum;
  private double wrapupTimeAvg;
  private double wrapupTimeMax;
  private double wrapupTimeMin;
  private double wrapupTimeSum;
  private int wrapupUsersCount;

  public String getQueueName() {
    return queueName;
  }

  public void setQueueName(String queueName) {
    this.queueName = queueName;
  }

  public int getAbandonedCallsCount() {
    return abandonedCallsCount;
  }

  public void setAbandonedCallsCount(int abandonedCallsCount) {
    this.abandonedCallsCount = abandonedCallsCount;
  }

  public int getAvailableForCallUsersCount() {
    return availableForCallUsersCount;
  }

  public void setAvailableForCallUsersCount(int availableForCallUsersCount) {
    this.availableForCallUsersCount = availableForCallUsersCount;
  }

  public int getCallsFinishedByAgentCount() {
    return callsFinishedByAgentCount;
  }

  public void setCallsFinishedByAgentCount(int callsFinishedByAgentCount) {
    this.callsFinishedByAgentCount = callsFinishedByAgentCount;
  }

  public int getCallsFinishedByCustomerCount() {
    return callsFinishedByCustomerCount;
  }

  public void setCallsFinishedByCustomerCount(int callsFinishedByCustomerCount) {
    this.callsFinishedByCustomerCount = callsFinishedByCustomerCount;
  }

  public double getClientsInQueueSum() {
    return clientsInQueueSum;
  }

  public void setClientsInQueueSum(double clientsInQueueSum) {
    this.clientsInQueueSum = clientsInQueueSum;
  }

  public int getHandledCallsCount() {
    return handledCallsCount;
  }

  public void setHandledCallsCount(int handledCallsCount) {
    this.handledCallsCount = handledCallsCount;
  }

  public int getHandledCallsInSLCount() {
    return handledCallsInSLCount;
  }

  public void setHandledCallsInSLCount(int handledCallsInSLCount) {
    this.handledCallsInSLCount = handledCallsInSLCount;
  }

  public double getInQueueTimeAvg() {
    return inQueueTimeAvg;
  }

  public void setInQueueTimeAvg(double inQueueTimeAvg) {
    this.inQueueTimeAvg = inQueueTimeAvg;
  }

  public double getInQueueTimeAvgAbandon() {
    return inQueueTimeAvgAbandon;
  }

  public void setInQueueTimeAvgAbandon(double inQueueTimeAvgAbandon) {
    this.inQueueTimeAvgAbandon = inQueueTimeAvgAbandon;
  }

  public double getInQueueTimeAvgCurrent() {
    return inQueueTimeAvgCurrent;
  }

  public void setInQueueTimeAvgCurrent(double inQueueTimeAvgCurrent) {
    this.inQueueTimeAvgCurrent = inQueueTimeAvgCurrent;
  }

  public double getInQueueTimeMax() {
    return inQueueTimeMax;
  }

  public void setInQueueTimeMax(double inQueueTimeMax) {
    this.inQueueTimeMax = inQueueTimeMax;
  }

  public double getInQueueTimeMaxAbandon() {
    return inQueueTimeMaxAbandon;
  }

  public void setInQueueTimeMaxAbandon(double inQueueTimeMaxAbandon) {
    this.inQueueTimeMaxAbandon = inQueueTimeMaxAbandon;
  }

  public double getInQueueTimeMaxCurrent() {
    return inQueueTimeMaxCurrent;
  }

  public void setInQueueTimeMaxCurrent(double inQueueTimeMaxCurrent) {
    this.inQueueTimeMaxCurrent = inQueueTimeMaxCurrent;
  }

  public double getInQueueTimeMin() {
    return inQueueTimeMin;
  }

  public void setInQueueTimeMin(double inQueueTimeMin) {
    this.inQueueTimeMin = inQueueTimeMin;
  }

  public double getInQueueTimeMinAbandon() {
    return inQueueTimeMinAbandon;
  }

  public void setInQueueTimeMinAbandon(double inQueueTimeMinAbandon) {
    this.inQueueTimeMinAbandon = inQueueTimeMinAbandon;
  }

  public double getInQueueTimeMinCurrent() {
    return inQueueTimeMinCurrent;
  }

  public void setInQueueTimeMinCurrent(double inQueueTimeMinCurrent) {
    this.inQueueTimeMinCurrent = inQueueTimeMinCurrent;
  }

  public double getInQueueTimeSum() {
    return inQueueTimeSum;
  }

  public void setInQueueTimeSum(double inQueueTimeSum) {
    this.inQueueTimeSum = inQueueTimeSum;
  }

  public double getInQueueTimeSumAbandon() {
    return inQueueTimeSumAbandon;
  }

  public void setInQueueTimeSumAbandon(double inQueueTimeSumAbandon) {
    this.inQueueTimeSumAbandon = inQueueTimeSumAbandon;
  }

  public double getInQueueTimeSumCurrent() {
    return inQueueTimeSumCurrent;
  }

  public void setInQueueTimeSumCurrent(double inQueueTimeSumCurrent) {
    this.inQueueTimeSumCurrent = inQueueTimeSumCurrent;
  }

  public int getInSystemUsersCount() {
    return inSystemUsersCount;
  }

  public void setInSystemUsersCount(int inSystemUsersCount) {
    this.inSystemUsersCount = inSystemUsersCount;
  }

  public int getInSystemUsersCountWithCallAutoAccept() {
    return inSystemUsersCountWithCallAutoAccept;
  }

  public void setInSystemUsersCountWithCallAutoAccept(int inSystemUsersCountWithCallAutoAccept) {
    this.inSystemUsersCountWithCallAutoAccept = inSystemUsersCountWithCallAutoAccept;
  }

  public double getIncallTimeAvg() {
    return incallTimeAvg;
  }

  public void setIncallTimeAvg(double incallTimeAvg) {
    this.incallTimeAvg = incallTimeAvg;
  }

  public double getIncallTimeMax() {
    return incallTimeMax;
  }

  public void setIncallTimeMax(double incallTimeMax) {
    this.incallTimeMax = incallTimeMax;
  }

  public double getIncallTimeMin() {
    return incallTimeMin;
  }

  public void setIncallTimeMin(double incallTimeMin) {
    this.incallTimeMin = incallTimeMin;
  }

  public double getIncallTimeSum() {
    return incallTimeSum;
  }

  public void setIncallTimeSum(double incallTimeSum) {
    this.incallTimeSum = incallTimeSum;
  }

  public int getIncallUsersCount() {
    return incallUsersCount;
  }

  public void setIncallUsersCount(int incallUsersCount) {
    this.incallUsersCount = incallUsersCount;
  }

  public double getOnHoldTimeAvg() {
    return onHoldTimeAvg;
  }

  public void setOnHoldTimeAvg(double onHoldTimeAvg) {
    this.onHoldTimeAvg = onHoldTimeAvg;
  }

  public double getOnHoldTimeMax() {
    return onHoldTimeMax;
  }

  public void setOnHoldTimeMax(double onHoldTimeMax) {
    this.onHoldTimeMax = onHoldTimeMax;
  }

  public double getOnHoldTimeMin() {
    return onHoldTimeMin;
  }

  public void setOnHoldTimeMin(double onHoldTimeMin) {
    this.onHoldTimeMin = onHoldTimeMin;
  }

  public double getOnHoldTimeSum() {
    return onHoldTimeSum;
  }

  public void setOnHoldTimeSum(double onHoldTimeSum) {
    this.onHoldTimeSum = onHoldTimeSum;
  }

  public int getOnholdUsersCount() {
    return onholdUsersCount;
  }

  public void setOnholdUsersCount(int onholdUsersCount) {
    this.onholdUsersCount = onholdUsersCount;
  }

  public int getReceivedCallsCount() {
    return receivedCallsCount;
  }

  public void setReceivedCallsCount(int receivedCallsCount) {
    this.receivedCallsCount = receivedCallsCount;
  }

  public int getRejectedCallsCount() {
    return rejectedCallsCount;
  }

  public void setRejectedCallsCount(int rejectedCallsCount) {
    this.rejectedCallsCount = rejectedCallsCount;
  }

  public double getRelaxTimeAvg() {
    return relaxTimeAvg;
  }

  public void setRelaxTimeAvg(double relaxTimeAvg) {
    this.relaxTimeAvg = relaxTimeAvg;
  }

  public double getRelaxTimeMax() {
    return relaxTimeMax;
  }

  public void setRelaxTimeMax(double relaxTimeMax) {
    this.relaxTimeMax = relaxTimeMax;
  }

  public double getRelaxTimeMin() {
    return relaxTimeMin;
  }

  public void setRelaxTimeMin(double relaxTimeMin) {
    this.relaxTimeMin = relaxTimeMin;
  }

  public double getRelaxTimeSum() {
    return relaxTimeSum;
  }

  public void setRelaxTimeSum(double relaxTimeSum) {
    this.relaxTimeSum = relaxTimeSum;
  }

  public int getRelaxUsersCount() {
    return relaxUsersCount;
  }

  public void setRelaxUsersCount(int relaxUsersCount) {
    this.relaxUsersCount = relaxUsersCount;
  }

  public int getRingingDeclinedEventsCount() {
    return ringingDeclinedEventsCount;
  }

  public void setRingingDeclinedEventsCount(int ringingDeclinedEventsCount) {
    this.ringingDeclinedEventsCount = ringingDeclinedEventsCount;
  }

  public int getRingingIgnoredEventsCount() {
    return ringingIgnoredEventsCount;
  }

  public void setRingingIgnoredEventsCount(int ringingIgnoredEventsCount) {
    this.ringingIgnoredEventsCount = ringingIgnoredEventsCount;
  }

  public double getRingingTimeAvg() {
    return ringingTimeAvg;
  }

  public void setRingingTimeAvg(double ringingTimeAvg) {
    this.ringingTimeAvg = ringingTimeAvg;
  }

  public double getRingingTimeMax() {
    return ringingTimeMax;
  }

  public void setRingingTimeMax(double ringingTimeMax) {
    this.ringingTimeMax = ringingTimeMax;
  }

  public double getRingingTimeMin() {
    return ringingTimeMin;
  }

  public void setRingingTimeMin(double ringingTimeMin) {
    this.ringingTimeMin = ringingTimeMin;
  }

  public double getRingingTimeSum() {
    return ringingTimeSum;
  }

  public void setRingingTimeSum(double ringingTimeSum) {
    this.ringingTimeSum = ringingTimeSum;
  }

  public int getRingingUsersCount() {
    return ringingUsersCount;
  }

  public void setRingingUsersCount(int ringingUsersCount) {
    this.ringingUsersCount = ringingUsersCount;
  }

  public int getShortAbandonedCallsCount() {
    return shortAbandonedCallsCount;
  }

  public void setShortAbandonedCallsCount(int shortAbandonedCallsCount) {
    this.shortAbandonedCallsCount = shortAbandonedCallsCount;
  }

  public int getTransferredCallsCount() {
    return transferredCallsCount;
  }

  public void setTransferredCallsCount(int transferredCallsCount) {
    this.transferredCallsCount = transferredCallsCount;
  }

  public int getTransferredCallsCountCurrent() {
    return transferredCallsCountCurrent;
  }

  public void setTransferredCallsCountCurrent(int transferredCallsCountCurrent) {
    this.transferredCallsCountCurrent = transferredCallsCountCurrent;
  }

  public double getTransferredCallsDurationAvg() {
    return transferredCallsDurationAvg;
  }

  public void setTransferredCallsDurationAvg(double transferredCallsDurationAvg) {
    this.transferredCallsDurationAvg = transferredCallsDurationAvg;
  }

  public double getTransferredCallsDurationMax() {
    return transferredCallsDurationMax;
  }

  public void setTransferredCallsDurationMax(double transferredCallsDurationMax) {
    this.transferredCallsDurationMax = transferredCallsDurationMax;
  }

  public double getTransferredCallsDurationMin() {
    return transferredCallsDurationMin;
  }

  public void setTransferredCallsDurationMin(double transferredCallsDurationMin) {
    this.transferredCallsDurationMin = transferredCallsDurationMin;
  }

  public double getTransferredCallsDurationSum() {
    return transferredCallsDurationSum;
  }

  public void setTransferredCallsDurationSum(double transferredCallsDurationSum) {
    this.transferredCallsDurationSum = transferredCallsDurationSum;
  }

  public double getWrapupTimeAvg() {
    return wrapupTimeAvg;
  }

  public void setWrapupTimeAvg(double wrapupTimeAvg) {
    this.wrapupTimeAvg = wrapupTimeAvg;
  }

  public double getWrapupTimeMax() {
    return wrapupTimeMax;
  }

  public void setWrapupTimeMax(double wrapupTimeMax) {
    this.wrapupTimeMax = wrapupTimeMax;
  }

  public double getWrapupTimeMin() {
    return wrapupTimeMin;
  }

  public void setWrapupTimeMin(double wrapupTimeMin) {
    this.wrapupTimeMin = wrapupTimeMin;
  }

  public double getWrapupTimeSum() {
    return wrapupTimeSum;
  }

  public void setWrapupTimeSum(double wrapupTimeSum) {
    this.wrapupTimeSum = wrapupTimeSum;
  }

  public int getWrapupUsersCount() {
    return wrapupUsersCount;
  }

  public void setWrapupUsersCount(int wrapupUsersCount) {
    this.wrapupUsersCount = wrapupUsersCount;
  }

  @Override
  public String toString() {
    return "QueueStats [queueName="
        + queueName + "]";
  }

}

