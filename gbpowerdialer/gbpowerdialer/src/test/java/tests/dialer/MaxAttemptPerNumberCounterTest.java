package tests.dialer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;

/**
 * FEAT-325559 Faz 3: numara basina arama sayacinin kaynagi olan ARAMA DIZISI kurulumu.
 *
 * <p>Ayri bir sayac kolonu eklenmedigi icin dizi {@code call_process} izi kursor olarak
 * kullanilarak {@code retry_history} ve {@code cb_history} uzerinden yeniden kurulur. Bu testler
 * kursor eslemesini, fresh arama atfini, tutarsizlik fallback'ini ve muhafazakarlik yonunu
 * kilitler.</p>
 */
public class MaxAttemptPerNumberCounterTest {

  private final Call call = spy(new Call());
  private final Campaign campaign = mock(Campaign.class);
  private final JSONObject rawData = mock(JSONObject.class);
  private final List<PhoneNumberColumn> numberColumns = new ArrayList<>();

  @Before
  public void setup() {
    when(campaign.getColResultRetryHistory()).thenReturn("retry_history");
    when(campaign.getColResultCbHistory()).thenReturn("cb_history");
    when(campaign.getColResultCallProcess()).thenReturn("call_process");
    // Gercek Campaign.getEnabledPhoneNumberColumns() enabled kolonlari colSort'a gore SIRALAR;
    // stub da ayni sozlesmeyi uygulamalidir, aksi halde test ekleme sirasini olcerdi.
    when(campaign.getEnabledPhoneNumberColumns()).thenAnswer(invocation -> {
      List<PhoneNumberColumn> sorted = new ArrayList<>(numberColumns);
      sorted.sort(Comparator.comparingInt(PhoneNumberColumn::getColSort));
      return sorted;
    });

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

  private void threeNumbers() {
    addPhoneColumn("phone_number_1", "number_1", 1);
    addPhoneColumn("phone_number_2", "number_2", 2);
    addPhoneColumn("phone_number_3", "number_3", 3);
  }

  // ---------------------------------------------------------------------
  // Fresh arama atfi
  // ---------------------------------------------------------------------

  @Test
  public void firstDialedNumberIsFirstFilledColumnByColSort() {
    addPhoneColumn("phone_number_2", "number_2", 2);
    addPhoneColumn("phone_number_1", "number_1", 1);

    assertEquals("number_1", call.getFirstDialedNumber());
  }

  @Test
  public void firstDialedNumberIsNullWhenNoColumnHasValue() {
    PhoneNumberColumn empty = new PhoneNumberColumn();
    empty.setColNumber("phone_number_1");
    numberColumns.add(empty);

    assertNull(call.getFirstDialedNumber());
  }

  /**
   * Iz henuz yazilmamis fresh arama: {@code createRetry} icinde iz
   * {@code updateRetryHistory} sirasinda yazildigi icin ilk retry kararinda uc kolon da bostur.
   * Kayit fiilen bir kez aranmistir; dizi tek elemanli olmalidir.
   */
  @Test
  public void emptyTrailAndEmptyHistoriesCountFreshDialOnce() {
    threeNumbers();

    assertEquals(Arrays.asList("number_1"), call.getDialedNumberSequence());
  }

  @Test
  public void recordWithoutAnyNumberProducesEmptySequence() {
    assertTrue(call.getDialedNumberSequence().isEmpty());
  }

  // ---------------------------------------------------------------------
  // Kursor eslemesi
  // ---------------------------------------------------------------------

  @Test
  public void retryStepsConsumeRetryHistoryInOrder() {
    threeNumbers();
    setRawColumn("call_process", "F;R;R");
    setRawColumn("retry_history", "number_2;number_3");

    assertEquals(Arrays.asList("number_1", "number_2", "number_3"), call.getDialedNumberSequence());
  }

  @Test
  public void callbackStepsConsumeCallbackHistoryInOrder() {
    threeNumbers();
    setRawColumn("call_process", "F;CB;CB");
    setRawColumn("cb_history", "number_2,number_3");

    assertEquals(Arrays.asList("number_1", "number_2", "number_3"), call.getDialedNumberSequence());
  }

  /**
   * Karisik iz iki listeyi BAGIMSIZ kursorlarla tuketir; esleme belirsizlik birakmaz.
   */
  @Test
  public void mixedTrailConsumesBothHistoriesWithIndependentCursors() {
    threeNumbers();
    setRawColumn("call_process", "F;R;CB;R");
    setRawColumn("retry_history", "number_2;number_3");
    setRawColumn("cb_history", "number_2");

    assertEquals(Arrays.asList("number_1", "number_2", "number_2", "number_3"),
        call.getDialedNumberSequence());
  }

  @Test
  public void sameNumberDialedRepeatedlyIsCountedEachTime() {
    threeNumbers();
    setRawColumn("call_process", "F;R;R;R");
    setRawColumn("retry_history", "number_1;number_1;number_1");

    assertEquals(Arrays.asList("number_1", "number_1", "number_1", "number_1"),
        call.getDialedNumberSequence());
  }

  /**
   * D2: agent'in elle girdigi, telefon kolonlarinda BULUNMAYAN callback numarasi da kendi
   * degeriyle diziye girer (K16); muaf tutulmaz.
   */
  @Test
  public void callbackNumberOutsidePhoneColumnsIsCountedWithItsOwnValue() {
    threeNumbers();
    setRawColumn("call_process", "F;CB");
    setRawColumn("cb_history", "0555_manual_entry");

    assertEquals(Arrays.asList("number_1", "0555_manual_entry"), call.getDialedNumberSequence());
  }

  // ---------------------------------------------------------------------
  // Tutarsizlik fallback'i
  // ---------------------------------------------------------------------

  /**
   * Eski kayit: {@code retry_history} dolu ama {@code call_process} bos (kolon sonradan eklendi).
   * Kisayol EKSIK sayardi; fallback fresh + tum retry gecmisini sayar.
   */
  @Test
  public void emptyTrailWithPopulatedRetryHistoryFallsBackInsteadOfUndercounting() {
    threeNumbers();
    setRawColumn("retry_history", "number_2;number_3;number_2");

    assertEquals(Arrays.asList("number_1", "number_2", "number_3", "number_2"),
        call.getDialedNumberSequence());
  }

  @Test
  public void trailWithFewerRetryStepsThanRetryHistoryFallsBack() {
    threeNumbers();
    setRawColumn("call_process", "F;R");
    setRawColumn("retry_history", "number_2;number_3;number_2");

    assertEquals(Arrays.asList("number_1", "number_2", "number_3", "number_2"),
        call.getDialedNumberSequence());
  }

  @Test
  public void trailWithMoreRetryStepsThanRetryHistoryFallsBackWithoutIndexError() {
    threeNumbers();
    setRawColumn("call_process", "F;R;R;R");
    setRawColumn("retry_history", "number_2");

    assertEquals(Arrays.asList("number_1", "number_2"), call.getDialedNumberSequence());
  }

  @Test
  public void unmappedCallProcessColumnFallsBackSafely() {
    threeNumbers();
    when(campaign.getColResultCallProcess()).thenReturn(null);
    setRawColumn("retry_history", "number_2;number_2");

    assertEquals(Arrays.asList("number_1", "number_2", "number_2"), call.getDialedNumberSequence());
  }

  @Test
  public void callbackStepCountMismatchAlsoFallsBack() {
    threeNumbers();
    setRawColumn("call_process", "F;CB");
    setRawColumn("retry_history", "number_2");

    // CB adimi var ama cb_history bos -> tutarsiz -> fallback (fresh + retry gecmisi)
    assertEquals(Arrays.asList("number_1", "number_2"), call.getDialedNumberSequence());
  }

  // ---------------------------------------------------------------------
  // Iki OKUMA: hangup SONRASI (tum iz) vs arama ONCESI (tamamlanmislar)
  // ---------------------------------------------------------------------

  /**
   * {@code getDialedNumberSequence()} HANGUP SONRASI okumadir: {@code R}/{@code CB} adimlari arama
   * planlanirken yazilir, dolayisiyla strateji karar verirken izdeki her girdi FIILEN yapilmis bir
   * aramadir. Bu yuzden bu dizi izin tamamini sayar.
   *
   * <p><b>Iddia guncellendi:</b> eskiden ayni sonuc "muhafazakarlik / bir fazla sayma" olarak
   * yorumlaniyordu. Yorum yanlisti; iki katman ayni diziyi ayni anlamla okuyunca arama ONCESI
   * okumada numaranin son hakki yaniyordu (fiili limit {@code maxAttemptPerNumber - 1}). Ayrim
   * artik acikca iki metotta durur.</p>
   */
  @Test
  public void postHangupSequenceCountsEveryTrailEntry() {
    threeNumbers();
    setRawColumn("call_process", "F;R");
    setRawColumn("retry_history", "number_2");

    List<String> sequence = call.getDialedNumberSequence();

    assertEquals("Hangup sonrasi: fresh + retry = 2 yapilmis arama", 2, sequence.size());
    assertTrue(sequence.contains("number_2"));
  }

  /**
   * {@code getCompletedDialSequence()} ARAMA ONCESI okumadir: satirda bekleyen bir plan varsa
   * ({@code retry_date} dolu) izdeki son girdi HENUZ YAPILMAMIS bir aramadir ve sayilmaz.
   */
  @Test
  public void completedSequenceExcludesPendingPlanEntry() {
    threeNumbers();
    when(campaign.getColResultRetryDate()).thenReturn("retry_date");
    setRawColumn("call_process", "F;R");
    setRawColumn("retry_history", "number_2");
    setRawColumn("retry_date", "45000.5");

    assertEquals("Arama oncesi: yalniz fresh arama tamamlanmis",
        Arrays.asList("number_1"), call.getCompletedDialSequence());
  }

  /**
   * Bekleyen plan YOKSA ({@code retry_date} bos) iki okuma ayni sonucu verir; izin tamami
   * tamamlanmis aramadir. Kampanya reset'i sonrasi FRESH olarak donen kayitlarin hali budur.
   */
  @Test
  public void completedSequenceEqualsFullTrailWhenNoPendingPlan() {
    threeNumbers();
    when(campaign.getColResultRetryDate()).thenReturn("retry_date");
    setRawColumn("call_process", "F;R");
    setRawColumn("retry_history", "number_2");

    assertEquals(call.getDialedNumberSequence(), call.getCompletedDialSequence());
  }

  /**
   * Hic islem gormemis kayit: uc gecmis kolonu da bos. Hangup sonrasi okuma ilk kolona bir arama
   * atfeder (fresh arama yapilmistir), arama oncesi okuma SIFIR dondurur; aksi halde
   * {@code limit=1} olan kampanyada ilk kolon hic aranmazdi.
   */
  @Test
  public void untouchedRecordHasNoCompletedDialYet() {
    threeNumbers();

    assertEquals(Arrays.asList("number_1"), call.getDialedNumberSequence());
    assertTrue(call.getCompletedDialSequence().isEmpty());
  }

}
