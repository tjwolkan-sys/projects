package com.powerdialer.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Sets;
import com.powerdialer.util.LoggerDecorator;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.DNCPhoneNumber;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.Tenant;
import com.powerdialer.service.CDRService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.DNCService;

/**
 * Test for DNCEngine class
 */
@RunWith(MockitoJUnitRunner.class)
public class DNCEngineTest {

  private static final long TENANT_ID = 9999L;
  private static final long CAMPAIGN_ID = 11L;
  private static final long CAMPAIGN_DNC_LIST_ID = 2L;
  private static final String CAMPAIGN_DNC_PHONE_NUMBER = "231";
  private static final long CAMPAIGN_DNC_NUMBER_ID = 20L;
  private static final long GLOBAL_DNC_LIST_ID = 1L;
  private static final String GLOBAL_DNC_PHONE_NUMBER = "123";
  private static final long GLOBAL_DNC_NUMBER_ID = 10L;

  private DNC globalDnc;
  private DNC campaignDnc;

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
  private CDRService cdrService;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private LoggerDecorator loggerDecorator;

  private Tenant tenant = new Tenant() {
    {
      setId(TENANT_ID);
    }
  };

  /**
   * Global DNC list
   */
  private List<DNC> globalDncs = new ArrayList<DNC>() {
    {
      globalDnc = new DNC();
      globalDnc.setTenant(tenant);
      globalDnc.setId(GLOBAL_DNC_LIST_ID);
      globalDnc.setEnabled(true);
      globalDnc.setDeleted(false);
      globalDnc.setGlobal(true);
      DNCPhoneNumber dncPhoneNumber = new DNCPhoneNumber();
      dncPhoneNumber.setId(GLOBAL_DNC_NUMBER_ID);
      dncPhoneNumber.setPhoneNumber(GLOBAL_DNC_PHONE_NUMBER);
      dncPhoneNumber.setDncList(globalDnc);
      dncPhoneNumber.setEnabled(true);
      dncPhoneNumber.setDeleted(false);
      globalDnc.setPhoneNumbers(Collections.singletonList(dncPhoneNumber));
      add(globalDnc);
    }
  };

  /**
   * Campaign DNC list
   */
  private List<DNC> campaignDncs = new ArrayList<DNC>() {
    {
      campaignDnc = new DNC();
      campaignDnc.setId(CAMPAIGN_DNC_LIST_ID);
      campaignDnc.setEnabled(true);
      campaignDnc.setDeleted(false);
      campaignDnc.setGlobal(true);
      DNCPhoneNumber dncPhoneNumber = new DNCPhoneNumber();
      dncPhoneNumber.setId(CAMPAIGN_DNC_NUMBER_ID);
      dncPhoneNumber.setPhoneNumber(CAMPAIGN_DNC_PHONE_NUMBER);
      dncPhoneNumber.setDncList(campaignDnc);
      dncPhoneNumber.setEnabled(true);
      dncPhoneNumber.setDeleted(false);
      campaignDnc.setPhoneNumbers(Collections.singletonList(dncPhoneNumber));
      add(campaignDnc);
    }
  };

  private Campaign runningCampaign;

  /**
   * Init mocks and runningCampaign
   */
  @Before
  public void init() {
    when(dncService.getGlobalDnc(true)).thenReturn(globalDncs);
    when(dncService.getDncForCampaign(CAMPAIGN_ID)).thenReturn(campaignDncs);
    lenient().when(dncService.getWithInitializedPhoneNumbers(GLOBAL_DNC_LIST_ID)).thenReturn(globalDnc);
    lenient().when(dncService.getDNCByDncPhoneNumberIdWithInitializedTenant(GLOBAL_DNC_LIST_ID)).thenReturn(globalDnc);
    when(dncPhoneNumberService.getByListId(CAMPAIGN_DNC_LIST_ID)).thenReturn(campaignDnc.getPhoneNumbers());
    when(dncPhoneNumberService.getByListId(GLOBAL_DNC_LIST_ID)).thenReturn(globalDnc.getPhoneNumbers());
    when(dncPhoneNumberService.findActivePhoneNumbersByDncIdsAndPhoneNumbers(any(), any(), any(Date.class)))
        .thenAnswer(invocation -> new HashSet<>((Collection<String>) invocation.getArgument(1)));
    Mockito.doNothing().when(cdrService).save(any());
    runningCampaign = new Campaign();
    runningCampaign.setId(CAMPAIGN_ID);
    runningCampaign.setDncEnabled(true);
    runningCampaign.setTenant(tenant);
    runningCampaign.getPhoneNumberColumns().add(new PhoneNumberColumn());
    dncEngine.init();
    dncEngine.addRunningCampaign(runningCampaign);
  }

