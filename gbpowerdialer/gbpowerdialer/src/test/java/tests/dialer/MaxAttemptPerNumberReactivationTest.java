package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.powerdialer.engine.dialer.CallProducer;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.CampaignFilterStatsCalculator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.dialer.retry.RetryStrategy;
import com.powerdialer.engine.dialer.retry.RetryStrategyFactory;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Filter;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.util.Constants;

/**
 * FEAT-325559 Faz 8 — veri manipulasyon ekranindan AKTIVE EDILEN kayitlar.
 *
 * <p>Kabul kriteri 8'in dogal akisi: kayit numara limitiyle kapanir, operator kaydi aktive eder ve
 * yeni bir numara ekler. Aktivasyon SQL'i ({@code CampaignRecordService.activateRecordReturningCount})
 * {@code retry_date}, {@code callback_date}, {@code complete_date} ve {@code is_fetched}'i temizler;
 * {@code retry_number}, {@code callback_number}, arama izi ({@code call_process},
 * {@code retry_history}, {@code cb_history}) ve {@code close_reason} ise satirda KALIR. Satir
 * yeniden fresh fetch'ten gelir.</p>
 *
 * <p><b>K1:</b> producer bayat {@code retry_number}/{@code callback_number}'i bekleyen plan gibi
 * okuyordu; o numara limitteyken yonlendirme bekleyen plan olmadigi icin reddediliyor ve kayit yeni
 * numara HIC aranmadan tekrar kapaniyordu.</p>
 *
 * <p><b>K2:</b> aktivasyon sonrasi aramanin planlama adimi yoktur; izde karsiligi olmadigi icin
 * sayilmiyordu ve yeni numara limitten 1 fazla arama alirdi.</p>
 *
 * <p><b>Korunan invariant:</b> aktivasyondan sonra da hicbir numara limitinden fazla aranmaz ve
 * limitini doldurmamis numara limitine kadar aranir.</p>
 */
public class MaxAttemptPerNumberReactivationTest {

  private static final String UNIQUE_KEY_COLUMN = "unique_key";
  private static final int LOOP_FUSE = 200;
  private static final int LIMIT = 2;

  private Campaign campaign;
  private CallProducer callProducer;
  private DialerFacade dialerFacadeMock;
  private final RetryStrategyFactory strategyFactory = new RetryStrategyFactory();

  @Before
  public void setup() {
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
    campaign.setColUniqueKey(UNIQUE_KEY_COLUMN);
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCbHistory("cb_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setColResultRetryNumber("retry_number");
    campaign.setColResultRetryDate("retry_date");
    campaign.setColResultCallbackDate("callback_date");
    campaign.setColResultCallbackNumber("callback_number");
    campaign.setColResultCloseReason("close_reason");
    campaign.setRetryOveralAttempt(100);
    campaign.setMacroMaxAttempt(100);
    campaign.setMacroInterval(24);
    campaign.setRetryDelay(0D);
    campaign.setMaxAttemptPerNumber(LIMIT);
    campaign.setRetryStrategy(Constants.RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER);
    for (int i = 1; i <= 2; i++) {
      PhoneNumberColumn column = new PhoneNumberColumn();
      column.setColNumber("phone_number_" + i);
      column.setColSort(i);
      column.setEnabled(true);
      campaign.getPhoneNumberColumns().add(column);
    }

    dialerFacadeMock = mock(DialerFacade.class, org.mockito.Answers.RETURNS_DEEP_STUBS.get());
    CampaignFilterStatsCalculator statsMock = mock(CampaignFilterStatsCalculator.class);
    CampaignContext campaignContext = new CampaignContext(campaign, dialerFacadeMock, statsMock);
    callProducer = new CallProducer(campaignContext, statsMock);
  }

  // ---------------------------------------------------------------------
  // Harness: producer -> dial -> strateji -> persist (insertCallResultToDB) -> aktivasyon
  // ---------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private Call produce(JSONObject row) throws Exception {
    JSONArray rawData = new JSONArray();
    rawData.put(row);
    Method method = CallProducer.class.getDeclaredMethod("createCallsFromRawData",
        JSONArray.class, Constants.CALL_TYPES.class, Optional.class);
    method.setAccessible(true);
    Constants.CALL_TYPES callType = row.has("callback_date") ? Constants.CALL_TYPES.CALLBACK
        : row.has("retry_date") ? Constants.CALL_TYPES.RETRY : Constants.CALL_TYPES.FRESH;
    List<Call> calls = (List<Call>) method.invoke(callProducer, rawData, callType, Optional.<Filter>empty());
    Field field = CallProducer.class.getDeclaredField("callsUniqueKeys");
    field.setAccessible(true);
    ((Set<?>) field.get(callProducer)).clear();
    return calls.isEmpty() ? null : calls.get(0);
  }

