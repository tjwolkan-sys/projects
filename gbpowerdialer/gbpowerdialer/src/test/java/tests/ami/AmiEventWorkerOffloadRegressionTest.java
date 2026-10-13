package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

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
import com.powerdialer.service.CDRService;
import com.powerdialer.util.LoggerDecorator;

/**
 * Faz 1B: {@link ManagerConnectionDecorator#onManagerEvent} reader thread'inde
 * SENKRON DB/CDR işi YAPMAMALI; işi enjekte edilen {@code eventWorker}'a devretmeli.
 *
 * <p>Capturing executor (Runnable'ı çalıştırmadan saklayan) enjekte edilir.
 * {@code onManagerEvent} çağrıldıktan sonra DB yan etkisinin HENÜZ olmadığı
 * doğrulanır (iş çağıran thread'de inline çalışmadı = deferred). Sonra yakalanan
 * Runnable elle çalıştırılınca yan etki gerçekleşir (deferred task gerçek işi yapar).
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AmiEventWorkerOffloadRegressionTest {

  private static final String CALL_ID = "dl-offload-1";

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

  private ManagerConnectionDecorator decorator;
  private CapturingExecutorService capturingExecutor;

  @Before
  public void setUp() {
    when(loggerDecorator.getLogger(ManagerConnectionDecorator.class)).thenReturn(loggerDecorator);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(connectionComposition.getDialerFacade()).thenReturn(dialerFacade);
    when(connectionComposition.getStopConditionChecker()).thenReturn(stopConditionChecker);
    when(connectionComposition.getCallCleaner()).thenReturn(callCleaner);
    when(connectionComposition.getCallUpToDateKeeper()).thenReturn(callUpToDateKeeper);
    when(dialerFacade.getCallEventPublisher()).thenReturn(callEventPublisher);
    when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(campaignRealTimeRingingManager);

    decorator = new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition);
    decorator.setCdrService(cdrService);
    decorator.setServer(server());
    decorator.setDbOperations(dbOperations);

    capturingExecutor = new CapturingExecutorService();
    // setEventWorker package-private; tests/ami farklı pakette olduğu için reflection ile çağrılır
    // (mevcut tests/ami testlerinin private handler invokeMethod idiom'uyla aynı).
    ReflectionTestUtils.invokeMethod(decorator, "setEventWorker", capturingExecutor);
  }

  @Test
  public void shouldDeferEventHandlingToWorkerInsteadOfRunningOnCallerThread() {
    Call call = baseCall();
    decorator.getCalls().put(call.getId(), call);

    VarSetEvent event = new VarSetEvent(this);
    event.setVariable("ORIGINID");
    event.setValue(CALL_ID);
    event.setChannel("Local/5551000@out-dialer-attempt-0009b39a;1");
    event.setUniqueId("GAVXBSNTRGRP01-1777966771.480652");
    event.setDateReceived(new java.util.Date());

    decorator.onManagerEvent(event);

    // İş worker'a submit edildi ama HENÜZ çalıştırılmadı: çağıran (reader) thread bloklanmadı.
    assertThat("onManagerEvent işi worker'a submit etmeli", capturingExecutor.captured.size(), is(1));
    assertThat("VarSet handler'ı çağıran thread'de inline çalışmamalı (startDate hâlâ null)",
        call.getStartDate(), is(nullValue()));
    verify(dbOperations, never()).insertCallResultToDB(call);

    // Yakalanan task çalıştırılınca gerçek iş yapılır.
    capturingExecutor.runCaptured();

    assertThat("Deferred task handleCallCreation'ı çalıştırınca startDate set olmalı",
        call.getStartDate(), notNullValue());
    verify(dbOperations).insertCallResultToDB(call);
  }

  /** Runnable'ları çalıştırmadan saklayan deterministik executor. */
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
