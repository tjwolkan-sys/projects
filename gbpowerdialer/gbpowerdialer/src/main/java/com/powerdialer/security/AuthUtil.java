package com.powerdialer.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.NonUniqueResultException;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.encoding.ShaPasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.powerdialer.exceptions.NonUniqueUserException;
import com.powerdialer.exceptions.PasswordShallBeChangedException;
import com.powerdialer.model.PermissionProfile;
import com.powerdialer.model.User;
import com.powerdialer.oauth.OneAccessException;
import com.powerdialer.oauth.services.OneAccessService;
import com.powerdialer.service.CampaignService;
import com.powerdialer.service.UserService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LDAPUtils;

/**
 * This class handles Spring Security or LDAP Authorization
 */
@Component
public class AuthUtil {

  private static ShaPasswordEncoder passwordEncoder = new ShaPasswordEncoder();

  @Autowired
  private RoleHierarchy roleHierarchy;

  @Autowired
  private CampaignService campaignService;

  @Autowired
  private UserService userService;

  @Autowired
  private OneAccessService oneAccessService;

  private ThreadLocal<User> threadLocalUser = new ThreadLocal<>();

  /**
   * Try to authenticate user using active directory or database base on user
   * settings
   * @param name (username or adNickname)
   * @param password
   * @return response
   */
  public String tryToAuthenticate(String name, String password) {
    User preLoadedUser;
    try {
      preLoadedUser = userService.getUserByUsernameOrAdNickname(name);
    } catch (NonUniqueResultException e) {
      throw new NonUniqueUserException("Username '" + name + "' is not unique", e);
    }
    if (preLoadedUser == null)
      throw new BadCredentialsException("Bad credentials");
    if (preLoadedUser.isChangePassword() && !needsLdapAuth(preLoadedUser)){
      throw new PasswordShallBeChangedException("Password must be changed, access is denied.");
    }
    JSONObject response = new JSONObject();
    if (needsLdapAuth(preLoadedUser))
      doLdapAuth(preLoadedUser, password);
    else
      doSpringAuth(preLoadedUser, password);
    fillSuccessResponse(response);
    return response.toString();
  }

  private boolean needsLdapAuth(User user) {
    return (user.getLdapAuth() == 0 && user.getTenant() != null && user.getTenant().isLdapAuth()) || user.getLdapAuth() == 1;
  }

  public void fillSuccessResponse(JSONObject response) {
    User authenticationUser = getAuthenticationUser();
    response.put("authenticated", true).put("username", authenticationUser.getUsername()).put("fullname", authenticationUser.getFullname())
        .put("roles", authenticationUser.getRolesNames()).put("tenantId", authenticationUser.getTenant().getId().toString())
        .put("authorities", SecurityContextHolder.getContext().getAuthentication().getAuthorities());
  }

  /**
   * Authenticate user using active directory
   * @param user
   * @param ldappassword
   * @throws Exception
   */
  public void doLdapAuth(User user, String ldappassword) {
    if (StringUtils.isNotEmpty(ldappassword) && user.getTenant() != null) {

      if (StringUtils.isBlank(user.getTenant().getLdapSuffix()) || StringUtils.isBlank(user.getTenant().getLdapServerIp()))
        throw new AccessDeniedException("Authentication failed for " + user + ", LDAP auth parameters are not set for " + user.getTenant());

      if (LDAPUtils.login(user.getAdNickname(), user.getTenant().getLdapSuffix(), ldappassword, user.getTenant().getLdapServerIp(),
          user.getTenant().getLdapFailoverServerIp(), user.getTenant().isLdapSSL()))
        doAuth(user);
    }
  }

  /**
   * Authenticate user using database
   * @param user
   * @param password
   */
  public void doSpringAuth(User user, String password) {
    if (!passwordEncoder.isPasswordValid(user.getPassword(), password, null))
      throw new BadCredentialsException("Bad credentials");
    doAuth(user);
  }

  /*
   * Authenticate user through OneAccess server
   */
  public void doOneAccessAuth() throws OneAccessException {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(null, null, Arrays.asList((new SimpleGrantedAuthority(Constants.ROLE_ANONYMOUS)))));
    String username = oneAccessService.getUsernameOfAuthenticatedUser();
    User user = userService.getUserByUsername(username);

