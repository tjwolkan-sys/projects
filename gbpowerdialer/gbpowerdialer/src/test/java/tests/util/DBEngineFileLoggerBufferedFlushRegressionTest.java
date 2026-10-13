package tests.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.junit.Test;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;
import org.slf4j.LoggerFactory;

import com.powerdialer.aop.DBEngineFileLogger;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.service.ActionLogService;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.LoggerDecorator;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.rolling.RollingFileAppender;

/**
 * Task 325469 (A4-2): Kampanya SQL loglari her query'de senkron flush ile
 * yaziliyordu (query basina fsync/syscall). Bu test kampanya appender'inin
 * buffered (immediateFlush=false) kurulmasini kilitler; dosya icerigi ve
 * formati degismez, yalniz OS buffer'i uzerinden yazilir.
 */
public class DBEngineFileLoggerBufferedFlushRegressionTest {

  @Test
  public void campaignQueryFileAppenderIsConfiguredWithBufferedFlush() throws Exception {
    LoggerDecorator decorator = new LoggerDecorator(
        LoggerFactory.getLogger(DBEngineFileLoggerBufferedFlushRegressionTest.class),
        Mockito.mock(ActionLogService.class));
    DBEngineFileLogger fileLogger = new DBEngineFileLogger(decorator,
        Mockito.mock(DialerFacade.class), Mockito.mock(CampaignService.class));
    Whitebox.setInternalState(fileLogger, "filePath", System.getProperty("java.io.tmpdir") + File.separator);
    Whitebox.setInternalState(fileLogger, "fileSuffix", ".%d{yyyy-MM-dd}.log.zip");
    Whitebox.setInternalState(fileLogger, "layoutPattern", "%msg%n");

    Logger campaignLogger = Whitebox.invokeMethod(fileLogger, "configureLogger", 999888L, "perf-test-flush-999888");
    try {
      RollingFileAppender<?> appender = (RollingFileAppender<?>) campaignLogger.getAppender("perf-test-flush-999888");
      assertNotNull("Kampanya appender'i kurulmus olmali", appender);
      assertFalse("Kampanya SQL log appender'i buffered yazmali (immediateFlush=false)",
          appender.isImmediateFlush());
    } finally {
      campaignLogger.detachAndStopAllAppenders();
    }
  }
}
