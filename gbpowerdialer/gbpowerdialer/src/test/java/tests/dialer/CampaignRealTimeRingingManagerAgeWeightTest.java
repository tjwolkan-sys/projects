package tests.dialer;

import com.powerdialer.dto.RingingMetricsDTO;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.predictive.gamma.model.GammaRingAgeWeight;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Utils;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * MADDE 2B — ringing havuzunun yaş ağırlıklı toplanması.
 *
 * <p>Veri zaten manager'da vardı ({@code Call.originationDate}); {@code RingingMetricsDTO}
 * API sınırında yok ediyordu. Bu sınıf iki şeyi kilitler: bayrak kapalıyken ağırlık alanları
 * dolmaz (ek maliyet ve davranış değişikliği yok), açıkken {@code Σw} ve {@code Σw²} doğru
 * hesaplanır.
 */
public class CampaignRealTimeRingingManagerAgeWeightTest {

    private static final long CID = 5L;

    private CampaignRealTimeRingingManager manager;

    @Before
    public void setUp() {
        manager = new CampaignRealTimeRingingManager();
    }

    private Campaign campaign(boolean gammaPlus) {
        Campaign campaign = new Campaign();
        campaign.setId(CID);
        campaign.setName("Ring Age Test");
        campaign.setRingTimeout(60);
        campaign.setGammaPlus(gammaPlus);
        return campaign;
    }

    private Call ringingCall(Campaign campaign, Constants.CALL_TYPES type, double ageSeconds) {
        Call call = new Call();
        call.setId("call-" + type + "-" + ageSeconds + "-" + System.nanoTime());
        call.setCampaign(campaign);
        call.setCallType(type);
        call.setOriginationDate(Utils.convertDateToDouble(new Date(System.currentTimeMillis() - (long) (ageSeconds * 1000))));
        return call;
    }

    // ---------------------------------------------------------------- bayrak kapalı

    @Test
    public void flagOff_countsAreUnchangedAndWeightsStayZero() {
        Campaign campaign = campaign(false);
        manager.onCampaignStart(campaign);
        manager.addRingingCall(ringingCall(campaign, Constants.CALL_TYPES.FRESH, 3));
        manager.addRingingCall(ringingCall(campaign, Constants.CALL_TYPES.FRESH, 28));
        manager.addRingingCall(ringingCall(campaign, Constants.CALL_TYPES.RETRY, 10));

        RingingMetricsDTO m = manager.getRealTimeRingingMetrics(campaign);

        assertEquals(3, m.getTotalRinging());
        assertEquals(2, m.getFreshRinging());
        assertEquals(1, m.getRetryRinging());
        assertEquals("Bayrak kapali: agirlik hesaplanmaz", 0.0, m.getFreshRingingWeight(), 1e-12);
        assertEquals(0.0, m.getFreshRingingWeightSq(), 1e-12);
        assertEquals(0.0, m.getRetryRingingWeight(), 1e-12);
    }

    // ---------------------------------------------------------------- bayrak açık

    @Test
    public void flagOn_freshCallHasFullWeightAndOldCallHasSmallWeight() {
        Campaign campaign = campaign(true);
        manager.onCampaignStart(campaign);
        manager.addRingingCall(ringingCall(campaign, Constants.CALL_TYPES.FRESH, 3));
        manager.addRingingCall(ringingCall(campaign, Constants.CALL_TYPES.FRESH, 30));

        RingingMetricsDTO m = manager.getRealTimeRingingMetrics(campaign);

        assertEquals(2, m.getFreshRinging());
        double expected = GammaRingAgeWeight.weight(3) + GammaRingAgeWeight.weight(30);
        assertEquals(expected, m.getFreshRingingWeight(), 0.02);
        assertTrue("Yasli cagri ham sayidan az agirlik uretmeli", m.getFreshRingingWeight() < 2.0);
    }

    @Test
    public void flagOn_weightSquareSumIsSeparateFromWeightSum() {
        Campaign campaign = campaign(true);
        manager.onCampaignStart(campaign);
        manager.addRingingCall(ringingCall(campaign, Constants.CALL_TYPES.FRESH, 30));
        manager.addRingingCall(ringingCall(campaign, Constants.CALL_TYPES.FRESH, 30));

        RingingMetricsDTO m = manager.getRealTimeRingingMetrics(campaign);

        double w = GammaRingAgeWeight.weight(30);
        assertEquals(2 * w, m.getFreshRingingWeight(), 0.02);
        assertEquals("Sigma^2 ayri tasinmali (Poisson-binom varyansi icin)", 2 * w * w, m.getFreshRingingWeightSq(), 0.02);
        assertTrue(m.getFreshRingingWeightSq() < m.getFreshRingingWeight());
    }

