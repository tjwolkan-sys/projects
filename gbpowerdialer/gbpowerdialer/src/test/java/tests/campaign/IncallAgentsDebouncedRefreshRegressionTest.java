package tests.campaign;

import com.powerdialer.dto.AgentMetricsDTO;
import com.powerdialer.engine.IncallAgentsRefresher;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 325654 regression testi.
 *
 * <p>Eskiden {@code UserSubscriber.handleStatusChange} her agent status degisiminde TUM
 * campaign context'lerini dolasip her eslesen kampanya icin {@code updateIncallAgents}
 * cagiriyordu; her cagri da o kampanyanin tum agent populasyonu icin Redis'ten bulk
 * status okuyordu. Yani maliyet "status degisimi x eslesen kampanya x agent" seklinde
 * carpiliyordu (O(N^2)) ve burst aninda CPU'yu tepeye tasiyordu.</p>
 *
 * <p>Yeni davranis: status degisimi kampanyayi yalniz "dirty" isaretler (Redis yok,
 * koleksiyon taramasi yok); yeniden hesap sabit araliklarla toplu yapilir.</p>
 *
 * <p>Kritik guvenlik ozelligi: {@code incallAgents} sayacinin periyodik bir reconciler'i
 * YOKTUR, bu yuzden debounce hicbir kosulda son degisikligi dusurmemelidir. Asagidaki
 * testler yeniden isaretlemenin her zaman yeni bir hesaba yol actigini kilitler.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class IncallAgentsDebouncedRefreshRegressionTest {

  @Mock
  private ACDEngine acdEngine;

  @Mock
  private RedisUtil redisUtil;

  private IncallAgentsRefresher refresher;

  private Campaign campaignA;
  private Campaign campaignB;
  private User userA1;
  private User userA2;
  private User userB1;

  private static Campaign campaign(long id) {
    Campaign campaign = new Campaign();
    campaign.setId(id);
    return campaign;
  }

  private static User user(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  private static Map<Long, List<User>> activeUsers(Long campaignId, List<User> users) {
    Map<Long, List<User>> map = new HashMap<>();
    map.put(campaignId, users);
    return map;
  }

  private void stubActiveUsers(Map<Long, List<User>> byCampaignId) {
    when(acdEngine.getCampaignAllActiveUsersByCampaignId(anyCollection())).thenReturn(byCampaignId);
  }

  @Before
  public void setUp() {
    refresher = new IncallAgentsRefresher();
    Whitebox.setInternalState(refresher, "acdEngine", acdEngine);
    Whitebox.setInternalState(refresher, "redisUtil", redisUtil);

    campaignA = campaign(11L);
    campaignB = campaign(22L);
    userA1 = user(101L);
    userA2 = user(102L);
    userB1 = user(201L);
  }

  @Test
  public void repeatedMarksWithinOneWindowCauseExactlyOneRecalculation() {
    stubActiveUsers(activeUsers(campaignA.getId(), Arrays.asList(userA1, userA2)));
    when(redisUtil.getUsersStatusBulk(anySet())).thenReturn(new AgentMetricsDTO(2, 1, 1, 0));

    for (int i = 0; i < 50; i++) {
      refresher.markDirty(campaignA);
    }
    refresher.refreshDirtyCampaigns();

    verify(redisUtil, times(1)).getUsersStatusBulk(anySet());
    verify(acdEngine, times(1)).getCampaignAllActiveUsersByCampaignId(anyCollection());
    assertEquals(1, campaignA.getIncallAgents().get());
  }

  @Test
  public void remarkAfterFlushIsRecalculatedSoCounterNeverStaysStale() {
    stubActiveUsers(activeUsers(campaignA.getId(), Collections.singletonList(userA1)));
    when(redisUtil.getUsersStatusBulk(anySet()))
        .thenReturn(new AgentMetricsDTO(1, 1, 0, 0))
        .thenReturn(new AgentMetricsDTO(1, 0, 1, 0));

    refresher.markDirty(campaignA);
    refresher.refreshDirtyCampaigns();
    assertEquals(1, campaignA.getIncallAgents().get());

    refresher.markDirty(campaignA);
    refresher.refreshDirtyCampaigns();

    assertEquals("ikinci turda taze deger uygulanmali", 0, campaignA.getIncallAgents().get());
    verify(redisUtil, times(2)).getUsersStatusBulk(anySet());
  }

  @Test
  public void flushWithoutAnyMarkDoesNoRedisOrAcdWork() {
    refresher.refreshDirtyCampaigns();

    verify(redisUtil, never()).getUsersStatusBulk(anySet());
    verify(acdEngine, never()).getCampaignAllActiveUsersByCampaignId(anyCollection());
  }

  @Test
  public void furtherFlushesDoNothingUntilCampaignIsMarkedDirtyAgain() {
    stubActiveUsers(activeUsers(campaignA.getId(), Collections.singletonList(userA1)));
    when(redisUtil.getUsersStatusBulk(anySet())).thenReturn(new AgentMetricsDTO(1, 1, 0, 0));

    refresher.markDirty(campaignA);
    refresher.refreshDirtyCampaigns();
    refresher.refreshDirtyCampaigns();
    refresher.refreshDirtyCampaigns();

    verify(redisUtil, times(1)).getUsersStatusBulk(anySet());
  }

  @Test
  public void multipleDirtyCampaignsShareASingleRegisteredUserSnapshot() {
    Map<Long, List<User>> byCampaignId = new HashMap<>();
    byCampaignId.put(campaignA.getId(), Arrays.asList(userA1, userA2));
    byCampaignId.put(campaignB.getId(), Collections.singletonList(userB1));
    stubActiveUsers(byCampaignId);
    when(redisUtil.getUsersStatusBulk(anySet())).thenReturn(new AgentMetricsDTO(2, 1, 1, 0));

    refresher.markDirty(campaignA);
    refresher.markDirty(campaignB);
    refresher.refreshDirtyCampaigns();

    verify(acdEngine, times(1)).getCampaignAllActiveUsersByCampaignId(anyCollection());
    verify(redisUtil, times(2)).getUsersStatusBulk(anySet());
    assertEquals(1, campaignA.getIncallAgents().get());
    assertEquals(1, campaignB.getIncallAgents().get());
  }

  @Test
  public void nullOrIdlessCampaignIsIgnored() {
    refresher.markDirty(null);
    refresher.markDirty(new Campaign());

    refresher.refreshDirtyCampaigns();

    verify(acdEngine, never()).getCampaignAllActiveUsersByCampaignId(anyCollection());
    verify(redisUtil, never()).getUsersStatusBulk(anySet());
  }

  @Test
  public void redisFailureIsContainedAndPreviousCounterIsPreserved() {
    campaignA.setIncallAgents(7);
    stubActiveUsers(activeUsers(campaignA.getId(), Collections.singletonList(userA1)));
    when(redisUtil.getUsersStatusBulk(anySet())).thenThrow(new RuntimeException("redis down"));

    refresher.markDirty(campaignA);
    refresher.refreshDirtyCampaigns();

    assertEquals("hata yayilmamali ve onceki deger korunmali", 7, campaignA.getIncallAgents().get());
  }
}
