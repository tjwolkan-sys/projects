package com.powerdialer.controller;

import com.powerdialer.oauth.OneAccessException;
import com.powerdialer.request.LoginRequest;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.util.Constants;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/auth")
public class LoginController {

  private final AuthUtil authUtil;

  @Autowired
  public LoginController(AuthUtil authUtil) {
    this.authUtil = authUtil;
  }

  @RequestMapping(value = "/oauthLogin", method = RequestMethod.GET)
  public String globalAuthenticate() throws OneAccessException {
    authUtil.doOneAccessAuth();
    return "redirect:/pages/oauthSuccess.jsp";
  }

  @PostMapping(value = "/login")
  public @ResponseBody String authenticate(@RequestBody LoginRequest loginRequest, HttpServletResponse response) {
    if (loginRequest.getCredentials() == null) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return null;
    }
    String name = loginRequest.getCredentials().getUsername();
    String password = loginRequest.getCredentials().getPassword();
    return authUtil.tryToAuthenticate(name, password);
  }

  @RequestMapping(value = "/logout", method = { RequestMethod.POST, RequestMethod.GET })
  public @ResponseBody void resetAuthentication(HttpServletRequest request) {
    String locale = (String) request.getSession().getAttribute("locale");
    request.getSession().invalidate();
    request.getSession().setAttribute("locale", locale);
    SecurityContextHolder.getContext().setAuthentication(null);
  }

  @RequestMapping(value = "/check", method = { RequestMethod.POST, RequestMethod.GET })
  public @ResponseBody String check() {
    Authentication authenticate = SecurityContextHolder.getContext().getAuthentication();
    JSONObject response = new JSONObject();
    if (authenticate != null && !authenticate.getAuthorities().contains(new SimpleGrantedAuthority(Constants.ROLE_ANONYMOUS)))
      authUtil.fillSuccessResponse(response);
    else
      response.put("authenticated", false);
    return response.toString();
  }

}
