package com.powerdialer.aop;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PredefinedValue;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.Constants;

/**
 * Interceptor for merge method of CampaignDao to update allowed fields only by
 * user permission
 */
@Aspect
@Component
public class CampaignMergingAspect {

  @Autowired
  PermissionUtil permissionUtil;

  @Autowired
  CampaignService campaignService;

  @Autowired
  DBEngineFileLogger dbEngineFileLogger;

  @Around(value = "(execution(* com.powerdialer.dao.impl.GenericDao.merge(..))) && target(com.powerdialer.dao.ICampaignDao)", argNames = "joinPoint")
  public Object aroundMergingCampaign(ProceedingJoinPoint joinPoint) throws Throwable {
    Campaign campaignFromFrontEnd = (Campaign) joinPoint.getArgs()[0];
    Campaign campaignFromBackEnd = Optional.ofNullable(campaignService.getDialerFacade().getCampaignContexts().get(campaignFromFrontEnd.getId())).map(CampaignContext::getCampaign)
        .orElse(campaignService.getInitializedCampaign(campaignFromFrontEnd.getId()));

    if (!permissionUtil.hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN, Collections.singleton(campaignFromFrontEnd.getId().toString()))) {
      if (permissionUtil.hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN_TECH, Collections.singleton(campaignFromFrontEnd.getId().toString())))
        setTechCampaignFields(campaignFromBackEnd, campaignFromFrontEnd);
      else if (permissionUtil.hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN_NON_TECH, Collections.singleton(campaignFromFrontEnd.getId().toString())))
        setNonTechCampaignFields(campaignFromBackEnd, campaignFromFrontEnd);
      return joinPoint.proceed(new Object[] { campaignFromBackEnd });
    }

    List<PredefinedValue> predefinedValues = campaignFromFrontEnd.getPredefinedValues();
    if (!CollectionUtils.isEmpty(predefinedValues)) {
      PredefinedValueValidator pvv = (fieldValue, valueTypeMin, valueTypeMax, values) -> !isValueInPredefinedRange(fieldValue, valueTypeMin, valueTypeMax, values);
      validatePredefinedValues(campaignFromFrontEnd, campaignFromFrontEnd, campaignFromBackEnd, predefinedValues, pvv);
    }

    Object result = joinPoint.proceed(new Object[] { campaignFromFrontEnd });
    if (!campaignFromBackEnd.getName().equals(campaignFromFrontEnd.getName()))
      dbEngineFileLogger.resetLogger(campaignFromFrontEnd);
    return result;
  }

  private void setTechCampaignFields(Campaign campaignFromBackEnd, Campaign campaignFromFrontEnd) {
    setCommonTechAndNonTechCampaignFields(campaignFromBackEnd, campaignFromFrontEnd);
    campaignFromBackEnd.setCallsPerSecond(campaignFromFrontEnd.getCallsPerSecond());
    campaignFromBackEnd.setTimezoneType(campaignFromFrontEnd.getTimezoneType());
    campaignFromBackEnd.setColCallFromTime(campaignFromFrontEnd.getColCallFromTime());
    campaignFromBackEnd.setColCallToTime(campaignFromFrontEnd.getColCallToTime());
    campaignFromBackEnd.setColTimezoneOffset(campaignFromFrontEnd.getColTimezoneOffset());
    campaignFromBackEnd.setRetryOveralAttempt(campaignFromFrontEnd.getRetryOveralAttempt());
    campaignFromBackEnd.setRetryDelay(campaignFromFrontEnd.getRetryDelay());
    campaignFromBackEnd.setRetryRatio(campaignFromFrontEnd.getRetryRatio());
    campaignFromBackEnd.setMacroInterval(campaignFromFrontEnd.getMacroInterval());
    campaignFromBackEnd.setMacroMaxAttempt(campaignFromFrontEnd.getMacroMaxAttempt());
    campaignFromBackEnd.setSchedules(campaignFromFrontEnd.getSchedules());
    campaignFromBackEnd.setBlackoutSchedules(campaignFromFrontEnd.getBlackoutSchedules());
    campaignFromBackEnd.setScheduleEnabled(campaignFromFrontEnd.getScheduleEnabled());
    campaignFromBackEnd.setScheduleAutomations(campaignFromFrontEnd.getScheduleAutomations());
    campaignFromBackEnd.setScheduleAutomationEnabled(campaignFromFrontEnd.getScheduleAutomationEnabled());
    campaignFromBackEnd.setPredefinedValues(campaignFromFrontEnd.getPredefinedValues());
    if (campaignFromFrontEnd.getFilters() != null) {
      campaignFromBackEnd.setFilters(campaignFromFrontEnd.getFilters());
      campaignFromBackEnd.setFilterJoinType(campaignFromFrontEnd.getFilterJoinType());
      campaignFromBackEnd.setApplyFilterTo(campaignFromFrontEnd.getApplyFilterTo());
    }
  }

  private void setNonTechCampaignFields(Campaign campaignFromBackEnd, Campaign campaignFromFrontEnd) {
    setCommonTechAndNonTechCampaignFields(campaignFromBackEnd, campaignFromFrontEnd);
    if (campaignFromFrontEnd.getFilters() != null) {
      if (campaignFromBackEnd.getFilters() == null || campaignFromBackEnd.getFilters().stream().noneMatch(filter -> filter.getTechnical() != null && filter.getTechnical())) {
        campaignFromBackEnd.setFilterJoinType(campaignFromFrontEnd.getFilterJoinType());
        campaignFromBackEnd.setApplyFilterTo(campaignFromFrontEnd.getApplyFilterTo());
      }
      setFiltersIgnoringTechnical(campaignFromBackEnd, campaignFromFrontEnd);
    }
  }

  private void setCommonTechAndNonTechCampaignFields(Campaign campaignFromBackEnd, Campaign campaignFromFrontEnd) {
    campaignFromBackEnd.setSkillEnabled(campaignFromFrontEnd.getSkillEnabled());
    campaignFromBackEnd.setColSkill(campaignFromFrontEnd.getColSkill());
    campaignFromBackEnd.setSkills(campaignFromFrontEnd.getSkills());
    campaignFromBackEnd.setSortFields(campaignFromFrontEnd.getSortFields());
    campaignFromBackEnd.setVersion(campaignFromFrontEnd.getVersion());
    if (campaignFromFrontEnd.getCampaignGroups() != null)
      campaignFromBackEnd.setCampaignGroups(campaignFromFrontEnd.getCampaignGroups());
    List<PredefinedValue> predefinedValues = campaignFromBackEnd.getPredefinedValues();
    if (!CollectionUtils.isEmpty(predefinedValues)) {
      validatePredefinedValues(campaignFromFrontEnd, campaignFromBackEnd, campaignFromFrontEnd, predefinedValues, this::isValueInPredefinedRange);
    }
  }

  private void setFiltersIgnoringTechnical(Campaign campaignFromBackEnd, Campaign campaignFromFrontEnd) {
    campaignFromBackEnd.setFilters(campaignFromBackEnd.getFilters().stream().filter(filter -> filter.getTechnical() != null && filter.getTechnical()).collect(Collectors.toList()));
    campaignFromFrontEnd.getFilters().forEach(filterFromFrontEnd -> {
      if ((filterFromFrontEnd.getTechnical() == null || !filterFromFrontEnd.getTechnical()) && !campaignFromBackEnd.getFilters().contains(filterFromFrontEnd))
        campaignFromBackEnd.getFilters().add(filterFromFrontEnd);
    });
  }

  private boolean isValueInPredefinedRange(float fieldValue, Constants.INPUT_VALUE_TYPE valueTypeMin, Constants.INPUT_VALUE_TYPE valueTypeMax, List<PredefinedValue> predefinedValues) {
    PredefinedValue predefinedValueMin = extractPredefinedValueByType(valueTypeMin, predefinedValues);
    PredefinedValue predefinedValueMax = extractPredefinedValueByType(valueTypeMax, predefinedValues);
    if (predefinedValueMin != null && predefinedValueMax != null)
      return Float.parseFloat(predefinedValueMax.getValue()) >= fieldValue && fieldValue >= Float.parseFloat(predefinedValueMin.getValue());
    return true;
  }

  private PredefinedValue extractPredefinedValueByType(Constants.INPUT_VALUE_TYPE type, List<PredefinedValue> predefinedValues) {
    return predefinedValues.stream()
        .filter(predefinedValue -> predefinedValue.getType() == type && predefinedValue.isEnabled())
        .findFirst()
        .orElse(null);
  }

  private void validatePredefinedValues(Campaign toCompare, Campaign toChange, Campaign valueProvider, List<PredefinedValue> values, PredefinedValueValidator pvv) {
    if (pvv.apply(toCompare.getAbandonTimeout(), Constants.INPUT_VALUE_TYPE.ABANDON_TIMEOUT_MIN, Constants.INPUT_VALUE_TYPE.ABANDON_TIMEOUT_MAX, values))
      toChange.setAbandonTimeout(valueProvider.getAbandonTimeout());
    if (pvv.apply(toCompare.getConcurrentLines(), Constants.INPUT_VALUE_TYPE.CONCURRENT_LINES_MIN, Constants.INPUT_VALUE_TYPE.CONCURRENT_LINES_MAX, values))
      toChange.setConcurrentLines(valueProvider.getConcurrentLines());
    if (pvv.apply(toCompare.getDialingBoost(), Constants.INPUT_VALUE_TYPE.DIALING_BOOST_MIN, Constants.INPUT_VALUE_TYPE.DIALING_BOOST_MAX, values))
      toChange.setDialingBoost(valueProvider.getDialingBoost());
    if (pvv.apply(toCompare.getRingTimeout(), Constants.INPUT_VALUE_TYPE.RING_TIMEOUT_MIN, Constants.INPUT_VALUE_TYPE.RING_TIMEOUT_MAX, values))
      toChange.setRingTimeout(valueProvider.getRingTimeout());
  }

}
