package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;

import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

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
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Utils;

/**
 * FEAT-325470 Faz 4: "kullanilmayan numara" (AMD bad number, code 113) kurali. Aranan numara
 * kayit icin gecersiz sayilir; colSort halkasinda sonraki uygun (DNC disi, numara basina limiti
 * dolmamis, dolu) numara varsa retry ona kurulur, yoksa kayit kapanir ve kapanis nedeni yazilir.
 * Kural stratejiye ozgu degil, numara gecerliligi kuralidir; ALTI stratejide de ayni calisir
 * (S3) ve retry zamani stratejinin normal hesabindan gelir (S4). Kalicilik kolonu yoktur (S5):
 * yalniz o karardaki aranan numara dislanir.
 */
@RunWith(Parameterized.class)
public class RetryStrategyBadNumberNextNumberFeatureTest {

  private static final int OVERALL_ATTEMPT = 3;
  private static final int MACRO_MAX_ATTEMPT = 3;
  private static final double RETRY_DELAY_MINUTES = 1D;
  private static final double MINUTES_PER_DAY = 24 * 60D;
  private static final int BAD_NUMBER = Constants.TELEPHONY_RC_AMD_BAD_NUMBER;
  private static final int[] OTHER_NON_ANSWERED_CODES = { Constants.TELEPHONY_RC_AMD, 17, 19, 1 };

  @Parameters(name = "{0}")
  public static Collection<Object[]> strategies() {
    return Arrays.asList(new Object[][] {
        { "0-REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER", supplier(ReachRetrySingleNumber::new),
            Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER, "number_1", RETRY_DELAY_MINUTES },
        { "1-REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER", supplier(ReachRetryPerNumber::new),
            Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER, "number_1", RETRY_DELAY_MINUTES },
        { "2-ROUND_ROBIN_SCHEDULE_NEXT_NUMBER", supplier(RoundRobinNextNumber::new),
            Constants.RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER, "number_2", RETRY_DELAY_MINUTES },
        { "3-ROUND_ROBIN_SCHEDULE_NEXT_CYCLE", supplier(RoundRobinNextCycle::new),
            Constants.RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_CYCLE, "number_2", 0D },
        { "4-ROUND_ROBIN_PER_CYCLE_RETRY_LIMIT", supplier(RoundRobinPerCycle::new),
            Constants.RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER, "number_2", 0D },
        { "5-RETRY_RULES_MANAGES_CYCLE", supplier(RetryRulesCycle::new),
            Constants.RETRY_STRATEGY.RETRY_RULES_MANAGES_CYCLE, "number_1", RETRY_DELAY_MINUTES },
    });
  }

  private static Supplier<RetryStrategy> supplier(Supplier<RetryStrategy> supplier) {
    return supplier;
  }

  private final Supplier<RetryStrategy> strategyFactory;
  private final Constants.RETRY_STRATEGY strategyType;
  private final String normalPickWithThreeNumbers;
  private final double expectedDelayMinutes;

  public RetryStrategyBadNumberNextNumberFeatureTest(String name, Supplier<RetryStrategy> strategyFactory,
      Constants.RETRY_STRATEGY strategyType, String normalPickWithThreeNumbers, double expectedDelayMinutes) {
    this.strategyFactory = strategyFactory;
    this.strategyType = strategyType;
    this.normalPickWithThreeNumbers = normalPickWithThreeNumbers;
    this.expectedDelayMinutes = expectedDelayMinutes;
  }

  // ---------------------------------------------------------------------
  // Kapanis: baska aranabilir numara yok
  // ---------------------------------------------------------------------

  @Test
  public void singleNumberRecordWithBadNumberCompletesWithUnreachableCloseReason() {
    Fixture fixture = new Fixture(strategyType, "number_1");
    fixture.badNumberResult();

    strategyFactory.get().createRetry(fixture.call);

    verify(fixture.callCleaner).completeCall(fixture.call);
    assertEquals(Constants.CLOSE_REASON_ALL_NUMBERS_UNREACHABLE, fixture.call.getCloseReason());
    assertNull(fixture.call.getRetryDate());
    assertTrue(fixture.call.getRetryHistory().isEmpty());
  }