  /**
   * {@code insertCallResultToDB}'nin anlamli alanlari. Uretimdeki gibi {@code retry_number} YALNIZ
   * doluyken yazilir (bosken satirdaki eski deger kalir), {@code retry_date} ise NULL yazilir.
   */
  private void persist(Call call, JSONObject row) {
    writeOrRemove(row, "call_process", join(call.getCallProcessHistory(), ";"));
    writeOrRemove(row, "retry_history", join(call.getRetryHistory(), ";"));
    writeOrRemove(row, "cb_history", join(call.getCbHistory(), ","));
    if (call.getRetryNumber() != null) {
      row.put("retry_number", call.getRetryNumber());
    }
    writeOrRemove(row, "retry_date", call.getRetryDate() == null ? null : String.valueOf(call.getRetryDate()));
    writeOrRemove(row, "callback_date", call.getCallbackDate() == null ? null : String.valueOf(call.getCallbackDate()));
    if (call.getCloseReason() != null) {
      row.put("close_reason", call.getCloseReason());
    }
    if (call.getCompleteDate() != null) {
      row.put("complete_date", String.valueOf(call.getCompleteDate()));
    }
  }

  /** {@code CampaignRecordService.activateRecordReturningCount}'un temizledigi kolonlar. */
  private void activate(JSONObject row) {
    for (String column : Arrays.asList("complete_date", "is_fetched", "retry_date", "callback_date",
        "start_date", "answer_date", "telephony_result_code")) {
      row.remove(column);
    }
  }

  private void writeOrRemove(JSONObject row, String column, String value) {
    if (value == null || value.isEmpty()) {
      row.remove(column);
    } else {
      row.put(column, value);
    }
  }

  private String join(List<String> values, String separator) {
    return values == null || values.isEmpty() ? null : String.join(separator, values);
  }

  /** Kayit kapanana kadar gercek arama dongusu; numara bazinda GERCEK arama sayisi. */
  private Map<String, Integer> runDialLoop(JSONObject row) throws Exception {
    Map<String, Integer> actualDials = new LinkedHashMap<>();
    for (int iteration = 0; iteration < LOOP_FUSE; iteration++) {
      Call call = produce(row);
      if (call == null) {
        return actualDials;
      }
      actualDials.merge(call.getCalledNumber(), 1, Integer::sum);
      RetryStrategy strategy = strategyFactory.createStrategy(campaign.getRetryStrategy());
      strategy.createRetry(call);
      persist(call, row);
      if (call.getCompleteDate() != null) {
        return actualDials;
      }
    }
    throw new AssertionError("Dongu " + LOOP_FUSE + " adimda kapanmadi");
  }

  private Call newCall(JSONObject row) {
    Call call = new Call();
    call.setCampaign(campaign);
    call.setRawData(row);
    if (row.has("callback_number")) {
      call.setCallbackNumber(row.getString("callback_number"));
    }
    if (row.has("retry_number")) {
      call.setRetryNumber(row.getString("retry_number"));
    }
    return call;
  }

  private String resolve(Call call) throws Exception {
    Method method = CallProducer.class.getDeclaredMethod("resolveCalledNumber", Call.class);
    method.setAccessible(true);
    try {
      return (String) method.invoke(callProducer, call);
    } catch (InvocationTargetException e) {
      throw (Exception) e.getCause();
    }
  }

  /**
   * Strateji kapanisindan sonraki gercekci satir: number_1 limitini doldurdu (F + R = 2), satirda
   * {@code retry_number} kaldi, aktivasyon yapildi, ikinci kolona YENI numara eklendi.
   */
  private JSONObject reactivatedRowWithStaleRetryNumberAndNewNumber() {
    JSONObject row = new JSONObject();
    row.put(UNIQUE_KEY_COLUMN, "8001");
    row.put("phone_number_1", "number_1");
    row.put("phone_number_2", "number_new");
    row.put("call_process", "F;R");
    row.put("retry_history", "number_1");
    row.put("retry_number", "number_1");
    row.put("close_reason", Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED);
    return row;
  }

