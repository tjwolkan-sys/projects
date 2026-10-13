package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.retry.NoEligibleRetryNumberException;
import com.powerdialer.engine.dialer.retry.ReachRetryPerNumber;
import com.powerdialer.engine.dialer.retry.RetryStrategy;
import com.powerdialer.engine.dialer.retry.RoundRobinNextNumber;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.util.Constants;

/**
 * 03.08.2026 uretim vakasi: countAllCallTypes=true kampanyada DNC-identifier
 * result code sonrasi tum numaralar DNC/limit disi kalinca
 * ReachRetryPerNumber.processRetryNumberWithDNC sonsuz donguye girdi; 4
 * webphone-event thread'i kilitlendi, CPU %100'e sabitlendi ve user result
 * code'lar 4 gun camp tablosu ile pd_cdr'a yazilamadi. Bu testler donguyu
 * lookup sigortasiyla (sonsuz yuruyus = sigorta patlar, hang olmaz) kanitlar
 * ve beklenen davranisi tanimlar: aranabilir numara yoksa cagri complete
 * edilir, thread asla kilitlenmez.
 */
public class RetryDncExhaustionInfiniteLoopRegressionTest {

  private static final int WALK_FUSE_LIMIT = 200;

  private Call call = spy(new Call());
  private Campaign campaign = mock(Campaign.class);
  private JSONObject rawData = mock(JSONObject.class);
  private CallCleaner callCleaner = mock(CallCleaner.class);
  private CallOriginator callOriginator = mock(CallOriginator.class);
  private List<PhoneNumberColumn> numberColumns = new ArrayList<>();
  private ReachRetryPerNumber strategy = new ReachRetryPerNumber();

  @Before
  public void setup() {
    when(campaign.getColResultRetryHistory()).thenReturn("retry_history");
    when(campaign.getEnabledPhoneNumberColumns()).thenReturn(numberColumns);
    when(campaign.getRetryOveralAttempt()).thenReturn(2);
    when(campaign.getMacroMaxAttempt()).thenReturn(5);

    when(call.getCallOriginator()).thenReturn(callOriginator);
    when(callOriginator.getCallCleaner()).thenReturn(callCleaner);

    call.setCampaign(campaign);
    call.setRawData(rawData);
  }

  /**
   * Telefon kolonu okumasina sigorta takar: sonsuz DNC yuruyusu bu kolonu
   * sinirsiz okuyacagi icin test hang etmek yerine sigorta mesajiyla duser.
   */
  private void addFusedPhoneColumn(String columnName, final String phoneNumber) {
    PhoneNumberColumn column = new PhoneNumberColumn();
    column.setColNumber(columnName);
    numberColumns.add(column);
    when(rawData.has(columnName)).thenReturn(true);
    final AtomicInteger lookups = new AtomicInteger();
    when(rawData.getString(columnName)).thenAnswer(invocation -> {
      if (lookups.incrementAndGet() > WALK_FUSE_LIMIT)
        throw new AssertionError("Sonsuz DNC yuruyusu: '" + columnName + "' kolonu "
            + WALK_FUSE_LIMIT + " kereden fazla okundu (fix oncesi kilitlenme davranisi)");
      return phoneNumber;
    });
  }

  private void setRetryHistory(String history) {
    when(rawData.has("retry_history")).thenReturn(true);
    when(rawData.getString("retry_history")).thenReturn(history);
  }

  private static void injectStrategyState(RetryStrategy strategy, Call call, Campaign campaign) throws Exception {
    Field callField = RetryStrategy.class.getDeclaredField("call");
    callField.setAccessible(true);
    callField.set(strategy, call);
    Field campaignField = RetryStrategy.class.getDeclaredField("campaign");
    campaignField.setAccessible(true);
    campaignField.set(strategy, campaign);
  }

  @Test
  public void countAllCallTypesGuardReportsLimitReachedWhenOnlyNumberIsInDnc() throws Exception {
    addFusedPhoneColumn("phone_number_1", "number_1");
    call.setCalledNumber("number_1");
    call.setDNCNumbers(new HashSet<>(Arrays.asList("number_1")));
    when(campaign.getCountAllCallTypes()).thenReturn(true);
    doReturn(1).when(call).getAttempts();
    injectStrategyState(strategy, call, campaign);

    assertTrue("Aranabilir numara kalmadiginda guard retry'i kesmeli",
        strategy.isOverallRetryLimitReached());
  }

