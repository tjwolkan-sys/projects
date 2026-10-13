package tests.dialer;

import com.powerdialer.dto.RingingMetricsDTO;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Originate edilmiş ama Asterisk VarSetEvent'i henüz gelmemiş çağrıların
 * Gamma ringingLoad hesabında görülmesini sağlayan pending ringing buffer
 * davranışını kilitler.
 *
 * AMI VarSet roundtrip 1-2 saniye sürebilir; bu pencerede CallConsumer Gamma
 * tick'i koşarsa originate edilmiş çağrıları "uçuşta" sayamaz ve over-dial olur.
 * Pending cache bu pencereyi kapatır.
 */
@RunWith(MockitoJUnitRunner.class)
public class CampaignRealTimeRingingManagerPendingRingingTest {

    private static final long CID = 42L;

    @Mock
    private Campaign campaign;

    private CampaignRealTimeRingingManager manager;

    @Before
    public void setUp() {
        manager = new CampaignRealTimeRingingManager();
        when(campaign.getId()).thenReturn(CID);
        lenient().when(campaign.getRingTimeout()).thenReturn(30);
        manager.onCampaignStart(campaign);
    }

    @Test
    public void pendingCall_isCountedInTotalRinging() {
        Call call = freshCall("call-1");

        manager.addPendingRingingCall(call);

        RingingMetricsDTO metrics = manager.getRealTimeRingingMetrics(campaign);
        assertEquals(1, metrics.getTotalRinging());
        assertEquals(1, metrics.getFreshRinging());
        assertEquals(0, metrics.getCbRinging());
        assertEquals(0, metrics.getRetryRinging());
    }

    @Test
    public void pendingCall_typeBreakdown_callback() {
        Call call = callOfType("cb-1", Constants.CALL_TYPES.CALLBACK);

        manager.addPendingRingingCall(call);

        RingingMetricsDTO metrics = manager.getRealTimeRingingMetrics(campaign);
        assertEquals(1, metrics.getTotalRinging());
        assertEquals(1, metrics.getCbRinging());
    }

    @Test
    public void pendingCall_typeBreakdown_retry() {
        Call call = callOfType("retry-1", Constants.CALL_TYPES.RETRY);

        manager.addPendingRingingCall(call);

        RingingMetricsDTO metrics = manager.getRealTimeRingingMetrics(campaign);
        assertEquals(1, metrics.getTotalRinging());
        assertEquals(1, metrics.getRetryRinging());
    }

    @Test
    public void varSetTransition_movesCallFromPendingToRealTime_withoutDoubleCounting() {
        Call call = freshCall("call-2");
        manager.addPendingRingingCall(call);

        // VarSetEvent simülasyonu: addRingingCall pending'den otomatik çıkarmalı.
        manager.addRingingCall(call);

        RingingMetricsDTO metrics = manager.getRealTimeRingingMetrics(campaign);
        assertEquals("VarSet sonrası toplam aynı kalmalı, double-count olmamalı",
                1, metrics.getTotalRinging());
        assertEquals(1, metrics.getFreshRinging());
    }

    @Test
    public void removePendingRingingCall_clearsEntry() {
        Call call = freshCall("call-3");
        manager.addPendingRingingCall(call);

        manager.removePendingRingingCall(call);

        RingingMetricsDTO metrics = manager.getRealTimeRingingMetrics(campaign);
        assertEquals(0, metrics.getTotalRinging());
    }

    @Test
    public void pendingForUnstartedCampaign_isNoOp() {
        Campaign other = mockOtherCampaign();
        Call call = freshCallForCampaign("call-4", other);

        manager.addPendingRingingCall(call);
        manager.removePendingRingingCall(call);

        RingingMetricsDTO metrics = manager.getRealTimeRingingMetrics(other);
        assertEquals(0, metrics.getTotalRinging());
    }

    @Test
    public void onCampaignStop_clearsPendingCache() {
        Call call = freshCall("call-5");
        manager.addPendingRingingCall(call);

        manager.onCampaignStop(campaign);
        manager.onCampaignStart(campaign);

        RingingMetricsDTO metrics = manager.getRealTimeRingingMetrics(campaign);
        assertEquals(0, metrics.getTotalRinging());
    }

