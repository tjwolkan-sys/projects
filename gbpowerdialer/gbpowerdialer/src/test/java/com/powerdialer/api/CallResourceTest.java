package com.powerdialer.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Arrays;
import java.util.Properties;

import javax.servlet.ServletContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.SessionFactory;

import org.dozer.DozerBeanMapper;
import org.dozer.Mapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.powerdialer.dao.impl.CampaignMementoDao;
import com.powerdialer.dao.impl.DNCPhoneNumberDao;
import com.powerdialer.datamanipulation.DataManipulationStateHolder;
import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.LSCEngine;
import com.powerdialer.engine.StopConditionChecker;
import com.powerdialer.engine.UserHolder;
import com.powerdialer.engine.UserLoginLogoutHandler;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.CallerIdManager;
import com.powerdialer.engine.asterisk.actions.ManagerActionFactory;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CampaignFilterStatsCalculator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.priority.PriorityRecalculationStateHolder;
import com.powerdialer.eureka.DiscoveryClientFactory;
import com.powerdialer.model.Call;
import com.powerdialer.oauth.services.OneAccessService;
import com.powerdialer.rds.ResourceDistributionServiceFacade;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.service.ActionLogService;
import com.powerdialer.service.AttachedDataItemService;
import com.powerdialer.service.CDRService;
import com.powerdialer.service.CampaignDataTypeChecker;
import com.powerdialer.service.CampaignMementoService;
import com.powerdialer.service.CampaignService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.DNCService;
import com.powerdialer.service.ResultCodeScheduleService;
import com.powerdialer.service.RoutingGroupService;
import com.powerdialer.service.ServerService;
import com.powerdialer.service.TelephonyResultCodeService;
import com.powerdialer.service.UserService;
import com.powerdialer.util.ApplicationUtil;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;
import tests.support.CommonAnnotationDisablerBeanFactoryPostProcessor;
import tests.support.MockitoMockBeanPostProcessor;

import static tests.support.MockitoSpringMocks.mockBean;

