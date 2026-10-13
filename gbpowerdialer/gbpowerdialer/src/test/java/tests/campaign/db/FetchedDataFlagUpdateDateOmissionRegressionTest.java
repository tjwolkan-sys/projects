package tests.campaign.db;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

/**
 * Task 323316: fetch (kuyruğa alma) işaretlemesi kampanya sonuç tablosunun
 * {@code UpdatedDate} (col_result_update_date) kolonunu ARTIK güncellememelidir.
 *
 * <p>Raporlamada {@code UpdatedDate}, "işlem gören kayıt tarihi" CASE'inde ve ayrıca
 * expire tarihi için kullanılıyor. Fetch'in bu kolonu ezmesi, hiç aranmamış kaydın
 * fetch gününe düşmesine (hayalet kayıt) yol açıyordu. Bu test, fetch SQL'lerinin
 * (UPDATE ve ayrı source/result tablosu INSERT branch'i) {@code UpdatedDate} yazımı
 * içermediğini; buna karşın {@code is_fetched} işaretlemesi ile start/answer date
 * sıfırlamasının korunduğunu kilitler.</p>
 */
public class FetchedDataFlagUpdateDateOmissionRegressionTest {

  @Test
  public void fetchMarkingDoesNotWriteUpdatedDateButKeepsFetchedFlagAndDateResets() throws Exception {
    DBEngine dbEngine = mock(DBEngine.class);
    EnCipherUtils enCipherUtils = mock(EnCipherUtils.class);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");

    CampaignDbOperations operations =
        new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);

    Call c1 = new Call();
    c1.setUniqueKey("101");
    Call c2 = new Call();
    c2.setUniqueKey("102");
    List<Call> calls = Arrays.asList(c1, c2);

    operations.reserveRowsForFetch(separateSourceResultCampaign(), calls);

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(dbEngine, times(2))
        .executeUpdate(sqlCaptor.capture(), anyLong(), anyString(), anyString(), anyString());
    List<String> sqls = sqlCaptor.getAllValues();
    String updateSql = sqls.get(0);
    String insertSql = sqls.get(1);

    // UPDATE branch: is_fetched işaretlenir, start/answer resetlenir ama UpdatedDate yazılmaz.
    assertTrue("UPDATE sonuç tablosuna yapılmalı", updateSql.startsWith("UPDATE result_table SET"));
    assertTrue("is_fetched işaretlenmeli", updateSql.contains("is_fetched = '"));
    assertTrue("start_date sıfırlanmalı", updateSql.contains("start_date = NULL"));
    assertTrue("answer_date sıfırlanmalı", updateSql.contains("answer_date = NULL"));
    assertFalse("fetch UPDATE'i UpdatedDate yazmamalı", updateSql.contains("UpdatedDate"));
    assertFalse("fetch UPDATE'i CURRENT_TIMESTAMP yazmamalı", updateSql.contains("CURRENT_TIMESTAMP"));

    // INSERT branch (ayrı source/result tablosu): kolon listesi UpdatedDate içermemeli.
    assertTrue("INSERT sonuç tablosuna yapılmalı", insertSql.startsWith("INSERT INTO result_table"));
    assertTrue("kolon listesi yalnız is_fetched + unique key olmalı",
        insertSql.contains("(is_fetched,result_id)"));
    assertFalse("fetch INSERT'i UpdatedDate yazmamalı", insertSql.contains("UpdatedDate"));
    assertFalse("fetch INSERT'i CURRENT_TIMESTAMP yazmamalı", insertSql.contains("CURRENT_TIMESTAMP"));
  }

  private Campaign separateSourceResultCampaign() {
    Campaign campaign = new Campaign();
    campaign.setId(99L);
    campaign.setDbConnectionString("jdbc:sqlserver://localhost;databaseName=campaign");
    campaign.setDbUsername("user");
    campaign.setDbPassword("encrypted");
    campaign.setDbListTable("list_table");
    campaign.setDbResultTable("result_table");
    campaign.setColUniqueKey("id");
    campaign.setColResultUniqueKey("result_id");
    campaign.setColResultFetchedDataFlag("is_fetched");
    campaign.setColResultUpdateDate("UpdatedDate");
    campaign.setColResultStartDate("start_date");
    campaign.setColResultAnswerDate("answer_date");
    return campaign;
  }
}
