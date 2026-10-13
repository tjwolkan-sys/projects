package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.retry.ReachRetryPerNumber;
import com.powerdialer.engine.dialer.retry.ReachRetrySingleNumber;
import com.powerdialer.engine.dialer.retry.RetryRulesCycle;
import com.powerdialer.engine.dialer.retry.RetryStrategy;
import com.powerdialer.engine.dialer.retry.RoundRobinNextCycle;
import com.powerdialer.engine.dialer.retry.RoundRobinNextNumber;
import com.powerdialer.engine.dialer.retry.RoundRobinPerCycle;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.ResultCodeSchedule;
import com.powerdialer.model.ResultCodeScheduleAdvanced;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.util.Constants;

/**
 * FEAT-325559 Faz 1 karakterizasyonu.
 *
 * <p>Alti retry stratejisinin numara secim ve limit karari, "numara basina
 * azami arama limiti" ozelligi eklenmeden ONCE kilitlenir. Faz 3'te ortak
 * eligibility predicate genellestirilecegi icin bu testlerin tamami
 * <b>feature off</b> (yeni alan bos) senaryosunda aynen yesil kalmalidir.</p>
 *
 * <p>Ozellikle kilitlenen kritik davranis: strateji 2 ve 3'te
 * {@code retryOveralAttempt} kayit bazli DUZ bir tavandir ve round-robin onu
 * yalnizca dolu numaralara bolustur. Talebin degistirmeyi istedigi davranis
 * budur; degisimin bilincli oldugu buradan gorulur.</p>
 */
public class RetryStrategySixStrategiesBaselineCharacterizationTest {

  private static final int OVERALL_ATTEMPT = 3;
  private static final int MACRO_MAX_ATTEMPT = 3;

  private final Call call = spy(new Call());
  private final Campaign campaign = mock(Campaign.class);
  private final JSONObject rawData = mock(JSONObject.class);
  private final CallCleaner callCleaner = mock(CallCleaner.class);
  private final CallOriginator callOriginator = mock(CallOriginator.class);
  private final List<PhoneNumberColumn> numberColumns = new ArrayList<>();

  @Before
  public void setup() {
    when(campaign.getColResultRetryHistory()).thenReturn("retry_history");
    when(campaign.getEnabledPhoneNumberColumns()).thenReturn(numberColumns);
    when(campaign.getRetryOveralAttempt()).thenReturn(OVERALL_ATTEMPT);
    when(campaign.getMacroMaxAttempt()).thenReturn(MACRO_MAX_ATTEMPT);
    when(campaign.getMacroInterval()).thenReturn(24);
    when(campaign.getRetryDelay()).thenReturn(1D);
    when(campaign.getResultCodeSchedules()).thenReturn(new ArrayList<ResultCodeSchedule>());

    when(call.getCallOriginator()).thenReturn(callOriginator);
    when(callOriginator.getCallCleaner()).thenReturn(callCleaner);

    call.setCampaign(campaign);
    call.setRawData(rawData);

    addPhoneColumn("phone_number_1", "number_1");
    addPhoneColumn("phone_number_2", "number_2");
    addPhoneColumn("phone_number_3", "number_3");
    call.setCalledNumber("number_1");
  }

  private void addPhoneColumn(String columnName, String phoneNumber) {
    PhoneNumberColumn column = new PhoneNumberColumn();
    column.setColNumber(columnName);
    numberColumns.add(column);
    when(rawData.has(columnName)).thenReturn(true);
    when(rawData.getString(columnName)).thenReturn(phoneNumber);
  }

  private void setRetryHistory(String history) {
    when(rawData.has("retry_history")).thenReturn(true);
    when(rawData.getString("retry_history")).thenReturn(history);
  }

  private static void injectStrategyState(RetryStrategy strategy, Call call, Campaign campaign) throws Exception {
    Field callField = RetryStrategy.class.getDeclaredField("call");
    callField.setAccessible(true);
    callField.set(strategy, call);
    Field campaignField = RetryStrategy.class.getDeclaredField("campaign");
    campaignField.setAccessible(true);
    campaignField.set(strategy, campaign);
  }

  // ---------------------------------------------------------------------
  // Strateji 0 - REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER
  // ---------------------------------------------------------------------

  @Test
  public void singleNumberStrategyKeepsDialingCalledNumberEvenWhenOtherColumnsExist() {
    setRetryHistory("number_1");

    new ReachRetrySingleNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_1", call.getRetryNumber());
    assertNotNull(call.getRetryDate());
  }

  @Test
  public void singleNumberStrategyCompletesWhenFlatOverallLimitReached() {
    setRetryHistory("number_1;number_1;number_1");

    new ReachRetrySingleNumber().createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
  }

  // ---------------------------------------------------------------------
  // Strateji 1 - REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER
  // ---------------------------------------------------------------------

