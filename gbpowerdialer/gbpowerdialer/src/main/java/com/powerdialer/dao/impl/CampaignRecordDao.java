package com.powerdialer.dao.impl;

import com.powerdialer.dao.ICampaignRecordDao;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.EnCipherUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * JDBC-based DAO for executing parameterized DML on campaign-related tables.
 * Extends GenericDao<Campaign> to align with the overall DAO design.
 */
@Repository
public class CampaignRecordDao extends GenericDao<Campaign> implements ICampaignRecordDao {

    @Autowired
    private EnCipherUtils enCipherUtils;

    public CampaignRecordDao() {
        super(Campaign.class);
    }

    @Override
    public int executeUpdate(Campaign campaign, String sql, List<Object> params) throws SQLException {
        List<Object> safeParams = (params == null) ? Collections.emptyList() : params;

        try (Connection con = DriverManager.getConnection(
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
             PreparedStatement ps = con.prepareStatement(sql)) {

            for (int i = 0; i < safeParams.size(); i++) {
                ps.setObject(i + 1, safeParams.get(i));
            }
            return ps.executeUpdate();
        }
    }
}