  // =====================================================================
  // K1 + K2 — uctan uca
  // =====================================================================

  /**
   * Kabul kriteri 8'in dogal akisi uctan uca: tek numarali kayit limitle kapanir, aktive edilir,
   * sonraki kolona yeni numara eklenir. Yeni numara TAM limit kadar aranmali (K1: hic aranmiyordu;
   * K2 duzeltilmeden limit+1 aranirdi), eski numara bir daha aranmamali.
   */
  @Test
  public void newNumberAddedAfterReactivationReceivesExactlyLimitDials() throws Exception {
    JSONObject row = new JSONObject();
    row.put(UNIQUE_KEY_COLUMN, "8000");
    row.put("phone_number_1", "number_1");

    Map<String, Integer> firstSession = runDialLoop(row);
    assertEquals("Ilk oturum: number_1 limit kadar", Integer.valueOf(LIMIT), firstSession.get("number_1"));
    assertTrue("Kayit kapanmis olmali", row.has("complete_date"));
    assertEquals("Kapanis sonrasi retry_number satirda kalir (uretim davranisi)",
        "number_1", row.getString("retry_number"));

    activate(row);
    row.put("phone_number_2", "number_new");

    Map<String, Integer> secondSession = runDialLoop(row);

    assertEquals("Yeni numara TAM limit kadar aranmali",
        Integer.valueOf(LIMIT), secondSession.get("number_new"));
    assertNull("Limitini dolduran eski numara bir daha aranmamali", secondSession.get("number_1"));
  }

  /**
   * Aktivasyon sonrasi da hicbir numara limitini asmaz — iki numarali kayitta ikinci oturum.
   */
  @Test
  public void noNumberExceedsItsLimitAcrossReactivation() throws Exception {
    JSONObject row = new JSONObject();
    row.put(UNIQUE_KEY_COLUMN, "8002");
    row.put("phone_number_1", "number_1");

    Map<String, Integer> firstSession = runDialLoop(row);
    activate(row);
    row.put("phone_number_2", "number_new");
    Map<String, Integer> secondSession = runDialLoop(row);

    Map<String, Integer> total = new LinkedHashMap<>(firstSession);
    secondSession.forEach((number, dials) -> total.merge(number, dials, Integer::sum));
    for (Map.Entry<String, Integer> entry : total.entrySet()) {
      assertTrue(entry.getKey() + " limitini asti: " + entry.getValue(), entry.getValue() <= LIMIT);
    }
  }

  // =====================================================================
  // K1 — producer, bayat isaretciler
  // =====================================================================

  @Test
  public void staleRetryNumberOfExhaustedNumberDoesNotBlockNewNumber() throws Exception {
    Call call = newCall(reactivatedRowWithStaleRetryNumberAndNewNumber());

    assertEquals("number_new", resolve(call));
  }

  /**
   * Callback vaktinde numarasi limitte oldugu icin kapanan kayit: izi {@code F;R;CB} (F + R = 2
   * tamamlanmis arama, CB aranmamis plan). {@code setClosedWithoutDialing} callback alanlarini
   * bilincli korur; aktivasyon {@code callback_date}'i temizler ama {@code callback_number} kalir.
   */
  private JSONObject reactivatedRowWithStaleCallbackNumber(String uniqueKey) {
    JSONObject row = new JSONObject();
    row.put(UNIQUE_KEY_COLUMN, uniqueKey);
    row.put("phone_number_1", "number_1");
    row.put("call_process", "F;R;CB");
    row.put("retry_history", "number_1");
    row.put("cb_history", "number_1");
    row.put("callback_number", "number_1");
    row.put("close_reason", Constants.CLOSE_REASON_MAX_ATTEMPT_ON_CALLBACK);
    return row;
  }

  @Test
  public void staleCallbackNumberOfExhaustedNumberDoesNotBlockNewNumber() throws Exception {
    JSONObject row = reactivatedRowWithStaleCallbackNumber("8003");
    row.put("phone_number_2", "number_new");
    Call call = newCall(row);

    assertEquals("number_new", resolve(call));
    assertFalse("Vadesi gelmis callback plani yok; kayit callback olarak ele alinmaz", call.isCallback());
  }