    doAuth(user);
  }

  /**
   * Fill user authorities and authenticate user
   * @param user
   */
  private void doAuth(User user) {
    try {
      threadLocalUser.set(user);
      Set<GrantedAuthority> userAuthorities = user.getAuthorities();
      if (hasAuthority(Constants.PERMISSION_CREATE_CAMPAIGN, userAuthorities)) {
        List<Long> campaignIdsCreatedByUser = campaignService.getCampaignIdsCreatedByUser(user, false);
        restrictCreatedCampaigns(user, campaignIdsCreatedByUser);
        for (Long campaignId : campaignIdsCreatedByUser) {
          userAuthorities.add(new GrantedAuthorityWithValue(Constants.PERMISSION_EDIT_CAMPAIGN, campaignId.toString()));
        }
      }

      userAuthorities.addAll(getAdditionalAuthorities(user));

      Collection<? extends GrantedAuthority> reachableGrantedAuthorities = roleHierarchy.getReachableGrantedAuthorities(userAuthorities);
      SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, user.getPassword(), reachableGrantedAuthorities));
    } finally {
      threadLocalUser.remove();
    }
  }

  /**
   * Generate additional permission using fact that DialerAdministration ALL <->
   * ROLE_SUPERADMIN and DialerAdministration TENANT <-> ROLE_TENANT_ADMIN
   * @param user
   * @return
   */
  private Set<GrantedAuthority> getAdditionalAuthorities(User user) {
    Set<GrantedAuthority> userAuthorities = user.getAuthorities();
    Set<GrantedAuthority> additionalAuthorities = new HashSet<>();

    if (hasPermission(Constants.PERMISSION_DIALER_ADMINISTRATION,  userAuthorities))
      additionalAuthorities.add(new SimpleGrantedAuthority(Constants.ROLE_TENANT_ADMIN));

    if (hasAuthority(Constants.ROLE_SUPERADMIN, userAuthorities))
      additionalAuthorities.add(new GrantedAuthorityWithValue(Constants.PERMISSION_DIALER_ADMINISTRATION, Constants.PERMISSION_VALUE_ALL));

    if (hasAuthority(Constants.ROLE_TENANT_ADMIN, userAuthorities) || additionalAuthorities.contains(new SimpleGrantedAuthority(Constants.ROLE_TENANT_ADMIN))) {
      List<Long> campaignIdsCreatedByTenant = campaignService.getCampaignIdsCreatedByUserTenants(user, false);
      for (Long campaignId : campaignIdsCreatedByTenant) {
        additionalAuthorities.add(new GrantedAuthorityWithValue(Constants.PERMISSION_EDIT_CAMPAIGN, campaignId.toString()));
      }
      additionalAuthorities.add(new GrantedAuthorityWithValue(Constants.PERMISSION_DIALER_ADMINISTRATION, Constants.PERMISSION_VALUE_ALL));
    } else if (hasAuthority(Constants.ROLE_SUPERVISOR, userAuthorities)) {
      List<Long> campaignIdsCreatedByTenant = campaignService.getCampaignIdsCreatedByUserTenants(user, false);
      for (Long campaignId : campaignIdsCreatedByTenant) {
        additionalAuthorities.add(new GrantedAuthorityWithValue(Constants.PERMISSION_VIEW_CAMPAIGN, campaignId.toString()));
      }
    }

    return additionalAuthorities;
  }

  /**
   * Restrict editing campaigns which appear in the {@link User#getPermissionProfiles()}
   * @param user
   * @param campaignIdsCreatedByUser
   */
  private void restrictCreatedCampaigns(User user, List<Long> campaignIdsCreatedByUser) {
    user.getPermissionProfiles().stream()
        .map(PermissionProfile::getPermissions)
        .flatMap(Collection::stream)
        .filter(p -> !StringUtils.isEmpty(p.getPermissionValue()))
        .filter(p -> p.getPermissionKey().equals(Constants.PERMISSION_EDIT_CAMPAIGN))
        .filter(p -> !p.getPermissionValue().contains(Constants.PERMISSION_VALUE_ALL))
        .forEach(p -> campaignIdsCreatedByUser.remove(Long.valueOf(p.getPermissionValue())));
  }

  /**
   * get authenticated user
   * @return user or null
   */
  public User getAuthenticationUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null) {
      return (User) auth.getPrincipal();
    }
    return null;
  }

  /**
   * Update user authentication
   */
  public void reauthenticate() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    doAuth(user);
  }

  /**
   * Check is specified user has role
   * @param auth
   * @param role
   * @return
   */
  public boolean isInRole(Authentication auth, String role) {
    if (StringUtils.isEmpty(role) || (auth == null) || (auth.getPrincipal() == null)) {
      return false;
    }
    return hasAuthority(role, auth.getAuthorities());
  }

  private boolean hasAuthority(String authority, Collection<? extends GrantedAuthority> authorities) {
    if (authorities == null) {
      return false;
    }

    for (GrantedAuthority grantedAuthority : authorities) {
      if (authority.equals(grantedAuthority.getAuthority())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check is logged user has roles
   * @param roles
   * @return
   */
  public boolean isUserInRoles(String... roles){
    for (String role : roles) {
      if (isUserInRole(role)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check is logged user has role
   * @param role
   * @return
   */
  public boolean isUserInRole(String role) {
    return isInRole(SecurityContextHolder.getContext().getAuthentication(), role);
  }

  private boolean hasPermission(String key, Set<GrantedAuthority> authorities) {
    for (GrantedAuthority authority : authorities) {
      if (authority instanceof GrantedAuthorityWithValue) {
        GrantedAuthorityWithValue permission = (GrantedAuthorityWithValue) authority;
        if (permission.getAuthority().equals(key))
          return true;
      }
    }
    return false;
  }

  /**
   * Get specified permission from collections. Used for retrieving
   * {@link GrantedAuthorityWithValue}s with same authority and different values
   * @param key
   * @param authorities
   * @return
   */
  public Set<GrantedAuthorityWithValue> getPermissions(String key, Collection<? extends GrantedAuthority> authorities) {
    Set<GrantedAuthorityWithValue> resultSet = new HashSet<>();
    for (GrantedAuthority grantedAuthority : authorities) {
      if (grantedAuthority instanceof GrantedAuthorityWithValue) {
        GrantedAuthorityWithValue permission = (GrantedAuthorityWithValue) grantedAuthority;
        if (permission.getAuthority().equals(key))
          resultSet.add(permission);
      }
    }
    return resultSet;
  }

  public ThreadLocal<User> getThreadLocalUser() {
    return threadLocalUser;
  }
}
