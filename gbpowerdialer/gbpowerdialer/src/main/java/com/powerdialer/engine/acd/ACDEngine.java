package com.powerdialer.engine.acd;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ComparisonChain;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.UserHolder;
import com.powerdialer.engine.boost.DynamicBoostManager;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignGroup;
import com.powerdialer.model.Group;
import com.powerdialer.model.Skill;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Automated call distribution engine. Holds users and matches appropriate user
 * for call.
 */
@Component
@Lazy(false)
public class ACDEngine {

  private LoggerDecorator LOG;
  private Logger logger = LoggerFactory.getLogger(this.getClass());
  private UserFindingContextBuilder userFindingContextBuilder;
  private final Map<Group, Queue<User>> idleUsers = new ConcurrentHashMap<>();
  private final Map<Group, Queue<User>> systemActiveUsers = new ConcurrentHashMap<>();
  private final Cache<Long, Date> lastUsersCallEndDate = CacheBuilder.newBuilder()
      .expireAfterWrite(12, TimeUnit.HOURS)
      .build();

  @Autowired
  private CallUpToDateKeeper callUpToDateKeeper;

  @Autowired
  private LoggerDecorator loggerDecorator;

  @Autowired
  private UserHolder userHolder;

  @Autowired
  private RedisUtil redisUtil;

  @Autowired
  private DynamicBoostManager dynamicBoostManager;

  @PostConstruct
  public void init() {
    LOG = loggerDecorator.getLogger(ACDEngine.class);
    userFindingContextBuilder = new UserFindingContextBuilder(redisUtil, this);
  }

  private Comparator<User> getComparator(Collection<Skill> skills) {
    // Skill weight DESC dominant; esit yetkinlikte tie-breaker olarak gercek avail
    // suresi (statusSetDate) kullanilir. Eski UserLastCallDateComparator lastCallEndDate
    // uzerinden tie-breaker yapiyordu; cagri bittikten sonraki wrapup/break surelerini
    // saymadigi icin break veya wrapup'tan az once donen agent yanlis "uzun bekleyen"
    // gibi gorunup secimde one cikiyordu. statusSetDate AVAILABLE status'a girdigi an
    // oldugu icin gercek avail baslangici sinyalidir; cross-group LCD sort ile ayni
    // veriyi kullanir, dagitim tutarliligi saglanir.
    return (o1, o2) -> ComparisonChain.start()
        .compare(o1, o2, new UserSkillWeightComparator(skills))
        .compare(o1, o2, new UserAvailTimeComparator())
        .result();
  }

  /**
   * Get idle users in group
   * @param group
   * @return users in group
   */
  public Queue<User> getIdleUsersInGroup(Group group) {
    return idleUsers.get(group);
  }

  /**
   * Get queue of users (for given group) with calls that created but not
   * bridged between user and client. Note that result queue may do not have all
   * users with not bridged calls
   * @param group
   * @return
   */
  public Queue<User> getAvailableUsersQueueInGroup(Group group) {
    Map<User, Integer> availableUserToCallsCount = getAvailableUserToCallsCount(group);
    LinkedList<User> result = new LinkedList<>();
    Integer minCallsNumber = availableUserToCallsCount.values().stream()
        .min(Comparator.naturalOrder())
        .orElse(null);
    if (minCallsNumber != null) {
      availableUserToCallsCount.entrySet().stream()
          .filter(e -> e.getValue().equals(minCallsNumber))
          .forEach(e -> result.add(e.getKey()));
      // Avail wait siralamasi: agent'in mevcut status'a girdigi an (statusSetDate)
      // uzerinden siralanir. Pool zaten AVAILABLE status agentlari ile filtrelenmis
      // oldugundan statusSetDate = avail oluş anı; wrapup/break geri donus sonrasi
      // dogru avail suresini verir. lastCallEndDate kullanan UserLastCallDateComparator
      // wrapup/break suresini saymadigi icin yanlis "uzun bekleyen" yanilticisi
      // veriyordu; statusSetDate gercek sinyaldir. Eski comparator paralel mekanizmalarda
      // (idleUsers/systemActiveUsers PriorityBlockingQueue, skill TreeSet zinciri,
      // trySearchLessOccupiedUser swap karari) korunmustur; sadece bizim cross-group
      // pool ve API endpoint donus sirasi degisir.
      result.sort(new UserAvailTimeComparator());
    }
    return result;
  }

  /**
   * Get user to calls map who can accept call at the moment. User can be
   * available for call, but have more than 1 originated (or under LSC processing)
   * call while dialing boost used
   * @param group
   * @return
   */
  public Map<User, Integer> getAvailableUserToCallsCount(Group group) {
    Map<User, Integer> availableUsersToCallsCount = new HashMap<>();
    Map<User, List<Call>> userToCalls = callUpToDateKeeper.getCallsByUserGroup(group);
    for (Map.Entry<User, List<Call>> entry : userToCalls.entrySet()) {
      User user = entry.getKey();
      if (!user.isDialInProcess() && user.getStatus() != null && user.getStatus().isAvailableForDialerCalls()) {
        availableUsersToCallsCount.put(user, entry.getValue().size());
      }
    }
    Queue<User> idleUsers = this.idleUsers.get(group);
    if (!CollectionUtils.isEmpty(idleUsers)) {
      for (User user : idleUsers) {
        // T3 — idle kuyrugundan gelen adaya da YUKARIDAKI ILE AYNI musaitlik kapisi uygulanir.
        //
        // Gerekce: bu blok 2018'de (Bug #10586) eklendiginde agent originate aninda kuyruktan
        // CIKARILIYORDU; "kuyrukta olmak" = "gercekten bosta" demekti ve statu kontrolu gereksizdi.
        // FIX-GLB90060295 (10.08.2026) starvation'i cozmek icin GAMMA/ALPHA/PROGRESSIVE_RELEASED'de
        // originate-fazi cikarmayi atlayinca o varsayim bozuldu: cagriya baglanmis (Incall) agent
        // kuyrukta kalabiliyor.
        //
        // Etkisi iki katli: (1) mesgul agent aday havuzuna giriyor, (2) put(user, 0) ile "0 cagrisi
        // var" isaretlendigi icin minCallsNumber filtresinde ONE CIKIYOR ve UserAvailTimeComparator
        // "en uzun suredir ayni statude" mantigiyla onu basa aliyor -> uzun konusan agent
        // odullendiriliyor. Olculen: abandon'larin %77-81'inde secilen agent ANSWER aninda Incall.
        //
        // Kontrol, satir ~151'deki ile BIREBIR AYNIDIR (yeni mantik degil, eksik kalan kapinin
        // tamamlanmasi). getCampaignIdleAndAvailableUsers de ayni suzgeci kullanir.
        if (!user.isDialInProcess() && user.getStatus() != null
            && user.getStatus().isAvailableForDialerCalls()) {
          availableUsersToCallsCount.put(user, 0);
        }
      }
    }
    return availableUsersToCallsCount;
  }