/**
 * Tests for rest controller CallResource
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class CallResourceTest {

  @Configuration
  @EnableWebMvc
  static class ContextConfiguration {

    @Bean
    public static InstantiationAwareBeanPostProcessor mockBeanPostProcessor() {
      return new MockitoMockBeanPostProcessor();
    }

    @Bean
    public static BeanFactoryPostProcessor commonAnnotationDisabler() {
      return new CommonAnnotationDisablerBeanFactoryPostProcessor();
    }

    @Bean
    public PropertyPlaceholderConfigurer propertyConfigurer() {
      PropertyPlaceholderConfigurer propertiesConfigurer = new PropertyPlaceholderConfigurer();
      Properties properties = new Properties();
      properties.setProperty("redis.enabled", "true");
      properties.setProperty("redis.configuration.type", "single");
      properties.setProperty("pbx.context.outboundIvr", "from-dialer");
      properties.setProperty("pbx.context.progressive", "from-dialer");
      properties.setProperty("pbx.context.preview", "from-dialer-preview");
      properties.setProperty("campaign.iv.key", "ssdkF$HUy2A#D%kd");
      properties.setProperty("campaign.secret.key", "weJiSEvR5yAC5ftB");
      properties.setProperty("manager.connection.login.attempts", "3");
      properties.setProperty("manager.connection.ping.attempts", "3");
      properties.setProperty("manager.connection.ping.timeout", "10");
      properties.setProperty("manager.connection.check.interval", "60");
      properties.setProperty("eureka.enabled", "false");
      properties.setProperty("requireTokenForRestAPI", "false");
      properties.setProperty("previewTimeoutOriginationDelaySec", "20");
      propertiesConfigurer.setProperties(properties);
      return propertiesConfigurer;
    }

    @Bean
    public ManagerConnectionComposition connectionComposition() {
      return mockBean(ManagerConnectionComposition.class, RETURNS_DEEP_STUBS);
    }

    @Bean
    public RedissonClient redissonSingle() {
      return mockBean(RedissonClient.class);
    }

    @Bean
    public Mapper mapper() {
      return new DozerBeanMapper();
    }

    @Bean
    public ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    public ServletContext context() {
      return mockBean(ServletContext.class);
    }

    @Bean
    public CampaignDbOperations dbOperations() {
      return mockBean(CampaignDbOperations.class);
    }

    @Bean
    public ServerService serverService() {
      return mockBean(ServerService.class);
    }

    @Bean
    public UserService userService() {
      return mockBean(UserService.class);
    }

    @Bean
    public TelephonyResultCodeService telephonyResultCodeService() {
      return mockBean(TelephonyResultCodeService.class);
    }

    @Bean
    public LoggerDecorator loggerDecorator() {
      return mockBean(LoggerDecorator.class);
    }

    @Bean
    public ActionLogService actionLogService() {
      return mockBean(ActionLogService.class);
    }

    @Bean
    public RedisUtil redisUtil() {
      return mockBean(RedisUtil.class);
    }

    @Bean
    public ApplicationUtil applicationUtil() {
      return mockBean(ApplicationUtil.class);
    }

    @Bean
    public ACDEngine acdEngine() {
      return mockBean(ACDEngine.class);
    }

    @Bean
    public SessionFactory sessionFactory() {
      return mockBean(SessionFactory.class);
    }

    @Bean
    public CallUpToDateKeeper callUpToDateKeeper() {
      return mockBean(CallUpToDateKeeper.class);
    }

    @Bean
    public CallResource callResource() {
      return new CallResource();
    }

    @Bean
    public DNCEngine dncEngine() {
      return mockBean(DNCEngine.class);
    }

    @Bean
    public DNCService dncService() {
      return mockBean(DNCService.class);
    }

    @Bean
    public DNCPhoneNumberService dncPhoneNumberService() {
      return mockBean(DNCPhoneNumberService.class);
    }

    @Bean
    public DNCPhoneNumberDao dncPhoneNumberDao() {
      return mockBean(DNCPhoneNumberDao.class);
    }

    @Bean
    public DialerFacade dialerFacade() {
      return mockBean(DialerFacade.class);
    }

    @Bean
    public CampaignMementoService campaignMementoService() {
      return mockBean(CampaignMementoService.class);
    }

    @Bean
    public CampaignMementoDao campaignMementoDao() {
      return mockBean(CampaignMementoDao.class);
    }

    @Bean
    public LSCEngine lscEngine() {
      return mockBean(LSCEngine.class);
    }

    @Bean
    public CampaignDataTypeChecker campaignDataTypeChecker() {
      return mockBean(CampaignDataTypeChecker.class);
    }

    @Bean
    public UserLoginLogoutHandler userLoginLogoutHandler() {
      return mockBean(UserLoginLogoutHandler.class);
    }

    @Bean
    public ManagerActionFactory managerActionFactory() {
      return mockBean(ManagerActionFactory.class);
    }

    @Bean
    public EnCipherUtils enCiperUtils() {
      return mockBean(EnCipherUtils.class);
    }

    @Bean
    public CallCleaner callCleaner() {
      return mockBean(CallCleaner.class);
    }

    @Bean
    public StopConditionChecker stopConditionChecker() {
      return mockBean(StopConditionChecker.class);
    }

    @Bean
    public CampaignService campaignService() {
      return mockBean(CampaignService.class);
    }

    @Bean
    public DataManipulationStateHolder dataManipulationStateHolder() {
      return mockBean(DataManipulationStateHolder.class);
    }

    @Bean
    public AuthUtil authUtil() {
      return mockBean(AuthUtil.class);
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
      return mockBean(RoleHierarchy.class);
    }

    @Bean
    public OneAccessService oneAccessService() {
      return mockBean(OneAccessService.class);
    }

    @Bean
    public PriorityRecalculationStateHolder priorityStateHolder() {
      return mockBean(PriorityRecalculationStateHolder.class);
    }

    @Bean
    public CallerIdManager callerIdManager() {
      return mockBean(CallerIdManager.class);
    }

    @Bean
    public ResourceDistributionServiceFacade resourceDistributionServiceFacade() {
      return mockBean(ResourceDistributionServiceFacade.class);
    }

    @Bean
    public DiscoveryClientFactory discoveryClientFactory() {
      return mockBean(DiscoveryClientFactory.class);
    }

    @Bean
    public RoutingGroupService routingGroupService() {
      return mockBean(RoutingGroupService.class);
    }

    @Bean
    public CDRService cdrService() {
      return mockBean(CDRService.class);
    }

    @Bean
    public AttachedDataItemService attachedDataItemService() {
      return mockBean(AttachedDataItemService.class);
    }

    @Bean
    public ResultCodeScheduleService resultCodeScheduleService() {
      return mockBean(ResultCodeScheduleService.class);
    }

    @Bean
    public UserHolder userHolder() {
      return mockBean(UserHolder.class);
    }

    @Bean
    public CallEventPublisher callEventPublisher() {return mockBean(CallEventPublisher.class);}

    @Bean
    public CampaignFilterStatsCalculator campaignFiltersStatsService() {
      return mockBean(CampaignFilterStatsCalculator.class);
    }
  }

  @Autowired
  private ManagerConnectionComposition connectionComposition;

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  private ManagerConnectionDecorator connectionDecorator;

  @Before
  public void init() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    connectionDecorator = mock(ManagerConnectionDecorator.class, RETURNS_DEEP_STUBS);

    when(connectionDecorator.getCalls().asMap().values()).thenReturn(Arrays.asList(mock(Call.class)));
    when(connectionComposition.getOpenedConnections().values()).thenReturn(Arrays.asList(connectionDecorator));
  }



  @Test
  public void shouldGetCalls() throws Exception {
    mockMvc.perform(get("/api/calls")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
  }

  @Test
  public void shouldGetCallsByServerIfServerExists() throws Exception {
    Long serverId = 1L;
    when(connectionDecorator.getServer().getId()).thenReturn(serverId);
    mockMvc.perform(get("/api/calls/server/" + serverId)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
  }

  @Test
  public void shouldThrowServerIdCastException() throws Exception {
    mockMvc.perform(get("/api/calls/server/serverId")).andExpect(status().isBadRequest());
  }

  @Test
  public void shouldReturnEmtpyListIfServerDoesntExist() throws Exception {
    when(connectionDecorator.getServer().getId()).thenReturn(1L);
    mockMvc.perform(get("/api/calls/server/2")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
  }

  @Test
  public void shouldGetCallByIdIfExists() throws Exception {
    when(connectionDecorator.getCalls().getIfPresent(anyString())).thenReturn(mock(Call.class));
    mockMvc.perform(get("/api/calls/dl-1234-5678")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
  }

  @Test
  public void shouldReturnNothingIfCallDoesntExist() throws Exception {
    when(connectionDecorator.getCalls().getIfPresent(anyString())).thenReturn(null);
    mockMvc.perform(get("/api/calls/dl-1234-5678")).andExpect(status().isOk());
  }

}
