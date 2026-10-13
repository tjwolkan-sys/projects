package tests.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.util.Constants;

/**
 * FEAT-325470 veri contract'i: pd_telephony_rc migration script'indeki uc yeni satir
 * (id 86/87/88, code 113/114/115), {@link Constants} code sabitleri, AMD ailesi kumesi ve
 * startup dogrulamasinin zorunlu kod listesi birbirine kilitlenir.
 *
 * <p>Dialer haritasi {@code pd_telephony_rc.code} ile anahtarlanir; santral DialerHangup cause
 * olarak bu code'lari gonderir. Script ile sabitler ayri duserse hangup'lar 901 fallback'ine
 * (Internal Error) duser ve bad-number kurali hic tetiklenmez. Bu test o ayrismayi derleme/CI
 * aninda yakalar.</p>
 *
 * <p>05.10.2026 revizyonu: ilk surumde code'lar 913/914/915 idi; santral karariyla 113/114/115
 * oldu. Script ilk surumle yazilmis satirlari da (PRP ve S2 test ortamlari) yeni koda tasimak
 * zorundadir; bu yuzden UPDATE adimlari ve iki yonlu cakisma korumasi da kilitlenir.</p>
 */
public class TelephonyResultCodeMigrationContractTest {

  private static final String MIGRATION_FILE = "src/main/resources/sql/migration/FEAT-325470-amd-telephony-result-codes.sql";

  private static final int LEGACY_BAD_NUMBER_CODE = 913;
  private static final int LEGACY_NOT_REACHED_CODE = 914;
  private static final int LEGACY_UNKNOWN_TONE_CODE = 915;

  private static final Pattern INSERT_ROW = Pattern.compile(
      "INSERT\\s+INTO\\s+dbo\\.pd_telephony_rc\\s*\\(\\s*id\\s*,\\s*name\\s*,\\s*description\\s*,\\s*code\\s*\\)\\s*"
          + "VALUES\\s*\\(\\s*(\\d+)\\s*,\\s*N'([^']*)'\\s*,\\s*N'([^']*)'\\s*,\\s*(\\d+)\\s*\\)",
      Pattern.CASE_INSENSITIVE);

  /** Satir yoksa ekle: guard artik id uzerinden, cunku code eski surumden tasinmis olabilir. */
  private static final Pattern INSERT_GUARD = Pattern.compile(
      "IF\\s+NOT\\s+EXISTS\\s*\\(\\s*SELECT\\s+1\\s+FROM\\s+dbo\\.pd_telephony_rc\\s+WHERE\\s+id\\s*=\\s*(\\d+)\\s*\\)",
      Pattern.CASE_INSENSITIVE);

  /** Eski kodlu satiri yeni koda tasiyan UPDATE: SET code = <yeni> WHERE id = <id> AND code = <eski>. */
  private static final Pattern RECODE_UPDATE = Pattern.compile(
      "UPDATE\\s+dbo\\.pd_telephony_rc\\s+SET\\s+code\\s*=\\s*(\\d+)\\s+WHERE\\s+id\\s*=\\s*(\\d+)\\s+AND\\s+code\\s*=\\s*(\\d+)",
      Pattern.CASE_INSENSITIVE);

  @Test
  public void constantsCarryTheAgreedCodesOfTheAmdFamily() {
    assertEquals(900, Constants.TELEPHONY_RC_AMD);
    assertEquals(113, Constants.TELEPHONY_RC_AMD_BAD_NUMBER);
    assertEquals(114, Constants.TELEPHONY_RC_AMD_NOT_REACHED);
    assertEquals(115, Constants.TELEPHONY_RC_AMD_UNKNOWN_TONE);
  }

  @Test
  public void newCodesDoNotCollideWithKnownItuErrorCauses() {
    // 113/114/115 Q.850'de atanmamis araliktadir; ASTERISK_ERROR_CODES (ITU hata cause'lari) ile
    // kesismemeli, aksi halde hasErrorCode AMD sonucunu "hata" sayip finalize yolunu degistirirdi.
    for (int code : new int[] { Constants.TELEPHONY_RC_AMD_BAD_NUMBER, Constants.TELEPHONY_RC_AMD_NOT_REACHED,
        Constants.TELEPHONY_RC_AMD_UNKNOWN_TONE }) {
      assertFalse("code " + code + " ITU hata kumesinde olmamali", Constants.ASTERISK_ERROR_CODES.contains(code));
      assertFalse("code " + code + " attempt muafiyet kumesinde olmamali",
          Constants.TELEPHONY_RESULT_CODES_EXCLUDED_FROM_ATTEMPTS.contains(code));
    }
  }

  @Test
  public void amdFamilySetContainsExactlyTheFourMachineDetectedCodes() {
    assertEquals(new HashSet<>(Arrays.asList(900, 113, 114, 115)), Constants.AMD_FAMILY_TELEPHONY_RC_CODES);

    assertTrue(Constants.isAmdFamilyTelephonyResultCode(Constants.TELEPHONY_RC_AMD));
    assertTrue(Constants.isAmdFamilyTelephonyResultCode(Constants.TELEPHONY_RC_AMD_BAD_NUMBER));
    assertTrue(Constants.isAmdFamilyTelephonyResultCode(Constants.TELEPHONY_RC_AMD_NOT_REACHED));
    assertTrue(Constants.isAmdFamilyTelephonyResultCode(Constants.TELEPHONY_RC_AMD_UNKNOWN_TONE));
    assertFalse("user busy AMD ailesinde degil", Constants.isAmdFamilyTelephonyResultCode(17));
    assertFalse("ITU code 71 (id 83 satiri) AMD ailesinde degil", Constants.isAmdFamilyTelephonyResultCode(71));
    assertFalse("eski 913 artik AMD ailesinde degil", Constants.isAmdFamilyTelephonyResultCode(LEGACY_BAD_NUMBER_CODE));
    assertFalse("null code", Constants.isAmdFamilyTelephonyResultCode(null));
  }

