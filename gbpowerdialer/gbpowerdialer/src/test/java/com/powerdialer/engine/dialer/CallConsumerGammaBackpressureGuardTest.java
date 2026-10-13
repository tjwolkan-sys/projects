package com.powerdialer.engine.dialer;

import com.powerdialer.engine.predictive.beta.processor.PredictiveBetaCallManager;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallData;
import com.powerdialer.engine.predictive.gamma.processor.PredictiveGammaCallManager;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AMI bağlantı kaybı (veya CallOriginator yavaşlama) sırasında callQueue'nun
 * sınırsız büyümesini engelleyen backpressure guard testleri.
 *
 * <p>Davranış: Gamma branch'inde, drainWithBudget çağrısından ÖNCE
 * <code>callQueue.size() &gt;= cps</code> ise drain atlanır. Asıl mantık:
 * sağlıklı state'te callQueue tick anında 0'a yakındır (CallOriginator her
 * saniye CPS hızında boşaltır). Eğer dayanmış kaldıysa CallOriginator
 * yetişemiyor demektir (en olası sebep: AMI bağlantı kaybı). Backlog
 * birikmesin diye consumer drain'ini atlarız; mevcut bekleyenler boşalsın.
 *
 * <p>Guard yalnızca Gamma kampanyalarında devrededir; Beta ve preview
 * branch'leri etkilenmez.
 */
@RunWith(MockitoJUnitRunner.class)
public class CallConsumerGammaBackpressureGuardTest {

    @Mock private CampaignContext campaignContext;
    @Mock private DialerFacade dialerFacade;
    @Mock private CallProducer callProducer;
    @Mock private Campaign campaign;
    @Mock private LoggerDecorator loggerDecorator;
    @Mock private LoggerDecorator logger;
    @Mock private PredictiveBetaCallManager predictiveBetaCallManager;
    @Mock private PredictiveGammaCallManager predictiveGammaCallManager;

    private BlockingQueue<Call> dataQueue;
    private CallConsumer callConsumer;

    private static final long CID = 1L;
    private static final int CPS = 7;
    private static final int CONCURRENT_LINES = 30;

    @Before
    public void setUp() {
        dataQueue = new LinkedBlockingDeque<>();

        when(campaignContext.getCampaign()).thenReturn(campaign);
        when(campaignContext.getCallProducer()).thenReturn(callProducer);
        when(campaignContext.getDialerFacade()).thenReturn(dialerFacade);
        when(callProducer.getDataQueue()).thenReturn(dataQueue);
        when(dialerFacade.getLoggerDecorator()).thenReturn(loggerDecorator);
        when(loggerDecorator.getLogger(CallConsumer.class)).thenReturn(logger);
        when(dialerFacade.getPredictiveBetaCallManager()).thenReturn(predictiveBetaCallManager);
        when(dialerFacade.getPredictiveGammaCallManager()).thenReturn(predictiveGammaCallManager);

        when(campaign.getId()).thenReturn(CID);
        when(campaign.getConcurrentLines()).thenReturn(CONCURRENT_LINES);
        when(campaign.getCallsPerSecond()).thenReturn(CPS);
        when(campaign.isPredictiveCampaign()).thenReturn(true);
        when(campaign.getPredictiveStrategy()).thenReturn(Constants.PREDICTIVE_STRATEGY.GAMMA);
        // campaign.getState() doğrudan run() içinde kontrol edilir; testlerimiz
        // pollCallFromProducerToConsumer'ı Whitebox ile çağırdığı için stub yok.

        lenient().when(predictiveGammaCallManager.getAnswerRateCost(any(Call.class), eq(CID))).thenReturn(0.5);

        callConsumer = new CallConsumer(campaignContext);
    }

    // ---------------- Guard davranışı ----------------

    @Test
    public void backpressureGuard_skipsDrain_whenCallQueueAtCps() throws Exception {
        fillCallQueueWith(CPS);            // callQueue'da tam CPS adet bekleyen var
        dataQueue.offer(call(Constants.CALL_TYPES.FRESH));
        dataQueue.offer(call(Constants.CALL_TYPES.FRESH));
        mockDialBudget(CPS);

        Whitebox.invokeMethod(callConsumer, "pollCallFromProducerToConsumer");

        // Guard tetiklendi: dataQueue dokunulmadı, callQueue olduğu gibi kaldı
        assertEquals("dataQueue tüketilmemeli (guard tetiklendi)", 2, dataQueue.size());
        assertEquals("callQueue cps'de kaldı (yeni eklenmedi)", CPS, callConsumer.getCallQueue().size());
    }

