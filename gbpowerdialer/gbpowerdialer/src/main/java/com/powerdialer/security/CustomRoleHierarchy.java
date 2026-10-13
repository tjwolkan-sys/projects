package com.powerdialer.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.hierarchicalroles.CycleInRoleHierarchyException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.powerdialer.model.User;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.Constants;

/**
 * Just copy
 * {@link org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl}
 * with modification in method
 * {@link CustomRoleHierarchy#getRolesReachableInOneOrMoreSteps} for support
 * {@link GrantedAuthorityWithValue#getValue()} options
 */
@Component
public class CustomRoleHierarchy implements RoleHierarchy {

  @Autowired
  private CampaignService campaignService;

  @Autowired
  private AuthUtil authUtil;

  private String roleHierarchyStringRepresentation = null;

  /**
   * rolesReachableInOneStepMap is a Map that under the key of a specific role
   * name contains a set of all roles reachable from this role in 1 step
   */
  private Map<GrantedAuthority, Set<GrantedAuthority>> rolesReachableInOneStepMap = null;

  /**
   * rolesReachableInOneOrMoreStepsMap is a Map that under the key of a specific
   * role name contains a set of all roles reachable from this role in 1 or more
   * steps
   */
  private Map<GrantedAuthority, Set<GrantedAuthority>> rolesReachableInOneOrMoreStepsMap = null;

  @PostConstruct
  public void init() {
    String roleHierarchy = Constants.PERMISSION_MANAGE_CAMPAIGN + " > " + Constants.PERMISSION_VIEW_CAMPAIGN + " " +
                           Constants.PERMISSION_EDIT_CAMPAIGN_NON_TECH + " > " + Constants.PERMISSION_MANAGE_CAMPAIGN + " " +
                           Constants.PERMISSION_EDIT_CAMPAIGN_TECH + " > " + Constants.PERMISSION_EDIT_CAMPAIGN_NON_TECH + " " +
                           Constants.PERMISSION_EDIT_CAMPAIGN + " > " + Constants.PERMISSION_EDIT_CAMPAIGN_TECH + " " +
                           Constants.PERMISSION_DIALER_ADMINISTRATION + " > " + Constants.PERMISSION_CREATE_CAMPAIGN + " ";
    setHierarchy(roleHierarchy);
  }

  /**
   * Set the role hierarchy and pre-calculate for every role the set of all
   * reachable roles, i.e. all roles lower in the hierarchy of every given role.
   * Pre-calculation is done for performance reasons (reachable roles can then
   * be calculated in O(1) time). During pre-calculation, cycles in role
   * hierarchy are detected and will cause a
   * <tt>CycleInRoleHierarchyException</tt> to be thrown.
   * @param roleHierarchyStringRepresentation - String definition of the role
   *          hierarchy.
   */
  public void setHierarchy(String roleHierarchyStringRepresentation) {
    this.roleHierarchyStringRepresentation = roleHierarchyStringRepresentation;
    buildRolesReachableInOneStepMap();
    buildRolesReachableInOneOrMoreStepsMap();
  }

  @Override
  public Collection<GrantedAuthority> getReachableGrantedAuthorities(Collection<? extends GrantedAuthority> authorities) {
    if (authorities == null || authorities.isEmpty()) {
      return AuthorityUtils.NO_AUTHORITIES;
    }
    Set<GrantedAuthority> reachableRoles = new HashSet<>();
    for (GrantedAuthority authority : authorities) {
      if (authority instanceof GrantedAuthorityWithValue && isValueAll(authority)) {
        Set<GrantedAuthority> expandedAuthorities = expandAuthority(authority);
        expandedAuthorities.forEach(auth -> processAuthority(reachableRoles, auth));
      } else {
        processAuthority(reachableRoles, authority);
      }
    }
    return reachableRoles;
  }

  /**
   * If authority value ALL or TENANT replace it with respective multiple authorities where values is campaign ids.
   * @param authority
   * @return
   */
  private Set<GrantedAuthority> expandAuthority(GrantedAuthority authority) {
    List<Long> ids;
    if (isValueAll(authority)) {
      User user = authUtil.getThreadLocalUser().get();
      ids = campaignService.getCampaignIdsCreatedByUserTenants(user, false);
      if (ids.isEmpty()) {
        Set<GrantedAuthority> authorityWithValueSet = new HashSet<>();
        GrantedAuthorityWithValue authorityWithValue = new GrantedAuthorityWithValue(authority.getAuthority(), Constants.PERMISSION_VALUE_ALL);
        authorityWithValueSet.add(authorityWithValue);
        return authorityWithValueSet;
      }
    } else {
      return Collections.singleton(authority);
    }
    return createAuthoritiesByCampaignIds(authority, ids);
  }

  private void processAuthority(Set<GrantedAuthority> reachableRoles, GrantedAuthority authority) {
    addReachableRoles(reachableRoles, authority);
    Set<? extends GrantedAuthority> additionalReachableRoles = getRolesReachableInOneOrMoreSteps(authority);
    if (additionalReachableRoles != null) {
      reachableRoles.addAll(additionalReachableRoles);
    }
  }

  private boolean isValueAll(GrantedAuthority authority) {
    String value = ((GrantedAuthorityWithValue) authority).getValue();
    return Constants.PERMISSION_VALUE_ALL.equals(value);
  }

