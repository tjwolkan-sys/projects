package tests.predictive.gamma;

import com.powerdialer.dto.AgentMetricsDTO;
import com.powerdialer.dto.RingingMetricsDTO;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.predictive.gamma.calculator.PredictiveGammaCallCalculator;
import com.powerdialer.engine.predictive.gamma.model.GammaAnswerSample;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCalculationResult;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallData;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallModel;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaSnapshot;
import com.powerdialer.engine.predictive.gamma.processor.HoltsTrendAnalyzer;
import com.powerdialer.engine.predictive.gamma.processor.PredictiveGammaCallManager;
import com.powerdialer.engine.predictive.gamma.processor.SharedGammaSnapshotProvider;
import com.powerdialer.model.Call;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MADDE 1 — açma oranını cevap anında ölç.
 *
 * <p>Bugün çağrı AR penceresine ancak hangup'ta giriyor: cevapsız ~30 sn'de, cevaplanan
 * ~230 sn'de. Hız artınca pencere hızlı raporlayan başarısızlıklarla dolar, ölçülen AR gerçeğin
 * altına düşer, aynı bütçeyle daha çok numara basılır ve fren de zayıflar.
 *
 * <p>Bu sınıf dört şeyi kilitler: (1) bayrak kapalıyken davranış aynen korunur, (2) bayrak
 * açıkken cevaplanan çağrı answer anında pencereye girer, (3) aynı çağrı hangup'ta İKİNCİ kez
 * sayılmaz, (4) sonradan abandon olan cevaplanmış çağrının sayacı yine de artar.
 */
@RunWith(MockitoJUnitRunner.class)
public class PredictiveGammaAnswerTimeSamplingTest {

    private static final long CID = 7L;

    @Mock private PredictiveGammaCallCalculator calculator;
    @Mock private HoltsTrendAnalyzer holtsTrendAnalyzer;
    @Mock private CDRService cdrService;
    @Mock private SharedGammaSnapshotProvider sharedGammaSnapshotProvider;
    @Mock private Campaign campaign;
    @Mock private DialerFacade dialerFacade;
    @Mock private CampaignRealTimeRingingManager ringingManager;

    private PredictiveGammaCallManager manager;

    @Before
    public void setUp() {
        manager = new PredictiveGammaCallManager(calculator, holtsTrendAnalyzer, cdrService, sharedGammaSnapshotProvider);
        lenient().when(campaign.getId()).thenReturn(CID);
        lenient().when(campaign.getName()).thenReturn("Answer Time Test");
        lenient().when(campaign.getGammaWindowCapacity()).thenReturn(400);
        lenient().when(campaign.getGammaRequiredWindowSize()).thenReturn(100);
    }

    private void startCampaign(boolean gammaPlus) {
        lenient().when(campaign.isGammaPlusEnabled()).thenReturn(gammaPlus);
        lenient().when(cdrService.findGammaStartupAverageTalkTime(CID, 10)).thenReturn(100.0);
        lenient().when(cdrService.findGammaStartupAverageRingTime(CID)).thenReturn(10.0);
        lenient().when(cdrService.findGammaStartupAverageRingTimeAnswered(CID)).thenReturn(13.0);
        lenient().when(cdrService.findGammaStartupAverageAcwTime(CID)).thenReturn(0.0);
        manager.onCampaignStart(campaign);
    }

    // ---------------------------------------------------------------- bayrak kapalı

    @Test
    public void flagOff_onCallAnswered_doesNothing() {
        startCampaign(false);
        Call call = call(10L, true, false);

        manager.onCallAnswered(call);

        assertNull("Bayrak kapali: cevap aninda ornek alinmaz", call.getGammaAnswerSample());
        assertEquals(0, freshWindow().getTotalCallCount());
    }

    @Test
    public void flagOff_endToEndFlow_countsExactlyOneRecordAtHangup() {
        startCampaign(false);
        Call call = call(10L, true, false);

        manager.onCallAnswered(call);
        manager.onCallEnded(call, 120, 14);

        PredictiveGammaSnapshot fresh = freshWindow();
        assertEquals("onCallEnded basina tam 1 kayit", 1, fresh.getTotalCallCount());
        assertEquals(1, countSamples(fresh));
    }

