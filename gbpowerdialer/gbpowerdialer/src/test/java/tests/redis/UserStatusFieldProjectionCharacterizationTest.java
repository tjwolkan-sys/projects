package tests.redis;

import com.powerdialer.dto.AgentStatusBucket;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.util.Constants;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.redisson.api.RFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Task 325654 karakterizasyon testi.
 *
 * <p>Uretim kaydinda (30.07.2026 pcap) app server'a gelen trafigin %93'u Redis
 * {@code HMGET userData<id> currentStatus statusSetDate staticData} yanitlarindan
 * olusuyordu; {@code staticData} alani tek basina ~36 KB'lik tam {@code User} JSON'u
 * (userPropertyXML, groupPropertyXML, groupResultCodes ...) tasiyor.</p>
 *
 * <p>Bu test iki agent-status aggregation parser'inin {@code staticData} alanina
 * HIC dokunmadigini kilitler. Yani alan istekten cikarildiginda sonuc birebir ayni
 * kalir; davranis degismez, yalniz tel uzerindeki maliyet duser. {@code staticData}
 * ayni zaten {@code UserHolder} icindeki hydrate {@code User} objesinde mevcuttur.</p>
 */
public class UserStatusFieldProjectionCharacterizationTest {

  private static final String CURRENT_STATUS = Constants.REDIS_USER_FIELD.currentStatus.toString();
  private static final String STATUS_SET_DATE = Constants.REDIS_USER_FIELD.statusSetDate.toString();
  private static final String STATIC_DATA = Constants.REDIS_USER_FIELD.staticData.toString();

  private final RedisUtil redisUtil = new RedisUtil();

  private static String statusJson(long id, String statusText) {
    return "{\"id\":" + id + ",\"statusText\":\"" + statusText + "\"}";
  }

  /** Uretimdeki ~36 KB'lik blob'un test icindeki temsili. */
  private static String bulkyStaticData() {
    StringBuilder sb = new StringBuilder("{\"id\":1001,\"username\":\"agent-1001\",\"userPropertyXML\":\"");
    for (int i = 0; i < 400; i++) {
      sb.append("<userSchedule><group><id>").append(10000 + i).append("</id></group></userSchedule>");
    }
    return sb.append("\"}").toString();
  }

  private static Map<String, String> withStaticData(String currentStatus) {
    Map<String, String> map = withoutStaticData(currentStatus);
    map.put(STATIC_DATA, bulkyStaticData());
    return map;
  }

  private static Map<String, String> withoutStaticData(String currentStatus) {
    Map<String, String> map = new HashMap<>();
    map.put(CURRENT_STATUS, currentStatus);
    map.put(STATUS_SET_DATE, "1785398691237");
    return map;
  }

  private AgentStatusBucket bucketOf(Map<String, String> userData) throws Exception {
    return Whitebox.invokeMethod(redisUtil, "parseAgentStatusBucket", userData);
  }

  @SuppressWarnings("unchecked")
  private int[] bulkMetricsOf(List<Map<String, String>> userDataList) throws Exception {
    List<RFuture<Map<String, String>>> futures = new ArrayList<>();
    for (Map<String, String> userData : userDataList) {
      RFuture<Map<String, String>> future = mock(RFuture.class);
      when(future.getNow()).thenReturn(userData);
      futures.add(future);
    }
    Object metrics = Whitebox.invokeMethod(redisUtil, "parseBulkAgentMetrics", futures);
    return new int[] {
        Whitebox.getInternalState(metrics, "incallCount"),
        Whitebox.getInternalState(metrics, "availCount"),
        Whitebox.getInternalState(metrics, "acwCount")
    };
  }

  @Test
  public void agentStatusBucketIsIdenticalWithAndWithoutStaticData() throws Exception {
    String[][] cases = {
        {statusJson(9999L, "Available"), "AVAILABLE"},
        {statusJson(9997L, "Incall"), "INCALL"},
        {statusJson(9995L, "Wrapup"), "ACW"},
        {statusJson(1234L, "ACW"), "ACW"},
        {statusJson(4242L, "Mola"), "OTHER"}
    };

    for (String[] testCase : cases) {
      String currentStatus = testCase[0];
      AgentStatusBucket expected = AgentStatusBucket.valueOf(testCase[1]);

      AgentStatusBucket withStatic = bucketOf(withStaticData(currentStatus));
      AgentStatusBucket withoutStatic = bucketOf(withoutStaticData(currentStatus));

      assertEquals("bucket contract degisti: " + currentStatus, expected, withStatic);
      assertEquals("staticData bucket sonucunu etkiliyor: " + currentStatus, withStatic, withoutStatic);
    }
  }

  @Test
  public void bulkAgentMetricsAreIdenticalWithAndWithoutStaticData() throws Exception {
    List<Map<String, String>> withStatic = new ArrayList<>();
    List<Map<String, String>> withoutStatic = new ArrayList<>();
    String[] statuses = {
        statusJson(9997L, "Incall"),
        statusJson(9997L, "Incall"),
        statusJson(9999L, "Available"),
        statusJson(1234L, "ACW"),
        statusJson(4242L, "Mola")
    };
    for (String status : statuses) {
      withStatic.add(withStaticData(status));
      withoutStatic.add(withoutStaticData(status));
    }

    int[] withStaticMetrics = bulkMetricsOf(withStatic);
    int[] withoutStaticMetrics = bulkMetricsOf(withoutStatic);

    assertEquals("incall sayisi degisti", 2, withStaticMetrics[0]);
    assertEquals("avail sayisi degisti", 1, withStaticMetrics[1]);
    assertEquals("acw sayisi degisti", 1, withStaticMetrics[2]);
    assertEquals("staticData incall sonucunu etkiliyor", withStaticMetrics[0], withoutStaticMetrics[0]);
    assertEquals("staticData avail sonucunu etkiliyor", withStaticMetrics[1], withoutStaticMetrics[1]);
    assertEquals("staticData acw sonucunu etkiliyor", withStaticMetrics[2], withoutStaticMetrics[2]);
  }

  @Test
  public void liveStatusFieldSetExcludesStaticData() {
    Set<String> liveFields = Whitebox.getInternalState(RedisUtil.class, "REDIS_LIVE_STATUS_FIELDS");

    assertTrue("currentStatus live status setinde olmali", liveFields.contains(CURRENT_STATUS));
    assertTrue("statusSetDate live status setinde olmali", liveFields.contains(STATUS_SET_DATE));
    assertFalse("staticData aggregation path'inde istenmemeli", liveFields.contains(STATIC_DATA));
    assertEquals("live status seti yalniz iki alan icermeli", 2, liveFields.size());
  }

  @Test
  public void remoteUserDataFieldSetStillCarriesStaticDataForFullUserHydration() {
    Set<String> remoteFields = Whitebox.getInternalState(RedisUtil.class, "REDIS_REMOTE_USER_DATA_FIELDS");

    assertTrue("getUserFromRedis tam User kurabilmek icin staticData'ya ihtiyac duyar",
        remoteFields.contains(STATIC_DATA));
    assertTrue(remoteFields.contains(CURRENT_STATUS));
    assertTrue(remoteFields.contains(STATUS_SET_DATE));
  }
}
