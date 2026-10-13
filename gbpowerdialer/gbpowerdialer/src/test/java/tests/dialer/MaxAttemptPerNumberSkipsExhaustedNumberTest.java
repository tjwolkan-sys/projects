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
import com.powerdialer.model.TelephonyResultCode;

/**
 * FEAT-325559 Faz 3 — Kabul kriterleri 1, 3, 4 ve 5: limitini doldurmus numaranin ATLANMASI.
 *
 * <p>Testler gercek {@code createRetry} akisindan gecer; yani hem eligibility predicate'i hem de
 * bounded-walk yuruyusu birlikte dogrulanir.</p>
 */
public class MaxAttemptPerNumberSkipsExhaustedNumberTest {

  private static final int OVERALL_ATTEMPT = 30;
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
   * Verilen numaralari colSort sirasiyla ekler.
   */
  private void numbers(String... phoneNumbers) {
    for (int i = 0; i < phoneNumbers.length; i++) {
      addPhoneColumn("phone_number_" + (i + 1), phoneNumbers[i], i + 1);
    }
  }

  /**
   * Tutarli bir iz uretir: fresh + verilen retry numaralari. Boylece sayim kursor yolundan
   * (fallback degil) gecer.
   */
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

  private String scheduleRetry() {
    new RoundRobinNextNumber().createRetry(call);
    return call.getRetryNumber();
  }

  // ---------------------------------------------------------------------
  // Kriter 1
  // ---------------------------------------------------------------------

  /**
   * Kriter 1: Tel-1 gecmiste limiti doldurmussa yeni dongude ATLANIR ve sirdaki uygun numara
   * (Tel-2) aranir.
   */
  @Test
  public void exhaustedNumberIsSkippedAndNextEligibleNumberIsDialed() {
    numbers("number_1", "number_2", "number_3");
    // fresh number_1 + 9 retry number_1 = 10 arama -> Tel-1 limitte
    dialHistory("number_1", "number_1", "number_1", "number_1", "number_1",
                "number_1", "number_1", "number_1", "number_1");
    call.setCalledNumber("number_1");

    String retryNumber = scheduleRetry();

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", retryNumber);
    assertNotNull(call.getRetryDate());
  }

  /**
   * Genel limit (30) dolmamis olmasina ragmen atlama yapilir; iki limit BAGIMSIZ degerlendirilir.
   */
  @Test
  public void skipHappensEvenThoughOverallLimitIsNotReached() {
    numbers("number_1", "number_2");
    dialHistory("number_1", "number_1", "number_1", "number_1", "number_1",
                "number_1", "number_1", "number_1", "number_1");
    call.setCalledNumber("number_1");

    assertEquals(10, call.getDialedNumberSequence().size());
    assertTrue("Genel limit henuz dolmadi", call.getDialedNumberSequence().size() < OVERALL_ATTEMPT);
    assertEquals("number_2", scheduleRetry());
  }

  // ---------------------------------------------------------------------
  // Kriter 4
  // ---------------------------------------------------------------------

  /**
   * Kriter 4: Tel-1 limitte, Tel-2 ve Tel-3 sifir. Tel-1 HIC denenmeden atlanir.
   */
  @Test
  public void singleExhaustedNumberIsSkippedWhileOthersAreUntouched() {
    numbers("number_1", "number_2", "number_3");
    dialHistory("number_1", "number_1", "number_1", "number_1", "number_1",
                "number_1", "number_1", "number_1", "number_1");
    call.setCalledNumber("number_1");

    assertEquals("number_2", scheduleRetry());
    assertTrue("Atlanan numara retry gecmisine yazilmamalidir",
        !call.getRetryHistory().get(call.getRetryHistory().size() - 1).equals("number_1"));
  }

  // ---------------------------------------------------------------------
  // Kriter 3 — dengeli dagilmis gecmis, tur tur ilerleme
  // ---------------------------------------------------------------------

  /**
   * Kriter 3'un cekirdek invariant'i: limitini doldurmus numaraya BIR DAHA arama atilmaz.
   * Tel-2 tam limitte (10), Tel-1 ve Tel-3 limitin altinda.
   */
  @Test
  public void numberAtExactLimitNeverReceivesAnotherDial() {
    numbers("number_1", "number_2", "number_3");
    // fresh number_1 + 5 x number_1 (toplam 6), 10 x number_2, 2 x number_3
    dialHistory("number_1", "number_1", "number_1", "number_1", "number_1",
                "number_2", "number_2", "number_2", "number_2", "number_2",
                "number_2", "number_2", "number_2", "number_2", "number_2",
                "number_3", "number_3");
    call.setCalledNumber("number_1");

    assertEquals("Tel-2 tam limitte olmalidir",
        Integer.valueOf(MAX_PER_NUMBER), countOf("number_2"));

    // Halkada number_1 -> number_2 sirasi var; number_2 limitte oldugu icin number_3'e atlanmali.
    assertEquals("number_3", scheduleRetry());
  }

  /**
   * Ard arda birden fazla retry planlandiginda limitli numara hicbir turda secilmez.
   */
  @Test
  public void exhaustedNumberIsSkippedInEverySubsequentCycle() {
    numbers("number_1", "number_2", "number_3");
    dialHistory("number_2", "number_2", "number_2", "number_2", "number_2",
                "number_2", "number_2", "number_2", "number_2", "number_2");
    call.setCalledNumber("number_1");

    RoundRobinNextNumber strategy = new RoundRobinNextNumber();
    List<String> scheduled = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      strategy.createRetry(call);
      scheduled.add(call.getRetryNumber());
    }

    verify(callCleaner, never()).completeCall(call);
    assertTrue("Limitli numaraya hicbir turda arama planlanmamalidir: " + scheduled,
        !scheduled.contains("number_2"));
  }

  // ---------------------------------------------------------------------
  // Kriter 5 — sonuc kodundan bagimsizlik
  // ---------------------------------------------------------------------

  /**
   * Kriter 5: sayac yalnizca arama ADEDINE bakar. Gecmisteki sonuc kodlari (mesgul, cevapsiz,
   * ulasilamadi) hicbir yerde saklanmadigi icin sayim dogal olarak sonuc kodundan bagimsizdir;
   * bu test kaydin TRC'si degistiginde de atlamanin ayni kaldigini gosterir.
   */
  @Test
  public void skipDecisionIsIndependentOfTelephonyResultCode() {
    numbers("number_1", "number_2");
    dialHistory("number_1", "number_1", "number_1", "number_1", "number_1",
                "number_1", "number_1", "number_1", "number_1");
    call.setCalledNumber("number_1");

    TelephonyResultCode userBusy = new TelephonyResultCode();
    userBusy.setCode(17);
    call.setTelephonyResultCode(userBusy);

    assertEquals("number_2", scheduleRetry());
  }

  private Integer countOf(String number) {
    int count = 0;
    for (String dialed : call.getDialedNumberSequence()) {
      if (number.equals(dialed)) {
        count++;
      }
    }
    return count;
  }

}
