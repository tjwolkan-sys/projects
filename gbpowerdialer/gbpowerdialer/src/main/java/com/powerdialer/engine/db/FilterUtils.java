package com.powerdialer.engine.db;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.powerdialer.model.Campaign;
import com.powerdialer.model.Field;
import com.powerdialer.model.Filter;
import com.powerdialer.model.Skill;
import com.powerdialer.util.Constants;

/**
 * Class for creating where clauses from filters based on join type, source alias and type of database
 */
public class FilterUtils {

  public static String createWhereFromFilters(Campaign campaign) {
    return createWhereFromFilters(campaign.getEnabledFilters(), Optional.empty(), campaign.getFilterJoinType(), CampaignDbOperations.SOURCE_TABLE_ALIAS_DOT,
        CampaignDbOperations.getResultAliasWithDot(campaign), Constants.DB_TYPE.getByConnectionString(campaign.getDbConnectionString()));
  }

  /**
   * Build a WHERE clause from the campaign's enabled filters, optionally dropping any filter
   * that targets a status column. Used by the denominator query (countActualListSize) so that
   * eliminating or completing records does not shrink the campaign goal (FIX-317277, Faz 2).
   *
   * Status columns: is_disabled (fixed), complete_date, is_fetched, attempts, callback_date,
   * retry_date (all campaign-configurable except is_disabled).
   */
  public static String createWhereFromFilters(Campaign campaign, boolean excludeStatusFilters) {
    List<Filter> filters = campaign.getEnabledFilters();
    if (excludeStatusFilters && !CollectionUtils.isEmpty(filters)) {
      Set<String> statusFields = getStatusFieldNames(campaign);
      filters = filters.stream()
          .filter(f -> !isStatusFilter(f, statusFields))
          .collect(Collectors.toList());
    }
    return createWhereFromFilters(filters, Optional.empty(), campaign.getFilterJoinType(), CampaignDbOperations.SOURCE_TABLE_ALIAS_DOT,
        CampaignDbOperations.getResultAliasWithDot(campaign), Constants.DB_TYPE.getByConnectionString(campaign.getDbConnectionString()));
  }

  public static String createWhereFromFilters(List<Filter> filters, Campaign campaign) {
    return createWhereFromFilters(filters, Optional.empty(), campaign.getFilterJoinType(), CampaignDbOperations.SOURCE_TABLE_ALIAS_DOT,
            CampaignDbOperations.getResultAliasWithDot(campaign), Constants.DB_TYPE.getByConnectionString(campaign.getDbConnectionString()));
  }

  public static String createWhereFromFilters(List<Filter> filters, Optional<Filter> segmentationFilter, Campaign campaign) {
    return createWhereFromFilters(filters, segmentationFilter, campaign.getFilterJoinType(), CampaignDbOperations.SOURCE_TABLE_ALIAS_DOT,
        CampaignDbOperations.getResultAliasWithDot(campaign), Constants.DB_TYPE.getByConnectionString(campaign.getDbConnectionString()));
  }

  /**
   * Create where clauses for query string by given filters
   * @return string of where clauses created from filters
   */
  public static String createWhereFromFilters(List<Filter> filters,
                                              Optional<Filter> segmentationFilter,
                                              Constants.FILTER_JOIN_TYPE joinType,
                                              String sourceAliasWithDot,
                                              String resultAliasWithDot,
                                              Constants.DB_TYPE dbType) {
    return createWhereFromFilters(filters, segmentationFilter, joinType, sourceAliasWithDot, resultAliasWithDot, dbType, false);
  }

  public static String createWhereFromFilters(List<Filter> filters,
                                              Constants.FILTER_JOIN_TYPE joinType,
                                              String sourceAliasWithDot,
                                              String resultAliasWithDot,
                                              Constants.DB_TYPE dbType) {
    return createWhereFromFilters(filters, Optional.empty(), joinType, sourceAliasWithDot, resultAliasWithDot, dbType, false);
  }

  public static String createWhereFromFiltersForPreparedStatement(List<Filter> filters,
                                                                  Constants.FILTER_JOIN_TYPE joinType,
                                                                  String sourceAliasWithDot,
                                                                  String resultAliasWithDot,
                                                                  Constants.DB_TYPE dbType) {
    return createWhereFromFilters(filters, Optional.empty(), joinType, sourceAliasWithDot, resultAliasWithDot, dbType, true);
  }

