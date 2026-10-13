package com.powerdialer.util;

@FunctionalInterface
public interface EntityRefResolver {
    <T> T ref(Class<T> type, Long id);
}