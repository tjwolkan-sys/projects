package com.powerdialer.dao.impl;

import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import org.json.JSONObject;
import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IRoutingGroupDao;
import com.powerdialer.model.RoutingGroup;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;

@Repository
public class RoutingGroupDao extends GenericDao<RoutingGroup> implements IRoutingGroupDao  {

  public RoutingGroupDao() {
    super(RoutingGroup.class);
  }

  @Override
  public ListDetailed<RoutingGroup> getAll(JSONObject page, JSONObject sort, JSONObject filters) {
    ListDetailed<RoutingGroup> listDetailed = new ListDetailed<>();
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(RoutingGroup.class);
    criteria.add(Restrictions.eq("enabled", true));
    criteria.add(Restrictions.eq("deleted", false));
    criteria.add(Restrictions.eq("type", Constants.ROUTING_GROUP_TYPE.CALLERID));
    applyGridFilters(criteria, filters);
    applyGridPagination(criteria, page, listDetailed);
    applyGridSort(criteria, sort);
    listDetailed.setData(criteria.setProjection(null).setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY).list());
    return listDetailed;
  }

}
