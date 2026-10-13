package com.powerdialer.util;

/**
 * Class for holding developer properties which contained in file dev.properties
 */
public class DevProperties {

  /**
   * IP of AGI server which consumes requests from Asterisk. Main purpose for
   * this option is to allow debugging Asterisk's AGI calls without deploying to
   * test environment
   */
  public static final String AGI_IP;

  /**
   * Disables the automatic campaign scheduling
   */
  public static final Boolean DISABLE_CAMPAIGN_SCHEDULER;

  static {
    PropertiesUtil properties = new PropertiesUtil("dev.properties");
    AGI_IP = properties.getProperty("agi.server.ip");
    DISABLE_CAMPAIGN_SCHEDULER = Boolean.parseBoolean(properties.getProperty("campaign.scheduler.disabled"));
  }

}
