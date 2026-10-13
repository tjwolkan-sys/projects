package com.powerdialer.engine.dialer.retry;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.powerdialer.engine.CallCleaner;
import org.apache.commons.collections4.ListUtils;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Template class for retries
 */
public abstract class RetryStrategy {

  private static final Logger log = LoggerFactory.getLogger(RetryStrategy.class);

  protected Call call;
  protected Campaign campaign;

  /**
   * Task 325559 — numara basina arama sayaci, KARAR BASINA BIR KEZ hesaplanip burada tutulur.
   * Strateji nesnesi her retry icin yeniden uretildigi icin (bkz.
   * {@code RetryStrategyFactory.createStrategy}) instance-level cache guvenlidir; static state
   * gerekmez. {@link #createRetry(Call)} girisinde ayrica sifirlanir.
   */
  private Map<String, Integer> dialCountsByNumber;
  private List<String> candidateNumbers;

  /**
   * Check if overall retry limis is reached for call
   */
  public abstract boolean isOverallRetryLimitReached();

  /**
   * Set number for retry
   */
  public abstract void setRetryNumber();

  /**
   * Create retry for given call
   */
  public void createRetry(Call call) {
    this.call = call;
    this.campaign = call.getCampaign();
    this.dialCountsByNumber = null;
    this.candidateNumbers = null;
    // Basarili kapanis (musteri yaniti + kesin sonuc kodu) numara limitlerinden ONCE degerlendirilir
    // ve bu yolda kapanis nedeni YAZILMAZ; aksi halde rapor "limit doldu" gibi okunurdu.
    // Kontrol sirasi davranisin parcasidir: basarili kapanis -> genel limit -> numara basina limit
    // tukenmesi (325559) -> kullanilmayan numara (325470) -> stratejinin normal numara secimi.
    if (isCallAnswered() && !isResultCodeAllowsRetries()) {
      completeCallWithoutRetry();
      return;
    }
    if (isOverallRetryLimitReached()) {
      completeCallWithoutRetry();
      return;
    }
    if (noEligibleNumberLeft()) {
      // Task 325559 (Kriter 2): genel limit dolmamis olsa dahi aranabilir tum numaralarin
      // bireysel limiti dolduysa kayit kapatilir.
      applyExhaustedCloseReason();
      completeCallWithoutRetry();
      return;
    }
    if (isUnreachableNumberResult()) {
      // FEAT-325470: aranan numara "kullanilmayan numara" (AMD bad number) cikti. Ayni numaraya
      // retry kurulmaz; strateji ne olursa olsun halkada sonraki uygun numaraya gecilir, aday
      // yoksa kayit kapanir. Zaman hesabi (asagida) stratejinin normal hesabi olarak kalir.
      if (!switchRetryNumberAfterUnreachable()) {
        completeCallWithoutRetry();
        return;
      }
    } else {
      try {
        if (call.isCallback() && call.getCallbackNumber() != null) {
          call.setRetryNumber(call.getCallbackNumber());
          call.setCallbackDate(null);
          call.setCallbackNumber(null);
          // Task 325559: callback numarasi da numara basina limite tabidir. Yuruyus YALNIZ ozellik
          // aktifken uygulanir; aksi halde bu dalin bugunku DNC-kontrolsuz davranisi degisirdi.
          if (isPerNumberLimitActive()) {
            processRetryNumberWithDNC();
          }
        } else {
          setRetryNumber();
        }
      } catch (NoEligibleRetryNumberException e) {
        log.error("No eligible retry number left, completing instead of retry for {}", call, e);
        applyExhaustedCloseReason();
        completeCallWithoutRetry();
        return;
      }
    }

    setRetryDateAndIncrementRetryCounts();
    updateRetryHistory();
    /* Deleted for task 283255 - deleting callbackDate after when created retry
    if (call.isCallback() && call.getCampaign().getCallbackToAppointer()) {
      log.info("Duplicating retry date and number to callback fields for appointed {}", call);
      call.setCallbackNumber(call.getRetryNumber());
      call.setCallbackDate(call.getRetryDate());
    }
    */
    log.info("Retry scheduled: {}", call);
  }

  /**
   * FEAT-325470 — cagrinin telephony sonucu "kullanilmayan numara" (AMD bad number, code 113) mi?
   * Karar {@code pd_telephony_rc.code} ile verilir; id ortamlar arasi guvenilir degildir.
   */
  private boolean isUnreachableNumberResult() {
    return call.getTelephonyResultCode() != null
        && call.getTelephonyResultCode().getCode() == Constants.TELEPHONY_RC_AMD_BAD_NUMBER;
  }

