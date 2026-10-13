package com.powerdialer.util;

import org.json.*;
import java.util.*;
import static com.powerdialer.util.FreeQueryNode.*;

public final class FreeQueryJsonParser {
    private FreeQueryJsonParser() {}

    public static FreeQueryNode parse(String json, boolean allowRaw) {
        if (json == null) return new Group(Group.Logic.AND);
        final String t = json.trim();
        if (t.isEmpty()) return new Group(Group.Logic.AND);

        try {
            Object node = new JSONTokener(t).nextValue();
            if (node instanceof JSONObject) return parseNode((JSONObject) node, allowRaw);
            if (node instanceof JSONArray)  return parseGroup((JSONArray) node, "AND", allowRaw);
            return new Group(Group.Logic.AND);
        } catch (JSONException e) {
            return new Group(Group.Logic.AND);
        }
    }

    private static FreeQueryNode parseNode(JSONObject obj, boolean allowRaw) {
        if (obj.has("conditions")) {
            String logic = obj.optString("logic", "AND");
            JSONArray arr = obj.optJSONArray("conditions");
            if (arr == null) return new Group(Group.Logic.AND);
            return parseGroup(arr, logic, allowRaw);
        }
        return parseCondition(obj, allowRaw);
    }

    private static FreeQueryNode parseGroup(JSONArray arr, String logicRaw, boolean allowRaw) {
        Group.Logic logic = "OR".equalsIgnoreCase(logicRaw) ? Group.Logic.OR : Group.Logic.AND;
        Group g = new Group(logic);
        for (int i=0;i<arr.length();i++) {
            Object it = arr.get(i);
            if (it instanceof JSONObject) g.add(parseNode((JSONObject) it, allowRaw));
            else if (it instanceof String && allowRaw) {
                final String s = ((String) it).trim();
                if (!s.isEmpty()) g.add(new Raw(s));
            }
        }
        return g;
    }

    private static FreeQueryNode parseCondition(JSONObject c, boolean allowRaw) {
        if (c.has("raw")) {
            if (!allowRaw) return new Group(Group.Logic.AND);
            final String raw = c.optString("raw", "").trim();
            return raw.isEmpty() ? new Group(Group.Logic.AND) : new Raw(raw);
        }

        String field = trimToNull(c.optString("field", null));
        String op    = normalizeOp(c.optString("op", "EQ"));
        String type  = c.optString("type", "").toLowerCase(Locale.ROOT);

        Object value = c.has("value") ? c.get("value") : null;

        JSONArray arr = c.optJSONArray("values");
        List<Object> values = null;
        if (arr != null) {
            values = new ArrayList<>(arr.length());
            for (int i=0;i<arr.length();i++) values.add(arr.get(i));
        }

        Integer days = null;
        if (c.has("days")) {
            try { days = c.getInt("days"); } catch (Exception ignore) {  }
        }

        String operator = null;
        if (c.has("operator")) {
            operator = c.optString("operator", null);
        }

        return new Condition(field, op, type, value, values, days, operator);
    }

    private static String trimToNull(String s){
        if (s==null) return null;
        s=s.trim();
        return s.isEmpty() ? null : s;
    }

    private static String normalizeOp(String raw) {
        if (raw == null) return "EQ";
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if ("=".equals(s))  return "EQ";
        if ("!=".equals(s) || "<>".equals(s)) return "NE";
        if (">".equals(s))  return "GT";
        if (">=".equals(s)) return "GTE";
        if ("<".equals(s))  return "LT";
        if ("<=".equals(s)) return "LTE";
        if ("NOT IN".equals(s))   return "NOT_IN";
        if ("NOT LIKE".equals(s)) return "NOT_LIKE";
        return s;
    }
}
