package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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
import com.powerdialer.engine.dialer.retry.RoundRobinNextNumber;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.ResultCodeSchedule;
import com.powerdialer.util.Constants;

/**
 * FEAT-325559 Faz 7 / P2.2 — kolon disi callback numarasi kolonlar tukendiginde DUSMEMELI.
 *
 * <p><b>Bulgu:</b> {@code createRetry} icinde {@code noEligibleNumberLeft()} kontrolu callback
 * dalindan ONCE geliyor ve aday kume yalnizca telefon KOLONLARINDAKI numaralardan olusuyordu
 * ({@code getCandidateNumbers} -&gt; {@code getNumbersInRawData}). Agent'in elle girdigi,
 * kolonlarda bulunmayan bir callback numarasi limitin ALTINDA olsa bile, kolonlarin tumu
 * limitliyse kayit kapaniyordu. Bu K16 ile celisiyor ("kolon disi callback numarasi kendi
 * degeriyle sayilir") ve musterinin KENDI talebiyle olusan randevunun dusmesi over-dialing'den
 * maliyetlidir.</p>
 *
 * <p><b>Cozum yonu:</b> bekleyen callback numarasi da aday kumesine dahil edilir (kendi
 * sayaciyla). {@code createRetry} kontrol SIRASI degismez (basarili kapanis -&gt; genel limit
 * -&gt; numara basina limit) — SINIRLAR geregi.</p>
 */
public class MaxAttemptPerNumberOffColumnCallbackTest {

  private static final int OVERALL_ATTEMPT = 60;
  private static final int MAX_PER_NUMBER = 3;

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
    // isCallback() rawData.has(colResultCallbackDate) uzerinden karar verir; bekleyen callback
    // senaryosu icin binding VE satir degeri birlikte gerekir.
    when(campaign.getColResultCallbackDate()).thenReturn("callback_date");
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

  /**
   * Tek kolon ({@code number_1}) limitini doldurmus, agent kolonlarda olmayan
   * {@code manual_cb} numarasina randevu vermis. Iz: F + 2R (number_1 = 3 arama) + bekleyen CB.
   */
  private void columnExhaustedWithPendingOffColumnCallback(String callbackNumber, int callbackDials) {
    addPhoneColumn("phone_number_1", "number_1", 1);
    StringBuilder trail = new StringBuilder("F;R;R");
    StringBuilder callbacks = new StringBuilder();
    for (int i = 0; i < callbackDials; i++) {
      trail.append(";CB");
      if (i > 0) {
        callbacks.append(",");
      }
      callbacks.append(callbackNumber);
    }
    setRawColumn("call_process", trail.toString());
    setRawColumn("retry_history", "number_1;number_1");
    if (callbackDials > 0) {
      setRawColumn("cb_history", callbacks.toString());
    }
    setRawColumn("callback_date", "45000.5");
    call.setCalledNumber(callbackNumber);
    call.setCallbackNumber(callbackNumber);
    call.setCallbackDate(45000.5D);
  }

  // ---------------------------------------------------------------------
  // P2.2
  // ---------------------------------------------------------------------

  /**
   * Kolon disi callback numarasi limitin ALTINDA (1 arama / 3): kayit KAPANMAMALI ve o numara
   * planlanmali. Duzeltme oncesi {@code noEligibleNumberLeft} yalniz kolonlara baktigi icin
   * kayit kapaniyordu.
   */
  @Test
  public void offColumnCallbackBelowItsLimitKeepsRecordOpen() {
    columnExhaustedWithPendingOffColumnCallback("manual_cb", 1);

    new RoundRobinNextNumber().createRetry(call);

    assertNull("Kayit kapatilmamalidir", call.getCloseReason());
    assertNotNull("Retry planlanmalidir", call.getRetryDate());
    assertEquals("Randevu numarasi planlanmalidir", "manual_cb", call.getRetryNumber());
  }

  /**
   * Kolon disi callback numarasi da limitini doldurduysa kayit kapanir (kriter 7) ve neden
   * callback metnidir.
   */
  @Test
  public void offColumnCallbackAtItsLimitClosesRecordWithCallbackReason() {
    columnExhaustedWithPendingOffColumnCallback("manual_cb", 3);

    new RoundRobinNextNumber().createRetry(call);

    assertEquals(Constants.CLOSE_REASON_MAX_ATTEMPT_ON_CALLBACK, call.getCloseReason());
    assertNull("Yeni retry planlanmamalidir", call.getRetryDate());
  }

  /**
   * Bekleyen callback numarasi aday kumesine dahil edilir; kolon disi olsa bile.
   */
  @Test
  public void pendingCallbackNumberIsPartOfLimitCandidates() {
    columnExhaustedWithPendingOffColumnCallback("manual_cb", 1);

    assertEquals(Arrays.asList("number_1", "manual_cb"), call.getLimitCandidateNumbers());
  }

  /**
   * Bekleyen callback YOKSA aday kumesi yalnizca kolonlardir; mevcut davranis degismedi.
   */
  @Test
  public void candidateSetIsColumnsOnlyWhenNoPendingCallback() {
    addPhoneColumn("phone_number_1", "number_1", 1);
    addPhoneColumn("phone_number_2", "number_2", 2);

    assertEquals(Arrays.asList("number_1", "number_2"), call.getLimitCandidateNumbers());
  }

  /**
   * Callback numarasi kolonlardan biriyle AYNIYSA aday kumesinde tek kez gorunur; sayac
   * bolunmez.
   */
  @Test
  public void callbackNumberEqualToAColumnIsNotDuplicatedInCandidates() {
    addPhoneColumn("phone_number_1", "number_1", 1);
    setRawColumn("callback_date", "45000.5");
    call.setCallbackNumber("number_1");
    call.setCallbackDate(45000.5D);

    assertEquals(Arrays.asList("number_1"), call.getLimitCandidateNumbers());
  }

  /**
   * Kolonlarin tamami limitli VE bekleyen callback yoksa kayit kapanir (kriter 2) — P2.2
   * duzeltmesi erken kapatmayi zayiflatmamali.
   */
  @Test
  public void exhaustedColumnsWithoutCallbackStillCloseRecord() {
    addPhoneColumn("phone_number_1", "number_1", 1);
    setRawColumn("call_process", "F;R;R");
    setRawColumn("retry_history", "number_1;number_1");
    call.setCalledNumber("number_1");
    call.setRetryNumber("number_1");

    new RoundRobinNextNumber().createRetry(call);

    assertEquals(Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED, call.getCloseReason());
  }

  /**
   * Ozellik kapaliyken kolon disi callback dali bugunku davranisini surdurur.
   */
  @Test
  public void featureOffKeepsCallbackBranchUnchanged() {
    when(campaign.getMaxAttemptPerNumber()).thenReturn(null);
    columnExhaustedWithPendingOffColumnCallback("manual_cb", 3);

    new RoundRobinNextNumber().createRetry(call);

    assertNull(call.getCloseReason());
    assertNotNull(call.getRetryDate());
    assertEquals("manual_cb", call.getRetryNumber());
  }

}