  private Set<GrantedAuthority> createAuthoritiesByCampaignIds(GrantedAuthority authority, List<Long> ids) {
    return ids.stream()
        .map(String::valueOf)
        .map(id -> new GrantedAuthorityWithValue(authority.getAuthority(), id))
        .collect(Collectors.toSet());
  }

  private void addReachableRoles(Set<GrantedAuthority> reachableRoles, GrantedAuthority authority) {
    String authorityToTest = authority.getAuthority();
    String valueToTest;
    valueToTest = authority instanceof GrantedAuthorityWithValue ? ((GrantedAuthorityWithValue) authority).getValue() : null;
    for (GrantedAuthority testAuthority : reachableRoles) {
      String testKey = testAuthority.getAuthority();
      String testValue = null;
      if (testAuthority instanceof GrantedAuthorityWithValue)
        testValue = ((GrantedAuthorityWithValue) testAuthority).getValue();

      if ((testKey != null) && (testKey.equals(authorityToTest)))
        if (testValue != null && valueToTest != null) {
          if (testValue.equals(valueToTest))
            return;
        } else {
          return;
        }
    }
    reachableRoles.add(authority);
  }

  private Set<? extends GrantedAuthority> getRolesReachableInOneOrMoreSteps(GrantedAuthority authority) {
    if (authority.getAuthority() == null) {
      return null;
    }
    for (Map.Entry<GrantedAuthority, Set<GrantedAuthority>> entry : rolesReachableInOneOrMoreStepsMap.entrySet()) {
      GrantedAuthority testAuthority = entry.getKey();
      String testKey = testAuthority.getAuthority();
      if ((testKey != null) && (testKey.equals(authority.getAuthority()))) {
        Set<GrantedAuthority> grantedAuthorities = entry.getValue();
        return authority instanceof GrantedAuthorityWithValue ? createChildPermissions((GrantedAuthorityWithValue) authority, grantedAuthorities) : grantedAuthorities;
      }
    }
    return null;
  }

  /**
   * Create child permission from given permission
   * @param permission
   * @param grantedAuthorities
   * @return
   */
  private Set<? extends GrantedAuthority> createChildPermissions(GrantedAuthorityWithValue permission, Set<GrantedAuthority> grantedAuthorities) {
    Set<GrantedAuthorityWithValue> permissions = new HashSet<>();
    for (GrantedAuthority grantedAuthority : grantedAuthorities) {
      permissions.add(new GrantedAuthorityWithValue(grantedAuthority.getAuthority(), permission.getValue()));
    }
    return permissions;
  }

  /**
   * Parse input and build the map for the roles reachable in one step: the
   * higher role will become a key that references a set of the reachable lower
   * roles.
   */
  private void buildRolesReachableInOneStepMap() {
    Pattern pattern = Pattern.compile("(\\s*([^\\s>]+)\\s*>\\s*([^\\s>]+))");

    Matcher roleHierarchyMatcher = pattern.matcher(roleHierarchyStringRepresentation);
    rolesReachableInOneStepMap = new HashMap<>();

    while (roleHierarchyMatcher.find()) {
      GrantedAuthority higherRole = new SimpleGrantedAuthority(roleHierarchyMatcher.group(2));
      GrantedAuthority lowerRole = new SimpleGrantedAuthority(roleHierarchyMatcher.group(3));
      Set<GrantedAuthority> rolesReachableInOneStepSet;

      if (!rolesReachableInOneStepMap.containsKey(higherRole)) {
        rolesReachableInOneStepSet = new HashSet<>();
        rolesReachableInOneStepMap.put(higherRole, rolesReachableInOneStepSet);
      } else {
        rolesReachableInOneStepSet = rolesReachableInOneStepMap.get(higherRole);
      }
      addReachableRoles(rolesReachableInOneStepSet, lowerRole);

    }
  }

  /**
   * For every higher role from rolesReachableInOneStepMap store all roles that
   * are reachable from it in the map of roles reachable in one or more steps.
   * (Or throw a CycleInRoleHierarchyException if a cycle in the role hierarchy
   * definition is detected)
   */
  private void buildRolesReachableInOneOrMoreStepsMap() {
    rolesReachableInOneOrMoreStepsMap = new HashMap<>();
    // iterate over all higher roles from rolesReachableInOneStepMap

    for (Map.Entry<GrantedAuthority, Set<GrantedAuthority>> entry : rolesReachableInOneStepMap.entrySet()) {

      GrantedAuthority role = entry.getKey();
      List<GrantedAuthority> rolesToVisitSet = new ArrayList<>();

      if (rolesReachableInOneStepMap.containsKey(role)) {
        rolesToVisitSet.addAll(entry.getValue());
      }

      Set<GrantedAuthority> visitedRolesSet = new HashSet<>();

      while (!rolesToVisitSet.isEmpty()) {
        // take a role from the rolesToVisit set
        GrantedAuthority aRole = rolesToVisitSet.iterator().next();
        rolesToVisitSet.remove(aRole);
        addReachableRoles(visitedRolesSet, aRole);
        if (rolesReachableInOneStepMap.containsKey(aRole)) {
          Set<GrantedAuthority> newReachableRoles = rolesReachableInOneStepMap.get(aRole);

          // definition of a cycle: you can reach the role you are starting from
          if (rolesToVisitSet.contains(role) || visitedRolesSet.contains(role)) {
            throw new CycleInRoleHierarchyException();
          } else {
            // no cycle
            rolesToVisitSet.addAll(newReachableRoles);
          }
        }
      }
      rolesReachableInOneOrMoreStepsMap.put(role, visitedRolesSet);

    }
  }

}
