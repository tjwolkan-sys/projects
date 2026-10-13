package com.powerdialer.redis;

import com.powerdialer.util.Constants;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.redisson.api.RFuture;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RedisUtilBulkAgentMetricsTest {

  @Test
  public void parseBulkAgentMetrics_countsAvailableAgentWithCallQueuesAsAvailable() throws Exception {
    RedisUtil redisUtil = new RedisUtil();

    Object metrics = Whitebox.invokeMethod(redisUtil, "parseBulkAgentMetrics", Collections.singletonList(
            future(statusJson(Constants.SYSTEM_STATUS.AVAILABLE.getId(), "Available", true))
    ));

    assertEquals(1, (int) Whitebox.getInternalState(metrics, "availCount"));
    assertEquals(0, (int) Whitebox.getInternalState(metrics, "incallCount"));
    assertEquals(0, (int) Whitebox.getInternalState(metrics, "acwCount"));
  }

  @Test
  public void parseBulkAgentMetrics_keepsIncallAndAcwStatusCounts() throws Exception {
    RedisUtil redisUtil = new RedisUtil();

    Object metrics = Whitebox.invokeMethod(redisUtil, "parseBulkAgentMetrics", Arrays.asList(
            future(statusJson(Constants.SYSTEM_STATUS.INCALL.getId(), "Incall", false)),
            future(statusJson(Constants.SYSTEM_STATUS.WRAPUP.getId(), "Wrapup", false)),
            future(statusJson(Constants.SYSTEM_STATUS.AVAILABLE.getId(), "Available", false))
    ));

    assertEquals(1, (int) Whitebox.getInternalState(metrics, "incallCount"));
    assertEquals(1, (int) Whitebox.getInternalState(metrics, "acwCount"));
    assertEquals(1, (int) Whitebox.getInternalState(metrics, "availCount"));
  }

  private RFuture<Map<String, String>> future(String statusJson) {
    RFuture<Map<String, String>> future = mock(RFuture.class);
    when(future.getNow()).thenReturn(Collections.singletonMap(Constants.REDIS_USER_FIELD.currentStatus.toString(), statusJson));
    return future;
  }

  private String statusJson(Long id, String statusText, boolean withCallQueues) {
    String callQueues = withCallQueues ? ",\"callQueues\":[{\"name\":\"inbound\"}]" : "";
    return "{\"id\":" + id + ",\"statusText\":\"" + statusText + "\"" + callQueues + "}";
  }
}
