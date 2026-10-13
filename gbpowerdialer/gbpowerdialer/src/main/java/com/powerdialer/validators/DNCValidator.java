package com.powerdialer.validators;

import org.springframework.security.core.context.SecurityContextHolder;

import com.powerdialer.exceptions.NonUniqueGlobalDNC;
import com.powerdialer.model.DNC;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.service.DNCService;

public class DNCValidator implements Validator<DNC> {

  private DNCService dncService;

  public DNCService getDncService() {
    return dncService;
  }

  public void setDncService(DNCService dncService) {
    this.dncService = dncService;
  }

  @Override
  public ValidationResult validate(DNC t) throws RuntimeException {
    Tenant tenant = t.getTenant();
    if (tenant == null)
      tenant = ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getTenant();
    if (t.getGlobal()) {
      long tenantGlobalDNCCount;
	    tenantGlobalDNCCount = t.getId() != null ? dncService.getTenantDNCCount(tenant.getId(), true, false, t.getId()) : dncService.getTenantDNCCount(tenant.getId(), true, false);
      if (tenantGlobalDNCCount >= 1)
        throw new NonUniqueGlobalDNC();
    }
    return new ValidationResult(true, new String[0]);
  }

}
