package tests.angular.campaign;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * FEAT-325559 — "Numara Başına Azami Arama" alanını açan checkbox (UI sözleşmesi).
 *
 * <p><b>İstek (28.09.2026):</b> kampanya yöneticileri alana rastgele değer girip davranışı
 * bozmasın; alan ancak bilinçli olarak etkinleştirildiğinde (checkbox) düzenlenebilsin.
 * "Reach retry limit before next number" (ordinal 1) ve "Reach retry limit for single number"
 * (ordinal 0, karar 29.09.2026) stratejilerinde checkbox da alan da her durumda pasif kalır.</p>
 *
 * <p><b>Tasarım:</b> checkbox yalnız ARAYÜZ durumudur; veritabanında kolonu yoktur. Tek kaynak
 * alanın kendisidir: değer doluysa özellik açıktır (backend {@code null} = kapalı sözleşmesi
 * değişmedi). Checkbox durumu yüklemede değerden türetilir, işaret kaldırılınca değer temizlenir
 * (özellik kapanır), işaretliyken alan zorunludur. Model controller'da ({@code campaign.}) durur,
 * {@code campaign.item}'a yazılmaz; böylece backend'e bilinmeyen bir alan gönderilmez ve bayrak
 * ile değer arasında iki ayrı doğruluk kaynağı oluşmaz.</p>
 *
 * <p>Karma spec'i ({@code campaignFormMaxAttemptPerNumber.spec.js}) {@code node_modules}
 * blocker'ı nedeniyle koşulamıyor; bu test aynı sözleşmeyi offline doğrular.</p>
 */
public class CampaignMaxAttemptPerNumberCheckboxUiContractTest {

  private static final String FORM = "src/main/webapp/pages/campaignForm.html";
  private static final String CONTROLLER = "src/main/webapp/js/controllers/campaignFormCtrl.js";
  private static final String CHECKBOX_ID = "id=\"campaign.maxAttemptPerNumberEnabled\"";
  private static final String INPUT_ID = "id=\"campaign.maxAttemptPerNumber\"";

  @Test
  public void checkboxExistsAndBindsToUiStateNotToCampaignItem() throws IOException {
    String checkbox = tagOf(readProjectFile(FORM), CHECKBOX_ID);

    assertTrue("Checkbox tipinde olmalı", checkbox.contains("type=\"checkbox\""));
    assertTrue("Model controller'daki UI durumu olmalı",
        checkbox.contains("ng-model=\"campaign.maxAttemptPerNumberEnabled\""));
    assertFalse("Bayrak campaign.item'a yazılmamalı (backend'e gitmez, iki doğruluk kaynağı olmaz)",
        readProjectFile(FORM).contains("campaign.item.maxAttemptPerNumberEnabled"));
  }

  @Test
  public void uncheckingTheBoxGoesThroughTheChangeHandler() throws IOException {
    String checkbox = tagOf(readProjectFile(FORM), CHECKBOX_ID);

    assertTrue(checkbox.contains("ng-change=\"campaign.onMaxAttemptPerNumberEnabledChange()\""));
  }

  /** Strateji 0 ve 1'de, tekrar arama kapalıyken ve yetki yokken checkbox da pasif. */
  @Test
  public void checkboxIsDisabledUnderTheSameConditionsAsTheField() throws IOException {
    String checkbox = tagOf(readProjectFile(FORM), CHECKBOX_ID);

    assertTrue(checkbox.contains("!campaign.item.retryScheduleEnabled"));
    assertTrue("Reach retry limit before next number'da her durumda pasif",
        checkbox.contains("campaign.item.retryStrategy == 1"));
    assertTrue("Reach retry limit for single number'da her durumda pasif",
        checkbox.contains("campaign.item.retryStrategy == 0"));
    assertTrue(checkbox.contains("canEditSelectedCampaignByPermission(campaign.item, 'EditCampaignTech')"));
  }

  /** Alan yalnız checkbox işaretliyken aktif; strateji 1 kilidi aynen duruyor. */
  @Test
  public void fieldIsEnabledOnlyWhenCheckboxIsChecked() throws IOException {
    String input = tagOf(readProjectFile(FORM), INPUT_ID);

    assertTrue("Checkbox işaretli değilken alan pasif olmalı",
        input.contains("!campaign.maxAttemptPerNumberEnabled"));
    assertTrue("Strateji 1 kilidi korunmalı", input.contains("campaign.item.retryStrategy == 1"));
    assertTrue("Strateji 0 kilidi olmalı", input.contains("campaign.item.retryStrategy == 0"));
    assertTrue(input.contains("!campaign.item.retryScheduleEnabled"));
  }

