package com.gblive.observability.logengine.api;

import com.gblive.observability.logengine.aggregation.CallTimeline;
import com.gblive.observability.logengine.ingestion.ReactiveLogSearchService;
import com.gblive.observability.logengine.model.RawLogEvent;
import com.gblive.observability.logengine.parsing.ExtractedEvent;
import com.gblive.observability.logengine.rca.RcaResult;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/logs")
public class LogSearchController {

    private final ReactiveLogSearchService logSearchService;

    public LogSearchController(ReactiveLogSearchService logSearchService) {
        this.logSearchService = logSearchService;
    }

    @GetMapping("/search")
    public Flux<ExtractedEvent> search(
            @RequestParam String index,
            @RequestParam String app,
            @RequestParam(defaultValue = "now-15m") String since
    ) {
        return logSearchService.searchLogs(index, app, since);
    }

    @GetMapping("/timeline")
    public Flux<CallTimeline> timeline(
            @RequestParam String index,
            @RequestParam String app,
            @RequestParam(defaultValue = "now-15m") String since
    ) {
        return logSearchService.searchAndAggregateByCall(index, app, since);
    }

    @GetMapping("/rca")
    public Flux<RcaResult> rca(
            @RequestParam String index,
            @RequestParam String app,
            @RequestParam(defaultValue = "now-15m") String since
    ) {
        return logSearchService.analyzeRca(index, app, since);
    }

    @GetMapping("/rca/ai")
    public Flux<RcaResult> aiRca(
            @RequestParam String index,
            @RequestParam String app,
            @RequestParam(defaultValue = "now-15m") String since
    ) {
        return logSearchService.analyzeRcaWithAi(index, app, since);
    }

}
