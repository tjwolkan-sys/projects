package tests.predictive.gamma;

import com.powerdialer.dto.AgentMetricsDTO;
import com.powerdialer.dto.RingingMetricsDTO;
import com.powerdialer.engine.predictive.dispatcher.PredictiveGammaEventDispatcher;
import com.powerdialer.engine.predictive.gamma.calculator.PredictiveGammaCallCalculator;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCalculationResult;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallModel;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaRecentCalls;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaSnapshot;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaStartupProfile;
import com.powerdialer.engine.predictive.gamma.processor.HoltsTrendAnalyzer;
import com.powerdialer.model.Campaign;
import com.powerdialer.redis.event.predictive.PredictiveEventFactory;
import com.powerdialer.util.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

/**
 * T1 (vardiya sonu freni) ve T2 (talk ortalamasi kademeli gecis) davranislarini kilitler.
 *
 * <p><b>Test stratejisi:</b> her madde icin once "eski davranis korunuyor mu" (regresyon kapisi),
 * sonra "yeni davranis dogru mu" (fonksiyonel kapi). Sayisal beklentiler elle hesaplanmistir.
 *
 * <p>Formul: {@code Xn = (effectiveAvailable + (serviceAgents / (talk+acw)) * ring * CF) * penalty}
 */
@RunWith(MockitoJUnitRunner.class)
public class PredictiveGammaShiftEndAndTalkBlendTest {

    private static final int WINDOW_CAPACITY = 400;
    private static final int BLEND_START = 100;
    private static final int FULL_TRUST = 400;

    @Mock private PredictiveEventFactory eventFactory;
    @Mock private PredictiveGammaEventDispatcher gammaDispatcher;
    @Mock private HoltsTrendAnalyzer holtsTrendAnalyzer;
    @Mock private Campaign campaign;

    private PredictiveGammaCallCalculator calculator;
    private double holtTrend;

    @Before
    public void setUp() {
        calculator = new PredictiveGammaCallCalculator(eventFactory, gammaDispatcher, holtsTrendAnalyzer);
        lenient().when(campaign.getId()).thenReturn(1L);
        lenient().when(campaign.getName()).thenReturn("T1-T2 Test");
        lenient().when(campaign.getGammaCorrectionFactor()).thenReturn(1.0);
        lenient().when(campaign.getGammaAbandonSensitivity()).thenReturn(0.0);
        lenient().when(campaign.getGammaWindowCapacity()).thenReturn(WINDOW_CAPACITY);
        lenient().when(campaign.getGammaRequiredWindowSize()).thenReturn(BLEND_START);
        lenient().when(campaign.getQueueSize()).thenReturn(0);
        lenient().when(holtsTrendAnalyzer.updateAndGetTrend(anyLong(), anyInt()))
                // HoltResult(level, trend, lastUpdateMs) — trend IKINCI parametredir.
                .thenAnswer(i -> new HoltsTrendAnalyzer.HoltResult(0.0, holtTrend, 0L));
    }

    // ==================================================================== T1

    /**
     * REGRESYON KAPISI: trend >= 0 iken serviceAgents'a DOKUNULMAZ.
     * Gunun %95'inde gecerli olan yol; bugunku kodla bit-esit kalmali.
     */
    @Test
    public void t1_trendPozitifken_serviceAgentsDegismez() {
        holtTrend = 0.5;   // ajan ARTIYOR

        // avail=0, incall=10, talk=100, ring=10, CF=1, penalty=1
        // beklenen: 0 + (10 / 100) * 10 * 1 = 1.0
        double xn = runXn(agents(10, 10, 10, 0, 0));

        assertEquals("Pozitif trendte fren devreye girmemeli", 1.0, xn, 1e-9);
    }

    /** REGRESYON KAPISI: trend tam 0 iken de dokunulmaz (sinir degeri). */
    @Test
    public void t1_trendSifirken_serviceAgentsDegismez() {
        holtTrend = 0.0;

        double xn = runXn(agents(10, 10, 10, 0, 0));

        assertEquals("Trend 0 iken fren devreye girmemeli", 1.0, xn, 1e-9);
    }