  /**
   * Test filtering by global DNC list
   */
  @Test
  public void testGlobalDNC() {
    Call call1 = new Call();
    call1.setId("1");
    call1.setCalledNumber("213");
    call1.setCampaign(runningCampaign);
    call1.setResultCodeSequence(0);
    call1.setRawData(new JSONObject());
    Call call2 = new Call();
    call2.setId("2");
    call2.setCalledNumber(GLOBAL_DNC_PHONE_NUMBER);
    call2.setCampaign(runningCampaign);
    call2.setResultCodeSequence(0);
    call2.setRawData(new JSONObject());
    List<Call> calls = new ArrayList<Call>() {
      {
        add(call1);
        add(call2);
      }
    };
    dncEngine.applyDncToCalls(runningCampaign, calls);
    assertTrue(calls.contains(call1));
    assertFalse(calls.contains(call2));
  }

  /**
   * Test filtering by campaign DNC list
   */
  @Test
  public void testCampaignDNC() {
    Call call1 = new Call();
    call1.setId("1");
    call1.setCalledNumber("999");
    call1.setCampaign(runningCampaign);
    call1.setResultCodeSequence(0);
    call1.setRawData(new JSONObject());

    Call call2 = new Call();
    call2.setId("2");
    call2.setCalledNumber(CAMPAIGN_DNC_PHONE_NUMBER);
    call2.setCampaign(runningCampaign);
    call2.setResultCodeSequence(0);
    call2.setRawData(new JSONObject());
    List<Call> calls = new ArrayList<Call>() {
      {
        add(call1);
        add(call2);
      }
    };
    dncEngine.applyDncToCalls(runningCampaign, calls);
    assertTrue(calls.contains(call1));
    assertFalse(calls.contains(call2));
  }

  /**
   * Test updating global DNC
   */
  @Test
  public void testUpdateGlobalDNC() {
    globalDncs.get(0).setEnabled(false);
    dncEngine.updateDnc(GLOBAL_DNC_LIST_ID);

    Call call1 = new Call();
    call1.setId("1");
    call1.setCalledNumber(GLOBAL_DNC_PHONE_NUMBER);
    call1.setCampaign(runningCampaign);

    List<Call> calls = new ArrayList<Call>() {
      {
        add(call1);
      }
    };
    dncEngine.applyDncToCalls(runningCampaign, calls);
    assertTrue(calls.contains(call1));
  }

  /**
   * Test add global DNC's phone number
   */
  @Test
  public void testAddDncPhoneNumber() {
    when(dncService.getDNCByDncPhoneNumberIdWithInitializedTenant(3L)).thenReturn(globalDncs.get(0));
    DNCPhoneNumber dncPhoneNumber = new DNCPhoneNumber();
    dncPhoneNumber.setId(3L);
    dncPhoneNumber.setEnabled(true);
    dncPhoneNumber.setDeleted(false);
    dncPhoneNumber.setPhoneNumber("999");
    dncEngine.updatePhoneNumberInDnc(dncPhoneNumber);

    Call call1 = new Call();
    call1.setId("1");
    call1.setCalledNumber("111");
    call1.setCampaign(runningCampaign);
    call1.setResultCodeSequence(0);
    call1.setRawData(new JSONObject());

    Call call999 = new Call();
    call999.setId("999");
    call999.setCalledNumber("999");
    call999.setCampaign(runningCampaign);
    call999.setResultCodeSequence(0);
    call999.setRawData(new JSONObject());

    List<Call> calls = new ArrayList<Call>() {
      {
        add(call1);
        add(call999);
      }
    };
    dncEngine.applyDncToCalls(runningCampaign, calls);

    assertTrue(calls.contains(call1));
    assertFalse(calls.contains(call999));
  }