  /**
   * Bu strateji numara basina limiti BUGUN de uyguluyor; limit alani olarak
   * {@code retryOveralAttempt} kullaniliyor. FEAT-325559'da yeni alan bu
   * stratejide bilincli olarak YOK SAYILACAK (K2), dolayisiyla bu davranis
   * degismemelidir.
   */
  @Test
  public void perNumberStrategyMovesToNextNumberWhenCurrentNumberHitsOverallAttempt() {
    setRetryHistory("number_1;number_1;number_1");

    new ReachRetryPerNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
  }

  @Test
  public void perNumberStrategyCompletesOnlyWhenEveryNumberHitsItsOwnLimit() {
    setRetryHistory("number_1;number_1;number_1;number_2;number_2;number_2;number_3;number_3;number_3");

    new ReachRetryPerNumber().createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
  }

  // ---------------------------------------------------------------------
  // Strateji 2 - ROUND_ROBIN ... SCHEDULE_NEXT_NUMBER
  // ---------------------------------------------------------------------

  @Test
  public void nextNumberStrategyRotatesToFollowingColumn() {
    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
  }

  /**
   * FEAT-325559'un degistirdigi CEKIRDEK davranis: tavan kayit bazli ve DUZ.
   * Uc numaraya birer arama dagitilmis olsa bile toplam 3'e ulasildigi icin
   * kayit kapanir; numaralarin bireysel dagilimina bakilmaz.
   */
  @Test
  public void nextNumberStrategyCompletesOnFlatOverallLimitRegardlessOfPerNumberSpread() {
    setRetryHistory("number_1;number_2;number_3");

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
  }

  /**
   * Ayni duz tavan, tum aramalar TEK numaraya inmis olsa da ayni noktada
   * kapatir. Yani bugun numara basina bagimsiz bir ust sinir YOK.
   */
  @Test
  public void nextNumberStrategyAppliesNoPerNumberCeilingBeforeOverallLimit() {
    setRetryHistory("number_1;number_1");

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
  }

  // ---------------------------------------------------------------------
  // Strateji 3 - ROUND_ROBIN ... SCHEDULE_NEXT_CYCLE
  // ---------------------------------------------------------------------

  @Test
  public void nextCycleStrategySelectsSameNumberAsNextNumberStrategy() {
    new RoundRobinNextCycle().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
  }

  @Test
  public void nextCycleStrategyCompletesOnFlatOverallLimitLikeNextNumberStrategy() {
    setRetryHistory("number_1;number_2;number_3");

    new RoundRobinNextCycle().createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
  }

  // ---------------------------------------------------------------------
  // Strateji 4 - ROUND_ROBIN ... PER_CYCLE_RETRY_LIMIT
  // ---------------------------------------------------------------------

  /**
   * Numara secimi halka yuruyusu DEGIL, gecmis uzunluguna dayali indeks
   * aritmetigidir: {@code (retryHistory.size() + 1) % numberColumns.size()}.
   */
  @Test
  public void perCycleStrategySelectsNumberByHistoryIndexArithmetic() {
    new RoundRobinPerCycle().createRetry(call);
    assertEquals("number_2", call.getRetryNumber());

    call.setRetryNumber(null);
    call.setRetryHistory(null);
    setRetryHistory("number_2");
    new RoundRobinPerCycle().createRetry(call);
    assertEquals("number_3", call.getRetryNumber());

    call.setRetryNumber(null);
    call.setRetryHistory(null);
    setRetryHistory("number_2;number_3");
    new RoundRobinPerCycle().createRetry(call);
    assertEquals("number_1", call.getRetryNumber());
  }

  /**
   * Strateji 4 tavani numara sayisiyla OLCEKLIYOR: esik
   * {@code retryOveralAttempt * numberSize}. Bu yuzden strateji 2/3'un
   * kapandigi noktada (gecmis = 3) burada hala retry planlanir. Talep
   * metnindeki "bu strateji de limit ustu arar" ifadesi kodla uyusmuyor.
   */
  @Test
  public void perCycleStrategyScalesOverallLimitWithNumberCount() {
    setRetryHistory("number_1;number_2;number_3");

    new RoundRobinPerCycle().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertNotNull(call.getRetryDate());
  }

  // ---------------------------------------------------------------------
  // Strateji 5 - RETRY_RULES_MANAGES_CYCLE
  // ---------------------------------------------------------------------

  /**
   * Result code schedule tanimli DEGILSE bu strateji numarayi hic dondurmez;
   * ayni numarada kalir ve duz genel limitle kapanir.
   */
  @Test
  public void retryRulesCycleWithoutScheduleStaysOnSameNumber() {
    setRetryHistory("number_1");

    new RetryRulesCycle().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_1", call.getRetryNumber());
  }

