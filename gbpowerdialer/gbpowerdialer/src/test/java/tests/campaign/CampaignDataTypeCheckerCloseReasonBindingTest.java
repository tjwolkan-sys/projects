package tests.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.Before;
import org.junit.Test;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.exceptions.CampaignIllegalStateException;
import com.powerdialer.model.Campaign;
import com.powerdialer.service.CampaignDataTypeChecker;
import com.powerdialer.util.EnCipherUtils;

/**
 * FEAT-325559 Faz 2: {@code close_reason} kolonunun tip doğrulaması ve OTOMATİK binding'i (K18).
 *
 * <p>Binding kampanyada opsiyoneldir; zorunlu yapılırsa kolonu henüz eklenmemiş tüm mevcut
 * kampanyalar kaydedilemez hâle gelirdi (R9). Bunun yerine kolon result tablosunda mevcutsa
 * binding kendiliğinden atanır — repoda {@code cb_history} ve {@code call_process} için
 * kullanılan desenin aynısı.</p>
 */
public class CampaignDataTypeCheckerCloseReasonBindingTest {

  private static final String LIST_TABLE = "SRC_TABLE";
  private static final String RESULT_TABLE = "RES_TABLE";

  private CampaignDataTypeChecker checker;
  private CampaignDbOperations dbOperations;
  private Campaign campaign;

