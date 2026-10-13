package com.powerdialer.service;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import java.util.Map;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import com.powerdialer.datamanipulation.DataManipulationStateHolder;
import com.powerdialer.datamanipulation.supplier.DataSupplier;
import com.powerdialer.datamanipulation.supplier.DataSupplierBuilder;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.UploadToolContext;
import com.powerdialer.request.StartUploadParameterRequest;
import com.powerdialer.util.LoggerDecorator;

@RunWith(MockitoJUnitRunner.class)
public class CampaignDataManipulationServiceTest {

  @Mock
  private UploadToolFileService tempFileService;

  @Mock
  private CampaignDbOperations campaignDbOperations;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private LoggerDecorator loggerDecorator;

  @Mock
  private CampaignService campaignService;

  @Mock
  private DataManipulationStateHolder dataManipulationStateHolder;

  @Mock
  private ApplicationContext applicationContext;

  @InjectMocks
  private CampaignDataManipulationService campaignDataManipulationService;

  @Captor
  private ArgumentCaptor<DataSupplier<Map>> mapDataSupplierArgumentCaptor;

  private Campaign campaign;

  @Before
  public void init() {
    campaign = new Campaign();
    campaign.setId(1L);
    lenient().doReturn(campaignDataManipulationService).when(applicationContext)
      .getBean(campaignDataManipulationService.getClass());
    when(dataManipulationStateHolder.getOrCreateUploadToolContext(campaign)).thenReturn(new UploadToolContext(campaign));
  }

  @Test
  public void givenDataSupplierAndUploadParameterWhenInvokeProcessStartUploadThenFillDbIdFiled() throws IOException, SQLException {
    DataSupplier<Map> dataSupplier = DataSupplierBuilder.<Map>getInstance()
      .usingStringSource("name,db_id\n"
                           + "bob,\n",
                         "text/csv")
      .sourceFirstRowColumnNames(true)
      .dataType(Map.class)
      .build();
    StartUploadParameterRequest parameterRequest = new StartUploadParameterRequest();
    parameterRequest.setDbIdColumnName("db_id");
    parameterRequest.setFirstRowColumnNames(true);
    parameterRequest.setUpdateExistingData(false);

    doReturn(campaign).when(campaignService).getInitializedCampaign(eq(1L));
    doReturn("0").when(campaignDbOperations).aggregateCampaignSourceTableColumn(eq("db_id"), eq("MAX"), eq(campaign));
    campaignDataManipulationService.handleStartUpload(1L, parameterRequest, dataSupplier);
    verify(tempFileService).saveAsTempCsvFile(mapDataSupplierArgumentCaptor.capture(), any());
    DataSupplier<Map> actualDataSupplier = mapDataSupplierArgumentCaptor.getValue();
    assertTrue(hasEntry("db_id", "1", actualDataSupplier));
  }

  @Test
  public void givenDataSupplierAndUploadParameterWhenInvokeProcessStartUploadThenSaveToTempFile() throws IOException, SQLException {
    DataSupplier<Map> dataSupplier = DataSupplierBuilder.<Map>getInstance()
      .usingStringSource("name,db_id\n"
                           + "bob,-1\n",
                         "text/csv")
      .sourceFirstRowColumnNames(true)
      .dataType(Map.class)
      .build();
    StartUploadParameterRequest parameterRequest = new StartUploadParameterRequest();
    parameterRequest.setFirstRowColumnNames(true);
    parameterRequest.setUpdateExistingData(false);

    doReturn(campaign).when(campaignService).getInitializedCampaign(eq(1L));
    lenient().doReturn("0").when(campaignDbOperations).aggregateCampaignSourceTableColumn(eq("db_id"), eq("MAX"), eq(campaign));
    campaignDataManipulationService.handleStartUpload(1L, parameterRequest, dataSupplier);
    verify(tempFileService).saveAsTempCsvFile(mapDataSupplierArgumentCaptor.capture(), any());
    DataSupplier<Map> actualDataSupplier = mapDataSupplierArgumentCaptor.getValue();
    assertTrue(hasEntry("db_id", "-1", actualDataSupplier));
  }

  private boolean hasEntry(String key, String value, DataSupplier<Map> dataSupplier) {
    try {
      Map nextRow = dataSupplier.getNextObject();
      do {
        if (nextRow != null && nextRow.containsKey(key) && nextRow.values().contains(value)) {
          return true;
        }
        nextRow = dataSupplier.getNextObject();
      } while (nextRow != null);
    } catch (IOException e) {
    }
    return false;
  }

}