  @Test
  public void retryRulesCycleWithoutScheduleCompletesOnFlatOverallLimit() {
    setRetryHistory("number_1;number_1;number_1");

    new RetryRulesCycle().createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
  }

  /**
   * Denizbank kampanyalarinin dordu bu stratejide ve result code schedule
   * tanimli. Advanced kural {@code NEXT} oldugunda numara halkada ilerler ve
   * ardindan {@code processRetryNumberWithDNC} yuruyusu uygulanir - yani Faz
   * 3'te genellestirilecek ortak predicate bu stratejiyi de etkiler.
   */
  @Test
  public void retryRulesCycleWithAdvancedNextRuleRotatesToFollowingNumber() {
    TelephonyResultCode userBusy = new TelephonyResultCode();
    userBusy.setCode(17);

    ResultCodeScheduleAdvanced advanced = new ResultCodeScheduleAdvanced();
    advanced.setEnabled(true);
    advanced.setDialAttempt(1);
    advanced.setRetryInterval(30D);
    advanced.setNumberToTry(Constants.NUMBER_TO_TRY.NEXT);

    ResultCodeSchedule schedule = new ResultCodeSchedule();
    schedule.setEnabled(true);
    schedule.setTelephonyResultCode(userBusy);
    schedule.setApplyTo(Constants.APPLY_TO.RETRIES);
    schedule.setRetryAmount(10);
    schedule.setRetryInterval(30D);
    schedule.setAdvancedIntervalEnabled(true);
    schedule.setMacroCycle(false);
    schedule.setMinScheduleTime(timeOfDay(9, 0));
    schedule.setMaxScheduleTime(timeOfDay(23, 50));
    schedule.setResultCodeScheduleAdvanceds(new ArrayList<>(Arrays.asList(advanced)));

    when(campaign.getResultCodeSchedules()).thenReturn(new ArrayList<>(Arrays.asList(schedule)));
    call.setTelephonyResultCode(userBusy);
    call.setResultCodeSequence(1);

    new RetryRulesCycle().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
  }

  // ---------------------------------------------------------------------
  // Macro limit predicate'i: base DUZ, per-cycle OLCEKLI
  // ---------------------------------------------------------------------

  @Test
  public void macroLimitPredicateIsFlatForBaseTemplateAndScaledForPerCycle() throws Exception {
    setRetryHistory("number_1;number_2;number_3");

    RetryStrategy baseTemplate = new RoundRobinNextNumber();
    injectStrategyState(baseTemplate, call, campaign);
    assertTrue("Base template: gecmis toplami macroMaxAttempt katiysa macro siniri dolmus sayilir",
        baseTemplate.isRetryLimitInMacroReached());

    RetryStrategy perCycle = new RoundRobinPerCycle();
    injectStrategyState(perCycle, call, campaign);
    assertFalse("Per-cycle: esik macroMaxAttempt * numara sayisi oldugu icin ayni gecmiste dolmaz",
        perCycle.isRetryLimitInMacroReached());
  }

  /**
   * Gecmis bos oldugunda hicbir stratejide macro siniri dolmus sayilmaz.
   */
  @Test
  public void macroLimitPredicateIsNotReachedOnEmptyHistory() throws Exception {
    RetryStrategy baseTemplate = new RoundRobinNextNumber();
    injectStrategyState(baseTemplate, call, campaign);
    assertFalse(baseTemplate.isRetryLimitInMacroReached());
  }

  /**
   * Tek dolu numarasi olan kayitta strateji 4'un cycle kavrami her aramada
   * dolar; bu, per-cycle gecikme mantiginin sinir durumudur.
   */
  @Test
  public void perCycleStrategyTreatsSingleNumberRecordAsCycleAlwaysReached() throws Exception {
    numberColumns.clear();
    addPhoneColumn("phone_number_1", "number_1");

    RoundRobinPerCycle perCycle = new RoundRobinPerCycle();
    injectStrategyState(perCycle, call, campaign);

    assertTrue(perCycle.isRetryCycleReached());
  }

  @Test
  public void dncNumbersAreExcludedFromCandidateSetInBaseTemplate() throws Exception {
    call.setDNCNumbers(new java.util.HashSet<>(Collections.singletonList("number_1")));

    RetryStrategy baseTemplate = new RoundRobinNextNumber();
    injectStrategyState(baseTemplate, call, campaign);

    assertTrue("DNC disinda numara kaldigi surece aday kumesi bos degildir",
        baseTemplate.anyNumberPresentAfterDNC());
    assertTrue(baseTemplate.isCalledNumberInDNC());
  }

  private static Date timeOfDay(int hour, int minute) {
    Calendar calendar = Calendar.getInstance();
    calendar.set(Calendar.HOUR_OF_DAY, hour);
    calendar.set(Calendar.MINUTE, minute);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTime();
  }

}
