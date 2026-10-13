package com.powerdialer.datamanipulation.antiduplication;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test for SkipValueAntiDuplicationStrategy class
 */
public class SkipValueAntiDuplicationStrategyTest {

  /**
   * Test removing already checked values
   */
  @Test
  public void givenRowAndInsertedValuesWhenPerformAntiDuplicationThenRowDoNotContainsInsertedValues() {
    AntiDuplicationStrategy antiDuplicationStrategy = new SkipValueAntiDuplicationStrategy();
    Map<String, String> row = new HashMap<>();
    row.put("col1", "val1");
    row.put("col2", "val2");

    Map<?, ?> deduplicatedRow = antiDuplicationStrategy.performAntiDuplication(row,
                                                                               new HashSet<>(Arrays.asList("col1", "col2")),
                                                                               Collections.singleton("val1"));
    Assert.assertFalse(deduplicatedRow.containsKey("col1"));
    Assert.assertTrue(deduplicatedRow.containsKey("col2"));
  }

  /**
   * Test remaining row unchanged when no intersection with checked values
   */
  @Test
  public void givenRowAndInsertedValuesWhenPerformAntiDuplicationThenContainsAllValue() {
    AntiDuplicationStrategy antiDuplicationStrategy = new SkipValueAntiDuplicationStrategy();
    Map<String, String> row = new HashMap<>();
    row.put("col1", "val1");
    row.put("col2", "val2");

    Map<?, ?> deduplicatedRow = antiDuplicationStrategy.performAntiDuplication(row,
                                                                               Collections.singleton("col1"),
                                                                               Collections.singleton("val3"));
    Assert.assertTrue(deduplicatedRow.containsKey("col1"));
    Assert.assertTrue(deduplicatedRow.containsKey("col2"));
  }

  /**
   * Test remaining row unchanged when no intersection with checked keys
   */
  @Test
  public void givenRowAndInsertedValuesWhenPerformAntiDuplicationAndRowDoNotHaveCheckedKeysThenRowIsSkipped() {
    AntiDuplicationStrategy antiDuplicationStrategy = new SkipValueAntiDuplicationStrategy();
    Map<String, String> row = new HashMap<>();
    row.put("col1", "val1");
    row.put("col2", "val2");

    Map<?, ?> deduplicatedRow = antiDuplicationStrategy.performAntiDuplication(row,
                                                                               Collections.singleton("col3"),
                                                                               new HashSet<>(Arrays.asList("val3", null)));
    Assert.assertNull(deduplicatedRow);
  }

  /**
   * Test skipping row when all checked keys is duplicated
   */
  @Test
  public void givenRowAndInsertedValuesWhenPerformAntiDuplicationAndAllKeysExcludedThenRowIsSkipped() {
    AntiDuplicationStrategy antiDuplicationStrategy = new SkipValueAntiDuplicationStrategy();
    Map<String, String> row = new HashMap<>();
    row.put("col1", "val1");
    row.put("col2", "val2");
    row.put("col3", "val3");

    Map<?, ?> deduplicatedRow = antiDuplicationStrategy.performAntiDuplication(row,
                                                                               new HashSet<>(Arrays.asList("col1", "col2")),
                                                                               new HashSet<>(Arrays.asList("val1", "val2")));
    Assert.assertNull(deduplicatedRow);
  }

}