    /**
     * FONKSIYONEL KAPI: trend negatifken serviceAgents azalir -> arz kisilir.
     *
     * <p>leaving = trend * (talk+acw) = -0.02 * 100 = -2.0
     * serviceAgents = max(10 * 0.5, 10 - 2.0) = max(5, 8) = 8
     * Xn = 0 + (8 / 100) * 10 * 1 = 0.8   (frensiz hali 1.0 idi)
     */
    @Test
    public void t1_trendNegatifken_arzKisilir() {
        holtTrend = -0.02;

        double xn = runXn(agents(10, 10, 10, 0, 0));

        assertEquals("Negatif trend serviceAgents'i dusurmeli", 0.8, xn, 1e-9);
        assertTrue("Fren arzi AZALTMALI", xn < 1.0);
    }

    /**
     * SIGORTA KAPISI: asiri negatif trend arzi sifirlamaz, %50 tabana oturur.
     *
     * <p>leaving = -1.0 * 100 = -100 -> 10 - 100 = -90 (negatif!)
     * taban: 10 * 0.5 = 5 -> serviceAgents = 5
     * Xn = (5 / 100) * 10 = 0.5
     */
    @Test
    public void t1_asiriNegatifTrend_yuzde50TabanaOturur() {
        holtTrend = -1.0;

        double xn = runXn(agents(10, 10, 10, 0, 0));

        assertEquals("Taban serviceAgents'in yarisi olmali", 0.5, xn, 1e-9);
        assertTrue("Arz sifirlanmamali", xn > 0.0);
    }

    /** Vardiya sonu senaryosu: bosta ajan 0, yalniz konusanlar var — eski fren buraya ISLEMIYORDU. */
    @Test
    public void t1_vardiyaSonu_bostaAjanYokkenDeFrenCalisir() {
        holtTrend = -0.05;

        // avail=0 (vardiya sonu), incall=20
        // leaving = -0.05 * 100 = -5 -> serviceAgents = max(10, 15) = 15
        // Xn = 0 + (15/100) * 10 = 1.5   (frensiz: 20/100*10 = 2.0)
        double xn = runXn(agents(20, 20, 20, 0, 0));

        assertEquals(1.5, xn, 1e-9);
        assertTrue("Bosta ajan 0 iken bile fren isirmali", xn < 2.0);
    }

    // ==================================================================== T2

    /** REGRESYON KAPISI: esik altinda startup degeri kullanilir (eski davranisla ayni). */
    @Test
    public void t2_esikAltinda_startupDegeriKullanilir() {
        holtTrend = 0.0;

        // 50 ornek (< 100) -> startup avgTalk = 100 kullanilmali
        // Xn = 0 + (10 / 100) * 10 = 1.0
        double xn = runXnWithTalk(agents(10, 10, 10, 0, 0), 50, 40.0);

        assertEquals("Esik altinda canli ortalama KULLANILMAMALI", 1.0, xn, 1e-9);
    }

    /** REGRESYON KAPISI: tam guven esiginde canli deger kullanilir (eski davranisla ayni). */
    @Test
    public void t2_tamGuvenEsiginde_canliDegerKullanilir() {
        holtTrend = 0.0;

        // 400 ornek (>= 400) -> canli avgTalk = 40 kullanilmali
        // Xn = 0 + (10 / 40) * 10 = 2.5
        double xn = runXnWithTalk(agents(10, 10, 10, 0, 0), FULL_TRUST, 40.0);

        assertEquals("Tam guven esiginde canli deger kullanilmali", 2.5, xn, 1e-9);
    }

    /**
     * FONKSIYONEL KAPI: arada KADEMELI karisim — eskiden sert atlama vardi.
     *
     * <p>n=250: w = (250-100)/(400-100) = 0.5
     * avgTalk = 40*0.5 + 100*0.5 = 70
     * Xn = (10 / 70) * 10 = 1.4285714...
     *
     * <p>Eski davranista n=250 iken dogrudan canliya (40) atlanir, Xn = 2.5 olurdu.
     * Yeni deger arada kalmali: 1.0 (startup) < 1.428 < 2.5 (canli).
     */
    @Test
    public void t2_aradaKademeliKarisimUygulanir() {
        holtTrend = 0.0;

        double xn = runXnWithTalk(agents(10, 10, 10, 0, 0), 250, 40.0);

        assertEquals(10.0 / 70.0 * 10.0, xn, 1e-9);
        assertTrue("Karisim startup ile canli ARASINDA olmali", xn > 1.0 && xn < 2.5);
    }

