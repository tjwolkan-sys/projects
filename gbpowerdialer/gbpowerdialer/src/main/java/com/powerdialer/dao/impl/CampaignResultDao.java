package com.powerdialer.dao.impl;

import com.powerdialer.dao.ICampaignResultDao;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.EnCipherUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Repository
public class CampaignResultDao implements ICampaignResultDao {
    private static final Logger LOG = LoggerFactory.getLogger(CampaignResultDao.class);

    private final EnCipherUtils enCipherUtils;

    @Autowired
    public CampaignResultDao(EnCipherUtils enCipherUtils) {
        this.enCipherUtils = enCipherUtils;
    }

    /**
     * Updates the user_result_code column for a specific call record in the campaign’s result table.
     *
     * @param cdr The call record containing campaign and contact info.
     * @param newResultCodeId The new result code ID to set (e.g. -2 for “forcefully stop”)
     */
    @Override
    public void updateUserResultCode(CDR cdr, long newResultCodeId) {
        Long callId = cdr.getContactId();
        Campaign campaign = cdr.getCampaign();
        String resultTable = campaign.getDbResultTable();

        String sql = "UPDATE " + resultTable + " SET user_result_code = ? WHERE id = ?";

        String jdbcUrl = campaign.getDbConnectionString();
        String username = campaign.getDbUsername();
        String password = enCipherUtils.decryptPassword(campaign.getDbPassword());

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, newResultCodeId);
            ps.setLong(2, callId);

            int affected = ps.executeUpdate();

            if (affected > 0) {
                LOG.info("Updated user_result_code={} for callId={} in campaign={}", newResultCodeId, callId, campaign.getName());
            } else {
                LOG.warn("No rows updated for callId={} in campaign={}", callId, campaign.getName());
            }
        } catch (SQLException e) {
            LOG.error("Failed to update user_result_code for callId={} in campaign={}", callId, campaign.getName(), e);
            throw new RuntimeException(e);
        }
    }
}
