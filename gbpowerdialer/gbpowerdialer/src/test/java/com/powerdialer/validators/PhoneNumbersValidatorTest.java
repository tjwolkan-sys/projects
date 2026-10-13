package com.powerdialer.validators;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.exceptions.PhoneNumberNameDuplicateException;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.service.CampaignService;

@RunWith(MockitoJUnitRunner.class)
public class PhoneNumbersValidatorTest {

  private CampaignValidator campaignValidator;
  private PhoneNumberColumn phoneNumberColumnOld1, phoneNumberColumnOld2, phoneNumberColumnNew1, phoneNumberColumnNew2;
  private Campaign campaignOld, campaignNew;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CampaignService campaignService;

  @Before
  public void setUp() {
    campaignValidator = new CampaignValidator();
    campaignValidator.setCampaignService(campaignService);
    phoneNumberColumnOld1 = createPhoneNumberColumn(100L);
    phoneNumberColumnOld2 = createPhoneNumberColumn(101L);
    phoneNumberColumnNew1 = createPhoneNumberColumn(100L);
    phoneNumberColumnNew2 = createPhoneNumberColumn(101L);
    campaignOld = createCampaign();
    campaignNew = createCampaign();
  }

  private Campaign createCampaign() {
    Campaign result = new Campaign();
    result.setName("campaign");
    result.setId(1L);
    result.setPhoneNumberColumns(new ArrayList<>());
    return result;
  }

  private PhoneNumberColumn createPhoneNumberColumn(Long id) {
    PhoneNumberColumn result = new PhoneNumberColumn();
    result.setColNumber("name1");
    result.setId(id);
    return result;
  }

  @Test
  public void whenSaveWithSameNameAndDifferentDeleteStatusThenPass() {
    Mockito.doReturn(campaignOld).when(campaignService).getInitializedCampaign(1L);
    phoneNumberColumnNew2.setDeleted(true);
    campaignNew.getPhoneNumberColumns().addAll(Arrays.asList(phoneNumberColumnNew1, phoneNumberColumnNew2));

    campaignValidator.validate(campaignNew);
  }

  @Test
  public void whenSaveWithDifferentNameAndSameFalseDeleteStatusThenPass() {
    Mockito.doReturn(campaignOld).when(campaignService).getInitializedCampaign(1L);
    phoneNumberColumnNew2.setColNumber("name2");
    campaignNew.getPhoneNumberColumns().addAll(Arrays.asList(phoneNumberColumnNew1, phoneNumberColumnNew2));

    campaignValidator.validate(campaignNew);
  }

  @Test
  public void whenSaveWithSameNameAndSameTrueDeleteStatusThenPass() {
    Mockito.doReturn(campaignOld).when(campaignService).getInitializedCampaign(1L);
    phoneNumberColumnNew1.setDeleted(true);
    phoneNumberColumnNew2.setDeleted(true);
    campaignNew.getPhoneNumberColumns().addAll(Arrays.asList(phoneNumberColumnNew1, phoneNumberColumnNew2));

    campaignValidator.validate(campaignNew);
  }

  @Test(expected = PhoneNumberNameDuplicateException.class)
  public void whenSaveWithSameNameAndSameFalseDeleteStatusThenFail() {
    Mockito.doReturn(campaignOld).when(campaignService).getInitializedCampaign(1L);
    campaignNew.getPhoneNumberColumns().addAll(Arrays.asList(phoneNumberColumnNew1, phoneNumberColumnNew2));

    campaignValidator.validate(campaignNew);
  }

  @Test
  public void whenSaveWithSameNameAndTrueDeleteStatusInNewCampaignThenPass() {
    Mockito.doReturn(campaignOld).when(campaignService).getInitializedCampaign(1L);
    campaignOld.getPhoneNumberColumns().add(phoneNumberColumnOld1);
    phoneNumberColumnNew1.setDeleted(true);
    campaignNew.getPhoneNumberColumns().add(phoneNumberColumnNew1);

    campaignValidator.validate(campaignNew);
  }

  @Test
  public void whenSaveWithSameNameAndFalseDeleteStatusAndSameIdInNewCampaignThenPass() {
    Mockito.doReturn(campaignOld).when(campaignService).getInitializedCampaign(1L);
    campaignOld.getPhoneNumberColumns().add(phoneNumberColumnOld1);
    phoneNumberColumnNew1.setColSort(10);
    campaignNew.getPhoneNumberColumns().add(phoneNumberColumnNew1);

    campaignValidator.validate(campaignNew);
  }

  @Test
  public void whenSaveWithSameNameAndFalseDeleteStatusAndDifferentIdInNewCampaignThenPass() {
    Mockito.doReturn(campaignOld).when(campaignService).getInitializedCampaign(1L);
    campaignOld.getPhoneNumberColumns().addAll(Arrays.asList(phoneNumberColumnOld1, phoneNumberColumnOld2));
    phoneNumberColumnNew1.setDeleted(true);
    campaignNew.getPhoneNumberColumns().addAll(Arrays.asList(phoneNumberColumnNew1, phoneNumberColumnNew2));

    campaignValidator.validate(campaignNew);
  }

  @Test(expected = PhoneNumberNameDuplicateException.class)
  public void whenSaveWithSameNameAndSameFalseDeleteStatusAndDifferentIdInNewCampaignThenFail() {
    Mockito.doReturn(campaignOld).when(campaignService).getInitializedCampaign(1L);
    campaignOld.getPhoneNumberColumns().addAll(Arrays.asList(phoneNumberColumnOld1, phoneNumberColumnOld2));
    campaignNew.getPhoneNumberColumns().add(phoneNumberColumnNew2);

    campaignValidator.validate(campaignNew);
  }

  @Test(expected = PhoneNumberNameDuplicateException.class)
  public void whenSaveWithSameNameAndSameFalseDeleteStatusAndNullIdInNewCampaignThenFail() {
    Mockito.doReturn(campaignOld).when(campaignService).getInitializedCampaign(1L);
    campaignOld.getPhoneNumberColumns().addAll(Arrays.asList(phoneNumberColumnOld1, phoneNumberColumnOld2));
    phoneNumberColumnNew1.setId(null);
    campaignNew.getPhoneNumberColumns().add(phoneNumberColumnNew1);

    campaignValidator.validate(campaignNew);
  }

}
