package com.powerdialer.service;

import com.powerdialer.dao.ICampaignDao;
import com.powerdialer.dao.ITenantDao;
import com.powerdialer.dao.IUserDao;
import com.powerdialer.datamanipulation.DataManipulationStateHolder;
import com.powerdialer.dto.AttachedDataFormDTO;
import com.powerdialer.dto.BaseEntityDTO;
import com.powerdialer.dto.CampaignFormDTO;
import com.powerdialer.dto.CampaignGroupFormDTO;
import com.powerdialer.dto.CampaignListDTO;
import com.powerdialer.dto.FieldDTO;
import com.powerdialer.dto.ScheduleBlackoutAutomationFormDTO;
import com.powerdialer.dto.FilterFormDTO;
import com.powerdialer.dto.FilterTestDTO;
import com.powerdialer.dto.PhoneNumberColumnFormDTO;
import com.powerdialer.dto.ResultCodeScheduleAdvancedFormDTO;
import com.powerdialer.dto.ResultCodeScheduleFormDTO;
import com.powerdialer.dto.ScheduleAutomationFormDTO;
import com.powerdialer.dto.ScheduleFormDTO;
import com.powerdialer.dto.SortFieldFormDTO;
import com.powerdialer.dto.SortFieldValueFormDTO;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.priority.PriorityRecalculationStateHolder;
import com.powerdialer.exceptions.CampaignDbConnectionException;
import com.powerdialer.exceptions.CampaignIllegalStateException;
import com.powerdialer.exceptions.CampaignNotFoundException;
import com.powerdialer.exceptions.DisconnectRunningCampaignException;
import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.exceptions.QueryException;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.ScheduleAutomation;
import com.powerdialer.model.ScheduleBlackoutAutomation;
import com.powerdialer.model.CampaignFiltersStats;
import com.powerdialer.model.CampaignGroup;
import com.powerdialer.model.CampaignHolder;
import com.powerdialer.model.CampaignMemento;
import com.powerdialer.model.DNC;
import com.powerdialer.model.Filter;
import com.powerdialer.model.Location;
import com.powerdialer.model.ResultCodeSchedule;
import com.powerdialer.model.ResultCodeScheduleAdvanced;
import com.powerdialer.model.Schedule;
import com.powerdialer.model.SortField;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.publish.CampaignCreatedEvent;
import com.powerdialer.publish.CampaignMergedEvent;
import com.powerdialer.rds.ResourceDistributionServiceFacade;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.redis.message.RedisMessage;
import com.powerdialer.request.CreateSourceTableRequest;
import com.powerdialer.request.CustomFieldsRequest;
import com.powerdialer.request.DbTableRequest;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import com.powerdialer.util.Constants.CAMPAIGN_TYPE;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.ListDetailed;
import com.powerdialer.util.Utils;
import com.powerdialer.validators.CampaignValidator;
import com.powerdialer.validators.ScheduleBlackoutAutomationValidator;
import com.powerdialer.validators.ScheduleValidator;
import com.powerdialer.validators.ValidationResult;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.StaleObjectStateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PreDestroy;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.powerdialer.util.Constants.RESERVED_SQL_NAMES;

@Service
public class CampaignService extends GenericService<Campaign, CampaignFormDTO, CampaignListDTO> {

  private static final Log LOG = LogFactory.getLog(CampaignService.class);
  public static final String PREDICTIVE_STRATEGY_CHANGE_ILLEGAL_STATE_KEY = "campaign.edit.predictiveStrategy.illegal.state";
  public static final String CAMPAIGN_TYPE_CHANGE_ILLEGAL_STATE_KEY = "campaign.edit.type.illegal.state";
  /** RESTART sonrası START'ın aynı campaign kilidi için en fazla bekleme süresi. */
  static final long RESTART_FOLLOW_UP_LOCK_WAIT_MS = 300_000L;

  private final Pattern CAMPAIGN_TABLE_PATTERN = Pattern.compile(Constants.CAMPAIGN_TABLE_NAME_PATTERN);
  private final CampaignDbOperations campaignDbOperations;
  private final ITenantDao tenantDao;
  private final DialerFacade dialerFacade;
  private final CampaignMementoService campaignMementoService;
  private final CampaignActionCoordinator actionCoordinator = new CampaignActionCoordinator();
  private TransactionTemplate actionTransactionTemplate;

  @Autowired
  private AuthUtil authUtil;

  @Autowired
  private EnCipherUtils enCiperUtils;

  @Autowired
  private RedisUtil redisUtil;

  @Autowired
  private DataManipulationStateHolder dataManipulationStateHolder;

  @Autowired
  private ApplicationEventPublisher applicationEventPublisher;

  @Autowired
  private SessionFactory sessionFactory;

  @Autowired
  private PriorityRecalculationStateHolder priorityStateHolder;

  @Autowired
  private ResourceDistributionServiceFacade resourceDistributionServiceFacade;

  @Autowired
  private IUserDao userDao;

  @Autowired
  public CampaignService(ICampaignDao dao, CampaignDbOperations campaignDbOperations, ITenantDao tenantDao, DialerFacade dialerFacade,
                         CampaignMementoService campaignMementoService) {
    super(dao, Campaign.class, CampaignFormDTO.class, CampaignListDTO.class, new CampaignValidator());
    this.campaignDbOperations = campaignDbOperations;
    this.tenantDao = tenantDao;
    this.dialerFacade = dialerFacade;
    this.campaignMementoService = campaignMementoService;
    ((CampaignValidator) getValidator()).setCampaignService(this);
  }

  public DataManipulationStateHolder getDataManipulationStateHolder() {
    return dataManipulationStateHolder;
  }

  public PriorityRecalculationStateHolder getPriorityStateHolder() {
    return priorityStateHolder;
  }


  private void ensureScheduleAutomationDefaults(Campaign campaign) {
    if (campaign == null || campaign.getScheduleAutomations() == null) return;

    for (ScheduleAutomation sa : campaign.getScheduleAutomations()) {
      if (sa == null) continue;

      sa.setCampaign(campaign);
      if (sa.getComparisonType() == null) {
        sa.setComparisonType(Constants.COMPARISON_STRATEGY_TYPE.ONE_BY_ONE);
      }
    }
  }

  /**
   * Saving existing campaign
   */
  @Override
  @Transactional(readOnly = false)
  public CampaignFormDTO merge(final CampaignFormDTO item) throws DuplicatedNameException, StaleObjectStateException {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    Campaign campaign = mapper.map(item, Campaign.class);
    fixNullRoutingGroupMapping(campaign);

    if (getValidator().validate(campaign).getResult()) {

      setCampaignChildrenValues(campaign, user);
      setCampaignDefaultValues(campaign);
      ensureScheduleAutomationDefaults(campaign);

      Campaign persistedCampaign = getDao().get(item.getId());
      preserveManyToManyCollectionsIfNull(item, campaign, persistedCampaign);
      ensureDefaultDncCannotBeRemoved(campaign, persistedCampaign);
      // expire_filter_enabled DB-managed; UI/DTO tasimadigi icin form save'de mevcut DB degerini koru
      campaign.setExpireFilterEnabled(persistedCampaign.getExpireFilterEnabled());
      if (isDbPasswordChanged(item.getDbPassword(), persistedCampaign.getDbPassword())) {
        campaign.setDbPassword(enCiperUtils.encryptPassword(item.getDbPassword()));
      }

      if (persistedCampaign.getRetryCallerIdBehavior() != null && !persistedCampaign.getRetryCallerIdBehavior().equals(item.getRetryCallerIdBehavior())) {
       resourceDistributionServiceFacade.deleteHistory(persistedCampaign.getCallerIdPool().getId());
      }

      campaign.setUpdateDate(new Date());
      campaign.setUpdateUser(user);
      Campaign oldCampaign = getInitializedCampaign(campaign.getId());

      syncDefaultDncWithCampaign(oldCampaign, campaign);

      if (!StringUtils.isBlank(campaign.getDbPriorityTable()) && needForeignKeyUpdate(oldCampaign, campaign)) {
        campaignDbOperations.dropForeignKeyInPriorityTable(oldCampaign, campaign);
      }
      sessionFactory.getCurrentSession().evict(oldCampaign);
      campaign = getDao().merge(campaign);
      sessionFactory.getCurrentSession().evict(campaign);
      Campaign newCampaign = getInitializedCampaign(campaign.getId());
      sessionFactory.getCurrentSession().evict(newCampaign);
      checkForPriorityTable(oldCampaign, newCampaign);
      campaign.setDbPriorityTable(newCampaign.getDbPriorityTable());
      applicationEventPublisher.publishEvent(new CampaignMergedEvent(this, oldCampaign, newCampaign));
      RedisMessage redisMessage = new RedisMessage(Constants.MSG_ACTION_TYPE.EDIT, Constants.MSG_ENTITY_TYPE.CAMPAIGN, campaign);
      redisUtil.publishMessage(Constants.REDIS_CHANNEL.channelGBPowerDialerEntity.toString(), redisMessage);
    } else {
      return item; // TODO handle validation fail
    }
    restoreRunningCampaignState(campaign);
    return mapper.map(campaign, CampaignFormDTO.class);
  }

