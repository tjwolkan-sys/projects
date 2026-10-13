package com.powerdialer.dto;

/**
 * Monitor ekranı alt KPI şeridi. Prune ve skip sayaçları 5 dakikalık kayan pencereden hesaplanır;
 * prune sayısının artması release kaçağının sağlık sinyalidir.
 */
public class FctChannelKpiDTO {

  private int prunedLast5Min;
  private int skippedLast5Min;
  private int rejectedLast5Min;

  public int getPrunedLast5Min() {
    return prunedLast5Min;
  }

  public void setPrunedLast5Min(int prunedLast5Min) {
    this.prunedLast5Min = prunedLast5Min;
  }

  public int getSkippedLast5Min() {
    return skippedLast5Min;
  }

  public void setSkippedLast5Min(int skippedLast5Min) {
    this.skippedLast5Min = skippedLast5Min;
  }

  public int getRejectedLast5Min() {
    return rejectedLast5Min;
  }

  public void setRejectedLast5Min(int rejectedLast5Min) {
    this.rejectedLast5Min = rejectedLast5Min;
  }
}
