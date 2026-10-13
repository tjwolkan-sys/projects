package tests.predictive.gamma;

import com.powerdialer.dto.AgentMetricsDTO;
import com.powerdialer.dto.RingingMetricsDTO;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.predictive.gamma.calculator.PredictiveGammaCallCalculator;
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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gamma {@code avgTalk} penceresinin TRC kapisini kilitler.
 *
 * <p><b>Degisiklik:</b> pencere artik yalniz TRC 10 (Normal call clearing) degil, TRC 85
 * (Ambiguous / Belirsiz Kayit) cagrilarini da alir. 85, "gorusme gerceklesti ama agent sonuc
 * kodu girmedi" demektir; talk suresi AMI damgalarindan gelir ve URC'den bagimsizdir, yani
 * kapasite acisindan gecerli bir gozlemdir. Gerekce:
 * {@link Constants#GAMMA_TALK_VALID_RESULT_CODES}.
 *
 * <p><b>Test stratejisi:</b> once regresyon kapilari (eski davranis aynen duruyor mu), sonra
 * yeni davranis, sonra sinir/sizinti kontrolleri.
 */
@RunWith(MockitoJUnitRunner.class)
public class GammaTalkWindowResultCodeTest {

    private static final long CID = 7L;

    /** {@code MIN_TALK_TIME_SEC} ile ayni; pencereye girebilmek icin alt sinir. */
    private static final int MIN_TALK = 10;

    private static final long TRC_NORMAL_CLEARING = 10L;
    private static final long TRC_AMBIGUOUS = 85L;
    private static final long TRC_ABANDON_TIMEOUT = 78L;
    private static final long TRC_AMD = 83L;

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
        lenient().when(campaign.getName()).thenReturn("Talk Window TRC Test");
        lenient().when(campaign.getGammaWindowCapacity()).thenReturn(400);
        lenient().when(campaign.getGammaRequiredWindowSize()).thenReturn(100);
        lenient().when(campaign.isGammaPlusEnabled()).thenReturn(false);
        lenient().when(cdrService.findGammaStartupAverageTalkTime(CID, MIN_TALK)).thenReturn(100.0);
        lenient().when(cdrService.findGammaStartupAverageRingTime(CID)).thenReturn(10.0);
        lenient().when(cdrService.findGammaStartupAverageRingTimeAnswered(CID)).thenReturn(13.0);
        lenient().when(cdrService.findGammaStartupAverageAcwTime(CID)).thenReturn(0.0);
        manager.onCampaignStart(campaign);
    }

    // ==================================================== REGRESYON: eski davranis korunuyor

    /** TRC 10 pencereye girer — degisiklik oncesiyle bit-esit. */
    @Test
    public void trc10_pencereyeGirer() {
        manager.onCallEnded(call(TRC_NORMAL_CLEARING, true), 120, 14);

        assertEquals("TRC 10 pencereye girmeli", 1, countSamples(successfulWindow()));
    }

    /** Abandon (78) pencereye GIRMEZ — agent gorusmesi olmamistir. */
    @Test
    public void trc78Abandon_pencereyeGirmez() {
        manager.onCallEnded(call(TRC_ABANDON_TIMEOUT, true), 120, 14);

        assertEquals("Abandon cagrisi avgTalk penceresine girmemeli", 0, countSamples(successfulWindow()));
    }

    /** AMD (83) pencereye GIRMEZ — telesekreter, agent gorusmesi degil. */
    @Test
    public void trc83Amd_pencereyeGirmez() {
        manager.onCallEnded(call(TRC_AMD, true), 120, 14);

        assertEquals("AMD cagrisi avgTalk penceresine girmemeli", 0, countSamples(successfulWindow()));
    }

    /** Acilmamis cagri pencereye GIRMEZ (isAnswered sarti korunuyor). */
    @Test
    public void acilmamisCagri_pencereyeGirmez() {
        manager.onCallEnded(call(TRC_NORMAL_CLEARING, false), 120, 14);

        assertEquals("Acilmamis cagri pencereye girmemeli", 0, countSamples(successfulWindow()));
    }

    // ==================================================== YENI DAVRANIS: TRC 85

    /** ASIL KAPI: TRC 85 artik pencereye girer. */
    @Test
    public void trc85Ambiguous_pencereyeGirer() {
        manager.onCallEnded(call(TRC_AMBIGUOUS, true), 90, 14);

        PredictiveGammaSnapshot window = successfulWindow();
        assertEquals("TRC 85 avgTalk penceresine girmeli", 1, countSamples(window));
        assertEquals("Talk suresi aynen tasinmali", 90, first(window).getTalkTime());
    }

    /** TRC 85 ve TRC 10 ayni pencerede birlikte yasar; avgTalk ikisinden hesaplanir. */
    @Test
    public void trc10VeTrc85_ayniPenceredeBirlikte() {
        manager.onCallEnded(call(TRC_NORMAL_CLEARING, true), 200, 14);
        manager.onCallEnded(call(TRC_AMBIGUOUS, true), 100, 14);

        PredictiveGammaSnapshot window = successfulWindow();
        assertEquals("Iki cagri da pencerede olmali", 2, countSamples(window));

        int toplam = 0;
        for (PredictiveGammaCallModel m : window) toplam += m.getTalkTime();
        assertEquals("Ortalama ikisinden hesaplanmali (200+100)/2 = 150", 150, toplam / 2);
    }

    // ==================================================== SINIR: MIN_TALK_TIME_SEC hala gecerli

    /**
     * TRC 85 olsa bile 10 sn altindaki cagri pencereye GIRMEZ.
     *
     * <p>Onemli: olculen TRC 85 vakalarinin cogu 3-16 sn araligindaydi. Bu kapi kalkarsa
     * pencere cop ile dolar ve avgTalk sahte sekilde duser.
     */
    @Test
    public void trc85_minTalkAltinda_pencereyeGirmez() {
        manager.onCallEnded(call(TRC_AMBIGUOUS, true), MIN_TALK - 1, 14);

        assertEquals("10 sn altindaki TRC 85 pencereye girmemeli", 0, countSamples(successfulWindow()));
    }

    /** Tam sinirda (10 sn) girer — {@code >=} karsilastirmasi. */
    @Test
    public void trc85_tamSinirda_pencereyeGirer() {
        manager.onCallEnded(call(TRC_AMBIGUOUS, true), MIN_TALK, 14);

        assertEquals("Tam 10 sn pencereye girmeli", 1, countSamples(successfulWindow()));
    }

    // ==================================================== SABIT SOZLESMESI

    /** Kume tam olarak {10, 85} olmali; sessizce buyurse arz hesabi kayar. */
    @Test
    public void sabitKumesi_tamOlarak10ve85() {
        assertEquals("Kume tam 2 eleman icermeli", 2, Constants.GAMMA_TALK_VALID_RESULT_CODES.size());
        assertTrue(Constants.GAMMA_TALK_VALID_RESULT_CODES.contains(10));
        assertTrue(Constants.GAMMA_TALK_VALID_RESULT_CODES.contains(85));
        assertFalse("Abandon (78) bu kumede OLMAMALI", Constants.GAMMA_TALK_VALID_RESULT_CODES.contains(78));
        assertFalse("AMD (83) bu kumede OLMAMALI", Constants.GAMMA_TALK_VALID_RESULT_CODES.contains(83));
    }

    /**
     * SIZINTI KAPISI: ring kumesine dokunulmadi.
     *
     * <p>{@code RING_VALID_RESULT_CODES} paylasimli bir sabittir — hem canli ring kapisini hem
     * CDRDao'nun iki ring sorgusunu besler. 85'in oraya sizmasi avgRing'i degistirir ve Gamma
     * Plus KAPALI kampanyalari da etkilerdi.
     */
    @Test
    public void ringKumesineSizintiYok() {
        assertFalse("85 ring kumesine sizmamali", Constants.RING_VALID_RESULT_CODES.contains(85));
        assertTrue("Ring kumesi eskisi gibi 10'u icermeli", Constants.RING_VALID_RESULT_CODES.contains(10));
    }

    // ==================================================== yardimcilar

    private Call call(long resultCodeId, boolean answered) {
        TelephonyResultCode trc = new TelephonyResultCode();
        trc.setId(resultCodeId);

        Call call = new Call();
        call.setCampaign(campaign);
        call.setCallType(Constants.CALL_TYPES.FRESH);
        call.setTelephonyResultCode(trc);
        call.getCdr().setId(1000L + resultCodeId);
        call.getCdr().setTelephonyRC(trc);
        call.setAnswerDate(answered ? 1.0 : null);
        call.setAbandoned(false);
        return call;
    }

    /** Manager'in calculateXn'e gecirdigi basarili-cagri penceresini yakalar (7. parametre). */
    private PredictiveGammaSnapshot successfulWindow() {
        when(sharedGammaSnapshotProvider.getAgentMetrics(CID)).thenReturn(AgentMetricsDTO.zero());
        when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(ringingManager);
        when(ringingManager.getRealTimeRingingMetrics(campaign)).thenReturn(new RingingMetricsDTO());
        when(calculator.calculateXn(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PredictiveGammaCalculationResult(0.0, 1.0, 1.0, 1.0, new PredictiveGammaCallData(campaign, null)));

        manager.calculateDialBudget(campaign, 1, 1, dialerFacade, null);

        ArgumentCaptor<PredictiveGammaSnapshot> successful = ArgumentCaptor.forClass(PredictiveGammaSnapshot.class);
        verify(calculator).calculateXn(eq(campaign), any(), any(), any(), any(), any(),
                successful.capture(), any(), any(), anyInt(), anyInt());
        return successful.getValue();
    }

    private int countSamples(PredictiveGammaSnapshot snapshot) {
        int count = 0;
        for (PredictiveGammaCallModel ignored : snapshot) count++;
        return count;
    }

    private PredictiveGammaCallModel first(PredictiveGammaSnapshot snapshot) {
        return snapshot.iterator().next();
    }
}
