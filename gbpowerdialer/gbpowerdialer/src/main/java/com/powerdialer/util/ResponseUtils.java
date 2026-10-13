package com.powerdialer.util;

import static com.powerdialer.util.Constants.SERVICE_STATUS.SUCCESS_1000;
import static com.powerdialer.util.Constants.SERVICE_STATUS_HEADER_NAME;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ResponseUtils {

  private ResponseUtils() {}

  /**
   * Create default successful response
   * @param body response body
   * @param <T> response body type
   * @return ResponseEntity
   */
  public static <T> ResponseEntity<T> ok(T body) {
    return ok(body, SUCCESS_1000);
  }

  /**
   * Create successful response with specific service status
   * @param body response body
   * @param status service status
   * @param <T> response body type
   * @return ResponseEntity
   */
  public static <T> ResponseEntity<T> ok(T body, Constants.SERVICE_STATUS status) {
    return ResponseEntity.status(HttpStatus.OK).header(SERVICE_STATUS_HEADER_NAME, String.valueOf(status.getCode())).body(body);
  }

  /**
   * Create response with {@link HttpStatus#BAD_REQUEST} HTTP status and specific service status
   * @param status service status
   * @return ResponseEntity
   */
  public static ResponseEntity<Void> badRequest(Constants.SERVICE_STATUS status) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).header(SERVICE_STATUS_HEADER_NAME, String.valueOf(status.getCode())).build();
  }

  /**
   * Create response with {@link HttpStatus#BAD_REQUEST} HTTP status, specific service status and error message
   * @param status service status
   * @param message error message
   * @return ResponseEntity
   */
  public static ResponseEntity<String> badRequest(Constants.SERVICE_STATUS status, String message) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).header(SERVICE_STATUS_HEADER_NAME, String.valueOf(status.getCode())).body(message);
  }

  /**
   * Create response with {@link HttpStatus#INTERNAL_SERVER_ERROR} HTTP status, specific service status and error message
   * @param status service status
   * @param message error message
   * @return ResponseEntity
   */
  public static ResponseEntity<String> serverError(Constants.SERVICE_STATUS status, String message) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).header(SERVICE_STATUS_HEADER_NAME, String.valueOf(status.getCode())).body(message);
  }

}