  private static String createWhereFromFilters(List<Filter> filters,
                                               Optional<Filter> segmentationFilter,
                                               Constants.FILTER_JOIN_TYPE joinType,
                                               String sourceAliasWithDot,
                                               String resultAliasWithDot,
                                               Constants.DB_TYPE dbType,
                                               boolean useValuePlaceholder) {
    if ((CollectionUtils.isEmpty(filters) && !segmentationFilter.isPresent()) || joinType == null) {
      return "";
    }
    StringBuilder whereClause = new StringBuilder();
    if (segmentationFilter.isPresent()) {
      Filter filter = segmentationFilter.get();
      String tableAliasWithDot = (filter.getTargetTable() == Constants.FILTER_TARGET_TABLE.RESULT_TABLE)
              ? resultAliasWithDot
              : sourceAliasWithDot;
      whereClause.append("(")
              .append(createWhereForFilter(filter, tableAliasWithDot, dbType, useValuePlaceholder))
              .append(")");
    }
    if (!CollectionUtils.isEmpty(filters)) {
      StringBuilder subClause = new StringBuilder();
      subClause.append("(");
      for (Filter filter : filters) {
        if (subClause.length() > 1) {
          subClause.append(" ").append(joinType).append(" ");
        }
        String tableAliasWithDot = (filter.getTargetTable() == Constants.FILTER_TARGET_TABLE.RESULT_TABLE)
                ? resultAliasWithDot
                : sourceAliasWithDot;
        subClause.append(createWhereForFilter(filter, tableAliasWithDot, dbType, useValuePlaceholder));
      }
      subClause.append(")");
      if (whereClause.length() > 0) {
        whereClause.append(" AND ");
      }
      whereClause.append(subClause);
    }
    return whereClause.toString();
  }


  /**
   * Create where clause for filter
   * @param filter
   * @param tableAliasWithDot
   * @param dbType
   * @param useValuePlaceholder - if set to true, then placeholder "?" will be
   *          used instead of value of filter
   * @return
   */
  private static String createWhereForFilter(Filter filter, String tableAliasWithDot, Constants.DB_TYPE dbType, boolean useValuePlaceholder) {
    if (filter.getType() == Constants.FILTER_TYPE.PREDEFINED)
      return createWhereForPredefinedType(filter, tableAliasWithDot, dbType, useValuePlaceholder);
    return filter.getValue();
  }

  private static String createWhereForPredefinedType(Filter filter, String tableAliasWithDot, Constants.DB_TYPE dbType, boolean useValuePlaceholder) {
    if (filter.getCondition() != null && filter.getCondition().isNullCheck())
      return createWhereForNullCheck(filter, tableAliasWithDot);

    if (filter.getValue() == null || "null".equalsIgnoreCase(filter.getValue()))
      return createWhereForNullValue(filter, tableAliasWithDot, dbType);

    if (filter.getCondition() == Constants.FILTER_CONDITION.IN || filter.getCondition() == Constants.FILTER_CONDITION.NOTIN)
      return createWhereForInCondition(filter, tableAliasWithDot, dbType, useValuePlaceholder);

    StringBuilder query = new StringBuilder(tableAliasWithDot);
    query.append(filter.getField().getFieldName()).append(" ").append(filter.getCondition()).append(" ");
    if (useValuePlaceholder) {
      query.append("?");
    } else {
      query.append(getValueRespectingType(filter.getField().getFieldType(), filter.getValue(), dbType));
    }
    return query.toString();
  }

  private static String createWhereForInCondition(Filter filter, String tableAliasWithDot, Constants.DB_TYPE dbType, boolean useValuePlaceholder) {
    StringBuilder filterClause = new StringBuilder(tableAliasWithDot);
    filterClause.append(filter.getField().getFieldName()).append(" ").append(filter.getCondition()).append(" (");
    if (!StringUtils.isEmpty(filter.getValue())) {
      String[] parameters = filter.getValue().split(",");
      for (int i = 0; i < parameters.length; i++) {
        if (useValuePlaceholder)
          filterClause.append("?" +  ((i + 1 < parameters.length) ? "," : ""));
        else {
          String value = getValueRespectingType(filter.getField().getFieldType(), parameters[i].trim(), dbType);
          filterClause.append((i == 0) ? value : ", " + value);
        }
      }
    }
    filterClause.append(")");
    return filterClause.toString();
  }