  /**
   * Kampanyanın default DNC listesini kampanya ile senkron tutar. Kampanya
   * tenant'ı değiştiğinde liste de yeni tenant'a taşınır; endDate değiştiğinde
   * liste expire tarihi güncellenir. İlgili alan değişmediyse DNC ekranından
   * girilmiş değer korunur.
   * @param oldCampaign DB'deki mevcut kampanya
   * @param campaign kaydedilmek üzere gelen kampanya
   */
  private void syncDefaultDncWithCampaign(Campaign oldCampaign, Campaign campaign) {
    if (oldCampaign == null || oldCampaign.getDnc() == null || oldCampaign.getDnc().getId() == null) {
      return;
    }

    boolean endDateChanged = !Objects.equals(oldCampaign.getEndDate(), campaign.getEndDate());
    Long newTenantId = getTenantId(campaign);
    Tenant newTenant = null;
    if (newTenantId != null && !newTenantId.equals(getTenantId(oldCampaign))) {
      newTenant = tenantDao.get(newTenantId);
    }

    if (!endDateChanged && newTenant == null) {
      return;
    }

    DNC dnc = sessionFactory.getCurrentSession().get(DNC.class, oldCampaign.getDnc().getId());
    if (dnc == null) {
      return;
    }

    if (endDateChanged) { // update default DNC expire date
      dnc.setCampaign(campaign);
      dnc.setExpireDate(campaign.getEndDate());
    }
    if (newTenant != null) { // kampanya tenant'ı değiştiyse default DNC listesi de yeni tenant'a taşınır
      LOG.info("Moving default DNC list " + dnc.getId() + " of campaign " + oldCampaign.getId() + " from tenant "
          + getTenantId(oldCampaign) + " to tenant " + newTenantId);
      dnc.setTenant(newTenant);
    }
    dnc.setUpdateDate(new Date());
    dnc.setUpdateUser(userDao.getDncSystemUser());

    sessionFactory.getCurrentSession().merge(dnc);
    sessionFactory.getCurrentSession().flush();
  }

  private Long getTenantId(Campaign campaign) {
    return campaign != null && campaign.getTenant() != null ? campaign.getTenant().getId() : null;
  }

  private void preserveManyToManyCollectionsIfNull(CampaignFormDTO item, Campaign campaign, Campaign persistedCampaign) {
    if (item.getLscs() == null) {
      Hibernate.initialize(persistedCampaign.getLscs());
      campaign.setLscs(new ArrayList<>(persistedCampaign.getLscs()));
    }

    if (item.getSkills() == null) {
      Hibernate.initialize(persistedCampaign.getSkills());
      campaign.setSkills(new HashSet<>(persistedCampaign.getSkills()));
    }

    if (item.getLocations() == null) {
      Hibernate.initialize(persistedCampaign.getLocations());
      campaign.setLocations(new HashSet<>(persistedCampaign.getLocations()));
    }

    if (item.getDncs() == null) {
      Hibernate.initialize(persistedCampaign.getDncs());
      campaign.setDncs(new HashSet<>(persistedCampaign.getDncs()));
    }
  }

  private void ensureDefaultDncCannotBeRemoved(Campaign campaign, Campaign persistedCampaign) {
    if (campaign == null || persistedCampaign == null || persistedCampaign.getDnc() == null || persistedCampaign.getDnc().isDeleted()) {
      return;
    }
    if (campaign.getDncs() == null) {
      campaign.setDncs(new HashSet<>());
    }
    DNC defaultDnc = persistedCampaign.getDnc();
    boolean alreadyMapped = campaign.getDncs().stream()
        .filter(Objects::nonNull)
        .anyMatch(dnc -> Objects.equals(dnc.getId(), defaultDnc.getId()));
    if (!alreadyMapped) {
      campaign.getDncs().add(defaultDnc);
    }
  }


  private boolean needForeignKeyUpdate(Campaign oldCampaign, Campaign newCampaign) {
    boolean dbListTableChanged =  !newCampaign.getDbListTable().equals(oldCampaign.getDbListTable());
    boolean colUniqueKeyChanged = !newCampaign.getColUniqueKey().equals(oldCampaign.getColUniqueKey());
    return dbListTableChanged || colUniqueKeyChanged;
  }

  public boolean needForeignKeyUpdate(CampaignFormDTO campaignFormDTO) {
    Campaign campaign = getDao().get(campaignFormDTO.getId());
    if (campaign == null) {
      return false;
    }
    boolean dbListTableChanged = !campaign.getDbListTable().equals(campaignFormDTO.getDbListTable());
    boolean colUniqueKeyChanged = !campaign.getColUniqueKey().equals(campaignFormDTO.getColUniqueKey());
    return dbListTableChanged || colUniqueKeyChanged;
  }

  private void checkForPriorityTable(Campaign campaign) {
    checkForPriorityTable(null, campaign);
  }

  private void checkForPriorityTable(Campaign oldCampaign, Campaign newCampaign) {
    if (newCampaign.isMultipleSort() && (oldCampaign == null || StringUtils.isBlank(oldCampaign.getDbPriorityTable()))) {
      try {
        String tableName = Constants.PRIORITY_TABLE_PREFIX + newCampaign.getId();
        LOG.info("Creating priority table for the " + newCampaign + ": " + tableName);
        newCampaign.setDbPriorityTable(Constants.PRIORITY_TABLE_PREFIX + newCampaign.getId());
        campaignDbOperations.createPriorityTable(newCampaign);
        newCampaign = getDao().merge(newCampaign);
        sessionFactory.getCurrentSession().evict(newCampaign);
      } catch (SQLException e) {
        newCampaign.setDbPriorityTable(null);
        throw new RuntimeException("Could not create a priority table for the " + newCampaign, e);
      }
    }
  }

  public boolean addForeignKeyToPriorityTableIfNeeded(Long campaignId,
                                                      boolean wasMultipleSort,
                                                      boolean multipleSort,
                                                      boolean needForeignKeyReplacement) {
    if ((!wasMultipleSort && multipleSort) || (needForeignKeyReplacement && multipleSort)) {
      Campaign campaign = getDao().get(campaignId);
      if (campaign != null) {
        return campaignDbOperations.addForeignKeyToPriorityTable(campaign);
      }
    }
    return false;
  }

  public boolean hasForeignKeyAdditionError(boolean wasMultipleSort,
                                            boolean multipleSort,
                                            boolean needForeignKeyReplacement,
                                            boolean foreignKeyAdded) {
    if (!wasMultipleSort && multipleSort && !foreignKeyAdded) {
      return true;
    }
    return multipleSort && needForeignKeyReplacement && !foreignKeyAdded;
  }

