package com.realdev.readle.domain.content.controller;

import com.realdev.readle.domain.content.dto.request.ContentExtractRequest;
import com.realdev.readle.domain.content.dto.response.ContentExtractResponse;
import com.realdev.readle.domain.content.service.ContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contents")
@RequiredArgsConstructor
public class ContentController {

  private final ContentService contentService;

  @PostMapping("/extract")
  public ResponseEntity<ContentExtractResponse> extract(
      @Valid @RequestBody ContentExtractRequest request) {
    ContentExtractResponse response = contentService.extract(request);
    return ResponseEntity.ok(response);
  }
}
