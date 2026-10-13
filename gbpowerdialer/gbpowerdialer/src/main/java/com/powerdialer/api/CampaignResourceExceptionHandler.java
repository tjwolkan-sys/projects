package com.powerdialer.api;

import com.powerdialer.exceptions.CampaignNotFoundException;
import com.powerdialer.exceptions.InvalidApiTokenException;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ResponseUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

@ControllerAdvice(assignableTypes = { CampaignResource.class })
public class CampaignResourceExceptionHandler {

  @ExceptionHandler(InvalidApiTokenException.class)
  public @ResponseBody ResponseEntity<String> handleInvalidApiTokenException(InvalidApiTokenException ex) {
    return ResponseUtils.badRequest(Constants.SERVICE_STATUS.ERROR_2013, ex.getMessage());
  }

  @ExceptionHandler(CampaignNotFoundException.class)
  public @ResponseBody ResponseEntity handleCampaignNotFoundException(CampaignNotFoundException ex, HttpServletRequest request) {
    return ResponseUtils.badRequest(Constants.SERVICE_STATUS.ERROR_2006, ex.getMessage());
  }
}
