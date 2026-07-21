package com.realdev.readle.domain.content.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.realdev.readle.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class ContentValidationConfigTest {

  @Test
  @DisplayName("safewords.data 파일이 존재하지 않으면 CustomException이 발생한다")
  void safeWords_fileNotFound_throwsException() {
    // given
    ResourceLoader resourceLoader = mock(ResourceLoader.class);
    ContentValidationProperties properties = mock(ContentValidationProperties.class);

    when(properties.safewordsPath()).thenReturn("classpath:invalid-path.data");

    Resource resource = mock(Resource.class);
    when(resource.exists()).thenReturn(false);
    when(resourceLoader.getResource(anyString())).thenReturn(resource);

    ContentValidationConfig config = new ContentValidationConfig(resourceLoader, properties);

    // when & then
    assertThatThrownBy(() -> config.safeWords())
        .isInstanceOf(CustomException.class)
        .hasMessageContaining("safewords.data 파일을 찾을 수 없습니다");
  }
}