  @Test
  public void badNumberCompletesWhenEveryOtherNumberIsInDnc() {
    Fixture fixture = new Fixture(strategyType, "number_1", "number_2", "number_3");
    fixture.badNumberResult();
    fixture.dnc("number_2", "number_3");

    strategyFactory.get().createRetry(fixture.call);

    verify(fixture.callCleaner).completeCall(fixture.call);
    assertEquals(Constants.CLOSE_REASON_ALL_NUMBERS_UNREACHABLE, fixture.call.getCloseReason());
    assertNull(fixture.call.getRetryDate());
  }

  // ---------------------------------------------------------------------
  // Numara gecisi: colSort halkasinda sonraki uygun numara
  // ---------------------------------------------------------------------

  @Test
  public void badNumberSwitchesRetryToNextNumberInColSortOrder() {
    assertSwitch("number_1", "number_2");
    assertSwitch("number_2", "number_3");
    assertSwitch("number_3", "number_1");
  }

  private void assertSwitch(String calledNumber, String expectedRetryNumber) {
    Fixture fixture = new Fixture(strategyType, "number_1", "number_2", "number_3");
    fixture.call.setCalledNumber(calledNumber);
    fixture.badNumberResult();

    strategyFactory.get().createRetry(fixture.call);

    verify(fixture.callCleaner, never()).completeCall(fixture.call);
    assertEquals("called " + calledNumber, expectedRetryNumber, fixture.call.getRetryNumber());
    assertNotNull(fixture.call.getRetryDate());
    assertNull(fixture.call.getCloseReason());
  }

  @Test
  public void badNumberSkipsDncNumbersWhenSwitching() {
    Fixture fixture = new Fixture(strategyType, "number_1", "number_2", "number_3");
    fixture.badNumberResult();
    fixture.dnc("number_2");

    strategyFactory.get().createRetry(fixture.call);

    verify(fixture.callCleaner, never()).completeCall(fixture.call);
    assertEquals("number_3", fixture.call.getRetryNumber());
  }

  /**
   * 325559 entegrasyonu: numara basina limit aktifken limitini doldurmus numara da atlanir.
   * REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER (K2) ve REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER (325559 karari,
   * 29.09.2026) bu alani yok sayar; orada number_2 secilir. Kullanilmayan numarada sonraki numaraya
   * gecis (S3) iki stratejide de aynen uygulanir, yalniz limit hesaba katilmaz.
   *
   * <p><b>Beklenti degisti (29.09.2026):</b> strateji 0 icin eskiden number_3 bekleniyordu; strateji
   * 0 artik muaf oldugu icin number_2. Fixture degismedi.</p>
   */
  @Test
  public void badNumberSkipsNumbersExhaustedByPerNumberLimit() {
    Fixture fixture = new Fixture(strategyType, "number_1", "number_2", "number_3");
    fixture.badNumberResult();
    fixture.retryHistory("number_2");
    when(fixture.campaign.getMaxAttemptPerNumber()).thenReturn(1);

    strategyFactory.get().createRetry(fixture.call);

    verify(fixture.callCleaner, never()).completeCall(fixture.call);
    boolean exemptFromPerNumberLimit = Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER.equals(strategyType)
        || Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER.equals(strategyType);
    String expected = exemptFromPerNumberLimit ? "number_2" : "number_3";
    assertEquals(expected, fixture.call.getRetryNumber());
  }

