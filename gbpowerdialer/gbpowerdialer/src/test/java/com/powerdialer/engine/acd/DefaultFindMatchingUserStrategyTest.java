package com.powerdialer.engine.acd;

import com.google.common.collect.Sets;
import com.powerdialer.engine.UserHolder;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignGroup;
import com.powerdialer.model.Group;
import com.powerdialer.model.Queue;
import com.powerdialer.model.Skill;
import com.powerdialer.model.Status;
import com.powerdialer.model.User;
import com.powerdialer.util.LoggerDecorator;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Tests for DefaultFindMatchingUserStrategy class
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultFindMatchingUserStrategyTest {

  @InjectMocks
  @Spy
  private ACDEngine acdEngine;

  @Spy
  private List<CampaignGroup> campaignGroups = new ArrayList<>();

  @Spy
  private Group group1;

  @Spy
  private Group group2;

  @Spy
  private Group group3;

  @Spy
  private User user1;

  @Spy
  private User user2;

  @Spy
  private User user3;

  @Mock
  private UserHolder userHolder;

  @Mock(name = "logger", answer = Answers.RETURNS_DEEP_STUBS)
  private LoggerDecorator loggerDecorator;

  /**
   * Initialise mock objects
   */
  @Before
  public void init() {
    Queue queue1 = spy(Queue.class);
    queue1.setId(1L);

    Queue queue2 = spy(Queue.class);
    queue2.setId(2L);

    Queue queue3 = spy(Queue.class);
    queue3.setId(3L);

    group1.setId(1L);
    group1.setQueues(Sets.newHashSet(queue1));

    group2.setId(2L);
    group2.setQueues(Sets.newHashSet(queue1, queue2));

    group3.setId(3L);
    group3.setQueues(Sets.newHashSet(queue1, queue2, queue3));

    CampaignGroup campaignGroup1 = spy(CampaignGroup.class);
    campaignGroup1.setId(1L);
    campaignGroup1.setPriority(0);
    campaignGroup1.setGroup(group1);
    campaignGroup1.setEnabled(true);

    CampaignGroup campaignGroup2 = spy(CampaignGroup.class);
    campaignGroup2.setId(2L);
    campaignGroup2.setPriority(1);
    campaignGroup2.setGroup(group2);
    campaignGroup2.setEnabled(true);

    CampaignGroup campaignGroup3 = spy(CampaignGroup.class);
    campaignGroup3.setId(3L);
    campaignGroup3.setPriority(1);
    campaignGroup3.setGroup(group3);
    campaignGroup3.setEnabled(true);

    campaignGroups.add(campaignGroup1);
    campaignGroups.add(campaignGroup2);
    campaignGroups.add(campaignGroup3);

    // En-idle siralamasi UserAvailTimeComparator (statusSetDate) ile yapilir: daha ESKI statusSetDate =
    // daha uzun avail bekleyen = daha idle. user3 en eski -> en idle (lastCallEndDate ile ayni niyet).
    // (Eskiden ucu de NOW idi; comparator lastCallEndDate'ten statusSetDate'e gecince tie olusup kiriliyordu.)
    user1.setId(1L);
    user1.setGroup(group1);
    user1.setStatusSetDate(new Date(3));
    acdEngine.updateUserLastCallEndDate(user1, new Date(3));

    user2.setId(2L);
    user2.setGroup(group1);
    user2.setStatusSetDate(new Date(2));
    acdEngine.updateUserLastCallEndDate(user2, new Date(2));

    user3.setId(3L);
    user3.setGroup(group1);
    user3.setStatusSetDate(new Date(1));
    acdEngine.updateUserLastCallEndDate(user3, new Date(1));

    // IDLE-GATE (ACDEngine.addToIdleUsers) artik status'u null olan / avail olmayan user'i reddeder;
    // gercek idle agent daima AVAILABLE status'tadir. Test user'larina bu invaryanti yansitan avail
    // status verilir (aksi halde asagidaki addToIdleUsers cagrilari [IDLE-GATE] ile atlanip idle bos kalir).
    Status availableStatus = new Status();
    availableStatus.setAvailableForDialerCalls(true);
    user1.setStatus(availableStatus);
    user2.setStatus(availableStatus);
    user3.setStatus(availableStatus);

    when(userHolder.getUser(user1.getId())).thenReturn(user1);
    when(userHolder.getUser(user2.getId())).thenReturn(user2);
    when(userHolder.getUser(user3.getId())).thenReturn(user3);

    acdEngine.addToIdleUsers(user1);
    acdEngine.addToIdleUsers(user2);
    acdEngine.addToIdleUsers(user3);
  }

  /**
   * Test finding user for call within campaign groups and defined skill set
   * @throws SecurityException
   * @throws NoSuchMethodException
   * @throws InvocationTargetException
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   */
  @Test
  public void testFindMatchingUser() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    Skill skill1 = new Skill();
    skill1.setName("en");

    Skill skill2 = new Skill();
    skill2.setName("ru");

    user1.setSkillWeightPair(new HashMap<Skill, Long>() {
      {
        put(skill1, 20L);
        put(skill2, 10L);
      }
    });

    user2.setSkillWeightPair(new HashMap<Skill, Long>() {
      {
        put(skill1, 10L);
        put(skill2, 10L);
      }
    });

    user3.setSkillWeightPair(new HashMap<Skill, Long>() {
      {
        put(skill1, 10L);
      }
    });

    Campaign campaign = spy(Campaign.class);
    campaign.setCampaignGroups(campaignGroups);
    campaign.setSkills(Sets.newHashSet(skill1, skill2));
    campaign.setSkillEnabled(true);
    campaign.setAvailableThreshold(0);

    Call call = spy(Call.class);
    call.setCampaign(campaign);

    FindMatchingUserStrategy defaultFindMatchingUserStrategy = new DefaultFindMatchingUserStrategy(acdEngine, call, true);
    User result = defaultFindMatchingUserStrategy.findMatchingUser();

    assertEquals(user1, result);
  }

  /**
   * Test finding user for call within campaign groups and a concrete skill
   * @throws SecurityException
   * @throws NoSuchMethodException
   * @throws InvocationTargetException
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   */
  @Test
  public void testGetUserWithConcreteSkill() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    Skill skill1 = new Skill();
    skill1.setName("en");

    user1.setSkillWeightPair(new HashMap<Skill, Long>() {
      {
        put(skill1, 10L);
      }
    });

    user2.setSkillWeightPair(new HashMap<Skill, Long>() {
      {
        put(skill1, 20L);
      }
    });

    Campaign campaign = spy(Campaign.class);
    campaign.setCampaignGroups(campaignGroups);
    campaign.setColSkill("skill");
    campaign.setSkillEnabled(true);
    campaign.setAvailableThreshold(0);

    Call call = spy(Call.class);
    call.setCampaign(campaign);
    call.setRawData(new JSONObject("{'skill' : 'en'}"));

    FindMatchingUserStrategy defaultFindMatchingUserStrategy = new DefaultFindMatchingUserStrategy(acdEngine, call, true);
    User result = defaultFindMatchingUserStrategy.findMatchingUser();

    assertEquals(user2, result);
  }

  /**
   * Check if campaignGroups is sorted properly by priority and queue size
   * @throws NoSuchMethodException
   * @throws SecurityException
   * @throws IllegalAccessException
   * @throws IllegalArgumentException
   * @throws InvocationTargetException
   */
  @Test
  public void testSortCampaignGroupsByPriorityAndQueueSize() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    DefaultFindMatchingUserStrategy defaultFindMatchingUserStrategy = new DefaultFindMatchingUserStrategy(acdEngine, spy(Call.class), true);
    Collections.sort(campaignGroups, (Comparator) Whitebox.getInternalState(defaultFindMatchingUserStrategy, "campaignGroupComparator"));

    assertEquals(2L, campaignGroups.get(0).getId().longValue());
    assertEquals(3L, campaignGroups.get(1).getId().longValue());
    assertEquals(1L, campaignGroups.get(2).getId().longValue());
  }

  /**
   * Test searching user with skills that have higher weight
   * @throws SecurityException
   * @throws NoSuchMethodException
   * @throws InvocationTargetException
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   */
  @Test
  public void testGetMostIdleUserWithMatchingSkills() throws SecurityException, IllegalArgumentException {
    Skill skill1 = new Skill();
    skill1.setName("en");

    Skill skill2 = new Skill();
    skill2.setName("ru");

    user1.setSkillWeightPair(new HashMap<Skill, Long>() {
      {
        put(skill1, 10L);
        put(skill2, 20L);
      }
    });

    user2.setSkillWeightPair(new HashMap<Skill, Long>() {
      {
        put(skill1, 20L);
        put(skill2, 30L);
      }
    });

    user3.setSkillWeightPair(new HashMap<Skill, Long>() {
      {
        put(skill1, 10L);
        put(skill2, 10L);
      }
    });

    Campaign campaign = new Campaign();
    campaign.setAvailableThreshold(0);

    Call call = spy(Call.class);
    call.setCampaign(campaign);

    DefaultFindMatchingUserStrategy defaultFindMatchingUserStrategy = new DefaultFindMatchingUserStrategy(acdEngine, call, true);
    User returnedUser = defaultFindMatchingUserStrategy.getMostIdleUserWithMatchingSkills(campaignGroups, Sets.newHashSet(skill1, skill2));

    assertEquals(user2, returnedUser);
  }

  /**
   * Test searching user with skills that have higher weight
   * @throws SecurityException
   * @throws NoSuchMethodException
   * @throws InvocationTargetException
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   */
  @Test
  public void testGetMostIdleUserWithMatchingSkillsSameWeights() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    Skill skill1 = new Skill();
    skill1.setName("en");

    Skill skill2 = new Skill();
    skill2.setName("ru");

    user1.setSkillWeightPair(new HashMap<Skill, Long>() {
      {
        put(skill1, 10L);
        put(skill2, 10L);
      }
    });

    user2.setSkillWeightPair(new HashMap<Skill, Long>() {
      {
        put(skill1, 10L);
        put(skill2, 10L);
      }
    });

    user3.setSkillWeightPair(new HashMap<Skill, Long>() {
      {
        put(skill1, 10L);
        put(skill2, 10L);
      }
    });

    Campaign campaign = new Campaign();
    campaign.setAvailableThreshold(0);

    Call call = spy(Call.class);
    call.setCampaign(campaign);

    DefaultFindMatchingUserStrategy defaultFindMatchingUserStrategy = new DefaultFindMatchingUserStrategy(acdEngine, call, true);
    User returnedUser = defaultFindMatchingUserStrategy.getMostIdleUserWithMatchingSkills(campaignGroups, Sets.newHashSet(skill1, skill2));

    assertEquals(user3, returnedUser);
  }

  /**
   * Test receiving most idle user from idle users (the user who was added
   * before others is the most idle)
   * @throws NoSuchMethodException
   * @throws SecurityException
   * @throws IllegalAccessException
   * @throws IllegalArgumentException
   * @throws InvocationTargetException
   */
  @Test
  public void testGetMostIdleUser() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    Campaign campaign = new Campaign();
    campaign.setAvailableThreshold(0);

    Call call = spy(Call.class);
    call.setCampaign(campaign);

    DefaultFindMatchingUserStrategy defaultFindMatchingUserStrategy = new DefaultFindMatchingUserStrategy(acdEngine, call, true);
    User returnedUser = defaultFindMatchingUserStrategy.getMostIdleUser(campaignGroups);

    assertEquals(user3, returnedUser);
    assertEquals(2, ((PriorityBlockingQueue) ((Map) Whitebox.getInternalState(acdEngine, "idleUsers")).get(group1)).size());
  }

}