    // ---------------------------------------------------------------- bayrak açık

    @Test
    public void flagOn_answeredCall_entersWindowAtAnswerTime() {
        startCampaign(true);
        Call call = call(10L, true, false);

        manager.onCallAnswered(call);

        PredictiveGammaSnapshot fresh = freshWindow();
        assertNotNull("Kayit referansi cagri uzerinde tutulmali", call.getGammaAnswerSample());
        assertEquals("Cevap aninda pencereye girmeli", 1, fresh.getTotalCallCount());
        PredictiveGammaCallModel sample = first(fresh);
        assertTrue(sample.isAnswered());
        assertFalse("Cevap aninda abandon bilinmiyor", sample.isAbandoned());
    }

    @Test
    public void flagOn_unansweredCall_stillEntersWindowOnlyAtHangup() {
        // Cevapsizin sonucu daha erken bilinemez; bugunku yerinde kalir.
        startCampaign(true);
        Call call = call(13L, false, false);

        manager.onCallEnded(call, 0, 30);

        PredictiveGammaSnapshot fresh = freshWindow();
        assertEquals(1, fresh.getTotalCallCount());
        assertFalse(first(fresh).isAnswered());
    }

    @Test
    public void flagOn_answeredCall_isNotCountedTwiceAtHangup() {
        // Cift sayim korumasi: AR yukari saparsa fren yanlis tarafa kayar.
        startCampaign(true);
        Call call = call(10L, true, false);

        manager.onCallAnswered(call);
        manager.onCallEnded(call, 120, 14);

        PredictiveGammaSnapshot fresh = freshWindow();
        assertEquals("Tek cagri = tek kayit", 1, fresh.getTotalCallCount());
        assertEquals(1, countSamples(fresh));
        assertNull("Referans hangup'ta birakilmali", call.getGammaAnswerSample());
    }

    @Test
    public void flagOn_duplicateAnswerEvent_isIgnored() {
        startCampaign(true);
        Call call = call(10L, true, false);

        manager.onCallAnswered(call);
        GammaAnswerSample first = call.getGammaAnswerSample();
        manager.onCallAnswered(call);

        assertEquals(1, freshWindow().getTotalCallCount());
        assertEquals("Ayni kayit korunmali", first, call.getGammaAnswerSample());
    }

    @Test
    public void flagOn_hangupFinalisesResultCodeOnTheAnswerTimeRecord() {
        startCampaign(true);
        Call call = call(84L, true, false);

        manager.onCallAnswered(call);
        assertEquals("Cevap aninda RC henuz yok", 0L, call.getGammaAnswerSample().getModel().getResultCode());

        PredictiveGammaCallModel sample = call.getGammaAnswerSample().getModel();
        manager.onCallEnded(call, 5, 25);

        assertEquals("Hangup'ta RC yazilmali", 84L, sample.getResultCode());
        assertEquals(25L, sample.getRingTime());
    }

    @Test
    public void flagOn_answeredThenAbandoned_stillIncrementsAbandonCounters() {
        // add() abandon sayaclarini EKLEME aninda artirir. Cevap aninda abandoned=false
        // girildigi icin damga sonradan gelmezse sayac bir daha artmazdi.
        startCampaign(true);
        Call call = call(84L, true, true);

        manager.onCallAnswered(call);
        manager.onCallEnded(call, 5, 25);

        PredictiveGammaSnapshot fresh = freshWindow();
        assertEquals(1, fresh.getTotalCallCount());
        assertEquals("Abandon sayilmali", 1, fresh.getTotalAbandonCallCount());
        assertEquals("rc84 musteri kaynakli", 1, fresh.getTotalAbandonedByCustomer());
        assertEquals(0, fresh.getTotalAbandonedBySystem());
        assertTrue(first(fresh).isAbandoned());
    }

    @Test
    public void flagOn_answeredThenSystemAbandoned_isAttributedToSystem() {
        startCampaign(true);
        Call call = call(78L, true, true);

        manager.onCallAnswered(call);
        manager.onCallEnded(call, 0, 34);

        PredictiveGammaSnapshot fresh = freshWindow();
        assertEquals(1, fresh.getTotalAbandonCallCount());
        assertEquals("rc78 sistem kaynakli", 1, fresh.getTotalAbandonedBySystem());
        assertEquals(0, fresh.getTotalAbandonedByCustomer());
    }

