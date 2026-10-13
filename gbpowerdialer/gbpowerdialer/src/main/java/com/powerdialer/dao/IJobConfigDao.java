package com.powerdialer.dao;

import com.powerdialer.dto.JobConfigCampaignDTO;
import com.powerdialer.dto.JobConfigFilterDTO;
import com.powerdialer.model.JobConfig;

import java.util.Collection;
import java.util.List;

public interface IJobConfigDao extends IGenericDao<JobConfig> {


    List<Long> findEligibleJobConfigIds_FirstScan();
    List<JobConfigCampaignDTO> findCampaignRowsByJobConfigIds(Collection<Long> ids);
    List<JobConfigFilterDTO>    findFiltersByJobConfigIds(Collection<Long> ids);

    /**
     * Active JobConfig rows linked to a campaign (any campaign state). Used by
     * JIT expire validator to fetch result_code_id / expire_user_id per JobConfig.
     */
    List<JobConfigCampaignDTO> findActiveJobConfigsForCampaign(Long campaignId);


}
