package com.realdev.readle.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PrometheusMetricsPropertiesTest {

  @Test
  void disabledMonitoringUsesNonAuthenticatingPassword() {
    PrometheusMetricsProperties properties = new PrometheusMetricsProperties(false, null, "");

    assertThat(properties.enabled()).isFalse();
    assertThat(properties.username()).isEqualTo("readle-monitor");
    assertThat(properties.password()).startsWith("__disabled_prometheus_metrics__");
  }

  @Test
  void enabledMonitoringRequiresNonblankPassword() {
    assertThatThrownBy(() -> new PrometheusMetricsProperties(true, "readle-monitor", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("root-password");
  }

  @Test
  void enabledMonitoringUsesConfiguredPassword() {
    PrometheusMetricsProperties properties =
        new PrometheusMetricsProperties(true, "readle-monitor", "test-prometheus-root-password");

    assertThat(properties.username()).isEqualTo("readle-monitor");
    assertThat(properties.password()).isEqualTo("test-prometheus-root-password");
  }

  @Test
  void rejectsNonCanonicalUsername() {
    assertThatThrownBy(
            () ->
                new PrometheusMetricsProperties(
                    true, "custom-monitor", "test-prometheus-root-password"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("readle-monitor");
  }
}