    @Test
    public void flagOn_answeredNotAbandoned_doesNotTouchAbandonCounters() {
        startCampaign(true);
        Call call = call(10L, true, false);

        manager.onCallAnswered(call);
        manager.onCallEnded(call, 120, 14);

        assertEquals(0, freshWindow().getTotalAbandonCallCount());
    }

    @Test
    public void flagOn_answerBeforeCampaignStart_isIgnoredSafely() {
        lenient().when(campaign.isGammaPlusEnabled()).thenReturn(true);
        Call call = call(10L, true, false);

        manager.onCallAnswered(call);

        assertNull(call.getGammaAnswerSample());
    }

    @Test
    public void flagOn_callbackAndRetryTypesGoToTheirOwnWindows() {
        startCampaign(true);
        Call cb = call(10L, true, false);
        cb.setCallType(Constants.CALL_TYPES.CALLBACK);
        Call retry = call(10L, true, false);
        retry.setCallType(Constants.CALL_TYPES.RETRY);

        manager.onCallAnswered(cb);
        manager.onCallAnswered(retry);

        SnapshotSet snapshots = allWindows();
        assertEquals(1, snapshots.cb.getTotalCallCount());
        assertEquals(1, snapshots.retry.getTotalCallCount());
        assertEquals(0, snapshots.fresh.getTotalCallCount());
    }

    // ---------------------------------------------------------------- null RC guard (bayraktan bağımsız)

    @Test
    public void nullTelephonyResultCode_doesNotLoseTheSample() {
        // telephony_rc NULL olabiliyor (~%0,04). Eski kod burada NPE atiyordu ve o cagri
        // gamma pencerelerinin hicbirine girmiyordu — sessiz veri kaybi.
        startCampaign(false);
        Call call = call(10L, false, false);
        call.getCdr().setTelephonyRC(null);

        manager.onCallEnded(call, 0, 5);

        PredictiveGammaSnapshot fresh = freshWindow();
        assertEquals("NPE yerine kayit pencereye girmeli", 1, fresh.getTotalCallCount());
        assertEquals(0L, first(fresh).getResultCode());
    }

    @Test
    public void nullTelephonyResultCode_withFlagOn_alsoSurvives() {
        startCampaign(true);
        Call call = call(10L, true, false);
        call.getCdr().setTelephonyRC(null);

        manager.onCallAnswered(call);
        manager.onCallEnded(call, 30, 12);

        PredictiveGammaSnapshot fresh = freshWindow();
        assertEquals(1, fresh.getTotalCallCount());
        assertEquals(1, countSamples(fresh));
    }

    // ---------------------------------------------------------------- MADDE 4A startup sorgusu

    @Test
    public void startupProfile_flagOff_usesUnfilteredRingQuery() {
        startCampaign(false);
        verify(cdrService).findGammaStartupAverageRingTime(CID);
        verify(cdrService, never()).findGammaStartupAverageRingTimeAnswered(CID);
    }

    @Test
    public void startupProfile_flagOn_usesAnsweredOnlyRingQuery() {
        // Sorgu SAYISI degismez: iki varyanttan biri calisir.
        startCampaign(true);
        verify(cdrService).findGammaStartupAverageRingTimeAnswered(CID);
        verify(cdrService, never()).findGammaStartupAverageRingTime(CID);
    }

    @Test
    public void startupProfile_flagOn_withNoHistory_fallsBackToAnsweredRingDefault() {
        // Filtresiz sabit (30 sn) burada kullanilirsa ufuk 2 kattan fazla siser ve
        // MADDE 4A'nin duzeltmeye calistigi hatanin buyugu kampanya acilisinda geri gelir.
        lenient().when(campaign.isGammaPlusEnabled()).thenReturn(true);
        lenient().when(cdrService.findGammaStartupAverageTalkTime(CID, 10)).thenReturn(100.0);
        lenient().when(cdrService.findGammaStartupAverageRingTimeAnswered(CID)).thenReturn(null);
        lenient().when(cdrService.findGammaStartupAverageAcwTime(CID)).thenReturn(0.0);
        manager.onCampaignStart(campaign);

        assertEquals("Cevaplanan-bazli varsayilan 15 sn olmali", 15.0, capturedStartupAvgRing(), 1e-9);
    }

