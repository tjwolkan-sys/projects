package tests.redis;

import com.powerdialer.redis.RedisUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;

import java.util.Arrays;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 325654 contract testi.
 *
 * <p>{@code getLiveUserIds()} eskiden ham {@code KEYS userData*} komutunu cluster'daki
 * TUM master node'lara fan-out ediyordu (30.07.2026 pcap: 55 saniyede 27 KEYS = 3 tur
 * x 9 node). {@code KEYS} O(N) bir komuttur ve Redis'in tek thread'li event loop'unu
 * bloke eder; bu da tum client'lari etkiler.</p>
 *
 * <p>Bu test ayni key setinin cursor tabanli {@code SCAN} ile cozuldugunu ve bloke
 * eden node fan-out'unun artik kullanilmadigini kilitler.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class LiveUserIdsScanContractTest {

  private static final String USER_DATA_PATTERN = "userData*";
  private static final int SCAN_COUNT = 1000;

  @Mock
  private RedissonClient redissonClient;

  @Mock
  private RKeys rKeys;

  private RedisUtil redisUtil;

  @Before
  public void setUp() {
    redisUtil = new RedisUtil();
    Whitebox.setInternalState(redisUtil, "redissonClient", redissonClient);
    when(redissonClient.getKeys()).thenReturn(rKeys);
  }

  @Test
  public void liveUserIdsAreResolvedFromScanBasedKeyLookup() {
    when(rKeys.getKeysByPattern(USER_DATA_PATTERN, SCAN_COUNT)).thenReturn(
        Arrays.asList("userData1001", "userData1002", "userDataBozuk", "digerKey"));

    Set<Long> ids = redisUtil.getLiveUserIds();

    assertEquals("yalniz numerik userData key'leri id'ye cevrilmeli", 2, ids.size());
    assertTrue(ids.contains(1001L));
    assertTrue(ids.contains(1002L));
    verify(rKeys).getKeysByPattern(USER_DATA_PATTERN, SCAN_COUNT);
  }

  @Test
  public void emptyKeySpaceYieldsEmptyIdSet() {
    when(rKeys.getKeysByPattern(USER_DATA_PATTERN, SCAN_COUNT)).thenReturn(java.util.Collections.<String>emptyList());

    assertTrue(redisUtil.getLiveUserIds().isEmpty());
  }

  @Test
  public void liveUserIdLookupNeverFansOutBlockingCommandToClusterNodes() {
    when(rKeys.getKeysByPattern(USER_DATA_PATTERN, SCAN_COUNT)).thenReturn(Arrays.asList("userData1"));

    redisUtil.getLiveUserIds();

    verify(redissonClient, never()).getClusterNodesGroup();
    verify(redissonClient, never()).getNodesGroup();
  }
}
