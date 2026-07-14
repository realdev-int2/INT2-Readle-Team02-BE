package com.realdev.readle.global.infrastructure.prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class PromptLoader {

  private final ResourceLoader resourceLoader;

  public PromptLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  public String loadPrompt(String promptFileName, Map<String, String> variables) {
    String template = readTemplate(promptFileName);

    for (Map.Entry<String, String> entry : variables.entrySet()) {
      String placeholder = "${" + entry.getKey() + "}";
      String value = entry.getValue() == null ? "" : entry.getValue();
      template = template.replace(placeholder, value);
    }

    return template;
  }

  private String readTemplate(String promptFileName) {
    String resourcePath = "classpath:prompts/" + promptFileName;
    Resource resource = resourceLoader.getResource(resourcePath);

    if (!resource.exists()) {
      throw new IllegalArgumentException("프롬프트 템플릿 파일을 찾을 수 없습니다: " + resourcePath);
    }

    try {
      return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("프롬프트 파일을 읽는 중 오류가 발생했습니다: " + resourcePath, e);
    }
  }
}
