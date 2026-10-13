package com.powerdialer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.util.Constants;

/**
 * Test for Call class
 */
public class CallTest {

  private Call call = new Call();
  private Campaign campaign = new Campaign();
  private JSONObject rawData = new JSONObject();
  private List<PhoneNumberColumn> numberColumnsInRawData = new ArrayList<>();

  @Before
  public void init() {
    campaign.setColCallFromTime("call_from_time");
    campaign.setColCallToTime("call_to_time");
    campaign.setColResultCalledNumber("called_number");

    rawData.put("call_from_time", "0.1");
    rawData.put("call_to_time", "0.2");

    rawData.put("called_number", "0931606104");
    rawData.put("phone_number_1", "0931606104");
    rawData.put("call_from_time_1", "0.2");
    rawData.put("call_till_time_1", "0.3");

    rawData.put("phone_number_2", "0635444626");
    rawData.put("call_from_time_2", "0.3");
    rawData.put("call_till_time_2", "0.4");

    rawData.put("phone_number_3", "");

    call.setCalledNumber("0931606104");
    rawData.put(campaign.getColResultCalledNumber(), "0931606104");

    call.setRawData(rawData);
    call.setCampaign(campaign);

    PhoneNumberColumn phoneNumberColumn1 = new PhoneNumberColumn();
    phoneNumberColumn1.setColNumber("phone_number_1");
    phoneNumberColumn1.setColCallFromTime("call_from_time_1");
    phoneNumberColumn1.setColCallTillTime("call_till_time_1");
    phoneNumberColumn1.setEnabled(true);

    PhoneNumberColumn phoneNumberColumn2 = new PhoneNumberColumn();
    phoneNumberColumn2.setColNumber("phone_number_2");
    phoneNumberColumn2.setColCallFromTime("call_from_time_2");
    phoneNumberColumn2.setColCallTillTime("call_till_time_2");
    phoneNumberColumn2.setEnabled(true);

    PhoneNumberColumn phoneNumberColumn3 = new PhoneNumberColumn();
    phoneNumberColumn3.setColNumber("phone_number_3");
    phoneNumberColumn3.setColCallFromTime("call_from_time_3");
    phoneNumberColumn3.setColCallTillTime("call_till_time_3");
    phoneNumberColumn3.setEnabled(true);

    numberColumnsInRawData.add(phoneNumberColumn1);
    numberColumnsInRawData.add(phoneNumberColumn2);
    numberColumnsInRawData.add(phoneNumberColumn3);
    campaign.setPhoneNumberColumns(numberColumnsInRawData);
  }

  /**
   * Test extracting next number to call
   */
  @Test
  public void testNextNumber() {
    assertEquals("0635444626", call.getNextNumberFromRawData());
  }

  /**
   * Test extracting next number to call while previous number was last in list
   */
  @Test
  public void testNextNumberWhenLastNumber() {
    call.setRetryNumber("0635444626");
    assertEquals("0931606104", call.getNextNumberFromRawData());
  }

  /**
   * Test extracting previous number called number
   */
  @Test
  public void testPreviousNumber() {
    call.setRetryNumber("0635444626");
    assertEquals("0931606104", call.getPreviousNumberFromRawData());
  }

  /**
   * Test extracting previous called number when current if first
   */
  @Test
  public void testPreviousNumberWhenFirstNumber() {
    call.setRetryNumber("0931606104");
    assertEquals("0635444626", call.getPreviousNumberFromRawData());
  }

  /**
   * Test extracting call from time while campaign have simple timezone
   */
  @Test
  public void testExtractingFromTimeForSimpleTimezone() {
    campaign.setTimezoneType(Constants.TIMEZONE_TYPE.SIMPLE);
    Double callFromTimeFromRawData = call.getCallFromTimeFromRawData();
    assertEquals(0.1D, callFromTimeFromRawData, 0.01);
  }

  /**
   * Test extracting call to time while campaign have simple timezone
   */
  @Test
  public void testExtractingToTimeForSimpleTimezone() {
    campaign.setTimezoneType(Constants.TIMEZONE_TYPE.SIMPLE);
    Double callFromTimeFromRawData = call.getCallToTimeFromRawData();
    assertEquals(0.2D, callFromTimeFromRawData, 0.01);
  }

