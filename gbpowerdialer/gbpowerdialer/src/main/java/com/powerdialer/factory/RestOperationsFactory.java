package com.powerdialer.factory;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * Factory for creating instances of {@link RestOperations} which has different
 * configs from restTemplate defined in applicationContext.xml
 *
 * Task 325469: RestTemplate kurulumu (converter taramasi + Tomcat classloader
 * kilidi) cagri hot path'inde tekrarlanamayacak kadar pahalidir. RestTemplate
 * thread-safe oldugu icin instance'lar timeout kombinasyonu basina bir kez
 * yaratilir ve paylasilir. Timeout semantigi degismez: build(-1) yine
 * timeout'suz default-converter'li RestTemplate'tir (tekil), build(-1,-1)
 * yine applicationContext'teki defaultRestTemplate'i doner.
 */
@Component
public class RestOperationsFactory {

  @Autowired
  private RestOperations defaultRestTemplate;

  private final ConcurrentHashMap<Long, RestOperations> cacheByTimeouts = new ConcurrentHashMap<>();

  /**
   * Build with specified timeouts for requests
   */
  public RestOperations build(int readTimeout, int connectTimeout) {
    if (readTimeout < 0 && connectTimeout < 0) {
      return defaultRestTemplate;
    } else {
      return getOrCreate(readTimeout, connectTimeout);
    }
  }

  /**
   * Build new RestTemplate with specified timeout for requests
   */
  public RestOperations build(int timeout) {
    if (timeout < 0) {
      return getOrCreate(-1, -1);
    } else {
      return getOrCreate(timeout, timeout);
    }
  }

  private RestOperations getOrCreate(int readTimeout, int connectTimeout) {
    Long key = (((long) readTimeout) << 32) | (connectTimeout & 0xFFFFFFFFL);
    return cacheByTimeouts.computeIfAbsent(key, k -> create(readTimeout, connectTimeout));
  }

  private RestOperations create(int readTimeout, int connectTimeout) {
    if (readTimeout < 0 && connectTimeout < 0) {
      return new RestTemplate();
    }
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setReadTimeout(readTimeout);
    requestFactory.setConnectTimeout(connectTimeout);
    return new RestTemplate(requestFactory);
  }

}
