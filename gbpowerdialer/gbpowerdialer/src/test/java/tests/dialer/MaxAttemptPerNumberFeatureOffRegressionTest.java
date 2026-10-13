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
import java.util.HashSet;
import java.util.List;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.retry.ReachRetryPerNumber;
import com.powerdialer.engine.dialer.retry.ReachRetrySingleNumber;
import com.powerdialer.engine.dialer.retry.RoundRobinNextNumber;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.util.Constants;

/**
 * FEAT-325559 Faz 3 — R1 ve R2'nin tek gercek guvencesi.
 *
 * <p>Ortak eligibility predicate ALTI stratejide birden degistigi icin, alan bos olan diger
 * musterilerin kampanyalari da bu koddan geciyor. Bu testler iki seyi kilitler:</p>
 *
 * <ul>
 * <li><b>Feature off:</b> alan {@code null} veya {@code <= 0} iken atlama, erken kapatma ve kapanis
 * nedeni yazimi HIC devreye girmez; davranis birebir eski haliyle kalir.</li>
 * <li><b>K2:</b> {@code REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER} stratejisinde alan dolu olsa bile YOK
 * SAYILIR; o strateji numara basina limiti kendi {@code retryOveralAttempt} semantigiyle uygular.</li>
 * <li><b>Strateji 0 muafiyeti (karar 29.09.2026):</b> {@code REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER}
 * tek numarayi arar; alan bu stratejide de YOK SAYILIR. Numara degismez, kayit limit yuzunden
 * kapanmaz.</li>
 * </ul>
 */
public class MaxAttemptPerNumberFeatureOffRegressionTest {

  private static final int OVERALL_ATTEMPT = 30;

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

  /**
   * number_1 uzerinde 15 arama birikmis gecmis: numara basina limit AKTIF olsaydi (ornek 10)
   * atlama tetiklenirdi.
   */
  private void heavilyDialedFirstNumber() {
    numbers("number_1", "number_2");
    StringBuilder trail = new StringBuilder("F");
    StringBuilder retries = new StringBuilder();
    for (int i = 0; i < 14; i++) {
      trail.append(";R");
      retries.append(i == 0 ? "number_1" : ";number_1");
    }
    setRawColumn("call_process", trail.toString());
    setRawColumn("retry_history", retries.toString());
    call.setCalledNumber("number_1");
  }

  // ---------------------------------------------------------------------
  // Feature off
  // ---------------------------------------------------------------------

  @Test
  public void nullLimitLeavesRoundRobinRotationUntouched() {
    when(campaign.getMaxAttemptPerNumber()).thenReturn(null);
    heavilyDialedFirstNumber();

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("Halka normal ilerlemeli", "number_2", call.getRetryNumber());
    assertNotNull(call.getRetryDate());
    assertNull("Kapanis nedeni yazilmamalidir", call.getCloseReason());
  }

  @Test
  public void zeroLimitIsTreatedAsFeatureOff() {
    when(campaign.getMaxAttemptPerNumber()).thenReturn(0);
    heavilyDialedFirstNumber();

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
    assertNull(call.getCloseReason());
  }

  @Test
  public void negativeLimitIsTreatedAsFeatureOff() {
    when(campaign.getMaxAttemptPerNumber()).thenReturn(-5);
    heavilyDialedFirstNumber();

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
    assertNull(call.getCloseReason());
  }

