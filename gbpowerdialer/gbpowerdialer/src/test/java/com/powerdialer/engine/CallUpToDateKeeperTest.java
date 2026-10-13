package com.powerdialer.engine;

import static org.junit.Assert.assertEquals;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.api.mockito.PowerMockito;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Group;
import com.powerdialer.model.Status;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;

/**
 * Test for class CallUpToDateKeeper
 */
@RunWith(MockitoJUnitRunner.class)
public class CallUpToDateKeeperTest {

  @InjectMocks
  private CallUpToDateKeeper callUpToDateKeeper;
  private User user;
  private Call call;
  private Campaign campaign;

  @Before
  public void setup() {
    user = new User();
    user.setId(1L);
    call = new Call();
    call.setUser(user);
    campaign = new Campaign();
    campaign.setId(1L);
    call.setCampaign(campaign);
  }

  /**
   * Test register call with external hard link
   */
  @Test
  public void testCallRegisterWithExternalLink() {
    callUpToDateKeeper.registerCall(call);
    System.gc();
    List<Call> calls = callUpToDateKeeper.getUserCalls(user);
    assertEquals(call, calls.get(0));
  }

  /**
   * Test update user status
   */
  @Test
  public void testUserStatusUpdate() {
    callUpToDateKeeper.registerCall(call);
    Status status = new Status();
    status.setId(Constants.SYSTEM_STATUS.AVAILABLE.getId());
    user.setStatus(status);
    callUpToDateKeeper.onUserStatusUpdate(user, status);
    assertEquals(Constants.SYSTEM_STATUS.AVAILABLE.getId(), callUpToDateKeeper.getUserCalls(user).get(0).getUser().getStatus().getId());
  }

  /**
   * Uretimde call.getUser() ile statu mesajindan gelen user AYNI agent'in FARKLI kopyalaridir
   * (User.equals id-bazli). Statu guncellemesinde call kopyasinin statusSetDate'i de tazelenmeli;
   * bayat kalirsa avail siralamasi (UserAvailTimeComparator), availableThreshold hesabi ve
   * shouldSwitchUser kiyasi eski tarihle yanlis karar verir.
   */
  @Test
  public void testUserStatusUpdateRefreshesStatusSetDateOnCallUserCopy() {
    User callUserCopy = new User();
    callUserCopy.setId(1L);
    callUserCopy.setStatusSetDate(new java.sql.Date(1_000L)); // eski statusSetDate
    Call registeredCall = new Call();
    registeredCall.setUser(callUserCopy);
    registeredCall.setCampaign(campaign);
    callUpToDateKeeper.registerCall(registeredCall);

    User freshUser = new User();
    freshUser.setId(1L);
    Status status = new Status();
    status.setId(Constants.SYSTEM_STATUS.AVAILABLE.getId());
    freshUser.setStatus(status);
    freshUser.setStatusSetDate(new java.sql.Date(2_000L)); // statu mesajindan gelen taze tarih

    callUpToDateKeeper.onUserStatusUpdate(freshUser, status);

    assertEquals(new java.sql.Date(2_000L),
        callUpToDateKeeper.getUserCalls(freshUser).get(0).getUser().getStatusSetDate());
  }

  /**
   * Login yolundan (handleLogin -> handleStatusChange) gelen user'da statusSetDate null olabilir;
   * bu durumda call kopyasindaki mevcut deger korunur (null ile ezilmez).
   */
  @Test
  public void testUserStatusUpdateKeepsOldStatusSetDateWhenFreshOneIsNull() {
    User callUserCopy = new User();
    callUserCopy.setId(1L);
    callUserCopy.setStatusSetDate(new java.sql.Date(1_000L));
    Call registeredCall = new Call();
    registeredCall.setUser(callUserCopy);
    registeredCall.setCampaign(campaign);
    callUpToDateKeeper.registerCall(registeredCall);

    User freshUser = new User();
    freshUser.setId(1L);
    Status status = new Status();
    status.setId(Constants.SYSTEM_STATUS.AVAILABLE.getId());
    freshUser.setStatus(status);
    // freshUser.statusSetDate bilinçli null

    callUpToDateKeeper.onUserStatusUpdate(freshUser, status);

    assertEquals(new java.sql.Date(1_000L),
        callUpToDateKeeper.getUserCalls(freshUser).get(0).getUser().getStatusSetDate());
  }

  /**
   * Test concurrent call's user update
   */
  @Test
  public void testGetCallsByUserGroupWhileWhileCallUnderACDProcessing() {
    Call call = PowerMockito.mock(Call.class);
    User user = new User();
    user.setId(1L);
    Group group = new Group(1L);
    user.setGroup(group);
    PowerMockito.doReturn(user, user, null).when(call).getUser();
    callUpToDateKeeper.registerCall(call);
    Map<User, List<Call>> callsByUserGroup = callUpToDateKeeper.getCallsByUserGroup(group);
    assertEquals(1, callsByUserGroup.get(user).size());
  }

}
