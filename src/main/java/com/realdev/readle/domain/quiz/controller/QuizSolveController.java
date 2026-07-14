package com.realdev.readle.domain.quiz.controller;

import com.realdev.readle.domain.quiz.dto.QuizDetailResponse;
import com.realdev.readle.domain.quiz.dto.QuizSubmitRequest;
import com.realdev.readle.domain.quiz.dto.QuizSubmitResponse;
import com.realdev.readle.domain.quiz.service.QuizSolveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/quizzes")
public class QuizSolveController {

    private final QuizSolveService quizSolveService;

    // MVP에서는 SecurityContextHolder를 통해 Member ID를 가져와야 하지만, 현재 구현 환경상 하드코딩 또는 세션에서 조회한다고 가정.
    // 여기서는 헤더나 별도 어노테이션 대신 임시로 1L을 사용하거나 SecurityContext가 적용된 이후 대체해야 합니다.
    // 임시로 memberId = 1L 로 고정합니다. (실제 환경에서는 @AuthenticationPrincipal 등 사용)
    private Long getCurrentMemberId() {
        return 1L; // TODO: Security 연동
    }

    @PostMapping("/{quizSetId}/attempts")
    public ResponseEntity<Map<String, Long>> startQuiz(@PathVariable("quizSetId") Long quizSetId) {
        Long memberId = getCurrentMemberId();
        Long attemptId = quizSolveService.startQuiz(quizSetId, memberId);
        return ResponseEntity.ok(Map.of("attemptId", attemptId));
    }

    @GetMapping("/attempts/{attemptId}")
    public ResponseEntity<QuizDetailResponse> getQuizAttemptDetail(@PathVariable("attemptId") Long attemptId) {
        Long memberId = getCurrentMemberId();
        QuizDetailResponse response = quizSolveService.getQuizAttemptDetail(attemptId, memberId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public ResponseEntity<QuizSubmitResponse> submitAnswers(
            @PathVariable("attemptId") Long attemptId,
            @Valid @RequestBody QuizSubmitRequest request) {
        Long memberId = getCurrentMemberId();
        QuizSubmitResponse response = quizSolveService.submitAnswers(attemptId, memberId, request);
        return ResponseEntity.ok(response);
    }
}
