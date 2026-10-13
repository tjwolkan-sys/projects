package com.powerdialer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Annotation is used to escape column and table names for Oracle database in order to guarantee case sensitivity.
 * Escaping is performed only once. You could call annotated method with same parameter twice without having problem
 * @see com.powerdialer.aop.EscapeSqlNamesAnnotationProcessor
 */
@Target(ElementType.METHOD)
public @interface EscapeSqlNames {}
