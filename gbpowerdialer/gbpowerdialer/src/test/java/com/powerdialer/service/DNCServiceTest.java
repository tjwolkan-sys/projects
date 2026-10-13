package com.powerdialer.service;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.dozer.Mapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.dao.IDNCDao;
import com.powerdialer.dao.IDNCPhoneNumberDao;
import com.powerdialer.dao.ITenantDao;
import com.powerdialer.dto.DNCFormDTO;
import com.powerdialer.dto.DNCListDTO;
import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.exceptions.DncPhoneExpiredDateBeforeCreateDateException;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.DNCPhoneNumber;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.util.Constants;
import com.powerdialer.validators.ValidationResult;
import com.powerdialer.validators.Validator;

@RunWith(MockitoJUnitRunner.class)
public class DNCServiceTest {

  @Mock
  private IDNCDao dncDao;

  @Mock
  private IDNCPhoneNumberDao dncPhoneNumberDao;

  @Mock
  private ITenantDao tenantDao;

  @Mock
  private PermissionUtil permissionUtil;

  @Mock
  private Mapper mapper;

  @Mock
  private AuthUtil authUtil;

  @Mock
  private Validator<DNC> validator;

  private DNCService dncService;

  @Before
  public void setUp() {
    dncService = new DNCService(dncDao, dncPhoneNumberDao, tenantDao, permissionUtil);
    ReflectionTestUtils.setField(dncService, "mapper", mapper);
    ReflectionTestUtils.setField(dncService, "authUtil", authUtil);
    ReflectionTestUtils.setField(dncService, "validator", validator);

    when(validator.validate(any(DNC.class))).thenReturn(new ValidationResult(true, new String[0]));
    when(authUtil.isUserInRole(Constants.ROLE_SUPERADMIN)).thenReturn(true);

    SecurityContext securityContext = mock(SecurityContext.class);
    Authentication authentication = mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn(new User());
    when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);
  }

  @After
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void mergeShouldIgnoreDeletedPhoneNumbersFromCurrentRequestWhileValidatingListExpireDate() throws DuplicatedNameException {
    DNCFormDTO form = new DNCFormDTO();
    DNCFormDTO mappedResponse = new DNCFormDTO();

    DNC entity = new DNC();
    entity.setId(7L);
    entity.setTenant(tenant(1L));
    entity.setExpireDate(new Date(1_777_344_000_000L));

    DNCPhoneNumber persistedPhone = persistedPhoneNumber(11L, new Date(1_777_603_200_000L), new Date(1_777_084_800_000L), false);
    DNCPhoneNumber deletedPhone = persistedPhoneNumber(11L, new Date(1_777_603_200_000L), new Date(1_777_084_800_000L), true);
    entity.setPhoneNumbers(Collections.singletonList(deletedPhone));

    when(mapper.map(form, DNC.class)).thenReturn(entity);
    when(mapper.map(entity, DNCFormDTO.class)).thenReturn(mappedResponse);
    when(dncDao.merge(entity)).thenReturn(entity);
    when(dncPhoneNumberDao.findByDncListId(7L)).thenReturn(Collections.singletonList(persistedPhone));

    DNCFormDTO result = dncService.merge(form);

    assertSame(mappedResponse, result);
    verify(dncDao).merge(entity);
  }

  @Test(expected = DncPhoneExpiredDateBeforeCreateDateException.class)
  public void mergeShouldUsePersistedCreateDateWhenValidatingExistingPhoneExpireDate() throws DuplicatedNameException {
    DNCFormDTO form = new DNCFormDTO();

    DNC entity = new DNC();
    entity.setId(8L);
    entity.setTenant(tenant(1L));
    entity.setExpireDate(new Date(1_777_776_000_000L));

    DNCPhoneNumber requestPhone = persistedPhoneNumber(21L, new Date(1_777_430_800_000L), new Date(1_776_652_800_000L), false);
    entity.setPhoneNumbers(Collections.singletonList(requestPhone));

    DNCPhoneNumber persistedPhone = persistedPhoneNumber(21L, new Date(1_777_430_800_000L), new Date(1_777_517_200_000L), false);

    when(mapper.map(form, DNC.class)).thenReturn(entity);
    when(dncPhoneNumberDao.findByDncListId(8L)).thenReturn(Collections.singletonList(persistedPhone));

    try {
      dncService.merge(form);
    } finally {
      verify(dncDao, never()).merge(any(DNC.class));
    }
  }

  @Test
  public void getDNCsByCampaignIdShouldMarkCampaignDefaultDnc() {
    Long campaignId = 119L;
    Campaign campaign = new Campaign();
    campaign.setId(campaignId);

    DNC defaultDnc = new DNC();
    defaultDnc.setId(102L);
    defaultDnc.setCampaign(campaign);

    DNC selectedDnc = new DNC();
    selectedDnc.setId(103L);

    DNCListDTO defaultDto = new DNCListDTO();
    DNCListDTO selectedDto = new DNCListDTO();

    when(dncDao.getDNCsByCampaignId(campaignId)).thenReturn(Arrays.asList(defaultDnc, selectedDnc));
    when(mapper.map(defaultDnc, DNCListDTO.class)).thenReturn(defaultDto);
    when(mapper.map(selectedDnc, DNCListDTO.class)).thenReturn(selectedDto);

    List<DNCListDTO> result = dncService.getDNCsByCampaignId(campaignId).getData();

    assertTrue(result.get(0).isCampaignDefault());
    assertFalse(result.get(1).isCampaignDefault());
  }

  private Tenant tenant(Long id) {
    Tenant tenant = new Tenant();
    tenant.setId(id);
    return tenant;
  }

  private DNCPhoneNumber persistedPhoneNumber(Long id, Date expiredDate, Date createDate, boolean deleted) {
    DNCPhoneNumber phoneNumber = new DNCPhoneNumber();
    phoneNumber.setId(id);
    phoneNumber.setExpiredDate(expiredDate);
    phoneNumber.setCreateDate(createDate);
    phoneNumber.setDeleted(deleted);
    phoneNumber.setEnabled(true);
    phoneNumber.setPhoneNumber("905555555555");
    return phoneNumber;
  }
}
