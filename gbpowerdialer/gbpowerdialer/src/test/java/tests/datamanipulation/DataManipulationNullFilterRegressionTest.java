package tests.datamanipulation;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.FilterUtils;
import com.powerdialer.model.Field;
import com.powerdialer.model.Filter;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

public class DataManipulationNullFilterRegressionTest {

  @Test
  public void filterConditionParseAcceptsNullCheckTokens() throws ParseException {
    assertEquals(Constants.FILTER_CONDITION.ISNULL, Constants.FILTER_CONDITION.parse("is null"));
    assertEquals(Constants.FILTER_CONDITION.ISNULL, Constants.FILTER_CONDITION.parse("ISNULL"));
    assertEquals(Constants.FILTER_CONDITION.ISNULL, Constants.FILTER_CONDITION.parse("IS_NULL"));

    assertEquals(Constants.FILTER_CONDITION.ISNOTNULL, Constants.FILTER_CONDITION.parse("is not null"));
    assertEquals(Constants.FILTER_CONDITION.ISNOTNULL, Constants.FILTER_CONDITION.parse("ISNOTNULL"));
    assertEquals(Constants.FILTER_CONDITION.ISNOTNULL, Constants.FILTER_CONDITION.parse("IS_NOT_NULL"));
  }

  @Test
  public void filterUtilsRendersExplicitNullChecksWithoutValues() {
    List<Filter> filters = Arrays.asList(
        predefined("USER_RESULT_CODE", Constants.FILTER_CONDITION.ISNULL, null),
        predefined("CALLBACK_DATE", Constants.FILTER_CONDITION.ISNOTNULL, "")
    );

    String where = FilterUtils.createWhereFromFilters(
        filters,
        Constants.FILTER_JOIN_TYPE.AND,
        "src.",
        "res.",
        Constants.DB_TYPE.MS_SQL);

    assertEquals("(src.USER_RESULT_CODE is null AND src.CALLBACK_DATE is not null)", where);
  }

  @Test
  public void filterUtilsPreparedStatementRendersNullChecksWithoutPlaceholders() {
    List<Filter> filters = Arrays.asList(
        predefined("USER_RESULT_CODE", Constants.FILTER_CONDITION.ISNULL, null),
        predefined("CALLBACK_DATE", Constants.FILTER_CONDITION.ISNOTNULL, null)
    );

    String where = FilterUtils.createWhereFromFiltersForPreparedStatement(
        filters,
        Constants.FILTER_JOIN_TYPE.AND,
        "src.",
        "res.",
        Constants.DB_TYPE.MS_SQL);

    assertEquals("(src.USER_RESULT_CODE is null AND src.CALLBACK_DATE is not null)", where);
  }

  @Test
  public void campaignDbOperationsDoesNotCollectBindValuesForNullChecks() throws Exception {
    List<Filter> filters = Arrays.asList(
        predefined("USER_RESULT_CODE", Constants.FILTER_CONDITION.ISNULL, null),
        predefined("CALLBACK_DATE", Constants.FILTER_CONDITION.ISNOTNULL, ""),
        predefined("TELEPHONY_RESULT_CODE", Constants.FILTER_CONDITION.EQUAL, "10")
    );

    CampaignDbOperations operations = new CampaignDbOperations(null, new LoggerDecorator(), null, null, null);
    Method method = CampaignDbOperations.class.getDeclaredMethod("getNonNullValuesFromPredefinedFilters", List.class);
    method.setAccessible(true);

    assertEquals(Arrays.<Object>asList("10"), method.invoke(operations, filters));
  }

  private Filter predefined(String fieldName, Constants.FILTER_CONDITION condition, String value) {
    Field field = new Field();
    field.setFieldName(fieldName);
    field.setFieldType(Constants.DB_FIELD_TYPES.INT);

    Filter filter = new Filter();
    filter.setType(Constants.FILTER_TYPE.PREDEFINED);
    filter.setField(field);
    filter.setCondition(condition);
    filter.setValue(value);
    return filter;
  }
}
