package com.powerdialer.controller;

import com.powerdialer.exceptions.ArgumentativeException;
import com.powerdialer.exceptions.AstServerConnectionException;
import com.powerdialer.exceptions.CampaignDbConnectionException;
import com.powerdialer.exceptions.DisconnectRunningCampaignException;
import com.powerdialer.exceptions.DncExpireDateValidationException;
import com.powerdialer.exceptions.DncPhoneExpiredDateBeforeCreateDateException;
import com.powerdialer.exceptions.DncPnFileImportFailedException;
import com.powerdialer.exceptions.DncPnFileImportInvalidCharacterException;
import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.exceptions.LdapBadCredentialsException;
import com.powerdialer.exceptions.ModifiedWhilePrioritiesRecalculatingException;
import com.powerdialer.exceptions.ModifiedWhileUploadingException;
import com.powerdialer.exceptions.NonUniqueGlobalDNC;
import com.powerdialer.exceptions.NonUniqueUserException;
import com.powerdialer.exceptions.PasswordShallBeChangedException;
import com.powerdialer.exceptions.PhoneNumberNameDuplicateException;
import com.powerdialer.exceptions.QueryException;
import com.powerdialer.exceptions.SortConditionsOvercomplicatedException;
import com.powerdialer.service.LocaleService;
import com.powerdialer.util.BadStatus;
import org.hibernate.StaleObjectStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.Map;

@ControllerAdvice
public class ExceptionHandlingController {

  private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandlingController.class);

  @Autowired
  public LocaleService localeService;

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(LdapBadCredentialsException.class)
  public @ResponseBody BadStatus handleException(LdapBadCredentialsException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    return new BadStatus(localeService.get("bad.credentials.ldap", lang), true);
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(PasswordShallBeChangedException.class)
  public @ResponseBody BadStatus handleException(PasswordShallBeChangedException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error(ex.getMessage());
    return new BadStatus(localeService.get("password.shall.be.changed", lang), true);
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(NonUniqueGlobalDNC.class)
  public @ResponseBody BadStatus handleException(NonUniqueGlobalDNC ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error(ex.getMessage());
    return new BadStatus(localeService.get("dnc.global.non_unique", lang), true);
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(StaleObjectStateException.class)
  public @ResponseBody BadStatus handleException(StaleObjectStateException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error("", ex);
    return new BadStatus(localeService.get("stale.object", lang), true);
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(DuplicatedNameException.class)
  public @ResponseBody BadStatus handleException(DuplicatedNameException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error(ex.getMessage());
    return new BadStatus(localeService.get("name.duplicated", lang), true);
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(ConstraintViolationException.class)
  public @ResponseBody BadStatus handleException(ConstraintViolationException ex, HttpServletRequest request) {
    StringBuilder msg = new StringBuilder();
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    for (ConstraintViolation cv : ex.getConstraintViolations()) {
      Map descriptorAttributes = cv.getConstraintDescriptor().getAttributes();
      String messageKey = cv.getMessageTemplate().replace("{", "").replace("}", "");
      String message = localeService.getEvaluatingAttributes(messageKey, lang, descriptorAttributes);
      msg.append(message).append("\n");
    }
    LOG.error("", ex);
    return new BadStatus(msg.toString(), true);
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(ArgumentativeException.class)
  public @ResponseBody BadStatus handleException(ArgumentativeException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error("", ex);
    return new BadStatus(localeService.getWithArguments(ex.getMessage(), lang, ex.getArguments()), true);
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(AstServerConnectionException.class)
  public @ResponseBody BadStatus handleException(AstServerConnectionException ex, HttpServletRequest request) {
    LOG.error("", ex);
    return new BadStatus(ex.getMessage(), true);
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(CampaignDbConnectionException.class)
  public @ResponseBody BadStatus handleException(CampaignDbConnectionException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error("", ex);
    return new BadStatus(localeService.get("db.connection.error", lang) + ": " + ex.getMessage(), true);
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(QueryException.class)
  public @ResponseBody BadStatus handleException(QueryException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error("", ex);
    return new BadStatus(localeService.get("db.query.error", lang) + ": " + ex.getMessage(), true);
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(SQLException.class)
  public @ResponseBody BadStatus handleException(SQLException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error("", ex);
    return new BadStatus(localeService.get("db.query.error", lang) + ": " + ex.getMessage(), true);
  }

  @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(NonUniqueUserException.class)
  public @ResponseBody BadStatus handleException(NonUniqueUserException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error("", ex);
    return new BadStatus(localeService.get("authentication.failed", lang), true);
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(PhoneNumberNameDuplicateException.class)
  public @ResponseBody BadStatus handleException(PhoneNumberNameDuplicateException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error(ex.getMessage());
    return new BadStatus(localeService.get("name.phoneNumber.duplicated", lang), true);
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(SortConditionsOvercomplicatedException.class)
  public @ResponseBody BadStatus handleException(SortConditionsOvercomplicatedException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error(ex.getMessage());
    return new BadStatus(localeService.get(ex.getMessage(), lang), true);
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
  @ExceptionHandler(ModifiedWhileUploadingException.class)
  public @ResponseBody BadStatus handleException(ModifiedWhileUploadingException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error(ex.getMessage());
    return new BadStatus(localeService.get("campaign.modifying_while_upload", lang), true);
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
  @ExceptionHandler(ModifiedWhilePrioritiesRecalculatingException.class)
  public @ResponseBody BadStatus handleException(ModifiedWhilePrioritiesRecalculatingException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error(ex.getMessage());
    return new BadStatus(localeService.get("campaign.modifying_while_recalculate", lang), true);
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(DisconnectRunningCampaignException.class)
  public @ResponseBody BadStatus handleException(DisconnectRunningCampaignException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error(ex.getMessage());
    return new BadStatus(localeService.get("campaign.running.disconnect", lang), true);
  }

  @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(DncPnFileImportFailedException.class)
  public @ResponseBody BadStatus handleException(DncPnFileImportFailedException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error(ex.getMessage());
    return new BadStatus(localeService.get("dnc.pn.file.import.fail", lang), true);
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(DncPnFileImportInvalidCharacterException.class)
  public @ResponseBody BadStatus handleException(DncPnFileImportInvalidCharacterException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error(ex.getMessage());
    return new BadStatus( localeService.get("dnc.pn.file.import.invalid.character", lang), true);
  }

  @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(DncExpireDateValidationException.class)
  public @ResponseBody BadStatus handleException(DncExpireDateValidationException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error(ex.getMessage());
    return new BadStatus(localeService.get("dnc.expire.date.invalid", lang), true);
  }

  @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(DncPhoneExpiredDateBeforeCreateDateException.class)
  public @ResponseBody BadStatus handleException(DncPhoneExpiredDateBeforeCreateDateException ex, HttpServletRequest request) {
    String lang = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
    LOG.error(ex.getMessage());
    return new BadStatus(localeService.get("dnc.phone.expire.date.before.create.date.invalid", lang), true);
  }
}
