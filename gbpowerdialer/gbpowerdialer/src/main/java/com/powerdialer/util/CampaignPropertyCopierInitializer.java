package com.powerdialer.util;

import com.powerdialer.model.Campaign;
import com.powerdialer.util.CampaignPropertyCopier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class CampaignPropertyCopierInitializer  {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignPropertyCopierInitializer.class);

    @PostConstruct
    public void init() {
        LOG.info("Spring context initialization: Calling CampaignPropertyCopier pre-load...");
        CampaignPropertyCopier.preloadCampaignMethods(Campaign.class);
        LOG.info("Spring context initialization: CampaignPropertyCopier pre-load finished.");
    }
}