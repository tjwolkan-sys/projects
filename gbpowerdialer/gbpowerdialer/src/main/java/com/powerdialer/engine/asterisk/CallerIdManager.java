package com.powerdialer.engine.asterisk;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailEntry;
import com.powerdialer.engine.dialer.routing.RouteDetailsCursor;
import com.powerdialer.engine.dialer.routing.RouteDetailsFilter;
import com.powerdialer.exceptions.RouteDetailsUnavailableException;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.powerdialer.exceptions.NoAvailablePoolCallerIdException;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.RoutingGroup;
import com.powerdialer.rds.ResourceDistributionServiceFacade;
import com.powerdialer.util.Constants;

@Component
public class CallerIdManager {

  @Autowired
  private ResourceDistributionServiceFacade resourceDistributionServiceFacade;

  public String obtainCallerId(Call call) throws NoAvailablePoolCallerIdException {
    return obtainCallerId(call, call.getAttempts() + 1);
  }

  public String obtainCallerId(Call call, Integer attempt) throws NoAvailablePoolCallerIdException {
    switch (call.getCampaign().getCallerIdBehaviour()) {
      case POOL:
        String result = resourceDistributionServiceFacade.obtainCallerId(
            getCallerIdPoolId(call),
            call.getCalledNumber(),
            call.getCampaign().getRetryCallerIdBehavior(),
            attempt,
            call.getCallType(),
            call.getCampaign().getId()
        );
        if (StringUtils.isEmpty(result)) {
          throw new NoAvailablePoolCallerIdException();
        } else {
          return result;
        }
      case FIXED:
        return call.getCampaign().getCallerId();
      case DYNAMIC:
        return getDynamicCallerId(call);
      case DIAL_PLAN_DEFINED:
      default:
        return "";
    }
  }

  /**
   * FCT routing aktif POOL kampanyalarda QSS'den sıralı RouteDetailEntry listesi alır
   * ve cursor olarak döner. İlk Originate öncesi çağrılır; cause 34 hangup'larında
   * cursor üzerinden bir sonraki entry kullanılır.
   *
   * Liste boş dönerse standart obtainCallerId davranışına paralel olarak
   * NoAvailablePoolCallerIdException atılır.
   */
  public RouteDetailsCursor obtainRouteDetailsCursor(Call call)
      throws NoAvailablePoolCallerIdException, RouteDetailsUnavailableException {
    long routingGroupId = getCallerIdPoolId(call);
    List<RouteDetailEntry> entries = resourceDistributionServiceFacade.getRouteDetails(routingGroupId);
    if (entries == null || entries.isEmpty()) {
      throw new NoAvailablePoolCallerIdException();
    }
    // QSS route'ları callType ve attempt attribute'larıyla tanımlı; eski obtainCallerId yolu bu
    // context'i QSS'e gönderip seçimi ona bırakıyordu. getRouteDetails context almadığı için
    // süzmeyi burada yapıyoruz, aksi hâlde çağrı tipine tanımlı olmayan route'lar da denenirdi.
    List<RouteDetailEntry> usable = RouteDetailsFilter.filterForCall(entries, call.getCallType(), call.getAttempts());
    return new RouteDetailsCursor(usable);
  }

  public void onCallComplete(Call call) {
    if (call.getCampaign().getCallerIdBehaviour() == Constants.CALLER_ID_BEHAVIOUR.POOL && StringUtils.isNotBlank(call.getCallerId())) {
      resourceDistributionServiceFacade.deleteHistory(getCallerIdPoolId(call), call.getCalledNumber());
    }
  }

  private String getDynamicCallerId(Call call) {
    JSONObject rawData = call.getRawData();
    Iterator<String> rawKeys = rawData.keys();
    while (rawKeys.hasNext()) {
      String key = rawKeys.next();
      String value = rawData.getString(key);
      if (value.equals(call.getCalledNumber())) {
        for (PhoneNumberColumn phoneNumberColumn : call.getCampaign().getEnabledPhoneNumberColumns()) {
          if (phoneNumberColumn.getColNumber().equals(key)) {
            String callerId = rawData.optString(phoneNumberColumn.getColCallerId());
            if (StringUtils.isEmpty(callerId)) {
              callerId = call.getCampaign().getCallerId();
            }
            return callerId;
          }
        }
      }
    }
    return call.getCampaign().getCallerId();
  }

//  private RETRY_CALLER_ID_BEHAVIOR shouldRequestUniqueCallerId(Call call) {
  // TODO replace old getUniqueRetryCallerId with new 3-options approach (support the SAME option)
//    return call.isRetry() && Constants.RETRY_CALLER_ID_BEHAVIOR.UNIQUE.equals(call.getCampaign().getRetryCallerIdBehavior());
//  }

  private long getCallerIdPoolId(Call call) {
    return Optional.of(call)
        .map(Call::getCampaign)
        .map(Campaign::getCallerIdPool)
        .map(RoutingGroup::getId)
        .orElseThrow(() -> new IllegalStateException("No callerId pool for " + call));
  }


}
