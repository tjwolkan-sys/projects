package com.powerdialer.dao.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import javax.persistence.Transient;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.sql.JoinType;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import com.powerdialer.dao.IGenericDao;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;

@SuppressWarnings("unchecked")
public class GenericDao<T> implements IGenericDao<T> {

  private static final Log LOG = LogFactory.getLog(GenericDao.class);

  @Autowired
  private SessionFactory sessionFactory;

  private Class<T> clazz;

  public GenericDao(Class<T> clazz) {
    this.clazz = clazz;
  }

  @Override
  public T save(final T o) {
    sessionFactory.getCurrentSession().save(o);
    return o;
  }

  @Override
  public void delete(final Long id) {
    getSessionFactory().getCurrentSession().createQuery("update " + clazz.getSimpleName() + " set deleted = :deleted, enabled = :enabled where id = :id")
        .setParameter("deleted", true)
        .setParameter("enabled", false)
        .setParameter("id", id)
        .executeUpdate();
  }

  public SessionFactory getSessionFactory() {
    return sessionFactory;
  }

  public void setSessionFactory(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public void delete(final List<Long> ids) {
    getSessionFactory().getCurrentSession()
        .createQuery("update " + clazz.getSimpleName() + " set deleted = :deleted, enabled = :enabled where id in (:ids)")
        .setParameterList("ids", ids)
        .setParameter("deleted", true)
        .setParameter("enabled", false)
        .executeUpdate();
  }

  @Override
  public void setEnabled(List<Long> ids, boolean enabled) {
    getSessionFactory().getCurrentSession().createQuery("update " + clazz.getSimpleName() + " set enabled = :enabled where id in (:ids)")
        .setParameter("enabled", enabled)
        .setParameterList("ids", ids).executeUpdate();
  }

  @Override
  public T get(final Long id) {
    return (T) sessionFactory.getCurrentSession().get(clazz, id);
  }

  @Override
  public T merge(final T o) {
    return (T) sessionFactory.getCurrentSession().merge(o);
  }

  @Override
  public void saveOrUpdate(final T o) {
    sessionFactory.getCurrentSession().saveOrUpdate(o);
    sessionFactory.getCurrentSession().flush();
  }

  @Override
  public List<T> getAll() {
    final Session session = sessionFactory.getCurrentSession();
    final Criteria crit = session.createCriteria(clazz);
    return crit.list();
  }

  @Override
  public List<T> getAll(Boolean enabled, Boolean deleted) {
    if (enabled == null || deleted == null) {
      return new ArrayList<>();
    }
    final Session session = sessionFactory.getCurrentSession();
    final Criteria crit = session.createCriteria(clazz);
    crit.add(Restrictions.eq("enabled", enabled));
    crit.add(Restrictions.eq("deleted", deleted));
    return crit.list();
  }

  @Override
  public ListDetailed<T> getAll(JSONObject page, JSONObject sort, JSONObject filters) {
    ListDetailed<T> listDetailed = new ListDetailed<>();

    Criteria criteria = sessionFactory.getCurrentSession().createCriteria(clazz);

    applyGridFilters(criteria, filters);

    applyGridPagination(criteria, page, listDetailed);

    applyGridSort(criteria, sort);

    listDetailed.setData(criteria.setProjection(null).setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY).list());

    return listDetailed;
  }

  protected void applyGridFilters(Criteria criteria, JSONObject filters) {
    if (filters != null && filters.length() > 0) {

      Iterator itr = filters.keys();

      while (itr.hasNext()) {

        try {

          String propertyName = (String) itr.next();

          if (!isTransient(propertyName)) {

            JSONArray propertyFilters = (JSONArray) filters.get(propertyName);

            Disjunction orCondition = Restrictions.disjunction();
            for (int i = 0; i < propertyFilters.length(); i++) {

              JSONObject filter = propertyFilters.getJSONObject(i);
              String term = filter.has("term") ? filter.getString("term") : null;
              String[] innerProperties = null;
              Object value = new Object();
              Class type = null;

              if (term != null) {
                if (propertyName.contains(".")) {
                  innerProperties = propertyName.split("\\.");
                  type = (innerProperties.length == 3
                      ? getFieldType(clazz, innerProperties[0])
                      : getFieldType(getFieldType(clazz, innerProperties[0]), innerProperties[1]));
                } else {
                  type = getFieldType(clazz, propertyName);
                }

                if (type.equals(String.class)) {
                  value = term;
                } else if (type.equals(boolean.class) || type.equals(Boolean.class)) {
                  value = Boolean.valueOf(term);
                } else if (type.equals(int.class) || type.equals(Integer.class)) {
                  value = Integer.valueOf(term);
                } else if (type.equals(long.class) || type.equals(Long.class)) {
                  value = Long.valueOf(term);
                } else if (type.equals(Date.class)) {
                  value = new SimpleDateFormat("dd/MM/yyyy HH:mm").parse(term);
                } else if (type.isEnum()) {
                  value = hasNumericJsonFormat(type) ? type.getEnumConstants()[Integer.parseInt(term)] : Enum.valueOf(type, term.trim().toUpperCase());
                }
              }

              if (innerProperties != null && innerProperties.length == 2 && !innerProperties[1].equals("id")) {
                criteria.createAlias(innerProperties[0], innerProperties[0]);
              }

              int condition = filter.has("condition") ? filter.getInt("condition") : 0;

              switch (condition) {
              case Constants.GRID_FILTER_STARTS_WITH:
                criteria.add(Restrictions.like(propertyName, (String) value, MatchMode.START));
                break;
              case Constants.GRID_FILTER_ENDS_WITH:
                criteria.add(Restrictions.like(propertyName, (String) value, MatchMode.END));
                break;
              case Constants.GRID_FILTER_EXACT:
                if (type.isInterface()) {
                  criteria.createAlias(innerProperties[0], innerProperties[1]).add(Restrictions.eq(innerProperties[1] + "." + innerProperties[2], Long.valueOf(term)));
                } else {
                  orCondition.add(Restrictions.eq(propertyName, value));
                }
                break;
              case Constants.GRID_FILTER_CONTAINS:
                if (type.isInterface()) {
                  criteria.createAlias(innerProperties[0], innerProperties[1]).add(Restrictions.like(innerProperties[1] + "." + innerProperties[2], term, MatchMode.ANYWHERE));
                } else {
                  criteria.add(Restrictions.like(propertyName, (String) value, MatchMode.ANYWHERE));
                }
                break;
              case Constants.GRID_FILTER_GREATER_THAN:
                criteria.add(Restrictions.gt(propertyName, value));
                break;
              case Constants.GRID_FILTER_GREATER_THAN_OR_EQUAL:
                criteria.add(Restrictions.ge(propertyName, value));
                break;
              case Constants.GRID_FILTER_LESS_THAN:
                criteria.add(Restrictions.lt(propertyName, value));
                break;
              case Constants.GRID_FILTER_LESS_THAN_OR_EQUAL:
                criteria.add(Restrictions.le(propertyName, value));
                break;
              case Constants.GRID_FILTER_NOT_EQUAL:
                criteria.add(Restrictions.ne(propertyName, value));
                break;
              case Constants.GRID_FILTER_IS_NULL:
                criteria.add(Restrictions.isNull(propertyName));
                break;
              case Constants.GRID_FILTER_IS_NOT_NULL:
                criteria.add(Restrictions.isNotNull(propertyName));
                break;
              default:
                criteria.add(Restrictions.eq(propertyName, value));
                break;
              }

            }
            criteria.add(orCondition);
          }

        } catch (Exception e) {
          LOG.error("", e);
        }

      }

    }
  }

