package com.powerdialer.eureka;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.net.InetSocketAddress;

import com.google.common.base.Preconditions;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.CacheRefreshedEvent;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.EurekaEvent;
import com.netflix.discovery.EurekaEventListener;
import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is using to obtain the list of node addresses from Eureka
 * discovery service, filter this list according to configuration and pass it to
 * gRPC load balancer.
 */
public class EurekaNameResolver extends NameResolver implements EurekaEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(EurekaNameResolver.class);
  private static final String METADATA_ZONE_KEY = "zone";
  private static final String GRPC_PORT_KEY = "grpc.port";

  private DiscoveryClient discoveryClient;
  private String host;
  private String localZone;
  private Listener2 listener;
  private boolean securedFlag;
  private Lock resolvingLock = new ReentrantLock();
  private List<EquivalentAddressGroup> currentAddresses;

  public EurekaNameResolver(DiscoveryClient discoveryClient,
                            String host,
                            boolean securedFlag,
                            InstanceInfo instanceInfo) {
    this.discoveryClient = discoveryClient;
    this.host = host;
    this.securedFlag = securedFlag;
    EurekaClientConfig clientConfig = discoveryClient.getEurekaClientConfig();
    String[] availZones = clientConfig.getAvailabilityZones(clientConfig.getRegion());
    this.localZone = InstanceInfo.getZone(availZones, instanceInfo);
  }

  @Override
  public String getServiceAuthority() {
    return "";
  }

  @Override
  public void start(Listener2 listener) {
    LOG.info("Starting up: " + this);
    Preconditions.checkState(this.listener == null, "already started");
    this.listener = Preconditions.checkNotNull(listener, "listener");
    discoveryClient.registerEventListener(this);
    resolveInstances();
  }

  @Override
  public void shutdown() {
    LOG.info("Shutting down: " + this);
    discoveryClient.unregisterEventListener(this);
  }

  @Override
  public void onEvent(EurekaEvent event) {
    if (event instanceof CacheRefreshedEvent)
      resolveInstances();
  }

  private void resolveInstances() {
    if (!resolvingLock.tryLock()) {
      LOG.warn("Passing resolving for " + this + ": previous resolving process is not done yet");
    } else {
      try {
        List<EquivalentAddressGroup> addresses = filterInstances(discoveryClient.getInstancesByVipAddress(host, securedFlag))
            .map(this::createEquivalentAddressGroup)
            .sorted(Comparator.comparing(EquivalentAddressGroup::hashCode))
            .collect(Collectors.toList());
        if (!addresses.equals(currentAddresses)) {
          NameResolver.ResolutionResult resolutionResult = NameResolver.ResolutionResult
              .newBuilder()
              .setAddresses(addresses)
              .build();
          LOG.info("Using new resolution result: " + resolutionResult);
          currentAddresses = addresses;
          listener.onResult(resolutionResult);
        }
      } finally {
        resolvingLock.unlock();
      }
    }
  }

  private EquivalentAddressGroup createEquivalentAddressGroup(InstanceInfo instance) {
    int port;
    if (instance.getMetadata().containsKey(GRPC_PORT_KEY)) {
      port = Integer.parseInt(instance.getMetadata().get(GRPC_PORT_KEY));
    } else {
      port = instance.getPort();
    }
    return new EquivalentAddressGroup(new InetSocketAddress(instance.getHostName(), port), Attributes.EMPTY);
  }

  private Stream<InstanceInfo> filterInstances(List<InstanceInfo> instances) {
    if (discoveryClient.getEurekaClientConfig().shouldPreferSameZoneEureka()) {
      List<InstanceInfo> localInstances = new ArrayList<>();
      List<InstanceInfo> remoteInstances = new ArrayList<>();
      instances.stream()
          .filter(i -> InstanceInfo.InstanceStatus.UP.equals(i.getStatus()))
          .forEach(i -> {
            if (hasLocalZone(i))
              localInstances.add(i);
            else
              remoteInstances.add(i);
          });
      return (localInstances.isEmpty())
             ? remoteInstances.stream()
             : localInstances.stream();
    } else {
      return instances.stream()
          .filter(i -> InstanceInfo.InstanceStatus.UP.equals(i.getStatus()));
    }
  }

  private boolean hasLocalZone(InstanceInfo instanceInfo) {
    if (instanceInfo == null || instanceInfo.getMetadata() == null)
      return false;
    String zone = instanceInfo.getMetadata().get(METADATA_ZONE_KEY);
    return zone != null && zone.equals(localZone);
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + " [host=" + host + ", secured=" + securedFlag + ", localZone=" + localZone + "]";
  }

}
