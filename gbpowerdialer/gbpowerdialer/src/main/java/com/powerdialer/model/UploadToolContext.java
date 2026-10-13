package com.powerdialer.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import com.powerdialer.request.StartUploadParameterRequest;

/**
 * Class for storing upload options per Campaign. Instance of this class is
 * created while upload start initiated
 */
public class UploadToolContext {

  private final Campaign campaign;
  private StartUploadParameterRequest startParameters;
  private String startUploadTempFilename;
  private Integer totalRowsCount;
  private Integer processedRowsCount;
  private Integer failedRowsCount;
  private Future<?> uploadFuture;
  private String failedRowsFilenameLink;
  private List<Map> rowsForPreview;

  public UploadToolContext(Campaign campaign) {
    this.campaign = campaign;
  }

  public Campaign getCampaign() {
    return campaign;
  }

  public StartUploadParameterRequest getStartParameters() {
    return startParameters;
  }

  public void setStartParameters(StartUploadParameterRequest startParameters) {
    this.startParameters = startParameters;
  }

  public String getStartUploadTempFilename() {
    return startUploadTempFilename;
  }

  public void setStartUploadTempFilename(String startUploadTempFilename) {
    this.startUploadTempFilename = startUploadTempFilename;
  }

  public Integer getTotalRowsCount() {
    return totalRowsCount;
  }

  public void setTotalRowsCount(Integer totalRowsCount) {
    this.totalRowsCount = totalRowsCount;
  }

  public void incrementProcessedRows() {
    if (processedRowsCount == null) {
      processedRowsCount = 1;
    } else {
      processedRowsCount++;
    }
  }

  public Integer getProcessedRowsCount() {
    return processedRowsCount;
  }

  public Integer getFailedRowsCount() {
    return failedRowsCount;
  }

  public void setFailedRowsCount(Integer failedRowsCount) {
    this.failedRowsCount = failedRowsCount;
  }

  public Future<?> getUploadFuture() {
    return uploadFuture;
  }

  public void setUploadFuture(Future<?> uploadFuture) {
    this.uploadFuture = uploadFuture;
  }

  public String getFailedRowsFilenameLink() {
    return failedRowsFilenameLink;
  }

  public void setFailedRowsFilenameLink(String failedRowsFilenameLink) {
    this.failedRowsFilenameLink = failedRowsFilenameLink;
  }

  public List<Map> getRowsForPreview() {
    return rowsForPreview;
  }

  public void setRowsForPreview(List<Map> rowsForPreview) {
    this.rowsForPreview = rowsForPreview;
  }
}
