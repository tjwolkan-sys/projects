package tests.acd;

import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.acd.DefaultFindMatchingUserStrategy;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignGroup;
import com.powerdialer.model.Group;
import com.powerdialer.model.Skill;
import com.powerdialer.model.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ACD agent dağıtımının priority-aware cross-group LCD davranışını kilitler.
 *
 * <p>Test edilen davranışlar:
 * <ul>
 *   <li>Tek grupta {@code Collections.shuffle} yerine LCD (longest avail wait) sıralı dağıtım.</li>
 *   <li>Aynı priority'li ardışık grupların tek havuza birleşmesi.</li>
 *   <li>Havuz içinde "hiç çağrı almayan → en uzun avail" sıralaması.</li>
 *   <li>Yüksek priority havuzu boşsa bir alt priority havuzuna düşme.</li>
 *   <li>{@code availableThreshold} gate'inin korunması.</li>
 *   <li>Skill enabled kampanyalarda mevcut skill weight + LCD comparator zincirinin korunması.</li>
 * </ul>
 *
 * <p>Korunan mevcut mekanizmalar (testte stub'lanan davranışlar):
 * <ul>
 *   <li>{@link ACDEngine#getAvailableUsersQueueInGroup} per-group minCallsNumber filtresi.</li>
 *   <li>{@link ACDEngine#removeFromIdleUsers} atomic remove kontrolü.</li>
 *   <li>{@link ACDEngine#getUserLastCallEndDate} cache lookup'ı.</li>
 *   <li>{@link ACDEngine#findUsersWithMatchingSkills} TreeSet skill-aware sort.</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.class)
public class AcdPriorityAwareLcdDistributionTest {

  @Mock private ACDEngine acdEngine;
  @Mock private Call call;
  @Mock private Campaign campaign;

  /** Recorded last-call-end dates per user; ACDEngine stub returns from this map. */
  private final Map<User, Date> lastCallEndDates = new HashMap<>();

  @Before
  public void setUp() {
    when(call.getCampaign()).thenReturn(campaign);
    lenient().when(call.getId()).thenReturn("call-1");
    lenient().when(call.getSkills()).thenReturn(Collections.emptySet());
    lenient().when(campaign.getAvailableThreshold()).thenReturn(0);
    lenient().when(campaign.getSkillEnabled()).thenReturn(false);

    // ACDEngine.getUserLastCallEndDate -> map lookup
    lenient().when(acdEngine.getUserLastCallEndDate(any(User.class))).thenAnswer(invocation -> {
      User u = invocation.getArgument(0);
      return lastCallEndDates.get(u);
    });
    // removeFromIdleUsers -> true (idle path)
    lenient().when(acdEngine.removeFromIdleUsers(any(User.class))).thenReturn(true);
  }

  // ------------------------------------------------------------------ Tek grup, LCD

  @Test
  public void singlePriorityGroup_availTimeSortApplied_userWithLongestAvailFirst() {
    Group g = group(1L, "G1");
    CampaignGroup cg = campaignGroup(g, 5);
    when(campaign.getEnabledCampaignGroups()).thenReturn(list(cg));

    // statusSetDate uzerinden avail suresi: mutlu 4 dk, ayse 2 dk, hasan 30 sn
    User mutlu = user(101L, "mutlu", 240);
    User hasan = user(102L, "hasan", 30);
    User ayse = user(103L, "ayse", 120);
    stubQueue(g, mutlu, hasan, ayse);

    DefaultFindMatchingUserStrategy strategy = strategy(false);

    User matched = strategy.findMatchingUser();

    assertNotNull(matched);
    assertEquals("En uzun avail bekleyen secilmeli", "mutlu", matched.getUsername());
  }

  @Test
  public void singlePriorityGroup_freshLoginWithLongAvailTime_isFirst() {
    Group g = group(1L, "G1");
    CampaignGroup cg = campaignGroup(g, 5);
    when(campaign.getEnabledCampaignGroups()).thenReturn(list(cg));

    // Gun basi: freshLogin sabah login oldu (10 dk avail), mutlu az once cagri bitirdi (1 dk avail)
    // freshLogin daha uzun avail bekledigi icin en basta gelmeli.
    User freshLogin = user(201L, "freshlogin", 600);
    User mutlu = user(202L, "mutlu", 60);
    stubQueue(g, mutlu, freshLogin);

    DefaultFindMatchingUserStrategy strategy = strategy(false);

    User matched = strategy.findMatchingUser();

    assertNotNull(matched);
    assertEquals("Uzun avail bekleyen yeni login en basta gelmeli", "freshlogin", matched.getUsername());
  }

  // ----------------------------------------------------------- Cross-group aynı priority

  @Test
  public void multipleGroupsSamePriority_pooledIntoSingleBucket_availTimeAppliedAcrossPool() {
    Group ga = group(1L, "A");
    Group gb = group(2L, "B");
    CampaignGroup cgA = campaignGroup(ga, 5);
    CampaignGroup cgB = campaignGroup(gb, 5);
    when(campaign.getEnabledCampaignGroups()).thenReturn(list(cgA, cgB));

    // Avail sureleri statusSetDate uzerinden: mutluA 4 dk, hasanA 30 sn, ayseB 6 dk
    // En uzun avail ayseB ama farkli grupta — cross-group havuzda onun secilmesi beklenir.
    User mutluA = user(301L, "mutluA", 240);
    User hasanA = user(302L, "hasanA", 30);
    User ayseB = user(303L, "ayseB", 360);

    stubQueue(ga, mutluA, hasanA);
    stubQueue(gb, ayseB);

    DefaultFindMatchingUserStrategy strategy = strategy(false);

    User matched = strategy.findMatchingUser();

    assertNotNull(matched);
    assertEquals("Cross-group havuzda en uzun avail bekleyen (Ayse 6 dk) secilmeli",
        "ayseB", matched.getUsername());
  }

  // ------------------------------------------------------------ Farklı priority hiyerarşisi

  @Test
  public void differentPriority_higherPriorityBucketFirst() {
    Group ga = group(1L, "A");
    Group gb = group(2L, "B");
    CampaignGroup cgA = campaignGroup(ga, 10);   // yuksek priority
    CampaignGroup cgB = campaignGroup(gb, 5);
    when(campaign.getEnabledCampaignGroups()).thenReturn(list(cgA, cgB));

    User mutluA = user(401L, "mutluA", 60);   // Grup-A, 30 sn
    User mehmetB = user(402L, "mehmetB", 60); // Grup-B, 10 dk
    setLastCallEnd(mutluA, secondsAgo(30));
    setLastCallEnd(mehmetB, secondsAgo(600));

    stubQueue(ga, mutluA);
    stubQueue(gb, mehmetB);

    DefaultFindMatchingUserStrategy strategy = strategy(false);

    User matched = strategy.findMatchingUser();

    assertNotNull(matched);
    assertEquals("Yuksek priority grup once alir (Grup-B'de daha uzun bekleyen olsa bile)",
        "mutluA", matched.getUsername());
  }

  @Test
  public void higherPriorityBucketEmpty_fallsToLowerPriorityBucket() {
    Group ga = group(1L, "A");
    Group gb = group(2L, "B");
    CampaignGroup cgA = campaignGroup(ga, 10);
    CampaignGroup cgB = campaignGroup(gb, 5);
    when(campaign.getEnabledCampaignGroups()).thenReturn(list(cgA, cgB));

    User mehmetB = user(501L, "mehmetB", 60);
    setLastCallEnd(mehmetB, secondsAgo(180));

    // Grup-A bos
    stubQueue(ga);
    stubQueue(gb, mehmetB);

    DefaultFindMatchingUserStrategy strategy = strategy(false);

    User matched = strategy.findMatchingUser();

    assertNotNull(matched);
    assertEquals("Yuksek priority bos ise alt priority havuzuna dusulmeli",
        "mehmetB", matched.getUsername());
  }

  // ----------------------------------------------------------- Threshold gate

  @Test
  public void availableThresholdGate_userBelowThresholdSkipped() {
    Group g = group(1L, "G1");
    CampaignGroup cg = campaignGroup(g, 5);
    when(campaign.getEnabledCampaignGroups()).thenReturn(list(cg));
    when(campaign.getAvailableThreshold()).thenReturn(60);   // 60 sn esigi

    User hasan = user(601L, "hasan", 30);    // 30 sn avail - threshold gecmedi
    User mutlu = user(602L, "mutlu", 120);   // 2 dk avail - threshold gecti
    setLastCallEnd(hasan, secondsAgo(600));  // 10 dk - daha eski ama threshold yok
    setLastCallEnd(mutlu, secondsAgo(180));  // 3 dk - daha yeni ama threshold var

    stubQueue(g, hasan, mutlu);

    DefaultFindMatchingUserStrategy strategy = strategy(false);

    User matched = strategy.findMatchingUser();

    assertNotNull(matched);
    assertEquals("Threshold gecmeyen user atlanmali; threshold gecen yedek secilmeli",
        "mutlu", matched.getUsername());
  }

  // ----------------------------------------------------------- Skill enabled

  @Test
  public void skillEnabled_samePriorityGroupsPooled_skillWeightAndLcdApplied() {
    Group ga = group(1L, "A");
    Group gb = group(2L, "B");
    CampaignGroup cgA = campaignGroup(ga, 5);
    CampaignGroup cgB = campaignGroup(gb, 5);
    when(campaign.getEnabledCampaignGroups()).thenReturn(list(cgA, cgB));
    when(campaign.getSkillEnabled()).thenReturn(true);

    Skill tahsilat = new Skill("Tahsilat");
    Set<Skill> required = new HashSet<>(Collections.singletonList(tahsilat));
    when(call.getSkills()).thenReturn(required);

    User mutluA = user(701L, "mutluA", 60);
    User hasanB = user(702L, "hasanB", 60);

    stubQueue(ga, mutluA);
    stubQueue(gb, hasanB);

    // findUsersWithMatchingSkills stub: havuza giren her iki user da skill'i tasiyor;
    // donen TreeSet'i hasanB once siralayalim (skill weight + LCD comparator'unu simule eder).
    Set<User> matchedSet = new LinkedHashSet<>();
    matchedSet.add(hasanB);
    matchedSet.add(mutluA);
    when(acdEngine.findUsersWithMatchingSkills(any(), org.mockito.ArgumentMatchers.eq(required)))
        .thenReturn(matchedSet);

    DefaultFindMatchingUserStrategy strategy = strategy(false);

    User matched = strategy.findMatchingUser();

    assertNotNull(matched);
    assertEquals("Skill versiyonunda cross-group havuz mevcut TreeSet siralamasiyla cozulur",
        "hasanB", matched.getUsername());
  }

  @Test
  public void skillEnabled_userWithoutMatchingSkillFiltered() {
    Group g = group(1L, "G1");
    CampaignGroup cg = campaignGroup(g, 5);
    when(campaign.getEnabledCampaignGroups()).thenReturn(list(cg));
    when(campaign.getSkillEnabled()).thenReturn(true);

    Skill ingilizce = new Skill("Ingilizce");
    Set<Skill> required = new HashSet<>(Collections.singletonList(ingilizce));
    when(call.getSkills()).thenReturn(required);

    User mutluTr = user(801L, "mutluTr", 60);   // Sadece TR konusur, skill match yok
    stubQueue(g, mutluTr);

    // findUsersWithMatchingSkills bos donsun (skill match yok)
    when(acdEngine.findUsersWithMatchingSkills(any(), org.mockito.ArgumentMatchers.eq(required)))
        .thenReturn(new HashSet<>());

    DefaultFindMatchingUserStrategy strategy = strategy(false);

    User matched = strategy.findMatchingUser();

    assertNull("Skill match olmayan user secilmemeli", matched);
  }

  // ----------------------------------------------------------- Hic grup yoksa

  @Test
  public void noEnabledGroups_returnsNull() {
    when(campaign.getEnabledCampaignGroups()).thenReturn(Collections.emptyList());

    DefaultFindMatchingUserStrategy strategy = strategy(false);

    assertNull(strategy.findMatchingUser());
  }

  // =============================================================== Helper'lar

  private DefaultFindMatchingUserStrategy strategy(boolean useOnlyIdleUsers) {
    return new DefaultFindMatchingUserStrategy(acdEngine, call, useOnlyIdleUsers);
  }

  private Group group(long id, String name) {
    Group g = new Group();
    g.setId(id);
    g.setName(name);
    g.setQueues(new HashSet<>());
    return g;
  }

  private CampaignGroup campaignGroup(Group group, int priority) {
    CampaignGroup cg = new CampaignGroup();
    cg.setGroup(group);
    cg.setPriority(priority);
    return cg;
  }

  private User user(long id, String username, int secondsInStatus) {
    User u = new User();
    u.setId(id);
    u.setUsername(username);
    u.setStatusSetDate(new java.sql.Date(System.currentTimeMillis() - secondsInStatus * 1000L));
    return u;
  }

  private void setLastCallEnd(User u, Date date) {
    lastCallEndDates.put(u, date);
  }

  private Date secondsAgo(int seconds) {
    return new Date(System.currentTimeMillis() - seconds * 1000L);
  }

  private void stubQueue(Group group, User... users) {
    Queue<User> queue = new LinkedList<>(Arrays.asList(users));
    // useOnlyIdleUsers'a göre iki yoldan birinden çağrılır; ikisini de lenient stub'la.
    lenient().when(acdEngine.getAvailableUsersQueueInGroup(group)).thenReturn(queue);
    lenient().when(acdEngine.getIdleUsersInGroup(group)).thenReturn(queue);
  }

  @SafeVarargs
  private final <T> List<T> list(T... items) {
    return new ArrayList<>(Arrays.asList(items));
  }

  // Ordered set kullanılan testte deterministik iteration için
  private static final class LinkedHashSet<E> extends java.util.LinkedHashSet<E> {
    private static final long serialVersionUID = 1L;
  }
}
