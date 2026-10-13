package com.gblive.observability.logengine.failure.model;

public enum RootCauseType {
    ASTERISK_CONN_REFUSED,
    DB_TIMEOUT,
    REDIS_BLOCK,
    CALL_PRODUCER_WARN,
    UNKNOWN
}
