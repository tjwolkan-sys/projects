package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.IQueueDao;
import com.powerdialer.dto.QueueFormDTO;
import com.powerdialer.dto.QueueListDTO;
import com.powerdialer.model.Queue;
import com.powerdialer.validators.QueueValidator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service("queueService")
@Transactional(readOnly = true)
public class QueueService extends GenericService<Queue, QueueFormDTO, QueueListDTO> {

  @Autowired
  public QueueService(IQueueDao dao) {
    super(dao, Queue.class, QueueFormDTO.class, QueueListDTO.class, new QueueValidator());
  }

  @Transactional(readOnly = true)
  public List<QueueListDTO> findDistinctByCampaignId(Long campaignId) {
    return Optional.ofNullable(((IQueueDao)getDao()).findDistinctByCampaignId(campaignId))
            .orElse(Collections.emptyList())
            .stream()
            .map(queue -> mapper.map(queue, QueueListDTO.class))
            .collect(Collectors.toList());
  }

}
