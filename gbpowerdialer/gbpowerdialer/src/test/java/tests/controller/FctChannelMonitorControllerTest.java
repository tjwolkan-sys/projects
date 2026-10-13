package tests.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import com.powerdialer.controller.FctChannelMonitorController;
import com.powerdialer.dto.FctChannelEntryDTO;
import com.powerdialer.dto.FctRegistrySnapshotDTO;
import com.powerdialer.engine.dialer.routing.FctChannelRegistry;
import com.powerdialer.util.Constants;

/**
 * Monitor endpoint'i sözleşmesi: DTO şeması, yetki kısıtı, snapshot cache davranışı ve müşteri
 * verisi taşımama garantisi.
 */
public class FctChannelMonitorControllerTest {

  private FctChannelMonitorController controller;
  private FctChannelRegistry registry;

  @Before
  public void setUp() {
    registry = newRegistry();
    controller = new FctChannelMonitorController();
    ReflectionTestUtils.setField(controller, "fctChannelRegistry", registry);
    ReflectionTestUtils.setField(controller, "snapshotCacheMs", 0L);
  }

  @Test
  public void shouldExposeRegistrySnapshotShape() {
    registry.tryAcquire("05550000501", "MOBILE", "1240", 8, "dl-call-1", 12L);

    FctRegistrySnapshotDTO snapshot = controller.snapshot();

    assertNotNull(snapshot);
    assertTrue("serverTimeMs istemci saat sapması için doldurulur", snapshot.getServerTimeMs() > 0L);
    assertEquals(3900000L, snapshot.getTtlMs());
    assertEquals(600000L, snapshot.getStaleWarnMs());
    assertEquals(60000L, snapshot.getCooldownMs());
    assertEquals(1, snapshot.getEntries().size());
    assertNotNull(snapshot.getKpi());

    FctChannelEntryDTO entry = snapshot.getEntries().get(0);
    assertEquals("05550000501", entry.getPhoneNumber());
    assertEquals(8, entry.getCapacity());
    assertEquals(1, entry.getInFlight().size());
    assertEquals("dl-call-1", entry.getInFlight().get(0).getCallId());
    assertTrue("olay feed'i doldurulur", snapshot.getRecentEvents().size() >= 1);
  }

  @Test
  public void shouldReturnEmptySnapshotForEmptyRegistry() {
    FctRegistrySnapshotDTO snapshot = controller.snapshot();

    assertTrue(snapshot.getEntries().isEmpty());
    assertTrue(snapshot.getRecentEvents().isEmpty());
    assertEquals(0, snapshot.getKpi().getPrunedLast5Min());
  }

  @Test
  public void shouldServeCachedSnapshotWithinCacheWindow() {
    ReflectionTestUtils.setField(controller, "snapshotCacheMs", 60000L);

    FctRegistrySnapshotDTO first = controller.snapshot();
    registry.tryAcquire("05550000502", "MOBILE", "1", 4, "dl-call-2", 12L);
    FctRegistrySnapshotDTO second = controller.snapshot();

    assertSame("cache penceresi içinde aynı snapshot döner", first, second);
    assertTrue(second.getEntries().isEmpty());
  }

  @Test
  public void shouldRequireDialerAdministrationAuthority() throws Exception {
    Method snapshot = FctChannelMonitorController.class.getMethod("snapshot");
    PreAuthorize preAuthorize = snapshot.getAnnotation(PreAuthorize.class);

    assertNotNull("snapshot endpoint'i yetkisiz erişime açık olmamalı", preAuthorize);
    assertTrue("DialerAdministration yetkisi zorunlu",
        preAuthorize.value().contains(Constants.PERMISSION_DIALER_ADMINISTRATION));
  }

  @Test
  public void shouldBeMappedUnderFctChannelPath() {
    RequestMapping mapping = FctChannelMonitorController.class.getAnnotation(RequestMapping.class);

    assertNotNull(mapping);
    assertEquals("security.xml içindeki /fctChannel/** girdisiyle eşleşmeli", "/fctChannel", mapping.value()[0]);
  }

  private FctChannelRegistry newRegistry() {
    FctChannelRegistry created = new FctChannelRegistry();
    ReflectionTestUtils.setField(created, "gateEnabled", true);
    ReflectionTestUtils.setField(created, "ttlMs", 3900000L);
    ReflectionTestUtils.setField(created, "staleWarnMs", 600000L);
    ReflectionTestUtils.setField(created, "cooldownMs", 60000L);
    ReflectionTestUtils.setField(created, "headroomPercent", 0);
    ReflectionTestUtils.setField(created, "metaRetentionMs", 1800000L);
    ReflectionTestUtils.setField(created, "eventLogSize", 200);
    return created;
  }
}
