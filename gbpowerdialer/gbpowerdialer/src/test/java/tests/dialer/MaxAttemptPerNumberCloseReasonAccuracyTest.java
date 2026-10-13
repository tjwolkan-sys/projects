package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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
import com.powerdialer.model.ResultCodeSchedule;
import com.powerdialer.util.Constants;

/**
 * FEAT-325559 Faz 7 / P2.1a — kapanis nedeni GERCEK nedeni anlatmali (strateji katmani).
 *
 * <p><b>Bulgu:</b> {@code RetryStrategy.noEligibleNumberLeft} DNC'yi de "uygun degil" sayiyor.
 * Ozellik acikken tum numaralari DNC'de olan kayit bu daldan kapanip
 * {@code CLOSE_REASON_NUMBER_LIMIT_REACHED} yaziyordu; gercek neden DNC. Kapanisin KENDISI
 * dogru (strateji 0 ve 4 icin bu dal DNC tukenmesine karsi yeni ve dogru bir kapanis yolu, o
 * stratejilerde eskiden guard yoktu) ama ETIKETI yanlisti ve raporu okuyan is birimini
 * yaniltirdi.</p>
 *
 * <p><b>Karar:</b> yeni bir {@code CLOSE_REASON_*} sabiti EKLENMEDI ve mevcut uc metnin degeri
 * DEGISTIRILMEDI (musteriye verilen metinler). DNC kaynakli kapanista neden BOS birakilir:
 * (1) yeni bir musteri-gorunur metin is birimiyle mutabik degil ve lokalizasyon karari (K14)
 * hala acik; (2) bos birakmak DNC kapanislarinda bugunku (ozellik-kapali) davranisi birebir
 * korur. Tek kriter: rapor yaniltmasin.</p>
 *
 * <p>P2.1'in producer tarafi ({@code resolveUncallableReason})
 * {@code CallProducerFreshNumberSelectionCharacterizationTest} icinde dogrulanir.</p>
 */
public class MaxAttemptPerNumberCloseReasonAccuracyTest {

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
    when(campaign.getColResultDNCNumbers()).thenReturn("dnc_numbers");
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
  // ---------------------------------------------------------------------
  // P2.1 (a) — strateji katmani
  // ---------------------------------------------------------------------

  /**
   * Tum numaralari DNC'de olan kayit kapanir (kapanisin KENDISI dogru; strateji 0 ve 4 icin bu
   * dal yeni bir kapanis yolu ve mevcut DNC guard'i olmayan o stratejilerde iyilestirme) ama
   * numara-limiti metni YAZILMAZ.
   */
  @Test
  public void dncDrivenCloseDoesNotWriteNumberLimitReason() {
    numbers("number_1", "number_2");
    setRawColumn("dnc_numbers", "number_1;number_2");
    dialHistory();
    call.setCalledNumber("number_1");
    call.setRetryNumber("number_1");

    new RoundRobinNextNumber().createRetry(call);

    assertNull("DNC kaynakli kapanista numara-limiti metni yazilmamalidir", call.getCloseReason());
    assertNull("Yeni retry planlanmamalidir", call.getRetryDate());
  }

  /**
   * Karsit kontrol: limit kaynakli kapanista metin AYNEN yazilir (Faz 3 davranisi korunur).
   */
  @Test
  public void limitDrivenCloseStillWritesNumberLimitReason() {
    numbers("number_1", "number_2");
    dialHistory("number_1", "number_1", "number_2", "number_2", "number_2");
    call.setCalledNumber("number_1");
    call.setRetryNumber("number_1");

    new RoundRobinNextNumber().createRetry(call);

    assertEquals(Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED, call.getCloseReason());
  }

  /**
   * Karisik durum: bir numara DNC'de, digeri limitini doldurmus. En az bir numara LIMIT
   * yuzunden elendigi icin limit metni yazilir — rapor yaniltici olmaz.
   */
  @Test
  public void mixedDncAndLimitCloseWritesNumberLimitReason() {
    numbers("number_1", "number_2");
    setRawColumn("dnc_numbers", "number_2");
    dialHistory("number_1", "number_1");
    call.setCalledNumber("number_1");
    call.setRetryNumber("number_1");

    new RoundRobinNextNumber().createRetry(call);

    assertEquals(Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED, call.getCloseReason());
  }

  /**
   * Ozellik kapaliyken DNC tukenmesi mevcut guard'dan kapanir ve nedene HICBIR SEY yazilmaz;
   * bu yol degismedi.
   */
  @Test
  public void featureOffDncCloseWritesNoReason() {
    when(campaign.getMaxAttemptPerNumber()).thenReturn(null);
    numbers("number_1", "number_2");
    setRawColumn("dnc_numbers", "number_1;number_2");
    dialHistory();
    call.setCalledNumber("number_1");
    call.setRetryNumber("number_1");

    new RoundRobinNextNumber().createRetry(call);

    assertNull(call.getCloseReason());
  }

  // P2.1 (b) producer tarafi CallProducerFreshNumberSelectionCharacterizationTest icinde durur;
  // o sinif producer harness'ini (createCallsFromRawData + verifyClosedWithoutDialing) zaten
  // tasiyor ve burada ikinci bir kopyasini tutmak gereksizdi.

}
