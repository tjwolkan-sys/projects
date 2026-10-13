package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.asteriskjava.manager.event.VarSetEvent;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.asterisk.events.NewTransferChannelEvent;
import com.powerdialer.engine.asterisk.fastagi.AgentDialLockRegistry;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Group;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.Server;
import com.powerdialer.model.Status;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.Utils;

/**
 * Transfer sırasında agent'ı erken serbest bırakan akışın regression testleri.
 *
 * <p>Test edilen contract:
 * <ul>
 *   <li>{@code handleCallCreation} VarSetEvent.uniqueId'yi {@code Call.linkedid}'e yazar.</li>
 *   <li>{@code handleNewTransferChannel}, önce chanVariables.PARENTID üzerinden
 *       (canonical dialer call ID), sonra LinkedChannel↔linkedid fallback'i ile
 *       eşleşen call'u bulup agent'ı idle havuzuna döndürür, callUpToDateKeeper'dan
 *       unregister eder, dialInProcess'i kapatır, inCallsSize'ı düşürür ve
 *       transferred bayrağını set eder.</li>
 *   <li>Transfer sonrası hangup yolundaki {@code doCampaignDecrementationsOnHangup}
 *       çift decrement yapmaz (bayrak guard'ı).</li>
 *   <li>Eşleşme yok, LinkedChannel boş ve status PAUSE edge case'leri güvenli.</li>
 *   <li>Idempotent: aynı çağrı için ikinci NewTransferChannel no-op.</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class TransferReleaseAgentRegressionTest {

  private static final String CALL_ID = "dl-transfer-release-1";
  private static final String LINKEDID = "GAVXBSNTRGRP01-1777966771.480652";

  @Mock
  private LoggerDecorator loggerDecorator;

  @Mock
  private ManagerConnectionComposition connectionComposition;

  @Mock
  private DialerFacade dialerFacade;

  @Mock
  private CampaignDbOperations dbOperations;

  @Mock
  private CampaignRealTimeRingingManager campaignRealTimeRingingManager;

  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;

  @Mock
  private ACDEngine acdEngine;

  private ManagerConnectionDecorator decorator;
  private Map<String, ManagerConnectionDecorator> openedConnections;
  private AgentDialLockRegistry lockRegistry;

  @Before
  public void setUp() {
    when(loggerDecorator.getLogger(ManagerConnectionDecorator.class)).thenReturn(loggerDecorator);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(connectionComposition.getDialerFacade()).thenReturn(dialerFacade);
    when(connectionComposition.getCallUpToDateKeeper()).thenReturn(callUpToDateKeeper);
    when(connectionComposition.getAcdEngine()).thenReturn(acdEngine);
    lockRegistry = new AgentDialLockRegistry();
    when(connectionComposition.getAgentDialLockRegistry()).thenReturn(lockRegistry);
    when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(campaignRealTimeRingingManager);
    openedConnections = new HashMap<>();
    when(connectionComposition.getOpenedConnections()).thenReturn(openedConnections);

    decorator = new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition);
    decorator.setServer(server());
    decorator.setDbOperations(dbOperations);
    openedConnections.put("127.0.0.1", decorator);
  }

  @Test
  public void shouldCaptureLinkedidFromVarSetEventOnCallCreation() {
    Call call = baseCall();
    decorator.getCalls().put(call.getId(), call);

    VarSetEvent varSetEvent = new VarSetEvent(this);
    varSetEvent.setVariable("ORIGINID");
    varSetEvent.setValue(CALL_ID);
    varSetEvent.setChannel("Local/05462828950@out-dialer-attempt-yapikredi-bireysel-0009b39a;1");
    varSetEvent.setUniqueId(LINKEDID);
    varSetEvent.setDateReceived(new Date());

    ReflectionTestUtils.invokeMethod(decorator, "handleCallCreation", varSetEvent);

    assertThat("VarSetEvent.uniqueId Call.linkedid'e yazılmalı (LINKEDID eşleştirme anahtarı)",
        call.getLinkedid(), is(LINKEDID));
    assertThat(call.getStartDate(), notNullValue());
  }

  @Test
  public void shouldReleaseAgentToIdlePoolOnTransferEvent() {
    Call call = answeredCall();
    call.setLinkedid(LINKEDID);
    User agent = call.getUser();
    agent.setDialInProcess(true);
    decorator.getCalls().put(call.getId(), call);
    int inCallsBefore = call.getCampaign().getInCalls().get();

    NewTransferChannelEvent event = transferEvent(LINKEDID);
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", event);

    assertThat("Agent transfer anında idle havuzuna eklenmeli",
        call.isTransferred(), is(true));
    assertThat("dialInProcess transfer ile kapanmalı",
        agent.isDialInProcess(), is(false));
    assertThat("calls cache'inde call hâlâ duruyor olmalı (DialerHangup beklenecek)",
        decorator.getCalls().getIfPresent(CALL_ID), sameInstance(call));
    assertThat("inCalls sayacı transfer anında 1 düşmeli",
        call.getCampaign().getInCalls().get(), is(inCallsBefore - 1));

    verify(acdEngine).addToIdleUsers(agent);
    verify(callUpToDateKeeper).unregisterCall(call);
  }

  @Test
  public void shouldReleaseAgentDialLockOnTransferEvent() {
    Call call = answeredCall();
    call.setLinkedid(LINKEDID);
    User agent = call.getUser();
    agent.setDialInProcess(true);
    // The AGI script holds the dial lock for this call: its Dial is still blocked because the
    // customer is now bridged to the transfer target (no hangup yet).
    lockRegistry.tryLock(agent.getId(), call.getId());
    decorator.getCalls().put(call.getId(), call);

    NewTransferChannelEvent event = transferEvent(LINKEDID);
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", event);

    assertThat("Transfer, agent'in dialer dial lock'unu serbest bırakmalı ki agent yeni çağrı alabilsin",
        lockRegistry.isLocked(agent.getId()), is(false));
  }

  @Test
  public void shouldNotDoubleDecrementInCallsOnHangupAfterTransfer() {
    Call call = answeredCall();
    call.setLinkedid(LINKEDID);
    call.setTransferred(true);
    int inCallsBefore = call.getCampaign().getInCalls().get();

    ReflectionTestUtils.invokeMethod(decorator, "doCampaignDecrementationsOnHangup", call);

    assertThat("Transferred çağrıda hangup decrementInCallsSize çağırmamalı",
        call.getCampaign().getInCalls().get(), is(inCallsBefore));
  }

  @Test
  public void shouldDecrementInCallsOnHangupForNonTransferredCall() {
    Call call = answeredCall();
    // transferred=false (varsayılan)
    int inCallsBefore = call.getCampaign().getInCalls().get();

    ReflectionTestUtils.invokeMethod(decorator, "doCampaignDecrementationsOnHangup", call);

    assertThat("Transfer edilmemiş çağrıda hangup mevcut decrementInCallsSize davranışını korumalı",
        call.getCampaign().getInCalls().get(), is(inCallsBefore - 1));
  }

  @Test
  public void shouldBeIdempotentOnDuplicateTransferEvent() {
    Call call = answeredCall();
    call.setLinkedid(LINKEDID);
    User agent = call.getUser();
    decorator.getCalls().put(call.getId(), call);

    NewTransferChannelEvent first = transferEvent(LINKEDID);
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", first);
    int inCallsAfterFirst = call.getCampaign().getInCalls().get();

    NewTransferChannelEvent second = transferEvent(LINKEDID);
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", second);

    assertThat("İkinci transfer event'i no-op olmalı",
        call.getCampaign().getInCalls().get(), is(inCallsAfterFirst));
    verify(acdEngine, times(1)).addToIdleUsers(agent);
    verify(callUpToDateKeeper, times(1)).unregisterCall(call);
  }

  @Test
  public void shouldIgnoreTransferEventWithEmptyLinkedChannel() {
    Call call = answeredCall();
    call.setLinkedid(LINKEDID);
    decorator.getCalls().put(call.getId(), call);

    NewTransferChannelEvent event = transferEvent(null);
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", event);

    assertThat(call.isTransferred(), is(false));
    verify(acdEngine, never()).addToIdleUsers(any(User.class));
    verify(callUpToDateKeeper, never()).unregisterCall(any(Call.class));
  }

  @Test
  public void shouldIgnoreTransferEventWhenNoCallMatchesLinkedid() {
    Call call = answeredCall();
    call.setLinkedid(LINKEDID);
    decorator.getCalls().put(call.getId(), call);

    NewTransferChannelEvent event = transferEvent("GAVXBSNTRGRP99-9999999999.999999");
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", event);

    assertThat(call.isTransferred(), is(false));
    verify(acdEngine, never()).addToIdleUsers(any(User.class));
    verify(callUpToDateKeeper, never()).unregisterCall(any(Call.class));
  }

  @Test
  public void shouldUnregisterCallButNotAddToIdleWhenAgentIsPaused() {
    Call call = answeredCall();
    call.setLinkedid(LINKEDID);
    User agent = call.getUser();
    // Webphone MT'yi PAUSE'a almış: dialer için available değil.
    Status pauseStatus = new Status();
    pauseStatus.setAvailableForDialerCalls(false);
    agent.setStatus(pauseStatus);
    agent.setDialInProcess(true);
    decorator.getCalls().put(call.getId(), call);

    NewTransferChannelEvent event = transferEvent(LINKEDID);
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", event);

    assertThat("dialInProcess pause durumunda da kapanmalı",
        agent.isDialInProcess(), is(false));
    assertThat("Transfer bayrağı set edilmeli (çift muhasebeyi engellemek için)",
        call.isTransferred(), is(true));
    verify(callUpToDateKeeper).unregisterCall(call);
    verify(acdEngine, never()).addToIdleUsers(any(User.class));
  }

  @Test
  public void shouldMatchTransferByParentIdFromChanVariables() {
    // Asterisk dialplan'inde Local kanal zinciri nedeniyle VarSetEvent.uniqueId, transfer
    // event'inin LinkedChannel'iyle eşleşmeyebilir. chanVariables.PARENTID dialer call ID'sini
    // taşıdığı için `calls` cache'ine doğrudan O(1) çözünür — bu daha güvenilir match path'idir.
    Call call = answeredCall();
    // linkedid kasıtlı olarak farklı: PARENTID match'ini izole ediyoruz.
    call.setLinkedid("does-not-match");
    User agent = call.getUser();
    agent.setDialInProcess(true);
    decorator.getCalls().put(call.getId(), call);
    int inCallsBefore = call.getCampaign().getInCalls().get();

    NewTransferChannelEvent event = transferEventWithChanVariables(
        "GAVXBSS2REGT01-some-other-linkedid",
        Collections.singletonMap("PARENTID", CALL_ID));
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", event);

    assertThat("PARENTID üzerinden eşleşen call transfer edilmeli",
        call.isTransferred(), is(true));
    assertThat(agent.isDialInProcess(), is(false));
    assertThat(call.getCampaign().getInCalls().get(), is(inCallsBefore - 1));
    verify(acdEngine).addToIdleUsers(agent);
    verify(callUpToDateKeeper).unregisterCall(call);
  }

  @Test
  public void shouldPreferParentIdOverLinkedChannelWhenBothMatchDifferentCalls() {
    // İki anahtar farklı call'lara işaret ederse PARENTID kazanmalı: dialer call ID'si
    // canonical anahtardır, linkedid Asterisk inheritance'ına bağımlıdır.
    Call linkedidMatch = answeredCall();
    linkedidMatch.setId("dl-linkedid-match");
    linkedidMatch.getCdr().setInteractionId("dl-linkedid-match");
    linkedidMatch.setLinkedid(LINKEDID);
    decorator.getCalls().put(linkedidMatch.getId(), linkedidMatch);

    Call parentIdMatch = answeredCall();
    parentIdMatch.setId("dl-parentid-match");
    parentIdMatch.getCdr().setInteractionId("dl-parentid-match");
    parentIdMatch.setLinkedid("unrelated-linkedid");
    decorator.getCalls().put(parentIdMatch.getId(), parentIdMatch);

    NewTransferChannelEvent event = transferEventWithChanVariables(
        LINKEDID,
        Collections.singletonMap("PARENTID", "dl-parentid-match"));
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", event);

    assertThat("PARENTID match'i öncelikli olmalı",
        parentIdMatch.isTransferred(), is(true));
    assertThat("LinkedChannel ile eşleşen diğer call etkilenmemeli",
        linkedidMatch.isTransferred(), is(false));
  }

  @Test
  public void shouldFallBackToLinkedChannelWhenParentIdAbsent() {
    // PARENTID yoksa veya cache'de yoksa eski linkedid path'i hâlâ çalışmalı.
    Call call = answeredCall();
    call.setLinkedid(LINKEDID);
    decorator.getCalls().put(call.getId(), call);

    NewTransferChannelEvent event = transferEventWithChanVariables(
        LINKEDID,
        Collections.singletonMap("INTERACTION_ID", LINKEDID)); // PARENTID yok
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", event);

    assertThat(call.isTransferred(), is(true));
  }

  @Test
  public void shouldFallBackToLinkedChannelWhenParentIdNotInCallsCache() {
    // PARENTID değeri var ama cache'e expire olmuş / kaydedilmemiş — linkedid fallback.
    Call call = answeredCall();
    call.setLinkedid(LINKEDID);
    decorator.getCalls().put(call.getId(), call);

    NewTransferChannelEvent event = transferEventWithChanVariables(
        LINKEDID,
        Collections.singletonMap("PARENTID", "dl-not-in-cache"));
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", event);

    assertThat(call.isTransferred(), is(true));
  }

  @Test
  public void shouldIgnoreTransferEventWhenNeitherKeyMatches() {
    Call call = answeredCall();
    call.setLinkedid(LINKEDID);
    decorator.getCalls().put(call.getId(), call);

    NewTransferChannelEvent event = transferEventWithChanVariables(
        "unrelated-linkedid",
        Collections.singletonMap("PARENTID", "dl-not-in-cache"));
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", event);

    assertThat(call.isTransferred(), is(false));
    verify(acdEngine, never()).addToIdleUsers(any(User.class));
    verify(callUpToDateKeeper, never()).unregisterCall(any(Call.class));
  }

  @Test
  public void shouldFindCallInSiblingDecoratorByParentId() {
    // Cross-server blind transfer canlı senaryo: Call origination'ı bir sunucuda
    // (örn. GAVXBSS2REGT02) yapılmış, NewTransferChannel başka bir sunucunun
    // decorator'una (örn. GAVXBSS2REGT01) düşmüş. PARENTID dialer call ID UUID'si
    // olduğu için sunucu namespace'inden bağımsız ve cross-decorator lookup ile
    // bulunabilmeli.
    ManagerConnectionDecorator originDecorator = newSiblingDecorator("10.12.135.32");
    Call call = answeredCall();
    call.setLinkedid("GAVXBSS2REGT02-1779433904.720");
    User agent = call.getUser();
    agent.setDialInProcess(true);
    originDecorator.getCalls().put(call.getId(), call);
    int inCallsBefore = call.getCampaign().getInCalls().get();

    NewTransferChannelEvent event = transferEventWithChanVariables(
        "GAVXBSS2REGT01-1779433915.3988",
        Collections.singletonMap("PARENTID", CALL_ID));
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", event);

    assertThat("Sibling decorator'un cache'indeki call PARENTID ile bulunmalı",
        call.isTransferred(), is(true));
    assertThat(agent.isDialInProcess(), is(false));
    assertThat(call.getCampaign().getInCalls().get(), is(inCallsBefore - 1));
    verify(acdEngine).addToIdleUsers(agent);
    verify(callUpToDateKeeper).unregisterCall(call);
  }

  @Test
  public void shouldPreferLocalCacheOverSiblingForSameParentId() {
    // Defensive: aynı PARENTID hem local hem sibling cache'inde (pratikte
    // imkansız ama önceliği kilitliyoruz). Local cache kazanır — sıcak path.
    Call localCall = answeredCall();
    decorator.getCalls().put(localCall.getId(), localCall);

    ManagerConnectionDecorator siblingDecorator = newSiblingDecorator("10.12.135.99");
    Call siblingCall = answeredCall();
    siblingDecorator.getCalls().put(siblingCall.getId(), siblingCall);

    NewTransferChannelEvent event = transferEventWithChanVariables(
        "unrelated-linkedid",
        Collections.singletonMap("PARENTID", CALL_ID));
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", event);

    assertThat(localCall.isTransferred(), is(true));
    assertThat(siblingCall.isTransferred(), is(false));
  }

  @Test
  public void shouldNotCrossDecoratorMatchByLinkedidOnly() {
    // linkedid Asterisk per-server UNIQUEID namespace'i; cross-decorator linkedid
    // taraması yanlış call eşleştirme riski taşır. Sadece bu decorator'da
    // aranmalı. PARENTID yoksa ve linkedid sadece sibling'de eşleşiyorsa → no match.
    ManagerConnectionDecorator siblingDecorator = newSiblingDecorator("10.12.135.32");
    Call siblingCall = answeredCall();
    siblingCall.setLinkedid(LINKEDID);
    siblingDecorator.getCalls().put(siblingCall.getId(), siblingCall);

    NewTransferChannelEvent event = transferEvent(LINKEDID); // chanVariables yok → PARENTID null
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", event);

    assertThat("Sibling decorator'daki linkedid eşleşmesi cross-decorator yapılmamalı",
        siblingCall.isTransferred(), is(false));
    verify(acdEngine, never()).addToIdleUsers(any(User.class));
  }

  @Test
  public void shouldNotCrashWhenCrossDecoratorIterationThrows() {
    // openedConnections iterasyonu (örn. CME/NPE) fırlatırsa handler çökmemeli;
    // WARN düşürüp fallback path'e (linkedid) geçmeli.
    when(connectionComposition.getOpenedConnections())
        .thenThrow(new java.util.ConcurrentModificationException("simulated CME"));
    Call call = answeredCall();
    call.setLinkedid(LINKEDID);
    decorator.getCalls().put(call.getId(), call); // local PARENTID lookup miss eder
                                                  // (Call ID = CALL_ID, biz farklı PARENTID gönderiyoruz)

    NewTransferChannelEvent event = transferEventWithChanVariables(
        LINKEDID,
        Collections.singletonMap("PARENTID", "dl-only-on-siblings"));
    ReflectionTestUtils.invokeMethod(decorator, "handleNewTransferChannel", event);

    // PARENTID local cache'te yok, cross-decorator throw'a takılır → linkedid fallback
    // mevcut decorator'da bulur ve transfer side-effect'leri tetiklenir.
    assertThat("Cross-decorator throw exception leak etmemeli; linkedid fallback çalışmalı",
        call.isTransferred(), is(true));
  }

  @Test
  public void shouldFindCallByLinkedidAcrossDecoratorCallsCache() {
    Call other = baseCall();
    other.setId("dl-other-call");
    other.setLinkedid("GAVXBSNTRGRP01-other.0001");
    decorator.getCalls().put(other.getId(), other);

    Call target = answeredCall();
    target.setLinkedid(LINKEDID);
    decorator.getCalls().put(target.getId(), target);

    Call matched = ReflectionTestUtils.invokeMethod(decorator, "findCallByLinkedid", LINKEDID);

    assertThat(matched, sameInstance(target));

    Call notFound = ReflectionTestUtils.invokeMethod(decorator, "findCallByLinkedid", "no-match");
    assertThat(notFound, nullValue());

    Call onNull = ReflectionTestUtils.invokeMethod(decorator, "findCallByLinkedid", new Object[]{null});
    assertThat(onNull, nullValue());
  }

  private Call answeredCall() {
    Call call = baseCall();
    call.setUser(agentUser());
    call.setStartDate(Utils.convertDateToDouble(new Date(1000L)));
    call.setAnswerDate(Utils.convertDateToDouble(new Date(2000L)));
    call.setAnsweredByUser(true);
    call.getCdr().setCallHandlingStartTime(new Date(2500L));
    // Active call simulation: agent answered, inCalls counter incremented
    call.getCampaign().incrementInCallsSize(call);
    return call;
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

  private User agentUser() {
    Group group = new Group();
    group.setId(1L);

    Status available = new Status();
    available.setId(Constants.SYSTEM_STATUS.AVAILABLE.getId());
    available.setAvailableForDialerCalls(true);

    User user = new User();
    user.setId(81340L);
    user.setUsername("81340");
    user.setGroup(group);
    user.setStatus(available);
    return user;
  }

  private NewTransferChannelEvent transferEvent(String linkedChannel) {
    NewTransferChannelEvent event = new NewTransferChannelEvent(this);
    event.setLinkedChannel(linkedChannel);
    event.setTarget("55325");
    event.setEventChannel("Local/55325@ontransfer-0000c396;2");
    event.setDateReceived(new Date());
    return event;
  }

  private NewTransferChannelEvent transferEventWithChanVariables(String linkedChannel, Map<String, String> chanVariables) {
    NewTransferChannelEvent event = transferEvent(linkedChannel);
    event.setChanVariables(new HashMap<>(chanVariables));
    return event;
  }

  /**
   * openedConnections registry'sine bir sibling decorator ekler ve döner.
   * Her sibling kendi {@code calls} cache'ine sahiptir; testler bu cache'i
   * doldurarak cross-server senaryosunu simüle eder.
   */
  private ManagerConnectionDecorator newSiblingDecorator(String ip) {
    ManagerConnectionDecorator sibling = new ManagerConnectionDecorator(ip, 5038, "user", "password", connectionComposition);
    Server siblingServer = new Server();
    siblingServer.setIp(ip);
    sibling.setServer(siblingServer);
    openedConnections.put(ip, sibling);
    return sibling;
  }

  private Server server() {
    Server server = new Server();
    server.setIp("127.0.0.1");
    return server;
  }
}
