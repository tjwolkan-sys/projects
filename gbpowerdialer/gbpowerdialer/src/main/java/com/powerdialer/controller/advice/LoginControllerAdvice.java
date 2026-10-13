package com.powerdialer.controller.advice;

import com.powerdialer.controller.LoginController;
import com.powerdialer.service.LocaleService;
import com.powerdialer.util.BadStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;

@ControllerAdvice(assignableTypes = LoginController.class)
public class LoginControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(LoginControllerAdvice.class);

    private final LocaleService localeService;

    @Autowired
    public LoginControllerAdvice(LocaleService localeService) {
        this.localeService = localeService;
    }

    @ExceptionHandler({
            BadCredentialsException.class
    })
    public ResponseEntity<BadStatus> handleGenericException(BadCredentialsException ignored,
                                                            HttpServletRequest request) {
        String language = (request.getSession().getAttribute("locale") != null) ? (String) request.getSession().getAttribute("locale") : "en";
        return ResponseEntity.badRequest().body(new BadStatus(localeService.get("bad.credentials.spring", language), true));
    }
}
