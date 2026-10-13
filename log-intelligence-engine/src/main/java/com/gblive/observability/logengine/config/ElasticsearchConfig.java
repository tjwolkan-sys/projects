package com.gblive.observability.logengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ElasticsearchConfig {

    @Bean
    public WebClient elasticsearchWebClient() {
        return WebClient.builder()
                .baseUrl("http://ELK_HOST:9200")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
