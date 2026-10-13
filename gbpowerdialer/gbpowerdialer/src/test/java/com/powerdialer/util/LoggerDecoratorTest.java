package com.powerdialer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;

import java.lang.reflect.InvocationTargetException;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import com.powerdialer.model.ActionLog;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.service.ActionLogService;
import com.powerdialer.util.Constants.LOG_MODULE;

/**
 * Tests for LoggerDecorator class
 */
public class LoggerDecoratorTest {

  /**
   * Test if logger is created properly
   */
  @Test
  public void testLoggerIsNotNull() {
    LoggerDecorator loggerDecorator = new LoggerDecorator(LoggerFactory.getLogger(Campaign.class), mock(ActionLogService.class));
    LoggerDecorator log = loggerDecorator.getLogger();
    assertNotNull(log);
  }

  /**
   * Test if exception is thrown if there is no logger
   */
  @Test(expected = IllegalArgumentException.class)
  public void testLoggerIsNull() {
    LoggerDecorator loggerDecorator = new LoggerDecorator();
    loggerDecorator.getLogger();
  }

  /**
   * Test if info logged properly with arguments
   * @throws InvocationTargetException
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   * @throws SecurityException
   * @throws NoSuchMethodException
   */
  @Test
  public void testLoggerWithArguments() {
    ActionLogService actionLogService = mock(ActionLogService.class);

    LoggerDecorator loggerDecorator = new LoggerDecorator(LoggerFactory.getLogger(Campaign.class), actionLogService);
    LoggerDecorator log = loggerDecorator.getLogger();

    Tenant tenant = new Tenant();
    tenant.setName("Test Tenant");

    Campaign campaign = new Campaign();
    campaign.setName("Test Campaign");
    campaign.setTenant(tenant);

    User user = new User();
    user.setUsername("Test Username");
    user.setTenant(tenant);

    log.info("Campaign is started", campaign, user, LOG_MODULE.CAMPAIGN);

    ArgumentCaptor<ActionLog> captor = ArgumentCaptor.forClass(ActionLog.class);
    verify(actionLogService).addActionLog(captor.capture());

    ActionLog actionLog = captor.getValue();
    assertNotNull(actionLog);
    assertEquals(campaign, actionLog.getCampaign());
    assertEquals(tenant,   actionLog.getTenant());
    assertEquals(user,     actionLog.getUser());
    assertEquals(LOG_MODULE.CAMPAIGN, actionLog.getLogModule());

    verifyNoMoreInteractions(actionLogService);
  }

}