  /**
   * Get all idle users
   * @return all users
   */
  public Queue<User> getAllIdleUsers() {
    Queue<User> allIdleUsers = new ConcurrentLinkedQueue<>();
    for (Queue<User> users : idleUsers.values())
      allIdleUsers.addAll(users);
    return allIdleUsers;
  }

  /**
   * Add given user to active users
   * @param user
   */
  public void addToActiveUsers(User user) {

    Queue<User> users = systemActiveUsers.get(user.getGroup());
    if (users == null) {
      users = new PriorityBlockingQueue<>(5, new UserLastCallDateComparator(this));
      systemActiveUsers.put(user.getGroup(), users);
    }
    if (!users.contains(user)) {
      if (users.add(user)) {
        LOG.info("Avail user added: " + user);
      }
    }
  }


  /**
   * Add given user to idle users.
   *
   * <p>SORUN 4 (idle kapisi invariant'i): Idle kuyruguna yalniz o an GERCEKTEN avail olan agent
   * girer ve girdi her zaman CANONICAL (userHolder) nesnesidir. Karar, cagiranin elindeki User
   * uzerinden verilmez cunku o cogu zaman cagriya ilistirilmis/eski bir KOPYADIR (User.equals
   * id-bazli, kopyalar ayri nesneler): iptal/swap yollari statuye bakmadan geri ekleyip
   * Mola/Toplanti'daki agent'i secime sokuyor, yetim kopyalar da statu mesajlariyla
   * guncellenemeden idle'da bayat kaliyordu. Canonical eklendigi icin idle'daki girdi sonraki
   * statu mesajlarinda otomatik guncel kalir; agent gercekten avail oldugunda UserSubscriber
   * yolu onu zaten ekler (bu kapi ekleme kaybi yaratmaz, en fazla mesaj gecikmesi kadar erteler).
   * @param user
   */
  public void addToIdleUsers(User user) {
    User canonical = userHolder.getUser(user.getId());
    if (canonical == null) {
      LOG.warn("Can not add offline user to idle ones: " + user);
      return;
    }
    if (canonical.getStatus() == null || !canonical.getStatus().isAvailableForDialerCalls()
        || canonical.isDialInProcess()) {
      // isDialInProcess: okuma tarafiyla (getCampaignIdleAndAvailableUsers, getAvailableUserToCallsCount)
      // ayni "avail" tanimi kullanilir; dial'i suren agent idle'a geri sizamaz (code-review bulgusu).
      LOG.info("[IDLE-GATE] Idle user add skipped (canonical not available or dial in process): " + canonical);
      return;
    }
    if (canonical.getGroup() == null) {
      LOG.warn("Can not add user without of group to idle ones: " + canonical);
      return;
    }
    Queue<User> users = idleUsers.get(canonical.getGroup());
    if (users == null) {
      users = new PriorityBlockingQueue<>(5, new UserLastCallDateComparator(this));
      idleUsers.put(canonical.getGroup(), users);
    }
    if (!users.contains(canonical)) {
      if (users.add(canonical)) {
        LOG.info("Idle user added: " + canonical);
      }
    }
  }

  /**
   * Remove given user from ActiveUsers users. Users queues count is finite, so we do not
   * need to remove it.
   * @param user
   * @return true if user removed from idle user
   */
  public boolean removeActiveUsers(User user) {
    boolean result = false;
    if (user.getGroup() != null) {
      Queue<User> users = systemActiveUsers.get(user.getGroup());
      if (users != null) {
        result = users.remove(user);
      }
    }
    if (result) {
      LOG.info("System active user removed: " + user);
    }
    return result;
  }



  /**
   * Remove given user from idle users. Users queues count is finite, so we do not
   * need to remove it.
   * @param user
   * @return true if user removed from idle user
   */
  public boolean removeFromIdleUsers(User user) {
    boolean result = false;
    // Ekleme canonical grubun kuyruguna yapildigi icin cikarma da ayni kuyrugu hedeflemeli;
    // cagiranin elindeki kopyanin grubu bayat olabilir (grup degisikligi penceresi). Canonical
    // yoksa (logout akisi userHolder'dan silmis olabilir) kopyanin grubuna dusulur (eski davranis).
    User canonical = userHolder.getUser(user.getId());
    Group group = canonical != null && canonical.getGroup() != null ? canonical.getGroup() : user.getGroup();
    if (group != null) {
      Queue<User> users = idleUsers.get(group);
      if (users != null) {
        result = users.remove(user);
      }
    }
    if (result) {
      LOG.info("Idle user removed: " + user);
    }
    return result;
  }

