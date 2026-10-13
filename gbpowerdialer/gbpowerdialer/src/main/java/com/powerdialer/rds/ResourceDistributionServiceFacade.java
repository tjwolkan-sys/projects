package com.powerdialer.rds;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import com.globalbilgi.gbqueueselectionservice.grpc.*;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.RETRY_CALLER_ID_BEHAVIOR;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.powerdialer.eureka.DiscoveryClientFactory;
import com.powerdialer.eureka.EurekaNameResolverProvider;
import com.powerdialer.exceptions.RouteDetailsUnavailableException;

@Component
public class ResourceDistributionServiceFacade {

  private static final Logger LOG = LoggerFactory.getLogger(ResourceDistributionServiceFacade.class);
  private static final String SERVICE_URL = "eureka://gbqueueselectionservice";
  private static final String BALANCING_POLICY = "round_robin";
  private static final boolean SECURED_FLAG = false;
  private static final int IDLE_TIMEOUT_MIN = 10;
  private static final String ATTEMPT_ATTRIBUTE_PREFIX = "AT_";
  private static final String CALL_TYPE_ATTRIBUTE_PREFIX = "CT_PD_";
  private static final String UTILIZER_CAMPAIGN_PREFIX = "UT_CAMPAIGN_";

  private QueueSelectionServiceGrpc.QueueSelectionServiceBlockingStub blockingStub;

  @Autowired
  private DiscoveryClientFactory discoveryClientFactory;

  @PostConstruct
  public void init() {
    if (discoveryClientFactory.isEurekaEnabled()) {
      ManagedChannel channel = ManagedChannelBuilder.forTarget(SERVICE_URL)
          .nameResolverFactory(new EurekaNameResolverProvider(discoveryClientFactory.getDiscoveryClient(),
              SECURED_FLAG,
              discoveryClientFactory.getCurrentInstanceInfo()))
          .defaultLoadBalancingPolicy(BALANCING_POLICY)
          .usePlaintext()
          .idleTimeout(IDLE_TIMEOUT_MIN, TimeUnit.MINUTES)
          .build();
      blockingStub = QueueSelectionServiceGrpc.newBlockingStub(channel);
    } else {
      LOG.info("Discarding channel initialization due to disabled Eureka discovery config");
    }
  }

  public String obtainCallerId(Long routingGroupId,
                               String phoneNumber,
                               RETRY_CALLER_ID_BEHAVIOR behavior,
                               Integer attempt,
                               Constants.CALL_TYPES callType,
                               Long campaignId) {
    if (blockingStub == null) {
      LOG.warn("No gRPC channel configured");
      return null;
    }
    try {
      RoutingRequest request = RoutingRequest.newBuilder()
          .setRoutingGroupId(routingGroupId)
          .setSubjectId(phoneNumber)
          .setUnique(mapToUniqueness(behavior))
          .addAttributes(ATTEMPT_ATTRIBUTE_PREFIX + (attempt - 1))
          .addAttributes(CALL_TYPE_ATTRIBUTE_PREFIX + callType.name())
          .setUtilizer(UTILIZER_CAMPAIGN_PREFIX + campaignId.toString())
          .build();
      RoutingResponse response = blockingStub.route(request);
      LOG.info("Got routing response: (id={}, name={}) for phone number {} and unique flag = {}", response.getTargetId(), response.getTargetName(), phoneNumber, behavior.name());
      return response.getTargetName();
    } catch (Exception e) {
      LOG.error("Could not build routing request for pool " + routingGroupId + " and phone number " + phoneNumber, e);
      return null;
    }
  }
  
  private Uniqueness mapToUniqueness(RETRY_CALLER_ID_BEHAVIOR behavior) {
    return Uniqueness.valueOf(behavior.name());
  }

  public void deleteHistory(Long routingGroupId, String phoneNumber) {
    if (blockingStub == null) {
      LOG.warn("No gRPC channel configured");
      return;
    }
    try {
      DeleteProvisionHistoryRequest request = DeleteProvisionHistoryRequest.newBuilder()
          .setRoutingGroupId(routingGroupId)
          .setSubjectId(phoneNumber)
          .build();
      blockingStub.deleteProvisionHistory(request);
      LOG.info("Cleared CallerId history for pool " + routingGroupId + " and number " + phoneNumber);
    } catch (Exception e) {
      LOG.error("Could not build DeleteProvisionHistoryRequest for pool " + routingGroupId + " and phone number " + phoneNumber, e);
    }
  }

  public void deleteHistory(Long routingGroupId) {
    deleteHistory(routingGroupId, "");
  }

  /**
   * QSS'ten sıralı route (FCT caller ID) listesini alır.
   *
   * <p>Boş liste ile erişim hatası BİLİNÇLİ olarak ayrıdır: QSS sözleşmesine göre grup bulunamazsa
   * boş liste döner (hata değil) ve bu kalıcı bir konfigürasyon durumudur. gRPC channel yoksa veya
   * çağrı hata atarsa {@link RouteDetailsUnavailableException} fırlatılır; bu geçici bir erişim
   * sorunudur ve çağrının retry'a bırakılmasını gerektirir. Eskiden iki durum da boş liste
   * döndürüyordu ve geçici bir kesintide çağrılar kalıcı olarak kaybediliyordu.
   *
   * <p>Exception mesajı ve cause'u çağırana taşınır; log tek noktada (çağrı bağlamıyla birlikte)
   * basılır, böylece kesinti sırasında originate başına çift log oluşmaz.
   */
  public List<RouteDetailEntry> getRouteDetails(Long routingGroupId) throws RouteDetailsUnavailableException {
    if (blockingStub == null) {
      throw new RouteDetailsUnavailableException("No gRPC channel configured for routingGroupId " + routingGroupId);
    }
    try {
      // NOT: istek yalnız grup taşır. `calledNumber` PD tarafında eklenmişti (ebb5188d) ama QSS
      // sözleşmesi grup bazlıdır — grubun default rule'una bağlı enabled route'lar dönüyor, aranan
      // numara seçime girmiyor. QSS canonical proto'sunda alan bulunmadığı için kaldırıldı.
      // Çağrı tipine/denemeye göre süzme bu yüzden tüketen tarafta (RouteDetailsFilter) yapılır.
      RouteDetailsRequest request = RouteDetailsRequest.newBuilder()
          .setGroupId(routingGroupId)
          .build();
      RouteDetailsResponse response = blockingStub.getRouteDetails(request);
      List<RouteDetailEntry> entries = response.getEntriesList();
      // Originate başına INFO yerine DEBUG: bu çağrı her FCT originate'inde yapılıyor (günlük
      // yüz binlerce satır). Teşhis gerektiğinde debug açılır.
      if (LOG.isDebugEnabled()) {
        LOG.debug("Got route details: size={} for routingGroupId={}", entries.size(), routingGroupId);
      }
      return entries;
    } catch (Exception e) {
      throw new RouteDetailsUnavailableException(
          "Could not getRouteDetails for routingGroupId " + routingGroupId, e);
    }
  }
}
