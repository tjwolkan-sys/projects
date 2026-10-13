package com.powerdialer.engine.acd;

import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.UserHolder;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignGroup;
import com.powerdialer.model.Group;
import com.powerdialer.model.Skill;
import com.powerdialer.model.Status;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.CAMPAIGN_TYPE;
import com.powerdialer.util.LoggerDecorator;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for ACDEngine class
 */
@RunWith(MockitoJUnitRunner.class)
public class ACDEngineTest {

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd HH:mm");

  @InjectMocks
  private ACDEngine acdEngine;

  @Spy
  private User user1;

  @Spy
  private User user2;

  @Spy
  private Campaign campaign;

  @Spy
  private Call call;

  private UserFindingContextBuilder userFindingContextBuilder;

  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;

  @Mock
  private UserHolder userHolder;

  @Mock(name = "logger")
  private LoggerDecorator loggerDecorator;

  private Group userGroup;

  /**
   * Initialise mock objects
   */
  @Before
  public void init() {
    userGroup = new Group();
    user1.setId(1L);
    user1.setGroup(userGroup);
    user2.setId(2L);
    user2.setGroup(userGroup);
    Status status = new Status();
    status.setAvailableForDialerCalls(true);
    user1.setStatus(status);
    user2.setStatus(status);
    campaign.setColResultUserId("user_id");
    campaign.setType(CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setCampaignGroups(Collections.singletonList(new CampaignGroup() {
      {
        setCampaign(campaign);
        setGroup(userGroup);
        setEnabled(true);
      }
    }));
    call.setCampaign(campaign);
    userFindingContextBuilder = spy(new UserFindingContextBuilder(mock(RedisUtil.class), acdEngine));

    Whitebox.setInternalState(acdEngine, "userFindingContextBuilder", userFindingContextBuilder);
    Whitebox.setInternalState(acdEngine, "callUpToDateKeeper", callUpToDateKeeper);

    when(userHolder.getUser(user1.getId())).thenReturn(user1);
  }

  /**
   * Test user addition to idle users and removal from idle users
   */
  @Test
  public void testAddAndRemoveOnlineIdleUsersInGroups() {
    acdEngine.addToIdleUsers(user1);
    Map<Group, Queue<User>> idleUsers = ((Map<Group, Queue<User>>) Whitebox.getInternalState(acdEngine, "idleUsers"));
    assertEquals(1, idleUsers.get(user1.getGroup()).size());

    acdEngine.removeFromIdleUsers(user1);
    assertEquals(0, idleUsers.get(user1.getGroup()).size());
  }

  @Test
  public void givenOfflineUserWhenAddToIdleUserThenDoNothing() {
    when(userHolder.getUser(user1.getId())).thenReturn(null);
    acdEngine.addToIdleUsers(user1);

    Map<Group, Queue<User>> idleUsers = ((Map<Group, Queue<User>>) Whitebox.getInternalState(acdEngine, "idleUsers"));
    assertEquals(0, idleUsers.size());
  }

  /**
   * Test calling processACD method, verify if campaign type created
   */
  @Test
  public void testProcessACD() {
    // FIX-GLB90060295: varsayilan tip PROGRESSIVE_RELEASED. Originate fazinda agent artik idle
    // havuzda birakildigi icin aday havuzu idle kuyrugu OLAMAZ (aksi halde
    // removeFromIdleUsersAndCheck false doner ve hic agent secilemez) -> beklenen argüman false.
    // Idle-only havuzun korundugu tipler testProcessACDForReservedType'ta dogrulanir.
    when(campaign.getEnabledCampaignGroups()).thenReturn(new ArrayList<>());
    campaign.setDialingBoost(1f);
    acdEngine.addToIdleUsers(user1);
    acdEngine.processACD(call);
    verify(userFindingContextBuilder).buildUserFindingContext(call, false);
  }

  @Test
  public void testProcessACDForReservedType() {
    // Rezervasyonlu tipte (PROGRESSIVE_RESERVED) idle-only aday havuzu AYNEN korunur.
    campaign.setType(CAMPAIGN_TYPE.PROGRESSIVE_RESERVED);
    when(campaign.getEnabledCampaignGroups()).thenReturn(new ArrayList<>());
    campaign.setDialingBoost(1f);
    acdEngine.addToIdleUsers(user1);
    acdEngine.processACD(call);
    verify(userFindingContextBuilder).buildUserFindingContext(call, true);
  }

  /**
   * Test calling processACD method, verify if campaign type created
   */
  @Test
  public void testProcessACDWithDisabledCampaignGroup() {
    campaign.getCampaignGroups().get(0).setEnabled(false);
    lenient().when(campaign.getEnabledCampaignGroups()).thenReturn(new ArrayList<>());
    campaign.setDialingBoost(1f);
    acdEngine.addToIdleUsers(user1);
    acdEngine.processACD(call);
    verify(userFindingContextBuilder, times(0)).buildUserFindingContext(call, true);
  }

  /**
   * Test building context for active call
   */
  @Test
  public void testProcessingACDForActiveCall() {
    campaign.setDialingBoost(1f);
    campaign.setSkillEnabled(false);
    call.setAnswerDate(0D);
    acdEngine.processACD(call);
    verify(userFindingContextBuilder).buildUserFindingContext(call, false);
  }

  /**
   * Test building proper finding context for dialing boost greater than 1
   */
  @Test
  public void givenProgressiveReleasedCampaignWithIncreasedDialingBoostWhenProcessACDThenUseNotBridgedUsers() {
    campaign.setType(CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);

    processAcdWithNoIdleAgentsAndIncreasedDialingBoost();

    verify(userFindingContextBuilder, never()).buildUserFindingContext(call, true);
    verify(userFindingContextBuilder).buildUserFindingContext(call, false);
  }

  @Test
  public void givenProgressiveReservedCampaignWithIncreasedDialingBoostWhenProcessACDThenUseNotBridgedUsers() {
    campaign.setType(CAMPAIGN_TYPE.PROGRESSIVE_RESERVED);

    processAcdWithNoIdleAgentsAndIncreasedDialingBoost();

    verify(userFindingContextBuilder, never()).buildUserFindingContext(call, true);
    verify(userFindingContextBuilder).buildUserFindingContext(call, false);
  }

  @Test
  public void givenPreviewStraightCampaignWithIncreasedDialingBoostWhenProcessACDThenDontBuildContext() {
    campaign.setType(CAMPAIGN_TYPE.PREVIEW_STRAIGHT);

    processAcdWithNoIdleAgentsAndIncreasedDialingBoost();

    verify(userFindingContextBuilder, never()).buildUserFindingContext(any(Call.class), anyBoolean());
  }

  @Test
  public void givenPreviewFreeCampaignWithIncreasedDialingBoostWhenProcessACDThenDontBuildContext() {
    campaign.setType(CAMPAIGN_TYPE.PREVIEW_STRAIGHT);

    processAcdWithNoIdleAgentsAndIncreasedDialingBoost();

    verify(userFindingContextBuilder, never()).buildUserFindingContext(any(Call.class), anyBoolean());
  }

  /**
   * Test building proper finding context for dialing boost less than 1
   */
  @Test
  public void givenProgressiveReleasedCampaignWithDecreasedDialingBoostWhenProcessACDThenDontBuildContext() {
    campaign.setType(CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);

    processAcdWithIdleAgentAndDecreasedDialingBoost();

    verify(userFindingContextBuilder, never()).buildUserFindingContext(any(Call.class), anyBoolean());
  }

  @Test
  public void givenProgressiveReservedCampaignWithDecreasedDialingBoostWhenProcessACDThenDontBuildContext() {
    campaign.setType(CAMPAIGN_TYPE.PROGRESSIVE_RESERVED);

    processAcdWithIdleAgentAndDecreasedDialingBoost();

    verify(userFindingContextBuilder, never()).buildUserFindingContext(any(Call.class), anyBoolean());
  }

  @Test
  public void givenPreviewStraightCampaignWithDecreasedDialingBoostWhenProcessACDThenUseIdleUsers() {
    campaign.setType(CAMPAIGN_TYPE.PREVIEW_STRAIGHT);

    processAcdWithIdleAgentAndDecreasedDialingBoost();

    verify(userFindingContextBuilder).buildUserFindingContext(call, true);
    verify(userFindingContextBuilder, never()).buildUserFindingContext(call, false);
  }

  @Test
  public void givenPreviewFreeCampaignWithDecreasedDialingBoostWhenProcessACDThenUseIdleUsers() {
    campaign.setType(CAMPAIGN_TYPE.PREVIEW_FREE);

    processAcdWithIdleAgentAndDecreasedDialingBoost();

    verify(userFindingContextBuilder).buildUserFindingContext(call, true);
    verify(userFindingContextBuilder, never()).buildUserFindingContext(call, false);
  }

  /**
   * Test finding users with not bridged calls
   */
  @Test
  public void testGetAvailableUsersQueueInGroup() {
    Call call = new Call();
    call.setCampaign(campaign);

    Call call1 = new Call();
    call1.setCampaign(campaign);
    HashMap<User, List<Call>> userToCalls = new HashMap<>();
    userToCalls.put(user1, Collections.singletonList(this.call));
    userToCalls.put(user2, Arrays.asList(call, call1));
    doReturn(userToCalls).when(callUpToDateKeeper).getCallsByUserGroup(userGroup);
    Queue<User> usersQueueWithOnlyNotBridgedCallsInGroup = acdEngine.getAvailableUsersQueueInGroup(userGroup);
    assertFalse(usersQueueWithOnlyNotBridgedCallsInGroup.contains(user2));
    assertTrue(usersQueueWithOnlyNotBridgedCallsInGroup.contains(user1));
  }

  /**
   * Test finding users with not bridged call which is retry
   */
  @Test
  public void testGetAvailableUsersQueueInGroupRetryInGroup() {
    Call callWithRetry = new Call();
    callWithRetry.setAnswerDate(0D);
    callWithRetry.setRetryDate(123D);
    callWithRetry.setCampaign(campaign);

    HashMap<User, List<Call>> userToCalls = new HashMap<>();
    userToCalls.put(user2, Collections.singletonList(callWithRetry));
    doReturn(userToCalls).when(callUpToDateKeeper).getCallsByUserGroup(userGroup);
    Queue<User> usersQueueWithOnlyNotBridgedCallsInGroup = acdEngine.getAvailableUsersQueueInGroup(userGroup);
    assertTrue(usersQueueWithOnlyNotBridgedCallsInGroup.contains(user2));
  }

  /**
   * Test finding users with stuck call
   */
  @Test
  public void testGetAvailableUsersQueueInGroupWithStuckCall() {
    Call stuckCall = new Call();
    stuckCall.setAnswerDate(0D);
    stuckCall.setCampaign(campaign);

    HashMap<User, List<Call>> userToCalls = new HashMap<>();
    userToCalls.put(user2, Collections.singletonList(stuckCall));
    doReturn(userToCalls).when(callUpToDateKeeper).getCallsByUserGroup(userGroup);
    Queue<User> usersQueueWithOnlyNotBridgedCallsInGroup = acdEngine.getAvailableUsersQueueInGroup(userGroup);
    assertTrue(usersQueueWithOnlyNotBridgedCallsInGroup.contains(user2));
  }

  /**
   * Test finding user that appointed to handle the call
   */
  @Test
  public void testFindAppointedUser() {
    Call call = new Call();
    call.setRawData(new JSONObject("{\"user_id\" : 1}"));
    call.setCampaign(campaign);
    campaign.setAllowCallback(true);
    call.setCallType(Constants.CALL_TYPES.CALLBACK);
    campaign.setCallbackToAppointer(true);
    campaign.setColResultCallbackDate("callback_date");
    campaign.setAppointerReset(false);
    acdEngine.addToIdleUsers(user1);
    acdEngine.processACD(call);
    assertEquals(user1, call.getUser());
  }

  @Test
  public void givenTwoUsersWhenFindUsersWithMatchingSkillsThenReturnMostSkilledOne() {
    Skill skill = new Skill();
    user1.getSkillWeightPair().put(skill, 10L);
    user2.getSkillWeightPair().put(skill, 11L);

    for (int i = 1; i <= 20; i++) {
      Set<User> result = acdEngine.findUsersWithMatchingSkills(Arrays.asList(user1, user2), Collections.singleton(skill));
      assertEquals(result.size(), 2);
      assertUsersOrder(result, user2, user1);
    }
  }

  @Test
  public void givenTwoUsersWithSameSkillWeightWhenFindUsersWithMatchingSkillsThenReturnMostIdleOne() throws ParseException {
    // Esit skill weight'te tie-break UserAvailTimeComparator ile statusSetDate uzerinden yapilir
    // (lastCallEndDate DEGIL — eski comparator wrapup/break suresini saymadigi icin bilinclice
    // degistirildi, bkz. ACDEngine.getComparator yorumu). Daha eski statusSetDate = daha uzun
    // avail bekleyen agent one gecer.
    Skill skill = new Skill();
    user1.getSkillWeightPair().put(skill, 10L);
    user2.getSkillWeightPair().put(skill, 10L);
    user1.setStatusSetDate(new java.sql.Date(DATE_FORMAT.parse("2024.09.18 09:05").getTime()));
    user2.setStatusSetDate(new java.sql.Date(DATE_FORMAT.parse("2024.09.18 09:02").getTime()));

    for (int i = 1; i <= 20; i++) {
      Set<User> result = acdEngine.findUsersWithMatchingSkills(Arrays.asList(user1, user2), Collections.singleton(skill));
      assertEquals(result.size(), 2);
      assertUsersOrder(result, user2, user1);
    }
  }

  private void processAcdWithNoIdleAgentsAndIncreasedDialingBoost() {
    when(campaign.getEnabledCampaignGroups()).thenReturn(new ArrayList<>());
    campaign.setDialingBoost(2f);
    acdEngine.removeFromIdleUsers(user1);
    doReturn(Collections.emptyList()).when(callUpToDateKeeper).getAllRegisteredUsers();
    doReturn(new HashMap<User, List<Call>>() {
      {
        put(user1, Collections.singletonList(call));
      }
    }).when(callUpToDateKeeper).getCallsByUserGroup(userGroup);

    acdEngine.processACD(call);
  }

  private void processAcdWithIdleAgentAndDecreasedDialingBoost() {
    when(campaign.getEnabledCampaignGroups()).thenReturn(new ArrayList<>());
    campaign.setDialingBoost(0.5f);
    acdEngine.addToIdleUsers(user1);
    lenient().doReturn(Collections.emptyList()).when(callUpToDateKeeper).getAllRegisteredUsers();

    acdEngine.processACD(call);
  }

  private void assertUsersOrder(Set<User> users, User expectedFirst, User expectedSecond) {
    Iterator<User> iterator = users.iterator();
    User actualFirst = iterator.next();
    User actualSecond = iterator.next();
    assertEquals(actualFirst, expectedFirst);
    assertEquals(actualSecond, expectedSecond);
  }

  // ------------------------------------------------------ MADDE 1: trySearchLessOccupiedUser GAMMA yolu

  @Test
  public void trySearchLessOccupiedUser_gamma_runsProcessACDUnconditionally() {
    // GAMMA: on-atama yok (initialUser null), lastCallEndDate kapisi ve shouldSwitchUser swap atlanir;
    // kosulsuz processACD ile tek-sefer secim yapilir (buildUserFindingContext cagrilir).
    campaign.setType(CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY.GAMMA);
    call.setUser(null);
    when(campaign.getEnabledCampaignGroups()).thenReturn(new ArrayList<>());
    campaign.setDialingBoost(1f);

    acdEngine.trySearchLessOccupiedUser(call);

    // GAMMA branch resolveUserFindingContext -> buildUserFindingContext(call, false) (ACDEngine.java:410).
    // Onemli olan: processACD kosulsuz CALISTI (kapi/swap atlandi); secim akisi tetiklendi.
    verify(userFindingContextBuilder).buildUserFindingContext(call, false);
  }

  @Test
  public void trySearchLessOccupiedUser_reservedType_keepsLastCallEndDateGate() {
    // Rezervasyonlu tip (PROGRESSIVE_RESERVED): initialUser'in lastCallEndDate'i yoksa (cache null)
    // eski kapi kapali kalir -> processACD CAGRILMAZ (mevcut davranis korunur, regresyon).
    //
    // FIX-GLB90060295: bu test eskiden varsayilan PROGRESSIVE_RELEASED tipiyle kosuyordu. Artik
    // PROGRESSIVE_RELEASED'de on atama SEMBOLIKTIR (agent originate fazinda idle havuzdan
    // cikarilmaz), bu yuzden answer fazinda yeniden secim kosulsuz calisir. Eski kapinin hala
    // gecerli oldugu tipler icin kapsam korunsun diye test PROGRESSIVE_RESERVED'e tasindi;
    // PROGRESSIVE_RELEASED'in yeni davranisi tests.acd.AcdAnswerPhaseReselectionScopeTest'te.
    campaign.setType(CAMPAIGN_TYPE.PROGRESSIVE_RESERVED);
    call.setUser(user1);
    // getUserLastCallEndDate(user1) -> cache bos -> null (kapi kapali)

    acdEngine.trySearchLessOccupiedUser(call);

    verify(userFindingContextBuilder, never()).buildUserFindingContext(any(Call.class), anyBoolean());
  }

  // ------------------------------------------------------ SORUN 3 / GLB90090645: answer aninda avail yoksa
  // koru korune (statu kontrolsuz) initialUser'a geri donus GAMMA'da kaldirilir.

  @Test
  public void trySearchLessOccupiedUser_gammaNoAvail_leavesUserNullInsteadOfBusyInitialUser() {
    // GAMMA: musteri actiginda processACD avail agent bulamazsa, ring sirasinda meşgullenmis
    // olabilecek initialUser'a geri DONULMEZ; user null birakilir. AGI dongusu (processDialCommand)
    // 100ms poll ile taze agent arar; initialUser gercekten bosalirsa idle'a doner ve yeniden
    // secilebilir (bugunku davranista tryLock/SKIP sonrasi triedUsers'a kalici yakiliyordu).
    campaign.setType(CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY.GAMMA);
    Status busyStatus = new Status();
    busyStatus.setAvailableForDialerCalls(false);
    user1.setStatus(busyStatus); // initialUser ring sirasinda Incall/Mola olmus
    call.setUser(user1);
    when(campaign.getEnabledCampaignGroups()).thenReturn(new ArrayList<>()); // processACD aday bulamaz -> null

    acdEngine.trySearchLessOccupiedUser(call);

    assertNull(call.getUser());
  }

  @Test
  public void trySearchLessOccupiedUser_reservedTypeNoAvail_keepsFallbackToInitialUser() {
    // Karakterizasyon: rezervasyonlu tip (PROGRESSIVE_RESERVED) ayni senaryoda mevcut davranisini
    // korur; processACD aday bulamayinca initialUser'a geri doner. (Tazeleme kapisi lastCallEndDate
    // ile acilir.) Orada on atama gercek rezervasyondur, o yuzden kor geri donus dogrudur.
    //
    // FIX-GLB90060295: bu test eskiden varsayilan PROGRESSIVE_RELEASED tipiyle kosuyordu.
    // PROGRESSIVE_RELEASED artik sembolik on atama kullandigi icin avail agent yoksa user NULL
    // birakilir (mesgul olabilecek on atamaya kor donus yapilmaz); bkz.
    // tests.acd.AcdAnswerPhaseReselectionScopeTest.
    campaign.setType(CAMPAIGN_TYPE.PROGRESSIVE_RESERVED);
    call.setUser(user1);
    acdEngine.updateUserLastCallEndDate(user1, new Date());
    campaign.setDialingBoost(1f);
    // idle havuzu bos + boost=1 -> canUseIdleUsers/canUseNotBridgedUsers false -> processACD user'i null yapar

    acdEngine.trySearchLessOccupiedUser(call);

    assertEquals(user1, call.getUser());
  }

  // ------------------------------------------------------ REDIRECT-TO-ME / OWNER-BOUND CALLBACK regresyonu:
  // appointed/same-user callback fresh/retry gibi symbolic + reselect + NOAVAIL akisina GIRMEMELI; sahibine
  // baglanmali. (Regresyon: GAMMA reselect, sahip idle havuzunda olmadigi an null'a dusurup callback'i dusuruyordu.)

  @Test
  public void trySearchLessOccupiedUser_gammaOwnerBoundCallback_skipsReselectAndKeepsOwner() {
    campaign.setType(CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY.GAMMA);
    doReturn(true).when(call).isOwnerBoundCallback();
    call.setUser(user1); // originate'te atanan sahip

    acdEngine.trySearchLessOccupiedUser(call);

    // Yeniden secim/swap/NOAVAIL calismaz: processACD tetiklenmez, sahip korunur.
    verify(userFindingContextBuilder, never()).buildUserFindingContext(any(Call.class), anyBoolean());
    assertEquals("owner-bound callback answer'da sahibe baglanmali (reselect yok)", user1, call.getUser());
  }

  @Test
  public void trySearchLessOccupiedUser_gammaFreshCall_stillRunsReselect() {
    // Muafiyet yalnizca owner-bound callback'e; fresh/retry GAMMA reselect'i bit-esit korunur.
    campaign.setType(CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY.GAMMA);
    doReturn(false).when(call).isOwnerBoundCallback();
    call.setUser(null);
    when(campaign.getEnabledCampaignGroups()).thenReturn(new ArrayList<>());
    campaign.setDialingBoost(1f);

    acdEngine.trySearchLessOccupiedUser(call);

    verify(userFindingContextBuilder).buildUserFindingContext(call, false);
  }

  // ------------------------------------------------------ SORUN 1 / 1A - GLB90090645: GAMMA originate fazinda
  // on atama SEMBOLIKTIR: secilen agent idle'dan CIKARILMAZ (rezervasyon yok). Answer fazinda
  // (originationDate dolu) cikarma aynen surer. Diger stratejiler bit-esit ayni kalir.

  /**
   * Ortak kurulum: user1 idle'da, havuz yalniz idle'dan beslenir, threshold gecilir.
   * Faz, cagiran testte call.setOriginationDate/setAnswerDate ile belirlenir.
   */
  private void arrangeIdleSelectionScenario(Constants.PREDICTIVE_STRATEGY strategy) {
    campaign.setType(CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(strategy);
    campaign.setSkillEnabled(false);
    acdEngine.addToIdleUsers(user1);
    doReturn(Collections.emptyMap()).when(callUpToDateKeeper).getCallsByUserGroup(userGroup);
    doReturn(Integer.MAX_VALUE).when(user1).calculateTimeInStatus();
    doReturn(0).when(campaign).getAvailableThreshold();
  }

  private Queue<User> idleQueue() {
    Map<Group, Queue<User>> idleUsers = ((Map<Group, Queue<User>>) Whitebox.getInternalState(acdEngine, "idleUsers"));
    return idleUsers.get(userGroup);
  }

  @Test
  public void processACD_gammaOriginatePhase_keepsSelectedUserInIdleQueue() {
    arrangeIdleSelectionScenario(Constants.PREDICTIVE_STRATEGY.GAMMA);
    // call.originationDate/startDate/answerDate == null -> originate (1. secim) fazi

    acdEngine.processACD(call);

    assertEquals(user1, call.getUser());
    assertTrue("originate fazinda secilen agent idle'dan dusurulmemeli (SORUN 1/1A)",
        idleQueue().contains(user1));
  }

  @Test
  public void processACD_gammaAnswerPhase_stillRemovesSelectedUserFromIdleQueue() {
    arrangeIdleSelectionScenario(Constants.PREDICTIVE_STRATEGY.GAMMA);
    call.setOriginationDate(123D); // originate edilmis -> answer-time (2. secim) fazi

    acdEngine.processACD(call);

    assertEquals(user1, call.getUser());
    assertTrue("answer fazinda dial edilecek agent bugunku gibi idle'dan alinmali",
        idleQueue().isEmpty());
  }

  @Test
  public void processACD_gammaAnsweredCallWithNulledOriginationDate_stillRemovesFromIdle() {
    // FCT caller-id retry'i originationDate'i null'layip cagriyi dogrudan yeniden originate eder
    // ve tarih bir daha set edilmez; cevaplanmis cagrinin answer-time secimi originate fazi
    // SANILMAMALI. Faz tespiti startDate/answerDate'i de kontrol eder (code-review CONFIRMED).
    arrangeIdleSelectionScenario(Constants.PREDICTIVE_STRATEGY.GAMMA);
    call.setAnswerDate(5D); // originationDate null ama cagri cevaplanmis

    acdEngine.processACD(call);

    assertEquals(user1, call.getUser());
    assertTrue("cevaplanmis cagrida (originationDate null'lansa bile) cikarma surmeli",
        idleQueue().isEmpty());
  }

  @Test
  public void processACD_betaOriginatePhase_stillRemovesSelectedUserFromIdleQueue() {
    // En yakin komsu strateji: BETA ayni kod yolunu (useOnlyIdleUsers=false) paylasir;
    // guard yalnizca GAMMA'yi kapsamali, BETA bugunku gibi cikarmali.
    arrangeIdleSelectionScenario(Constants.PREDICTIVE_STRATEGY.BETA);

    acdEngine.processACD(call);

    assertEquals(user1, call.getUser());
    assertTrue("BETA originate secimi bugunku gibi idle'dan cikarmali (guard yalniz GAMMA)",
        idleQueue().isEmpty());
  }

  @Test
  public void processACD_gammaOriginatePhaseOwnerBoundCallback_reservesSelectedUserFromIdleQueue() {
    // Owner-bound callback (appointed/same-user) originate fazinda SEMBOLIK DEGILDIR: secilen sahip idle'dan
    // CIKARILIR (rezerve) ki ring boyunca fresh (Xn) cagrilar ayni sahibi kapamasin. Fresh originate'te ise
    // idle'da kalir (bkz. processACD_gammaOriginatePhase_keepsSelectedUserInIdleQueue).
    arrangeIdleSelectionScenario(Constants.PREDICTIVE_STRATEGY.GAMMA);
    doReturn(true).when(call).isOwnerBoundCallback();
    // originationDate/startDate/answerDate == null (originate fazi) ama owner-bound -> isGammaOriginatePhase false

    acdEngine.processACD(call);

    assertEquals(user1, call.getUser());
    assertTrue("owner-bound callback originate'te sahip idle'dan rezerve edilmeli (ring'de fresh kapamasin)",
        idleQueue() == null || idleQueue().isEmpty());
  }

  @Test
  public void trySearchLessOccupiedUser_gammaBusyCarrierInitial_switchesToFreshAvailableUser() {
    // 1B: initialUser mesgul temsili tasiyici. statusSetDate kiyasina girmeden (eski tarihli
    // tasiyici kiyasi "kazanirdi") dogrudan taze avail agent'a gecilmeli (code-review bulgusu).
    campaign.setType(CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY.GAMMA);
    campaign.setSkillEnabled(false);
    call.setOriginationDate(123D); // answer fazi
    Status busyStatus = new Status();
    busyStatus.setAvailableForDialerCalls(false);
    user1.setStatus(busyStatus);
    user1.setStatusSetDate(new java.sql.Date(1_000L)); // eski tarih: eski kiyas tasiyiciyi tutardi
    call.setUser(user1);
    Status availStatus = new Status();
    availStatus.setAvailableForDialerCalls(true);
    user2.setStatus(availStatus);
    user2.setStatusSetDate(new java.sql.Date(2_000L));
    when(userHolder.getUser(user2.getId())).thenReturn(user2);
    acdEngine.addToIdleUsers(user2);
    doReturn(Collections.emptyMap()).when(callUpToDateKeeper).getCallsByUserGroup(userGroup);
    doReturn(Integer.MAX_VALUE).when(user2).calculateTimeInStatus();
    doReturn(0).when(campaign).getAvailableThreshold();

    acdEngine.trySearchLessOccupiedUser(call);

    assertEquals("mesgul tasiyici degil, taze avail agent atanmali", user2, call.getUser());
    assertTrue("mesgul tasiyici idle'a sizmamali (kapi reddetmeli)",
        idleQueue() == null || !idleQueue().contains(user1));
  }

  @Test
  public void addToIdleUsers_skipsWhenCanonicalDialInProcess() {
    // Okuma tarafinin "avail" tanimiyla ayni: dial'i suren agent idle'a geri sizamaz.
    user1.setDialInProcess(true);

    acdEngine.addToIdleUsers(user1);

    assertTrue(idleQueue() == null || idleQueue().isEmpty());
  }

  @Test
  public void removeFromIdleUsers_usesCanonicalGroupWhenPassedCopyHasStaleGroup() {
    // Ekleme canonical grubun kuyruguna yapilir; cikarma da bayat gruplu kopyayla cagrilsa bile
    // ayni kuyrugu hedeflemeli (grup degisikligi penceresinde girdi mahsur kalmamali).
    acdEngine.addToIdleUsers(user1);
    User staleGroupCopy = new User();
    staleGroupCopy.setId(user1.getId());
    staleGroupCopy.setGroup(new Group(99L));

    boolean removed = acdEngine.removeFromIdleUsers(staleGroupCopy);

    assertTrue("bayat gruplu kopya ile de canonical kuyruktan cikarilmali", removed);
    assertTrue(idleQueue().isEmpty());
  }

  // ------------------------------------------------------ SORUN 2 / 1B - GLB90090645: temsili tasiyici

  @Test
  public void findGammaSymbolicCarrier_returnsOnlineCampaignUserEvenIfBusy() {
    // Tasiyicinin avail olmasi gerekmez (exten tasiyicisidir, gercek secim answer'da yapilir):
    // Mola'daki online agent bile taissiyici olabilir.
    Status molaStatus = new Status();
    molaStatus.setAvailableForDialerCalls(false);
    user1.setStatus(molaStatus);
    acdEngine.addToActiveUsers(user1); // login/aktif havuzu

    User carrier = acdEngine.findGammaSymbolicCarrier(campaign);

    assertEquals(user1, carrier);
  }

  @Test
  public void findGammaSymbolicCarrier_returnsNullWhenNoOnlineUsers() {
    // Kampanyada hic online agent yoksa tasiyici da yoktur -> originate cikarilmaz (bos oda emniyeti).
    assertNull(acdEngine.findGammaSymbolicCarrier(campaign));
  }

  // ------------------------------------------------------ SORUN 4 / GLB90090645: idle kapisi invariant'i
  // Idle kuyruguna yalniz o an GERCEKTEN avail olan agent girer ve girdi her zaman canonical
  // (userHolder) nesnesidir. Iptal/swap yollari statuye bakmadan kopya nesneleri geri ekleyip
  // Mola/Toplanti'daki agent'i secime sokuyor, yetim kopyalar da guncellenemeden idle'da kaliyordu.

  @Test
  public void addToIdleUsers_skipsWhenCanonicalUserNotAvailable() {
    // Cagiranin elindeki kopya "Available" gorunse bile karar canonical kopyaya gore verilir:
    // canonical Mola'da (avail=false) ise agent idle'a giremez.
    User canonical = new User();
    canonical.setId(7L);
    canonical.setGroup(userGroup);
    Status molaStatus = new Status();
    molaStatus.setAvailableForDialerCalls(false);
    canonical.setStatus(molaStatus);
    when(userHolder.getUser(7L)).thenReturn(canonical);

    User staleCopy = new User();
    staleCopy.setId(7L);
    staleCopy.setGroup(userGroup);
    Status staleAvailStatus = new Status();
    staleAvailStatus.setAvailableForDialerCalls(true);
    staleCopy.setStatus(staleAvailStatus);

    acdEngine.addToIdleUsers(staleCopy);

    Map<Group, Queue<User>> idleUsers = ((Map<Group, Queue<User>>) Whitebox.getInternalState(acdEngine, "idleUsers"));
    assertTrue(idleUsers.get(userGroup) == null || idleUsers.get(userGroup).isEmpty());
  }

  @Test
  public void addToIdleUsers_insertsCanonicalInstanceInsteadOfPassedCopy() {
    // Kuyruga CANONICAL nesne konur: idle'daki girdi her zaman statu mesajlariyla guncellenen
    // nesnedir; kimsenin guncellemedigi yetim kopyalar kuyruga giremez.
    User canonical = new User();
    canonical.setId(8L);
    canonical.setGroup(userGroup);
    Status availStatus = new Status();
    availStatus.setAvailableForDialerCalls(true);
    canonical.setStatus(availStatus);
    when(userHolder.getUser(8L)).thenReturn(canonical);

    User staleCopy = new User();
    staleCopy.setId(8L);
    staleCopy.setGroup(userGroup);
    staleCopy.setStatus(availStatus);

    acdEngine.addToIdleUsers(staleCopy);

    Map<Group, Queue<User>> idleUsers = ((Map<Group, Queue<User>>) Whitebox.getInternalState(acdEngine, "idleUsers"));
    assertSame(canonical, idleUsers.get(userGroup).peek());
  }

  @Test
  public void trySearchLessOccupiedUser_gammaSameUserReselected_keepsInitialUser() {
    // GAMMA: processACD ayni agent'i (initialUser) yeniden secerse davranis degismez; user korunur.
    // Null-birakma yalnizca processACD'nin HIC aday bulamadigi dala ozeldir.
    campaign.setType(CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY.GAMMA);
    campaign.setSkillEnabled(false);
    call.setUser(user1);
    doReturn(new HashMap<User, List<Call>>() {
      {
        put(user1, Collections.singletonList(mock(Call.class)));
      }
    }).when(callUpToDateKeeper).getCallsByUserGroup(userGroup);
    doReturn(Integer.MAX_VALUE).when(user1).calculateTimeInStatus();
    doReturn(0).when(campaign).getAvailableThreshold();

    acdEngine.trySearchLessOccupiedUser(call);

    assertEquals(user1, call.getUser());
  }

}
