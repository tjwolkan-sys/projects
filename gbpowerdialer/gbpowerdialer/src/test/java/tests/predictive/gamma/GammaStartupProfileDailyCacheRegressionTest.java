package tests.predictive.gamma;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerdialer.dto.AgentMetricsDTO;
import com.powerdialer.dto.RingingMetricsDTO;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.predictive.gamma.calculator.PredictiveGammaCallCalculator;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCalculationResult;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallData;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaStartupProfile;
import com.powerdialer.engine.predictive.gamma.processor.HoltsTrendAnalyzer;
import com.powerdialer.engine.predictive.gamma.processor.PredictiveGammaCallManager;
import com.powerdialer.engine.predictive.gamma.processor.SharedGammaSnapshotProvider;
import com.powerdialer.model.Campaign;
import com.powerdialer.service.CDRService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * FIX-333607: GAMMA startup profilindeki dünkü ortalamalar (konuşma, çalma, ACW) gün içinde
 * değişmez; aynı gün yeniden start'ta ağır {@code pd_cdr} sorguları tekrar çalışmamalıdır.
 *
 * <p>02.10.2026'da C71, C85, C91 ve C134 gün içinde birden fazla kez start edildi ve her start'ta
 * aynı dakikalar süren sorgular baştan çalıştı. Bugünkü cevap oranı ise canlı veri olduğu için her
 * start'ta yeniden okunur. Bir sorgunun hatası diğer değerleri varsayılana düşürmez.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class GammaStartupProfileDailyCacheRegressionTest {

  private static final long CID = 42L;
  private static final double EPSILON = 1e-9;
  private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

  @Mock private PredictiveGammaCallCalculator calculator;
  @Mock private HoltsTrendAnalyzer holtsTrendAnalyzer;
  @Mock private CDRService cdrService;
  @Mock private SharedGammaSnapshotProvider sharedGammaSnapshotProvider;
  @Mock private CampaignRealTimeRingingManager ringingManager;
  @Mock private DialerFacade dialerFacade;
  @Mock private Campaign campaign;

  private PredictiveGammaCallManager manager;

  @Before
  public void setUp() {
    manager = new PredictiveGammaCallManager(calculator, holtsTrendAnalyzer, cdrService, sharedGammaSnapshotProvider);
    manager.setClock(clockAt("2026-10-02T09:00:00Z"));

    when(campaign.getId()).thenReturn(CID);
    lenient().when(campaign.getName()).thenReturn("Daily Cache Test");
    when(campaign.getGammaWindowCapacity()).thenReturn(400);
    when(campaign.getGammaRequiredWindowSize()).thenReturn(100);

    lenient().when(sharedGammaSnapshotProvider.getAgentMetrics(CID)).thenReturn(AgentMetricsDTO.zero());
    lenient().when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(ringingManager);
    lenient().when(ringingManager.getRealTimeRingingMetrics(campaign)).thenReturn(new RingingMetricsDTO());
    lenient().when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new PredictiveGammaCalculationResult(0.0, 0.0, 0.0, 0.0, new PredictiveGammaCallData(campaign, null)));

    lenient().when(cdrService.findGammaStartupAverageTalkTime(eq(CID), anyInt())).thenReturn(100.0);
    lenient().when(cdrService.findGammaStartupAverageRingTime(CID)).thenReturn(10.0);
    lenient().when(cdrService.findGammaStartupAverageAcwTime(CID)).thenReturn(5.0);
  }

  @Test
  public void secondStartOnSameDayReusesYesterdayAverages() {
    manager.onCampaignStart(campaign);
    manager.onCampaignStart(campaign);

    verify(cdrService, times(1)).findGammaStartupAverageTalkTime(eq(CID), anyInt());
    verify(cdrService, times(1)).findGammaStartupAverageRingTime(CID);
    verify(cdrService, times(1)).findGammaStartupAverageAcwTime(CID);
    verify(cdrService, times(2)).findGammaTodayAnswerStatsByType(CID);

    PredictiveGammaStartupProfile profile = lastStartupProfile();
    assertEquals(100.0, profile.getAvgTalkTime(), EPSILON);
    assertEquals(10.0, profile.getAvgRingTime(), EPSILON);
    assertEquals(5.0, profile.getAvgAcwTime(), EPSILON);
  }

  @Test
  public void averagesAreRecomputedOnNextDay() {
    manager.onCampaignStart(campaign);
    manager.setClock(clockAt("2026-10-03T06:00:00Z"));
    manager.onCampaignStart(campaign);

    verify(cdrService, times(2)).findGammaStartupAverageTalkTime(eq(CID), anyInt());
    verify(cdrService, times(2)).findGammaStartupAverageAcwTime(CID);
  }

  @Test
  public void missingAveragesAreNotCached() {
    when(cdrService.findGammaStartupAverageTalkTime(eq(CID), anyInt())).thenReturn(null, 100.0);

    manager.onCampaignStart(campaign);
    manager.onCampaignStart(campaign);

    verify(cdrService, times(2)).findGammaStartupAverageTalkTime(eq(CID), anyInt());
    assertEquals(100.0, lastStartupProfile().getAvgTalkTime(), EPSILON);
  }

  /**
   * ACW sorgusu zaman aşımında 0 döner (startup ACW varsayılanı da 0). Eskiden her start ACW'yi
   * yeniden deniyordu; zaman aşımı sonucu gün boyu önbellekte kalırsa o günkü bütün restart'lar
   * gerçek ACW yerine 0 ile başlar (agent'lar görüşme biter bitmez boş varsayılır).
   */
  @Test
  public void acwTimeoutFallbackIsNotCachedAndIsRetriedOnNextStart() {
    when(cdrService.findGammaStartupAverageAcwTime(CID)).thenReturn(0.0, 7.0);

    manager.onCampaignStart(campaign);
    manager.onCampaignStart(campaign);
    assertEquals(7.0, lastStartupProfile().getAvgAcwTime(), EPSILON);

    manager.onCampaignStart(campaign);

    verify(cdrService, times(1)).findGammaStartupAverageTalkTime(eq(CID), anyInt());
    verify(cdrService, times(1)).findGammaStartupAverageRingTime(CID);
    verify(cdrService, times(2)).findGammaStartupAverageAcwTime(CID);
    assertEquals(7.0, lastStartupProfile().getAvgAcwTime(), EPSILON);
  }

  @Test
  public void failingAverageQueryFallsBackOnlyForThatValue() {
    when(cdrService.findGammaStartupAverageTalkTime(eq(CID), anyInt())).thenThrow(new IllegalStateException("db failure"));

    manager.onCampaignStart(campaign);

    PredictiveGammaStartupProfile profile = lastStartupProfile();
    assertEquals("Hata veren konuşma ortalaması varsayılana düşmeli", 200.0, profile.getAvgTalkTime(), EPSILON);
    assertEquals("Diğer değerler korunmalı", 10.0, profile.getAvgRingTime(), EPSILON);
    assertEquals(5.0, profile.getAvgAcwTime(), EPSILON);
  }

  private PredictiveGammaStartupProfile lastStartupProfile() {
    manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);
    ArgumentCaptor<PredictiveGammaStartupProfile> captor = ArgumentCaptor.forClass(PredictiveGammaStartupProfile.class);
    verify(calculator, org.mockito.Mockito.atLeastOnce()).calculateXn(eq(campaign), any(), any(), any(), any(), any(), any(), any(),
        captor.capture(), anyInt(), anyInt());
    List<PredictiveGammaStartupProfile> all = captor.getAllValues();
    return all.get(all.size() - 1);
  }

  private static Clock clockAt(String instant) {
    return Clock.fixed(Instant.parse(instant), ZONE);
  }
}
