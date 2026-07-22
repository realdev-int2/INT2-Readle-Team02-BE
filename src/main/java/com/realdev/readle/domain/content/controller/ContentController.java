package com.realdev.readle.domain.content.controller;

import com.realdev.readle.domain.content.dto.request.ContentCreateRequest;
import com.realdev.readle.domain.content.dto.request.ContentExtractRequest;
import com.realdev.readle.domain.content.dto.response.ContentCreateResponse;
import com.realdev.readle.domain.content.dto.response.ContentExtractResponse;
import com.realdev.readle.domain.content.dto.response.ContentValidationResponse;
import com.realdev.readle.domain.content.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Content", description = "콘텐츠 API")
@RestController
@RequestMapping("/api/contents")
@RequiredArgsConstructor
public class ContentController {

  private final ContentService contentService;

  @Operation(
      summary = "URL 본문 텍스트 추출",
      description = "입력한 URL의 웹 페이지 본문 텍스트와 제목을 정제하여 마크다운 포맷으로 추출합니다.")
  @PostMapping("/extract")
  public ResponseEntity<ContentExtractResponse> extract(
      @Valid @RequestBody ContentExtractRequest request) {
    ContentExtractResponse response = contentService.extract(request);
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "콘텐츠 등록",
      description = "URL 추출 결과 또는 텍스트를 DB에 저장하고 검증 대기(pending) 응답을 반환합니다.")
  @PostMapping
  public ResponseEntity<ContentCreateResponse> create(
      @AuthenticationPrincipal String memberUuid,
      @Valid @RequestBody ContentCreateRequest request) {
    ContentCreateResponse response = contentService.createContent(request, memberUuid);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(
      summary = "콘텐츠 검증 상태 및 결과 조회",
      description =
          "비동기로 진행되는 콘텐츠의 적합성 검증 현재 상태(PENDING, PASSED, REJECTED, FAILED) 및 최종 결과를 조회합니다. 검증 실패(거절/에러) 시 사용자 노출용 사유 메시지와 우회 생성 가능 여부(bypassAvailable)를 함께 반환합니다.")
  @GetMapping("/{contentId}/validation")
  public ResponseEntity<ContentValidationResponse> validate(
      @PathVariable Long contentId, @AuthenticationPrincipal String memberUuid) {
    ContentValidationResponse response = contentService.getValidationResult(contentId, memberUuid);
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "콘텐츠 검증 재시도",
      description =
          "검증에 실패(FAILED)한 콘텐츠에 대해 검증 파이프라인을 처음부터 다시 실행합니다. 기존 FAILED 상태의 검증 이력을 덮어쓰지 않고 새로운 PENDING 이력이 생성됩니다.")
  @PostMapping("/{contentId}/validation/retry")
  public ResponseEntity<ContentValidationResponse> retryValidation(
      @PathVariable Long contentId, @AuthenticationPrincipal String memberUuid) {
    ContentValidationResponse response = contentService.retryValidation(contentId, memberUuid);
    return ResponseEntity.ok(response);
  }
}
