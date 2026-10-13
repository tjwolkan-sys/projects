package com.powerdialer.engine.synchronization;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.UserHolder;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.redis.UserSubscriber;

@RunWith(MockitoJUnitRunner.class)
public class UserSynchronizerTest {

  private static final long USER_ID = 42L;

  private Set<Long> setWithUserId;

  @InjectMocks
  private UserSynchronizer userSynchronizer;

  @Mock
  private RedisUtil mockRedisUtil;

  @Mock
  private UserHolder mockUserHolder;

  @Mock
  private UserSubscriber mockUserSubscriber;

  @Before
  public void setUp() {
    setWithUserId = new HashSet<>();
    setWithUserId.add(USER_ID);
  }

  @Test
  public void redundantUserReachedCatchLimitTest() {
    when(mockRedisUtil.getLiveUserIds()).thenReturn(Collections.emptySet());
    when(mockUserHolder.getOnlineUserIds()).thenReturn(setWithUserId);

    userSynchronizer.synchronizeUsers();
    userSynchronizer.synchronizeUsers();
    userSynchronizer.synchronizeUsers();

    verify(mockUserSubscriber).handleLogout(USER_ID);
  }

  @Test
  public void redundantUserEscapedFromCandidatesTest() {
    when(mockRedisUtil.getLiveUserIds()).thenReturn(Collections.emptySet()).thenReturn(Collections.emptySet()).thenReturn(setWithUserId);
    when(mockUserHolder.getOnlineUserIds()).thenReturn(setWithUserId);

    userSynchronizer.synchronizeUsers();
    userSynchronizer.synchronizeUsers();
    userSynchronizer.synchronizeUsers();

    verify(mockUserSubscriber, never()).handleLogout(USER_ID);
  }

}
