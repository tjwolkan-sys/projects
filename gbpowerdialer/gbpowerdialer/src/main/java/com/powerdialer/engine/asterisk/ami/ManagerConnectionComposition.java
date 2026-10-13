package com.powerdialer.engine.asterisk.ami;

import static com.powerdialer.util.Constants.TELEPHONY_RESULT_CODES;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;
import javax.servlet.ServletContext;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.powerdialer.engine.boost.DynamicBoostManager;
import com.powerdialer.engine.predictive.beta.processor.PredictiveBetaCallManager;
import com.powerdialer.service.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asteriskjava.manager.ManagerConnectionState;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.StopConditionChecker;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.actions.ManagerActionFactory;
import com.powerdialer.engine.asterisk.fastagi.AgentDialLockRegistry;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.dialer.routing.FctChannelGate;
import com.powerdialer.model.Call;
import com.powerdialer.model.Server;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.SERVER_PROPERTY;
import com.powerdialer.util.LoggerDecorator;

/**
 * Composition class encapsulating asterisk connections
 */
@Component
@Lazy(false)
public class ManagerConnectionComposition implements InitializingBean {

  private static final Log LOG = LogFactory.getLog(ManagerConnectionComposition.class);
  private static final int CALLS_CLEAN_UP_PERIOD = 60000;
  private ScheduledExecutorService callsCleanUpService;
  private Map<String, ManagerConnectionDecorator> openedConnections = new HashMap<>();
  private Map<Integer, TelephonyResultCode> ituToTelephonyResultCodeRelations = new HashMap<>();

  @Value("${manager.connection.login.attempts}")
  private int maxLoginAttempts;

  @Value("${manager.connection.ping.attempts}")
  private int maxPingAttempts;

  @Value("${manager.connection.ping.timeout}")
  private long pingTimeout;

  @Value("${manager.connection.check.interval}")
  private long stateCheckInterval;

  @Value("${previewTimeoutOriginationDelaySec}")
  private int previewTimeoutOriginationDelaySec;

  @Autowired
  private ServletContext context;

  @Autowired
  private CampaignDbOperations dbOperations;

  @Autowired
  private ServerService serverService;

  @Autowired
  private TelephonyResultCodeService telephonyResultCodeService;

  @Autowired
  private LoggerDecorator loggerDecorator;

  private RedisUtil redisUtil;

  @Autowired
  private ACDEngine acdEngine;

  @Autowired
  private CallUpToDateKeeper callUpToDateKeeper;

  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private DialerFacade dialerFacade;

  @Autowired
  private ManagerActionFactory managerActionFactory;

  @Autowired
  private CallCleaner callCleaner;

  @Autowired
  private StopConditionChecker stopConditionChecker;

  @Autowired
  private CDRService cdrService;

  @Autowired
  private AttachedDataItemService attachedDataItemService;

  @Autowired
  private DynamicBoostManager dynamicBoostManager;

  @Autowired
  private PredictiveBetaCallManager predictiveBetaCallManager;

  @Autowired
  private AgentDialLockRegistry agentDialLockRegistry;

  @Autowired
  private FctChannelGate fctChannelGate;

  private CampaignService campaignService;

  public CampaignDbOperations getCampaignDbOperations() {
    return dbOperations;
  }

  public LoggerDecorator getLoggerDecorator() {
    return loggerDecorator;
  }

  public RedisUtil getRedisUtil() {
    if (redisUtil == null) {
      redisUtil = applicationContext.getBean(RedisUtil.class);
    }
    return redisUtil;
  }

  public ACDEngine getAcdEngine() {
    return acdEngine;
  }

  public AgentDialLockRegistry getAgentDialLockRegistry() {
    return agentDialLockRegistry;
  }

  public FctChannelGate getFctChannelGate() {
    return fctChannelGate;
  }

  public CallUpToDateKeeper getCallUpToDateKeeper() {
    return callUpToDateKeeper;
  }