  /**
   * Test delete global DNC's phone number
   */
  @Test
  public void testDeleteDncPhoneNumber() {
    DNCPhoneNumber dncPhoneNumber = globalDncs.get(0).getPhoneNumbers().get(0);
    dncPhoneNumber.setDeleted(true);
    dncEngine.updatePhoneNumberInDnc(dncPhoneNumber);

    Call call = new Call();
    call.setId("1");
    call.setCalledNumber("123");
    call.setCampaign(runningCampaign);
    List<Call> calls = new ArrayList<Call>() {
      {
        add(call);
      }
    };
    dncEngine.applyDncToCalls(runningCampaign, calls);

    assertTrue(calls.contains(call));
  }

  /**
   * Test remove campaign from {@link DNCEngine}
   */
  @Test
  public void testRemoveCampaign() {
    assertTrue(dncEngine.removeRunningCampaign(runningCampaign));
    assertFalse(dncEngine.removeRunningCampaign(runningCampaign));
  }

  /**
   * Test add campaign to {@link DNCEngine}
   */
  @Test
  public void testAddCampaign() {
    assertFalse(dncEngine.addRunningCampaign(runningCampaign));
  }

  /**
   * Test DNC expire date
   * @throws InterruptedException
   */
  @Test
  public void testExpiringDnc() throws InterruptedException {
    globalDnc.setExpireDate(new Date(System.currentTimeMillis() + 3000));
    dncEngine.updateDnc(GLOBAL_DNC_LIST_ID);

    Call call = new Call();
    call.setId("999");
    call.setCalledNumber(GLOBAL_DNC_PHONE_NUMBER);
    call.setCampaign(runningCampaign);
    call.setResultCodeSequence(0);
    call.setRawData(new JSONObject());
    List<Call> calls = new ArrayList<Call>() {
      {
        add(call);
      }
    };
    dncEngine.applyDncToCalls(runningCampaign, calls);
    assertFalse(calls.contains(call));

    globalDnc.setExpireDate(new Date(System.currentTimeMillis() - 1));

    calls.clear();
    calls.add(call);

    dncEngine.updateDnc(GLOBAL_DNC_LIST_ID);
    dncEngine.applyDncToCalls(runningCampaign, calls);
    assertTrue(calls.contains(call));
  }

  @Test
  public void givenPhoneExpireDateWhenListExpiredThenPhoneExpireDateTakesPriority() {
    globalDnc.setExpireDate(new Date(System.currentTimeMillis() - 1));
    globalDnc.getPhoneNumbers().get(0).setExpiredDate(new Date(System.currentTimeMillis() + 3000));
    dncEngine.updateDnc(GLOBAL_DNC_LIST_ID);

    Call call = new Call();
    call.setId("999");
    call.setCalledNumber(GLOBAL_DNC_PHONE_NUMBER);
    call.setCampaign(runningCampaign);
    call.setResultCodeSequence(0);
    call.setRawData(new JSONObject());
    List<Call> calls = new ArrayList<Call>() {
      {
        add(call);
      }
    };

    dncEngine.applyDncToCalls(runningCampaign, calls);

    assertFalse(calls.contains(call));
  }

  @Test
  public void givenIncrementalPhoneUpdateWithoutPhoneExpireDateWhenListExpiredThenCallShouldNotBeFiltered() {
    globalDnc.setExpireDate(new Date(System.currentTimeMillis() - 1));
    when(dncService.getDNCByDncPhoneNumberIdWithInitializedTenant(3L)).thenReturn(globalDnc);

    DNCPhoneNumber dncPhoneNumber = new DNCPhoneNumber();
    dncPhoneNumber.setId(3L);
    dncPhoneNumber.setEnabled(true);
    dncPhoneNumber.setDeleted(false);
    dncPhoneNumber.setPhoneNumber("999");
    dncEngine.updatePhoneNumberInDnc(dncPhoneNumber);

    Call call = new Call();
    call.setId("999");
    call.setCalledNumber("999");
    call.setCampaign(runningCampaign);
    call.setResultCodeSequence(0);
    call.setRawData(new JSONObject());

    List<Call> calls = new ArrayList<Call>() {
      {
        add(call);
      }
    };

    dncEngine.applyDncToCalls(runningCampaign, calls);

    assertTrue(calls.contains(call));
  }

