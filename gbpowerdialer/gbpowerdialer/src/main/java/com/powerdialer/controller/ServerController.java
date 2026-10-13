package com.powerdialer.controller;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.dto.ServerFormDTO;
import com.powerdialer.dto.ServerListDTO;
import com.powerdialer.model.Server;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.service.ServerService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;

@Controller
@RequestMapping("/server")
public class ServerController extends GenericController<Server, ServerFormDTO, ServerListDTO> {

  @Autowired
  AuthUtil authUtil;

  private final String [] authorities = { Constants.PERMISSION_DIALER_ADMINISTRATION};

  @Autowired
  public ServerController(ServerService serverService) {
    super(serverService);
  }

  @Override
  public boolean isPreAuthorized() {
    return authUtil.isUserInRoles(authorities);
  }

  @RequestMapping(value = "/disconnect", method = RequestMethod.POST)
  public @ResponseBody ListDetailed<ServerListDTO> disconnect(@RequestBody String requestBody) {
    JSONObject param = new JSONObject(requestBody).getJSONObject("params");
    ((ServerService) getService()).disconnect(param.getString("ip"));
    return getAll(param.getJSONObject("page"), param.getJSONObject("sort"), param.getJSONObject("filters"));
  }

  @RequestMapping(value = "/connect", method = RequestMethod.POST)
  public @ResponseBody ListDetailed<ServerListDTO> connect(@RequestBody String requestBody) {
    JSONObject param = new JSONObject(requestBody).getJSONObject("params");
    ((ServerService) getService()).connect(param.getLong("id"));
    return getAll(param.getJSONObject("page"), param.getJSONObject("sort"), param.getJSONObject("filters"));
  }

}