  /**
   * Test extracting call from time while campaign have advanced timezone
   */
  @Test
  public void testExtractingFromTimeForAdvancedTimezone() {
    campaign.setTimezoneType(Constants.TIMEZONE_TYPE.ADVANCED);
    call.setCalledNumber("0931606104");
    Double callFromTimeFromRawData = call.getCallFromTimeFromRawData();
    assertEquals(0.2D, callFromTimeFromRawData, 0.01);
  }

  /**
   * Test extracting call from time while campaign have simple timezone
   */
  @Test
  public void testExtractingToTimeForAdvancedTimezone() {
    campaign.setTimezoneType(Constants.TIMEZONE_TYPE.ADVANCED);
    call.setCalledNumber("0635444626");
    Double callFromTimeFromRawData = call.getCallToTimeFromRawData();
    assertEquals(0.4D, callFromTimeFromRawData, 0.01);
  }

  /**
   * Test extracting call from time while campaign have advanced timezone
   */
  @Test
  public void testExtractingFromTimeForAdvancedTimezoneForCallWithCustomCallbackNumber() {
    campaign.setTimezoneType(Constants.TIMEZONE_TYPE.ADVANCED);
    call.setCalledNumber("custom_callback_number");
    rawData.put("called_number", "0931606104");
    Double callFromTimeFromRawData = call.getCallFromTimeFromRawData();
    assertEquals(0.2D, callFromTimeFromRawData, 0.01);
  }

  @Test
  public void givenNoCallBackDateInRawDataWhenGetUserIdAppointedToCallbackThenReturnUserId() {
    campaign.setAllowCallback(true);
    campaign.setCallbackToAppointer(true);
    campaign.setColResultUserId("user_id");
    campaign.setAppointerReset(true);
    campaign.setAppointerResetTimeout(10L);
    campaign.setColResultCallbackDate("callback_date");
    rawData.put("user_id", "999");

    assertThat(call.getUserIdAppointedToCallback(), is(999L));
  }

  @Test
  public void ensureCallProcessStartsWithFreshAddsFreshOnlyOnce() {
    campaign.setColResultCallProcess("call_process");

    call.ensureCallProcessStartsWithFresh();
    call.ensureCallProcessStartsWithFresh();

    assertEquals(1, call.getCallProcessHistory().size());
    assertEquals("F", call.getCallProcessHistory().get(0));
  }

  // isOwnerBoundCallback(): GAMMA sembolik on-atama + answer reselect'ten muaf tutulacak callback tespiti.
  // CallOriginator.processCall'daki isAppointedToUser || isAssignedToSameUser kosuluyla birebir olmali.

  @Test
  public void appointedCallback_isOwnerBoundCallback_true() {
    // Redirect-to-me (appointed): userIdAppointedToCallback dolu -> owner-bound.
    campaign.setAllowCallback(true);
    campaign.setCallbackToAppointer(true);
    campaign.setColResultUserId("user_id");
    campaign.setAppointerReset(true);
    campaign.setAppointerResetTimeout(10L);
    campaign.setColResultCallbackDate("callback_date");
    rawData.put("user_id", "999");

    assertThat(call.isOwnerBoundCallback(), is(true));
  }

  @Test
  public void sameUserCallback_isOwnerBoundCallback_true() {
    // ASSIGN_TO_SAME_USER + allowOverwriteByWebphone + userId dolu + appointer timeout yok -> owner-bound.
    // (Appointed kosullari kurulmaz: getUserIdAppointedToCallback null kalir; sameUser dalindan true doner.)
    campaign.setColResultUserId("user_id");
    campaign.setAllowOverwriteByWebphone(true);
    campaign.setAppointerReset(false);
    call.setCallbackType(Constants.CALLBACKTYPE.ASSIGN_TO_SAME_USER);
    rawData.put("user_id", "999");

    assertThat(call.isOwnerBoundCallback(), is(true));
  }

  @Test
  public void freshCall_isOwnerBoundCallback_false() {
    // Callback konfigurasyonu yok (appointed null, callbackType null) -> owner-bound DEGIL (fresh/retry).
    assertThat(call.isOwnerBoundCallback(), is(false));
  }

}
