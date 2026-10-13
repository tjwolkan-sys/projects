package tests.datamanipulation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.ResponseEntity;

import com.powerdialer.controller.CampaignRecordController;
import com.powerdialer.dto.CampaignRecordFilterDTO;
import com.powerdialer.exceptions.MissingSelectionWarningException;
import com.powerdialer.service.CampaignRecordService;
import com.powerdialer.util.Constants;

/**
 * Task 327143: /campaign-records/fetch endpoint'inin truncation sozlesmesi.
 * JSON body sekli degismez (Records/UserRC/TelephonyRC); tavan asildiginda
 * bilgi X-Records-Truncated header'i ile tasinir (X-Updated-Count deseni).
 */
public class CampaignRecordFetchTruncationHeaderContractTest {

  private CampaignRecordService service;
  private CampaignRecordController controller;

  @Before
  public void init() {
    service = mock(CampaignRecordService.class);
    controller = new CampaignRecordController(service);
  }

  private CampaignRecordFilterDTO dto() {
    CampaignRecordFilterDTO dto = new CampaignRecordFilterDTO();
    dto.setCampaignId(7L);
    return dto;
  }

  private Map<Object, List<Map<String, Object>>> emptyData() {
    Map<Object, List<Map<String, Object>>> data = new HashMap<>();
    data.put(Constants.RECORDS, Collections.<Map<String, Object>>emptyList());
    data.put(Constants.UserRC, Collections.<Map<String, Object>>emptyList());
    data.put(Constants.TelephonyRC, Collections.<Map<String, Object>>emptyList());
    return data;
  }

  @Test
  public void truncatedFetchSetsHeaderTrueAndKeepsBodyShape() throws Exception {
    CampaignRecordFilterDTO dto = dto();
    Map<Object, List<Map<String, Object>>> data = emptyData();
    when(service.fetchAllWithTruncation(dto))
        .thenReturn(new CampaignRecordService.FetchAllOutcome(data, true));

    ResponseEntity<Map<Object, List<Map<String, Object>>>> response = controller.fetchRecords(dto);

    assertEquals("true", response.getHeaders().getFirst("X-Records-Truncated"));
    assertSame("JSON body servis verisiyle ayni olmali", data, response.getBody());
    assertEquals(200, response.getStatusCodeValue());
  }

  @Test
  public void completeFetchSetsHeaderFalse() throws Exception {
    CampaignRecordFilterDTO dto = dto();
    when(service.fetchAllWithTruncation(dto))
        .thenReturn(new CampaignRecordService.FetchAllOutcome(emptyData(), false));

    ResponseEntity<Map<Object, List<Map<String, Object>>>> response = controller.fetchRecords(dto);

    assertEquals("false", response.getHeaders().getFirst("X-Records-Truncated"));
  }

  @Test(expected = MissingSelectionWarningException.class)
  public void nullCampaignIdStillThrowsMissingSelectionWarning() {
    controller.fetchRecords(new CampaignRecordFilterDTO());
  }

  @Test(expected = RuntimeException.class)
  public void sqlExceptionStillWrappedAsRuntimeException() throws Exception {
    CampaignRecordFilterDTO dto = dto();
    when(service.fetchAllWithTruncation(dto)).thenThrow(new SQLException("boom"));

    controller.fetchRecords(dto);
  }
}
