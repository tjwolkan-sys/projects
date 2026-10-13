package com.powerdialer.engine.scheduler;

import java.util.List;
import java.util.Objects;

import java.io.IOException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;

import com.powerdialer.util.LoggerDecorator;

public abstract class StatsDataProvider {

  protected final LoggerDecorator LOG;
  private static final ObjectMapper mapper = new ObjectMapper();

  public StatsDataProvider(LoggerDecorator loggerDecorator) {
    this.LOG = loggerDecorator;
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  <T> T requestStatsData(String requestUrl, Class<T> projection) {
    ResponseEntity responseObject = processRequest(requestUrl);
    if (Objects.isNull(responseObject)) {
      LOG.info("Response body is null. Request Url:  " + requestUrl);
      return null;
    }
    if (Objects.isNull(responseObject.getBody())){
      LOG.info("Response body is empty. Request Url:  " + requestUrl);
      return null;
    }
    return mapResponseToObject(projection, responseObject);
  }

  private <T> T mapResponseToObject(Class<T> projection, ResponseEntity responseObject) {
    try {
      List<T> result = mapper.readValue(responseObject.getBody().toString(), mapper.getTypeFactory().constructCollectionType(List.class, projection));
      return CollectionUtils.isEmpty(result) ? null : result.get(0);
    } catch (IOException e) {
      LOG.error("Error occurred while parsing response. Target class:  " + projection, e);
    }
    return null;
  }

  protected abstract ResponseEntity processRequest(String requestUrl);

}
