package com.powerdialer.engine.dialer.routing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.powerdialer.dto.FctChannelEntryDTO;
import com.powerdialer.dto.FctChannelEventDTO;
import com.powerdialer.dto.FctChannelKpiDTO;
import com.powerdialer.dto.FctInFlightDTO;
import com.powerdialer.dto.FctRegistrySnapshotDTO;

/**
 * FCT numarası bazında eşzamanlı kanal rezervasyonu tutar. Bir numara = bir FCT olduğu için anahtar
 * doğrudan telefon numarasıdır; kapasite QSS'ten {@code RouteDetailEntry.capacity} ile gelir.
 *
 * <p>Kalıp olarak {@link com.powerdialer.engine.asterisk.fastagi.AgentDialLockRegistry}'nin kapasitesi
 * N olan hâlidir: {@code @Component}, per-anahtar atomik acquire, owner kontrollü idempotent release.
 * Registry kampanya veya Asterisk server başına DEĞİL, uygulama geneli tektir — aynı FCT birden fazla
 * kampanya ve birden fazla server tarafından kullanılabilir.
 *
 * <p>Rezervasyonlar sayaç değil {@code callId} set'i olarak tutulur; bu yüzden çift acquire doluluğu
 * şişirmez, çift release eksiltmez ve sayısal drift yapısal olarak oluşmaz. Kaçan release'ler
 * {@code fct.channel.ttl.ms} sonunda prune edilir ve {@link FctChannelEventKind#PRUNE} olayı olarak
 * raporlanır. TTL, {@code Constants.CALL_EXPIRE_TIMEOUT} eşiğinden sonra devreye girecek şekilde
 * seçilir; aksi hâlde konuşma hâlindeki uzun çağrının kanalı erken serbest bırakılırdı.
 *
 * <p>Rezervasyonun sahibi çağrıdır, kampanya değildir: kampanya start/pause/reset/reload akışları
 * registry'ye dokunmaz. Yalnız forceful stop sonrası artık kalmaması için
 * {@link #releaseAllForCampaign(Long)} safety net'i vardır.
 */
