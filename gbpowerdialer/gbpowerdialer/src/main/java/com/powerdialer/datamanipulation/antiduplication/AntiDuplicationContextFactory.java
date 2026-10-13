package com.powerdialer.datamanipulation.antiduplication;

import java.util.HashSet;
import java.util.Set;

import java.sql.SQLException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

@Component
public class AntiDuplicationContextFactory {

  /**
   * Context which do nothing with input data
   */
  private final IAntiDuplicationContext NO_OP_CONTEXT = (row) -> row;

  @Autowired
  private CampaignDbOperations campaignDbOperations;

  public IAntiDuplicationContext createContext(Campaign campaign, Constants.PHONE_NUMBERS_ANTI_DUPLICATION strategyEnum) throws SQLException {
    if (strategyEnum == null)
      return NO_OP_CONTEXT;
    AntiDuplicationStrategy strategy;
    Set<String> alreadyInsertedValues;
    switch (strategyEnum) {
      case IN_FILE_EXCLUDE_NUMBER:
        alreadyInsertedValues = new HashSet<>();
        strategy = new SkipValueAntiDuplicationStrategy();
        break;
      case IN_FILE_EXCLUDE_CONTACT:
        alreadyInsertedValues = new HashSet<>();
        strategy = new SkipRowAntiDuplicationStrategy();
        break;
      case IN_DATABASE_EXCLUDE_NUMBER:
        alreadyInsertedValues = convertToStringSet(campaignDbOperations.getAllPhoneNumbers(campaign));
        strategy = new SkipValueAntiDuplicationStrategy();
        break;
      case IN_DATABASE_EXCLUDE_CONTACT:
        alreadyInsertedValues = convertToStringSet(campaignDbOperations.getAllPhoneNumbers(campaign));
        strategy = new SkipRowAntiDuplicationStrategy();
        break;
      default:
        return NO_OP_CONTEXT;
    }
    return new AntiDuplicationContext(strategy, campaign, alreadyInsertedValues);
  }

  private Set<String> convertToStringSet(JSONArray jsonArray) {
    Set<String> result = new HashSet<>();
    for (int i = 0; i < jsonArray.length(); i++) {
      JSONObject phoneNumbersFormSingleRow = jsonArray.getJSONObject(i);
      for (String phoneNumberColumnName : phoneNumbersFormSingleRow.keySet()) {
        result.add(phoneNumbersFormSingleRow.getString(phoneNumberColumnName));
      }
    }
    return result;
  }

}
