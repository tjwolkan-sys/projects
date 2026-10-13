package com.powerdialer.aop;

import java.util.List;

import com.powerdialer.model.PredefinedValue;
import com.powerdialer.util.Constants.INPUT_VALUE_TYPE;

@FunctionalInterface
public interface PredefinedValueValidator {

  boolean apply(float value, INPUT_VALUE_TYPE valueTypeMin, INPUT_VALUE_TYPE valueTypeMax, List<PredefinedValue> predefinedValues);

}
