package com.powerdialer.engine.scheduler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

import com.powerdialer.factory.RestOperationsFactory;
import com.powerdialer.model.GroupStats;
import com.powerdialer.model.QueueStats;
import com.powerdialer.util.LoggerDecorator;

@Component
public class StatsRestEngine extends StatsDataProvider {

  private static final String QUEUE_ENDPOINT = "queues/";
  private static final String GROUP_ENDPOINT = "groups/";

  @Value( "${dashboard.rest.url}" )
  private String requestUrl;

  @Value("${dashboard.rest.username}")
  private String username;

  @Value("${dashboard.rest.password}")
  private String password;

  @Autowired
  private RestOperationsFactory restFactory;

  @Autowired
  public StatsRestEngine(LoggerDecorator loggerDecorator) {
    super(loggerDecorator.getLogger(ExpressionEngine.class));
  }

  public QueueStats prepareQueueStats(String ids) {
    return requestStatsData(requestUrl + QUEUE_ENDPOINT + ids, QueueStats.class);
  }

  public GroupStats prepareGroupStats(String ids) {
    return requestStatsData(requestUrl + GROUP_ENDPOINT + ids, GroupStats.class);
  }

  protected ResponseEntity processRequest(String requestUrl) {
    RestOperations restClient = restFactory.build(10000, 10000);
    try {
      return restClient.exchange(requestUrl, HttpMethod.GET, getHeader(), String.class);
    } catch (HttpStatusCodeException e) {
      LOG.error("Dashboard returned " + e.getStatusCode() + " code. ");
      return null;
    } catch (IllegalArgumentException e){
      LOG.error("Invalid argument " + e.getMessage());
      return null;
    } catch (Exception e) {
      LOG.error("Error occurred while request stats from dashboard " + e);
      return null;
    }
  }

  private HttpEntity<Map<String, String>> getHeader() {
    String userCredentials = username + ":" + password;
    String basicAuth = "Basic " + new String(Base64.encode(userCredentials.getBytes()));
    Map<String, String> authHeader = new HashMap<>();
    authHeader.put("Authorization", basicAuth);
    HttpHeaders headers = new HttpHeaders();
    headers.setAll(authHeader);
    return new HttpEntity<>(headers);
  }

}
