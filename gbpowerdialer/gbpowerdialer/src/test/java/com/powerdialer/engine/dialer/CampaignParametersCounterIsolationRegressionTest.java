package com.powerdialer.engine.dialer;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

/**
 * FIX-325461 — {@code CallProducer.calculateCampaignParameters} sayaç zincirinde hata izolasyonu.
 *
 * <p>Vaka: altı count sorgusu tek {@code try/catch} içinde sırayla çağrılıyordu. 28.07.2026
 * ölçümünde 500 timeout örneğinin 101'i <b>2. sıradaki</b> {@code countTotalListSize}'da
 * patladı; bu yüzden {@code retryListSize}, {@code callbackListSize}, {@code freshDataListSize}
 * ve {@code listRemaining} hiç yazılmadı. {@code CampaignMemento} {@code freshDataListSize}'ı
 * restore etmediği için ekranda kalıcı "0" göründü — müşterinin ilk şikâyeti ("fresh data
 * dolmuyor") tam olarak buydu, oysa DB'de 1154 uygun satır vardı.</p>
 *
 * <p>Bu test bir sayaç sorgusu patladığında diğerlerinin yazılmaya devam ettiğini, buna karşılık
 * türetilmiş sayaçların (listRemaining / contactsToHandle) bayat girdi üzerinden
 * güncellenmediğini kilitler.</p>
 */
public class CampaignParametersCounterIsolationRegressionTest {

  private Campaign campaign;
  private CallProducer callProducer;
  private CampaignDbOperations campaignDbOperations;

  @Before
  public void setUp() {
    campaign = new Campaign();
    campaign.setId(160L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setState(Constants.CAMPAIGN_STATE.RUNNING);
    campaign.setCacheLimit(100);
    campaign.setConcurrentLines(1);
    campaign.setRetryRatio(10);
    campaign.setSkillEnabled(false);
    campaign.setDncEnabled(false);
    campaign.setAllowCallback(true);
    campaign.setCallbackToAppointer(false);
    campaign.setColUniqueKey("id");
    campaign.setRetryStrategy(Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER);
    // CampaignContext ctor'u EndListBehaviourFactory.create(this) çağırıyor; alan null ise
    // yalnız harness kaynaklı NPE atar (üretimde alan hep dolu).
    campaign.setEndListBehaviour(Constants.END_LIST_BEHAVIOUR.CONTINUE);

    DialerFacade dialerFacade = mock(DialerFacade.class, Answers.RETURNS_DEEP_STUBS);
    campaignDbOperations = mock(CampaignDbOperations.class);
    when(dialerFacade.getCampaignDbOperations()).thenReturn(campaignDbOperations);

    CampaignFilterStatsCalculator statsCalculator = mock(CampaignFilterStatsCalculator.class);
    CampaignContext campaignContext = new CampaignContext(campaign, dialerFacade, statsCalculator);
    callProducer = new CallProducer(campaignContext, statsCalculator);
  }

  @Test
  public void failingCounterDoesNotBlockRemainingCounters() throws Exception {
    when(campaignDbOperations.countActualListSize(campaign)).thenReturn(7999);
    when(campaignDbOperations.countTotalListSize(campaign)).thenThrow(new SQLException("The query has timed out."));
    when(campaignDbOperations.countRetryListSize(campaign)).thenReturn(5);
    when(campaignDbOperations.countCallbackListSize(campaign)).thenReturn(3);
    when(campaignDbOperations.countFreshDataListSize(campaign)).thenReturn(1154);
    when(campaignDbOperations.countCompletedCalls(campaign)).thenReturn(2936);

    callProducer.calculateCampaignParameters();

    assertEquals("aktüel liste yazılmalı", 7999, campaign.getActualListSize());
    assertEquals("fresh sayacı, önceki sorgu patlasa bile yazılmalı", 1154, campaign.getFreshDataListSize());
    assertEquals("retry sayacı yazılmalı", 5, campaign.getRetryListSize());
    assertEquals("callback sayacı yazılmalı", 3, campaign.getCallbackListSize());
    assertEquals("listRemaining aktüel başarılıysa hesaplanmalı", 7999 - 2936, campaign.getListRemaining().get());
    assertEquals("contactsToHandle hesaplanmalı", 7999 - 2936 - 5 - 3, campaign.getContactsToHandleAmount());
  }

  @Test
  public void actualListSizeFailureLeavesDerivedCountersUntouched() throws Exception {
    when(campaignDbOperations.countActualListSize(campaign)).thenThrow(new SQLException("The query has timed out."));
    when(campaignDbOperations.countTotalListSize(campaign)).thenReturn(30562);
    when(campaignDbOperations.countRetryListSize(campaign)).thenReturn(5);
    when(campaignDbOperations.countCallbackListSize(campaign)).thenReturn(3);
    when(campaignDbOperations.countFreshDataListSize(campaign)).thenReturn(1154);

    callProducer.calculateCampaignParameters();

    assertEquals("total liste yazılmalı", 30562, campaign.getTotalListSize());
    assertEquals("fresh sayacı yazılmalı", 1154, campaign.getFreshDataListSize());
    assertEquals("aktüel liste alınamadıysa türetilmiş sayaç güncellenmemeli", 0, campaign.getListRemaining().get());
    verify(campaignDbOperations, never()).countCompletedCalls(campaign);
  }
}
