package com.powerdialer.engine.dialer.retry;

import static com.powerdialer.util.Constants.RETRY_STRATEGY.*;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class RetryStrategyFactoryTest {

	RetryStrategyFactory factory = new RetryStrategyFactory();

	@Test
	public void shouldCreateReachRetrySingleNumber() {
		assertTrue(ReachRetrySingleNumber.class.equals(factory.createStrategy(REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER).getClass()));
	}

	@Test
	public void shouldCreateReachRetryPerNumber() {
			assertTrue(ReachRetryPerNumber.class.equals(factory.createStrategy(REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER).getClass()));
	}

	@Test
	public void shouldCreateRoundRobinNextNumber() {
		assertTrue(RoundRobinNextNumber.class.equals(factory.createStrategy(ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER).getClass()));
	}

	@Test
	public void shouldCreateRoundRobinNextCycle() {
		assertTrue(RoundRobinNextCycle.class.equals(factory.createStrategy(ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_CYCLE).getClass()));
	}

	@Test
	public void shouldCreateRoundRobinPerCycle() {
		assertTrue(RoundRobinPerCycle.class.equals(factory.createStrategy(ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER).getClass()));
	}

	@Test
	public void shouldCreateRetryRulesCycle() {
		assertTrue(RetryRulesCycle.class.equals(factory.createStrategy(RETRY_RULES_MANAGES_CYCLE).getClass()));
	}

}
