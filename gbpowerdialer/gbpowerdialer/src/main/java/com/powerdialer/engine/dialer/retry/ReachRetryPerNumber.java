package com.powerdialer.engine.dialer.retry;

import java.util.List;

import org.apache.commons.collections4.ListUtils;

/**
 * Reach retry limit before next number, (Reach retry rules on one number before
 * starting next one. Dialing ends when all numbers exceeded)
 */
public class ReachRetryPerNumber extends RetryStrategy {

  @Override
  public boolean isOverallRetryLimitReached() {
    // countAllCallTypes global attempt limitini EK kosul olarak uygular; aranabilir
    // numara kontrolunu asla atlamaz. Aksi halde tum numaralar DNC/limit disi
    // kalinca setRetryNumber yuruyusu cikissiz kalir (03.08.2026 sonsuz dongu vakasi).
    if (campaign.getCountAllCallTypes() != null && campaign.getCountAllCallTypes()
        && call.getAttempts() >= campaign.getRetryOveralAttempt()) {
      return true;
    }

    List<String> dncExcludedNumbers = ListUtils.removeAll(call.getNumbersInRawData(), call.getDNCNumbers());

    boolean anyNumberPresent = dncExcludedNumbers.stream()
            .anyMatch(number -> !isRetryLimitForNumberReached(number));

    return !anyNumberPresent;
  }

  @Override
  public void setRetryNumber() {
    if (isRetryLimitForNumberReached(call.getCalledNumber()))
      call.setRetryNumber(call.getNextNumberFromRawData());

    if (call.getRetryNumber() == null)
      call.setRetryNumber(call.getCalledNumber());

    processRetryNumberWithDNC();
  }

  @Override
  public void processRetryNumberWithDNC() {
    advanceRetryNumberPastIneligible(number -> call.getDNCNumbers().contains(number) || isRetryLimitForNumberReached(number));
  }

  /**
   * Task 325559 (K2): bu strateji numara basina limiti ZATEN uyguluyor; limit alani olarak
   * {@code retryOveralAttempt} kullaniyor. Yeni {@code maxAttemptPerNumber} alani burada bilincli
   * olarak YOK SAYILIR, boylece cift limit cakismasi hic dogmaz ve mevcut Strateji 1 musterilerinin
   * davranisi degismez. UI'da alan ayrica freeze edilir.
   */
  @Override
  protected boolean isPerNumberLimitActive() {
    return false;
  }

  private boolean isRetryLimitForNumberReached(String phoneNumber) {
    int calledNumberRetryCount = (int) call.getRetryHistory().stream()
        .filter(number -> number.equals(phoneNumber))
        .count();
    return calledNumberRetryCount >= campaign.getRetryOveralAttempt();
  }

}
