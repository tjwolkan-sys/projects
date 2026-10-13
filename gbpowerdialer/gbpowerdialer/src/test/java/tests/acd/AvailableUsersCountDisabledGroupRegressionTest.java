package tests.acd;

import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.UserHolder;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.acd.UserFindingContextBuilder;
import com.powerdialer.engine.boost.DynamicBoostManager;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignGroup;
import com.powerdialer.model.Group;
import com.powerdialer.model.Skill;
import com.powerdialer.model.Status;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FIX-GLB90060295 (22.09.2026) — dialing boost originate bütçesinin tabanı olan
 * {@link ACDEngine#getCampaignAvailableUsersCount(Campaign)} yalnız AKTİF (enabled) kampanya
 * gruplarının agent'larını saymalı; skill açık kampanyada skill filtresi uygulanmalı.
 *
 * <p><b>Canlı vaka (Turkcell, camp 164, 22.09.2026 09:00-09:54):</b> GUVENCELL_GENEL grubu
 * 08:35:56'da pasife alındı ama bütçe tabanı pasif grubun Available agent'larını saymaya devam
 * etti. Aday seçimi yalnız aktif grubu gördüğü için floor(0,65 × (1 aktif + 6 pasif)) = 4-5 çağrı
 * tek Available agent'a yığıldı; 535 dial'da 46 abandon. Ekrandaki Avail
 * ({@code countCampaignAvailableUsersNow}) aktif grubu sayarken bütçe tabanı saymıyordu.</p>
 *
 * <p>Karakterizasyon testleri eski davranışı korur; regresyon testleri düzeltmeden önce RED,
 * sonra GREEN olmalıdır.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AvailableUsersCountDisabledGroupRegressionTest {

  private static final float BOOST_065 = 0.65f;

  @InjectMocks
  private ACDEngine acdEngine;

  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;
  @Mock
  private UserHolder userHolder;
  @Mock
  private DynamicBoostManager dynamicBoostManager;
  @Mock
  private RedisUtil redisUtil;

  private Group aktifGrup;
  private Group pasifGrup;
  private Campaign campaign;
  private long nextUserId = 9000L;

  @Before
  public void init() {
    aktifGrup = group(10135L, "GUVENCELL_WIYO_SAGLIKLI_YASAM");
    pasifGrup = group(10173L, "GUVENCELL_GENEL");

    campaign = new Campaign();
    campaign.setId(164L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setDialingBoost(BOOST_065);
    campaign.setAvailableThreshold(0);
    campaign.setSkillEnabled(false);
    campaign.setAppointerReset(false);
    campaign.setCampaignGroups(new ArrayList<>(Arrays.asList(
        campaignGroup(aktifGrup, true),
        campaignGroup(pasifGrup, false))));

    Whitebox.setInternalState(acdEngine, "LOG", mock(LoggerDecorator.class));
    Whitebox.setInternalState(acdEngine, "callUpToDateKeeper", callUpToDateKeeper);
    Whitebox.setInternalState(acdEngine, "userHolder", userHolder);
    Whitebox.setInternalState(acdEngine, "dynamicBoostManager", dynamicBoostManager);
    Whitebox.setInternalState(acdEngine, "userFindingContextBuilder",
        new UserFindingContextBuilder(redisUtil, acdEngine));

    // Varsayılan: keeper'da kayıtlı çağrı yok.
    when(callUpToDateKeeper.getCallsByUserGroup(any(Group.class)))
        .thenReturn(Collections.<User, List<Call>>emptyMap());
    when(callUpToDateKeeper.getAllRegisteredUsers()).thenReturn(Collections.<User>emptyList());
  }

  // ================================================================ karakterizasyon

  /** Tek aktif grup, iki Available agent: eski ve yeni davranış aynı, sayı 2. */
  @Test
  public void tekAktifGrup_ikiMusaitAgent_ikiSayilir() {
    idleAvailable(aktifGrup);
    idleAvailable(aktifGrup);

    assertEquals(2L, acdEngine.getCampaignAvailableUsersCount(campaign));
    assertEquals("Yan etki korunur: campaign.availableUsersCount set edilir",
        2L, campaign.getAvailableUsersCount());
  }

  /** Keeper'da çalan çağrısı olan Available agent idle kuyruğunda da olsa BİR kez sayılır. */
  @Test
  public void keeperVeIdleKuyrugundakiAyniAgent_birKezSayilir() {
    User agent = idleAvailable(aktifGrup);
    registerNotBridgedCalls(aktifGrup, agent, 1);

    assertEquals(1L, acdEngine.getCampaignAvailableUsersCount(campaign));
  }

  // ================================================================ regresyon: pasif grup

  /** ASIL BUG: pasif grubun Available agent'ları bütçe tabanına GİRMEMELİ (bugün 7 sayıyor). */
  @Test
  public void pasifGrubunMusaitAgentlari_butceTabaninaSayilmaz() {
    idleAvailable(aktifGrup);
    for (int i = 0; i < 6; i++) {
      idleAvailable(pasifGrup);
    }

    assertEquals("Yalnız aktif gruptaki 1 agent sayılmalı",
        1L, acdEngine.getCampaignAvailableUsersCount(campaign));
  }

  /** Bütçe tabanı ekrandaki Avail ({@code countCampaignAvailableUsersNow}) ile aynı sayıyı vermeli. */
  @Test
  public void butceTabani_ekrandakiAvailIleAynidir() {
    idleAvailable(aktifGrup);
    idleAvailable(aktifGrup);
    for (int i = 0; i < 6; i++) {
      idleAvailable(pasifGrup);
    }

    assertEquals(acdEngine.countCampaignAvailableUsersNow(campaign),
        acdEngine.getCampaignAvailableUsersCount(campaign));
    assertEquals(2L, acdEngine.getCampaignAvailableUsersCount(campaign));
  }

  /**
   * Canlı senaryo uçtan uca: Progressive Released, boost 0,65, aktif grupta 1 Available agent,
   * pasif grupta 6 Available agent. floor(1 × 0,65) = 0 ⟹ hiç çağrı çıkmamalı. Bugün
   * floor(7 × 0,65) = 4 ile tek agent'a 4 çağrı yığılıyor.
   */
  @Test
  public void progressiveReleased_boost065_pasifGrupTekAgentaCagriYigmaz() {
    idleAvailable(aktifGrup);
    for (int i = 0; i < 6; i++) {
      idleAvailable(pasifGrup);
    }
    Call call = call();

    acdEngine.processACD(call);

    assertNull("floor(1 avail × 0,65) = 0: bütçe yok, agent atanmamalı", call.getUser());
  }

  /**
   * Aktif grupta 2 Available agent, boost 0,65 ⟹ bütçe floor(1,3) = 1 çağrı. İlk çağrı agent alır;
   * uçuşta 1 çalan çağrı varken ikinci çağrı almaz. Pasif gruptaki 6 agent bütçeyi genişletmemeli.
   */
  @Test
  public void progressiveReleased_boost065_ikinciCagriButceyeTakilir() {
    User birinci = idleAvailable(aktifGrup);
    idleAvailable(aktifGrup);
    for (int i = 0; i < 6; i++) {
      idleAvailable(pasifGrup);
    }

    Call ilkCagri = call();
    acdEngine.processACD(ilkCagri);
    assertNotNull("Bütçe 1: ilk çağrı agent almalı", ilkCagri.getUser());

    // İlk çağrı çalıyor (answerDate null) ve keeper'a kayıtlı.
    registerNotBridgedCalls(aktifGrup, birinci, 1);
    Call ikinciCagri = call();
    acdEngine.processACD(ikinciCagri);

    assertNull("Uçuşta 1 çalan çağrı = bütçe dolu; pasif grup bütçeyi genişletmemeli",
        ikinciCagri.getUser());
  }

  /**
   * boost > 1 yolu ({@code isDialingBoostNotReached}) aynı tabanı kullanır: Progressive Reserved,
   * boost 2, aktif grupta 1 Available agent (idle değil, 2 çalan çağrısı keeper'da), pasif grupta
   * 6 Available agent. Tavan 1 × 2 = 2; uçuşta 2 çağrı varken üçüncü çağrı agent almamalı.
   * Bugün tavan 7 × 2 = 14 sayılıp aynı agent'a üçüncü çağrı bağlanıyor.
   */
  @Test
  public void boostBuyukBir_pasifGrupNotBridgedTavaniniSisirmez() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED);
    campaign.setDialingBoost(2f);
    User mesgulAmaAvailable = availableAgent(aktifGrup);   // idle kuyruğunda DEĞİL
    registerNotBridgedCalls(aktifGrup, mesgulAmaAvailable, 2);
    for (int i = 0; i < 6; i++) {
      idleAvailable(pasifGrup);
    }
    Call call = call();

    acdEngine.processACD(call);

    assertNull("Tavan floor/ham 1 × 2 = 2 dolu: üçüncü çağrı atanmamalı", call.getUser());
  }

  // ================================================================ regresyon: skill filtresi

  /** Skill açık kampanyada skill'i eksik Available agent bütçe tabanına sayılmaz (ekranla aynı). */
  @Test
  public void skillAcikKampanya_skilliEksikAgentSayilmaz() {
    Skill skill = new Skill();
    skill.setId(1L);
    campaign.setSkillEnabled(true);
    campaign.setSkills(new HashSet<>(Collections.singletonList(skill)));

    User skilli = idleAvailable(aktifGrup);
    Map<Skill, Long> skiller = new HashMap<>();
    skiller.put(skill, 1L);
    skilli.setSkillWeightPair(skiller);
    User skilsiz = idleAvailable(aktifGrup);
    skilsiz.setSkillWeightPair(new HashMap<Skill, Long>());

    assertEquals(1L, acdEngine.getCampaignAvailableUsersCount(campaign));
    assertEquals(acdEngine.countCampaignAvailableUsersNow(campaign),
        acdEngine.getCampaignAvailableUsersCount(campaign));
  }

  /** Skill açık ama kampanyanın skill seti boş: skill şartı yok sayılır, NPE atılmaz. */
  @Test
  public void skillAcikAmaSkillSetiBos_tumMusaitlerSayilir() {
    campaign.setSkillEnabled(true);
    campaign.setSkills(new HashSet<Skill>());
    idleAvailable(aktifGrup);
    idleAvailable(aktifGrup);

    assertEquals(2L, acdEngine.getCampaignAvailableUsersCount(campaign));
  }

  // ================================================================ yardımcılar

  private Group group(long id, String name) {
    Group g = new Group();
    g.setId(id);
    g.setName(name);
    g.setQueues(new HashSet<>());
    return g;
  }

  private CampaignGroup campaignGroup(Group group, boolean enabled) {
    CampaignGroup cg = new CampaignGroup();
    cg.setId(group.getId());
    cg.setGroup(group);
    cg.setCampaign(campaign);
    cg.setPriority(0);
    cg.setEnabled(enabled);
    return cg;
  }

  /** Available statülü, grubu verilen online agent (userHolder canonical olarak tanınır). */
  private User availableAgent(Group group) {
    User u = new User();
    u.setId(nextUserId++);
    u.setUsername(String.valueOf(80000 + u.getId()));
    u.setGroup(group);
    Status available = new Status();
    available.setId(Constants.SYSTEM_STATUS.AVAILABLE.getId());
    available.setAvailableForDialerCalls(true);
    u.setStatus(available);
    u.setStatusSetDate(new java.sql.Date(System.currentTimeMillis() - 120_000L));
    when(userHolder.getUser(u.getId())).thenReturn(u);
    return u;
  }

  /** Available agent'ı grubunun idle kuyruğuna ekler. */
  private User idleAvailable(Group group) {
    User u = availableAgent(group);
    acdEngine.addToIdleUsers(u);
    return u;
  }

  /** Agent'a {@code count} adet çalan (answerDate null) çağrıyı keeper'a kayıtlı gösterir. */
  private void registerNotBridgedCalls(Group group, User agent, int count) {
    List<Call> calls = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Call c = new Call();
      c.setCampaign(campaign);
      c.setUser(agent);
      calls.add(c);
    }
    Map<User, List<Call>> byUser = new HashMap<>();
    byUser.put(agent, calls);
    when(callUpToDateKeeper.getCallsByUserGroup(group)).thenReturn(byUser);
    when(callUpToDateKeeper.getAllRegisteredUsers()).thenReturn(Collections.singletonList(agent));
    when(callUpToDateKeeper.getUserCalls(agent)).thenReturn(calls);
  }

  private Call call() {
    Call c = new Call();
    c.setCampaign(campaign);
    return c;
  }
}
