package com.powerdialer.redis.event.dialercall;

import com.powerdialer.redis.event.CallEvent;

public class DialerCallAbandonIVREvent extends CallEvent {

  public DialerCallAbandonIVREvent(String publisher, String interactionId) {
    super(publisher, interactionId);
  }

}