  private void fixNullRoutingGroupMapping(Campaign campaign) {
    if (campaign.getCallerIdPool() != null && campaign.getCallerIdPool().getId() == null) {
      campaign.setCallerIdPool(null);
    }
  }

  private boolean isDbPasswordChanged(String newPassword, String oldPassword) {
    return !Objects.equals(newPassword, oldPassword);
  }

  private void restoreRunningCampaignState(Campaign campaign) {
    CampaignContext campaignContext = dialerFacade.getCampaignContexts().get(campaign.getId());
    if (campaignContext != null)
      campaign.setState(campaignContext.getCampaign().getState());
  }

  /**
   * Set campaign default values
   * @param campaign
   */
  private void setCampaignDefaultValues(Campaign campaign) {
    if (campaign.getAllowCallback() == null) {
      campaign.setAllowCallback(false);
    }
    if (campaign.getAllowPreviewBeep() == null) {
      campaign.setAllowPreviewBeep(false);
    }
    if (campaign.getAllowCallbackCustom() == null) {
      campaign.setAllowCallbackCustom(false);
    }
    if (campaign.getCallbackToAppointer() == null) {
      campaign.setCallbackToAppointer(false);
    }
    if (campaign.getAllowOverwriteByWebphone() == null) {
      campaign.setAllowOverwriteByWebphone(false);
    }
    if(campaign.getAllowUrcWithoutMt() == null) {
      campaign.setAllowUrcWithoutMt(false);
    }
    if (campaign.getAppointerReset() == null) {
      campaign.setAppointerReset(false);
    }
    if (campaign.getAppointerResetTimeout() == null) {
      campaign.setAppointerResetTimeout(0L);
    }
    if (campaign.getConcurrentLines() == null) {
      campaign.setConcurrentLines(0);
    }
    if (campaign.getCallsPerSecond() == null) {
      campaign.setCallsPerSecond(0);
    }
    if (campaign.getCacheLimit() == null) {
      campaign.setCacheLimit(0);
    }
    if (campaign.getRingTimeout() == null) {
      campaign.setRingTimeout(0);
    }
    if (campaign.getDialingBoost() == null) {
      campaign.setDialingBoost(1F);
    }
    if (campaign.getAbandonTimeout() == null) {
      campaign.setAbandonTimeout(0);
    }
    if (campaign.getAmdEnabled() == null) {
      campaign.setAmdEnabled(false);
    }
    if (campaign.getRetryScheduleEnabled() == null) {
      campaign.setRetryScheduleEnabled(false);
    }
    if (campaign.getScheduleEnabled() == null) {
      campaign.setScheduleEnabled(false);
    }
    if (campaign.getScheduleAutomationEnabled() == null) {
      campaign.setScheduleAutomationEnabled(false);
    }
    if (campaign.getDncEnabled() == null) {
      campaign.setDncEnabled(false);
    }
    if (campaign.getLscEnabled() == null) {
      campaign.setLscEnabled(false);
    }
    if (!(Boolean.TRUE.equals(campaign.getFullContactListCheckEnabled()))) {
        campaign.setFullContactListCheckEnabled(false);
    }
    if (campaign.getSkillEnabled() == null) {
      campaign.setSkillEnabled(false);
    }
    if(campaign.getRetryCallerIdBehavior() == null) {
      campaign.setRetryCallerIdBehavior(Constants.RETRY_CALLER_ID_BEHAVIOR.ANY);
    }
    if (campaign.getAllowApiControl() == null) {
      campaign.setAllowApiControl(false);
    }
    // gamma_plus NOT NULL kolonuna map'lenir. Entity'deki alan başlatması tek başına yetmez:
    // Dozer varsayılan olarak null'ı da kopyalar (map-null=true), yani DTO'dan gelen null
    // Boolean.FALSE'un üzerine yazılır ve INSERT NULL ile patlardı. Diğer NOT NULL boolean'lar
    // da tam olarak bu nedenle burada null'lanıyor.
    if (campaign.getGammaPlus() == null) {
      campaign.setGammaPlus(false);
    }
  }

  /**
   * Set campaign children values
   * @param campaign
   * @param user
   */
  private void setCampaignChildrenValues(Campaign campaign, User user) {
    setCreateUpdateUserFieldsToEntities(campaign, user);
    setCampaignToEntities(campaign);
    setIvrApplicationAndNumberToNull(campaign);
    validateSchedules(campaign.getSchedules());
    validateScheduleBlackoutAutomation(campaign.getBlackoutSchedules());
    if (campaign.getResultCodeSchedules() != null) {
      for (ResultCodeSchedule rcs : campaign.getResultCodeSchedules()) {
        setCreateUpdateFields(rcs.getResultCodeScheduleAdvanceds(), user);
        setResultCodeSchedule(rcs, rcs.getResultCodeScheduleAdvanceds());
      }
    }
    if (campaign.getSortFields() != null) {
      for (SortField field : campaign.getSortFields()) {
        if (CollectionUtils.isNotEmpty(field.getValues())) {
          setCreateUpdateFields(field.getValues(), user);
          field.getValues().forEach(v -> v.setSortField(field));
        }
      }
    }
  }

  /**
   * Set create date, update date, create user or update user fields to children
   * of campaign
   * @param campaign
   * @param user
   */
  private void setCreateUpdateUserFieldsToEntities(Campaign campaign, User user) {
    setCreateUpdateFields(campaign.getPhoneNumberColumns(), user);
    setCreateUpdateFields(campaign.getSortFields(), user);
    setCreateUpdateFields(campaign.getCampaignGroups(), user);
    setCreateUpdateFields(campaign.getResultCodeSchedules(), user);
    setCreateUpdateFields(campaign.getFilters(), user);
    setCreateUpdateFields(campaign.getSchedules(), user);
    setCreateUpdateFields(campaign.getBlackoutSchedules(), user);
    setCreateUpdateFields(campaign.getAttachedDatas(), user);
    setCreateUpdateFields(campaign.getPredefinedValues(), user);
    setCreateUpdateFields(campaign.getScheduleAutomations(), user);
  }

  /**
   * Set campaign object to children of campaign
   * @param campaign
   */
  private void setCampaignToEntities(Campaign campaign) {
    setCampaign(campaign, campaign.getPhoneNumberColumns());
    setCampaign(campaign, campaign.getSortFields());
    setCampaign(campaign, campaign.getCampaignGroups());
    setCampaign(campaign, campaign.getResultCodeSchedules());
    setCampaign(campaign, campaign.getFilters());
    setCampaign(campaign, campaign.getSchedules());
    setCampaign(campaign, campaign.getBlackoutSchedules());
    setCampaign(campaign, campaign.getAttachedDatas());
    setCampaign(campaign, campaign.getPredefinedValues());
    setCampaign(campaign, campaign.getScheduleAutomations());
  }

  /**
   * Set IVR application of campaign to null if type of campaign is not Outbound
   * IVR. (Workaround for saving)
   * @param campaign
   */
  private void setIvrApplicationAndNumberToNull(Campaign campaign) {
    if (campaign.getIvrApplication() != null && campaign.getIvrApplication().getId() == null) {
      campaign.setIvrApplication(null);
    }
    if (campaign.getIvrApplicationNumber() != null && campaign.getIvrApplicationNumber().getId() == null) {
      campaign.setIvrApplicationNumber(null);
    }
  }

  /**
   * Validate from and till date for schedule
   * @param schedules
   * @return
   */
  private ValidationResult validateSchedules(List<Schedule> schedules) {
    ScheduleValidator scheduleValidator = new ScheduleValidator();
    return scheduleValidator.validate(schedules);
  }



