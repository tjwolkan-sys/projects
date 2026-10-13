package tests.campaign.db;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.request.CreateSourceTableRequest;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

/**
 * FEAT-318332: PD'nin oluşturduğu default source tablolarına customer_no
 * base alanı eklenir (üçüncü parti import yapıları doldurur). Mevcut müşteri
 * tablolarına kolon ekleme müşteri/DBA sorumluluğundadır.
 */
public class CampaignSourceTableCustomerNoColumnTest {

  @Test
  public void defaultSourceTableCreateIncludesCustomerNoBaseColumn() throws Exception {
    DBEngine dbEngine = mock(DBEngine.class);
    EnCipherUtils enCipherUtils = mock(EnCipherUtils.class);
    CampaignDbOperations operations = new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);

    CreateSourceTableRequest request = new CreateSourceTableRequest();
    request.setCampaignId(318332L);
    request.setTableName("pd_data_318332");
    request.setConnectionString("jdbc:sqlserver://localhost;databaseName=campaign");
    request.setDbUsername("user");
    request.setDbPassword("pwd");
    request.setFetchDataRandomly(false);

    operations.createDefaultSourceTableForCampaign(request);

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(dbEngine).executeUpdate(queryCaptor.capture(), eq(318332L), anyString(), anyString(), anyString());
    String createTableQuery = queryCaptor.getValue();

    assertTrue("Default source tabloda customer_no base alanı olmalı: " + createTableQuery,
        createTableQuery.contains("customer_no NVARCHAR(255)"));
  }
}
