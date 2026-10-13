package tests.acd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.UserHolder;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.acd.UserFindingContextBuilder;
import com.powerdialer.engine.boost.DynamicBoostManager;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignGroup;
import com.powerdialer.model.Group;
import com.powerdialer.model.Status;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.redis.message.UserPauseMessage;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * FIX-GLB90060295 regresyonu: originate-fazi rezervasyonunun KALDIRILMADIGI kampanya tipleri.
 *
 * <p>ACDEngine.resolveUserFindingContext'te BETA ve GAMMA disindaki TUM tipler ayni
 * {@code canUseIdleUsers} dalindan gecer: PROGRESSIVE_RESERVED, PROGRESSIVE_RELEASED,
 * PREVIEW_FREE, PREVIEW_STRAIGHT ve PREDICTIVE-ALPHA. Bu yuzden fix'in guard'i "gamma degilse"
 * veya "progressive ise" seklinde yazilamaz; yalniz PROGRESSIVE_RELEASED'i kapsamalidir (R2).</p>
 *
 * <p>PROGRESSIVE_RESERVED'de rezervasyon urun garantisidir: UserFindingContextBuilder secim
 * aninda UserPauseMessage(true) publish eder (ManagerConnectionDecorator hangup'ta false ile
 * kaldirir) ve UserPopulationEvaluator bu tipte userId/assignedUserId'yi her kosulda korur.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ProgressiveReservedReservationRegressionTest {

  @InjectMocks
  private ACDEngine acdEngine;

  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;
  @Mock
  private UserHolder userHolder;
  @Mock
  private DynamicBoostManager dynamicBoostManager;
  @Mock
  private RedisUtil redisUtil;

  private Group group;
  private User agent;
  private Campaign campaign;
  private Call call;

  @Before
  public void init() {
    group = new Group();
    group.setId(7L);
    group.setName("GUVENCELL_GENEL");
    group.setQueues(new HashSet<>());

    agent = new User();
    agent.setId(9865L);
    agent.setUsername("81937");
    agent.setGroup(group);
    Status available = new Status();
    available.setAvailableForDialerCalls(true);
    agent.setStatus(available);
    agent.setStatusSetDate(new java.sql.Date(System.currentTimeMillis() - 120_000L));

    campaign = new Campaign();
    campaign.setId(160L);
    campaign.setDialingBoost(1.01f);
    campaign.setAvailableThreshold(0);
    campaign.setSkillEnabled(false);
    campaign.setAppointerReset(false);

    CampaignGroup campaignGroup = new CampaignGroup();
    campaignGroup.setGroup(group);
    campaignGroup.setCampaign(campaign);
    campaignGroup.setPriority(1);
    campaignGroup.setEnabled(true);
    campaign.setCampaignGroups(new ArrayList<>(Collections.singletonList(campaignGroup)));

    call = new Call();
    call.setCampaign(campaign);

    Whitebox.setInternalState(acdEngine, "LOG", mock(LoggerDecorator.class));
    Whitebox.setInternalState(acdEngine, "callUpToDateKeeper", callUpToDateKeeper);
    Whitebox.setInternalState(acdEngine, "userHolder", userHolder);
    Whitebox.setInternalState(acdEngine, "dynamicBoostManager", dynamicBoostManager);
    Whitebox.setInternalState(acdEngine, "userFindingContextBuilder",
        new UserFindingContextBuilder(redisUtil, acdEngine));

    when(userHolder.getUser(agent.getId())).thenReturn(agent);
    when(dynamicBoostManager.getBoostValue(campaign.getId())).thenReturn(1.5f);
    acdEngine.addToIdleUsers(agent);
  }

  private void assertAgentReservedAtOriginate(String label) {
    acdEngine.processACD(call);

    assertEquals(label + ": agent cagriya atanmali", agent, call.getUser());
    assertFalse(label + ": agent originate aninda idle havuzdan CIKARILMALI (davranis degismemeli)",
        acdEngine.getIdleUsersInGroup(group).contains(agent));
  }

  @Test
  public void progressiveReservedStillReservesAgentAtOriginate() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED);

    assertAgentReservedAtOriginate("PROGRESSIVE_RESERVED");
  }

  @Test
  public void progressiveReservedStillPublishesUserPauseMessage() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED);

    acdEngine.processACD(call);

    verify(redisUtil, atLeastOnce()).publishUserPauseMessage(any(UserPauseMessage.class));
  }

  @Test
  public void previewFreeStillReservesAgentAtOriginate() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PREVIEW_FREE);

    assertAgentReservedAtOriginate("PREVIEW_FREE");
  }

  @Test
  public void previewStraightStillReservesAgentAtOriginate() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT);

    assertAgentReservedAtOriginate("PREVIEW_STRAIGHT");
  }

  @Test
  public void predictiveBetaStillReservesAgentAtOriginate() {
    // BETA kendi butcesini PredictiveBetaCallManager uzerinden yonetir; bu is kapsaminda
    // incelenmedi ve originate-fazi rezervasyonu AYNEN korunur.
    campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY.BETA);

    assertAgentReservedAtOriginate("PREDICTIVE-BETA");
  }

  @Test
  public void progressiveReleasedDoesNotPublishUserPauseMessage() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);

    acdEngine.processACD(call);

    verify(redisUtil, never()).publishUserPauseMessage(any(UserPauseMessage.class));
  }
}
