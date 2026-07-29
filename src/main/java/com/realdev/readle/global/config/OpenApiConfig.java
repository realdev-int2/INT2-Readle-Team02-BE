package com.realdev.readle.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  public static final String BEARER_AUTH_SCHEME = "bearerAuth";
  public static final String BASIC_AUTH_SCHEME = "basicAuth";

  private static final String PROMETHEUS_PATH = "/api/actuator/prometheus";

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_AUTH_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSecuritySchemes(
                    BASIC_AUTH_SCHEME,
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")));
  }

  @Bean
  public OpenApiCustomizer prometheusBasicAuthOpenApiCustomizer() {
    return openApi -> {
      if (openApi.getPaths() == null) {
        return;
      }

      PathItem prometheusPath = openApi.getPaths().get(PROMETHEUS_PATH);
      if (prometheusPath != null && prometheusPath.getGet() != null) {
        prometheusPath
            .getGet()
            .setSecurity(List.of(new SecurityRequirement().addList(BASIC_AUTH_SCHEME)));
      }
    };
  }
}
