package com.powerdialer.engine.db;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.powerdialer.model.Field;
import com.powerdialer.model.Filter;
import com.powerdialer.model.Skill;
import com.powerdialer.util.Constants;

@RunWith(Parameterized.class)
public class FilterUtilsTest {

  private static Field id;
  private static Field isFetched;
  private static Field phoneNumber;
  private static Filter predefinedFilter1;
  private static Filter predefinedFilter2;
  private static Filter predefinedFilter3;
  private static Filter inConditionFilter1;
  private static Filter inConditionFilter2;
  private static Filter inConditionFilter3;
  private static Filter nullValueFilter1;
  private static Filter nullValueFilter2;
  private static Filter nullValueFilter3;
  private static List<Filter> predefinedFilters;
  private static List<Filter> inConditionFilters;
  private static List<Filter> nullValueFilters;
  private static final String whereClauseForPredefinedFilters = "(src.ID > '3' AND res.IS_FETCHED < 3 AND src.PHONE_NUMBER like '3')";
  private static final String whereClauseForInConditionFilters = "(src.ID in (to_date('3', 'yyyy-mm-dd hh:mi')) OR res.IS_FETCHED not in (3) OR src.PHONE_NUMBER in ('3'))";
  private static final String whereClauseForNullValueFilters = "(src.ID IS NULL AND res.IS_FETCHED IS NOT NULL AND src.PHONE_NUMBER IS NULL)";

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    initiateData();
    return Arrays.asList(new Object[][]{
        {predefinedFilters, Constants.FILTER_JOIN_TYPE.AND, "src.", "res.", Constants.DB_TYPE.MYSQL, whereClauseForPredefinedFilters},
        {inConditionFilters, Constants.FILTER_JOIN_TYPE.OR, "src.", "res.", Constants.DB_TYPE.ORACLE, whereClauseForInConditionFilters},
        {nullValueFilters, Constants.FILTER_JOIN_TYPE.AND, "src.", "res.", Constants.DB_TYPE.MS_SQL, whereClauseForNullValueFilters}
    });
  }

  private static void initiateData() {
    predefinedFilter1 = new Filter();
    inConditionFilter1 = new Filter();
    nullValueFilter1 = new Filter();
    predefinedFilter1.setType(Constants.FILTER_TYPE.PREDEFINED);
    inConditionFilter1.setType(Constants.FILTER_TYPE.PREDEFINED);
    nullValueFilter1.setType(Constants.FILTER_TYPE.PREDEFINED);
    id = new Field();
    id.setFieldName("ID");
    id.setFieldType(Constants.DB_FIELD_TYPES.DATETIME);
    predefinedFilter1.setField(id);
    inConditionFilter1.setField(id);
    nullValueFilter1.setField(id);
    predefinedFilter1.setCondition(Constants.FILTER_CONDITION.GREATER);
    predefinedFilter1.setValue("3");

    predefinedFilter2 = new Filter();
    inConditionFilter2 = new Filter();
    nullValueFilter2 = new Filter();
    predefinedFilter2.setType(Constants.FILTER_TYPE.PREDEFINED);
    inConditionFilter2.setType(Constants.FILTER_TYPE.PREDEFINED);
    nullValueFilter2.setType(Constants.FILTER_TYPE.PREDEFINED);
    predefinedFilter2.setTargetTable(Constants.FILTER_TARGET_TABLE.RESULT_TABLE);
    inConditionFilter2.setTargetTable(Constants.FILTER_TARGET_TABLE.RESULT_TABLE);
    nullValueFilter2.setTargetTable(Constants.FILTER_TARGET_TABLE.RESULT_TABLE);
    isFetched = new Field();
    isFetched.setFieldName("IS_FETCHED");
    isFetched.setFieldType(Constants.DB_FIELD_TYPES.INT);
    predefinedFilter2.setField(isFetched);
    inConditionFilter2.setField(isFetched);
    nullValueFilter2.setField(isFetched);
    predefinedFilter2.setCondition(Constants.FILTER_CONDITION.LESS);
    predefinedFilter2.setValue("3");

    predefinedFilter3 = new Filter();
    inConditionFilter3 = new Filter();
    nullValueFilter3 = new Filter();
    predefinedFilter3.setType(Constants.FILTER_TYPE.PREDEFINED);
    inConditionFilter3.setType(Constants.FILTER_TYPE.PREDEFINED);
    nullValueFilter3.setType(Constants.FILTER_TYPE.PREDEFINED);
    phoneNumber = new Field();
    phoneNumber.setFieldName("PHONE_NUMBER");
    phoneNumber.setFieldType(Constants.DB_FIELD_TYPES.VARCHAR);
    predefinedFilter3.setField(phoneNumber);
    inConditionFilter3.setField(phoneNumber);
    nullValueFilter3.setField(phoneNumber);
    predefinedFilter3.setCondition(Constants.FILTER_CONDITION.LIKE);
    predefinedFilter3.setValue("3");

    predefinedFilters = Arrays.asList(predefinedFilter1, predefinedFilter2, predefinedFilter3);

    inConditionFilter1.setCondition(Constants.FILTER_CONDITION.IN);
    inConditionFilter1.setValue("3");

    inConditionFilter2.setCondition(Constants.FILTER_CONDITION.NOTIN);
    inConditionFilter2.setValue("3");

    inConditionFilter3.setCondition(Constants.FILTER_CONDITION.IN);
    inConditionFilter3.setValue("3");

    inConditionFilters = Arrays.asList(inConditionFilter1, inConditionFilter2, inConditionFilter3);

    nullValueFilter1.setCondition(Constants.FILTER_CONDITION.EQUAL);
    nullValueFilter1.setValue("null");

    nullValueFilter2.setCondition(Constants.FILTER_CONDITION.NOTEQUAL);
    nullValueFilter2.setValue("null");

    nullValueFilter3.setCondition(Constants.FILTER_CONDITION.EQUAL);
    nullValueFilter3.setValue("null");

    nullValueFilters = Arrays.asList(nullValueFilter1, nullValueFilter2, nullValueFilter3);
  }

  private List<Filter> filters;
  private Constants.FILTER_JOIN_TYPE joinType;
  private String sourceAlias;
  private String resultAlias;
  private Constants.DB_TYPE dbType;
  private String expectedWhereClause;

  public FilterUtilsTest(List<Filter> filters,
                         Constants.FILTER_JOIN_TYPE joinType,
                         String sourceAlias,
                         String resultAlias,
                         Constants.DB_TYPE dbType,
                         String expectedWhereClause) {
    this.filters = filters;
    this.joinType = joinType;
    this.sourceAlias = sourceAlias;
    this.resultAlias = resultAlias;
    this.dbType = dbType;
    this.expectedWhereClause = expectedWhereClause;
  }

  @Test
  public void shouldCreateCorrectWhereClauseForFilters() throws Exception {
    assertEquals(expectedWhereClause, FilterUtils.createWhereFromFilters(filters, joinType, sourceAlias, resultAlias, dbType));
  }

  @Test
  public void shouldCreateFilterForSkills() throws Exception {
    List<Skill> skills = new ArrayList<>();
    Skill skill1 = new Skill();
    skill1.setName("skill1");
    Skill skill2 = new Skill();
    skill2.setName("skill2");
    skills.add(skill1);
    skills.add(skill2);
    String result = FilterUtils.createSkillFilter("testColumnName", skills);
    assertEquals("testColumnName IN ('skill1','skill2')", result);
  }

  @Test
  public void shouldReturnEmptyStringFilterForEmptySkillsList() throws Exception {
    List<Skill> skills = new ArrayList<>();
    String result = FilterUtils.createSkillFilter("testColumnName", skills);
    assertEquals("", result);
  }

}