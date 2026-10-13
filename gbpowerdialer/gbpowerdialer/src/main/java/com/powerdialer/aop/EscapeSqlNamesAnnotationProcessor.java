package com.powerdialer.aop;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Field;
import com.powerdialer.model.Filter;
import com.powerdialer.request.AddPhoneNumberColumnsRequest;
import com.powerdialer.util.Constants;

/**
 * Process for {@link com.powerdialer.annotation.EscapeSqlNames} annotation.
 * Here we create proxies that should return escaped table and column names when
 * generating SQL queries
 */
@Component
@Aspect
public class EscapeSqlNamesAnnotationProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(EscapeSqlNamesAnnotationProcessor.class);

  @Around("@annotation(com.powerdialer.annotation.EscapeSqlNames)")
  public Object processAnnotatedMethod(ProceedingJoinPoint joinPoint) throws Throwable {
    Object[] args = joinPoint.getArgs();
    try {
      Constants.DB_TYPE dbType = getDBType(args);
      if (dbType == null) {
        LOG.warn("Can't determine dbType for annotated method: " + joinPoint.getSignature().getName() + ". SQL names will now be escaped");
      }
      if (CampaignDbOperations.shouldEscapeNames(dbType)) {
        for (int i = 0; i < args.length; i++) {
          Object arg = args[i];
          if (arg != null && !Enhancer.isEnhanced(arg.getClass())) {
            if (arg instanceof Campaign) {
              Campaign proxy = createProxyForCampaign((Campaign) arg);
              args[i] = proxy;
            } else if (arg instanceof AddPhoneNumberColumnsRequest) {
              AddPhoneNumberColumnsRequest proxy = createProxyForAddPhoneNumberColumnsRequest((AddPhoneNumberColumnsRequest) arg);
              args[i] = proxy;
            } else if (arg instanceof Collection) {
              Collection<?> collection = (Collection<?>) arg;
              args[i] = createProxyForCollectionsObjects(collection);
            } else if (arg instanceof Call) {
              Call call = (Call) arg;
              Call proxiedCall = createProxyForCall(call);
              args[i] = proxiedCall;
            } else if (arg instanceof Map) {
              Map<?, ?> map = (Map<?, ?>) arg;
              Map<?, ?> mapWithEscapedKeys = createMapWithEscapedKeys(map);
              args[i] = mapWithEscapedKeys;
            }
          }
        }
      }
    } catch (Exception e) {
      LOG.warn("Processing annotation EscapeSqlNames failed. Execute SQL names will not be escaped", e);
    }
    Object proceed = joinPoint.proceed(args);
    return proceed;
  }

  /**
   * Extract type of database from method parameters
   * @param args
   * @return
   */
  private Constants.DB_TYPE getDBType(Object[] args) {
    for (Object arg : args) {
      if (arg instanceof Campaign) {
        return Constants.DB_TYPE.getByConnectionString(((Campaign) arg).getDbConnectionString());
      } else if (arg instanceof AddPhoneNumberColumnsRequest) {
        return Constants.DB_TYPE.getByConnectionString(((AddPhoneNumberColumnsRequest) arg).getConnectionString());
      } else if (arg instanceof Call) {
        return Constants.DB_TYPE.getByConnectionString(((Call) arg).getCampaign().getDbConnectionString());
      }
    }
    return null;
  }

  /**
   * Get type of collection elements
   * @param iterable
   * @return
   */
  private Class<?> getCollectionItemsType(Iterable<?> iterable) {
    Iterator<?> iterator = iterable.iterator();
    if (iterator.hasNext()) {
      return iterator.next().getClass();
    } else {
      return null;
    }
  }

  /**
   * Create proxy for Campaign object that intercept all methods that return SQL
   * names and escape it's return values
   */
  private Campaign createProxyForCampaign(Campaign campaign) {
    MethodInterceptor handler = (obj, method, args, proxy) -> {
      if (method.getName().startsWith("getCol") || method.getName().equals("getDbListTable")
          || method.getName().equals("getDbResultTable") || method.getName().equals("getDbPriorityTable")) {
        return Optional.ofNullable(proxy.invoke(campaign, args))
            .map(result -> "\"" + result + "\"")
            .orElse(null);
      } else if (method.getName().equals("getEnabledFilters")) {
        List<Filter> originalFilters = (List<Filter>) proxy.invoke(campaign, args);
        return originalFilters.stream()
            .map(this::createProxyForFilter)
            .collect(Collectors.toList());
      }
      return proxy.invoke(campaign, args);
    };
    return (Campaign) Enhancer.create(Campaign.class, handler);
  }

  /**
   * Create proxy for Filter object that intercept all methods that return SQL
   * names and escape it's return values
   */
  private Filter createProxyForFilter(Filter filter) {
    MethodInterceptor handler = (obj, method, args, proxy) -> {
      if (method.getName().equals("getField")) {
        Field originalResult = (Field) proxy.invoke(filter, args);
        return createProxyForField(originalResult);
      }
      return proxy.invoke(filter, args);
    };
    return (Filter) Enhancer.create(Filter.class, handler);
  }

  /**
   * Create proxy for Field object that intercept all methods that return SQL
   * names and escape it's return values
   */
  private Field createProxyForField(Field field) {
    MethodInterceptor handler = (obj, method, args, proxy) -> {
      if (method.getName().equals("getFieldName")) {
        String originalResult = (String) proxy.invoke(field, args);
        return "\"" + originalResult + "\"";
      }
      return proxy.invoke(field, args);
    };
    return (Field) Enhancer.create(Field.class, handler);
  }

  /**
   * Create proxy for AddPhoneNumberColumnsRequest object that intercept all
   * methods that return SQL names and escape it's return values
   */
  private AddPhoneNumberColumnsRequest createProxyForAddPhoneNumberColumnsRequest(AddPhoneNumberColumnsRequest request) {
    MethodInterceptor handler = (obj, method, args, proxy) -> {
      if (method.getName().endsWith("ColumnName") || method.getName().equals("getTableName")) {
        String originalResult = proxy.invoke(request, args).toString();
        if (!originalResult.startsWith("\"") && !originalResult.endsWith("\""))
          return "\"" + originalResult + "\"";
      }
      return proxy.invoke(request, args);
    };
    return (AddPhoneNumberColumnsRequest) Enhancer.create(AddPhoneNumberColumnsRequest.class, handler);
  }

  /**
   * Create proxy for each object in collection that intercept all methods that
   * return SQL names and escape it's return values
   */
  private Collection<?> createProxyForCollectionsObjects(Collection<?> collection) {
    Class<?> itemsType = getCollectionItemsType(collection);
    if (itemsType != null) {
      if (Filter.class.isAssignableFrom(itemsType)) {
        return ((Collection<Filter>) collection).stream()
            .filter(f -> !Enhancer.isEnhanced(f.getClass()))
            .map(this::createProxyForFilter)
            .collect(Collectors.toList());
      } else if (Map.class.isAssignableFrom(itemsType)) {
        return ((Collection<Map>) collection).stream()
            .filter(m -> !Enhancer.isEnhanced(m.getClass()))
            .map(this::createMapWithEscapedKeys)
            .collect(Collectors.toList());
      }
    }
    return collection;
  }

  /**
   * Create proxy for Call object that intercept all methods that return SQL
   * names and escape it's return values
   */
  private Call createProxyForCall(Call call) {
    MethodInterceptor handler = (obj, method, args, proxy) -> {
      if (method.getName().startsWith("getCampaign")) {
        Campaign campaign = (Campaign) proxy.invoke(call, args);
        return createProxyForCampaign(campaign);
      }
      return proxy.invoke(call, args);
    };
    return (Call) Enhancer.create(Call.class, handler);
  }

  /**
   * Create new map that has escaped keys
   * @param mapToEscape
   * @return
   */
  private Map<?, ?> createMapWithEscapedKeys(Map mapToEscape) {
    if (!CollectionUtils.isEmpty(mapToEscape)) {
      Map escapedMap;
      try {
        escapedMap = mapToEscape.getClass().newInstance();
      } catch (InstantiationException | IllegalAccessException e) {
        escapedMap = new HashMap();
      }
      for (Map.Entry entry : (Iterable<Map.Entry>) mapToEscape.entrySet()) {
        if (entry.getKey() instanceof String) {
          if (!isEscaped(entry.getKey().toString())) {
            escapedMap.put("\"" + entry.getKey() + "\"", entry.getValue());
            continue;
          }
        }
        escapedMap.put(entry.getKey(), entry.getValue());
      }
      return escapedMap;
    }
    return mapToEscape;
  }

  /**
   * Check is string already escaped
   * @param string
   * @return
   */
  private boolean isEscaped(String string) {
    return string != null && string.startsWith("\"") && string.endsWith("\"");
  }

}
