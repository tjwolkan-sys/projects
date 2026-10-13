package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
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

/**
 * FEAT-325559 Faz 1 karakterizasyonu: gecmis kolonlarinin KILITLI ADIM
 * (lockstep) yazimi.
 *
 * <p>Numara basina sayac icin ayri bir kolon eklenmeyecek (K4); sayim
 * {@code call_process} izi bir KURSOR gibi kullanilarak
 * {@code retry_history} ve {@code cb_history} uzerinden yeniden kurulacak.
 * Bu tasarimin gecerliligi tamamen su uc invariant'a dayanir ve asagidaki
 * testler onlari kilitler:</p>
 *
 * <ul>
 * <li>Her retry planlamasi {@code retry_history}'ye BIR numara ve
 * {@code call_process}'e BIR {@code R} adimi ekler.</li>
 * <li>Her callback olusturmasi {@code cb_history}'ye BIR numara ve
 * {@code call_process}'e BIR {@code CB} adimi ekler.</li>
 * <li>{@code call_process} her zaman {@code F} ile baslar ve fresh (ilk)
 * arama {@code retry_history}'de YER ALMAZ.</li>
 * </ul>
 */
public class CallHistoryColumnsLockstepCharacterizationTest {

  private static final String FRESH = "F";
  private static final String RETRY = "R";
  private static final String CALLBACK = "CB";

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
    when(campaign.getEnabledPhoneNumberColumns()).thenReturn(numberColumns);
    when(campaign.getRetryOveralAttempt()).thenReturn(10);
    when(campaign.getMacroMaxAttempt()).thenReturn(10);
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

  private void setRawColumn(String columnName, String value) {
    when(rawData.has(columnName)).thenReturn(true);
    when(rawData.get(columnName)).thenReturn(value);
    when(rawData.getString(columnName)).thenReturn(value);
  }

  @Test
  public void freshDialIsNotRecordedInRetryHistory() {
    assertTrue("Fresh arama retry gecmisine yazilmaz", call.getRetryHistory().isEmpty());
    assertTrue("Fresh arama call process izini de kendiliginden acmaz", call.getCallProcessHistory().isEmpty());
  }

  @Test
  public void scheduledRetryAddsOneNumberAndOneRetryStepTogether() {
    new RoundRobinNextNumber().createRetry(call);

    assertEquals(Arrays.asList("number_2"), call.getRetryHistory());
    assertEquals("Ilk adim F, ardindan tek R", Arrays.asList(FRESH, RETRY), call.getCallProcessHistory());
  }

  @Test
  public void consecutiveRetriesKeepRetryHistoryAndProcessTrailInStep() {
    RoundRobinNextNumber strategy = new RoundRobinNextNumber();
    strategy.createRetry(call);
    strategy.createRetry(call);

    assertEquals(Arrays.asList("number_2", "number_1"), call.getRetryHistory());
    assertEquals(Arrays.asList(FRESH, RETRY, RETRY), call.getCallProcessHistory());
    assertEquals("R adimi sayisi retry gecmisi uzunlugu ile ayni olmalidir",
        call.getRetryHistory().size(), countSteps(RETRY));
  }

  @Test
  public void callbackEntryAddsOneNumberAndOneCallbackStepTogether() {
    call.addCallbackHistoryEntry("number_2");

    assertEquals(Arrays.asList("number_2"), call.getCbHistory());
    assertEquals(Arrays.asList(FRESH, CALLBACK), call.getCallProcessHistory());
  }

  @Test
  public void blankCallbackNumberAddsNeitherNumberNorStep() {
    call.addCallbackHistoryEntry("   ");

    assertTrue(call.getCbHistory().isEmpty());
    assertTrue(call.getCallProcessHistory().isEmpty());
  }

  /**
   * Karisik dizide kursor esleme belirsizlik BIRAKMAZ: {@code F;R;CB;R} izi
   * iki retry ve bir callback numarasina birebir karsilik gelir.
   */
  @Test
  public void mixedTrailMapsUnambiguouslyOntoRetryAndCallbackHistories() {
    RoundRobinNextNumber strategy = new RoundRobinNextNumber();
    strategy.createRetry(call);
    call.addCallbackHistoryEntry("number_2");
    strategy.createRetry(call);

    assertEquals(Arrays.asList(FRESH, RETRY, CALLBACK, RETRY), call.getCallProcessHistory());
    assertEquals(2, countSteps(RETRY));
    assertEquals(2, call.getRetryHistory().size());
    assertEquals(1, countSteps(CALLBACK));
    assertEquals(1, call.getCbHistory().size());
  }

  @Test
  public void callProcessTrailAlwaysStartsWithFresh() {
    call.ensureCallProcessStartsWithFresh();

    assertEquals(Arrays.asList(FRESH), call.getCallProcessHistory());

    call.ensureCallProcessStartsWithFresh();

    assertEquals("Tekrar cagri F adimini cogaltmaz", Arrays.asList(FRESH), call.getCallProcessHistory());
  }

  // ---------------------------------------------------------------------
  // Kolon hydration semantigi (ayrac farklari sayim icin kritik)
  // ---------------------------------------------------------------------

  @Test
  public void retryHistoryIsHydratedFromSemicolonSeparatedColumn() {
    setRawColumn("retry_history", "number_1;number_2;number_1");

    assertEquals(Arrays.asList("number_1", "number_2", "number_1"), call.getRetryHistory());
  }

  @Test
  public void cbHistoryIsHydratedFromCommaSeparatedColumn() {
    setRawColumn("cb_history", "number_2,number_1");

    assertEquals(Arrays.asList("number_2", "number_1"), call.getCbHistory());
  }

  @Test
  public void callProcessIsHydratedFromSemicolonSeparatedColumn() {
    setRawColumn("call_process", "F;R;CB;R");

    assertEquals(Arrays.asList(FRESH, RETRY, CALLBACK, RETRY), call.getCallProcessHistory());
  }

  @Test
  public void blankEntriesAreDroppedDuringHydration() {
    setRawColumn("retry_history", "number_1;;number_2;");

    assertEquals(Arrays.asList("number_1", "number_2"), call.getRetryHistory());
  }

  @Test
  public void unmappedHistoryColumnsHydrateToEmptyLists() {
    when(campaign.getColResultCbHistory()).thenReturn(null);
    when(campaign.getColResultCallProcess()).thenReturn(null);

    assertTrue(call.getCbHistory().isEmpty());
    assertTrue(call.getCallProcessHistory().isEmpty());
    assertFalse("Kolon eslenmemesi hata firlatmamalidir", call.getRetryHistory() == null);
  }

  /**
   * Var olan gecmis uzerine yeni retry eklendiginde hydration ve append
   * birlikte calisir; sayim kaynagi bu birlesik listedir.
   */
  @Test
  public void hydratedHistoryIsExtendedByNewlyScheduledRetry() {
    setRawColumn("retry_history", "number_1");
    setRawColumn("call_process", "F;R");

    new RoundRobinNextNumber().createRetry(call);

    assertEquals(Arrays.asList("number_1", "number_2"), call.getRetryHistory());
    assertEquals(Arrays.asList(FRESH, RETRY, RETRY), call.getCallProcessHistory());
  }

  private int countSteps(String step) {
    int count = 0;
    for (String entry : call.getCallProcessHistory()) {
      if (step.equals(entry)) {
        count++;
      }
    }
    return count;
  }

}
