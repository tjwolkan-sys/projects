package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.retry.ReachRetrySingleNumber;
import com.powerdialer.engine.dialer.retry.RetryRulesCycle;
import com.powerdialer.engine.dialer.retry.RetryStrategy;
import com.powerdialer.engine.dialer.retry.RoundRobinNextCycle;
import com.powerdialer.engine.dialer.retry.RoundRobinNextNumber;
import com.powerdialer.engine.dialer.retry.RoundRobinPerCycle;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.ResultCode;
import com.powerdialer.model.ResultCodeSchedule;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.util.Constants;

/**
 * FEAT-325559 Faz 3 — Kabul kriteri 2 (erken kayit kapatma) ve kapanis nedeni yazimi.
 *
 * <p>Ortak guard {@code createRetry} icinde uygulandigi icin limitin gecerli oldugu stratejilerin
 * tamami kapsanir; kendi {@code isOverallRetryLimitReached} mantigini tasiyan strateji 5 ayrica
 * dogrulanir (F3). Strateji 1 (K2) ve strateji 0 (karar 29.09.2026) muaftir: alan o stratejilerde
 * yok sayilir, erken kapatma uygulanmaz.</p>
 */
public class MaxAttemptPerNumberEarlyRecordTerminationTest {

  private static final int OVERALL_ATTEMPT = 60;
  private static final int MAX_PER_NUMBER = 10;

  private final Call call = spy(new Call());
  private final Campaign campaign = mock(Campaign.class);
  private final JSONObject rawData = mock(JSONObject.class);
  private final CallCleaner callCleaner = mock(CallCleaner.class);
  private final CallOriginator callOriginator = mock(CallOriginator.class);
  private final List<PhoneNumberColumn> numberColumns = new ArrayList<>();

  @Before
  public void setup() {
    when(campaign.getColResultRetryHistory()).thenReturn("retry_history");
    when(campaign.getColResultCbHistory()).thenReturn("cb_history");
    when(campaign.getColResultCallProcess()).thenReturn("call_process");
    when(campaign.getRetryOveralAttempt()).thenReturn(OVERALL_ATTEMPT);
    when(campaign.getMacroMaxAttempt()).thenReturn(OVERALL_ATTEMPT);
    when(campaign.getMacroInterval()).thenReturn(24);
    when(campaign.getRetryDelay()).thenReturn(1D);
    when(campaign.getMaxAttemptPerNumber()).thenReturn(MAX_PER_NUMBER);
    when(campaign.getResultCodeSchedules()).thenReturn(new ArrayList<ResultCodeSchedule>());
    when(campaign.getEnabledPhoneNumberColumns()).thenAnswer(invocation -> {
      List<PhoneNumberColumn> sorted = new ArrayList<>(numberColumns);
      sorted.sort(Comparator.comparingInt(PhoneNumberColumn::getColSort));
      return sorted;
    });

    when(call.getCallOriginator()).thenReturn(callOriginator);
    when(callOriginator.getCallCleaner()).thenReturn(callCleaner);

    call.setCampaign(campaign);
    call.setRawData(rawData);
  }

  private void addPhoneColumn(String columnName, String phoneNumber, int colSort) {
    PhoneNumberColumn column = new PhoneNumberColumn();
    column.setColNumber(columnName);
    column.setColSort(colSort);
    numberColumns.add(column);
    when(rawData.has(columnName)).thenReturn(true);
    when(rawData.getString(columnName)).thenReturn(phoneNumber);
  }

  private void setRawColumn(String columnName, String value) {
    when(rawData.has(columnName)).thenReturn(true);
    when(rawData.get(columnName)).thenReturn(value);
    when(rawData.getString(columnName)).thenReturn(value);
  }

  private void numbers(String... phoneNumbers) {
    for (int i = 0; i < phoneNumbers.length; i++) {
      addPhoneColumn("phone_number_" + (i + 1), phoneNumbers[i], i + 1);
    }
  }

