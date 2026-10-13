package com.powerdialer.dao;

import java.util.List;

import com.powerdialer.model.Skill;

public interface ISkillDao extends IGenericDao<Skill> {

  List<Skill> getSkillsByCampaignId(Long campaignId);

}
