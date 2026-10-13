package tests.dnc;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
 * Hot-drop yalnız tetiklenen DNC listesini fiilen kullanan kampanyaların
 * kuyruklarını tarar; DNC'yi paylaşmayan kampanyanın aynı numaralı çağrısı
 * kuyrukta kalır ve o kampanya için hiçbir persist çağrısı yapılmaz.
 */
@RunWith(MockitoJUnitRunner.class)
public class DncEngineQueueDropRespectsDncListSharingTest {

  private static final long TENANT_ID = 9999L;
  private static final long DNC_CAMPAIGN_ID = 318332L;
  private static final long OTHER_CAMPAIGN_ID = 555L;
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
  private CampaignContext dncCampaignContext;

  @Mock
  private CallProducer dncCampaignProducer;

  @Mock
  private CallConsumer dncCampaignConsumer;

  @Mock
  private CampaignContext otherCampaignContext;

  @Mock
  private CallProducer otherCampaignProducer;

  @Mock
  private CallConsumer otherCampaignConsumer;

  private Campaign dncCampaign;
  private Campaign otherCampaign;
  private DNC campaignDnc;
  private PriorityBlockingQueue<Call> otherDataQueue;
  private LinkedBlockingDeque<Call> otherCallQueue;

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
    when(dncService.getDncForCampaign(DNC_CAMPAIGN_ID)).thenReturn(Collections.singletonList(campaignDnc));
    when(dncService.getDncForCampaign(OTHER_CAMPAIGN_ID)).thenReturn(Collections.emptyList());
    when(dncPhoneNumberService.getByListId(CAMPAIGN_DNC_LIST_ID)).thenReturn(campaignDnc.getPhoneNumbers());

    dncCampaign = campaign(DNC_CAMPAIGN_ID, tenant);
    otherCampaign = campaign(OTHER_CAMPAIGN_ID, tenant);

    dncEngine.init();
    dncEngine.addRunningCampaign(dncCampaign);
    dncEngine.addRunningCampaign(otherCampaign);

    PriorityBlockingQueue<Call> dncDataQueue = new PriorityBlockingQueue<>(11, Comparator.comparing(Call::getId));
    LinkedBlockingDeque<Call> dncCallQueue = new LinkedBlockingDeque<>();
    otherDataQueue = new PriorityBlockingQueue<>(11, Comparator.comparing(Call::getId));
    otherCallQueue = new LinkedBlockingDeque<>();

    Map<Long, CampaignContext> contexts = new HashMap<>();
    contexts.put(DNC_CAMPAIGN_ID, dncCampaignContext);
    contexts.put(OTHER_CAMPAIGN_ID, otherCampaignContext);
    when(dialerFacade.getCampaignContexts()).thenReturn(contexts);

    when(dncCampaignContext.getCampaign()).thenReturn(dncCampaign);
    org.mockito.Mockito.lenient().when(dncCampaignContext.getCallProducer()).thenReturn(dncCampaignProducer);
    org.mockito.Mockito.lenient().when(dncCampaignContext.getCallConsumer()).thenReturn(dncCampaignConsumer);
    org.mockito.Mockito.lenient().when(dncCampaignProducer.getDataQueue()).thenReturn(dncDataQueue);
    org.mockito.Mockito.lenient().when(dncCampaignConsumer.getCallQueue()).thenReturn(dncCallQueue);

    when(otherCampaignContext.getCampaign()).thenReturn(otherCampaign);
    org.mockito.Mockito.lenient().when(otherCampaignContext.getCallProducer()).thenReturn(otherCampaignProducer);
    org.mockito.Mockito.lenient().when(otherCampaignContext.getCallConsumer()).thenReturn(otherCampaignConsumer);
    org.mockito.Mockito.lenient().when(otherCampaignProducer.getDataQueue()).thenReturn(otherDataQueue);
    org.mockito.Mockito.lenient().when(otherCampaignConsumer.getCallQueue()).thenReturn(otherCallQueue);
  }

  @Test
  public void doesNotTouchQueuesOfCampaignNotUsingTheDnc() throws Exception {
    Call unrelatedDataCall = callWithNumber("dl-x", BLOCKED_NUMBER, otherCampaign);
    Call unrelatedQueuedCall = callWithNumber("dl-y", BLOCKED_NUMBER, otherCampaign);
    otherDataQueue.add(unrelatedDataCall);
    otherCallQueue.add(unrelatedQueuedCall);

    dncEngine.dropQueuedCallsForNumbers(campaignDnc, new HashSet<>(Collections.singletonList(BLOCKED_NUMBER)));

    assertTrue("DNC'yi kullanmayan kampanyanın dataQueue'su korunmalı", otherDataQueue.contains(unrelatedDataCall));
    assertTrue("DNC'yi kullanmayan kampanyanın callQueue'su korunmalı", otherCallQueue.contains(unrelatedQueuedCall));
    verify(campaignDbOperations, never()).setFilteredByDncListStatus(eq(otherCampaign), any());
    verify(cdrService, never()).save(any());
  }

  private Campaign campaign(long id, Tenant tenant) {
    Campaign campaign = new Campaign();
    campaign.setId(id);
    campaign.setDncEnabled(true);
    campaign.setTenant(tenant);
    campaign.getPhoneNumberColumns().add(new PhoneNumberColumn());
    return campaign;
  }

  private Call callWithNumber(String id, String number, Campaign campaign) {
    Call call = new Call();
    call.setId(id);
    call.setUniqueKey(id);
    call.setCalledNumber(number);
    call.setCampaign(campaign);
    call.setResultCodeSequence(0);
    call.setRawData(new JSONObject());
    return call;
  }
}