  /**
   * Feature off iken tum numaralar yogun aranmis olsa bile erken kapatma DEVREYE GIRMEZ; kayit
   * yalnizca genel limit veya DNC ile kapanir.
   */
  @Test
  public void featureOffDoesNotTriggerEarlyRecordTermination() {
    when(campaign.getMaxAttemptPerNumber()).thenReturn(null);
    numbers("number_1", "number_2");
    StringBuilder trail = new StringBuilder("F");
    StringBuilder retries = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      trail.append(";R");
      retries.append(i == 0 ? "number_1" : ";number_" + ((i % 2) + 1));
    }
    setRawColumn("call_process", trail.toString());
    setRawColumn("retry_history", retries.toString());
    call.setCalledNumber("number_1");

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertNotNull(call.getRetryDate());
    assertNull(call.getCloseReason());
  }

  /**
   * Feature off iken eligibility predicate'i birebir eski DNC-only davranisa duser.
   */
  @Test
  public void featureOffKeepsDncOnlySkipSemantics() {
    when(campaign.getMaxAttemptPerNumber()).thenReturn(null);
    numbers("number_1", "number_2", "number_3");
    call.setCalledNumber("number_1");
    call.setDNCNumbers(new HashSet<>(Arrays.asList("number_2")));

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("DNC'li numara atlanip number_3'e gecilmeli", "number_3", call.getRetryNumber());
  }

  /**
   * Feature off iken callback dali eski haliyle kalir: numara DNC/limit yuruyusune SOKULMAZ.
   */
  @Test
  public void featureOffLeavesCallbackBranchWithoutEligibilityWalk() {
    when(campaign.getMaxAttemptPerNumber()).thenReturn(null);
    numbers("number_1", "number_2");
    when(campaign.getColResultCallbackDate()).thenReturn("callback_date");
    when(rawData.has("callback_date")).thenReturn(true);
    call.setCalledNumber("number_1");
    call.setCallbackNumber("number_1");
    call.setDNCNumbers(new HashSet<>(Arrays.asList("number_1")));

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("Callback numarasi DNC'de olsa bile eski davranis korunur",
        "number_1", call.getRetryNumber());
  }

  // ---------------------------------------------------------------------
  // K2 — Strateji 1 alani yok sayar
  // ---------------------------------------------------------------------

  /**
   * K2: alan dolu ve strateji 1. Numara basina limit alani YOK SAYILIR; davranis
   * {@code retryOveralAttempt} tabanli mevcut semantikle ayni kalir. Burada
   * {@code retryOveralAttempt = 30} oldugu icin number_1 (15 arama) limitini DOLDURMAMIS sayilir ve
   * ayni numarada kalinir; yeni alan (5) uygulanirsa atlama olurdu.
   */
  @Test
  public void reachRetryPerNumberIgnoresMaxAttemptPerNumberField() {
    when(campaign.getRetryStrategy())
        .thenReturn(Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER);
    when(campaign.getMaxAttemptPerNumber()).thenReturn(5);
    heavilyDialedFirstNumber();

    new ReachRetryPerNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("Strateji 1 kendi retryOveralAttempt semantigini korumali",
        "number_1", call.getRetryNumber());
    assertNull(call.getCloseReason());
  }

  /**
   * K2 sigortasi iki katmanlidir: kampanya alani strateji 1'i gosteriyorsa ortak guard kapanir,
   * ayrica {@code ReachRetryPerNumber} kendi override'iyla da alani yok sayar. Kampanya alani
   * baska bir strateji gosterse bile bu strateji sinifi alandan etkilenmez.
   */
  @Test
  public void reachRetryPerNumberIgnoresFieldEvenWhenCampaignStrategyValueDiffers() {
    when(campaign.getRetryStrategy())
        .thenReturn(Constants.RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER);
    when(campaign.getMaxAttemptPerNumber()).thenReturn(5);
    heavilyDialedFirstNumber();

    new ReachRetryPerNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_1", call.getRetryNumber());
    assertNull(call.getCloseReason());
  }

  /**
   * Strateji 0 muafiyeti (karar 29.09.2026): "Reach retry limit for single number" aranan numarayi
   * tekrar arar. Alan dolu ve aranan numara limitini coktan asmis olsa bile strateji ikinci
   * numaraya GECMEZ ve kayit limit yuzunden kapanmaz.
   */
  @Test
  public void reachRetrySingleNumberIgnoresMaxAttemptPerNumberField() {
    when(campaign.getRetryStrategy())
        .thenReturn(Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER);
    when(campaign.getMaxAttemptPerNumber()).thenReturn(5);
    heavilyDialedFirstNumber();

    new ReachRetrySingleNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("Strateji 0 aranan numarada kalmali", "number_1", call.getRetryNumber());
    assertNull(call.getCloseReason());
  }

  /**
   * Strateji 1'deki iki katmanli sigorta strateji 0 icin de gecerli: kampanya alani baska bir
   * strateji gosterse bile {@code ReachRetrySingleNumber} kendi override'iyla alani yok sayar.
   */
  @Test
  public void reachRetrySingleNumberIgnoresFieldEvenWhenCampaignStrategyValueDiffers() {
    when(campaign.getRetryStrategy())
        .thenReturn(Constants.RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER);
    when(campaign.getMaxAttemptPerNumber()).thenReturn(5);
    heavilyDialedFirstNumber();

    new ReachRetrySingleNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_1", call.getRetryNumber());
    assertNull(call.getCloseReason());
  }

  /**
   * Karsit kontrol: ayni gecmis ve ayni alan degeri round-robin stratejide ATLAMA uretir. Boylece
   * yukaridaki iki testin "alan yok sayildi" sonucunun gercekten K2'den geldigi kanitlanir.
   */
  @Test
  public void sameFixtureDoesSkipUnderRoundRobinStrategy() {
    when(campaign.getRetryStrategy())
        .thenReturn(Constants.RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER);
    when(campaign.getMaxAttemptPerNumber()).thenReturn(5);
    heavilyDialedFirstNumber();

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
  }

}
