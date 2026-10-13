package com.powerdialer.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.cache.Cache;
import org.dozer.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.powerdialer.dto.CallDTO;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.model.Call;

/**
 * Rest controller providing active calls information
 */
@RestController
@RequestMapping(value = "/api/calls", produces = MediaType.APPLICATION_JSON_VALUE)
public class CallResource {

  @Autowired
  private ManagerConnectionComposition connectionComposition;

  @Autowired
  private Mapper mapper;

  /**
   * Get calls by campaign id
   * @param campaignId
   * @return list of calls
   */
  @RequestMapping(value = "/campaign/{campaignId}", method = RequestMethod.GET)
  public List<CallDTO> getCallsByCampaign(@PathVariable Long campaignId) {
    return connectionComposition.getOpenedConnections().values().stream()
        .map(ManagerConnectionDecorator::getCalls)
        .map(Cache::asMap)
        .map(Map::values)
        .flatMap(Collection::stream)
        .filter(call -> campaignId.equals(call.getCampaign().getId()))
        .map(call -> mapper.map(call, CallDTO.class))
        .collect(Collectors.toList());
  }

  /**
   * Get calls by server id
   * @param serverId
   * @return list of calls
   */
  @RequestMapping(value = "/server/{serverId}", method = RequestMethod.GET)
  public List<CallDTO> getCallsByServer(@PathVariable Long serverId) {
    for (ManagerConnectionDecorator connectionDecorator : connectionComposition.getOpenedConnections().values()) {
      if (connectionDecorator.getServer().getId().equals(serverId))
        return convertCallsToDTOs(new ArrayList<>(connectionDecorator.getCalls().asMap().values()));
    }
    return new ArrayList<>();
  }

  /**
   * Get call by id
   * @param callId
   * @return call
   */
  @RequestMapping(value = "/{callId}", method = RequestMethod.GET)
  public CallDTO getCallById(@PathVariable String callId) {
    for (ManagerConnectionDecorator connectionDecorator : connectionComposition.getOpenedConnections().values()) {
      Call call = connectionDecorator.getCalls().getIfPresent(callId);
      if (call != null)
        return mapper.map(call, CallDTO.class);
    }
    return null;
  }

  /**
   * Get all calls
   * @return list of calls
   */
  @RequestMapping(method = RequestMethod.GET)
  public List<CallDTO> getCalls() {
    List<Call> callsFromAllServers = new ArrayList<>();
    for (ManagerConnectionDecorator connectionDecorator : connectionComposition.getOpenedConnections().values())
      callsFromAllServers.addAll(connectionDecorator.getCalls().asMap().values());
    return convertCallsToDTOs(callsFromAllServers);
  }

  /**
   * Convert calls to DTOs
   * @param calls
   * @return list of DTOs
   */
  private List<CallDTO> convertCallsToDTOs(List<Call> calls) {
    List<CallDTO> callsAsDTOs = new ArrayList<>();
    for (Call call : calls)
      callsAsDTOs.add(mapper.map(call, CallDTO.class));
    return callsAsDTOs;
  }

}
