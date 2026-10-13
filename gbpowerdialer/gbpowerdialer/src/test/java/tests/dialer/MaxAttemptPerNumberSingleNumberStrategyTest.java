package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.CallProducer;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.CampaignFilterStatsCalculator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.dialer.retry.ReachRetrySingleNumber;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Filter;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.ResultCodeSchedule;
import com.powerdialer.util.Constants;

/**
 * FEAT-325559 — "Reach retry limit for single number" (strateji 0) numara basina limitten MUAFTIR.
 *
 * <p><b>Karar (29.09.2026):</b> strateji 0 tek numarayi arar; numara basina limit bu stratejiye
 * uygulanmaz, strateji 1 ("Reach retry limit before next number") gibi alan YOK SAYILIR. UI'da alan
 * ve checkbox bu stratejide de pasiftir. Kayit, stratejinin kendi kurali olan genel limitle
 * (Toplam Deneme) durur; aranan numara degismez.</p>
 *
 * <p>Onceki karar (Faz 8b) aranan numara limitini doldurunca siradaki numaraya geciyordu; test
 * ortaminda strateji 0'in beklenen "tek numara" davranisini bozdugu goruldu ve kaldirildi. Bu
 * sinifin gecis ve DNC yuruyusu vakalari cikarildi; kapanis ve uctan uca vakalari yeni karara gore
 * tersine cevrildi.</p>
 */
public class MaxAttemptPerNumberSingleNumberStrategyTest {

  private static final int LIMIT = 2;

  // ---------------------------------------------------------------------
  // Strateji katmani (mock harness)
  // ---------------------------------------------------------------------

  private final Call call = spy(new Call());
  private final Campaign mockCampaign = mock(Campaign.class);
  private final JSONObject rawData = mock(JSONObject.class);
  private final List<PhoneNumberColumn> numberColumns = new ArrayList<>();

  @Before
  public void setup() {
    when(mockCampaign.getColResultRetryHistory()).thenReturn("retry_history");
    when(mockCampaign.getColResultCbHistory()).thenReturn("cb_history");
    when(mockCampaign.getColResultCallProcess()).thenReturn("call_process");
    when(mockCampaign.getColResultDNCNumbers()).thenReturn("dnc_numbers");
    when(mockCampaign.getRetryOveralAttempt()).thenReturn(20);
    when(mockCampaign.getMacroMaxAttempt()).thenReturn(20);
    when(mockCampaign.getMacroInterval()).thenReturn(1);
    when(mockCampaign.getRetryDelay()).thenReturn(1D);
    when(mockCampaign.getMaxAttemptPerNumber()).thenReturn(LIMIT);
    when(mockCampaign.getRetryStrategy()).thenReturn(Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER);
    when(mockCampaign.getResultCodeSchedules()).thenReturn(new ArrayList<ResultCodeSchedule>());
    when(mockCampaign.getEnabledPhoneNumberColumns()).thenAnswer(invocation -> {
      List<PhoneNumberColumn> sorted = new ArrayList<>(numberColumns);
      sorted.sort(Comparator.comparingInt(PhoneNumberColumn::getColSort));
      return sorted;
    });
    CallOriginator callOriginator = mock(CallOriginator.class);
    when(call.getCallOriginator()).thenReturn(callOriginator);
    when(callOriginator.getCallCleaner()).thenReturn(mock(CallCleaner.class));
    call.setCampaign(mockCampaign);
    call.setRawData(rawData);

    setupProducerHarness();
  }

  private void numbers(String... phoneNumbers) {
    for (int i = 0; i < phoneNumbers.length; i++) {
      PhoneNumberColumn column = new PhoneNumberColumn();
      column.setColNumber("phone_number_" + (i + 1));
      column.setColSort(i + 1);
      numberColumns.add(column);
      when(rawData.has(column.getColNumber())).thenReturn(true);
      when(rawData.getString(column.getColNumber())).thenReturn(phoneNumbers[i]);
    }
  }

  private void setRawColumn(String columnName, String value) {
    when(rawData.has(columnName)).thenReturn(true);
    when(rawData.get(columnName)).thenReturn(value);
    when(rawData.getString(columnName)).thenReturn(value);
  }

