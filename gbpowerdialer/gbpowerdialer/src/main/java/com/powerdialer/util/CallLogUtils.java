package com.powerdialer.util;

import org.slf4j.MDC;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants.LOG_MODULE;

public final class CallLogUtils {

    // MDC keys (logback pattern ile uyumlu olmalı)
    public static final String MDC_CALL_ID       = "callId";
    public static final String MDC_CALLED_NUMBER = "calledNumber";
    public static final String MDC_CAMPAIGN_ID   = "campaignId";
    public static final String MDC_UNIQUE_KEY    = "uniqueKey";
    public static final String MDC_CALL_TYPE     = "callType";

    private CallLogUtils() {}

    public static void setMDC(Call call) {
        if (call == null) return;

        Campaign c = call.getCampaign();

        MDC.put(MDC_CALL_ID,       call.getId() != null ? call.getId() : "null");
        MDC.put(MDC_CALLED_NUMBER, call.getCalledNumber() != null ? call.getCalledNumber() : "NULL");
        MDC.put(MDC_CAMPAIGN_ID,   c != null && c.getId() != null ? String.valueOf(c.getId()) : "null");
        MDC.put(MDC_UNIQUE_KEY,    call.getUniqueKey() != null ? call.getUniqueKey() : "null");
        MDC.put(MDC_CALL_TYPE,     call.getCallType() != null ? call.getCallType().name() : "null");
    }

    public static void clearMDC() {
        MDC.remove(MDC_CALL_ID);
        MDC.remove(MDC_CALLED_NUMBER);
        MDC.remove(MDC_CAMPAIGN_ID);
        MDC.remove(MDC_UNIQUE_KEY);
        MDC.remove(MDC_CALL_TYPE);
    }

    public static void warnIfCalledNumberNull(Call call, String stage, LoggerDecorator LOG, LOG_MODULE module) {
        if (call != null && call.getCalledNumber() == null) {
            // Alanları tekrar loga basmıyoruz; MDC taşıyacak.
            // stage'i kısa ve grep-friendly tutuyoruz.
            LOG.warn("CALLED_NUMBER_NULL stage=" + stage, module, call.getCampaign());
        }
    }
}