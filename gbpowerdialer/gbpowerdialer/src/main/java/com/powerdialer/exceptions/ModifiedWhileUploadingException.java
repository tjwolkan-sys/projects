package com.powerdialer.exceptions;

import com.powerdialer.model.Campaign;

public class ModifiedWhileUploadingException extends RuntimeException {

  public ModifiedWhileUploadingException(Campaign campaign) {
    super ("Campaign could not be modified due to active data uploading process: " + campaign);
  }

}
