package com.powerdialer.service;

import com.powerdialer.dto.JobConfigFilterDTO;
import com.powerdialer.util.Constants;
import com.powerdialer.util.FreeQuerySqlHelper;
import com.powerdialer.util.ParamSql;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public final class CompositeWhereClauseBuilder {

    private final StructuredFilterSqlRenderer renderer;

    public CompositeWhereClauseBuilder(SqlWhitelist wl) {
        this.renderer = new StructuredFilterSqlRenderer(wl);
    }

    public ParamSql build(List<JobConfigFilterDTO> filters) {
        if (filters == null || filters.isEmpty()) return ParamSql.of("", Collections.emptyList());

        for (JobConfigFilterDTO f : filters) {
            if (f.getFilterType() == Constants.FILTER_TYPE.FREE) {
                final String txt = StringUtils.trimToNull(f.getFreeQueryTxt());
                if (!StringUtils.isBlank(txt)) {
                    return FreeQuerySqlHelper.parseFreeQueryToParamSql(txt, false);
                }
            }
        }

        final LinkedHashSet<ParamSql> parts = new LinkedHashSet<>();
        for (JobConfigFilterDTO f : filters) {
            if (f.getFilterType() == Constants.FILTER_TYPE.FREE) continue;
            final ParamSql cond = renderer.toConditionParam(f);
            if (cond != null && !StringUtils.isBlank(cond.getSql())) parts.add(cond);
        }
        if (parts.isEmpty()) return ParamSql.of("", Collections.emptyList());

        final List<ParamSql> sorted = new ArrayList<>(parts);
        sorted.sort(Comparator.comparingInt(ps -> renderer.operatorRank(ps.getSql())));

        final StringBuilder sb = new StringBuilder();
        final List<Object> all = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) sb.append(" AND ");
            sb.append('(').append(sorted.get(i).getSql()).append(')');
            all.addAll(sorted.get(i).getParams());
        }
        return ParamSql.of(sb.toString(), all);
    }
}
