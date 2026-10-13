package tests.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;

/**
 * FEAT-321997 (ATTEMPT_COUNT): Call.getCallbackAttempts() agent-callback deneme sayacını result
 * tablosundaki col_result_callback_attempts kolonundan lazy okur. Her fetch'te yeni Call kurulduğu
 * için değer rawData'dan hidrate edilir; kolon adı tanımlı değilse veya rawData'da yoksa 0 döner.
 * Bu testler reschedule UPDATE'inin optimistic guard'ında kullanılan expectedOld kaynağını kilitler.
 */
public class CallCallbackAttemptsHydrationTest {

  @Test
  public void readsCallbackAttemptsFromRawDataWhenColumnConfigured() {
    Campaign campaign = new Campaign();
    campaign.setColResultCallbackAttempts("callback_attempts");
    Call call = new Call();
    call.setCampaign(campaign);
    call.setRawData(new JSONObject().put("callback_attempts", 2));

    assertEquals(2, call.getCallbackAttempts());
  }

  @Test
  public void returnsZeroWhenCallbackAttemptsColumnNotConfigured() {
    Campaign campaign = new Campaign();
    // col_result_callback_attempts tanımlı değil (null) -> sayım yapılamaz.
    Call call = new Call();
    call.setCampaign(campaign);
    call.setRawData(new JSONObject().put("callback_attempts", 5));

    assertEquals(0, call.getCallbackAttempts());
  }

  @Test
  public void returnsZeroWhenColumnConfiguredButAbsentFromRawData() {
    Campaign campaign = new Campaign();
    campaign.setColResultCallbackAttempts("callback_attempts");
    Call call = new Call();
    call.setCampaign(campaign);
    // rawData kolonu içermiyor (taze kayıt) -> 0.
    call.setRawData(new JSONObject().put("id", "42"));

    assertEquals(0, call.getCallbackAttempts());
  }

  /**
   * FEAT-321997 takip (29.06.2026): ASSIGN_TO_SAME_USER callback ATTEMPT_COUNT ile havuza devredilince
   * result tablosunda user_id=NULL olur. Call.getUserId() bu durumda exception ATMAMALI (eski hali
   * Long.valueOf("null") / JSONException ile patlıyordu) → null dönmeli; aksi halde ACD findSameUser'da takılır.
   */
  @Test
  public void getUserIdReadsValueWhenColumnPresent() {
    Campaign campaign = new Campaign();
    campaign.setColResultUserId("user_id");
    Call call = new Call();
    call.setCampaign(campaign);
    call.setRawData(new JSONObject().put("user_id", "7"));

    assertEquals(Long.valueOf(7L), call.getUserId());
  }

  @Test
  public void getUserIdReturnsNullWhenColumnIsJsonNull() {
    Campaign campaign = new Campaign();
    campaign.setColResultUserId("user_id");
    Call call = new Call();
    call.setCampaign(campaign);
    // Havuza devredilen callback: user_id DB'de NULL -> rawData'da JSONObject.NULL.
    call.setRawData(new JSONObject().put("user_id", JSONObject.NULL));

    assertNull(call.getUserId());
  }

  @Test
  public void getUserIdReturnsNullWhenColumnAbsent() {
    Campaign campaign = new Campaign();
    campaign.setColResultUserId("user_id");
    Call call = new Call();
    call.setCampaign(campaign);
    call.setRawData(new JSONObject().put("id", "42")); // user_id kolonu yok

    assertNull(call.getUserId());
  }
}
