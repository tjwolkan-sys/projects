package com.powerdialer.validators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.powerdialer.exceptions.InvalidFilterRatioSumException;
import com.powerdialer.exceptions.NotEnoughSegmentationFiltersException;
import com.powerdialer.model.Filter;
import org.apache.commons.collections4.CollectionUtils;

import com.powerdialer.exceptions.CampaignIllegalStateException;
import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.exceptions.ModifiedWhilePrioritiesRecalculatingException;
import com.powerdialer.exceptions.ModifiedWhileUploadingException;
import com.powerdialer.exceptions.PhoneNumberNameDuplicateException;
import com.powerdialer.exceptions.SortConditionsOvercomplicatedException;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.SortField;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.Constants;

/**
 * Campaign validation class is used to make sure that information to be
 * persisted is valid
 */
public class CampaignValidator implements Validator<Campaign> {

  private CampaignService campaignService;

  public CampaignValidator() {}

  public void setCampaignService(CampaignService campaignService) {
    this.campaignService = campaignService;
  }

  public ValidationResult validate(Campaign newCampaign) throws DuplicatedNameException, CampaignIllegalStateException,
      SortConditionsOvercomplicatedException {
    if (newCampaign.getId() != null) {
      if (campaignService.getDataManipulationStateHolder().isDataUploading(newCampaign.getId())) {
        throw new ModifiedWhileUploadingException(newCampaign);
      } else if (campaignService.getPriorityStateHolder().isRecalculationInProgress(newCampaign.getId())) {
        throw new ModifiedWhilePrioritiesRecalculatingException(newCampaign);
      }
    }
    List<Campaign> campaigns = campaignService.getByName(newCampaign.getName(), false);
    checkDuplicateName(newCampaign, campaigns);
    Campaign oldCampaign = (newCampaign.getId() != null) ? campaignService.getInitializedCampaign(newCampaign.getId()) : null;
    checkFilterRatios(newCampaign, oldCampaign);
    if (CollectionUtils.isNotEmpty(newCampaign.getPhoneNumberColumns())) {
      checkCampaignPhoneNumberColumnNames(newCampaign, oldCampaign);
    }
    if (CollectionUtils.isNotEmpty(newCampaign.getSortFields())) {
      checkSortConditions(newCampaign, oldCampaign);
    }
    return new ValidationResult(true, new String[0]);
  }

  /**
   * Check for phone number duplicates
   * @param newCampaign
   * @param oldCampaign
   */
  private void checkCampaignPhoneNumberColumnNames(Campaign newCampaign, Campaign oldCampaign) {
    Map<Long, String> usedColumnNames = (oldCampaign != null)
        ? oldCampaign.getPhoneNumberColumns().stream().collect(Collectors.toMap(PhoneNumberColumn::getId, PhoneNumberColumn::getColNumber))
        : new HashMap<>();

    newCampaign.getPhoneNumberColumns().stream()
      .filter(PhoneNumberColumn::isDeleted)
      .map(PhoneNumberColumn::getColNumber)
      .forEach(deletedColumnName -> usedColumnNames.values().remove(deletedColumnName));

    Set<String> createdPhoneNumberColumnNames = new HashSet<>();
    for (PhoneNumberColumn phoneNumberColumn : newCampaign.getPhoneNumberColumns()) {
      if (!phoneNumberColumn.isDeleted() && phoneNumberColumn.getId() == null) {
        if (!createdPhoneNumberColumnNames.add(phoneNumberColumn.getColNumber())) {
          throw new PhoneNumberNameDuplicateException();
        }
      }
    }

    createdPhoneNumberColumnNames.retainAll(usedColumnNames.values());
    if (createdPhoneNumberColumnNames.size() > 0) {
      throw new PhoneNumberNameDuplicateException();
    }

    newCampaign.getPhoneNumberColumns().stream()
      .filter(pn -> !pn.isDeleted())
      .forEach(pn -> usedColumnNames.put(pn.getId(), pn.getColNumber()));

    if (usedColumnNames.size() != new HashSet<>(usedColumnNames.values()).size()) {
      throw new PhoneNumberNameDuplicateException();
    }
  }

  private void checkSortConditions(Campaign newCampaign, Campaign oldCampaign) {
    List<SortField> combinedFields;
    if (oldCampaign != null) {
      combinedFields = new ArrayList<>();
      Map<Long, SortField> existingSortFields = oldCampaign.getSortFields().stream()
          .collect(Collectors.toMap(SortField::getId, s -> s));
      newCampaign.getSortFields().forEach(s -> {
        if (s.isDeleted()) {
          existingSortFields.remove(s.getId());
        } else if (s.getId() != null) {
          existingSortFields.put(s.getId(), s);
        } else {
          combinedFields.add(s);
        }
      });
      combinedFields.addAll(existingSortFields.values());
    } else {
      combinedFields = newCampaign.getSortFields();
    }
    if (CollectionUtils.isNotEmpty(combinedFields) && combinedFields.stream()
        .filter(f -> f.getSortMethod() != Constants.MULTIPLE_SORT_METHOD.NATURAL_ORDER)
        .count() > Constants.MAX_PRIORITY_COLUMNS) {
      throw new SortConditionsOvercomplicatedException("campaign.sort_conditions.too_many_fields");
    };
  }

  /**
   * Check if campaign name duplicates
   * @param newCampaign
   * @throws DuplicatedNameException
   */
  private void checkDuplicateName(Campaign newCampaign, List<Campaign> campaigns) throws DuplicatedNameException {
    if (CollectionUtils.isNotEmpty(campaigns))
      for (Campaign campaign : campaigns)
        if (!campaign.equals(newCampaign))
          throw new DuplicatedNameException();
  }

  private void checkFilterRatios(Campaign newCampaign, Campaign oldCampaign) {
    List<Filter> combinedFilters = new ArrayList<>();
    if (oldCampaign != null && !oldCampaign.getFilters().isEmpty()) {
      Map<Long, Filter> oldFiltersMap = oldCampaign.getFilters().stream()
              .collect(Collectors.toMap(Filter::getId, f -> f));
      List<Filter> newCampaignFilters = newCampaign.getFilters() == null ? new ArrayList<>() : newCampaign.getFilters();
      for (Filter newFilter : newCampaignFilters) {
        if (newFilter.isDeleted()) {
          oldFiltersMap.remove(newFilter.getId());
        } else if (newFilter.getId() != null) {
          oldFiltersMap.put(newFilter.getId(), newFilter);
        } else {
          combinedFilters.add(newFilter);
        }
      }
      combinedFilters.addAll(oldFiltersMap.values());
    } else if (newCampaign.getFilters() != null) {
      combinedFilters.addAll(newCampaign.getFilters());
    }
    if (!combinedFilters.isEmpty()) {
      List<Filter> enabledFilters = combinedFilters.stream()
              .filter(filter -> filter.getSegmentation() && filter.isEnabled() && !filter.isDeleted())
              .collect(Collectors.toList());
      if (enabledFilters.isEmpty()) {
        return;
      }
      if (enabledFilters.size() == 1) {
        throw new NotEnoughSegmentationFiltersException("At least two segmentation filters are required if one is enabled");
      }
      int totalRatio = enabledFilters.stream()
              .mapToInt(f -> f.getRatio() != null ? f.getRatio() : 0)
              .sum();
      if (totalRatio != 100) {
        throw new InvalidFilterRatioSumException("Sum of enabled filter's segmentation ratios must be equal to 100%");
      }
    }
  }

}
