package com.powerdialer.util;

import java.net.InetAddress;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

@Component
public class ApplicationUtil {

  private static final Log log = LogFactory.getLog(ApplicationUtil.class);
  private String applicationLocalIpAddress;

  public String getThisApplicationLocalIpAddress() {
    if (applicationLocalIpAddress == null)
      getLocalHostAddress();
    return applicationLocalIpAddress;
  }

  private void getLocalHostAddress() {
    try {
      InetAddress inetAddress = InetAddress.getLocalHost();
      applicationLocalIpAddress = inetAddress.getHostAddress();
    } catch (Exception e) {
      log.warn("Could not obtain server IP address.", e);
    }
  }
}
