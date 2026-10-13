package com.powerdialer.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.powerdialer.dao.ISkillDao;
import com.powerdialer.dto.SkillFormDTO;
import com.powerdialer.dto.SkillListDTO;
import com.powerdialer.model.Skill;
import com.powerdialer.util.ListDetailed;
import com.powerdialer.validators.SkillValidator;

@Service("skillService")
public class SkillService extends GenericService<Skill, SkillFormDTO, SkillListDTO> {

  private final ISkillDao skillDao;

  @Autowired
  public SkillService(ISkillDao dao) {
    super(dao, Skill.class, SkillFormDTO.class, SkillListDTO.class, new SkillValidator()); this.skillDao = dao;
  }

  public ListDetailed<SkillListDTO> getSkillsByCampaignId(Long campaignId) {
    List<Skill> campaignsLocations = skillDao.getSkillsByCampaignId(campaignId);
    ListDetailed<SkillListDTO> locationListDetailed = new ListDetailed<>();
    locationListDetailed.setData(convertEntitiesToDTO(campaignsLocations));
    locationListDetailed.setTotalItems(campaignsLocations.size());
    return locationListDetailed;
  }

}
