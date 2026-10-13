package com.powerdialer.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.persistence.AttributeConverter;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MapConverter implements AttributeConverter<Map<Long, Long>, String> {

  private static final Logger LOG = LoggerFactory.getLogger(MapConverter.class);

  @Override
  public String convertToDatabaseColumn(Map<Long, Long> attribute) {
    if (!CollectionUtils.isEmpty(attribute)) {
      try {
        return new ObjectMapper().writeValueAsString(attribute);
      } catch (Exception e) {
        LOG.error("Could not convert map to string", e);
      }
    }
    return null;
  }

  @Override
  public Map<Long, Long> convertToEntityAttribute(String dbData) {
    if (!StringUtils.isBlank(dbData)) {
      try {
        return new ObjectMapper().readValue(dbData, new TypeReference<ConcurrentHashMap<Long, Long>>() {});
      } catch (Exception e) {
        LOG.error("Could not convert string to map: " + dbData, e);
      }
    }
    return new ConcurrentHashMap<>();
  }

}
