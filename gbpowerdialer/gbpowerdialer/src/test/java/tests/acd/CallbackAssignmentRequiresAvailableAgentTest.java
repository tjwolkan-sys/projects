package tests.acd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.UserHolder;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.acd.UserFindingContextBuilder;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignGroup;
import com.powerdialer.model.Group;
import com.powerdialer.model.Status;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * Task 321997 (Request 3) regresyon: callback atamasi (callbackToAppointer ile appointed VE
 * webphone ASSIGN_TO_SAME_USER ile same-user) yalniz sahip agent AVAILABLE iken
 * (status.isAvailableForDialerCalls() && !dialInProcess) yapilir. Sadece idle kuyrugunda
 * olmak yeterli degildir: molada (available=false) olan sahip callback'i ALAMAZ.
 *
 * findSameUser/findAppointedUser artik getCampaignIdleAndAvailableUsers uzerinden donduigu icin
 * mola durumunda call.getUser() null kalir (callback baska yola -> reschedule/return-to-DB).
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class CallbackAssignmentRequiresAvailableAgentTest {

  @InjectMocks
  private ACDEngine acdEngine;

  @Spy
  private User owner;
  @Spy
  private Campaign campaign;
  @Spy
  private Call call;

  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;
  @Mock
  private UserHolder userHolder;
  @Mock(name = "logger")
  private LoggerDecorator loggerDecorator;

  private Group group;
  private UserFindingContextBuilder builder;

  @Before
  public void init() {
    group = new Group();
    owner.setId(1L);
    owner.setGroup(group);
    Status available = new Status();
    available.setAvailableForDialerCalls(true);
    owner.setStatus(available);

    campaign.setColResultUserId("user_id");
    campaign.setColResultCallbackDate("callback_date");
    campaign.setAllowCallback(true);
    campaign.setAppointerReset(false); // isAppointerTimedOut() NPE'sini onler (Boolean unboxing)
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setCampaignGroups(Collections.singletonList(new CampaignGroup() {
      {
        setCampaign(campaign);
        setGroup(group);
        setEnabled(true);
      }
    }));

    call.setCampaign(campaign);
    call.setRawData(new JSONObject().put("user_id", "1"));
    call.setCallType(Constants.CALL_TYPES.CALLBACK);

    builder = spy(new UserFindingContextBuilder(mock(RedisUtil.class), acdEngine));
    Whitebox.setInternalState(acdEngine, "userFindingContextBuilder", builder);
    Whitebox.setInternalState(acdEngine, "callUpToDateKeeper", callUpToDateKeeper);
    Whitebox.setInternalState(acdEngine, "LOG", mock(LoggerDecorator.class));

    when(userHolder.getUser(owner.getId())).thenReturn(owner);
    acdEngine.addToIdleUsers(owner);
  }

  @Test
  public void appointedCallbackAssignedWhenOwnerAvailable() {
    campaign.setCallbackToAppointer(true);

    acdEngine.processACD(call);

    assertEquals("Available sahibe appointed callback atanmali", owner, call.getUser());
  }

  @Test
  public void appointedCallbackNotAssignedWhenOwnerOnBreak() {
    campaign.setCallbackToAppointer(true);
    owner.getStatus().setAvailableForDialerCalls(false); // mola

    acdEngine.processACD(call);

    assertNull("Moladaki sahibe appointed callback ATANMAMALI", call.getUser());
  }

  @Test
  public void sameUserWebphoneCallbackAssignedWhenOwnerAvailable() {
    campaign.setAllowOverwriteByWebphone(true);
    call.setCallbackType(Constants.CALLBACKTYPE.ASSIGN_TO_SAME_USER);

    acdEngine.processACD(call);

    assertEquals("Available sahibe ASSIGN_TO_SAME_USER callback atanmali", owner, call.getUser());
  }

  @Test
  public void sameUserWebphoneCallbackNotAssignedWhenOwnerOnBreak() {
    campaign.setAllowOverwriteByWebphone(true);
    call.setCallbackType(Constants.CALLBACKTYPE.ASSIGN_TO_SAME_USER);
    owner.getStatus().setAvailableForDialerCalls(false); // mola

    acdEngine.processACD(call);

    assertNull("Moladaki sahibe ASSIGN_TO_SAME_USER callback ATANMAMALI", call.getUser());
  }

  /**
   * FEAT-321997 takip (29.06.2026): ATTEMPT_COUNT stratejisi sahibe ulaşamayıp ASSIGN_TO_SAME_USER
   * callback'i havuza devredince result tablosunda user_id=NULL olur (callback_type değişmez). Bu durumda
   * ACD artık findSameUser'a GİTMEMELİ (orada sahip bulunamayıp callback hiçbir agent'a atanamadan takılırdı);
   * normal campaign-wide atamaya (resolveUserFindingContext -> buildUserFindingContext) düşmelidir.
   */
  @Test
  public void sameUserCallbackConvertedToPoolRoutesToCampaignWideWhenUserIdNull() {
    campaign.setAllowOverwriteByWebphone(true);
    campaign.setDialingBoost(2f); // idle owner var + boost>=1 -> canUseIdleUsers true (getDialingBoost NPE'sini de önler)
    call.setCallbackType(Constants.CALLBACKTYPE.ASSIGN_TO_SAME_USER);
    // Havuza devredilen callback: user_id DB'de NULL -> rawData'da JSONObject.NULL.
    call.setRawData(new JSONObject().put("user_id", JSONObject.NULL));
    doReturn(null).when(builder).buildUserFindingContext(any(), anyBoolean());

    acdEngine.processACD(call);

    // FIX-GLB90060295: kampanya PROGRESSIVE_RELEASED ve cagri originate fazinda oldugu icin aday
    // havuzu artik idle kuyrugu DEGIL, avail agent havuzudur (agent ring boyunca idle'da kalir).
    // Bu testin asil kilitledigi davranis degismedi: user_id NULL olan callback findSameUser'a
    // GITMEZ, normal campaign-wide atamaya duser.
    verify(builder).buildUserFindingContext(call, false);
  }
}
