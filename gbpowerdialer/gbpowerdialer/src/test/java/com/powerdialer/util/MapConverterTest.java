package com.powerdialer.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class MapConverterTest {

  private MapConverter mapConverter = new MapConverter();

  @Test
  public void doubleConversionTest() {
    Map<Long, Long> map = new HashMap<>();
    map.put(1L, 100L);
    map.put(2L, 200L);

    String columnData = mapConverter.convertToDatabaseColumn(map);
    Map<Long, Long> resultMap = mapConverter.convertToEntityAttribute(columnData);

    assertThat(resultMap.get(1L), is(100L));
    assertThat(resultMap.get(2L), is(200L));
  }

  @Test
  public void givenNullValueWhenConvertToEntityAttributeThenReturnEmptyMap() {
    Map<Long, Long> result = mapConverter.convertToEntityAttribute(null);

    assertThat(result.isEmpty(), is(true));
  }

  @Test
  public void givenEmptyValueWhenConvertToEntityAttributeThenReturnEmptyMap() {
    Map<Long, Long> result = mapConverter.convertToEntityAttribute("");

    assertThat(result.isEmpty(), is(true));
  }

  @Test
  public void giveEmptyMapWhenConvertToDatabaseColumnThenReturnNull() {
    String result = mapConverter.convertToDatabaseColumn(new HashMap<>());

    assertThat(result, is(nullValue()));
  }

  @Test
  public void giveNullWhenConvertToDatabaseColumnThenReturnNull() {
    String result = mapConverter.convertToDatabaseColumn(null);

    assertThat(result, is(nullValue()));
  }

}
