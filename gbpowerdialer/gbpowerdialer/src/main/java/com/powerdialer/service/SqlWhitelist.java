package com.powerdialer.service;

import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.ParametrizedQuery;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class SqlWhitelist {

    public final String table;
    public final Set<String> columns;

    private SqlWhitelist(String table, Set<String> columns) {
        this.table = table;
        this.columns = columns;
    }

    public static SqlWhitelist load(DBEngine db, Long ownerId, String url, String user, String pwd, String tableName) throws Exception {
        String t = normalizeTable(tableName);
        if (t == null) throw new IllegalArgumentException("Invalid table name");

        final String q =
                "SELECT COLUMN_NAME " +
                        "FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_NAME = ?";

        final JSONArray rs = db.executeParametrizedQuery(
                new ParametrizedQuery(q, Collections.singletonList(t)),
                ownerId, Integer.MAX_VALUE, url, user, pwd
        );

        final HashSet<String> cols = new HashSet<>();
        for (int i = 0; i < rs.length(); i++) {
            String c = rs.getJSONObject(i).optString("COLUMN_NAME", null);
            if (c != null && !c.isEmpty()) cols.add(c.toUpperCase(Locale.ROOT));
        }
        if (cols.isEmpty()) {
            throw new IllegalArgumentException("Table not found or has no columns: " + t);
        }
        return new SqlWhitelist(t, cols);
    }

    public boolean isAllowedColumn(String col) {
        return col != null && columns.contains(col.toUpperCase(Locale.ROOT));
    }

    public static String normalizeTable(String raw) {
        if (raw == null) return null;
        String s = StringUtils.trimToEmpty(raw);
        if (!s.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)?")) return null;
        return s;
    }
}
