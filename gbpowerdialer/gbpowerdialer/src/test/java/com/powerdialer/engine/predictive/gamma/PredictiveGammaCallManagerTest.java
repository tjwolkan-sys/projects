package com.powerdialer.engine.predictive.gamma;

import com.powerdialer.dto.AgentMetricsDTO;
import com.powerdialer.dto.RingingMetricsDTO;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCalculationResult;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallData;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaSnapshot;
import com.powerdialer.engine.predictive.gamma.calculator.PredictiveGammaCallCalculator;
import com.powerdialer.engine.predictive.gamma.processor.HoltsTrendAnalyzer;
import com.powerdialer.engine.predictive.gamma.processor.PredictiveGammaCallManager;
import com.powerdialer.engine.predictive.gamma.processor.SharedGammaSnapshotProvider;
import com.powerdialer.model.Call;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.service.CDRService;
import com.powerdialer.util.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PredictiveGammaCallManagerTest {

    @Mock private PredictiveGammaCallCalculator calculator;
    @Mock private HoltsTrendAnalyzer holtsTrendAnalyzer;
    @Mock private CDRService cdrService;
    @Mock private SharedGammaSnapshotProvider sharedGammaSnapshotProvider;
    @Mock private Campaign campaign;
    @Mock private DialerFacade dialerFacade;
    @Mock private CampaignRealTimeRingingManager ringingManager;

    private PredictiveGammaCallManager manager;

    private static final long CID = 1L;

    @Before
    public void setUp() {
        manager = new PredictiveGammaCallManager(calculator, holtsTrendAnalyzer, cdrService, sharedGammaSnapshotProvider);
        when(campaign.getId()).thenReturn(CID);
        lenient().when(campaign.getName()).thenReturn("Gamma Test");
    }

    @Test
    public void allowedDialNow_missingGammaConfig_returnsZero() {
        int allowed = manager.allowedDialNow(campaign, 5, 3, dialerFacade);

        assertEquals(0, allowed);
        verify(calculator, never()).calculateXn(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    public void starvationCredit_accumulatesOnlyWhenSharedIdleAndCanBoostMissingBudget() {
        RingingMetricsDTO noRinging = new RingingMetricsDTO();
        when(campaign.getCacheSize()).thenReturn(1);
        when(campaign.getQueueSize()).thenReturn(0);
        when(sharedGammaSnapshotProvider.isSharedCampaign(CID)).thenReturn(true);
        when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(ringingManager);
        when(ringingManager.getRealTimeRingingMetrics(campaign)).thenReturn(noRinging);

        manager.updateStarvationCredit(campaign, 0.30, 0.90, 0, dialerFacade);
        assertEquals(0.0, manager.consumeStarvationBoost(CID, 0.30, 0.90, 1, 1), 0.0001);

        manager.updateStarvationCredit(campaign, 0.30, 0.90, 0, dialerFacade);
        assertEquals(0.60, manager.consumeStarvationBoost(CID, 0.30, 0.90, 1, 1), 0.0001);
        assertEquals(0.0, manager.consumeStarvationBoost(CID, 0.30, 0.90, 1, 1), 0.0001);
    }

    @Test
    public void starvationCredit_resetsWhenQueueHasCalls() {
        when(campaign.getCacheSize()).thenReturn(1);
        when(campaign.getQueueSize()).thenReturn(0, 1);
        when(sharedGammaSnapshotProvider.isSharedCampaign(CID)).thenReturn(true);
        when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(ringingManager);
        when(ringingManager.getRealTimeRingingMetrics(campaign)).thenReturn(new RingingMetricsDTO());

        manager.updateStarvationCredit(campaign, 0.60, 0.90, 0, dialerFacade);
        manager.updateStarvationCredit(campaign, 0.10, 0.90, 0, dialerFacade);

        assertEquals(0.0, manager.consumeStarvationBoost(CID, 0.30, 0.90, 1, 1), 0.0001);
    }

    @Test
    public void starvationBoost_doesNotCreateCallWhenCurrentBudgetIsZero() {
        RingingMetricsDTO noRinging = new RingingMetricsDTO();
        when(campaign.getCacheSize()).thenReturn(1);
        when(campaign.getQueueSize()).thenReturn(0);
        when(sharedGammaSnapshotProvider.isSharedCampaign(CID)).thenReturn(true);
        when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(ringingManager);
        when(ringingManager.getRealTimeRingingMetrics(campaign)).thenReturn(noRinging);

        manager.updateStarvationCredit(campaign, 0.60, 0.90, 0, dialerFacade);

        assertEquals(0.0, manager.consumeStarvationBoost(CID, 0.0, 0.90, 1, 1), 0.0001);
    }

    @Test
    public void calculateDialBudget_populatesStarvationTelemetryOnCalculationData() {
        configureGammaCampaign();
        when(cdrService.findGammaStartupAverageTalkTime(CID, 10)).thenReturn(100.0);
        when(cdrService.findGammaStartupAverageRingTime(CID)).thenReturn(10.0);
        when(cdrService.findGammaStartupAverageAcwTime(CID)).thenReturn(0.0);
        manager.onCampaignStart(campaign);

        RingingMetricsDTO noRinging = new RingingMetricsDTO();
        PredictiveGammaCallData data = new PredictiveGammaCallData(campaign, null);
        when(campaign.getCacheSize()).thenReturn(1);
        when(campaign.getQueueSize()).thenReturn(0);
        when(sharedGammaSnapshotProvider.isSharedCampaign(CID)).thenReturn(true);
        when(sharedGammaSnapshotProvider.getAgentMetrics(CID)).thenReturn(new AgentMetricsDTO(1.0, 0.5, 0.0, 0.5, 0.0));
        when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(ringingManager);
        when(ringingManager.getRealTimeRingingMetrics(campaign)).thenReturn(noRinging);
        when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PredictiveGammaCalculationResult(0.30, 0.90, 0.90, 0.90, data));

        manager.updateStarvationCredit(campaign, 0.60, 0.90, 0, dialerFacade);

        Call nextCall = new Call();
        nextCall.setCallType(Constants.CALL_TYPES.FRESH);
        PredictiveGammaCallManager.GammaDialBudget budget = manager.calculateDialBudget(campaign, 1, 1, dialerFacade, nextCall);

        assertEquals(0.30, budget.getBaseDialBudget(), 0.0001);
        assertEquals(0.60, budget.getStarvationBoost(), 0.0001);
        assertEquals(0.90, budget.getTotalDialBudget(), 0.0001);
        assertTrue(data.getIsSharedCampaign());
        assertTrue(data.getStarvationEligible());
        assertTrue(data.getStarvationApplied());
        assertEquals(0.60, data.getStarvationCreditBefore(), 0.0001);
        assertEquals(0.60, data.getStarvationBoost(), 0.0001);
        assertEquals(0.60, data.getStarvationCreditAfter(), 0.0001);
        assertEquals(0.90, data.getTotalDialBudget(), 0.0001);
        assertEquals("FRESH", data.getNextCallType());
        assertEquals(0.90, data.getNextCallCost(), 0.0001);
    }

    @Test
    public void calculateDialBudget_marksStarvationIneligibleForSingleCampaign() {
        configureGammaCampaign();
        when(cdrService.findGammaStartupAverageTalkTime(CID, 10)).thenReturn(100.0);
        when(cdrService.findGammaStartupAverageRingTime(CID)).thenReturn(10.0);
        when(cdrService.findGammaStartupAverageAcwTime(CID)).thenReturn(0.0);
        manager.onCampaignStart(campaign);

        PredictiveGammaCallData data = new PredictiveGammaCallData(campaign, null);
        when(sharedGammaSnapshotProvider.isSharedCampaign(CID)).thenReturn(false);
        when(sharedGammaSnapshotProvider.getAgentMetrics(CID)).thenReturn(new AgentMetricsDTO(1.0, 1.0, 0.0, 1.0, 0.0));
        when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(ringingManager);
        when(ringingManager.getRealTimeRingingMetrics(campaign)).thenReturn(new RingingMetricsDTO());
        when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PredictiveGammaCalculationResult(0.30, 0.90, 0.90, 0.90, data));

        PredictiveGammaCallManager.GammaDialBudget budget = manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        assertEquals(0.0, budget.getStarvationBoost(), 0.0001);
        assertFalse(data.getIsSharedCampaign());
        assertFalse(data.getStarvationEligible());
        assertFalse(data.getStarvationApplied());
        assertEquals("FRESH", data.getNextCallType());
    }

    @Test
    public void onCallEnded_usesCallAbandonedFlagWithoutLegacyResultCodeTenFallback() {
        configureAndStartCampaign();
        Call call = gammaCall(10L, true, false, false);

        manager.onCallEnded(call, 0, 3);

        PredictiveGammaSnapshot freshSnapshot = calculateAndCaptureFreshSnapshot();

        assertEquals(1, freshSnapshot.getTotalCallCount());
        assertEquals(0, freshSnapshot.getTotalAbandonCallCount());
    }

    @Test
    public void onCallEnded_preservesEvaluatorAbandonedFlagForTrc84() {
        configureAndStartCampaign();
        Call call = gammaCall(Long.valueOf(Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION), true, false, true);

        manager.onCallEnded(call, 0, 3);

        PredictiveGammaSnapshot freshSnapshot = calculateAndCaptureFreshSnapshot();

        assertEquals(1, freshSnapshot.getTotalCallCount());
        assertEquals(1, freshSnapshot.getTotalAbandonCallCount());
        assertEquals(1, freshSnapshot.getTotalAbandonedByCustomer());
    }

    private void configureGammaCampaign() {
        when(campaign.getGammaWindowCapacity()).thenReturn(400);
        when(campaign.getGammaRequiredWindowSize()).thenReturn(100);
    }

    private void configureAndStartCampaign() {
        configureGammaCampaign();
        when(cdrService.findGammaStartupAverageTalkTime(CID, 10)).thenReturn(100.0);
        when(cdrService.findGammaStartupAverageRingTime(CID)).thenReturn(10.0);
        when(cdrService.findGammaStartupAverageAcwTime(CID)).thenReturn(0.0);
        manager.onCampaignStart(campaign);
    }

    private Call gammaCall(Long resultCodeId, boolean answered, boolean answeredByUser, boolean abandoned) {
        TelephonyResultCode trc = new TelephonyResultCode();
        trc.setId(resultCodeId);

        Call call = new Call();
        call.setCampaign(campaign);
        call.setCallType(Constants.CALL_TYPES.FRESH);
        call.setTelephonyResultCode(trc);
        call.getCdr().setId(100L + resultCodeId);
        call.getCdr().setTelephonyRC(trc);
        call.setAnswerDate(answered ? 1.0 : null);
        call.setAnsweredByUser(answeredByUser);
        call.setAbandoned(abandoned);
        return call;
    }

    private PredictiveGammaSnapshot calculateAndCaptureFreshSnapshot() {
        when(sharedGammaSnapshotProvider.getAgentMetrics(CID)).thenReturn(AgentMetricsDTO.zero());
        when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(ringingManager);
        when(ringingManager.getRealTimeRingingMetrics(campaign)).thenReturn(new RingingMetricsDTO());
        when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PredictiveGammaCalculationResult(0.0, 1.0, 1.0, 1.0, new PredictiveGammaCallData(campaign, null)));

        manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        ArgumentCaptor<PredictiveGammaSnapshot> freshSnapshotCaptor = ArgumentCaptor.forClass(PredictiveGammaSnapshot.class);
        verify(calculator).calculateXn(eq(campaign), any(), any(), any(), any(), freshSnapshotCaptor.capture(), any(), any(), any(), anyInt(), anyInt());
        return freshSnapshotCaptor.getValue();
    }

