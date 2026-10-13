package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IQueueDao;
import com.powerdialer.model.Queue;
import com.powerdialer.model.CampaignGroup;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import java.util.Collections;
import java.util.List;

@Repository
public class QueueDao extends GenericDao<Queue> implements IQueueDao {

  public QueueDao() {
    super(Queue.class);
  }

  @Override
  public List<Queue> findDistinctByCampaignId(Long campaignId) {
    final Session session = getSessionFactory().getCurrentSession();
    final Criteria cg = session.createCriteria(CampaignGroup.class, "cg")
            .createAlias("cg.group", "g")
            .createAlias("g.queues", "q");

    cg.add(Restrictions.eq("cg.campaign.id", campaignId));
    cg.add(Restrictions.eq("cg.enabled", true));
    cg.add(Restrictions.eq("cg.deleted", false));

    cg.setProjection(Projections.distinct(Projections.property("q.id")));
    final List<Long> queueIds = cg.list();

    if (queueIds == null || queueIds.isEmpty()) {
      return Collections.emptyList();
    }

    final Criteria qCrit = session.createCriteria(Queue.class, "q");
    qCrit.add(Restrictions.in("q.id", queueIds));
    qCrit.addOrder(Order.asc("q.name"));

    return qCrit.list();
  }
}
