package com.powerdialer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Arrays;
import java.util.Properties;

import javax.servlet.ServletContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerdialer.eureka.DiscoveryClientFactory;
import com.powerdialer.rds.ResourceDistributionServiceFacade;
import com.powerdialer.engine.dialer.CampaignFilterStatsCalculator;
import com.powerdialer.service.TokenCampaignValidatorService;
import org.hibernate.SessionFactory;

import org.dozer.DozerBeanMapper;
import org.dozer.Mapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.powerdialer.datamanipulation.DataManipulationStateHolder;
import com.powerdialer.dto.CampaignFormDTO;
import com.powerdialer.dto.CampaignListDTO;
import com.powerdialer.engine.priority.PriorityRecalculationStateHolder;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Schedule;
import com.powerdialer.oauth.services.OneAccessService;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.ActionLogService;
import com.powerdialer.service.CampaignService;
import com.powerdialer.service.UserService;
import com.powerdialer.util.ApplicationUtil;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;
import tests.support.CommonAnnotationDisablerBeanFactoryPostProcessor;
import tests.support.MockitoMockBeanPostProcessor;

import static tests.support.MockitoSpringMocks.mockBean;

/**
 * Tests for rest controller CampaignResource
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class CampaignsResourceTest {

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
    public CampaignService campaignService() {
      return mockBean(CampaignService.class);
    }

    @Bean
    public PermissionUtil permissionUtil() {
      return mockBean(PermissionUtil.class);
    }

    @Bean
    public ServletContext context() {
      return mockBean(ServletContext.class);
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
    public AuthUtil authUtil() {
      return mockBean(AuthUtil.class);
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
      return mockBean(RoleHierarchy.class);
    }

    @Bean
    public CampaignResource campaignResource() {
      return new CampaignResource();
    }

    @Bean
    public UserService userService() {
      return mockBean(UserService.class);
    }

    @Bean
    public OneAccessService oneAccessService() {
      return mockBean(OneAccessService.class);
    }

    @Bean
    public EnCipherUtils enChiperUtils() {
      return mockBean(EnCipherUtils.class);
    }

    @Bean
    public RedisUtil redisUtil() {
      return mockBean(RedisUtil.class, RETURNS_DEEP_STUBS);
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
    public DataManipulationStateHolder dataManipulationStateHolder() {
      return mockBean(DataManipulationStateHolder.class);
    }

    @Bean
    public SessionFactory sessionFactory() {
      return mockBean(SessionFactory.class);
    }

    @Bean
    public PriorityRecalculationStateHolder priorityStateHolder() {
      return mockBean(PriorityRecalculationStateHolder.class);
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
      propertiesConfigurer.setProperties(properties);
      return propertiesConfigurer;
    }

    @Bean
    public RedissonClient redissonSingle() {
      return mockBean(RedissonClient.class);
    }

    @Bean
    public ApplicationUtil applicationUtil() {
      return mockBean(ApplicationUtil.class);
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
    public CampaignFilterStatsCalculator campaignFilterStatsCalculator() {
      return mockBean(CampaignFilterStatsCalculator.class);
    }

    @Bean
    public TokenCampaignValidatorService tokenCampaignValidatorService() {
      return mockBean(TokenCampaignValidatorService.class);
    }
  }

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private PermissionUtil permissionUtil;

  @Autowired
  private CampaignService campaignService;

  @Autowired
  private Mapper mapper;

  @Autowired
  private ResourceDistributionServiceFacade resourceDistributionServiceFacade;

  private MockMvc mockMvc;

  @Before
  public void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        return invocation.getArguments()[0];
      }
    }).when(permissionUtil).restrictCampaignList(any());
  }


  @Test
  public void shouldGetCampaigns() throws Exception {
    mockMvc.perform(get("/api/campaigns")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
  }

  @Test
  public void shouldReturnCampaignWithSchedule() throws Exception {
    Campaign campaign = new Campaign();
    campaign.setId(1L);
    Schedule schedule = new Schedule();
    schedule.setId(1L);
    schedule.setCampaign(campaign);
    schedule.setEnabled(true);
    campaign.setSchedules(Arrays.asList(schedule));
    doReturn(Arrays.asList(mapper.map(campaign, CampaignFormDTO.class))).when(campaignService).getAll(any(), any(Boolean.class), any());

    ResultActions resultActions = mockMvc.perform(get("/api/campaigns"));
    resultActions.andExpect(status().isOk());
    resultActions.andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
    resultActions.andExpect(jsonPath("$").isArray());
    resultActions.andExpect(jsonPath("$[0].id").value(1));
    resultActions.andExpect(jsonPath("$[0].schedules").isArray());
    resultActions.andExpect(jsonPath("$[0].schedules[0].id").value(1));
  }

  @Test
  public void shouldGetRealtimeInfo() throws Exception {
    CampaignListDTO campaign = new CampaignListDTO();
    campaign.setId(1L);
    campaign.setCallsInProgressCount(3);
    campaign.setFreshDataListSize(5);
    doReturn(Arrays.asList(campaign)).when(campaignService).realTimeInfo(any());

    ResultActions resultActions = mockMvc.perform(get("/api/campaigns/realtimeInfo"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
    Mockito.verify(campaignService).realTimeInfo(any());
    resultActions.andExpect(jsonPath("$[0].callsInProgressCount").value(3));
    resultActions.andExpect(jsonPath("$[0].freshDataListSize").value(5));
  }

  @Test
  public void shouldGetCampaignInfoIncludingIdle() throws Exception {
    CampaignListDTO running = new CampaignListDTO();
    running.setId(1L);
    running.setState(3);
    running.setCallsInProgressCount(3);
    CampaignListDTO idle = new CampaignListDTO();
    idle.setId(2L);
    idle.setState(0);
    idle.setTotalListSize(50);
    doReturn(Arrays.asList(running, idle)).when(campaignService).campaignInfo(any());

    ResultActions resultActions = mockMvc.perform(get("/api/campaigns/info?ids=1,2"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
    Mockito.verify(campaignService).campaignInfo(any());
    resultActions.andExpect(jsonPath("$").isArray());
    resultActions.andExpect(jsonPath("$[0].id").value(1));
    resultActions.andExpect(jsonPath("$[0].state").value(3));
    resultActions.andExpect(jsonPath("$[1].id").value(2));
    resultActions.andExpect(jsonPath("$[1].state").value(0));
    resultActions.andExpect(jsonPath("$[1].totalListSize").value(50));
  }

}