  private void dialHistory(String... retryNumbers) {
    StringBuilder trail = new StringBuilder("F");
    StringBuilder retries = new StringBuilder();
    for (int i = 0; i < retryNumbers.length; i++) {
      trail.append(";R");
      if (i > 0) {
        retries.append(";");
      }
      retries.append(retryNumbers[i]);
    }
    setRawColumn("call_process", trail.toString());
    if (retryNumbers.length > 0) {
      setRawColumn("retry_history", retries.toString());
    }
  }

  /**
   * Kriter 2 senaryosu: 4 dolu numara, her biri tam 10 aramada (fresh dahil toplam 40).
   */
  private void fourNumbersAllAtLimit() {
    numbers("number_1", "number_2", "number_3", "number_4");
    List<String> retries = new ArrayList<>();
    // fresh number_1 sayildigi icin number_1'e 9 retry, digerlerine 10 retry.
    for (int i = 0; i < 9; i++) {
      retries.add("number_1");
    }
    for (String number : Arrays.asList("number_2", "number_3", "number_4")) {
      for (int i = 0; i < 10; i++) {
        retries.add(number);
      }
    }
    dialHistory(retries.toArray(new String[0]));
    call.setCalledNumber("number_1");
  }

  // ---------------------------------------------------------------------
  // Kriter 2
  // ---------------------------------------------------------------------

  /**
   * Kriter 2: 4 dolu numara x 10 = 40 arama. Genel limit 60'a ULASILMAMIS olsa dahi aranabilir
   * numara kalmadigi icin kayit kapatilir.
   */
  @Test
  public void recordIsClosedWhenEveryFilledNumberHitsItsOwnLimit() {
    fourNumbersAllAtLimit();

    assertEquals("Toplam 40 arama olmalidir", 40, call.getDialedNumberSequence().size());

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull("Kapanan kayda retry planlanmamalidir", call.getRetryDate());
  }

  @Test
  public void closeReasonIsNumberLimitReachedOnEarlyTermination() {
    fourNumbersAllAtLimit();

    new RoundRobinNextNumber().createRetry(call);

    assertEquals(Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED, call.getCloseReason());
  }

  /**
   * Bir numara hala limit altindaysa kayit KAPANMAZ; retry o numaraya planlanir.
   */
  @Test
  public void recordStaysOpenWhileAnyNumberIsBelowItsLimit() {
    numbers("number_1", "number_2");
    List<String> retries = new ArrayList<>();
    for (int i = 0; i < 9; i++) {
      retries.add("number_1");
    }
    retries.add("number_2");
    dialHistory(retries.toArray(new String[0]));
    call.setCalledNumber("number_1");

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
    assertNotNull(call.getRetryDate());
    assertNull("Kapanmayan kayda kapanis nedeni yazilmaz", call.getCloseReason());
  }

  /**
   * Hic dolu numarasi olmayan kayit bu katmanda ZATEN kapaniyor: strateji kendi
   * {@code isOverallRetryLimitReached} guard'inda ({@code !anyNumberPresentAfterDNC()}) devreye
   * girip numara basina degerlendirmeye hic sira gelmiyor. Dolayisiyla kapanis nedeni de
   * yazilmiyor ve bu DOGRUDUR:
   *
   * <p>Uretimde boyle bir kayit {@code createRetry}'ye hic ULASMAZ. D1 geregi
   * {@code CallProducer.extractNumberToCallFromPhoneNumbers} istisna atiyor ve kayit icin hic
   * {@link Call} olusmuyor. {@code CLOSE_REASON_NO_CALLABLE_NUMBER} bu yuzden producer katmaninin
   * (Faz 4) sorumlulugundadir; bu test o sinirin bilincli oldugunu kilitler.</p>
   */
  @Test
  public void recordWithoutFilledNumberIsClosedByExistingGuardWithoutCloseReason() {
    PhoneNumberColumn empty = new PhoneNumberColumn();
    empty.setColNumber("phone_number_1");
    empty.setColSort(1);
    numberColumns.add(empty);
    call.setCalledNumber("number_1");

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull("Neden atfi producer katmanina (Faz 4) aittir", call.getCloseReason());
  }