  /** İşaretliyken değer zorunlu: "işaretli ama boş" ile özellik açık sanılıp kaydedilmesin. */
  @Test
  public void fieldIsRequiredWhileCheckboxIsChecked() throws IOException {
    String input = tagOf(readProjectFile(FORM), INPUT_ID);

    int required = input.indexOf("ng-required=\"");
    assertTrue("Alan ng-required taşımalı", required >= 0);
    String expression = input.substring(required, input.indexOf('"', required + "ng-required=\"".length()));
    assertTrue(expression.contains("campaign.maxAttemptPerNumberEnabled"));
    assertTrue("Strateji 1'de zorunlu olmamalı", expression.contains("campaign.item.retryStrategy != 1"));
    assertTrue("Strateji 0'da zorunlu olmamalı", expression.contains("campaign.item.retryStrategy != 0"));
  }

  /** Muaf stratejilerin ikisinde de alanın neden kapalı olduğunu anlatan metin görünür. */
  @Test
  public void frozenStrategyHelpTextIsShownForBothExemptStrategies() throws IOException {
    String form = readProjectFile(FORM);
    int help = form.indexOf("'campaign.maxAttemptPerNumber.frozenForStrategy'");
    assertTrue("Açıklama metni bulunamadı", help >= 0);
    String helpTag = form.substring(form.lastIndexOf('<', help), help);

    assertTrue("Strateji 1'de görünmeli", helpTag.contains("campaign.item.retryStrategy == 1"));
    assertTrue("Strateji 0'da görünmeli", helpTag.contains("campaign.item.retryStrategy == 0"));
  }

  @Test
  public void checkboxComesBeforeTheField() throws IOException {
    String form = readProjectFile(FORM);

    assertTrue(form.indexOf(CHECKBOX_ID) >= 0);
    assertTrue("Checkbox alandan önce gelmeli", form.indexOf(CHECKBOX_ID) < form.indexOf(INPUT_ID));
  }

  /**
   * Alan, kendisini açan checkbox'ın hemen altında ve AYNI kolonda durur (istek 28.09.2026):
   * ikisi arasında yeni bir kolon veya satır açılmaz; kullanıcı checkbox ile alanı tek bir
   * grup olarak görür.
   */
  @Test
  public void fieldSitsUnderTheCheckboxInTheSameColumn() throws IOException {
    String form = readProjectFile(FORM);
    String between = form.substring(form.indexOf(CHECKBOX_ID), form.indexOf(INPUT_ID));

    assertFalse("Checkbox ile alan arasında yeni kolon açılmamalı", between.contains("class=\"col-"));
    assertFalse("Checkbox ile alan arasında yeni satır açılmamalı", between.contains("class=\"row\""));
  }

  /** Durum değerden türetilir (yeni kampanya + düzenleme için sunucudan yükleme). */
  @Test
  public void controllerDerivesCheckboxStateFromTheValueOnEveryLoad() throws IOException {
    String controller = readProjectFile(CONTROLLER);

    assertTrue("Türetme fonksiyonu tanımlı olmalı",
        controller.contains("vm.syncMaxAttemptPerNumberEnabled = function()"));
    assertTrue("Durum değerin doluluğundan türetilmeli",
        controller.contains("vm.maxAttemptPerNumberEnabled = vm.item.maxAttemptPerNumber != null"));
    int calls = countOccurrences(controller, "vm.syncMaxAttemptPerNumberEnabled();");
    assertTrue("Yeni kampanya ve düzenleme yüklemesinde çağrılmalı (bulunan: " + calls + ")", calls >= 2);
  }

  /** İşaret kaldırılınca değer temizlenir: özellik kapanır (backend: null = kapalı). */
  @Test
  public void uncheckingClearsTheValue() throws IOException {
    String controller = readProjectFile(CONTROLLER);

    int handler = controller.indexOf("vm.onMaxAttemptPerNumberEnabledChange = function()");
    assertTrue("Değişim işleyicisi tanımlı olmalı", handler >= 0);
    String body = controller.substring(handler, controller.indexOf("};", handler));
    assertTrue(body.contains("vm.item.maxAttemptPerNumber = null"));
  }

  @Test
  public void everyLanguageHasTheCheckboxLabel() throws IOException {
    for (String language : new String[] { "en", "tr", "ru", "ua" }) {
      String localization = readProjectFile("src/main/webapp/js/localization/localization_" + language + ".js");
      assertTrue(language + " dilinde checkbox etiketi olmalı",
          localization.contains("'campaign.maxAttemptPerNumberEnabled'"));
    }
  }

  /** Strateji değişimi işleyiciden geçer: muaf stratejiye geçişte limit sıfırlanabilsin (333220). */
  @Test
  public void retryStrategySelectGoesThroughTheChangeHandler() throws IOException {
    String select = openingTagOf(readProjectFile(FORM), "id=\"campaign.retryStrategy\"");

    assertTrue("Strateji alanı select olmalı", select.startsWith("<select"));
    assertTrue(select.contains("ng-change=\"campaign.onRetryStrategyChange()\""));
  }