  /** Aranan numaranin tamamlanmis arama izi: F (fresh) + verilen retry numaralari. */
  private void dialHistory(String... retryNumbers) {
    StringBuilder trail = new StringBuilder("F");
    for (int i = 0; i < retryNumbers.length; i++) {
      trail.append(";R");
    }
    setRawColumn("call_process", trail.toString());
    if (retryNumbers.length > 0) {
      setRawColumn("retry_history", String.join(";", retryNumbers));
    }
  }

  /**
   * Cekirdek davranis: aranan numara limitini doldurdu, kayitta baska numara da var. Strateji
   * muaf oldugu icin ayni numarayi yeniden planlar; ikinci numaraya GECMEZ, kaydi kapatmaz.
   */
  @Test
  public void keepsDialingCalledNumberEvenAfterItReachesItsLimit() {
    numbers("number_1", "number_2");
    dialHistory("number_1");
    call.setCalledNumber("number_1");

    new ReachRetrySingleNumber().createRetry(call);

    assertEquals("Strateji aranan numarada kalmali", "number_1", call.getRetryNumber());
    assertEquals(Arrays.asList("number_1", "number_1"), call.getRetryHistory());
    assertNull(call.getCloseReason());
    assertNotNull(call.getRetryDate());
  }

  /** Strateji ritmi korunur: numara limitinin altindayken AYNI numara tekrar aranir. */
  @Test
  public void keepsDialingTheSameNumberWhileItIsBelowItsLimit() {
    numbers("number_1", "number_2");
    call.setCalledNumber("number_1");

    new ReachRetrySingleNumber().createRetry(call);

    assertEquals("number_1", call.getRetryNumber());
  }

  /**
   * Tum numaralar limitini doldurmus olsa bile strateji 0'da erken kapatma yoktur; kaydi yalniz
   * genel limit durdurur ve kapanis nedeni yazilmaz.
   */
  @Test
  public void doesNotCloseRecordEvenWhenEveryNumberIsExhausted() {
    numbers("number_1", "number_2");
    dialHistory("number_1", "number_2", "number_2");
    call.setCalledNumber("number_2");

    new ReachRetrySingleNumber().createRetry(call);

    assertNull(call.getCloseReason());
    assertNotNull("Genel limit dolmadigi icin tekrar arama planlanmali", call.getRetryDate());
    assertEquals("number_2", call.getRetryNumber());
  }

  /** Ozellik KAPALIYKEN strateji 0 birebir eskisi gibi: hep ayni numara, yuruyus yok. */
  @Test
  public void featureOffKeepsSingleNumberBehaviourUnchanged() {
    when(mockCampaign.getMaxAttemptPerNumber()).thenReturn(null);
    numbers("number_1", "number_2");
    dialHistory("number_1", "number_1", "number_1");
    call.setCalledNumber("number_1");

    new ReachRetrySingleNumber().createRetry(call);

    assertEquals("number_1", call.getRetryNumber());
  }

  // ---------------------------------------------------------------------
  // Uctan uca: producer -> dial -> strateji -> persist
  // ---------------------------------------------------------------------

  private Campaign campaign;
  private CallProducer callProducer;

