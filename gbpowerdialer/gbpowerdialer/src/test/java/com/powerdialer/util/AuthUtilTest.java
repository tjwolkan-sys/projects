package com.powerdialer.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.security.AuthUtil;

public class AuthUtilTest {

  private User user;
  private String password = "password";
  private AuthUtil authUtil;

  @Before
  public void setup() {
    user = mock(User.class);
    authUtil = new AuthUtil();
  }

  @Test(expected = AccessDeniedException.class)
  public void doLdapAuthMustThrowAccessDeniedException() {

    when(user.getTenant()).thenReturn(new Tenant());    
    authUtil.doLdapAuth(user, password);
  }

  @Test(expected = RuntimeException.class)
  public void doLdapAuthMustThrowRuntimeException() {

    Tenant tenant = new Tenant();
    tenant.setLdapSuffix("@gbukr.entp");
    tenant.setLdapServerIp("172.21.15.10");
    when(user.getTenant()).thenReturn(tenant);    
    authUtil.doLdapAuth(user, password);
  }

  @Test(expected = BadCredentialsException.class)
  public void doSpringAuthMustThrowBadCredentialsException() {

    when(user.getPassword()).thenReturn("password");
    authUtil.doSpringAuth(user, password);
  }
  
}
