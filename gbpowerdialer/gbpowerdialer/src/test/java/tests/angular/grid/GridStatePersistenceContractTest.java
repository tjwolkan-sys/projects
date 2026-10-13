package tests.angular.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Task 324857: Grid filtre ve kolon gorunurluk tercihleri sayfa degisiminde
 * sifirlaniyordu.
 *
 * Kok neden: grid state persistence'inin siralama sozlesmesi hicbir yerde
 * sahiplenilmemisti. Controller'lar save handler'larini onRegisterApi icinde
 * (grid kurulumunun en basinda) canliya aliyor, gridStateService.restore ise
 * $timeout(..., 10) ile sonraki tick'e erteleniyordu. Bu iki an arasinda
 * ui-grid'in kendi kurulum event'leri (ornek: select filtre template'indeki
 * ng-init colFilter.term = '') save handler'larini tetikliyor ve kullanicinin
 * localStorage kaydini varsayilan state ile eziyordu; restore da kendi yok
 * ettigi degeri okuyordu.
 *
 * Bu test iki degismezi kilitler:
 *   1. gridStateService, bir grid instance'i icin restore tamamlanmadan
 *      otomatik save'leri kalici yazmaz (gate). Gate atlanamasin diye save
 *      artik serilestirilmis state degil gridApi alir.
 *   2. Select filtre template'lerindeki ng-init, zaten atanmis bir filtre
 *      terimini ezmez.
 */
public class GridStatePersistenceContractTest {

  private static final String SERVICE = "src/main/webapp/js/services/gridStateService.js";

  /** gridStateService kullanan tum grid ekranlari ve dosya basina beklenen save cagrisi sayisi. */
  private static final Map<String, Integer> STATE_GRID_CONTROLLERS = new LinkedHashMap<String, Integer>();

  /** Select filtre template'i bulunan controller'lar ve dosya basina ng-init sayisi. */
  private static final Map<String, Integer> SELECT_FILTER_CONTROLLERS = new LinkedHashMap<String, Integer>();

  static {
    STATE_GRID_CONTROLLERS.put("actionLogCacheCtrl.js", 4);
    STATE_GRID_CONTROLLERS.put("actionLogCtrl.js", 4);
    STATE_GRID_CONTROLLERS.put("adminLogsCtrl.js", 4);
    STATE_GRID_CONTROLLERS.put("campaignCtrl.js", 4);
    STATE_GRID_CONTROLLERS.put("dncCtrl.js", 4);
    STATE_GRID_CONTROLLERS.put("locationCtrl.js", 4);
    STATE_GRID_CONTROLLERS.put("lscCtrl.js", 4);
    STATE_GRID_CONTROLLERS.put("serverCtrl.js", 4);
    STATE_GRID_CONTROLLERS.put("serviceStatusesCtrl.js", 4);
    STATE_GRID_CONTROLLERS.put("telephonyRcCtrl.js", 4);

    SELECT_FILTER_CONTROLLERS.put("actionLogCtrl.js", 2);
    SELECT_FILTER_CONTROLLERS.put("adminLogsCtrl.js", 2);
    SELECT_FILTER_CONTROLLERS.put("attachedDataColumnCtrl.js", 3);
    SELECT_FILTER_CONTROLLERS.put("campaignCtrl.js", 2);
    SELECT_FILTER_CONTROLLERS.put("campaignGroupCtrl.js", 1);
    SELECT_FILTER_CONTROLLERS.put("campaignScheduleAutomationCtrl.js", 1);
    SELECT_FILTER_CONTROLLERS.put("dncCtrl.js", 2);
    SELECT_FILTER_CONTROLLERS.put("dncPhoneCtrl.js", 2);
    SELECT_FILTER_CONTROLLERS.put("filterColumnCtrl.js", 6);
    SELECT_FILTER_CONTROLLERS.put("lscCtrl.js", 4);
    SELECT_FILTER_CONTROLLERS.put("phoneNumberColumnCtrl.js", 1);
    SELECT_FILTER_CONTROLLERS.put("serverCtrl.js", 3);
  }

  private static final String RESTORED_FLAG = "gbpdGridStateRestored";

