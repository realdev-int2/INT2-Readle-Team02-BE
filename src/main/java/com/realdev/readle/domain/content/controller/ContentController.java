package com.realdev.readle.domain.content.controller;

import com.realdev.readle.domain.content.dto.request.ContentExtractRequest;
import com.realdev.readle.domain.content.dto.response.ContentExtractResponse;
import com.realdev.readle.domain.content.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
}
