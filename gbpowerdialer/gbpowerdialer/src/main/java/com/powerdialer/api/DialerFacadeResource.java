package com.powerdialer.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.dozer.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.powerdialer.dto.CallDTO;
import com.powerdialer.dto.CallOriginatorDTO;
import com.powerdialer.dto.CampaignContextDTO;
import com.powerdialer.dto.CampaignDTO;
import com.powerdialer.dto.ServerDTO;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Call;

@RestController
@RequestMapping(value = "/api/dialerFacade", produces = MediaType.APPLICATION_JSON_VALUE)
public class DialerFacadeResource {

  @Autowired
  private DialerFacade dialerFacade;

  @Autowired
  private Mapper mapper;

  @RequestMapping(value = "/campaignContexts", method = RequestMethod.GET)
  public List<CampaignContextDTO> getCampaignContexts() {
    return dialerFacade.getCampaignContexts().values().stream()
        .map(this::createCampaignContextDTO)
        .collect(Collectors.toList());
  }

  @RequestMapping(value = "/campaignContexts/{campaignId}", method = RequestMethod.GET)
  public CampaignContextDTO getCampaignContextByCampaignId(@PathVariable Long campaignId) {
    CampaignContext campaignContext = dialerFacade.getCampaignContexts().get(campaignId);
    return campaignContext == null ? null : createCampaignContextDTO(campaignContext);
  }

  private CampaignContextDTO createCampaignContextDTO(CampaignContext campaignContext) {
    CampaignContextDTO campaignContextDTO = new CampaignContextDTO();
    campaignContextDTO.setCampaign(mapper.map(campaignContext.getCampaign(), CampaignDTO.class));
    campaignContextDTO.setCallQueue(convertCallsToDTOs(campaignContext.getCallConsumer().getCallQueue()));
    campaignContextDTO.setDataQueue(convertCallsToDTOs(campaignContext.getCallProducer().getDataQueue()));
    return campaignContextDTO;
  }

  @RequestMapping(value = "/callOriginators", method = RequestMethod.GET)
  public List<CallOriginatorDTO> getCallOriginators() {
    return dialerFacade.getAsteriskCallOriginators().values().stream()
        .map(this::createCallOriginatorDTO)
        .collect(Collectors.toList());
  }

  @RequestMapping(value = "/callOriginators/{serverId}", method = RequestMethod.GET)
  public CallOriginatorDTO getCallOriginatorById(@PathVariable Long serverId) {
    CallOriginator callOriginator = dialerFacade.getAsteriskCallOriginators().get(serverId);
    return callOriginator == null ? null : createCallOriginatorDTO(callOriginator);
  }

  private CallOriginatorDTO createCallOriginatorDTO(CallOriginator callOriginator) {
    CallOriginatorDTO callOriginatorDTO = new CallOriginatorDTO();
    callOriginatorDTO.setServer(mapper.map(callOriginator.getConnectionDecorator().getServer(), ServerDTO.class));
    callOriginatorDTO.setCallQueue(convertCallsToDTOs(callOriginator.getCallQueue()));
    return callOriginatorDTO;
  }

  private List<CallDTO> convertCallsToDTOs(Collection<Call> calls) {
    List<CallDTO> callsAsDTOs = new ArrayList<>();
    for (Call call : calls)
      callsAsDTOs.add(mapper.map(call, CallDTO.class));
    return callsAsDTOs;
  }

}
