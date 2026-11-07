package com.auxiruta.fleet.config;

import com.auxiruta.commons.time.UtcClock;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {
    @Bean
    Clock clock() {
        return UtcClock.system();
    }
}
