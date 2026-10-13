package com.powerdialer.engine;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.net.SocketTimeoutException;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.powermock.reflect.Whitebox;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.web.client.RestTemplate;

import com.powerdialer.dto.LSCAuthFormDTO;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.factory.RestOperationsFactory;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.LSC;
import com.powerdialer.model.LSCAuthorization;
import com.powerdialer.service.LSCAuthService;
import com.powerdialer.service.LSCService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.LSC_RESULT;
import com.powerdialer.util.LoggerDecorator;

/**
 * Test for LSCEngine class
 */
@RunWith(MockitoJUnitRunner.class)
public class LSCEngineTest {

  @InjectMocks
  @Spy
  private LSCEngine lscEngine;

  @Mock
  private LSCService lscService;

  @Mock
  private CampaignDbOperations campaignDbOperations;

  @Mock
  private RestOperationsFactory restOperationsFactory;

  @Mock
  private LSCAuthService lscAuthService;

  @Spy
  private RestTemplate defaultRestTemplate;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private LoggerDecorator loggerDecorator;

  private Campaign runningCampaign = new Campaign();

  private Campaign runningCampaign2 = new Campaign();

  @Before
  public void init() {
    runningCampaign.setId(1L);
    runningCampaign.setLscEnabled(true);

    runningCampaign2.setId(2L);
    runningCampaign2.setLscEnabled(true);
    when(restOperationsFactory.build(anyInt())).thenReturn(defaultRestTemplate);
  }

  /**
   * clear running campaigns and its LSCs
   */
  @After
  public void tearDown() {
    lscEngine.removeRunningCampaign(runningCampaign);
    lscEngine.removeRunningCampaign(runningCampaign2);
  }