  private static final String OLD_NG_INIT = "ng-init=\"colFilter.term = \\'\\'\"";
  private static final String GUARDED_NG_INIT =
      "ng-init=\"colFilter.term = colFilter.term == null ? \\'\\' : colFilter.term\"";

  // --- 1. gridStateService gate ---

  @Test
  public void restoreMarksGridAsRestoredOnlyAfterUiGridRestoreCompletes() throws IOException {
    String service = readProjectFile(SERVICE);

    int uiGridRestore = indexOfRequired(service, "gridApi.saveState.restore(controllerScope, gridState)");
    int flagAssignment = indexOfRequired(service, RESTORED_FLAG + " = true");

    assertTrue("restore bayragi ui-grid restore'u tamamlanmadan set edilmemeli"
        + " (restoreColumns kolon dongusunun icinde filterChanged yayinliyor)",
        uiGridRestore < flagAssignment);
  }

  @Test
  public void saveIsBlockedUntilGridStateIsRestored() throws IOException {
    String service = readProjectFile(SERVICE);
    String save = methodBody(service, "save : function(");

    assertTrue("save, restore bayragini kontrol etmeli", save.contains(RESTORED_FLAG));
    assertTrue("save, bayrak yokken erken donmeli", save.contains("return;"));

    int guard = indexOfRequired(save, RESTORED_FLAG);
    int write = indexOfRequired(save, "localStorageService.set(");
    assertTrue("gate localStorage yazmasindan once gelmeli", guard < write);

    int earlyReturn = indexOfRequired(save, "return;");
    assertTrue("erken donus localStorage yazmasindan once gelmeli", earlyReturn < write);

    assertTrue("save, state'i kendisi serilestirmeli", save.contains("gridApi.saveState.save()"));
  }

  @Test
  public void saveTakesGridApiSoTheGateCannotBeBypassed() throws IOException {
    String service = readProjectFile(SERVICE);

    assertTrue("save imzasi gridApi almali", service.contains("save : function(stateName, gridApi)"));
    assertTrue("saveDefaultStateIfNotExist imzasi gridApi almali",
        service.contains("saveDefaultStateIfNotExist : function(defaultStateName, gridApi)"));
  }

  @Test
  public void resetWritesDefaultStateDirectlyAndThenRestores() throws IOException {
    String service = readProjectFile(SERVICE);
    String reset = methodBody(service, "reset : function(");

    assertFalse("reset, gate'e tabi save'i kullanmamali; elindeki state zaten serilestirilmis default state",
        reset.contains("this.save("));
    assertTrue(reset.contains("localStorageService.set(regularStateName"));
    assertTrue(reset.contains("this.restore(regularStateName, gridApi, controllerScope)"));
  }

  @Test
  public void gridStateServiceIntroducesNoSharedMutableState() throws IOException {
    String service = readProjectFile(SERVICE);

    assertFalse("state bayragi grid instance'inda tutulmali, servis singleton'inda paylasilan map'te degil",
        service.contains("restoredStates"));
    assertTrue("bayrak grid nesnesinde tutulmali", service.contains("gridApi.grid"));
  }

  // --- 2. Controller cagri siteleri ---

  @Test
  public void noControllerPassesAPreSerializedStateToGridStateService() throws IOException {
    for (String controller : STATE_GRID_CONTROLLERS.keySet()) {
      String source = readController(controller);

      assertFalse(controller + " eski imzayi kullaniyor: gridStateService.save(..., saveState.save())",
          source.contains("gridStateService.save(vm.regularGridStateName, vm.gridApi.saveState.save())"));
      assertFalse(controller + " eski imzayi kullaniyor: saveDefaultStateIfNotExist(..., saveState.save())",
          source.contains("gridStateService.saveDefaultStateIfNotExist(vm.defaultGridStateName, vm.gridApi.saveState.save())"));
    }
  }

  @Test
  public void everyStateGridControllerUsesTheGatedSaveSignature() throws IOException {
    for (Map.Entry<String, Integer> entry : STATE_GRID_CONTROLLERS.entrySet()) {
      String controller = entry.getKey();
      String source = readController(controller);

      assertEquals(controller + " icindeki gated save cagrisi sayisi",
          entry.getValue().intValue(),
          countOccurrences(source, "gridStateService.save(vm.regularGridStateName, vm.gridApi)"));

      assertEquals(controller + " icindeki saveDefaultStateIfNotExist cagrisi sayisi", 1,
          countOccurrences(source, "gridStateService.saveDefaultStateIfNotExist(vm.defaultGridStateName, vm.gridApi)"));
    }
  }

