package com.powerdialer.util;


public final class FreeQuerySqlHelper {
    private FreeQuerySqlHelper(){}

    public static ParamSql parseFreeQueryToParamSql(String json) {
        return parseFreeQueryToParamSql(json, false);
    }
    public static ParamSql parseFreeQueryToParamSql(String json, boolean allowRaw) {
        FreeQueryNode ast = FreeQueryJsonParser.parse(json, allowRaw);
        return FreeQueryParamRenderer.render(ast);
    }

    public static String parseFreeQueryToSql(String json) {
        ParamSql p = parseFreeQueryToParamSql(json, true);
        return p.getSql() == null ? "" : p.getSql();
    }
}
