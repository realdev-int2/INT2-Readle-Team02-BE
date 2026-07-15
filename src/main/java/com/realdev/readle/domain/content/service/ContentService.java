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
import java.net.MalformedURLException;
import java.net.URL;
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
    validateAuthentication(memberUuid);
    validateCreateRequest(request);

    // 1. 회원 조회
    Member member = memberRepository
        .findByUuid(memberUuid.toString())
        .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

    // 2. 저장
    Content content = buildContent(request, member);
    Content saved = contentRepository.save(content);

    return new ContentCreateResponse(saved.getId(), ValidationStatus.PENDING);
  }

  private void validateAuthentication(UUID memberUuid) {
    if (memberUuid == null) {
      throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
    }
  }

  private void validateCreateRequest(ContentCreateRequest request) {
    if (request.inputType() == InputType.URL) {
      validateUrlType(request);
    } else if (request.inputType() == InputType.TEXT) {
      validateTextType(request);
    }

    // 공통 텍스트 길이 검증 (Fast Fail)
    String contentText = (request.inputType() == InputType.TEXT) ? request.text() : request.extractedText();
    if (contentText.length() > MAX_TEXT_LENGTH) {
      throw new CustomException(ContentErrorCode.CONTENT_TOO_LARGE);
    }
  }

  private void validateUrlType(ContentCreateRequest request) {
    if (request.title() == null || request.title().isBlank()) {
      throw new CustomException(ContentErrorCode.TITLE_REQUIRED);
    }
    if (request.title().length() > 255) {
      throw new CustomException(ContentErrorCode.TITLE_TOO_LONG);
    }
    if (request.url() == null || request.url().isBlank()) {
      throw new CustomException(ContentErrorCode.URL_REQUIRED);
    }
    validateUrlFormat(request.url());
    if (request.extractedText() == null || request.extractedText().isBlank()) {
      throw new CustomException(ContentErrorCode.MISSING_EXTRACTED_TEXT);
    }
    if (request.text() != null && !request.text().isBlank()) {
      throw new CustomException(ContentErrorCode.UNNECESSARY_TEXT);
    }
  }

  private void validateTextType(ContentCreateRequest request) {
    if (request.title() != null && !request.title().isBlank() && request.title().length() > 255) {
      throw new CustomException(ContentErrorCode.TITLE_TOO_LONG);
    }
    if (request.text() == null || request.text().isBlank()) {
      throw new CustomException(ContentErrorCode.TEXT_REQUIRED);
    }
    if ((request.url() != null && !request.url().isBlank())
        || (request.extractedText() != null && !request.extractedText().isBlank())) {
      throw new CustomException(ContentErrorCode.UNNECESSARY_URL_INFO);
    }
  }

  private void validateUrlFormat(String urlString) {
    try {
      URL parsedUrl = new URL(urlString);
      String protocol = parsedUrl.getProtocol();
      if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
        throw new CustomException(ContentErrorCode.INVALID_URL);
      }
    } catch (MalformedURLException e) {
      throw new CustomException(ContentErrorCode.INVALID_URL);
    }
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
