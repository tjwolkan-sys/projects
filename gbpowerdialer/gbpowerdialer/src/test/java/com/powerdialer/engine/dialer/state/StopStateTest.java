package com.powerdialer.engine.dialer.state;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

import java.sql.SQLException;
import java.time.LocalDateTime;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;

import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.LSCEngine;
import com.powerdialer.engine.StopConditionChecker;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.boost.DynamicBoostManager;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CallConsumer;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.CallProducer;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.exceptions.CampaignIllegalStateException;
import com.powerdialer.exceptions.DateMismatchException;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignMemento;
import com.powerdialer.model.Location;
import com.powerdialer.model.RoutingGroup;
import com.powerdialer.model.Schedule;
import com.powerdialer.model.Server;
import com.powerdialer.model.User;
import com.powerdialer.service.CampaignMementoService;
import com.powerdialer.service.CampaignResultJobService;
import com.powerdialer.service.RoutingGroupService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.CAMPAIGN_STATE;
import com.powerdialer.util.Constants.DAY_OF_WEEK;
import com.powerdialer.util.LoggerDecorator;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Tests for StopState class
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ LocalDateTime.class, StopState.class })
public class StopStateTest {

  @Mock
  private CampaignContext campaignContext;

  @Mock
  private DialerFacade dialerFacade;

  @Mock
  private RoutingGroupService routingGroupService;

  @Mock
  private LoggerDecorator loggerDecorator;

  @Mock
  private Campaign campaign;

  @Mock
  private CampaignMementoService campaignMementoService;

  @Mock
  private CampaignResultJobService campaignResultJobService;

  @Mock
  private CampaignRealTimeRingingManager campaignRealTimeRingingManager;

  @Mock
  private DynamicBoostManager dynamicBoostManager;

  @Mock
  private ACDEngine acdEngine;

  @Mock
  private CampaignDbOperations campaignDbOperations;

  private CampaignStateTemplate stopState;

