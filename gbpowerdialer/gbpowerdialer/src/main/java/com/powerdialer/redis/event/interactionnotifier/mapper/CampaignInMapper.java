package com.powerdialer.redis.event.interactionnotifier.mapper;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.redis.event.interactionnotifier.dto.CampaignInDTO;

public final class CampaignInMapper {

    private CampaignInMapper() {}

    public static CampaignInDTO toDto(Campaign campaign,Call call) {
        if (campaign == null) {
            return null;
        }

        CampaignInDTO.CampaignInDTOBuilder builder = CampaignInDTO.builder();

        mapBasicFields(builder, campaign);
        mapDateFields(builder, campaign);
        mapPreviewFields(builder, campaign);
        mapRetryFields(builder, campaign);
        mapCallerIdFields(builder, campaign);
        mapDialingFields(builder, campaign);
        mapScheduleFields(builder, campaign);
        mapDncFields(builder, campaign);
        mapStopConditionFields(builder, campaign);
        mapApiFields(builder, campaign);
        mapCallResult(builder,call);

        return builder.build();
    }

    private static void mapBasicFields(CampaignInDTO.CampaignInDTOBuilder builder, Campaign campaign) {
        builder.campaign_id(campaign.getId())
                .tenantId(campaign.getTenant() != null ? campaign.getTenant().getId() : null)
                .campaign_name(campaign.getName())
                .description(campaign.getDescription())
                .type(campaign.getType())
                .enabled(campaign.isEnabled())
                .deleted(campaign.isDeleted())
                .context(campaign.getContext())
                .abandonIvr(campaign.getAbandonIvr())
                .ivrApplicationId(campaign.getIvrApplication() != null ? campaign.getIvrApplication().getId() : null)
                .version(campaign.getVersion());
    }

    private static void mapDateFields(CampaignInDTO.CampaignInDTOBuilder builder, Campaign campaign) {
        builder.startDate(campaign.getStartDate())
                .endDate(campaign.getEndDate())
                .stoppedDate(campaign.getStoppedDate())
                .createdDate(campaign.getCreateDate())
                .updatedDate(campaign.getUpdateDate());
    }

    private static void mapPreviewFields(CampaignInDTO.CampaignInDTOBuilder builder, Campaign campaign) {
        builder.previewTimeout(campaign.getPreviewTimeout())
                .previewRelax(campaign.getPreviewRelax())
                .availableThreshold(campaign.getAvailableThreshold())
                .allowCallback(campaign.getAllowCallback())
                .allowUrcWithoutMt(campaign.getAllowUrcWithoutMt())
                .allowPreviewBeep(campaign.getAllowPreviewBeep())
                .allowCallbackCustom(campaign.getAllowCallbackCustom())
                .callbackToAppointer(campaign.getCallbackToAppointer())
                .allowOverwriteByWebphone(campaign.getAllowOverwriteByWebphone())
                .appointerReset(campaign.getAppointerReset())
                .appointerResetTimeout(campaign.getAppointerResetTimeout())
                .appointerResetStrategy(campaign.getAppointerResetStrategy())
                .agentCbRescheduleDelayMin(campaign.getAgentCbRescheduleDelayMin())
                .agentCbConvertAfterAttempts(campaign.getAgentCbConvertAfterAttempts())
                .endListBehaviour(campaign.getEndListBehaviour())
                .lowCacheSelectInterval(campaign.getLowCacheSelectInterval());
    }

    private static void mapRetryFields(CampaignInDTO.CampaignInDTOBuilder builder, Campaign campaign) {
        builder.retryStrategy(campaign.getRetryStrategy())
                .retryScheduleEnabled(campaign.getRetryScheduleEnabled())
                .retryOveralAttempt(campaign.getRetryOveralAttempt())
                .macroInterval(campaign.getMacroInterval())
                .macroMaxAttempt(campaign.getMacroMaxAttempt())
                .maxAttemptPerNumber(campaign.getMaxAttemptPerNumber())
                .retryRatio(campaign.getRetryRatio())
                .retryDelay(campaign.getRetryDelay())
                .retryCallerIdBehavior(campaign.getRetryCallerIdBehavior());
    }