  /**
   * Callback numarasi telefon kolonlarindan biri olmayabilir (agent elle girer). O numara
   * kullanilmayan numara cikarsa halka bastan taranir ve callback plani retry'a donusur.
   */
  @Test
  public void badNumberOnCallbackNumberOutsideColumnsSwitchesToFirstEligibleColumnNumber() {
    Fixture fixture = new Fixture(strategyType, "number_1", "number_2");
    fixture.call.setCalledNumber("5559999");
    fixture.pendingCallback("5559999");
    fixture.badNumberResult();

    strategyFactory.get().createRetry(fixture.call);

    verify(fixture.callCleaner, never()).completeCall(fixture.call);
    assertEquals("number_1", fixture.call.getRetryNumber());
    assertNull(fixture.call.getCallbackDate());
    assertNull(fixture.call.getCallbackNumber());
  }

  // ---------------------------------------------------------------------
  // Zaman ve gecmis: stratejinin normal hesabi korunur
  // ---------------------------------------------------------------------

  @Test
  public void badNumberRetryDateFollowsStrategyTiming() {
    Fixture fixture = new Fixture(strategyType, "number_1", "number_2", "number_3");
    fixture.badNumberResult();
    double now = Utils.convertDateToDouble(new Date());

    strategyFactory.get().createRetry(fixture.call);

    double plannedMinutesFromNow = (fixture.call.getRetryDate() - now) * MINUTES_PER_DAY;
    assertEquals(expectedDelayMinutes, plannedMinutesFromNow, 0.25D);
  }

  @Test
  public void retryRulesCycleUsesScheduleRowIntervalForBadNumberButStillSwitchesNumber() {
    Assume.assumeTrue(Constants.RETRY_STRATEGY.RETRY_RULES_MANAGES_CYCLE.equals(strategyType));
    Fixture fixture = new Fixture(strategyType, "number_1", "number_2", "number_3");
    TelephonyResultCode badNumber = fixture.badNumberResult();
    fixture.call.setResultCodeSequence(1);
    when(fixture.campaign.getResultCodeSchedules()).thenReturn(new ArrayList<>(Arrays.asList(scheduleRow(badNumber, 45D))));
    double now = Utils.convertDateToDouble(new Date());

    strategyFactory.get().createRetry(fixture.call);

    verify(fixture.callCleaner, never()).completeCall(fixture.call);
    assertEquals("number_2", fixture.call.getRetryNumber());
    double plannedMinutesFromNow = (fixture.call.getRetryDate() - now) * MINUTES_PER_DAY;
    assertTrue("planned " + plannedMinutesFromNow + " min", plannedMinutesFromNow >= 44.5D);
  }

  @Test
  public void badNumberRetryIsRecordedInRetryHistoryAndCallProcess() {
    Fixture fixture = new Fixture(strategyType, "number_1", "number_2", "number_3");
    fixture.badNumberResult();

    strategyFactory.get().createRetry(fixture.call);

    assertEquals(Arrays.asList("number_2"), fixture.call.getRetryHistory());
    assertEquals(Arrays.asList("F", "R"), fixture.call.getCallProcessHistory());
  }

  // ---------------------------------------------------------------------
  // Negatif: diger cevapsiz sonuclar kurali tetiklemez
  // ---------------------------------------------------------------------

  @Test
  public void otherNonAnsweredCodesDoNotTriggerTheSwitch() {
    for (int code : OTHER_NON_ANSWERED_CODES) {
      Fixture fixture = new Fixture(strategyType, "number_1", "number_2", "number_3");
      fixture.call.setTelephonyResultCode(telephonyResultCode(code));

      strategyFactory.get().createRetry(fixture.call);

      verify(fixture.callCleaner, never()).completeCall(fixture.call);
      assertEquals("code " + code, normalPickWithThreeNumbers, fixture.call.getRetryNumber());
      assertNull("code " + code, fixture.call.getCloseReason());
    }
  }

  // ---------------------------------------------------------------------
  // Yardimcilar
  // ---------------------------------------------------------------------

