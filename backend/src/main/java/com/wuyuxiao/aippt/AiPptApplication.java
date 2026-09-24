package com.wuyuxiao.aippt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class AiPptApplication {
    public static void main(String[] args) { SpringApplication.run(AiPptApplication.class, args); }
}

