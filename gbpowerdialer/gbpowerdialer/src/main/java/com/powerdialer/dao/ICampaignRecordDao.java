package com.powerdialer.dao;

import com.powerdialer.model.Campaign;

import java.sql.SQLException;
import java.util.List;

/**
 * DAO contract for executing parameterized DML on campaign-related tables.
 * Extends the generic DAO contract for consistency across the project.
 */
public interface ICampaignRecordDao extends IGenericDao<Campaign> {

    /**
     * Executes a parameterized update statement against the campaign's target database and
     * returns the affected row count from {@link java.sql.PreparedStatement#executeUpdate()}.
     *
     * @param campaign Campaign entity carrying connection metadata
     * @param sql      SQL string with {@code ?} placeholders
     * @param params   Parameter values in positional order; may be {@code null} or empty
     * @return affected row count
     * @throws SQLException if a database error occurs
     */
    int executeUpdate(Campaign campaign, String sql, List<Object> params) throws SQLException;
}