  @Test
  public void everyStateGridControllerRestoresItsStateSoTheGateCanOpen() throws IOException {
    for (String controller : STATE_GRID_CONTROLLERS.keySet()) {
      String source = readController(controller);

      assertTrue(controller + " restore cagirmali; aksi halde gate hic acilmaz ve state hic kaydedilmez",
          source.contains("gridStateService.restore(vm.regularGridStateName, vm.gridApi, vm)"));
      assertTrue(controller + " reset menu ogesi korunmali",
          source.contains("gridStateService.reset(vm.defaultGridStateName, vm.regularGridStateName, vm.gridApi, vm)"));
    }
  }

  // --- 3. Select filtre template'leri ---

  @Test
  public void selectFilterTemplatesDoNotOverwriteAnAlreadyAssignedTerm() throws IOException {
    for (Map.Entry<String, Integer> entry : SELECT_FILTER_CONTROLLERS.entrySet()) {
      String controller = entry.getKey();
      String source = readController(controller);

      assertEquals(controller + " icinde korumasiz ng-init kalmamali", 0,
          countOccurrences(source, OLD_NG_INIT));
      assertEquals(controller + " icindeki korumali ng-init sayisi",
          entry.getValue().intValue(),
          countOccurrences(source, GUARDED_NG_INIT));
    }
  }

  @Test
  public void guardedNgInitStillDefaultsUnsetTermsToTheEmptySelectOption() throws IOException {
    // Gevsek == null hem undefined hem null yakalar; 0 / false / '' gibi gecerli
    // secim degerlerine dokunmaz. enabled filtresi true/false degerleri kullandigi
    // icin katiyen === undefined kullanilmamalidir.
    assertTrue(GUARDED_NG_INIT.contains("colFilter.term == null"));
    assertFalse(GUARDED_NG_INIT.contains("=== undefined"));

    String campaign = readController("campaignCtrl.js");
    assertTrue("enabled filtresi bolean select degerleri kullaniyor",
        campaign.contains("value : true") && campaign.contains("value : false"));
  }

  @Test
  public void noSelectFilterTemplateRemainsUnguardedAcrossTheWholeApplication() throws IOException {
    List<String> stray = Arrays.asList();
    Path controllersDir = resolveProjectPath("src/main/webapp/js/controllers");

    for (Path file : Files.newDirectoryStream(controllersDir, "*.js")) {
      String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      assertEquals(file.getFileName() + " icinde korumasiz ng-init kaldi", 0,
          countOccurrences(source, OLD_NG_INIT));
    }
    assertTrue(stray.isEmpty());
  }

  // --- yardimcilar ---

  private String methodBody(String service, String signature) {
    int start = indexOfRequired(service, signature);
    int end = service.indexOf("\n    },", start);
    if (end < 0) {
      end = service.length();
    }
    return service.substring(start, end);
  }

  private int countOccurrences(String content, String needle) {
    int count = 0;
    int index = content.indexOf(needle);
    while (index >= 0) {
      count++;
      index = content.indexOf(needle, index + needle.length());
    }
    return count;
  }

  private int indexOfRequired(String content, String text) {
    int index = content.indexOf(text);
    assertTrue("Missing expected text: " + text, index >= 0);
    return index;
  }

  private String readController(String fileName) throws IOException {
    return readProjectFile("src/main/webapp/js/controllers/" + fileName);
  }

  private String readProjectFile(String relativePath) throws IOException {
    return new String(Files.readAllBytes(resolveProjectPath(relativePath)), StandardCharsets.UTF_8);
  }

  private Path resolveProjectPath(String relativePath) {
    Path path = Paths.get(relativePath);
    if (!Files.exists(path)) {
      path = Paths.get("gbpowerdialer").resolve(relativePath);
    }
    assertTrue("Missing project file: " + relativePath, Files.exists(path));
    return path;
  }
}
