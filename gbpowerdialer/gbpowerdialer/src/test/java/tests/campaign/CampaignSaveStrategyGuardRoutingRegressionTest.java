package tests.campaign;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerdialer.aop.CampaignAspect;
import com.powerdialer.controller.CampaignController;
import com.powerdialer.dto.CampaignFormDTO;
import com.powerdialer.exceptions.CampaignIllegalStateException;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.PREDICTIVE_STRATEGY;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * FIX-333607: predictive strateji kontrolü yalnız değişikliği runtime'a init'siz uygulayan kaydetme
 * yollarında (düz kaydet, kaydet + reload) ve kayıttan önce çalışır.
 *
 * <p>Kaydet + restart yolu stop'ta eski stratejiyi kapatır, start'ta yenisini init eder; bu yol
 * engellenmemelidir. Kontrol {@code CampaignService.merge} içinde olsaydı hem bu yolu hem de
 * stratejiyi null gönderen Tech/NonTech kullanıcıların kayıtlarını engellerdi.</p>
 *
 * <p>Campaign tipi kontrolü aynı yollarda stratejiden önce çalışır. Tipi yalnız tam düzenleme
 * yetkili kullanıcı gönderir; Tech/NonTech formunda {@code int} tip 0 gelir ve kontrol edilmez.</p>
 */
public class CampaignSaveStrategyGuardRoutingRegressionTest {

  private static final long ID = 7L;

  private final CampaignService service = mock(CampaignService.class);
  private final PermissionUtil permissionUtil = mock(PermissionUtil.class);
  private CampaignController controller;
  private CampaignFormDTO form;

  @Before
  public void setUp() throws Exception {
    controller = new CampaignController(service, mock(CampaignAspect.class));
    ReflectionTestUtils.setField(controller, "permissionUtil", permissionUtil);
    when(permissionUtil.hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN, Collections.singleton(String.valueOf(ID))))
        .thenReturn(true);
    form = new CampaignFormDTO();
    form.setId(ID);
    form.setPredictiveStrategy(PREDICTIVE_STRATEGY.GAMMA);
    when(service.merge(any(CampaignFormDTO.class))).thenReturn(form);
  }

  @Test
  public void plainSaveChecksStrategyBeforeSaving() throws Exception {
    controller.merge(ID, form);

    InOrder order = inOrder(service);
    order.verify(service).checkCampaignTypeChangeAllowed(ID, form, true);
    order.verify(service).checkPredictiveStrategyChangeAllowed(ID, form);
    order.verify(service).merge(form);
  }

  @Test
  public void saveAndReloadChecksStrategyBeforeSaving() throws Exception {
    controller.mergeAndReloadRunningCampaign(ID, form);

    InOrder order = inOrder(service);
    order.verify(service).checkCampaignTypeChangeAllowed(ID, form, true);
    order.verify(service).checkPredictiveStrategyChangeAllowed(ID, form);
    order.verify(service).merge(form);
  }

  @Test
  public void rejectedStrategyChangeIsNotSaved() throws Exception {
    doThrow(new CampaignIllegalStateException(CampaignService.PREDICTIVE_STRATEGY_CHANGE_ILLEGAL_STATE_KEY))
        .when(service).checkPredictiveStrategyChangeAllowed(ID, form);

    try {
      controller.merge(ID, form);
      fail("Reddedilen strateji değişikliği hata vermeli");
    } catch (CampaignIllegalStateException expected) {
      // beklenen
    }
    verify(service, never()).merge(any(CampaignFormDTO.class));
    verify(service, never()).updateContext(anyLong());
  }

  @Test
  public void saveAndRestartIsNotBlockedBecauseRestartReinitializesStrategy() throws Exception {
    controller.mergeAndRestartRunningCampaign(ID, form);

    verify(service, never()).checkPredictiveStrategyChangeAllowed(anyLong(), any(CampaignFormDTO.class));
    verify(service, never()).checkCampaignTypeChangeAllowed(anyLong(), any(CampaignFormDTO.class), anyBoolean());
    verify(service).merge(form);
    verify(service).restartRunningCampaign(ID);
  }

  @Test
  public void rejectedTypeChangeIsNotSaved() throws Exception {
    doThrow(new CampaignIllegalStateException(CampaignService.CAMPAIGN_TYPE_CHANGE_ILLEGAL_STATE_KEY))
        .when(service).checkCampaignTypeChangeAllowed(ID, form, true);

    try {
      controller.mergeAndReloadRunningCampaign(ID, form);
      fail("Reddedilen tip değişikliği hata vermeli");
    } catch (CampaignIllegalStateException expected) {
      // beklenen
    }
    verify(service, never()).merge(any(CampaignFormDTO.class));
    verify(service, never()).updateContext(anyLong());
  }

  @Test
  public void restrictedUserSaveDoesNotTreatMissingTypeAsChange() throws Exception {
    when(permissionUtil.hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN, Collections.singleton(String.valueOf(ID))))
        .thenReturn(false);

    controller.merge(ID, form);

    verify(service).checkCampaignTypeChangeAllowed(ID, form, false);
    verify(service).merge(form);
  }
}
