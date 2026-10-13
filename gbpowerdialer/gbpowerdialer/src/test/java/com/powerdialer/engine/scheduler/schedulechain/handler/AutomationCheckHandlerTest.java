package com.powerdialer.engine.scheduler.schedulechain.handler;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.powerdialer.engine.scheduler.EvaluationResultContext;
import com.powerdialer.engine.scheduler.ScheduleAutomationEngine;
import com.powerdialer.engine.scheduler.schedulechain.model.ScheduleCheckResult;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.ScheduleAutomation;
import com.powerdialer.util.Constants;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AutomationCheckHandlerTest {

    @Mock private ScheduleAutomationEngine engine;
    @Mock private Campaign campaign;

    @Mock private ScheduleAutomation startAutomation;
    @Mock private ScheduleAutomation stopAutomation;
    @Mock private ScheduleAutomation pauseAutomation;

    private AutomationCheckHandler handler;

    @Before
    public void setUp() {
        handler = new AutomationCheckHandler(engine);
        when(campaign.getScheduleAutomationEnabled()).thenReturn(true);
    }

    @Test
    public void startTrue_setsMustBeStarted() {
        when(campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.START)).thenReturn(startAutomation);
        when(campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.STOP)).thenReturn(null);
        when(campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.PAUSE)).thenReturn(null);

        when(engine.processScheduleAutomation(startAutomation)).thenReturn(boolCtx(true));

        ScheduleCheckResult res = new ScheduleCheckResult();
        handler.check(campaign, LocalDateTime.now(), res);

        assertTrue(res.isMustBeStarted());
        assertFalse(res.isMustBeStopped());
        assertFalse(res.isMustBePaused());
    }

    @Test
    public void stopTrue_overridesStart() {
        when(campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.START)).thenReturn(startAutomation);
        when(campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.STOP)).thenReturn(stopAutomation);
        when(campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.PAUSE)).thenReturn(null);

        when(engine.processScheduleAutomation(startAutomation)).thenReturn(boolCtx(true));
        when(engine.processScheduleAutomation(stopAutomation)).thenReturn(boolCtx(true));

        ScheduleCheckResult res = new ScheduleCheckResult();
        handler.check(campaign, LocalDateTime.now(), res);

        assertFalse(res.isMustBeStarted());
        assertTrue(res.isMustBeStopped());
        assertFalse(res.isMustBePaused());
    }

    @Test
    public void pauseTrue_overridesStart() {
        when(campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.START)).thenReturn(startAutomation);
        when(campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.STOP)).thenReturn(null);
        when(campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.PAUSE)).thenReturn(pauseAutomation);

        when(engine.processScheduleAutomation(startAutomation)).thenReturn(boolCtx(true));
        when(engine.processScheduleAutomation(pauseAutomation)).thenReturn(boolCtx(true));

        ScheduleCheckResult res = new ScheduleCheckResult();
        handler.check(campaign, LocalDateTime.now(), res);

        assertFalse(res.isMustBeStarted());
        assertFalse(res.isMustBeStopped());
        assertTrue(res.isMustBePaused());
    }

    @Test
    public void stopAndPauseTrue_stopWinsStartCleared_andMustBeStoppedTrue() {
        when(campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.START)).thenReturn(startAutomation);
        when(campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.STOP)).thenReturn(stopAutomation);
        when(campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.PAUSE)).thenReturn(pauseAutomation);

        when(engine.processScheduleAutomation(startAutomation)).thenReturn(boolCtx(true));
        when(engine.processScheduleAutomation(stopAutomation)).thenReturn(boolCtx(true));
        when(engine.processScheduleAutomation(pauseAutomation)).thenReturn(boolCtx(true));

        ScheduleCheckResult res = new ScheduleCheckResult();
        handler.check(campaign, LocalDateTime.now(), res);
        assertFalse(res.isMustBeStarted());
        assertTrue(res.isMustBeStopped());
    }

    /**
     * EvaluationResultContext gerçek instance üret.
     * ExpressionEngine boolean TRUE üretince genelde BigDecimal 1 döner, FALSE için 0.
     */
    private EvaluationResultContext boolCtx(boolean v) {
        return new EvaluationResultContext(v ? BigDecimal.ONE : BigDecimal.ZERO, "test");
    }
}
