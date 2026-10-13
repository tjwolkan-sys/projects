package com.powerdialer.util;

import java.util.Collections;
import java.util.List;

public final class ParamSql {
    private final String sql;
    private final List<Object> params;

    public ParamSql(String sql, List<Object> params) {
        this.sql = (sql == null ? "" : sql);
        this.params = (params == null ? Collections.emptyList() : params);
    }

    public String getSql() { return sql; }
    public List<Object> getParams() { return params; }

    public boolean isEmpty() { return sql == null || sql.trim().isEmpty(); }

    public static ParamSql of(String sql) {
        return new ParamSql(sql, Collections.emptyList());
    }

    public static ParamSql of(String sql, List<Object> params) {
        return new ParamSql(sql, params);
    }

    public static ParamSql empty() {
        return new ParamSql("", Collections.emptyList());
    }
}