  /**
   * Search user for call who has finish last call before initial user finished
   * @param call
   */
  public void trySearchLessOccupiedUser(Call call) {
    User initialUser = call.getUser();
    Date originalUserLastCallEndDate = getUserLastCallEndDate(initialUser);
    // Predictive-GAMMA: on atama (CallOriginator.processCall -> processACD) AYNEN korunur; burada yalnizca
    // tazeleme kapisi (lastCallEndDate != null) atlanir. Eski kapi, hic cagri tamamlamamis (yeni login /
    // vardiya basi / cache TTL dolmus) agent'ta lastCallEndDate=null olunca tazelemeyi HIC calistirmiyordu;
    // bu yuzden korlemesine on-atanan agent kullaniliyor, daha uzun bekleyen agent atlaniyordu. GAMMA'da
    // kapiyi kaldirinca acilista her zaman processACD calisir ve en-uzun-avail dogru secilir. shouldSwitchUser
    // swap karari (statusSetDate bazli) aynen korunur, boylece on-atanan ile yeni secilen dogru kiyaslanir.
    // Appointed/same-user (owner-bound) callback answer'da yeniden secime/swap/NOAVAIL'a GIRMEZ:
    // sahip originate'te rezerve edildi, call.getUser() zaten sahiptir -> dogrudan sahibe baglanir. Fresh/retry
    // aynen reselect olur (temsili -> en-uzun-avail).
    //
    // FIX-GLB90060295: kapsam GAMMA'dan "on atamanin sembolik oldugu tipler"e genisletildi; artik
    // PROGRESSIVE_RELEASED ve PREDICTIVE-ALPHA da dahildir. Gerekce: originate fazinda rezervasyon
    // kalkinca call.getUser() "rezerve edilmis agent" degil, yalnizca "originate aninda musaitti"
    // demektir; o agent answer aninda mesgul olabilir. Rezervasyonun devam ettigi tiplerde
    // (PROGRESSIVE_RESERVED, PREVIEW_*, PREDICTIVE-BETA) eski lastCallEndDate kapisi ve kor geri
    // donus davranisi BIT-ESIT korunur.
    boolean symbolicPreAssignment = OriginatePhase.isSymbolicPreAssignmentCampaign(call.getCampaign());
    boolean refreshSelection = symbolicPreAssignment
        ? !call.isOwnerBoundCallback()
        : originalUserLastCallEndDate != null;
    if (refreshSelection) {
      processACD(call);
      if (call.getUser() != null && !call.getUser().equals(initialUser)) {
        // initialUser (temsili tasiyici olabilir) o an avail DEGILSE statusSetDate kiyasina hic girme,
        // yeni secilen avail agent'a dogrudan gec. Aksi halde eski tarihli mesgul tasiyici kiyasi
        // "kazanip" geri atanir, dial-onu kontrol SKIP eder ve her cagrida bir tur bosa doner
        // (code-review bulgusu). Rezervasyonlu tiplerin davranisi bit-esit.
        boolean initialUnavailable = symbolicPreAssignment
            && (initialUser == null || initialUser.getStatus() == null
                || !initialUser.getStatus().isAvailableForDialerCalls());
        if (initialUnavailable || shouldSwitchUser(call, initialUser, call.getUser())) {
          if (initialUnavailable) {
            // Mesgul initial/tasiyici idle'a geri EKLENMEZ: kapi zaten reddederdi; bosuna cagri +
            // cift log ([ACD-SWAP] yeterli kanit) olusmasin. Avail initial (else dali) aynen doner.
            LOG.info("[ACD-SWAP] initial/tasiyici " + initialUser + " avail degil; answer aninda taze secilen "
                + call.getUser() + " atandi for " + call);
          } else {
            LOG.info("User is changed for " + call + " initial user is " + initialUser);
            addToIdleUsers(initialUser);
          }
        } else {
          addToIdleUsers(call.getUser());
          call.setUser(initialUser);
        }
      } else if (call.getUser() == null && symbolicPreAssignment) {
        // SORUN 3: processACD avail agent bulamadi. Ring sirasinda meşgullenmis (Incall/Mola)
        // olabilecek initialUser'a statu kontrolsuz geri donus, AGI'da tryLock/SKIP + triedUsers'a
        // kalici yakilma uretiyordu (initialUser sonradan bosalsa bile ayni cagriya bir daha
        // secilemiyordu). Sembolik on atamali tiplerde user null birakilir: processDialCommand
        // dongusu 100ms poll ile taze agent arar; initialUser gercekten bosalirsa idle'a doner ve
        // yeniden secilebilir. Rezervasyonlu tiplerin davranisi aynen korunur.
        LOG.info("[ACD-NOAVAIL] No available user on answer-time reselection for " + call
            + "; leaving user null (no blind fallback to initial user " + initialUser + ")");
      } else {
        call.setUser(initialUser);
      }
    }
  }

  private boolean shouldSwitchUser(Call call, User initialUser, User newUser) {
    if (call.getCampaign().getSkillEnabled() && !CollectionUtils.isEmpty(call.getSkills())) {
      Set<Skill> callSkills = call.getSkills();
      if (initialUser.getSummarySkillWeight(callSkills) != newUser.getSummarySkillWeight(callSkills)) {
        return false;
      }
    }
    // Swap karari "yeni user daha uzun avail bekledi mi" kriterine dayanir.
    // lastCallEndDate wrapup/break suresini saymadigi icin gercek avail suresinin
    // yanli sinyalidir; statusSetDate (current status'a girdigi an) AVAILABLE
    // agent icin gercek avail baslangicidir. Boylece swap karari ilk secimle
    // ayni gercege dayali kriter kullanir, tutarsizlik kalmaz.
    Date newUserStatusSetDate = newUser == null ? null : newUser.getStatusSetDate();
    Date initialUserStatusSetDate = initialUser == null ? null : initialUser.getStatusSetDate();
    if (newUserStatusSetDate == null) return true;
    if (initialUserStatusSetDate == null) return false;
    return initialUserStatusSetDate.after(newUserStatusSetDate);
  }

  /**
   * Apply ACD to call depending on campaign type
   * @param call
   */
  public void processACD(Call call) {
    StopWatch stopWatch = new StopWatch(call.getId());
    stopWatch.start();
    UserFindingContext userFindingContext = null;

    //If CallBack MT is decided by webphone Callback Type
    if (call.getCallType() == Constants.CALL_TYPES.CALLBACK
            && Boolean.TRUE.equals(call.getCampaign().getAllowOverwriteByWebphone())) {

      userFindingContext = processACDAccordingToCallbackType(call);

    } else if (call.getCallType() == Constants.CALL_TYPES.CALLBACK
              && call.getCampaign().getCallbackToAppointer()
              && call.getUserIdAppointedToCallback() != null) {

      User appointedUser = findAppointedUser(call);
      if (appointedUser != null) {
        userFindingContext = new UserFindingContext(call, () -> {
          removeFromIdleUsers(appointedUser);
          return appointedUser;
        });
      }

    } else {
      userFindingContext = resolveUserFindingContext(call);
    }

    if (userFindingContext != null) {
      userFindingContext.execute();
    } else {
      call.setUser(null);
    }

    stopWatch.stop();
    if (stopWatch.getTotalTimeMillis() > 100) {
      LOG.warn("ACD processing too slow for: " + call + " " + stopWatch.shortSummary());
    }
  }

