package com.powerdialer.datamanipulation.antiduplication;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class SkipRowAntiDuplicationStrategyTest {

  /**
   * Test removing row when it contains already inserted values
   */
  @Test
  public void givenRowAndInsertedValuesWhenPerformAntiDuplicationThenRowDoNotContainsInsertedValues() {
    AntiDuplicationStrategy antiDuplicationStrategy = new SkipRowAntiDuplicationStrategy();
    Map<String, String> row = new HashMap<>();
    row.put("col1", "val1");
    row.put("col2", "val2");

    Map<?, ?> deduplicatedRow = antiDuplicationStrategy.performAntiDuplication(row,
                                                                               Collections.singleton("col1"),
                                                                               Collections.singleton("val1"));
    Assert.assertNull(deduplicatedRow);
  }

  /**
   * Test keeping row when it do not contains checked keys
   */
  @Test
  public void givenRowAndInsertedValuesWhenPerformAntiDuplicationThenRowDoNotHaveCheckedKeys() {
    AntiDuplicationStrategy antiDuplicationStrategy = new SkipRowAntiDuplicationStrategy();
    Map<String, String> row = new HashMap<>();
    row.put("col1", "val1");
    row.put("col2", "val2");

    Map<?, ?> deduplicatedRow = antiDuplicationStrategy.performAntiDuplication(row,
                                                                               Collections.singleton("col3"),
                                                                               new HashSet<>(Arrays.asList("val1", null)));
    Assert.assertNull(deduplicatedRow);
  }

}