  /**
   * FEAT-325470 — aranan numarayi bu karar icin gecersiz sayar ve retry numarasini halkada sonraki
   * uygun numaraya cevirir. Aday yoksa kapanis nedenini yazar ve {@code false} doner; cagiran
   * kaydi kapatir.
   *
   * <p>Kalicilik yoktur (S5): gecersizlik yalniz o anki aranan numara icin ve bu karar suresince
   * gecerlidir. Birden fazla numarasi da 113 alan kayitta halka ilk numaraya geri donebilir;
   * dongu genel attempt limiti ve (aktifse) numara basina limitle sinirlidir — bilincli kabul.</p>
   *
   * <p>Gecersizlik {@link #isNumberIneligible(String)} predicate'ine BILEREK eklenmez: 325559'un
   * {@link #noEligibleNumberLeft()} guard'i o predicate ile "limit tukendi" nedenini yazar; bad
   * number kaynakli kapanis ayni etiketi alsaydi rapor yaniltirdi.</p>
   *
   * <p>Callback plani (agent'in girdigi numara dahil) bu yolda mevcut callback→retry
   * donusumuyle ayni sekilde temizlenir; kapanan veya numara degistiren kayit bekleyen callback
   * tasimaz.</p>
   */
  private boolean switchRetryNumberAfterUnreachable() {
    String unreachableNumber = call.getCalledNumber();
    String nextNumber = selectNextNumberAfterUnreachable(unreachableNumber);
    if (nextNumber == null) {
      log.info("Unreachable number {} and no other callable number left, completing without retry: {}",
          unreachableNumber, call);
      call.setCloseReason(Constants.CLOSE_REASON_ALL_NUMBERS_UNREACHABLE);
      return false;
    }
    if (call.isCallback()) {
      call.setCallbackDate(null);
      call.setCallbackNumber(null);
    }
    call.setRetryNumber(nextNumber);
    log.info("Unreachable number {}, retry switched to {} for {}", unreachableNumber, nextNumber, call);
    return true;
  }

  /**
   * Halkada (colSort sirali, dolu telefon kolonlari) gecersiz numaradan SONRAKI ilk uygun numarayi
   * bulur. Gecersiz numara kolonlarda yoksa (ornek: kolon disi callback numarasi) tarama bastan
   * baslar. En fazla bir tam tur doner, istisna firlatmaz; uygun aday yoksa {@code null}.
   * Uygunluk: gecersiz numaranin kendisi degil VE {@link #isNumberIneligible(String)} degil
   * (DNC + aktifse numara basina limit).
   */
  private String selectNextNumberAfterUnreachable(String unreachableNumber) {
    List<String> numbers = call.getNumbersInRawData();
    if (numbers.isEmpty()) {
      return null;
    }
    String normalizedUnreachable = Call.normalizeNumberForLimit(unreachableNumber);
    int start = 0;
    for (int index = 0; index < numbers.size(); index++) {
      if (isSameNumber(numbers.get(index), normalizedUnreachable)) {
        start = index + 1;
        break;
      }
    }
    for (int step = 0; step < numbers.size(); step++) {
      String candidate = numbers.get((start + step) % numbers.size());
      if (isSameNumber(candidate, normalizedUnreachable) || isNumberIneligible(candidate)) {
        continue;
      }
      return candidate;
    }
    return null;
  }

  private static boolean isSameNumber(String number, String normalizedOther) {
    String normalized = Call.normalizeNumberForLimit(number);
    return normalized != null && normalized.equals(normalizedOther);
  }

  private void completeCallWithoutRetry() {
    log.info("No more retries to schedule. Completing: {}", call);
    if (call.getCallOriginator() != null) {
      call.getCallOriginator().getCallCleaner().completeCall(call);
    } else {
      call.setCompleteDate(Utils.convertDateToDouble(new Date()));
    }
  }

  /**
   * Check if call is answered by client
   */
  public boolean isCallAnswered() {
    return call.getTelephonyResultCode() != null && call.getTelephonyResultCode().getCode() == Constants.TELEPHONY_RC_NORMAL_CLEARING;
  }

  private boolean isResultCodeAllowsRetries() {
    return call.getUserResultCode() != null && (!call.getUserResultCode().isFinalStatus() || call.getUserResultCode().isDncIdentifier());
  }

  /**
   * Set retry date for call, increment total retries and retries in macro
   */
  public void setRetryDateAndIncrementRetryCounts() {
    double plannedRetryDate = calculatePlannedRetryDate();

    if (isRetryLimitInMacroReached())
      plannedRetryDate += (campaign.getMacroInterval() / 24D);

    if (isPlannedRetryDateExceedScope(plannedRetryDate))
      plannedRetryDate = calculateRetryDateFromNextDay(plannedRetryDate);

    call.setRetryDate(plannedRetryDate);
  }

