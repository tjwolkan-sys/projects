package com.powerdialer.aop;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.internal.SessionImpl;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.powerdialer.annotation.OptionalIsolationLevel;

@Component
@Aspect
public class OptionalIsolationLevelAnnotationProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(OptionalIsolationLevelAnnotationProcessor.class);

  @Autowired
  private SessionFactory sessionFactory;

  @Around("@annotation(com.powerdialer.annotation.OptionalIsolationLevel)")
  public Object process(ProceedingJoinPoint joinPoint) throws Throwable {
    Session currentSession = sessionFactory.getCurrentSession();
    if (!currentSession.isConnected()) {
      throw new IllegalStateException("Session is not connected for " + joinPoint.getSignature().getName());
    }
    if (!currentSession.isOpen()) {
      throw new IllegalStateException("Session is not opened for " + joinPoint.getSignature().getName());
    }
    Transaction currentTransaction = currentSession.getTransaction();
    if (!currentTransaction.isActive()) {
      throw new IllegalStateException("Transaction is active " + joinPoint.getSignature().getName());
    }
    Connection connection = ((SessionImpl) currentSession).connection();
    DatabaseMetaData metaData = connection.getMetaData();
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    int requestedIsolationLevel = method.getAnnotation(OptionalIsolationLevel.class).level();
    if (metaData.supportsTransactionIsolationLevel(requestedIsolationLevel)) {
      connection.setTransactionIsolation(requestedIsolationLevel);
    }
    return joinPoint.proceed(joinPoint.getArgs());
  }
}
