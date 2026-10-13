package tests.predictive.gamma;

import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallModel;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaRecentCalls;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaSnapshot;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MADDE 1 destek davranışı — {@code markAbandoned}.
 *
 * <p>{@code add()} abandon sayaçlarını EKLEME anında artırır. Gamma Plus'ta cevaplanan çağrı
 * pencereye {@code abandoned=false} ile answer anında giriyor; sonradan abandon olursa damga
 * kayıt pencereden düşmeden gelmelidir.
 */
public class PredictiveGammaRecentCallsMarkAbandonedTest {

    private PredictiveGammaRecentCalls recentCalls;
    private Campaign campaign;

    @Before
    public void setUp() {
        recentCalls = new PredictiveGammaRecentCalls(10);
        campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(1L);
        when(campaign.getName()).thenReturn("mark_abandoned_test");
    }

    @Test
    public void markAbandoned_incrementsCountersForCustomerAbandon() {
        PredictiveGammaCallModel m = answeredCall();
        recentCalls.add(m);
        m.setResultCode(84);

        assertTrue(recentCalls.markAbandoned(m));

        PredictiveGammaSnapshot s = recentCalls.snapshot(campaign);
        assertEquals(1, s.getTotalCallCount());
        assertEquals(1, s.getTotalAbandonCallCount());
        assertEquals(1, s.getTotalAbandonedByCustomer());
        assertEquals(0, s.getTotalAbandonedBySystem());
        assertTrue(m.isAbandoned());
    }

    @Test
    public void markAbandoned_attributesResultCode78ToSystem() {
        PredictiveGammaCallModel m = answeredCall();
        recentCalls.add(m);
        m.setResultCode(78);

        assertTrue(recentCalls.markAbandoned(m));

        PredictiveGammaSnapshot s = recentCalls.snapshot(campaign);
        assertEquals(1, s.getTotalAbandonedBySystem());
        assertEquals(0, s.getTotalAbandonedByCustomer());
    }

    @Test
    public void markAbandoned_isIdempotent() {
        // Ayni kayit iki kez damgalanirsa abandon orani yukari saparadi.
        PredictiveGammaCallModel m = answeredCall();
        recentCalls.add(m);
        m.setResultCode(84);

        assertTrue(recentCalls.markAbandoned(m));
        assertFalse("Ikinci damga sayaci artirmamali", recentCalls.markAbandoned(m));

        assertEquals(1, recentCalls.snapshot(campaign).getTotalAbandonCallCount());
    }

    @Test
    public void markAbandoned_alreadyAbandonedRecord_isNotDoubleCounted() {
        // add() sirasinda zaten abandon olarak sayilmis kayit tekrar sayilmamali.
        PredictiveGammaCallModel m = answeredCall();
        m.setAbandoned(true);
        m.setResultCode(84);
        recentCalls.add(m);

        assertFalse(recentCalls.markAbandoned(m));
        assertEquals(1, recentCalls.snapshot(campaign).getTotalAbandonCallCount());
    }

    @Test
    public void markAbandoned_recordEvictedFromWindow_isIgnored() {
        // Kapasite 10; ilk kayit 10 yeni eklemeden sonra pencereden duser.
        PredictiveGammaCallModel evicted = answeredCall();
        recentCalls.add(evicted);
        for (int i = 0; i < 10; i++) recentCalls.add(answeredCall());
        evicted.setResultCode(84);

        assertFalse("Pencereden dusen kayit sayaci artirmamali", recentCalls.markAbandoned(evicted));
        assertEquals(0, recentCalls.snapshot(campaign).getTotalAbandonCallCount());
    }

    @Test
    public void markAbandoned_recordNeverAdded_isIgnored() {
        PredictiveGammaCallModel stranger = answeredCall();
        stranger.setResultCode(84);

        assertFalse(recentCalls.markAbandoned(stranger));
        assertEquals(0, recentCalls.snapshot(campaign).getTotalAbandonCallCount());
    }

    @Test
    public void markAbandoned_null_isIgnored() {
        assertFalse(recentCalls.markAbandoned(null));
    }

    @Test
    public void markAbandoned_findsRecordAnywhereInWindow() {
        PredictiveGammaCallModel target = answeredCall();
        for (int i = 0; i < 4; i++) recentCalls.add(answeredCall());
        recentCalls.add(target);
        for (int i = 0; i < 4; i++) recentCalls.add(answeredCall());
        target.setResultCode(84);

        assertTrue(recentCalls.markAbandoned(target));
        assertEquals(1, recentCalls.snapshot(campaign).getTotalAbandonCallCount());
    }

    @Test
    public void markAbandoned_worksAfterBufferWrapAround() {
        // head kapasiteyi asip basa dondukten sonra kimlik taramasi hala dogru olmali.
        for (int i = 0; i < 13; i++) recentCalls.add(answeredCall());
        PredictiveGammaCallModel target = answeredCall();
        recentCalls.add(target);
        target.setResultCode(78);

        assertTrue(recentCalls.markAbandoned(target));
        assertEquals(1, recentCalls.snapshot(campaign).getTotalAbandonedBySystem());
    }

    @Test
    public void markAbandoned_doesNotChangeTotalCallCount() {
        PredictiveGammaCallModel m = answeredCall();
        recentCalls.add(m);
        m.setResultCode(84);
        recentCalls.markAbandoned(m);

        assertEquals("Damga yeni kayit uretmez", 1, recentCalls.snapshot(campaign).getTotalCallCount());
        assertEquals(1, recentCalls.size());
    }

    private PredictiveGammaCallModel answeredCall() {
        PredictiveGammaCallModel m = new PredictiveGammaCallModel();
        m.setCallType(Constants.CALL_TYPES.FRESH);
        m.setAnswered(true);
        return m;
    }
}
