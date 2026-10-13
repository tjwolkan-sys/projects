package com.powerdialer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Set desired transaction isolation level if it allowed by database. If specified isolation level not supported then nothing happened. Isolation
 * level specified here must override isolation level specified in {@link org.springframework.transaction.annotation.Transactional}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OptionalIsolationLevel {

  /**
   * Available values described in {@link java.sql.Connection}
   */
  int level() default -1;
}
