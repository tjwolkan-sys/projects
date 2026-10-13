package com.powerdialer.engine.predictive.gamma;

import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallModel;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaRecentCalls;
import com.powerdialer.model.Campaign;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PredictiveGammaRecentCallsTest {
    private PredictiveGammaRecentCalls recentCalls;
    private Campaign campaign;

    @Before
    public void setUp() {
        recentCalls = new PredictiveGammaRecentCalls(400);
        campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(1L);
        when(campaign.getName()).thenReturn("test_campaign");
    }

    /** empty buffer should have size = 0 */
    @Test
    public void shouldStartEmpty() {
        assertEquals(0, recentCalls.size());
        assertFalse(recentCalls.snapshot(campaign).iterator().hasNext());
    }

    /** add one call and verify size = 1 and snapshot contains it */
    @Test
    public void shouldAddSingleCall() {
        PredictiveGammaCallModel call = new PredictiveGammaCallModel();
        call.setAnswered(true);
        call.setTalkTime(10);
        call.setRingTime(5);

        recentCalls.add(call);

        assertEquals(1, recentCalls.size());
        PredictiveGammaCallModel fromSnapshot = recentCalls.snapshot(campaign).iterator().next();
        assertEquals(call, fromSnapshot);
    }

    /** Adds fewer calls than capacity and checks that size and snapshot match the number added. */
    @Test
    public void shouldAddMultipleCalls() {
        int addCount = Math.min(5, recentCalls.getCapacity() - 1);

        for (int i = 0; i < addCount; i++) {
            PredictiveGammaCallModel call = new PredictiveGammaCallModel();
            call.setAnswered(i % 2 == 0);
            call.setTalkTime(i * 10);
            call.setRingTime(i);
            recentCalls.add(call);
        }

        assertEquals(addCount, recentCalls.size());

        int count = 0;
        for (PredictiveGammaCallModel ignored : recentCalls.snapshot(campaign)) {
            count++;
        }
        assertEquals(addCount, count);
    }

    /** when more than capacity calls are added, it should keep only last N */
    @Test
    public void shouldOverwriteWhenExceedingCapacity() {
        int cap = recentCalls.getCapacity();

        for (int i = 0; i < cap + 5; i++) {
            PredictiveGammaCallModel call = new PredictiveGammaCallModel();
            call.setTalkTime(i);
            recentCalls.add(call);
        }

        assertEquals(cap, recentCalls.size());

        // The first element in snapshot should be the (5th) call because older ones were overwritten
        PredictiveGammaCallModel first = recentCalls.snapshot(campaign).iterator().next();
        assertEquals(5, first.getTalkTime()); // talkTime == index
    }

    /**
     * Concurrency stress test:
     * Verifies that multiple threads can add and read calls concurrently
     * without violating ring buffer capacity or consistency.
     */
    @Test
    public void shouldBeThreadSafeUnderConcurrentAccess() throws Exception {
        int threads = 10;
        int capacity = recentCalls.getCapacity();
        int iterations = capacity * 2;
        // we add more than capacity to trigger overwrite logic

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                for (int i = 0; i < iterations; i++) {
                    PredictiveGammaCallModel call = new PredictiveGammaCallModel();
                    call.setTalkTime(i);
                    recentCalls.add(call); // concurrent write
                    recentCalls.snapshot(campaign); // concurrent read (protected by read lock)
                    recentCalls.size(); // concurrent read (protected by read lock)
                }
                latch.countDown(); // signal that this thread has finished
            });
        }

        latch.await(); // wait until all threads finish
        executor.shutdown();

        // At the end: size must equal capacity (buffer is full, old calls overwritten)
        assertEquals(recentCalls.getCapacity(), recentCalls.size());
    }
}