  /**
   * Yeni numara yoksa kayit yine kapanir; etiket bayat callback numarasina gore DEGIL gercek
   * nedene gore yazilir.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void reactivatedRecordWithoutAnyEligibleNumberIsClosedWithNumberLimitReason() throws Exception {
    JSONObject row = reactivatedRowWithStaleCallbackNumber("8004");

    assertNull(produce(row));

    ArgumentCaptor<Collection<String>> keys = ArgumentCaptor.forClass(Collection.class);
    verify(dialerFacadeMock.getCampaignDbOperations())
        .setClosedWithoutDialing(any(Campaign.class), keys.capture(), eq(Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED));
    assertTrue(keys.getValue().contains("8004"));
  }

  // =====================================================================
  // K2 — aktivasyon sonrasi arama izde tek girdiyle kaydedilir
  // =====================================================================

  @Test
  public void reactivationDialIsRecordedInTrailInLockstep() throws Exception {
    Call call = newCall(reactivatedRowWithStaleRetryNumberAndNewNumber());

    resolve(call);

    assertEquals("call_process'e R eklenmeli", Arrays.asList("F", "R", "R"), call.getCallProcessHistory());
    assertEquals("retry_history'ye fiilen aranan numara eklenmeli",
        Arrays.asList("number_1", "number_new"), call.getRetryHistory());
    assertEquals("Hangup sonrasi okuma aktivasyon aramasini saymali",
        Arrays.asList("number_1", "number_1", "number_new"), call.getDialedNumberSequence());
  }

  // =====================================================================
  // Karakterizasyon — dalin disinda kalmasi gerekenler
  // =====================================================================

  /**
   * Ozellik KAPALIYKEN davranis birebir eskisi gibi: aktive satir bayat {@code retry_number}'dan
   * aranir, iz degismez.
   */
  @Test
  public void featureOffReactivatedRecordStillDialsStaleRetryNumberWithoutTouchingTrail() throws Exception {
    campaign.setMaxAttemptPerNumber(null);
    Call call = newCall(reactivatedRowWithStaleRetryNumberAndNewNumber());

    assertEquals("number_1", resolve(call));
    assertEquals(Arrays.asList("F", "R"), call.getCallProcessHistory());
    assertEquals(Arrays.asList("number_1"), call.getRetryHistory());
  }

  /** Hic islem gormemis kayit aktive kayit dalina GIRMEZ; iz bos kalir. */
  @Test
  public void untouchedRecordIsNotTreatedAsReactivated() throws Exception {
    JSONObject row = new JSONObject();
    row.put(UNIQUE_KEY_COLUMN, "8005");
    row.put("phone_number_1", "number_1");
    Call call = newCall(row);

    assertEquals("number_1", resolve(call));
    assertTrue(call.getCallProcessHistory().isEmpty());
    assertTrue(call.getRetryHistory().isEmpty());
  }

  /** Bekleyen retry plani olan satir aktive kayit dalina GIRMEZ; mevcut akis aynen calisir. */
  @Test
  public void rowWithPendingRetryPlanIsNotTreatedAsReactivated() throws Exception {
    JSONObject row = new JSONObject();
    row.put(UNIQUE_KEY_COLUMN, "8006");
    row.put("phone_number_1", "number_1");
    row.put("phone_number_2", "number_2");
    row.put("call_process", "F;R");
    row.put("retry_history", "number_2");
    row.put("retry_number", "number_2");
    row.put("retry_date", "46290.5");
    Call call = newCall(row);

    assertEquals("number_2", resolve(call));
    assertEquals("Producer bekleyen planli satirin izine ekleme YAPMAZ",
        Arrays.asList("F", "R"), call.getCallProcessHistory());
    assertFalse(call.getRetryHistory().contains("number_1"));
  }

  /** Ayni kural {@code getLimitCandidateNumbers} icin: aktive kayitta bayat callback aday degil. */
  @Test
  public void reactivatedRecordDoesNotKeepStaleCallbackAsLimitCandidate() throws Exception {
    JSONObject row = new JSONObject();
    row.put(UNIQUE_KEY_COLUMN, "8007");
    row.put("phone_number_1", "number_1");
    row.put("call_process", "F;CB");
    row.put("cb_history", "manual_cb");
    row.put("callback_number", "manual_cb");
    Call call = newCall(row);

    assertEquals("Callback vadesi (callback_date) yokken kolon disi numara aday sayilmaz",
        new ArrayList<>(Arrays.asList("number_1")), call.getLimitCandidateNumbers());
  }
}
