package tests.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerdialer.dao.ICampaignDao;
import com.powerdialer.dao.ITenantDao;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.service.CampaignMementoService;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * FIX-333607: {@link CampaignService#action} campaign bazlı kilitle çalışır ve kilidi transaction
 * açılmadan önce alır.
 *
 * <p>Eski hali {@code @Transactional public synchronized} idi: Spring proxy önce transaction (ve
 * JDBC bağlantısı) açıyor, sonra monitörü bekliyordu. 02.10.2026'da kuyrukta bekleyen her istek ana
 * DB havuzundan bağlantı tutuyordu ve bir campaign'in start'ı bütün campaign'leri bekletiyordu.</p>
 */
public class CampaignServiceActionLockRegressionTest {

  private final ICampaignDao campaignDao = mock(ICampaignDao.class);
  private final DialerFacade dialerFacade = mock(DialerFacade.class);
  private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
  private final List<String> events = new CopyOnWriteArrayList<>();
  private CampaignService campaignService;
  private Thread worker;

  @Before
  public void setUp() {
    campaignService = new CampaignService(campaignDao, mock(CampaignDbOperations.class), mock(ITenantDao.class),
        dialerFacade, mock(CampaignMementoService.class));
    campaignService.setTransactionManager(transactionManager);
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());
    when(transactionManager.getTransaction(any())).thenAnswer(inv -> {
      events.add("begin");
      return new SimpleTransactionStatus();
    });
    doAnswer(inv -> {
      events.add("commit");
      return null;
    }).when(transactionManager).commit(any());
  }

  @After
  public void tearDown() throws InterruptedException {
    SecurityContextHolder.clearContext();
    campaignService.shutdownActionCoordinator();
    if (worker != null) {
      worker.interrupt();
      worker.join(5000L);
    }
  }

  @Test
  public void actionOfOtherCampaignCompletesWhileCampaignStartIsStuck() throws Exception {
    CountDownLatch release = blockStartOfCampaign(1L);

    campaignService.action(2L, CAMPAIGN_ACTION.STOP_REGULAR, null);

    verify(dialerFacade).shutdownCampaign(2L, CAMPAIGN_ACTION.STOP_REGULAR);
    release.countDown();
  }

  @Test
  public void waitingActionOfSameCampaignDoesNotOpenTransactionBeforeLock() throws Exception {
    CountDownLatch release = blockStartOfCampaign(1L);

    Thread second = new Thread(() -> campaignService.action(1L, CAMPAIGN_ACTION.STOP_REGULAR, null), "second-action");
    second.start();
    awaitWaiting(second);

    assertEquals("Kilidi bekleyen aksiyon transaction açmamalı", 1, count("begin"));

    release.countDown();
    second.join(5000L);
    verify(dialerFacade, timeout(5000L)).shutdownCampaign(1L, CAMPAIGN_ACTION.STOP_REGULAR);
    assertEquals(2, count("begin"));
    assertTrue("İkinci aksiyonun transaction'ı ilkinin commit'inden sonra açılmalı",
        events.indexOf("commit") < events.lastIndexOf("begin"));
  }

  @Test
  public void actionAsyncReturnsImmediatelyWhileSameCampaignIsBusy() throws Exception {
    CountDownLatch release = blockStartOfCampaign(1L);

    campaignService.actionAsync(1L, CAMPAIGN_ACTION.STOP_REGULAR, null);

    release.countDown();
    verify(dialerFacade, timeout(15000L)).shutdownCampaign(eq(1L), eq(CAMPAIGN_ACTION.STOP_REGULAR));
  }

  /**
   * {@code GenericService} sınıf seviyesinde {@code @Transactional(readOnly = true)} taşır ve
   * {@code CampaignService} bunu miras alır. Giriş noktaları kendi niteliğini vermezse Spring proxy'si
   * kilitten önce salt okunur bir transaction açar; içteki {@code TransactionTemplate} buna katılır
   * ve Hibernate flush modu MANUAL kalır (aksiyonun entity değişiklikleri yazılmayabilir).
   */
  @Test
  public void actionEntryPointsDoNotInheritReadOnlyClassLevelTransaction() throws Exception {
    AnnotationTransactionAttributeSource source = new AnnotationTransactionAttributeSource();
    for (String name : new String[] {"action", "actionAsync"}) {
      Method method = CampaignService.class.getMethod(name, Long.class, CAMPAIGN_ACTION.class, Constants.CAMPAIGN_STOP_TYPE.class);
      TransactionAttribute attribute = source.getTransactionAttribute(method, CampaignService.class);
      assertNotNull(name, attribute);
      assertFalse(name + " salt okunur transaction açmamalı", attribute.isReadOnly());
      assertEquals(name + " kendi transaction'ını açmamalı; transaction kilit alındıktan sonra açılır",
          TransactionDefinition.PROPAGATION_SUPPORTS, attribute.getPropagationBehavior());
    }
    Method[] inMemoryOrSelfManaged = {
        CampaignService.class.getMethod("startAfterRestart", Long.class),
        CampaignService.class.getMethod("hasPendingAction", Long.class),
        CampaignService.class.getMethod("checkPredictiveStrategyChangeAllowed", Long.class, com.powerdialer.dto.CampaignFormDTO.class)
    };
    for (Method method : inMemoryOrSelfManaged) {
      TransactionAttribute attribute = source.getTransactionAttribute(method, CampaignService.class);
      assertNotNull(method.getName(), attribute);
      assertFalse(method.getName() + " salt okunur transaction (ve DB bağlantısı) açmamalı", attribute.isReadOnly());
      assertEquals(method.getName(), TransactionDefinition.PROPAGATION_SUPPORTS, attribute.getPropagationBehavior());
    }
  }

  @Test
  public void startAfterRestartStartsCampaign() {
    campaignService.startAfterRestart(5L);

    verify(dialerFacade).startCampaign(any());
  }

  /** Senkron çağrıdaki gibi aksiyon, çağıranın kimliğiyle (action log kullanıcısı) çalışmalı. */
  @Test
  public void actionAsyncRunsWithCallerSecurityContext() throws Exception {
    Authentication caller = new UsernamePasswordAuthenticationToken("operator", "n/a");
    SecurityContextHolder.getContext().setAuthentication(caller);
    AtomicReference<Authentication> seen = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);
    doAnswer(inv -> {
      seen.set(SecurityContextHolder.getContext().getAuthentication());
      done.countDown();
      return null;
    }).when(dialerFacade).shutdownCampaign(eq(3L), eq(CAMPAIGN_ACTION.STOP_REGULAR));

    campaignService.actionAsync(3L, CAMPAIGN_ACTION.STOP_REGULAR, null);

    assertTrue("Asenkron aksiyon çalışmalı", done.await(5, TimeUnit.SECONDS));
    assertSame(caller, seen.get());
  }

  private CountDownLatch blockStartOfCampaign(Long campaignId) throws InterruptedException {
    when(campaignDao.get(campaignId)).thenReturn(null);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    doAnswer(inv -> {
      entered.countDown();
      release.await(30, TimeUnit.SECONDS);
      return null;
    }).when(dialerFacade).startCampaign(any());
    worker = new Thread(() -> campaignService.action(campaignId, CAMPAIGN_ACTION.START, null), "stuck-start");
    worker.start();
    assertTrue("Start aksiyonu DialerFacade'e ulaşmalı", entered.await(5, TimeUnit.SECONDS));
    return release;
  }

  private static void awaitWaiting(Thread thread) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (thread.getState() != Thread.State.TIMED_WAITING && thread.getState() != Thread.State.WAITING
        && thread.getState() != Thread.State.BLOCKED) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Thread beklemeye geçmedi: " + thread.getState());
      }
      Thread.yield();
    }
  }

  private long count(String event) {
    return events.stream().filter(event::equals).count();
  }
}
