package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

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
import com.powerdialer.util.Utils;

/**
 * FEAT-325559 — UCTAN UCA arama dongusu.
 *
 * <p>Bu testin varlik sebebi: numara basina limit motorun IKI katmaninda uygulaniyor ve her iki
 * katman ayni sayaci FARKLI zamanlarda okuyor:</p>
 *
 * <ul>
 * <li>{@code RetryStrategy} hangup SONRASI okur; izdeki son girdi YAPILMIS aramadir.</li>
 * <li>{@code CallProducer} arama ONCESI okur; izdeki son girdi YAPILACAK aramadir
 * ({@code RetryStrategy.updateRetryHistory} plani yazar,
 * {@code ManagerConnectionDecorator.manageRetries} hemen ardindan
 * {@code insertCallResultToDB} ile persist eder).</li>
 * </ul>
 *
 * <p>Katmanlar ayri ayri, elle kurulmus fixture'larla test edildiginde ikisi de dogru gorunuyor;
 * BIRLESIMI yanlis. Bu test tek kayit uzerinde gercek dongu kurar (producer -&gt; dial -&gt;
 * strateji -&gt; persist) ve GERCEK arama sayisini numara bazinda sayar.</p>
 *
 * <p><b>Korunan invariant:</b> bir numara {@code maxAttemptPerNumber} degeri kadar GERCEK arama
 * alir — {@code limit+1}'inci arama hicbir kosulda cikmaz, {@code limit}'inci arama da
 * KACIRILMAZ.</p>
 */
public class MaxAttemptPerNumberEndToEndDialLoopTest {

  private static final String UNIQUE_KEY_COLUMN = "unique_key";
  private static final int LOOP_FUSE = 500;

  private Campaign campaign;
  private CallProducer callProducer;
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
    // Genel limit ve macro bu testte KISITLAMAMALI; olculen sey yalniz numara basina limit.
    campaign.setRetryOveralAttempt(500);
    campaign.setMacroMaxAttempt(500);
    campaign.setMacroInterval(24);
    campaign.setRetryDelay(0D);
    campaign.setRetryStrategy(Constants.RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER);