  @Before
  public void init() {
    when(campaign.isEnabled()).thenReturn(true);
    when(campaign.getStartDate()).thenReturn(new DateTime().minusHours(1).toDate());
    when(campaign.getEndDate()).thenReturn(new DateTime().plusHours(1).toDate());
    when(campaign.getPredictiveStrategy()).thenReturn(Constants.PREDICTIVE_STRATEGY.ALPHA);
    when(loggerDecorator.getLogger(StopState.class)).thenReturn(loggerDecorator);
    when(loggerDecorator.getLogger(CampaignStateTemplate.class)).thenReturn(loggerDecorator);
    when(dialerFacade.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(dialerFacade.getCampaignMementoService()).thenReturn(campaignMementoService);
    when(dialerFacade.getCampaignResultJobService()).thenReturn(campaignResultJobService);
    when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(campaignRealTimeRingingManager);
    when(dialerFacade.getDynamicBoostManager()).thenReturn(dynamicBoostManager);
    when(dialerFacade.getDncEngine()).thenReturn(mock(DNCEngine.class));
    when(dialerFacade.getLscEngine()).thenReturn(mock(LSCEngine.class));
    when(dialerFacade.getCampaignDbOperations()).thenReturn(campaignDbOperations);
    when(dialerFacade.getRoutingGroupService()).thenReturn(routingGroupService);
    when(campaignContext.getDialerFacade()).thenReturn(dialerFacade);
    when(campaignContext.getCallProducer()).thenReturn(mock(CallProducer.class));
    when(campaignContext.getCallConsumer()).thenReturn(mock(CallConsumer.class));
    when(campaignContext.getCampaign()).thenReturn(campaign);
    when(dialerFacade.getAcdEngine()).thenReturn(acdEngine);
    when(dialerFacade.getStopConditionChecker()).thenReturn(mock(StopConditionChecker.class));
    when(acdEngine.getCampaignUsers(campaign)).thenReturn(Collections.singletonList(new User()));
    stopState = spy(new StopState(campaignContext));
  }

  @Test
  public void constructorShouldSetInstanceVariables() {
    assertSame(campaignContext, stopState.campaignContext);
  }

  @Test
  public void shouldStartIfCampaignIsEnabled() {
    stopState.start();

    verify(campaign).setState(CAMPAIGN_STATE.RUNNING);
  }

  @Test(expected = CampaignIllegalStateException.class)
  public void shouldNotStartIfCampaignIsDisabled() {
    when(campaign.isEnabled()).thenReturn(false);

    stopState.start();
  }

  @Test(expected = DateMismatchException.class)
  public void shouldNotStartIfNowIsBeforeStartDate() {
    when(campaign.getStartDate()).thenReturn(new DateTime().plusHours(1).toDate());

    stopState.start();
  }

  @Test(expected = DateMismatchException.class)
  public void shouldNotStartIfNowIsAfterStartDate() {
    when(campaign.getStartDate()).thenReturn(new DateTime().minusHours(1).toDate());
    when(campaign.getEndDate()).thenReturn(new DateTime().minusHours(1).toDate());

    stopState.start();
  }

  @Test
  public void shouldStartWithSchedule() {
    LocalDateTime fixedNow = LocalDateTime.of(2025, 6, 11, 12, 0);

    PowerMockito.mockStatic(LocalDateTime.class);
    PowerMockito.when(LocalDateTime.now()).thenReturn(fixedNow);

    DAY_OF_WEEK dayOfWeek = DAY_OF_WEEK.intToDayOfWeek(fixedNow.getDayOfWeek().getValue());

    Schedule schedule = new Schedule();
    schedule.setDay(dayOfWeek);
    schedule.setFrom(Date.from(fixedNow.minusHours(1).atZone(ZoneId.systemDefault()).toInstant()));
    schedule.setTill(Date.from(fixedNow.plusHours(1).atZone(ZoneId.systemDefault()).toInstant()));

    when(campaign.getEnabledSchedules()).thenReturn(Arrays.asList(schedule));
    when(campaign.getScheduleEnabled()).thenReturn(true);

    stopState.start();

    verify(campaign).setState(CAMPAIGN_STATE.RUNNING);
  }

  @Test(expected = DateMismatchException.class)
  public void shouldNotStartWhenScheduleFromIsAfterTill() {
    LocalDateTime fixedNow = LocalDateTime.of(2025, 6, 11, 0, 0);

    PowerMockito.mockStatic(LocalDateTime.class);
    PowerMockito.when(LocalDateTime.now()).thenReturn(fixedNow);

    Schedule schedule = new Schedule();
    schedule.setFrom(Date.from(fixedNow.minusHours(1).atZone(ZoneId.systemDefault()).toInstant()));
    schedule.setTill(Date.from(fixedNow.plusHours(1).atZone(ZoneId.systemDefault()).toInstant()));

    when(campaign.getEnabledSchedules()).thenReturn(Arrays.asList(schedule));
    when(campaign.getScheduleEnabled()).thenReturn(true);

    stopState.start();
  }


  @Test(expected = DateMismatchException.class)
  public void shouldNotStartIfScheduleIsNotForToday() {
    LocalDateTime fixedNow = LocalDateTime.of(2025, 6, 11, 12, 0);

    PowerMockito.mockStatic(LocalDateTime.class);
    PowerMockito.when(LocalDateTime.now()).thenReturn(fixedNow);

    Schedule schedule = new Schedule();
    schedule.setFrom(Date.from(fixedNow.minusHours(1).atZone(ZoneId.systemDefault()).toInstant()));
    schedule.setTill(Date.from(fixedNow.plusHours(1).atZone(ZoneId.systemDefault()).toInstant()));

    when(campaign.getEnabledSchedules()).thenReturn(Arrays.asList(schedule));
    when(campaign.getScheduleEnabled()).thenReturn(true);

    stopState.start();
  }

  @Test(expected = DateMismatchException.class)
  public void shouldNotStartWithEmptySchedules() {
    when(campaign.getScheduleEnabled()).thenReturn(true);

    stopState.start();
  }

  @Test(expected = CampaignIllegalStateException.class)
  public void shouldNotStartWhenNoCallerIdPoolConfigured() {
    when(campaign.getCallerIdBehaviour()).thenReturn(Constants.CALLER_ID_BEHAVIOUR.POOL);

    stopState.start();
  }

  @Test(expected = CampaignIllegalStateException.class)
  public void shouldNotStartWhenCallerIdPoolNotFound() {
    RoutingGroup routingGroup = new RoutingGroup();
    routingGroup.setId(2L);
    when(campaign.getCallerIdBehaviour()).thenReturn(Constants.CALLER_ID_BEHAVIOUR.POOL);
    when(campaign.getCallerIdPool()).thenReturn(routingGroup);

    stopState.start();
  }

  @Test(expected = CampaignIllegalStateException.class)
  public void shouldNotStartWhenCallerIdPoolDeleted() {
    RoutingGroup routingGroup = new RoutingGroup();
    routingGroup.setDeleted(true);
    routingGroup.setId(2L);
    when(campaign.getCallerIdBehaviour()).thenReturn(Constants.CALLER_ID_BEHAVIOUR.POOL);
    when(campaign.getCallerIdPool()).thenReturn(routingGroup);
    when(routingGroupService.getRoutingGroup(routingGroup.getId())).thenReturn(routingGroup);

    stopState.start();
  }

  @Test
  public void shouldStartWhenCallerIdPoolEnabled() {
    RoutingGroup routingGroup = new RoutingGroup();
    routingGroup.setEnabled(true);
    routingGroup.setId(2L);
    when(campaign.getCallerIdBehaviour()).thenReturn(Constants.CALLER_ID_BEHAVIOUR.POOL);
    when(campaign.getCallerIdPool()).thenReturn(routingGroup);
    when(routingGroupService.getRoutingGroup(routingGroup.getId())).thenReturn(routingGroup);

    stopState.start();
  }

  @Test(expected = CampaignIllegalStateException.class)
  public void shouldNotStartWhenCallerIdPoolDisabled() {
    RoutingGroup routingGroup = new RoutingGroup();
    routingGroup.setId(2L);
    when(campaign.getCallerIdBehaviour()).thenReturn(Constants.CALLER_ID_BEHAVIOUR.POOL);
    when(campaign.getCallerIdPool()).thenReturn(routingGroup);
    when(routingGroupService.getRoutingGroup(routingGroup.getId())).thenReturn(routingGroup);

    stopState.start();
  }

  @Test
  public void shouldRestoreCampaignFromMemento() {
    CampaignMemento campaignMemento = mock(CampaignMemento.class);
    when(campaignMementoService.getCampaignMementoByCampaignId(0L)).thenReturn(campaignMemento);

    stopState.start();

    verify(campaignMemento).restore(campaign);
  }

  @Test
  public void shouldAddCallQueueToAsteriskCallQueues() {
    Location location = mock(Location.class);
    CallOriginator callOriginator = mock(CallOriginator.class);
    when(location.getServers()).thenReturn(Arrays.asList(mock(Server.class)));
    when(campaign.getLocations()).thenReturn(new HashSet() {
      {
        add(location);
      }
    });
    when(dialerFacade.getAsteriskCallOriginators()).thenReturn(new HashMap() {
      {
        put(0L, callOriginator);
      }
    });

    stopState.start();

    verify(callOriginator).addCallQueue(campaignContext.getCallConsumer().getCallQueue());
  }

  @Test
  public void givenStartupDbCheckEnabledWhenStartThenCleanFetchedFlagBeforeStateChange() throws SQLException {
    when(campaign.getStartupDbCheck()).thenReturn(true);
    InOrder inOrder = inOrder(campaignDbOperations, campaign);

    stopState.start();

    // FIX-333599 (05.10.2026): kardeş yokken de kesme (cleanup başlangıç anı) geçilir.
    inOrder.verify(campaignDbOperations).removeFetchedDataFlagFromUnfinishedCalls(eq(campaign), notNull());
    inOrder.verify(campaign).setState(CAMPAIGN_STATE.RUNNING);
  }

  @Test
  public void givenStartupDbCheckDisabledWhenStartThenDoNotCleanFetchedFlag() throws SQLException {
    when(campaign.getStartupDbCheck()).thenReturn(false);

    stopState.start();

    verify(campaignDbOperations, never()).removeFetchedDataFlagFromUnfinishedCalls(eq(campaign), any());
  }

  @Test
  public void givenStartupDbCheckNotSetWhenStartThenDoNotCleanFetchedFlag() throws SQLException {
    when(campaign.getStartupDbCheck()).thenReturn(null);

    stopState.start();

    verify(campaignDbOperations, never()).removeFetchedDataFlagFromUnfinishedCalls(eq(campaign), any());
  }

}
