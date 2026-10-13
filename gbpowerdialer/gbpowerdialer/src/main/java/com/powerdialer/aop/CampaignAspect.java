package com.powerdialer.aop;

import com.powerdialer.dto.CampaignListDTO;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.model.Campaign;
import com.powerdialer.security.PermissionUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.dozer.Mapper;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Interceptor for save, edit and delete method of CampaignService and to handle
 * campaign forceful stop
 */
@Component
@Aspect
public class CampaignAspect {

  private final Mapper mapper;
  private final PermissionUtil permissionUtil;
  private final Map<String, DeferredResult<List<?>>> suspendedUpdates = new ConcurrentHashMap<>();
  private final Map<String, Authentication> sessionToAuthentication = new ConcurrentHashMap<>();

  @Autowired
  public CampaignAspect(Mapper mapper, PermissionUtil permissionUtil) {
    this.mapper = mapper;
    this.permissionUtil = permissionUtil;
  }

  /**
   * Intercept remove action for campaignContext
   */

  @AfterReturning(value = "execution(* com.powerdialer.engine.dialer.DialerFacade.removeCampaignContext(..))", returning = "returnValue")
  public void afterRemovingCampaignContext(CampaignContext returnValue) throws Throwable {
    Campaign campaign = returnValue.getCampaign();
    suspendedUpdates.forEach((k, v) -> {
      Authentication authentication = sessionToAuthentication.get(k);
      if (authentication != null && permissionUtil.canViewCampaign(campaign.getId().toString(), authentication))
        v.setResult(Collections.singletonList(mapper.map(campaign, CampaignListDTO.class)));
    });
  }

  /**
   * Intercept all save and update actions for campaigns
   */
  @AfterReturning(value = "((execution(* com.powerdialer.dao.impl.GenericDao.save*(..))) || execution(* com.powerdialer.dao.impl.GenericDao.merge(..))) "
      + "&& target(com.powerdialer.dao.ICampaignDao)", returning = "returnValue")
  public void afterReturningCampaign(Campaign returnValue) {
    suspendedUpdates.forEach((k, v) -> {
      Authentication authentication = sessionToAuthentication.get(k);
      if (authentication != null && permissionUtil.canViewCampaign(returnValue.getId().toString(), authentication))
        v.setResult(Collections.singletonList(mapper.map(returnValue, CampaignListDTO.class)));
    });
  }

  /**
   * Intercept all delete actions for campaigns
   */
  @SuppressWarnings("unchecked")
  @Around(value = "(execution(* com.powerdialer.dao.impl.GenericDao.delete(..))) && target(com.powerdialer.dao.ICampaignDao)", argNames = "joinPoint")
  public void afterDeletingCampaign(ProceedingJoinPoint joinPoint) throws Throwable {
    Object[] args = joinPoint.getArgs();
    joinPoint.proceed(args);
    if (args != null && args.length > 0) {
      List<Long> ids = new ArrayList<>();
      if (args[0] instanceof Long) {
        ids.add(((Long) args[0]));
      } else if (args[0] instanceof List) {
        ids.addAll((Collection<? extends Long>) args[0]);
      }
      final List<CampaignListDTO> deletedCampaigns = ids.stream().map(id -> {
        Campaign campaign = new Campaign();
        campaign.setId(id);
        campaign.setDeleted(true);
        return campaign;
      }).map(c -> mapper.map(c, CampaignListDTO.class)).collect(Collectors.toList());
      suspendedUpdates.forEach((k, v) -> v.setResult(deletedCampaigns));
    }
  }

  /**
   * Intercept enable/disable actions for campaigns
   * @param joinPoint
   * @throws Throwable
   */
  @SuppressWarnings("unchecked")
  @Around(value = "(execution(* com.powerdialer.dao.impl.GenericDao.setEnabled(..))) && target(com.powerdialer.dao.ICampaignDao)", argNames = "joinPoint")
  public void afterEnablingCampaign(ProceedingJoinPoint joinPoint) throws Throwable {
    Object[] args = joinPoint.getArgs();
    joinPoint.proceed(args);
    if (args != null) {
      List<Long> ids = ((List<Long>) args[0]);
      boolean enabled = ((boolean) args[1]);
      List<JSONObject> result = ids.stream().map(id -> {
        JSONObject campaign = new JSONObject();
        campaign.put("id", id);
        campaign.put("enabled", enabled);
        return campaign;
      }).collect(Collectors.toList());
      suspendedUpdates.forEach((k, v) -> v.setResult(result));
    }
  }

  public DeferredResult<List<?>> removeSuspendedUpdates(HttpSession session) {
    sessionToAuthentication.remove(session.getId());
    return suspendedUpdates.remove(session.getId());
  }

  public DeferredResult<List<?>> addSuspendedUpdates(HttpSession session, DeferredResult<List<?>> deferredResult) {
    sessionToAuthentication.put(session.getId(), SecurityContextHolder.getContext().getAuthentication());
    return suspendedUpdates.put(session.getId(), deferredResult);
  }
  
}
