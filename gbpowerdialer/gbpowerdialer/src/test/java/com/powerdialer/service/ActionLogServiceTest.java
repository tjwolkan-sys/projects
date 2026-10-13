package com.powerdialer.service;

import static org.junit.Assert.assertEquals;

import org.dozer.Mapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.reflect.Whitebox;

import com.powerdialer.dao.impl.ActionLogDao;
import com.powerdialer.model.ActionLog;
import com.powerdialer.model.Tenant;

/**
 * Tests for ActionLogService class
 */
public class ActionLogServiceTest {

  @InjectMocks
  private ActionLogService actionLogService;

  @Mock
  private ActionLogDao actionLogDao;

  /**
   * Setup mock objects
   */
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Whitebox.setInternalState(actionLogService, "mapper", Mockito.mock(Mapper.class));
  }

  /**
   * Test if actionLog is added to cachedActionLogs and cachedActionLogsByTenant
   */
  @Test
  public void testAddActionLog() {
    ActionLog actionLog = Mockito.mock(ActionLog.class);
    Mockito.when(actionLog.getTenant()).thenReturn(Mockito.mock(Tenant.class));
    actionLogService.addActionLog(actionLog);

    assertEquals(1, actionLogService.getCachedActionLogs().size());
    assertEquals(1, actionLogService.getCacheQueueByTenantId(actionLog.getTenant().getId()).size());
  }

}
