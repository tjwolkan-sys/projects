package tests.predictive.gamma;

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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gamma startup answer rate sabitlerinin {@link PredictiveGammaCallManager#onCampaignStart(Campaign)}
 * tarafından üretilen {@link PredictiveGammaStartupProfile} içinde doğru aktarıldığını kilitler.
 *
 * <p>Sabitler kampanya start anında üç tipte (callback/retry/fresh) startup profile içine yazılır.
 * Profile sonradan {@link PredictiveGammaCallCalculator#calculateXn} çağrılarında AR baseline'ı
 * olarak kullanılır; ilk ~400 çağrıya kadar live AR'a blendlenir. Bu yüzden sabit değerler
 * üretim AR'larına yakın olmalıdır.
 *
 * <p>CDRService startup ortalama değerlerini (talk/ring/acw) sağlar; bu test AR sabitlerine
 * odaklanır ve avg değerler için default fallback davranışını da kilitler.
 */
@RunWith(MockitoJUnitRunner.class)
public class PredictiveGammaCallManagerStartupAnswerRateTest {

    private static final long CID = 42L;
    private static final double EPSILON = 1e-9;

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

        when(campaign.getId()).thenReturn(CID);
        lenient().when(campaign.getName()).thenReturn("Startup AR Test");
        when(campaign.getGammaWindowCapacity()).thenReturn(400);
        when(campaign.getGammaRequiredWindowSize()).thenReturn(100);

        lenient().when(sharedGammaSnapshotProvider.getAgentMetrics(CID)).thenReturn(AgentMetricsDTO.zero());
        lenient().when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(ringingManager);
        lenient().when(ringingManager.getRealTimeRingingMetrics(campaign)).thenReturn(new RingingMetricsDTO());
        lenient().when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PredictiveGammaCalculationResult(0.0, 0.0, 0.0, 0.0, new PredictiveGammaCallData(campaign, null)));
    }

    @Test
    public void startupProfile_setsCallbackAnswerRateForGamma() {
        manager.onCampaignStart(campaign);
        manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        PredictiveGammaStartupProfile captured = captureStartupProfile();

        assertEquals("Callback startup AR uretim AR'lerine yakin olmali (0.50)",
                0.50d, captured.getCbAnswerRate(), EPSILON);
    }

    @Test
    public void startupProfile_setsRetryAnswerRateForGamma() {
        manager.onCampaignStart(campaign);
        manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        PredictiveGammaStartupProfile captured = captureStartupProfile();

        assertEquals("Retry startup AR uretim AR'lerine yakin olmali (0.40)",
                0.40d, captured.getRetryAnswerRate(), EPSILON);
    }

    @Test
    public void startupProfile_setsFreshAnswerRateForGamma() {
        manager.onCampaignStart(campaign);
        manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        PredictiveGammaStartupProfile captured = captureStartupProfile();

        assertEquals("Fresh startup AR uretim AR'lerine yakin olmali (0.50)",
                0.50d, captured.getFreshAnswerRate(), EPSILON);
    }

    @Test
    public void startupProfile_usesFallbackAveragesWhenCdrServiceReturnsNull() {
        // CDRService null donduren durum -> sanitizeAverage default sabitlere fallback
        when(cdrService.findGammaStartupAverageTalkTime(eq(CID), anyInt())).thenReturn(null);
        when(cdrService.findGammaStartupAverageRingTime(eq(CID))).thenReturn(null);
        when(cdrService.findGammaStartupAverageAcwTime(eq(CID))).thenReturn(null);

        manager.onCampaignStart(campaign);
        manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        PredictiveGammaStartupProfile captured = captureStartupProfile();

        // CDRService null dondu, bugune ait AR verisi de yok
        // -> AR'ler guvenli sabitlere dusmeli
        assertEquals(0.50d, captured.getCbAnswerRate(), EPSILON);
        assertEquals(0.40d, captured.getRetryAnswerRate(), EPSILON);
        assertEquals(0.50d, captured.getFreshAnswerRate(), EPSILON);

        // Avg'ler default fallback degerleri olmali (talk=200, ring=30, acw=0)
        assertEquals(200.0d, captured.getAvgTalkTime(), EPSILON);
        assertEquals(30.0d, captured.getAvgRingTime(), EPSILON);
        assertEquals(0.0d, captured.getAvgAcwTime(), EPSILON);
    }

    @Test
    public void startupProfile_usesTodayAnswerRateWhenFreshSampleAboveThreshold() {
        // FRESH bugun 500 cagri (>= windowCapacity 400), 200 cevaplanmis -> gercek AR 0.40, sabit (0.50) degil
        when(cdrService.findGammaTodayAnswerStatsByType(CID))
                .thenReturn(rows(typeRow(1L, 500L, 200L)));

        manager.onCampaignStart(campaign);
        manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        PredictiveGammaStartupProfile captured = captureStartupProfile();

        assertEquals("Yeterli veride fresh AR gercek gunluk degerden gelmeli (200/500)",
                0.40d, captured.getFreshAnswerRate(), EPSILON);
    }

    @Test
    public void startupProfile_fallsBackToConstantWhenFreshSampleBelowThreshold() {
        // FRESH bugun yalniz 10 cagri (< windowCapacity 400) -> sabite dusmeli (0.50)
        when(cdrService.findGammaTodayAnswerStatsByType(CID))
                .thenReturn(rows(typeRow(1L, 10L, 9L)));

        manager.onCampaignStart(campaign);
        manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        PredictiveGammaStartupProfile captured = captureStartupProfile();

        assertEquals("Yetersiz veride fresh AR sabite dusmeli (0.50)",
                0.50d, captured.getFreshAnswerRate(), EPSILON);
    }

    @Test
    public void startupProfile_callbackUsesHalfThreshold() {
        // CALLBACK esigi windowCapacity/2 = 200. total=250 (>=200) -> gercek AR 0.60 (150/250)
        when(cdrService.findGammaTodayAnswerStatsByType(CID))
                .thenReturn(rows(typeRow(4L, 250L, 150L)));

        manager.onCampaignStart(campaign);
        manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        PredictiveGammaStartupProfile captured = captureStartupProfile();

        assertEquals("Callback yarim esikte (250>=200) gercek AR kullanilmali (150/250)",
                0.60d, captured.getCbAnswerRate(), EPSILON);
    }

    @Test
    public void startupProfile_callbackFallsBackBelowHalfThreshold() {
        // CALLBACK total=150 (< windowCapacity/2 = 200) -> sabite dusmeli (0.50)
        when(cdrService.findGammaTodayAnswerStatsByType(CID))
                .thenReturn(rows(typeRow(4L, 150L, 140L)));

        manager.onCampaignStart(campaign);
        manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        PredictiveGammaStartupProfile captured = captureStartupProfile();

        assertEquals("Callback yarim esik altinda sabite dusmeli (0.50)",
                0.50d, captured.getCbAnswerRate(), EPSILON);
    }

    @Test
    public void startupProfile_retryAggregatesImmediateAndScheduledTypes() {
        // RETRY = immediate_retry(2) + scheduled_retry(3) toplanir: total 300+200=500 (>=400), answered 120+80=200 -> 0.40
        when(cdrService.findGammaTodayAnswerStatsByType(CID))
                .thenReturn(rows(typeRow(2L, 300L, 120L), typeRow(3L, 200L, 80L)));

        manager.onCampaignStart(campaign);
        manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        PredictiveGammaStartupProfile captured = captureStartupProfile();

        assertEquals("Retry immediate+scheduled toplanip gercek AR vermeli (200/500)",
                0.40d, captured.getRetryAnswerRate(), EPSILON);
    }

    @Test
    public void startupProfile_emptyTodayStatsFallsBackToAllConstants() {
        // Bugun hic veri yok -> tum tipler sabite dusmeli
        when(cdrService.findGammaTodayAnswerStatsByType(CID))
                .thenReturn(java.util.Collections.emptyList());

        manager.onCampaignStart(campaign);
        manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        PredictiveGammaStartupProfile captured = captureStartupProfile();

        assertEquals(0.50d, captured.getCbAnswerRate(), EPSILON);
        assertEquals(0.40d, captured.getRetryAnswerRate(), EPSILON);
        assertEquals(0.50d, captured.getFreshAnswerRate(), EPSILON);
    }

    private Object[] typeRow(long outdialingTypeId, long total, long answered) {
        return new Object[]{outdialingTypeId, total, answered};
    }

    private java.util.List<Object[]> rows(Object[]... rows) {
        return new java.util.ArrayList<>(java.util.Arrays.asList(rows));
    }

    private PredictiveGammaStartupProfile captureStartupProfile() {
        ArgumentCaptor<PredictiveGammaStartupProfile> captor = ArgumentCaptor.forClass(PredictiveGammaStartupProfile.class);
        verify(calculator).calculateXn(eq(campaign), any(), any(), any(), any(), any(), any(), any(),
                captor.capture(), anyInt(), anyInt());
        return captor.getValue();
    }
}
