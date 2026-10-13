package com.powerdialer.engine;

import static com.powerdialer.util.Constants.KEY;
import static com.powerdialer.util.Constants.VALUE;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.io.StringReader;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.jayway.jsonpath.JsonPath;
import com.powerdialer.serialization.LscWildcardsCallSerializer;
import com.powerdialer.dto.LSCAuthFormDTO;
import com.powerdialer.service.LSCAuthService;
import org.dozer.Mapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.powerdialer.factory.RestOperationsFactory;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.LSC;
import com.powerdialer.service.LSCService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.HTTP_METHOD;
import com.powerdialer.util.Constants.LSC_RESULT;
import com.powerdialer.util.Constants.REPLY_TYPE;
import com.powerdialer.util.LoggerDecorator;

/**
 * Engine for applying LSC services to Calls.
 */
@Component
@Lazy(false)
public class LSCEngine {

  private static final Pattern WILDCARD_PATTERN = Pattern.compile("\\{[^:]+?}");
  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{[^}]+\\}");
  private static final String CALL_WILDCARD_PREFIX = "call.";

  private static final XPathFactory xPathFactory = XPathFactory.newInstance();
  private LoggerDecorator LOG;
  private Set<Campaign> runningCampaigns = Collections.synchronizedSet(new HashSet<>());
  private Set<LSC> globalLSCs = Collections.synchronizedSet(new HashSet<>());
  private Map<Campaign, Set<LSC>> campaignLscsMap = new ConcurrentHashMap<>();
  private LSCService lscService;
  private RestOperationsFactory restOperationsFactory;
  private ObjectMapper objectMapper;
  @Autowired
  private LSCAuthService lscAuthService;

  @Autowired
  Mapper mapper;

  @Autowired
  public LSCEngine(LoggerDecorator loggerDecorator, LSCService lscService, RestOperationsFactory restOperationsFactory, LSCAuthService lscAuthService) {
    LOG = loggerDecorator.getLogger(LSCEngine.class);
    this.lscService = lscService;
    this.restOperationsFactory = restOperationsFactory;
    this.lscAuthService = lscAuthService;
  }

  @PostConstruct
  public void init() {
    objectMapper = new ObjectMapper();
    SimpleModule module = new SimpleModule().addSerializer(Call.class, new LscWildcardsCallSerializer());
    objectMapper.registerModule(module);
    objectMapper.disable(MapperFeature.USE_ANNOTATIONS);
  }

  /**
   * Adds store in memory running campaign and load LSC list for it
   */
  public boolean addRunningCampaign(Campaign campaign) {
    if (runningCampaigns.isEmpty())
      loadGlobalLSCs();
    loadLSCForCampaign(campaign);
    return runningCampaigns.add(campaign);
  }

  /**
   * Loads all enabled global LSC lists in memory
   */
  private void loadGlobalLSCs() {
    List<LSC> globalLSCs = lscService.getGlobalLscs(true);
    if (!globalLSCs.isEmpty()) {
      this.globalLSCs.addAll(globalLSCs);
    }
  }

  /**
   * Loads all campaign LSCs into memory (if LSC enabled for campaign)
   * @param campaign
   */
  public void loadLSCForCampaign(Campaign campaign) {
    LOG.info("Loading LSC lists for " + campaign);
    if (campaign.getLscEnabled()) {
      Set<LSC> campaignLscs = new LinkedHashSet<>(
              lscService.getLscsForCampaign(campaign.getId(), true)
      );
      campaignLscsMap.put(campaign, campaignLscs);
    } else {
      campaignLscsMap.remove(campaign);
    }
  }

  /**
   * Removes running campaign and clears it's LSC list
   */
  public boolean removeRunningCampaign(Campaign campaign) {
    boolean removeResult = runningCampaigns.remove(campaign);
    clearLSCForCampaign(campaign);
    if (runningCampaigns.isEmpty())
      clearGlobalLSCs();
    return removeResult;
  }

  /**
   * Removes LSCs for specified campaign
   * @param campaign
   */
  private void clearLSCForCampaign(Campaign campaign) {
    campaignLscsMap.remove(campaign);
  }

  /**
   * Clears global LSC cache
   */
  private void clearGlobalLSCs() {
    globalLSCs.clear();
  }

  /**
   * Updates LSCs stored in memory
   */
  public void updateLSC(Long id) {
    LOG.info("Updating LSC list with id " + id);
    LSC lscToUpdate = lscService.getWithInitializedCampaigns(id);
    updateGlobalLSCs(lscToUpdate);
    updateCampaignsLSCs(lscToUpdate);
  }

  /**
   * Updates global LSCs
   */
  private void updateGlobalLSCs(LSC lsc) {
    LOG.info("Removing LSC from the global cache: " + lsc);
    globalLSCs.remove(lsc);
    if (lsc.getGlobal() && lsc.isEnabled() && !lsc.isDeleted()) {
      LOG.info("Adding global LSC in the global cache: " + lsc);
      globalLSCs.add(lsc);
    }
  }

  /**
   * Updates campaign LSCs
   */
  private void updateCampaignsLSCs(LSC lsc) {
    if (lsc == null || lsc.getGlobal() || lsc.getCampaigns() == null) {
      return;
    }

    lsc.getCampaigns().stream()
            .filter(campaignLscsMap::containsKey)
            .forEach(campaign -> {
              loadLSCForCampaign(campaign);
            });
  }

  /**
   * Applies all appropriate LSCs to call
   * @param call
   * @return result of lsc
   */
  public LSC_RESULT applyLscsToCall(Call call) {
    Campaign campaign = call.getCampaign();
    Set<LSC> campaignLSCs = campaignLscsMap.get(campaign);

    Set<LSC> orderedLscs = new LinkedHashSet<>();
    orderedLscs.addAll(globalLSCs);
    orderedLscs.addAll(campaignLSCs);

    LSC_RESULT lscResult = processLSCs(call, orderedLscs, campaign.getLscTimeoutBehavior());
    LOG.info("LSC(s) result is " + lscResult + " for " + call);
    return lscResult;
  }

  /**
   * Applies all specified LSCs to Call
   * @return result of lsc
   */
  private LSC_RESULT processLSCs(Call call, Collection<LSC> lscs, Constants.LSC_TIMEOUT_BEHAVIOR timeoutBehavior) {
    if (!CollectionUtils.isEmpty(lscs))
      for (LSC lsc : lscs) {
        LSC_RESULT lscResult = applyLSC(call, lsc);
        if (!isLSCResultSuitable(lscResult, timeoutBehavior))
          return lscResult;
      }
    return LSC_RESULT.OK;
  }

  private boolean isLSCResultSuitable(LSC_RESULT result, Constants.LSC_TIMEOUT_BEHAVIOR timeoutBehavior) {
    if (LSC_RESULT.OK.equals(result)) {
      return  true;
    }
    return LSC_RESULT.TIMEOUT.equals(result) && Constants.LSC_TIMEOUT_BEHAVIOR.DIAL.equals(timeoutBehavior);
  }

  /**
   * Applies single LSC to Call
   * @return result of lsc
   */
  private LSC_RESULT applyLSC(Call call, LSC lsc) {
    Map<String, String> headers = parseAndEvaluate(call, lsc.getHeaders());
    String url = prepareUrl(lsc.getUrl(), call);
    RestOperations restClient = getRestClientForLSC(lsc);
    ResponseEntity<String> responseObject = null;

    final int maxRetry = lsc.isTokenRequired() ? 3 : 1;
    int attempt = 0;
    LSC_RESULT result = LSC_RESULT.OK;
    String businessErrorBody = null;

    while (attempt < maxRetry) {
      if (attempt > 0) {
        LOG.info("[LSC-RETRY] lscId=" + lsc.getId() + " | attempt=" + attempt + " / " + maxRetry, Constants.LOG_MODULE.LSC);
      } else {
        LOG.audit("[LSC-RETRY] lscId=" + lsc.getId() + " | attempt=" + attempt + " / " + maxRetry, Constants.LOG_MODULE.LSC);
      }

      JSONObject rawData = call.getRawData();
      boolean hasCalledNumber = rawData != null && rawData.has("called_number");
      String retryNumber = hasCalledNumber ? call.getCalledNumber() : null;
      String calledNumber = hasCalledNumber ? rawData.getString("called_number") : null;

      try {
        if (lsc.isTokenRequired()) {
          if(attempt > 0) {
            getAndSetNewToken(lsc);
            result = LSC_RESULT.OK;
          }

          LSCAuthFormDTO lscAuthFormDTO = lscAuthService.get(lsc.getAuthorization().getId());
          LOG.audit(
                  "[LSC-AUTH] READ | lscId=" + lsc.getId()
                          + " | authId=" + lscAuthFormDTO.getId()
                          + " | attempt=" + attempt
                          + " | tokenHash=" + (lscAuthFormDTO.getToken() != null
                          ? lscAuthFormDTO.getToken().hashCode()
                          : "null")
                          + " | updateDate=" + lscAuthFormDTO.getUpdateDate(),
                  Constants.LOG_MODULE.LSC
          );
          headers.put(lsc.getAuthorization().getTokenHeaderName(), String.format("Bearer %s", lscAuthFormDTO.getToken()));
        }

        applyLSCStartsWithZero(lsc.isStartsWithZero(), call);

        if (lsc.isFreePayloadEnabled()) {
          String payload = evaluateFreePayload(call, lsc.getPayload());
          if (lsc.getHttpMethod().ordinal() == HTTP_METHOD.GET.ordinal()) {
            HttpEntity<String> entity = getHttpEntity("", headers);
            LOG.audit("LSC URL: " + url +  " headers: " + headers + " payload: " + payload, Constants.LOG_MODULE.LSC, lsc);
            responseObject = restClient.exchange(url, HttpMethod.GET, entity, String.class, payload);
          } else {
            HttpEntity<String> entity = getHttpEntity(payload, headers);
            LOG.audit("LSC URL: " + url +  " headers: " + headers + " payload: " + payload, Constants.LOG_MODULE.LSC, lsc);
            responseObject = restClient.exchange(url, HttpMethod.POST, entity, String.class);
          }
        } else {
          Map<String, String> payload = parseAndEvaluate(call, lsc.getPayload());
          if (lsc.getHttpMethod().ordinal() == HTTP_METHOD.GET.ordinal()) {
            HttpEntity<String> entity = getHttpEntity("", headers);
            LOG.audit("LSC URL: " + url +  " headers: " + headers + " payload: " + payload, Constants.LOG_MODULE.LSC, lsc);
            responseObject = restClient.exchange(url, HttpMethod.GET, entity, String.class, payload);
          } else {
            HttpEntity<Map<String, String>> entity = getHttpEntity(payload, headers);
            LOG.audit("LSC URL: " + url +  " headers: " + headers + " payload: " + payload, Constants.LOG_MODULE.LSC, lsc);
            responseObject = restClient.exchange(url, HttpMethod.POST, entity, String.class);
          }
        }

        if (responseObject.getStatusCode().is2xxSuccessful()) {
          break;
        }
      } catch (HttpStatusCodeException e) {
        int statusCode = e.getStatusCode().value();
        if (statusCode == 401 || statusCode == 403) {
          // AUTH: token expired/invalid -> keep looping so token-required LSCs can refresh and retry.
          LOG.warn("[LSC-AUTH] " + lsc + " returned " + statusCode + " during " + call, Constants.LOG_MODULE.LSC, lsc);
          result = LSC_RESULT.FAIL;
        } else if (e.getStatusCode().is5xxServerError()) {
          // INFRASTRUCTURE (provider contract: 5xx = access/technical problem) -> eligibility unknown.
          // Treat like a timeout so the per-campaign LSC_TIMEOUT_BEHAVIOR and retry strategy apply.
          LOG.error("[LSC-INFRA] " + lsc + " returned " + statusCode + " during " + call, e, Constants.LOG_MODULE.LSC, lsc);
          result = LSC_RESULT.TIMEOUT;
          break;
        } else {
          // BUSINESS (provider contract: 409 = business exception) and any other client error ->
          // definitive "no". Do not retry/refresh token; keep the reason body for lscResponseValues.
          LOG.warn("[LSC-BUSINESS] " + lsc + " returned " + statusCode + " during " + call + ", body: " + e.getResponseBodyAsString(), Constants.LOG_MODULE.LSC, lsc);
          result = LSC_RESULT.FAIL;
          businessErrorBody = e.getResponseBodyAsString();
          break;
        }
      } catch (Exception e) {
        if (e.getCause() instanceof SocketTimeoutException) {
          LOG.info("Timeout has occurred for " + lsc + " during " + call, e, Constants.LOG_MODULE.LSC, lsc);
          result = LSC_RESULT.TIMEOUT;
        } else if (e.getCause() instanceof ConnectException && lsc.getRequestTimeout() != null && lsc.getRequestTimeout() > 0) {
          LOG.info("Connection refused for " + lsc + " during " + call, e, Constants.LOG_MODULE.LSC, lsc);
          result = LSC_RESULT.TIMEOUT;
        } else {
          LOG.error("Error occurred while accessing " + lsc + " during " + call + ", error message: " + e, e, Constants.LOG_MODULE.LSC, lsc);
          result = LSC_RESULT.FAIL;
        }
      } finally {
        rollbackLSCStartsWithZero(lsc.isStartsWithZero(), call, calledNumber, retryNumber);
      }

      attempt++;
    }

    if(!LSC_RESULT.OK.equals(result)) {
      // Business rejections (e.g. 409) carry a body with the reason. Run the same response-key
      // extraction on it so lscResponseValues (persisted/published with the call result) reflects why.
      if (businessErrorBody != null) {
        try {
          call.setLscResponseValues(processResponseWithKeys(businessErrorBody, lsc.getReplyType(), lsc.getResponseKeys()));
        } catch (Exception e) {
          LOG.error("Error while processing keys from business error body" + e, e, Constants.LOG_MODULE.LSC, lsc);
          call.setLscResponseValues(null);
        }
      }
      return result;
    }

    try {
      call.setLscResponseValues(processResponseWithKeys(responseObject.getBody(), lsc.getReplyType(), lsc.getResponseKeys()));
    } catch (Exception e) {
      LOG.error("Error while processing keys" + e, e, Constants.LOG_MODULE.LSC, lsc);
      call.setLscResponseValues(null);
    }

    return processResponse(responseObject, lsc);
  }

  private void getAndSetNewToken(LSC lsc) {
    LSCAuthFormDTO lscAuthFormDTO = mapper.map(lsc.getAuthorization(), LSCAuthFormDTO.class);

    LOG.info("[LSC-AUTH] REFRESH-START | lscId=" + lsc.getId() + " | authId=" + lscAuthFormDTO.getId()
                    + " | oldTokenHash=" + (lscAuthFormDTO.getToken() != null ? lscAuthFormDTO.getToken().hashCode() : "null"), Constants.LOG_MODULE.LSC);

    Map<String, String> authResponse = lscAuthService.sendRequest(lscAuthFormDTO);
    String newToken = authResponse.get("access_token");
    lscAuthFormDTO.setToken(newToken);
    lscAuthFormDTO.setResponse(authResponse.get("response"));
    lscAuthFormDTO.setUpdateDate(new Date());

    LOG.info("[LSC-AUTH] REFRESH-END | lscId=" + lsc.getId() + " | authId=" + lscAuthFormDTO.getId()
                    + " | newTokenHash=" + (newToken != null ? newToken.hashCode() : "null"), Constants.LOG_MODULE.LSC);

    try {
      lscAuthService.merge(lscAuthFormDTO);

      //db verify
      LSCAuthFormDTO after = lscAuthService.get(lscAuthFormDTO.getId());
      LOG.info("[LSC-AUTH] DB-VERIFY | authId=" + after.getId()
              + " | tokenHash=" + (after.getToken() != null ? after.getToken().hashCode() : "null")
              + " | updateDate=" + after.getUpdateDate(), Constants.LOG_MODULE.LSC);
    } catch (Exception e) {
      LOG.error("[LSC-AUTH] REFRESH-FAIL | authId=" + lscAuthFormDTO.getId(), e, Constants.LOG_MODULE.LSC);
      throw e;
    }
  }

  private String evaluateFreePayload(Call call, String payload) {
    if (payload == null) {
      return "";
    }
    String result = payload;
    for (String match : findWildcards(payload)) {
      result = evaluateWildcard(result, match, call);
    }
    return result;
  }

  private List<String> findWildcards(String payload) {
    Matcher matcher = WILDCARD_PATTERN.matcher(payload);
    List<String> matches = new ArrayList<>();
    while(matcher.find()) {
      matches.add(matcher.group());
    }
    return matches;
  }

  private String evaluateWildcard(String payload, String match, Call call) {
    String key = match.substring(1, match.length() - 1);
    if (key.startsWith(CALL_WILDCARD_PREFIX)) {
      String callKey = key.substring(CALL_WILDCARD_PREFIX.length());
      String callValue = getCallFieldValue(call, callKey);
      if (callValue != null) {
        return payload.replace(match, callValue);
      }
    }
    JSONObject rawData = call.getRawData();
    return payload.replace(match,
            Optional.ofNullable(rawData != null ? rawData.optString(key, "") : "")
                    .map(String::trim)
                    .filter(v -> !"null".equalsIgnoreCase(v.trim()))
                    .orElse(""));
  }

  private String getCallFieldValue(Call call, String key) {
    try {
      JSONObject callJson = new JSONObject(objectMapper.writeValueAsString(call));
      return callJson.has(key) ? callJson.getString(key) : null;
    } catch (JsonProcessingException e) {
      LOG.error("Error occurred while parsing call to JSON", e, Constants.LOG_MODULE.LSC, call);
      return null;
    }
  }

  /**
   * Parses string JSON array using {@link Call#rawData}. Used for parsing
   * payload and headers
   */
  private Map<String, String> parseAndEvaluate(Call call, String jsonArray) {
    Map<String, String> result = new HashMap<>();
    if (StringUtils.isEmpty(jsonArray))
      return result;
    JSONArray rawPayload = new JSONArray(jsonArray);
    if (rawPayload.length() > 0) {
      for (int i = 0; i < rawPayload.length(); i++) {
        String payloadKey = rawPayload.getJSONObject(i).get(KEY).toString();
        String expression = rawPayload.getJSONObject(i).get(VALUE).toString();
        result.put(payloadKey, evaluateExpressionWithRawData(call, expression));
      }
    }
    return result;
  }

  /**
   * Parses LSC url using {@link Call#rawData}
   */
  private String prepareUrl(String rawUrl, Call call) {
    String[] split = rawUrl.split(Constants.LSC_EXPRESSION_PATTERN);
    if (split.length == 0)
      return rawUrl;
    Pattern pattern = Pattern.compile(Constants.LSC_EXPRESSION_PATTERN);
    Matcher matcher = pattern.matcher(rawUrl);
    StringBuilder parsedUrl = new StringBuilder(split[0]);
    int expressionCounter = 0;
    while (matcher.find()) {
      expressionCounter++;
      String expression = matcher.group(0);
      parsedUrl.append(evaluateExpressionWithRawData(call, expression));
      if (expressionCounter < split.length) {
        parsedUrl.append(split[expressionCounter]);
      }
    }
    return parsedUrl.toString();
  }

  /**
   * Gets expression value from {@link Call#rawData} and fields in {@link LscWildcardsCallSerializer}
   */
  private String evaluateExpressionWithRawData(Call call, String expression) {
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(expression);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      String placeholder = matcher.group();
      String key = placeholder.substring(1, placeholder.length() - 1);
      String replacement = placeholder;
      if (key.startsWith(CALL_WILDCARD_PREFIX)) {
        String callKey = key.substring(CALL_WILDCARD_PREFIX.length());
        String callValue = getCallFieldValue(call, callKey);
        if (callValue != null) {
          replacement = callValue;
        }
      } else {
        JSONObject rawData = call.getRawData();
        if (rawData != null && rawData.has(key)) {
          replacement = rawData.get(key).toString();
        }
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  /**
   * Builds rest client for LSC using it's timeout settings
   */
  public RestOperations getRestClientForLSC(LSC lsc) {
    Integer requestTimeout = lsc.getRequestTimeout();
    return requestTimeout == null || requestTimeout <= 0 ? restOperationsFactory.build(-1) : restOperationsFactory.build(requestTimeout);
  }

  /**
   * Builds HttpEntity with payload. If payload not specified HttpEntity without
   * body be built
   */
  private HttpEntity<Map<String, String>> getHttpEntity(Map<String, String> payload, Map<String, String> lscHeaders) {
    HttpHeaders headers = prepareHeaders(lscHeaders);
    if (payload == null) {
      return new HttpEntity<>(headers);
    } else {
      headers.setContentType(MediaType.APPLICATION_JSON);
      return new HttpEntity<>(payload, headers);
    }
  }

  private HttpEntity<String> getHttpEntity(String payload, Map<String, String> lscHeaders) {
    HttpHeaders headers = prepareHeaders(lscHeaders);
    return StringUtils.isEmpty(payload) ? new HttpEntity<>(headers) : new HttpEntity<>(payload, headers);
  }

  private HttpHeaders prepareHeaders(Map<String, String> lscHeaders) {
    List<MediaType> acceptableMediaTypes = new ArrayList<>();
    acceptableMediaTypes.add(MediaType.APPLICATION_XML);
    acceptableMediaTypes.add(MediaType.TEXT_XML);
    acceptableMediaTypes.add(MediaType.APPLICATION_JSON);
    acceptableMediaTypes.add(MediaType.TEXT_PLAIN);
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(acceptableMediaTypes);
    headers.setAll(lscHeaders);
    return headers;
  }

  /**
   * Processes response from LSC service
   * @return result of lsc
   */
  private LSC_RESULT processResponse(ResponseEntity responseObject, LSC lsc) {
    if (responseObject == null || responseObject.getBody() == null) {
      LOG.warn("HTTP response object (body) is null for " + lsc, Constants.LOG_MODULE.LSC, lsc);
      return LSC_RESULT.FAIL;
    }
    LOG.audit(lsc + " respond: '" + responseObject.getBody(), Constants.LOG_MODULE.LSC, lsc);
    LSC_RESULT result = LSC_RESULT.FAIL;
    if (lsc.getReplyType() == REPLY_TYPE.JSON) {
      result = processJSON(responseObject.getBody().toString(), lsc);
    } else if ((lsc.getReplyType() == REPLY_TYPE.XML)) {
      result = processXML(responseObject.getBody().toString(), lsc);
    } else if (lsc.getReplyType() == REPLY_TYPE.PLAIN_TEXT) {
      result = processPlainText(responseObject.getBody().toString(), lsc);
    }
    LOG.audit("LSC result is " + result, result, Constants.LOG_MODULE.LSC, lsc);
    return result;
  }

  /**
   * Processes JSON answer from LSC service
   */
  private LSC_RESULT processJSON(String json, LSC lsc) {
    try {
      Object replyValue = JsonPath.compile(lsc.getReplyKey()).read(json);
      if (replyValue == null) {
        // FIX-333607: reply key değeri cevapta null; sonuç eskisi gibi FAIL, NPE/ERROR stack yerine WARN.
        LOG.warn("[LSC] " + lsc + " reply key " + lsc.getReplyKey() + " has no value in response; LSC result is FAIL", Constants.LOG_MODULE.LSC, lsc);
        return LSC_RESULT.FAIL;
      }
      return replyValue.toString().equals(lsc.getReplyResult()) ? LSC_RESULT.OK : LSC_RESULT.FAIL;
    } catch (Exception e) {
      LOG.error("JSONPath expression:" + lsc.getReplyKey() + " can't be evaluated for JSON: " + json, e, Constants.LOG_MODULE.LSC, lsc);
      return LSC_RESULT.FAIL;
    }
  }

  /**
   * Processes XML answer from LSC service
   */
  private LSC_RESULT processXML(String xml, LSC lsc) {
    try {
      XPath xPath = xPathFactory.newXPath();
      InputSource source = new InputSource(new StringReader(xml));
      String evaluationResult = xPath.evaluate(lsc.getReplyKey(), source);
      return lsc.getReplyResult().equals(evaluationResult) ? LSC_RESULT.OK : LSC_RESULT.FAIL;
    } catch (Exception e) {
      LOG.error("XPath expression:" + lsc.getReplyKey() + " can't be evaluated for XML: " + xml, e, Constants.LOG_MODULE.LSC, lsc);
    }
    return LSC_RESULT.FAIL;
  }

  /**
   * Processes plain text answer from LSC service
   */
  private LSC_RESULT processPlainText(String text, LSC lsc) {
    try {
      return (text != null && Pattern.compile(lsc.getReplyResult()).matcher(text).matches()) ? LSC_RESULT.OK : LSC_RESULT.FAIL;
    } catch (Exception e) {
      LOG.error("RegEx: " + lsc.getReplyResult() + " can't be evaluated for text: " + text, e, Constants.LOG_MODULE.LSC, lsc);
    }
    return LSC_RESULT.FAIL;
  }

  public String processResponseWithKeys(String responseObject, REPLY_TYPE replyType, String responseKeys) {
    if (responseObject == null || !StringUtils.hasText(responseKeys)) {
      return null;
    }

    String result = "";
    List<String> keys = Arrays.asList(responseKeys.split(";"));

    if (REPLY_TYPE.JSON.equals(replyType)) {
      result = processJSONWithKeys(responseObject, keys);
    } else if (REPLY_TYPE.XML.equals(replyType)) {
      result = processXMLWithKeys(responseObject, keys);
    } else if (REPLY_TYPE.PLAIN_TEXT.equals(replyType)) {
      result = processPlainTextWithKeys(responseObject, keys);
    }

    return result;
  }

  /**
   * Processes JSON answer from LSC service
   */
  public String processJSONWithKeys(String json, List<String> keys) {
    try {
      StringBuilder result = new StringBuilder();
      boolean firstPair = true;

      for (String keyPath : keys) {
        String keyName = extractKeyName(keyPath);
        Object value = null;

        try {
          value = JsonPath.compile(keyPath).read(json);
        } catch (Exception e) {
          LOG.warn("Key '{}' not found in JSON", keyPath);
        }

        // Append only present keys (mirrors the XML/plain-text variants) so absent keys such as
        // "$.error" on a successful response don't emit noise like "error: null".
        if (value != null) {
          if (!firstPair) {
            result.append("; ");
          }
          result.append(keyName).append(": ").append(value.toString());
          firstPair = false;
        }
      }

      return result.toString();

    } catch (Exception e) {
      LOG.error("Error processing JSON with keys. JSON: " + json, e);
      return null;
    }
  }

  private String extractKeyName(String keyPath) {
    if (keyPath == null || keyPath.isEmpty()) return "";
    int lastDot = keyPath.lastIndexOf('.');
    return lastDot >= 0 ? keyPath.substring(lastDot + 1).replace("$", "") : keyPath.replace("$", "");
  }

  /**
   * Processes XML answer from LSC service
   */
  public String processXMLWithKeys(String xml, List<String> keys) {
    try {
      // Parse once
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document doc = db.parse(new InputSource(new StringReader(xml)));

      XPath xpath = XPathFactory.newInstance().newXPath();

      StringBuilder out = new StringBuilder();
      boolean firstPair = true;

      for (String expr : keys) {
        String label = extractLastLocalName(expr);

        // Evaluate as NODESET to support multiple matches
        NodeList nodes = (NodeList) xpath.evaluate(expr, doc, XPathConstants.NODESET);

        String valueStr = null;
        if (nodes != null && nodes.getLength() > 0) {
          List<String> values = new ArrayList<>(nodes.getLength());
          for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            String v = n.getTextContent();
            if (v != null) {
              v = v.trim();
              if (!v.isEmpty()) values.add(v);
            }
          }
          if (!values.isEmpty()) {
            valueStr = String.join(",", values);
          }
        }

        // Append only if we actually have a value (mirrors your JSON approach)
        if (valueStr != null) {
          if (!firstPair) out.append("; ");
          out.append(label).append(": ").append(valueStr);
          firstPair = false;
        }
      }

      return out.toString();

    } catch (Exception e) {
      LOG.error("XPath evaluation failed for XML.", e);
      return null;
    }
  }

  /**
   * Extracts the last local-name from an expression like:
   *  /*[local-name()='Envelope']/*[local-name()='Body']/*[local-name()='result']  -> "result"
   */
  private String extractLastLocalName(String xpathExpr) {
    if (xpathExpr == null) return "";
    // Find last occurrence of local-name()='...'
    int pos = xpathExpr.lastIndexOf("local-name()");
    if (pos == -1) return ""; // fallback if unexpected
    // Look for the opening quote after local-name()='
    int start = xpathExpr.indexOf("'", pos);
    int end = (start >= 0) ? xpathExpr.indexOf("'", start + 1) : -1;
    if (start >= 0 && end > start) {
      return xpathExpr.substring(start + 1, end);
    }
    // Double-quote variant support
    start = xpathExpr.indexOf("\"", pos);
    end = (start >= 0) ? xpathExpr.indexOf("\"", start + 1) : -1;
    if (start >= 0 && end > start) {
      return xpathExpr.substring(start + 1, end);
    }
    return "";
  }

  /**
   * Processes plain text answer from LSC service
   */
  public String processPlainTextWithKeys(String text, List<String> keys) {
    try {
      if (text == null) return "";

      StringBuilder result = new StringBuilder();
      boolean first = true;

      for (String key : keys) {
        List<String> matches = new ArrayList<>();
        try {
          Matcher matcher = Pattern.compile(key).matcher(text);
          while (matcher.find()) {
            matches.add(matcher.group());
          }
        } catch (Exception e) {
          LOG.warn("Regex '{}' could not be evaluated for text.", key);
        }

        if (!matches.isEmpty()) {
          if (!first) result.append("; ");
          result.append(String.format("%s: %s", key, String.join(",", matches)));
          first = false;
        }
      }

      return result.toString();

    } catch (Exception e) {
      LOG.error("Error evaluating regex for text: " + text, e);
      return null;
    }
  }

  private void applyLSCStartsWithZero(boolean value, Call call) {
    if (value) {
      JSONObject rawData = call.getRawData();
      if (rawData == null) {
        return;
      }
      String calledNumber = call.getCalledNumber();
      if (StringUtils.hasText(calledNumber) && calledNumber.startsWith("0")) {
        rawData.put("called_number", calledNumber.substring(1));
      }

      Campaign campaign = call.getCampaign();
      calledNumber = rawData.has("called_number") ? rawData.getString("called_number") : null;
      if(campaign != null && campaign.getRetryStrategy() != null) {
        if (StringUtils.hasText(calledNumber)) {
          rawData.put("retry_number", calledNumber);
        }
      }
    }
  }

  private void rollbackLSCStartsWithZero(boolean value, Call call, String calledNumber, String retryNumber) {
    JSONObject rawData = call.getRawData();
    if (value && rawData != null) {
      rawData.put("called_number", calledNumber);
      rawData.put("retry_number", retryNumber);
    }
  }
}