//    @Mock private PredictiveGammaCallCalculator calculator;
//    @Mock private ACDEngine acdEngine;
//    @Mock private CDRService cdrService;
//    @Mock private Campaign campaign;
//
//    private PredictiveGammaCallManager manager;
//
//    private static final long CID = 1L;
//
//    @Before
//    public void setUp() {
//        manager = new PredictiveGammaCallManager(calculator, acdEngine, cdrService);
//        when(campaign.getId()).thenReturn(CID);
//        lenient().when(campaign.getPredictiveStrategy()).thenReturn(Constants.PREDICTIVE_STRATEGY.GAMMA);
//    }
//
//    /** Helper: generate n unique Users */
//    private Set<User> generateUsers(int n) {
//        Set<User> s = new HashSet<>();
//        long id = 0L;
//        for (int i = 0; i < n; i++) {
//            User u = new User();
//            u.setId(++id);
//            u.setUsername("u" + i);
//            s.add(u);
//        }
//        return s;
//    }
//
//    /** Helper: end the specified number of (GAMMA) calls */
//    private void addGammaCalls(int n) {
//        for (int i = 0; i < n; i++) {
//            Call call = new Call();
//            call.setCampaign(campaign);
//            manager.onCallEnded(call, 10 + i, 3);
//        }
//    }
//
//    private PredictiveGammaCalculationResult resultOf(double xn) {
//        return new PredictiveGammaCalculationResult(xn, 0.0, 0.0, 0.0);
//    }
//
//    /** Bootstrap: window < REQUIRED -> allowed = min(idle, cps, capacity) */
//    @Test
//    public void bootstrapAllowsMinOfIdleCpsCapacity() {
//        when(acdEngine.getCampaignIdleUsers(any(Campaign.class))).thenReturn(generateUsers(7));
//        int allowed = manager.allowedDialNow(campaign, 5, 3);
//        assertEquals(3, allowed);
//        verify(acdEngine, times(1)).getCampaignIdleUsers(campaign);
//        verify(calculator, never()).calculateXn(any(), any(), any(), any(), any(), any(), any());
//    }
//
//    /** Bootstrap: idle = 0 -> allowed = 0 */
//    @Test
//    public void bootstrapReturnsZeroWhenNoIdle() {
//        when(acdEngine.getCampaignIdleUsers(any(Campaign.class))).thenReturn(Collections.emptySet());
//        int allowed = manager.allowedDialNow(campaign, 10, 10);
//        assertEquals(0, allowed);
//        verify(acdEngine, times(1)).getCampaignIdleUsers(campaign);
//        verify(calculator, never()).calculateXn(any(), any(), any(), any(), any(), any(), any());
//    }
//
//    /** Non-GAMMA: onCallEnded should not call calculator nor change behavior */
//    @Test
//    public void onCallEndedNonGammaFallsBackToBootstrap() {
//        lenient().when(campaign.getPredictiveStrategy()).thenReturn(Constants.PREDICTIVE_STRATEGY.BETA);
//
//        Call call = new Call();
//        call.setCampaign(campaign);
//        manager.onCallEnded(call, 12, 4);
//
//        verify(calculator, never()).calculateXn(any(), any(), any(), any(), any(), any(), any());
//
//        when(acdEngine.getCampaignIdleUsers(campaign)).thenReturn(generateUsers(2));
//        int allowed = manager.allowedDialNow(campaign, 10, 10);
//        assertEquals(2, allowed); // still bootstrap
//    }
//
//    /** GAMMA: onCallEnded computes Xn via calculator only after REQUIRED window */
//    @Test
//    public void onCallEndedGammaUpdatesXnAfterRequiredWindow() {
//        when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any()))
//                .thenReturn(resultOf(3.7));
//
//        addGammaCalls(campaign.getGammaRequiredWindowSize());
//
//        verify(calculator, times(1)).calculateXn(any(), any(), any(), any(), any(), any(), any());
//
//        int allowed = manager.getXnFromMap(CID, 10, 10);
//        assertEquals(3, allowed); // floor(3.7) = 3
//    }
//
//    /** Xn capping by cps */
//    @Test
//    public void allowedIsCappedByCps() {
//        when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any()))
//                .thenReturn(resultOf(6.4)); // floor 6
//        addGammaCalls(campaign.getGammaRequiredWindowSize());
//
//        int allowed = manager.allowedDialNow(campaign, 2, 10);
//        assertEquals(2, allowed); // min(6,2,10)=2
//    }
//
//    /** Xn capping by remaining capacity */
//    @Test
//    public void allowedIsCappedByRemainingCapacity() {
//        when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any()))
//                .thenReturn(resultOf(6.4)); // floor 6
//        addGammaCalls(campaign.getGammaRequiredWindowSize());
//
//        int allowed = manager.allowedDialNow(campaign, 10, 4);
//        assertEquals(4, allowed); // min(6,10,4)=4
//    }
//
//    /** Negative Xn -> allowed = 0 */
//    @Test
//    public void negativeXnResultsInZeroAllowed() {
//        when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any()))
//                .thenReturn(resultOf(-1.2));
//        addGammaCalls(campaign.getGammaRequiredWindowSize());
//
//        assertEquals(0, manager.allowedDialNow(campaign, 10, 10));
//    }
//
//    /** Zero Xn (floor) -> allowed = 0 */
//    @Test
//    public void zeroXnResultsInZeroAllowed() {
//        when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any()))
//                .thenReturn(resultOf(0.4)); // floor = 0
//        addGammaCalls(campaign.getGammaRequiredWindowSize());
//
//        assertEquals(0, manager.allowedDialNow(campaign, 10, 10));
//    }
//
//    /** When window size is less than REQUIRED, calculateXn must NOT be called and allowedDialNow uses bootstrap */
//    @Test
//    public void shouldUseBootstrapWhenWindowNotFilled() {
//        when(acdEngine.getCampaignIdleUsers(any(Campaign.class))).thenReturn(generateUsers(5));
//
//        addGammaCalls(campaign.getGammaRequiredWindowSize() - 1); // not enough calls
//
//        int allowed = manager.allowedDialNow(campaign, 10, 10);
//        assertEquals(5, allowed); // min(idle=5, cps=10, cap=10) = 5
//
//        verify(calculator, never()).calculateXn(any(), any(), any(), any(), any(), any(), any());
//    }
//
//    /** When window size is enough, calculateXn is called and allowedDialNow uses Xn */
//    @Test
//    public void shouldUseXnWhenWindowFilled() {
//        when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any()))
//                .thenReturn(resultOf(4.7));
//
//        addGammaCalls(campaign.getGammaRequiredWindowSize()); // enough calls
//
//        int allowed = manager.allowedDialNow(campaign, 10, 10);
//        assertEquals(4, allowed);
//
//        verify(calculator, atLeastOnce()).calculateXn(any(), any(), any(), any(), any(), any(), any());
//    }
//
//    /** After onCampaignStop: state is cleared and falls back to bootstrap */
//    @Test
//    public void onCampaignStopResetsStateAndFallsBackToBootstrap() {
//        when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any()))
//                .thenReturn(resultOf(5.9)); // floor 5
//        addGammaCalls(campaign.getGammaRequiredWindowSize());
//
//        assertEquals(5, manager.allowedDialNow(campaign, 10, 10));
//
//        reset(calculator);
//        manager.onCampaignStop(campaign);
//
//        when(acdEngine.getCampaignIdleUsers(campaign)).thenReturn(generateUsers(4));
//        addGammaCalls(1);
//
//        assertEquals(4, manager.allowedDialNow(campaign, 10, 10));
//        verify(calculator, never()).calculateXn(any(), any(), any(), any(), any(), any(), any());
//    }
//
//    /** onCampaignStart: state is initialized; allowedDialNow still uses bootstrap since window is empty */
//    @Test
//    public void onCampaignStartInitializesStateAndUsesBootstrapUntilWindowFills() {
//        manager.onCampaignStart(campaign);
//        when(acdEngine.getCampaignIdleUsers(campaign)).thenReturn(generateUsers(3));
//        assertEquals(3, manager.allowedDialNow(campaign, 10, 10));
//
//        verify(calculator, never()).calculateXn(any(), any(), any(), any(), any(), any(), any());
//    }
}
