package com.powerdialer.validators;

import static com.powerdialer.util.Constants.DECIMAL_FIELD_TYPES;
import static com.powerdialer.util.Constants.INTEGER_FIELD_TYPES;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.powerdialer.util.Constants;

/**
 * Validate values of maps. Values must be string in valid format
 */
public class MapValuesTypesValidator implements Validator<Map<String, String>> {

  private static final Pattern DECIMAL_REGEX = Pattern.compile("-?\\d+(\\.\\d+)?");
  private static final Pattern INTEGER_REGEX = Pattern.compile("-?\\d+");

  private final Map<String, Constants.DB_FIELD_TYPES> columnsToTypes;

  public MapValuesTypesValidator(Map<String, Constants.DB_FIELD_TYPES> columnsToTypes) {
    this.columnsToTypes = columnsToTypes;
  }

  @Override
  public ValidationResult validate(Map<String, String> row) throws RuntimeException {
    boolean isAllValuesValid = true;
    List<String> validationMessages = new ArrayList<>();
    for (Map.Entry<String, String> rowEntry : row.entrySet()) {
      String columnName = rowEntry.getKey();
      String columnValue = rowEntry.getValue();
      Constants.DB_FIELD_TYPES dbFieldType = columnsToTypes.get(columnName);
      if (INTEGER_FIELD_TYPES.contains(dbFieldType)) {
        boolean isInteger = INTEGER_REGEX.matcher(columnValue).matches();
        isAllValuesValid &= isInteger;
        if (!isInteger) {
          validationMessages.add("Column " + columnName + " must be integer");
        }
      } else if (DECIMAL_FIELD_TYPES.contains(dbFieldType)) {
        boolean isDecimal = DECIMAL_REGEX.matcher(columnValue).matches();
        isAllValuesValid &= isDecimal;
        if (!isDecimal) {
          validationMessages.add("Column " + columnName + " must be decimal");
        }
      }
    }
    return new ValidationResult(isAllValuesValid, validationMessages.toArray(new String[]{}));
  }
}
