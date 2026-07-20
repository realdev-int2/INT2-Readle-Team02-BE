package com.realdev.readle.domain.tag.service;

import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.tag.entity.ContentTag;
import com.realdev.readle.domain.tag.entity.Tag;
import com.realdev.readle.domain.tag.repository.ContentTagRepository;
import com.realdev.readle.domain.tag.repository.TagRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

  private final TagRepository tagRepository;
  private final ContentTagRepository contentTagRepository;

  @Transactional
  public Tag findOrCreateTag(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("태그 이름은 비어있을 수 없습니다.");
    }
    String normalized = name.trim().toLowerCase();
    return tagRepository
        .findByName(normalized)
        .orElseGet(() -> tagRepository.save(Tag.create(normalized)));
  }

  @Transactional
  public void saveContentTags(Content content, List<String> tagNames) {
    if (content == null || tagNames == null) {
      return;
    }

    // 중복 제거 및 소문자 정규화 처리
    List<String> uniqueNormalizedNames =
        tagNames.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(s -> !s.isEmpty())
            .distinct()
            .toList();

    // 1개당 태그 1~3개 제한 검증
    if (uniqueNormalizedNames.isEmpty() || uniqueNormalizedNames.size() > 3) {
      throw new IllegalArgumentException("태그 개수는 1개 이상 3개 이하여야 합니다.");
    }

    for (String tagName : uniqueNormalizedNames) {
      Tag tag = findOrCreateTag(tagName);
      ContentTag contentTag = ContentTag.create(content, tag);
      contentTagRepository.save(contentTag);
    }
  }
}
