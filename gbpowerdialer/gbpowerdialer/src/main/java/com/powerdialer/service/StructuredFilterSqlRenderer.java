package com.powerdialer.service;

import com.powerdialer.dto.JobConfigFilterDTO;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ParamSql;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class StructuredFilterSqlRenderer {

    private final SqlWhitelist wl;

    public StructuredFilterSqlRenderer(SqlWhitelist wl) { this.wl = wl; }

    private String validateColumn(String raw) {
        String s = StringUtils.trimToNull(raw);
        if (s == null) return null;
        if (!s.matches("[A-Za-z0-9_]+")) return null;
        return wl.isAllowedColumn(s) ? s : null;
    }

    public String dateOnlyCompareByExpireDays(String field, int days, Constants.FILTER_CONDITION compareOp) {
        if (compareOp == null || !compareOp.isOrderedCompare()) {
            throw new IllegalArgumentException("Unsupported compare operator for expireDays");
        }
        final String sym = compareSymbol(compareOp);
        return "CAST(" + field + " AS datetime) " + sym + " DATEADD(DAY, -" + days + ", GETDATE())";
    }

    public ParamSql toConditionParam(JobConfigFilterDTO f) {
        final String field = validateColumn(f.getFilterField());
        if (field == null) return ParamSql.of("", Collections.emptyList());

        if (f.getExpireThresholdDays() != null && f.getExpireThresholdDays() >= 0) {
            final Constants.FILTER_CONDITION cmp = f.getOperationType();
            final String expr = dateOnlyCompareByExpireDays(field, f.getExpireThresholdDays(), cmp);
            return ParamSql.of(expr, Collections.emptyList());
        }

        final Constants.FILTER_CONDITION op = (f.getOperationType() != null)
                ? f.getOperationType()
                : Constants.FILTER_CONDITION.EQUAL;


        if (op.isNullCheck()) {
            String sql = field + " " + compareSymbol(op);
            return ParamSql.of(sql, Collections.emptyList());
        }

        final String val = StringUtils.trimToNull(f.getFilterValue());

        if (!StringUtils.isBlank(val) && (op == Constants.FILTER_CONDITION.LIKE || op == Constants.FILTER_CONDITION.NOTLIKE)) {
            String p = val.contains("%") ? val : "%" + val + "%";
            String sql = field + " " + compareSymbol(op) + " ?";
            return ParamSql.of(sql, Collections.singletonList(p));
        }

        if (!StringUtils.isBlank(val) && (op == Constants.FILTER_CONDITION.IN || op == Constants.FILTER_CONDITION.NOTIN)) {
            String[] arr = val.split("\\s*,\\s*");
            List<Object> params = new ArrayList<>();
            List<String> qs = new ArrayList<>();
            for (String t : arr) {
                if (StringUtils.isBlank(t)) continue;
                qs.add("?");
                params.add(coerceNumberOrString(t));
            }
            if (qs.isEmpty()) return ParamSql.of("", Collections.emptyList());
            String sql = field + " " + compareSymbol(op) + " (" + String.join(", ", qs) + ")";
            return ParamSql.of(sql, params);
        }

        if (f.getFilterDateValue() != null) {
            if (!op.isDateAllowed()) {
                throw new IllegalArgumentException("Unsupported compare operator for filterDateValue");
            }
            String sql = "CAST(" + field + " AS datetime) " + compareSymbol(op) + " CAST(? AS datetime)";
            return ParamSql.of(sql,
                    Collections.singletonList(new java.sql.Timestamp(f.getFilterDateValue().getTime())));
        }


        if (!StringUtils.isBlank(val)) {
            String sql = field + " " + compareSymbol(op) + " ?";
            return ParamSql.of(sql, Collections.singletonList(coerceNumberOrString(val)));
        }

        return ParamSql.of("", Collections.emptyList());
    }

    public int operatorRank(String condition) {
        final String c = condition.toUpperCase(Locale.ROOT);
        if (c.contains(" IS NULL") || c.contains(" IS NOT NULL")) return 0;
        if (c.contains(" IN (") || c.contains(" = ")) return 1;
        if (c.contains(" >= ") || c.contains(" <= ") || c.contains(" > ") || c.contains(" < ")) return 2;
        if (c.contains(" <> ") || c.contains(" !=")) return 3;
        if (c.contains(" NOT LIKE") || c.contains(" LIKE")) return 4;
        return 5;
    }

    private String compareSymbol(Constants.FILTER_CONDITION op) {
        if (op == null) return "=";
        switch (op) {
            case EQUAL: return "=";
            case NOTEQUAL: return "<>";
            case GREATER: return ">";
            case GREATEROREQUAL: return ">=";
            case LESS: return "<";
            case LESSOREQUAL: return "<=";
            case LIKE: return "LIKE";
            case NOTLIKE: return "NOT LIKE";
            case IN: return "IN";
            case NOTIN: return "NOT IN";
            case ISNULL: return "IS NULL";
            case ISNOTNULL: return "IS NOT NULL";
            default: return "=";
        }
    }

    private Object coerceNumberOrString(String s) {
        if (s == null) return null;
        final String t = s.trim();
        if (t.matches("-?\\d+")) {
            try { return Long.valueOf(t); } catch (NumberFormatException ignore) {}
        } else if (t.matches("-?\\d+\\.\\d+")) {
            try { return Double.valueOf(t); } catch (NumberFormatException ignore) {}
        }
        return t;
    }
}
