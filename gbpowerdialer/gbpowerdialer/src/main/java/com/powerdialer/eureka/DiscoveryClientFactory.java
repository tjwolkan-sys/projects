package com.powerdialer.eureka;

import javax.annotation.PostConstruct;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy(false)
public class DiscoveryClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(DiscoveryClientFactory.class);

  private DiscoveryClient discoveryClient;
  private InstanceInfo currentInstanceInfo;

  @Value("#{new Boolean('${eureka.enabled}'.trim())}")
  private boolean eurekaEnabled;

  @PostConstruct
  private void init() {
    if (eurekaEnabled)
      createDiscoveryClient();
    else
      LOG.info("Eureka configuration is disabled, no discovery client available");
  }

  public boolean isEurekaEnabled() {
    return eurekaEnabled;
  }

  public InstanceInfo getCurrentInstanceInfo() {
    if (currentInstanceInfo == null && eurekaEnabled)
      createDiscoveryClient();
    return currentInstanceInfo;
  }

  public DiscoveryClient getDiscoveryClient() {
    if (discoveryClient == null && eurekaEnabled)
      createDiscoveryClient();
    return discoveryClient;
  }

  private synchronized void createDiscoveryClient() {
    if (discoveryClient == null) {
      LOG.info("Creating Eureka discovery client...");
      EurekaInstanceConfig instanceConfig = new MyDataCenterInstanceConfig();
      InstanceInfo instanceInfo = new EurekaConfigBasedInstanceInfoProvider(instanceConfig).get();
      ApplicationInfoManager applicationInfoManager = new ApplicationInfoManager(instanceConfig, instanceInfo);
      EurekaClientConfig clientConfig = new DefaultEurekaClientConfig();
      discoveryClient = new DiscoveryClient(applicationInfoManager, clientConfig);
      currentInstanceInfo = instanceInfo;
    }
  }

}
