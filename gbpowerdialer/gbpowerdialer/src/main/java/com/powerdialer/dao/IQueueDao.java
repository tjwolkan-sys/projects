package com.powerdialer.dao;

import com.powerdialer.model.Queue;

import java.util.List;

public interface IQueueDao extends IGenericDao<Queue> {
    List<Queue> findDistinctByCampaignId(Long campaignId);
}
