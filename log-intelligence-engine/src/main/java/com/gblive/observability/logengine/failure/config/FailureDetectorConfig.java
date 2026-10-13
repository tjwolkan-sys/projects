package com.gblive.observability.logengine.failure.config;

import com.gblive.observability.logengine.failure.model.BurstSeverity;
import com.gblive.observability.logengine.failure.model.RootCauseType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "failure-detector")
public class FailureDetectorConfig {

    private List<WindowConfig> windows;
    private Map<RootCauseType, BaselineConfig> baselines;

    public List<WindowConfig> getWindows() {
        return windows;
    }

    public void setWindows(List<WindowConfig> windows) {
        this.windows = windows;
    }

    public Map<RootCauseType, BaselineConfig> getBaselines() {
        return baselines;
    }

    public void setBaselines(Map<RootCauseType, BaselineConfig> baselines) {
        this.baselines = baselines;
    }

    public static class WindowConfig {
        private Duration duration;

        public Duration getDuration() {
            return duration;
        }

        public void setDuration(Duration duration) {
            this.duration = duration;
        }
    }

    public static class BaselineConfig {
        private int normalRatePerMinute;
        private int burstThreshold;
        private BurstSeverity severity;

        public int getNormalRatePerMinute() {
            return normalRatePerMinute;
        }

        public void setNormalRatePerMinute(int normalRatePerMinute) {
            this.normalRatePerMinute = normalRatePerMinute;
        }

        public int getBurstThreshold() {
            return burstThreshold;
        }

        public void setBurstThreshold(int burstThreshold) {
            this.burstThreshold = burstThreshold;
        }

        public BurstSeverity getSeverity() {
            return severity;
        }

        public void setSeverity(BurstSeverity severity) {
            this.severity = severity;
        }
    }

    // getter / setter
}
