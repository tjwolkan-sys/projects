package com.powerdialer.datamanipulation.antiduplication;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;

public class SkipValueAntiDuplicationStrategy implements AntiDuplicationStrategy {

  @Override
  public Map<String, String> performAntiDuplication(Map<String, String> row, Set<String> keysToCheck, Set<String> insertedValues) {
    Map<String, String> resultRow = new LinkedHashMap<>();
    row.forEach((key, value) -> {
      if (!keysToCheck.contains(key) || !insertedValues.contains(value)) {
        resultRow.put(key, value);
      }
    });
    if (!Sets.intersection(resultRow.keySet(), keysToCheck).isEmpty()) {
      return resultRow;
    } else {
      return null;
    }
  }

}
