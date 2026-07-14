package com.realdev.readle.domain.content.service;

import com.realdev.readle.domain.content.dto.request.ContentCreateRequest;
import com.realdev.readle.domain.content.dto.request.ContentExtractRequest;
import com.realdev.readle.domain.content.dto.response.ContentCreateResponse;
import com.realdev.readle.domain.content.dto.response.ContentExtractResponse;
import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.content.entity.InputType;
import com.realdev.readle.domain.content.entity.ValidationStatus;
import com.realdev.readle.domain.content.exception.ContentErrorCode;
import com.realdev.readle.domain.content.repository.ContentRepository;
import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.exception.MemberErrorCode;
import com.realdev.readle.domain.member.repository.MemberRepository;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.util.crawler.WebCrawler;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentService {

  private static final int MAX_TEXT_LENGTH = 15_000;
  private static final int TITLE_FALLBACK_LENGTH = 30;

  private final WebCrawler webCrawler;
  private final ContentRepository contentRepository;
  private final MemberRepository memberRepository;

  public ContentExtractResponse extract(ContentExtractRequest request) {
    WebCrawler.CrawledDocument crawledDocument = webCrawler.crawl(request.url());
    return new ContentExtractResponse(crawledDocument.title(), crawledDocument.content());
  }

  @Transactional
  public ContentCreateResponse createContent(ContentCreateRequest request, UUID memberUuid) {
    // 1. 미인증 검증
    if (memberUuid == null) {
      throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
    }

    // 2. Fast Fail: 텍스트 길이 제한
    if (request.inputType() == InputType.TEXT
        && request.text() != null
        && request.text().length() > MAX_TEXT_LENGTH) {
      throw new CustomException(ContentErrorCode.CONTENT_TOO_LARGE);
    }

    // 3. 인증된 회원 조회
    Member member =
        memberRepository
            .findByUuid(memberUuid.toString())
            .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

    // 4. inputType 분기 및 저장
    Content content = buildContent(request, member);
    Content saved = contentRepository.save(content);

    return new ContentCreateResponse(saved.getId(), ValidationStatus.PENDING);
  }

  private Content buildContent(ContentCreateRequest request, Member member) {
    if (request.inputType() == InputType.TEXT) {
      String title = resolveTitle(request.title(), request.text());
      return Content.fromText(member, title, request.text());
    }

    return Content.fromUrl(member, request.title(), request.url(), request.extractedText());
  }

  private String resolveTitle(String title, String text) {
    if (title != null && !title.isBlank()) {
      return title;
    }
    if (text == null || text.isBlank()) {
      return "";
    }
    return text.substring(0, Math.min(TITLE_FALLBACK_LENGTH, text.length()));
  }
}
