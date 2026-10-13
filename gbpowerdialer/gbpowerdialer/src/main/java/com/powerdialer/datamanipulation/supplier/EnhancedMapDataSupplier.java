package com.powerdialer.datamanipulation.supplier;

import java.util.Map;
import java.util.Objects;

import java.io.IOException;

/**
 * Enhance original DataSupplier with additional data
 */
public class EnhancedMapDataSupplier extends DataSupplier<Map> {

  private final DataSupplier<Map> originalDataSupplier;
  private final Map<?, ?> dataForEnhancing;

  public EnhancedMapDataSupplier(DataSupplier<Map> originalDataSupplier,
                                 Map<?, ?> dataForEnhancing) {
    Objects.requireNonNull(originalDataSupplier);
    Objects.requireNonNull(dataForEnhancing);
    this.originalDataSupplier = originalDataSupplier;
    this.dataForEnhancing = dataForEnhancing;
  }

  @Override
  protected Map getNextObjectInternal() throws IOException {
    Map nextObject = originalDataSupplier.getNextObject();
    if (nextObject != null) {
      nextObject.putAll(dataForEnhancing);
    }
    return nextObject;
  }

}
