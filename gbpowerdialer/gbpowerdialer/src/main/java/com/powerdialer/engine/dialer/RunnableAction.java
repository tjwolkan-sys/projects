package com.powerdialer.engine.dialer;

public interface RunnableAction extends Runnable {

  void start();

  void pause();

  void stop();

}
