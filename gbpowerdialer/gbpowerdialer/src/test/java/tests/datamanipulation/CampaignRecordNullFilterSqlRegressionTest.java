package tests.datamanipulation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.powerdialer.dto.CampaignPriorityFilterFormDTO;
import com.powerdialer.service.CampaignRecordService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

public class CampaignRecordNullFilterSqlRegressionTest {

  @Test
  public void campaignRecordFilterBuilderRendersNullChecksWithoutValues() throws Exception {
    CampaignRecordService service = newService();
    List<String> conditions = new ArrayList<>();

    Method method = CampaignRecordService.class.getDeclaredMethod(
        "prepareConditionFilters",
        String.class,
        String.class,
        String.class,
        String.class,
        List.class);
    method.setAccessible(true);

    method.invoke(service, null, "is null", "user_result_code", "INT", conditions);
    method.invoke(service, "", "is not null", "callback_date", "DATETIME", conditions);

    assertEquals(Arrays.asList("user_result_code is null", "callback_date is not null"), conditions);
  }

  @Test
  public void priorityFilterBuilderDoesNotBindParametersForNullChecks() throws Exception {
    CampaignRecordService service = newService();
    CampaignPriorityFilterFormDTO isNullFilter = new CampaignPriorityFilterFormDTO();
    isNullFilter.setField("user_result_code");
    isNullFilter.setCondition(Constants.FILTER_CONDITION.ISNULL);
    isNullFilter.setValue(null);

    CampaignPriorityFilterFormDTO isNotNullFilter = new CampaignPriorityFilterFormDTO();
    isNotNullFilter.setField("callback_date");
    isNotNullFilter.setCondition(Constants.FILTER_CONDITION.ISNOTNULL);
    isNotNullFilter.setValue("");

    Method method = CampaignRecordService.class.getDeclaredMethod("buildWhereClauseWithParams", List.class, String.class);
    method.setAccessible(true);
    Object clause = method.invoke(service, Arrays.asList(isNullFilter, isNotNullFilter), "AND");

    assertEquals("user_result_code is null AND callback_date is not null", readField(clause, "sql"));
    assertTrue(((List<?>) readField(clause, "params")).isEmpty());
  }

  private CampaignRecordService newService() {
    return new CampaignRecordService(new LoggerDecorator(), null, null, null, null);
  }

  private Object readField(Object target, String fieldName) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
  }
}
