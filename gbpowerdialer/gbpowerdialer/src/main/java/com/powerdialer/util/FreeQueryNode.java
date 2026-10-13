package com.powerdialer.util;

import java.util.ArrayList;
import java.util.List;

public interface FreeQueryNode {
    final class Group implements FreeQueryNode {
        public enum Logic { AND, OR }
        public final Logic logic;
        public final List<FreeQueryNode> children = new ArrayList<>();
        public Group(Logic logic) { this.logic = logic == null ? Logic.AND : logic; }
        public Group add(FreeQueryNode n) { if (n != null) children.add(n); return this; }
        public boolean isEmpty() { return children.isEmpty(); }
    }

    final class Raw implements FreeQueryNode {
        public final String sql;
        public Raw(String sql) { this.sql = sql; }
    }

    final class Condition implements FreeQueryNode {
        public final String field;
        public final String op;
        public final String type;
        public final Object value;
        public final java.util.List<Object> values;
        public final Integer days;
        public final String operator;

        public Condition(String field,
                         String op,
                         String type,
                         Object value,
                         java.util.List<Object> values,
                         Integer days,
                         String operator) {
            this.field = field;
            this.op = op;
            this.type = type;
            this.value = value;
            this.values = values;
            this.days = days;
            this.operator = operator;
        }

        public Condition(String field,
                         String op,
                         String type,
                         Object value,
                         java.util.List<Object> values,
                         Integer days) {
            this(field, op, type, value, values, days, null);
        }
    }
}