  protected void applyGridPagination(Criteria criteria, JSONObject page, ListDetailed<T> listDetailed) {
    if (page != null && page.length() > 0) {
      if (!page.has("fetchRowCount") || (page.has("fetchRowCount") && page.getBoolean("fetchRowCount"))) {
        listDetailed.setTotalItems(((Number) criteria.setProjection(Projections.countDistinct("id")).uniqueResult()).intValue());
      }
      if (page.has("first") && page.has("max")) {
        int first = (Integer) page.get("first");
        int max = (Integer) page.get("max");
        criteria.setFirstResult((first - 1) * (max)).setMaxResults(max);
      }
    }
  }

  protected void applyGridSort(Criteria criteria, JSONObject sort) {
    if (sort != null && sort.has("direction") && sort.has("field")) {
      try {
        if (!isTransient(sort.get("field").toString())) {

          String[] str = sort.get("field").toString().split("\\.");
          if (str.length == 2 && !isAliasExist(criteria, str[0])) {
            criteria.createAlias(str[0], str[0], JoinType.LEFT_OUTER_JOIN);
          }

          criteria.addOrder(sort.get("direction").equals("asc") ? Order.asc(sort.get("field").toString()) : Order.desc(sort.get("field").toString()));
        }
      } catch (Exception e) {
        LOG.error("", e);
      }
    }
  }

  private boolean isTransient(String propertyName) throws NoSuchFieldException, SecurityException, IntrospectionException {
    Field field = null;
    Class currentClass = clazz;
    while (currentClass != null) {
      try {
        field = currentClass.getDeclaredField(propertyName);
        break;
      } catch (NoSuchFieldException e) {
      }
      currentClass = currentClass.getSuperclass();
    }
	  return field != null && new PropertyDescriptor(propertyName, currentClass).getReadMethod().getAnnotation(Transient.class) != null;
  }

  private Class getFieldType(Class currentClass, String propertyName) throws NoSuchFieldException, SecurityException {
    while (currentClass != null) {
      try {
        return currentClass.getDeclaredField(propertyName).getType();
      } catch (NoSuchFieldException e) {
      }
      currentClass = currentClass.getSuperclass();
    }
    throw new NoSuchFieldException(propertyName);
  }

  private boolean hasNumericJsonFormat(Class clazz) {
    Annotation jsonFormatAnnotation = clazz.getAnnotation(JsonFormat.class);
    if (jsonFormatAnnotation != null) {
      return ((JsonFormat) jsonFormatAnnotation).shape().isNumeric();
    }
    return false;
  }

  private boolean isAliasExist(Criteria criteria, String alias) {
    if (alias == null) {
      return false;
    }
    try {
      List<Criteria> subcriteriaList = (ArrayList<Criteria>) FieldUtils.readField(criteria, "subcriteriaList", true);
      for (Criteria subcriteria : subcriteriaList) {
        if (alias.equals(subcriteria.getAlias())) {
          return true;
        }
      }
    } catch (IllegalAccessException e) {
      return false;
    }
    return false;
  }

  @Override
  public void batchInsert(Iterable<T> entities) {
    Session session = sessionFactory.getCurrentSession();
    int i = 0;
    for (T entity : entities) {
      i++;
      session.save(entity);
      if (i % 30 == 0) {
        session.flush();
        session.clear();
      }
    }
    session.flush();
    session.clear();
  }
}
