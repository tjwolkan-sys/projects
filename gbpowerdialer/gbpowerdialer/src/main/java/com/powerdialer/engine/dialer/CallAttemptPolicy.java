package com.powerdialer.engine.dialer;

import org.apache.commons.collections4.ListUtils;

import com.powerdialer.model.Call;
import com.powerdialer.util.Constants;

public final class CallAttemptPolicy {

  private static final Long SUCCESS_TRC_NORMAL_CLEARING = 10L;
  private static final Long SUCCESS_TRC_NORMAL_UNSPECIFIED = 24L;
  // Belirsiz Kayıt (327734): görüşülmüş çağrıdır, attempt sayımında başarılı kapanışla eşdeğerdir.
  private static final Long TRC_AMBIGUOUS_RECORD = 85L;

  private CallAttemptPolicy() {
  }

  public static void incrementForOrigination(Call call) {
    if (shouldIncrementForOrigination(call)) {
      call.snapshotAttemptsBeforeOrigination();
      call.incrementAttempts();
    }
  }

  public static void incrementForSuccessfulCompletionIfNeeded(Call call) {
    if (shouldIncrementForSuccessfulCompletion(call))
      call.incrementAttempts();
  }

  public static boolean restoreForNonAttemptResultIfNeeded(Call call) {
    return call != null && call.restoreAttemptsBeforeOriginationForNonAttemptResult();
  }

  public static boolean restoreForDeferredOriginationIfNeeded(Call call) {
    return call != null && call.restoreAttemptsBeforeOrigination();
  }

  static boolean shouldIncrementForOrigination(Call call) {
    if (call == null || call.getCampaign() == null)
      return false;

    if (call.isCallback())
      return true;

    if (!Constants.RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER.equals(call.getCampaign().getRetryStrategy()))
      return true;

    int usableNumberCount = ListUtils.removeAll(call.getNumbersInRawData(), call.getDNCNumbers()).size();
    if (usableNumberCount <= 1)
      return true;

    int currentCallNumberInCycle = call.getRetryHistory().size() + 1;
    return currentCallNumberInCycle % usableNumberCount == 0;
  }

  static boolean shouldIncrementForSuccessfulCompletion(Call call) {
    return call != null
        && call.getCampaign() != null
        && isSuccessfulTelephonyResult(call)
        && !call.isCallback()
        && !shouldIncrementForOrigination(call);
  }

  private static boolean isSuccessfulTelephonyResult(Call call) {
    if (call == null || call.getTelephonyResultCode() == null || call.getTelephonyResultCode().getId() == null)
      return false;

    Long telephonyResultCodeId = call.getTelephonyResultCode().getId();
    return SUCCESS_TRC_NORMAL_CLEARING.equals(telephonyResultCodeId)
        || SUCCESS_TRC_NORMAL_UNSPECIFIED.equals(telephonyResultCodeId)
        || TRC_AMBIGUOUS_RECORD.equals(telephonyResultCodeId);
  }

}
