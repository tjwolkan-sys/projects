package tests.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import javax.persistence.Column;

import org.hibernate.validator.constraints.NotBlank;
import org.junit.Test;

import com.powerdialer.dto.CampaignFormDTO;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

/**
 * Müşteri bazlı DNC için Campaign üzerindeki yeni opsiyonel kolon
 * mapping'lerinin contract'ı: col_result_customer_no + col_date_entered.
 * Mapping'ler boş bırakılabilir (mevcut kampanyalar davranış değiştirmez)
 * ve CampaignFormDTO üzerinden Dozer isim eşleşmesiyle taşınır.
 */
public class CampaignCustomerNoColumnMappingTest {

  @Test
  public void campaignMapsCustomerNoMappingColumn() throws Exception {
    Method getter = Campaign.class.getMethod("getColResultCustomerNo");

    Column column = getter.getAnnotation(Column.class);
    assertNotNull("getColResultCustomerNo @Column olmalı", column);
    assertEquals("col_result_customer_no", column.name());
    assertTrue("col_result_customer_no nullable olmalı", column.nullable());
    assertNull("Opsiyonel mapping @NotBlank taşımamalı", getter.getAnnotation(NotBlank.class));
  }

  @Test
  public void campaignMapsDateEnteredMappingColumn() throws Exception {
    Method getter = Campaign.class.getMethod("getColDateEntered");

    Column column = getter.getAnnotation(Column.class);
    assertNotNull("getColDateEntered @Column olmalı", column);
    assertEquals("col_date_entered", column.name());
    assertTrue("col_date_entered nullable olmalı", column.nullable());
    assertNull("Opsiyonel mapping @NotBlank taşımamalı", getter.getAnnotation(NotBlank.class));
  }

  @Test
  public void campaignNewMappingFieldsRoundTrip() {
    Campaign campaign = new Campaign();
    campaign.setColResultCustomerNo("customer_no");
    campaign.setColDateEntered("date_entered");

    assertEquals("customer_no", campaign.getColResultCustomerNo());
    assertEquals("date_entered", campaign.getColDateEntered());
  }

  @Test
  public void campaignFormDtoCarriesNewMappingFields() throws Exception {
    assertNotNull(CampaignFormDTO.class.getMethod("getColResultCustomerNo"));
    assertNotNull(CampaignFormDTO.class.getMethod("setColResultCustomerNo", String.class));
    assertNotNull(CampaignFormDTO.class.getMethod("getColDateEntered"));
    assertNotNull(CampaignFormDTO.class.getMethod("setColDateEntered", String.class));
  }

  @Test
  public void defaultColumnNamesEnumContainsCustomerNoAndDateEntered() {
    assertEquals("customer_no",
        Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.valueOf("CUSTOMER_NO").getValue());
    assertEquals("date_entered",
        Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.valueOf("DATE_ENTERED").getValue());
  }
}
