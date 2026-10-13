package tests.acd;

import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.UserHolder;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.boost.DynamicBoostManager;
import com.powerdialer.model.Group;
import com.powerdialer.model.Status;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.util.LoggerDecorator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T3 — {@code getAvailableUserToCallsCount} icindeki idle-kuyrugu kaynagina uygulanan
 * musaitlik kapisini kilitler.
 *
 * <p><b>Neden gerekli:</b> GAMMA/ALPHA/PROGRESSIVE_RELEASED'de agent originate aninda idle
 * kuyrugundan CIKARILMADIGI icin (FIX-GLB90060295) cagriya baglanmis (Incall) agent kuyrukta
 * kalabiliyor. Kapi olmadan bu agent aday havuzuna giriyor ve {@code put(user, 0)} isaretiyle
 * minCallsNumber filtresinde ONE CIKIYORDU.
 *
 * <p><b>Regresyon kapisi:</b> gercekten musait agent'in davranisi DEGISMEMELI.
 */
@RunWith(MockitoJUnitRunner.class)
public class ACDEngineIdleQueueStatusGateTest {

    @InjectMocks
    private ACDEngine acdEngine;

    @Mock private CallUpToDateKeeper callUpToDateKeeper;
    @Mock private UserHolder userHolder;
    @Mock private RedisUtil redisUtil;
    @Mock private DynamicBoostManager dynamicBoostManager;
    @Mock(name = "logger") private LoggerDecorator loggerDecorator;

    private Group group;

    @Before
    public void setUp() {
        group = new Group();
        group.setId(7L);

        lenient().when(callUpToDateKeeper.getCallsByUserGroup(group))
                .thenReturn(Collections.<User, java.util.List<com.powerdialer.model.Call>>emptyMap());

        // init() LoggerDecorator uzerinden LOG kurar; deep-stub mock NPE'yi onler.
        LoggerDecorator deepLogger = mock(LoggerDecorator.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        Whitebox.setInternalState(acdEngine, "loggerDecorator", deepLogger);
        acdEngine.init();
    }

    /** Musait agent aday havuzuna GIRMELI — eski davranisla bit-esit. */
    @Test
    public void musaitAgent_havuzaGirer() {
        User musait = user(1L, true, false);
        seedIdleQueue(musait);

        Map<User, Integer> havuz = acdEngine.getAvailableUserToCallsCount(group);

        assertEquals("Musait agent havuzda olmali", 1, havuz.size());
        assertTrue(havuz.containsKey(musait));
        assertEquals("Idle kaynagi 0 cagri ile isaretler", Integer.valueOf(0), havuz.get(musait));
    }

    /** T3 ASIL KAPI: Incall agent kuyrukta kalmis olsa bile havuza GIRMEMELI. */
    @Test
    public void incallAgent_kuyruktaKalsaBileHavuzaGirmez() {
        User incall = user(2L, false, false);   // isAvailableForDialerCalls = false
        seedIdleQueue(incall);

        Map<User, Integer> havuz = acdEngine.getAvailableUserToCallsCount(group);

        assertTrue("Mesgul agent aday havuzuna girmemeli", havuz.isEmpty());
        assertFalse(havuz.containsKey(incall));
    }

    /** Dial suren agent da havuza girmemeli (satir ~151'deki kapiyla ayni kural). */
    @Test
    public void dialSurenAgent_havuzaGirmez() {
        User dialing = user(3L, true, true);    // musait ama isDialInProcess = true
        seedIdleQueue(dialing);

        Map<User, Integer> havuz = acdEngine.getAvailableUserToCallsCount(group);

        assertTrue("Dial suren agent aday havuzuna girmemeli", havuz.isEmpty());
    }

    /** Statusu null olan agent NPE atmadan elenmeli. */
    @Test
    public void statusuNullAgent_npeAtmadanElenir() {
        User statusuz = new User();
        statusuz.setId(4L);
        statusuz.setGroup(group);
        statusuz.setStatus(null);
        seedIdleQueue(statusuz);

        Map<User, Integer> havuz = acdEngine.getAvailableUserToCallsCount(group);

        assertTrue("Statusu null agent elenmeli", havuz.isEmpty());
    }

    /** Karisik kuyruk: yalniz musait olan(lar) gecmeli. */
    @Test
    public void karisikKuyruk_yalnizMusaitlerGecer() {
        User musait1 = user(10L, true, false);
        User incall  = user(11L, false, false);
        User musait2 = user(12L, true, false);
        User dialing = user(13L, true, true);
        seedIdleQueue(musait1, incall, musait2, dialing);

        Map<User, Integer> havuz = acdEngine.getAvailableUserToCallsCount(group);

        assertEquals("Yalniz 2 musait agent gecmeli", 2, havuz.size());
        assertTrue(havuz.containsKey(musait1));
        assertTrue(havuz.containsKey(musait2));
        assertFalse("Incall elenmeli", havuz.containsKey(incall));
        assertFalse("Dial suren elenmeli", havuz.containsKey(dialing));
    }

    /** Bos kuyruk NPE atmamali. */
    @Test
    public void bosKuyruk_sorunsuzCalisir() {
        Map<User, Integer> havuz = acdEngine.getAvailableUserToCallsCount(group);
        assertTrue(havuz.isEmpty());
    }

    // ==================================================================== yardimcilar

    private User user(long id, boolean availableForDialerCalls, boolean dialInProcess) {
        User u = new User();
        u.setId(id);
        u.setGroup(group);
        Status s = new Status();
        s.setAvailableForDialerCalls(availableForDialerCalls);
        u.setStatus(s);
        u.setDialInProcess(dialInProcess);
        return u;
    }

    private void seedIdleQueue(User... users) {
        Queue<User> q = new ConcurrentLinkedQueue<>();
        Collections.addAll(q, users);
        Map<Group, Queue<User>> idle = new HashMap<>();
        idle.put(group, q);
        Whitebox.setInternalState(acdEngine, "idleUsers", idle);
    }
}
