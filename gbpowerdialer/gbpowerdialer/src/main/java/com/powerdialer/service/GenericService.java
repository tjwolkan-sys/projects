package com.powerdialer.service;

import com.powerdialer.dao.IGenericDao;
import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.LSCAuthorization;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.model.base.BaseEntityTenant;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;
import com.powerdialer.validators.Validator;
import org.dozer.Mapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * @author Pasha Omelchenko
 * @param <T> entity type
 * @param <D1> form dto type
 * @param <D2> list dto type
 */
@Transactional(readOnly = true)
public class GenericService<T, D1, D2> {

  @Autowired
  Mapper mapper;
  @Autowired
  @Lazy
  AuthUtil authUtil;
  private IGenericDao<T> dao;
  private Validator<T> validator;
  private Class<T> entityClazz;
  private Class<D1> dtoFormClazz;
  private Class<D2> dtoListClazz;

  public GenericService(IGenericDao<T> dao,
                        Class<T> entityClazz,
                        Class<D1> dtoFormClazz,
                        Class<D2> dtoListClazz,
                        Validator<T> validator) {
    this.dao = dao;
    this.entityClazz = entityClazz;
    this.dtoListClazz = dtoListClazz;
    this.dtoFormClazz = dtoFormClazz;
    this.validator = validator;
  }

  public Validator<T> getValidator() {
    return validator;
  }

  /**
   * Save new object
   * @param item
   * @return D1
   */
  @Transactional(readOnly = false)
  public D1 save(final D1 item) {

    T entity = mapper.map(item, entityClazz);

    if (validator.validate(entity).getResult()) {

      boolean isBaseEntity = entity instanceof BaseEntity;

      if (isBaseEntity) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (entity instanceof BaseEntityTenant && !authUtil.isUserInRole(Constants.ROLE_SUPERADMIN)) {
          BaseEntityTenant b = (BaseEntityTenant) entity;
          b.setTenant(user.getTenant());
          setCreateUpdateFields(b, user);
        } else if (entity instanceof BaseEntity) {
          BaseEntity b = (BaseEntity) entity;
          setCreateUpdateFields(b, user);
        }
      }

      entity = dao.save(entity);

    } else {
      return item; // TODO handle validation fail
    }