    @Test
    public void flagOn_weightsAreSeparatedByCallType() {
        Campaign campaign = campaign(true);
        manager.onCampaignStart(campaign);
        manager.addRingingCall(ringingCall(campaign, Constants.CALL_TYPES.CALLBACK, 2));
        manager.addRingingCall(ringingCall(campaign, Constants.CALL_TYPES.RETRY, 25));
        manager.addRingingCall(ringingCall(campaign, Constants.CALL_TYPES.FRESH, 40));

        RingingMetricsDTO m = manager.getRealTimeRingingMetrics(campaign);

        assertEquals(GammaRingAgeWeight.weight(2), m.getCbRingingWeight(), 0.02);
        assertEquals(GammaRingAgeWeight.weight(25), m.getRetryRingingWeight(), 0.02);
        assertEquals(GammaRingAgeWeight.weight(40), m.getFreshRingingWeight(), 0.02);
    }

    @Test
    public void flagOn_nullOriginationDate_getsFullWeight() {
        // FCT routing retry sirasinda originationDate gecici olarak null'a cekiliyor.
        // Bilgi yoklugunda fren zayiflatilmamali.
        Campaign campaign = campaign(true);
        manager.onCampaignStart(campaign);
        Call call = ringingCall(campaign, Constants.CALL_TYPES.FRESH, 30);
        call.setOriginationDate(null);
        manager.addRingingCall(call);

        RingingMetricsDTO m = manager.getRealTimeRingingMetrics(campaign);

        assertEquals(1.0, m.getFreshRingingWeight(), 1e-9);
        assertEquals(1.0, m.getFreshRingingWeightSq(), 1e-9);
    }

    @Test
    public void flagOn_pendingRingingCallsAreAlsoWeighted() {
        // Uçustaki (AMI bekleyen) cagrilar da havuza dahil; agirlikta atlanmamali.
        Campaign campaign = campaign(true);
        manager.onCampaignStart(campaign);
        manager.addPendingRingingCall(ringingCall(campaign, Constants.CALL_TYPES.FRESH, 1));

        RingingMetricsDTO m = manager.getRealTimeRingingMetrics(campaign);

        assertEquals(1, m.getFreshRinging());
        assertEquals(1, m.getPendingTotalRinging());
        assertTrue(m.getFreshRingingWeight() > 0.9);
    }

    @Test
    public void flagOn_answeredCallLeavesThePoolAndItsWeight() {
        Campaign campaign = campaign(true);
        manager.onCampaignStart(campaign);
        Call call = ringingCall(campaign, Constants.CALL_TYPES.FRESH, 5);
        manager.addRingingCall(call);
        call.setAnswerDate(1.0);

        RingingMetricsDTO m = manager.getRealTimeRingingMetrics(campaign);

        assertEquals(0, m.getTotalRinging());
        assertEquals(0.0, m.getFreshRingingWeight(), 1e-12);
    }

    @Test
    public void flagOn_emptyPool_producesZeroWeights() {
        Campaign campaign = campaign(true);
        manager.onCampaignStart(campaign);

        RingingMetricsDTO m = manager.getRealTimeRingingMetrics(campaign);

        assertEquals(0, m.getTotalRinging());
        assertEquals(0.0, m.getFreshRingingWeight(), 1e-12);
        assertEquals(0.0, m.getCbRingingWeight(), 1e-12);
        assertEquals(0.0, m.getRetryRingingWeight(), 1e-12);
    }

    @Test
    public void flagOn_weightSumIsAlwaysBelowRawCountForAgedPool() {
        // 2B'nin ozu: yasli havuz ham sayidan daha az fren uretir.
        Campaign campaign = campaign(true);
        manager.onCampaignStart(campaign);
        for (int i = 0; i < 8; i++) {
            manager.addRingingCall(ringingCall(campaign, Constants.CALL_TYPES.FRESH, 22 + i));
        }

        RingingMetricsDTO m = manager.getRealTimeRingingMetrics(campaign);

        assertEquals(8, m.getFreshRinging());
        assertTrue("Yasli havuzda Sw << N olmali: " + m.getFreshRingingWeight(),
                m.getFreshRingingWeight() < 3.0);
    }
}
