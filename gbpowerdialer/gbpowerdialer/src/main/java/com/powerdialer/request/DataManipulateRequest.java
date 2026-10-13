package com.powerdialer.request;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

import com.powerdialer.dto.FieldDTO;
import com.powerdialer.dto.FilterListDTO;
import com.powerdialer.util.Constants;

/**
 * Request for data manipulating
 */
public class DataManipulateRequest {

  private List<Filter> filters;
  private Map<String, Object> data;

  public List<Filter> getFilters() {
    return filters;
  }

  public void setFilters(List<Filter> filters) {
    this.filters = filters;
  }

  public Map<String, Object> getData() {
    return data;
  }

  public void setData(Map<String, Object> data) {
    this.data = data;
  }

  /**
   * The same as {@link FilterListDTO} but field {@link Filter#condition}
   * converted string. Field {@link Filter#condition} should has value which can
   * be parsed by
   * {@link com.powerdialer.util.Constants.FILTER_CONDITION#parse(String)}
   */
  public static class Filter {

    private String field;
    private String value;
    private String condition;

    /**
     * Just convert {@link Filter#condition} filed from string representation to
     * ordinal
     */
    public FilterListDTO convertToFilterDto() throws ParseException {
      FilterListDTO filterListDTO = new FilterListDTO();
      filterListDTO.setField(new FieldDTO(getField()));
      filterListDTO.setValue(getValue());
      filterListDTO.setCondition(Constants.FILTER_CONDITION.parse(getCondition()).ordinal());
      return filterListDTO;
    }

    public String getField() {
      return field;
    }

    public void setField(String field) {
      this.field = field;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }

    public String getCondition() {
      return condition;
    }

    public void setCondition(String condition) {
      this.condition = condition;
    }

    @Override
    public String toString() {
      return "{" +
        "field:\"" + field + '\"' +
        ", value:\"" + value + '\"' +
        ", condition:\"" + condition + '\"' +
        '}';
    }
  }

}
