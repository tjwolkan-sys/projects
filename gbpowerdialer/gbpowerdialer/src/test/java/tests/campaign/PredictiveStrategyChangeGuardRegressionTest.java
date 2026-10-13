package tests.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.powerdialer.dao.ICampaignDao;
import com.powerdialer.dao.ITenantDao;
import com.powerdialer.dto.CampaignFormDTO;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.exceptions.CampaignIllegalStateException;
import com.powerdialer.model.Campaign;
import com.powerdialer.service.CampaignMementoService;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.Constants.CAMPAIGN_STATE;
import com.powerdialer.util.Constants.PREDICTIVE_STRATEGY;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * FIX-333607: kaydedilen predictive strateji, init edilmeden çalışan veya duraklatılmış campaign'in
 * runtime'ına uygulanamaz.
 *
 * <p>Strateji manager'ları (ALPHA boost, BETA, GAMMA) yalnız start'ta (ve unpause'ta) init edilir.
 * 01.10.2026'da çalışan campaign UI'dan ALPHA → GAMMA çevrildi; düz kaydet runtime context'i
 * {@code updateContext} ile yeni stratejiye geçirdi, GAMMA init edilmediği için campaign arama
 * çıkarmadı. Karşılaştırma runtime'daki (init edilmiş) stratejiyle yapılır. Front-end strateji
 * göndermiyorsa (Tech/NonTech yetkisi; {@code CampaignMergingAspect} alanı korur) değişiklik yoktur.</p>
 */
public class PredictiveStrategyChangeGuardRegressionTest {

  private static final Long CAMPAIGN_ID = 113L;

  private final DialerFacade dialerFacade = mock(DialerFacade.class);
  private final Map<Long, CampaignContext> contexts = new HashMap<>();
  private CampaignService campaignService;

  @Before
  public void setUp() {
    campaignService = new CampaignService(mock(ICampaignDao.class), mock(CampaignDbOperations.class), mock(ITenantDao.class),
        dialerFacade, mock(CampaignMementoService.class));
    when(dialerFacade.getCampaignContexts()).thenReturn(contexts);
  }

  @After
  public void tearDown() {
    campaignService.shutdownActionCoordinator();
  }

  @Test
  public void strategyChangeOfRunningCampaignIsRejected() {
    runtime(CAMPAIGN_STATE.RUNNING, PREDICTIVE_STRATEGY.ALPHA);

    try {
      campaignService.checkPredictiveStrategyChangeAllowed(CAMPAIGN_ID, form(PREDICTIVE_STRATEGY.GAMMA));
      fail("Çalışan campaign'de strateji değişikliği reddedilmeli");
    } catch (CampaignIllegalStateException e) {
      assertEquals(CampaignService.PREDICTIVE_STRATEGY_CHANGE_ILLEGAL_STATE_KEY, e.getMessage());
    }
  }

  @Test
  public void strategyChangeOfPausedCampaignIsRejected() {
    runtime(CAMPAIGN_STATE.PAUSED, PREDICTIVE_STRATEGY.GAMMA);

    try {
      campaignService.checkPredictiveStrategyChangeAllowed(CAMPAIGN_ID, form(PREDICTIVE_STRATEGY.ALPHA));
      fail("Duraklatılmış campaign'de strateji değişikliği reddedilmeli");
    } catch (CampaignIllegalStateException expected) {
      // beklenen
    }
  }

  @Test
  public void strategyChangeWithoutRuntimeContextIsAllowed() {
    campaignService.checkPredictiveStrategyChangeAllowed(CAMPAIGN_ID, form(PREDICTIVE_STRATEGY.GAMMA));
  }

  @Test
  public void strategyChangeOfStoppedCampaignIsAllowed() {
    runtime(CAMPAIGN_STATE.STOPPED, PREDICTIVE_STRATEGY.ALPHA);

    campaignService.checkPredictiveStrategyChangeAllowed(CAMPAIGN_ID, form(PREDICTIVE_STRATEGY.GAMMA));
  }

  @Test
  public void updateWithoutStrategyChangeOfRunningCampaignIsAllowed() {
    runtime(CAMPAIGN_STATE.RUNNING, PREDICTIVE_STRATEGY.GAMMA);

    campaignService.checkPredictiveStrategyChangeAllowed(CAMPAIGN_ID, form(PREDICTIVE_STRATEGY.GAMMA));
  }

  /** Tech/NonTech yetkili kullanıcının formu izinli olmayan alanları (strateji dâhil) null gönderir. */
  @Test
  public void restrictedUserUpdateWithoutStrategyOfRunningCampaignIsAllowed() {
    runtime(CAMPAIGN_STATE.RUNNING, PREDICTIVE_STRATEGY.GAMMA);

    campaignService.checkPredictiveStrategyChangeAllowed(CAMPAIGN_ID, form(null));
  }

  private void runtime(CAMPAIGN_STATE state, PREDICTIVE_STRATEGY strategy) {
    Campaign runtime = mock(Campaign.class);
    when(runtime.getState()).thenReturn(state);
    org.mockito.Mockito.lenient().when(runtime.getPredictiveStrategy()).thenReturn(strategy);
    CampaignContext context = mock(CampaignContext.class);
    when(context.getCampaign()).thenReturn(runtime);
    contexts.put(CAMPAIGN_ID, context);
  }

  private static CampaignFormDTO form(PREDICTIVE_STRATEGY strategy) {
    CampaignFormDTO form = new CampaignFormDTO();
    form.setId(CAMPAIGN_ID);
    form.setPredictiveStrategy(strategy);
    return form;
  }
}
