package com.powerdialer.dao;

import java.util.List;

import org.json.JSONObject;

import com.powerdialer.util.ListDetailed;

public interface IGenericDao<T> {

  T save(final T o);

  void delete(final Long id);

  void delete(final List<Long> ids);

  T get(final Long id);

  T merge(final T o);

  void saveOrUpdate(final T o);

  List<T> getAll();

  List<T> getAll(Boolean enabled, Boolean deleted);

  ListDetailed<T> getAll(JSONObject page, JSONObject sort, JSONObject filters);

  void setEnabled(List<Long> ids, boolean enabled);

  void batchInsert(Iterable<T> entities);
}
