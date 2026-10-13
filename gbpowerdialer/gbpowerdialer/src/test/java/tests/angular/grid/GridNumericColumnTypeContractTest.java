package tests.angular.grid;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * Task 324856: Grid'lerde sayisal kolonlar alfabetik siraliyordu (bildirilen
 * semptom: Campaign Form > List Source > Phone Numbers > Sort, asc 0,1,10,11,12).
 *
 * ui-grid 3.2.6 tipi bildirilmemis kolonlar icin tahmin yapar: GridOptions
 * .initialize gridOptions.data'yi [] yapar, ilk data watch turu
 * Grid.modifyRows([]) calistirir ve modifyRows sonundaki kosulsuz assignTypes()
 * satir olmadigi icin tipi kalici olarak "string" damgalar. if (!colDef.type)
 * guard'i yuzunden gercek veri geldiginde tip yeniden hesaplanmaz.
 *
 * Ek olarak rowSorter.colSortFnCache anahtari colDef.name'dir ve servis
 * singleton'inda tutulur; ayni adi tasiyan sayisal kolonlar (ozellikle "id")
 * uygulama genelinde birlikte tiplendirilmelidir, aksi halde tiplendirilmemis
 * bir grid once acilirsa cache'e sortAlpha yazip digerini de bozar.
 */
public class GridNumericColumnTypeContractTest {

  private static final String CONTROLLERS = "src/main/webapp/js/controllers/";

  private static final String NUMERIC_TYPE = "type : 'number'";

  @Test
  public void phoneNumberColumnGridDeclaresNumericTypes() throws IOException {
    assertNumeric("phoneNumberColumnCtrl.js", "id", "colSort");
  }

  @Test
  public void campaignGroupGridDeclaresNumericTypes() throws IOException {
    assertNumeric("campaignGroupCtrl.js", "id", "priority");
  }

  @Test
  public void resultCodeScheduleGridDeclaresNumericTypes() throws IOException {
    assertNumeric("resultCodeScheduleCtrl.js", "id", "retryAmount", "retryInterval");
  }

  @Test
  public void resultCodeScheduleAdvancedGridDeclaresNumericTypes() throws IOException {
    assertNumeric("resultCodeScheduleAdvancedCtrl.js", "id", "dialAttempt", "retryInterval", "numberToTry");
  }

  @Test
  public void filterColumnGridDeclaresNumericTypes() throws IOException {
    assertNumeric("filterColumnCtrl.js", "id", "ratio");
  }

  @Test
  public void remainingGridsDeclareNumericIdType() throws IOException {
    assertNumeric("attachedDataColumnCtrl.js", "id");
    assertNumeric("campaignScheduleAutomationCtrl.js", "id");
    assertNumeric("adminLogsDetailsCtrl.js", "id");
  }

  /**
   * Bilincli kapsam disi kolonlar sayisal tip ALMAMALI: applyTo i18n etiketli
   * enum, min/maxScheduleTime Date (hucrede date:"HH:mm"), macroCycle ve enabled
   * boolean, geri kalanlar string.
   */
  @Test
  public void deliberatelyExcludedColumnsDoNotDeclareNumericType() throws IOException {
    assertNotNumeric("resultCodeScheduleCtrl.js", "applyTo", "minScheduleTime", "maxScheduleTime", "macroCycle");
    assertNotNumeric("phoneNumberColumnCtrl.js", "colNumber", "colCallerId", "colTimezoneOffset", "enabled");
  }

  /**
   * dncPhoneCtrl useExternalSorting : true kullanir; rowSorter.sort external
   * sorting'de getSortFn'e hic ulasmadan dondugu icin ne yanlis siralar ne de
   * paylasilan colSortFnCache'i kirletir. Bu varsayim bozulursa test kirilir.
   */
  @Test
  public void externalSortingGridStaysOutOfClientSideSorting() throws IOException {
    assertTrue("dncPhoneCtrl client-side siralamaya donerse 'id' kolonu da tiplendirilmelidir",
        readProjectFile(CONTROLLERS + "dncPhoneCtrl.js").contains("useExternalSorting : true"));
  }

  /**
   * sortFieldCtrl / sortFieldValueCtrl bu repo'daki referans desendir; dogru
   * siraladiklari icin karsi kanit olarak kullanildilar, korunmalilar.
   */
  @Test
  public void sortConditionsGridsKeepNumericTypeConvention() throws IOException {
    assertNumeric("sortFieldCtrl.js", "id", "priority");
    assertNumeric("sortFieldValueCtrl.js", "id", "priority");
  }

  private void assertNumeric(String file, String... fields) throws IOException {
    String content = readProjectFile(CONTROLLERS + file);
    for (String field : fields) {
      assertTrue(file + " -> " + field + " columnDef'i " + NUMERIC_TYPE + " bildirmeli",
          columnDefBlock(content, field, file).contains(NUMERIC_TYPE));
    }
  }

  private void assertNotNumeric(String file, String... fields) throws IOException {
    String content = readProjectFile(CONTROLLERS + file);
    for (String field : fields) {
      assertFalse(file + " -> " + field + " columnDef'i sayisal tip almamali",
          columnDefBlock(content, field, file).contains(NUMERIC_TYPE));
    }
  }

  /**
   * columnDef'ler dosyalarda "}, {" ile ayrilir ve dizi "columnDefs : [ {" ile
   * baslar. Aranan alanin bulundugu tek columnDef blogunu dondurur.
   */
  private String columnDefBlock(String content, String field, String file) {
    int fieldIndex = content.indexOf("field : '" + field + "'");
    assertTrue("columnDef bulunamadi: " + file + " -> " + field, fieldIndex >= 0);

    int start = Math.max(content.lastIndexOf("}, {", fieldIndex), content.lastIndexOf("columnDefs", fieldIndex));
    if (start < 0) {
      start = 0;
    }
    int end = content.indexOf("}, {", fieldIndex);
    if (end < 0) {
      end = content.length();
    }
    return content.substring(start, end);
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
