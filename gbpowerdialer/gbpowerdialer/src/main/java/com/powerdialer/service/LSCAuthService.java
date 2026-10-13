package com.powerdialer.service;

import com.jayway.jsonpath.JsonPath;
import com.powerdialer.dao.ILSCAuthDao;
import com.powerdialer.dto.LSCAuthFormDTO;
import com.powerdialer.dto.LSCAuthListDTO;
import com.powerdialer.model.LSC;
import com.powerdialer.model.LSCAuthorization;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.RequestBodyType;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.validators.LSCAuthValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.xml.sax.InputSource;

import javax.transaction.Transactional;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LSCAuthService extends GenericService<LSCAuthorization, LSCAuthFormDTO, LSCAuthListDTO> {

  private final ILSCAuthDao lscAuthDao;
  private RestTemplate restTemplate;
  private LoggerDecorator LOG;
  private static final XPathFactory xPathFactory = XPathFactory.newInstance();

  @Autowired
  public LSCAuthService(ILSCAuthDao dao, LoggerDecorator loggerDecorator) {
    super(dao, LSCAuthorization.class, LSCAuthFormDTO.class, LSCAuthListDTO.class, new LSCAuthValidator());
    LOG = loggerDecorator.getLogger(LSCAuthService.class);
    this.lscAuthDao = dao;
    this.restTemplate = new RestTemplate();
  }

  public Map<String, String> sendRequest(LSCAuthFormDTO request) {
    HttpHeaders headers = buildHeaders(request.getResponseType(), parseHeader(request.getHeaders()));
    HttpMethod httpMethod = determineHttpMethod(request.getMethod());
    Map<String, String> response = new LinkedHashMap<>();
    ResponseEntity<String> serviceResponse;

    try {
      HttpEntity<?> httpEntity = buildHttpEntity(request, headers);
      serviceResponse = restTemplate.exchange(
              request.getUrl(),
              httpMethod,
              httpEntity,
              new ParameterizedTypeReference<String>() {}
      );

    } catch (Exception e) {
      LOG.error("Error occurred while sending request: " + e.getMessage(), e, Constants.LOG_MODULE.LSC, request);;
      response.put("error", "Error occurred while sending request: " + e.getMessage());
      return response;
    }

    if (serviceResponse.getStatusCode().is2xxSuccessful()) {
      String accessToken = "";
      if (Constants.RESPONSE_TYPE.JSON.equals(request.getResponseType())) {
        accessToken = processJSON(serviceResponse.getBody(), request.getResponseKey(), response);
      } else if (Constants.RESPONSE_TYPE.XML.equals(request.getResponseType())) {
        accessToken = processXML(serviceResponse.getBody(), request.getResponseKey(), response);
      }

      if (accessToken != null) {
        response.put("access_token", accessToken);
        response.put("response", serviceResponse.getBody());
        response.put("request", request.getBody());
        response.put("status_code", serviceResponse.getStatusCode().toString());
      }

      LOG.info("Process Successful. Access token: " + accessToken, Constants.LOG_MODULE.LSC, request);

    } else {
      response.put("error", "Error occurred while sending request: " + serviceResponse.getBody());
      LOG.error("Error occurred while sending request: " + serviceResponse.getBody(), serviceResponse, Constants.LOG_MODULE.LSC, request);
    }

    return response;
  }

  private HttpHeaders buildHeaders(Constants.RESPONSE_TYPE replyType, Map<String, String> headerMap) {
    HttpHeaders headers = new HttpHeaders();
    if (Constants.RESPONSE_TYPE.JSON.equals(replyType)) {
      headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    }
    if (headerMap != null) {
      for (Map.Entry<String, String> entry : headerMap.entrySet()) {
        headers.set(entry.getKey(), entry.getValue());
      }
    }
    return headers;
  }

  private HttpEntity<?> buildHttpEntity(LSCAuthFormDTO request, HttpHeaders headers) {
    if (request.getBodyType() == RequestBodyType.FORM_URLENCODED) {
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
      MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
      if (request.getBody() != null && !request.getBody().isEmpty()) {
        String[] pairs = request.getBody().split("&");
        for (String pair : pairs) {
          String[] kv = pair.split("=");
          if (kv.length == 2) {
              formData.add(kv[0], kv[1]);
          }
        }
      }
      return new HttpEntity<>(formData, headers);
    } else if (request.getBodyType() == RequestBodyType.JSON) {
      headers.setContentType(MediaType.APPLICATION_JSON);
      return new HttpEntity<>(request.getBody(), headers);
    } else if (request.getBodyType() == RequestBodyType.XML) {
      headers.setContentType(MediaType.APPLICATION_XML);
      return new HttpEntity<>(request.getBody(), headers);
    } else {
      headers.setContentType(MediaType.TEXT_PLAIN);
      return new HttpEntity<>(request.getBody(), headers);
    }
  }

  private HttpMethod determineHttpMethod(String method) {
    try {
      return HttpMethod.valueOf(method.toUpperCase());
    } catch (IllegalArgumentException e) {
      return HttpMethod.GET;
    }
  }

  private Map<String, String> parseHeader(String header) {
    Map<String, String> formData = new LinkedHashMap<>();
    if (header != null && !header.isEmpty()) {
      String[] pairs = header.split("&");
      for (String pair : pairs) {
        String[] kv = pair.split("=");
        if (kv.length == 2) {
          formData.put(kv[0], kv[1]);
        }
      }
    }
    return formData;
  }

  private String processJSON(String body, String responseKey, Map<String, String> response) {
    try {
      return JsonPath.compile(responseKey).read(body).toString();
    } catch (Exception e) {
      LOG.error("JSONPath expression:" + responseKey + " can't be evaluated for JSON: " + body, e, Constants.LOG_MODULE.LSC);
      response.put("error", "Error occurred while processing JSON Path for response key");
    }
    return null;
  }

  private String processXML(String body, String responseKey, Map<String, String> response) {
    try {
      XPath xPath = xPathFactory.newXPath();
      InputSource source = new InputSource(new StringReader(body));
      return xPath.evaluate(responseKey, source);
    } catch (Exception e) {
      LOG.error("XPath expression:" + responseKey + " can't be evaluated for XML: " + body, e, Constants.LOG_MODULE.LSC);
      response.put("error", "XPath expression cant be evaluated for xml for response key");
    }
    return null;
  }


}
