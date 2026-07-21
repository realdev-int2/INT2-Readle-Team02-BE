package com.realdev.readle.domain.dashboard.controller;

import com.realdev.readle.domain.dashboard.dto.response.DashboardResponse;
import com.realdev.readle.domain.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "학습 현황 대시보드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final DashboardService dashboardService;

  @Operation(
      summary = "학습 현황 요약 조회",
      description = "완료한 퀴즈 수, 누적 문제 수, 태그별 평균 정답률 및 최근 학습 이력을 집계 조회합니다.")
  @GetMapping
  public ResponseEntity<DashboardResponse> getDashboard(
      @AuthenticationPrincipal String memberUuid) {
    DashboardResponse response = dashboardService.getDashboardSummary(memberUuid);
    return ResponseEntity.ok(response);
  }
}
