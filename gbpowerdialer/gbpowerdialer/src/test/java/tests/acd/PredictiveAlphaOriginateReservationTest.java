package tests.acd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
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
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * FIX-GLB90060295 (ALPHA genislemesi): PREDICTIVE-ALPHA kampanyada da agent originate (ring)
 * fazinda idle havuzdan CIKARILMAZ; gercek secim ve havuzdan alma answer fazinda yapilir.
 *
 * <p>ALPHA'da pacing zaten {@code DynamicBoostManager}'in dinamik boost degeriyle yapilir. Sorun,
 * o boost'un carpildigi "avail agent" sayisinin rezervasyon yuzunden gercek degerinden kucuk
 * gorunmesiydi (PROGRESSIVE_RELEASED'de olculen ayni starvation). Bu testler hem havuzun
 * bosalmadigini hem de over-dial tavaninin KALKMADIGINI kilitler: tavan
 * {@code floor(availableUsers x dinamikBoost)}.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class PredictiveAlphaOriginateReservationTest {

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
    campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY.ALPHA);
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
    when(dynamicBoostManager.getBoostValue(campaign.getId())).thenReturn(2f);
    acdEngine.addToIdleUsers(agent);
  }

  @Test
  public void agentIsAssignedAndStaysInIdlePoolDuringOriginatePhase() {
    acdEngine.processACD(call);

    assertEquals("Originate fazinda agent yine de cagriya atanmali", agent, call.getUser());
    assertTrue("ALPHA: musteri calarken agent idle havuzda KALMALI",
        acdEngine.getIdleUsersInGroup(group).contains(agent));
  }

  @Test
  public void campaignIdleUserCountIsNotDrainedByRingingCall() {
    assertEquals(1, acdEngine.getCampaignIdleUsersCount(campaign));

    acdEngine.processACD(call);

    assertEquals("Ring boyunca kampanyanin idle agent sayisi dusmemeli",
        1, acdEngine.getCampaignIdleUsersCount(campaign));
  }

  @Test
  public void dynamicBoostCeilingStillAppliesWhenReservationIsSkipped() {
    // Dinamik boost 2, 1 avail agent -> tavan floor(1 x 2) = 2 ucustaki cagri.
    Call inFlight1 = new Call();
    inFlight1.setCampaign(campaign);
    Call inFlight2 = new Call();
    inFlight2.setCampaign(campaign);
    when(callUpToDateKeeper.getAllRegisteredUsers()).thenReturn(Collections.singletonList(agent));
    when(callUpToDateKeeper.getUserCalls(agent)).thenReturn(Arrays.asList(inFlight1, inFlight2));

    acdEngine.processACD(call);

    assertNull("Dinamik boost tavani dolu: ucuncu cagri atanmamali", call.getUser());
  }

  @Test
  public void originateProceedsWhileUnderDynamicBoostCeiling() {
    Call inFlight = new Call();
    inFlight.setCampaign(campaign);
    when(callUpToDateKeeper.getAllRegisteredUsers()).thenReturn(Collections.singletonList(agent));
    when(callUpToDateKeeper.getUserCalls(agent)).thenReturn(Collections.singletonList(inFlight));

    acdEngine.processACD(call);

    assertEquals("Tavan 2 iken 1 ucustaki cagri varken ikinci cagri cikabilmeli",
        agent, call.getUser());
  }

  @Test
  public void agentIsReservedOnAnswerPhase() {
    call.setAnswerDate(1.0d);

    acdEngine.processACD(call);

    assertEquals(agent, call.getUser());
    assertFalse("Answer fazinda gercek secim yapilir ve agent havuzdan ALINIR",
        acdEngine.getIdleUsersInGroup(group).contains(agent));
  }
}
