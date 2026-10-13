package com.powerdialer.datamanipulation.antiduplication;

import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;

/**
 * Test for AntiDuplicationContext class
 */
public class AntiDuplicationContextTest {

  /**
   * Test updating already inserted values after processing
   */
  @Test
  public void givenStrategyWhenPerformAntiDuplicationThenAlreadyInsertedValuesFilled() {
    Campaign campaign = new Campaign();
    campaign.setId(1L);
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("col_number");
    phoneNumberColumn.setEnabled(true);
    campaign.setPhoneNumberColumns(Collections.singletonList(phoneNumberColumn));
    AntiDuplicationStrategy antiDuplicationStrategyMock = Mockito.mock(AntiDuplicationStrategy.class);
    Set<String> alreadyInsertedValues = new HashSet<>();
    IAntiDuplicationContext IAntiDuplicationContext = new AntiDuplicationContext(antiDuplicationStrategyMock, campaign, alreadyInsertedValues);
    Map<String, String> row = new HashMap<>();
    row.put("col1", "val1");
    row.put("col2", "val2");
    row.put("col_number", "123");
    doReturn(row).when(antiDuplicationStrategyMock).performAntiDuplication(any(), any(), any());
    Map<String, String> result = IAntiDuplicationContext.performAntiDuplication(row);
    Assert.assertTrue(alreadyInsertedValues.contains("123"));
  }

  /**
   * Test skipping rows with already inserted values after processing
   */
  @Test
  public void givenStrategyWhenPerformAntiDuplicationThenRowSkippedWithAlreadyInsertedValues() {
    Campaign campaign = new Campaign();
    campaign.setId(1L);
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("col_number");
    phoneNumberColumn.setEnabled(true);
    campaign.setPhoneNumberColumns(Collections.singletonList(phoneNumberColumn));
    AntiDuplicationStrategy antiDuplicationStrategyMock = Mockito.mock(AntiDuplicationStrategy.class);
    Set<String> alreadyInsertedValues = new HashSet<>();
    IAntiDuplicationContext antiDuplicationContext = new AntiDuplicationContext(antiDuplicationStrategyMock, campaign, alreadyInsertedValues);
    Map<String, String> row = new HashMap<>();
    row.put("col1", "val1");
    row.put("col2", "val2");
    row.put("col_number", "123");
    doReturn(null).when(antiDuplicationStrategyMock).performAntiDuplication(any(), any(), any());
    Assert.assertNull(antiDuplicationContext.performAntiDuplication(row));
  }

}
