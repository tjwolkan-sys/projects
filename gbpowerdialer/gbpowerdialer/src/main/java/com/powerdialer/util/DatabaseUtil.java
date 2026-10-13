package com.powerdialer.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.powerdialer.datamanipulation.supplier.DataSupplier;
import com.powerdialer.model.Filter;
import com.powerdialer.request.DataManipulateRequest;

public class DatabaseUtil {

  private static final Logger LOG = LoggerFactory.getLogger(DatabaseUtil.class);

  /**
   * Validates the column names in the given source object based on the database type specified in the JDBC URL.
   * @param dbString the JDBC URL string
   * @param source   the source object to validate
   */
  public static void validateColumnNames(String dbString, Object source) {
    Constants.DB_TYPE dbType = getDatabaseType(dbString);
    if (source instanceof DataManipulateRequest) {
      validate(dbType, (DataManipulateRequest) source);
    } else if (source instanceof DataSupplier) {
      ((DataSupplier<?>) source).setQuerySafeMode(true);
      ((DataSupplier<?>) source).setDbType(dbType);
    } else if (source instanceof Map) {
      validate(dbType, (Map) source);
    } else if (source instanceof List && !((List) source).isEmpty() && ((List) source).get(0) instanceof Filter) {
      validate(dbType, (List<Filter>) source);
    }
  }

  public static void validate(Constants.DB_TYPE dbType, Map source) {
    try {
      Map<String, Object> updatedMap = new HashMap<>();
      for (String key : ((Map<String, Object>) source).keySet()) {
        updatedMap.put(getShieldedKey(key, dbType), ((Map<String, Object>) source).get(key));
      }
      ((Map<String, Object>) source).clear();
      ((Map<String, Object>) source).putAll(updatedMap);
    } catch (Exception e) {
      LOG.warn("Unable to validate column names", e);
    }
  }

  private static void validate(Constants.DB_TYPE dbType, DataManipulateRequest source) {
    try {
      validate(dbType, source.getData());
      for (DataManipulateRequest.Filter filter : source.getFilters()) {
        filter.setField(getShieldedKey(filter.getField(), dbType));
      }
    } catch (Exception e) {
      LOG.warn("Unable to validate filter names", e);
    }
  }

  private static void validate(Constants.DB_TYPE dbType, List<Filter> source) {
    try {
      for (Filter filter : source) {
        filter.getField().setFieldName(getShieldedKey(filter.getField().getFieldName(), dbType));
      }
    } catch (Exception e) {
      LOG.warn("Unable to validate filter names", e);
    }
  }

  private static String getShieldedKey(String key, Constants.DB_TYPE dbType) {
    String updatedKey;
    if (dbType.equals(Constants.DB_TYPE.MS_SQL)) {
      updatedKey = "[" + key + "]";
    } else {
      updatedKey = "\"" + key + "\"";
    }
    return updatedKey;
  }

  private static Constants.DB_TYPE getDatabaseType(String jdbcUrl) {
    if (jdbcUrl.startsWith("jdbc:sqlserver:")) {
      return Constants.DB_TYPE.MS_SQL;
    } else if (jdbcUrl.startsWith("jdbc:oracle:")) {
      return Constants.DB_TYPE.ORACLE;
    } else if (jdbcUrl.startsWith("jdbc:postgresql:")) {
      return Constants.DB_TYPE.POSTGRE_SQL;
    } else {
      throw new IllegalArgumentException("Unsupported JDBC URL: " + jdbcUrl);
    }
  }
}
