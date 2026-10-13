package com.powerdialer.service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.powerdialer.dto.LSCAuthFormDTO;
import com.powerdialer.engine.LSCEngine;
import com.powerdialer.model.LSCAuthorization;
import org.hibernate.Hibernate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.powerdialer.dao.ILSCDao;
import com.powerdialer.dto.LSCFormDTO;
import com.powerdialer.dto.LSCListDTO;
import com.powerdialer.model.LSC;
import com.powerdialer.util.ListDetailed;
import com.powerdialer.validators.LSCValidator;

import javax.transaction.Transactional;

@Service
public class LSCService extends GenericService<LSC, LSCFormDTO, LSCListDTO> {

  private final ILSCDao lscDao;
  private final LSCAuthService lscAuthService;

  @Autowired
  public LSCService(ILSCDao dao, LSCAuthService lscAuthService) {
    super(dao, LSC.class, LSCFormDTO.class, LSCListDTO.class, new LSCValidator());
    this.lscDao = dao;
    this.lscAuthService = lscAuthService;
  }

  public LSC get(long id) {
    return getDao().get(id);
  }

  public LSC getWithInitializedCampaigns(long id) {
    LSC lsc = ((ILSCDao) getDao()).getWithInitializedCampaigns(id);
    Hibernate.initialize(lsc.getTenant());
    return lsc;
  }

  public List<LSC> getLscsForCampaign(Long campaignId, boolean enabled) {
    List<LSC> lscsForCampaign = ((ILSCDao) getDao()).getLscsForCampaign(campaignId, enabled);
    for (LSC lsc : lscsForCampaign) {
      Hibernate.initialize(lsc.getTenant());
    }
    return lscsForCampaign;
  }

  public List<LSC> getGlobalLscs(boolean enabled) {
    List<LSC> globalLscs = ((ILSCDao) getDao()).getGlobalLscs(enabled);
    for (LSC globalLsc : globalLscs) {
      Hibernate.initialize(globalLsc.getTenant());
    }
    return globalLscs;
  }

  public ListDetailed<LSCListDTO> getLSCsByCampaignId(Long campaignId) {
    List<LSC> campaignsLocations = lscDao.getLSCsByCampaignId(campaignId);
    ListDetailed<LSCListDTO> lscListDetailed = new ListDetailed<>();
    lscListDetailed.setData(convertEntitiesToDTO(campaignsLocations));
    lscListDetailed.setTotalItems(campaignsLocations.size());
    return lscListDetailed;
  }

  @Override
  @Transactional
  public LSCFormDTO save(LSCFormDTO lscFormDTO) {
    if (Objects.isNull(lscFormDTO)) {
      return null;
    }

    LSCAuthFormDTO lscAuthFormDTO = lscFormDTO.getAuthorization();
    if (Objects.nonNull(lscAuthFormDTO) && lscFormDTO.isTokenRequired()) {
      Map<String, String> response = lscAuthService.sendRequest(lscAuthFormDTO);
      if (response != null && response.get("response") != null) {
        lscAuthFormDTO.setResponse(response.get("response"));

        if (Objects.equals(response.get("status_code"), HttpStatus.OK.toString())) {
          lscAuthFormDTO.setToken(response.get("access_token"));
        }
      }

      if (lscAuthFormDTO.getId() == null) {
        lscAuthFormDTO.setCreateDate(new Date());
        lscAuthFormDTO = lscAuthService.save(lscAuthFormDTO);
      } else {
        lscAuthFormDTO.setUpdateDate(new Date());
        lscAuthFormDTO = lscAuthService.merge(lscAuthFormDTO);
      }
      LSCAuthorization lscAuthorization = mapper.map(lscAuthFormDTO, LSCAuthorization.class);
      lscAuthorization.setResponse(null);
      lscAuthFormDTO = mapper.map(lscAuthorization, LSCAuthFormDTO.class);
      lscFormDTO.setAuthorization(lscAuthFormDTO);
    } else {
      lscFormDTO.setAuthorization(null);
    }

    if (lscFormDTO.getId() == null) {
      lscFormDTO = super.save(lscFormDTO);
    } else {
      lscFormDTO = super.merge(lscFormDTO);
    }

    return lscFormDTO;
  }

}
