package com.realdev.readle.domain.content.config;

import com.vane.badwordfiltering.BadWordFiltering;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ContentValidationProperties.class)
public class ContentValidationConfig {

  private final ResourceLoader resourceLoader;
  private final ContentValidationProperties properties;

  @Bean
  public BadWordFiltering badWordFiltering() {
    BadWordFiltering badWordFiltering = new BadWordFiltering();

    try {
      Resource resource = resourceLoader.getResource(properties.badwordsKoResourcePath());
      if (resource.exists()) {
        try (BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
          String line;
          int count = 0;
          while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) {
              badWordFiltering.add(line);
              count++;
            }
          }
          log.info("[VALIDATION_CONFIG] badwords.data 사전 적재 완료 (총 {}개 단어)", count);
        }
      } else {
        log.warn(
            "[VALIDATION_CONFIG] badwords.data 파일을 찾을 수 없습니다. 경로: {}",
            properties.badwordsKoResourcePath());
      }
    } catch (Exception e) {
      log.error("[VALIDATION_CONFIG] badwords.data 사전 적재 중 예외 발생. 기본 필터만 적용합니다.", e);
    }
    return badWordFiltering;
  }
}
