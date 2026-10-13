package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.dialer.CallProducer;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.CampaignFilterStatsCalculator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Filter;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.util.Constants;

/**
 * FEAT-325559: fresh cagride numara secimi.
 *
 * <p><b>Faz 1'de karakterizasyon olarak yazildi, Faz 4'te yeni contract'a tasindi.</b> Secim artik
 * {@code selectFreshNumber} / {@code resolveCalledNumber} tarafindan yapiliyor. KORUNAN
 * invariantlar: colSort'a gore ilk dolu kolon, bos/eksik kolonlarin atlanmasi, disabled kolonlarin
 * yok sayilmasi, {@code calledNumber} oncelik sirasi (callback &gt; retry &gt; kolonlar).</p>
 *
 * <p><b>Bilincli olarak DEGISEN iki davranis:</b></p>
 *
 * <ol>
 * <li><b>Arama gecmisine korluk kalkti.</b> Ozellik aktifken limitini doldurmus kolonlar atlanir
 * (Kabul kriteri 4 ve 8). Ozellik KAPALIYKEN korluk aynen surer ve o durum burada da test edilir.</li>
 * <li><b>D1 sizintisi kapatildi.</b> Aranabilir numarasi olmayan kayit artik
 * {@code IllegalStateException} ile sessizce dusurulmuyor; {@code resolveCalledNumber} null donuyor
 * ve kayit {@code setClosedWithoutDialing} ile kapatiliyor. Eski davranista kayit
 * {@code is_fetched} de isaretlenmedigi icin HER producer tick'inde yeniden SELECT edilip
 * sonsuza kadar loglaniyordu.</li>
 * </ol>
 */
@RunWith(MockitoJUnitRunner.class)
public class CallProducerFreshNumberSelectionCharacterizationTest {

  private static final String UNIQUE_KEY_COLUMN = "unique_key";

  private CallProducer callProducer;
  private Campaign campaign;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private DialerFacade dialerFacadeMock;

  @Mock
  private CampaignFilterStatsCalculator campaignFilterStatsCalculatorMock;

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
    campaign.setRetryStrategy(Constants.RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER);

