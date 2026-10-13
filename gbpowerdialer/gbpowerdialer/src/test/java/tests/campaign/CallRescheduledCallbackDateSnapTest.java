package tests.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.json.JSONObject;
import org.junit.Test;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Utils;

/**
 * FEAT-321997 (ATTEMPT_COUNT): Call.computeRescheduledCallbackDate(baseMs) reschedule edilen
 * callback_date'i OLE serial double olarak hesaplar. TIMEZONE_TYPE.OFF veya offset/pencere bilgisi
 * yoksa base aynen kullanılır; timezone tanımlı + base arama penceresi içindeyse yine base; pencere
 * dışına düşerse sonuç bir sonraki pencere açılışına snap edilir (base'ten ileri).
 */
public class CallRescheduledCallbackDateSnapTest {

  /** Deterministik bir an: 2026-06-25 06:00:00 UTC. UTC time-of-day fraction = 0.25. */
  private static final long BASE_MS = baseUtcMillis();

  @Test
  public void returnsBaseDoubleWhenTimezoneTypeOff() {
    Campaign campaign = new Campaign();
    campaign.setTimezoneType(Constants.TIMEZONE_TYPE.OFF);
    Call call = new Call();
    call.setCampaign(campaign);
    call.setRawData(new JSONObject());

    double result = call.computeRescheduledCallbackDate(BASE_MS);

    assertEquals(Utils.convertDateToDouble(new Date(BASE_MS)), result, 0D);
  }

  @Test
  public void returnsBaseDoubleWhenSimpleTimezoneAndBaseInsideWindow() {
    Campaign campaign = simpleTimezoneCampaign();
    Call call = new Call();
    call.setCampaign(campaign);
    // offset 0 -> localFraction == UTC fraction (0.25). Pencere [0.0, 0.5] -> içeride.
    call.setRawData(new JSONObject()
        .put("tz_offset", 0)
        .put("call_from", 0.0D)
        .put("call_to", 0.5D));

    double result = call.computeRescheduledCallbackDate(BASE_MS);

    assertEquals(Utils.convertDateToDouble(new Date(BASE_MS)), result, 0D);
  }

  @Test
  public void snapsForwardWhenSimpleTimezoneAndBaseOutsideWindow() {
    Campaign campaign = simpleTimezoneCampaign();
    Call call = new Call();
    call.setCampaign(campaign);
    // offset 0 -> localFraction == 0.25. Pencere [0.5, 0.75] -> dışarıda, sonraki açılış 0.5.
    call.setRawData(new JSONObject()
        .put("tz_offset", 0)
        .put("call_from", 0.5D)
        .put("call_to", 0.75D));

    double base = Utils.convertDateToDouble(new Date(BASE_MS));
    double result = call.computeRescheduledCallbackDate(BASE_MS);

    assertTrue("Pencere dışı base ileri bir pencere açılışına snap edilmeli: base=" + base + " result=" + result,
        result > base);
  }

  private Campaign simpleTimezoneCampaign() {
    Campaign campaign = new Campaign();
    campaign.setTimezoneType(Constants.TIMEZONE_TYPE.SIMPLE);
    campaign.setColTimezoneOffset("tz_offset");
    campaign.setColCallFromTime("call_from");
    campaign.setColCallToTime("call_to");
    return campaign;
  }

  private static long baseUtcMillis() {
    java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
    cal.clear();
    cal.set(2026, java.util.Calendar.JUNE, 25, 6, 0, 0);
    return cal.getTimeInMillis();
  }
}
