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
import com.powerdialer.model.ResultCode;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.util.Constants;

/**
 * FEAT-325559 Faz 1 karakterizasyonu: "basarili kapanis" karari.
 *
 * <p>Talep, musteri yanitladiginda arama surecinin tum limitler beklenmeksizin
 * DERHAL ve KALICI sonlandirilmasinin netlestirilmesini istiyor. Bu kural
 * bugun {@code RetryStrategy.createRetry} girisindeki
 * {@code (isCallAnswered() && !isResultCodeAllowsRetries())} kosulunda
 * uygulaniyor. Asagidaki matris o kurali kilitler; Faz 3'te eklenecek numara
 * basina limit bu karari EZMEMELIDIR ve bu yolda kapanis nedeni
 * YAZILMAMALIDIR.</p>
 */
public class RetryStrategySuccessfulCloseMatrixCharacterizationTest {

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
    when(campaign.getRetryOveralAttempt()).thenReturn(5);
    when(campaign.getMacroMaxAttempt()).thenReturn(5);
    when(campaign.getMacroInterval()).thenReturn(24);
    when(campaign.getRetryDelay()).thenReturn(1D);

    when(call.getCallOriginator()).thenReturn(callOriginator);
    when(callOriginator.getCallCleaner()).thenReturn(callCleaner);

    call.setCampaign(campaign);
    call.setRawData(rawData);

    addPhoneColumn("phone_number_1", "number_1");
    addPhoneColumn("phone_number_2", "number_2");
    call.setCalledNumber("number_1");
  }

  private void addPhoneColumn(String columnName, String phoneNumber) {
    PhoneNumberColumn column = new PhoneNumberColumn();
    column.setColNumber(columnName);
    numberColumns.add(column);
    when(rawData.has(columnName)).thenReturn(true);
    when(rawData.getString(columnName)).thenReturn(phoneNumber);
  }

  private static TelephonyResultCode telephonyResultCode(int code) {
    TelephonyResultCode trc = new TelephonyResultCode();
    trc.setCode(code);
    return trc;
  }

  private static ResultCode userResultCode(boolean finalStatus, boolean dncIdentifier) {
    ResultCode urc = new ResultCode();
    urc.setFinalStatus(finalStatus);
    urc.setDncIdentifier(dncIdentifier);
    return urc;
  }

  /**
   * Musteri yanitladi (TRC 16 = normal clearing) ve agent bir sonuc kodu
   * girmedi: kayit KAPANIR. Bu, "yanit alindi, surec bitti" kuralinin
   * bugunku hali.
   */
  @Test
  public void answeredCallWithoutUserResultCodeCompletesRecord() {
    call.setTelephonyResultCode(telephonyResultCode(Constants.TELEPHONY_RC_NORMAL_CLEARING));

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
    assertTrue(call.getRetryHistory().isEmpty());
  }

  /**
   * Yanit + kesin (final) sonuc kodu: kayit KAPANIR.
   */
  @Test
  public void answeredCallWithFinalUserResultCodeCompletesRecord() {
    call.setTelephonyResultCode(telephonyResultCode(Constants.TELEPHONY_RC_NORMAL_CLEARING));
    call.setUserResultCode(userResultCode(true, false));

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
  }

  /**
   * Yanit + kesin OLMAYAN sonuc kodu: retry DEVAM eder.
   */
  @Test
  public void answeredCallWithNonFinalUserResultCodeSchedulesRetry() {
    call.setTelephonyResultCode(telephonyResultCode(Constants.TELEPHONY_RC_NORMAL_CLEARING));
    call.setUserResultCode(userResultCode(false, false));

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
    assertNotNull(call.getRetryDate());
  }

  /**
   * Yanit + DNC isaretleyici sonuc kodu: final olsa bile retry DEVAM eder
   * (kalan numaralara gecilebilmesi icin).
   */
  @Test
  public void answeredCallWithDncIdentifierResultCodeSchedulesRetryEvenWhenFinal() {
    call.setTelephonyResultCode(telephonyResultCode(Constants.TELEPHONY_RC_NORMAL_CLEARING));
    call.setUserResultCode(userResultCode(true, true));

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
    assertNotNull(call.getRetryDate());
  }

  /**
   * Yanit ALINMADI (TRC 16 disi, ornek: user busy): sonuc kodu ne olursa olsun
   * limitler dahilinde retry devam eder.
   */
  @Test
  public void unansweredCallSchedulesRetryRegardlessOfResultCode() {
    call.setTelephonyResultCode(telephonyResultCode(17));

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
  }

  /**
   * TRC hic set edilmemisse de yanit alinmis sayilmaz.
   */
  @Test
  public void callWithoutTelephonyResultCodeIsNotTreatedAsAnswered() {
    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_2", call.getRetryNumber());
  }

  /**
   * Yanit alinmis kayitta gecmis limiti dolmamis olsa bile kapanis olur; yani
   * basarili kapanis limit kontrolunden ONCE degerlendirilir.
   */
  @Test
  public void successfulCloseTakesPrecedenceOverRemainingAttempts() {
    when(rawData.has("retry_history")).thenReturn(true);
    when(rawData.getString("retry_history")).thenReturn("number_1");
    call.setTelephonyResultCode(telephonyResultCode(Constants.TELEPHONY_RC_NORMAL_CLEARING));

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
    assertEquals("Basarili kapanista retry gecmisine yeni kayit eklenmez", 1, call.getRetryHistory().size());
  }

}