  private static TelephonyResultCode telephonyResultCode(int code) {
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();
    telephonyResultCode.setId(1000L + code);
    telephonyResultCode.setCode(code);
    telephonyResultCode.setName("TRC-" + code);
    return telephonyResultCode;
  }

  private static ResultCodeSchedule scheduleRow(TelephonyResultCode telephonyResultCode, double intervalMinutes) {
    ResultCodeSchedule schedule = new ResultCodeSchedule();
    schedule.setEnabled(true);
    schedule.setTelephonyResultCode(telephonyResultCode);
    schedule.setApplyTo(Constants.APPLY_TO.RETRIES);
    schedule.setRetryAmount(5);
    schedule.setRetryInterval(intervalMinutes);
    schedule.setAdvancedIntervalEnabled(false);
    schedule.setMacroCycle(false);
    schedule.setMinScheduleTime(timeOfDay(0, 0));
    schedule.setMaxScheduleTime(timeOfDay(23, 59));
    return schedule;
  }

  private static Date timeOfDay(int hour, int minute) {
    Calendar calendar = Calendar.getInstance();
    calendar.set(Calendar.HOUR_OF_DAY, hour);
    calendar.set(Calendar.MINUTE, minute);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTime();
  }

  private static final class Fixture {
    final Call call = spy(new Call());
    final Campaign campaign = mock(Campaign.class);
    final JSONObject rawData = mock(JSONObject.class);
    final CallCleaner callCleaner = mock(CallCleaner.class);
    final CallOriginator callOriginator = mock(CallOriginator.class);
    final List<PhoneNumberColumn> numberColumns = new ArrayList<>();

    Fixture(Constants.RETRY_STRATEGY strategyType, String... numbers) {
      when(campaign.getRetryStrategy()).thenReturn(strategyType);
      when(campaign.getColResultRetryHistory()).thenReturn("retry_history");
      when(campaign.getEnabledPhoneNumberColumns()).thenReturn(numberColumns);
      when(campaign.getRetryOveralAttempt()).thenReturn(OVERALL_ATTEMPT);
      when(campaign.getMacroMaxAttempt()).thenReturn(MACRO_MAX_ATTEMPT);
      when(campaign.getMacroInterval()).thenReturn(24);
      when(campaign.getRetryDelay()).thenReturn(RETRY_DELAY_MINUTES);
      when(campaign.getResultCodeSchedules()).thenReturn(new ArrayList<ResultCodeSchedule>());

      when(call.getCallOriginator()).thenReturn(callOriginator);
      when(callOriginator.getCallCleaner()).thenReturn(callCleaner);

      call.setCampaign(campaign);
      call.setRawData(rawData);

      for (int i = 0; i < numbers.length; i++) {
        addPhoneColumn("phone_number_" + (i + 1), numbers[i]);
      }
      call.setCalledNumber(numbers[0]);
    }

    TelephonyResultCode badNumberResult() {
      TelephonyResultCode badNumber = telephonyResultCode(BAD_NUMBER);
      call.setTelephonyResultCode(badNumber);
      return badNumber;
    }

    void dnc(String... numbers) {
      call.setDNCNumbers(new HashSet<>(Arrays.asList(numbers)));
    }

    void retryHistory(String history) {
      when(rawData.has("retry_history")).thenReturn(true);
      when(rawData.getString("retry_history")).thenReturn(history);
    }

    void pendingCallback(String callbackNumber) {
      when(campaign.getColResultCallbackDate()).thenReturn("callback_date");
      when(rawData.has("callback_date")).thenReturn(true);
      call.setCallbackNumber(callbackNumber);
      call.setCallbackDate(45000.5D);
    }

    private void addPhoneColumn(String columnName, String phoneNumber) {
      PhoneNumberColumn column = new PhoneNumberColumn();
      column.setColNumber(columnName);
      numberColumns.add(column);
      when(rawData.has(columnName)).thenReturn(true);
      when(rawData.getString(columnName)).thenReturn(phoneNumber);
    }
  }

}
