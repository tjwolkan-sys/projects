package tests.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Set;

import javax.persistence.Column;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.constraints.Min;

import org.junit.BeforeClass;
import org.junit.Test;

import com.powerdialer.dto.CampaignFormDTO;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

/**
 * FEAT-325559 Faz 2: yeni konfigürasyon alanlarının contract'ı.
 *
 * <p>Bu faz tamamen additive'dir; alan boş bırakıldığında retry davranışı değişmez. Testler
 * kalıcılık kolon adlarını, validasyon sınırını, yetki kümesi üyeliğini ve Dozer'ın isim-eşli
 * çalışabilmesi için entity/DTO alan adlarının birebir aynı olduğunu kilitler.</p>
 */
public class CampaignMaxAttemptPerNumberFieldContractTest {

  private static Validator validator;

  @BeforeClass
  public static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  // ---------------------------------------------------------------------
  // Kalıcılık contract'ı
  // ---------------------------------------------------------------------

  @Test
  public void maxAttemptPerNumberIsMappedToExpectedColumn() throws Exception {
    Column column = Campaign.class.getMethod("getMaxAttemptPerNumber").getAnnotation(Column.class);

    assertNotNull("Alan Hibernate kolonuna map edilmelidir", column);
    assertEquals("max_attempt_per_number", column.name());
  }

  @Test
  public void closeReasonBindingIsMappedToExpectedColumn() throws Exception {
    Column column = Campaign.class.getMethod("getColResultCloseReason").getAnnotation(Column.class);

    assertNotNull(column);
    assertEquals("col_result_close_reason", column.name());
  }

  @Test
  public void closeReasonResultColumnNameIsCloseReason() {
    assertEquals("close_reason", Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON.getValue());
  }

  @Test
  public void closeReasonColumnAcceptsOnlyTextTypes() {
    Set<Constants.DB_FIELD_TYPES> allowed =
        Constants.RESULT_FIELDS_ALLOWED_TYPES.get(Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON);

    assertNotNull("CLOSE_REASON icin izinli tip kumesi tanimli olmalidir", allowed);
    assertTrue(allowed.contains(Constants.DB_FIELD_TYPES.NVARCHAR));
    assertTrue(allowed.contains(Constants.DB_FIELD_TYPES.VARCHAR));
    assertFalse("Sayisal tip kabul edilmemelidir", allowed.contains(Constants.DB_FIELD_TYPES.INT));
  }

  // ---------------------------------------------------------------------
  // Validasyon
  // ---------------------------------------------------------------------

  @Test
  public void nullMaxAttemptPerNumberIsValidBecauseFieldIsOptional() {
    Campaign campaign = new Campaign();
    campaign.setMaxAttemptPerNumber(null);

    Set<ConstraintViolation<Campaign>> violations = validator.validateProperty(campaign, "maxAttemptPerNumber");

    assertTrue("Alan opsiyoneldir; null gecerli olmalidir", violations.isEmpty());
  }

  @Test
  public void zeroMaxAttemptPerNumberIsRejected() {
    Campaign campaign = new Campaign();
    campaign.setMaxAttemptPerNumber(0);

    Set<ConstraintViolation<Campaign>> violations = validator.validateProperty(campaign, "maxAttemptPerNumber");

    assertFalse("0 anlamsizdir; en az 1 olmalidir", violations.isEmpty());
  }

  @Test
  public void oneIsTheSmallestAcceptedMaxAttemptPerNumber() {
    Campaign campaign = new Campaign();
    campaign.setMaxAttemptPerNumber(1);

    Set<ConstraintViolation<Campaign>> violations = validator.validateProperty(campaign, "maxAttemptPerNumber");

    assertTrue(violations.isEmpty());
  }

  @Test
  public void minimumBoundIsOneNotZero() throws Exception {
    Min min = Campaign.class.getMethod("getMaxAttemptPerNumber").getAnnotation(Min.class);

    assertNotNull(min);
    assertEquals(1L, min.value());
    assertEquals("{campaign.max_attempt_per_number.min}", min.message());
  }

  // ---------------------------------------------------------------------
  // DTO / Dozer isim eşlemesi ve yetki kümesi
  // ---------------------------------------------------------------------

  /**
   * Dozer isim-eşli çalıştığı için entity ve DTO alan adları birebir aynı olmalıdır; aksi halde
   * alan sessizce taşınmaz.
   */
  @Test
  public void entityAndDtoExposeIdenticallyNamedAccessors() throws Exception {
    for (String property : new String[] { "MaxAttemptPerNumber", "ColResultCloseReason" }) {
      Method entityGetter = Campaign.class.getMethod("get" + property);
      Method dtoGetter = CampaignFormDTO.class.getMethod("get" + property);

      assertEquals("Getter donus tipleri uyusmalidir: " + property,
          entityGetter.getReturnType(), dtoGetter.getReturnType());
      assertNotNull(Campaign.class.getMethod("set" + property, entityGetter.getReturnType()));
      assertNotNull(CampaignFormDTO.class.getMethod("set" + property, dtoGetter.getReturnType()));
    }
  }

  @Test
  public void maxAttemptPerNumberIsEditableWithTechPermission() {
    assertTrue("Alan EditCampaignTech yetkisiyle duzenlenebilir olmalidir",
        Constants.ALLOWED_TECH_FIELDS.contains("maxAttemptPerNumber"));
    assertFalse("Non-tech yetkiye acilmamalidir",
        Constants.ALLOWED_NON_TECH_FIELDS.contains("maxAttemptPerNumber"));
  }

  @Test
  public void newCampaignLeavesMaxAttemptPerNumberEmptySoFeatureIsOff() {
    assertNull("Varsayilan deger yazilmamalidir; ozellik opt-in'dir",
        new Campaign().getMaxAttemptPerNumber());
  }

  // ---------------------------------------------------------------------
  // Kapanış nedeni metinleri (K14)
  // ---------------------------------------------------------------------

  @Test
  public void closeReasonTextsMatchAgreedWording() {
    assertEquals("closing for reached number limit", Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED);
    assertEquals("max attempt reached on callback", Constants.CLOSE_REASON_MAX_ATTEMPT_ON_CALLBACK);
    assertEquals("no callable number available", Constants.CLOSE_REASON_NO_CALLABLE_NUMBER);
  }

  /**
   * Metinler {@code NVARCHAR(255)} kolona yazılacağı için sınırın altında kalmalıdır.
   */
  @Test
  public void closeReasonTextsFitInTargetColumn() {
    for (String reason : new String[] { Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED,
                                        Constants.CLOSE_REASON_MAX_ATTEMPT_ON_CALLBACK,
                                        Constants.CLOSE_REASON_NO_CALLABLE_NUMBER }) {
      assertTrue("Kapanis nedeni 255 karakteri asmamalidir: " + reason, reason.length() <= 255);
      assertFalse("Kapanis nedeni tek tirnak icermemelidir (SQL literal yazimi): " + reason,
          reason.contains("'"));
    }
  }

}
