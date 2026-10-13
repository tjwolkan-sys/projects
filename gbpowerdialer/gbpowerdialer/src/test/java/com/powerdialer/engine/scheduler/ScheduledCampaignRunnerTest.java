package com.powerdialer.engine.scheduler;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.scheduler.schedulechain.model.ScheduleCheckResult;
import com.powerdialer.engine.scheduler.schedulechain.orchestrator.CampaignSchedulerOrchestrator;
import com.powerdialer.model.Campaign;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.Constants;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ScheduledCampaignRunnerTest {

    @Mock private CampaignService campaignService;
    @Mock private CampaignSchedulerOrchestrator orchestrator;
    @Mock private DialerFacade dialerFacade;

    private Map<Long, CampaignContext> contexts;
    private ScheduledCampaignRunner runner;

    @Before
    public void setUp() {
        runner = new ScheduledCampaignRunner(campaignService, orchestrator);

        contexts = new ConcurrentHashMap<>();
        when(dialerFacade.getCampaignContexts()).thenReturn(contexts);
        when(campaignService.getDialerFacade()).thenReturn(dialerFacade);
    }

    /**
     * FIX-333607: scheduler aksiyonları asenkron çalışır. Önceki turun aksiyonu (or. RESET ardından
     * START) kuyrukta veya çalışırken bayat state ile yeni karar verilmez; eskiden aksiyonlar senkron
     * olduğu için bir sonraki tur ancak onlar bitince başlıyordu.
     */
    @Test
    public void campaignWithPendingActionIsNotEvaluatedThisTick() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(79L);
        when(campaignService.getCampaignsWithSchedules()).thenReturn(Collections.singletonList(campaign));
        when(campaignService.hasPendingAction(79L)).thenReturn(true);

        runner.processScheduledCampaigns();

        verify(orchestrator, never()).evaluateCampaign(campaign);
        verify(campaignService, never()).actionAsync(anyLong(), any(), any());
    }

    @Test
    public void startsWhenStopped_andMustBeStartedTrue() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(70L);

        // context yok => STOPPED
        when(campaignService.getCampaignsWithSchedules()).thenReturn(Collections.singletonList(campaign));

        ScheduleCheckResult r = new ScheduleCheckResult();
        r.setMustBeStarted(true);
        when(orchestrator.evaluateCampaign(campaign)).thenReturn(r);

        runner.processScheduledCampaigns();

        verify(campaignService, times(1)).actionAsync(70L, Constants.CAMPAIGN_ACTION.START, null);
    }

    @Test
    public void doesNotStartWhenRunning_evenIfMustBeStartedTrue() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(71L);

        putContext(71L, Constants.CAMPAIGN_STATE.RUNNING);

        when(campaignService.getCampaignsWithSchedules()).thenReturn(Collections.singletonList(campaign));

        ScheduleCheckResult r = new ScheduleCheckResult();
        r.setMustBeStarted(true);
        when(orchestrator.evaluateCampaign(campaign)).thenReturn(r);

        runner.processScheduledCampaigns();

        verify(campaignService, never()).actionAsync(anyLong(), eq(Constants.CAMPAIGN_ACTION.START), any());
    }

    @Test
    public void stopsWhenRunning_andMustBeStoppedTrue() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(72L);

        putContext(72L, Constants.CAMPAIGN_STATE.RUNNING);

        when(campaignService.getCampaignsWithSchedules()).thenReturn(Collections.singletonList(campaign));

        ScheduleCheckResult r = new ScheduleCheckResult();
        r.setMustBeStopped(true);
        when(orchestrator.evaluateCampaign(campaign)).thenReturn(r);

        runner.processScheduledCampaigns();

        verify(campaignService, times(1)).actionAsync(
                72L,
                Constants.CAMPAIGN_ACTION.STOP_REGULAR,
                Constants.CAMPAIGN_STOP_TYPE.AUTOMATIC
        );
    }

    @Test
    public void pausesWhenRunning_andMustBePausedTrue() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(73L);

        putContext(73L, Constants.CAMPAIGN_STATE.RUNNING);

        when(campaignService.getCampaignsWithSchedules()).thenReturn(Collections.singletonList(campaign));

        ScheduleCheckResult r = new ScheduleCheckResult();
        r.setMustBePaused(true);
        when(orchestrator.evaluateCampaign(campaign)).thenReturn(r);

        runner.processScheduledCampaigns();

        // BEKLENEN: RUNNING iken PAUSE action atmalı
        verify(campaignService, times(1)).actionAsync(73L, Constants.CAMPAIGN_ACTION.PAUSE, null);
    }

    @Test
    public void doesNotPauseWhenStopped_evenIfMustBePausedTrue() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(74L);

        // context yok => STOPPED
        when(campaignService.getCampaignsWithSchedules()).thenReturn(Collections.singletonList(campaign));

        ScheduleCheckResult r = new ScheduleCheckResult();
        r.setMustBePaused(true);
        when(orchestrator.evaluateCampaign(campaign)).thenReturn(r);

        runner.processScheduledCampaigns();

        // BEKLENEN: STOPPED iken pause anlamsız, action atmamalı
        verify(campaignService, never()).actionAsync(anyLong(), eq(Constants.CAMPAIGN_ACTION.PAUSE), any());
    }

    @Test
    public void stopHasPriorityOverPause_whenBothTrue_andRunning() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(75L);

        putContext(75L, Constants.CAMPAIGN_STATE.RUNNING);

        when(campaignService.getCampaignsWithSchedules()).thenReturn(Collections.singletonList(campaign));

        ScheduleCheckResult r = new ScheduleCheckResult();
        r.setMustBeStopped(true);
        r.setMustBePaused(true);
        when(orchestrator.evaluateCampaign(campaign)).thenReturn(r);

        runner.processScheduledCampaigns();

        // STOP çalışmalı
        verify(campaignService, times(1)).actionAsync(
                75L,
                Constants.CAMPAIGN_ACTION.STOP_REGULAR,
                Constants.CAMPAIGN_STOP_TYPE.AUTOMATIC
        );
        // STOP varken PAUSE atılmamalı (öncelik)
        verify(campaignService, never()).actionAsync(75L, Constants.CAMPAIGN_ACTION.PAUSE, null);
    }

    @Test
    public void doesNotStartWhenManuallyPaused_evenIfMustBeStartedTrue() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(76L);

        // Operatör manuel pause yaptı: schedule penceresinde olsa bile START edilmemeli
        putContext(76L, Constants.CAMPAIGN_STATE.PAUSED, true);

        when(campaignService.getCampaignsWithSchedules()).thenReturn(Collections.singletonList(campaign));

        ScheduleCheckResult r = new ScheduleCheckResult();
        r.setMustBeStarted(true);
        when(orchestrator.evaluateCampaign(campaign)).thenReturn(r);

        runner.processScheduledCampaigns();

        verify(campaignService, never()).actionAsync(anyLong(), eq(Constants.CAMPAIGN_ACTION.START), any());
    }

    @Test
    public void startsWhenAutomationPaused_andMustBeStartedTrue() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(77L);

        // Otomasyon kaynaklı pause: koşul kalkınca auto-resume korunmalı
        putContext(77L, Constants.CAMPAIGN_STATE.PAUSED, false);

        when(campaignService.getCampaignsWithSchedules()).thenReturn(Collections.singletonList(campaign));

        ScheduleCheckResult r = new ScheduleCheckResult();
        r.setMustBeStarted(true);
        when(orchestrator.evaluateCampaign(campaign)).thenReturn(r);

        runner.processScheduledCampaigns();

        verify(campaignService, times(1)).actionAsync(77L, Constants.CAMPAIGN_ACTION.START, null);
    }

    /**
     * FIX-333607: scheduler tek thread'de tüm campaign'leri işler; aksiyonu senkron çalıştırırsa bir
     * campaign'in uzun start'ı (02.10.2026'da C74, 10,5 dk) diğer tüm zamanlanmış aksiyonları durdurur.
     * Scheduler bu yüzden yalnız asenkron dağıtımı kullanır.
     */
    @Test
    public void schedulerNeverUsesBlockingActionPath() {
        Campaign toStart = mock(Campaign.class);
        when(toStart.getId()).thenReturn(80L);
        Campaign toStop = mock(Campaign.class);
        when(toStop.getId()).thenReturn(81L);
        putContext(81L, Constants.CAMPAIGN_STATE.RUNNING);
        when(campaignService.getCampaignsWithSchedules()).thenReturn(java.util.Arrays.asList(toStart, toStop));

        ScheduleCheckResult start = new ScheduleCheckResult();
        start.setMustBeStarted(true);
        when(orchestrator.evaluateCampaign(toStart)).thenReturn(start);
        ScheduleCheckResult stop = new ScheduleCheckResult();
        stop.setMustBeStopped(true);
        when(orchestrator.evaluateCampaign(toStop)).thenReturn(stop);

        runner.processScheduledCampaigns();

        verify(campaignService).actionAsync(80L, Constants.CAMPAIGN_ACTION.START, null);
        verify(campaignService).actionAsync(eq(81L), eq(Constants.CAMPAIGN_ACTION.STOP_REGULAR), any());
        verify(campaignService, never()).action(anyLong(), any(), any());
    }

    private void putContext(Long campaignId, Constants.CAMPAIGN_STATE state) {
        Campaign ctxCampaign = mock(Campaign.class);
        when(ctxCampaign.getState()).thenReturn(state);

        CampaignContext ctx = mock(CampaignContext.class);
        when(ctx.getCampaign()).thenReturn(ctxCampaign);

        contexts.put(campaignId, ctx);
    }

    private void putContext(Long campaignId, Constants.CAMPAIGN_STATE state, boolean manuallyPaused) {
        Campaign ctxCampaign = mock(Campaign.class);
        when(ctxCampaign.getState()).thenReturn(state);
        when(ctxCampaign.isManuallyPaused()).thenReturn(manuallyPaused);

        CampaignContext ctx = mock(CampaignContext.class);
        when(ctx.getCampaign()).thenReturn(ctxCampaign);

        contexts.put(campaignId, ctx);
    }
}