    DialerFacade dialerFacadeMock = mock(DialerFacade.class, org.mockito.Answers.RETURNS_DEEP_STUBS.get());
    CampaignFilterStatsCalculator statsMock = mock(CampaignFilterStatsCalculator.class);
    CampaignContext campaignContext = new CampaignContext(campaign, dialerFacadeMock, statsMock);
    callProducer = new CallProducer(campaignContext, statsMock);
  }

  private void numbers(String... phoneNumbers) {
    for (int i = 0; i < phoneNumbers.length; i++) {
      PhoneNumberColumn column = new PhoneNumberColumn();
      column.setColNumber("phone_number_" + (i + 1));
      column.setColSort(i + 1);
      column.setEnabled(true);
      campaign.getPhoneNumberColumns().add(column);
    }
  }

  private JSONObject newRow(String uniqueKey, String... phoneNumbers) {
    JSONObject row = new JSONObject();
    row.put(UNIQUE_KEY_COLUMN, uniqueKey);
    for (int i = 0; i < phoneNumbers.length; i++) {
      row.put("phone_number_" + (i + 1), phoneNumbers[i]);
    }
    return row;
  }

  /**
   * Producer fazi: satirdan {@link Call} kurar. Aranabilir numara yoksa {@code null} doner
   * (uretimde kayit {@code setClosedWithoutDialing} ile kapatilir).
   */
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
    // callsUniqueKeys tick basina sifirlanir; dongude ayni key tekrar geldigi icin temizlenmeli.
    Method reset = CallProducer.class.getDeclaredMethod("fetchCalls");
    reset.setAccessible(true);
    clearProducedKeys();
    return calls.isEmpty() ? null : calls.get(0);
  }

  private void clearProducedKeys() throws Exception {
    java.lang.reflect.Field field = CallProducer.class.getDeclaredField("callsUniqueKeys");
    field.setAccessible(true);
    ((java.util.Set<?>) field.get(callProducer)).clear();
  }

  /**
   * Persist fazi: {@code insertCallResultToDB}'nin bu test icin anlamli alanlarini satira yazar.
   * NULL degerler satirdan CIKARILIR; {@code DBEngine} NULL kolonlari JSON'a hic koymuyor.
   */
  private void persist(Call call, JSONObject row) {
    writeOrRemove(row, "call_process", join(call.getCallProcessHistory(), ";"));
    writeOrRemove(row, "retry_history", join(call.getRetryHistory(), ";"));
    writeOrRemove(row, "cb_history", join(call.getCbHistory(), ","));
    writeOrRemove(row, "retry_number", call.getRetryNumber());
    writeOrRemove(row, "retry_date", call.getRetryDate() == null ? null : String.valueOf(call.getRetryDate()));
    // Callback alanlari createRetry tarafindan temizlenmis olabilir.
    writeOrRemove(row, "callback_date", call.getCallbackDate() == null ? null : String.valueOf(call.getCallbackDate()));
    writeOrRemove(row, "callback_number", call.getCallbackNumber());
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

  /**
   * Gercek arama dongusu: producer -&gt; dial -&gt; strateji -&gt; persist. Kayit kapanana kadar
   * surer ve numara bazinda GERCEKLESEN arama sayisini dondurur.
   */
  private Map<String, Integer> runDialLoop(JSONObject row) throws Exception {
    Map<String, Integer> actualDials = new LinkedHashMap<>();
    for (int iteration = 0; iteration < LOOP_FUSE; iteration++) {
      Call call = produce(row);
      if (call == null) {
        return actualDials;
      }

      String dialedNumber = call.getCalledNumber();
      actualDials.merge(dialedNumber, 1, Integer::sum);

      RetryStrategy strategy = strategyFactory.createStrategy(campaign.getRetryStrategy());
      strategy.createRetry(call);

      if (call.getCompleteDate() != null) {
        return actualDials;
      }
      persist(call, row);
    }
    throw new AssertionError("Dongu " + LOOP_FUSE + " adimda kapanmadi; sonsuz dongu suphesi");
  }

  // =====================================================================
  // Cekirdek invariant
  // =====================================================================

  @Test
  public void singleNumberReceivesExactlyLimitDials() throws Exception {
    campaign.setMaxAttemptPerNumber(3);
    numbers("number_1");

    Map<String, Integer> dials = runDialLoop(newRow("1", "number_1"));

    assertEquals("Tek numara TAM limit kadar arama almalidir",
        Integer.valueOf(3), dials.get("number_1"));
  }

  @Test
  public void limitOfOneStillProducesOneDial() throws Exception {
    campaign.setMaxAttemptPerNumber(1);
    numbers("number_1");

    Map<String, Integer> dials = runDialLoop(newRow("1", "number_1"));

    assertEquals("limit=1 iken numara BIR kez aranmalidir", Integer.valueOf(1), dials.get("number_1"));
  }

  @Test
  public void everyNumberReceivesExactlyLimitDials() throws Exception {
    campaign.setMaxAttemptPerNumber(4);
    numbers("number_1", "number_2", "number_3");

    Map<String, Integer> dials = runDialLoop(newRow("1", "number_1", "number_2", "number_3"));

    for (String number : Arrays.asList("number_1", "number_2", "number_3")) {
      assertEquals(number + " tam limit kadar arama almalidir", Integer.valueOf(4), dials.get(number));
    }
    assertEquals("Toplam arama = numara sayisi x limit", 12, totalDials(dials));
  }

  /**
   * Kabul kriteri 2'nin aritmetigi: 4 dolu numara x limit 10 = TAM 40 arama.
   */
  @Test
  public void acceptanceCriterion2ArithmeticHolds() throws Exception {
    campaign.setMaxAttemptPerNumber(10);
    numbers("number_1", "number_2", "number_3", "number_4");

    Map<String, Integer> dials =
        runDialLoop(newRow("1", "number_1", "number_2", "number_3", "number_4"));

    assertEquals("Kriter 2: 4 numara x 10 = 40 arama beklenir", 40, totalDials(dials));
    for (String number : Arrays.asList("number_1", "number_2", "number_3", "number_4")) {
      assertEquals(Integer.valueOf(10), dials.get(number));
    }
  }

  @Test
  public void noNumberEverExceedsItsLimit() throws Exception {
    campaign.setMaxAttemptPerNumber(2);
    numbers("number_1", "number_2");

    Map<String, Integer> dials = runDialLoop(newRow("1", "number_1", "number_2"));

    for (Map.Entry<String, Integer> entry : dials.entrySet()) {
      assertTrue(entry.getKey() + " limiti asmamalidir: " + entry.getValue(), entry.getValue() <= 2);
    }
  }

  // =====================================================================
  // Kabul kriteri 3 — dengesiz gecmis, kayit DEVAM eder
  // =====================================================================

  /**
   * Kriter 3: Tel-1 5, Tel-2 9, Tel-3 2 tamamlanmis arama ve strateji sirdaki aramayi Tel-2'ye
   * PLANLAMIS durumda (retry_number + retry_date dolu). Beklenen: Tel-2 onuncu aramasini ALIR,
   * ardindan atlanir ve kayit Tel-1 ile Tel-3 uzerinden DEVAM eder.
   */
  @Test
  public void unbalancedHistoryLetsNumberTakeItsFinalDialThenSkipsIt() throws Exception {
    campaign.setMaxAttemptPerNumber(10);
    numbers("number_1", "number_2", "number_3");

    JSONObject row = rowWithCompletedDialsAndPendingRetry("number_2");

    Map<String, Integer> dials = runDialLoop(row);

    assertEquals("Tel-2 onuncu aramasini almalidir (9 tamamlanmis + 1)",
        Integer.valueOf(1), dials.get("number_2"));
    assertTrue("Kayit Tel-1 ile devam etmelidir", dials.getOrDefault("number_1", 0) > 0);
    assertTrue("Kayit Tel-3 ile devam etmelidir", dials.getOrDefault("number_3", 0) > 0);
  }

  /**
   * Ayni senaryoda kalan kotalar TAM olarak kullanilir: Tel-1'in 5, Tel-3'un 8 aramasi kalmistir.
   */
  @Test
  public void unbalancedHistoryConsumesRemainingQuotaOfOtherNumbers() throws Exception {
    campaign.setMaxAttemptPerNumber(10);
    numbers("number_1", "number_2", "number_3");

    Map<String, Integer> dials = runDialLoop(rowWithCompletedDialsAndPendingRetry("number_2"));

    assertEquals("Tel-1 kalan 5 aramasini almalidir", Integer.valueOf(5), dials.get("number_1"));
    assertEquals("Tel-2 kalan 1 aramasini almalidir", Integer.valueOf(1), dials.get("number_2"));
    assertEquals("Tel-3 kalan 8 aramasini almalidir", Integer.valueOf(8), dials.get("number_3"));
  }

  /**
   * Planlanan numara GERCEKTEN limitini doldurmussa kayit kapatilmaz; kalan numaralara dusulur.
   */
  @Test
  public void exhaustedPlannedNumberFallsBackToOtherNumbersInsteadOfClosing() throws Exception {
    campaign.setMaxAttemptPerNumber(3);
    numbers("number_1", "number_2");

    // number_1 uc aramayi tamamlamis (F + 2R) ve strateji yine number_1'e planlamis (bozuk durum).
    JSONObject row = newRow("1", "number_1", "number_2");
    row.put("call_process", "F;R;R;R");
    row.put("retry_history", "number_1;number_1;number_1");
    row.put("retry_number", "number_1");
    row.put("retry_date", String.valueOf(Utils.convertDateToDouble(new java.util.Date())));

    Map<String, Integer> dials = runDialLoop(row);

    assertFalse("Limitli plan kaydi kapatmamali; number_2 aranmalidir", dials.isEmpty());
    assertEquals("number_2 tam kotasini almalidir", Integer.valueOf(3), dials.get("number_2"));
    assertEquals("number_1 limitini asmamalidir", null, dials.get("number_1"));
  }

  // =====================================================================
  // Callback (kriter 6)
  // =====================================================================

  /**
   * Kriter 6: numara limit-1 aramada iken musterinin talebiyle olusan randevu ARANIR. Bekleyen
   * callback girdisi ({@code cb_history} + {@code CB} adimi) sayimda tamamlanmis arama gibi
   * degerlendirilmemelidir.
   */
  @Test
  public void pendingCallbackOnItsLastAllowedDialIsStillDialed() throws Exception {
    campaign.setMaxAttemptPerNumber(3);
    numbers("number_1", "number_2");

    // number_1 iki aramayi tamamlamis (F + R), ardindan agent callback olusturmus:
    // cb_history + "CB" adimi bekleyen plandir.
    JSONObject row = newRow("1", "number_1", "number_2");
    row.put("call_process", "F;R;CB");
    row.put("retry_history", "number_1");
    row.put("cb_history", "number_1");
    row.put("callback_number", "number_1");
    row.put("callback_date", String.valueOf(Utils.convertDateToDouble(new java.util.Date())));

    Call call = produce(row);

    assertTrue("Bekleyen callback aranabilir olmalidir", call != null);
    assertEquals("number_1", call.getCalledNumber());
  }

  // =====================================================================
  // K2 — strateji 1 muafiyeti iki katmanda da gecerli
  // =====================================================================

  /**
   * Strateji 1'de alan DB'de dolu kalabilir (UI freeze degeri temizlemiyor). Producer da limiti
   * uygulamamalidir; aksi halde K2'nin "alan dolu gelse bile etkisiz" garantisi yarim kalir.
   */
  @Test
  public void strategyOneExemptionIsHonouredByProducerToo() throws Exception {
    campaign.setRetryStrategy(Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER);
    campaign.setMaxAttemptPerNumber(2);
    campaign.setRetryOveralAttempt(4);
    campaign.setMacroMaxAttempt(4);
    numbers("number_1");

    JSONObject row = newRow("1", "number_1");
    row.put("call_process", "F;R;R");
    row.put("retry_history", "number_1;number_1");

    Call call = produce(row);

    assertTrue("Strateji 1'de producer numara basina limiti UYGULAMAMALIDIR", call != null);
    assertEquals("number_1", call.getCalledNumber());
  }

  /**
   * Strateji 0 muafiyeti (karar 29.09.2026) producer'da da gecerlidir. Producer limiti uygulasaydi
   * izi olan tek numarali kayit hic aranmadan kapanirdi.
   */
  @Test
  public void strategyZeroExemptionIsHonouredByProducerToo() throws Exception {
    campaign.setRetryStrategy(Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER);
    campaign.setMaxAttemptPerNumber(2);
    campaign.setRetryOveralAttempt(4);
    campaign.setMacroMaxAttempt(4);
    numbers("number_1");

    JSONObject row = newRow("1", "number_1");
    row.put("call_process", "F;R;R");
    row.put("retry_history", "number_1;number_1");

    Call call = produce(row);

    assertTrue("Strateji 0'da producer numara basina limiti UYGULAMAMALIDIR", call != null);
    assertEquals("number_1", call.getCalledNumber());
  }

  /**
   * Test ortaminda gorulen durum: iki numarali kayitta bekleyen retry plani limitine ulasmis
   * numarayi gosteriyor. Strateji 0'da producer plani diger numaraya YONLENDIRMEZ; strateji tek
   * numarada kalir.
   */
  @Test
  public void strategyZeroPendingRetryIsNotRedirectedToAnotherNumber() throws Exception {
    campaign.setRetryStrategy(Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER);
    campaign.setMaxAttemptPerNumber(2);
    campaign.setRetryOveralAttempt(10);
    campaign.setMacroMaxAttempt(10);
    numbers("number_1", "number_2");

    JSONObject row = newRow("1", "number_1", "number_2");
    row.put("call_process", "F;R;R");
    row.put("retry_history", "number_1;number_1");
    row.put("retry_number", "number_1");
    row.put("retry_date", String.valueOf(Utils.convertDateToDouble(new java.util.Date())));

    Call call = produce(row);

    assertTrue(call != null);
    assertEquals("Strateji 0'da plan diger numaraya yonlendirilmemeli", "number_1", call.getCalledNumber());
  }

  // =====================================================================
  // Feature off
  // =====================================================================

  /**
   * Ozellik kapaliyken tavan YALNIZ genel limittir. Beklenen toplam
   * {@code retryOveralAttempt + 1}'dir: {@code anyRetryLeft()} retry gecmisi uzunlugunu sayar ve
   * fresh arama gecmise yazilmaz ("+1 is for first call" semantigi,
   * {@code RoundRobinPerCycle}/{@code CallAttemptPolicy} icinde 4 yerde ayni varsayim). Bu mevcut
   * ve DOGRU davranistir; bu is kapsaminda degistirilmedi.
   */
  @Test
  public void featureOffLeavesLoopBoundedOnlyByOverallLimit() throws Exception {
    campaign.setMaxAttemptPerNumber(null);
    campaign.setRetryOveralAttempt(5);
    campaign.setMacroMaxAttempt(5);
    numbers("number_1", "number_2");

    Map<String, Integer> dials = runDialLoop(newRow("1", "number_1", "number_2"));

    assertEquals("Ozellik kapaliyken tavan genel limit + fresh aramadir", 6, totalDials(dials));
  }

  // =====================================================================
  // Yardimcilar
  // =====================================================================

  /**
   * Tel-1 5, Tel-2 9, Tel-3 2 tamamlanmis arama + verilen numaraya BEKLEYEN retry plani uretir.
   * Iz uzunlugu ile gecmis listeleri kilitli adim invariantina uyar: 1 F + 16 R adimi, 16 retry
   * girdisi (15 tamamlanmis + 1 bekleyen).
   */
  private JSONObject rowWithCompletedDialsAndPendingRetry(String pendingNumber) {
    List<String> retryHistory = new ArrayList<>();
    // F adimi Tel-1'e atfedilir (colSort'a gore ilk dolu kolon), kalan 4 Tel-1 retry olarak.
    for (int i = 0; i < 4; i++) {
      retryHistory.add("number_1");
    }
    for (int i = 0; i < 9; i++) {
      retryHistory.add("number_2");
    }
    for (int i = 0; i < 2; i++) {
      retryHistory.add("number_3");
    }
    retryHistory.add(pendingNumber);

    StringBuilder trail = new StringBuilder("F");
    for (int i = 0; i < retryHistory.size(); i++) {
      trail.append(";R");
    }

    JSONObject row = newRow("1", "number_1", "number_2", "number_3");
    row.put("call_process", trail.toString());
    row.put("retry_history", String.join(";", retryHistory));
    row.put("retry_number", pendingNumber);
    row.put("retry_date", String.valueOf(Utils.convertDateToDouble(new java.util.Date())));
    return row;
  }

  private int totalDials(Map<String, Integer> dials) {
    int total = 0;
    for (Integer count : dials.values()) {
      total += count;
    }
    return total;
  }

}
