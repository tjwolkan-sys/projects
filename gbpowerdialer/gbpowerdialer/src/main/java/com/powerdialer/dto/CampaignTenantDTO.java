package com.powerdialer.dto;

import java.util.List;
import java.util.Map;

public class CampaignTenantDTO {

    private Map<Long, List<Map<String, Object>>> campaignsByTenantList;
    private List<Map<String, Object>> tenantsList;

    public Map<Long, List<Map<String, Object>>> getCampaignsByTenantList() {
        return campaignsByTenantList;
    }

    public void setCampaignsByTenantList(Map<Long, List<Map<String, Object>>> campaignsByTenantList) {
        this.campaignsByTenantList = campaignsByTenantList;
    }

    public List<Map<String, Object>> getTenantsList() {
        return tenantsList;
    }

    public void setTenantsList(List<Map<String, Object>> tenantsList) {
        this.tenantsList = tenantsList;
    }
}
