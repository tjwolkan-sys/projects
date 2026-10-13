package com.powerdialer.jmx.mbean;

import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import com.powerdialer.jmx.MetricManager;

@Component
@ManagedResource(objectName = "com.powerdialer:name=CallOriginator", description = "Bean for accessing metrics of connections to Asterisks servers")
public class CallOriginatorMBean extends DynamicMapMBean {

  public CallOriginatorMBean() {
    super("com.powerdialer.jmx.mbean.CallOriginator", MetricManager.getInstance()::getServersLastOriginationTime);
  }

  /**
   * No prefix needed. Keys is server IPs
   * @return
   */
  @Override
  public String getPropertiesPrefix() {
    return "";
  }
}
