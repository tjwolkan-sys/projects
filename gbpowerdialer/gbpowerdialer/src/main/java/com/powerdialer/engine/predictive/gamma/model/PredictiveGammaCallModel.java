// com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallModel
package com.powerdialer.engine.predictive.gamma.model;

import com.powerdialer.util.Constants;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PredictiveGammaCallModel {

    private final AtomicLong cdrId = new AtomicLong(0L);
    private final AtomicLong resultCode = new AtomicLong(0L);
    private final AtomicBoolean isAnswered = new AtomicBoolean(false);
    private final AtomicBoolean isAbandoned = new AtomicBoolean(false);
    private final AtomicLong talkTime = new AtomicLong(0L);
    private final AtomicLong ringTime = new AtomicLong(0L);
    private Constants.CALL_TYPES callType;

    public long getCdrId() { return cdrId.get(); }
    public void setCdrId(long cdrId) { this.cdrId.set(cdrId); }

    public long getResultCode() { return resultCode.get(); }
    public void setResultCode(long resultCode) { this.resultCode.set(resultCode); }

    public boolean isAnswered() { return isAnswered.get(); }
    public void setAnswered(boolean isAnswered) { this.isAnswered.set(isAnswered); }

    public boolean isAbandoned() { return isAbandoned.get(); }
    public void setAbandoned(boolean isAbandoned) { this.isAbandoned.set(isAbandoned); }

    public long getTalkTime() { return talkTime.get(); }
    public void setTalkTime(long talkTime) { this.talkTime.set(talkTime); }

    public long getRingTime() { return ringTime.get(); }
    public void setRingTime(long ringTime) { this.ringTime.set(ringTime); }

    public Constants.CALL_TYPES getCallType() { return callType; }
    public void setCallType(Constants.CALL_TYPES callType) { this.callType = callType; }

    @Override
    public String toString() {
        return "PredictiveGammaCallModel{" +
                "cdrId=" + cdrId +
                "resultCode=" + resultCode.get() +
                "isAnswered=" + isAnswered.get() +
                "isAbandoned=" + isAbandoned.get() +
                ", talkTime=" + talkTime.get() +
                ", ringTime=" + ringTime.get() +
                ", callType=" + callType.name() +
                '}';
    }
}
