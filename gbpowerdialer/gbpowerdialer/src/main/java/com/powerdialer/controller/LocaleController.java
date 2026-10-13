package com.powerdialer.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.service.LocaleService;

@Controller
public class LocaleController {

  @Autowired
  public LocaleService localeService;

  public LocaleService getLocaleService() {
    return localeService;
  }

  public void setLocaleService(LocaleService localeService) {
    this.localeService = localeService;
  }

  @RequestMapping(value = "/locale", method = RequestMethod.POST)
  @ResponseBody
  public void setLocale(@RequestBody String lang, HttpServletRequest req, HttpServletResponse res) {
    localeService.setLocale(lang, req, res);
  }

}