  @Test(timeout = 10000)
  public void countAllCallTypesCreateRetryCompletesWhenOnlyNumberIsInDnc() {
    addFusedPhoneColumn("phone_number_1", "number_1");
    call.setCalledNumber("number_1");
    call.setDNCNumbers(new HashSet<>(Arrays.asList("number_1")));
    when(campaign.getCountAllCallTypes()).thenReturn(true);
    doReturn(1).when(call).getAttempts();

    strategy.createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
    assertTrue(call.getRetryHistory().isEmpty());
  }

  @Test(timeout = 10000)
  public void countAllCallTypesCreateRetryCompletesWhenAllNumbersReachedPerNumberLimit() {
    addFusedPhoneColumn("phone_number_1", "number_1");
    addFusedPhoneColumn("phone_number_2", "number_2");
    call.setCalledNumber("number_1");
    when(campaign.getCountAllCallTypes()).thenReturn(true);
    doReturn(1).when(call).getAttempts();
    setRetryHistory("number_1;number_1;number_2;number_2");

    strategy.createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
  }

  /**
   * Ayni numara birden fazla kolonda olunca eski guard Collectors.toMap
   * duplicate key istisnasiyla patliyor ve retry karari hic verilemiyordu;
   * guard total olmali, istisna atmamalidir.
   */
  @Test(timeout = 10000)
  public void legacyGuardToleratesDuplicatePhoneNumbersAcrossColumns() {
    addFusedPhoneColumn("phone_number_1", "number_1");
    addFusedPhoneColumn("phone_number_2", "number_1");
    addFusedPhoneColumn("phone_number_3", "number_2");
    call.setCalledNumber("number_1");
    call.setDNCNumbers(new HashSet<>(Arrays.asList("number_2")));

    strategy.createRetry(call);

    verify(callCleaner, never()).completeCall(call);
    assertEquals("number_1", call.getRetryNumber());
  }

  /**
   * Savunma katmani: guard yanlislikla gecse bile numara yuruyusu en fazla bir
   * tam turla sinirlidir ve thread'i kilitlemek yerine aday kalmadigini bildirir.
   */
  @Test(timeout = 10000)
  public void boundedWalkFailsFastInsteadOfSpinningWhenNoEligibleNumberLeft() throws Exception {
    addFusedPhoneColumn("phone_number_1", "number_1");
    call.setCalledNumber("number_1");
    call.setRetryNumber("number_1");
    call.setDNCNumbers(new HashSet<>(Arrays.asList("number_1")));
    injectStrategyState(strategy, call, campaign);

    try {
      strategy.processRetryNumberWithDNC();
      fail("Aday numara yokken yuruyus sessizce donmemeli");
    } catch (NoEligibleRetryNumberException expected) {
      assertTrue(expected.getMessage().contains("No callable retry number left"));
    }
  }

  /**
   * FEAT-325559: ayni bounded-walk sigortasi numara basina limit predicate'i icin de gecerlidir.
   * Tum numaralar limitini doldurdugunda yuruyus sonsuza girmez; aday kalmadigini bildirir.
   */
  @Test(timeout = 10000)
  public void boundedWalkFailsFastWhenAllNumbersReachedPerNumberLimit() throws Exception {
    addFusedPhoneColumn("phone_number_1", "number_1");
    addFusedPhoneColumn("phone_number_2", "number_2");
    when(campaign.getMaxAttemptPerNumber()).thenReturn(2);
    when(campaign.getColResultCallProcess()).thenReturn("call_process");
    when(rawData.has("call_process")).thenReturn(true);
    when(rawData.get("call_process")).thenReturn("F;R;R;R");
    when(rawData.getString("call_process")).thenReturn("F;R;R;R");
    setRetryHistory("number_1;number_2;number_2");
    call.setCalledNumber("number_1");
    call.setRetryNumber("number_1");

    RoundRobinNextNumber strategy = new RoundRobinNextNumber();
    injectStrategyState(strategy, call, campaign);

    try {
      strategy.processRetryNumberWithDNC();
      fail("Tum numaralar limitte iken yuruyus sessizce donmemeli");
    } catch (NoEligibleRetryNumberException expected) {
      assertTrue(expected.getMessage().contains("No callable retry number left"));
    }
  }