  /** Numara başına limitin genel limitten geldiği (muaf) stratejiler: 0 ve 1. */
  @Test
  public void singleNumberAndBeforeNextNumberStrategiesAreExempt() throws IOException {
    String controller = readProjectFile(CONTROLLER);

    int function = controller.indexOf("vm.isPerNumberLimitFromOverall = function()");
    assertTrue("Muaf strateji fonksiyonu tanımlı olmalı", function >= 0);
    String body = controller.substring(function, controller.indexOf("};", function));
    assertTrue("Reach retry limit for single number (0) muaf olmalı",
        body.contains("vm.item.retryStrategy === 0"));
    assertTrue("Reach retry limit before next number (1) muaf olmalı",
        body.contains("vm.item.retryStrategy === 1"));
  }

  /** Muaf stratejiye geçilince değer temizlenir ve checkbox işareti kalkar (backend: null = kapalı). */
  @Test
  public void switchingToAnExemptStrategyClearsTheValueAndUnchecksTheBox() throws IOException {
    String controller = readProjectFile(CONTROLLER);

    int handler = controller.indexOf("vm.onRetryStrategyChange = function()");
    assertTrue("Strateji değişim işleyicisi tanımlı olmalı", handler >= 0);
    String body = controller.substring(handler, controller.indexOf("};", handler));
    int guard = body.indexOf("if (vm.isPerNumberLimitFromOverall())");
    assertTrue("Temizlik yalnız muaf stratejilerde yapılmalı", guard >= 0);
    String guarded = body.substring(guard);
    assertTrue("Değer temizlenmeli", guarded.contains("vm.item.maxAttemptPerNumber = null"));
    assertTrue("Checkbox işareti kalkmalı", guarded.contains("vm.maxAttemptPerNumberEnabled = false"));
  }

  /**
   * Düzenleme yüklemesinde muaf stratejide kayıtlı değer temizlenir. Temizlik
   * {@code vm.item = data;} satırından sonra ve {@code vm.itemInitial} kopyasından önce yapılır:
   * {@code itemInitial} değişiklik tespitinin (admin log) referansıdır; temizlik kopyadan sonra
   * yapılsaydı form hiç dokunulmadan değişmiş sayılırdı.
   */
  @Test
  public void editLoadClearsTheValueForExemptStrategiesBeforeTheInitialSnapshot() throws IOException {
    String controller = readProjectFile(CONTROLLER);

    int get = controller.indexOf("campaignFactory.get(");
    assertTrue("Düzenleme yüklemesi bulunamadı", get >= 0);
    int then = controller.indexOf(".then(", get);
    assertTrue("Düzenleme yüklemesinin then bloğu bulunamadı", then >= 0);
    String block = controller.substring(then, controller.indexOf("});", then));

    int assign = block.indexOf("vm.item = data;");
    int snapshot = block.indexOf("vm.itemInitial = angular.copy(data);");
    assertTrue("vm.item = data; satırı bulunamadı", assign >= 0);
    assertTrue("vm.itemInitial = angular.copy(data); satırı bulunamadı", snapshot >= 0);
    int guard = block.indexOf("if (vm.isPerNumberLimitFromOverall())", assign);
    assertTrue("Temizlik muaf strateji koşuluna bağlı olmalı ve vm.item = data;'dan sonra gelmeli",
        guard > assign);
    int clear = block.indexOf("vm.item.maxAttemptPerNumber = null", guard);
    assertTrue("Değer koşulun içinde temizlenmeli", clear > guard);
    assertTrue("Temizlik vm.itemInitial kopyasından önce yapılmalı", clear < snapshot);
  }

  // ---------------------------------------------------------------------

  /** {@code <input ... id="..." ... />} etiketinin tamamını döndürür. */
  private String tagOf(String content, String idAttribute) {
    int id = content.indexOf(idAttribute);
    assertTrue("Eleman bulunamadı: " + idAttribute, id >= 0);
    int start = content.lastIndexOf('<', id);
    int end = content.indexOf("/>", id);
    assertTrue("Etiket kapanışı bulunamadı: " + idAttribute, start >= 0 && end > id);
    return content.substring(start, end + 2);
  }

  /**
   * {@code />} ile kapanmayan {@code <select ... id="..." ...>} gibi açılış etiketini döndürür;
   * tırnak içindeki {@code >} etiket sonu sayılmaz.
   */
  private String openingTagOf(String content, String idAttribute) {
    int id = content.indexOf(idAttribute);
    assertTrue("Eleman bulunamadı: " + idAttribute, id >= 0);
    int start = content.lastIndexOf('<', id);
    assertTrue("Etiket başlangıcı bulunamadı: " + idAttribute, start >= 0);
    int end = -1;
    boolean quoted = false;
    for (int i = start; i < content.length() && end < 0; i++) {
      char c = content.charAt(i);
      if (c == '"') {
        quoted = !quoted;
      } else if (c == '>' && !quoted) {
        end = i;
      }
    }
    assertTrue("Etiket kapanışı bulunamadı: " + idAttribute, end > id);
    return content.substring(start, end + 1);
  }

  private int countOccurrences(String content, String needle) {
    int count = 0;
    for (int from = content.indexOf(needle); from >= 0; from = content.indexOf(needle, from + needle.length())) {
      count++;
    }
    return count;
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
