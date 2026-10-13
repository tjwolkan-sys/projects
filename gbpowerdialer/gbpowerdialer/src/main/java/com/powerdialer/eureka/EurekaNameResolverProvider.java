package com.powerdialer.eureka;

import java.net.URI;
import javax.annotation.Nullable;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryClient;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;

public class EurekaNameResolverProvider extends NameResolverProvider {

  private static final String EUREKA = "eureka";
  private DiscoveryClient discoveryClient;
  private boolean securedFlag;
  private InstanceInfo instanceInfo;

  public EurekaNameResolverProvider(DiscoveryClient discoveryClient,
                                    boolean securedFlag,
                                    InstanceInfo instanceInfo) {
    this.discoveryClient = discoveryClient;
    this.securedFlag = securedFlag;
    this.instanceInfo = instanceInfo;
  }

  @Override
  protected boolean isAvailable() {
    return true;
  }

  @Override
  protected int priority() {
    return 5;
  }

  @Nullable
  @Override
  public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
    return new EurekaNameResolver(discoveryClient, targetUri.getHost(), securedFlag, instanceInfo);
  }

  @Override
  public String getDefaultScheme() {
    return EUREKA;
  }

}
