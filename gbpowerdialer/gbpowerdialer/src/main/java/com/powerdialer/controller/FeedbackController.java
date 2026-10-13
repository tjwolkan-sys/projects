package com.powerdialer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.powerdialer.dto.FeedbackFormDTO;
import com.powerdialer.dto.FeedbackListDTO;
import com.powerdialer.model.Feedback;
import com.powerdialer.service.FeedbackService;

@RestController
@RequestMapping("/feedback")
public class FeedbackController extends GenericController<Feedback, FeedbackFormDTO, FeedbackListDTO> {

  @Autowired
  public FeedbackController(FeedbackService feedbackService) {
    super(feedbackService);
  }

}