    /**
     * KORUMA KAPISI: canli ortalama 0 ise (olcum yok) startup'a dusulur.
     * resolveAverage ile ayni koruma; bolme hatasi olusmamali.
     */
    @Test
    public void t2_canliOlcumYokken_startupaDusulur() {
        holtTrend = 0.0;

        // 250 ornek ama talkTime = 0 -> canli ortalama 0 -> startup (100) kullanilmali
        double xn = runXnWithTalk(agents(10, 10, 10, 0, 0), 250, 0.0);

        assertEquals("Canli olcum yokken startup kullanilmali", 1.0, xn, 1e-9);
    }

    /** Gun basi senaryosu: canli ortalama carpik DUSUK iken arz asiri sismemeli. */
    @Test
    public void t2_gunBasindaCarpikOlcum_arziAsiriSisirmez() {
        holtTrend = 0.0;

        // Gercek talk ~150, gun basinda olculen 48.7 (sadece kisa cagrilar bitti)
        double kademeliXn = runXnWithTalk(agents(10, 10, 10, 0, 0), 150, 48.7);
        double eskiSertGecisXn = 10.0 / 48.7 * 10.0;   // eski davranis: dogrudan canliya atla

        assertTrue("Kademeli gecis, sert gecisten DAHA AZ arz uretmeli",
                kademeliXn < eskiSertGecisXn);
    }

    // ==================================================================== yardimcilar

    private double runXn(AgentMetricsDTO agents) {
        // 50 talk ornegi -> esik alti -> startup avgTalk (100) kullanilir, T2 devrede degil
        return run(agents, 50, 100.0).getCallData().getRawXn();
    }

    private double runXnWithTalk(AgentMetricsDTO agents, int sampleCount, double liveTalk) {
        return run(agents, sampleCount, liveTalk).getCallData().getRawXn();
    }

    private PredictiveGammaCalculationResult run(AgentMetricsDTO agents, int talkSamples, double liveTalk) {
        PredictiveGammaStartupProfile startup =
                new PredictiveGammaStartupProfile(0.90, 0.90, 0.90, 100.0, 10.0, 0.0, false);
        return calculator.calculateXn(campaign, agents, ringing(),
                emptyWindow(), emptyWindow(), emptyWindow(),
                talkWindow(talkSamples, liveTalk), ringWindow(),
                startup, BLEND_START, 50);
    }

    private AgentMetricsDTO agents(double physical, double total, double incall, double avail, double acw) {
        return new AgentMetricsDTO(physical, total, incall, avail, acw);
    }

    private RingingMetricsDTO ringing() {
        RingingMetricsDTO m = new RingingMetricsDTO();
        m.setCbRinging(0);
        m.setRetryRinging(0);
        m.setFreshRinging(0);
        m.setTotalRinging(0);
        return m;
    }

    private PredictiveGammaSnapshot emptyWindow() {
        return new PredictiveGammaRecentCalls(WINDOW_CAPACITY).snapshot(campaign);
    }

    private PredictiveGammaSnapshot talkWindow(int count, double talkTime) {
        PredictiveGammaRecentCalls recent = new PredictiveGammaRecentCalls(WINDOW_CAPACITY);
        for (int i = 0; i < count; i++) {
            PredictiveGammaCallModel m = new PredictiveGammaCallModel();
            m.setCallType(Constants.CALL_TYPES.FRESH);
            m.setAnswered(true);
            m.setResultCode(10);
            m.setTalkTime((int) talkTime);
            recent.add(m);
        }
        return recent.snapshot(campaign);
    }

    /** avgRing startup degerinde (10) kalsin diye bos birakilir. */
    private PredictiveGammaSnapshot ringWindow() {
        return new PredictiveGammaRecentCalls(WINDOW_CAPACITY).snapshot(campaign);
    }
}