  @Before
  public void setup() throws Exception {
    checker = new CampaignDataTypeChecker();
    dbOperations = mock(CampaignDbOperations.class);
    EnCipherUtils cipherUtils = mock(EnCipherUtils.class);

    inject(checker, "campaignDbOperations", dbOperations);
    inject(checker, "enChiperUtils", cipherUtils);

    campaign = new Campaign();
    campaign.setId(1L);
    campaign.setDbListTable(LIST_TABLE);
    campaign.setDbResultTable(RESULT_TABLE);
    campaign.setDbConnectionString("jdbc:sqlserver://placeholder");
    campaign.setDbUsername("placeholder");
    campaign.setDbPassword("placeholder");
    when(cipherUtils.decryptPassword(any())).thenReturn("placeholder");
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  /**
   * Kaynak tablo boş, result tablosu verilen kolonları taşır. Kampanyadaki diğer
   * {@code colResult*} alanları null bırakıldığı için onların doğrulaması otomatik geçer.
   */
  private void stubTables(String[][] resultTableColumns) throws Exception {
    when(dbOperations.getTableColumnsWithTypes(eq(LIST_TABLE), anyLong(), any(), any(), any()))
        .thenReturn(new String[0][0]);
    when(dbOperations.getTableColumnsWithTypes(eq(RESULT_TABLE), anyLong(), any(), any(), any()))
        .thenReturn(resultTableColumns);
  }

  @Test
  public void textCloseReasonColumnPassesTypeCheck() throws Exception {
    stubTables(new String[][] { { "close_reason", "NVARCHAR" } });
    campaign.setColResultCloseReason("close_reason");

    assertTrue(checker.checkColumnsTypes(campaign));
  }

  @Test
  public void numericCloseReasonColumnIsRejected() throws Exception {
    stubTables(new String[][] { { "close_reason", "INT" } });
    campaign.setColResultCloseReason("close_reason");

    try {
      checker.checkColumnsTypes(campaign);
      fail("Sayisal tipte close_reason kolonu reddedilmelidir");
    } catch (CampaignIllegalStateException expected) {
      assertEquals("campaign.start.wrong.types", expected.getMessage());
    }
  }

  @Test
  public void customColumnNameIsAcceptedWhenTypeIsText() throws Exception {
    stubTables(new String[][] { { "kapanis_nedeni", "NVARCHAR" } });
    campaign.setColResultCloseReason("kapanis_nedeni");

    assertTrue(checker.checkColumnsTypes(campaign));
  }

  /**
   * K18: binding boşken result tablosunda {@code close_reason} varsa otomatik atanır.
   */
  @Test
  public void unboundCloseReasonIsAutoMappedWhenDefaultColumnExists() throws Exception {
    stubTables(new String[][] { { "close_reason", "NVARCHAR" } });
    campaign.setColResultCloseReason(null);

    assertTrue(checker.checkColumnsTypes(campaign));
    assertEquals("Kolon varsa binding kendiliginden atanmalidir",
        "close_reason", campaign.getColResultCloseReason());
  }

  /**
   * Kolon yoksa binding boş kalır ve kampanya start'ı engellenmez; özellik çalışmaya devam eder,
   * yalnız kapanış nedeni kaydedilmez.
   */
  @Test
  public void missingCloseReasonColumnLeavesBindingEmptyAndDoesNotBlockStart() throws Exception {
    stubTables(new String[][] { { "some_other_column", "NVARCHAR" } });
    campaign.setColResultCloseReason(null);

    assertTrue(checker.checkColumnsTypes(campaign));
    assertNull(campaign.getColResultCloseReason());
  }

  /**
   * Operatör bilinçli olarak başka bir kolon seçtiyse otomatik atama onu EZMEMELİDİR.
   */
  @Test
  public void explicitBindingIsNotOverwrittenByAutoMapping() throws Exception {
    stubTables(new String[][] { { "close_reason", "NVARCHAR" }, { "kapanis_nedeni", "NVARCHAR" } });
    campaign.setColResultCloseReason("kapanis_nedeni");

    assertTrue(checker.checkColumnsTypes(campaign));
    assertEquals("kapanis_nedeni", campaign.getColResultCloseReason());
  }

  /**
   * Mevcut otomatik binding'ler (cb_history, call_process) bozulmamalıdır.
   */
  @Test
  public void existingHistoryAutoMappingsStillWork() throws Exception {
    stubTables(new String[][] { { "cb_history", "NVARCHAR" },
                                { "call_process", "NVARCHAR" },
                                { "close_reason", "NVARCHAR" } });

    assertTrue(checker.checkColumnsTypes(campaign));
    assertEquals("cb_history", campaign.getColResultCbHistory());
    assertEquals("call_process", campaign.getColResultCallProcess());
    assertEquals("close_reason", campaign.getColResultCloseReason());
  }

  // ---------------------------------------------------------------------
  // Faz 7 / P1.1 — otomatik binding TIP kontrolu yapmali
  // ---------------------------------------------------------------------

  /**
   * P1.1: müşterinin result/CRM tablosunda {@code close_reason} adında ama YANLIŞ tipte (INT)
   * bir kolon varsa, otomatik binding onu bağlamamalıdır. Bağlarsa hemen ardından çalışan
   * {@code checkColumn} {@code campaign.start.wrong.types} fırlatır ve o kampanya bu sürümden
   * sonra HİÇ BAŞLAMAZ — operatörün hiçbir seçimi olmadığı hâlde.
   *
   * <p>Beklenen: binding boş kalır, kampanya normal başlar, yalnız kapanış nedeni yazılmaz.</p>
   */
  @Test
  public void wrongTypedCloseReasonColumnIsNotAutoMappedAndDoesNotBlockStart() throws Exception {
    stubTables(new String[][] { { "close_reason", "INT" } });
    campaign.setColResultCloseReason(null);

    assertTrue("Yanlis tipli varsayilan kolon kampanya start'ini ENGELLEMEMELIDIR",
        checker.checkColumnsTypes(campaign));
    assertNull("Yanlis tipli kolon otomatik BAGLANMAMALIDIR", campaign.getColResultCloseReason());
  }

  /**
   * Aynı guard {@code cb_history} için de geçerli: bugün de yanlış tipli bir {@code cb_history}
   * kolonu kampanyayı başlatılamaz hâle getirebiliyor (bu iş bunu ortaya çıkardı, sebebi değil).
   */
  @Test
  public void wrongTypedCbHistoryColumnIsNotAutoMappedAndDoesNotBlockStart() throws Exception {
    stubTables(new String[][] { { "cb_history", "FLOAT" } });

    assertTrue(checker.checkColumnsTypes(campaign));
    assertNull(campaign.getColResultCbHistory());
  }

  /**
   * Aynı guard {@code call_process} için de geçerli.
   */
  @Test
  public void wrongTypedCallProcessColumnIsNotAutoMappedAndDoesNotBlockStart() throws Exception {
    stubTables(new String[][] { { "call_process", "BIGINT" } });

    assertTrue(checker.checkColumnsTypes(campaign));
    assertNull(campaign.getColResultCallProcess());
  }

  /**
   * Guard yalnız OTOMATİK binding'i zayıflatır; operatörün ELLE seçtiği yanlış tipli kolon
   * hâlâ reddedilir ({@code checkColumn} aynen durur). Yanlış konfigürasyonun sessizce yok
   * sayılması operatörü yanıltırdı.
   */
  @Test
  public void explicitWrongTypedBindingIsStillRejected() throws Exception {
    stubTables(new String[][] { { "kapanis_nedeni", "INT" } });
    campaign.setColResultCloseReason("kapanis_nedeni");

    try {
      checker.checkColumnsTypes(campaign);
      fail("Elle secilmis yanlis tipli kolon reddedilmelidir");
    } catch (CampaignIllegalStateException expected) {
      assertEquals("campaign.start.wrong.types", expected.getMessage());
    }
  }

  /**
   * Aynı tabloda hem yanlış tipli varsayılan kolon hem doğru tipli başka kolon varsa: otomatik
   * binding yine boş kalır (varsayılan ad dışındaki kolonları kendiliğinden seçmez — bu davranış
   * değişmedi), kampanya başlar.
   */
  @Test
  public void wrongTypedDefaultColumnDoesNotFallBackToAnotherColumn() throws Exception {
    stubTables(new String[][] { { "close_reason", "INT" }, { "kapanis_nedeni", "NVARCHAR" } });
    campaign.setColResultCloseReason(null);

    assertTrue(checker.checkColumnsTypes(campaign));
    assertNull(campaign.getColResultCloseReason());
  }

  /**
   * Tanımlanmamış tip (parse edilemeyen DB tipi) de bağlanmamalıdır: {@code addColumnType}
   * parse hatasında kolonu haritaya hiç koymaz, dolayısıyla tip {@code null} kalır.
   */
  @Test
  public void unparsableTypeIsNotAutoMapped() throws Exception {
    stubTables(new String[][] { { "close_reason", "SOME_EXOTIC_TYPE" } });
    campaign.setColResultCloseReason(null);

    assertTrue(checker.checkColumnsTypes(campaign));
    assertNull(campaign.getColResultCloseReason());
  }

}