  private UserFindingContext processACDAccordingToCallbackType(Call call) {
    UserFindingContext userFindingContext = null;
    // user_id NULL ise (ATTEMPT_COUNT stratejisi sahibe ulaşamayıp callback'i havuza/campaign-wide'a devretti)
    // artık "same user" yok → normal campaign-wide atamaya düş. Aksi halde findSameUser (artık olmayan) sahibi
    // bulamayıp userFindingContext null kalır ve callback hiçbir agent'a atanamadan takılır.
    if (Constants.CALLBACKTYPE.ASSIGN_TO_SAME_USER.equals(call.getCallbackType())
            && call.getUserId() != null
            && !call.isAppointerTimedOut()) {
      User sameUser = findSameUser(call);
      if (sameUser != null) {
       userFindingContext = new UserFindingContext(call, () -> {
          removeFromIdleUsers(sameUser);
          return sameUser;
        });
      }
    } else {
      userFindingContext = resolveUserFindingContext(call);
    }

    return userFindingContext;
  }

  private User findSameUser(Call call) {
    // Callback yalnız MT'si AVAILABLE (molada/ACW/dial-in-process değil) olan sahibe verilir.
    // getCampaignIdleAndAvailableUsers idle kuyruğunu isAvailableForDialerCalls()+!isDialInProcess()
    // ile süzer; idle kuyruğunda olup molada olan agent callback alamaz.
    for (User user : getCampaignIdleAndAvailableUsers(call.getCampaign()))
      if (user.getId().equals(call.getUserId()))
        return user;

    return null;
  }

  /**
   * Determines and builds the appropriate {@link UserFindingContext} for the given call,
   * based on campaign type, predictive strategy, and user availability.
   * <p>
   * This method handles only non-callback calls. It applies ACD logic by checking:
   * <ul>
   *   <li>If the campaign is predictive and uses the BETA strategy</li>
   *   <li>If there are idle users available for assignment</li>
   *   <li>If there are not-bridged users who can be used</li>
   * </ul>
   * If none of these conditions apply, it returns {@code null}.
   *
   * @param call the call instance to evaluate for user assignment
   * @return a suitable {@code UserFindingContext} if a matching condition is found; {@code null} otherwise
   */
  private UserFindingContext resolveUserFindingContext(Call call) {

    if (call.getCampaign().isPredictiveCampaign()
            && call.getCampaign().getPredictiveStrategy() != null
            && call.getCampaign().getPredictiveStrategy() == Constants.PREDICTIVE_STRATEGY.BETA) {
      return userFindingContextBuilder.buildUserFindingContext(call, false);

    } else if (call.getCampaign().isPredictiveCampaign()
            && call.getCampaign().getPredictiveStrategy() != null
            && call.getCampaign().getPredictiveStrategy() == Constants.PREDICTIVE_STRATEGY.GAMMA) {
      return userFindingContextBuilder.buildUserFindingContext(call, false);

    } else if (OriginatePhase.isWithoutReservation(call)) {
      // FIX-GLB90060295: sembolik on atamali tipler (PROGRESSIVE_RELEASED ve PREDICTIVE-ALPHA;
      // BETA/GAMMA yukaridaki dallarda zaten donuyor) originate fazinda agent'i idle havuzdan
      // CIKARMADIGI icin idle sayisi artik "kac cagri daha cikarabilirim" sinyali DEGILDIR; ring
      // boyunca dolu kalir. canUseIdleUsers kisayoli burada kullanilsaydi ilk eslesen dal olacagi
      // icin boost butcesi HIC degerlendirilmez ve over-dial tavani kalkardi (geriye yalniz
      // CPS/concurrentLines kalirdi). Bu yuzden tavan dogrudan boost'tan turetilen not-bridged
      // butcesidir. Not: eski kodda ayni butceye ancak idle havuz BOSALINCA ulasiliyordu; havuzu
      // bosaltan sey de rezervasyonun kendisiydi.
      return isOriginateBudgetAvailable(call.getCampaign())
              ? userFindingContextBuilder.buildUserFindingContext(call, false)
              : null;

    } else if (canUseIdleUsers(call)) {
      return userFindingContextBuilder.buildUserFindingContext(call, true);

    } else if (canUseNotBridgedUsers(call)) {
      return userFindingContextBuilder.buildUserFindingContext(call, false);
    }

    return null;
  }

