package com.powerdialer.security;

import java.util.Objects;

import org.springframework.security.core.GrantedAuthority;

import com.powerdialer.model.Permission;
import com.powerdialer.util.Constants;

/**
 * Granted authority with additional value. Used to give access to single
 * campaign.
 */
public class GrantedAuthorityWithValue implements GrantedAuthority {

  private static final long serialVersionUID = -1354344937331336149L;
  private String value;
  private String authority;

  public GrantedAuthorityWithValue(String authority, String value) {
    this.authority = authority;
    this.value = value;
  }

  public static GrantedAuthorityWithValue fromPermission(Permission permission) {
    String authorityValue = (Constants.PERMISSION_VALUE_ALL.equals(permission.getPermissionValue()))
        ? Constants.PERMISSION_VALUE_ALL + permission.getTenant().getId()
        : permission.getPermissionValue().trim();
    return new GrantedAuthorityWithValue(permission.getPermissionKey().trim(), authorityValue);
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public String getAuthority() {
    return authority;
  }

  public void setAuthority(String authority) {
    this.authority = authority;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    GrantedAuthorityWithValue that = (GrantedAuthorityWithValue) o;

    if (!Objects.equals(value, that.value))
      return false;
    return Objects.equals(authority, that.authority);

  }

  @Override
  public int hashCode() {
    int result = value != null ? value.hashCode() : 0;
    result = 31 * result + (authority != null ? authority.hashCode() : 0);
    return result;
  }
}