  /**
   * Validate start and end date for blackoutschedule
   * @param scheduleBlackoutAutomations
   * @return
   */
  private ValidationResult validateScheduleBlackoutAutomation(List<ScheduleBlackoutAutomation> scheduleBlackoutAutomations) {
    ScheduleBlackoutAutomationValidator scheduleValidator = new ScheduleBlackoutAutomationValidator();
    return scheduleValidator.validate(scheduleBlackoutAutomations);
  }

  /**
   * Set ResultCodeSchedule object to all ResultCodeScheduleAdvanceds
   * @param rcs
   * @param rcshs
   */
  private void setResultCodeSchedule(ResultCodeSchedule rcs, List<ResultCodeScheduleAdvanced> rcshs) {
    if (rcshs == null)
      return;

    for (ResultCodeScheduleAdvanced rcsh : rcshs) {
      rcsh.setResultCodeSchedule(rcs);
    }
  }

  /**
   * Set Campaign object to all CampaignHolders
   * @param cn
   * @param chs
   */
  private void setCampaign(Campaign cn, List<? extends CampaignHolder> chs) {
    if (chs == null)
      return;

    for (CampaignHolder ch : chs) {
      ch.setCampaign(cn);
    }
  }

  /**
   * Saving or clone new campaign
   */
  @Override
  @Transactional(readOnly = false)
  public CampaignFormDTO save(final CampaignFormDTO item) throws DuplicatedNameException {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    Campaign campaign = mapper.map(item, Campaign.class);
    fixNullRoutingGroupMapping(campaign);
    if (getValidator().validate(campaign).getResult()) {

      setCampaignChildrenValues(campaign, user);
      setCampaignDefaultValues(campaign);

      if (item.getCloneId() != null) {
        campaign.setDbPassword(item.getDbPassword());
      } else {
        if (StringUtils.isNotBlank(item.getDbPassword())) {
          campaign.setDbPassword(enCiperUtils.encryptPassword(item.getDbPassword()));
        } else {
          campaign.setDbPassword(null);
        }
      }

      if (!authUtil.isUserInRole(Constants.ROLE_SUPERADMIN))
        campaign.setTenant(user.getTenant());
      else
        campaign.setTenant(tenantDao.get(campaign.getTenant().getId()));

      Date now = new Date();
      campaign.setCreateDate(now);
      campaign.setCreateUser(user);
      campaign.setStoppedDate(now);
      campaign.setEnabled(true);
      campaign = getDao().save(campaign); //for campaign id

      DNC dnc = createDefaultDNCForCampaign(campaign, user);
      campaign.setDncEnabled(true);
      campaign.setDnc(dnc);
      campaign.getDncs().add(dnc);
      campaign = getDao().save(campaign);

      sessionFactory.getCurrentSession().evict(campaign);

      authUtil.reauthenticate();
      String plainPassword = null;
      try {
        if (StringUtils.isNotBlank(campaign.getDbPassword())) {
          plainPassword = enCiperUtils.decryptPassword(campaign.getDbPassword());
        }
      } catch (Exception e) {
        LOG.error("Failed to decrypt password for campaign ID: " + campaign.getId(), e);
        throw new RuntimeException("Failed to decrypt database password", e);
      }

      campaignDbOperations.addDataSource(campaign.getId(), campaign.getDbConnectionString(), campaign.getDbUsername(), plainPassword);
      checkForPriorityTable(campaign);

      RedisMessage redisMessage = new RedisMessage(Constants.MSG_ACTION_TYPE.ADD, Constants.MSG_ENTITY_TYPE.CAMPAIGN, campaign);
      applicationEventPublisher.publishEvent(new CampaignCreatedEvent(this, campaign));
      redisUtil.publishMessage(Constants.REDIS_CHANNEL.channelGBPowerDialerEntity.toString(), redisMessage);
    } else {
      return item; // TODO handle validation fail
    }

    return mapper.map(campaign, CampaignFormDTO.class);
  }

  /**
   * Create default DNC for campaign
   * @param campaign
   * @param user
   * @return
   */
  private DNC createDefaultDNCForCampaign(Campaign campaign, User user) {
    DNC dnc = new DNC();
    dnc.setName("Default_DNC_" + campaign.getName() + "_" + campaign.getId());
    dnc.setExpireDate(campaign.getEndDate());
    dnc.setEnabled(true);
    dnc.setCampaign(campaign);
    dnc.setTenant(campaign.getTenant());
    dnc.setCreateDate(new Date());
    dnc.setCreateUser(userDao.getDncSystemUser());
    return dnc;
  }

  @Transactional(readOnly = false)
  @Override
  public void delete(Long id) {
    super.delete(id);
    campaignDbOperations.removeDataSource(id);
  }

  @Transactional(readOnly = false)
  @Override
  public void delete(List<Long> ids) {
    checkCampaignsAreStopped(ids);
    super.delete(ids);
    for (Long id : ids) {
      campaignDbOperations.removeDataSource(id);
    }
  }

  @Transactional(propagation = Propagation.SUPPORTS)
  public void checkCampaignsAreStopped(List<Long> ids) {
    for (Long id : ids) {
      if (getDialerFacade().getCampaignContexts().get(id) != null)
        throw new CampaignIllegalStateException("campaign.edit.illegal.state");
    }
  }

  @Transactional(propagation = Propagation.SUPPORTS)
  public DialerFacade getDialerFacade() {
    return dialerFacade;
  }

  @Transactional(readOnly = false)
  @Override
  public void setEnabled(List<Long> ids, boolean enabled) {
    if (!enabled)
      checkCampaignsAreStopped(ids);
    getDao().setEnabled(ids, enabled);
  }

  /**
   * Test the whole campaign filter set.
   * @return array of rows
   */
  @Transactional
  public String[][] filtersTest(CampaignFormDTO item) {
    try {
      Campaign entity = mapper.map(item, Campaign.class);
      return campaignDbOperations.filtersTest(entity);
    } catch (SQLException e) {
      LOG.error("", e);
      throw new QueryException(e.getMessage(), e);
    }
  }

  /**
   * Test the single campaign filter.
   * @throws QueryException
   */
  @Transactional
  public void filterTest(FilterTestDTO item) {
    try {
      Campaign campaign = mapper.map(item.getCampaign(), Campaign.class);
      Filter filter = mapper.map(item, Filter.class);
      campaignDbOperations.filterTest(campaign, filter);
    } catch (SQLException e) {
      LOG.warn("Tried to create wrong filter. ", e);
      throw new QueryException(e.getMessage(), e);
    }
  }

  /**
   * Check connection to client db
   * @return tables and views of this user
   */
  @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
  public boolean checkConnection(Long campaignId, String url, String user, String password) throws CampaignDbConnectionException {
    try {
      password = getDecryptedPassword(campaignId, password);
      return campaignDbOperations.checkConnection(campaignId, url, user, password);
    } catch (SQLException e) {
      LOG.error("", e);
      throw new CampaignDbConnectionException(e.getMessage(), e);
    }
  }

  /**
   * Create data source for this campaign if campaignId is not null
   * @return tables and views of this user
   */
  @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
  public boolean connect(Long campaignId, String url, String user, String password) throws CampaignDbConnectionException {
    try {
      password = getDecryptedPassword(campaignId, password);
      return campaignDbOperations.connect(campaignId, url, user, password);
    } catch (SQLException e) {
      LOG.error("", e);
      throw new CampaignDbConnectionException(e.getMessage(), e);
    }
  }

  /**
   * Get tables for campaign
   * @return tables and views of this user
   */
  @Transactional
  public String[] getTables(Long campaignId, String url, String user, String password) {
    try {
      password = getDecryptedPassword(campaignId, password);
      return campaignDbOperations.getTablesAndViews(campaignId, url, user, password);
    } catch (SQLException e) {
      LOG.error("", e);
      throw new CampaignDbConnectionException(e.getMessage(), e);
    }
  }

