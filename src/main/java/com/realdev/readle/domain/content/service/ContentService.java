package com.realdev.readle.domain.content.service;

import com.realdev.readle.domain.content.dto.request.ContentCreateRequest;
import com.realdev.readle.domain.content.dto.request.ContentExtractRequest;
import com.realdev.readle.domain.content.dto.response.ContentCreateResponse;
import com.realdev.readle.domain.content.dto.response.ContentExtractResponse;
import com.realdev.readle.domain.content.dto.response.ContentValidationResponse;
import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.content.entity.ContentValidation;
import com.realdev.readle.domain.content.entity.InputType;
import com.realdev.readle.domain.content.entity.ValidationMethod;
import com.realdev.readle.domain.content.entity.ValidationStatus;
import com.realdev.readle.domain.content.exception.ContentErrorCode;
import com.realdev.readle.domain.content.repository.ContentRepository;
import com.realdev.readle.domain.content.repository.ContentValidationRepository;
import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.exception.MemberErrorCode;
import com.realdev.readle.domain.member.repository.MemberRepository;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.util.crawler.WebCrawler;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
  private final ApplicationEventPublisher eventPublisher;
  private final ContentValidationRepository contentValidationRepository;

  public ContentExtractResponse extract(ContentExtractRequest request) {
    WebCrawler.CrawledDocument crawledDocument = webCrawler.crawl(request.url());
    return new ContentExtractResponse(crawledDocument.title(), crawledDocument.content());
  }

  @Transactional
  public ContentCreateResponse createContent(ContentCreateRequest request, String memberUuid) {
    validateAuthentication(memberUuid);
    validateCreateRequest(request);

    // 1. 회원 조회
    Member member =
        memberRepository
            .findByUuid(memberUuid)
            .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

    // 2. 저장
    Content content = buildContent(request, member);
    Content saved = contentRepository.save(content);

    eventPublisher.publishEvent(new ContentCreatedEvent(saved.getId(), memberUuid));

    return new ContentCreateResponse(saved.getId(), ValidationStatus.PENDING);
  }

  @Transactional(readOnly = true)
  public ContentValidationResponse getValidationResult(Long contentId, String memberUuid) {
    validateAuthentication(memberUuid);
    validateContentOwnership(contentId, memberUuid);
    ContentValidation validation = getLatestValidation(contentId);

    // bypassAvailable 조건 계산
    boolean bypassAvailable =
        validation.getStatus() == ValidationStatus.REJECTED
            && validation.getValidationMethod() == ValidationMethod.AI;

    // errorCode 및 매핑 메시지 추출
    String errorCodeString = null;
    String message = null;

    if (validation.getRejectReasonCode() != null) {
      errorCodeString = validation.getRejectReasonCode().name();
      message =
          ValidationMessageConverter.convertRejectReasonMessage(validation.getRejectReasonCode());
    } else if (validation.getErrorCode() != null) {
      errorCodeString = validation.getErrorCode().name();
      message = ValidationMessageConverter.convertErrorMessage(validation.getErrorCode());
    }

    // 응답 DTO 반환
    return new ContentValidationResponse(
        contentId,
        validation.getStatus(),
        errorCodeString,
        message,
        bypassAvailable,
        validation.getCreatedAt(),
        validation.getStatus() == ValidationStatus.PENDING ? null : validation.getValidatedAt());
  }

  @Transactional
  public ContentValidationResponse retryValidation(Long contentId, String memberUuid) {
    validateAuthentication(memberUuid);
    Content content = getOwnedContentWithLock(contentId, memberUuid);

    ContentValidation validation = getLatestValidation(contentId);

    if (validation.getStatus() == ValidationStatus.PENDING) {
      throw new CustomException(ContentErrorCode.VALIDATION_ALREADY_RUNNING);
    }
    if (validation.getStatus() == ValidationStatus.PASSED
        || validation.getStatus() == ValidationStatus.REJECTED) {
      throw new CustomException(ContentErrorCode.NOT_RETRYABLE);
    }

    // 프론트엔드 폴링 Race Condition을 막기 위해 PENDING 이력을 즉시 DB에 저장
    ContentValidation pendingValidation =
        ContentValidation.builder()
            .content(content)
            .status(ValidationStatus.PENDING)
            .validationMethod(ValidationMethod.AI)
            .build();
    contentValidationRepository.save(pendingValidation);

    // 비동기 검증 파이프라인 재트리거 (실제 가드레일 -> AI 로직 수행)
    eventPublisher.publishEvent(new ContentCreatedEvent(contentId, memberUuid));

    return new ContentValidationResponse(
        contentId,
        ValidationStatus.PENDING,
        null,
        null,
        false,
        pendingValidation.getCreatedAt(),
        null);
  }

  private void validateAuthentication(String memberUuid) {
    if (memberUuid == null) {
      throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
    }
  }

  private void validateCreateRequest(ContentCreateRequest request) {
    validateFieldsByInputType(request);
    validateContentLength(request);
  }

  private void validateFieldsByInputType(ContentCreateRequest request) {
    if (request.inputType() == InputType.URL) {
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
      if (request.text() != null && !request.text().isBlank()) {
        throw new CustomException(ContentErrorCode.UNNECESSARY_TEXT);
      }
    } else if (request.inputType() == InputType.TEXT) {
      if (request.title() != null && !request.title().isBlank() && request.title().length() > 255) {
        throw new CustomException(ContentErrorCode.TITLE_TOO_LONG);
      }
      if ((request.url() != null && !request.url().isBlank())
          || (request.extractedText() != null && !request.extractedText().isBlank())) {
        throw new CustomException(ContentErrorCode.UNNECESSARY_URL_INFO);
      }
    } else {
      throw new CustomException(ContentErrorCode.INVALID_INPUT_TYPE);
    }
  }

  private void validateContentLength(ContentCreateRequest request) {
    String contentText =
        (request.inputType() == InputType.TEXT) ? request.text() : request.extractedText();

    if (contentText == null || contentText.isBlank()) {
      throw new CustomException(
          request.inputType() == InputType.URL
              ? ContentErrorCode.MISSING_EXTRACTED_TEXT
              : ContentErrorCode.TEXT_REQUIRED);
    }

    if (contentText.length() > MAX_TEXT_LENGTH) {
      throw new CustomException(ContentErrorCode.CONTENT_TOO_LARGE);
    }
  }

  private void validateUrlFormat(String urlString) {
    try {
      URI parsedUri = new URI(urlString);
      String scheme = parsedUri.getScheme();
      if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
        throw new CustomException(ContentErrorCode.INVALID_URL);
      }
      if (parsedUri.getHost() == null || parsedUri.getHost().isBlank()) {
        throw new CustomException(ContentErrorCode.INVALID_URL);
      }
    } catch (URISyntaxException | NullPointerException e) {
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
    return text.substring(0, Math.min(TITLE_FALLBACK_LENGTH, text.length()));
  }

  private void validateContentOwnership(Long contentId, String memberUuid) {
    Content content =
        contentRepository
            .findById(contentId)
            .orElseThrow(() -> new CustomException(ContentErrorCode.CONTENT_NOT_FOUND));
    if (!content.getMember().getUuid().equals(memberUuid)) {
      throw new CustomException(ContentErrorCode.CONTENT_ACCESS_DENIED);
    }
  }

  private Content getOwnedContentWithLock(Long contentId, String memberUuid) {
    Content content =
        contentRepository
            .findByIdWithPessimisticLock(contentId)
            .orElseThrow(() -> new CustomException(ContentErrorCode.CONTENT_NOT_FOUND));
    if (!content.getMember().getUuid().equals(memberUuid)) {
      throw new CustomException(ContentErrorCode.CONTENT_ACCESS_DENIED);
    }
    return content;
  }

  private ContentValidation getLatestValidation(Long contentId) {
    return contentValidationRepository
        .findFirstByContentIdOrderByCreatedAtDesc(contentId)
        .orElseThrow(() -> new CustomException(ContentErrorCode.CONTENT_VALIDATION_NOT_FOUND));
  }
}
