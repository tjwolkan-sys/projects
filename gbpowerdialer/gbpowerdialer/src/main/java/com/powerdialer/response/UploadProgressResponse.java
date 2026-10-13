package com.powerdialer.response;

public class UploadProgressResponse {

  private Integer processed;

  private Integer total;

  private Integer failed;

  private String failedRowsFilenameLink;

  public Integer getProcessed() {
    return processed;
  }

  public void setProcessed(Integer processed) {
    this.processed = processed;
  }

  public Integer getTotal() {
    return total;
  }

  public void setTotal(Integer total) {
    this.total = total;
  }

  public Integer getFailed() {
    return failed;
  }

  public void setFailed(Integer failed) {
    this.failed = failed;
  }

  public String getFailedRowsFilenameLink() {
    return failedRowsFilenameLink;
  }

  public void setFailedRowsFilenameLink(String failedRowsFilenameLink) {
    this.failedRowsFilenameLink = failedRowsFilenameLink;
  }

}
