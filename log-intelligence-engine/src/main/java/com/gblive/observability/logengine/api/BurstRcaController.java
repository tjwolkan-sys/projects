package com.gblive.observability.logengine.api;

import com.gblive.observability.logengine.burst.AiAssistedBurstRcaService;
import com.gblive.observability.logengine.burst.BurstContext;
import com.gblive.observability.logengine.burst.BurstContextFactory;
import com.gblive.observability.logengine.burst.BurstRcaResult;
import com.gblive.observability.logengine.model.BurstRcaRequest;
import com.gblive.observability.logengine.model.BurstRcaResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/rca/bursts")
public class BurstRcaController {

    private final BurstContextFactory contextFactory;
    private final AiAssistedBurstRcaService burstRcaService;

    public BurstRcaController(
            BurstContextFactory contextFactory,
            AiAssistedBurstRcaService burstRcaService
    ) {
        this.contextFactory = contextFactory;
        this.burstRcaService = burstRcaService;
    }

    @PostMapping("/analyze")
    public Mono<BurstRcaResponse> analyze(@RequestBody BurstRcaRequest request) {

        BurstContext context = contextFactory.build(
                request.windowStart(),
                request.windowEnd(),
                request.callIds()
        );

        return burstRcaService.analyze(context)
                .map(this::toResponse);
    }

    private BurstRcaResponse toResponse(BurstRcaResult r) {
        return new BurstRcaResponse(
                r.windowStart(),
                r.windowEnd(),
                r.rootCause(),
                r.description(),
                r.confidence(),
                r.affectedCallIds()
        );
    }
}
