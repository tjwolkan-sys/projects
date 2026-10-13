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
import com.powerdialer.util.Constants.CAMPAIGN_TYPE;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * FIX-333607: kaydedilen campaign tipi, çalışan veya duraklatılmış campaign'in runtime'ına init
 * edilmeden uygulanamaz.
 *
 * <p>Tipe bağlı runtime (predictive strateji manager'ları, preview/progressive call producer akışı)
 * yalnız start'ta kurulur. UI tip değişikliğinde restart seçmez; düz kaydet runtime context'i
 * {@code updateContext} ile yeni tipe geçirir. Predictive strateji değişikliği gibi tip değişikliği de
 * kayıttan önce reddedilir. Tip {@code int} olduğu için gönderilmediği ayırt edilemez; Tech/NonTech
 * yetkili kullanıcının formu tipi taşımaz ({@code CampaignMergingAspect} alanı korur), bu yüzden
 * kontrol yalnız tipi düzenleyebilen kullanıcıda yapılır.</p>
 */
public class CampaignTypeChangeGuardRegressionTest {

  private static final Long CAMPAIGN_ID = 114L;

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
  public void typeChangeOfRunningCampaignIsRejected() {
    runtime(CAMPAIGN_STATE.RUNNING, CAMPAIGN_TYPE.PREDICTIVE);

    try {
      campaignService.checkCampaignTypeChangeAllowed(CAMPAIGN_ID, form(CAMPAIGN_TYPE.PROGRESSIVE_RELEASED), true);
      fail("Çalışan campaign'de tip değişikliği reddedilmeli");
    } catch (CampaignIllegalStateException e) {
      assertEquals(CampaignService.CAMPAIGN_TYPE_CHANGE_ILLEGAL_STATE_KEY, e.getMessage());
    }
  }

  @Test
  public void typeChangeToPredictiveOfPausedCampaignIsRejected() {
    runtime(CAMPAIGN_STATE.PAUSED, CAMPAIGN_TYPE.PREVIEW_FREE);

    try {
      campaignService.checkCampaignTypeChangeAllowed(CAMPAIGN_ID, form(CAMPAIGN_TYPE.PREDICTIVE), true);
      fail("Duraklatılmış campaign'de tip değişikliği reddedilmeli");
    } catch (CampaignIllegalStateException expected) {
      // beklenen
    }
  }

  @Test
  public void typeChangeBetweenNonPredictiveTypesOfRunningCampaignIsRejected() {
    runtime(CAMPAIGN_STATE.RUNNING, CAMPAIGN_TYPE.PROGRESSIVE_RESERVED);

    try {
      campaignService.checkCampaignTypeChangeAllowed(CAMPAIGN_ID, form(CAMPAIGN_TYPE.PREVIEW_STRAIGHT), true);
      fail("Çalışan campaign'de tip değişikliği reddedilmeli");
    } catch (CampaignIllegalStateException expected) {
      // beklenen
    }
  }

  @Test
  public void typeChangeWithoutRuntimeContextIsAllowed() {
    campaignService.checkCampaignTypeChangeAllowed(CAMPAIGN_ID, form(CAMPAIGN_TYPE.PREDICTIVE), true);
  }

  @Test
  public void typeChangeOfStoppedCampaignIsAllowed() {
    runtime(CAMPAIGN_STATE.STOPPED, CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);

    campaignService.checkCampaignTypeChangeAllowed(CAMPAIGN_ID, form(CAMPAIGN_TYPE.PREDICTIVE), true);
  }

  @Test
  public void updateWithoutTypeChangeOfRunningCampaignIsAllowed() {
    runtime(CAMPAIGN_STATE.RUNNING, CAMPAIGN_TYPE.PREDICTIVE);

    campaignService.checkCampaignTypeChangeAllowed(CAMPAIGN_ID, form(CAMPAIGN_TYPE.PREDICTIVE), true);
  }

  /** Tech/NonTech yetkili kullanıcının formu tipi taşımaz; {@code int} alan 0 (PROGRESSIVE_RESERVED) gelir. */
  @Test
  public void restrictedUserUpdateOfRunningCampaignIsAllowed() {
    runtime(CAMPAIGN_STATE.RUNNING, CAMPAIGN_TYPE.PREDICTIVE);

    campaignService.checkCampaignTypeChangeAllowed(CAMPAIGN_ID, new CampaignFormDTO(), false);
  }

  private void runtime(CAMPAIGN_STATE state, CAMPAIGN_TYPE type) {
    Campaign runtime = mock(Campaign.class);
    when(runtime.getState()).thenReturn(state);
    org.mockito.Mockito.lenient().when(runtime.getType()).thenReturn(type);
    CampaignContext context = mock(CampaignContext.class);
    when(context.getCampaign()).thenReturn(runtime);
    contexts.put(CAMPAIGN_ID, context);
  }

  private static CampaignFormDTO form(CAMPAIGN_TYPE type) {
    CampaignFormDTO form = new CampaignFormDTO();
    form.setId(CAMPAIGN_ID);
    form.setType(type.ordinal());
    return form;
  }
}
