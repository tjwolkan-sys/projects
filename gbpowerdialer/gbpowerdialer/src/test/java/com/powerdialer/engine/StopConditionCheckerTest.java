package com.powerdialer.engine;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Before;
import org.junit.Test;

import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

public class StopConditionCheckerTest {

  private static final long THRESHOLD_VALUE = 2L;
  private static final long LESS_THAN_THRESHOLD_VALUE = 1L;
  private static final long GREATER_THAN_THRESHOLD_VALUE = 3L;
  private static final long RESULT_CODE_ID = 100L;

  private Campaign campaign = new Campaign();
  private StopConditionChecker stopConditionChecker = new StopConditionChecker();

  @Before
  public void setUp() {
    campaign.setStopConditionEnabled(true);
    campaign.setStopConditionThreshold(THRESHOLD_VALUE);
  }

  @Test
  public void touchedContactsStopConditionTest() {
    testTouchedContactsStopCondition(LESS_THAN_THRESHOLD_VALUE, false);
    testTouchedContactsStopCondition(THRESHOLD_VALUE, true);
    testTouchedContactsStopCondition(GREATER_THAN_THRESHOLD_VALUE, true);
  }

  @Test
  public void completedContactsStopConditionTest() {
    testCompletedContactsStopCondition(LESS_THAN_THRESHOLD_VALUE, false);
    testCompletedContactsStopCondition(THRESHOLD_VALUE, true);
    testCompletedContactsStopCondition(GREATER_THAN_THRESHOLD_VALUE, true);
  }

  @Test
  public void userResultCodesStopConditionTest() {
    testUserResultCodeStopCondition(LESS_THAN_THRESHOLD_VALUE, false);
    testUserResultCodeStopCondition(THRESHOLD_VALUE, true);
    testUserResultCodeStopCondition(GREATER_THAN_THRESHOLD_VALUE, true);
  }

  @Test
  public void telephonyResultCodesStopConditionTest() {
    testTelephonyResultCodeStopCondition(LESS_THAN_THRESHOLD_VALUE, false);
    testTelephonyResultCodeStopCondition(THRESHOLD_VALUE, true);
    testTelephonyResultCodeStopCondition(GREATER_THAN_THRESHOLD_VALUE, true);
  }

  private void testTouchedContactsStopCondition(long value, boolean expectedResult) {
    campaign.setStopConditionParameter(Constants.STOP_CONDITION_PARAMETER.TOUCHED_CONTACTS_COUNT);
    campaign.setTouchedContacts(value);

    boolean result = stopConditionChecker.isStopConditionReached(campaign);

    assertThat(result, is(expectedResult));
  }

  private void testCompletedContactsStopCondition(long value, boolean expectedResult) {
    campaign.setStopConditionParameter(Constants.STOP_CONDITION_PARAMETER.COMPLETED_CONTACTS_COUNT);
    campaign.setCompletedContacts(value);

    boolean result = stopConditionChecker.isStopConditionReached(campaign);

    assertThat(result, is(expectedResult));
  }

  private void testUserResultCodeStopCondition(long value, boolean expectedResult) {
    campaign.setStopConditionParameter(Constants.STOP_CONDITION_PARAMETER.USER_RC_COUNT);
    campaign.setStopConditionValue(RESULT_CODE_ID);
    for (int i = 1; i <= value; i++)
      campaign.incrementUserResultCode(RESULT_CODE_ID);

    boolean result = stopConditionChecker.isStopConditionReached(campaign);

    assertThat(result, is(expectedResult));
  }

  private void testTelephonyResultCodeStopCondition(long value, boolean expectedResult) {
    campaign.setStopConditionParameter(Constants.STOP_CONDITION_PARAMETER.TELEPHONY_RC_COUNT);
    campaign.setStopConditionValue(RESULT_CODE_ID);
    for (int i = 1; i <= value; i++)
      campaign.incrementTelephonyResultCode(RESULT_CODE_ID);

    boolean result = stopConditionChecker.isStopConditionReached(campaign);

    assertThat(result, is(expectedResult));
  }

}