  /**
   * FEAT-325559: limit kaynakli yuruyus tukenmesinde de thread kilitlenmez, kayit complete edilir
   * ve kapanis nedeni yazilir.
   */
  @Test(timeout = 10000)
  public void createRetryCompletesWithCloseReasonWhenPerNumberLimitExhaustsAllNumbers() {
    addFusedPhoneColumn("phone_number_1", "number_1");
    addFusedPhoneColumn("phone_number_2", "number_2");
    when(campaign.getMaxAttemptPerNumber()).thenReturn(2);
    when(campaign.getColResultCallProcess()).thenReturn("call_process");
    when(rawData.has("call_process")).thenReturn(true);
    when(rawData.get("call_process")).thenReturn("F;R;R;R");
    when(rawData.getString("call_process")).thenReturn("F;R;R;R");
    when(campaign.getRetryOveralAttempt()).thenReturn(60);
    setRetryHistory("number_1;number_2;number_2");
    call.setCalledNumber("number_1");

    new RoundRobinNextNumber().createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
    assertEquals(com.powerdialer.util.Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED, call.getCloseReason());
  }

  /**
   * Savunma katmani: setRetryNumber aday bulamadigini bildirirse createRetry
   * cagriyi retry planlamadan complete eder.
   */
  @Test(timeout = 10000)
  public void createRetryCompletesCallWhenWalkSignalsNoEligibleNumber() {
    addFusedPhoneColumn("phone_number_1", "number_1");
    call.setCalledNumber("number_1");

    RetryStrategy signallingStrategy = new RetryStrategy() {
      @Override
      public boolean isOverallRetryLimitReached() {
        return false;
      }

      @Override
      public void setRetryNumber() {
        throw new NoEligibleRetryNumberException("test: aday numara yok");
      }
    };

    signallingStrategy.createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
    assertTrue(call.getRetryHistory().isEmpty());
  }

  /**
   * FEAT-325470: kullanilmayan numara (code 113) sonucu + diger tum numaralar DNC'de. Yeni dal
   * halkayi en fazla bir tur tarar, istisna firlatmaz ve cagriyi kapanis nedeniyle complete
   * eder; kolon okuma sigortasi patlamamalidir (yuruyus sinirsiz degildir).
   */
  @Test(timeout = 10000)
  public void badNumberWithAllOtherNumbersInDncCompletesWithoutSpinning() {
    addFusedPhoneColumn("phone_number_1", "number_1");
    addFusedPhoneColumn("phone_number_2", "number_2");
    call.setCalledNumber("number_1");
    call.setDNCNumbers(new HashSet<>(Arrays.asList("number_2")));
    TelephonyResultCode badNumber = new TelephonyResultCode();
    badNumber.setId(86L);
    badNumber.setCode(Constants.TELEPHONY_RC_AMD_BAD_NUMBER);
    call.setTelephonyResultCode(badNumber);

    strategy.createRetry(call);

    verify(callCleaner).completeCall(call);
    assertEquals(Constants.CLOSE_REASON_ALL_NUMBERS_UNREACHABLE, call.getCloseReason());
    assertNull(call.getRetryDate());
    assertTrue(call.getRetryHistory().isEmpty());
  }

  /**
   * FEAT-325470: kullanilmayan numara + aranan numaranin KENDISI de DNC'de (DNC identifier sonrasi
   * gelen bad number). Iki dislama ust uste binse de sigorta patlamaz ve kayit kapanir.
   */
  @Test(timeout = 10000)
  public void badNumberOnDncNumberWithNoOtherCandidateCompletesWithoutSpinning() {
    addFusedPhoneColumn("phone_number_1", "number_1");
    call.setCalledNumber("number_1");
    call.setDNCNumbers(new HashSet<>(Arrays.asList("number_1")));
    TelephonyResultCode badNumber = new TelephonyResultCode();
    badNumber.setId(86L);
    badNumber.setCode(Constants.TELEPHONY_RC_AMD_BAD_NUMBER);
    call.setTelephonyResultCode(badNumber);

    strategy.createRetry(call);

    verify(callCleaner).completeCall(call);
    assertNull(call.getRetryDate());
    assertTrue(call.getRetryHistory().isEmpty());
  }

}
