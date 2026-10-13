package com.powerdialer.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.persistence.Transient;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.routing.RouteDetailsCursor;
import com.powerdialer.engine.predictive.gamma.model.GammaAnswerSample;
import com.powerdialer.serialization.CallSerializer;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Utils;


@JsonSerialize(using = CallSerializer.class)
public class Call {

  private static final Logger log = LoggerFactory.getLogger(Call.class);
  private String id;
  private String uniqueKey;
  private String calledNumber;
  private String channel;
  // volatile: alan AMI eventWorker, scheduleService, Redis subscriber, login/logout handler,
  // CampaignStopJob ve cache-expiry thread'lerinden yazılıp 50'den fazla noktadan okunuyor.
  // Senkronizasyonsuz düz alanda yazımların diğer thread'lerde görünürlüğü garanti değildi.
  private volatile TelephonyResultCode telephonyResultCode;
  private ResultCode userResultCode;
  private Integer resultCodeSequence;
  private volatile User user;
  private String callbackNumber;
  private Double callbackDate;
  private String retryNumber;
  private List<String> retryHistory;
  private List<String> cbHistory;
  private List<String> callProcessHistory;
  private Double retryDate;
  private Double originationDate;
  private Double startDate;
  private Double answerDate;
  private Double completeDate;
  private Campaign campaign;
  private JSONObject rawData;
  private JSONObject rawData2;
  private String attachedData;
  private CallOriginator callOriginator;
  private long durationFromAnswer;
  private long durationFromOrigination;
  private boolean abandoned;
  private boolean orphaned;
  private Future<?> previewFuture;
  private transient volatile Future<?> originationFailFallbackFuture;
  private final Date createDate;
  private Set<String> DNCNumbers;
  private Constants.CALL_TYPES callType;
  private volatile boolean answeredByUser;
  private Double isFetched;
  private String callerId;
  private transient volatile Long productionNumber;
  private transient volatile boolean telephonySlotReleased;
  private transient volatile int fctChannelDeferCount;
  private transient volatile int fctListRetryCount;
  private transient volatile int fixedLineAttemptCount;
  private transient volatile String fctTrunk;
  private transient volatile boolean businessResultFinalized;
  private transient volatile boolean awaitingUserResultCode;
  // SORUN 2 / 1B (GAMMA): user, avail agent yoklugunda originate exten'ini tasiyan TEMSILI
  // tasiyicidir (gercek secim answer'da). Bu bayrak set iken cagri CallUpToDateKeeper'a
  // KAYDedilmez: mesgul tasiyiciya "tamamlanmamis hayalet cagri" yazilirsa, tasiyici bosaldiginda
  // UserSubscriber.handleStatusChange'in isUserHasNotCompletedCall kapisi onu idle'a almaz ve
  // getAvailableUserToCallsCount sayimini sisirirdi (code-review bulgusu, CONFIRMED-MED).
  private transient volatile boolean gammaSymbolicCarrierAssignment;
  // FIX-GLB90048674 (Faz 3, Scope A): answer yolundaki persisted-CDR lookup
  // (cdrService.getByInteractionId) çağrı yaşam döngüsü başına EN FAZLA BİR kez yapılmalı.
  // CDR id bilindiğinde lookup zaten atlanır; bu bayrak, ilk lookup BOŞ dönse (ve save id
  // atamasa) bile sonraki answer event'lerinde SELECT'in tekrarlanmamasını garanti eder.
  // transient/volatile: JVM-only, çağrı cache'iyle birlikte GC olur (yeni kalıcı state yok).
  private transient volatile boolean cdrPersistLookupAttempted;
  private transient volatile String linkedid;
  private transient volatile boolean transferred;
  private CDR cdr;
  private Integer attempts;
  private Integer attemptsBeforeOrigination;
  private Integer callbackAttempts;
  private Filter appliedSegmentationFilter;
  private String lscResponseValues;
  private Constants.CALLBACKTYPE callbackType;
  private String customerName;
  private String customerSurname;
  private Integer relationType;
  private String  relation;
  private static final String CALL_PROCESS_FRESH = "F";
  private static final String CALL_PROCESS_CALLBACK = "CB";
  private static final String CALL_PROCESS_RETRY = "R";
  private String closeReason;
  private String  filter;
  private transient RouteDetailsCursor routeDetailsCursor;

  /**
   * Gamma Plus (MADDE 1) — cevap anında AR penceresine yazılan kaydın referansı.
   *
   * <p>İki iş yapar: (1) hangup'ta aynı çağrının pencereye ikinci kez eklenmesini engeller
   * (çift sayım koruması), (2) çağrı sonradan abandon olursa doğru kaydın damgalanmasını sağlar.
   *
   * <p>JVM-only transient/volatile: çağrı cache'iyle birlikte GC olur, DB/CDR/Redis'e yazılmaz,
   * yeni kalıcı state üretmez. Bayrak kapalıyken hiç set edilmez (null kalır).
   */
  private transient volatile GammaAnswerSample gammaAnswerSample;

  public Call() {
    this.createDate = new Date();
    this.productionNumber = 0L;
    this.cdr = new CDR();
  }

  @Transient
  public GammaAnswerSample getGammaAnswerSample() {
    return gammaAnswerSample;
  }

  public void setGammaAnswerSample(GammaAnswerSample gammaAnswerSample) {
    this.gammaAnswerSample = gammaAnswerSample;
  }

  public boolean isAbandoned() {
    return abandoned;
  }

  public void setAbandoned(boolean abandoned) {
    this.abandoned = abandoned;
  }

  public boolean isOrphaned() {
    return orphaned;
  }

