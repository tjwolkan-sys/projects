package com.powerdialer.jmx.mbean;

import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import com.powerdialer.jmx.MetricManager;

@Component
@ManagedResource(objectName = "com.powerdialer:name=CampaignDatasource", description = "Bean for accessing metrics of campaign's connections to databases")
public class CampaignDatasourceMBean extends DynamicMapMBean {

  private static final String PROPERTIES_PREFIX = "campaign";

  public CampaignDatasourceMBean() {
    super("com.powerdialer.jmx.mbean.CampaignDatasource", MetricManager.getInstance()::getCampaignDatasourceLastRequestTime);
  }

  @Override
  public String getPropertiesPrefix() {
    return PROPERTIES_PREFIX;
  }

}
