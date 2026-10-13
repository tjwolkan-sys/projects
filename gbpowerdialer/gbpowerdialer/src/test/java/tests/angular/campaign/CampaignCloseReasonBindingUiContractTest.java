package tests.angular.campaign;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * FEAT-325559 Faz 7 / P3.1 — {@code close_reason} binding'inin AngularJS sihirbaz akislarindaki
 * TUTARLILIGI.
 *
 * <p><b>Bulgu:</b> Faz 2'de yalnizca {@code setDefaultValuesForUndefinedResultTableColumns}
 * guncellenmisti. Kolon backend'de olusuyor ({@code createDefaultResultTable} ve
 * {@code addDefaultResultColumnsToSourceTable} ikisi de {@code close_reason} iceriyor) ve binding
 * kampanya start'inda otomatik ataniyor; yani islevsel kayip yoktu. Ama sihirbaz akisinda
 * operator binding'i BOS goruyor ve kolonu olusturulacak-kolon dropdown'inda BULAMIYORDU.</p>
 *
 * <p>Bu test dort akisin da {@code close_reason} tasidigini kilitler:</p>
 *
 * <ol>
 * <li>result tablosu secildiginde default binding'ler ({@code campaignFormCtrl})</li>
 * <li>tanimsiz result kolonlari icin default atama ({@code campaignFormCtrl}, Faz 2'de yapildi)</li>
 * <li>kaynak tabloya result kolonlarinin eklenmesi ({@code addResultColsToSourceTable})</li>
 * <li>yeni result tablosu olusturma ({@code dbTableFormCtrl})</li>
 * </ol>
 *
 * <p>Ayrica iki PICKLIST (olusturulacak kolon listeleri) kontrol edilir.</p>
 *
 * <p><b>Not:</b> bu sinif Java'dan JS/HTML dosyalarini METIN olarak dogrular; repodaki
 * {@code tests.angular.*} deseninin aynisi. Karma/Jasmine spec'i ayrica
 * {@code src/test/angular/forms/campaignFormMaxAttemptPerNumber.spec.js} icinde durur ve
 * {@code node_modules} blocker'i nedeniyle KOSULAMIYOR; bu test o bosluğu offline kapatir.</p>
 */
public class CampaignCloseReasonBindingUiContractTest {

  private static final String CONTROLLER = "src/main/webapp/js/controllers/campaignFormCtrl.js";
  private static final String DB_TABLE_CONTROLLER = "src/main/webapp/js/controllers/dbTableFormCtrl.js";
  private static final String CONSTANTS = "src/main/webapp/js/constants.js";

  /**
   * Akis 1 + 2: iki ayri noktada da {@code setDefaultValueForResultColumn('close_reason')}
   * cagrilmalidir. Faz 2 yalnizca birini yapmisti.
   */
  @Test
  public void bothDefaultValueFlowsShouldBindCloseReason() throws IOException {
    String controller = readProjectFile(CONTROLLER);

    int occurrences = countOccurrences(controller, "setDefaultValueForResultColumn('close_reason')");
    assertTrue("close_reason iki default-binding akisinda da atanmali (bulunan: " + occurrences + ")",
        occurrences >= 2);
  }

  /**
   * Akis 3: kaynak tabloya result kolonlari eklendiginde binding sabit degerden atanir.
   */
  @Test
  public void addResultColsToSourceTableShouldBindCloseReason() throws IOException {
    String controller = readProjectFile(CONTROLLER);

    assertTrue("addResultColsToSourceTable close_reason binding'ini atamali",
        controller.contains("vm.item.colResultCloseReason = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON"));
  }

  /**
   * Akis 3'un picklist'i: olusturulacak kolonlar listesinde {@code close_reason} yer almali,
   * yoksa operator dropdown'da kolonu bulamaz.
   */
  @Test
  public void addResultColsPicklistShouldOfferCloseReason() throws IOException {
    String controller = readProjectFile(CONTROLLER);

    int push = controller.indexOf("vm.callResultColumnsToCreate.push");
    assertTrue("callResultColumnsToCreate picklist'i bulunmali", push >= 0);
    assertTrue("Picklist close_reason icermeli",
        controller.contains("fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON"));
  }

  /**
   * Akis 4: yeni result tablosu olusturulurken hem binding atanir hem kolon olusturulacaklar
   * listesine girer.
   */
  @Test
  public void createResultTableFlowShouldBindAndCreateCloseReason() throws IOException {
    String dbTableController = readProjectFile(DB_TABLE_CONTROLLER);

    assertTrue("Yeni result tablosu akisi close_reason binding'ini atamali",
        dbTableController.contains("campaign.colResultCloseReason = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON"));
    assertTrue("Yeni result tablosu akisi close_reason kolonunu olusturmali",
        dbTableController.contains("addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON)"));
  }

  /**
   * Sabit ve izinli tipler Faz 2'de eklendi; regresyona karsi kilitlenir. Tip listesi
   * {@code CampaignDataTypeChecker}'in otomatik binding guard'i ile ayni aileyi (metin tipleri)
   * tanimlamali.
   */
  @Test
  public void constantsShouldDeclareCloseReasonAndItsAllowedTypes() throws IOException {
    String constants = readProjectFile(CONSTANTS);

    assertTrue("CLOSE_REASON sabiti tanimli olmali",
        constants.contains("CLOSE_REASON : 'close_reason'"));
    int allowed = constants.indexOf("RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON]");
    assertTrue("close_reason icin izinli tipler tanimli olmali", allowed >= 0);
    String line = constants.substring(allowed, constants.indexOf(";", allowed));
    assertTrue("Izinli tipler metin tipleri olmali", line.contains("NVARCHAR"));
    assertTrue("Izinli tipler metin tipleri olmali", line.contains("VARCHAR"));
  }

  /**
   * Binding'in ekranda secilebilir oldugu Faz 2'de eklendi; akislarin tamamlanmasi bu select'i
   * gecersiz kilmaz (operator hala bilincli baska bir kolon secebilir).
   */
  @Test
  public void campaignFormShouldStillOfferManualCloseReasonSelect() throws IOException {
    String campaignForm = readProjectFile("src/main/webapp/pages/campaignForm.html");

    assertTrue("colResultCloseReason select'i formda kalmali",
        campaignForm.contains("campaign.item.colResultCloseReason"));
  }

  private int countOccurrences(String content, String needle) {
    int count = 0;
    int from = 0;
    while (true) {
      int index = content.indexOf(needle, from);
      if (index < 0) {
        return count;
      }
      count++;
      from = index + needle.length();
    }
  }

  private String readProjectFile(String relativePath) throws IOException {
    Path path = Paths.get(relativePath);
    if (!Files.exists(path)) {
      path = Paths.get("gbpowerdialer").resolve(relativePath);
    }
    assertTrue("Missing project file: " + relativePath, Files.exists(path));
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }
}