    @Test
    public void backpressureGuard_skipsDrain_whenCallQueueAboveCps() throws Exception {
        fillCallQueueWith(CPS + 5);        // cps üstünde dolu — birikim oluşmuş simulasyonu
        dataQueue.offer(call(Constants.CALL_TYPES.FRESH));
        mockDialBudget(CPS);

        Whitebox.invokeMethod(callConsumer, "pollCallFromProducerToConsumer");

        assertEquals("dataQueue dokunulmamalı", 1, dataQueue.size());
        assertEquals("callQueue boyutu değişmemeli", CPS + 5, callConsumer.getCallQueue().size());
    }

    @Test
    public void backpressureGuard_allowsDrain_whenCallQueueBelowCps() throws Exception {
        // callQueue cps'in altında → drain serbest, mevcut davranış (regression)
        fillCallQueueWith(CPS - 2);
        dataQueue.offer(call(Constants.CALL_TYPES.FRESH));
        dataQueue.offer(call(Constants.CALL_TYPES.FRESH));
        mockDialBudget(CPS);

        Whitebox.invokeMethod(callConsumer, "pollCallFromProducerToConsumer");

        // Drain çalıştı, dataQueue tüketildi
        assertTrue("dataQueue en az kısmen tüketilmiş olmalı", dataQueue.size() < 2);
        assertTrue("callQueue büyümüş olmalı (drain çalıştı)", callConsumer.getCallQueue().size() > CPS - 2);
    }

    @Test
    public void backpressureGuard_allowsDrain_whenCallQueueIsEmpty() throws Exception {
        // Standart sağlıklı state başlangıç — callQueue boş, drain serbest
        dataQueue.offer(call(Constants.CALL_TYPES.FRESH));
        dataQueue.offer(call(Constants.CALL_TYPES.FRESH));
        mockDialBudget(CPS);

        Whitebox.invokeMethod(callConsumer, "pollCallFromProducerToConsumer");

        assertTrue("callQueue dolmuş olmalı (drain çalıştı)", callConsumer.getCallQueue().size() > 0);
    }

    // ---------------- AMI loss simülasyonu — birikim 7'de durur ----------------

    @Test
    public void backpressureGuard_callQueueDoesNotGrowAboveCps_overMultipleTicks() throws Exception {
        // CallOriginator hiç çekmiyor (AMI ölü simulasyonu) — callQueue'dan
        // hiçbir contact çıkmıyor. Consumer her tick çağrıldığında guard
        // tetiklenmeli; callQueue cps'i geçmemeli.
        for (int i = 0; i < 20; i++) {
            dataQueue.offer(call(Constants.CALL_TYPES.FRESH));
        }
        mockDialBudget(CPS);

        for (int tick = 0; tick < 10; tick++) {
            Whitebox.invokeMethod(callConsumer, "pollCallFromProducerToConsumer");
        }

        assertTrue(
            "callQueue cps'den büyük olmamalı (AMI ölü = backlog yok)",
            callConsumer.getCallQueue().size() <= CPS
        );
    }

    // ---------------- Overshoot regresyonu — kısmi drain ----------------

    @Test
    public void callQueueDoesNotOvershootCps_whenPartiallyDrainedBelowCps() throws Exception {
        // Bug: maxCallCount kuyruktaki artığı saymadığı için, kuyruk cps'in
        // ALTINDAYKEN (guard inactive) bir tick tam cps daha ekleyip toplamı
        // cps'in üstüne çıkarabiliyor. Senaryo: originator önceki tick'i tam
        // boşaltamadı, kuyrukta artık kaldı; dataQueue dolu, bütçe yüksek.
        fillCallQueueWith(CPS - 2);                 // 5 artık var, cps=7 altında → guard kapalı
        for (int i = 0; i < 20; i++) {
            dataQueue.offer(call(Constants.CALL_TYPES.FRESH));
        }
        mockDialBudget(CPS * 10);                   // bütçe sınırlayıcı olmasın

        Whitebox.invokeMethod(callConsumer, "pollCallFromProducerToConsumer");

        // Invariant: callQueue tek tick'te cps'i aşmamalı (cps'e tamamlanmalı).
        assertTrue(
            "callQueue cps'i aşmamalı (overshoot bug) — boyut=" + callConsumer.getCallQueue().size(),
            callConsumer.getCallQueue().size() <= CPS
        );
    }

