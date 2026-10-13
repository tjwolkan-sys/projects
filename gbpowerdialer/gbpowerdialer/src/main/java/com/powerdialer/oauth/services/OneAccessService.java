package com.powerdialer.oauth.services;

import com.powerdialer.oauth.OneAccessException;

public interface OneAccessService {

  /**
   * @return The username of authenticated user.
   */
  String getUsernameOfAuthenticatedUser() throws OneAccessException;

}