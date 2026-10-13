package com.powerdialer.dao.impl;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.ISkillDao;
import com.powerdialer.model.Skill;

@Repository("skillDao")
public class SkillDao extends GenericDao<Skill> implements ISkillDao {

  public SkillDao() {
    super(Skill.class);
  }

  @Override
  public List<Skill> getSkillsByCampaignId(Long campaignId) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(Skill.class);
    criteria.createAlias("campaigns", "c");
    criteria.add(Restrictions.eq("c.id", campaignId));
    criteria.add(Restrictions.eq("deleted", false));
    criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
    return criteria.list();
  }

}
