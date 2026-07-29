package com.realdev.readle.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.ReadleApplication;
import java.util.LinkedHashMap;
import java.util.Map;
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
      "management.endpoint.health.probes.enabled=true",
      "springdoc.show-actuator=true"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {

  private static final String BEARER_AUTH = OpenApiConfig.BEARER_AUTH_SCHEME;

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void documentsJwtProtectedApiBoundary() throws Exception {
    JsonNode apiDocs =
        objectMapper.readTree(
            mockMvc
                .perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    JsonNode bearerScheme = apiDocs.at("/components/securitySchemes/" + BEARER_AUTH);
    assertThat(bearerScheme.path("type").asText()).isEqualTo("http");
    assertThat(bearerScheme.path("scheme").asText()).isEqualTo("bearer");
    assertThat(bearerScheme.path("bearerFormat").asText()).isEqualTo("JWT");
    assertThat(apiDocs.path("security").isMissingNode() || apiDocs.path("security").isEmpty())
        .isTrue();

    protectedOperations()
        .forEach(
            (route, method) -> {
              JsonNode operation = operation(apiDocs, route, method);
              assertBearerRequired(operation);
              assertErrorResponseSchema(operation, "401");
            });
    assertErrorResponseSchema(operation(apiDocs, "/api/auth/logout", "post"), "403");

    publicOperations()
        .forEach((route, method) -> assertBearerNotRequired(operation(apiDocs, route, method)));
    optionalPublicOperations()
        .forEach(
            (route, method) -> {
              if (apiDocs.path("paths").has(route)) {
                assertBearerNotRequired(operation(apiDocs, route, method));
              }
            });
  }

  private static Map<String, String> protectedOperations() {
    Map<String, String> operations = new LinkedHashMap<>();
    operations.put("/api/auth/logout", "post");
    operations.put("/api/users/me", "get");
    operations.put("/api/contents/extract", "post");
    operations.put("/api/contents", "post");
    operations.put("/api/contents/{contentId}/validation", "get");
    operations.put("/api/contents/{contentId}/validation/retry", "post");
    operations.put("/api/dashboard", "get");
    operations.put("/api/quizzes", "post");
    operations.put("/api/quizzes/{quizSetId}/attempts", "post");
    operations.put("/api/quizzes/attempts/{attemptId}", "get");
    operations.put("/api/quizzes/attempts/{attemptId}/submit", "post");
    operations.put("/api/quizzes/attempts/{attemptId}/result", "get");
    operations.put("/api/result-reports", "get");
    operations.put("/api/result-reports/{reportId}", "get");
    return operations;
  }

  private static Map<String, String> publicOperations() {
    Map<String, String> operations = new LinkedHashMap<>();
    operations.put("/api/auth/{provider}/start", "get");
    operations.put("/api/auth/{provider}/callback", "get");
    operations.put("/api/auth/refresh", "post");
    operations.put("/api/auth/session", "get");
    operations.put("/api/actuator/health", "get");
    return operations;
  }

  private static Map<String, String> optionalPublicOperations() {
    Map<String, String> operations = new LinkedHashMap<>();
    operations.put("/api/actuator/prometheus", "get");
    return operations;
  }

  private static JsonNode operation(JsonNode apiDocs, String route, String method) {
    JsonNode operation = apiDocs.path("paths").path(route).path(method);
    assertThat(operation.isMissingNode())
        .as("OpenAPI operation %s %s should exist", method.toUpperCase(), route)
        .isFalse();
    return operation;
  }

  private static void assertBearerRequired(JsonNode operation) {
    JsonNode security = operation.path("security");
    assertThat(security.isArray()).isTrue();
    assertThat(security).hasSize(1);
    assertThat(security.get(0).has(BEARER_AUTH)).isTrue();
  }

  private static void assertBearerNotRequired(JsonNode operation) {
    JsonNode security = operation.path("security");
    assertThat(security.isMissingNode() || security.isEmpty()).isTrue();
  }

  private static void assertErrorResponseSchema(JsonNode operation, String status) {
    JsonNode schema = operation.at("/responses/" + status + "/content/application~1json/schema");
    assertThat(schema.path("$ref").asText()).endsWith("/ErrorResponse");
  }
}
