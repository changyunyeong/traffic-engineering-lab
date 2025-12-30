package com.ticketing.global.snowflake;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SnowflakeConfig {

    @Bean
    public Snowflake snowflake() {
        return new Snowflake();
    }
}
