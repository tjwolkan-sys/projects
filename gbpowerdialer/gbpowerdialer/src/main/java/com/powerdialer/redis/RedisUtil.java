package com.powerdialer.redis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import java.sql.Date;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.redisson.api.ClusterNode;
import org.redisson.api.Node;
import org.redisson.api.NodesGroup;
import org.redisson.api.RBucket;
import org.redisson.api.RFuture;
import org.redisson.api.RMap;
import org.redisson.api.RBatch;
import org.redisson.api.RFuture;
import org.redisson.api.RMapAsync;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.RedisClientEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.powerdialer.model.Status;
import com.powerdialer.model.User;
import com.powerdialer.redis.message.RedisMessage;
import com.powerdialer.redis.message.UserPauseMessage;
import com.powerdialer.util.ApplicationUtil;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.dto.AgentMetricsDTO;
import com.powerdialer.dto.AgentStatusBucket;

/**
 * Util class to create connection to Redis and add/remove objects
 */
@Component
@Lazy(false)
public class RedisUtil {
  @Autowired
  private ObjectMapper objectMapper;
  private static final Log LOG = LogFactory.getLog(RedisUtil.class);
  private static final int ENTRY_EXPIRE_PERIOD_SEC = 7200;
  private static final String REDIS_USER_MAP_NAME_PREFIX = "userData";
  private static final int KEYS_SCAN_COUNT = 1000;
  private static final Set<String> REDIS_REMOTE_USER_DATA_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
      Constants.REDIS_USER_FIELD.staticData.toString(),
      Constants.REDIS_USER_FIELD.currentStatus.toString(),
      Constants.REDIS_USER_FIELD.statusSetDate.toString())));

  /**
   * Task 325654: Agent status aggregation path'leri ({@link #getUsersStatusBulk} ve
   * {@link #getUsersCurrentStatusMap}) yalniz {@code currentStatus} alanini okur;
   * bkz. {@code parseBulkAgentMetrics} ve {@code parseAgentStatusBucket}.
   *
   * <p>{@code staticData} alani tek basina ~36 KB'lik tam {@code User} JSON'u
   * (userPropertyXML, groupPropertyXML, groupResultCodes ...) tasidigi icin bu
   * path'lerde istenmez. Ayni veri zaten {@link com.powerdialer.engine.UserHolder}
   * icindeki hydrate {@code User} objesinde mevcuttur ve login sirasinda
   * {@link #getUserFromRedis} ile bir kez deserialize edilir.</p>
   *
   * <p>{@link #REDIS_REMOTE_USER_DATA_FIELDS} tam User hydration icin oldugu gibi korunur.</p>
   */
  private static final Set<String> REDIS_LIVE_STATUS_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
      Constants.REDIS_USER_FIELD.currentStatus.toString(),
      Constants.REDIS_USER_FIELD.statusSetDate.toString())));

  private ScheduledExecutorService pingScheduledExecutorService;

  @Autowired
  private LoggerDecorator loggerDecorator;

  @Autowired
  private RedissonClient redissonClient;

  @Autowired
  private ApplicationUtil applicationUtil;

  @PostConstruct
  public void init() {
    ThreadFactory threadFactory = new BasicThreadFactory.Builder().namingPattern("ping-subscription-%d").daemon(true).priority(Thread.MAX_PRIORITY).build();
    pingScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
  }

  /**
   * Start receiving messages from channel
   * @param channel
   * @param listener
   */
  public void subscribe(String channel, MessageListener<String> listener) {
    if (channel != null) {
      String channelName = channelName(channel);
      RTopic topic = redissonClient.getTopic(channelName);
      topic.addListener(SubscriptionStatusListener.getInstance());
      topic.addListener(String.class, listener);
    }
  }

  private String channelName(String name) {
    if (name.startsWith("channel"))
      return name;
    return "channel" + name;
  }

  /**
   * Save string object
   * @param key
   * @param value
   */
  @Async
  public void saveObject(String key, String value) {
    if (!StringUtils.isEmpty(key)) {
      RBucket<String> bucket = redissonClient.getBucket(key);
      if (bucket.trySet(value)) {
        bucket.expire(ENTRY_EXPIRE_PERIOD_SEC, TimeUnit.SECONDS);
      }
    }
  }

  /**
   * Get value as JSONObject
   * @param key
   * @return JSONObject
   */
  public JSONObject getJSONObject(String key) {
    try {
      String data = getObject(key);
      if (data == null)
        return null;
      return new JSONObject(data);
    } catch (JSONException e) {
      LOG.error("", e);
    }

    return null;
  }

  /**
   * Get value as String
   * @param key
   * @return String
   */
  public String getObject(String key) {
    RBucket<String> bucket = redissonClient.getBucket(key);
    return bucket.get();
  }

  /**
   * Remove object
   * @param key
   */
  public boolean remove(String key) {
    return redissonClient.getBucket(key).delete();
  }

  /**
   * Publish Redis channel message for subscribers
   * @param channel
   * @param msg
   */
  public void publishMessage(String channel, RedisMessage msg) {
    try {
      publish(channel, objectMapper.writeValueAsString(msg));
    } catch (JsonProcessingException e) {
      LOG.error("", e);
    }
  }

  void publish(String channel, String message) {
    redissonClient.getTopic(channel).publish(message);
  }

  /**
   * Actions to take when resources holding this instance is released
   */
  @PreDestroy
  private void cleanUp() {
    LOG.info("Cleaning-up RedisUtil");
    pingScheduledExecutorService.shutdownNow();
  }

  public LoggerDecorator getLoggerDecorator() {
    return loggerDecorator;
  }

  /**
   * Task 325654: Bu metot eskiden ham {@code KEYS} komutunu cluster'daki tum master
   * node'lara fan-out ediyordu. {@code KEYS} O(N) calisir ve Redis'in tek thread'li
   * event loop'unu bloke eder; uretimde 20 saniyelik {@code UserSynchronizer} turu
   * basina 9 node'a birer blocking komut gidiyordu.
   *
   * <p>Redisson'un {@code getKeysByPattern} implementasyonu cursor tabanli {@code SCAN}
   * kullanir, cluster fan-out'unu kendisi yonetir ve server'i bloke etmez. Donen key
   * kumesi ayni kalir; {@code SCAN} ayni key'i tekrar dondurebilir fakat cagiran taraf
   * ({@link #getLiveUserIds}) sonucu {@code Set} olarak topladigi icin bu etkisizdir.</p>
   *
   * <p>Task 333467: Tek parametreli overload {@code SCAN COUNT 10} kullanir; buyuk keyspace'te
   * round-trip sayisi senkron turunu ortak scheduler periyodunun uzerine cikardigi icin sayfa
   * boyutu {@code KEYS_SCAN_COUNT} verilir.</p>
   */
  public Iterable<String> keys(String pattern) {
    return redissonClient.getKeys().getKeysByPattern(pattern, KEYS_SCAN_COUNT);
  }

  /**
   * Send same RedisCommand to each node of cluster and get responses
   * @return list of responses from each node (e. g. for KEYS command result will
   *         be list of lists)
   */
  private <T> List<T> sendCommandToAllNodes(RedisCommand<?> command, Object... params) {
    List<RFuture<T>> commandFutures = new LinkedList<>();
    Set<RedisConnection> openedConnections = new HashSet<>();
    try {
      for (ClusterNode clusterNode : redissonClient.getClusterNodesGroup().getNodes()) {
        if (clusterNode instanceof RedisClientEntry) {
          RedisClient clusterNodeClient = ((RedisClientEntry) clusterNode).getClient();
          RedisConnection connect = clusterNodeClient.connect();
          openedConnections.add(connect);
          RFuture<T> commandFuture = connect.async(redissonClient.getConfig().getCodec(), command, params);
          commandFutures.add(commandFuture);
        } else {
          throw new IllegalStateException("Can't send command to node: " + clusterNode.getAddr() + " not supported ClusterNode type: " + clusterNode.getClass());
        }
      }
      List<T> result = new ArrayList<>();
      for (RFuture<T> commandFuture : commandFutures) {
        T joinResult = commandFuture.join();
        result.add(joinResult);
      }
      return result;
    } finally {
      openedConnections.forEach(RedisConnection::closeAsync);
    }
  }

  private <T> T sendCommandToSingleNode(RedisCommand<?> command, Object... params) {
    NodesGroup<Node> nodes = redissonClient.getNodesGroup();
    Node node  = nodes.getNodes().stream().findAny().orElseThrow(() -> new IllegalStateException("No Redis nodes found"));
    if (!(node instanceof RedisClientEntry)) {
      throw new IllegalStateException("Can't send command to node: " + node.getAddr() + " not supported ClusterNode type: " + node.getClass());
    }
    RedisClient redisClient = ((RedisClientEntry) node).getClient();
    RedisConnection connect = redisClient.connect();
    try {
      RFuture<T> commandFuture = connect.async(redissonClient.getConfig().getCodec(), command, params);
      return commandFuture.join();
    } finally {
      connect.closeAsync();
    }
  }

  public Map<String, String> getMap(String name, Set<String> fields) {
    RMap<String, String> rMap = redissonClient.getMap(name);
    return rMap.getAll(fields);
  }

  public User getUserFromRedis(Long id) {
    try {
      Map<String, String> userData = getMap(REDIS_USER_MAP_NAME_PREFIX + id, REDIS_REMOTE_USER_DATA_FIELDS);
      String fieldData = userData.get(Constants.REDIS_USER_FIELD.staticData.toString());
      if (StringUtils.isEmpty(fieldData)) {
        LOG.warn("Could not get user from Redis map: no static data");
        return null;
      }
      User user = objectMapper.readValue(fieldData, User.class);
      fieldData = userData.get(Constants.REDIS_USER_FIELD.currentStatus.toString());
      if (!StringUtils.isEmpty(fieldData))
        user.setStatus(objectMapper.readValue(fieldData, Status.class));
      fieldData = userData.get(Constants.REDIS_USER_FIELD.statusSetDate.toString());
      if (!StringUtils.isEmpty(fieldData))
        user.setStatusSetDate(new Date(Long.parseLong(fieldData)));
      return user;
    } catch (Exception e) {
      LOG.error("Could not convert Redis map to user", e);
      return null;
    }
  }

  public Set<Long> getLiveUserIds() {
    Iterable<String> keys = keys(REDIS_USER_MAP_NAME_PREFIX + "*");
    return StreamSupport.stream(keys.spliterator(), false)
               .map(name -> name.replace(REDIS_USER_MAP_NAME_PREFIX, ""))
               .filter(NumberUtils::isNumber)
               .map(Long::parseLong)
               .collect(Collectors.toSet());
  }

  public AgentMetricsDTO getUsersStatusBulk(Set<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return new AgentMetricsDTO(0, 0, 0, 0);
    }
    try {
      RBatch batch = redissonClient.createBatch();
      List<RFuture<Map<String, String>>> futures = new ArrayList<>();
      
      for (Long id : userIds) {
        RMapAsync<String, String> mapAsync = batch.getMap(REDIS_USER_MAP_NAME_PREFIX + id);
        futures.add(mapAsync.getAllAsync(REDIS_LIVE_STATUS_FIELDS));
      }
      
      batch.execute();
      
      BulkMetrics metrics = parseBulkAgentMetrics(futures);

      return new AgentMetricsDTO(userIds.size(), metrics.incallCount, metrics.availCount, metrics.acwCount);
               
    } catch (Exception e) {
      LOG.error("[GAMMA-SYNC] Error fetching bulk user statuses", e);
      return new AgentMetricsDTO(userIds.size(), 0, 0, 0);
    }
  }

  /**
   * Webphone üzerinden girilen grup bazlı outbound oranlarını Redis'ten tek seferde okur.
   * Oranı olmayan gruplar provider tarafında 1.0, yani yüzde 100 outbound kabul edilir.
   */
  public Map<Long, Double> getAllGroupBlendRatios() {
    Map<Long, Double> ratioMap = new java.util.HashMap<>();
    try {
      RMap<String, String> rMap = redissonClient.getMap("dialer:group:blend_ratios");
      Map<String, String> rawMap = rMap.readAllMap();

      if (rawMap != null) {
        for (Map.Entry<String, String> entry : rawMap.entrySet()) {
          try {
            Long groupId = Long.parseLong(entry.getKey());
            Double ratio = Double.parseDouble(entry.getValue());

            if (ratio >= 0.0 && ratio <= 1.0) {
              ratioMap.put(groupId, ratio);
            } else {
              LOG.warn("[GAMMA-BLEND] Invalid ratio detected, using 1.0. GroupId: " + groupId + " Ratio: " + ratio);
              ratioMap.put(groupId, 1.0);
            }
          } catch (NumberFormatException e) {
            LOG.warn("[GAMMA-BLEND] Blend ratio could not be parsed. Key: " + entry.getKey() + " Value: " + entry.getValue());
          }
        }
      }
    } catch (Exception e) {
      LOG.error("[GAMMA-BLEND] Critical error while reading blend ratios from Redis.", e);
    }
    return ratioMap;
  }

  public Map<Long, AgentStatusBucket> getUsersCurrentStatusMap(Set<Long> userIds) {
    Map<Long, AgentStatusBucket> statusMap = new java.util.HashMap<>();
    if (userIds == null || userIds.isEmpty()) {
      return statusMap;
    }

    try {
      RBatch batch = redissonClient.createBatch();
      List<Long> orderedUserIds = new ArrayList<>(userIds);
      List<RFuture<Map<String, String>>> futures = new ArrayList<>();

      for (Long id : orderedUserIds) {
        RMapAsync<String, String> mapAsync = batch.getMap(REDIS_USER_MAP_NAME_PREFIX + id);
        futures.add(mapAsync.getAllAsync(REDIS_LIVE_STATUS_FIELDS));
      }

      batch.execute();

      for (int i = 0; i < orderedUserIds.size(); i++) {
        statusMap.put(orderedUserIds.get(i), parseAgentStatusBucket(futures.get(i).getNow()));
      }
    } catch (Exception e) {
      LOG.error("[GAMMA-SYNC] Error fetching user status buckets", e);
    }

    return statusMap;
  }

  private AgentStatusBucket parseAgentStatusBucket(Map<String, String> userData) {
    if (userData == null || userData.isEmpty()) return AgentStatusBucket.OTHER;

    String statusJson = userData.get(Constants.REDIS_USER_FIELD.currentStatus.toString());
    if (StringUtils.isEmpty(statusJson)) return AgentStatusBucket.OTHER;

    try {
      JSONObject currentStatusObj = new JSONObject(statusJson);
      String statusText = currentStatusObj.optString("statusText", "");

      if ("ACW".equals(statusText) || "Wrapup".equals(statusText)) {
        return AgentStatusBucket.ACW;
      }

      if (currentStatusObj.has("id")) {
        long statusId = currentStatusObj.getLong("id");
        if (Constants.SYSTEM_STATUS.INCALL.getId().equals(statusId)) {
          return AgentStatusBucket.INCALL;
        }
        if (Constants.SYSTEM_STATUS.AVAILABLE.getId().equals(statusId)) {
          return AgentStatusBucket.AVAILABLE;
        }
        if (Constants.SYSTEM_STATUS.WRAPUP.getId().equals(statusId)) {
          return AgentStatusBucket.ACW;
        }
      }
    } catch (JSONException e) {
      LOG.warn("Error parsing agent status JSON", e);
    }

    return AgentStatusBucket.OTHER;
  }

  private BulkMetrics parseBulkAgentMetrics(List<RFuture<Map<String, String>>> futures) {
    BulkMetrics metrics = new BulkMetrics();
    for (RFuture<Map<String, String>> future : futures) {
      Map<String, String> userData = future.getNow();
      if (userData == null || userData.isEmpty()) continue;
      
      String statusJson = userData.get(Constants.REDIS_USER_FIELD.currentStatus.toString());
      if (StringUtils.isEmpty(statusJson)) continue;
      
      try {
        JSONObject currentStatusObj = new JSONObject(statusJson);
        String statusText = currentStatusObj.optString("statusText", "");
        
        if ("ACW".equals(statusText) || "Wrapup".equals(statusText) ) {
          metrics.acwCount++;
          continue;
        }
        
        if (currentStatusObj.has("id")) {
          long statusId = currentStatusObj.getLong("id");
          
          if (statusId == 9997L) { // INCALL
            metrics.incallCount++;
          } else if (statusId == 9999L) { // AVAILABLE
            metrics.availCount++;
          }
        }
      } catch (JSONException e) {
        LOG.warn("Error parsing agent status JSON", e);
      }
    }
    return metrics;
  }

  private static class BulkMetrics {
    int incallCount = 0;
    int availCount = 0;
    int acwCount = 0;
  }

  public void publishUserPauseMessage(UserPauseMessage message) {
    message.setPublisher(applicationUtil.getThisApplicationLocalIpAddress());
    try {
      publish(Constants.REDIS_CHANNEL.channelUserPause.toString(), objectMapper.writeValueAsString(message));
    } catch (JsonProcessingException e) {
      LOG.error("", e);
    }
  }

}