  /**
   * Delete data source for this campaign
   * @param campaignId
   * @return true if deleting is successful
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public boolean disconnect(Long campaignId) {
    if (dialerFacade.getCampaignContexts().containsKey(campaignId)) {
      throw new DisconnectRunningCampaignException();
    }
    return campaignId == null || campaignDbOperations.removeDataSource(campaignId);
  }

  /**
   * Get columns of table
   * @param table
   * @param campaignId
   * @param url
   * @param user
   * @param password
   * @return column names
   */
  @Transactional
  public List<FieldDTO> getTableColumnsWithTypes(String table, Long campaignId, String url, String user, String password) throws ParseException {
    try {
      List<FieldDTO> result = new LinkedList<>();
      password = getDecryptedPassword(campaignId, password);
      for (String[] columnWithType : campaignDbOperations.getTableColumnsWithTypes(table, campaignId, url, user, password)) {
        result.add(new FieldDTO(Constants.DB_FIELD_TYPES.parse(columnWithType[1]), columnWithType[0]));
      }
      return result;
    } catch (SQLException e) {
      LOG.error("", e);
      throw new CampaignDbConnectionException(e.getMessage(), e);
    }
  }

  /**
   * Get source table columns excluding columns which contains result
   * @param campaignId
   * @return
   * @throws ParseException
   */
  @Transactional
  public List<FieldDTO> getSourceTableColumnsWithTypes(Long campaignId) throws ParseException {
    CampaignContext campaignContext = dialerFacade.getCampaignContexts().get(campaignId);
    Campaign campaign;
    if (campaignContext != null) {
      campaign = campaignContext.getCampaign();
    } else {
      campaign = getDao().get(campaignId);
    }
    List<FieldDTO> tableColumnsWithTypes = getTableColumnsWithTypes(campaign.getDbListTable(),
                                                                    campaignId,
                                                                    campaign.getDbConnectionString(),
                                                                    campaign.getDbUsername(), campaign.getDbPassword());
    if (campaign.getDbListTable().equalsIgnoreCase(campaign.getDbResultTable())) {
      tableColumnsWithTypes = tableColumnsWithTypes.stream()
        .filter(fieldDTO -> !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultCalledNumber())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultCallbackDate())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultCallbackNumber())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultTelephonyRc())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultUserRc())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultRcSequence())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultUserId())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultRetryNumber())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultRetryDate())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultRetryHistory())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultCbHistory())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultCallProcess())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultFetchedDataFlag())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultStartDate())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultAnswerDate())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultCompleteDate())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultDNCNumbers())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultAttempts())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultUpdateDate())
          && !fieldDTO.getFieldName().equalsIgnoreCase(campaign.getColResultResponseValues()))
        .collect(Collectors.toList());
    }
    return tableColumnsWithTypes;
  }

  private String getDecryptedPassword(Long campaignId, String password) {
    if (campaignId != null && !isDbPasswordChanged(password, getDao().get(campaignId).getDbPassword()))
      return enCiperUtils.decryptPassword(password);
    else
      return password;  // Password from frontend is already encrypted, use as-is
  }

  /**
   * Create default source table for campaign
   * @param sourceTableOptions
   * @throws SQLException
   */
  @Transactional
  public void createDefaultSourceTableForCampaign(CreateSourceTableRequest sourceTableOptions) throws SQLException, ParseException {
    campaignDbOperations.createDefaultSourceTableForCampaign(sourceTableOptions);
  }

  /**
   * Adds custom fields to the campaign source table
   */
  @Transactional
  public void addCustomColumnsToSourceTable(CustomFieldsRequest request) throws SQLException {
    campaignDbOperations.addCustomColumnsToSourceTable(request);
  }

  /**
   * Create result table with default columns for campaign
   * @param resultTableTableOptions
   * @throws SQLException
   */
  @Transactional
  public void createDefaultResultTableForCampaign(DbTableRequest resultTableTableOptions) throws SQLException {
    campaignDbOperations.createDefaultResultTable(resultTableTableOptions);
  }

  /**
   * Add default result columns to source table
   * @param sourceTableOptions
   * @throws SQLException
   */
  @Transactional
  public void addDefaultResultColumnsToSourceTable(DbTableRequest sourceTableOptions) throws SQLException {
    campaignDbOperations.addDefaultResultColumnsToSourceTable(sourceTableOptions);
  }

  /**
   * Check where or not table with name {@link DbTableRequest#tableName} can be
   * created
   * @param request
   * @return true if table name can be used
   * @throws SQLException
   */
  @Transactional
  public Boolean checkTableName(DbTableRequest request) throws SQLException {
    if (!StringUtils.isEmpty(request.getTableName())) {
      Matcher matcher = CAMPAIGN_TABLE_PATTERN.matcher(request.getTableName());
      if (matcher.matches() && !RESERVED_SQL_NAMES.contains(request.getTableName().toLowerCase())) {
        String[] tablesAndViews = campaignDbOperations
            .getTablesAndViews(request.getCampaignId(), request.getConnectionString(), request.getDbUsername(), request.getDbPassword());
        return !Arrays.asList(tablesAndViews).contains(request.getTableName());
      }
    }
    return false;
  }

  /**
   * Get campaigns with initialized schedules
   * @return list of campaigns with schedules
   */
  public List<Campaign> getCampaignsWithSchedules() {
    List<Campaign> campaigns = getDao().getAll(true, false);
    for (Campaign campaign : campaigns) {
      Hibernate.initialize(campaign.getSchedules());
      Hibernate.initialize(campaign.getBlackoutSchedules());
      Hibernate.initialize(campaign.getScheduleAutomations());
      Hibernate.initialize(campaign.getTenant());
    }
    return campaigns;
  }

  @Transactional
  public void restartRunningCampaign(Long campaignId) {
    action(campaignId, CAMPAIGN_ACTION.RESTART, null);
  }

  public CampaignFormDTO clone(Long campaignId, String campaignName) {
    CampaignFormDTO campaign = mapper.map(getInitializedCampaign(campaignId), CampaignFormDTO.class);
    campaign.setName(campaignName);
    campaign.setCloneId(campaignId);
    campaign.setDbPriorityTable(null);
    return clearIdsAndSetDefaultValues(campaign);
  }

  /**
   * Clear ids of existing campaign and its fields to create new entities in db
   * @param campaign existing in db campaign
   * @return new disabled campaign without ids
   */
  private CampaignFormDTO clearIdsAndSetDefaultValues(CampaignFormDTO campaign) {
    campaign.setId(null);
    campaign.setEnabled(false);
    campaign.setVersion(0L);
    campaign.setStoppedDate(null);
    clearDates(campaign);
    for (PhoneNumberColumnFormDTO phoneNumberColumn : campaign.getPhoneNumberColumns()) {
      phoneNumberColumn.setId(null);
      phoneNumberColumn.setCampaign(null);
      clearDates(phoneNumberColumn);
    }
    for (SortFieldFormDTO sortField : campaign.getSortFields()) {
      sortField.setId(null);
      sortField.setCampaign(null);
      clearDates(sortField);
      for (SortFieldValueFormDTO sortFieldValue : sortField.getValues()) {
        sortFieldValue.setId(null);
        sortFieldValue.setSortField(null);
        clearDates(sortFieldValue);
      }
    }
    for (CampaignGroupFormDTO campaignGroup : campaign.getCampaignGroups()) {
      campaignGroup.setId(null);
      campaignGroup.setCampaign(null);
      clearDates(campaignGroup);
    }
    for (ResultCodeScheduleFormDTO resultCodeSchedule : campaign.getResultCodeSchedules()) {
      resultCodeSchedule.setId(null);
      resultCodeSchedule.setCampaign(null);
      clearDates(resultCodeSchedule);
      for (ResultCodeScheduleAdvancedFormDTO resultCodeScheduleAdvanced : resultCodeSchedule.getResultCodeScheduleAdvanceds()) {
        resultCodeScheduleAdvanced.setId(null);
        resultCodeScheduleAdvanced.setResultCodeSchedule(null);
        clearDates(resultCodeScheduleAdvanced);
      }
    }
    for (ScheduleFormDTO schedule : campaign.getSchedules()) {
      schedule.setId(null);
      schedule.setCampaign(null);
      clearDates(schedule);
    }
    for (ScheduleBlackoutAutomationFormDTO blackoutSchedule : campaign.getBlackoutSchedules()) {
      blackoutSchedule.setId(null);
      blackoutSchedule.setCampaign(null);
      clearDates(blackoutSchedule);
    }
    for (FilterFormDTO filter : campaign.getFilters()) {
      filter.setId(null);
      clearDates(filter);
    }
    for (AttachedDataFormDTO attachedData : campaign.getAttachedDatas()) {
      attachedData.setId(null);
      clearDates(attachedData);
    }

    for (ScheduleAutomationFormDTO scheduleAutomation : campaign.getScheduleAutomations()) {
      scheduleAutomation.setId(null);
      scheduleAutomation.setCampaign(null);
      clearDates(scheduleAutomation);
    }

    // Filter campaign-specific (default) DNCs
    campaign.setDncs(campaign.getDncs().stream().filter(dnc -> dnc.getCampaign() == null).collect(Collectors.toSet()));

    return campaign;
  }

  private void clearDates(BaseEntityDTO baseEntity) {
    baseEntity.setCreateDate(null);
    baseEntity.setUpdateDate(null);
  }

  /**
   * Manipulations to play with campaign(start, stop, pause, unpause, reload,
   * reset)
   *
   * <p>FIX-333607: aksiyonlar campaign bazlı kilitle sıralanır ({@link CampaignActionCoordinator});
   * farklı campaign'lerin aksiyonları birbirini beklemez. Kilit transaction açılmadan önce alınır
   * ve commit'ten sonra bırakılır; kilidi bekleyen istek DB bağlantısı tutmaz. Aynı campaign'de
   * devam eden aksiyon varsa en fazla 10 sn beklenir, sonra {@code campaign.action.inProgress}
   * hatası döner.</p>
   * @param campaignId
   * @param type
   */
  // FIX-333607: GenericService'in sınıf seviyesi readOnly transaction'ı ezilir; transaction kilitten
  // sonra executeActionInTransaction içinde açılır. Dışarıda transaction varsa ona katılınır (eskisi gibi).
  @Transactional(propagation = Propagation.SUPPORTS)
  public void action(Long campaignId, CAMPAIGN_ACTION type, Constants.CAMPAIGN_STOP_TYPE stopType) {
    actionCoordinator.runExclusive(campaignId, type.name(), () -> executeActionInTransaction(campaignId, type, stopType));
  }

  /**
   * FIX-333607: scheduler ve stop condition gibi sistem tetikleyicileri için. Çağıran thread'i
   * bekletmez; aynı campaign + aksiyon için kuyrukta bekleyen iş varsa yenisini eklemez. Hata ve
   * kilit beklemesi {@link CampaignActionCoordinator} tarafından loglanır.
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public void actionAsync(Long campaignId, CAMPAIGN_ACTION type, Constants.CAMPAIGN_STOP_TYPE stopType) {
    // Senkron çağrıdaki gibi aksiyon çağıranın kimliğiyle çalışır (action log kullanıcısı).
    actionCoordinator.submit(campaignId, type.name(),
        new DelegatingSecurityContextRunnable(() -> executeActionInTransaction(campaignId, type, stopType)));
  }

  @Autowired
  public void setTransactionManager(PlatformTransactionManager transactionManager) {
    this.actionTransactionTemplate = new TransactionTemplate(transactionManager);
  }

  @PreDestroy
  public void shutdownActionCoordinator() {
    actionCoordinator.shutdown();
  }

  /**
   * FIX-333607: predictive strateji manager'ları (ALPHA boost, BETA, GAMMA) yalnız start'ta ve
   * unpause'ta init edilir. Düz kaydet ve kaydet + reload, kaydedilen campaign'i
   * {@link #updateContext(Long)} ile çalışan context'e uygular; strateji değiştiyse yeni strateji
   * init edilmeden çalışır (01.10.2026: ALPHA → GAMMA geçişinde arama durdu). Bu iki yol kayıttan
   * önce bu kontrolü çağırır. Kaydet + restart çağırmaz: stop eski stratejiyi kapatır, start yenisini
   * init eder.
   *
   * <p>Karşılaştırma runtime'daki (init edilmiş) stratejiyle yapılır. Form strateji göndermiyorsa
   * (Tech/NonTech yetkisi; {@code CampaignMergingAspect} alanı DB'deki değerle korur) değişiklik
   * yoktur.</p>
   *
   * @throws CampaignIllegalStateException campaign'in runtime context'i varsa, durumu STOPPED değilse
   *         ve formdaki strateji runtime'dakinden farklıysa
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public void checkPredictiveStrategyChangeAllowed(Long campaignId, CampaignFormDTO item) {
    if (campaignId == null || item == null || item.getPredictiveStrategy() == null) {
      return;
    }
    Campaign runtimeCampaign = getActiveRuntimeCampaign(campaignId);
    if (runtimeCampaign == null) {
      return;
    }
    if (!Objects.equals(runtimeCampaign.getPredictiveStrategy(), item.getPredictiveStrategy())) {
      throw new CampaignIllegalStateException(PREDICTIVE_STRATEGY_CHANGE_ILLEGAL_STATE_KEY);
    }
  }

  /**
   * FIX-333607: campaign tipine bağlı runtime (predictive strateji manager'ları, preview/progressive
   * çağrı üretimi) yalnız start'ta kurulur. UI tip değişikliğinde restart seçmez; düz kaydet ve
   * kaydet + reload yeni tipi {@link #updateContext(Long)} ile çalışan context'e init'siz uygular. Bu
   * iki yol {@link #checkPredictiveStrategyChangeAllowed(Long, CampaignFormDTO)} gibi bu kontrolü de
   * kayıttan önce çağırır; kaydet + restart çağırmaz.
   *
   * <p>Formdaki tip {@code int} olduğu için gönderilmediği ayırt edilemez (0 = PROGRESSIVE_RESERVED).
   * Tech/NonTech yetkili kullanıcının formu tipi taşımaz ve {@code CampaignMergingAspect} tipi korur;
   * {@code typeEditable} false ise değişiklik yoktur.</p>
   *
   * @param typeEditable kullanıcı campaign'i tam yetkiyle düzenleyebiliyorsa (form tipi taşır)
   * @throws CampaignIllegalStateException campaign'in runtime context'i varsa, durumu STOPPED değilse
   *         ve formdaki tip runtime'dakinden farklıysa
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public void checkCampaignTypeChangeAllowed(Long campaignId, CampaignFormDTO item, boolean typeEditable) {
    if (!typeEditable || campaignId == null || item == null) {
      return;
    }
    Campaign runtimeCampaign = getActiveRuntimeCampaign(campaignId);
    if (runtimeCampaign == null || runtimeCampaign.getType() == null) {
      return;
    }
    if (runtimeCampaign.getType().ordinal() != item.getType()) {
      throw new CampaignIllegalStateException(CAMPAIGN_TYPE_CHANGE_ILLEGAL_STATE_KEY);
    }
  }

  /** Runtime context'i olan ve STOPPED olmayan campaign; yoksa null. */
  private Campaign getActiveRuntimeCampaign(Long campaignId) {
    CampaignContext context = dialerFacade.getCampaignContexts().get(campaignId);
    Campaign runtimeCampaign = context != null ? context.getCampaign() : null;
    if (runtimeCampaign == null || Constants.CAMPAIGN_STATE.STOPPED.equals(runtimeCampaign.getState())) {
      return null;
    }
    return runtimeCampaign;
  }

  /**
   * FIX-333607: campaign'in kuyrukta bekleyen veya çalışan aksiyonu var mı. Scheduler bu durumda o
   * turda karar vermez (eskiden aksiyonlar senkron olduğu için bir sonraki tur onlar bitince
   * başlıyordu).
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public boolean hasPendingAction(Long campaignId) {
    return actionCoordinator.hasPendingAction(campaignId);
  }

  /**
   * FIX-333607: RESTART'ın stop job'ı bitince campaign'i yeniden başlatır ({@code CampaignListener}).
   * Aynı campaign'in RESTART aksiyonu kilidi henüz bırakmamışsa UI'daki 10 sn yerine
   * {@link #RESTART_FOLLOW_UP_LOCK_WAIT_MS} kadar bekler; eskiden global kilitte süresiz beklerdi ve
   * kısa bekleme campaign'i restart sonrası STOPPING'de bırakırdı.
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public void startAfterRestart(Long campaignId) {
    actionCoordinator.runExclusive(campaignId, CAMPAIGN_ACTION.START.name(),
        () -> executeActionInTransaction(campaignId, CAMPAIGN_ACTION.START, null), RESTART_FOLLOW_UP_LOCK_WAIT_MS);
  }

  private void executeActionInTransaction(Long campaignId, CAMPAIGN_ACTION type, Constants.CAMPAIGN_STOP_TYPE stopType) {
    actionTransactionTemplate.execute(status -> {
      doAction(campaignId, type, stopType);
      return null;
    });
  }

  private void doAction(Long campaignId, CAMPAIGN_ACTION type, Constants.CAMPAIGN_STOP_TYPE stopType) {
    switch (type) {
      case START:
        dialerFacade.startCampaign(getInitializedCampaign(campaignId));
        updateStoppedDate(campaignId, null);
        updateStopType(campaignId, null);
        break;
      case STOP_REGULAR:
      case STOP_FORCEFUL:
      case RESTART:
        dialerFacade.shutdownCampaign(campaignId, type);
        updateStoppedDate(campaignId, new Date());
        updateStopType(campaignId, stopType);
        break;
      case PAUSE:
        dialerFacade.pauseCampaign(campaignId, Constants.CAMPAIGN_STOP_TYPE.MANUAL.equals(stopType));
        break;
      case UNPAUSE:
        dialerFacade.unpauseCampaign(campaignId);
        break;
      case RELOAD:
        dialerFacade.reloadCampaign(campaignId);
        break;
      case RESET:
        dialerFacade.resetCampaign(campaignId);
        break;
      default:
        break;
    }

    // Switch'ten sonra her zaman çalışır
    recalculateOnlineAgents(campaignId);
  }

  /**
   * Recalculates online agent sum for the given campaign if context is available.
   */
  private void recalculateOnlineAgents(Long campaignId) {
    CampaignContext campaignContext = dialerFacade.getCampaignContexts().get(campaignId);
    if (campaignContext != null) {
      campaignContext.getCallConsumer().calculateOnlineAgentsSum();
    }
  }

  @Transactional
  public void updateStopType(Long campaignId, Constants.CAMPAIGN_STOP_TYPE stopType) {
    ((ICampaignDao) getDao()).updateStopType(campaignId, stopType);
  }

  public void cancelRestart(Long campaignId) {
    CampaignContext campaignContext = dialerFacade.getCampaignContexts().get(campaignId);
    if (campaignContext != null) {
      Campaign campaign = campaignContext.getCampaign();
      if (campaign != null) {
        if (campaign.getState() == Constants.CAMPAIGN_STATE.STOPPING) {
          LOG.info("Cancelling restart and stopping " + campaign);
          campaign.setCampaignStopJob(null);
          campaignContext.setCampaignState(campaignContext.getStartState());
          action(campaignId, CAMPAIGN_ACTION.STOP_FORCEFUL, null);
        } else
          LOG.warn("Could not cancel restart for " + campaign + ": current state is " + campaign.getState());
      } else
        LOG.error("Could not cancel restart for campaign with id " + campaignId + ": there is no campaign inside of context");
    } else
      LOG.warn("Could not cancel restart for campaign with id " + campaignId + ": there is no such context");
  }

  /**
   * Get campaign with initialized fields
   * @param campaignId
   * @return
   */
  public Campaign getInitializedCampaign(Long campaignId) {
    Campaign campaign = getDao().get(campaignId);

    if (campaign != null) {

      Hibernate.initialize(campaign.getSkills());
      Hibernate.initialize(campaign.getLocations());
      for (Location location : campaign.getLocations()) {
        Hibernate.initialize(location.getServers());
      }

      Hibernate.initialize(campaign.getDncs());
      Hibernate.initialize(campaign.getDnc());
      Hibernate.initialize(campaign.getLscs());
      Hibernate.initialize(campaign.getPhoneNumberColumns());
      Hibernate.initialize(campaign.getSortFields());

      for (SortField sortField : campaign.getSortFields()) {
        Hibernate.initialize(sortField.getValues());
      }

      Hibernate.initialize(campaign.getCampaignGroups());
      
      for (CampaignGroup campaignGroup : campaign.getCampaignGroups()) {
        Hibernate.initialize(campaignGroup.getGroup());
        Hibernate.initialize(campaignGroup.getGroup().getQueues());
      }

      Hibernate.initialize(campaign.getResultCodeSchedules());
      for (ResultCodeSchedule resultCodeSchedule : campaign.getResultCodeSchedules()) {
        Hibernate.initialize(resultCodeSchedule.getResultCode());
        Hibernate.initialize(resultCodeSchedule.getTelephonyResultCode());
        Hibernate.initialize(resultCodeSchedule.getResultCodeScheduleAdvanceds());
      }

      Hibernate.initialize(campaign.getSchedules());
      Hibernate.initialize(campaign.getBlackoutSchedules());
      Hibernate.initialize(campaign.getScheduleAutomations());
      Hibernate.initialize(campaign.getFilters());
      Hibernate.initialize(campaign.getAttachedDatas());
      Hibernate.initialize(campaign.getTenant());
      Hibernate.initialize(campaign.getPredefinedValues());
      Hibernate.initialize(campaign.getCallerIdPool());
      
      if (campaign.getType() == CAMPAIGN_TYPE.OUTBOUND_IVR)
        Hibernate.initialize(campaign.getIvrApplicationNumber());

    }
    return campaign;
  }

  /**
   * Update campaign's stoppedDate
   */
  @Transactional(readOnly = false)
  public void updateStoppedDate(Long campaignId, Date date) {
    ((ICampaignDao) getDao()).updateStoppedDate(campaignId, date);
  }

  @Transactional(readOnly = true)
  public List<Campaign> getByName(String name, boolean deleted) {
    return ((ICampaignDao) getDao()).getByName(name, deleted);
  }

  /**
   * Get campaign with latest values
   * @param ids
   * @return list of campaigns with latest info
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public List<CampaignListDTO> realTimeInfo(Long[] ids) {
    List<Campaign> campaigns = dialerFacade.getCampaignsRealtimeInfo(ids);
    List<CampaignListDTO> realtimeInfo = convertEntitiesToDTO(campaigns);
    applyRealtimeAgentSizes(campaigns, realtimeInfo);
    return realtimeInfo;
  }

  private void applyRealtimeAgentSizes(List<Campaign> campaigns, List<CampaignListDTO> realtimeInfo) {
    if (CollectionUtils.isEmpty(campaigns) || CollectionUtils.isEmpty(realtimeInfo)) {
      return;
    }

    Map<Long, Campaign> campaignsById = campaigns.stream()
            .filter(Objects::nonNull)
            .filter(campaign -> campaign.getId() != null)
            .collect(Collectors.toMap(Campaign::getId, campaign -> campaign, (first, second) -> second));

    ACDEngine acdEngine = dialerFacade.getAcdEngine();
    // Task 325654: Eskiden her kampanya icin ayri ayri countCampaignAllActiveUsers
    // cagriliyordu; her cagri CallUpToDateKeeper.getAllRegisteredUsers() ile tum aktif
    // cagri koleksiyonunu yeniden tariyordu. Tek istek 65 kampanya tasidigi ve endpoint
    // saniyede ~5 kez cagrildigi icin bu saniyede ~330 tam tarama demekti. Batch cagri
    // snapshot'i bir kez alir; donen degerler per-campaign sonucla birebir aynidir.
    Map<Long, Integer> agentSizesByCampaignId =
            acdEngine.countCampaignAllActiveUsersByCampaignId(campaignsById.values());

    for (CampaignListDTO campaignInfo : realtimeInfo) {
      if (campaignInfo == null || campaignInfo.getId() == null) {
        continue;
      }
      Integer agentsSize = agentSizesByCampaignId.get(campaignInfo.getId());
      if (agentsSize != null) {
        campaignInfo.setAgentsSize(agentsSize);
      }
    }
  }

  /**
   * Kampanya list bilgisini running/idle ayrımı yapmadan döner.
   *
   * <p>Context'te olan (running/starting/pausing vb.) kampanyalar için {@link #realTimeInfo}
   * ile canlı runtime değerleri kullanılır. Context'te olmayan (idle) kampanyalar için DB
   * entity'sinden üretilen DTO, persist edilmiş {@link CampaignMemento} snapshot'ı ile
   * doldurulur; bu, kampanya list ekranındaki ({@code /list}) davranışla birebir aynıdır.</p>
   *
   * <p>{@code ids} boş/null ise tüm-DB taraması yapılmaz; yalnız running kampanyalar döner
   * (mevcut {@link #realTimeInfo} davranışı korunur).</p>
   *
   * @param ids istenen kampanya id'leri
   * @return running + idle kampanyaların list DTO'ları
   */
  @Transactional(readOnly = true)
  public List<CampaignListDTO> campaignInfo(Long[] ids) {
    List<CampaignListDTO> result = realTimeInfo(ids);
    if (ids == null || ids.length == 0) {
      return result;
    }
    Set<Long> runningIds = result.stream()
        .filter(Objects::nonNull)
        .map(CampaignListDTO::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Long[] idleIds = Arrays.stream(ids)
        .filter(Objects::nonNull)
        .distinct()
        .filter(id -> !runningIds.contains(id))
        .toArray(Long[]::new);
    if (idleIds.length == 0) {
      return result;
    }
    result.addAll(idleCampaignInfo(idleIds));
    return result;
  }

  private List<CampaignListDTO> idleCampaignInfo(Long[] idleIds) {
    List<CampaignListDTO> idleInfo = convertEntitiesToDTO(((ICampaignDao) getDao()).getAll(null, false, idleIds));
    restoreCampaignsFromMemento(idleInfo);
    return idleInfo;
  }

  /**
   * Restore campaign parameters from campaignMemento
   * @param listDetailed
   */
  public void restoreCampaignFromMemento(ListDetailed<CampaignListDTO> listDetailed) {
    restoreCampaignsFromMemento(listDetailed.getData());
  }

  private void restoreCampaignsFromMemento(List<CampaignListDTO> campaigns) {
    if (campaigns == null || campaigns.isEmpty()) {
      return;
    }
    List<Long> campaignIds = campaigns.stream().map(CampaignListDTO::getId).collect(Collectors.toList());
    List<CampaignMemento> campaignMementos = campaignMementoService.getCampaignMementosByCampaignIds(campaignIds);
    HashMap<Long, CampaignMemento> campaignMementoMap = new HashMap<>();
    for (CampaignMemento campaignMemento : campaignMementos) {
      campaignMementoMap.put(campaignMemento.getCampaign().getId(), campaignMemento);
    }
    for (CampaignListDTO campaign : campaigns) {
      CampaignMemento campaignMemento = campaignMementoMap.get(campaign.getId());
      if (campaignMemento != null) {
        campaignMemento.restore(campaign);
      }
    }
  }

  @Transactional(readOnly = true)
  public List<Long> getCampaignIdsCreatedByUser(User user, boolean deleted) {
    return ((ICampaignDao) getDao()).getCampaignIdsCreatedByUser(user, deleted);
  }

  @Transactional(readOnly = true)
  public List<Long> getCampaignIdsCreatedByUserTenants(User user, boolean deleted) {
    List<Long> tenantsIds = Stream.concat(
        Stream.of(user.getTenant()),
        user.getTenants().stream())
        .map(Tenant::getId)
        .collect(Collectors.toList());
    return getCampaignIdsCreatedByTenants(tenantsIds, deleted);
  }

  @Transactional(readOnly = true)
  public List<Long> getCampaignIdsCreatedByTenants(Collection<Long> tenantsIds, boolean deleted) {
    return ((ICampaignDao) getDao()).getCampaignIdsCreatedByTenants(tenantsIds, deleted);
  }

  @Transactional(readOnly = true)
  public List<CampaignFormDTO> getAll(Boolean enabled, Boolean deleted, Long[] ids) {
    List<CampaignFormDTO> result = new ArrayList<>();
    for (Campaign campaign : ((ICampaignDao) getDao()).getAll(enabled, deleted, ids)) {
      Hibernate.initialize(campaign.getSchedules());
      Hibernate.initialize(campaign.getBlackoutSchedules());
      result.add(mapper.map(campaign, CampaignFormDTO.class));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public List<Campaign> getAll(Boolean deleted) {
    return ((ICampaignDao) getDao()).getAll(deleted);
  }

  @Transactional(readOnly = true)
  public Campaign getCampaignByNameOrId(String campaignIdOrName) {
    Campaign campaign;
    if (Utils.isStringInteger(campaignIdOrName)) {
      campaign = getEntity(Long.parseLong(campaignIdOrName));
      if (campaign == null || campaign.isDeleted())
        throw new CampaignNotFoundException("campaign with id: " + campaignIdOrName + " can't be found");
    } else {
      List<Campaign> campaignList = getByName(campaignIdOrName, false);
      if (!org.springframework.util.CollectionUtils.isEmpty(campaignList))
        campaign = campaignList.get(0);
      else
        throw new CampaignNotFoundException("campaign with name: " + campaignIdOrName + " can't be found");
    }
    return campaign;
  }

  /**
   * Customer-scope DNC akışları için kampanyayı telefon kolonu mapping'leri
   * initialize edilmiş hâlde yükler; dönen entity transaction dışında
   * (detached) güvenle kullanılabilir.
   */
  public Campaign getCampaignWithPhoneNumberColumnsByNameOrId(String campaignIdOrName) {
    Campaign campaign = getCampaignByNameOrId(campaignIdOrName);
    Hibernate.initialize(campaign.getPhoneNumberColumns());
    return campaign;
  }

  @Transactional(readOnly = true)
  public void updateContext(Long campaignId) {
    Optional.ofNullable(getInitializedCampaign(campaignId))
        .ifPresent(dialerFacade::updateContext);
  }

  public Optional<CampaignFiltersStats> getCampaignFiltersStatsById(Long campaignId) {
    CampaignContext campaignContext = dialerFacade.getCampaignContexts().get(campaignId);
    if (campaignContext != null) {
      return Optional.ofNullable(campaignContext.getCallProducer().getLastCampaignFiltersStats());
    } else {
      return Optional.empty();
    }
  }

  @Transactional
  public void updateLscPriorities(Long campaignId, List<Long> lscIds) {
    if (campaignId == null) {
      throw new IllegalArgumentException("campaignId cannot be null");
    }
    if (lscIds == null) {
      throw new IllegalArgumentException("lscIds cannot be null");
    }


    ((ICampaignDao) getDao()).resetLscPriorities(campaignId);

    for (int i = 0; i < lscIds.size(); i++) {
      Long lscId = lscIds.get(i);
      ((ICampaignDao) getDao()).updateLscPriority(campaignId, lscId, i);
    }

  }
}
