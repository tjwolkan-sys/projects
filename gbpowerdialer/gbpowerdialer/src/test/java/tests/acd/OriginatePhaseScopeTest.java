package tests.acd;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import com.powerdialer.engine.acd.OriginatePhase;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

/**
 * FIX-GLB90060295: Originate (ring) fazinda agent'in idle havuzda BIRAKILACAGI kampanya kumesini
 * kilitler.
 *
 * <p>Kapsam icinde: GAMMA (mevcut davranis, SORUN 1/1A ile getirildi) ve PROGRESSIVE_RELEASED
 * (bu fix). Kapsam disinda: PROGRESSIVE_RESERVED, PREVIEW_FREE, PREVIEW_STRAIGHT,
 * PREDICTIVE-ALPHA/BETA ve OUTBOUND_IVR — bu tiplerde agent originate aninda rezerve edilmeye
 * devam eder.</p>
 *
 * <p>PROGRESSIVE_RESERVED ozellikle disaridadir: orada rezervasyon hata degil urun garantisidir
 * (UserFindingContextBuilder secim aninda UserPauseMessage(true) publish eder, hangup'ta kalkar;
 * UserPopulationEvaluator bu tipte userId/assignedUserId'yi her kosulda korur).</p>
 */
public class OriginatePhaseScopeTest {

  private static Call callFor(Constants.CAMPAIGN_TYPE type, Constants.PREDICTIVE_STRATEGY strategy) {
    Campaign campaign = new Campaign();
    campaign.setType(type);
    campaign.setPredictiveStrategy(strategy);
    Call call = new Call();
    call.setCampaign(campaign);
    return call;
  }

  @Test
  public void gammaOriginatePhaseLeavesAgentInIdlePool() {
    assertTrue("GAMMA originate fazinda rezervasyon yapilmaz (mevcut davranis)",
        OriginatePhase.isWithoutReservation(
            callFor(Constants.CAMPAIGN_TYPE.PREDICTIVE, Constants.PREDICTIVE_STRATEGY.GAMMA)));
  }

  @Test
  public void progressiveReleasedOriginatePhaseLeavesAgentInIdlePool() {
    assertTrue("PROGRESSIVE_RELEASED originate fazinda rezervasyon yapilmamali",
        OriginatePhase.isWithoutReservation(
            callFor(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED, null)));
  }

  @Test
  public void progressiveReservedAlwaysReservesAgent() {
    assertFalse("PROGRESSIVE_RESERVED'de rezervasyon urun garantisidir, kaldirilmaz",
        OriginatePhase.isWithoutReservation(
            callFor(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED, null)));
  }

  @Test
  public void predictiveAlphaOriginatePhaseLeavesAgentInIdlePool() {
    assertTrue("PREDICTIVE-ALPHA originate fazinda rezervasyon yapilmamali; pacing zaten "
            + "DynamicBoostManager'in dinamik boost'u ile yapilir",
        OriginatePhase.isWithoutReservation(
            callFor(Constants.CAMPAIGN_TYPE.PREDICTIVE, Constants.PREDICTIVE_STRATEGY.ALPHA)));
  }

  @Test
  public void previewAndIvrAndBetaAlwaysReserveAgent() {
    assertFalse("PREVIEW_FREE kapsam disi", OriginatePhase.isWithoutReservation(
        callFor(Constants.CAMPAIGN_TYPE.PREVIEW_FREE, null)));
    assertFalse("PREVIEW_STRAIGHT kapsam disi", OriginatePhase.isWithoutReservation(
        callFor(Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT, null)));
    assertFalse("OUTBOUND_IVR kapsam disi", OriginatePhase.isWithoutReservation(
        callFor(Constants.CAMPAIGN_TYPE.OUTBOUND_IVR, null)));
    assertFalse("PREDICTIVE-BETA kapsam disi (butcesini PredictiveBetaCallManager yonetir)",
        OriginatePhase.isWithoutReservation(
            callFor(Constants.CAMPAIGN_TYPE.PREDICTIVE, Constants.PREDICTIVE_STRATEGY.BETA)));
  }

  @Test
  public void symbolicPreAssignmentCampaignSetIsExplicit() {
    // Answer fazi guard'lari (trySearchLessOccupiedUser) faz kontrolu olmadan bu kumeye bakar.
    assertTrue(OriginatePhase.isSymbolicPreAssignmentCampaign(
        callFor(Constants.CAMPAIGN_TYPE.PREDICTIVE, Constants.PREDICTIVE_STRATEGY.GAMMA).getCampaign()));
    assertTrue(OriginatePhase.isSymbolicPreAssignmentCampaign(
        callFor(Constants.CAMPAIGN_TYPE.PREDICTIVE, Constants.PREDICTIVE_STRATEGY.ALPHA).getCampaign()));
    assertTrue(OriginatePhase.isSymbolicPreAssignmentCampaign(
        callFor(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED, null).getCampaign()));
    assertFalse(OriginatePhase.isSymbolicPreAssignmentCampaign(
        callFor(Constants.CAMPAIGN_TYPE.PREDICTIVE, Constants.PREDICTIVE_STRATEGY.BETA).getCampaign()));
    assertFalse(OriginatePhase.isSymbolicPreAssignmentCampaign(
        callFor(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED, null).getCampaign()));
    assertFalse(OriginatePhase.isSymbolicPreAssignmentCampaign(null));
  }

  @Test
  public void answerPhaseReservesAgentEvenForProgressiveReleased() {
    Call originated = callFor(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED, null);
    originated.setOriginationDate(1.0d);
    assertFalse("originationDate dolu -> originate fazi degil",
        OriginatePhase.isWithoutReservation(originated));

    Call ringing = callFor(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED, null);
    ringing.setStartDate(1.0d);
    assertFalse("startDate dolu -> answer fazi", OriginatePhase.isWithoutReservation(ringing));

    Call answered = callFor(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED, null);
    answered.setAnswerDate(1.0d);
    assertFalse("answerDate dolu -> answer fazi; gercek secim burada yapilir ve agent havuzdan alinir",
        OriginatePhase.isWithoutReservation(answered));
  }

  @Test
  public void ownerBoundCallbackReservesAgentEvenForProgressiveReleased() {
    Call call = callFor(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED, null);
    Campaign campaign = call.getCampaign();
    campaign.setAllowCallback(true);
    campaign.setCallbackToAppointer(true);
    campaign.setAppointerReset(false);
    campaign.setColResultUserId("user_id");
    campaign.setColResultCallbackDate("callback_date");
    call.setRawData(new JSONObject().put("user_id", "1"));

    assertTrue("test kurulumu owner-bound callback uretmeli", call.isOwnerBoundCallback());
    assertFalse("Appointed callback sahibi originate'te rezerve edilmeye devam etmeli",
        OriginatePhase.isWithoutReservation(call));
  }

  @Test
  public void nullSafeForMissingCallOrCampaign() {
    assertFalse(OriginatePhase.isWithoutReservation(null));
    assertFalse(OriginatePhase.isWithoutReservation(new Call()));
  }
}
