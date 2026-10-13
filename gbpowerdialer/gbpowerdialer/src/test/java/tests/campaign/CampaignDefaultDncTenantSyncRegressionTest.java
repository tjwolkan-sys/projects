package tests.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.util.Date;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.dao.ICampaignDao;
import com.powerdialer.dao.ITenantDao;
import com.powerdialer.dao.IUserDao;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.service.CampaignMementoService;
import com.powerdialer.service.CampaignService;

/**
 * Kampanya tenant'i degistiginde kampanyanin default DNC listesinin de yeni
 * tenant'a tasinmasini dogrular. Ayrica endDate senkronizasyonunun mevcut
 * davranisini karakterize eder.
 */
@RunWith(MockitoJUnitRunner.class)
public class CampaignDefaultDncTenantSyncRegressionTest {

  private static final Long CAMPAIGN_ID = 70L;
  private static final Long DEFAULT_DNC_ID = 102L;
  private static final Long OLD_TENANT_ID = 5L;
  private static final Long NEW_TENANT_ID = 9L;

  @Mock
  private ICampaignDao campaignDao;

  @Mock
  private CampaignDbOperations campaignDbOperations;

  @Mock
  private ITenantDao tenantDao;

  @Mock
  private DialerFacade dialerFacade;

  @Mock
  private CampaignMementoService campaignMementoService;

  @Mock
  private SessionFactory sessionFactory;

  @Mock
  private Session session;

  @Mock
  private IUserDao userDao;

  private CampaignService campaignService;
  private User dncSystemUser;

  @Before
  public void setUp() {
    campaignService = new CampaignService(campaignDao, campaignDbOperations, tenantDao, dialerFacade, campaignMementoService);
    ReflectionTestUtils.setField(campaignService, "sessionFactory", sessionFactory);
    ReflectionTestUtils.setField(campaignService, "userDao", userDao);
    dncSystemUser = new User();
  }

  @Test
  public void defaultDncTenantShouldFollowCampaignTenantChange() {
    Date expireDate = new Date(1_900_000_000_000L);
    DNC defaultDnc = defaultDnc(OLD_TENANT_ID, expireDate);
    Date endDate = new Date(1_800_000_000_000L);
    Campaign persistedCampaign = campaign(OLD_TENANT_ID, endDate, defaultDnc);
    Campaign requestCampaign = campaign(NEW_TENANT_ID, endDate, null);

    givenSessionReturnsDefaultDnc(defaultDnc);
    when(tenantDao.get(NEW_TENANT_ID)).thenReturn(tenant(NEW_TENANT_ID));
    when(userDao.getDncSystemUser()).thenReturn(dncSystemUser);

    syncDefaultDnc(persistedCampaign, requestCampaign);

    assertNotNull(defaultDnc.getTenant());
    assertEquals(NEW_TENANT_ID, defaultDnc.getTenant().getId());
    assertEquals(expireDate, defaultDnc.getExpireDate());
    assertSame(dncSystemUser, defaultDnc.getUpdateUser());
    assertNotNull(defaultDnc.getUpdateDate());
  }

  @Test
  public void defaultDncExpireDateShouldStillFollowCampaignEndDateChange() {
    DNC defaultDnc = defaultDnc(OLD_TENANT_ID, new Date(1_900_000_000_000L));
    Campaign persistedCampaign = campaign(OLD_TENANT_ID, new Date(1_800_000_000_000L), defaultDnc);
    Date newEndDate = new Date(1_850_000_000_000L);
    Campaign requestCampaign = campaign(OLD_TENANT_ID, newEndDate, null);

    givenSessionReturnsDefaultDnc(defaultDnc);
    when(userDao.getDncSystemUser()).thenReturn(dncSystemUser);

    syncDefaultDnc(persistedCampaign, requestCampaign);

    assertEquals(newEndDate, defaultDnc.getExpireDate());
    assertEquals(OLD_TENANT_ID, defaultDnc.getTenant().getId());
    verifyNoInteractions(tenantDao);
  }

  @Test
  public void defaultDncShouldNotBeTouchedWhenTenantAndEndDateUnchanged() {
    Date expireDate = new Date(1_900_000_000_000L);
    DNC defaultDnc = defaultDnc(OLD_TENANT_ID, expireDate);
    Date endDate = new Date(1_800_000_000_000L);
    Campaign persistedCampaign = campaign(OLD_TENANT_ID, endDate, defaultDnc);
    Campaign requestCampaign = campaign(OLD_TENANT_ID, endDate, null);

    syncDefaultDnc(persistedCampaign, requestCampaign);

    assertEquals(OLD_TENANT_ID, defaultDnc.getTenant().getId());
    assertEquals(expireDate, defaultDnc.getExpireDate());
    assertNull(defaultDnc.getUpdateDate());
    verifyNoInteractions(sessionFactory, tenantDao, userDao);
  }

  @Test
  public void tenantSyncShouldBeSkippedWhenCampaignHasNoDefaultDnc() {
    Campaign persistedCampaign = campaign(OLD_TENANT_ID, new Date(1_800_000_000_000L), null);
    Campaign requestCampaign = campaign(NEW_TENANT_ID, new Date(1_850_000_000_000L), null);

    syncDefaultDnc(persistedCampaign, requestCampaign);

    verifyNoInteractions(sessionFactory, tenantDao, userDao);
  }

  @Test
  public void tenantSyncShouldBeSkippedWhenRequestHasNoTenant() {
    Date expireDate = new Date(1_900_000_000_000L);
    DNC defaultDnc = defaultDnc(OLD_TENANT_ID, expireDate);
    Date endDate = new Date(1_800_000_000_000L);
    Campaign persistedCampaign = campaign(OLD_TENANT_ID, endDate, defaultDnc);
    Campaign requestCampaign = campaign(null, endDate, null);

    syncDefaultDnc(persistedCampaign, requestCampaign);

    assertEquals(OLD_TENANT_ID, defaultDnc.getTenant().getId());
    verifyNoInteractions(sessionFactory, tenantDao, userDao);
  }

  private void syncDefaultDnc(Campaign persistedCampaign, Campaign requestCampaign) {
    ReflectionTestUtils.invokeMethod(campaignService, "syncDefaultDncWithCampaign", persistedCampaign, requestCampaign);
  }

  private void givenSessionReturnsDefaultDnc(DNC defaultDnc) {
    when(sessionFactory.getCurrentSession()).thenReturn(session);
    when(session.get(DNC.class, (Serializable) DEFAULT_DNC_ID)).thenReturn(defaultDnc);
  }

  private DNC defaultDnc(Long tenantId, Date expireDate) {
    DNC dnc = new DNC();
    dnc.setId(DEFAULT_DNC_ID);
    dnc.setTenant(tenant(tenantId));
    dnc.setExpireDate(expireDate);
    return dnc;
  }

  private Campaign campaign(Long tenantId, Date endDate, DNC defaultDnc) {
    Campaign campaign = new Campaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setEndDate(endDate);
    campaign.setDnc(defaultDnc);
    if (tenantId != null) {
      campaign.setTenant(tenant(tenantId));
    }
    return campaign;
  }

  private Tenant tenant(Long id) {
    Tenant tenant = new Tenant();
    tenant.setId(id);
    return tenant;
  }
}