  private void setupProducerHarness() {
    campaign = new Campaign();
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setState(Constants.CAMPAIGN_STATE.RUNNING);
    campaign.setEndListBehaviour(Constants.END_LIST_BEHAVIOUR.CONTINUE);
    campaign.setSkillEnabled(false);
    campaign.setDncEnabled(false);
    campaign.setAllowCallback(true);
    campaign.setCallbackToAppointer(false);
    campaign.setCacheLimit(100);
    campaign.setConcurrentLines(1);
    campaign.setRetryRatio(100);
    campaign.setColUniqueKey("unique_key");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCbHistory("cb_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setColResultRetryNumber("retry_number");
    campaign.setColResultRetryDate("retry_date");
    campaign.setColResultCallbackDate("callback_date");
    campaign.setColResultCallbackNumber("callback_number");
    campaign.setRetryOveralAttempt(100);
    campaign.setMacroMaxAttempt(100);
    campaign.setMacroInterval(24);
    campaign.setRetryDelay(0D);
    campaign.setMaxAttemptPerNumber(LIMIT);
    campaign.setRetryStrategy(Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER);
    for (int i = 1; i <= 3; i++) {
      PhoneNumberColumn column = new PhoneNumberColumn();
      column.setColNumber("phone_number_" + i);
      column.setColSort(i);
      column.setEnabled(true);
      campaign.getPhoneNumberColumns().add(column);
    }
    DialerFacade dialerFacadeMock = mock(DialerFacade.class, org.mockito.Answers.RETURNS_DEEP_STUBS.get());
    CampaignFilterStatsCalculator statsMock = mock(CampaignFilterStatsCalculator.class);
    callProducer = new CallProducer(new CampaignContext(campaign, dialerFacadeMock, statsMock), statsMock);
  }

  @SuppressWarnings("unchecked")
  private Call produce(JSONObject row) throws Exception {
    JSONArray raw = new JSONArray();
    raw.put(row);
    Method method = CallProducer.class.getDeclaredMethod("createCallsFromRawData",
        JSONArray.class, Constants.CALL_TYPES.class, Optional.class);
    method.setAccessible(true);
    Constants.CALL_TYPES callType = row.has("retry_date") ? Constants.CALL_TYPES.RETRY : Constants.CALL_TYPES.FRESH;
    List<Call> calls = (List<Call>) method.invoke(callProducer, raw, callType, Optional.<Filter>empty());
    Field field = CallProducer.class.getDeclaredField("callsUniqueKeys");
    field.setAccessible(true);
    ((Set<?>) field.get(callProducer)).clear();
    return calls.isEmpty() ? null : calls.get(0);
  }

  private void persist(Call produced, JSONObject row) {
    putOrRemove(row, "call_process", produced.getCallProcessHistory().isEmpty() ? null
        : String.join(";", produced.getCallProcessHistory()));
    putOrRemove(row, "retry_history", produced.getRetryHistory().isEmpty() ? null
        : String.join(";", produced.getRetryHistory()));
    if (produced.getRetryNumber() != null) {
      row.put("retry_number", produced.getRetryNumber());
    }
    putOrRemove(row, "retry_date", produced.getRetryDate() == null ? null : String.valueOf(produced.getRetryDate()));
  }

  private void putOrRemove(JSONObject row, String column, String value) {
    if (value == null) {
      row.remove(column);
    } else {
      row.put(column, value);
    }
  }

  /**
   * Strateji 0 uctan uca (producer -&gt; strateji -&gt; persist): alan dolu olsa da BUTUN aramalar
   * ilk numaraya gider; ne strateji ne producer diger numaralara gecer. Kaydi genel limit
   * durdurur: Toplam Deneme 5 + ilk arama = 6 arama. Her adimda producer stratejinin planladigi
   * numarayi arar.
   */
  @Test
  public void endToEndOnlyTheFirstNumberIsDialedUntilTheOverallLimit() throws Exception {
    campaign.setRetryOveralAttempt(5);
    campaign.setMacroMaxAttempt(5);
    JSONObject row = new JSONObject();
    row.put("unique_key", "9100");
    row.put("phone_number_1", "number_1");
    row.put("phone_number_2", "number_2");
    row.put("phone_number_3", "number_3");

    List<String> dialOrder = new ArrayList<>();
    Map<String, Integer> dials = new LinkedHashMap<>();
    String plannedNumber = null;
    Call lastCall = null;
    for (int iteration = 0; iteration < 100; iteration++) {
      Call produced = produce(row);
      if (produced == null) {
        break;
      }
      if (plannedNumber != null) {
        assertEquals("Producer stratejinin planladigi numarayi aramali (adim " + iteration + ")",
            plannedNumber, produced.getCalledNumber());
      }
      dialOrder.add(produced.getCalledNumber());
      dials.merge(produced.getCalledNumber(), 1, Integer::sum);
      lastCall = produced;

      new ReachRetrySingleNumber().createRetry(produced);
      if (produced.getCompleteDate() != null) {
        break;
      }
      plannedNumber = produced.getRetryNumber();
      persist(produced, row);
    }

    assertEquals("Butun aramalar ilk numaraya gitmeli",
        Arrays.asList("number_1", "number_1", "number_1", "number_1", "number_1", "number_1"), dialOrder);
    assertEquals("Diger numaralar hic aranmamali", 1, dials.size());
    assertNotNull("Kayit genel limitle kapanmali", lastCall.getCompleteDate());
    assertNull("Genel limit kapanisinda numara limiti nedeni yazilmamali", lastCall.getCloseReason());
  }
}