    // ---------------- Event davranışı ----------------

    @Test
    public void backpressureGuard_stillDispatchesEvent_withDrainZero() throws Exception {
        fillCallQueueWith(CPS);
        dataQueue.offer(call(Constants.CALL_TYPES.FRESH));
        PredictiveGammaCallData callData = mockDialBudget(CPS);

        Whitebox.invokeMethod(callConsumer, "pollCallFromProducerToConsumer");

        // Telemetri: event hâlâ yayımlanır, drainedCallCount=0 olur
        verify(predictiveGammaCallManager, times(1)).dispatchEvent(eq(callData));
        verify(callData).setDrainedCallCount(0);
        verify(callData).setQueueSize(CPS);
    }

    @Test
    public void backpressureGuard_doesNotInvokeStarvationCredit() throws Exception {
        // Guard tetiklenince starvation credit update SKIP edilmeli — sebep:
        // bu "starvation" değil, fiziksel backpressure. Shared kampanyalarda
        // yanlış kredi birikmesin diye.
        fillCallQueueWith(CPS);
        mockDialBudget(CPS);

        Whitebox.invokeMethod(callConsumer, "pollCallFromProducerToConsumer");

        verify(predictiveGammaCallManager, never()).updateStarvationCredit(
                any(Campaign.class), anyDouble(), anyDouble(), anyInt(), any(DialerFacade.class)
        );
    }

    // ---------------- Beta branch etkilenmemeli ----------------

    @Test
    public void backpressureGuard_doesNotApplyToBetaCampaign() throws Exception {
        // Beta kampanyada guard devre dışı — sadece Gamma branch.
        when(campaign.getPredictiveStrategy()).thenReturn(Constants.PREDICTIVE_STRATEGY.BETA);
        when(predictiveBetaCallManager.getPredictiveTotalNumberOfCall(eq(CID))).thenReturn(20);
        fillCallQueueWith(CPS);
        dataQueue.offer(call(Constants.CALL_TYPES.FRESH));

        Whitebox.invokeMethod(callConsumer, "pollCallFromProducerToConsumer");

        // Beta branch'inde drain davranışı normal (guard kontrolü Gamma'da)
        // Bu test guard'ın Beta'da YOK olduğunu doğrular — Beta dial logic
        // başka kurallar uygular; drain davranışını burada assert etmiyoruz,
        // yalnızca Beta branch'in çalıştığını teyit ediyoruz.
        verify(predictiveBetaCallManager).getPredictiveTotalNumberOfCall(eq(CID));
    }

    // ---------------- Helper'lar ----------------

    private Call call(Constants.CALL_TYPES callType) {
        Call call = new Call();
        call.setCallType(callType);
        return call;
    }

    private void fillCallQueueWith(int count) {
        BlockingDeque<Call> callQueue = callConsumer.getCallQueue();
        for (int i = 0; i < count; i++) {
            callQueue.offer(call(Constants.CALL_TYPES.FRESH));
        }
    }

    /**
     * Mock'lanmış GammaDialBudget döner; calculateDialBudget her çağrıda
     * aynı object'i verir. PredictiveGammaCallData mock'u event setter
     * verify'ları için döner.
     */
    private PredictiveGammaCallData mockDialBudget(double totalBudget) {
        PredictiveGammaCallData callData = mock(PredictiveGammaCallData.class);
        PredictiveGammaCallManager.GammaDialBudget budget =
                mock(PredictiveGammaCallManager.GammaDialBudget.class);
        lenient().when(budget.getBaseDialBudget()).thenReturn(totalBudget);
        lenient().when(budget.getStarvationBoost()).thenReturn(0.0);
        lenient().when(budget.getNextCallCost()).thenReturn(1.0);
        lenient().when(budget.getTotalDialBudget()).thenReturn(totalBudget);
        lenient().when(budget.getCallData()).thenReturn(callData);
        lenient().when(predictiveGammaCallManager.calculateDialBudget(
                any(Campaign.class), anyInt(), anyInt(), any(DialerFacade.class), any()
        )).thenReturn(budget);
        lenient().when(predictiveGammaCallManager.getStarvationCredit(anyLong())).thenReturn(0.0);
        return callData;
    }
}
