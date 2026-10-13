package tests.dnc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.ParametrizedQuery;
import com.powerdialer.util.Constants;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

/**
 * Müşteri sibling satırlarını aranamaz işaretleyen UPDATE ve müşteri
 * satırlarını çeken SELECT'in SQL contract'ı: mevcut DNC işaretleme alan
 * semantiği (complete_date + TRC=76 + is_disabled + user alanları NULL),
 * parametrized customer_no, yalnız açık satırlar (complete_date IS NULL)
 * ve tetikleyen satırın hariç tutulması.
 */
public class CampaignDbOperationsMarkSiblingsByCustomerNoSqlContractTest {

  private DBEngine dbEngine;
  private CampaignDbOperations operations;

  @Before
  public void init() {
    dbEngine = mock(DBEngine.class);
    EnCipherUtils enCipherUtils = mock(EnCipherUtils.class);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");
    operations = new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);
  }

  @Test
  public void markSiblingsUpdateCarriesDncFilteredFieldSemanticsAndExcludesTriggeringRow() throws Exception {
    operations.setFilteredByDncListStatusByCustomerNo(campaign(), "CUST-999", "41");

    ArgumentCaptor<ParametrizedQuery> captor = ArgumentCaptor.forClass(ParametrizedQuery.class);
    verify(dbEngine).executeBatchParametrizedUpdate(captor.capture(), eq(99L), anyString(), anyString(), eq("plain"));

    String query = captor.getValue().getQuery();
    List<Object> params = captor.getValue().getParameters();

    assertTrue(query.startsWith("UPDATE result_table SET "));
    assertTrue(query.contains("complete_date = '"));
    assertTrue(query.contains("telephony_result_code = " + Constants.TELEPHONY_RC_ID_FILTERED_BY_DNC));
    assertTrue(query.contains("is_disabled = " + Constants.DISABLED));
    assertTrue(query.contains("user_id = NULL"));
    assertTrue(query.contains("user_result_code = NULL"));
    assertTrue(query.contains("WHERE customer_no = ?"));
    assertTrue(query.contains("AND complete_date IS NULL"));
    assertTrue("Tetikleyen satır hariç tutulmalı", query.contains("AND result_id <> ?"));

    assertEquals(2, params.size());
    assertEquals("CUST-999", params.get(0));
    assertEquals("41", params.get(1));
  }

  @Test
  public void markSiblingsUpdateWithoutExcludeKeyHasSingleParameter() throws Exception {
    operations.setFilteredByDncListStatusByCustomerNo(campaign(), "CUST-999", null);

    ArgumentCaptor<ParametrizedQuery> captor = ArgumentCaptor.forClass(ParametrizedQuery.class);
    verify(dbEngine).executeBatchParametrizedUpdate(captor.capture(), eq(99L), anyString(), anyString(), eq("plain"));

    String query = captor.getValue().getQuery();
    List<Object> params = captor.getValue().getParameters();

    assertFalse(query.contains("<> ?"));
    assertEquals(1, params.size());
    assertEquals("CUST-999", params.get(0));
  }

  @Test
  public void fetchRowsByCustomerNoSelectsJoinedRowsWithParametrizedCustomerNo() throws Exception {
    when(dbEngine.executeParametrizedQuery(org.mockito.ArgumentMatchers.any(ParametrizedQuery.class),
        anyLong(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(), eq("plain")))
        .thenReturn(new JSONArray());

    operations.fetchRowsByCustomerNo(campaign(), "CUST-999");

    ArgumentCaptor<ParametrizedQuery> captor = ArgumentCaptor.forClass(ParametrizedQuery.class);
    verify(dbEngine).executeParametrizedQuery(captor.capture(), eq(99L),
        org.mockito.ArgumentMatchers.any(), anyString(), anyString(), eq("plain"));

    String query = captor.getValue().getQuery();
    assertTrue(query.contains("SELECT * FROM list_table src LEFT JOIN result_table res ON (src.id = res.result_id)"));
    assertTrue(query.contains("WHERE res.customer_no = ?"));
    assertEquals(1, captor.getValue().getParameters().size());
    assertEquals("CUST-999", captor.getValue().getParameters().get(0));
  }

  private Campaign campaign() {
    Campaign campaign = new Campaign();
    campaign.setId(99L);
    campaign.setDbConnectionString("jdbc:sqlserver://localhost;databaseName=campaign");
    campaign.setDbUsername("user");
    campaign.setDbPassword("encrypted");
    campaign.setDbListTable("list_table");
    campaign.setDbResultTable("result_table");
    campaign.setColUniqueKey("id");
    campaign.setColResultUniqueKey("result_id");
    campaign.setColResultCompleteDate("complete_date");
    campaign.setColResultTelephonyRc("telephony_result_code");
    campaign.setColResultUserId("user_id");
    campaign.setColResultUserRc("user_result_code");
    campaign.setColResultCustomerNo("customer_no");
    return campaign;
  }
}