    CampaignContext campaignContext = new CampaignContext(campaign, dialerFacadeMock, campaignFilterStatsCalculatorMock);
    callProducer = new CallProducer(campaignContext, campaignFilterStatsCalculatorMock);
  }

  /**
   * colSort degeri kasten ekleme sirasindan FARKLI verilir; secimin sirayla
   * degil colSort ile yapildigi boylece kanitlanir.
   */
  private void addPhoneColumn(String columnName, int colSort) {
    PhoneNumberColumn column = new PhoneNumberColumn();
    column.setColNumber(columnName);
    column.setColSort(colSort);
    column.setEnabled(true);
    campaign.getPhoneNumberColumns().add(column);
  }

  private JSONObject row(String uniqueKey) {
    JSONObject jsonRow = new JSONObject();
    jsonRow.put(UNIQUE_KEY_COLUMN, uniqueKey);
    return jsonRow;
  }

  @SuppressWarnings("unchecked")
  private List<Call> createCallsFromRawData(JSONObject... rows) throws Exception {
    JSONArray rawData = new JSONArray();
    for (JSONObject jsonRow : rows) {
      rawData.put(jsonRow);
    }
    Method method = CallProducer.class.getDeclaredMethod("createCallsFromRawData",
        JSONArray.class, Constants.CALL_TYPES.class, Optional.class);
    method.setAccessible(true);
    return (List<Call>) method.invoke(callProducer, rawData, Constants.CALL_TYPES.FRESH, Optional.<Filter>empty());
  }

  /**
   * Numara secimini {@code resolveCalledNumber} uzerinden calistirir. Kayitta aranabilir numara
   * yoksa {@code null} doner (eskiden {@code IllegalStateException} atiliyordu).
   */
  private String resolveCalledNumber(JSONObject jsonRow) throws Exception {
    Call call = new Call();
    call.setCampaign(campaign);
    call.setRawData(jsonRow);
    if (jsonRow.has("callback_number")) {
      call.setCallbackNumber(jsonRow.get("callback_number").toString());
    }
    if (jsonRow.has("retry_number")) {
      call.setRetryNumber(jsonRow.get("retry_number").toString());
    }
    Method method = CallProducer.class.getDeclaredMethod("resolveCalledNumber", Call.class);
    method.setAccessible(true);
    try {
      return (String) method.invoke(callProducer, call);
    } catch (InvocationTargetException e) {
      throw (Exception) e.getCause();
    }
  }

  // ---------------------------------------------------------------------
  // Numara secimi
  // ---------------------------------------------------------------------

  @Test
  public void freshNumberSelectionPicksFirstFilledColumnByColSort() throws Exception {
    addPhoneColumn("phone_number_2", 2);
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("1001");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("phone_number_2", "number_2");

    assertEquals("number_1", resolveCalledNumber(jsonRow));
  }

  @Test
  public void freshNumberSelectionSkipsMissingAndBlankColumns() throws Exception {
    addPhoneColumn("phone_number_1", 1);
    addPhoneColumn("phone_number_2", 2);
    addPhoneColumn("phone_number_3", 3);

    JSONObject jsonRow = row("1002");
    jsonRow.put("phone_number_2", "   ");
    jsonRow.put("phone_number_3", "number_3");

    assertEquals("number_3", resolveCalledNumber(jsonRow));
  }

  @Test
  public void freshNumberSelectionIgnoresDisabledColumns() throws Exception {
    PhoneNumberColumn disabled = new PhoneNumberColumn();
    disabled.setColNumber("phone_number_0");
    disabled.setColSort(0);
    disabled.setEnabled(false);
    campaign.getPhoneNumberColumns().add(disabled);
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("1003");
    jsonRow.put("phone_number_0", "disabled_number");
    jsonRow.put("phone_number_1", "number_1");

    assertEquals("number_1", resolveCalledNumber(jsonRow));
  }

  /**
   * Ozellik KAPALIYKEN (alan bos) secim arama gecmisine korlugunu SURDURUR; feature-off davranisi
   * birebir eskisiyle ayni kalir.
   */
  @Test
  public void freshNumberSelectionStaysBlindToRetryHistoryWhenFeatureIsOff() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setMaxAttemptPerNumber(null);
    addPhoneColumn("phone_number_1", 1);
    addPhoneColumn("phone_number_2", 2);

    JSONObject jsonRow = row("1004");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("phone_number_2", "number_2");
    jsonRow.put("retry_history", "number_1;number_1;number_1;number_1;number_1");

    assertEquals("Ozellik kapaliyken gecmis secimi degistirmez", "number_1", resolveCalledNumber(jsonRow));
  }

  /**
   * Kabul kriteri 4: ozellik aktifken limitini doldurmus kolon ATLANIR ve ilk uygun numara secilir.
   */
  @Test
  public void freshNumberSelectionSkipsColumnThatReachedItsLimit() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setMaxAttemptPerNumber(5);
    addPhoneColumn("phone_number_1", 1);
    addPhoneColumn("phone_number_2", 2);

    JSONObject jsonRow = row("1004");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("phone_number_2", "number_2");
    // fresh number_1 + 4 retry number_1 = 5 arama -> Tel-1 limitte
    jsonRow.put("call_process", "F;R;R;R;R");
    jsonRow.put("retry_history", "number_1;number_1;number_1;number_1");

    assertEquals("number_2", resolveCalledNumber(jsonRow));
  }

  /**
   * Kabul kriteri 8: tum eski numaralar limitli, sonradan eklenen numara sifir gecmisli. Yalnizca
   * yeni numara secilir.
   */
  @Test
  public void freshNumberSelectionPicksNewlyAddedNumberWhenOldOnesAreExhausted() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setMaxAttemptPerNumber(3);
    addPhoneColumn("phone_number_1", 1);
    addPhoneColumn("phone_number_2", 2);
    addPhoneColumn("phone_number_3", 3);
    addPhoneColumn("phone_number_4", 4);

    JSONObject jsonRow = row("1005");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("phone_number_2", "number_2");
    jsonRow.put("phone_number_3", "number_3");
    jsonRow.put("phone_number_4", "number_4");
    // fresh number_1 + 2 number_1, 3 number_2, 3 number_3 -> ilk uc numara limitte, number_4 sifir
    jsonRow.put("call_process", "F;R;R;R;R;R;R;R;R");
    jsonRow.put("retry_history",
        "number_1;number_1;number_2;number_2;number_2;number_3;number_3;number_3");

    assertEquals("Yalnizca yeni eklenen numara aranabilir", "number_4", resolveCalledNumber(jsonRow));
  }

  /**
   * Planlanan retry numarasi limitini doldurmus VE kayitta baska numara yoksa aranabilir numara
   * bulunamaz.
   *
   * <p><b>Fixture yeniden turetildi:</b> onceki hali {@code retry_date} tasimadigi icin GERCEK bir
   * bekleyen-retry satirini temsil etmiyordu; "F;R;R" + 2 retry girdisi arama oncesi okumada
   * 2 tamamlanmis + 1 bekleyen demektir, 3 degil. Limitin gercekten dolu oldugu durum 3 tamamlanmis
   * + 1 bekleyen plandir: "F;R;R;R" + 3 retry girdisi + {@code retry_date}.</p>
   */
  @Test
  public void retryNumberAtItsLimitIsNotDialableWhenNoOtherNumberExists() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setColResultRetryDate("retry_date");
    campaign.setMaxAttemptPerNumber(3);
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("1006");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("retry_number", "number_1");
    jsonRow.put("call_process", "F;R;R;R");
    jsonRow.put("retry_history", "number_1;number_1;number_1");
    jsonRow.put("retry_date", "45000.5");

    assertNull(resolveCalledNumber(jsonRow));
  }

  /**
   * Ayni fixture'da limitin HENUZ dolmadigi hal: 2 tamamlanmis + 1 bekleyen plan -&gt; ucuncu arama
   * yapilabilir. Bekleyen plani tamamlanmis saymak numaranin son hakkini yakiyordu.
   */
  @Test
  public void retryNumberWithPendingPlanStillHasItsFinalDial() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setColResultRetryDate("retry_date");
    campaign.setMaxAttemptPerNumber(3);
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("1006b");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("retry_number", "number_1");
    jsonRow.put("call_process", "F;R;R");
    jsonRow.put("retry_history", "number_1;number_1");
    jsonRow.put("retry_date", "45000.5");

    assertEquals("2 tamamlanmis + 1 bekleyen: ucuncu arama yapilabilir",
        "number_1", resolveCalledNumber(jsonRow));
  }

  /**
   * Kriter 3: planlanan callback numarasi limitini doldurmussa kayit KAPATILMAZ; kalan numaraya
   * dusulur.
   *
   * <p><b>Fixture yeniden turetildi:</b> onceki hali gercekci degildi — bekleyen callback'in
   * {@code call_process} icinde {@code CB} adimi, {@code cb_history} icinde girdisi ve
   * {@code callback_date} olmali. Ayrica iddia F2 ile degisti: eskiden "baska numaraya
   * DUSULMEZ, kayit kapanir" bekleniyordu; artik kalan numaralarin kotasi kontrol edilir.</p>
   */
  @Test
  public void callbackNumberAtItsLimitFallsBackToRemainingNumber() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setColResultCbHistory("cb_history");
    campaign.setColResultCallbackDate("callback_date");
    campaign.setMaxAttemptPerNumber(3);
    addPhoneColumn("phone_number_1", 1);
    addPhoneColumn("phone_number_2", 2);

    JSONObject jsonRow = row("1007");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("phone_number_2", "number_2");
    jsonRow.put("callback_number", "number_1");
    jsonRow.put("callback_date", "45000.5");
    // number_1 uc aramayi tamamlamis (F + 2R), ardindan bekleyen callback: CB adimi + cb_history.
    jsonRow.put("call_process", "F;R;R;CB");
    jsonRow.put("retry_history", "number_1;number_1");
    jsonRow.put("cb_history", "number_1");

    assertEquals("Limitli callback kaydi kapatmaz; kalan numaraya dusulur",
        "number_2", resolveCalledNumber(jsonRow));
  }

  /**
   * Kalan numara da yoksa aranabilir numara bulunamaz ve kayit kapanir (kriter 7).
   */
  @Test
  public void callbackNumberAtItsLimitWithNoAlternativeIsNotDialable() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setColResultCbHistory("cb_history");
    campaign.setColResultCallbackDate("callback_date");
    campaign.setMaxAttemptPerNumber(3);
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("1007b");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("callback_number", "number_1");
    jsonRow.put("callback_date", "45000.5");
    jsonRow.put("call_process", "F;R;R;CB");
    jsonRow.put("retry_history", "number_1;number_1");
    jsonRow.put("cb_history", "number_1");

    assertNull(resolveCalledNumber(jsonRow));
  }

  /**
   * Callback numarasi limitin altindaysa normal akar (Kriter 6).
   */
  @Test
  public void callbackNumberBelowItsLimitIsDialable() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setMaxAttemptPerNumber(10);
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("1008");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("callback_number", "number_1");
    jsonRow.put("call_process", "F;R;R");
    jsonRow.put("retry_history", "number_1;number_1");

    assertEquals("number_1", resolveCalledNumber(jsonRow));
  }

  /**
   * D1 fix: dolu numara yokken artik istisna ATILMAZ, {@code null} donulur. Cagiran taraf kaydi
   * kapatarak havuzdan cikarir. Eski davranista istisna yutuluyor ve kayit her tick'te yeniden
   * SELECT ediliyordu.
   */
  @Test
  public void freshNumberSelectionReturnsNullInsteadOfThrowingWhenNoColumnHasValue() throws Exception {
    addPhoneColumn("phone_number_1", 1);
    addPhoneColumn("phone_number_2", 2);

    JSONObject jsonRow = row("1009");

    assertNull(resolveCalledNumber(jsonRow));
  }

  // ---------------------------------------------------------------------
  // calledNumber onceligi
  // ---------------------------------------------------------------------

  @Test
  public void calledNumberPrefersCallbackNumberOverRetryNumberAndColumns() throws Exception {
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("2001");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("retry_number", "retry_target");
    jsonRow.put("callback_number", "callback_target");

    List<Call> calls = createCallsFromRawData(jsonRow);

    assertEquals(1, calls.size());
    assertEquals("callback_target", calls.get(0).getCalledNumber());
  }

  @Test
  public void calledNumberPrefersRetryNumberOverColumnsWhenNoCallbackNumber() throws Exception {
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("2002");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("retry_number", "retry_target");

    List<Call> calls = createCallsFromRawData(jsonRow);

    assertEquals(1, calls.size());
    assertEquals("retry_target", calls.get(0).getCalledNumber());
  }

  @Test
  public void calledNumberFallsBackToFirstFilledColumn() throws Exception {
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("2003");
    jsonRow.put("phone_number_1", "number_1");

    List<Call> calls = createCallsFromRawData(jsonRow);

    assertEquals(1, calls.size());
    assertEquals("number_1", calls.get(0).getCalledNumber());
  }

  // ---------------------------------------------------------------------
  // D1 sizintisinin kapatilmasi
  // ---------------------------------------------------------------------

  /**
   * Tum telefon kolonlari bos olan kayit icin Call uretilmez (bu kisim degismedi) AMA artik
   * kayit DB'de kapatilir; sessizce dusurulup her tick'te yeniden SELECT edilmez.
   */
  @Test
  public void recordWithAllPhoneColumnsEmptyProducesNoCallAndIsClosedInDb() throws Exception {
    addPhoneColumn("phone_number_1", 1);
    addPhoneColumn("phone_number_2", 2);

    List<Call> calls = createCallsFromRawData(row("3001"));

    assertTrue(calls.isEmpty());
    verifyClosedWithoutDialing(Constants.CLOSE_REASON_NO_CALLABLE_NUMBER, "3001");
  }

  /**
   * Onceki davranista callback numarasi DOLU olsa bile kayit dusuyordu
   * ({@code ObjectUtils.firstNonNull} varargs oldugu icin kolon secimi her zaman
   * degerlendiriliyordu). Artik argumanlar tembel degerlendirildigi icin callback numarasi olan
   * kayit normal sekilde Call'a donusur.
   */
  @Test
  public void recordWithCallbackNumberAndEmptyColumnsNowProducesCall() throws Exception {
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("3002");
    jsonRow.put("callback_number", "callback_target");

    List<Call> calls = createCallsFromRawData(jsonRow);

    assertEquals("Callback numarasi varsa kolonlarin bos olmasi kaydi dusurmez", 1, calls.size());
    assertEquals("callback_target", calls.get(0).getCalledNumber());
  }

  /**
   * Limitli callback kaydi kendi nedeniyle kapatilir (Kriter 7) — <b>kalan numara YOKSA</b>.
   *
   * <p><b>Fixture yeniden turetildi:</b> bekleyen callback'in gercek runtime izi
   * {@code call_process} icinde {@code CB} adimi, {@code cb_history} icinde girdi ve
   * {@code callback_date}'tir ({@link Call#addCallbackHistoryEntry} kilitli yazar). Onceki fixture
   * bunlari tasimadigi icin "2 tamamlanmis" gorunumu aslinda "1 tamamlanmis + 1 bekleyen" idi;
   * test dogru sonucu yanlis nedenle uretiyordu.</p>
   */
  @Test
  public void callbackRecordAtLimitIsClosedWithCallbackReason() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setColResultCbHistory("cb_history");
    campaign.setColResultCallbackDate("callback_date");
    campaign.setMaxAttemptPerNumber(2);
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("3010");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("callback_number", "number_1");
    jsonRow.put("callback_date", "45000.5");
    // number_1: F + R = 2 tamamlanmis arama, ardindan bekleyen callback (CB + cb_history).
    jsonRow.put("call_process", "F;R;CB");
    jsonRow.put("retry_history", "number_1");
    jsonRow.put("cb_history", "number_1");

    List<Call> calls = createCallsFromRawData(jsonRow);

    assertTrue(calls.isEmpty());
    verifyClosedWithoutDialing(Constants.CLOSE_REASON_MAX_ATTEMPT_ON_CALLBACK, "3010");
  }

  /**
   * Tum numaralari limitli kayit "limit doldu" nedeniyle kapatilir (Kriter 2'nin producer tarafi).
   *
   * <p>Satir bilincli olarak BEKLEYEN PLANSIZ'dir ({@code retry_date}/{@code retry_number} yok):
   * kampanya reset'i {@code is_fetched}'i temizler ama {@code call_process}/{@code retry_history}
   * kolonlarina dokunmaz, F5 ile kapanista {@code retry_date}/{@code retry_number} NULL'lanir.
   * Boyle bir satir FRESH olarak yeniden gelir ve izin TAMAMI tamamlanmis aramadir.</p>
   */
  @Test
  public void recordWithAllNumbersExhaustedIsClosedWithNumberLimitReason() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setMaxAttemptPerNumber(2);
    addPhoneColumn("phone_number_1", 1);
    addPhoneColumn("phone_number_2", 2);

    JSONObject jsonRow = row("3011");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("phone_number_2", "number_2");
    jsonRow.put("call_process", "F;R;R;R");
    jsonRow.put("retry_history", "number_1;number_2;number_2");

    List<Call> calls = createCallsFromRawData(jsonRow);

    assertTrue(calls.isEmpty());
    verifyClosedWithoutDialing(Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED, "3011");
  }

  /**
   * Ozellik kapaliyken hicbir kayit limit nedeniyle kapatilmaz.
   */
  @Test
  public void featureOffNeverClosesRecordsForPerNumberLimit() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setMaxAttemptPerNumber(null);
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("3012");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("call_process", "F;R;R;R;R;R");
    jsonRow.put("retry_history", "number_1;number_1;number_1;number_1;number_1");

    List<Call> calls = createCallsFromRawData(jsonRow);

    assertEquals(1, calls.size());
    assertEquals("number_1", calls.get(0).getCalledNumber());
  }

  @Test
  public void validRecordsSurviveWhileUncallableOneIsClosed() throws Exception {
    addPhoneColumn("phone_number_1", 1);

    JSONObject callable = row("3003");
    callable.put("phone_number_1", "number_1");

    List<Call> calls = createCallsFromRawData(callable, row("3004"));

    assertEquals("Aranabilir kayit korunur", 1, calls.size());
    assertEquals("3003", calls.get(0).getUniqueKey());
    verifyClosedWithoutDialing(Constants.CLOSE_REASON_NO_CALLABLE_NUMBER, "3004");
  }

  /**
   * Aranamayan kayitlar neden bazinda TEK batch cagrisiyla kapatilir; kayit basina ayri DB
   * cagrisi yapilmaz.
   */
  @Test
  public void uncallableRecordsAreClosedInOneBatchCallPerReason() throws Exception {
    addPhoneColumn("phone_number_1", 1);

    createCallsFromRawData(row("3020"), row("3021"), row("3022"));

    ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(dialerFacadeMock.getCampaignDbOperations(), times(1))
        .setClosedWithoutDialing(eq(campaign), keysCaptor.capture(),
            eq(Constants.CLOSE_REASON_NO_CALLABLE_NUMBER));

    assertEquals(3, keysCaptor.getValue().size());
    assertTrue(keysCaptor.getValue().containsAll(Arrays.asList("3020", "3021", "3022")));
  }

  @SuppressWarnings("unchecked")
  // ---------------------------------------------------------------------
  // Faz 7 / P2.1b — kapanis nedeni gercek nedeni anlatmali (producer tarafi)
  // ---------------------------------------------------------------------

  /**
   * P2.1b: yalnizca bosluktan olusan kolon tasiyan kayit "limit doldu" DEGIL "gecerli numara yok"
   * nedeniyle kapanmalidir.
   *
   * <p>Onceki hal {@code getNumbersInRawData()} kullaniyordu; o metot {@code isNotEmpty} ile
   * bosluklu kolonu da "dolu numara" saydigi icin etiket limit oluyordu. Producer o kolonu zaten
   * atliyor ({@code trimPhoneNumber} null doner), yani etiket ile gerceklik celisiyordu.</p>
   */
  @Test
  public void whitespaceOnlyColumnRecordIsClosedWithNoCallableNumberReason() throws Exception {
    campaign.setMaxAttemptPerNumber(3);
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("3030");
    jsonRow.put("phone_number_1", "    ");

    List<Call> calls = createCallsFromRawData(jsonRow);

    assertTrue(calls.isEmpty());
    verifyClosedWithoutDialing(Constants.CLOSE_REASON_NO_CALLABLE_NUMBER, "3030");
  }

  /**
   * Karsit kontrol: gercekten limitini doldurmus numara limit etiketini alir (davranis degismedi).
   */
  @Test
  public void exhaustedNumberRecordStillGetsNumberLimitReason() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setMaxAttemptPerNumber(3);
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("3031");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("call_process", "F;R;R");
    jsonRow.put("retry_history", "number_1;number_1");

    List<Call> calls = createCallsFromRawData(jsonRow);

    assertTrue(calls.isEmpty());
    verifyClosedWithoutDialing(Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED, "3031");
  }

  // ---------------------------------------------------------------------
  // Faz 7 / P2.3 — producer tarafinda tek normalizasyon kurali
  // ---------------------------------------------------------------------

  /**
   * P2.3a: kolon degeri bosluklu, gecmis kayitlari trimli. Sayac numarayi BULMALI ve numara
   * aranamaz sayilmalidir. Duzeltme oncesi harita anahtari {@code "  number_1  "}, sorgu
   * {@code "number_1"} idi; sayac 0 kabul edilip limit SESSIZCE hic uygulanmiyordu.
   */
  @Test
  public void paddedColumnValueIsCountedAgainstItsLimit() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setMaxAttemptPerNumber(3);
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("3032");
    jsonRow.put("phone_number_1", "  number_1  ");
    jsonRow.put("call_process", "F;R;R");
    jsonRow.put("retry_history", "number_1;number_1");

    assertNull("Bosluklu yazim limiti atlatmamalidir", resolveCalledNumber(jsonRow));
  }

  /**
   * Ters yon: gecmis kaydi bosluklu, kolon degeri trimli.
   */
  @Test
  public void paddedHistoryEntryIsCountedAgainstItsLimit() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setMaxAttemptPerNumber(3);
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("3033");
    jsonRow.put("phone_number_1", "number_1");
    jsonRow.put("call_process", "F;R;R");
    jsonRow.put("retry_history", " number_1 ; number_1 ");

    assertNull(resolveCalledNumber(jsonRow));
  }

  /**
   * Limitin altindaki bosluklu numara HALA aranabilir; normalizasyon yanlis yone hata yapmaz.
   * Donen deger HAM kolon degeridir (yazim semantigi degismez).
   */
  @Test
  public void paddedNumberBelowItsLimitIsStillDialable() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCallProcess("call_process");
    campaign.setMaxAttemptPerNumber(5);
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("3034");
    jsonRow.put("phone_number_1", "  number_1  ");
    jsonRow.put("call_process", "F;R;R");
    jsonRow.put("retry_history", "number_1;number_1");

    assertEquals("  number_1  ", resolveCalledNumber(jsonRow));
  }

  private void verifyClosedWithoutDialing(String expectedReason, String... expectedKeys) throws Exception {
    ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(dialerFacadeMock.getCampaignDbOperations())
        .setClosedWithoutDialing(eq(campaign), keysCaptor.capture(), eq(expectedReason));
    assertTrue("Beklenen key'ler kapatilmali: " + Arrays.toString(expectedKeys),
        keysCaptor.getValue().containsAll(Arrays.asList(expectedKeys)));
  }

  @Test
  public void duplicateUniqueKeyInSameFetchProducesSingleCall() throws Exception {
    addPhoneColumn("phone_number_1", 1);

    JSONObject first = row("3005");
    first.put("phone_number_1", "number_1");
    JSONObject duplicate = row("3005");
    duplicate.put("phone_number_1", "number_1");

    List<Call> calls = createCallsFromRawData(first, duplicate);

    assertEquals(1, calls.size());
  }

  @Test
  public void createdCallCarriesFreshCallTypeAndEmptyHistories() throws Exception {
    campaign.setColResultRetryHistory("retry_history");
    addPhoneColumn("phone_number_1", 1);

    JSONObject jsonRow = row("3006");
    jsonRow.put("phone_number_1", "number_1");

    List<Call> calls = createCallsFromRawData(jsonRow);

    assertEquals(1, calls.size());
    Call call = calls.get(0);
    assertEquals(Constants.CALL_TYPES.FRESH, call.getCallType());
    assertTrue(call.getRetryHistory().isEmpty());
    assertEquals(new ArrayList<String>(), call.getCallProcessHistory());
  }

}
