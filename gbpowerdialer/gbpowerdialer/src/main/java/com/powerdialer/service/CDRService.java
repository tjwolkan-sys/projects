package com.powerdialer.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.ICDRDao;
import com.powerdialer.model.CDR;

import java.util.Date;
import java.util.List;

@Service
public class CDRService {

  private static final Log LOG = LogFactory.getLog(CDRService.class);

  private final ICDRDao cdrDao;

  @Autowired
  public CDRService(ICDRDao cdrDao) {
    this.cdrDao = cdrDao;
  }

  @Transactional
  public void save(CDR cdr) {
    this.cdrDao.save(cdr);
    LOG.info(cdr + " is saved successfully");
  }

  @Transactional
  public void saveOrUpdate(CDR cdr) {
    this.cdrDao.saveOrUpdate(cdr);
    LOG.info(cdr + " is saved/updated successfully");
  }

  @Transactional(readOnly = true)
  public CDR getByInteractionId(String interactionId) {
    return cdrDao.getByInteractionId(interactionId);
  }

  @Transactional(readOnly = true)
  public Date findAgentRegistrarAnswerTime(String parentId) {
    return cdrDao.findAgentRegistrarAnswerTime(parentId);
  }

  @Transactional(readOnly = true)
  public boolean hasSuccessfulInteractionStatus(String parentId, Long userId) {
    return cdrDao.hasSuccessfulInteractionStatus(parentId, userId);
  }

  @Transactional(readOnly = true)
  public Long findWebphoneUserResultCodeId(String parentId, Long userId) {
    return cdrDao.findWebphoneUserResultCodeId(parentId, userId);
  }

  @Transactional(readOnly = true)
  public Double findGammaStartupAverageTalkTime(long campaignId, int minTalkTimeSec) {
    return cdrDao.findGammaStartupAverageTalkTime(campaignId, minTalkTimeSec);
  }

  @Transactional(readOnly = true)
  public Double findGammaStartupAverageRingTime(long campaignId) {
    return cdrDao.findGammaStartupAverageRingTime(campaignId);
  }

  /**
   * MADDE 4A — Gamma Plus warm-up profili için yalnız CEVAPLANAN çağrıların ring ortalaması.
   * Bayrak kapalı kampanyalarda kullanılmaz; sorgu sayısı artmaz, varyantlardan biri çalışır.
   */
  @Transactional(readOnly = true)
  public Double findGammaStartupAverageRingTimeAnswered(long campaignId) {
    return cdrDao.findGammaStartupAverageRingTimeAnswered(campaignId);
  }

  @Transactional(readOnly = true)
  public Double findGammaStartupAverageAcwTime(long campaignId) {
    return cdrDao.findGammaStartupAverageAcwTime(campaignId);
  }

  @Transactional(readOnly = true)
  public Double findGammaCurrentAverageAcwTime(long campaignId) {
    return cdrDao.findGammaCurrentAverageAcwTime(campaignId);
  }

  @Transactional(readOnly = true)
  public List<Object[]> findGammaTodayAnswerStatsByType(long campaignId) {
    return cdrDao.findGammaTodayAnswerStatsByType(campaignId);
  }
}