  public void setOrphaned(boolean orphaned) {
    this.orphaned = orphaned;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUniqueKey() {
    return uniqueKey;
  }

  public void setUniqueKey(String uniqueKey) {
    this.uniqueKey = uniqueKey;
  }

  public String getCalledNumber() {
    return calledNumber;
  }

  public void setCalledNumber(String calledNumber) {
    this.calledNumber = calledNumber;
  }

  public TelephonyResultCode getTelephonyResultCode() {
    return telephonyResultCode;
  }

  public void setTelephonyResultCode(TelephonyResultCode telephonyResultCode) {
    this.telephonyResultCode = telephonyResultCode;
  }

  public RouteDetailsCursor getRouteDetailsCursor() {
    return routeDetailsCursor;
  }

  public void setRouteDetailsCursor(RouteDetailsCursor routeDetailsCursor) {
    this.routeDetailsCursor = routeDetailsCursor;
  }

  public String getCustomerName() {
    return customerName;
  }

  public void setCustomerName(String customerName) {
    this.customerName = customerName;
  }

  public String getCustomerSurname() {
    return customerSurname;
  }

  public void setCustomerSurname(String customerSurname) {
    this.customerSurname = customerSurname;
  }

  public Integer getRelationType() {
    return relationType;
  }

  public void setRelationType(Integer relationType) {
    this.relationType = relationType;
  }

  public String getRelation() {
    return relation;
  }

  public void setRelation(String relation) {
    this.relation = relation;
  }

  public ResultCode getUserResultCode() {
    return userResultCode;
  }

  public void setUserResultCode(ResultCode userResultCode) {
    this.userResultCode = userResultCode;
  }

  public Integer getResultCodeSequence() {
    setResultCodeSequenceFromRawData();
    return resultCodeSequence;
  }

  private void setResultCodeSequenceFromRawData() {
    if (resultCodeSequence == null)
      resultCodeSequence = rawData.has(campaign.getColResultRcSequence()) ? rawData.getInt(campaign.getColResultRcSequence()) : 0;
  }

  public void setResultCodeSequence(Integer resultCodeSequence) {
    this.resultCodeSequence = resultCodeSequence;
  }

  public void incrementResultCodeSequence() {
    setResultCodeSequenceFromRawData();

    if (rawData.has(campaign.getColResultUserRc()) && userResultCode != null) {
      Long previousUserResultCodeId = rawData.getLong(campaign.getColResultUserRc());
      Long currentUserResultCodeId = userResultCode.getId();
      if (!previousUserResultCodeId.equals(currentUserResultCodeId))
        resultCodeSequence = 0;
    } else if (rawData.has(campaign.getColResultTelephonyRc()) && telephonyResultCode != null) {
      Long previousTelephonyResultCodeId = rawData.getLong(campaign.getColResultTelephonyRc());
      Long currentTelephonyResultCodeId = telephonyResultCode.getId();
      if (!previousTelephonyResultCodeId.equals(currentTelephonyResultCodeId))
        resultCodeSequence = 0;
    } else {
      resultCodeSequence = 0;
    }

    ++resultCodeSequence;
  }

  public User getUser() {
    return user;
  }

  public synchronized void setUser(User user) {
    if ((user == null && this.user != null) || (user != null && !user.equals(this.user))) {
      log.info("Set " + user + " as user to " + this);
    }
    this.user = user;
    this.answeredByUser = false;
  }

  public String getCallbackNumber() {
    setCallbackNumberFromRawData();
    return callbackNumber;
  }

  private void setCallbackNumberFromRawData() {
    if (callbackNumber == null)
      callbackNumber = rawData.has(campaign.getColResultCallbackNumber()) ? rawData.getString(campaign.getColResultCallbackNumber()) : null;
  }

  public void setCallbackNumber(String callBackNumber) {
    this.callbackNumber = callBackNumber;
  }

  public Double getCallbackDate() {
    return callbackDate;
  }

  public void setCallbackDate(Double callbackDate) {
    this.callbackDate = callbackDate;
  }

  public String getFilter() {
    return filter;
  }

  public void setFilter(String filter) {
    this.filter = filter;
  }

  public String getRetryNumber() {
    setRetryNumberFromRawData();
    return retryNumber;
  }

  private void setRetryNumberFromRawData() {
    if (retryNumber == null)
      retryNumber = rawData.has(campaign.getColResultRetryNumber()) ? rawData.getString(campaign.getColResultRetryNumber()) : null;
  }

  public void setRetryNumber(String retryNumber) {
    this.retryNumber = retryNumber;
  }

  public Double getRetryDate() {
    return retryDate;
  }

  public void setRetryDate(Double retryDate) {
    this.retryDate = retryDate;
  }

  public Double getOriginationDate() {
    return originationDate;
  }

  public void setOriginationDate(Double originationDate) {
    this.originationDate = originationDate;
  }

  public Double getStartDate() {
    return startDate;
  }

  public void setStartDate(Double startDate) {
    this.startDate = startDate;
  }

  public Double getAnswerDate() {
    return answerDate;
  }

  public void setAnswerDate(Double answerDate) {
    this.answerDate = answerDate;
  }

  public Double getCompleteDate() {
    return completeDate;
  }

  public void setCompleteDate(Double completeDate) {
    this.completeDate = completeDate;

    if (answerDate != null)
      this.durationFromAnswer = Utils.convertDoubleToLongDate(completeDate) - Utils.convertDoubleToLongDate(answerDate);

    if (originationDate != null)
      this.durationFromOrigination = Utils.convertDoubleToLongDate(completeDate) - Utils.convertDoubleToLongDate(originationDate);
  }

  public Long getUserIdAppointedToCallback() {
    // FIX-GLB90060295: getCallbackToAppointer() Boolean'dir; ham unboxing null'da NPE atiyordu.
    // Ayni ifadedeki getAllowCallback() zaten Boolean.TRUE.equals ile korunuyordu. Bu metot
    // isOwnerBoundCallback() uzerinden ACD karar yolunda cagrildigi icin null-safe olmali.
    return rawData != null && getCampaign() != null && Boolean.TRUE.equals(getCampaign().getAllowCallback())
        && Boolean.TRUE.equals(getCampaign().getCallbackToAppointer())
        && StringUtils.isNotBlank(campaign.getColResultCallbackDate())
        && rawData.has(getCampaign().getColResultUserId()) && !isAppointerTimedOut()
        ? Long.valueOf(rawData.get(getCampaign().getColResultUserId()).toString())
        : null;
  }

  public Long getUserId() {
    if (rawData == null || getCampaign() == null) {
      return null;
    }
    String userIdCol = getCampaign().getColResultUserId();
    // user_id kolonu yok ya da NULL ise sahip yok demektir → null dönülür (exception değil). ATTEMPT_COUNT
    // stratejisi sahibe N denemede ulaşamayınca callback'i campaign-wide'a (user_id=NULL) devreder; eski hali
    // Long.valueOf("null") / JSONException ile patlıyor, böylece ASSIGN_TO_SAME_USER callback'i ACD'de takılıyordu.
    if (userIdCol == null || rawData.isNull(userIdCol)) {
      return null;
    }
    String value = rawData.get(userIdCol).toString();
    return StringUtils.isNotBlank(value) ? Long.valueOf(value) : null;
  }

  /**
   * GAMMA sembolik on-atamadan ve answer-fazi yeniden secim/NOAVAIL akisindan MUAF olmasi gereken
   * "sahibine bagli" callback mi? Appointed (redirect-to-me) veya ASSIGN_TO_SAME_USER callback'te
   * cagri belirli bir agent (sahip) icin cikarilir: temsili tasiyici/reselect uygulanmaz, sahip
   * originate'te idle'dan rezerve edilir ve answer'da ona baglanir. Kosul CallOriginator.processCall
   * icindeki isAppointedToUser || isAssignedToSameUser ile birebir aynidir.
   */
  public boolean isOwnerBoundCallback() {
    boolean appointed = getUserIdAppointedToCallback() != null;
    boolean sameUser = Constants.CALLBACKTYPE.ASSIGN_TO_SAME_USER.equals(callbackType)
        && getCampaign() != null && Boolean.TRUE.equals(getCampaign().getAllowOverwriteByWebphone())
        && getUserId() != null
        && !isAppointerTimedOut();
    return appointed || sameUser;
  }

  public Campaign getCampaign() {
    return campaign;
  }

  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
  }

  public JSONObject getRawData() {
    return rawData;
  }

  public void setRawData(JSONObject rawData) {
    this.rawData = rawData;
  }

  public String getAttachedData() {
    return attachedData;
  }

  public void setAttachedData(String attachedData) {
    this.attachedData = attachedData;
  }

  public CallOriginator getCallOriginator() {
    return callOriginator;
  }

  public void setCallOriginator(CallOriginator callOriginator) {
    this.callOriginator = callOriginator;
  }

  public long getDurationFromAnswer() {
    if (answerDate != null && completeDate == null)
      this.durationFromAnswer = new Date().getTime() - Utils.convertDoubleToLongDate(answerDate);
    return durationFromAnswer;
  }

  public void setDurationFromAnswer(long durationFromAnswer) {
    this.durationFromAnswer = durationFromAnswer;
  }

  public long getDurationFromOrigination() {
    if (originationDate != null && completeDate == null)
      this.durationFromOrigination = new Date().getTime() - Utils.convertDoubleToLongDate(originationDate);
    return durationFromOrigination;
  }

  public void setDurationFromOrigination(long durationFromOrigination) {
    this.durationFromOrigination = durationFromOrigination;
  }

  public boolean isActive() {
    return answerDate != null && completeDate == null && retryDate == null && callbackDate == null;
  }

  public void setPreviewFuture(Future<?> previewFuture) {
    this.previewFuture = previewFuture;
  }

  public boolean cancelPreviewFuture() {
    if (previewFuture != null && !previewFuture.isCancelled() && !previewFuture.isDone()) {
      log.info("Cancelling preview timeout for " + this);
      return previewFuture.cancel(true);
    }
    return false;
  }

  public void setOriginationFailFallbackFuture(Future<?> originationFailFallbackFuture) {
    this.originationFailFallbackFuture = originationFailFallbackFuture;
  }

