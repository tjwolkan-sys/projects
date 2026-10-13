package tests.asterisk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailEntry;
import com.powerdialer.engine.asterisk.CallerIdManager;
import com.powerdialer.engine.dialer.routing.RouteDetailsCursor;
import com.powerdialer.exceptions.NoAvailablePoolCallerIdException;
import com.powerdialer.exceptions.RouteDetailsUnavailableException;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.RoutingGroup;
import com.powerdialer.rds.ResourceDistributionServiceFacade;
import com.powerdialer.util.Constants;

/**
 * `obtainRouteDetailsCursor` sözleşmesi:
 * <ul>
 *   <li>Kalem 1a — cursor çağrının callType/attempt'ine uyan entry'lerle kurulur.</li>
 *   <li>Kalem 2 — QSS'e ULAŞILAMAMA (`RouteDetailsUnavailableException`) ile "grup/route yok"
 *       (boş liste → `NoAvailablePoolCallerIdException`) ayrı sinyaller olarak taşınır; ilki
 *       geçicidir ve çağrının retry'a bırakılmasını gerektirir.</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class CallerIdManagerRouteDetailsTest {

  @Mock
  private ResourceDistributionServiceFacade facade;

  private CallerIdManager callerIdManager;

  @Before
  public void setUp() {
    callerIdManager = new CallerIdManager();
    ReflectionTestUtils.setField(callerIdManager, "resourceDistributionServiceFacade", facade);
  }

  @Test
  public void shouldBuildCursorFromEntriesMatchingCallType() throws Exception {
    when(facade.getRouteDetails(anyLong())).thenReturn(Arrays.asList(
        entry("0501", "Manual", "1"),
        entry("0502", "Retry", "1"),
        entry("0503", "Dialer First", "1")));

    RouteDetailsCursor cursor = callerIdManager.obtainRouteDetailsCursor(call(Constants.CALL_TYPES.RETRY, 1));

    assertEquals("yalnız çağrı tipine tanımlı route kalmalı", 1, cursor.size());
    assertEquals("0502", cursor.current().getPhoneNumber());
  }

  @Test
  public void shouldKeepFullListWhenFilterWouldEmptyIt() throws Exception {
    when(facade.getRouteDetails(anyLong())).thenReturn(Arrays.asList(
        entry("0501", "Manual", "1"),
        entry("0502", "Manual", "1")));

    RouteDetailsCursor cursor = callerIdManager.obtainRouteDetailsCursor(call(Constants.CALL_TYPES.RETRY, 1));

    assertEquals("fail-open: çağrı caller ID'siz bırakılmaz", 2, cursor.size());
  }

  @Test(expected = NoAvailablePoolCallerIdException.class)
  public void shouldThrowNoAvailableCallerIdWhenGroupHasNoRoutes() throws Exception {
    when(facade.getRouteDetails(anyLong())).thenReturn(Collections.<RouteDetailEntry>emptyList());

    callerIdManager.obtainRouteDetailsCursor(call(Constants.CALL_TYPES.FRESH, 0));
  }

  /**
   * Geçici erişim sorunu kalıcı konfigürasyon eksikliğiyle karıştırılmamalı: facade'in attığı
   * exception yukarı taşınır ve CallOriginator retry'a izin veren iptal yolunu uygular.
   */
  @Test(expected = RouteDetailsUnavailableException.class)
  public void shouldPropagateUnavailableExceptionInsteadOfBurningTheCall() throws Exception {
    when(facade.getRouteDetails(anyLong()))
        .thenThrow(new RouteDetailsUnavailableException("simulated qss outage"));

    callerIdManager.obtainRouteDetailsCursor(call(Constants.CALL_TYPES.FRESH, 0));
  }

  @Test
  public void shouldUseCallAttemptWhenFilteringByAttempts() throws Exception {
    when(facade.getRouteDetails(anyLong())).thenReturn(Arrays.asList(
        entry("0501", "Retry", "0,1"),
        entry("0502", "Retry", "7,8")));

    RouteDetailsCursor cursor = callerIdManager.obtainRouteDetailsCursor(call(Constants.CALL_TYPES.RETRY, 7));

    assertTrue("attempt eşleşen route seçilmeli", cursor.size() == 1);
    assertEquals("0502", cursor.current().getPhoneNumber());
  }

  private Call call(Constants.CALL_TYPES callType, int attempts) {
    RoutingGroup pool = new RoutingGroup();
    pool.setId(55L);

    Campaign campaign = new Campaign();
    campaign.setId(76L);
    campaign.setCallerIdBehaviour(Constants.CALLER_ID_BEHAVIOUR.POOL);
    campaign.setFctRoutingEnabled(true);
    campaign.setCallerIdPool(pool);
    campaign.setColResultAttempts("attempts");

    Call call = new Call();
    call.setId("dl-callerid-route-details");
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setCallType(callType);
    call.setRawData(new JSONObject().put("attempts", attempts));
    call.setAttempts(attempts);
    return call;
  }

  private RouteDetailEntry entry(String phoneNumber, String type, String attempts) {
    return RouteDetailEntry.newBuilder()
        .setPhoneNumber(phoneNumber)
        .setType(type)
        .setAttempts(attempts)
        .setCapacity(4)
        .build();
  }
}