  // ---------------------------------------------------------------------
  // Tum stratejiler kapsaniyor (F3 / F4 / K15)
  // ---------------------------------------------------------------------

  /**
   * Ortak guard {@code createRetry} icinde oldugu icin limitin gecerli oldugu TUM stratejiler,
   * strateji 5 (kendi limit mantigi var) dahil, erken kapatmayi uygular.
   *
   * <p><b>Liste degisti (29.09.2026):</b> strateji 0 ({@code ReachRetrySingleNumber}) listeden
   * cikarildi; karar geregi o strateji alani yok sayar (strateji 1 gibi). Muafiyeti
   * {@code MaxAttemptPerNumberFeatureOffRegressionTest} ve asagidaki tek numara testi kilitler.</p>
   */
  @Test
  public void everyApplicableStrategyClosesRecordWhenAllNumbersAreExhausted() {
    List<RetryStrategy> strategies = Arrays.asList(
        new RoundRobinNextNumber(),
        new RoundRobinNextCycle(),
        new RoundRobinPerCycle(),
        new RetryRulesCycle());

    for (RetryStrategy strategy : strategies) {
      Call freshCall = newCallWithAllNumbersAtLimit();

      strategy.createRetry(freshCall);

      assertNull(strategy.getClass().getSimpleName() + " retry planlamamaliydi",
          freshCall.getRetryDate());
      assertEquals(strategy.getClass().getSimpleName() + " kapanis nedeni yazmaliydi",
          Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED, freshCall.getCloseReason());
    }
  }

  /**
   * K15: strateji 0 tek numara arar. Numara limitine ulassa bile kayit limit yuzunden KAPANMAZ;
   * strateji ayni numaraya tekrar aramayi planlar, kaydi genel limit (Toplam Deneme) durdurur.
   *
   * <p><b>Iddia degisti (29.09.2026, kullanici karari):</b> onceki hali bu fixture'da kaydin
   * {@code closing for reached number limit} ile kapanmasini bekliyordu. Strateji 0 artik strateji
   * 1 gibi muaf; fixture degismedi.</p>
   */
  @Test
  public void singleNumberStrategyIgnoresLimitEvenWhenItsOnlyNumberIsExhausted() {
    numbers("number_1");
    List<String> retries = new ArrayList<>();
    for (int i = 0; i < 9; i++) {
      retries.add("number_1");
    }
    dialHistory(retries.toArray(new String[0]));
    call.setCalledNumber("number_1");

    new ReachRetrySingleNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertNotNull("Genel limit dolmadigi icin tekrar arama planlanmali", call.getRetryDate());
    assertEquals("number_1", call.getRetryNumber());
    assertNull(call.getCloseReason());
  }

  // ---------------------------------------------------------------------
  // R11 — basarili kapanis EZILMEZ ve nedeni yazilmaz
  // ---------------------------------------------------------------------

  /**
   * R11: musteri yanitladi ve kesin sonuc kodu olustu. Kayit kapanir ama bu bir LIMIT kapanisi
   * degildir; kapanis nedeni YAZILMAMALIDIR, aksi halde rapor "limit doldu" gibi okunur.
   */
  @Test
  public void successfulCloseDoesNotWriteCloseReason() {
    fourNumbersAllAtLimit();
    TelephonyResultCode normalClearing = new TelephonyResultCode();
    normalClearing.setCode(Constants.TELEPHONY_RC_NORMAL_CLEARING);
    call.setTelephonyResultCode(normalClearing);

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull("Basarili kapanista kapanis nedeni yazilmamalidir", call.getCloseReason());
  }

