package com.ticketing.global.config;

import com.ticketing.global.snowflake.Snowflake;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestDataConfig {

    @Bean
    public Snowflake snowflake() {
        return new Snowflake();
    }
}
