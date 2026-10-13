package com.powerdialer.service;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.exceptions.CampaignIllegalStateException;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import com.powerdialer.util.EnCipherUtils;

/**
 * Service for checking data types in result table of campaign
 */
@Service
public class CampaignDataTypeChecker {

  private static final Log LOG = LogFactory.getLog(CampaignDataTypeChecker.class);

  @Autowired
  private CampaignDbOperations campaignDbOperations;

  @Autowired
  private EnCipherUtils enChiperUtils;

  /**
   * Check campaign's result table column types
   * @param campaign
   * @return true if check was successful
   * @throws CampaignIllegalStateException if something went wrong either
   *           connection problem or check failed
   */
  public boolean checkColumnsTypes(Campaign campaign) throws CampaignIllegalStateException {
    try {
      String[][] sourceTableColumnsWithTypes = campaignDbOperations
          .getTableColumnsWithTypes(campaign.getDbListTable(), campaign.getId(), campaign.getDbConnectionString(), campaign.getDbUsername(), enChiperUtils.decryptPassword(campaign.getDbPassword()));
      String[][] resultTableColumnsWithTypes = campaignDbOperations
          .getTableColumnsWithTypes(campaign.getDbResultTable(), campaign.getId(), campaign.getDbConnectionString(), campaign.getDbUsername(), enChiperUtils.decryptPassword(campaign.getDbPassword()));

      Map<String, Constants.DB_FIELD_TYPES> columnsToTypes = new HashMap<>();
      Map<String, Constants.DB_FIELD_TYPES> resultColumnsToTypes = new HashMap<>();
      Stream.of(sourceTableColumnsWithTypes).forEach(columnToType -> addColumnType(columnsToTypes, columnToType));
      Stream.of(resultTableColumnsWithTypes).forEach(columnToType -> {
        addColumnType(columnsToTypes, columnToType);
        addColumnType(resultColumnsToTypes, columnToType);
      });
      applyDefaultHistoryColumnMappings(campaign, resultColumnsToTypes);

      return checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.FETCHED_DATA_FLAG, campaign.getColResultFetchedDataFlag())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_DATE, campaign.getColResultRetryDate())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_NUMBER, campaign.getColResultRetryNumber())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_HISTORY, campaign.getColResultRetryHistory())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CB_HISTORY, campaign.getColResultCbHistory())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_PROCESS, campaign.getColResultCallProcess())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_DATE, campaign.getColResultCallbackDate())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_NUMBER, campaign.getColResultCallbackNumber())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_ID, campaign.getColResultUserId())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RC_SEQUENCE, campaign.getColResultRcSequence())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_RC, campaign.getColResultUserRc())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.TELEPHONY_RC, campaign.getColResultTelephonyRc())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLED_NUMBER, campaign.getColResultCalledNumber())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.COMPLETE_DATE, campaign.getColResultCompleteDate())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.ANSWER_DATE, campaign.getColResultAnswerDate())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.START_DATE, campaign.getColResultStartDate())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.UNIQUE_KEY, campaign.getColUniqueKey())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.TIMEZONE_OFFSET, campaign.getColTimezoneOffset())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_FROM_TIME, campaign.getColCallFromTime())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_TILL_TIME, campaign.getColCallToTime())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.DNC_NUMBERS, campaign.getColResultDNCNumbers())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.ATTEMPTS, campaign.getColResultAttempts())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_ATTEMPTS, campaign.getColResultCallbackAttempts())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.UPDATE_DATE, campaign.getColResultUpdateDate())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON, campaign.getColResultCloseReason())
          && checkColumn(columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RESPONSE_VALUES, campaign.getColResultResponseValues());
    } catch (SQLException e) {
      LOG.error("Can't check column types for " + campaign, e);
      throw new CampaignIllegalStateException("campaign.start.dbConnectionProblem");
    }
  }

  /**
   * Check that the unique key column (id) has an index on the campaign list table.
   * @param campaign the campaign to check
   * @throws CampaignIllegalStateException if index is missing
   */
  public void checkUniqueKeyIndex(Campaign campaign) throws CampaignIllegalStateException {
    String columnName = campaign.getColUniqueKey();
    if (!StringUtils.hasText(columnName)) {
      return;
    }
    try {
      boolean hasIndex = campaignDbOperations.hasIndexOnColumn(
          campaign.getDbListTable(),
          columnName,
          campaign.getId(),
          campaign.getDbConnectionString(),
          campaign.getDbUsername(),
          enChiperUtils.decryptPassword(campaign.getDbPassword()));
      if (!hasIndex) {
        LOG.warn("No index found on column '" + columnName + "' in table " + campaign.getDbListTable() + " for " + campaign);
        throw new CampaignIllegalStateException("campaign.start.missing.index", columnName);
      }
    } catch (SQLException e) {
      LOG.error("Can't check index for column '" + columnName + "' on " + campaign, e);
      throw new CampaignIllegalStateException("campaign.start.dbConnectionProblem");
    }
  }

  /**
   * Check type of single column (for result table)
   * @param columnsToTypes
   * @param column
   * @param columnName
   * @return true if check was successful
   */
  private boolean checkColumn(Map<String, Constants.DB_FIELD_TYPES> columnsToTypes, Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES column, String columnName) {
    if (!StringUtils.hasText(columnName))
      return true;

    Set<Constants.DB_FIELD_TYPES> allowedColumnTypes = Constants.RESULT_FIELDS_ALLOWED_TYPES.get(column);
    if (!CollectionUtils.isEmpty(allowedColumnTypes)) {
      if (allowedColumnTypes.contains(columnsToTypes.get(columnName)))
        return true;
      else
        throw new CampaignIllegalStateException("campaign.start.wrong.types", columnName);
    } else {
      LOG.warn("There is no value in RESULT_FIELDS_ALLOWED_TYPES map for '" + column + "' key");
      throw new CampaignIllegalStateException("campaign.start.wrong.types", columnName);
    }
  }

  private void addColumnType(Map<String, Constants.DB_FIELD_TYPES> columnsToTypes, String[] columnToType) {
    try {
      columnsToTypes.put(columnToType[0], Constants.DB_FIELD_TYPES.parse(columnToType[1]));
    } catch (ParseException e) {
      LOG.error("", e);
    }
  }

  private void applyDefaultHistoryColumnMappings(Campaign campaign, Map<String, Constants.DB_FIELD_TYPES> columnsToTypes) {
    applyDefaultColumnMapping(campaign.getColResultCbHistory(),
        Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CB_HISTORY,
        campaign::setColResultCbHistory,
        columnsToTypes);
    applyDefaultColumnMapping(campaign.getColResultCallProcess(),
        Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_PROCESS,
        campaign::setColResultCallProcess,
        columnsToTypes);
    // Task 325559: close_reason kolonu result tablosunda varsa binding elle seçilmek zorunda
    // kalmadan atanır. Kolon yoksa alan boş kalır ve kapanış nedeni yazılmaz; özellik çalışmaya
    // devam eder.
    applyDefaultColumnMapping(campaign.getColResultCloseReason(),
        Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON,
        campaign::setColResultCloseReason,
        columnsToTypes);
  }

  /**
   * Varsayılan adlı kolon result tablosunda MEVCUT ve TİPİ UYGUNSA binding'i kendiliğinden atar.
   *
   * <p><b>Tip kontrolü zorunludur (Faz 7 / P1.1).</b> Yalnız kolonun varlığına bakılırsa,
   * müşterinin tablosunda aynı adı taşıyan ama farklı tipte (ör. {@code close_reason INT})
   * bir kolon bulunduğunda binding atanır ve hemen ardından çalışan {@link #checkColumn}
   * {@code campaign.start.wrong.types} fırlatır: operatör hiçbir seçim yapmadığı hâlde o
   * kampanya HİÇ BAŞLAMAZ. {@code close_reason} müşteri şemalarında olası bir addır, dolayısıyla
   * bu tesadüf gerçekçidir.</p>
   *
   * <p>Uygun tip yoksa binding BOŞ bırakılır: kampanya normal başlar, yalnız o kolona yazım
   * yapılmaz (kapanış nedeni kaydedilmez). Operatörün ELLE seçtiği binding'in doğrulaması
   * zayıflatılmaz — {@link #checkColumn} aynen çalışmaya devam eder, çünkü orada sessiz yok
   * sayma operatörü yanıltırdı.</p>
   *
   * <p>Guard {@code cb_history} ve {@code call_process} için de geçerlidir; bugün de yanlış
   * tipli böyle bir kolon aynı şekilde kampanyayı başlatılamaz hâle getirebiliyordu.</p>
   */
  private void applyDefaultColumnMapping(String configuredColumnName,
                                         Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES defaultColumn,
                                         Consumer<String> setter,
                                         Map<String, Constants.DB_FIELD_TYPES> columnsToTypes) {
    String defaultColumnName = defaultColumn.getValue();
    if (StringUtils.hasText(configuredColumnName) || !columnsToTypes.containsKey(defaultColumnName)) {
      return;
    }
    if (!isTypeAllowedForDefaultColumn(defaultColumn, columnsToTypes.get(defaultColumnName))) {
      LOG.warn("Skipping automatic binding of '" + defaultColumnName + "': column type "
          + columnsToTypes.get(defaultColumnName) + " is not allowed for " + defaultColumn);
      return;
    }
    setter.accept(defaultColumnName);
  }

  private boolean isTypeAllowedForDefaultColumn(Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES defaultColumn,
                                                Constants.DB_FIELD_TYPES actualType) {
    Set<Constants.DB_FIELD_TYPES> allowedColumnTypes = Constants.RESULT_FIELDS_ALLOWED_TYPES.get(defaultColumn);
    return !CollectionUtils.isEmpty(allowedColumnTypes) && allowedColumnTypes.contains(actualType);
  }

}
