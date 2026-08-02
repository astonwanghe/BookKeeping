package com.pixledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String jwtSecret, String publicUrl, String mailFrom, String adminBootstrapKey) {
}