  /**
   * Sembolik on atamali tiplerin (PROGRESSIVE_RELEASED, PREDICTIVE-ALPHA) originate fazi icin
   * pacing tavani.
   *
   * <p>Boost kaynagi {@link #getDialingBoost(Campaign)} ile ayni contract'i kullanir: PREDICTIVE
   * kampanyalarda {@code DynamicBoostManager}'in o anki dinamik degeri, diger tiplerde kampanyanin
   * {@code dialingBoost} alani. Boylece ALPHA'nin dinamik pacing'i otorite olmaya devam eder;
   * degisen tek sey, boost'un carpildigi "avail agent" sayisinin artik rezervasyonla
   * bosaltilmamis GERCEK deger olmasidir.</p>
   *
   * <p>Ayni anda calan (henuz bridge olmamis) cagri sayisi
   * {@code floor(availableUsers * dialingBoost)} degerini asamaz:</p>
   * <ul>
   *   <li>{@code boost = 1}, 5 agent -> en fazla 5 ucustaki cagri (agent basina 1). Rezervasyon
   *       kaldirilmadan onceki fiili tavanla ayni.</li>
   *   <li>{@code boost = 0,5}, 1 agent -> {@code floor(0,5) = 0}: hic cagri cikmaz. boost &lt; 1'in
   *       "bir kisim agent'i bos birak" semantigi korunur (eskiden bu, {@code canUseIdleUsers}
   *       icindeki {@code minIdleUsers} kapisiyla saglaniyordu; rezervasyon kalkinca idle sayisi o
   *       kapi icin de anlamli sinyal olmaktan cikti).</li>
   *   <li>{@code boost = 2}, 5 agent -> 10: operatorun bilincli over-dial tercihi.</li>
   * </ul>
   *
   * <p>{@link #isDialingBoostNotReached(Campaign)} burada KULLANILMAZ: o metot yalniz
   * {@code boost > 1} iken true dondurur, dolayisiyla {@code boost = 1} ile calisan kampanyalar hic
   * cagri cikaramazdi. Ayrica {@code floor} yerine ham carpimla kiyaslamak, kesirli boost'ta
   * (orn. 1,01 x 5 agent = 5,05) tavani bir cagri sisiriyordu; {@code floor} ile 1,01 durustce
   * "agent basina 1" anlamina gelir.</p>
   *
   * @param campaign originate edilecek cagrinin kampanyasi
   * @return butce musaitse {@code true}
   */
  private boolean isOriginateBudgetAvailable(Campaign campaign) {
    double maxNotBridgedCalls = Math.floor(getCampaignAvailableUsersCount(campaign) * resolveEffectiveBoost(campaign));
    return countCampaignNotBridgedCalls(campaign) < maxNotBridgedCalls;
  }

  /**
   * {@link #getDialingBoost(Campaign)} ile ayni kaynak, fakat null-safe: PREDICTIVE olmayan bir
   * kampanyada {@code dialingBoost} null ise ham unboxing NPE atardi. Bu metot originate hot
   * path'inde her cagri icin calistigindan null'a tolerans gerekir; null durumda notr deger (1)
   * kullanilir, yani "agent basina 1 ucustaki cagri".
   */
  private float resolveEffectiveBoost(Campaign campaign) {
    if (campaign.isPredictiveCampaign()) {
      return dynamicBoostManager.getBoostValue(campaign.getId());
    }
    Float configuredBoost = campaign.getDialingBoost();
    return configuredBoost == null ? 1f : configuredBoost;
  }

  private User findAppointedUser(Call call) {
    // Appointed callback yalnız sahibinin MT'si AVAILABLE iken atanır (molada/ACW/dial-in-process
    // ise atanmaz -> sahip uygun değil sayılır). idle kuyruğu availability garantisi vermediğinden
    // getCampaignIdleAndAvailableUsers (isAvailableForDialerCalls()+!isDialInProcess()) kullanılır.
    for (User user : getCampaignIdleAndAvailableUsers(call.getCampaign()))
      if (user.getId().equals(call.getUserIdAppointedToCallback()))
        return user;
    return null;
  }

  /**
   * Check is there not bridged users that can handle call in accordance to
   * dialing boost option
   * @param call
   * @return
   */
  private boolean canUseNotBridgedUsers(Call call) {
    return !call.getCampaign().isPreviewCampaign() && (call.isActive() || isDialingBoostNotReached(call.getCampaign()));
  }

  private boolean isDialingBoostNotReached(Campaign campaign) {
    float boost = getDialingBoost(campaign);
    if (boost > 1) {
      float maxNotBridgedCalls = getCampaignAvailableUsersCount(campaign) * boost;
      return countCampaignNotBridgedCalls(campaign) < maxNotBridgedCalls;
    }
    return false;

  }

  /**
   * Count all campaign's not bridged calls calls with given status
   * @param campaign
   * @return
   */
  private int countCampaignNotBridgedCalls(Campaign campaign) {
    return (int) getCampaignCalls(campaign).stream()
        .filter(c -> c.getAnswerDate() == null)
        .count();
  }

  private List<Call> getCampaignCalls(Campaign campaign) {
    List<Call> result = new ArrayList<>();
    for (User user : callUpToDateKeeper.getAllRegisteredUsers()) {
      if (isUserHasMatchingSkillsAndGroup(user, campaign)) {
        result.addAll(callUpToDateKeeper.getUserCalls(user));
      }
    }
    return result;
  }

  /**
   * Return all users for campaign. Campaign's users it is a user with matching
   * skill(s) and group(s)
   */
  public List<User> getCampaignUsers(Campaign campaign) {
    return Stream.concat(getCampaignIdleUsers(campaign).stream(), callUpToDateKeeper.getAllRegisteredUsers().stream().filter(u -> isUserHasMatchingSkillsAndGroup(u, campaign)))
        .distinct().collect(Collectors.toList());
  }



  public List<User> getCampaignAllActiveUsers(Campaign campaign) {
    return getCampaignAllActiveUsers(campaign, callUpToDateKeeper.getAllRegisteredUsers());
  }

  /**
   * Task 325654: {@link #getCampaignAllActiveUsers(Campaign)} ile birebir ayni sonucu
   * uretir, fakat registered-user snapshot'ini disaridan alir.
   *
   * <p>{@link CallUpToDateKeeper#getAllRegisteredUsers()} her cagrida tum aktif cagri
   * koleksiyonunu tarayip {@code HashSet} + {@code ArrayList} kurar ve sonuc kampanyadan
   * bagimsizdir. Snapshot'i disaridan almak, N kampanyalik bir islemde bu taramanin N
   * kez degil 1 kez yapilmasini saglar.</p>
   */
  public List<User> getCampaignAllActiveUsers(Campaign campaign, List<User> registeredUsersSnapshot) {
    return campaignAllActiveUsersStream(campaign, registeredUsersSnapshot).collect(Collectors.toList());
  }

  private Stream<User> campaignAllActiveUsersStream(Campaign campaign, List<User> registeredUsersSnapshot) {
    return Stream.concat(getAllLoginUsers(campaign).stream(),
            registeredUsersSnapshot.stream().filter(u -> isUserHasMatchingSkillsAndGroup(u, campaign)))
            .distinct();
  }

