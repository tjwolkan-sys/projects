package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
 * FEAT-325559 Faz 7 / P2.3 — numara karsilastirma ve sayiminda TEK normalizasyon kurali.
 *
 * <p><b>Bulgu:</b> sayim anahtarlari HAM kolon degerinden geliyordu
 * ({@code getFirstDialedNumber}, {@code retry_history}, {@code cb_history}), producer sorgusu ise
 * TRIMLENMIS deger ({@code CallProducer.trimPhoneNumber}). Veride bastaki/sondaki bosluk varsa
 * harita iskalaniyor, sayac 0 kabul ediliyor ve limit HIC uygulanmiyordu. Ayrica
 * {@code getNumbersInRawData} {@code isNotEmpty} kullandigi icin yalnizca bosluktan olusan bir
 * kolon "dolu numara" sayiliyor ve {@code noEligibleNumberLeft} sonsuza kadar {@code false}
 * kaliyordu — erken kapatma o kayitta hic tetiklenmiyordu.</p>
 *
 * <p><b>Kapsam siniri:</b> kolonlara YAZILAN deger degismez (gecmis semantigi korunur);
 * normalizasyon yalniz KARSILASTIRMA/SAYIM icin uygulanir. {@code getNumbersInRawData} ve
 * {@code getNumberColumnsInRawData} sozlesmeleri de DEGISMEDI — feature-off davranisi birebir
 * ayni kalsin diye ({@code anyNumberPresentAfterDNC} ve
 * {@code advanceRetryNumberPastIneligible} sigortasi o metotlara dayaniyor). Normalizasyon
 * ozellik-kapili aday kumesinde ({@code Call.getLimitCandidateNumbers}) durur.</p>
 */
public class MaxAttemptPerNumberNormalizationTest {

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
  // Normalizasyon kurali
  // ---------------------------------------------------------------------

  @Test
  public void normalizationTrimsAndTreatsBlankAsAbsent() {
    assertEquals("number_1", Call.normalizeNumberForLimit("  number_1 "));
    assertEquals("number_1", Call.normalizeNumberForLimit("number_1"));
    assertEquals("number_1", Call.normalizeNumberForLimit("\tnumber_1\t"));
    assertNull(Call.normalizeNumberForLimit("   "));
    assertNull(Call.normalizeNumberForLimit(""));
    assertNull(Call.normalizeNumberForLimit(null));
  }

  // ---------------------------------------------------------------------
  // P2.3 (a) — bosluklu deger limiti sessizce devre disi birakmamali
  // ---------------------------------------------------------------------

  /**
   * Kolon degeri bosluklu, gecmis kayitlari trimli: sayac numarayi BULMALI ve limit UYGULANMALI.
   * Duzeltme oncesi harita anahtari {@code "  number_1  "} idi, sorgu {@code "number_1"} yapiyordu;
   * sayac 0 kabul edilip numara SINIRSIZ araniyordu.
   *
   * <p><b>Kalan sinir (kapsam disi, bilincli):</b> bu kayitta limit artik uygulanir ama numara
   * halkasi {@code number_2}'ye ILERLEYEMEZ, kayit kapanir. Sebep bu isin disinda:
   * {@code Call.findNextNumberToCall} halkadaki konumu HAM esitlikle ariyor
   * ({@code "number_1".equals("  number_1  ")} -&gt; false), dolayisiyla bosluklu kolonda halka
   * yuruyusu bugun de kirik. O metot ozellik-kapali TUM stratejilerde kullanildigi icin burada
   * degistirilmedi (SINIRLAR: feature-off davranisi birebir ayni kalir). Onemli olan yon
   * korunuyor: over-dialing YOK.</p>
   */
  @Test
  public void paddedColumnValueStillCountsTowardItsLimit() {
    numbers("  number_1  ", "number_2");
    dialHistory("number_1", "number_1");
    call.setCalledNumber("number_1");
    call.setRetryNumber("number_1");

    new RoundRobinNextNumber().createRetry(call);

    assertNull("Limitini doldurmus numaraya YENI arama planlanmamalidir", call.getRetryDate());
    assertEquals("Kayit limit nedeniyle kapanmalidir",
        Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED, call.getCloseReason());
  }

  /**
   * Ayni senaryonun bosluksuz hali: halka yuruyusu calisir ve limitini doldurmus numara yerine
   * {@code number_2} planlanir. Yukaridaki testin kalan sinirinin normalizasyondan DEGIL halka
   * yuruyusunden geldigini kanitlar.
   */
  @Test
  public void unpaddedEquivalentAdvancesToNextNumberInstead() {
    numbers("number_1", "number_2");
    dialHistory("number_1", "number_1");
    call.setCalledNumber("number_1");
    call.setRetryNumber("number_1");

    new RoundRobinNextNumber().createRetry(call);

    assertEquals("number_2", call.getRetryNumber());
    assertNull(call.getCloseReason());
  }

  /**
   * Ters yon: gecmis kaydi bosluklu, kolon degeri trimli. Ayni tek kural iki yonde de calisir.
   */
  @Test
  public void paddedHistoryEntryStillCountsTowardItsLimit() {
    numbers("number_1", "number_2");
    setRawColumn("call_process", "F;R;R");
    setRawColumn("retry_history", " number_1 ; number_1 ");
    call.setCalledNumber("number_1");
    call.setRetryNumber("number_1");

    new RoundRobinNextNumber().createRetry(call);

    assertEquals("number_2", call.getRetryNumber());
  }

