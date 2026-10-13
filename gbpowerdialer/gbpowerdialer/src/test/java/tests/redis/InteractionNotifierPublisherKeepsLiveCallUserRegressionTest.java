package tests.redis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.powerdialer.dao.impl.CDRDao;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Call;
import com.powerdialer.model.User;
import com.powerdialer.redis.InteractionNotifierPublisher;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.redis.event.interactionnotifier.MockFactory;
import com.powerdialer.service.CDRService;
import com.powerdialer.service.UserService;
import com.powerdialer.util.ApplicationUtil;

/**
 * 10.08.2026 uretim kaydi: ACD hot path'i (CallUpToDateKeeper.getCallsByUserGroup -> Group.equals)
 * dakikalar boyunca LazyInitializationException atti, CallOriginator tick'leri dustu ve bazi
 * kampanyalarda originate 5-10 dakika durdu.
 *
 * <p>Kok neden bu publisher'daydi: rapor icin agent DB'den tazelenip CANLI {@link Call} nesnesine
 * yaziliyordu ({@code call.setUser(userService.getUserById(...))}). Donen entity detached'dir ve
 * {@code User.group} lazy @ManyToOne oldugu icin icinde initialize edilmemis bir Hibernate proxy
 * tasir. Bu Call, originate sirasinda {@code CallUpToDateKeeper}'a kayitli oldugundan olu proxy
 * paylasilan runtime state'e sizip her taramada patliyordu.</p>
 *
 * <p>Kilitlenen contract: rapor icin DB'den tazeleme KORUNUR, ancak tazelenen entity yalniz rapora
 * verilir; canli Call'in user'i DEGISTIRILMEZ.</p>
 */
public class InteractionNotifierPublisherKeepsLiveCallUserRegressionTest {

  private static final Long AGENT_ID = 1L;

  private RedisUtil redisUtil;
  private ApplicationUtil applicationUtil;
  private CampaignDbOperations campaignDbOperations;
  private UserService userService;
  private CDRService cdrService;
  private CDRDao cdrDao;

  private InteractionNotifierPublisher publisher;

  private Call call;
  private User inMemoryAgent;
  private User persistedAgent;

  @Before
  public void setUp() {
    redisUtil = mock(RedisUtil.class);
    applicationUtil = mock(ApplicationUtil.class);
    campaignDbOperations = mock(CampaignDbOperations.class);
    userService = mock(UserService.class);
    cdrService = mock(CDRService.class);
    cdrDao = mock(CDRDao.class);

    when(applicationUtil.getThisApplicationLocalIpAddress()).thenReturn("127.0.0.1");

    publisher = new InteractionNotifierPublisher(redisUtil, applicationUtil, campaignDbOperations,
        userService, cdrService, cdrDao);
    publisher.init();

    call = MockFactory.buildCall();
    inMemoryAgent = call.getUser();

    persistedAgent = mock(User.class);
    when(persistedAgent.getId()).thenReturn(AGENT_ID);
    when(persistedAgent.getUsername()).thenReturn("agent01");
    when(persistedAgent.getAdNickname()).thenReturn("db-nick01");
    when(persistedAgent.getFullname()).thenReturn("DB John Doe");

    when(userService.getUserById(AGENT_ID)).thenReturn(persistedAgent);
  }

  /**
   * Canli Call nesnesinin user'i publisher tarafindan degistirilmemelidir; aksi halde detached
   * entity ACD/keeper runtime state'ine sizar.
   */
  @Test
  public void publishDoesNotReplaceUserOfLiveCall() {
    publisher.publishDialerCallReport(call);

    verify(call, never()).setUser(any(User.class));
  }

  /**
   * Rapor payload'i DB'den tazelenen agent'tan beslenmeye devam etmelidir (adNickname gibi alanlar
   * Redis'ten gelen bellek kopyasinda bulunmaz).
   */
  @Test
  public void publishStillUsesPersistedAgentForReportPayload() {
    publisher.publishDialerCallReport(call);

    verify(userService).getUserById(AGENT_ID);
    verify(persistedAgent, atLeastOnce()).getAdNickname();
    verify(inMemoryAgent, never()).getAdNickname();
  }
}
