package com.powerdialer.util;

import org.hibernate.collection.internal.AbstractPersistentCollection;

import org.dozer.CustomFieldMapper;
import org.dozer.classmap.ClassMap;
import org.dozer.fieldmap.FieldMap;

import com.powerdialer.dto.BaseEntityTenantDTO;
import com.powerdialer.dto.TenantListDTO;
import com.powerdialer.model.base.BaseEntityTenant;

/**
 * Custom field mapping for fields of entities
 */
public class DozerCustomFieldMapper implements CustomFieldMapper {

  @Override
  public boolean mapField(Object source, Object destination, Object sourceFieldValue, ClassMap classMap, FieldMap fieldMapping) {
    if (sourceFieldValue instanceof AbstractPersistentCollection) {
      return !((AbstractPersistentCollection) sourceFieldValue).wasInitialized();
    }

    return source instanceof BaseEntityTenantDTO && sourceFieldValue instanceof TenantListDTO && destination instanceof BaseEntityTenant;
  }
}
