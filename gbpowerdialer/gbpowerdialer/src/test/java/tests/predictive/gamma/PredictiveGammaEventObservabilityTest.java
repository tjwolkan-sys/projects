package tests.predictive.gamma;

import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallData;
import com.powerdialer.model.Campaign;
import com.powerdialer.redis.event.predictive.PredictiveGammaEvent;
import com.powerdialer.util.Constants;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Gamma Plus izlenebilirlik alanlarının {@code PredictiveGammaCallData} → {@code PredictiveGammaEvent}
 * kopyalanmasını kilitler.
 *
 * <p>Bu hat elle yazılmış, alan alan bir kopyalamadır (reflection/Dozer yok); unutulan tek satır
 * kolonu kalıcı olarak NULL bırakır ve hiçbir yerde hata vermez. Tüketici tarafında da
 * (`gbpredictivecalculateviews`) hiç test yok — bu yüzden sözleşme burada kilitleniyor.
 */
public class PredictiveGammaEventObservabilityTest {

    /**
     * Alan adları tüketici projedeki DTO/entity ile BİREBİR aynı olmalı: DTO→entity dönüşümü
     * Gson roundtrip ile isim bazlı eşleşiyor, tek harf farkı sessizce NULL kolon üretir.
     */
    private static final String[] OBSERVABILITY_FIELDS = {
            "gammaPlus",
            "cbRingAnswerRate", "retryRingAnswerRate", "freshRingAnswerRate",
            "ringingWeightedCount", "ringingCbWeightedCount", "ringingRetryWeightedCount", "ringingFreshWeightedCount",
            "drainMarginalCost", "drainFloorApplied"
    };

    @Test
    public void event_copiesEveryGammaPlusObservabilityField() {
        PredictiveGammaCallData data = data();
        data.setGammaPlus(true);
        data.setCbRingAnswerRate(0.61);
        data.setRetryRingAnswerRate(0.09);
        data.setFreshRingAnswerRate(0.14);
        data.setRingingWeightedCount(6.76);
        data.setRingingCbWeightedCount(1.02);
        data.setRingingRetryWeightedCount(2.30);
        data.setRingingFreshWeightedCount(3.44);
        data.setDrainMarginalCost(1.87);
        data.setDrainFloorApplied(true);

        PredictiveGammaEvent event = new PredictiveGammaEvent("test", data);

        assertTrue(event.getGammaPlus());
        assertEquals(0.61, event.getCbRingAnswerRate(), 1e-9);
        assertEquals(0.09, event.getRetryRingAnswerRate(), 1e-9);
        assertEquals(0.14, event.getFreshRingAnswerRate(), 1e-9);
        assertEquals(6.76, event.getRingingWeightedCount(), 1e-9);
        assertEquals(1.02, event.getRingingCbWeightedCount(), 1e-9);
        assertEquals(2.30, event.getRingingRetryWeightedCount(), 1e-9);
        assertEquals(3.44, event.getRingingFreshWeightedCount(), 1e-9);
        assertEquals(1.87, event.getDrainMarginalCost(), 1e-9);
        assertTrue(event.getDrainFloorApplied());
    }

    @Test
    public void event_flagOffArm_carriesFalseFlagAndNullPlusOnlyFields() {
        // Bayrak kapalı kolda gammaPlus=false DOLU gelmeli (A/B filtresi buna bakar),
        // plus'a özel metrikler ise null kalmalı — "hesaplanmadı" bilgisi taşırlar.
        PredictiveGammaCallData data = data();
        data.setGammaPlus(false);

        PredictiveGammaEvent event = new PredictiveGammaEvent("test", data);

        assertFalse(event.getGammaPlus());
        assertNull(event.getCbRingAnswerRate());
        assertNull(event.getRetryRingAnswerRate());
        assertNull(event.getFreshRingAnswerRate());
        assertNull(event.getRingingWeightedCount());
        assertNull(event.getRingingCbWeightedCount());
        assertNull(event.getRingingRetryWeightedCount());
        assertNull(event.getRingingFreshWeightedCount());
        assertNull(event.getDrainMarginalCost());
        assertNull(event.getDrainFloorApplied());
    }

    @Test
    public void event_doesNotSilentlyDropAnyObservabilityField() {
        // Kopyalama satırı unutulursa alan kalıcı NULL kalır ve hiçbir yerde hata vermez.
        // Bu test her alanı ayrı ayrı doldurup event'te gerçekten göründüğünü doğrular.
        List<String> dropped = new ArrayList<>();
        for (String name : OBSERVABILITY_FIELDS) {
            PredictiveGammaCallData data = data();
            setField(data, name, sampleValueFor(data, name));
            PredictiveGammaEvent event = new PredictiveGammaEvent("test", data);
            if (readField(event, name) == null) {
                dropped.add(name);
            }
        }
        assertTrue("Event'e kopyalanmayan alan(lar): " + dropped, dropped.isEmpty());
    }

    @Test
    public void observabilityFieldsExistOnBothCallDataAndEvent() {
        // Alan adı sözleşmesi: tüketici projedeki DTO/entity bu adlarla eşleşiyor.
        for (String name : OBSERVABILITY_FIELDS) {
            assertTrue("PredictiveGammaCallData." + name + " yok", hasField(PredictiveGammaCallData.class, name));
            assertTrue("PredictiveGammaEvent." + name + " yok", hasField(PredictiveGammaEvent.class, name));
        }
    }

    @Test
    public void observabilityFieldsAreBoxedSoNullMeansNotComputed() {
        // Primitive olsalardı bayrak kapalı kolda 0.0 yazılır ve "hesaplanmadı" ile
        // "sıfır ölçüldü" ayırt edilemezdi.
        for (String name : OBSERVABILITY_FIELDS) {
            Class<?> type = fieldType(PredictiveGammaEvent.class, name);
            assertFalse(name + " primitive olmamali", type.isPrimitive());
        }
    }

    // ---------------------------------------------------------------- helper

    private PredictiveGammaCallData data() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(10L);
        when(campaign.getName()).thenReturn("Gamma Plus Observability");
        when(campaign.getState()).thenReturn(Constants.CAMPAIGN_STATE.RUNNING);
        return new PredictiveGammaCallData(campaign, null);
    }

    private Object sampleValueFor(PredictiveGammaCallData data, String name) {
        Class<?> type = fieldType(PredictiveGammaCallData.class, name);
        return Boolean.class.equals(type) ? Boolean.TRUE : Double.valueOf(0.75);
    }

    private boolean hasField(Class<?> clazz, String name) {
        try {
            clazz.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private Class<?> fieldType(Class<?> clazz, String name) {
        try {
            return clazz.getDeclaredField(name).getType();
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(clazz.getSimpleName() + "." + name + " yok", e);
        }
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Object readField(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