  @Test
  public void startupValidationRequiresEveryAmdFamilyCodeToBeMapped() {
    Set<Integer> requiredSet = new HashSet<>();
    for (int code : ManagerConnectionComposition.requiredInternalTelephonyResultCodes()) {
      requiredSet.add(code);
    }

    assertTrue(requiredSet.containsAll(Constants.AMD_FAMILY_TELEPHONY_RC_CODES));
    assertFalse("eski kodlar zorunlu listede kalmamali", requiredSet.contains(LEGACY_BAD_NUMBER_CODE));
    assertTrue("mevcut zorunlu kodlar korunmali", requiredSet.contains(Constants.TELEPHONY_RC_AMBIGUOUS_RECORD));
    assertTrue(requiredSet.contains(Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT));
    assertTrue(requiredSet.contains(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));
  }

  @Test
  public void migrationInsertsTheThreeRowsWithAgreedIdsNamesAndCodes() throws IOException {
    String sql = readMigration();

    Map<Integer, String[]> rowsById = new LinkedHashMap<>();
    Matcher matcher = INSERT_ROW.matcher(sql);
    while (matcher.find()) {
      rowsById.put(Integer.valueOf(matcher.group(1)), new String[] { matcher.group(2), matcher.group(3), matcher.group(4) });
    }

    assertEquals("uc satir beklenir", 3, rowsById.size());
    assertRow(rowsById, 86, "AMD Bad Number (unallocated number)", "Wrong Number", Constants.TELEPHONY_RC_AMD_BAD_NUMBER);
    assertRow(rowsById, 87, "AMD Not Reached", "No answer", Constants.TELEPHONY_RC_AMD_NOT_REACHED);
    assertRow(rowsById, 88, "AMD Unknown Tone", "Answering Machine", Constants.TELEPHONY_RC_AMD_UNKNOWN_TONE);
  }

  @Test
  public void migrationRecodesRowsWrittenByTheFirstVersion() throws IOException {
    String sql = readMigration();

    Map<Integer, int[]> recodeById = new LinkedHashMap<>();
    Matcher matcher = RECODE_UPDATE.matcher(sql);
    while (matcher.find()) {
      recodeById.put(Integer.valueOf(matcher.group(2)),
          new int[] { Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(1)) });
    }

    assertEquals("uc satir icin eski->yeni UPDATE beklenir", 3, recodeById.size());
    assertRecode(recodeById, 86, LEGACY_BAD_NUMBER_CODE, Constants.TELEPHONY_RC_AMD_BAD_NUMBER);
    assertRecode(recodeById, 87, LEGACY_NOT_REACHED_CODE, Constants.TELEPHONY_RC_AMD_NOT_REACHED);
    assertRecode(recodeById, 88, LEGACY_UNKNOWN_TONE_CODE, Constants.TELEPHONY_RC_AMD_UNKNOWN_TONE);
  }

  @Test
  public void migrationIsIdempotentAndWritesExplicitIdentityValues() throws IOException {
    String sql = readMigration();

    Set<Integer> guardedIds = new HashSet<>();
    Matcher matcher = INSERT_GUARD.matcher(sql);
    while (matcher.find()) {
      guardedIds.add(Integer.valueOf(matcher.group(1)));
    }
    assertEquals(new HashSet<>(Arrays.asList(86, 87, 88)), guardedIds);

    assertTrue(sql.contains("SET IDENTITY_INSERT dbo.pd_telephony_rc ON;"));
    assertTrue(sql.contains("SET IDENTITY_INSERT dbo.pd_telephony_rc OFF;"));
    assertTrue("id cakisma korumasi (eski ve yeni kod disinda dolu id)",
        sql.contains("id IN (86, 87, 88) AND code NOT IN (113, 114, 115, 913, 914, 915)"));
    assertTrue("code cakisma korumasi (yeni kod baska id'de)",
        sql.contains("code IN (113, 114, 115) AND id NOT IN (86, 87, 88)"));
    assertTrue("cakismada script durmali", sql.contains("RAISERROR"));
  }

  @Test
  public void closeReasonForUnreachableNumbersIsPlainAndColumnSafe() {
    String reason = Constants.CLOSE_REASON_ALL_NUMBERS_UNREACHABLE;

    assertEquals("all numbers unreachable", reason);
    assertTrue(reason.length() <= 255);
    assertFalse("tek tirnak SQL cumlesini bozar", reason.contains("'"));
  }

  private static void assertRow(Map<Integer, String[]> rowsById, int id, String name, String description, int code) {
    String[] row = rowsById.get(id);
    assertTrue("id " + id + " satiri yok", row != null);
    assertEquals(name, row[0]);
    assertEquals(description, row[1]);
    assertEquals(String.valueOf(code), row[2]);
  }

  private static void assertRecode(Map<Integer, int[]> recodeById, int id, int legacyCode, int newCode) {
    int[] recode = recodeById.get(id);
    assertTrue("id " + id + " icin UPDATE yok", recode != null);
    assertEquals("id " + id + " eski kod", legacyCode, recode[0]);
    assertEquals("id " + id + " yeni kod", newCode, recode[1]);
  }

  private static String readMigration() throws IOException {
    Path path = Paths.get(MIGRATION_FILE);
    assertTrue("migration script bulunamadi: " + path.toAbsolutePath(), Files.exists(path));
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

}