    return mapper.map(entity, dtoFormClazz);
  }

  /**
   * Set createDate, updateDate and user fields for given object
   * @param o
   * @param user
   */
  public void setCreateUpdateFields(BaseEntity o, User user) {
    if (o.getCreateDate() == null) {
      o.setCreateDate(new Date());
      o.setCreateUser(user);
    } else {
      if (o.isChanged()) {
        o.setUpdateDate(new Date());
        o.setUpdateUser(user);
      }
    }
  }

  /**
   * Update existing object
   * @param item
   * @return D1
   */
  @Transactional(readOnly = false)
  public D1 merge(final D1 item) {
    T entity = mapper.map(item, entityClazz);

    boolean isBaseEntity = entity instanceof BaseEntity;

    if (validator.validate(entity).getResult() && isBaseEntity) {
      BaseEntity o = (BaseEntity) entity;
      o.setUpdateDate(new Date());
      User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
      o.setUpdateUser(user);
      entity = dao.merge(entity);
    } else if (validator.validate(entity).getResult() && !isBaseEntity) {
      entity = dao.merge(entity);
    } else {
      return item; // TODO handle validation fail
    }

    return mapper.map(entity, dtoFormClazz);
  }

  /**
   * Save new object or update existing one
   * @param item
   * @return D1
   * @throws DuplicatedNameException
   */
  @Transactional(readOnly = false)
  public D1 saveOrUpdate(final D1 item) throws DuplicatedNameException {

    T entity = mapper.map(item, entityClazz);

    if (validator.validate(entity).getResult()) {

      User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
      if (entity instanceof BaseEntityTenant) {
        BaseEntityTenant b = (BaseEntityTenant) entity;
        if (b.getCreateDate() == null && !authUtil.isUserInRole(Constants.ROLE_SUPERADMIN)) {
          b.setTenant(user.getTenant());
        }
        setCreateUpdateFields(b, user);
      } else {
        BaseEntity b = (BaseEntity) entity;
        setCreateUpdateFields(b, user);
      }

      dao.saveOrUpdate(entity);

    } else {
      return item; // TODO handle validation fail
    }

    return mapper.map(entity, dtoFormClazz);
  }

  /**
   * Delete object
   * @param id
   */
  @Transactional(readOnly = false)
  public void delete(final Long id) {
    dao.delete(id);
  }

  /**
   * Delete objects
   * @param ids
   */
  @Transactional(readOnly = false)
  public void delete(final List<Long> ids) {
    dao.delete(ids);
  }

  /**
   * Set enabled flag for objects
   * @param ids
   * @param enabled
   */
  @Transactional(readOnly = false)
  public void setEnabled(List<Long> ids, boolean enabled) {
    dao.setEnabled(ids, enabled);
  }

  /**
   * Get object
   * @param id
   * @return
   */
  public D1 get(final Long id) {
    T t = dao.get(id);
    return mapper.map(t, dtoFormClazz);
  }

  /**
   * Get entity by id
   * @param id
   * @return
   */
  public T getEntity(final long id) {
    return dao.get(id);
  }

  /**
   * Get all objects
   * @return
   */
  public List<T> getAll() {
    return dao.getAll();
  }

  /**
   * Get all enabled and non-deleted objects
   * @param enabled
   * @param deleted
   * @return List<T>
   */
  public List<T> getAll(Boolean enabled, Boolean deleted) {
    return dao.getAll(enabled, deleted);
  }

  /**
   * Get all objects by pagination, sort and filters
   * @param page
   * @param sort
   * @param filters
   * @return ListDetailed<D2>
   */
  @Transactional(readOnly = true)
  public ListDetailed<D2> getAll(JSONObject page, JSONObject sort, JSONObject filters) {

    filters = addCustomFilters(filters);

    ListDetailed<T> srcList = dao.getAll(page, sort, filters);

    ListDetailed<D2> resultList = new ListDetailed<>();
    List<D2> resultListData = new ArrayList<>();

    for (T src : srcList.getData()) {
      D2 dest = mapper.map(src, dtoListClazz);
      resultListData.add(dest);
    }

    resultList.setData(resultListData);
    resultList.setTotalItems(srcList.getTotalItems());

    return resultList;
  }

  /**
   * Add filters for data fetch manually
   * @param filters
   * @return
   */
  public JSONObject addCustomFilters(JSONObject filters) {
    if (filters == null) {
      filters = new JSONObject();
    }

    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    boolean hasTenantField = false;

    try {
      hasTenantField = getField(entityClazz, "tenant") != null;
    } catch (NoSuchFieldException | SecurityException e) {
      // if no tenant field in entity - skip adding tenant filter
    }

    JSONArray jsonArray = new JSONArray();

    if (!authUtil.isUserInRole(Constants.ROLE_SUPERADMIN) && hasTenantField) {
      if (authUtil.isUserInRole(Constants.ROLE_TENANT_ADMIN)) {
        for (Tenant tenant : user.getTenants()) {
          addFilter(jsonArray, Constants.GRID_FILTER_EXACT, tenant.getId().toString());
        }
      } else {
        addFilter(jsonArray, Constants.GRID_FILTER_EXACT, user.getTenant().getId().toString());
      }
    }

    filters.put("tenant.id", jsonArray);

    try {
      getField(entityClazz, "deleted");
      JSONObject val = new JSONObject();
      val.put("condition", Constants.GRID_FILTER_EXACT);
      val.put("term", "false");
      filters.put("deleted", new JSONArray().put(val));
    } catch (NoSuchFieldException e) {
      // if no deleted - skip deleted filter
    }

    return filters;
  }

  private void addFilter(JSONArray jsonArray, int condition, String term) {
    JSONObject val = new JSONObject();
    val.put("condition", condition);
    val.put("term", term);
    jsonArray.put(val);
  }

  protected void addFilter(JSONObject filters, String key, int condition, String term) {
    JSONObject val = new JSONObject();
    val.put("condition", condition);
    if (term != null)
      val.put("term", term);
    filters.put(key, new JSONArray().put(val));
  }

  /**
   * Check if given class has field and return it
   * @param propertyName
   * @return Field
   * @throws NoSuchFieldException
   * @throws SecurityException
   */
  private Field getField(Class currentClass, String propertyName) throws NoSuchFieldException, SecurityException {
    while (currentClass != null) {
      try {
        return currentClass.getDeclaredField(propertyName);
      } catch (NoSuchFieldException e) {
      }
      currentClass = currentClass.getSuperclass();
    }
    throw new NoSuchFieldException(propertyName);
  }

  /**
   * Set createDate, updateDate and user fields for the list of given objects
   * @param os
   * @param user
   */
  public void setCreateUpdateFields(List<? extends BaseEntity> os, User user) {
    if (os == null)
      return;

    for (BaseEntity o : os) {
      setCreateUpdateFields(o, user);
    }
  }

  /**
   * Convert given entities to DTO objects
   * @param entities
   * @return
   */
  public List<D2> convertEntitiesToDTO(Collection<T> entities) {
    List<D2> resultListData = new ArrayList<>();
    for (T src : entities) {
      D2 dest = mapper.map(src, dtoListClazz);
      resultListData.add(dest);
    }
    return resultListData;
  }

  @Transactional(readOnly = false)
  public void batchInsert(Iterable<T> entities) {
    for (T entity : entities) {
      if (validator.validate(entity).getResult()) {
        User user = (User) Optional.of(SecurityContextHolder.getContext())
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getPrincipal)
            .filter(p -> p instanceof User)
            .orElse(null);
        if (entity instanceof BaseEntityTenant && user != null) {
          BaseEntityTenant b = (BaseEntityTenant) entity;
          if (b.getCreateDate() == null && !authUtil.isUserInRole(Constants.ROLE_SUPERADMIN)) {
            b.setTenant(user.getTenant());
          }
          setCreateUpdateFields(b, user);
        } else {
          BaseEntity b = (BaseEntity) entity;
          setCreateUpdateFields(b, user);
        }
      } else {
        // TODO handle validation fail
      }
    }
    getDao().batchInsert(entities);
  }

  public IGenericDao<T> getDao() {
    return dao;
  }

}
