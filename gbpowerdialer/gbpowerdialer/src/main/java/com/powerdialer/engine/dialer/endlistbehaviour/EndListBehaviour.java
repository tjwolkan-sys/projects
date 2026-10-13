package com.powerdialer.engine.dialer.endlistbehaviour;

/**
 * Interface for campaign end list behaviour
 */
@FunctionalInterface
public interface EndListBehaviour {

  /**
   * This method should call when end of campaign data appear
   */
  void processEndList();

}
