package com.powerdialer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.powerdialer.dto.QueueFormDTO;
import com.powerdialer.dto.QueueListDTO;
import com.powerdialer.model.Queue;
import com.powerdialer.service.QueueService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/queue")
public class QueueController extends GenericController<Queue, QueueFormDTO, QueueListDTO> {

  @Autowired
  public QueueController(QueueService queueService) {
    super(queueService);
  }

  @GetMapping
  @PreAuthorize("@permissionUtil.canEditCampaigns(#campaignId)")
  public @ResponseBody List<QueueListDTO> getByCampaign(@RequestParam Long campaignId) {
    return ((QueueService)getService()).findDistinctByCampaignId(campaignId);
  }
}