    @Test
    public void startupProfile_flagOff_withNoHistory_keepsTodaysThirtySecondDefault() {
        lenient().when(campaign.isGammaPlusEnabled()).thenReturn(false);
        lenient().when(cdrService.findGammaStartupAverageTalkTime(CID, 10)).thenReturn(100.0);
        lenient().when(cdrService.findGammaStartupAverageRingTime(CID)).thenReturn(null);
        lenient().when(cdrService.findGammaStartupAverageAcwTime(CID)).thenReturn(0.0);
        manager.onCampaignStart(campaign);

        assertEquals(30.0, capturedStartupAvgRing(), 1e-9);
    }

    // ---------------------------------------------------------------- helper

    private Call call(long resultCodeId, boolean answered, boolean abandoned) {
        TelephonyResultCode trc = new TelephonyResultCode();
        trc.setId(resultCodeId);

        Call call = new Call();
        call.setCampaign(campaign);
        call.setCallType(Constants.CALL_TYPES.FRESH);
        call.setTelephonyResultCode(trc);
        call.getCdr().setId(1000L + resultCodeId);
        call.getCdr().setTelephonyRC(trc);
        call.setAnswerDate(answered ? 1.0 : null);
        call.setAbandoned(abandoned);
        return call;
    }

    private PredictiveGammaSnapshot freshWindow() {
        return allWindows().fresh;
    }

    private double capturedStartupAvgRing() {
        when(sharedGammaSnapshotProvider.getAgentMetrics(CID)).thenReturn(AgentMetricsDTO.zero());
        when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(ringingManager);
        when(ringingManager.getRealTimeRingingMetrics(campaign)).thenReturn(new RingingMetricsDTO());
        when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PredictiveGammaCalculationResult(0.0, 1.0, 1.0, 1.0, new PredictiveGammaCallData(campaign, null)));

        manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        ArgumentCaptor<com.powerdialer.engine.predictive.gamma.model.PredictiveGammaStartupProfile> profile =
                ArgumentCaptor.forClass(com.powerdialer.engine.predictive.gamma.model.PredictiveGammaStartupProfile.class);
        verify(calculator).calculateXn(eq(campaign), any(), any(), any(), any(), any(), any(), any(), profile.capture(), anyInt(), anyInt());
        return profile.getValue().getAvgRingTime();
    }

    private SnapshotSet allWindows() {
        when(sharedGammaSnapshotProvider.getAgentMetrics(CID)).thenReturn(AgentMetricsDTO.zero());
        when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(ringingManager);
        when(ringingManager.getRealTimeRingingMetrics(campaign)).thenReturn(new RingingMetricsDTO());
        when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PredictiveGammaCalculationResult(0.0, 1.0, 1.0, 1.0, new PredictiveGammaCallData(campaign, null)));

        manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        ArgumentCaptor<PredictiveGammaSnapshot> cb = ArgumentCaptor.forClass(PredictiveGammaSnapshot.class);
        ArgumentCaptor<PredictiveGammaSnapshot> retry = ArgumentCaptor.forClass(PredictiveGammaSnapshot.class);
        ArgumentCaptor<PredictiveGammaSnapshot> fresh = ArgumentCaptor.forClass(PredictiveGammaSnapshot.class);
        verify(calculator).calculateXn(eq(campaign), any(), any(), cb.capture(), retry.capture(), fresh.capture(), any(), any(), any(), anyInt(), anyInt());

        SnapshotSet set = new SnapshotSet();
        set.cb = cb.getValue();
        set.retry = retry.getValue();
        set.fresh = fresh.getValue();
        return set;
    }

    private int countSamples(PredictiveGammaSnapshot snapshot) {
        int count = 0;
        for (PredictiveGammaCallModel ignored : snapshot) count++;
        return count;
    }

    private PredictiveGammaCallModel first(PredictiveGammaSnapshot snapshot) {
        return snapshot.iterator().next();
    }

    private static class SnapshotSet {
        PredictiveGammaSnapshot cb;
        PredictiveGammaSnapshot retry;
        PredictiveGammaSnapshot fresh;
    }
}
