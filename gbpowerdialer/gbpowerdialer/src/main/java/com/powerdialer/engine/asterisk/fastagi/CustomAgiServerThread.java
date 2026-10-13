package com.powerdialer.engine.asterisk.fastagi;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.asteriskjava.fastagi.AgiServerThread;
import org.asteriskjava.fastagi.DefaultAgiServer;
import org.asteriskjava.fastagi.MappingStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * Thread for AgiServer which consumes AGI requests from asterisk
 */
@Component
@Lazy(false)
public class CustomAgiServerThread extends AgiServerThread {

  /** Core AGI thread count used when {@code agi.server.pool.size} is missing or invalid. */
  public static final int DEFAULT_POOL_SIZE = 10;

  /**
   * AGI thread limit used when {@code agi.server.max.pool.size} is missing or invalid. Every bridged
   * call holds one AGI thread for the whole Dial; when the pool is full asterisk-java rejects new AGI
   * requests and the answered customer is dropped before reaching an agent (the former hard-coded
   * 150 was exhausted on Tcell on 01.10.2026).
   */
  public static final int DEFAULT_MAX_POOL_SIZE = 1000;

  private final LoggerDecorator LOG;

  @Autowired
  private MappingStrategy mappingStrategy;

  @Value("${agi.server.pool.size:" + DEFAULT_POOL_SIZE + "}")
  private int poolSize = DEFAULT_POOL_SIZE;

  @Value("${agi.server.max.pool.size:" + DEFAULT_MAX_POOL_SIZE + "}")
  private int maxPoolSize = DEFAULT_MAX_POOL_SIZE;

  @Autowired
  public CustomAgiServerThread(LoggerDecorator loggerDecorator) {
    LOG = loggerDecorator.getLogger(CustomAgiServerThread.class);
  }

  /**
   * Initialize AGI server thread
   */
  @PostConstruct
  public void init() {
    DefaultAgiServer agiServer = createAgiServer(mappingStrategy, poolSize, maxPoolSize);
    LOG.info("FastAGI server pool configured: poolSize=" + agiServer.getPoolSize()
        + ", maximumPoolSize=" + agiServer.getMaximumPoolSize());
    this.setAgiServer(agiServer);
    this.startup();
  }

  /**
   * Create AGI server with given pool sizes. Non-positive values fall back to the defaults and the
   * maximum is never below the core size (asterisk-java rejects a maximum below the core size).
   */
  public static DefaultAgiServer createAgiServer(MappingStrategy mappingStrategy, int poolSize, int maxPoolSize) {
    int effectivePoolSize = poolSize > 0 ? poolSize : DEFAULT_POOL_SIZE;
    int effectiveMaxPoolSize = Math.max(effectivePoolSize, maxPoolSize > 0 ? maxPoolSize : DEFAULT_MAX_POOL_SIZE);
    DefaultAgiServer agiServer = new DefaultAgiServer();
    agiServer.setPoolSize(effectivePoolSize);
    agiServer.setMaximumPoolSize(effectiveMaxPoolSize);
    agiServer.setMappingStrategy(mappingStrategy);
    return agiServer;
  }

  /**
   * Customize creation AGI server thread
   */
  @Override
  protected Thread createThread() {
    Thread thread = super.createThread();
    thread.setUncaughtExceptionHandler(this::handleUncaughtException);
    return thread;
  }

  /**
   * Handle uncaught exception from fast AGI scripts
   */
  private void handleUncaughtException(Thread thread, Throwable throwable) {
    LOG.error("An error occured during FastAGI communication with Asterisk", throwable, Constants.LOG_MODULE.ASTERISK);
  }

  /**
   * Just shutdown server on bean destroy
   */
  @PreDestroy
  public void preDestroy() {
    shutdown();
  }
}
