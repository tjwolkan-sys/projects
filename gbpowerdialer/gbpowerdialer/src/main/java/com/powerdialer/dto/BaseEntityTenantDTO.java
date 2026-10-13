package com.powerdialer.dto;

import org.dozer.Mapping;

public abstract class BaseEntityTenantDTO extends BaseEntityDTO {

  private Long tenant;
  private TenantListDTO tenantListDTO;

  @Mapping("tenant.id")
  public Long getTenant() {
    return tenant;
  }

  public void setTenant(Long tenant) {
    this.tenant = tenant;
  }

  @Mapping("tenant")
  public TenantListDTO getTenantListDTO() {
    return tenantListDTO;
  }

  public void setTenantListDTO(TenantListDTO tenantListDTO) {
    this.tenantListDTO = tenantListDTO;
  }

}
