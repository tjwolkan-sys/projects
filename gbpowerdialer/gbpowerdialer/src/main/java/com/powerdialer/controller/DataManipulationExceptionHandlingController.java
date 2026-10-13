package com.powerdialer.controller;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

import com.powerdialer.exceptions.InvalidApiTokenException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.powerdialer.exceptions.BadContentException;
import com.powerdialer.exceptions.BadFilterConditionException;
import com.powerdialer.exceptions.BadRequestColumnName;
import com.powerdialer.exceptions.CampaignNotFoundException;
import com.powerdialer.exceptions.DNCNotFoundException;
import com.powerdialer.exceptions.EmptyDataException;
import com.powerdialer.exceptions.EmptyFiltersWarningException;
import com.powerdialer.exceptions.FetchLimitExceedException;
import com.powerdialer.exceptions.IllegalValueException;
import com.powerdialer.exceptions.MissingFieldInFilterException;
import com.powerdialer.exceptions.MultipartFileProcessingException;
import com.powerdialer.exceptions.UnknownFileExtensionException;
import com.powerdialer.exceptions.PriorityRecalculationStartException;
import com.powerdialer.service.TokenCampaignValidatorService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.SERVICE_STATUS;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.ResponseUtils;

/**
 * Exception handler for data manipulation controllers
 */