  /**
   * Task 325654: Birden fazla kampanyanin aktif agent listesini tek registered-user
   * snapshot'i uzerinden uretir. Her kampanya icin donen liste
   * {@link #getCampaignAllActiveUsers(Campaign)} sonucuyla birebir aynidir.
   */
  public Map<Long, List<User>> getCampaignAllActiveUsersByCampaignId(Collection<Campaign> campaigns) {
    Map<Long, List<User>> usersByCampaignId = new HashMap<>();
    if (CollectionUtils.isEmpty(campaigns)) {
      return usersByCampaignId;
    }
    List<User> registeredUsersSnapshot = callUpToDateKeeper.getAllRegisteredUsers();
    for (Campaign campaign : campaigns) {
      if (campaign == null || campaign.getId() == null) {
        continue;
      }
      usersByCampaignId.put(campaign.getId(), getCampaignAllActiveUsers(campaign, registeredUsersSnapshot));
    }
    return usersByCampaignId;
  }


  /**
   * Return true if campaign has specified online (user can be with any status)
   * user
   * @param campaign
   * @param userId
   * @return return true if user found otherwise return false
   */
  public boolean isCampaignHasOnlineUser(Campaign campaign, Long userId) {
    Stream<User> idleUsersStream = getCampaignIdleUsers(campaign).stream();
    Stream<User> campaignUsersStream = callUpToDateKeeper.getAllRegisteredUsers().stream().filter(u -> isUserHasMatchingSkillsAndGroup(u, campaign));
    return Stream.concat(idleUsersStream, campaignUsersStream).anyMatch(u -> u.getId().equals(userId));
  }

  /**
   * Returns a list of campaign users (users with matching skills and group) for the given campaign.
   *
   * @param campaign the campaign to filter users for
   * @return list of campaign users
   */
  public List<User> getOnlyCampaignUsers(Campaign campaign) {
      return callUpToDateKeeper.getAllRegisteredUsers().stream()
              .filter(u -> isUserHasMatchingSkillsAndGroup(u, campaign))
              .collect(Collectors.toList());
  }

  /**
   * Returns the count of campaign users (users with matching skills and group) for the given campaign.
   *
   * @param campaign the campaign to filter users for
   * @return count of campaign users
   */
  public int getOnlyCampaignUsersCount(Campaign campaign) {
      return getOnlyCampaignUsers(campaign).size();
  }

  /**
   * Return total number of users for campaign. Campaign's users it is a user
   * with matching skill(s) and group(s)
   */
  public int getCampaignUsersCount(Campaign campaign) {
    return getCampaignUsers(campaign).size();
  }

  /**
   * Get user count who can accept call at the moment.
   *
   * <p>FIX-GLB90060295 (22.09.2026): sayim yalniz AKTIF (enabled) kampanya gruplarini kapsar ve skill
   * acik kampanyada skill filtresi uygular; yani ekrandaki Avail ({@link #countCampaignAvailableUsersNow})
   * ile ayni "avail agent" tanimini okur ({@link #campaignAvailableUsersStream}). Eski hali pasif
   * (enabled=0, deleted=0) gruplari da sayiyordu. Bu sayi {@link #isOriginateBudgetAvailable} ve
   * {@link #isDialingBoostNotReached} butcelerinin tabani oldugu icin pasif gruptaki Available
   * agent'lar {@code floor(avail x boost)} tavanini sisiriyor, aday secimi ise yalniz aktif gruplari
   * gordugunden butcenin tamami tek aday agent'a yigiliyordu (Turkcell camp 164, 22.09.2026:
   * 535 dial / 46 abandon, tek agent'ta 9 eszamanli ringing).</p>
   *
   * <p>Yan etki korunur: {@link Campaign#setAvailableUsersCount(long)} (REST ve BETA okuyuculari).</p>
   * @param campaign
   * @return
   */
  public long getCampaignAvailableUsersCount(Campaign campaign) {
    long campaignAvailableUsersCount = campaignAvailableUsersStream(campaign).count();
    campaign.setAvailableUsersCount(campaignAvailableUsersCount);
    return campaignAvailableUsersCount;
  }

