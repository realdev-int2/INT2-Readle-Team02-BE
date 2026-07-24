package com.realdev.readle.domain.tag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.tag.entity.ContentTag;
import com.realdev.readle.domain.tag.entity.Tag;
import com.realdev.readle.domain.tag.exception.TagErrorCode;
import com.realdev.readle.domain.tag.repository.ContentTagRepository;
import com.realdev.readle.domain.tag.repository.TagRepository;
import com.realdev.readle.global.exception.CustomException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

  @InjectMocks private TagService tagService;

  @Mock private TagRepository tagRepository;
  @Mock private ContentTagRepository contentTagRepository;

  @Test
  @DisplayName("새로운 태그를 생성할 때 trim 및 소문자 정규화를 거쳐 저장한다")
  void findOrCreateTag_CreateNew() {
    // Given
    String rawName = "  Spring Boot  ";
    String expectedNormalizedName = "spring boot";
    given(tagRepository.findByName(expectedNormalizedName)).willReturn(Optional.empty());
    given(tagRepository.save(any(Tag.class))).willAnswer(invocation -> invocation.getArgument(0));

    // When
    Tag result = tagService.findOrCreateTag(rawName);

    // Then
    assertThat(result.getName()).isEqualTo(expectedNormalizedName);
    verify(tagRepository).save(any(Tag.class));
  }

  @Test
  @DisplayName("이미 존재하는 태그라면 새로 저장하지 않고 조회된 태그를 반환한다")
  void findOrCreateTag_ReturnExisting() {
    // Given
    String rawName = "Java";
    String expectedNormalizedName = "java";
    Tag existingTag = Tag.create(expectedNormalizedName);
    given(tagRepository.findByName(expectedNormalizedName)).willReturn(Optional.of(existingTag));

    // When
    Tag result = tagService.findOrCreateTag(rawName);

    // Then
    assertThat(result).isSameAs(existingTag);
    verify(tagRepository, never()).save(any(Tag.class));
  }

  @Test
  @DisplayName("태그명이 null이거나 비어 있으면 예외가 발생한다")
  void findOrCreateTag_InvalidInput() {
    assertThatThrownBy(() -> tagService.findOrCreateTag(null))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", TagErrorCode.INVALID_TAG_NAME);

    assertThatThrownBy(() -> tagService.findOrCreateTag("   "))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", TagErrorCode.INVALID_TAG_NAME);
  }

  @Test
  @DisplayName("중복 태그를 포함한 1~3개의 태그를 성공적으로 콘텐츠에 매핑하여 저장한다")
  void saveContentTags_Success() {
    // Given
    Content content = mock(Content.class);
    List<String> rawTagNames = List.of("Spring", "spring", "Java", "Kotlin");
    // 중복 제거 및 소문자화 후: "spring", "java", "kotlin" (딱 3개)

    given(tagRepository.findByName("spring")).willReturn(Optional.empty());
    given(tagRepository.findByName("java")).willReturn(Optional.empty());
    given(tagRepository.findByName("kotlin")).willReturn(Optional.empty());
    given(tagRepository.save(any(Tag.class))).willAnswer(invocation -> invocation.getArgument(0));

    // When
    tagService.saveContentTags(content, rawTagNames);

    // Then
    verify(tagRepository, times(3)).save(any(Tag.class));
    verify(contentTagRepository, times(3)).save(any(ContentTag.class));
  }

  @Test
  @DisplayName("태그 개수가 3개를 초과하는 경우 예외가 발생한다")
  void saveContentTags_TooManyTags() {
    // Given
    Content content = mock(Content.class);
    List<String> rawTagNames = List.of("tag1", "tag2", "tag3", "tag4");

    // When & Then
    assertThatThrownBy(() -> tagService.saveContentTags(content, rawTagNames))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", TagErrorCode.INVALID_TAG_COUNT);
  }

  @Test
  @DisplayName("태그 개수가 0개인 경우 예외가 발생한다")
  void saveContentTags_EmptyTags() {
    // Given
    Content content = mock(Content.class);
    List<String> rawTagNames = List.of();

    // When & Then
    assertThatThrownBy(() -> tagService.saveContentTags(content, rawTagNames))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", TagErrorCode.INVALID_TAG_COUNT);
  }
}