  private static String createWhereForNullValue(Filter filter, String tableAliasWithDot, Constants.DB_TYPE dbType) {
    StringBuilder filterClause = new StringBuilder(tableAliasWithDot).append(filter.getField().getFieldName());
    if (Constants.FILTER_CONDITION.EQUAL == filter.getCondition())
      filterClause.append(" IS ");
    if (Constants.FILTER_CONDITION.NOTEQUAL == filter.getCondition())
      filterClause.append(" IS NOT ");
    filterClause.append(getValueRespectingType(filter.getField().getFieldType(), "NULL", dbType));
    return filterClause.toString();
  }

  private static String createWhereForNullCheck(Filter filter, String tableAliasWithDot) {
    return new StringBuilder(tableAliasWithDot)
            .append(filter.getField().getFieldName())
            .append(" ")
            .append(filter.getCondition().condition())
            .toString();
  }

  /**
   * Quote or not given value. Decision make based on
   * {@link Constants#QUOTED_FIELD_TYPES} set
   * @param type
   * @param value
   * @return
   */
  private static String getValueRespectingType(Constants.DB_FIELD_TYPES type, String value, Constants.DB_TYPE dbType) {
    if (!isAlreadyQuoted(value) && shouldQuoteValue(type, value))
      return dbType == Constants.DB_TYPE.ORACLE && Constants.DATE_FIELD_TYPES.contains(type) ? "to_date('" + value + "', 'yyyy-mm-dd hh:mi')" : "'" + value + "'";
    else
      return value;
  }

  /**
   * Should the value for given type be quoted in generated query
   * @param type
   * @return
   */
  private static boolean shouldQuoteValue(Constants.DB_FIELD_TYPES type, String value) {
    return (type == null || Constants.QUOTED_FIELD_TYPES.contains(type)) && !"NULL".equalsIgnoreCase(value);
  }

  /**
   * Check is specified string already quoted
   * @param value
   * @return
   */
  private static boolean isAlreadyQuoted(String value) {
    return value.startsWith("'") && value.endsWith("'");
  }

  public static void addFetchedFilter(Campaign campaign, List<Filter> filters) {
    Filter fetchedFilter = new Filter();
    fetchedFilter.setType(Constants.FILTER_TYPE.FREE);
    fetchedFilter.setValue(CampaignDbOperations.getResultAliasWithDot(campaign) + campaign.getColResultFetchedDataFlag() + " IS NULL");
    filters.add(fetchedFilter);
  }

  /**
   * Build the set of column names that are considered "status" markers for FIX-317277, Faz 2.
   * Values may flow through the EscapeSqlNames aspect (Campaign proxy adds surrounding quotes
   * on Oracle/PostgreSQL), so the set is normalized via {@link #normalizeFieldName}.
   */
  private static Set<String> getStatusFieldNames(Campaign campaign) {
    Set<String> names = new HashSet<>();
    addStatusField(names, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.DISABLED.getValue());
    addStatusField(names, campaign.getColResultCompleteDate());
    addStatusField(names, campaign.getColResultFetchedDataFlag());
    addStatusField(names, campaign.getColResultAttempts());
    addStatusField(names, campaign.getColResultCallbackDate());
    addStatusField(names, campaign.getColResultRetryDate());
    return names;
  }

  private static void addStatusField(Set<String> names, String name) {
    String normalized = normalizeFieldName(name);
    if (normalized != null && !normalized.isEmpty()) {
      names.add(normalized);
    }
  }

  private static boolean isStatusFilter(Filter filter, Set<String> statusFields) {
    Field field = filter.getField();
    if (field == null) {
      return false;
    }
    String normalized = normalizeFieldName(field.getFieldName());
    return normalized != null && statusFields.contains(normalized);
  }

  /**
   * Strip surrounding double quotes (added by EscapeSqlNames aspect for Oracle/PostgreSQL),
   * trim whitespace and lowercase. Lets status-field comparisons survive escaping and minor
   * user typos like trailing spaces or mixed case.
   */
  private static String normalizeFieldName(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
    }
    return trimmed.toLowerCase();
  }

  public static String createSkillFilter(String columnName, Collection<Skill> skills) {
    if (!CollectionUtils.isEmpty(skills)) {
      String joinedSkills = skills.stream()
          .map(Skill::getName)
          .map(s -> "'" + s + "'")
          .distinct()
          .collect(Collectors.joining(",", "(", ")"));
      if (!"()".equals(joinedSkills)) {
        return columnName + " IN " + joinedSkills;
      }
    }
    return "";
  }

}
