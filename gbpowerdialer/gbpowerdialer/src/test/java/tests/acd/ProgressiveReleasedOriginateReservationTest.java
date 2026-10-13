package tests.acd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
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
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * FIX-GLB90060295: PROGRESSIVE_RELEASED kampanyada agent originate (ring) fazinda idle havuzdan
 * CIKARILMAZ; gercek secim ve havuzdan alma answer fazinda yapilir.
 *
 * <p>Uretim kaniti (10.08.2026, 13:00-14:00, 5 agent): ACDEngine.idleUsers havuzu zamanin
 * %75,6'sinda tamamen bostu ve zaman-agirlikli ortalama havuz buyuklugu 0,30 agent'ti; ayni anda
 * supervisor ekrani 5 agent'i Available gosteriyordu. Sebep: agent originate aninda havuzdan
 * cikariliyor ve musteri calarken (ring medyan 10,7 sn, p90 34,1 sn) havuz disinda tutuluyordu.</p>
 *
 * <p>Bu test ayni zamanda R1'i kilitler: rezervasyonu atlamak TEK BASINA yeterli degildir.
 * DefaultFindMatchingUserStrategy.removeFromIdleUsersAndCheck donusu
 * {@code (userIsIdle || !useOnlyIdleUsers) && threshold} oldugundan, aday havuzu idle kuyrugu
 * olarak birakilirsa cikarma atlandiginda metot false doner ve HIC agent secilemez. Bu yuzden
 * "agent havuzda kaldi" ile "agent yine de atandi" birlikte dogrulanir.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ProgressiveReleasedOriginateReservationTest {

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
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
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
    acdEngine.addToIdleUsers(agent);
  }

  @Test
  public void agentIsAssignedAndStaysInIdlePoolDuringOriginatePhase() {
    acdEngine.processACD(call);

    assertEquals("Originate fazinda agent yine de cagriya atanmali (R1)", agent, call.getUser());
    assertTrue("Musteri calarken agent idle havuzda KALMALI",
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
  public void agentIsReservedOnAnswerPhase() {
    call.setAnswerDate(1.0d);

    acdEngine.processACD(call);

    assertEquals(agent, call.getUser());
    assertFalse("Answer fazinda gercek secim yapilir ve agent havuzdan ALINIR",
        acdEngine.getIdleUsersInGroup(group).contains(agent));
  }

  /**
   * Rezervasyon kalktigi icin idle sayisi artik pacing sinyali degildir; over-dial tavani
   * operatorun {@code dialingBoost} degerinden turetilen not-bridged butcesidir. Bu test tavanin
   * KALKMADIGINI kilitler: 1 avail agent + boost 1.0 iken ucusta 1 calan cagri varken ikinci cagri
   * agent ALMAZ.
   */
  @Test
  public void originateBudgetCeilingStillAppliesWhenReservationIsSkipped() {
    campaign.setDialingBoost(1f);
    Call inFlight = new Call();
    inFlight.setCampaign(campaign);
    inFlight.setUser(agent);
    // answerDate null -> henuz bridge olmamis (not-bridged) cagri
    when(callUpToDateKeeper.getAllRegisteredUsers()).thenReturn(Collections.singletonList(agent));
    when(callUpToDateKeeper.getUserCalls(agent)).thenReturn(Collections.singletonList(inFlight));

    acdEngine.processACD(call);

    assertNull("Butce dolu (1 avail agent x boost 1.0 = 1 ucustaki cagri): ikinci cagri atanmamali",
        call.getUser());
  }

  @Test
  public void originateProceedsWhenBudgetIsAvailable() {
    campaign.setDialingBoost(1f);
    when(callUpToDateKeeper.getAllRegisteredUsers()).thenReturn(Collections.singletonList(agent));
    when(callUpToDateKeeper.getUserCalls(agent)).thenReturn(Collections.<Call>emptyList());

    acdEngine.processACD(call);

    assertEquals("Ucusta cagri yokken butce musait; agent atanmali", agent, call.getUser());
    assertTrue("Agent yine de havuzda kalmali", acdEngine.getIdleUsersInGroup(group).contains(agent));
  }

  @Test
  public void agentIsReservedWhenCallIsAlreadyOriginated() {
    call.setOriginationDate(1.0d);

    acdEngine.processACD(call);

    assertEquals(agent, call.getUser());
    assertFalse("originationDate dolu ise originate fazi degildir; eski yol islemeli",
        acdEngine.getIdleUsersInGroup(group).contains(agent));
  }
}
