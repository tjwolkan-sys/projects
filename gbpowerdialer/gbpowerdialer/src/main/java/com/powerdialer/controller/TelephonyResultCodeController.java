package com.powerdialer.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.dto.TelephonyResultCodeFormDTO;
import com.powerdialer.dto.TelephonyResultCodeListDTO;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.service.TelephonyResultCodeService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;

@Controller
@RequestMapping("/telephonyRc")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN', 'ROLE_SUPERVISOR', 'ROLE_TEAMLEAD')")
public class TelephonyResultCodeController extends GenericController<TelephonyResultCode, TelephonyResultCodeFormDTO, TelephonyResultCodeListDTO> {

  @Autowired
  AuthUtil authUtil;

  private final String [] authorities = { Constants.PERMISSION_DIALER_ADMINISTRATION};

  @Autowired
  public TelephonyResultCodeController(TelephonyResultCodeService service) {
    super(service);
  }

  @Override
  public boolean isPreAuthorized() {
    return authUtil.isUserInRoles(authorities);
  }

  @Autowired
  private ServletContext context;

  @RequestMapping(value = "/list", method = RequestMethod.GET)
  public @ResponseBody ListDetailed<TelephonyResultCodeListDTO> getAll(@RequestParam(value = "page", required = false) JSONObject page,
                                                                       @RequestParam(value = "sort", required = false) JSONObject sort,
                                                                       @RequestParam(value = "filters", required = false) JSONObject filters) {
    Map<Integer, TelephonyResultCode> telephonyResultCodes = (Map<Integer, TelephonyResultCode>) context.getAttribute(Constants.TELEPHONY_RESULT_CODES);
    List<TelephonyResultCodeListDTO> telephonyResultCodeListDTOs = getService().convertEntitiesToDTO(new ArrayList<>(telephonyResultCodes.values()));
    ListDetailed<TelephonyResultCodeListDTO> listDetailed = new ListDetailed<>();
    listDetailed.setData(telephonyResultCodeListDTOs);
    listDetailed.setTotalItems(telephonyResultCodeListDTOs.size());
    return listDetailed;
  }

}
