package tests.campaign.db;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.engine.priority.PriorityService;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

/**
 * FEAT-321997 (ATTEMPT_COUNT): CampaignDbOperations.rescheduleAppointedCallback'ın ürettiği tek atomik
 * UPDATE SQL'ini kilitler. Sahip AVAILABLE değilken (online-meşgul VEYA offline) her çağrıda deneme +1
 * ve reschedule; eşiğe ulaşınca user_id=NULL + callback_date=now + callback_attempts=0 ile havuza devir.
 *
 * col_result_callback_attempts ve eşik tanımlı: CASE ifadesi (eşikte convert THEN branch) ve WHERE'de
 * optimistic guard (AND callback_attempts = expectedOld) bulunur.
 *
 * col_result_callback_attempts null: sayım yapılamaz; yalnız reschedule (callback_date + is_fetched),
 * CASE/guard/user_id yok. callback_type'a hiç dokunulmaz.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AppointedCallbackRescheduleSqlRegressionTest {

  @Mock
  private DBEngine dbEngine;
  @Mock
  private LoggerDecorator loggerDecorator;
  @Mock
  private ACDEngine acdEngine;
  @Mock
  private EnCipherUtils enCipherUtils;
  @Mock
  private PriorityService priorityService;

  private CampaignDbOperations dbOperations;

  @Before
  public void setUp() {
    when(loggerDecorator.getLogger(CampaignDbOperations.class)).thenReturn(loggerDecorator);
    when(enCipherUtils.decryptPassword(anyString())).thenAnswer(inv -> inv.getArgument(0));
    dbOperations = new CampaignDbOperations(dbEngine, loggerDecorator, acdEngine, enCipherUtils, priorityService);
  }

  @Test
  public void rescheduleWithThresholdEmitsCaseGuardAndConvertBranch() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setColResultCallbackAttempts("callback_attempts");
    campaign.setAgentCbConvertAfterAttempts(3);
    campaign.setAgentCbRescheduleDelayMin(20);
    Call call = call(campaign);
    // rawData'da mevcut deneme sayısı 1 -> optimistic guard expectedOld = 1.
    call.setRawData(new org.json.JSONObject().put("id", "42").put("callback_attempts", 1));

    dbOperations.rescheduleAppointedCallback(campaign, call);

    String sql = capturedUpdate();
    assertTrue("eşik CASE ifadesi COALESCE ile NULL-güvenli olmalı: " + sql, sql.contains("CASE WHEN COALESCE(callback_attempts, 0) >= 3"));
    // off-by-one regresyon: eşik karşılaştırması ESKİ sayaçla (>= N) yapılır, "+1 >= N" ile DEĞİL → sayaç N'e ulaşır.
    assertFalse("eşik karşılaştırması +1 içermemeli (convert AFTER N attempts): " + sql, sql.contains("+ 1 >= 3"));
    // Eşikte convert: callback_attempts sıfırlanır, ELSE COALESCE+1 (sayaç N'e kadar artar).
    assertTrue("convert THEN dalı callback_attempts=0/ELSE COALESCE+1 olmalı: " + sql, sql.contains("THEN 0 ELSE COALESCE(callback_attempts, 0) + 1 END"));
    assertTrue("user_id CASE ile koşullu temizlenmeli: " + sql, sql.contains("user_id = CASE"));
    assertTrue("convert THEN dalı user_id=NULL olmalı: " + sql, sql.contains("THEN NULL ELSE user_id END"));
    assertTrue("is_fetched temizlenmeli: " + sql, sql.contains("is_fetched = NULL"));
    assertTrue("optimistic guard COALESCE'lı expectedOld ile olmalı: " + sql, sql.contains("AND COALESCE(callback_attempts, 0) = 1"));
    assertFalse("callback_type'a dokunulmamalı: " + sql, sql.contains("callback_type"));
  }

  /**
   * Regresyon (saha bug'ı, 26.06.2026): callback_attempts kolonu DB'de NULL iken (callback'i kuran disposition
   * 0'a set etmiyor, eski satırlar da NULL). Çıplak kolon kullanılsaydı guard "callback_attempts = 0" NULL'u
   * eşleştirmez (NULL = 0 -> unknown) ve NULL + 1 = NULL olurdu; UPDATE 0 satır eder, callback is_fetched dolu
   * kalıp sonsuza dek takılırdı. COALESCE(col,0) hem guard'ı hem aritmetiği NULL-güvenli yapar.
   */
  @Test
  public void nullAttemptsColumnGuardAndArithmeticUseCoalesce() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setColResultCallbackAttempts("callback_attempts");
    campaign.setAgentCbConvertAfterAttempts(2);
    campaign.setAgentCbRescheduleDelayMin(20);
    Call call = call(campaign); // rawData'da callback_attempts yok -> getCallbackAttempts() 0 -> expectedOld = 0.

    dbOperations.rescheduleAppointedCallback(campaign, call);

    String sql = capturedUpdate();
    assertTrue("guard COALESCE = expectedOld(0) ile NULL satırı eşleştirmeli: " + sql, sql.contains("AND COALESCE(callback_attempts, 0) = 0"));
    assertTrue("eşik karşılaştırması COALESCE'lı ve +1'siz (ESKİ sayaç >= N) olmalı: " + sql, sql.contains("COALESCE(callback_attempts, 0) >= 2"));
    assertTrue("ELSE dalı sayacı COALESCE ile artırmalı: " + sql, sql.contains("ELSE COALESCE(callback_attempts, 0) + 1 END"));
    assertFalse("çıplak 'callback_attempts + 1' (NULL+1=NULL tuzağı) kullanılmamalı: " + sql, sql.contains("callback_attempts + 1"));
    assertFalse("çıplak 'callback_attempts = 0' guard'ı (NULL eşleşmez) kullanılmamalı: " + sql, sql.contains("AND callback_attempts = 0"));
  }

  @Test
  public void withoutAttemptsColumnOnlyReschedulesWithoutCaseOrGuard() throws SQLException {
    Campaign campaign = baseCampaign();
    // col_result_callback_attempts null -> sayım yapılamaz; convert/guard yok.
    campaign.setAgentCbRescheduleDelayMin(20);
    Call call = call(campaign);

    dbOperations.rescheduleAppointedCallback(campaign, call);

    String sql = capturedUpdate();
    assertTrue("yalnız reschedule callback_date set etmeli: " + sql, sql.contains("callback_date ="));
    assertTrue("is_fetched temizlenmeli: " + sql, sql.contains("is_fetched = NULL"));
    assertFalse("kolon yokken CASE üretilmemeli: " + sql, sql.contains("CASE"));
    assertFalse("kolon yokken optimistic guard olmamalı: " + sql, sql.contains("AND callback_attempts ="));
    assertFalse("kolon yokken user_id temizlenmemeli: " + sql, sql.contains("user_id ="));
    assertFalse("callback_type'a dokunulmamalı: " + sql, sql.contains("callback_type"));
  }

  private String capturedUpdate() throws SQLException {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(dbEngine).executeUpdate(captor.capture(), any(), any(), any(), any());
    return captor.getValue();
  }

  private Call call(Campaign campaign) {
    Call call = new Call();
    call.setUniqueKey("42");
    call.setCampaign(campaign);
    call.setRawData(new org.json.JSONObject().put("id", "42"));
    return call;
  }

  private Campaign baseCampaign() {
    Campaign c = new Campaign();
    c.setId(74L);
    c.setDbConnectionString("jdbc:sqlserver://localhost:1433;databaseName=test");
    c.setDbUsername("u");
    c.setDbPassword("p");
    c.setDbListTable("TC_TEST");
    c.setDbResultTable("TC_TEST");
    c.setColUniqueKey("id");
    c.setColResultUniqueKey("id");
    c.setColResultFetchedDataFlag("is_fetched");
    c.setColResultCallbackDate("callback_date");
    c.setColResultUserId("user_id");
    // Timezone OFF -> computeRescheduledCallbackDate base'i aynen kullanır (deterministik).
    c.setTimezoneType(Constants.TIMEZONE_TYPE.OFF);
    return c;
  }
}