  /**
   * Test successful LSC call GET request with JSON response
   */
  @Test
  public void testPassGetJsonLsc() {
    LSC getJsonLsc = new LSC();
    getJsonLsc.setId(1L);
    getJsonLsc.setUrl("http://lsc.test.com/get/{testVar}");
    getJsonLsc.setDeleted(false);
    getJsonLsc.setEnabled(true);
    getJsonLsc.setReplyKey("$.status");
    getJsonLsc.setReplyResult("1");
    getJsonLsc.setReplyType(Constants.REPLY_TYPE.JSON);
    getJsonLsc.setHttpMethod(Constants.HTTP_METHOD.GET);
    getJsonLsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign)));
    lenient().when(lscService.getWithInitializedCampaigns(1L)).thenReturn(getJsonLsc);
    when(lscService.getLscsForCampaign(1L, true)).thenReturn(Collections.singletonList(getJsonLsc));

    lscEngine.addRunningCampaign(runningCampaign);
    Call call = new Call();
    call.setCampaign(runningCampaign);
    call.setRawData(new JSONObject("{\"testVar\":124}"));
    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/get/124")).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("{\"status\":1}", MediaType.APPLICATION_JSON));

    assertThat(lscEngine.applyLscsToCall(call), is(equalTo(LSC_RESULT.OK)));
    mockRestServiceServer.verify();
  }

  /**
   * FIX-333607: LSC cevabında reply key değeri yoksa (null) JsonPath null döner ve
   * {@code toString()} NPE atıyordu (02.10.2026'da 15 ERROR). Sonuç mevcut davranışla aynı kalır
   * ({@code FAIL}); NPE ve ERROR stack yerine anlamlı bir WARN basılır.
   */
  @Test
  public void missingJsonReplyValueFailsWithWarnInsteadOfNpe() {
    LSC getJsonLsc = new LSC();
    getJsonLsc.setId(1L);
    getJsonLsc.setUrl("http://lsc.test.com/get/{testVar}");
    getJsonLsc.setDeleted(false);
    getJsonLsc.setEnabled(true);
    getJsonLsc.setReplyKey("$.status");
    getJsonLsc.setReplyResult("1");
    getJsonLsc.setReplyType(Constants.REPLY_TYPE.JSON);
    getJsonLsc.setHttpMethod(Constants.HTTP_METHOD.GET);
    getJsonLsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign)));
    lenient().when(lscService.getWithInitializedCampaigns(1L)).thenReturn(getJsonLsc);
    when(lscService.getLscsForCampaign(1L, true)).thenReturn(Collections.singletonList(getJsonLsc));

    lscEngine.addRunningCampaign(runningCampaign);
    Call call = new Call();
    call.setCampaign(runningCampaign);
    call.setRawData(new JSONObject("{\"testVar\":124}"));
    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/get/124")).andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"status\":null}", MediaType.APPLICATION_JSON));

    assertThat(lscEngine.applyLscsToCall(call), is(equalTo(LSC_RESULT.FAIL)));

    LoggerDecorator lscLog = loggerDecorator.getLogger(LSCEngine.class);
    boolean jsonPathErrorLogged = Mockito.mockingDetails(lscLog).getInvocations().stream()
        .anyMatch(inv -> "error".equals(inv.getMethod().getName())
            && String.valueOf(inv.getArguments()[0]).contains("can't be evaluated"));
    boolean replyKeyWarnLogged = Mockito.mockingDetails(lscLog).getInvocations().stream()
        .anyMatch(inv -> "warn".equals(inv.getMethod().getName())
            && String.valueOf(inv.getArguments()[0]).contains("reply key"));
    assertThat("Eksik reply değeri ERROR/stack üretmemeli", jsonPathErrorLogged, is(false));
    assertThat("Eksik reply değeri anlamlı WARN ile loglanmalı", replyKeyWarnLogged, is(true));
  }

  /**
   * Task 325469: Payload iceren "LSC URL" satiri info seviyesinde
   * loglanmamali (logback yuku), audit ile loglanmali (ActionLog korunur);
   * ozet "LSC(s) result is" satiri info olarak kalmali.
   */
  @Test
  public void testLscPayloadLogsAreAuditNotInfoLevel() {
    LSC getJsonLsc = new LSC();
    getJsonLsc.setId(1L);
    getJsonLsc.setUrl("http://lsc.test.com/get/{testVar}");
    getJsonLsc.setDeleted(false);
    getJsonLsc.setEnabled(true);
    getJsonLsc.setReplyKey("$.status");
    getJsonLsc.setReplyResult("1");
    getJsonLsc.setReplyType(Constants.REPLY_TYPE.JSON);
    getJsonLsc.setHttpMethod(Constants.HTTP_METHOD.GET);
    getJsonLsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign)));
    lenient().when(lscService.getWithInitializedCampaigns(1L)).thenReturn(getJsonLsc);
    when(lscService.getLscsForCampaign(1L, true)).thenReturn(Collections.singletonList(getJsonLsc));

    lscEngine.addRunningCampaign(runningCampaign);
    Call call = new Call();
    call.setCampaign(runningCampaign);
    call.setRawData(new JSONObject("{\"testVar\":124}"));
    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/get/124")).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("{\"status\":1}", MediaType.APPLICATION_JSON));

    assertThat(lscEngine.applyLscsToCall(call), is(equalTo(LSC_RESULT.OK)));

    LoggerDecorator lscLog = loggerDecorator.getLogger(LSCEngine.class);
    boolean payloadInfoLogged = Mockito.mockingDetails(lscLog).getInvocations().stream()
        .filter(invocation -> "info".equals(invocation.getMethod().getName()))
        .anyMatch(invocation -> String.valueOf((Object) invocation.getArgument(0)).startsWith("LSC URL"));
    boolean payloadAuditLogged = Mockito.mockingDetails(lscLog).getInvocations().stream()
        .filter(invocation -> "audit".equals(invocation.getMethod().getName()))
        .anyMatch(invocation -> String.valueOf((Object) invocation.getArgument(0)).startsWith("LSC URL"));
    boolean summaryInfoLogged = Mockito.mockingDetails(lscLog).getInvocations().stream()
        .filter(invocation -> "info".equals(invocation.getMethod().getName()))
        .anyMatch(invocation -> String.valueOf((Object) invocation.getArgument(0)).startsWith("LSC(s) result is"));
    assertFalse("Payload satiri info loglanmamali (logback yuku)", payloadInfoLogged);
    assertTrue("Payload satiri audit loglanmali (ActionLog korunur)", payloadAuditLogged);
    assertTrue("Ozet satiri info kalmali", summaryInfoLogged);
  }

  /**
   * Test successful LSC call POST request with JSON response
   */
  @Test
  public void testPassPostJsonLsc() {
    LSC postJsonLsc = new LSC();
    postJsonLsc.setId(2L);
    postJsonLsc.setUrl("http://lsc.test.com/post/{testVar}");
    postJsonLsc.setDeleted(false);
    postJsonLsc.setEnabled(true);
    postJsonLsc.setReplyKey("$.status");
    postJsonLsc.setReplyResult("1");
    postJsonLsc.setPayload("[{\"key\":\"testVar\",\"value\":\"{testVar}\"}]");
    postJsonLsc.setHeaders("[{\"key\":\"testHeaderKey\",\"value\":\"testHeaderValue\"}]");
    postJsonLsc.setRequestTimeout(50);
    postJsonLsc.setHttpMethod(Constants.HTTP_METHOD.POST);
    postJsonLsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign2)));
    postJsonLsc.setReplyType(Constants.REPLY_TYPE.JSON);

    lenient().when(lscService.getWithInitializedCampaigns(2L)).thenReturn(postJsonLsc);
    when(lscService.getLscsForCampaign(2L, true)).thenReturn(Collections.singletonList(postJsonLsc));

    lscEngine.addRunningCampaign(runningCampaign2);

    Call call = new Call();
    call.setCampaign(runningCampaign2);
    call.setRawData(new JSONObject("{\"testVar\":123}"));
    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/post/123")).andExpect(method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.content().string("{\"testVar\":\"123\"}")).andExpect(MockRestRequestMatchers.header("testHeaderKey", "testHeaderValue"))
        .andRespond(withSuccess("{\"status\":1}", MediaType.APPLICATION_JSON));

    LSC_RESULT ok = lscEngine.applyLscsToCall(call);
    mockRestServiceServer.verify();
    assertThat(ok, is(equalTo(LSC_RESULT.OK)));
  }

  /**
   * Test successful LSC call POST request with PLAIN_TEXT response
   */
  @Test
  public void testPassPostTextLsc() {
    LSC postJsonLsc = new LSC();
    postJsonLsc.setId(2L);
    postJsonLsc.setUrl("http://lsc.test.com/post/{testVar}");
    postJsonLsc.setDeleted(false);
    postJsonLsc.setEnabled(true);
    postJsonLsc.setReplyResult("[1]");
    postJsonLsc.setPayload("[{\"key\":\"testVar\",\"value\":\"{testVar}\"}]");
    postJsonLsc.setRequestTimeout(50);
    postJsonLsc.setHttpMethod(Constants.HTTP_METHOD.POST);
    postJsonLsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign2)));
    postJsonLsc.setReplyType(Constants.REPLY_TYPE.PLAIN_TEXT);

    lenient().when(lscService.getWithInitializedCampaigns(2L)).thenReturn(postJsonLsc);
    when(lscService.getLscsForCampaign(2L, true)).thenReturn(Collections.singletonList(postJsonLsc));

    lscEngine.addRunningCampaign(runningCampaign2);

    Call call = new Call();
    call.setCampaign(runningCampaign2);
    call.setRawData(new JSONObject("{\"testVar\":123}"));
    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/post/123")).andExpect(method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.content().string("{\"testVar\":\"123\"}")).andRespond(withSuccess("1", MediaType.TEXT_PLAIN));

    LSC_RESULT ok = lscEngine.applyLscsToCall(call);
    mockRestServiceServer.verify();
    assertThat(ok, is(equalTo(LSC_RESULT.OK)));
  }

  /**
   * Test successful LSC call POST request with XML response
   */
  @Test
  public void testPassPostXMLLsc() {
    LSC postJsonLsc = new LSC();
    postJsonLsc.setId(2L);
    postJsonLsc.setUrl("http://lsc.test.com/post/{testVar}");
    postJsonLsc.setDeleted(false);
    postJsonLsc.setEnabled(true);
    postJsonLsc.setReplyKey("status/code");
    postJsonLsc.setReplyResult("1");
    postJsonLsc.setPayload("[{\"key\":\"testVar\",\"value\":\"{testVar}\"}]");
    postJsonLsc.setRequestTimeout(50);
    postJsonLsc.setReplyType(Constants.REPLY_TYPE.JSON);
    postJsonLsc.setHttpMethod(Constants.HTTP_METHOD.POST);
    postJsonLsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign2)));
    postJsonLsc.setReplyType(Constants.REPLY_TYPE.XML);

    lenient().when(lscService.getWithInitializedCampaigns(2L)).thenReturn(postJsonLsc);
    when(lscService.getLscsForCampaign(2L, true)).thenReturn(Collections.singletonList(postJsonLsc));

    lscEngine.addRunningCampaign(runningCampaign2);

    Call call = new Call();
    call.setCampaign(runningCampaign2);
    call.setRawData(new JSONObject("{\"testVar\":123}"));
    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/post/123")).andExpect(method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.content().string("{\"testVar\":\"123\"}")).andRespond(withSuccess("<status><code>1</code></status>", MediaType.APPLICATION_XML));

    LSC_RESULT ok = lscEngine.applyLscsToCall(call);
    mockRestServiceServer.verify();
    assertThat(ok, is(equalTo(LSC_RESULT.OK)));
  }

  /**
   * A 5xx response is an infrastructure/access problem (eligibility unknown), so it is treated like a
   * timeout (retryable, subject to LSC_TIMEOUT_BEHAVIOR) rather than a definitive business FAIL.
   */
  @Test
  public void testLSCServerError() {
    LSC postJsonLsc = new LSC();
    postJsonLsc.setId(2L);
    postJsonLsc.setUrl("http://lsc.test.com/post/{testVar}");
    postJsonLsc.setDeleted(false);
    postJsonLsc.setEnabled(true);
    postJsonLsc.setReplyKey("status/code");
    postJsonLsc.setReplyResult("1");
    postJsonLsc.setPayload("[{\"key\":\"testVar\",\"value\":\"{testVar}\"}]");
    postJsonLsc.setRequestTimeout(50);
    postJsonLsc.setReplyType(Constants.REPLY_TYPE.JSON);
    postJsonLsc.setHttpMethod(Constants.HTTP_METHOD.POST);
    postJsonLsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign2)));
    postJsonLsc.setReplyType(Constants.REPLY_TYPE.XML);
    lenient().when(lscService.getWithInitializedCampaigns(2L)).thenReturn(postJsonLsc);
    when(lscService.getLscsForCampaign(2L, true)).thenReturn(Collections.singletonList(postJsonLsc));

    lscEngine.addRunningCampaign(runningCampaign2);
    Call call = new Call();
    call.setCampaign(runningCampaign2);
    call.setRawData(new JSONObject("{\"testVar\":restError}"));
    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/post/restError")).andExpect(method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.content().string("{\"testVar\":\"restError\"}")).andRespond(withServerError());

    assertThat(lscEngine.applyLscsToCall(call), is(equalTo(LSC_RESULT.TIMEOUT)));
    mockRestServiceServer.verify();
  }

  /**
   * Test call LSC service which return 400 response
   */
  @Test
  public void testLSCBadRequest() {
    LSC postJsonLsc = new LSC();
    postJsonLsc.setId(2L);
    postJsonLsc.setUrl("http://lsc.test.com/post/{testVar}");
    postJsonLsc.setDeleted(false);
    postJsonLsc.setEnabled(true);
    postJsonLsc.setReplyKey("status/code");
    postJsonLsc.setReplyResult("1");
    postJsonLsc.setPayload("[{\"key\":\"testVar\",\"value\":\"{testVar}\"}]");
    postJsonLsc.setRequestTimeout(50);
    postJsonLsc.setReplyType(Constants.REPLY_TYPE.JSON);
    postJsonLsc.setHttpMethod(Constants.HTTP_METHOD.POST);
    postJsonLsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign2)));
    postJsonLsc.setReplyType(Constants.REPLY_TYPE.XML);

    lenient().when(lscService.getWithInitializedCampaigns(2L)).thenReturn(postJsonLsc);
    when(lscService.getLscsForCampaign(2L, true)).thenReturn(Collections.singletonList(postJsonLsc));

    lscEngine.addRunningCampaign(runningCampaign2);
    Call call = new Call();
    call.setCampaign(runningCampaign2);
    call.setRawData(new JSONObject("{\"testVar\":restError}"));
    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/post/restError")).andExpect(method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.content().string("{\"testVar\":\"restError\"}")).andRespond(withBadRequest());

    assertThat(lscEngine.applyLscsToCall(call), is(equalTo(LSC_RESULT.FAIL)));
    mockRestServiceServer.verify();
  }

  /**
   * Test updating LSC after campaign started
   */
  @Test
  public void testUpdateLSCForRunningCampaign() {
    LSC getJsonLsc = new LSC();
    getJsonLsc.setId(1L);
    getJsonLsc.setUrl("http://lsc.test.com/get/{testVar}");
    getJsonLsc.setDeleted(false);
    getJsonLsc.setEnabled(true);
    getJsonLsc.setReplyKey("$.status");
    getJsonLsc.setReplyResult("1");
    getJsonLsc.setReplyType(Constants.REPLY_TYPE.JSON);
    getJsonLsc.setHttpMethod(Constants.HTTP_METHOD.GET);
    getJsonLsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign)));
    when(lscService.getWithInitializedCampaigns(1L)).thenReturn(getJsonLsc);
    when(lscService.getLscsForCampaign(1L, true)).thenReturn(Collections.singletonList(getJsonLsc));
    lscEngine.addRunningCampaign(runningCampaign);

    Call call = new Call();
    call.setCampaign(runningCampaign);
    call.setRawData(new JSONObject("{\"testVar\":124}"));
    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/get/124")).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));

    assertThat(lscEngine.applyLscsToCall(call), is(equalTo(LSC_RESULT.FAIL)));
    mockRestServiceServer.verify();

    getJsonLsc.setUrl("http://lsc.test.com/getUpdated/{testVar}");
    lscEngine.updateLSC(getJsonLsc.getId());

    mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/getUpdated/124")).andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"status\":1}", MediaType.APPLICATION_JSON));

    assertThat(lscEngine.applyLscsToCall(call), is(equalTo(LSC_RESULT.OK)));
    mockRestServiceServer.verify();
  }

  /**
   * Test add new running campaign with LSCs
   */
  @Test
  public void testAddRunningCampaign() {
    lscEngine.addRunningCampaign(runningCampaign);

    Collection runningCampaigns = ((Collection) Whitebox.getInternalState(lscEngine, "runningCampaigns"));
    assertTrue(runningCampaigns.contains(runningCampaign));

    Map<Campaign, Set<LSC>> campaignLscsMap = (Map<Campaign, Set<LSC>>) Whitebox.getInternalState(lscEngine, "campaignLscsMap");
    assertTrue(campaignLscsMap.containsKey(runningCampaign));
  }

  @Test
  public void givenCallWhenLSCTimeoutThenReturnTimeoutResult() {
    LSC postJsonLsc = new LSC();
    postJsonLsc.setUrl("ANY_URL");
    postJsonLsc.setHttpMethod(Constants.HTTP_METHOD.POST);
    when(lscService.getLscsForCampaign(anyLong(), anyBoolean())).thenReturn(Collections.singletonList(postJsonLsc));
    lscEngine.addRunningCampaign(runningCampaign);

    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("/ANY_URL")).andExpect(method(HttpMethod.POST)).andRespond(request -> {
      throw new SocketTimeoutException();
    });

    Call call = new Call();
    call.setCampaign(runningCampaign);

    assertThat(lscEngine.applyLscsToCall(call), is(equalTo(LSC_RESULT.TIMEOUT)));
  }


  @Test
  public void givenWildCardsAndFreePayloadEnabledWhenPostThenEvaluateWildcards() {
    LSC lsc = new LSC();
    lsc.setId(2L);
    lsc.setFreePayloadEnabled(true);
    lsc.setUrl("http://lsc.test.com/post/{testVar}");
    lsc.setDeleted(false);
    lsc.setEnabled(true);
    lsc.setReplyKey("$.status");
    lsc.setReplyResult("1");
    lsc.setPayload("{testVar}, {testVar}, exactValue , {testVar}");
    lsc.setHeaders("[{\"key\":\"testHeaderKey\",\"value\":\"testHeaderValue\"}]");
    lsc.setRequestTimeout(50);
    lsc.setHttpMethod(Constants.HTTP_METHOD.POST);
    lsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign2)));
    lsc.setReplyType(Constants.REPLY_TYPE.JSON);

    lenient().when(lscService.getWithInitializedCampaigns(2L)).thenReturn(lsc);
    when(lscService.getLscsForCampaign(2L, true)).thenReturn(Collections.singletonList(lsc));

    lscEngine.addRunningCampaign(runningCampaign2);

    Call call = new Call();
    call.setCampaign(runningCampaign2);
    call.setRawData(new JSONObject("{\"testVar\":123}"));
    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/post/123")).andExpect(method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.content().string("123, 123, exactValue , 123"))
        .andExpect(MockRestRequestMatchers.header("testHeaderKey", "testHeaderValue"))
        .andRespond(withSuccess("{\"status\":1}", MediaType.APPLICATION_JSON));

    LSC_RESULT ok = lscEngine.applyLscsToCall(call);
    mockRestServiceServer.verify();
    assertThat(ok, is(equalTo(LSC_RESULT.OK)));
  }

  @Test
  public void givenWildCardsAndFreePayloadEnabledWhenGetThenEvaluateWildcards() {
    LSC lsc = new LSC();
    lsc.setId(1L);
    lsc.setFreePayloadEnabled(true);
    lsc.setUrl("http://lsc.test.com/get/{testVar}");
    lsc.setDeleted(false);
    lsc.setEnabled(true);
    lsc.setPayload("test");
    lsc.setReplyKey("$.status");
    lsc.setReplyResult("1");
    lsc.setReplyType(Constants.REPLY_TYPE.JSON);
    lsc.setHttpMethod(Constants.HTTP_METHOD.GET);
    lsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign)));
    lenient().when(lscService.getWithInitializedCampaigns(1L)).thenReturn(lsc);
    when(lscService.getLscsForCampaign(1L, true)).thenReturn(Collections.singletonList(lsc));

    lscEngine.addRunningCampaign(runningCampaign);
    Call call = new Call();
    call.setCampaign(runningCampaign);
    call.setRawData(new JSONObject("{\"testVar\":124}"));
    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/get/124"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"status\":1}", MediaType.APPLICATION_JSON));

    assertThat(lscEngine.applyLscsToCall(call), is(equalTo(LSC_RESULT.OK)));
    mockRestServiceServer.verify();
  }

  @Test
  public void givenCallWhenLSCTimeoutAndLSCDialBehaviorThenReturnOK() {
    LSC postJsonLsc = new LSC();
    postJsonLsc.setUrl("ANY_URL");
    postJsonLsc.setHttpMethod(Constants.HTTP_METHOD.POST);
    when(lscService.getLscsForCampaign(anyLong(), anyBoolean())).thenReturn(Collections.singletonList(postJsonLsc));
    lscEngine.addRunningCampaign(runningCampaign);
    runningCampaign.setLscTimeoutBehavior(Constants.LSC_TIMEOUT_BEHAVIOR.DIAL);

    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("/ANY_URL")).andExpect(method(HttpMethod.POST)).andRespond(request -> {
      throw new SocketTimeoutException();
    });

    Call call = new Call();
    call.setCampaign(runningCampaign);

    assertThat(lscEngine.applyLscsToCall(call), is(equalTo(LSC_RESULT.OK)));
  }

  @Test
  public void givenCallWhenLSCTimeoutAndLSCNotDialBehaviorThenReturnTimeoutResult() {
    LSC postJsonLsc = new LSC();
    postJsonLsc.setUrl("ANY_URL");
    postJsonLsc.setHttpMethod(Constants.HTTP_METHOD.POST);
    when(lscService.getLscsForCampaign(anyLong(), anyBoolean())).thenReturn(Collections.singletonList(postJsonLsc));
    lscEngine.addRunningCampaign(runningCampaign);
    runningCampaign.setLscTimeoutBehavior(Constants.LSC_TIMEOUT_BEHAVIOR.NOT_DIAL);

    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("/ANY_URL")).andExpect(method(HttpMethod.POST)).andRespond(request -> {
      throw new SocketTimeoutException();
    });

    Call call = new Call();
    call.setCampaign(runningCampaign);

    assertThat(lscEngine.applyLscsToCall(call), is(equalTo(LSC_RESULT.TIMEOUT)));
  }

  @Test
  public void givenCallWhenLSCWithNoBodyThenReturnFalse() {
    LSC lsc = new LSC();
    lsc.setId(2L);
    lsc.setFreePayloadEnabled(true);
    lsc.setUrl("http://lsc.test.com/post/{testVar}");
    lsc.setDeleted(false);
    lsc.setEnabled(true);
    lsc.setReplyKey("$.status");
    lsc.setReplyResult("1");
    lsc.setPayload("{testVar}, {testVar}, exactValue , {testVar}");
    lsc.setHeaders("[{\"key\":\"testHeaderKey\",\"value\":\"testHeaderValue\"}]");
    lsc.setRequestTimeout(50);
    lsc.setHttpMethod(Constants.HTTP_METHOD.POST);
    lsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign2)));
    lsc.setReplyType(Constants.REPLY_TYPE.JSON);

    lenient().when(lscService.getWithInitializedCampaigns(2L)).thenReturn(lsc);
    when(lscService.getLscsForCampaign(2L, true)).thenReturn(Collections.singletonList(lsc));

    lscEngine.addRunningCampaign(runningCampaign2);

    Call call = new Call();
    call.setCampaign(runningCampaign2);
    call.setRawData(new JSONObject("{\"testVar\":123}"));
    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/post/123")).andExpect(method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.content().string("123, 123, exactValue , 123"))
        .andExpect(MockRestRequestMatchers.header("testHeaderKey", "testHeaderValue"))
        .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

    LSC_RESULT result = lscEngine.applyLscsToCall(call);
    mockRestServiceServer.verify();
    assertThat(result, is(equalTo(LSC_RESULT.FAIL)));
  }

  private static final String BUSINESS_409_BODY =
      "{\"returnCode\":2,\"path\":\"/solext/olmc/v1/check\",\"returnMessage\":\"A warning occured\","
          + "\"error\":\"İlgili kontrat bulunamadı\",\"status\":409,"
          + "\"timestamp\":\"2026-07-02T14:30:43.338118497\"}";

  /**
   * A 409 is a definitive business exception. The call must FAIL and the reason carried in the error
   * body must be extracted into lscResponseValues via the same response-key mechanism used for success.
   */
  @Test
  public void testLSC409BusinessExceptionCapturesErrorMessage() {
    LSC lsc = new LSC();
    lsc.setId(23L);
    lsc.setUrl("http://lsc.test.com/olmc/check");
    lsc.setDeleted(false);
    lsc.setEnabled(true);
    lsc.setReplyType(Constants.REPLY_TYPE.JSON);
    lsc.setReplyKey("$.content");
    lsc.setReplyResult("true");
    lsc.setResponseKeys("$.returnMessage;$.error");
    lsc.setHttpMethod(Constants.HTTP_METHOD.GET);
    lsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign2)));

    lenient().when(lscService.getWithInitializedCampaigns(23L)).thenReturn(lsc);
    when(lscService.getLscsForCampaign(2L, true)).thenReturn(Collections.singletonList(lsc));

    lscEngine.addRunningCampaign(runningCampaign2);

    Call call = new Call();
    call.setCampaign(runningCampaign2);
    call.setRawData(new JSONObject("{}"));
    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/olmc/check")).andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(HttpStatus.CONFLICT).body(BUSINESS_409_BODY).contentType(MediaType.APPLICATION_JSON));

    assertThat(lscEngine.applyLscsToCall(call), is(equalTo(LSC_RESULT.FAIL)));
    assertThat(call.getLscResponseValues(), is(equalTo("returnMessage: A warning occured; error: İlgili kontrat bulunamadı")));
    mockRestServiceServer.verify();
  }

  /**
   * A token-required LSC would normally retry up to 3 times (refreshing the token each retry). A 409 is
   * a business exception, so it must fail fast on the first attempt with no retry storm / token refresh.
   * MockRestServiceServer expects exactly one request, so a second attempt would fail this test.
   */
  @Test
  public void testLSC409TokenRequiredDoesNotRetry() {
    LSCAuthorization auth = new LSCAuthorization();
    auth.setId(10L);
    auth.setTokenHeaderName("Authorization");

    LSCAuthFormDTO authDto = new LSCAuthFormDTO();
    authDto.setId(10L);
    authDto.setToken("token-value");

    LSC lsc = new LSC();
    lsc.setId(23L);
    lsc.setUrl("http://lsc.test.com/olmc/check");
    lsc.setDeleted(false);
    lsc.setEnabled(true);
    lsc.setReplyType(Constants.REPLY_TYPE.JSON);
    lsc.setReplyKey("$.content");
    lsc.setReplyResult("true");
    lsc.setResponseKeys("$.error");
    lsc.setHttpMethod(Constants.HTTP_METHOD.GET);
    lsc.setTokenRequired(true);
    lsc.setAuthorization(auth);
    lsc.setCampaigns(new HashSet<>(Arrays.asList(runningCampaign2)));

    when(lscAuthService.get(10L)).thenReturn(authDto);
    when(lscService.getLscsForCampaign(2L, true)).thenReturn(Collections.singletonList(lsc));

    lscEngine.addRunningCampaign(runningCampaign2);

    Call call = new Call();
    call.setCampaign(runningCampaign2);
    call.setRawData(new JSONObject("{}"));
    MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(defaultRestTemplate);
    mockRestServiceServer.expect(requestTo("http://lsc.test.com/olmc/check")).andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(HttpStatus.CONFLICT).body(BUSINESS_409_BODY).contentType(MediaType.APPLICATION_JSON));

    assertThat(lscEngine.applyLscsToCall(call), is(equalTo(LSC_RESULT.FAIL)));
    assertThat(call.getLscResponseValues(), is(equalTo("error: İlgili kontrat bulunamadı")));
    mockRestServiceServer.verify();
  }

  /**
   * Keys with no value must be skipped (mirrors the XML/plain-text variants) so an absent key such as
   * "$.error" on a successful response does not emit noise like "error: null".
   */
  @Test
  public void processJSONWithKeysSkipsAbsentKeys() {
    String successJson = "{\"returnCode\":1,\"returnMessage\":\"The operation succeeded.\",\"content\":true,\"status\":200}";
    String result = lscEngine.processJSONWithKeys(successJson, Arrays.asList("$.returnMessage", "$.error"));
    assertThat(result, is(equalTo("returnMessage: The operation succeeded.")));
  }

}
