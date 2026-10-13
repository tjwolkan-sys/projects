package tests.campaign;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.dozer.DozerBeanMapper;

import com.powerdialer.dto.CampaignListDTO;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

public class CampaignRuntimeCountersCharacterizationTest {

  @Test
  public void decrementMethodsDoNotMakeRuntimeCountersNegative() {
    Campaign campaign = new Campaign();
    Call call = call("call-1", Constants.CALL_TYPES.FRESH);

    campaign.decrementActiveCalls(call);
    campaign.decrementRingingCalls(call);
    campaign.decrementInCallsSize(call);

    assertEquals(0, campaign.getActiveCalls().get());
    assertEquals(0, campaign.getRingingCalls().get());
    assertEquals(0, campaign.getRingingFreshCalls().get());
    assertEquals(0, campaign.getInCalls().get());
  }

  @Test
  public void ringingCountersTrackCallTypeAndStopAtZero() {
    Campaign campaign = new Campaign();
    Call freshCall = call("fresh-call", Constants.CALL_TYPES.FRESH);
    Call retryCall = call("retry-call", Constants.CALL_TYPES.RETRY);
    Call callbackCall = call("callback-call", Constants.CALL_TYPES.CALLBACK);

    campaign.incrementRingingCalls(freshCall);
    campaign.incrementRingingCalls(retryCall);
    campaign.incrementRingingCalls(callbackCall);

    assertEquals(3, campaign.getRingingCalls().get());
    assertEquals(1, campaign.getRingingFreshCalls().get());
    assertEquals(1, campaign.getRingingRetryCalls().get());
    assertEquals(1, campaign.getRingingCbCalls().get());

    campaign.decrementRingingCalls(freshCall);
    campaign.decrementRingingCalls(retryCall);
    campaign.decrementRingingCalls(callbackCall);
    campaign.decrementRingingCalls(callbackCall);

    assertEquals(0, campaign.getRingingCalls().get());
    assertEquals(0, campaign.getRingingFreshCalls().get());
    assertEquals(0, campaign.getRingingRetryCalls().get());
    assertEquals(0, campaign.getRingingCbCalls().get());
  }

  @Test
  public void callsInProgressUsesCallIdAsUniqueKey() {
    Campaign campaign = new Campaign();
    Call call = call("same-call-id", Constants.CALL_TYPES.FRESH);

    campaign.addCallInProgress(call);
    campaign.addCallInProgress(call);

    assertEquals(1, campaign.getCallsInProgressCount());

    campaign.removeCallInProgress(call);
    campaign.removeCallInProgress(call);

    assertEquals(0, campaign.getCallsInProgressCount());
  }

  @Test
  public void campaignListDtoIncludesCallsInProgressCount() {
    Campaign campaign = new Campaign();
    campaign.addCallInProgress(call("call-1", Constants.CALL_TYPES.FRESH));
    campaign.addCallInProgress(call("call-2", Constants.CALL_TYPES.RETRY));

    CampaignListDTO dto = new DozerBeanMapper().map(campaign, CampaignListDTO.class);

    assertEquals(2, dto.getCallsInProgressCount());
  }

  @Test
  public void campaignListDtoIncludesFreshDataListSize() {
    Campaign campaign = new Campaign();
    campaign.setFreshDataListSize(11);

    CampaignListDTO dto = new DozerBeanMapper().map(campaign, CampaignListDTO.class);

    assertEquals(11, dto.getFreshDataListSize());
  }

  private Call call(String id, Constants.CALL_TYPES callType) {
    Call call = new Call();
    call.setId(id);
    call.setCalledNumber("5550000000");
    call.setCallType(callType);
    return call;
  }
}
