package tests.acd;

import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignGroup;
import com.powerdialer.model.Group;
import com.powerdialer.model.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 325654 karakterizasyon testi.
 *
 * <p>{@code CampaignService.applyRealtimeAgentSizes} her kampanya icin ayri ayri
 * {@code countCampaignAllActiveUsers} cagiriyordu; o da her cagrida
 * {@code CallUpToDateKeeper.getAllRegisteredUsers()} ile tum aktif cagri koleksiyonunu
 * tarayip HashSet + ArrayList kuruyordu. Uretimde tek {@code realtimeInfo} istegi 65
 * kampanya tasiyor ve endpoint saniyede ~5 kez cagriliyordu; yani saniyede ~330 tam
 * tarama.</p>
 *
 * <p>Bu test batch sonucunun per-campaign sonucla BIREBIR ayni oldugunu (davranis
 * degismedi) ve batch'in registered-user snapshot'ini yalniz BIR kez aldigini kilitler.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class CampaignActiveUserCountBatchCharacterizationTest {

  private static final Long CAMPAIGN_A_ID = 11L;
  private static final Long CAMPAIGN_B_ID = 22L;

  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;

  @Mock
  private Campaign campaignA;

  @Mock
  private Campaign campaignB;

  @Mock
  private CampaignGroup campaignGroupA;

  @Mock
  private CampaignGroup campaignGroupB;

  private ACDEngine acdEngine;

  private final Group groupA = group(1L);
  private final Group groupB = group(2L);

  private final User loginOnlyA = user(101L, groupA);
  private final User sharedA = user(102L, groupA);
  private final User registeredOnlyA = user(103L, groupA);
  private final User registeredOnlyB = user(201L, groupB);

  private static Group group(long id) {
    Group group = new Group();
    group.setId(id);
    return group;
  }

  private static User user(long id, Group group) {
    User user = new User();
    user.setId(id);
    user.setGroup(group);
    user.setSkillWeightPair(new HashMap<>());
    return user;
  }

  @Before
  public void setUp() {
    acdEngine = spy(new ACDEngine());
    Whitebox.setInternalState(acdEngine, "callUpToDateKeeper", callUpToDateKeeper);

    lenient().when(campaignA.getId()).thenReturn(CAMPAIGN_A_ID);
    lenient().when(campaignB.getId()).thenReturn(CAMPAIGN_B_ID);
    lenient().when(campaignA.getCampaignGroups()).thenReturn(Collections.singletonList(campaignGroupA));
    lenient().when(campaignB.getCampaignGroups()).thenReturn(Collections.singletonList(campaignGroupB));
    lenient().when(campaignGroupA.isEnabled()).thenReturn(true);
    lenient().when(campaignGroupB.isEnabled()).thenReturn(true);
    lenient().when(campaignGroupA.getGroup()).thenReturn(groupA);
    lenient().when(campaignGroupB.getGroup()).thenReturn(groupB);
    lenient().when(campaignA.getSkills()).thenReturn(Collections.emptySet());
    lenient().when(campaignB.getSkills()).thenReturn(Collections.emptySet());

    // getAllLoginUsers idle/systemActive queue state'ine baglidir; bu test concat +
    // distinct + snapshot paylasimini dogruladigi icin queue mekanigi stub'lanir.
    doReturn(new LinkedHashSet<>(Arrays.asList(loginOnlyA, sharedA))).when(acdEngine).getAllLoginUsers(campaignA);
    doReturn(Collections.<User>emptySet()).when(acdEngine).getAllLoginUsers(campaignB);

    when(callUpToDateKeeper.getAllRegisteredUsers())
        .thenReturn(Arrays.asList(sharedA, registeredOnlyA, registeredOnlyB));
  }

  @Test
  public void batchCountsAreIdenticalToPerCampaignCounts() {
    int perCampaignA = acdEngine.countCampaignAllActiveUsers(campaignA);
    int perCampaignB = acdEngine.countCampaignAllActiveUsers(campaignB);

    Map<Long, Integer> batch =
        acdEngine.countCampaignAllActiveUsersByCampaignId(Arrays.asList(campaignA, campaignB));

    assertEquals("campaignA: login {101,102} + registered-matching {102,103} distinct = 3",
        3, perCampaignA);
    assertEquals("campaignB: login {} + registered-matching {201} = 1", 1, perCampaignB);
    assertEquals("batch campaignA sonucu per-campaign'den farkli",
        perCampaignA, batch.get(CAMPAIGN_A_ID).intValue());
    assertEquals("batch campaignB sonucu per-campaign'den farkli",
        perCampaignB, batch.get(CAMPAIGN_B_ID).intValue());
  }

  @Test
  public void batchTakesRegisteredUserSnapshotOnlyOnceForAllCampaigns() {
    acdEngine.countCampaignAllActiveUsersByCampaignId(Arrays.asList(campaignA, campaignB));

    verify(callUpToDateKeeper, times(1)).getAllRegisteredUsers();
  }

  @Test
  public void singleCampaignActiveUserListContentIsUnchanged() {
    List<User> users = acdEngine.getCampaignAllActiveUsers(campaignA);

    assertEquals(3, users.size());
    assertTrue("login + registered birlesimi korunmali",
        users.containsAll(Arrays.asList(loginOnlyA, sharedA, registeredOnlyA)));
  }

  @Test
  public void batchUserListsAreIdenticalToPerCampaignLists() {
    List<User> perCampaignA = acdEngine.getCampaignAllActiveUsers(campaignA);
    List<User> perCampaignB = acdEngine.getCampaignAllActiveUsers(campaignB);

    Map<Long, List<User>> batch =
        acdEngine.getCampaignAllActiveUsersByCampaignId(Arrays.asList(campaignA, campaignB));

    assertEquals(perCampaignA, batch.get(CAMPAIGN_A_ID));
    assertEquals(perCampaignB, batch.get(CAMPAIGN_B_ID));
  }

  @Test
  public void emptyOrNullCampaignCollectionYieldsEmptyResultWithoutTouchingCallState() {
    assertTrue(acdEngine.countCampaignAllActiveUsersByCampaignId(Collections.<Campaign>emptyList()).isEmpty());
    assertTrue(acdEngine.countCampaignAllActiveUsersByCampaignId(null).isEmpty());
    assertTrue(acdEngine.getCampaignAllActiveUsersByCampaignId(null).isEmpty());

    verify(callUpToDateKeeper, times(0)).getAllRegisteredUsers();
  }
}
