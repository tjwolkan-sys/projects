package com.powerdialer.engine.predictive.gamma.model;

import com.powerdialer.model.Campaign;
import lombok.Value;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Lightweight O(1) snapshot view of a ring buffer.
 */
@Value
public class PredictiveGammaSnapshot implements Iterable<PredictiveGammaCallModel> {
    String snapshotId;
    PredictiveGammaCallModel[] buf;
    int head;
    int size;
    int capacity;
    int totalCallCount;
    int totalAbandonCallCount;
    int totalAbandonedByCustomer;
    int totalAbandonedBySystem;

    @Override
    public Iterator<PredictiveGammaCallModel> iterator() {
        return new Iterator<PredictiveGammaCallModel>() {
            private int idx = 0;

            @Override
            public boolean hasNext() {
                return idx < size;
            }

            @Override
            public PredictiveGammaCallModel next() {
                if (!hasNext()) throw new NoSuchElementException();
                int realIndex = (head - size + idx + capacity) % capacity;
                PredictiveGammaCallModel e = buf[realIndex];
                idx++;
                return e;
            }
        };
    }

    public static String generateSnapshotId(Campaign campaign) {
        String timestamp = new SimpleDateFormat("ddMMyyyy_HHmm").format(new Date());
        return campaign.getId() + "_" + campaign.getName().replaceAll("\\s+", "_") + "_" + timestamp;
    }
}
