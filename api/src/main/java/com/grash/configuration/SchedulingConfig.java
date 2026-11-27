package com.grash.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} methods (SLA status refresh, contract invoicing). Preventive maintenance jobs keep
 * using Quartz.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
