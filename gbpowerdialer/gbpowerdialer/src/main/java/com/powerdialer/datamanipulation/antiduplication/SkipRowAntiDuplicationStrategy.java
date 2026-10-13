package com.powerdialer.datamanipulation.antiduplication;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;

public class SkipRowAntiDuplicationStrategy implements AntiDuplicationStrategy {

  @Override
  public Map<String, String> performAntiDuplication(Map<String, String> row, Set<String> keysToCheck, Set<String> insertedValues) {
    if (Sets.intersection(row.keySet(), keysToCheck).isEmpty()) {
      return null;
    }
    boolean containsDuplicates = keysToCheck.stream()
      .anyMatch(key -> row.containsKey(key) && insertedValues.contains(row.get(key)));
    if (!containsDuplicates) {
      return row;
    }
    return null;
  }

}
