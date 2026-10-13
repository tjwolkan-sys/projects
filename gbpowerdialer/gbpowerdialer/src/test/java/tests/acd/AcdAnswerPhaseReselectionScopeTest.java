package tests.acd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

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
 * FIX-GLB90060295: answer fazi yeniden secim (trySearchLessOccupiedUser) kapsaminin
 * PROGRESSIVE_RELEASED'i de icermesi.
 *
 * <p>Originate fazinda rezervasyon kalkinca on atama SEMBOLIK olur; {@code call.getUser()} artik
 * "o an rezerve edilmis agent" degil, yalnizca "originate aninda musaitti" demektir. Bu yuzden
 * answer fazinda:</p>
 * <ul>
 *   <li>yeniden secim her zaman calismalidir ({@code lastCallEndDate == null} olan yeni login /
 *       vardiya basi agent'inda da),</li>
 *   <li>avail agent yoksa {@code call.getUser()} NULL birakilmalidir — meshgul olabilecek on
 *       atamaya kor donus, AGI tarafinda tryLock/status SKIP turu uretir.</li>
 * </ul>
 *
 * <p>PROGRESSIVE_RESERVED'de eski davranis korunur: orada on atama gercek rezervasyondur.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AcdAnswerPhaseReselectionScopeTest {

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
  private User initialUser;
  private Campaign campaign;
  private Call call;

  @Before
  public void init() {
    group = new Group();
    group.setId(7L);
    group.setName("GUVENCELL_GENEL");
    group.setQueues(new HashSet<>());

    // Originate aninda musait olan, answer aninda mesgullesen temsili on atama.
    initialUser = new User();
    initialUser.setId(9865L);
    initialUser.setUsername("81937");
    initialUser.setGroup(group);
    Status incall = new Status();
    incall.setAvailableForDialerCalls(false);
    initialUser.setStatus(incall);
    initialUser.setStatusSetDate(new java.sql.Date(System.currentTimeMillis() - 5_000L));

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

    // Answer fazi: musteri acti, ama o an idle havuzda hic avail agent yok.
    call = new Call();
    call.setCampaign(campaign);
    call.setAnswerDate(1.0d);
    call.setUser(initialUser);

    Whitebox.setInternalState(acdEngine, "LOG", mock(LoggerDecorator.class));
    Whitebox.setInternalState(acdEngine, "callUpToDateKeeper", callUpToDateKeeper);
    Whitebox.setInternalState(acdEngine, "userHolder", userHolder);
    Whitebox.setInternalState(acdEngine, "dynamicBoostManager", dynamicBoostManager);
    Whitebox.setInternalState(acdEngine, "userFindingContextBuilder",
        new UserFindingContextBuilder(redisUtil, acdEngine));
  }

  @Test
  public void progressiveReleasedLeavesUserNullWhenNoAgentAvailableAtAnswer() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);

    acdEngine.trySearchLessOccupiedUser(call);

    assertNull("PROGRESSIVE_RELEASED: avail agent yoksa mesgul on atamaya kor donus YAPILMAMALI",
        call.getUser());
  }

  @Test
  public void gammaLeavesUserNullWhenNoAgentAvailableAtAnswer() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY.GAMMA);

    acdEngine.trySearchLessOccupiedUser(call);

    assertNull("GAMMA davranisi bit-esit korunmali", call.getUser());
  }

  @Test
  public void predictiveAlphaLeavesUserNullWhenNoAgentAvailableAtAnswer() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY.ALPHA);

    acdEngine.trySearchLessOccupiedUser(call);

    assertNull("PREDICTIVE-ALPHA: on atama sembolik oldugu icin mesgul agent'a kor donus YAPILMAMALI",
        call.getUser());
  }

  @Test
  public void predictiveBetaKeepsInitialUserAtAnswer() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY.BETA);

    acdEngine.trySearchLessOccupiedUser(call);

    assertEquals("PREDICTIVE-BETA kapsam disi; davranisi degismemeli", initialUser, call.getUser());
  }

  @Test
  public void progressiveReservedKeepsInitialUserAtAnswer() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED);

    acdEngine.trySearchLessOccupiedUser(call);

    assertEquals("PROGRESSIVE_RESERVED: on atama gercek rezervasyondur, sahibi degismemeli",
        initialUser, call.getUser());
  }

  @Test
  public void previewKeepsInitialUserAtAnswer() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PREVIEW_FREE);

    acdEngine.trySearchLessOccupiedUser(call);

    assertEquals("PREVIEW davranisi degismemeli", initialUser, call.getUser());
  }
}