@ControllerAdvice(assignableTypes = { CampaignDataManipulationController.class, DNCDataManipulationController.class, TokenCampaignValidatorService.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataManipulationExceptionHandlingController {

  private final LoggerDecorator LOG;

  @Autowired
  public DataManipulationExceptionHandlingController(LoggerDecorator loggerDecorator) {
    LOG = loggerDecorator.getLogger(this.getClass());
  }

  @ExceptionHandler(InvalidApiTokenException.class)
  public @ResponseBody ResponseEntity handleException(InvalidApiTokenException ex, HttpServletRequest request) {
    return ResponseUtils.badRequest(Constants.SERVICE_STATUS.ERROR_2013, ex.getMessage());
  }

  @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(Exception.class)
  public @ResponseBody String handleException(Exception ex, HttpServletRequest request) {
    LOG.error("Error raised. url: " + request.getRequestURL() + " method: " + request.getMethod(), ex);
    return ExceptionUtils.getStackTrace(ex);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public @ResponseBody ResponseEntity handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
    String error = ex.getParameterName() + " parameter is missing";
    return ResponseEntity.badRequest().body(error);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public @ResponseBody ResponseEntity handleConstraintViolationException(ConstraintViolationException ex) {
    List<String> errorMessages = ex.getConstraintViolations().stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.toList());
    String errorMessage = String.join(", ", errorMessages);
    return ResponseEntity.badRequest().body(errorMessage);
  }

  @ExceptionHandler(EmptyFiltersWarningException.class)
  public @ResponseBody ResponseEntity handleEmptyFiltersWarning(EmptyFiltersWarningException ex, HttpServletRequest request) {
    LOG.warn("Request to URL:" + request.getRequestURL() + " has no filters", Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.ok(ex.getResult(), SERVICE_STATUS.WARNING_1001);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public @ResponseBody ResponseEntity handleEmptyBodyException(HttpMessageNotReadableException ex, HttpServletRequest request) {
    LOG.warn("Request to URL:" + request.getRequestURL() + " has unreadable body", Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.badRequest(SERVICE_STATUS.ERROR_2000);
  }

  @ExceptionHandler(EmptyDataException.class)
  public @ResponseBody ResponseEntity handleEmptyDataException(EmptyDataException ex, HttpServletRequest request) {
    LOG.warn("Request to URL:" + request.getRequestURL() + " has no data object", ex, Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.badRequest(SERVICE_STATUS.ERROR_2002);
  }

  @ExceptionHandler(UnknownFileExtensionException.class)
  public @ResponseBody ResponseEntity handleUnknownFileExtension(UnknownFileExtensionException ex, HttpServletRequest request) {
    LOG.warn("Request to URL:" + request.getRequestURL() + " has bad file extension", ex, Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.badRequest(SERVICE_STATUS.ERROR_2003);
  }

  @ExceptionHandler(BadContentException.class)
  public @ResponseBody ResponseEntity handleBadContentException(BadContentException ex, HttpServletRequest request) {
    LOG.warn("Error raised while processing multipart file. URL:" + request.getRequestURL() + " Message: " + ex.getMessage(), ex, Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.badRequest(SERVICE_STATUS.ERROR_2004);
  }

  @ExceptionHandler(BadFilterConditionException.class)
  public @ResponseBody ResponseEntity handleBadFilterConditionException(BadFilterConditionException ex, HttpServletRequest request) {
    LOG.warn("URL:" + request.getRequestURL() + " Message: " + ex.getMessage(), ex, Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.badRequest(SERVICE_STATUS.ERROR_2005);
  }

  @ExceptionHandler(CampaignNotFoundException.class)
  public @ResponseBody ResponseEntity handleCampaignNotFoundException(CampaignNotFoundException ex, HttpServletRequest request) {
    LOG.warn("URL:" + request.getRequestURL() + " Message: " + ex.getMessage(), ex, Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.badRequest(SERVICE_STATUS.ERROR_2006, ex.getMessage());
  }

  @ExceptionHandler(DNCNotFoundException.class)
  public @ResponseBody ResponseEntity handleDNCNotFoundException(CampaignNotFoundException ex, HttpServletRequest request) {
    LOG.warn("URL:" + request.getRequestURL() + " Message: " + ex.getMessage(), ex, Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.badRequest(SERVICE_STATUS.ERROR_2007, ex.getMessage());
  }

  @ExceptionHandler(BadRequestColumnName.class)
  public @ResponseBody ResponseEntity handleBadRequestColumnName(BadRequestColumnName ex, HttpServletRequest request) {
    LOG.info("URL:" + request.getRequestURL() + " Message: " + ex.getMessage(), ex, Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.serverError(SERVICE_STATUS.ERROR_2009, ex.getMessage());
  }

  @ExceptionHandler(MissingFieldInFilterException.class)
  public @ResponseBody ResponseEntity handleBadFilterKeyException(MissingFieldInFilterException ex, HttpServletRequest request) {
    LOG.info("URL:" + request.getRequestURL() + " Message: " + ex.getMessage(), ex, Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.badRequest(SERVICE_STATUS.ERROR_2010);
  }

  @ExceptionHandler(IllegalValueException.class)
  public @ResponseBody ResponseEntity handleIllegalValueException(IllegalValueException ex, HttpServletRequest request) {
    LOG.warn("URL:" + request.getRequestURL() + " Message: " + ex.getMessage(), ex, Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.badRequest(SERVICE_STATUS.ERROR_2011);
  }

  @ExceptionHandler(PriorityRecalculationStartException.class)
  public @ResponseBody ResponseEntity handleException(PriorityRecalculationStartException ex, HttpServletRequest request) {
    LOG.warn("URL:" + request.getRequestURL() + " Message: " + ex.getMessage(), ex, Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.badRequest(SERVICE_STATUS.ERROR_2012, ex.getMessage());
  }

  @ExceptionHandler(SQLException.class)
  public @ResponseBody ResponseEntity handleSQLException(SQLException ex, HttpServletRequest request) {
    LOG.warn("Error raised while executing SQL script. URL:" + request.getRequestURL() + " Message: " + ex.getMessage(), ex, Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.serverError(SERVICE_STATUS.INTERNAL_ERROR_3002, ex.getMessage());
  }

  @ExceptionHandler(MultipartFileProcessingException.class)
  public @ResponseBody ResponseEntity handleMultipartFileProcessingException(MultipartFileProcessingException ex, HttpServletRequest request) {
    LOG.warn("Error raised while processing multipart file. URL:" + request.getRequestURL() + " Message: " + ex.getMessage(), ex, Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.serverError(SERVICE_STATUS.INTERNAL_ERROR_3001, ex.getMessage());
  }

  @ExceptionHandler(FetchLimitExceedException.class)
  public @ResponseBody ResponseEntity handleFetchLimitExceedException(FetchLimitExceedException ex, HttpServletRequest request) {
    LOG.warn("Error raised because limit of read response is exceed. URL:" + request.getRequestURI() + " Message: " + ex.getMessage(), ex, Constants.LOG_MODULE.DATA_MANIPULATION);
    return ResponseUtils.badRequest(SERVICE_STATUS.ERROR_2008);
  }

}
