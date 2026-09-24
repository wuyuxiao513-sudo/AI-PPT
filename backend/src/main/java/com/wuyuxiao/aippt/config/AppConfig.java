package com.wuyuxiao.aippt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.concurrent.Executor;

@Configuration
public class AppConfig {
    @Bean("slideExecutor")
    Executor slideExecutor(@Value("${app.generation.parallelism:4}") int size) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(size); executor.setMaxPoolSize(size); executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("slide-agent-"); executor.initialize(); return executor;
    }
    @Bean
    WebMvcConfigurer cors() {
        return new WebMvcConfigurer() {
            @Override public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**").allowedOrigins("http://localhost:5173").allowedMethods("*");
            }
        };
    }
}

