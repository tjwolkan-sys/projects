package com.powerdialer.engine.acd;

import com.powerdialer.model.User;

/**
 * Interface for campaign's finding user strategy .
 * {@link FindMatchingUserStrategy#findMatchingUser()} should return most
 * suitable agent for campaign
 * {@see UserFindingContext}
 */
@FunctionalInterface
public interface FindMatchingUserStrategy {

  User findMatchingUser();

}
