package com.pokecollect.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled only for the sync role, so web/consumer instances don't run the job. */
@Configuration
@Profile("sync")
@EnableScheduling
public class SchedulingConfig {
}
