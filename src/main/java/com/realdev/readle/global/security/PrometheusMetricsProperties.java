package com.realdev.readle.global.security;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.prometheus-metrics")
public record PrometheusMetricsProperties(boolean enabled, String username, String rootPassword) {

  private static final String DEFAULT_USERNAME = "readle-monitor";
  private static final String DISABLED_PASSWORD =
      "__disabled_prometheus_metrics__" + UUID.randomUUID();

  public PrometheusMetricsProperties {
    username = username == null || username.isBlank() ? DEFAULT_USERNAME : username;
    rootPassword = rootPassword == null ? "" : rootPassword;
    if (!DEFAULT_USERNAME.equals(username)) {
      throw new IllegalArgumentException(
          "security.prometheus-metrics.username must be readle-monitor");
    }
    if (enabled && rootPassword.isBlank()) {
      throw new IllegalArgumentException(
          "security.prometheus-metrics.root-password must be nonblank when monitoring is enabled");
    }
  }

  String password() {
    return enabled ? rootPassword : DISABLED_PASSWORD;
  }
}
