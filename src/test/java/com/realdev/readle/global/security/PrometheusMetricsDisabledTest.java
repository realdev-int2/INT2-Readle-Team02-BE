package com.realdev.readle.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realdev.readle.ReadleApplication;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = ReadleApplication.class,
    properties = {
      "management.endpoints.web.base-path=/api/actuator",
      "management.endpoints.web.exposure.include=health,prometheus",
      "management.prometheus.metrics.export.enabled=true",
      "security.prometheus-metrics.enabled=false",
      "security.prometheus-metrics.root-password="
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrometheusMetricsDisabledTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void disabledMonitoringDoesNotAcceptRootMetricsCredentials() throws Exception {
    mockMvc
        .perform(
            get("/api/actuator/prometheus")
                .header(
                    "Authorization", basicAuth("readle-monitor", "test-prometheus-root-password")))
        .andExpect(status().isUnauthorized());
  }

  private String basicAuth(String username, String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
  }
}
