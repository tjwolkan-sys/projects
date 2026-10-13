package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;

import com.powerdialer.dao.IScheduleAutomationDao;
import com.powerdialer.dto.ScheduleAutomationFormDTO;
import com.powerdialer.dto.ScheduleAutomationListDTO;
import com.powerdialer.model.ScheduleAutomation;
import com.powerdialer.validators.ScheduleAutomationValidator;
import com.powerdialer.util.Constants;
import com.powerdialer.model.User;
import com.powerdialer.model.base.BaseEntity;

@Service
public class ScheduleAutomationService extends GenericService<ScheduleAutomation, ScheduleAutomationFormDTO, ScheduleAutomationListDTO> {

  @Autowired
  public ScheduleAutomationService(IScheduleAutomationDao dao) {
    super(dao, ScheduleAutomation.class, ScheduleAutomationFormDTO.class, ScheduleAutomationListDTO.class, new ScheduleAutomationValidator());
  }



  private void ensureDefaultComparisonType(ScheduleAutomation entity) {
    if (entity != null && entity.getComparisonType() == null) {
      entity.setComparisonType(Constants.COMPARISON_STRATEGY_TYPE.ONE_BY_ONE);
    }
  }

  @Override
  @Transactional(readOnly = false)
  public ScheduleAutomationFormDTO save(final ScheduleAutomationFormDTO item) {
    ScheduleAutomation entity = mapper.map(item, ScheduleAutomation.class);
    ensureDefaultComparisonType(entity);

    if (getValidator().validate(entity).getResult()) {
      if (entity instanceof BaseEntity) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        setCreateUpdateFields((BaseEntity) entity, user);
      }
      entity = (ScheduleAutomation) getDao().save(entity);
      return mapper.map(entity, ScheduleAutomationFormDTO.class);
    }
    return item; // TODO handle validation fail
  }

  @Override
  @Transactional(readOnly = false)
  public ScheduleAutomationFormDTO merge(final ScheduleAutomationFormDTO item) {
    ScheduleAutomation entity = mapper.map(item, ScheduleAutomation.class);
    ensureDefaultComparisonType(entity);

    boolean isBaseEntity = entity instanceof BaseEntity;
    if (getValidator().validate(entity).getResult() && isBaseEntity) {
      BaseEntity o = (BaseEntity) entity;
      o.setUpdateDate(new java.util.Date());
      User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
      o.setUpdateUser(user);
      entity = (ScheduleAutomation) getDao().merge(entity);
      return mapper.map(entity, ScheduleAutomationFormDTO.class);
    } else if (getValidator().validate(entity).getResult() && !isBaseEntity) {
      entity = (ScheduleAutomation) getDao().merge(entity);
      return mapper.map(entity, ScheduleAutomationFormDTO.class);
    }
    return item; // TODO handle validation fail
  }

  @Override
  @Transactional(readOnly = false)
  public ScheduleAutomationFormDTO saveOrUpdate(final ScheduleAutomationFormDTO item)
          throws com.powerdialer.exceptions.DuplicatedNameException {

    ScheduleAutomation entity = mapper.map(item, ScheduleAutomation.class);
    ensureDefaultComparisonType(entity);

    if (getValidator().validate(entity).getResult()) {
      if (entity instanceof BaseEntity) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        setCreateUpdateFields((BaseEntity) entity, user);
      }
      getDao().saveOrUpdate(entity);
      return mapper.map(entity, ScheduleAutomationFormDTO.class);
    }
    return item; // TODO handle validation fail
  }



}
