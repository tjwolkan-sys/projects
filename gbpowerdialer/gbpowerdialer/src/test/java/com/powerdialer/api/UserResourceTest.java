package com.powerdialer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.UserHolder;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.boost.DynamicBoostCalculator;
import com.powerdialer.engine.boost.DynamicBoostManager;
import com.powerdialer.engine.boost.DynamicBoostTestConfigurator;
import com.powerdialer.model.Group;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.service.ActionLogService;
import com.powerdialer.util.ApplicationUtil;
import com.powerdialer.util.LoggerDecorator;
import org.dozer.Mapper;
import org.hibernate.SessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.concurrent.ConcurrentLinkedQueue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import tests.support.CommonAnnotationDisablerBeanFactoryPostProcessor;
import tests.support.MockitoMockBeanPostProcessor;

import static tests.support.MockitoSpringMocks.mockBean;

/**
 * Tests for rest controller UserResource
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class UserResourceTest {

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
    public CallUpToDateKeeper callUpToDateKeeper() {
      return mockBean(CallUpToDateKeeper.class);
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
    public Mapper mapper() {
      return mockBean(Mapper.class);
    }

    @Bean
    public ObjectMapper objectMapper() {
      return new ObjectMapper();
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
    public UserResource userResource() {
      return new UserResource();
    }

    @Bean
    public RedisUtil redisUtil() {
      return mockBean(RedisUtil.class);
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
    public UserHolder userHolder() {
      return mockBean(UserHolder.class);
    }

    @Bean
    public DynamicBoostManager dynamicBoostManager() {
      return mockBean(DynamicBoostManager.class);
    }

    @Bean
    public DynamicBoostCalculator dynamicBoostCalculator() {
      return mockBean(DynamicBoostCalculator.class);
    }

    @Bean
    public DynamicBoostTestConfigurator dynamicBoostTestConfigurator() {
      return mockBean(DynamicBoostTestConfigurator.class);
    }

  }

  @Autowired
  private ACDEngine acdEngine;

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @Before
  public void init() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void shouldGetUsersByGroup() throws Exception {
    Group group = new Group(1L);
    when(acdEngine.getIdleUsersInGroup(group)).thenReturn(new ConcurrentLinkedQueue() {
      {
        add(mock(User.class));
      }
    });
    mockMvc.perform(get("/api/users/group/" + group.getId())).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
  }

  @Test
  public void shouldThrowGroupIdCastException() throws Exception {
    mockMvc.perform(get("/api/users/group/groupId")).andExpect(status().isBadRequest());
  }

  @Test
  public void shouldGetIdleUsers() throws Exception {
    when(acdEngine.getAllIdleUsers()).thenReturn(new ConcurrentLinkedQueue() {
      {
        add(mock(User.class));
      }
    });
    mockMvc.perform(get("/api/users")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
  }

}
