package tests.campaign;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerdialer.exceptions.StopConditionReachedException;
import com.powerdialer.publish.CampaignChangedEvent;
import com.powerdialer.publish.CampaignListener;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.LoggerDecorator;
import org.junit.Before;
import org.junit.Test;

/**
 * FIX-333607: RESTART'ın stop job'ı bitince yayınlanan event campaign'i yeniden başlatır.
 *
 * <p>START, aynı campaign'in RESTART aksiyonu kilidi henüz bırakmamışsa bekler. Eskiden global
 * kilitte süresiz beklerdi; UI'daki 10 sn sınırıyla düşerse campaign restart sonrası STOPPING'de
 * kalır. Bu yüzden START {@code startAfterRestart} (uzun bekleme) ile verilir. Stop condition ve
 * tarih uyumsuzluğunda restart iptali (mevcut sözleşme) korunur.</p>
 */
public class CampaignListenerRestartFollowUpRegressionTest {

  private final CampaignService campaignService = mock(CampaignService.class);
  private CampaignListener listener;

  @Before
  public void setUp() {
    LoggerDecorator loggerDecorator = mock(LoggerDecorator.class);
    when(loggerDecorator.getLogger(any())).thenReturn(loggerDecorator);
    listener = new CampaignListener(loggerDecorator, campaignService);
  }

  @Test
  public void restartFollowUpStartUsesLongLockWait() {
    listener.onApplicationEvent(new CampaignChangedEvent(this, 9L));

    verify(campaignService).startAfterRestart(9L);
    verify(campaignService, never()).action(anyLong(), any(), any());
  }

  @Test
  public void stopConditionCancelsRestartAsBefore() {
    doThrow(new StopConditionReachedException("campaign.stopCondition.error", "c9"))
        .when(campaignService).startAfterRestart(9L);

    listener.onApplicationEvent(new CampaignChangedEvent(this, 9L));

    verify(campaignService).cancelRestart(9L);
  }
}
