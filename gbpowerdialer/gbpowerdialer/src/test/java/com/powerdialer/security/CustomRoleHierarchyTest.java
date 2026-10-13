package com.powerdialer.security;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.GrantedAuthority;

import com.powerdialer.model.Campaign;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.Constants;

@RunWith(MockitoJUnitRunner.class)
public class CustomRoleHierarchyTest {
  private List<GrantedAuthorityWithValue> authorities;
  private List<Campaign> campaignList;
  private GrantedAuthorityWithValue authority;
  private List<Long> ids;
  private Long id;
  private Campaign campaign;

  @InjectMocks
  private CustomRoleHierarchy roleHierarchy;

  @Mock
  private CampaignService campaignService;

  @Mock
  AuthUtil authUtil;

  @Mock
  private Tenant tenant;

  @Mock
  ThreadLocal<User> threadLocal;

  @Mock
  User user;

  @Before
  public void init(){
    roleHierarchy.init();

    authorities = new ArrayList<>();
    ids = new ArrayList<>();
    campaignList = new ArrayList<>();
    id = 1L;
    ids.add(id);
    campaign = new Campaign();
    campaign.setId(id);
    campaignList.add(campaign);
  }

  @Test
  public void givenAuthorityWhenValueAllThenReturnRemoveAuthorityWithValueAll() {
    authority = new GrantedAuthorityWithValue(Constants.PERMISSION_EDIT_CAMPAIGN, Constants.PERMISSION_VALUE_ALL);
    authorities.add(authority);
    when(authUtil.getThreadLocalUser()).thenReturn(threadLocal);
    lenient().when(threadLocal.get()).thenReturn(user);
    lenient().when(user.getTenant()).thenReturn(tenant);
    when(campaignService.getCampaignIdsCreatedByUserTenants(user, false)).thenReturn(ids);

    GrantedAuthority grantedAuthority = roleHierarchy.getReachableGrantedAuthorities(authorities).stream()
        .filter(auth ->(((GrantedAuthorityWithValue) auth).getValue().equals(Constants.PERMISSION_VALUE_ALL)))
        .findFirst()
        .orElse(null);

    assertNull(grantedAuthority);
  }

  @Test
  public void givenAuthorityWhenValueAllThenChangeValueAllAuthorityToAuthoritiesWhithNumberValue() {
    authority = new GrantedAuthorityWithValue(Constants.PERMISSION_DIALER_ADMINISTRATION, Constants.PERMISSION_VALUE_ALL);
    authorities.add(authority);
    when(authUtil.getThreadLocalUser()).thenReturn(threadLocal);
    lenient().when(threadLocal.get()).thenReturn(user);
    lenient().when(user.getTenant()).thenReturn(tenant);
    when(campaignService.getCampaignIdsCreatedByUserTenants(user, false)).thenReturn(ids);

    GrantedAuthority grantedAuthority = roleHierarchy.getReachableGrantedAuthorities(authorities).stream()
        .filter(auth ->(((GrantedAuthorityWithValue) auth).getValue().equals(id.toString())))
        .findFirst()
        .orElse(null);

    assertNotNull(grantedAuthority);
  }
}
