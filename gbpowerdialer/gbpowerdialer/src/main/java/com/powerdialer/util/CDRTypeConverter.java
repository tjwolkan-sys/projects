package com.powerdialer.util;

import javax.persistence.AttributeConverter;

public class CDRTypeConverter implements AttributeConverter<Constants.CDR_OUTDIALING_TYPE, Long> {

  @Override
  public Long convertToDatabaseColumn(Constants.CDR_OUTDIALING_TYPE attribute) {
    return attribute.getId();
  }

  @Override
  public Constants.CDR_OUTDIALING_TYPE convertToEntityAttribute(Long dbData) {
    return Constants.CDR_OUTDIALING_TYPE.parse(dbData);
  }

}