  /**
   * Normalizasyon sayimda birlestirdigi icin ayni numaranin bosluklu ve trimli yazimlari TEK
   * numara olarak sayilir; limit tam degerinde dolar.
   */
  @Test
  public void paddedAndTrimmedWritingsOfSameNumberShareOneCounter() {
    numbers("number_1");
    setRawColumn("call_process", "F;R;R");
    setRawColumn("retry_history", " number_1 ;number_1");

    assertEquals("3 arama tek numaraya ait sayilmalidir",
        Integer.valueOf(3), countFor("number_1"));
  }

  // ---------------------------------------------------------------------
  // P2.3 (b) — yalnizca bosluktan olusan kolon aday sayilmamali
  // ---------------------------------------------------------------------

  /**
   * Yalnizca bosluktan olusan bir kolon "dolu numara" sayilirsa {@code noEligibleNumberLeft}
   * sonsuza kadar {@code false} doner ve erken kapatma o kayitta HIC tetiklenmez.
   * Producer o kolonu zaten atliyor ({@code trimPhoneNumber} null doner), dolayisiyla kayit ne
   * kapanir ne aranir.
   */
  @Test
  public void whitespaceOnlyColumnIsNotALimitCandidate() {
    numbers("number_1", "   ");

    assertEquals(Arrays.asList("number_1"), call.getLimitCandidateNumbers());
  }

  /**
   * Ayni kayitta {@code number_1} limitini doldurmussa: bosluklu kolon aday olmadigi icin
   * aranabilir numara kalmaz ve kayit KAPANIR (kriter 2).
   */
  @Test
  public void recordWithWhitespaceOnlySecondColumnIsClosedWhenRealNumberIsExhausted() {
    numbers("number_1", "    ");
    dialHistory("number_1", "number_1");
    call.setCalledNumber("number_1");
    call.setRetryNumber("number_1");

    new RoundRobinNextNumber().createRetry(call);

    assertNotNull("Kayit kapatilmis olmalidir", call.getCloseReason());
    assertNull("Yeni retry planlanmamalidir", call.getRetryDate());
  }

  /**
   * Aday kumesi TEKILDIR: ayni numara iki kolonda duruyorsa bir kez sayilir (03.08.2026
   * duplicate-key semantigi korunur).
   */
  @Test
  public void duplicateNumberAcrossColumnsIsASingleCandidate() {
    numbers("number_1", "number_1", "number_2");

    assertEquals(Arrays.asList("number_1", "number_2"), call.getLimitCandidateNumbers());
  }

  // ---------------------------------------------------------------------
  // Kapsam siniri: yazim ve feature-off sozlesmeleri degismedi
  // ---------------------------------------------------------------------

  /**
   * Normalizasyon yalniz OKUMA/karsilastirma icindir; kolonlara yazilan deger degismez.
   * Bosluklu kolon degeri planlanirsa gecmise HAM hali yazilir.
   */
  @Test
  public void writtenHistoryValueIsNotNormalized() {
    numbers("  number_1  ");
    call.setCalledNumber("  number_1  ");
    call.setRetryNumber("  number_1  ");

    new RoundRobinNextNumber().createRetry(call);

    assertEquals("Yazilan deger HAM kalmalidir",
        Arrays.asList("  number_1  "), call.getRetryHistory());
  }

  /**
   * {@code getNumbersInRawData} sozlesmesi DEGISMEDI: yalnizca bosluktan olusan kolon hâlâ
   * "mevcut numara" sayilir. Bu metot feature-off yollarinda kullaniliyor
   * ({@code anyNumberPresentAfterDNC}) ve {@code advanceRetryNumberPastIneligible} sigortasinin
   * yuruyus sinirini besliyor; daraltmak feature-off davranisini degistirirdi.
   */
  @Test
  public void getNumbersInRawDataContractIsUnchanged() {
    numbers("number_1", "   ");

    assertEquals("Bosluklu kolon bu metotta HALA sayilir",
        Arrays.asList("number_1", "   "), call.getNumbersInRawData());
  }

  /**
   * Ozellik kapaliyken aday kumesi hic kullanilmaz; normalizasyon davranis degisikligi
   * uretmez.
   */
  @Test
  public void featureOffIsUnaffectedByNormalization() {
    when(campaign.getMaxAttemptPerNumber()).thenReturn(null);
    numbers("  number_1  ", "number_2");
    dialHistory("number_1", "number_1", "number_1", "number_1");
    call.setCalledNumber("number_1");
    call.setRetryNumber("number_1");

    new RoundRobinNextNumber().createRetry(call);

    assertNull("Ozellik kapaliyken kapanis nedeni YAZILMAZ", call.getCloseReason());
    assertNotNull("Ozellik kapaliyken retry planlanmaya devam eder", call.getRetryDate());
    assertFalse(call.getRetryHistory().isEmpty());
  }

  private Integer countFor(String number) {
    int count = 0;
    for (String dialed : call.getDialedNumberSequence()) {
      if (number.equals(Call.normalizeNumberForLimit(dialed))) {
        count++;
      }
    }
    return count;
  }

}
