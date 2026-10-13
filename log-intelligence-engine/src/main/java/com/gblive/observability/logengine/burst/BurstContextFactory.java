package com.gblive.observability.logengine.burst;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class BurstContextFactory {

    private final FailureSampleRepository failureSampleRepository;

    public BurstContextFactory(
            FailureSampleRepository failureSampleRepository
    ) {
        this.failureSampleRepository = failureSampleRepository;
    }

    public BurstContext build(
            Instant start,
            Instant end,
            List<String> callIds
    ) {

        List<FailureSample> samples =
                failureSampleRepository.findFailures(start, end, callIds);

        FailureBurst burst =
                FailureBurst.fromSamples(start, end, samples);

        return new BurstContext(burst, samples);
    }
}
