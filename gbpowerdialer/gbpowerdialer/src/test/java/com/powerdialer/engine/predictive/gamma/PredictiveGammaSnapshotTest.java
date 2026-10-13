package com.powerdialer.engine.predictive.gamma;

import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallModel;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaSnapshot;
import org.junit.Test;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.junit.Assert.*;

public class PredictiveGammaSnapshotTest {

//    private static final String SNAPSHOT_ID = "test_snapshot";
//
//    /** Empty snapshot should have no elements */
//    @Test
//    public void shouldReturnEmptyIteratorWhenSizeIsZero() {
//        PredictiveGammaSnapshot snapshot = new PredictiveGammaSnapshot(SNAPSHOT_ID, new PredictiveGammaCallModel[10], 0, 0, 10, 0, 0);
//
//        Iterator<PredictiveGammaCallModel> it = snapshot.iterator();
//        assertFalse(it.hasNext());
//    }
//
//    /** When calling next() on an empty snapshot, the iterator should throw a NoSuchElementException. */
//    @Test(expected = NoSuchElementException.class)
//    public void shouldThrowExceptionWhenNextCalledWithoutElements() {
//        PredictiveGammaSnapshot snapshot = new PredictiveGammaSnapshot(SNAPSHOT_ID, new PredictiveGammaCallModel[3], 0, 0, 3, 0, 0);
//        Iterator<PredictiveGammaCallModel> it = snapshot.iterator();
//        it.next();
//    }
//
//    /** Snapshot should return items in correct order without wrap-around */
//    @Test
//    public void shouldIterateInCorrectOrder() {
//        int capacity = 5;
//        PredictiveGammaCallModel[] buf = new PredictiveGammaCallModel[capacity];
//
//        for (int i = 0; i < capacity; i++) {
//            PredictiveGammaCallModel call = new PredictiveGammaCallModel();
//            call.setTalkTime(i);
//            buf[i] = call;
//        }
//
//        // head=0 (next write position), size=capacity
//        PredictiveGammaSnapshot snapshot = new PredictiveGammaSnapshot(SNAPSHOT_ID, buf, 0, capacity, capacity, capacity, 0);
//
//        int expected = 0;
//        for (PredictiveGammaCallModel m : snapshot) {
//            assertEquals(expected, m.getTalkTime());
//            expected++;
//        }
//        assertEquals(capacity, expected);
//    }
//
//    /** Snapshot should return items in correct order after wrap-around */
//    @Test
//    public void shouldIterateCorrectlyWhenBufferWrapped() {
//        int capacity = 5;
//        PredictiveGammaCallModel[] buf = new PredictiveGammaCallModel[capacity];
//
//        // Simulate writing 7 calls (indexes 0..6)
//        // Oldest two (0,1) are overwritten, buffer contains 2..6
//        for (int i = 0; i < 7; i++) {
//            PredictiveGammaCallModel call = new PredictiveGammaCallModel();
//            call.setTalkTime(i);
//            buf[i % capacity] = call;
//        }
//
//        // head=2 (next write), size=capacity, totalCallCount=7
//        PredictiveGammaSnapshot snapshot = new PredictiveGammaSnapshot(
//                SNAPSHOT_ID, buf, 2, capacity, capacity, 7, 0
//        );
//
//        int expected = 2;
//        for (PredictiveGammaCallModel m : snapshot) {
//            assertEquals(expected, m.getTalkTime());
//            expected++;
//        }
//        assertEquals(7, expected); // checked talkTimes 2..6
//    }
//
//    /** Multiple calls to iterator() should create independent iterators. */
//    @Test
//    public void iteratorShouldBeIndependentOnMultipleCalls() {
//        int capacity = 3;
//        PredictiveGammaCallModel[] buf = new PredictiveGammaCallModel[capacity];
//        for (int i = 0; i < capacity; i++) {
//            buf[i] = new PredictiveGammaCallModel();
//            buf[i].setTalkTime(i);
//        }
//        PredictiveGammaSnapshot snapshot = new PredictiveGammaSnapshot(
//                SNAPSHOT_ID, buf, 0, capacity, capacity, capacity, 0
//        );
//
//        Iterator<PredictiveGammaCallModel> it1 = snapshot.iterator();
//        Iterator<PredictiveGammaCallModel> it2 = snapshot.iterator();
//
//        assertTrue(it1.hasNext());
//        assertTrue(it2.hasNext());
//        assertEquals(it1.next().getTalkTime(), it2.next().getTalkTime());
//    }
//
//    /** Getters expose all fields correctly. */
//    @Test
//    public void shouldExposeFieldsViaGeneratedGetters() {
//        PredictiveGammaCallModel[] buf = new PredictiveGammaCallModel[4];
//        PredictiveGammaSnapshot snapshot = new PredictiveGammaSnapshot(
//                SNAPSHOT_ID, buf, 1, 2, 4, 10, 3
//        );
//
//        assertSame(buf, snapshot.getBuf());
//        assertEquals(1, snapshot.getHead());
//        assertEquals(2, snapshot.getSize());
//        assertEquals(4, snapshot.getCapacity());
//        assertEquals(10, snapshot.getTotalCallCount());
//        assertEquals(3, snapshot.getTotalAbandonCallCount());
//    }
}