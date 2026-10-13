package com.powerdialer.validators;

import org.junit.Test;
import org.mockito.Mockito;

import com.powerdialer.exceptions.NonUniqueGlobalDNC;
import com.powerdialer.model.DNC;
import com.powerdialer.model.Tenant;
import com.powerdialer.service.DNCService;

/**
 * Test for DNCValidator class
 */
public class DNCValidatorTest {

  /**
   * Test throwing NonUniqueGlobalDNC exception
   */
  @Test(expected = NonUniqueGlobalDNC.class)
  public void testFailNonUnique() throws Exception {
    DNCValidator dncValidator = new DNCValidator();
    DNCService dncService = Mockito.mock(DNCService.class);
    Mockito.doReturn(1L).when(dncService).getTenantDNCCount(1L, true, false);
    dncValidator.setDncService(dncService);
    DNC dnc = new DNC();
    Tenant tenant = new Tenant();
    tenant.setId(1L);
    dnc.setTenant(tenant);
    dnc.setGlobal(true);
    dncValidator.validate(dnc);
  }

}
