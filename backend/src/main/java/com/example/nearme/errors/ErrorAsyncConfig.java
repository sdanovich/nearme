package com.example.nearme.errors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated small thread pool for fire-and-forget error publishing. Bounded with
 * a CallerRunsPolicy-free DiscardPolicy: if the queue is full (broker is slow and
 * errors are flooding), we drop the publish rather than block the app or pile up
 * memory. The event is still in the local log, so dropping a broker publish under
 * extreme load is acceptable — availability of the app comes first.
 */
@Configuration
@EnableAsync
public class ErrorAsyncConfig {

    @Bean(name = "errorPublisherExecutor")
    public Executor errorPublisherExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(2);
        ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("err-pub-");
        // Under flood, discard the oldest queued publish rather than block callers.
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        ex.initialize();
        return ex;
    }
}