  /**
   * Bekleyen origination-fail fallback'ini iptal eder. Çağrı yeniden originate edildiğinde
   * (ör. FCT list-retry aynı Call nesnesini yeniden kullanır) veya finalize edildiğinde
   * çağrılır; aksi halde eski denemenin fallback'i yeni denemeye RC 907 yazabilir.
   */
  public boolean cancelOriginationFailFallbackFuture() {
    Future<?> future = originationFailFallbackFuture;
    originationFailFallbackFuture = null;
    if (future != null && !future.isCancelled() && !future.isDone()) {
      return future.cancel(false);
    }
    return false;
  }

  public Double getCallFromTimeFromRawData() {
    String colCallFromTime = null;
    if (Constants.TIMEZONE_TYPE.SIMPLE.equals(campaign.getTimezoneType())) {
      colCallFromTime = getCampaign().getColCallFromTime();
    } else if (Constants.TIMEZONE_TYPE.ADVANCED.equals(campaign.getTimezoneType())) {
      colCallFromTime = getActualPhoneNumberColumn().getColCallFromTime();
    }
    if (colCallFromTime != null && getRawData().has(colCallFromTime))
      return getRawData().getDouble(colCallFromTime);
    return 0D;
  }

  public Double getCallToTimeFromRawData() {
    String colCallToTime = null;
    if (Constants.TIMEZONE_TYPE.SIMPLE.equals(campaign.getTimezoneType())) {
      colCallToTime = getCampaign().getColCallToTime();
    } else if (Constants.TIMEZONE_TYPE.ADVANCED.equals(campaign.getTimezoneType())) {
      colCallToTime = getActualPhoneNumberColumn().getColCallTillTime();
    }
    if (colCallToTime != null && getRawData().has(colCallToTime))
      return getRawData().getDouble(colCallToTime);
    return 1D;
  }

  /**
   * Check is call could be used to perform dialing now. For range which overlap
   * 2 days (e. g. from 23 to 1) till time must be >1
   * @return true if call can dialed false otherwise
   */
  public boolean isNowFitCallTimeRange() {
    if (!Constants.TIMEZONE_TYPE.OFF.equals(getCampaign().getTimezoneType())) {
      Integer actualTimezoneOffset = getActualTimezoneOffset();
      if (actualTimezoneOffset != null) {
        double nowTimeWithOffset = Utils.convertTimeToDouble(new org.joda.time.LocalTime(DateTimeZone.UTC)) + actualTimezoneOffset / 24D;
        Double callFromTime = getCallFromTimeFromRawData();
        Double callToTime = getCallToTimeFromRawData();
        Double nowBetweenTwoDaysWithTimeZoneOffset = nowTimeWithOffset + 1;
        return (nowTimeWithOffset >= callFromTime && nowTimeWithOffset <= callToTime)
            || (nowBetweenTwoDaysWithTimeZoneOffset >= callFromTime && nowBetweenTwoDaysWithTimeZoneOffset <= callToTime);
      } else {
        log.warn("Can't extract timezone offset for " + this + " rawData: " + rawData);
      }
    }
    return true;
  }

  /**
   * Reschedule edilen callback_date'i OLE serial double olarak hesaplar (task 321997, ATTEMPT_COUNT).
   * baseMs referans an (now veya now + rescheduleDelay). Kampanyada timezone arama penceresi tanımlıysa
   * ve baseMs penceresi dışına düşüyorsa, sonuç bir sonraki pencere açılışına (callFromTime) snap edilir;
   * böylece rescheduled kayıt pencere sınırında tekrar churn'e girmez. Timezone OFF veya offset/pencere
   * bilgisi yoksa baseMs aynen kullanılır. isNowFitCallTimeRange ile aynı primitifleri kullanır
   * (UTC time-of-day fraction + offset/24).
   */
  public double computeRescheduledCallbackDate(long baseMs) {
    if (Constants.TIMEZONE_TYPE.OFF.equals(getCampaign().getTimezoneType())) {
      return Utils.convertDateToDouble(new Date(baseMs));
    }
    Integer offset = getActualTimezoneOffset();
    Double from = getCallFromTimeFromRawData();
    Double to = getCallToTimeFromRawData();
    if (offset == null || from == null || to == null) {
      return Utils.convertDateToDouble(new Date(baseMs));
    }
    double localFraction = (Utils.convertTimeToDouble(new org.joda.time.LocalTime(baseMs, DateTimeZone.UTC)) + offset / 24D) % 1D;
    if (localFraction < 0) {
      localFraction += 1D;
    }
    boolean overnight = to < from;
    boolean inWindow = overnight
        ? (localFraction >= from || localFraction <= to)
        : (localFraction >= from && localFraction <= to);
    if (inWindow) {
      return Utils.convertDateToDouble(new Date(baseMs));
    }
    double deltaFraction = from - localFraction;
    if (deltaFraction <= 0D) {
      deltaFraction += 1D;
    }
    long deltaMs = Math.round(deltaFraction * 24D * 60D * 60D * 1000D);
    return Utils.convertDateToDouble(new Date(baseMs + deltaMs));
  }

  /**
   * Get PhoneNumberColumn which column actual for current called number. For
   * custom callbacks actual PhoneNumberColumn is PhoneNumberColumn that used
   * for last dialing
   * @return
   */
  public PhoneNumberColumn getActualPhoneNumberColumn() {
    PhoneNumberColumn currentPhoneNumberColumn = null;
    PhoneNumberColumn lastDialedPhoneNumberColumn = null;
    for (PhoneNumberColumn phoneNumberColumn : campaign.getPhoneNumberColumns()) {
      String colNumber = phoneNumberColumn.getColNumber();
      if (getRawData().has(colNumber)) {
        if (getRawData().get(colNumber).equals(getCalledNumber())) {
          currentPhoneNumberColumn = phoneNumberColumn;
          break;
        }
        if (getRawData().has(getCampaign().getColResultCalledNumber())) {
          String lastDialedNumber = getRawData().getString(getCampaign().getColResultCalledNumber());
          if (getRawData().get(colNumber).equals(lastDialedNumber)) {
            lastDialedPhoneNumberColumn = phoneNumberColumn;
          }
        }
      }
    }
    if (currentPhoneNumberColumn != null) {
      return currentPhoneNumberColumn;
    } else if (lastDialedPhoneNumberColumn != null) {
      return lastDialedPhoneNumberColumn;
    } else {
      log.warn("Can't find actual phoneNumberColumn. Fallback to " + campaign.getPhoneNumberColumns().get(0)
          + " " + this + " rawData: " + getRawData());
      return campaign.getPhoneNumberColumns().get(0);
    }
  }

  private Integer getActualTimezoneOffset() {
    String colTimezoneOffset = null;
    if (Constants.TIMEZONE_TYPE.SIMPLE.equals(campaign.getTimezoneType()))
      colTimezoneOffset = getCampaign().getColTimezoneOffset();
    else if (Constants.TIMEZONE_TYPE.ADVANCED.equals(campaign.getTimezoneType()))
      colTimezoneOffset = getActualPhoneNumberColumn().getColTimezoneOffset();

    if (rawData.has(colTimezoneOffset))
      return getRawData().getInt(colTimezoneOffset);
    else
      return null;
  }

  public String getNextNumberFromRawData() {
    return findNextNumberToCall(getNumberColumnsInRawData());
  }

  public String getPreviousNumberFromRawData() {
    List<PhoneNumberColumn> numberColumnsInRawData = getNumberColumnsInRawData();
    Collections.reverse(numberColumnsInRawData);
    return findNextNumberToCall(numberColumnsInRawData);
  }

