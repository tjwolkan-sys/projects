package tests.acd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.hibernate.LazyInitializationException;
import org.junit.Before;
import org.junit.Test;

import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Group;
import com.powerdialer.model.User;

/**
 * 10.08.2026 uretim kaydi: {@code CallUpToDateKeeper.getCallsByUserGroup} icindeki
 * {@code user.getGroup().equals(userGroup)} satiri, DB'den yuklenmis (detached) bir user'in
 * initialize edilmemis lazy {@code Group} proxy'sine denk geldiginde
 * {@code LazyInitializationException: could not initialize proxy [Group#10082] - no Session}
 * atiyordu.
 *
 * <p>Bu keeper ACD hot path'inde her originate tick'inde ve her FastAGI dial script'inde
 * tarandigi icin TEK bozuk kayit tum agent secimini dusuruyordu. Grup kiyasi artik identifier
 * uzerinden yapilir: Hibernate proxy'de identifier getter'i initialize tetiklemez, equals/hashCode
 * tetikler.</p>
 */
public class CallsByUserGroupDetachedGroupProxyRegressionTest {

  private static final Long GROUP_ID = 10082L;

  /**
   * Detached ve initialize edilmemis bir Hibernate lazy proxy'sinin davranisini modeller:
   * identifier getter'i (getId) proxy'yi initialize etmez ve calisir; equals/hashCode ise proxy'yi
   * initialize etmeye calisir ve session olmadigi icin LazyInitializationException atar.
   */
  private static final class DeadGroupProxy extends Group {

    private static final long serialVersionUID = 1L;

    private DeadGroupProxy(Long id) {
      super(id);
    }

    @Override
    public boolean equals(Object o) {
      throw new LazyInitializationException("could not initialize proxy [Group#" + getId() + "] - no Session");
    }

    @Override
    public int hashCode() {
      throw new LazyInitializationException("could not initialize proxy [Group#" + getId() + "] - no Session");
    }
  }

  private CallUpToDateKeeper callUpToDateKeeper;
  private Campaign campaign;
  private Group requestedGroup;

  @Before
  public void setUp() {
    // getCallsByUserGroup log kullanmadigi icin @PostConstruct init() gerekmez.
    callUpToDateKeeper = new CallUpToDateKeeper();
    campaign = new Campaign();
    campaign.setId(1L);
    requestedGroup = new Group(GROUP_ID);
  }

  /** Olu proxy tasiyan agent taramayi dusurmemeli ve dogru grupta eslesmeye devam etmeli. */
  @Test
  public void detachedGroupProxyIsMatchedByIdWithoutInitialization() {
    User agent = agent(7L, new DeadGroupProxy(GROUP_ID));
    Call call = registeredCall(agent);

    Map<User, List<Call>> result = callUpToDateKeeper.getCallsByUserGroup(requestedGroup);

    assertEquals(1, result.size());
    assertEquals(1, result.get(agent).size());
    assertEquals(call, result.get(agent).get(0));
  }

  /** Baska gruba ait olu proxy eslesmemeli; id kiyasi grup ayrimini korumali. */
  @Test
  public void detachedGroupProxyOfAnotherGroupIsNotMatched() {
    registeredCall(agent(8L, new DeadGroupProxy(99999L)));

    assertTrue(callUpToDateKeeper.getCallsByUserGroup(requestedGroup).isEmpty());
  }

  /**
   * Grubu olmayan agent (ACDEngine'de de ayrica ele alinan durum) taramayi NullPointerException ile
   * dusurmemeli.
   */
  @Test
  public void agentWithoutGroupIsSkippedWithoutError() {
    registeredCall(agent(9L, null));

    assertTrue(callUpToDateKeeper.getCallsByUserGroup(requestedGroup).isEmpty());
  }

  /** Normal (proxy olmayan) grup davranisi aynen korunur. */
  @Test
  public void plainGroupIsStillMatchedById() {
    User agent = agent(10L, new Group(GROUP_ID));
    registeredCall(agent);

    assertEquals(1, callUpToDateKeeper.getCallsByUserGroup(requestedGroup).get(agent).size());
  }

  private User agent(Long id, Group group) {
    User user = new User();
    user.setId(id);
    user.setGroup(group);
    return user;
  }

  private Call registeredCall(User agent) {
    Call call = new Call();
    call.setCampaign(campaign);
    call.setUser(agent);
    callUpToDateKeeper.registerCall(call);
    return call;
  }
}
