package com.powerdialer.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for CSRF token information
 */
@RestController
public class CSRFController {

  @Autowired
  private CsrfTokenRepository csrfTokenRepository;

  /**
   * Get CSRF token from current {@link CsrfTokenRepository}
   */
  @RequestMapping(value = "/getCurrentCsrfToken", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
  public CsrfToken getCurrentCsrfToken(HttpServletRequest request, HttpServletResponse response) {
    CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
    if (csrfToken == null) {
      csrfToken = csrfTokenRepository.generateToken(request);
      csrfTokenRepository.saveToken(csrfToken, request, response);
    }
    return csrfToken;
  }

}