  @Test
  public void givenCallWhenCalledNumberInDNCNumbersThenRemove() {
    Call call = new Call();
    call.setCalledNumber("NUMBER_IN_DNC");
    call.setDNCNumbers(Sets.newHashSet("NUMBER_IN_DNC"));
    call.setCampaign(runningCampaign);
    call.setResultCodeSequence(0);
    call.setRawData(new JSONObject());
    List calls = new ArrayList<Call>() {
      {
        add(call);
      }
    };
    dncEngine.applyDncToCalls(runningCampaign, calls);
    assertTrue(calls.isEmpty());
  }

  @Test
  public void givenCachedDncNumberExpiredInDbWhenRevalidatedThenCallShouldNotBeFiltered() throws Exception {
    when(dncPhoneNumberService.findActivePhoneNumbersByDncIdsAndPhoneNumbers(any(), any(), any(Date.class)))
        .thenReturn(Collections.emptySet());

    Call call = new Call();
    call.setId("1");
    call.setCalledNumber(GLOBAL_DNC_PHONE_NUMBER);
    call.setCampaign(runningCampaign);
    call.setResultCodeSequence(0);
    call.setRawData(new JSONObject());

    List<Call> calls = new ArrayList<Call>() {
      {
        add(call);
      }
    };

    dncEngine.applyDncToCalls(runningCampaign, calls);

    assertTrue(calls.contains(call));
    verify(campaignDbOperations, never()).setFilteredByDncListStatus(any(), any());
  }

  @Test
  public void givenStaleDncNumbersColumnWhenDbDoesNotConfirmDncThenCallShouldNotBeFiltered() throws Exception {
    when(dncPhoneNumberService.findActivePhoneNumbersByDncIdsAndPhoneNumbers(any(), any(), any(Date.class)))
        .thenReturn(Collections.emptySet());

    Call call = new Call();
    call.setId("1");
    call.setCalledNumber("STALE_DNC_NUMBER");
    call.setDNCNumbers(Sets.newHashSet("STALE_DNC_NUMBER"));
    call.setCampaign(runningCampaign);
    call.setResultCodeSequence(0);
    call.setRawData(new JSONObject());

    List<Call> calls = new ArrayList<Call>() {
      {
        add(call);
      }
    };

    dncEngine.applyDncToCalls(runningCampaign, calls);

    assertTrue(calls.contains(call));
    assertFalse(call.getDNCNumbers().contains("STALE_DNC_NUMBER"));
    verify(campaignDbOperations, never()).setFilteredByDncListStatus(any(), any());
  }

  @Test
  public void givenExpiredDncNumberReactivatedInDbWhenRefreshTtlElapsedThenCallShouldBeFiltered() throws Exception {
    DNCPhoneNumber globalPhoneNumber = globalDnc.getPhoneNumbers().get(0);
    globalPhoneNumber.setExpiredDate(new Date(System.currentTimeMillis() - 1));
    dncEngine.updateDnc(GLOBAL_DNC_LIST_ID);

    Call expiredCall = callWithNumber(GLOBAL_DNC_PHONE_NUMBER);
    List<Call> expiredCalls = new ArrayList<Call>() {
      {
        add(expiredCall);
      }
    };
    dncEngine.applyDncToCalls(runningCampaign, expiredCalls);
    assertTrue(expiredCalls.contains(expiredCall));

    globalPhoneNumber.setExpiredDate(new Date(System.currentTimeMillis() + 60_000));
    forceDncCacheRefresh();

    Call reactivatedCall = callWithNumber(GLOBAL_DNC_PHONE_NUMBER);
    List<Call> reactivatedCalls = new ArrayList<Call>() {
      {
        add(reactivatedCall);
      }
    };

    dncEngine.applyDncToCalls(runningCampaign, reactivatedCalls);

    assertFalse(reactivatedCalls.contains(reactivatedCall));
  }

  private Call callWithNumber(String number) {
    Call call = new Call();
    call.setId(number);
    call.setCalledNumber(number);
    call.setCampaign(runningCampaign);
    call.setResultCodeSequence(0);
    call.setRawData(new JSONObject());
    return call;
  }

  private void forceDncCacheRefresh() throws Exception {
    Field refreshTimesField = DNCEngine.class.getDeclaredField("dncCacheRefreshTimestamps");
    refreshTimesField.setAccessible(true);
    Map<Long, Long> refreshTimes = new HashMap<>();
    refreshTimes.put(CAMPAIGN_ID, 0L);
    refreshTimes.put(Long.MIN_VALUE, 0L);
    refreshTimesField.set(dncEngine, refreshTimes);
  }

}