@Component
public class FctChannelRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(FctChannelRegistry.class);
  private static final long KPI_WINDOW_MS = 300000L;
  private static final int KPI_WINDOW_MAX_SAMPLES = 5000;

  @Value("${fct.channel.gate.enabled:true}")
  private boolean gateEnabled = true;

  @Value("${fct.channel.ttl.ms:3900000}")
  private long ttlMs = 3900000L;

  @Value("${fct.channel.stale.warn.ms:600000}")
  private long staleWarnMs = 600000L;

  @Value("${fct.channel.cooldown.ms:60000}")
  private long cooldownMs = 60000L;

  @Value("${fct.channel.headroom.percent:10}")
  private int headroomPercent = 10;

  @Value("${fct.channel.meta.retention.ms:1800000}")
  private long metaRetentionMs = 1800000L;

  @Value("${fct.channel.event.log.size:200}")
  private int eventLogSize = 200;

  /** Test seam: deterministik zaman kaynağı (ManagerConnectionDecorator.slowEventClockMs kalıbı). */
  private LongSupplier clockMs = System::currentTimeMillis;

  private final ConcurrentMap<String, ChannelState> statesByNumber = new ConcurrentHashMap<>();
  private final Deque<FctChannelEventDTO> events = new ArrayDeque<>();
  private final Object eventsLock = new Object();
  private final Deque<Long> prunedWindow = new ArrayDeque<>();
  private final Deque<Long> skippedWindow = new ArrayDeque<>();
  private final Deque<Long> rejectedWindow = new ArrayDeque<>();
  private final Object windowLock = new Object();
  private final AtomicLong eventSequence = new AtomicLong();

  @PostConstruct
  public void init() {
    LOG.warn("FctChannelRegistry cold start: FCT kanal rezervasyonlari bos basliyor. Restart oncesi"
        + " Asterisk'te devam eden cagrilar bir sure gorunmez; cooldown ve headroom bu pencereyi absorbe eder."
        + " gateEnabled={}, ttlMs={}, staleWarnMs={}, cooldownMs={}, headroomPercent={}",
        gateEnabled, ttlMs, staleWarnMs, cooldownMs, headroomPercent);
  }

  /**
   * Numara üzerinde kanal rezerve etmeyi dener. Kapasite kontrolü ve rezervasyon kaydı aynı
   * per-numara kritik bölümünde yapılır; iki originator thread'i son kanalı ikiye satamaz.
   *
   * <p>Gate pasifken (kill switch kapalı veya {@code capacity <= 0}) her zaman {@code true} döner
   * ancak rezervasyon yine kaydedilir; böylece monitor ekranı gerçek doluluğu gate açılmadan önce de
   * gösterir (fail-open).
   *
   * @return true: kanal rezerve edildi. false: kapasite dolu veya numara cooldown'da.
   */
  public boolean tryAcquire(String number, String type, String attempts, int capacity, String callId, Long campaignId) {
    return tryAcquire(number, type, attempts, capacity, null, callId, campaignId);
  }

  /**
   * Trunk bilgisini de kaydeden asıl sürüm. Üretim yolu ({@code FctChannelGate}) bunu kullanır;
   * trunk, Asterisk'e {@code DIALER_TRUNK} olarak gönderilen DOĞRULANMIŞ değerdir, böylece monitor
   * ekranı gerçekte ne gönderildiğini gösterir. Parametresiz sürüm trunk'ı önemsemeyen testler
   * içindir.
   */
  public boolean tryAcquire(String number, String type, String attempts, int capacity, String trunk,
                            String callId, Long campaignId) {
    if (isBlank(number) || isBlank(callId)) {
      return true;
    }
    long now = clockMs.getAsLong();
    ChannelState state = state(number);
    synchronized (state) {
      state.type = type;
      state.attempts = attempts;
      state.capacity = capacity;
      state.trunk = trunk;
      state.lastSeenMs = now;
      pruneExpired(state, now);

      if (isGateActive(capacity)) {
        if (state.cooldownUntilMs > now) {
          recordEvent(FctChannelEventKind.SKIP, number, callId, "cooldown kalan " + remainingSeconds(state.cooldownUntilMs, now) + "sn", now);
          return false;
        }
        int effectiveCapacity = effectiveCapacity(capacity);
        if (!state.inFlight.containsKey(callId) && state.inFlight.size() >= effectiveCapacity) {
          recordEvent(FctChannelEventKind.REJECT, number, callId, "kapasite dolu " + state.inFlight.size() + "/" + effectiveCapacity, now);
          return false;
        }
      }

      if (state.inFlight.put(callId, new Reservation(now, campaignId)) == null) {
        state.lastEventMs = now;
        recordEvent(FctChannelEventKind.ACQUIRE, number, callId, null, now);
      }
      return true;
    }
  }

  /**
   * Rezervasyonu bırakır. Idempotenttir ve owner kontrollüdür: kaydı olmayan {@code callId} için
   * no-op'tur, bu yüzden birden fazla yaşam döngüsü noktasından çağrılması güvenlidir.
   */
  public void release(String number, String callId) {
    if (isBlank(number) || isBlank(callId)) {
      return;
    }
    ChannelState state = statesByNumber.get(number);
    if (state == null) {
      return;
    }
    long now = clockMs.getAsLong();
    synchronized (state) {
      if (state.inFlight.remove(callId) != null) {
        state.lastEventMs = now;
        recordEvent(FctChannelEventKind.RELEASE, number, callId, null, now);
      }
    }
  }

  /**
   * cause 34 alındığında numarayı cooldown'a alır. Cooldown, kapasite sayımının göremediği trafiği
   * (PowerDialer dışı tüketiciler, restart sonrası bilinmeyen doluluk) dolaylı olarak telafi eder.
   */
  public void markFull(String number) {
    if (isBlank(number)) {
      return;
    }
    long now = clockMs.getAsLong();
    ChannelState state = state(number);
    synchronized (state) {
      state.cooldownUntilMs = now + cooldownMs;
      state.lastSeenMs = now;
      state.lastEventMs = now;
      recordEvent(FctChannelEventKind.MARK_FULL, number, null, "cooldown baslatildi (" + (cooldownMs / 1000L) + "sn)", now);
    }
  }

  /**
   * cause 34 sonrası aynı çağrının sıradaki FCT ile yeniden originate edildiğini feed'e yazar.
   * Rezervasyon {@code tryAcquire} ile ayrıca alınır; bu metot yalnız gözlem olayı üretir ve
   * KPI penceresi beslemez.
   */
  public void recordReoriginate(String number, String callId) {
    if (isBlank(number)) {
      return;
    }
    recordEvent(FctChannelEventKind.REORIGINATE, number, callId, null, clockMs.getAsLong());
  }

  /**
   * FCT listesi tükendikten sonra sabit hat caller ID'siyle devam edildiğini feed'e yazar.
   * Sabit hatta kapasite kavramı olmadığı için rezervasyon TUTULMAZ; numara için
   * {@link #statesByNumber} girdisi de oluşturulmaz, yalnız olay kaydedilir.
   */
  public void recordFixedLineFallback(String fixedLineNumber, String callId) {
    if (isBlank(fixedLineNumber)) {
      return;
    }
    recordEvent(FctChannelEventKind.FALLBACK_FIXED_LINE, fixedLineNumber, callId,
        "FCT listesi tukendi, sabit hat", clockMs.getAsLong());
  }

  /**
   * Kampanya forceful stop sonrası artık rezervasyon kalmaması için safety net. Normal akışta
   * release'ler çağrı bazında yapılır; bu metot yalnız stop job'ın son temizliğinde çağrılır.
   */
  public void releaseAllForCampaign(Long campaignId) {
    if (campaignId == null) {
      return;
    }
    long now = clockMs.getAsLong();
    for (ChannelState state : statesByNumber.values()) {
      synchronized (state) {
        Iterator<Map.Entry<String, Reservation>> iterator = state.inFlight.entrySet().iterator();
        while (iterator.hasNext()) {
          Map.Entry<String, Reservation> reservation = iterator.next();
          if (campaignId.equals(reservation.getValue().campaignId)) {
            iterator.remove();
            state.lastEventMs = now;
            recordEvent(FctChannelEventKind.RELEASE, state.phoneNumber, reservation.getKey(), "campaign cleanup", now);
          }
        }
      }
    }
  }

  /**
   * Numara cooldown'da mı? Gate kapalıyken her zaman {@code false} döner: cooldown kaydı
   * gözlemlenebilirlik için tutulmaya devam eder ama akışı kısmaz.
   */
  public boolean isInCooldown(String number) {
    if (!gateEnabled || isBlank(number)) {
      return false;
    }
    ChannelState state = statesByNumber.get(number);
    if (state == null) {
      return false;
    }
    synchronized (state) {
      return state.cooldownUntilMs > clockMs.getAsLong();
    }
  }

  /** Numara üzerindeki geçerli (TTL içinde) rezervasyon sayısı. */
  public int occupancy(String number) {
    if (isBlank(number)) {
      return 0;
    }
    ChannelState state = statesByNumber.get(number);
    if (state == null) {
      return 0;
    }
    long now = clockMs.getAsLong();
    synchronized (state) {
      pruneExpired(state, now);
      return state.inFlight.size();
    }
  }

  /** Headroom uygulanmış kapasite. {@code capacity <= 0} ise gate pasiftir. */
  public int effectiveCapacity(int capacity) {
    if (capacity <= 0) {
      return 0;
    }
    int headroom = Math.max(0, Math.min(100, headroomPercent));
    int effective = (int) Math.floor(capacity * (100.0d - headroom) / 100.0d);
    return Math.max(1, effective);
  }

  public boolean isGateEnabled() {
    return gateEnabled;
  }

  /**
   * Monitor ekranı için anlık durum kopyası. Kilit sırası her zaman state -> events yönündedir;
   * bu metot state kilitlerini bıraktıktan sonra event log'unu kopyalar (deadlock riski yok).
   */
  public FctRegistrySnapshotDTO snapshot() {
    long now = clockMs.getAsLong();
    FctRegistrySnapshotDTO snapshot = new FctRegistrySnapshotDTO();
    snapshot.setServerTimeMs(now);
    snapshot.setTtlMs(ttlMs);
    snapshot.setStaleWarnMs(staleWarnMs);
    snapshot.setCooldownMs(cooldownMs);
    snapshot.setHeadroomPercent(headroomPercent);
    snapshot.setGateEnabled(gateEnabled);

    List<FctChannelEntryDTO> entries = new ArrayList<>();
    Iterator<Map.Entry<String, ChannelState>> stateIterator = statesByNumber.entrySet().iterator();
    while (stateIterator.hasNext()) {
      ChannelState state = stateIterator.next().getValue();
      FctChannelEntryDTO entry;
      synchronized (state) {
        pruneExpired(state, now);
        if (isStaleMetadata(state, now)) {
          stateIterator.remove();
          continue;
        }
        entry = toEntryDto(state);
      }
      entries.add(entry);
    }
    Collections.sort(entries, new Comparator<FctChannelEntryDTO>() {
      @Override
      public int compare(FctChannelEntryDTO left, FctChannelEntryDTO right) {
        return left.getPhoneNumber().compareTo(right.getPhoneNumber());
      }
    });
    snapshot.setEntries(entries);

    List<FctChannelEventDTO> recentEvents = new ArrayList<>();
    synchronized (eventsLock) {
      Iterator<FctChannelEventDTO> descending = events.descendingIterator();
      while (descending.hasNext()) {
        recentEvents.add(descending.next());
      }
    }
    snapshot.setRecentEvents(recentEvents);
    snapshot.setKpi(kpi(now));
    return snapshot;
  }

  private FctChannelKpiDTO kpi(long now) {
    FctChannelKpiDTO kpi = new FctChannelKpiDTO();
    synchronized (windowLock) {
      kpi.setPrunedLast5Min(countWindow(prunedWindow, now));
      kpi.setSkippedLast5Min(countWindow(skippedWindow, now));
      kpi.setRejectedLast5Min(countWindow(rejectedWindow, now));
    }
    return kpi;
  }

  private FctChannelEntryDTO toEntryDto(ChannelState state) {
    FctChannelEntryDTO entry = new FctChannelEntryDTO();
    entry.setPhoneNumber(state.phoneNumber);
    entry.setType(state.type);
    entry.setAttempts(state.attempts);
    entry.setTrunk(state.trunk);
    entry.setCapacity(state.capacity);
    entry.setEffectiveCapacity(effectiveCapacity(state.capacity));
    entry.setCooldownUntilMs(state.cooldownUntilMs);
    entry.setLastEventMs(state.lastEventMs);
    List<FctInFlightDTO> inFlight = new ArrayList<>(state.inFlight.size());
    for (Map.Entry<String, Reservation> reservation : state.inFlight.entrySet()) {
      inFlight.add(new FctInFlightDTO(reservation.getKey(), reservation.getValue().reservedAtMs, reservation.getValue().campaignId));
    }
    entry.setInFlight(inFlight);
    return entry;
  }

  /**
   * Trafiği durmuş ve rezervasyonu kalmamış numara metadata retention süresinden sonra düşer;
   * böylece monitor listesi sınırsız büyümez ama boşta duran numara bir süre IDLE olarak görünür.
   */
  private boolean isStaleMetadata(ChannelState state, long now) {
    return state.inFlight.isEmpty()
        && state.cooldownUntilMs <= now
        && now - state.lastSeenMs > metaRetentionMs;
  }

  /** state kilidi altında çağrılır. */
  private void pruneExpired(ChannelState state, long now) {
    Iterator<Map.Entry<String, Reservation>> iterator = state.inFlight.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, Reservation> reservation = iterator.next();
      long age = now - reservation.getValue().reservedAtMs;
      if (age > ttlMs) {
        iterator.remove();
        state.lastEventMs = now;
        recordEvent(FctChannelEventKind.PRUNE, state.phoneNumber, reservation.getKey(), "TTL asildi, yas " + (age / 1000L) + "sn", now);
        LOG.warn("FCT kanal rezervasyonu TTL ile temizlendi (kacan release): number={}, callId={}, ageMs={}",
            state.phoneNumber, reservation.getKey(), age);
      }
    }
  }

  private void recordEvent(FctChannelEventKind kind, String number, String callId, String detail, long now) {
    FctChannelEventDTO event = new FctChannelEventDTO(eventSequence.incrementAndGet(), now, kind.name(), number, callId, detail);
    synchronized (eventsLock) {
      events.addLast(event);
      while (events.size() > Math.max(1, eventLogSize)) {
        events.removeFirst();
      }
    }
    if (kind == FctChannelEventKind.PRUNE) {
      pushWindow(prunedWindow, now);
    } else if (kind == FctChannelEventKind.SKIP) {
      pushWindow(skippedWindow, now);
    } else if (kind == FctChannelEventKind.REJECT) {
      pushWindow(rejectedWindow, now);
    }
  }

  private void pushWindow(Deque<Long> window, long now) {
    synchronized (windowLock) {
      window.addLast(now);
      trimWindow(window, now);
      while (window.size() > KPI_WINDOW_MAX_SAMPLES) {
        window.removeFirst();
      }
    }
  }

  /** windowLock altında çağrılır. */
  private void trimWindow(Deque<Long> window, long now) {
    while (!window.isEmpty() && now - window.peekFirst() > KPI_WINDOW_MS) {
      window.removeFirst();
    }
  }

  /** windowLock altında çağrılır. */
  private int countWindow(Deque<Long> window, long now) {
    trimWindow(window, now);
    return window.size();
  }

  private boolean isGateActive(int capacity) {
    return gateEnabled && capacity > 0;
  }

  private long remainingSeconds(long untilMs, long now) {
    return Math.max(0L, (untilMs - now + 999L) / 1000L);
  }

  private ChannelState state(String number) {
    ChannelState existing = statesByNumber.get(number);
    if (existing != null) {
      return existing;
    }
    ChannelState created = new ChannelState(number);
    ChannelState previous = statesByNumber.putIfAbsent(number, created);
    return previous == null ? created : previous;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  /** Tüm mutasyonları kendi monitörü altında yapılan per-numara durumu. */
  private static final class ChannelState {

    private final String phoneNumber;
    private final LinkedHashMap<String, Reservation> inFlight = new LinkedHashMap<>();
    private String type = "";
    private String attempts = "";
    private String trunk;
    private int capacity;
    private long lastSeenMs;
    private long cooldownUntilMs;
    private long lastEventMs;

    private ChannelState(String phoneNumber) {
      this.phoneNumber = phoneNumber;
    }
  }

  private static final class Reservation {

    private final long reservedAtMs;
    private final Long campaignId;

    private Reservation(long reservedAtMs, Long campaignId) {
      this.reservedAtMs = reservedAtMs;
      this.campaignId = campaignId;
    }
  }
}
