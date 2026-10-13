package com.powerdialer.util;

import java.util.*;
import static com.powerdialer.util.ValueBinders.*;
import static com.powerdialer.util.FreeQueryNode.*;

public final class FreeQueryParamRenderer {
    private FreeQueryParamRenderer(){}

    public static ParamSql render(FreeQueryNode node) {
        if (node == null) return ParamSql.of("", Collections.emptyList());
        if (node instanceof Group)     return renderGroup((Group) node);
        if (node instanceof Raw)       return ParamSql.of(((Raw) node).sql, Collections.emptyList());
        if (node instanceof Condition) return renderCond((Condition) node);
        return ParamSql.of("", Collections.emptyList());
    }

    private static ParamSql renderGroup(Group g) {
        if (g == null || g.isEmpty()) return ParamSql.of("", Collections.emptyList());
        List<String> parts = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (FreeQueryNode ch : g.children) {
            ParamSql ps = render(ch);
            if (ps.getSql()!=null && !ps.getSql().trim().isEmpty()) {
                parts.add("(" + ps.getSql() + ")");
                params.addAll(ps.getParams());
            }
        }
        if (parts.isEmpty()) return ParamSql.of("", Collections.emptyList());
        String join = (g.logic == Group.Logic.OR) ? " OR " : " AND ";
        return ParamSql.of(String.join(join, parts), params);
    }

    private static ParamSql renderCond(Condition c) {
        String field = safeIdentifier(c.field);
        if (isBlankStr(field)) return ParamSql.of("", Collections.emptyList());

        String op   = (c.op == null ? "EQ" : c.op.trim());
        String type = (c.type == null ? "" : c.type.toLowerCase(Locale.ROOT));
        String opUpper = op.toUpperCase(Locale.ROOT);

        if ("IS_NULL".equals(opUpper))     return ParamSql.of(field + " IS NULL", Collections.emptyList());
        if ("IS_NOT_NULL".equals(opUpper)) return ParamSql.of(field + " IS NOT NULL", Collections.emptyList());

        if ("THRESHOLDDATE".equals(opUpper)) {
            Integer days = c.days;
            if (days == null) return ParamSql.of("", Collections.emptyList());
            String cmp = mapCmpTokenToSql(c.operator);
            if (cmp == null) cmp = "<=";
            String sql = "CAST(" + field + " AS date) " + cmp + " CAST(DATEADD(DAY, ?, GETDATE()) AS date)";
            return ParamSql.of(sql, Collections.singletonList(-days));
        }

        if ("IN".equals(opUpper) || "NOT_IN".equals(opUpper)) {
            List<Object> vals = new ArrayList<>();
            if (c.values != null)       for (Object v : c.values) vals.add(coerceParam(v, type));
            else if (c.value != null)   vals.add(coerceParam(c.value, type));
            if (vals.isEmpty()) return ParamSql.of("", Collections.emptyList());
            String placeholders = String.join(", ", Collections.nCopies(vals.size(), "?"));
            String sql = field + ("IN".equals(opUpper) ? " IN (" : " NOT IN (") + placeholders + ")";
            return ParamSql.of(sql, vals);
        }

        if ("BETWEEN".equals(opUpper)) {
            if (c.values != null && c.values.size() == 2) {
                List<Object> b = Arrays.asList(
                        coerceParam(c.values.get(0), type),
                        coerceParam(c.values.get(1), type)
                );
                if ("date".equals(type) || "datetime".equals(type) || "timestamp".equals(type)) {
                    return ParamSql.of("CAST(" + field + " AS date) BETWEEN CAST(? AS date) AND CAST(? AS date)", b);
                }
                return ParamSql.of(field + " BETWEEN ? AND ?", b);
            }
            return ParamSql.of("", Collections.emptyList());
        }

        if ("LIKE".equals(opUpper) || "NOT_LIKE".equals(opUpper)) {
            String s = String.valueOf(c.value == null ? "" : c.value);
            if (!s.contains("%")) s = "%" + s + "%";
            String sql = field + ("LIKE".equals(opUpper) ? " LIKE ?" : " NOT LIKE ?");
            return ParamSql.of(sql, Collections.singletonList(s));
        }

        String cmp = toSqlOp(op);
        if (cmp == null) cmp = "=";

        if ("date".equals(type) || "datetime".equals(type) || "timestamp".equals(type)) {
            return ParamSql.of("CAST(" + field + " AS date) " + cmp + " CAST(? AS date)",
                    Collections.singletonList(coerceParam(c.value, type)));
        }

        return ParamSql.of(field + " " + cmp + " ?", Collections.singletonList(coerceParam(c.value, type)));
    }

    private static String mapCmpTokenToSql(String token) {
        if (token == null || token.trim().isEmpty()) return null;
        String t = token.trim().toUpperCase(Locale.ROOT);
        switch (t) {
            case "<":   case "LT":  return "<";
            case "<=":  case "LTE": return "<=";
            case "=":   case "EQ":  return "=";
            case "<>":  case "NE":  return "<>";
            case ">":   case "GT":  return ">";
            case ">=":  case "GTE": return ">=";
            default: return null;
        }
    }

    private static String toSqlOp(String op) {
        if (op == null) return "=";
        String t = op.trim().toUpperCase(Locale.ROOT);
        switch (t) {
            case "=": case "EQ":  return "=";
            case "<>": case "NE": return "<>";
            case ">":  case "GT": return ">";
            case ">=": case "GTE":return ">=";
            case "<":  case "LT": return "<";
            case "<=": case "LTE":return "<=";
            default: return null;
        }
    }

    private static boolean isBlankStr(String s) {
        return s == null || s.trim().isEmpty();
    }
}
