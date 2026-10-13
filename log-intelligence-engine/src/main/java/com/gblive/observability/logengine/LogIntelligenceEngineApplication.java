package com.gblive.observability.logengine;

import com.gblive.observability.logengine.burst.FailureBurstProperties;
import com.gblive.observability.logengine.config.AiProperties;
import com.gblive.observability.logengine.llm.config.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        AiProperties.class,
        FailureBurstProperties.class,
        LlmProperties.class
})
public class LogIntelligenceEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogIntelligenceEngineApplication.class, args);
    }
}
