package tests.dnc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.PriorityBlockingQueue;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.StopConditionChecker;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CallConsumer;
import com.powerdialer.engine.dialer.CallProducer;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.DNCPhoneNumber;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.Tenant;
import com.powerdialer.service.CDRService;
import com.powerdialer.service.CampaignService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.DNCService;
import com.powerdialer.util.LoggerDecorator;

/**
 * Edge Case 2 (hot-drop): DNC tetiklendiği anda CallProducer.dataQueue ve
 * CallConsumer.callQueue'da bekleyen eşleşen çağrılar originate edilmeden
 * düşürülür; düşürülenler mevcut DNC filtre semantiğiyle (TRC=76 + CDR)
 * kapatılır, eşleşmeyenler kuyrukta kalır.
 */
@RunWith(MockitoJUnitRunner.class)
public class DncEngineDropsBufferedCallsForBlockedNumbersTest {

  private static final long TENANT_ID = 9999L;
  private static final long CAMPAIGN_ID = 318332L;
  private static final long CAMPAIGN_DNC_LIST_ID = 7L;
  private static final String BLOCKED_NUMBER = "5551112233";

  @InjectMocks
  private DNCEngine dncEngine;

  @Mock
  private DNCService dncService;

  @Mock
  private CampaignDbOperations campaignDbOperations;

  @Mock
  private StopConditionChecker stopConditionChecker;

  @Mock
  private DNCPhoneNumberService dncPhoneNumberService;

  @Mock
  private CampaignService campaignService;

  @Mock
  private CDRService cdrService;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private LoggerDecorator loggerDecorator;

  // @InjectMocks, DialerFacade mock'unu DNCEngine'in lazy-lookup field'ına
  // tip eşleşmesiyle inject eder; ApplicationContext stub'ına gerek kalmaz.
  @Mock
  private DialerFacade dialerFacade;

  @Mock
  private CampaignContext campaignContext;

  @Mock
  private CallProducer callProducer;

  @Mock
  private CallConsumer callConsumer;

  private Campaign runningCampaign;
  private DNC campaignDnc;
  private PriorityBlockingQueue<Call> dataQueue;
  private LinkedBlockingDeque<Call> callQueue;

  @Before
  public void init() {
    Tenant tenant = new Tenant();
    tenant.setId(TENANT_ID);

    campaignDnc = new DNC();
    campaignDnc.setId(CAMPAIGN_DNC_LIST_ID);
    campaignDnc.setEnabled(true);
    campaignDnc.setDeleted(false);
    campaignDnc.setGlobal(false);
    campaignDnc.setTenant(tenant);

    DNCPhoneNumber existingPhone = new DNCPhoneNumber();
    existingPhone.setId(1L);
    existingPhone.setPhoneNumber("5550009999");
    existingPhone.setDncList(campaignDnc);
    existingPhone.setEnabled(true);
    existingPhone.setDeleted(false);
    campaignDnc.setPhoneNumbers(Collections.singletonList(existingPhone));

    when(dncService.getGlobalDnc(true)).thenReturn(Collections.emptyList());
    when(dncService.getDncForCampaign(CAMPAIGN_ID)).thenReturn(Collections.singletonList(campaignDnc));
    when(dncPhoneNumberService.getByListId(CAMPAIGN_DNC_LIST_ID)).thenReturn(campaignDnc.getPhoneNumbers());
    Mockito.lenient().doNothing().when(cdrService).save(any());

    runningCampaign = new Campaign();
    runningCampaign.setId(CAMPAIGN_ID);
    runningCampaign.setDncEnabled(true);
    runningCampaign.setTenant(tenant);
    runningCampaign.getPhoneNumberColumns().add(new PhoneNumberColumn());

    dncEngine.init();
    dncEngine.addRunningCampaign(runningCampaign);

    dataQueue = new PriorityBlockingQueue<>(11, Comparator.comparing(Call::getId));
    callQueue = new LinkedBlockingDeque<>();

    Map<Long, CampaignContext> contexts = new HashMap<>();
    contexts.put(CAMPAIGN_ID, campaignContext);
    when(dialerFacade.getCampaignContexts()).thenReturn(contexts);
    when(campaignContext.getCampaign()).thenReturn(runningCampaign);
    when(campaignContext.getCallProducer()).thenReturn(callProducer);
    when(campaignContext.getCallConsumer()).thenReturn(callConsumer);
    when(callProducer.getDataQueue()).thenReturn(dataQueue);
    when(callConsumer.getCallQueue()).thenReturn(callQueue);
  }

  @Test
  public void dropsMatchingCallsFromBothQueuesAndFinalizesThemAsDncFiltered() throws Exception {
    Call dataQueueBlocked = callWithNumber("dl-a", BLOCKED_NUMBER);
    Call dataQueueKept = callWithNumber("dl-b", "5550000000");
    Call callQueueBlocked = callWithNumber("dl-c", BLOCKED_NUMBER);
    Call callQueueKept = callWithNumber("dl-d", "5557777777");

    dataQueue.addAll(Arrays.asList(dataQueueBlocked, dataQueueKept));
    callQueue.addAll(Arrays.asList(callQueueBlocked, callQueueKept));

    dncEngine.dropQueuedCallsForNumbers(campaignDnc, new HashSet<>(Collections.singletonList(BLOCKED_NUMBER)));

    assertFalse(dataQueue.contains(dataQueueBlocked));
    assertTrue(dataQueue.contains(dataQueueKept));
    assertFalse(callQueue.contains(callQueueBlocked));
    assertTrue(callQueue.contains(callQueueKept));

    ArgumentCaptor<Collection<Call>> droppedCaptor = ArgumentCaptor.forClass((Class) Collection.class);
    verify(campaignDbOperations).setFilteredByDncListStatus(eq(runningCampaign), droppedCaptor.capture());
    Collection<Call> dropped = droppedCaptor.getValue();
    assertEquals(2, dropped.size());
    assertTrue(dropped.contains(dataQueueBlocked));
    assertTrue(dropped.contains(callQueueBlocked));

    verify(cdrService, times(2)).save(any());
  }

  @Test
  public void emptyBlockedNumbersIsNoOp() throws Exception {
    Call kept = callWithNumber("dl-a", BLOCKED_NUMBER);
    dataQueue.add(kept);

    dncEngine.dropQueuedCallsForNumbers(campaignDnc, Collections.emptySet());

    assertTrue(dataQueue.contains(kept));
    verify(campaignDbOperations, times(0)).setFilteredByDncListStatus(any(), any());
  }

  private Call callWithNumber(String id, String number) {
    Call call = new Call();
    call.setId(id);
    call.setUniqueKey(id);
    call.setCalledNumber(number);
    call.setCampaign(runningCampaign);
    call.setResultCodeSequence(0);
    call.setRawData(new JSONObject());
    return call;
  }
}
