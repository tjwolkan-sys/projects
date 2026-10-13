package tests.campaign;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Campaign;
import com.powerdialer.service.CampaignDataTypeChecker;
import com.powerdialer.util.EnCipherUtils;

@RunWith(MockitoJUnitRunner.class)
public class CampaignHistoryColumnMappingRegressionTest {

  @Mock
  private CampaignDbOperations campaignDbOperations;

  @Mock
  private EnCipherUtils enCipherUtils;

  private CampaignDataTypeChecker checker;

  @Before
  public void setUp() throws Exception {
    checker = new CampaignDataTypeChecker();
    ReflectionTestUtils.setField(checker, "campaignDbOperations", campaignDbOperations);
    ReflectionTestUtils.setField(checker, "enChiperUtils", enCipherUtils);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");
  }

  @Test
  public void checkColumnsTypesMapsDefaultHistoryColumnsWhenExistingCampaignMetadataIsBlank() {
    Campaign campaign = campaign();
    stubColumns(sourceColumns(), resultColumnsWithHistory());

    assertTrue(checker.checkColumnsTypes(campaign));

    assertThat(campaign.getColResultCbHistory(), is("cb_history"));
    assertThat(campaign.getColResultCallProcess(), is("call_process"));
  }

  @Test
  public void checkColumnsTypesDoesNotMapHistoryColumnsFromSourceOnly() {
    Campaign campaign = campaign();
    stubColumns(resultColumnsWithHistory(), new String[][] { { "id", "BIGINT" } });

    assertTrue(checker.checkColumnsTypes(campaign));

    assertThat(campaign.getColResultCbHistory(), is(nullValue()));
    assertThat(campaign.getColResultCallProcess(), is(nullValue()));
  }

  private void stubColumns(String[][] sourceColumns, String[][] resultColumns) {
    try {
      when(campaignDbOperations.getTableColumnsWithTypes(eq("campaign_source"), anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(sourceColumns);
      when(campaignDbOperations.getTableColumnsWithTypes(eq("campaign_result"), anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(resultColumns);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String[][] sourceColumns() {
    return new String[][] { { "id", "BIGINT" } };
  }

  private String[][] resultColumnsWithHistory() {
    return new String[][] {
        { "id", "BIGINT" },
        { "cb_history", "NVARCHAR" },
        { "call_process", "NVARCHAR" }
    };
  }

  private Campaign campaign() {
    Campaign campaign = new Campaign();
    campaign.setId(309403L);
    campaign.setDbConnectionString("jdbc:sqlserver://test-host;databaseName=test");
    campaign.setDbUsername("user");
    campaign.setDbPassword("encrypted");
    campaign.setDbListTable("campaign_source");
    campaign.setDbResultTable("campaign_result");
    campaign.setColUniqueKey("id");
    return campaign;
  }
}
