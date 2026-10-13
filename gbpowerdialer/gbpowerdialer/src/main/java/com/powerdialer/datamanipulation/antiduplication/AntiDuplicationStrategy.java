package com.powerdialer.datamanipulation.antiduplication;

import java.util.Map;
import java.util.Set;

public interface AntiDuplicationStrategy {

  Map<String, String> performAntiDuplication(Map<String, String> row, Set<String> keysToCheck, Set<String> insertedValues);

}
