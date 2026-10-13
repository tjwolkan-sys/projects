package com.powerdialer.dao;

import com.powerdialer.model.CDR;

import java.util.Date;
import java.util.List;

public interface ICDRDao extends IGenericDao<CDR> {
    CDR getByInteractionId(String interactionId);
    Date findAgentRegistrarAnswerTime(String parentId);
    boolean hasSuccessfulInteractionStatus(String parentId, Long userId);
    Long findWebphoneUserResultCodeId(String parentId, Long userId);
    Double findGammaStartupAverageTalkTime(long campaignId, int minTalkTimeSec);
    Double findGammaStartupAverageRingTime(long campaignId);
    Double findGammaStartupAverageRingTimeAnswered(long campaignId);
    Double findGammaStartupAverageAcwTime(long campaignId);
    Double findGammaCurrentAverageAcwTime(long campaignId);
    List<Object[]> findGammaTodayAnswerStatsByType(long campaignId);
}
