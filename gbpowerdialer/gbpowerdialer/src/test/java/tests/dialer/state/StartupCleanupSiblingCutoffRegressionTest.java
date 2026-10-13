package tests.dialer.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.CampaignFilterStatsCalculator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.dialer.SharedResultTableSiblings;
import com.powerdialer.engine.dialer.state.StopState;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

/**
 * FIX-333599 — Startup DB check cleanup'ı, aynı result tablosunu kullanan ve o anda canlı olan
 * kardeş kampanyanın rezervasyonlarına dokunmamalı.
 *
 * <p>Vaka (02.10.2026): campaign 6 ({@code KFK_TAHSILAT}) 09:00:16.493'te canlıya geçti ve
 * 16.8'de ilk batch'ini rezerve etti. Campaign 17 ({@code KFK_TAHSILAT_2_DONEM}, aynı tablo,
 * {@code startup_db_check=1}) 16.583'te tablo genelinde cleanup çalıştırıp bu rezervasyonları sildi.</p>
 *
 * <p>Kesme anı kardeşin STOP→START anıdır ({@code CampaignContext.liveSince}); {@code startedDate}
 * kullanılamaz çünkü unpause'ta da güncellenir ve pause öncesi canlı rezervasyonları bayat gösterirdi.
 * O andan önceki rezervasyonlar kardeşin canlı rezervasyonu olamaz (çökme kalıntısı); temizlenmeye
 * devam eder.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class StartupCleanupSiblingCutoffRegressionTest {

  private static final String KFK_DB = "jdbc:sqlserver://campaign-db-host;databaseName=camp";
  private static final String OTHER_DB = "jdbc:sqlserver://other-db-host;databaseName=camp";

  /** 02.10.2026 09:00:16.583 — vakada campaign 17 cleanup'ının çalıştığı an. */
  private static final long CLEANUP_START = 1790920816583L;
  /** 02.10.2026 09:00:16.493 — vakada campaign 6'nın canlıya geçtiği an (cleanup'tan önce). */
  private static final long SIBLING_LIVE_BEFORE_CLEANUP_START = 1790920816493L;
  /** Cleanup başladıktan 200 ms sonra canlıya geçen kardeş (FIX-333607 paralel start). */
  private static final long SIBLING_LIVE_AFTER_CLEANUP_START = CLEANUP_START + 200L;
  /** Kardeş taramasından sonra okunan saat; kesme olarak kullanılmamalı. */
  private static final long AFTER_SCAN = CLEANUP_START + 5_000L;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private DialerFacade dialerFacade;

  @Mock
  private CampaignDbOperations campaignDbOperations;

  @Mock
  private CampaignFilterStatsCalculator campaignFilterStatsCalculator;

  private final Map<Long, CampaignContext> campaignContexts = new ConcurrentHashMap<>();

  @Before
  public void setUp() {
    when(dialerFacade.getCampaignContexts()).thenReturn(campaignContexts);
    when(dialerFacade.getCampaignDbOperations()).thenReturn(campaignDbOperations);
  }

  @Test
  public void noSiblingMeansNoCutoff() {
    CampaignContext self = context(17L, KFK_DB, "KFK_TAHSILAT", true);

    assertNull(SharedResultTableSiblings.earliestLiveSince(self, Collections.singletonList(self)));
  }

  @Test
  public void liveSiblingOnSameResultTableGivesItsLiveSince() {
    CampaignContext self = context(17L, KFK_DB, "KFK_TAHSILAT", true);
    CampaignContext sibling = live(context(6L, KFK_DB, "kfk_tahsilat", true), 1000L);

    assertEquals(new Date(1000L), SharedResultTableSiblings.earliestLiveSince(self, Arrays.asList(self, sibling)));
  }

  @Test
  public void differentTableOrDifferentDatabaseIsNotASibling() {
    CampaignContext self = context(17L, KFK_DB, "KFK_TAHSILAT", true);
    CampaignContext otherTable = live(context(7L, KFK_DB, "KFK_TAHSILAT_TEST", true), 1000L);
    CampaignContext otherDb = live(context(8L, OTHER_DB, "KFK_TAHSILAT", true), 2000L);

    assertNull(SharedResultTableSiblings.earliestLiveSince(self, Arrays.asList(self, otherTable, otherDb)));
  }

  @Test
  public void earliestLiveSinceWinsAmongSeveralSiblings() {
    CampaignContext self = context(17L, KFK_DB, "KFK_TAHSILAT", true);
    CampaignContext later = live(context(6L, KFK_DB, "KFK_TAHSILAT", true), 5000L);
    CampaignContext earlier = live(context(12L, KFK_DB, "KFK_TAHSILAT", true), 3000L);

    assertEquals(new Date(3000L), SharedResultTableSiblings.earliestLiveSince(self, Arrays.asList(later, self, earlier)));
  }

  /** Canlıya geçiş anı bilinmeyen kardeş: hiçbir rezervasyon onun değilmiş gibi silinmemeli. */
  @Test
  public void siblingWithUnknownLiveSinceProtectsAllReservations() {
    CampaignContext self = context(17L, KFK_DB, "KFK_TAHSILAT", true);
    CampaignContext unknown = context(6L, KFK_DB, "KFK_TAHSILAT", true);

    assertEquals(new Date(0L), SharedResultTableSiblings.earliestLiveSince(self, Arrays.asList(self, unknown)));
  }

  /** Vakanın birebir karşılığı: 17'nin start'ı, canlı 6'nın canlıya geçiş anını kesme olarak geçer. */
  @Test
  public void startupCleanupPassesSiblingLiveSinceAsCutoff() throws Exception {
    CampaignContext campaign6 = live(context(6L, KFK_DB, "KFK_TAHSILAT", true), 1790920816493L);
    campaignContexts.put(6L, campaign6);
    CampaignContext campaign17 = context(17L, KFK_DB, "KFK_TAHSILAT", true);

    checkForUnfinishedCalls(campaign17);

    verify(campaignDbOperations).removeFetchedDataFlagFromUnfinishedCalls(campaign17.getCampaign(), new Date(1790920816493L));
  }

  /**
   * 05.10.2026: kardeş görünmese de kesme uygulanır; kesme cleanup'ın başladığı andır. Kampanya bu
   * koşuda henüz rezervasyon yapmadığı için (producer {@code super.start()} ile başlar) bu andan
   * sonraki hiçbir rezervasyon bayat olamaz; görünmeyen kardeşin veya başka node'un rezervasyonudur.
   */
  @Test
  public void startupCleanupWithoutVisibleSiblingCutsAtCleanupStart() throws Exception {
    CampaignContext campaign17 = context(17L, KFK_DB, "KFK_TAHSILAT", true);

    checkForUnfinishedCalls(new StopState(campaign17, () -> new Date(CLEANUP_START)));

    verify(campaignDbOperations).removeFetchedDataFlagFromUnfinishedCalls(campaign17.getCampaign(), new Date(CLEANUP_START));
  }

  /** Üretim constructor'ı kesmeyi sistem saatinden alır. */
  @Test
  public void productionConstructorCutsAtCurrentTime() throws Exception {
    CampaignContext campaign17 = context(17L, KFK_DB, "KFK_TAHSILAT", true);
    long before = System.currentTimeMillis();

    checkForUnfinishedCalls(campaign17);

    long after = System.currentTimeMillis();
    ArgumentCaptor<Date> cutoff = ArgumentCaptor.forClass(Date.class);
    verify(campaignDbOperations).removeFetchedDataFlagFromUnfinishedCalls(eq(campaign17.getCampaign()), cutoff.capture());
    assertNotNull(cutoff.getValue());
    assertTrue(cutoff.getValue().getTime() >= before && cutoff.getValue().getTime() <= after);
  }

  /**
   * FIX-333607 sonrası paralel start yarışı: campaign 6, campaign 17'nin cleanup taraması sırasında
   * henüz canlı değil ve cleanup yürürken canlıya geçip rezervasyon yapıyor. Rezervasyonları canlıya
   * geçiş anından sonradır; kesme bu andan önce kalmalı ki {@code is_fetched < kesme} onları silmesin.
   */
  @Test
  public void siblingGoingLiveWhileCleanupRunsKeepsItsReservations() throws Exception {
    CampaignContext campaign17 = context(17L, KFK_DB, "KFK_TAHSILAT", true);
    CampaignContext campaign6 = context(6L, KFK_DB, "KFK_TAHSILAT", true);
    doAnswer(invocation -> {
      campaignContexts.put(6L, live(campaign6, SIBLING_LIVE_AFTER_CLEANUP_START));
      return null;
    }).when(campaignDbOperations).removeFetchedDataFlagFromUnfinishedCalls(any(Campaign.class), any());

    checkForUnfinishedCalls(new StopState(campaign17, () -> new Date(CLEANUP_START)));

    ArgumentCaptor<Date> cutoff = ArgumentCaptor.forClass(Date.class);
    verify(campaignDbOperations).removeFetchedDataFlagFromUnfinishedCalls(eq(campaign17.getCampaign()), cutoff.capture());
    assertNotNull("paralel start'ta görünmeyen kardeşin rezervasyonları korunmalı", cutoff.getValue());
    assertTrue(cutoff.getValue().getTime() <= campaign6.getLiveSince().getTime());
  }

  /**
   * Kesme = min(cleanup anı, kardeş anı). Kardeş cleanup başladıktan sonra canlıya geçtiyse kesme
   * ileri itilmez; aradaki rezervasyonlar (görünmeyen başka kardeş, başka node) korunur.
   */
  @Test
  public void siblingLiveAfterCleanupStartDoesNotPushCutoffForward() throws Exception {
    campaignContexts.put(6L, live(context(6L, KFK_DB, "KFK_TAHSILAT", true), SIBLING_LIVE_AFTER_CLEANUP_START));
    CampaignContext campaign17 = context(17L, KFK_DB, "KFK_TAHSILAT", true);

    checkForUnfinishedCalls(new StopState(campaign17, () -> new Date(CLEANUP_START)));

    verify(campaignDbOperations).removeFetchedDataFlagFromUnfinishedCalls(campaign17.getCampaign(), new Date(CLEANUP_START));
  }

  /** Kardeş cleanup başlamadan önce canlıya geçtiyse kesme kardeşin anıdır (02.10 vakası). */
  @Test
  public void siblingLiveBeforeCleanupStartGivesItsLiveSince() throws Exception {
    campaignContexts.put(6L, live(context(6L, KFK_DB, "KFK_TAHSILAT", true), SIBLING_LIVE_BEFORE_CLEANUP_START));
    CampaignContext campaign17 = context(17L, KFK_DB, "KFK_TAHSILAT", true);

    checkForUnfinishedCalls(new StopState(campaign17, () -> new Date(CLEANUP_START)));

    verify(campaignDbOperations).removeFetchedDataFlagFromUnfinishedCalls(campaign17.getCampaign(), new Date(SIBLING_LIVE_BEFORE_CLEANUP_START));
  }

  /** Canlıya geçiş anı bilinmeyen kardeş varken cleanup hiçbir rezervasyonu silmez (Date(0) kesmesi). */
  @Test
  public void siblingWithUnknownLiveSinceStillReleasesNothing() throws Exception {
    campaignContexts.put(6L, context(6L, KFK_DB, "KFK_TAHSILAT", true));
    CampaignContext campaign17 = context(17L, KFK_DB, "KFK_TAHSILAT", true);

    checkForUnfinishedCalls(new StopState(campaign17, () -> new Date(CLEANUP_START)));

    verify(campaignDbOperations).removeFetchedDataFlagFromUnfinishedCalls(campaign17.getCampaign(), new Date(0L));
  }

  /**
   * Sıra kuralı: cleanup anı kardeş taramasından ÖNCE alınır. Tarama sonrasında alınsaydı, tarama ile
   * an arasında canlıya geçip rezervasyon yapan kardeş ne taramada görünür ne de kesmenin dışında
   * kalırdı.
   */
  @Test
  public void cleanupStartIsTakenBeforeScanningSiblings() throws Exception {
    AtomicBoolean scanned = new AtomicBoolean();
    Map<Long, CampaignContext> scanAwareContexts = new ConcurrentHashMap<Long, CampaignContext>() {
      @Override
      public Collection<CampaignContext> values() {
        scanned.set(true);
        return super.values();
      }
    };
    when(dialerFacade.getCampaignContexts()).thenReturn(scanAwareContexts);
    CampaignContext campaign17 = context(17L, KFK_DB, "KFK_TAHSILAT", true);

    checkForUnfinishedCalls(new StopState(campaign17, () -> new Date(scanned.get() ? AFTER_SCAN : CLEANUP_START)));

    verify(campaignDbOperations).removeFetchedDataFlagFromUnfinishedCalls(campaign17.getCampaign(), new Date(CLEANUP_START));
  }

  /** Karakterizasyon: Startup DB Check kapalıysa cleanup hiç çalışmaz. */
  @Test
  public void startupCleanupDisabledIssuesNoStatement() throws Exception {
    CampaignContext campaign6 = context(6L, KFK_DB, "KFK_TAHSILAT", false);

    checkForUnfinishedCalls(campaign6);

    verify(campaignDbOperations, never()).removeFetchedDataFlagFromUnfinishedCalls(any(), any());
    verify(campaignDbOperations, never()).removeFetchedDataFlagFromUnfinishedCalls(any());
  }

  /** STOP→START anı, context haritaya eklenmeden önce işaretlenir; kardeş cleanup'ı onu hep görür. */
  @Test
  public void registeringAsLiveStampsLiveSinceBeforeContextBecomesVisible() throws Exception {
    CampaignContext campaign6 = context(6L, KFK_DB, "KFK_TAHSILAT", false);
    long before = System.currentTimeMillis();

    Method register = StopState.class.getDeclaredMethod("registerAsLive");
    register.setAccessible(true);
    register.invoke(new StopState(campaign6));

    assertSame(campaign6, campaignContexts.get(6L));
    assertNotNull(campaign6.getLiveSince());
    assertTrue(campaign6.getLiveSince().getTime() >= before);
  }

  private void checkForUnfinishedCalls(CampaignContext context) throws Exception {
    checkForUnfinishedCalls(new StopState(context));
  }

  private void checkForUnfinishedCalls(StopState stopState) throws Exception {
    Method method = StopState.class.getDeclaredMethod("checkForUnfinishedCalls");
    method.setAccessible(true);
    method.invoke(stopState);
  }

  private CampaignContext context(long id, String connectionString, String resultTable, boolean startupDbCheck) {
    Campaign campaign = new Campaign();
    campaign.setId(id);
    campaign.setDbConnectionString(connectionString);
    campaign.setDbListTable(resultTable);
    campaign.setDbResultTable(resultTable);
    campaign.setStartupDbCheck(startupDbCheck);
    campaign.setCacheLimit(6);
    campaign.setConcurrentLines(25);
    campaign.setEndListBehaviour(Constants.END_LIST_BEHAVIOUR.CONTINUE);
    return new CampaignContext(campaign, dialerFacade, campaignFilterStatsCalculator);
  }

  private static CampaignContext live(CampaignContext context, long liveSinceMillis) {
    context.markLive(new Date(liveSinceMillis));
    return context;
  }
}
