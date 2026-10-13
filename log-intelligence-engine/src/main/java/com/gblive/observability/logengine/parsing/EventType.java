package com.gblive.observability.logengine.parsing;

public enum EventType {

    // --- Asterisk / Telephony ---
    AST_CONNECTED,
    AST_DISCONNECTED,
    AST_LOGIN_ATTEMPT,
    AST_CONNECTION_REFUSED,

    // --- Call lifecycle ---
    CALL_CREATED,
    CALL_PRODUCTION_ASSIGNED,
    CALL_DRAINED,
    CALL_RESULT_MISSING,

    // --- Campaign ---
    CAMPAIGN_CALLS_FETCHED,

    // --- Infra / App ---
    DB_WARNING,
    DEPRECATION_WARNING,

    // --- Fallback ---
    UNKNOWN
}
