package com.powerdialer.engine.predictive.gamma;

import com.powerdialer.dto.AgentMetricsDTO;
import com.powerdialer.dto.RingingMetricsDTO;
import com.powerdialer.engine.predictive.gamma.calculator.PredictiveGammaCallCalculator;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCalculationResult;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallData;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallModel;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaRecentCalls;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaSnapshot;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaStartupProfile;
import com.powerdialer.engine.predictive.gamma.processor.HoltsTrendAnalyzer;
import com.powerdialer.model.Campaign;
import com.powerdialer.redis.event.predictive.PredictiveEventFactory;
import com.powerdialer.engine.predictive.dispatcher.PredictiveGammaEventDispatcher;
import com.powerdialer.util.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PredictiveGammaCallCalculatorTest {

    @Mock private PredictiveEventFactory eventFactory;
    @Mock private PredictiveGammaEventDispatcher gammaDispatcher;
    @Mock private HoltsTrendAnalyzer holtsTrendAnalyzer;
    @Mock private Campaign campaign;

    private PredictiveGammaCallCalculator calculator;

    @Before
    public void setUp() {
        calculator = new PredictiveGammaCallCalculator(eventFactory, gammaDispatcher, holtsTrendAnalyzer);
        lenient().when(campaign.getId()).thenReturn(1L);
        lenient().when(campaign.getName()).thenReturn("Gamma Test");
    }

    @Test
    public void calculateXn_missingGammaConfig_returnsZero() {
        when(campaign.getGammaCorrectionFactor()).thenReturn(null);

        PredictiveGammaCalculationResult result =
                calculator.calculateXn(campaign, null, null, null, null, null, null, null, null, 0, 0);

        assertEquals(0.0, result.getXn(), 0.0);
        verify(gammaDispatcher, never()).dispatchAsync(any());
    }

    @Test
    public void calculateXn_blendsFreshAnswerRateByOwnHistoryAndUsesAgentBasedCorrectionFactor() {
        configureGammaCampaign(0.60, 1.0);
        when(holtsTrendAnalyzer.updateAndGetTrend(eq(1L), eq(30))).thenReturn(new HoltsTrendAnalyzer.HoltResult(30.0, 0.0, 123L));

        PredictiveGammaSnapshot empty = snapshot();
        PredictiveGammaSnapshot fresh = freshSnapshot(250, 125);
        PredictiveGammaSnapshot successful = snapshot(answeredCall(100, 10));
        PredictiveGammaSnapshot ring = snapshot(answeredCall(100, 10));

        AgentMetricsDTO agentMetrics = new AgentMetricsDTO(30.0, 17.5, 10.0, 0.0, 0.0);
        PredictiveGammaStartupProfile startupProfile = new PredictiveGammaStartupProfile(0.90, 0.90, 0.90, 100.0, 10.0, 0.0);

        PredictiveGammaCalculationResult result = calculator.calculateXn(
                campaign,
                agentMetrics,
                new RingingMetricsDTO(),
                empty,
                empty,
                fresh,
                successful,
                ring,
                startupProfile,
                100,
                50
        );

        assertEquals(0.70, result.getFreshAnswerRate(), 0.0001);
        // 5 agent üstünde CF artık tam configuredCF (katmanlı ramp kaldırıldı):
        // rawXn = (0 + (10/100)*10*0.60)*1 = 0.60
        assertEquals(0.60, result.getXn(), 0.0001);

        PredictiveGammaCallData data = result.getCallData();
        assertNotNull(data);
        assertEquals(0.60, data.getCorrectionFactor(), 0.0001);
        assertEquals(0.60, data.getConfiguredCorrectionFactor(), 0.0001);
        assertEquals(1.0, data.getCorrectionFactorScale(), 0.0001);
        assertEquals(30.0, data.getPhysicalTotalAgentsCount(), 0.0001);
        assertEquals(0.90, data.getFreshStartupAnswerRate(), 0.0001);
        assertEquals(0.50, data.getFreshLiveAnswerRate(), 0.0001);
        assertEquals(0.50, data.getFreshAnswerRateBlendWeight(), 0.0001);
        assertEquals(0.90, data.getCbStartupAnswerRate(), 0.0001);
        assertEquals(0.90, data.getCbLiveAnswerRate(), 0.0001);
        assertEquals(0.0, data.getCbAnswerRateBlendWeight(), 0.0001);
        assertEquals(0.0, data.getAvgAcwTime(), 0.0001);
        verify(eventFactory, never()).createGamma(any());
        verify(gammaDispatcher, never()).dispatchAsync(any());
        verify(holtsTrendAnalyzer).updateAndGetTrend(1L, 30);
    }

    // ---------------- CF eşik mantığı: katmanlı ramp kaldırıldı, tek eşik <=5 -> 0.0 ----------------

    @Test
    public void correctionFactor_atFiveAgents_isZero() {
        PredictiveGammaCallData data = runWithTotalAgents(5.0, 0.60);
        assertEquals(0.0, data.getCorrectionFactor(), 0.0001);
        assertEquals(0.0, data.getCorrectionFactorScale(), 0.0001);
        assertEquals(0.60, data.getConfiguredCorrectionFactor(), 0.0001);
    }

    @Test
    public void correctionFactor_aboveFiveAgents_isFullConfigured() {
        PredictiveGammaCallData data = runWithTotalAgents(6.0, 0.60);
        assertEquals(0.60, data.getCorrectionFactor(), 0.0001);
        assertEquals(1.0, data.getCorrectionFactorScale(), 0.0001);
    }

    @Test
    public void correctionFactor_noLongerRampsBetweenThresholds() {
        // Eski mantıkta 20 agent ramp'a denk gelir (scale ~0.67); yeni mantıkta tam CF.
        PredictiveGammaCallData data = runWithTotalAgents(20.0, 0.60);
        assertEquals(0.60, data.getCorrectionFactor(), 0.0001);
        assertEquals(1.0, data.getCorrectionFactorScale(), 0.0001);
    }

    /**
     * Verilen totalAgents/configuredCF ile tek bir calculateXn koşturur ve telemetri
     * callData'sını döner. avgTalk startup profilinden (100) gelir; erken-zero yok.
     */
    private PredictiveGammaCallData runWithTotalAgents(double totalAgents, double configuredCF) {
        configureGammaCampaign(configuredCF, 1.0);
        lenient().when(holtsTrendAnalyzer.updateAndGetTrend(eq(1L), anyInt()))
                .thenReturn(new HoltsTrendAnalyzer.HoltResult(totalAgents, 0.0, 123L));

        PredictiveGammaSnapshot empty = snapshot();
        PredictiveGammaSnapshot fresh = freshSnapshot(250, 125);
        PredictiveGammaSnapshot successful = snapshot(answeredCall(100, 10));
        PredictiveGammaSnapshot ring = snapshot(answeredCall(100, 10));

        AgentMetricsDTO agentMetrics = new AgentMetricsDTO(totalAgents, totalAgents, 10.0, 0.0, 0.0);
        PredictiveGammaStartupProfile startupProfile = new PredictiveGammaStartupProfile(0.90, 0.90, 0.90, 100.0, 10.0, 0.0);

        PredictiveGammaCalculationResult result = calculator.calculateXn(
                campaign, agentMetrics, new RingingMetricsDTO(), empty, empty, fresh, successful, ring, startupProfile, 100, 50);
        return result.getCallData();
    }

    // ---------------- AR geçiş eşikleri window capacity'den dinamik türetilir ----------------

    @Test
    public void answerRateBlend_freshStartAndFullTrust_scaleWithWindowCapacity() {
        // capacity=200 -> başlangıç=50, tam-güven=200. fresh totalCallCount=125 ->
        // liveWeight = (125-50)/(200-50) = 0.50. (Sabit 400/100 olsaydı 0.083 olurdu.)
        PredictiveGammaCallData data = runWithWindowCapacity(200, snapshot(), freshSnapshot(125, 40));
        assertEquals(0.50, data.getFreshAnswerRateBlendWeight(), 0.0001);
    }

    @Test
    public void answerRateBlend_callbackStart_isEighthOfWindowCapacity() {
        // capacity=80 -> cb başlangıç=10, tam-güven=80. cb totalCallCount=45 ->
        // liveWeight = (45-10)/(80-10) = 0.50. (Sabit 400/50 olsaydı 0.0 olurdu.)
        PredictiveGammaCallData data = runWithWindowCapacity(80, typedSnapshot(Constants.CALL_TYPES.CALLBACK, 45, 20), snapshot());
        assertEquals(0.50, data.getCbAnswerRateBlendWeight(), 0.0001);
    }

    /** Verilen window capacity + cb/fresh snapshot ile tek calculateXn koşturur, callData döner. */
    private PredictiveGammaCallData runWithWindowCapacity(int windowCapacity, PredictiveGammaSnapshot cb, PredictiveGammaSnapshot fresh) {
        when(campaign.getGammaCorrectionFactor()).thenReturn(0.60);
        when(campaign.getGammaAbandonSensitivity()).thenReturn(1.0);
        when(campaign.getGammaWindowCapacity()).thenReturn(windowCapacity);
        when(campaign.getGammaRequiredWindowSize()).thenReturn(100);
        lenient().when(campaign.getQueueSize()).thenReturn(0);
        lenient().when(holtsTrendAnalyzer.updateAndGetTrend(eq(1L), anyInt()))
                .thenReturn(new HoltsTrendAnalyzer.HoltResult(30.0, 0.0, 123L));

        PredictiveGammaSnapshot empty = snapshot();
        PredictiveGammaSnapshot successful = snapshot(answeredCall(100, 10));
        PredictiveGammaSnapshot ring = snapshot(answeredCall(100, 10));

        AgentMetricsDTO agentMetrics = new AgentMetricsDTO(30.0, 30.0, 10.0, 0.0, 0.0);
        PredictiveGammaStartupProfile startupProfile = new PredictiveGammaStartupProfile(0.90, 0.90, 0.90, 100.0, 10.0, 0.0);

        PredictiveGammaCalculationResult result = calculator.calculateXn(
                campaign, agentMetrics, new RingingMetricsDTO(), cb, empty, fresh, successful, ring, startupProfile, 100, 50);
        return result.getCallData();
    }

    private PredictiveGammaSnapshot typedSnapshot(Constants.CALL_TYPES type, int total, int answered) {
        PredictiveGammaRecentCalls recentCalls = new PredictiveGammaRecentCalls(400);
        for (int i = 0; i < total; i++) {
            PredictiveGammaCallModel call = new PredictiveGammaCallModel();
            call.setCallType(type);
            call.setAnswered(i < answered);
            call.setTalkTime(i < answered ? 100 : 0);
            call.setRingTime(10);
            recentCalls.add(call);
        }
        return recentCalls.snapshot(campaign);
    }

    private void configureGammaCampaign(double correctionFactor, double abandonSensitivity) {
        when(campaign.getGammaCorrectionFactor()).thenReturn(correctionFactor);
        when(campaign.getGammaAbandonSensitivity()).thenReturn(abandonSensitivity);
        when(campaign.getGammaWindowCapacity()).thenReturn(400);
        when(campaign.getGammaRequiredWindowSize()).thenReturn(100);
        lenient().when(campaign.getQueueSize()).thenReturn(0);
    }

    private PredictiveGammaSnapshot snapshot(PredictiveGammaCallModel... calls) {
        PredictiveGammaRecentCalls recentCalls = new PredictiveGammaRecentCalls(400);
        for (PredictiveGammaCallModel call : calls) {
            recentCalls.add(call);
        }
        return recentCalls.snapshot(campaign);
    }

    private PredictiveGammaSnapshot freshSnapshot(int totalCalls, int answeredCalls) {
        PredictiveGammaRecentCalls recentCalls = new PredictiveGammaRecentCalls(400);
        for (int i = 0; i < totalCalls; i++) {
            PredictiveGammaCallModel call = new PredictiveGammaCallModel();
            call.setCallType(Constants.CALL_TYPES.FRESH);
            call.setAnswered(i < answeredCalls);
            call.setTalkTime(i < answeredCalls ? 100 : 0);
            call.setRingTime(10);
            recentCalls.add(call);
        }
        return recentCalls.snapshot(campaign);
    }

    private PredictiveGammaCallModel answeredCall(long talkTime, long ringTime) {
        PredictiveGammaCallModel call = new PredictiveGammaCallModel();
        call.setCallType(Constants.CALL_TYPES.FRESH);
        call.setAnswered(true);
        call.setTalkTime(talkTime);
        call.setRingTime(ringTime);
        return call;
    }

//    @Mock private ACDEngine acdEngine;
//    @Mock private Campaign campaign;
//    @Mock private PredictiveEventFactory eventFactory;
//    @Mock private PredictiveGammaEventDispatcher gammaDispatcher;
//
//    private PredictiveGammaCallCalculator calculator;
//
//    private static final int WINDOW_CAPACITY = 400;
//
//    @Before
//    public void setUp() {
//        calculator = new PredictiveGammaCallCalculator(acdEngine, eventFactory, gammaDispatcher);
//        when(campaign.getId()).thenReturn(1L);
//        when(campaign.getName()).thenReturn("Test Campaign");
//        when(campaign.getInCalls()).thenReturn(new AtomicInteger(2));
//        when(campaign.getRingingCalls()).thenReturn(new AtomicInteger(0));
//        when(campaign.getRingingCbCalls()).thenReturn(new AtomicInteger(0));
//        when(campaign.getRingingRetryCalls()).thenReturn(new AtomicInteger(0));
//        when(campaign.getRingingFreshCalls()).thenReturn(new AtomicInteger(0));
//        when(campaign.getQueueSize()).thenReturn(0);
//        when(campaign.getGammaCorrectionFactor()).thenReturn(0.5);
//        when(campaign.getGammaAbandonSensitivity()).thenReturn(2.0);
//        when(campaign.getGammaWindowCapacity()).thenReturn(WINDOW_CAPACITY);
//        when(campaign.getGammaRequiredWindowSize()).thenReturn(10);
//        lenient().when(campaign.getState()).thenReturn(Constants.CAMPAIGN_STATE.RUNNING);
//
//        lenient().when(eventFactory.createGamma(any())).thenAnswer(inv ->
//                new com.powerdialer.redis.event.predictive.PredictiveGammaEvent("test-publisher", inv.getArgument(0))
//        );
//        doNothing().when(gammaDispatcher).dispatchAsync(any());
//    }
//
//    private PredictiveGammaSnapshot buildSnapshot(PredictiveGammaCallModel... calls) {
//        PredictiveGammaRecentCalls recentCalls = new PredictiveGammaRecentCalls(WINDOW_CAPACITY);
//        for (PredictiveGammaCallModel call : calls) {
//            recentCalls.add(call);
//        }
//        return recentCalls.snapshot(campaign);
//    }
//
//    private double xnFrom(PredictiveGammaCalculationResult result) {
//        return result.getXn();
//    }
//
//    /** no samples -> should return 0 */
//    @Test
//    public void shouldReturnZeroWhenNoSamples() {
//        when(acdEngine.getCampaignIdleAndAvailableUsers(campaign)).thenReturn(new HashSet<>());
//
//        PredictiveGammaSnapshot empty = buildSnapshot();
//        // cb, retry, fresh, successful, ring
//        double result = xnFrom(calculator.calculateXn(campaign, null, empty, empty, empty, empty, empty));
//
//        assertEquals(0.0, result, 0.0);
//    }
//
//    /** samples exist but none answered -> avgTalkTime = 0 -> should return 0 */
//    @Test
//    public void shouldReturnZeroWhenNoAnsweredCalls() {
//        when(acdEngine.getCampaignIdleAndAvailableUsers(campaign)).thenReturn(new HashSet<>());
//
//        PredictiveGammaCallModel sample = new PredictiveGammaCallModel();
//        sample.setAnswered(false);
//        sample.setTalkTime(0);
//        sample.setRingTime(5);
//
//        PredictiveGammaSnapshot snapshot = buildSnapshot(sample);
//        double result = xnFrom(calculator.calculateXn(campaign, null, snapshot, buildSnapshot(), buildSnapshot(), buildSnapshot(), buildSnapshot()));
//
//        assertEquals(0.0, result, 0.0);
//    }
//
//    /** answered call but avgTalkTime = 0 -> should return 0 */
//    @Test
//    public void shouldReturnZeroWhenAvgTalkTimeIsZero() {
//        when(acdEngine.getCampaignIdleAndAvailableUsers(campaign)).thenReturn(Collections.singleton(new User()));
//
//        PredictiveGammaCallModel sample = new PredictiveGammaCallModel();
//        sample.setAnswered(true);
//        sample.setTalkTime(0);
//        sample.setRingTime(5);
//
//        PredictiveGammaSnapshot snapshot = buildSnapshot(sample);
//        // successful snapshot has talkTime=0 -> avgTalk=0 -> early return 0
//        double result = xnFrom(calculator.calculateXn(campaign, null, snapshot, buildSnapshot(), buildSnapshot(), buildSnapshot(sample), buildSnapshot(sample)));
//
//        assertEquals(0.0, result, 0.0);
//    }
//
//    /**
//     * valid calls and agents -> should calculate correct Xn
//     *
//     * Formula:
//     *   abandonPenalty = max(0.1, 1.0 - abandonRate * abandonSensitivity)
//     *   rawXn = (availableAgents + (inCallAgents / avgTalk) * avgRing * correctionFactor) * abandonPenalty
//     *   ringingLoad = ringingCb*cbAR + ringingRetry*retryAR + ringingFresh*freshAR  (all 0 in this test)
//     *   xn = max(0, rawXn - ringingLoad)
//     */
//    @Test
//    public void shouldCalculateCorrectXn() {
//        User u1 = mock(User.class);
//        User u2 = mock(User.class);
//        when(acdEngine.getCampaignIdleAndAvailableUsers(campaign))
//                .thenReturn(new HashSet<>(Arrays.asList(u1, u2)));
//
//        // fresh calls: 2 answered, 1 not answered
//        PredictiveGammaCallModel call1 = new PredictiveGammaCallModel();
//        call1.setAnswered(true);
//        call1.setTalkTime(10);
//        call1.setRingTime(4);
//
//        PredictiveGammaCallModel call2 = new PredictiveGammaCallModel();
//        call2.setAnswered(true);
//        call2.setTalkTime(20);
//        call2.setRingTime(6);
//
//        PredictiveGammaCallModel call3 = new PredictiveGammaCallModel();
//        call3.setAnswered(false);
//        call3.setTalkTime(0);
//        call3.setRingTime(5);
//
//        // cb and retry snapshots are empty; all calls go into fresh
//        PredictiveGammaSnapshot cbSnapshot      = buildSnapshot();
//        PredictiveGammaSnapshot retrySnapshot   = buildSnapshot();
//        PredictiveGammaSnapshot freshSnapshot   = buildSnapshot(call1, call2, call3);
//        // successful: only answered calls (for avgTalk)
//        PredictiveGammaSnapshot successSnapshot = buildSnapshot(call1, call2);
//        // ring: all calls that reached ringing (for avgRing)
//        PredictiveGammaSnapshot ringSnapshot    = buildSnapshot(call1, call2, call3);
//
//        PredictiveGammaCalculationResult result = calculator.calculateXn(
//                campaign, null,
//                cbSnapshot, retrySnapshot, freshSnapshot,
//                successSnapshot, ringSnapshot
//        );
//
//        // Expected values
//        double freshAnswerRate  = 2.0 / 3.0;
//        double abandonRate      = 0.0 / 3.0;   // no abandoned calls
//        double abandonSensitivity = 2.0;
//        double correctionFactor = 0.5;
//        int    availableAgents  = 2;
//        int    inCallAgents     = 2;            // from campaign.getInCalls()
//
//        // avgTalk from successSnapshot (call1, call2)
//        double avgTalk = (10.0 + 20.0) / 2.0;  // = 15.0
//        // avgRing from ringSnapshot (call1, call2, call3)
//        double avgRing = (4.0 + 6.0 + 5.0) / 3.0; // = 5.0
//
//        double abandonPenalty = Math.max(0.1, 1.0 - abandonRate * abandonSensitivity); // = 1.0
//        double rawXn = (availableAgents + (inCallAgents / avgTalk) * avgRing * correctionFactor) * abandonPenalty;
//        // ringingLoad = 0 * freshAnswerRate = 0  (all ringing counters mocked to 0)
//        double ringingLoad = 0.0;
//        double expected = Math.max(0.0, rawXn - ringingLoad);
//
//        assertEquals(expected, result.getXn(), 0.0001);
//    }
}
