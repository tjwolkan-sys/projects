package com.powerdialer.config;

import com.powerdialer.util.StripedExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ExecutorConfig {

    // SORUN 4: eski "userEventExecutor" (4-8 thread, yonlendirmesiz ThreadPoolTaskExecutor) ayni
    // agent'in art arda statu mesajlarini ters sirada isleyebiliyordu (Available/Mola yarisi ->
    // bellekte bayat statu). Kullanan tek yer UserSubscriber oldugu icin bean serit modeline
    // tasindi: ayni agent ayni tek-thread seritte FIFO, farkli agent'lar paralel (8 serit = eski
    // maxPoolSize ile ayni kapasite).
    @Bean(name = "userEventStripedExecutor", destroyMethod = "shutdown")
    @Scope("singleton")
    public StripedExecutor userEventStripedExecutor() {
        return new StripedExecutor(8, "user-event-stripe-");
    }

    @Bean(name = "webphoneEventExecutor")
    @Scope("singleton")
    public ThreadPoolTaskExecutor webphoneEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(10000);
        executor.setThreadNamePrefix("webphone-event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}