  /**
   * Basarili kapanis, numara limitleri dolmamis olsa da derhal ve kalici sonlandirir; limit
   * kontrolu bu karari ezmez.
   */
  @Test
  public void successfulCloseTakesPrecedenceOverPerNumberLimitEvaluation() {
    numbers("number_1", "number_2");
    call.setCalledNumber("number_1");
    TelephonyResultCode normalClearing = new TelephonyResultCode();
    normalClearing.setCode(Constants.TELEPHONY_RC_NORMAL_CLEARING);
    call.setTelephonyResultCode(normalClearing);

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
    assertNull(call.getCloseReason());
  }

  /**
   * Yanit + kesin OLMAYAN sonuc kodu: basarili kapanis devreye girmez, limit degerlendirmesi
   * normal akar.
   */
  @Test
  public void answeredCallWithNonFinalResultCodeStillRespectsPerNumberLimit() {
    fourNumbersAllAtLimit();
    TelephonyResultCode normalClearing = new TelephonyResultCode();
    normalClearing.setCode(Constants.TELEPHONY_RC_NORMAL_CLEARING);
    call.setTelephonyResultCode(normalClearing);
    ResultCode nonFinal = new ResultCode();
    nonFinal.setFinalStatus(false);
    call.setUserResultCode(nonFinal);

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner).completeCall(call);
    assertEquals(Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED, call.getCloseReason());
  }

  private Call newCallWithAllNumbersAtLimit() {
    Call freshCall = spy(new Call());
    JSONObject freshRawData = mock(JSONObject.class);
    List<PhoneNumberColumn> freshColumns = new ArrayList<>();

    for (int i = 1; i <= 2; i++) {
      PhoneNumberColumn column = new PhoneNumberColumn();
      column.setColNumber("phone_number_" + i);
      column.setColSort(i);
      freshColumns.add(column);
      when(freshRawData.has("phone_number_" + i)).thenReturn(true);
      when(freshRawData.getString("phone_number_" + i)).thenReturn("number_" + i);
    }

    Campaign freshCampaign = mock(Campaign.class);
    when(freshCampaign.getColResultRetryHistory()).thenReturn("retry_history");
    when(freshCampaign.getColResultCbHistory()).thenReturn("cb_history");
    when(freshCampaign.getColResultCallProcess()).thenReturn("call_process");
    when(freshCampaign.getRetryOveralAttempt()).thenReturn(OVERALL_ATTEMPT);
    when(freshCampaign.getMacroMaxAttempt()).thenReturn(OVERALL_ATTEMPT);
    when(freshCampaign.getMacroInterval()).thenReturn(24);
    when(freshCampaign.getRetryDelay()).thenReturn(1D);
    when(freshCampaign.getMaxAttemptPerNumber()).thenReturn(MAX_PER_NUMBER);
    when(freshCampaign.getResultCodeSchedules()).thenReturn(new ArrayList<ResultCodeSchedule>());
    when(freshCampaign.getEnabledPhoneNumberColumns()).thenReturn(freshColumns);

    StringBuilder trail = new StringBuilder("F");
    StringBuilder retries = new StringBuilder();
    for (int i = 0; i < 9; i++) {
      trail.append(";R");
      retries.append(i == 0 ? "number_1" : ";number_1");
    }
    for (int i = 0; i < 10; i++) {
      trail.append(";R");
      retries.append(";number_2");
    }
    when(freshRawData.has("call_process")).thenReturn(true);
    when(freshRawData.get("call_process")).thenReturn(trail.toString());
    when(freshRawData.getString("call_process")).thenReturn(trail.toString());
    when(freshRawData.has("retry_history")).thenReturn(true);
    when(freshRawData.getString("retry_history")).thenReturn(retries.toString());

    CallOriginator freshOriginator = mock(CallOriginator.class);
    when(freshOriginator.getCallCleaner()).thenReturn(callCleaner);
    when(freshCall.getCallOriginator()).thenReturn(freshOriginator);

    freshCall.setCampaign(freshCampaign);
    freshCall.setRawData(freshRawData);
    freshCall.setCalledNumber("number_1");
    return freshCall;
  }

}
