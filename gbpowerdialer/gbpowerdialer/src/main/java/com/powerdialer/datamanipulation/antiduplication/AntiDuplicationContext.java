package com.powerdialer.datamanipulation.antiduplication;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;

/**
 * Class for holding data needed by DataUploadTool for performing anti-duplication.
 */
public class AntiDuplicationContext implements IAntiDuplicationContext {

  private final AntiDuplicationStrategy strategy;
  private final Set<String> columnsToCheck;
  private final Set<String> alreadyInsertedValues;

  AntiDuplicationContext(AntiDuplicationStrategy strategy,
                         Campaign campaign,
                         Set<String> alreadyInsertedValues) {
    this.strategy = strategy;
    this.columnsToCheck = campaign.getEnabledPhoneNumberColumns()
      .stream()
      .map(PhoneNumberColumn::getColNumber)
      .collect(Collectors.toSet());
    this.alreadyInsertedValues = alreadyInsertedValues;
  }

  @Override
  public Map<String, String> performAntiDuplication(Map<String, String> row) {
    Map<String, String> processedRow = strategy.performAntiDuplication(row, columnsToCheck, alreadyInsertedValues);
    columnsToCheck.forEach(columnName -> alreadyInsertedValues.add(row.get(columnName)));
    return processedRow;
  }

}
