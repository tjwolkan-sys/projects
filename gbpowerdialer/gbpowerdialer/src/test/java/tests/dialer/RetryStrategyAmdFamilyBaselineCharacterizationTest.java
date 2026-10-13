package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
 * FEAT-325470 Faz 1 karakterizasyonu.
 *
 * <p>AMD (code 900), user busy (17), no answer (19) ve unallocated number (1) sonuclarinin
 * alti retry stratejisinde BUGUNKU davranisi kilitlenir: code 16 disindaki her telephony result
 * retry'a girer, numara stratejinin normal seciminden gelir ve tarih (schedule satiri yoksa)
 * kampanya retry delay'inden hesaplanir. Yeni 113 (bad number) kurali eklendiginde bu
 * sonuclarin hicbiri etkilenmemelidir; 87/88 (114/115) icin de ayni yol gecerlidir.</p>
 *
 * <p>Bilerek kilitlenen kontrast: code 1 (unallocated) bugun ayni numaraya retry aliyor.
 * 113 sonrasi yalniz 113 numara degistirir, 1 eskisi gibi kalir.</p>
 */
@RunWith(Parameterized.class)
public class RetryStrategyAmdFamilyBaselineCharacterizationTest {

  private static final int OVERALL_ATTEMPT = 3;
  private static final int MACRO_MAX_ATTEMPT = 3;
  private static final double RETRY_DELAY_MINUTES = 1D;
  private static final double MINUTES_PER_DAY = 24 * 60D;
  private static final int[] NON_ANSWERED_CODES = { 900, 17, 19, 1 };

  @Parameters(name = "{0}")
  public static Collection<Object[]> strategies() {
    return Arrays.asList(new Object[][] {
        { "0-REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER", supplier(ReachRetrySingleNumber::new), "number_1", RETRY_DELAY_MINUTES },
        { "1-REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER", supplier(ReachRetryPerNumber::new), "number_1", RETRY_DELAY_MINUTES },
        { "2-ROUND_ROBIN_SCHEDULE_NEXT_NUMBER", supplier(RoundRobinNextNumber::new), "number_2", RETRY_DELAY_MINUTES },
        // Strateji 3 tarih hesabini RoundRobinPerCycle'dan miras alir: 3 numarali kayitta ilk
        // retry'da cycle dolmamistir, retry delay UYGULANMAZ (numara hemen halkada ilerler).
        { "3-ROUND_ROBIN_SCHEDULE_NEXT_CYCLE", supplier(RoundRobinNextCycle::new), "number_2", 0D },
        // Per-cycle strateji: ayni nedenle ilk retry'da gecikme uygulanmaz.
        { "4-ROUND_ROBIN_PER_CYCLE_RETRY_LIMIT", supplier(RoundRobinPerCycle::new), "number_2", 0D },
        { "5-RETRY_RULES_MANAGES_CYCLE", supplier(RetryRulesCycle::new), "number_1", RETRY_DELAY_MINUTES },
    });
  }

  private static Supplier<RetryStrategy> supplier(Supplier<RetryStrategy> supplier) {
    return supplier;
  }

  private final Supplier<RetryStrategy> strategyFactory;
  private final String expectedRetryNumber;
  private final double expectedDelayMinutes;

  public RetryStrategyAmdFamilyBaselineCharacterizationTest(String name, Supplier<RetryStrategy> strategyFactory,
      String expectedRetryNumber, double expectedDelayMinutes) {
    this.strategyFactory = strategyFactory;
    this.expectedRetryNumber = expectedRetryNumber;
    this.expectedDelayMinutes = expectedDelayMinutes;
  }

  @Test
  public void nonAnsweredTelephonyResultsAreRetriedOnStrategyNumber() {
    for (int code : NON_ANSWERED_CODES) {
      Fixture fixture = new Fixture();
      fixture.call.setTelephonyResultCode(telephonyResultCode(code));

      strategyFactory.get().createRetry(fixture.call);

      verify(fixture.callCleaner, never()).completeCall(fixture.call);
      assertEquals("code " + code, expectedRetryNumber, fixture.call.getRetryNumber());
      assertNotNull("code " + code, fixture.call.getRetryDate());
    }
  }

  @Test
  public void retryDateFollowsCampaignRetryDelayWhenNoScheduleRowMatches() {
    Fixture fixture = new Fixture();
    fixture.call.setTelephonyResultCode(telephonyResultCode(900));
    double now = Utils.convertDateToDouble(new Date());

    strategyFactory.get().createRetry(fixture.call);

    double plannedMinutesFromNow = (fixture.call.getRetryDate() - now) * MINUTES_PER_DAY;
    assertEquals(expectedDelayMinutes, plannedMinutesFromNow, 0.25D);
  }

  @Test
  public void retryHistoryRecordsThePlannedNumberForAmdResult() {
    Fixture fixture = new Fixture();
    fixture.call.setTelephonyResultCode(telephonyResultCode(900));

    strategyFactory.get().createRetry(fixture.call);

    assertEquals(Arrays.asList(expectedRetryNumber), fixture.call.getRetryHistory());
  }

  /**
   * Manages-cycle stratejisi TRC'ye esli, enabled ve retry kapsamli schedule satirini bulur ve
   * tarihi kampanya retry delay'i yerine o satirin interval'inden hesaplar. 87/88 icin istenen
   * "schedule list'e eklendiyse o kullanilir" davranisi bugunku kodun dogal sonucudur.
   */
  @Test
  public void retryRulesCycleUsesScheduleRowIntervalForMatchingTelephonyResultCode() {
    Assume.assumeTrue(strategyFactory.get() instanceof RetryRulesCycle);
    Fixture fixture = new Fixture();
    TelephonyResultCode amd = telephonyResultCode(900);
    fixture.call.setTelephonyResultCode(amd);
    fixture.call.setResultCodeSequence(1);
    when(fixture.campaign.getResultCodeSchedules()).thenReturn(new ArrayList<>(Arrays.asList(scheduleRow(amd, 60D))));
    double now = Utils.convertDateToDouble(new Date());

    strategyFactory.get().createRetry(fixture.call);

    verify(fixture.callCleaner, never()).completeCall(fixture.call);
    assertEquals("number_1", fixture.call.getRetryNumber());
    double plannedMinutesFromNow = (fixture.call.getRetryDate() - now) * MINUTES_PER_DAY;
    // Gun sonuna tasma durumunda ertesi gune atilir; her iki durumda da retry delay'in (1 dk)
    // cok uzerindedir.
    assertTrue("planned " + plannedMinutesFromNow + " min", plannedMinutesFromNow >= 59.5D);
  }

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

  /**
   * RetryStrategySixStrategiesBaselineCharacterizationTest ile ayni fixture deseni: spy Call,
   * mock Campaign/rawData, uc dolu telefon kolonu, aranan numara number_1, bos gecmis.
   */
  private static final class Fixture {
    final Call call = spy(new Call());
    final Campaign campaign = mock(Campaign.class);
    final JSONObject rawData = mock(JSONObject.class);
    final CallCleaner callCleaner = mock(CallCleaner.class);
    final CallOriginator callOriginator = mock(CallOriginator.class);
    final List<PhoneNumberColumn> numberColumns = new ArrayList<>();

    Fixture() {
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
  }

}
