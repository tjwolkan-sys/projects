package tests.datamanipulation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.powerdialer.dao.impl.CampaignRecordDao;
import com.powerdialer.dto.CampaignRecordsOperationDTO;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.OperationOutcome;
import com.powerdialer.model.User;
import com.powerdialer.service.ActionLogService;
import com.powerdialer.service.CampaignRecordService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * FEAT-325559 Faz 8 / K3 — kayit aktivasyonu kapanis durumunun TAMAMINI geri alir.
 *
 * <p>Aktivasyon ({@code ACTIVATE_RECORD}) kapanisi geri almak icin {@code complete_date},
 * {@code is_disabled}, {@code telephony_result_code} vb. kolonlari temizliyor; bu isle eklenen
 * kapanis durumu kolonu {@code close_reason} sozlesmeye eklenmemisti. Aktive edilen kayit sonra
 * baska bir sebeple kapanirsa (basarili gorusme, genel limit) kolonda eski metin kaliyordu.</p>
 *
 * <p><b>Neden AYRI bir UPDATE:</b> {@code close_reason} opsiyonel bir binding; musterinin kendi
 * tablosunda NOT NULL bir {@code close_reason} kolonu varsa NULL yazimi reddedilir. Temizleme
 * aktivasyon UPDATE'ine eklenseydi o musteride aktivasyonun TAMAMI duserdi. Temizleme izole
 * calisir; hatasi loglanir, aktivasyon sonucu degismez.</p>
 */
public class CampaignRecordActivationCloseReasonContractTest {

  private static final long CAMPAIGN_ID = 55L;

  private CampaignRecordService service;
  private CampaignRecordDao campaignRecordDao;
  private Campaign campaign;

  @Before
  public void setup() throws Exception {
    campaignRecordDao = mock(CampaignRecordDao.class);
    // Aktivasyon PUZZLE modulune islem logu yazar (ActionLogService); DB yerine mock.
    LoggerDecorator loggerDecorator =
        new LoggerDecorator(LoggerFactory.getLogger(getClass()), mock(ActionLogService.class));
    service = new CampaignRecordService(loggerDecorator, null, null, null, campaignRecordDao);

    campaign = new Campaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setName("activation_test");
    campaign.setDbResultTable("result_table");
    campaign.setState(Constants.CAMPAIGN_STATE.STOPPED);
    campaign.setColResultCloseReason("close_reason");
    registerCampaign(campaign);

    when(campaignRecordDao.executeUpdate(any(Campaign.class), any(String.class), anyList())).thenReturn(2);

    // Islem logu (getLogMessageBase) giris yapmis kullaniciyi SecurityContext'ten okur.
    User operator = new User();
    operator.setUsername("operator");
    SecurityContext securityContext = mock(SecurityContext.class);
    Authentication authentication = mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn(operator);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);
  }

  @After
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @SuppressWarnings("unchecked")
  private void registerCampaign(Campaign target) throws Exception {
    Field field = CampaignRecordService.class.getDeclaredField("campaignMap");
    field.setAccessible(true);
    ((Map<Long, Campaign>) field.get(service)).put(target.getId(), target);
  }

  private OperationOutcome<Constants.RECORD_UPDATE_OPERATION_STATUS> activate(String... ids) {
    CampaignRecordsOperationDTO dto = new CampaignRecordsOperationDTO();
    dto.setOperation(Constants.CAMPAIGN_RECORD_OPERATION.ACTIVATE_RECORD);
    dto.setRecordIds(Arrays.asList(ids));
    return service.applyOperationWithCount(CAMPAIGN_ID, dto);
  }

  @SuppressWarnings("unchecked")
  private List<String> executedStatements() throws SQLException {
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(campaignRecordDao, atLeastOnce()).executeUpdate(eq(campaign), sql.capture(), anyList());
    return sql.getAllValues();
  }

  // ---------------------------------------------------------------------

  @Test
  public void activationClearsCloseReasonOfTheActivatedRecords() throws Exception {
    activate("11", "12");

    List<String> statements = executedStatements();
    assertTrue("close_reason aktive edilen kayitlarda NULL'lanmali: " + statements,
        statements.stream().anyMatch(sql -> sql.startsWith("UPDATE result_table SET close_reason = NULL")
            && sql.contains("WHERE id IN (11, 12)")));
  }

  /**
   * Mevcut aktivasyon UPDATE'i DEGISMEDI: ayni kolonlari ayni sekilde temizler. close_reason ona
   * EKLENMEZ (izole calismasi icin).
   */
  @Test
  public void existingActivationStatementIsUnchanged() throws Exception {
    activate("11");

    String activation = executedStatements().get(0);
    assertEquals("UPDATE result_table SET is_disabled = 0, complete_date = NULL, is_fetched = NULL,"
        + " retry_date = NULL, callback_date = NULL, start_date = NULL, answer_date = NULL,"
        + " telephony_result_code = NULL WHERE id IN (11)", activation);
    assertFalse("close_reason aktivasyon UPDATE'inin icinde olmamali", activation.contains("close_reason"));
  }

  /** Custom binding adi aynen kullanilir. */
  @Test
  public void customCloseReasonBindingIsUsedVerbatim() throws Exception {
    campaign.setColResultCloseReason("kapanis_nedeni");

    activate("11");

    assertTrue(executedStatements().stream()
        .anyMatch(sql -> sql.startsWith("UPDATE result_table SET kapanis_nedeni = NULL")));
  }

  /** Binding yoksa kolon adi hicbir sorguya girmez; kolonu olmayan kampanyalar etkilenmez. */
  @Test
  public void withoutBindingNoStatementTouchesCloseReason() throws Exception {
    campaign.setColResultCloseReason(null);

    activate("11");

    List<String> statements = executedStatements();
    assertEquals("Yalniz mevcut aktivasyon UPDATE'i calismali", 1, statements.size());
    assertFalse(statements.get(0).contains("close_reason"));
  }

  /**
   * Temizleme basarisiz olursa (ornek: musterinin NOT NULL close_reason kolonu) aktivasyon
   * BASARILI kalir ve etkilenen kayit sayisi aktivasyon UPDATE'inden gelir.
   */
  @Test
  public void closeReasonClearFailureDoesNotFailActivation() throws Exception {
    when(campaignRecordDao.executeUpdate(eq(campaign), argThat(sql -> sql != null && sql.contains("close_reason = NULL")), anyList()))
        .thenThrow(new SQLException("Cannot insert the value NULL into column 'close_reason'"));

    OperationOutcome<Constants.RECORD_UPDATE_OPERATION_STATUS> outcome = activate("11", "12");

    assertEquals(Constants.RECORD_UPDATE_OPERATION_STATUS.UPDATE_SUCCESSFUL, outcome.getStatus());
    assertEquals(2, outcome.getAffectedCount());
  }

  /** Kampanya calisirken aktivasyon zaten reddediliyor; hicbir sorgu calismaz (degismedi). */
  @Test
  public void runningCampaignStillRejectsActivationWithoutAnyStatement() throws Exception {
    campaign.setState(Constants.CAMPAIGN_STATE.RUNNING);

    OperationOutcome<Constants.RECORD_UPDATE_OPERATION_STATUS> outcome = activate("11");

    assertEquals(Constants.RECORD_UPDATE_OPERATION_STATUS.CAMPAIGN_IS_RUNNING_CANT_UPDATE, outcome.getStatus());
    verify(campaignRecordDao, org.mockito.Mockito.never())
        .executeUpdate(any(Campaign.class), any(String.class), anyList());
  }
}
