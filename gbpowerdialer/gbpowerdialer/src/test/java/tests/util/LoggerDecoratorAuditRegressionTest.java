package tests.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

import com.powerdialer.model.ActionLog;
import com.powerdialer.service.ActionLogService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * Task 325469: LSC hot path INFO loglari iki senkron logback appender'inin
 * (FILE + CONSOLE) CachingDateFormatter kilidinde konvoy yaratti. Operasyon
 * ActionLog ekranini kullandigi icin ActionLog kaydi korunur; audit() ActionLog'u
 * INFO seviyesiyle kaydeder ama slf4j'ye debug yazar (uretimde appender'a girmez).
 */
@RunWith(MockitoJUnitRunner.class)
public class LoggerDecoratorAuditRegressionTest {

  @Mock
  private Logger slf4jLogger;

  @Mock
  private ActionLogService actionLogService;

  @Test
  public void auditWithArgumentsWritesActionLogAsInfoButLogsAsDebug() {
    LoggerDecorator decorator = new LoggerDecorator(slf4jLogger, actionLogService);

    decorator.audit("LSC URL: http://x", Constants.LOG_MODULE.LSC);

    ArgumentCaptor<ActionLog> captor = ArgumentCaptor.forClass(ActionLog.class);
    verify(actionLogService).addActionLog(captor.capture());
    assertEquals("LSC URL: http://x", captor.getValue().getLog());
    assertEquals(Constants.LOG_LEVEL.INFO, captor.getValue().getLogLevel());
    assertEquals(Constants.LOG_MODULE.LSC, captor.getValue().getLogModule());
    verify(slf4jLogger).debug("LSC URL: http://x");
    verify(slf4jLogger, never()).info("LSC URL: http://x");
  }

  @Test
  public void auditWithoutArgumentsSkipsActionLogLikeInfoDoes() {
    LoggerDecorator decorator = new LoggerDecorator(slf4jLogger, actionLogService);

    decorator.audit("sadece dosya izi");

    verifyNoInteractions(actionLogService);
    verify(slf4jLogger).debug("sadece dosya izi");
  }
}