    @Test
    public void answeredCallInPending_isNotCounted() {
        Call call = freshCall("call-6");
        call.setAnswerDate(123.0); // cevap geldi
        manager.addPendingRingingCall(call);

        RingingMetricsDTO metrics = manager.getRealTimeRingingMetrics(campaign);
        assertEquals("Cevap dönen call ringing sayılmamalı", 0, metrics.getTotalRinging());
    }

    @Test
    public void mixedRealTimeAndPending_areSummed() {
        Call ringing = freshCall("ringing-1");
        Call pending = callOfType("pending-1", Constants.CALL_TYPES.CALLBACK);

        manager.addRingingCall(ringing);     // gerçek kovaya
        manager.addPendingRingingCall(pending); // pending kovaya

        RingingMetricsDTO metrics = manager.getRealTimeRingingMetrics(campaign);
        assertEquals(2, metrics.getTotalRinging());
        assertEquals(1, metrics.getFreshRinging());
        assertEquals(1, metrics.getCbRinging());
    }

    @Test
    public void telemetry_reportsPendingCountSeparatelyFromTotal() {
        manager.addRingingCall(freshCall("real-1"));
        manager.addRingingCall(callOfType("real-2", Constants.CALL_TYPES.RETRY));
        manager.addPendingRingingCall(freshCall("pending-1"));
        manager.addPendingRingingCall(callOfType("pending-2", Constants.CALL_TYPES.CALLBACK));

        RingingMetricsDTO metrics = manager.getRealTimeRingingMetrics(campaign);

        assertEquals(4, metrics.getTotalRinging());
        assertEquals(2, metrics.getPendingTotalRinging());
        // bs-gamma-analyzer realtimeOnly = total - pending derivable; doğrula
        assertEquals(2, metrics.getTotalRinging() - metrics.getPendingTotalRinging());
    }

    @Test
    public void telemetry_pendingMaxAgeMs_reportsOldestPendingAge() throws InterruptedException {
        manager.addPendingRingingCall(freshCall("old-1"));
        Thread.sleep(50);
        manager.addPendingRingingCall(freshCall("new-1"));

        RingingMetricsDTO metrics = manager.getRealTimeRingingMetrics(campaign);

        assertEquals(2, metrics.getPendingTotalRinging());
        assertTrue("En eski pending yaşı 50ms üzerinde olmalı; ölçülen: " + metrics.getPendingMaxAgeMs(),
                metrics.getPendingMaxAgeMs() >= 50L);
    }

    @Test
    public void telemetry_pendingFieldsZeroWhenNoPending() {
        manager.addRingingCall(freshCall("real-only"));

        RingingMetricsDTO metrics = manager.getRealTimeRingingMetrics(campaign);

        assertEquals(1, metrics.getTotalRinging());
        assertEquals(0, metrics.getPendingTotalRinging());
        assertEquals(0L, metrics.getPendingMaxAgeMs());
    }

    private Call freshCall(String id) {
        return callOfType(id, Constants.CALL_TYPES.FRESH);
    }

    private Call callOfType(String id, Constants.CALL_TYPES type) {
        return freshCallForCampaign(id, campaign).withType(type);
    }

    private CallWithType freshCallForCampaign(String id, Campaign c) {
        CallWithType call = new CallWithType();
        call.setId(id);
        call.setCampaign(c);
        call.setCallType(Constants.CALL_TYPES.FRESH);
        return call;
    }

    private Campaign mockOtherCampaign() {
        Campaign other = org.mockito.Mockito.mock(Campaign.class);
        when(other.getId()).thenReturn(999L);
        lenient().when(other.getRingTimeout()).thenReturn(30);
        return other;
    }

    /** Fluent helper to set callType inline. */
    private static class CallWithType extends Call {
        CallWithType withType(Constants.CALL_TYPES type) {
            setCallType(type);
            return this;
        }
    }
}
