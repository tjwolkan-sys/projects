package com.powerdialer.dao;

import com.powerdialer.model.Tenant;

public interface ITenantDao extends IGenericDao<Tenant> {
  
  Tenant getTenantById(long tenantId);

}