    private static void mapCallerIdFields(CampaignInDTO.CampaignInDTOBuilder builder, Campaign campaign) {
        builder.callerIdBehaviour(campaign.getCallerIdBehaviour())
                .callerId(campaign.getCallerId())
                .callerIdPoolId(campaign.getCallerIdPool() != null ? campaign.getCallerIdPool().getId() : null)
                .timezoneType(campaign.getTimezoneType())
                .applyFilterTo(campaign.getApplyFilterTo())
                .filterJoinType(campaign.getFilterJoinType());
    }

    private static void mapDialingFields(CampaignInDTO.CampaignInDTOBuilder builder, Campaign campaign) {
        builder.concurrentLines(campaign.getConcurrentLines())
                .callsPerSecond(campaign.getCallsPerSecond())
                .cacheLimit(campaign.getCacheLimit())
                .ringTimeout(campaign.getRingTimeout())
                .dialingBoost(campaign.getDialingBoost())
                .abandonTimeout(campaign.getAbandonTimeout())
                .amdEnabled(campaign.getAmdEnabled())
                .maxAnswerRate(campaign.getMaxAnswerRate())
                .maxAbandonRate(campaign.getMaxAbandonRate())
                .gammaCorrectionFactor(campaign.getGammaCorrectionFactor())
                .gammaAbandonSensitivity(campaign.getGammaAbandonSensitivity())
                .gammaWindowCapacity(campaign.getGammaWindowCapacity())
                .gammaRequiredWindowSize(campaign.getGammaRequiredWindowSize())
                .predictiveStrategy(campaign.getPredictiveStrategy())
                .mohClassCallingParty(campaign.getMohClassCallingParty())
                .startupDbCheck(campaign.getStartupDbCheck())
                .resultCodeTimerStatus(campaign.getResultCodeTimerStatus())
                .resultCodeTimer(campaign.getResultCodeTimer());
    }

    private static void mapScheduleFields(CampaignInDTO.CampaignInDTOBuilder builder, Campaign campaign) {
        builder.scheduleEnabled(campaign.getScheduleEnabled())
                .scheduleAutomationEnabled(campaign.getScheduleAutomationEnabled());
    }

    private static void mapDncFields(CampaignInDTO.CampaignInDTOBuilder builder, Campaign campaign) {
        builder.dncEnabled(campaign.getDncEnabled())
                .dncId(campaign.getDnc() != null ? campaign.getDnc().getId() : null)
                .lscEnabled(campaign.getLscEnabled())
                .lscTimeoutBehavior(campaign.getLscTimeoutBehavior())
                .fullContactListCheckEnabled(campaign.getFullContactListCheckEnabled())
                .skillEnabled(campaign.getSkillEnabled())
                .attachedDataLongTermSave(campaign.getAttachedDataLongTermSave());
    }

    private static void mapStopConditionFields(CampaignInDTO.CampaignInDTOBuilder builder, Campaign campaign) {
        builder.stopConditionEnabled(campaign.getStopConditionEnabled())
                .stopConditionParameter(campaign.getStopConditionParameter())
                .stopConditionValue(campaign.getStopConditionValue())
                .stopConditionThreshold(campaign.getStopConditionThreshold())
                .stopType(campaign.getStopType())
                .stopTypeHistory(campaign.getStopTypeHistory());
    }

    private static void mapApiFields(CampaignInDTO.CampaignInDTOBuilder builder, Campaign campaign) {
        builder.allowApiControl(campaign.getAllowApiControl())
                .apiToken(campaign.getApiToken());
    }

    private static void mapCallResult(CampaignInDTO.CampaignInDTOBuilder builder, Call call) {
        builder.callResult(CallResultInMapper.toDto(call));
    }
}