  /**
   * Get all login user for campaign
   * @param campaign
   * @return
   */
  public Set<User> getAllLoginUsers(Campaign campaign) {
    Stream<User> usersInGroupStream = campaign.getCampaignGroups().stream()
            .filter(CampaignGroup::isEnabled)
            .map(CampaignGroup::getGroup)
            .map(systemActiveUsers::get)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream);
    if (Boolean.TRUE.equals(campaign.getSkillEnabled())) {
      return findUsersWithMatchingSkillsStream(usersInGroupStream, campaign.getSkills());
    } else {
      return findUsersWithMatchingSkillsStream(usersInGroupStream, Collections.emptySet());
    }
  }


  /**
   * Get idle user for campaign
   * @param campaign
   * @return
   */
  public Set<User> getCampaignIdleUsers(Campaign campaign) {
    Stream<User> usersInGroupStream = campaign.getCampaignGroups().stream()
        .filter(CampaignGroup::isEnabled)
        .map(CampaignGroup::getGroup)
        .map(idleUsers::get)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream);
    if (Boolean.TRUE.equals(campaign.getSkillEnabled())) {
      return findUsersWithMatchingSkillsStream(usersInGroupStream, campaign.getSkills());
    } else {
      return findUsersWithMatchingSkillsStream(usersInGroupStream, Collections.emptySet());
    }
  }

  /**
   * Returns users who are both in the idle queue and currently available for dialer calls.
   */
  public Set<User> getCampaignIdleAndAvailableUsers(Campaign campaign) {
    Stream<User> usersStream = campaign.getCampaignGroups().stream()
            .filter(CampaignGroup::isEnabled)
            .map(CampaignGroup::getGroup)
            .map(idleUsers::get)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .filter(user ->
                    !user.isDialInProcess()
                            && user.getStatus() != null
                            && user.getStatus().isAvailableForDialerCalls()
            );

    if (Boolean.TRUE.equals(campaign.getSkillEnabled())) {
      return findUsersWithMatchingSkillsStream(usersStream, campaign.getSkills());
    } else {
      return usersStream.collect(Collectors.toSet());
    }
  }

  /**
   * Get idle user count for campaign
   * @param campaign
   * @return
   */
  public int getCampaignIdleUsersCount(Campaign campaign) {
    return getCampaignIdleUsers(campaign).size();
  }

  /**
   * Checks if we can offer <b>idle</b> user for call. The check is based
   * on dialing boost option for progressive campaigns and on just idle agents
   * existence for preview campaigns. If dialing boost < 1 then we must leave some
   * users undisturbed. For example: there is total 10 users for campaign and
   * dialing boost 0.9 therefore we must assign only 9 (10 * 0.9) users to calls
   * and 1 user will be free all the time for this campaign.
   * @return true if system can offer idle user for call otherwise false
   */
  private boolean canUseIdleUsers(Call call) {
    int campaignIdleUsers = getCampaignIdleUsersCount(call.getCampaign());
    if (campaignIdleUsers > 0) {
      if (call.getCampaign().isPreviewCampaign())
        return true;
      float dialingBoost = getDialingBoost(call.getCampaign());
      if (dialingBoost < 1) {
        double minIdleUsers = Math.ceil(getCampaignUsersCount(call.getCampaign()) * (1 - dialingBoost));
        return campaignIdleUsers > minIdleUsers;
      } else {
        return true;
      }
    } else {
      return false;
    }
  }

  /**
   * Find users having given skill set
   * @param skills
   * @return users matched skill set
   */
  public Set<User> findUsersWithMatchingSkills(Collection<User> usersInGroup, Set<Skill> skills) {
    return findUsersWithMatchingSkillsStream(usersInGroup.stream(), skills);
  }

  /**
   * Find users having given skill set
   * @param skills
   * @return users matched skill set
   */
  private Set<User> findUsersWithMatchingSkillsStream(Stream<User> usersInGroupStream, Set<Skill> skills) {
    if (!CollectionUtils.isEmpty(skills)) {
      usersInGroupStream = usersInGroupStream.filter(user -> user.getSkillWeightPair().keySet().containsAll(skills));
    }
    return usersInGroupStream.collect(Collectors.toCollection(() -> Collections.synchronizedSet(new TreeSet<>(getComparator(skills)))));
  }

  /**
   * Checks if a user matches both the group and skill requirements of a campaign.
   *
   * <p>This method performs two validation steps:
   * <ol>
   *   <li>Verifies if the user belongs to any enabled group associated with the campaign.</li>
   *   <li>Verifies if the user possesses all required skills for the campaign.</li>
   * </ol>
   *
   * <p><strong>Performance Notes:</strong>
   * <ul>
   *   <li>Uses {@code anyMatch()} for short-circuit evaluation to avoid unnecessary iterations.</li>
   *   <li>Avoids creating intermediate collections (e.g., {@code Set}) to reduce GC pressure.</li>
   *   <li>Implements early return logic to skip skill checks if group validation fails.</li>
   * </ul>
   *
   * @param user the user entity to be evaluated
   * @param campaign the campaign entity containing target criteria
   * @return {@code true} if the user matches both group and skill requirements; {@code false} otherwise
   */
  public boolean isUserHasMatchingSkillsAndGroup(User user, Campaign campaign) {
    // 1. Null safety checks for main entities
    if (user == null || campaign == null) {
      return false;
    }

    // 2. Group compatibility validation
    // Retrieves the list of campaign groups; returns false if null or empty
    List<CampaignGroup> campaignGroups = campaign.getCampaignGroups();
    if (campaignGroups == null || campaignGroups.isEmpty()) {
      return false;
    }

    // Uses stream with anyMatch() for efficient short-circuit evaluation.
    // Filters only enabled groups, maps to Group objects, and checks for a match.
    // Objects.equals() handles potential null values in user.getGroup() safely.
    boolean hasValidGroup = campaignGroups.stream()
            .filter(CampaignGroup::isEnabled)
            .map(CampaignGroup::getGroup)
            .anyMatch(group -> Objects.equals(group, user.getGroup()));

    // Early return: If group validation fails, no need to check skills
    if (!hasValidGroup) {
      return false;
    }

    // 3. Skill compatibility validation
    // Retrieves the user's skill map (Skill -> Weight)
    Map<Skill, Long> userSkillsMap = user.getSkillWeightPair();
    // Retrieves the list of required skills for the campaign
    Set<Skill> requiredSkills = campaign.getSkills();

    // If user has no skills defined, they cannot match any requirement
    if (userSkillsMap == null) {
      return false;
    }

    // If campaign has no specific skill requirements, user is considered compatible
    if (requiredSkills == null || requiredSkills.isEmpty()) {
      return true;
    }

    // Checks if the user's skill set contains ALL required skills
    // Note: containsAll() returns true if the required list is empty (handled above)
    return userSkillsMap.keySet().containsAll(requiredSkills);
  }

  /**
   * Just save last successful call date
   * @param user
   * @param date
   */
  public void updateUserLastCallEndDate(User user, Date date) {
    lastUsersCallEndDate.put(user.getId(), date);
    LOG.info("Last call end date updated for " + user + ": " + date);
  }

  public Date getUserLastCallEndDate(User user) {
    return (user == null) ? null : lastUsersCallEndDate.getIfPresent(user.getId());
  }

  private float getDialingBoost(Campaign campaign) {
    return (campaign.getType() == Constants.CAMPAIGN_TYPE.PREDICTIVE)
            ? dynamicBoostManager.getBoostValue(campaign.getId())
            : campaign.getDialingBoost();
  }

  /**
   * Pure count — campaign state mutate etmez.
   * Same semantics as CallConsumer.calculateOnlineAgentsSum().
   */
  public int countCampaignAllActiveUsers(Campaign campaign) {
    return countCampaignAllActiveUsers(campaign, callUpToDateKeeper.getAllRegisteredUsers());
  }

  /**
   * Task 325654: Ara {@code ArrayList} kurmadan sayar ve registered-user snapshot'ini
   * disaridan alir. {@code distinct().count()} degeri
   * {@code distinct().collect(toList()).size()} ile aynidir.
   */
  private int countCampaignAllActiveUsers(Campaign campaign, List<User> registeredUsersSnapshot) {
    return (int) campaignAllActiveUsersStream(campaign, registeredUsersSnapshot).count();
  }

  /**
   * Task 325654: N kampanyanin aktif agent sayisini tek registered-user snapshot'i
   * uzerinden hesaplar. Her deger {@link #countCampaignAllActiveUsers(Campaign)} ile
   * birebir aynidir.
   */
  public Map<Long, Integer> countCampaignAllActiveUsersByCampaignId(Collection<Campaign> campaigns) {
    Map<Long, Integer> countsByCampaignId = new HashMap<>();
    if (CollectionUtils.isEmpty(campaigns)) {
      return countsByCampaignId;
    }
    List<User> registeredUsersSnapshot = callUpToDateKeeper.getAllRegisteredUsers();
    for (Campaign campaign : campaigns) {
      if (campaign == null || campaign.getId() == null) {
        continue;
      }
      countsByCampaignId.put(campaign.getId(), countCampaignAllActiveUsers(campaign, registeredUsersSnapshot));
    }
    return countsByCampaignId;
  }

  /**
   * Pure count — idle users only.
   */
  public int countCampaignIdleUsers(Campaign campaign) {
    return getCampaignIdleUsersCount(campaign);
  }

  /**
   * Pure count — available users now (idle + in-call but available).
   * Does NOT mutate campaign.setAvailableUsersCount().
   */
  public int countCampaignAvailableUsersNow(Campaign campaign) {
    return (int) campaignAvailableUsersStream(campaign).count();
  }

  /**
   * Tek "avail agent" tanimi: aktif (enabled) kampanya gruplarindaki,
   * {@link #getAvailableUserToCallsCount} kapisini gecen (Available statu, dial surmuyor)
   * kullanicilar; skill acik kampanyada kampanyanin skill setini tamamen tasiyanlar; kullanici
   * bazinda tekil. Hem ekran sayisi ({@link #countCampaignAvailableUsersNow}) hem originate butcesi
   * ({@link #getCampaignAvailableUsersCount}) bunu okur, boylece iki sayi birbirinden sapamaz.
   *
   * <p>Skill seti bos veya null ise skill sarti yok sayilir ({@link #isUserHasMatchingSkillsAndGroup}
   * ile ayni yorum); {@code skillWeightPair} null olan kullanici skill sartini gecemez (NPE yerine
   * elenir). Hot path notu: cagri basina calisir; ek IO/lock yok, yalniz kucuk bir distinct seti.</p>
   */
  private Stream<User> campaignAvailableUsersStream(Campaign campaign) {
    Set<Skill> requiredSkills = campaign.getSkills();
    boolean filterBySkills = Boolean.TRUE.equals(campaign.getSkillEnabled())
        && !CollectionUtils.isEmpty(requiredSkills);
    return campaign.getCampaignGroups().stream()
        .filter(CampaignGroup::isEnabled)
        .map(CampaignGroup::getGroup)
        .map(this::getAvailableUserToCallsCount)
        .map(Map::keySet)
        .flatMap(Collection::stream)
        .filter(user -> !filterBySkills || hasAllSkills(user, requiredSkills))
        .distinct();
  }

  private static boolean hasAllSkills(User user, Set<Skill> requiredSkills) {
    Map<Skill, Long> userSkills = user.getSkillWeightPair();
    return userSkills != null && userSkills.keySet().containsAll(requiredSkills);
  }

  /**
   * Returns count of idle users who are available for dialer calls
   * for the given campaign (skill + group matched).
   * Source: idleUsers queue — guaranteed available by handleStatusChange()
   */
  public int getAvailAgents(Campaign campaign) {
    return countCampaignAvailableUsersNow(campaign);
  }

  /**
   * SORUN 2 / 1B (GAMMA): avail agent yokken originate'i tasiyacak TEMSILI agent dondurur.
   *
   * <p>GAMMA'da on atama semboliktir (gercek secim musteri actiginda yapilir); ancak originate
   * action'in dialplan exten'i gecerli bir agent username'i olmak zorundadir (agentsiz originate
   * Asterisk'te "Extension does not exist" ile reddedilir — 30.06 kaydi). Bu metod, Xn'in istedigi
   * cagrinin avail agent olmadan da cikabilmesi icin online (userHolder) + kampanya grubu/skill
   * eslesen ilk login agent'in CANONICAL kopyasini verir. Avail olmasi GEREKMEZ: tasiyicidir,
   * answer fazindaki secim ve dial-onu statu kontrolleri gercek atamayi yapar. Online agent hic
   * yoksa null doner (bos odaya arama cikarilmaz). Yalniz avail-yok yolunda cagrilir (nadir),
   * hot path'e ek maliyet getirmez.
   */
  public User findGammaSymbolicCarrier(Campaign campaign) {
    // Tasiyici icin SIRALAMA GEREKMEZ (avail bile olmasi gerekmiyor); getAllLoginUsers'in
    // TreeSet+comparator kurulumu (O(N log N), her cagrida) burada bosa masraf olurdu — bu yol
    // "avail yok" durumunda HER originate tick'inde calisabilir (code-review bulgusu). Ham
    // login kuyruklari uzerinde ilk uygun adayda erken cikilir; heap-iterasyon sirasinin dogal
    // degiskenligi tasiyici yukunu tek agent'ta toplanmaktan da korur.
    if (campaign.getCampaignGroups() == null) {
      return null;
    }
    Set<Skill> requiredSkills = campaign.getSkills();
    boolean filterBySkills = Boolean.TRUE.equals(campaign.getSkillEnabled())
        && !CollectionUtils.isEmpty(requiredSkills);
    for (CampaignGroup campaignGroup : campaign.getCampaignGroups()) {
      if (!campaignGroup.isEnabled()) {
        continue;
      }
      Queue<User> loginUsers = systemActiveUsers.get(campaignGroup.getGroup());
      if (loginUsers == null) {
        continue;
      }
      for (User candidate : loginUsers) {
        if (candidate == null || candidate.getId() == null) {
          continue;
        }
        if (filterBySkills && (candidate.getSkillWeightPair() == null
            || !candidate.getSkillWeightPair().keySet().containsAll(requiredSkills))) {
          continue;
        }
        User canonical = userHolder.getUser(candidate.getId());
        if (canonical != null) {
          return canonical;
        }
      }
    }
    return null;
  }

}