  private void updateRetryHistory() {
    call.getRetryHistory().add(call.getRetryNumber());
    call.addRetryCallProcessEntry();
  }

  /**
   * Calculate the date for retry
   */
  public double calculatePlannedRetryDate() {
    Calendar plannedRetryDate = Calendar.getInstance();
    plannedRetryDate.setTime(new Date());

    if (campaign.getRetryDelay() != null)
      plannedRetryDate.add(Calendar.SECOND, (int) Math.round(campaign.getRetryDelay() * 60));

    return Utils.convertDateToDouble(plannedRetryDate.getTime());
  }

  /**
   * Check if retry limit is reached in macro
   */
  public boolean isRetryLimitInMacroReached() {
    int retryTotal = call.getRetryHistory().size();
    return retryTotal != 0 && retryTotal % campaign.getMacroMaxAttempt() == 0;
  }

  /**
   * Check if planned retry date is not after call till time limit
   */
  public boolean isPlannedRetryDateExceedScope(Double plannedRetryDate) {
    Double canSetRetryTill = call.getCallToTimeFromRawData();
    return (canSetRetryTill != null && plannedRetryDate != null) && (canSetRetryTill < plannedRetryDate % 1);
  }

  /**
   * Calculate retry date from next day with 'from time' value of call
   */
  public double calculateRetryDateFromNextDay(Double plannedRetryDate) {
    long nextDay = plannedRetryDate.longValue() + 1;
    return nextDay + call.getCallFromTimeFromRawData();
  }

  public boolean anyRetryLeft() {
    if (campaign.getCountAllCallTypes() != null && campaign.getCountAllCallTypes()) {

      return call.getAttempts() < campaign.getRetryOveralAttempt();
    }

    return call.getRetryHistory().size() < campaign.getRetryOveralAttempt();
  }

  public boolean isCalledNumberInDNC() {
    return call.getDNCNumbers().contains(call.getCalledNumber());
  }

  public boolean anyNumberPresentAfterDNC() {
    return ListUtils.removeAll(call.getNumbersInRawData(), call.getDNCNumbers()).size() > 0;
  }

  public void processRetryNumberWithDNC() {
    advanceRetryNumberPastIneligible(this::isNumberIneligible);
  }

  /**
   * Task 325559 — numara aranamaz mi? DNC'de olmasi VEYA numara basina limitini doldurmus olmasi.
   *
   * <p>Ozellik kapali oldugunda ({@link #isPerNumberLimitActive()} false) sonuc birebir eski
   * DNC-only davranisa duser; alan bos olan kampanyalarda hicbir sey degismez.</p>
   */
  protected boolean isNumberIneligible(String number) {
    return call.getDNCNumbers().contains(number) || isNumberAttemptLimitReached(number);
  }

  /**
   * Numara basina azami arama limiti dolmus mu?
   */
  protected boolean isNumberAttemptLimitReached(String number) {
    if (!isPerNumberLimitActive()) {
      return false;
    }
    String normalized = Call.normalizeNumberForLimit(number);
    if (normalized == null) {
      return false;
    }
    Integer counted = getDialCountsByNumber().get(normalized);
    return counted != null && counted >= campaign.getMaxAttemptPerNumber();
  }

