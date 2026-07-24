package com.realdev.readle.domain.content.config;

import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.vane.badwordfiltering.BadWordFiltering;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
        throw new CustomException(
            GlobalErrorCode.SERVER_ERROR,
            "[VALIDATION_CONFIG] badwords.data 파일을 찾을 수 없습니다. 경로: "
                + properties.badwordsKoResourcePath()
                + ".");
      }

    } catch (IOException e) {
      log.error("[VALIDATION_CONFIG] badwords.data 사전 적재 중 예외 발생.", e);
      throw new CustomException(
          GlobalErrorCode.SERVER_ERROR, "[VALIDATION_CONFIG] badwords.data 사전 적재 중 오류가 발생했습니다.", e);
    }
    return badWordFiltering;
  }

  @Bean(name = "safeWords")
  public List<String> safeWords() {
    List<String> safeWords = new ArrayList<>();
    try {
      Resource resource = resourceLoader.getResource(properties.safewordsPath());
      if (resource.exists()) {
        try (BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
          String line;
          int count = 0;
          while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) {
              safeWords.add(line);
              count++;
            }
          }
          log.info("[VALIDATION_CONFIG] safewords.data 사전 적재 완료 (총 {}개 단어)", count);
        }
      } else {
        throw new CustomException(
            GlobalErrorCode.SERVER_ERROR,
            "[VALIDATION_CONFIG] safewords.data 파일을 찾을 수 없습니다. 경로: "
                + properties.safewordsPath()
                + ".");
      }
    } catch (IOException e) {
      log.error("[VALIDATION_CONFIG] safewords.data 사전 적재 중 예외 발생.", e);
      throw new CustomException(
          GlobalErrorCode.SERVER_ERROR,
          "[VALIDATION_CONFIG] safewords.data 사전 적재 중 오류가 발생했습니다.",
          e);
    }
    return safeWords;
  }
}
