package com.powerdialer.aop;

import com.powerdialer.dto.AdminLogFormDTO;
import com.powerdialer.model.ActionLog;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.LSC;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.service.ActionLogService;
import com.powerdialer.util.Constants.ACTION_TARGET;
import com.powerdialer.util.Constants.ACTION_TYPE;
import com.powerdialer.util.Constants.LOG_LEVEL;
import com.powerdialer.util.Constants.LOG_MODULE;
import org.apache.commons.lang3.text.WordUtils;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.dozer.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

/**
 * Intercepter for save method of AdminLogService. AdminLog objects are
 * converted into ActionLog and saved to db.
 */
@Aspect
public class ActionLogAspect {

  @Autowired
  private ActionLogService actionLogService;

  @Autowired
  private Mapper mapper;

  /**
   * Convert AdminLog to ActionLog
   * @param returnValue
   */
  @AfterReturning(value = "execution(* com.powerdialer.service.AdminLogService.save(..))", returning = "returnValue")
  public void convertAdminLogToActionLog(AdminLogFormDTO returnValue) {
    ActionLog actionLog = new ActionLog();
    actionLog.setLogModule(LOG_MODULE.values()[returnValue.getActionTarget()]);
    actionLog.setLogLevel(LOG_LEVEL.INFO);
    actionLog.setCreateDate(new Date());
    actionLog.setLog(createActionLog(returnValue));

    if (returnValue.getUser() != null)
      actionLog.setUser(mapper.map(returnValue.getUser(), User.class));

    if (returnValue.getTenant() != null)
      actionLog.setTenant(mapper.map(returnValue.getTenant(), Tenant.class));

    if (returnValue.getActionTarget() == ACTION_TARGET.CAMPAIGN.ordinal())
      actionLog.setCampaign(buildCampaign(returnValue));
    else if (returnValue.getActionTarget() == ACTION_TARGET.DNC.ordinal())
      actionLog.setDnc(buildDnc(returnValue));
    else if (returnValue.getActionTarget() == ACTION_TARGET.LSC.ordinal())
      actionLog.setLsc(buildLsc(returnValue));

    actionLogService.addActionLog(actionLog);

  }

  /**
   * Build campaign out of adminLog object
   * @param logFormDTO
   * @return Campaign
   */
  private Campaign buildCampaign(AdminLogFormDTO logFormDTO) {
    Campaign campaign = new Campaign();
    campaign.setId(logFormDTO.getActionValue());
    campaign.setName(logFormDTO.getActionValueName());
    campaign.setVersion(logFormDTO.getActionTargetVersion());
    return campaign;
  }

  /**
   * Build dnc out of adminLog object
   * @param logFormDTO
   * @return DNC
   */
  private DNC buildDnc(AdminLogFormDTO logFormDTO) {
    DNC dnc = new DNC();
    dnc.setId(logFormDTO.getActionValue());
    dnc.setName(logFormDTO.getActionValueName());
    return dnc;
  }

  /**
   * Build lsc out of adminLog object
   * @param logFormDTO
   * @return LSC
   */
  private LSC buildLsc(AdminLogFormDTO logFormDTO) {
    LSC lsc = new LSC();
    lsc.setId(logFormDTO.getActionValue());
    lsc.setName(logFormDTO.getActionValueName());
    return lsc;
  }

  /**
   * Create actionLog string out of adminLog object
   * @param logFormDTO
   * @return actionLog string
   */
  private String createActionLog(AdminLogFormDTO logFormDTO) {
    return WordUtils.capitalize(ACTION_TARGET.getNameByOrdinal(logFormDTO.getActionTarget()).toLowerCase()) +
           " [id=" + logFormDTO.getActionValue() + ", name=" + logFormDTO.getActionValueName() + "]" +
           " is " + ACTION_TYPE.getNameByOrdinal(logFormDTO.getActionType()).toLowerCase() + "d!";
  }

}