  /**
   * Aranabilir (DNC disi, dolu) numaralarin TAMAMI bireysel limitini doldurdu mu? Kaydin hic dolu
   * numarasi yoksa da true doner.
   *
   * <p>Ozellik kapaliyken her zaman false doner; erken kapatma yalnizca opsiyonel alan
   * doldurulmus kampanyalarda devreye girer.</p>
   */
  protected boolean noEligibleNumberLeft() {
    if (!isPerNumberLimitActive()) {
      return false;
    }
    for (String number : getCandidateNumbers()) {
      if (!isNumberIneligible(number)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Numara basina limit bu kampanya ve strateji icin uygulanir mi?
   *
   * <p>Alan bos veya {@code <= 0} ise ozellik kapalidir. {@code REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER}
   * (K2) ve {@code REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER} (karar 29.09.2026) stratejilerinde alan YOK
   * SAYILIR: ikisi de numara basina siniri zaten {@code retryOveralAttempt} ile uyguluyor. Kontrol
   * hem kampanya alanina hem de {@link ReachRetryPerNumber} / {@link ReachRetrySingleNumber}
   * override'larina dayanir; boylece alan API/Redis uzerinden dolu gelse bile o stratejilerde
   * etkisiz kalir.</p>
   */
  protected boolean isPerNumberLimitActive() {
    return campaign != null
        && Campaign.isPerNumberAttemptLimitActive(campaign.getMaxAttemptPerNumber(), campaign.getRetryStrategy());
  }

  /**
   * Numara -> arama sayisi haritasi. Karar basina BIR KEZ hesaplanir; kaynak
   * {@link Call#getDialedNumberSequence()} (hangup SONRASI okuma).
   *
   * <p>Anahtarlar {@link Call#normalizeNumberForLimit(String)} ile normalize edilir; sorgu tarafi
   * da ayni kurali uygular. Aksi halde bosluklu bir kolon degeri harita anahtarini kaydirir,
   * sayim 0 kabul edilir ve limit sessizce hic uygulanmaz.</p>
   */
  protected Map<String, Integer> getDialCountsByNumber() {
    if (dialCountsByNumber == null) {
      Map<String, Integer> counts = new HashMap<>();
      for (String number : call.getDialedNumberSequence()) {
        String normalized = Call.normalizeNumberForLimit(number);
        if (normalized != null) {
          Integer current = counts.get(normalized);
          counts.put(normalized, current == null ? 1 : current + 1);
        }
      }
      dialCountsByNumber = counts;
    }
    return dialCountsByNumber;
  }

  /**
   * Numara basina limitin aday numaralari (normalize, tekil): telefon kolonlari + bekleyen
   * callback numarasi. Karar basina bir kez alinir; {@code getLimitCandidateNumbers} her cagrida
   * enabled kolon listesini yeniden siraladigi icin tekrar cagrilmaz.
   */
  private List<String> getCandidateNumbers() {
    if (candidateNumbers == null) {
      candidateNumbers = call.getLimitCandidateNumbers();
    }
    return candidateNumbers;
  }

  /**
   * Numara basina limit kaynakli kapanisin nedenini yazar. Ozellik kapaliyken hicbir sey yazilmaz;
   * boylece DNC tukenmesi gibi mevcut kapanis yollari etkilenmez.
   *
   * <p><b>Neden GERCEK nedeni anlatir (Faz 7 / P2.1a).</b> {@link #noEligibleNumberLeft()} DNC'yi
   * de "uygun degil" saydigi icin, tum numaralari DNC'de olan kayit da bu daldan kapanabilir.
   * Oyle bir kapanista numara-limiti metni YAZILMAZ: kapanisin kendisi dogrudur (strateji 0 ve 4
   * icin bu dal DNC tukenmesine karsi yeni ve dogru bir kapanis yolu) ama etiketi yanlis olurdu
   * ve raporu okuyan is birimini yaniltirdi.</p>
   *
   * <p>Yeni bir {@code CLOSE_REASON_*} sabiti EKLENMEDI; DNC kapanisinda neden bos birakilir.
   * Gerekce: yeni bir musteri-gorunur metin is birimiyle mutabik degil, lokalizasyon karari
   * (K14) hala acik ve bos birakmak DNC kapanislarinda ozellik-kapali davranisi birebir korur.</p>
   */
  private void applyExhaustedCloseReason() {
    if (!isPerNumberLimitActive()) {
      return;
    }
    List<String> candidates = getCandidateNumbers();
    if (candidates.isEmpty()) {
      call.setCloseReason(Constants.CLOSE_REASON_NO_CALLABLE_NUMBER);
      return;
    }
    if (!anyCandidateReachedItsLimit(candidates)) {
      return;
    }
    call.setCloseReason(call.isCallback()
        ? Constants.CLOSE_REASON_MAX_ATTEMPT_ON_CALLBACK
        : Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED);
  }

  /**
   * Adaylardan en az biri kendi limitini doldurdu mu? Hicbiri doldurmadiysa kapanis LIMIT
   * kaynakli degildir (tek kalan sebep DNC'dir).
   */
  private boolean anyCandidateReachedItsLimit(List<String> candidates) {
    for (String number : candidates) {
      if (isNumberAttemptLimitReached(number)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Retry numarasini uygun olmayan numaralari atlayarak ilerletir. Numara
   * halkasi (getNextNumberFromRawData) hic null donmedigi icin yuruyus en
   * fazla bir tam turla sinirlanir; tur sonunda aday kalmadiysa
   * NoEligibleRetryNumberException firlatilir (03.08.2026 webphone-event
   * sonsuz dongu vakasinin koruma katmani).
   */
  protected void advanceRetryNumberPastIneligible(Predicate<String> ineligible) {
    if (!ineligible.test(call.getRetryNumber()))
      return;
    int remainingAdvances = call.getNumbersInRawData().size() + 1;
    while (ineligible.test(call.getRetryNumber())) {
      if (remainingAdvances-- == 0)
        throw new NoEligibleRetryNumberException("No callable retry number left in raw data for " + call);
      call.setRetryNumber(call.getNextNumberFromRawData());
    }
  }

}
