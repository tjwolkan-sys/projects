package com.powerdialer.engine;

import com.powerdialer.engine.asterisk.CallerIdManager;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.routing.FctChannelGate;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.ServletContext;
import java.util.Date;
import java.util.Map;

/**
 * Detect and clear calls with unexpected state.
 */
@Component
public class CallCleaner {

  private final LoggerDecorator LOG;

  @Autowired
  private ServletContext context;

  @Autowired
  private CampaignDbOperations dbOperations;

  @Autowired
  private CallUpToDateKeeper callUpToDateKeeper;

  @Autowired
  private CallerIdManager callerIdManager;

  @Autowired(required = false)
  private FctChannelGate fctChannelGate;

  private Map<Integer, TelephonyResultCode> ituToTelephonyResultCodeRelations;

  @Autowired
  public CallCleaner(LoggerDecorator loggerDecorator) {
    this.LOG = loggerDecorator.getLogger(CallCleaner.class);
  }

  private TelephonyResultCode getTelephonyResultCode(Integer code) {
    if (ituToTelephonyResultCodeRelations == null) {
      ituToTelephonyResultCodeRelations = (Map<Integer, TelephonyResultCode>) context.getAttribute(Constants.TELEPHONY_RESULT_CODES);
    }
    return ituToTelephonyResultCodeRelations.get(code);
  }

  /**
   * Clear call that have no interaction during last
   * {@link Constants#CALL_EXPIRE_TIMEOUT} milliseconds
   * @param call - call to clear
   */
  public void clearExpiredCall(Call call) {
    // Bu yol ManagerConnectionDecorator.releaseTelephonySlotIfNeeded funnel'ını KULLANMIYOR;
    // FCT kanal rezervasyonu bu yüzden burada ayrıca bırakılır (release idempotenttir).
    releaseFctChannel(call);
    if (!hasPersistedTelephonyResult(call)) {
      call.setTelephonyResultCode(getTelephonyResultCode(Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode()));
    }
    Campaign campaign = call.getCampaign();
    if (Boolean.TRUE.equals(campaign.getRetryScheduleEnabled()) && campaign.getRetryStrategy() != null) {
      call.getCallOriginator().applyRetryStrategy(call);
    } else {
      completeCall(call);
    }
    call.getCallOriginator().clearCall(call);
    campaign.clearCall(call);
    campaign.removeCallInProgress(call);
    if (call.getStartDate() != null) {
      if (call.getAnswerDate() == null){
        campaign.decrementRingingCalls(call);
      } else {
        campaign.decrementActiveCalls(call);
        if(call.isAnsweredByUser())
          campaign.decrementInCallsSize(call);
      }
    }
    dbOperations.insertCallResultToDB(call);
    call.getCallOriginator().saveCDR(call);
  }

  private boolean hasPersistedTelephonyResult(Call call) {
    return call.getTelephonyResultCode() != null && call.getCdr() != null && call.getCdr().getId() != null;
  }

  /**
   * Clear preview calls that hung. User shouldn't have originated preview call
   * when he available fo dialer call
   * @param user - user to check
   */
  public void clearExpiredPreviewCallsForUser(User user) {
    if (user.getStatus() != null && user.getStatus().isAvailableForDialerCalls()) {
      for (Call call : callUpToDateKeeper.getUserCalls(user)) {
        // FIX-333607: originate edilmemiş çağrıda tarih null; dönüşüm null kontrolünden sonra yapılır.
        if (call.getOriginationDate() == null) {
          continue;
        }
        boolean isCallExpired = new Date().getTime() - Utils.convertDoubleToLongDate(call.getOriginationDate()) > Constants.CALL_EXPIRE_TIMEOUT;
        if (isCallExpired && call.getCampaign().isPreviewCampaign()) {
          clearExpiredCall(call);
          LOG.warn("User " + user.getUsername() + " change status to " + user.getStatus() + " but have originated preview " + call + ". Call will be cleared",
                   Constants.LOG_MODULE.CAMPAIGN,
                   call.getCampaign());
        }
      }
    }
  }

  public void completeCall(Call call) {
    completeCall(call, new Date());
  }

  public void completeCall(Call call, Date date) {
    call.setCompleteDate(Utils.convertDateToDouble(date));
    callerIdManager.onCallComplete(call);
    releaseFctChannel(call);
  }

  /**
   * FCT kanal rezervasyonunu bırakır. Gate bileşeni yoksa (Spring dışında kurulan testler) no-op'tur.
   * Release owner kontrollü ve idempotent olduğu için birden fazla yaşam döngüsü noktasından
   * çağrılması güvenlidir.
   */
  private void releaseFctChannel(Call call) {
    if (fctChannelGate != null) {
      fctChannelGate.release(call);
    }
  }

}
