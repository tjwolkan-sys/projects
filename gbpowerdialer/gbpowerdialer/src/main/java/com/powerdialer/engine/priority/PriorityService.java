package com.powerdialer.engine.priority;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.SortField;
import com.powerdialer.model.SortFieldValue;
import com.powerdialer.util.Constants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
public class PriorityService {

  public void getPriorityTableCreationQuery(Campaign campaign, StringBuilder query, String intType, String bigintType) {
    query.append("CREATE TABLE ").append(campaign.getDbPriorityTable()).append(" (");
    query.append(campaign.getColPriorityUniqueKey()).append(bigintType);
    query.deleteCharAt(query.length() - 1).append(" NOT NULL PRIMARY KEY,");
    IntStream.range(Constants.MIN_PRIORITY_COLUMN_INDEX, Constants.MAX_PRIORITY_COLUMNS + 1).forEach(i ->
        query.append(campaign.getColPriorityField(i)).append(intType));
    query.deleteCharAt(query.length() - 1);
    query.append(")");
  }

  public void addFieldsCasesToRecalculationQuery(List<SortField> sortFields, Campaign campaign, StringBuilder query) {
    int priorityColumnIndex = Constants.MIN_PRIORITY_COLUMN_INDEX;
    for (SortField sortField : sortFields) {
      String columnName = campaign.getColSortField(sortField);
      if (sortField.getSortMethod() == Constants.MULTIPLE_SORT_METHOD.BOOLEAN) {
        query.append(" ").append(campaign.getColPriorityField(priorityColumnIndex)).append(" = CASE WHEN ")
            .append(columnName).append(" IS NOT NULL AND ").append(columnName).append(" <> '0' AND ")
            .append(columnName).append(" <> '' THEN 1 ELSE 0 END,");
        priorityColumnIndex++;
      } else if (sortField.getSortMethod() == Constants.MULTIPLE_SORT_METHOD.DICTIONARY) {
        List<SortFieldValue> values = getEnabledValuesStream(sortField.getValues()).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(values)) {
          query.append(" ").append(campaign.getColPriorityField(priorityColumnIndex)).append(" = CASE");
          for (SortFieldValue value : values) {
            query.append(" WHEN ").append(columnName).append(" = '").append(value.getValue()).append("' THEN ").append(value.getPriority());
          }
          query.append(" ELSE 0 END,");
          priorityColumnIndex++;
        }
      }
    }
    query.deleteCharAt(query.length() - 1);
  }

  public String getOrderByClause(Campaign campaign) {
    List<SortField> sortFields = getOrderedSortFields(campaign);
    if (CollectionUtils.isEmpty(sortFields) || StringUtils.isBlank(campaign.getDbPriorityTable())) {
      return "";
    }
    StringBuilder query = new StringBuilder(" ORDER BY");
    int priorityColumnIndex = 1;
    for (SortField sortField : sortFields) {
      if (sortField.getSortMethod() == Constants.MULTIPLE_SORT_METHOD.NATURAL_ORDER) {
        query.append(" ").append(CampaignDbOperations.SOURCE_TABLE_ALIAS_DOT).append(campaign.getColSortField(sortField));
      } else {
        query.append(" ").append(CampaignDbOperations.PRIORITY_TABLE_ALIAS_DOT).append(campaign.getColPriorityField(priorityColumnIndex));
        priorityColumnIndex++;
      }
      query.append(" ").append((sortField.getSortDirection().toString())).append(",");
    }
    return query.deleteCharAt(query.length() - 1).toString();
  }

  public Stream<SortFieldValue> getEnabledValuesStream(List<SortFieldValue> values) {
    return CollectionUtils.isEmpty(values)
        ? Stream.empty()
        : values.stream()
            .filter(value -> value.isEnabled() && !value.isDeleted());
  }

  public Stream<SortField> getEnabledFieldsStream(Campaign campaign) {
    return CollectionUtils.isEmpty(campaign.getSortFields())
        ? Stream.empty()
        : campaign.getSortFields().stream()
            .filter(value -> value.isEnabled() && !value.isDeleted());
  }

  public List<SortField> getOrderedSortFields(Campaign campaign) {
    return getEnabledFieldsStream(campaign)
        .sorted(Comparator.comparingInt(SortField::getPriority).reversed())
        .collect(Collectors.toList());
  }

}