  public List<PhoneNumberColumn> getNumberColumnsInRawData() {
    List<PhoneNumberColumn> numberColumnsInRawData = new ArrayList<>();
    for (PhoneNumberColumn numberColumn : getCampaign().getEnabledPhoneNumberColumns()) {
      if (getRawData().has(numberColumn.getColNumber()) && StringUtils.isNotEmpty(getRawData().getString(numberColumn.getColNumber())))
        numberColumnsInRawData.add(numberColumn);
    }
    return numberColumnsInRawData;
  }

  public List<String> getNumbersInRawData() {
    List<String> numbersInRawData = new ArrayList<>();
    for (PhoneNumberColumn numberColumn : getCampaign().getEnabledPhoneNumberColumns()) {
      if (getRawData().has(numberColumn.getColNumber())) {
        String phoneNumber = getRawData().getString(numberColumn.getColNumber());
        if (StringUtils.isNotEmpty(phoneNumber))
          numbersInRawData.add(phoneNumber);
      }
    }
    return numbersInRawData;
  }

  /**
   * Task 325559 — numara basina limit karsilastirmalarinin TEK normalizasyon kurali: bastaki ve
   * sondaki bosluklar atilir, sonuc bossa numara YOK sayilir.
   *
   * <p>Kural gerekli, cunku ayni numara farkli yollarla farkli yazimlarla eline geliyor:
   * telefon kolonundan HAM okunuyor ({@link #getFirstDialedNumber()}, {@code retry_history},
   * {@code cb_history}), producer ise TRIMLENMIS deger uzerinden sorguluyor
   * ({@code CallProducer.trimPhoneNumber}). Iki taraf ayni kurali kullanmazsa sayac haritasi
   * iskalanir, sayim 0 kabul edilir ve limit SESSIZCE hic uygulanmaz.</p>
   *
   * <p><b>Yalniz KARSILASTIRMA/SAYIM icindir.</b> Kolonlara YAZILAN deger normalize edilmez;
   * {@code retry_history} / {@code cb_history} / {@code call_process} yazim semantigi
   * degismez.</p>
   *
   * @param phoneNumber ham numara
   * @return normalize numara; deger bos veya yalniz bosluksa {@code null}
   */
  public static String normalizeNumberForLimit(String phoneNumber) {
    if (phoneNumber == null) {
      return null;
    }
    String trimmed = phoneNumber.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Task 325559 — numara basina limitin ADAY numaralari: normalize, TEKIL, sirali.
   *
   * <p>Kume iki kaynaktan olusur:</p>
   *
   * <ol>
   * <li><b>Telefon kolonlari</b> (colSort sirasinda). Yalniz bosluktan olusan kolon aday
   * DEGILDIR: producer o kolonu zaten atlar ({@code trimPhoneNumber} null doner), aday sayilirsa
   * {@code noEligibleNumberLeft()} sonsuza kadar {@code false} kalir ve erken kapatma o kayitta
   * hic tetiklenmez — kayit ne aranir ne kapanir.</li>
   * <li><b>Bekleyen callback numarasi</b> (varsa). Callback numarasi telefon kolonlarindan biri
   * olmak zorunda DEGILDIR (D2): agent elle girebilir. Kolon disi kalan boyle bir numara aday
   * kumesinde olmazsa, kendi limitinin ALTINDA olsa bile kolonlarin tumu limitliyken kayit
   * kapanir ve musterinin KENDI talebiyle olusan randevu duser (K16 ile celisir).</li>
   * </ol>
   *
   * <p>Tekillik bilinclidir: ayni numara iki kolonda duruyorsa tek aday, tek sayactir
   * (03.08.2026 duplicate-key semantigi).</p>
   *
   * <p>{@link #getNumbersInRawData()} sozlesmesi bilincli olarak DEGISTIRILMEDI: o metot
   * feature-off yollarinda ({@code anyNumberPresentAfterDNC}) ve
   * {@code advanceRetryNumberPastIneligible} sigortasinin yuruyus sinirinda kullaniliyor;
   * daraltmak ozellik kapali kampanyalarin davranisini degistirirdi.</p>
   *
   * @return aday numaralarin sirali ve tekil listesi
   */
  public List<String> getLimitCandidateNumbers() {
    List<String> candidates = new ArrayList<>();
    for (PhoneNumberColumn numberColumn : getCampaign().getEnabledPhoneNumberColumns()) {
      if (getRawData().has(numberColumn.getColNumber())) {
        addLimitCandidate(candidates, getRawData().getString(numberColumn.getColNumber()));
      }
    }
    if (isCallback()) {
      addLimitCandidate(candidates, getCallbackNumber());
    }
    return candidates;
  }

  private void addLimitCandidate(List<String> candidates, String rawNumber) {
    String normalized = normalizeNumberForLimit(rawNumber);
    if (normalized != null && !candidates.contains(normalized)) {
      candidates.add(normalized);
    }
  }

  private String findNextNumberToCall(List<PhoneNumberColumn> numberColumnsInRawData) {
    Iterator<PhoneNumberColumn> numberColumnsInRawIterator = numberColumnsInRawData.iterator();
    String lastCalledNumber = getRetryNumber() != null ? getRetryNumber() : getCalledNumber();
    while (numberColumnsInRawIterator.hasNext()) {
      PhoneNumberColumn phoneNumberColumn = numberColumnsInRawIterator.next();
      String currentPhoneNumber = getRawData().getString(phoneNumberColumn.getColNumber());
      if (lastCalledNumber.equals(currentPhoneNumber)) {
        if (numberColumnsInRawIterator.hasNext())
          return getRawData().getString(numberColumnsInRawIterator.next().getColNumber());
        return getRawData().getString(numberColumnsInRawData.get(0).getColNumber());
      }
    }
    return getCalledNumber();
  }

  public Set<String> getDNCNumbers() {
    setDNCNumbersFromRawData();
    return DNCNumbers;
  }

  private void setDNCNumbersFromRawData() {
    if (DNCNumbers == null) {
      String DNCNumbersString = null;
      if (campaign.getColResultDNCNumbers() != null && rawData.has(campaign.getColResultDNCNumbers()))
        DNCNumbersString = rawData.getString(campaign.getColResultDNCNumbers());
      DNCNumbers = DNCNumbersString == null ? new HashSet<>()
                                            : Sets.newHashSet(DNCNumbersString.split(Constants.COL_RESULT_NUMBER_SEPERATOR)).stream()
                                                .filter(number -> !StringUtils.isBlank(number))
                                                .collect(Collectors.toSet());
    }
  }

  public void setDNCNumbers(Set<String> dNCNumbers) {
    DNCNumbers = dNCNumbers;
  }

  public List<String> getRetryHistory() {
    setRetryHistoryRawData();
    return retryHistory;
  }

  private void setRetryHistoryRawData() {
    if (retryHistory == null) {
      String retryHistoryString = rawData.has(campaign.getColResultRetryHistory()) ? rawData.getString(campaign.getColResultRetryHistory()) : null;
      retryHistory = (retryHistoryString == null ? new ArrayList<>()
                                                 : Arrays.stream(retryHistoryString.split(Constants.COL_RESULT_NUMBER_SEPERATOR))
                                                     .filter(number -> !StringUtils.isBlank(number))
                                                     .collect(Collectors.toList()));
    }
  }

  public void setRetryHistory(List<String> retryHistory) {
    this.retryHistory = retryHistory;
  }

  public List<String> getCbHistory() {
    setCbHistoryFromRawData();
    return cbHistory;
  }

  private void setCbHistoryFromRawData() {
    if (cbHistory == null) {
      String cbHistoryString = getRawDataValue(campaign != null ? campaign.getColResultCbHistory() : null);
      cbHistory = (cbHistoryString == null ? new ArrayList<>()
          : Arrays.stream(cbHistoryString.split(","))
              .map(String::trim)
              .filter(number -> !StringUtils.isBlank(number))
              .collect(Collectors.toList()));
    }
  }

  public void setCbHistory(List<String> cbHistory) {
    this.cbHistory = cbHistory;
  }

  public List<String> getCallProcessHistory() {
    setCallProcessHistoryFromRawData();
    return callProcessHistory;
  }

  private void setCallProcessHistoryFromRawData() {
    if (callProcessHistory == null) {
      String callProcessHistoryString = getRawDataValue(campaign != null ? campaign.getColResultCallProcess() : null);
      callProcessHistory = (callProcessHistoryString == null ? new ArrayList<>()
          : Arrays.stream(callProcessHistoryString.split(Constants.COL_RESULT_NUMBER_SEPERATOR))
              .map(String::trim)
              .filter(step -> !StringUtils.isBlank(step))
              .collect(Collectors.toList()));
    }
  }

  public void setCallProcessHistory(List<String> callProcessHistory) {
    this.callProcessHistory = callProcessHistory;
  }

  public void addCallbackHistoryEntry(String callbackNumber) {
    if (StringUtils.isBlank(callbackNumber)) {
      return;
    }
    getCbHistory().add(callbackNumber);
    appendCallProcessStep(CALL_PROCESS_CALLBACK);
  }

  public void addRetryCallProcessEntry() {
    appendCallProcessStep(CALL_PROCESS_RETRY);
  }

  /**
   * Task 325559 — kaydin bugune kadar yapilmis/planlanmis aramalarinin NUMARA DIZISI.
   *
   * <p>Numara basina sayac icin ayri bir kolon tutulmaz; dizi mevcut uc kolondan yeniden kurulur.
   * Bunu mumkun kilan invariant, {@code call_process} izinin {@code retry_history} ve
   * {@code cb_history} ile KILITLI ADIMLARLA yazilmasidir (bkz.
   * {@code RetryStrategy.updateRetryHistory} ve {@link #addCallbackHistoryEntry}). Dolayisiyla
   * {@code call_process} bir kursor gibi kullanilir: {@code F} fresh aramayi,
   * her {@code R} sirdaki retry numarasini, her {@code CB} sirdaki callback numarasini gosterir.</p>
   *
   * <p><b>Iz henuz yazilmamis fresh arama:</b> {@code call_process} bos oldugunda kayit fiilen bir
   * kez (fresh) aranmistir; iz {@code updateRetryHistory} icinde retry planlanirken yazilir. Bu
   * durumda dizi tek elemanli kabul edilir.</p>
   *
   * <p><b>Bu dizi HANGUP SONRASI okumadir.</b> {@code R}/{@code CB} adimlari arama
   * PLANLANDIGINDA yazilir; strateji katmani hangup'tan sonra karar verdigi icin o anda izdeki
   * HER girdi fiilen yapilmis bir aramaya karsilik gelir ve dizinin tamami sayilir. Bu metodun
   * dogru kullanicisi {@code RetryStrategy}'dir.</p>
   *
   * <p><b>Arama ONCESI okuma icin {@link #getCompletedDialSequence()} kullanilir.</b> Producer
   * satiri eline aldiginda izdeki son girdi henuz YAPILMAMIS bir plandir; ayni diziyi orada
   * kullanmak numaranin son hakkini yakar ve fiili limiti {@code maxAttemptPerNumber - 1}'e
   * dusurur. Iki okumanin ayrimi bilinclidir — eskiden bu fark "sayac en fazla 1 fazla sayar,
   * muhafazakarlik iyidir" diye yorumlaniyordu; yorum yanlisti.</p>
   *
   * <p><b>Dayaniklilik:</b> iz ile gecmis listelerinin uzunluklari tutarsizsa (ornek: eski kayitta
   * {@code call_process} bos ama {@code retry_history} dolu) guvenli fallback'e dusulur:
   * {@code retry_history} + fresh numara. Bu yol daha az degil, gerektiginde daha COK sayar.</p>
   *
   * @return aranan numaralarin sirali listesi; kaynak kolonlar okunamiyorsa bos liste
   */
  public List<String> getDialedNumberSequence() {
    String firstDialedNumber = getFirstDialedNumber();
    List<String> retries = getRetryHistory();
    List<String> callbacks = getCbHistory();
    List<String> trail = getCallProcessHistory();

    // Iz henuz yazilmamis fresh arama: yalnizca gecmis listelerinin de bos oldugu durumda gecerlidir.
    // Iz bos ama retry/callback gecmisi doluysa (eski kayit, eslenmemis call_process kolonu) bu
    // kisayol aramalari EKSIK sayardi; o durum tutarsizlik olarak fallback'e duser.
    if (trail.isEmpty() && retries.isEmpty() && callbacks.isEmpty()) {
      return firstDialedNumber == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(firstDialedNumber));
    }

    if (!isDialTrailConsistent(trail, retries, callbacks)) {
      log.debug("Dial trail inconsistent, falling back to retry history for uniqueKey={} "
          + "(trail={}, retries={}, callbacks={})",
          uniqueKey, trail.size(), retries.size(), callbacks.size());
      return buildFallbackDialSequence(firstDialedNumber, retries);
    }

    List<String> sequence = new ArrayList<>(trail.size());
    int retryIndex = 0;
    int callbackIndex = 0;
    for (String step : trail) {
      if (CALL_PROCESS_RETRY.equals(step)) {
        sequence.add(retries.get(retryIndex++));
      } else if (CALL_PROCESS_CALLBACK.equals(step)) {
        sequence.add(callbacks.get(callbackIndex++));
      } else if (firstDialedNumber != null) {
        sequence.add(firstDialedNumber);
      }
    }
    return sequence;
  }

  /**
   * Task 325559 — TAMAMLANMIS aramalarin numara dizisi (arama ONCESI okuma).
   *
   * <p>{@link #getDialedNumberSequence()} izin TAMAMINI sayar ve hangup SONRASI dogru olan
   * okumadir: o anda izdeki her girdi yapilmis bir aramaya karsilik gelir. Ancak
   * {@code RetryStrategy.updateRetryHistory} plani yazar ve
   * {@code ManagerConnectionDecorator.manageRetries} hemen ardindan persist eder; bu yuzden
   * {@code CallProducer} satiri eline aldiginda izdeki SON girdi henuz YAPILMAMIS bir plandir.
   * Ayni diziyi arama oncesi limit karari icin kullanmak, numaranin son hakkini yakar ve fiili
   * limiti {@code maxAttemptPerNumber - 1}'e dusurur.</p>
   *
   * <p>Bu metot iki durumu duser:</p>
   *
   * <ol>
   * <li><b>Bekleyen plan:</b> satirda {@code retry_date} veya {@code callback_date} varsa (satir
   * producer'a zaten bu yuzden gelir) dizinin son elemani plandir, sayilmaz.</li>
   * <li><b>Hic islem gormemis kayit:</b> uc gecmis kolonu da bossa fresh arama HENUZ YAPILMAMIStir.
   * {@link #getDialedNumberSequence()} bu durumda ilk kolona hayali bir arama yazar (hangup sonrasi
   * dogru); arama oncesi sayim SIFIR olmalidir, aksi halde {@code limit=1} iken ilk kolon hic
   * aranmaz.</li>
   * </ol>
   *
   * <p>Yazim semantigine (retry_history / call_process / cb_history) DOKUNULMAZ; bu yalnizca ayri
   * bir OKUMA'dir.</p>
   *
   * @return tamamlanmis aramalarin sirali numara listesi
   */
  public List<String> getCompletedDialSequence() {
    if (getCallProcessHistory().isEmpty() && getRetryHistory().isEmpty() && getCbHistory().isEmpty()) {
      return new ArrayList<>();
    }
    List<String> sequence = getDialedNumberSequence();
    if (sequence.isEmpty() || !hasPendingDialPlan()) {
      return sequence;
    }
    return new ArrayList<>(sequence.subList(0, sequence.size() - 1));
  }

  /**
   * Task 325559 — bekleyen arama planini FIILEN aranan numaraya yonlendirir.
   *
   * <p>Planlanan numara limitini doldurmussa producer kaydi kapatmak yerine kalan numaralardan
   * birini arar (kabul kriteri 3). Bu durumda gecmis kaydi da duzeltilmelidir: aksi halde iz
   * "n1 arandi" der ama fiilen n2 aranmis olur; n1'in sayaci sisirilir, n2'nin aramasi HIC
   * sayilmaz ve n2 limitinden bir fazla arama alir.</p>
   *
   * <p>Iz bir DIAL LOG'udur; plan yonlendirildiginde son girdi gercekten aranan numarayla
   * degistirilir. Yazim ZAMANI ve semantigi degismez, yalniz yanlis kalan son girdi duzeltilir.</p>
   *
   * <p><b>Hangi gecmis listesi duzeltilir (Faz 7 / P3.2):</b> secim {@code call_process} izinin
   * SON ADIMINDAN turetilir ({@code CB} -&gt; {@code cb_history}, {@code R} -&gt;
   * {@code retry_history}). Onceki hal cagiranin {@code callbackNumber != null} bilgisine
   * dayaniyordu; {@code callback_number} dolu ama bekleyen plan bir RETRY olan satirda YANLIS
   * listenin son elemani duzeltilirdi. Pratikte bu durum olusmuyor ({@code insertCallResultToDB}
   * {@code callback_date} null'sa {@code callback_number}'i da NULL'lar) ama varsayim artik kodda
   * yazili; iz yoksa veya son adim bir arama adimi degilse metot {@code false} doner ve arama
   * YAPILMAZ.</p>
   *
   * @param actualNumber fiilen aranacak numara
   * @return kayit duzeltilebildiyse {@code true}; {@code false} ise cagiran arama YAPMAMALIDIR
   */
  public boolean redirectPendingDialTo(String actualNumber) {
    if (actualNumber == null || !hasPendingDialPlan()) {
      return false;
    }
    List<String> pendingHistory = resolvePendingHistoryFromTrail();
    if (pendingHistory == null || pendingHistory.isEmpty()) {
      return false;
    }
    int lastIndex = pendingHistory.size() - 1;
    if (!actualNumber.equals(pendingHistory.get(lastIndex))) {
      pendingHistory.set(lastIndex, actualNumber);
    }
    return true;
  }

  /**
   * Bekleyen planin hangi gecmis listesine yazildigini {@code call_process} izinin son adimindan
   * belirler. Iz bos veya son adim {@code R}/{@code CB} degilse (ornek: yalniz {@code F})
   * bekleyen bir plan kaydi yoktur ve {@code null} doner.
   */
  private List<String> resolvePendingHistoryFromTrail() {
    List<String> trail = getCallProcessHistory();
    if (trail.isEmpty()) {
      return null;
    }
    String lastStep = trail.get(trail.size() - 1);
    if (CALL_PROCESS_CALLBACK.equals(lastStep)) {
      return getCbHistory();
    }
    if (CALL_PROCESS_RETRY.equals(lastStep)) {
      return getRetryHistory();
    }
    return null;
  }

  /**
   * Satirda henuz yapilmamis bir arama plani var mi? {@code retry_date} veya {@code callback_date}
   * dolu olan satirlar producer'a tam bu yuzden gelir; dolayisiyla izdeki son girdi o plandir.
   *
   * <p>Bir numaranin PLAN olmasinin tek olcutu budur. {@code retry_number} / {@code callback_number}
   * kolonlari tek basina plan degildir: strateji kapanisi {@code retry_number}'i temizlemez ve kayit
   * aktivasyonu yalniz tarih kolonlarini temizler; bu yuzden plansiz bir satirda o kolonlar BAYAT
   * isaretci olarak kalabilir (Faz 8 / K1).</p>
   */
  public boolean hasPendingDialPlan() {
    return rawDataHasValue(campaign == null ? null : campaign.getColResultRetryDate())
        || rawDataHasValue(campaign == null ? null : campaign.getColResultCallbackDate());
  }

  /**
   * Kaydin arama izi var mi — yani daha once en az bir kez aranip islenmis mi? Hic islem gormemis
   * kayitta uc gecmis kolonu da bostur.
   */
  public boolean hasDialHistory() {
    return !getCallProcessHistory().isEmpty() || !getRetryHistory().isEmpty() || !getCbHistory().isEmpty();
  }

  /**
   * Task 325559 / Faz 8 (K2) — PLANLAMA ADIMI OLMADAN yapilan bir aramayi izde tek girdiyle kaydeder.
   *
   * <p>Iz aramalari planlama aninda kaydeder ({@code RetryStrategy.updateRetryHistory},
   * {@link #addCallbackHistoryEntry}); ilk (fresh) arama ise {@code F} ile ORTUK temsil edilir ve
   * izin ilk yazimiyla eklenir. Aktive edilen bir kayit fresh fetch'ten yeniden gelir ama izinde
   * {@code F} zaten vardir: bu aramanin bir planlama adimi yoktur ve kaydedilmezse numara basina
   * sayim onu HIC gormez — numara limitinden 1 fazla aranir.</p>
   *
   * <p>Arama, planli bir tekrar arama gibi {@code retry_history} + {@code R} adimi olarak KILITLI
   * yazilir. Boylece "+1 is for first call" varsayimi korunur (yalniz ilk fresh arama ortuk kalir)
   * ve sonraki hangup sonrasi okuma ({@link #getDialedNumberSequence()}) bu aramayi sayar. Mevcut
   * hicbir iz yazimi degismez; daha once yazilmayan bir arama icin yazim noktasi eklenir.</p>
   *
   * @param dialedNumber fiilen aranacak numara (bos olamaz)
   */
  public void recordUnplannedDial(String dialedNumber) {
    if (StringUtils.isBlank(dialedNumber)) {
      throw new IllegalArgumentException("Unplanned dial must have a number for " + this);
    }
    getRetryHistory().add(dialedNumber);
    addRetryCallProcessEntry();
  }

  private boolean rawDataHasValue(String columnName) {
    return StringUtils.isNotBlank(getRawDataValue(columnName));
  }

  /**
   * Iz, gecmis listelerinin TAMAMINI temsil ediyor mu? Etmiyorsa (ornek: {@code call_process}
   * kolonu eslenmemis ya da kolon sonradan eklendigi icin eski kayitlarda bos) kursor esleme
   * eksik sayardi; bu yuzden fallback kullanilir.
   */
  private boolean isDialTrailConsistent(List<String> trail, List<String> retries, List<String> callbacks) {
    int retrySteps = 0;
    int callbackSteps = 0;
    for (String step : trail) {
      if (CALL_PROCESS_RETRY.equals(step)) {
        retrySteps++;
      } else if (CALL_PROCESS_CALLBACK.equals(step)) {
        callbackSteps++;
      }
    }
    return retrySteps == retries.size() && callbackSteps == callbacks.size();
  }

  private List<String> buildFallbackDialSequence(String firstDialedNumber, List<String> retries) {
    List<String> sequence = new ArrayList<>(retries.size() + 1);
    if (firstDialedNumber != null) {
      sequence.add(firstDialedNumber);
    }
    sequence.addAll(retries);
    return sequence;
  }

  /**
   * Kaydin fresh (ilk) aramasinda kullanilan numara: colSort'a gore ilk dolu enabled telefon
   * kolonunun degeri. {@code CallProducer.extractNumberToCallFromPhoneNumbers} ile ayni kural.
   *
   * @return numara, hic dolu kolon yoksa {@code null}
   */
  public String getFirstDialedNumber() {
    List<PhoneNumberColumn> numberColumns = getNumberColumnsInRawData();
    if (numberColumns.isEmpty()) {
      return null;
    }
    return getRawData().getString(numberColumns.get(0).getColNumber());
  }

  /**
   * Task 325559 — kaydin numara basina limit nedeniyle kapatildigini anlatan neden
   * ({@code Constants.CLOSE_REASON_*}). Yalniz limit kaynakli kapanislarda set edilir; basarili
   * kapanista (musteri yaniti + kesin sonuc kodu) bos kalir.
   */
  public String getCloseReason() {
    return closeReason;
  }

  public void setCloseReason(String closeReason) {
    this.closeReason = closeReason;
  }

  private void appendCallProcessStep(String step) {
    ensureCallProcessStartsWithFresh();
    getCallProcessHistory().add(step);
  }

  public void ensureCallProcessStartsWithFresh() {
    if (getCallProcessHistory().isEmpty()) {
      getCallProcessHistory().add(CALL_PROCESS_FRESH);
    }
  }

  private String getRawDataValue(String columnName) {
    if (rawData == null || StringUtils.isBlank(columnName) || !rawData.has(columnName)) {
      return null;
    }
    Object value = rawData.get(columnName);
    return JSONObject.NULL.equals(value) ? null : value.toString();
  }

  public Constants.CALL_TYPES getCallType() {
    return callType;
  }

  public void setCallType(Constants.CALL_TYPES callType) {
    this.callType = callType;
  }

  public boolean isAnsweredByUser() {
    return answeredByUser;
  }

  public synchronized boolean setAnsweredByUser(String username) {
    if (user != null && user.getUsername().equals(username))
      return this.answeredByUser = true;
    else
      return false;
  }

  public Double getIsFetched() {
    return isFetched;
  }

  public void setIsFetched(Double isFetched) {
    this.isFetched = isFetched;
  }

  public boolean isRetry() {
    return rawData != null && rawData.has(campaign.getColResultRetryDate());
  }

  public boolean isCallback() {
    return rawData != null && rawData.has(campaign.getColResultCallbackDate());
  }

  public Long getProductionNumber() {
    return productionNumber;
  }

  public void setProductionNumber(Long productionNumber) {
    this.productionNumber = productionNumber;
    log.info("Got production number " + productionNumber + ": " + this);
  }

  public synchronized boolean markTelephonySlotReleased() {
    if (telephonySlotReleased) {
      return false;
    }
    telephonySlotReleased = true;
    return true;
  }

  /**
   * FCT kapasite gate'i uygun kanal bulamadığında çağrı kuyruğun başına geri konur; bu sayaç kaç
   * originate tick'i ertelendiğini tutar. Sadece runtime alanıdır, DB'ye yansımaz.
   */
  public int incrementFctChannelDeferCount() {
    return ++fctChannelDeferCount;
  }

  public void resetFctChannelDeferCount() {
    fctChannelDeferCount = 0;
  }

  @Transient
  public int getFctChannelDeferCount() {
    return fctChannelDeferCount;
  }

  /**
   * cause 34 sonrası aynı çağrı içinde kaç kez sıradaki FCT ile yeniden originate edildiğini tutar.
   * `fct.channel.list.retry.max` sınırı bu sayaç üzerinden uygulanır. Sadece runtime alanıdır.
   */
  public int incrementFctListRetryCount() {
    return ++fctListRetryCount;
  }

  @Transient
  public int getFctListRetryCount() {
    return fctListRetryCount;
  }

  /**
   * FCT listesi tükendikten sonra sabit hat caller ID'siyle kaç kez yeniden originate edildiğini
   * tutar. `fct.fixed.line.retry.max` sınırı bu sayaç üzerinden uygulanır ve FCT list-retry
   * bütçesinden AYRIDIR: sabit hat FCT kapasitesiyle ilgili değildir, bu yüzden
   * `fct.channel.list.retry.max` 0 olsa bile fallback çalışır. Sadece runtime alanıdır.
   */
  public int incrementFixedLineAttemptCount() {
    return ++fixedLineAttemptCount;
  }

  public void resetFixedLineAttemptCount() {
    fixedLineAttemptCount = 0;
  }

  @Transient
  public int getFixedLineAttemptCount() {
    return fixedLineAttemptCount;
  }

  /**
   * Seçili FCT numarasının QSS'te tanımlı trunk'ı ({@code RouteDetailEntry.trunk}). Asterisk'e
   * {@code Variable: DIALER_TRUNK} olarak iletilir; boş/null ise değişken gönderilmez ve dialplan
   * varsayılan trunk'a düşer.
   *
   * <p>Caller ID ile AYNI noktada ({@code FctChannelGate.tryAcquire}) set edilir; ikisinin
   * ayrışmaması sözleşmedir. Sabit hat fallback'inde bilinçli olarak temizlenir: sabit hat QSS'te
   * tanımlı değildir, bayat trunk taşınırsa çağrı yanlış trunk'tan çıkar.
   */
  @Transient
  public String getFctTrunk() {
    return fctTrunk;
  }

  public void setFctTrunk(String fctTrunk) {
    this.fctTrunk = fctTrunk;
  }

  @Transient
  public boolean isTelephonySlotReleased() {
    return telephonySlotReleased;
  }

  public synchronized boolean markBusinessResultFinalized() {
    if (businessResultFinalized) {
      return false;
    }
    businessResultFinalized = true;
    return true;
  }

  /**
   * Telephony result code'u YALNIZ çağrı henüz finalize edilmemişse ve user result code
   * beklemiyorsa yazar. Kontrol ile yazım aynı monitör altında olduğu için atomiktir.
   *
   * <p><b>Neden gerekli:</b> origination-fail fallback'i "finalize edilmiş mi" kontrolünü yapıp
   * ARDINDAN TRC yazıyordu; latch ise {@code processCallResult} İÇİNDE alınır. Hangup thread'i
   * latch'i alıp {@code returnUserToACDEngine} (Redis round-trip) ile {@code persistFinalCdr}'ın
   * TRC'yi okuduğu an arasındayken fallback araya girip RC 907 yazabiliyordu; yani branch'in
   * düzeltmeye çalıştığı "907 gerçek sonucu eziyor" hatası daha dar bir pencerede geri geliyordu.
   *
   * <p>Latch BURADA TÜKETİLMEZ: one-shot {@link #markBusinessResultFinalized()} burada alınsaydı
   * {@code processCallResult} içindeki ikinci çağrı {@code false} dönüp çağrıyı hiç finalize
   * etmeyecek, ayrıca persist-başarısız {@link #rollbackBusinessResultFinalized()} yolu bozulacaktı.
   *
   * @return TRC yazıldıysa {@code true}; çağrı finalize edilmiş veya user result code bekliyorsa
   *         (yani yarışı gerçek sonuç kazandıysa) {@code false}
   */
  public synchronized boolean setTelephonyResultCodeIfNotFinalized(TelephonyResultCode telephonyResultCode) {
    if (businessResultFinalized || awaitingUserResultCode) {
      return false;
    }
    this.telephonyResultCode = telephonyResultCode;
    return true;
  }

  /**
   * FIX-320173: Finalize sırasında pd_cdr persist'i başarısız olursa one-shot bayrak geri
   * alınır; çağrı cache'lerden henüz çıkarılmadığı için sonraki hangup bacağı, expiry veya
   * result code mesajı finalize'ı yeniden deneyebilir. Yalnız persist-first akışının
   * persist-başarısız dalından çağrılır.
   */
  public synchronized void rollbackBusinessResultFinalized() {
    businessResultFinalized = false;
  }

  @Transient
  public boolean isBusinessResultFinalized() {
    return businessResultFinalized;
  }

  public synchronized void markAwaitingUserResultCode() {
    awaitingUserResultCode = true;
  }

  public synchronized void clearAwaitingUserResultCode() {
    awaitingUserResultCode = false;
  }

  @Transient
  public boolean isAwaitingUserResultCode() {
    return awaitingUserResultCode;
  }

  /**
   * FIX-GLB90048674 (Faz 3, Scope A): answer yolunda persisted-CDR lookup'ını tek-atış yapar.
   * İlk çağrıda {@code true} döner ve bayrağı set eder; sonraki çağrılarda {@code false} döner,
   * böylece {@code cdrService.getByInteractionId} çağrı başına en fazla bir kez tetiklenir.
   * One-shot bayrak idiom'u {@link #markBusinessResultFinalized()} ile aynıdır.
   *
   * @return lookup bu çağrıda İLK kez deneniyorsa {@code true}; zaten denenmişse {@code false}
   */
  public synchronized boolean markCdrPersistLookupAttempted() {
    if (cdrPersistLookupAttempted) {
      return false;
    }
    cdrPersistLookupAttempted = true;
    return true;
  }

  @Transient
  public boolean isCdrPersistLookupAttempted() {
    return cdrPersistLookupAttempted;
  }

  @Transient
  public String getLinkedid() {
    return linkedid;
  }

  public void setLinkedid(String linkedid) {
    this.linkedid = linkedid;
  }

  @Transient
  public boolean isTransferred() {
    return transferred;
  }

  public boolean isGammaSymbolicCarrierAssignment() {
    return gammaSymbolicCarrierAssignment;
  }

  public void setGammaSymbolicCarrierAssignment(boolean gammaSymbolicCarrierAssignment) {
    this.gammaSymbolicCarrierAssignment = gammaSymbolicCarrierAssignment;
  }

  public void setTransferred(boolean transferred) {
    this.transferred = transferred;
  }

  public boolean isAppointerTimedOut() {
    // FIX-GLB90060295: getAppointerReset() Boolean'dir; ham unboxing null'da NPE atiyordu.
    return Boolean.TRUE.equals(campaign.getAppointerReset())
        && campaign.getAppointerResetTimeout() != null && rawData != null
        && rawData.has(campaign.getColResultCallbackDate()) && Optional.of(rawData)
        .map(r -> (String) r.get(campaign.getColResultCallbackDate()))
        .map(Double::parseDouble)
        .map(Utils::convertDoubleToLongDate)
        .map(callbackDate -> new Date().getTime() > callbackDate + campaign.getAppointerResetTimeout() * 1000)
        .orElse(false);
  }

  public boolean isBetaPredictive() {
    return campaign != null &&
            campaign.isPredictiveCampaign() &&
            campaign.getPredictiveStrategy() == Constants.PREDICTIVE_STRATEGY.BETA;
  }

  public boolean isGammaPredictive() {
    return campaign != null &&
            campaign.isPredictiveCampaign() &&
            campaign.getPredictiveStrategy() == Constants.PREDICTIVE_STRATEGY.GAMMA;
  }

  public String getCallerId() {
    return callerId;
  }

  public void setCallerId(String callerId) {
    this.callerId = callerId;
  }

  public int getAttempts() {
    setAttemptsFromRawData();
    return attempts;
  }

  public void snapshotAttemptsBeforeOrigination() {
    setAttemptsFromRawData();
    attemptsBeforeOrigination = attempts;
  }

  public void incrementAttempts() {
    setAttemptsFromRawData();
    attempts++;
  }

  public boolean restoreAttemptsBeforeOriginationForNonAttemptResult() {
    if (telephonyResultCode == null || !Constants.isTelephonyResultCodeExcludedFromAttempts(telephonyResultCode.getCode())) {
      attemptsBeforeOrigination = null;
      return false;
    }
    return restoreAttemptsBeforeOrigination();
  }

  public boolean restoreAttemptsBeforeOrigination() {
    if (attemptsBeforeOrigination == null) {
      return false;
    }
    attempts = attemptsBeforeOrigination;
    attemptsBeforeOrigination = null;
    return true;
  }

  public void setAttemptsFromRawData() {
    if (attempts == null) {
      attempts = rawData.has(campaign.getColResultAttempts()) ? rawData.getInt(campaign.getColResultAttempts()) : 0;
    }
  }

  /**
   * Appointer'a ulaşılamadığında (Pause/meşgul) artırılan agent-callback deneme sayacı (task 321997).
   * Telefon retry sayacı olan {@link #attempts}'ten ayrıdır; genel Max Attempts'i etkilemez.
   * Değer result tablosundaki col_result_callback_attempts kolonundan lazy okunur; her fetch'te
   * yeni Call kurulduğu için return-to-DB ve node restart'ını atlatır. Artış sunucu tarafında
   * (rescheduleAppointedCallback UPDATE'i) yapılır; burada in-memory increment yoktur. Sadece
   * reschedule UPDATE'inin optimistic guard'ı (expectedOld) için okunur.
   */
  public int getCallbackAttempts() {
    setCallbackAttemptsFromRawData();
    return callbackAttempts;
  }

  public void setCallbackAttemptsFromRawData() {
    if (callbackAttempts == null) {
      callbackAttempts = (campaign.getColResultCallbackAttempts() != null
          && rawData != null
          && rawData.has(campaign.getColResultCallbackAttempts()))
          ? rawData.getInt(campaign.getColResultCallbackAttempts())
          : 0;
    }
  }

  @Transient
  public CDR getCdr() {
    return cdr;
  }

  @Transient
  public Filter getAppliedSegmentationFilter() {
    return appliedSegmentationFilter;
  }

  public void setAppliedSegmentationFilter(Filter appliedSegmentationFilter) {
    this.appliedSegmentationFilter = appliedSegmentationFilter;
  }

  @Transient
  public Set<Skill> getSkills() {
    Set<Skill> skills = new HashSet<>();
    if (CollectionUtils.isNotEmpty(campaign.getSkills())) {
      skills.addAll(campaign.getSkills());
    }
    if (StringUtils.isNotEmpty(campaign.getColSkill()) && rawData.has(campaign.getColSkill())) {
      String skillName = rawData.getString(campaign.getColSkill().trim());
      if (StringUtils.isNotEmpty(skillName))
        skills.add(new Skill(skillName));
    }
    return skills;
  }

  public String getLscResponseValues() {
    return lscResponseValues;
  }

  public void setLscResponseValues(String lscResponseValues) {
    this.lscResponseValues = lscResponseValues;
  }

  public Constants.CALLBACKTYPE getCallbackType() {
    return callbackType;
  }

  public void setCallbackType(Constants.CALLBACKTYPE callbackType) {
    this.callbackType = callbackType;
  }

  public Date getCreateDate() {
    return createDate;
  }

  public void setAnsweredByUser(boolean answeredByUser) {
    this.answeredByUser = answeredByUser;
  }

  public void setCdr(CDR cdr) {
    this.cdr = cdr;
  }

  public void setAttempts(Integer attempts) {
    this.attempts = attempts;
  }

  public JSONObject getRawData2() {
    return rawData2;
  }

  public void setRawData2(JSONObject rawData2) {
    this.rawData2 = rawData2;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    Call other = (Call) obj;
    if (id == null) {
      return other.id == null;
    } else
      return id.equals(other.id);
  }

  @Override
  public String toString() {
    return "Call [object=" + super.toString() + ", id=" + id + ", uniqueKey=" + uniqueKey + ", calledNumber=" + calledNumber + ", channel=" + channel + ", telephonyResultCode=" + telephonyResultCode
        + ", userResultCode=" + userResultCode + ", resultCodeSequence=" + (resultCodeSequence == null ? "null" : resultCodeSequence) + ", user=" + user + ", callBackNumber="
        + callbackNumber + ", callBackDate=" + callbackDate + ", retryNumber=" + retryNumber + ", retryDate=" + (retryDate == null ? "null" : retryDate) + ", retryHistory="
        + retryHistory + ", DNCNumbers=" + DNCNumbers + ", originationDate=" + originationDate + ", startDate=" + startDate + ", answerDate=" + answerDate + ", completeDate="
        + completeDate + ", campaign=" + campaign + ", durationFromAnswer=" + durationFromAnswer + ", durationFromOrigination=" + durationFromOrigination + ", orphaned=" + orphaned
        + ", createDate=" + createDate + ", active=" + isActive() + ", abandoned=" + abandoned + ", answeredByAgent=" + answeredByUser + ", productionNumber=" + productionNumber + "]";
  }

}