  public DialerFacade getDialerFacade() {
    return dialerFacade;
  }

  public ManagerActionFactory getManagerActionFactory() {
    return managerActionFactory;
  }

  public CallCleaner getCallCleaner() {
    return callCleaner;
  }

  public StopConditionChecker getStopConditionChecker() {
    return stopConditionChecker;
  }

  public AttachedDataItemService getAttachedDataItemService() {
    return attachedDataItemService;
  }

  public int getPreviewTimeoutOriginationDelaySec() {
    return previewTimeoutOriginationDelaySec;
  }

    public CDRService getCdrService() {
        return cdrService;
    }

  public CampaignService getCampaignService() {
    if (campaignService == null) {
      campaignService = applicationContext.getBean(CampaignService.class);
    }
    return campaignService;
  }

  @Override
  public void afterPropertiesSet() {
    getEnabledServersAndCreateManagerConnections();
    createItuToTelephonyResultCodeRelations();
    validateInternalTelephonyResultCodeMappings();
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true)
                                                            .setNameFormat("call-cleanup-executor-%d")
                                                            .build();
    callsCleanUpService = Executors.newSingleThreadScheduledExecutor(threadFactory);
    beginCallCleanUpExecution();
  }

  @PreDestroy
  public final void preDestroy() {
    // FIX-GLB90048674 (Faz 1B): decorator-level logoff() çağrılır ki worker drain yolu
    // (shutdown() + awaitTermination) çalışsın ve kuyruktaki DB/CDR yazımları kapanışta
    // boşaltılsın (invariant #2 — kayıpsızlık). Ham getManagerConnection().logoff() drain'i
    // atlardı. logoff() managerConnection için null-guard'lı, bağlantı hiç kurulmamışsa da güvenli.
    openedConnections.values().forEach(ManagerConnectionDecorator::logoff);
  }

  /**
   * Gets enabled servers and creates managerConnection for each registrar server.
   */
  private void getEnabledServersAndCreateManagerConnections() {
    for (Server server : serverService.getAll(true, false)) {
      if (server.getType() == Constants.SERVER_TYPE.REGISTRAR)
        try {
          openManagerConnection(server);
        } catch (Exception e) {
          LOG.error("Connection to server: " + server + " cannot be created: " + e.getMessage());
        }
      }
  }

  /**
   * Converting ITU resultcodes to internal telephonyResultCodes
   */
  private void createItuToTelephonyResultCodeRelations() {
    List<TelephonyResultCode> telephonyResultCodes = telephonyResultCodeService.getAll();
    for (TelephonyResultCode telephonyResultCode : telephonyResultCodes) {
      ituToTelephonyResultCodeRelations.put(telephonyResultCode.getCode(), telephonyResultCode);
    }
    context.setAttribute(TELEPHONY_RESULT_CODES, ituToTelephonyResultCodeRelations);
  }

  /**
   * FIX-320173 savunma katmanı: Engine'in finalize sözleşmesinde kullandığı dahili telephony
   * result code'larının pd_telephony_rc'de tanımlı olduğunu startup'ta doğrular. Eksik kod,
   * finalize/fallback yollarının null TRC üretmesine yol açabileceği için ERROR olarak
   * raporlanır; startup engellenmez.
   */
  private void validateInternalTelephonyResultCodeMappings() {
    int[] requiredCodes = requiredInternalTelephonyResultCodes();
    StringBuilder missingCodes = new StringBuilder();
    for (int requiredCode : requiredCodes) {
      if (!ituToTelephonyResultCodeRelations.containsKey(requiredCode)) {
        missingCodes.append(missingCodes.length() == 0 ? "" : ", ").append(requiredCode);
      }
    }
    if (missingCodes.length() > 0) {
      LOG.error("pd_telephony_rc is missing internal result code mapping(s): [" + missingCodes
          + "]. Finalize fallbacks may produce null telephony result codes.");
    }
  }

  /**
   * Engine'in karar verdiği dahili telephony result code'ları ({@code pd_telephony_rc.code}).
   * Startup doğrulaması bu listeyi kullanır; contract testi listeyi migration script'leriyle
   * kilitler. FEAT-325470 ile AMD ailesi (900, 113, 114, 115) eklendi: santral bu code'ları
   * gönderdiğinde satır yoksa hangup 901 fallback'ine düşer ve bad-number kuralı çalışmaz.
   */
  public static int[] requiredInternalTelephonyResultCodes() {
    return new int[] {
        Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION,
        Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode(),
        Constants.TELEPHONE_RC_CAMPAIGN_IS_STOPPED_FORCEFULLY,
        Constants.ERROR_RESULT_CODES.AMI_CONNECTION_LOSS.getCode(),
        Constants.TELEPHONY_RC_BLOCKED_BY_LSC,
        Constants.TELEPHONY_RC_ABANDONED_TIMEOUT,
        Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR,
        Constants.TELEPHONY_RC_LSC_TIMEOUT,
        Constants.TELEPHONE_RC_ORPHANED_CALL_TERMINATED,
        Constants.TELEPHONE_RC_NO_CALLERID_AVAILABLE,
        Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT,
        Constants.TELEPHONY_RC_AMBIGUOUS_RECORD,
        Constants.TELEPHONY_RC_AMD,
        Constants.TELEPHONY_RC_AMD_BAD_NUMBER,
        Constants.TELEPHONY_RC_AMD_NOT_REACHED,
        Constants.TELEPHONY_RC_AMD_UNKNOWN_TONE };
  }

  /**
   * Clean-up service to clean stale calls from managerConnectionDecorators
   */
  private void beginCallCleanUpExecution() {
    callsCleanUpService.scheduleAtFixedRate(() -> {
        for (Entry<String, ManagerConnectionDecorator> stringManagerConnectionDecoratorEntry : openedConnections.entrySet()) {
          stringManagerConnectionDecoratorEntry.getValue().getCalls().cleanUp();
          stringManagerConnectionDecoratorEntry.getValue().getRecoverableConnectionLossCalls().cleanUp();
        }
    }, Constants.CALL_EXPIRE_TIMEOUT, CALLS_CLEAN_UP_PERIOD, TimeUnit.MILLISECONDS);
  }

  /**
   * Open connection to asterisk server
   * @param server
   */
  public void openManagerConnection(final Server server) {
    if (server.getType() != Constants.SERVER_TYPE.REGISTRAR) {
      LOG.warn("Could not establish AMI connection with non-registrar: " + server);
      return;
    }
    if (openedConnections.get(server.getIp()) == null) {
      Properties serverProperties = retrieveServerProperties(server.getProperties());
      if (hasNecessaryConnectionProperties(serverProperties)) {
        createConnectionAddToOpenedConnections(serverProperties, server);
      } else {
        LOG.warn("Could not establish AMI connection with " + server + ": missed some required parameter");
      }
    }
  }

  /**
   * Convert serverProperties string to Properties object
   * @param serverProperties
   * @return
   */
  public Properties retrieveServerProperties(String serverProperties) {
    Properties properties = new Properties();
    if (serverProperties != null) {
      String[] propertyArray = serverProperties.split(";");
      for (String property : propertyArray) {
        String[] propertyKeyValue = property.split("=");
        if (propertyKeyValue.length == 2 && !(StringUtils.isEmpty(propertyKeyValue[0]) && StringUtils.isEmpty(propertyKeyValue[1]))) {
          properties.put(propertyKeyValue[0], propertyKeyValue[1]);
        }
      }
    }
    return properties;
  }

  /**
   * Check is serverProperties has port, username and password
   * @param serverProperties
   * @return
   */
  private boolean hasNecessaryConnectionProperties(Properties serverProperties) {
    return !(StringUtils.isEmpty(serverProperties.get(SERVER_PROPERTY.AMI_PORT.getName()))
        || StringUtils.isEmpty(serverProperties.get(SERVER_PROPERTY.AMI_USERNAME.getName()))
        || StringUtils.isEmpty(serverProperties.get(SERVER_PROPERTY.AMI_PASSWORD.getName())));
  }

  /**
   * Create AsteriskConnection and add to openedConnections map on if
   * successfully connected
   * @param serverProperties
   * @param server
   */
  public void createConnectionAddToOpenedConnections(Properties serverProperties, Server server) {
    ManagerConnectionDecorator connectionDecorator = new ManagerConnectionDecorator(server.getIp(),
                                                                                    Integer.parseInt(serverProperties.get(SERVER_PROPERTY.AMI_PORT.getName()).toString()),
                                                                                    serverProperties.get(SERVER_PROPERTY.AMI_USERNAME.getName()).toString(),
                                                                                    serverProperties.get(SERVER_PROPERTY.AMI_PASSWORD.getName()).toString(),
                                                                                    this);
    connectionDecorator.setServer(server);
    connectionDecorator.setDbOperations(dbOperations);
    connectionDecorator.setCdrService(cdrService);
    connectionDecorator.setDynamicBoostManager(dynamicBoostManager);
    connectionDecorator.setPredictiveBetaCallManager(predictiveBetaCallManager);
    connectionDecorator.setItuToTelephonyResultCodeRelations(ituToTelephonyResultCodeRelations);
    connectionDecorator.createConnection(maxLoginAttempts, maxPingAttempts, pingTimeout, stateCheckInterval, getAmiConnectionTimeoutProperty(serverProperties, server));
    server.setConnectionState(connectionDecorator.getManagerConnectionState());
    openedConnections.put(server.getIp(), connectionDecorator);
    LOG.info("Created AMI connection with server " + server.getIp());
  }

  private int getAmiConnectionTimeoutProperty(Properties properties, Server server) {
    Object value = properties.get(SERVER_PROPERTY.AMI_CONNECTION_TIMEOUT.getName());
    if (value != null)
      try {
        return Integer.parseInt(value.toString()) * 1000;
      } catch (NumberFormatException e) {
        LOG.warn("Could not parse AMI connection timeout for server " + server + ": " + value);
      }
    return Constants.DEFAULT_AMI_CONNECTION_TIMEOUT;
  }

  /**
   * Get asterisk connection state
   * @param hostname
   * @return
   */
  public ManagerConnectionState getManagerConnectionStatus(final String hostname) {
    ManagerConnectionDecorator serverConnection = openedConnections.get(hostname);
    if (serverConnection != null) {
      return serverConnection.getManagerConnectionState();
    }
    return null;
  }

  /**
   * Manual close ami connection by hostname, logs off from defined server,
   * removes it from connections map
   */
  public void stopManagerConnection(String hostname) {
    ManagerConnectionDecorator serverConnection = openedConnections.get(hostname);
    if (serverConnection != null) {
      serverConnection.logoff();
      openedConnections.remove(hostname);
    }
  }

  /**
   * Get call originated to asterisk
   */
  public Call getOriginatedCall(String callId) {
    for (ManagerConnectionDecorator connectionDecorator : getOpenedConnections().values()) {
      Call originatedCall = connectionDecorator.getCalls().getIfPresent(callId);
      if (originatedCall == null)
        originatedCall = connectionDecorator.getRecoverableConnectionLossCalls().getIfPresent(callId);
      if (originatedCall != null)
        return originatedCall;
    }
    return null;
  }

  public Map<String, ManagerConnectionDecorator> getOpenedConnections() {
    return openedConnections;
  }

  public Map<Integer, TelephonyResultCode> getItuToTelephonyResultCodeRelations() {
    return ituToTelephonyResultCodeRelations;
  }

}
