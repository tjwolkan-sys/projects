package com.powerdialer.oauth.services;

import java.net.URI;

import org.springframework.web.client.RestOperations;

import com.powerdialer.oauth.OneAccessException;

public class OneAccessServiceImpl implements OneAccessService {

  private String oneAccessUsernameURL;
  private RestOperations oneAccessRestTemplate;

  public String getUsernameOfAuthenticatedUser() throws OneAccessException {
    return oneAccessRestTemplate.getForObject(URI.create(oneAccessUsernameURL), String.class);
  }

  public void setOneAccessRestTemplate(RestOperations oneAccessRestTemplate) {
    this.oneAccessRestTemplate = oneAccessRestTemplate;
  }

  public void setOneAccessUsernameURL(String oneAccessUsernameURL) {
    this.oneAccessUsernameURL = oneAccessUsernameURL;
  }

}
