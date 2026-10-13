package com.powerdialer.engine.predictive.gamma.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class HoltsTrendAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(HoltsTrendAnalyzer.class);
    
    private final ConcurrentHashMap<Long, HoltState> stateMap = new ConcurrentHashMap<>();
    private static final double ALPHA = 0.3;
    private static final double BETA = 0.2;

    /**
     * Saniyede 1 kez tetiklenen (Discrete) Holt's Exponential Smoothing metodu.
     * Üretim bandındaki (P_Total) değişimi takip ederek anlık ajan ivmesini döner.
     */
    public HoltResult updateAndGetTrend(long campaignId, int currentProductionTotal) {
        HoltState finalState = stateMap.compute(campaignId, (cid, state) -> {
            long now = System.currentTimeMillis();
            
            if (state == null) {
                return new HoltState(currentProductionTotal, 0.0, now);
            }

            double deltaSeconds = (now - state.lastUpdateMs) / 1000.0;

            // Eğer uzun zaman geçmişse (kampanya duraklamışsa vb.) hafızayı taze başla
            if (deltaSeconds > 20.0) {
                log.debug("[GAMMA] Holts trend flushed for cid={}, duration without calc: {}s", campaignId, deltaSeconds);
                return new HoltState(currentProductionTotal, 0.0, now);
            }

            double oldLevel = state.level;
            double oldTrend = state.trend;
            
            // Yeni Level = (Gerçek Değer * Alpha) + (Eski Değerler * (1-Alpha))
            double newLevel = (ALPHA * currentProductionTotal) + (1 - ALPHA) * (oldLevel + oldTrend);
            
            // Yeni Trend = (Level'daki Değişim * Beta) + (Eski Trend * (1-Beta))
            double newTrend = (BETA * (newLevel - oldLevel)) + (1 - BETA) * oldTrend;

            state.level = newLevel;
            state.trend = newTrend;
            state.lastUpdateMs = now;

            return state;
        });
        return new HoltResult(finalState.level, finalState.trend, finalState.lastUpdateMs);
    }

    /** Kampanya durdurulduğunda hafızayı sıfırlar */
    public void reset(long campaignId) {
        stateMap.remove(campaignId);
    }

    private static class HoltState {
        double level;
        double trend;
        long lastUpdateMs;

        HoltState(double level, double trend, long lastUpdateMs) {
            this.level = level;
            this.trend = trend;
            this.lastUpdateMs = lastUpdateMs;
        }
    }

    public static class HoltResult {
        public final double level;
        public final double trend;
        public final long lastUpdateMs;

        public HoltResult(double level, double trend, long lastUpdateMs) {
            this.level = level;
            this.trend = trend;
            this.lastUpdateMs = lastUpdateMs;
        }
    }
}
