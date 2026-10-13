package com.powerdialer.service;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.util.LoggerDecorator;

@RunWith(MockitoJUnitRunner.class)
public class UploadToolFileServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private LoggerDecorator loggerDecorator;

  @InjectMocks
  private UploadToolFileService uploadToolFileService;

  @Test
  public void givenFilenameWhenGenerateFileAccessLinkThenPathVariableReplaced() {
    String link = uploadToolFileService.generateFileAccessLink("filename.csv");
    Assert.assertEquals("campaign/upload/failedRows/filename.csv", link);
  }

}