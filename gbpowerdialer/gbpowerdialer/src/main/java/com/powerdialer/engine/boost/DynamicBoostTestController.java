package com.powerdialer.engine.boost;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.service.CampaignService;

// TODO remove!
@RestController
@RequestMapping(value = "/api/boost", produces = "application/json")

public class DynamicBoostTestController {

  private static final Logger log = LoggerFactory.getLogger(DynamicBoostTestController.class);

  @Autowired
  private DynamicBoostManager dynamicBoostManager;

  @Autowired
  private DynamicBoostTestConfigurator dynamicBoostTestConfigurator;

  @Autowired
  private DynamicBoostTestCallsEmulator testCallsEmulator;

  @Autowired
  private CampaignService campaignService;

  @GetMapping("/getData/{campaignId}")
  public ResponseEntity<?> getData(@PathVariable Long campaignId) {
    return ResponseEntity.ok(Optional.ofNullable(dynamicBoostManager.getTestData(campaignId))
        .map(BoostDataTestDto::new)
        .orElse(null));
  }

  @GetMapping("/getDataHistory/{campaignId}")
  public ResponseEntity<?> getDataHistory(@PathVariable Long campaignId) {
    return ResponseEntity.ok(Optional.ofNullable(dynamicBoostManager.getTestData(campaignId))
        .map(CampaignBoostData::getDataHistory)
        .orElse(null));
  }

  @PostMapping("/addFailedCall/{campaignId}")
  public ResponseEntity<?> addFailedCall(@PathVariable Long campaignId, @RequestBody Integer count) {
    if (count > 0) {
      Call call = DynamicBoostManager.createFakeCall(campaignId);
      for (int i = 1; i <= count; i++) {
        dynamicBoostManager.onCallFailed(call);
      }
    }
    return ResponseEntity.ok().build();
  }

  @PostMapping("/addAnsweredCall/{campaignId}")
  public ResponseEntity<?> addAnsweredCall(@PathVariable Long campaignId, @RequestBody Integer count) {
    if (count > 0) {
      Call call = DynamicBoostManager.createFakeCall(campaignId);
      for (int i = 1; i <= count; i++) {
        dynamicBoostManager.onCallAnsweredByClient(call);
      }
    }
    return ResponseEntity.ok().build();
  }

  @PostMapping("/addAbandonedCall/{campaignId}")
  public ResponseEntity<?> addAbandonedCall(@PathVariable Long campaignId, @RequestBody Integer count) {
    if (count > 0) {
      Call call = DynamicBoostManager.createFakeCall(campaignId);
      for (int i = 1; i <= count; i++) {
        dynamicBoostManager.onCallAbandoned(call);
      }
    }
    return ResponseEntity.ok().build();
  }

  @PostMapping("/addHandledCall/{campaignId}")
  public ResponseEntity<?> addHandledCall(@PathVariable Long campaignId, @RequestBody Integer count) {
    if (count > 0) {
      Call call = DynamicBoostManager.createFakeCall(campaignId);
      for (int i = 1; i <= count; i++) {
        dynamicBoostManager.onCallAnsweredByAgent(call);
      }
    }
    return ResponseEntity.ok().build();
  }

  @PostMapping("/applyEmulation/{campaignId}")
  public ResponseEntity<?> applyEmulationParams(@PathVariable Long campaignId, @RequestBody TestCallsEmulationParams params) {
    testCallsEmulator.applyEmulationParams(campaignId, params);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/stopEmulation/{campaignId}")
  public ResponseEntity<?> stopEmulation(@PathVariable Long campaignId) {
    testCallsEmulator.stopEmulation(campaignId);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/getEmulationParams/{campaignId}")
  public ResponseEntity<TestCallsEmulationParams> getEmulationParams(@PathVariable Long campaignId) {
    return ResponseEntity.ok(testCallsEmulator.getEmulationParams(campaignId));
  }

  @GetMapping("/getConfig")
  public ResponseEntity<DynamicBoostTestConfig> getConfig() {
    return ResponseEntity.ok(dynamicBoostTestConfigurator.getConfig());
  }

  @PostMapping("/setConfig")
  public ResponseEntity<?> setConfig(@RequestBody DynamicBoostTestConfig config) {
    dynamicBoostTestConfigurator.setConfig(config);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/resetCalculation/{campaignId}")
  public ResponseEntity<?> resetCalculation(@PathVariable Long campaignId) {
    Campaign campaign = campaignService.getInitializedCampaign(campaignId);
    if (campaign != null) {
      log.info("Calculation reset requested for {}", campaign);
      dynamicBoostManager.resetCalculation(campaign);
    } else {
      log.warn("Could not reset calculation: campaign with id {} is not found", campaignId);
    }
    return ResponseEntity.ok().build();
  }

}
