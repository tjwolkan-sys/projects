package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import org.asteriskjava.manager.ManagerConnection;
import org.asteriskjava.manager.ManagerConnectionState;
import org.asteriskjava.manager.SendActionCallback;
import org.asteriskjava.manager.action.HangupAction;
import org.asteriskjava.manager.action.OriginateAction;
import org.asteriskjava.manager.event.VarSetEvent;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.StopConditionChecker;
import com.powerdialer.engine.asterisk.actions.ManagerActionFactory;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.Server;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.service.CDRService;
import com.powerdialer.util.LoggerDecorator;

/**
 * FIX-GLB90048674 (Faz 2): AMI event/command bağlantı ayrımı (gbwebphone modeli).
 *
 * <p>Tek {@code managerConnection} İKİYE bölünür: {@code eventConnection} (listener, bugünkü
 * bağlantının aynısı, state/recovery için AUTHORITATIVE) ve {@code commandConnection}
 * ({@code eventMask="off"}, listener YOK). Bu testler şunları kilitler:
 * <ul>
 *   <li>Originate ({@code originateCall} -> {@code sendActionUsingCallback}) COMMAND bağlantısından gider;
 *       event bağlantısı gönderim için KULLANILMAZ.</li>
 *   <li>{@code hangupCall} COMMAND bağlantısından gider.</li>
 *   <li>Event'ler {@code onManagerEvent}'e gelip handler'lara route edilir (event bağlantısı listener'dır)
 *       ve worker bunları işler.</li>
 *   <li>{@code getManagerConnectionState()} EVENT bağlantısının state'ini yansıtır (recovery semantiği değişmez).</li>
 *   <li>{@code logoff()} İKİ bağlantıyı da logoff eder ve worker'ı drain eder.</li>
 * </ul>
 *
 * <p>{@code createConnection} bağlantıları içeride kurduğu için, mevcut Faz 1 {@code setEventWorker}
 * seam'iyle aynı stilde package-private {@code setConnections} seam'i ile mock bağlantılar enjekte edilir
 * (tests/ami farklı pakette olduğundan {@link ReflectionTestUtils#invokeMethod} ile çağrılır).
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AmiCommandConnectionSplitRegressionTest {

  private static final String CALL_ID = "dl-cmd-split-1";

  @Mock
  private LoggerDecorator loggerDecorator;
  @Mock
  private ManagerConnectionComposition connectionComposition;
  @Mock
  private CDRService cdrService;
  @Mock
  private DialerFacade dialerFacade;
  @Mock
  private CallEventPublisher callEventPublisher;
  @Mock
  private CampaignDbOperations dbOperations;
  @Mock
  private CampaignRealTimeRingingManager campaignRealTimeRingingManager;
  @Mock
  private StopConditionChecker stopConditionChecker;
  @Mock
  private CallCleaner callCleaner;
  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;
  @Mock
  private ManagerActionFactory managerActionFactory;
  @Mock
  private RedisUtil redisUtil;
  @Mock
  private ManagerConnection eventConnection;
  @Mock
  private ManagerConnection commandConnection;

  private ManagerConnectionDecorator decorator;
  private CapturingExecutorService capturingExecutor;

  @Before
  public void setUp() {
    when(loggerDecorator.getLogger(ManagerConnectionDecorator.class)).thenReturn(loggerDecorator);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(connectionComposition.getDialerFacade()).thenReturn(dialerFacade);
    when(connectionComposition.getManagerActionFactory()).thenReturn(managerActionFactory);
    when(connectionComposition.getRedisUtil()).thenReturn(redisUtil);
    when(connectionComposition.getStopConditionChecker()).thenReturn(stopConditionChecker);
    when(connectionComposition.getCallCleaner()).thenReturn(callCleaner);
    when(connectionComposition.getCallUpToDateKeeper()).thenReturn(callUpToDateKeeper);
    when(dialerFacade.getCallEventPublisher()).thenReturn(callEventPublisher);
    when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(campaignRealTimeRingingManager);

    decorator = new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition);
    decorator.setCdrService(cdrService);
    decorator.setServer(server());
    decorator.setDbOperations(dbOperations);

    // Faz 2 seam: event + command bağlantısını enjekte et (createConnection çağrılmaz).
    ReflectionTestUtils.invokeMethod(decorator, "setConnections", eventConnection, commandConnection);

    capturingExecutor = new CapturingExecutorService();
    ReflectionTestUtils.invokeMethod(decorator, "setEventWorker", capturingExecutor);
  }

  @Test
  public void originateShouldSendViaCommandConnectionNotEventConnection() throws Exception {
    Call call = baseCall();
    OriginateAction originateAction = new OriginateAction();
    when(managerActionFactory.generateCall(call)).thenReturn(originateAction);

    boolean result = decorator.originateCall(call);

    assertThat("originate başarılı dönmeli", result, is(true));
    // Originate COMMAND bağlantısından gitmeli (callback wrapper ile).
    verify(commandConnection).sendAction(eq(originateAction), any(SendActionCallback.class));
    // EVENT bağlantısı gönderim için KULLANILMAMALI.
    verify(eventConnection, never()).sendAction(any(), any());
  }

  @Test
  public void hangupShouldSendViaCommandConnectionNotEventConnection() throws Exception {
    Call call = baseCall();
    call.setChannel("Local/5551000@out-dialer-attempt-0009b39a;1");

    decorator.hangupCall(call);

    // Hangup COMMAND bağlantısından gitmeli (callback null).
    verify(commandConnection).sendAction(any(HangupAction.class), isNull());
    verify(eventConnection, never()).sendAction(any(), any());
  }

  @Test
  public void eventsShouldStillRouteToHandlersViaWorker() {
    Call call = baseCall();
    decorator.getCalls().put(call.getId(), call);

    VarSetEvent event = new VarSetEvent(this);
    event.setVariable("ORIGINID");
    event.setValue(CALL_ID);
    event.setChannel("Local/5551000@out-dialer-attempt-0009b39a;1");
    event.setUniqueId("GAVXBSNTRGRP01-1777966771.480652");
    event.setDateReceived(new java.util.Date());

    // Event bağlantısı listener olduğu için onManagerEvent çağrılır (decorator listener'dır).
    decorator.onManagerEvent(event);

    // İş worker'a submit edildi; henüz çalışmadı.
    assertThat("event işi worker'a submit edilmeli", capturingExecutor.captured.size(), is(1));
    assertThat("handler çağıran thread'de inline çalışmamalı (startDate null)", call.getStartDate(), is(nullValue()));

    // Worker task'ı çalışınca handler işini yapar (event -> handleCallCreation).
    capturingExecutor.runCaptured();

    assertThat("worker event'i handler'a route etmeli (startDate set olur)", call.getStartDate(), notNullValue());
    verify(dbOperations).insertCallResultToDB(call);
    // Event yolu komut bağlantısına dokunmamalı.
    verifyNoSendActionOnEither();
  }

  @Test
  public void managerConnectionStateShouldReflectEventConnection() {
    when(eventConnection.getState()).thenReturn(ManagerConnectionState.CONNECTED);
    when(commandConnection.getState()).thenReturn(ManagerConnectionState.DISCONNECTED);

    assertThat("getManagerConnectionState EVENT bağlantısını yansıtmalı",
        decorator.getManagerConnectionState(), is(ManagerConnectionState.CONNECTED));
    // getManagerConnection() primary (event) bağlantısı olmalı.
    assertThat(decorator.getManagerConnection(), sameInstance(eventConnection));
    // State okuması command bağlantısının state'ine bakmamalı.
    verify(commandConnection, never()).getState();
  }

  @Test
  public void logoffShouldLogoffBothConnectionsAndDrainWorker() {
    ReflectionTestUtils.invokeMethod(decorator, "logoff");

    verify(eventConnection).logoff();
    verify(commandConnection).logoff();
    assertThat("logoff worker'ı drain edip kapatmalı (shutdown çağrıldı)", capturingExecutor.shutdown, is(true));
  }

  private void verifyNoSendActionOnEither() {
    try {
      verify(commandConnection, never()).sendAction(any(), any());
      verify(eventConnection, never()).sendAction(any(), any());
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  /** Runnable'ları çalıştırmadan saklayan deterministik executor (Faz 1 stiliyle aynı). */
  private static final class CapturingExecutorService extends AbstractExecutorService {
    private final List<Runnable> captured = new ArrayList<>();
    private boolean shutdown;

    @Override
    public void execute(Runnable command) {
      captured.add(command);
    }

    void runCaptured() {
      List<Runnable> snapshot = new ArrayList<>(captured);
      captured.clear();
      for (Runnable r : snapshot) {
        r.run();
      }
    }

    @Override
    public void shutdown() {
      shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      shutdown = true;
      return new ArrayList<>(captured);
    }

    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    @Override
    public boolean isTerminated() {
      return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }
  }

  private Call baseCall() {
    Campaign campaign = new Campaign();
    campaign.setId(45L);
    campaign.setColResultRcSequence("rc_sequence");
    campaign.setAttachedDataLongTermSave(false);
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    Call call = new Call();
    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setResultCodeSequence(0);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));

    CDR cdr = new CDR();
    cdr.setInteractionId(CALL_ID);
    call.setCdr(cdr);
    return call;
  }

  private Server server() {
    Server server = new Server();
    server.setIp("127.0.0.1");
    return server;
  }
}
