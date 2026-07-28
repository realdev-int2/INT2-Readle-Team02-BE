package com.realdev.readle.domain.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.domain.quiz.dto.response.ClaudeQualityVerifyResponseDto.QuestionVerifyResult;
import com.realdev.readle.global.infrastructure.ai.ClaudeClient;
import com.realdev.readle.global.infrastructure.prompt.PromptLoader;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuizQualityGuardTest {

  private QuizQualityGuard quizQualityGuard;
  private PromptLoader promptLoader;
  private ClaudeClient claudeClient;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    promptLoader = mock(PromptLoader.class);
    claudeClient = mock(ClaudeClient.class);
    objectMapper = new ObjectMapper();
    quizQualityGuard = new QuizQualityGuard(promptLoader, claudeClient, objectMapper);
  }

  @Test
  @DisplayName("질문 괄호 안에 정답 단어가 명시된 경우 leak 탐지 성공")
  void checkRegexLeak_DetectsParenthesesLeak() {
    String question = "다음 스프링 트랜잭션 전파 속성 (REQUIRED) 에 대한 설명으로 올바른 것은?";
    String answer = "REQUIRED";

    boolean hasLeak = quizQualityGuard.checkRegexLeak(question, answer);

    assertThat(hasLeak).isTrue();
  }

  @Test
  @DisplayName("질문에 약어 정답의 풀네임(Container-Managed Transactions)이 노출된 경우 leak 탐지 성공")
  void checkRegexLeak_DetectsAcronymFullNameLeak() {
    String question = "Container-Managed Transactions 방식으로 관리되는 이 기술의 약어는?";
    String answer = "CMT";

    boolean hasLeak = quizQualityGuard.checkRegexLeak(question, answer);

    assertThat(hasLeak).isTrue();
  }

  @Test
  @DisplayName("정답 단어에 한국어 조사가 결합되어 노출된 경우 leak 탐지 성공")
  void checkRegexLeak_DetectsKoreanParticleLeak() {
    String question = "스프링 프레임워크에서 Autowired는 객체를 주입받는다.";
    String answer = "Autowired";

    boolean hasLeak = quizQualityGuard.checkRegexLeak(question, answer);

    assertThat(hasLeak).isTrue();
  }

  @Test
  @DisplayName("정답 단어나 풀네임이 노출되지 않고 문맥으로만 테스트하는 정상 문항은 leak 미탐지")
  void checkRegexLeak_ReturnsFalseForCleanQuestion() {
    String question = "기존 트랜잭션이 존재하면 참여하고, 존재하지 않으면 새로운 트랜잭션을 생성하는 전파 속성은?";
    String answer = "REQUIRED";

    boolean hasLeak = quizQualityGuard.checkRegexLeak(question, answer);

    assertThat(hasLeak).isFalse();
  }

  @Test
  @DisplayName("화이트리스트 단어(Java, SQL 등)는 질문 본문에 포함되어도 leak 미탐지")
  void checkRegexLeak_ReturnsFalseForWhitelistedTerms() {
    String question = "Java 언어로 작성된 프로그램의 특징으로 가장 올바른 것은?";
    String answer = "Java";

    boolean hasLeak = quizQualityGuard.checkRegexLeak(question, answer);

    assertThat(hasLeak).isFalse();
  }

  @Test
  @DisplayName("LLM 검증기 mock 연동 시 검증 결과를 정상 파싱하여 반환한다")
  void verifyWithLlm_ReturnsVerifyResult() {
    String mockResponse = "{\"results\":[{\"index\":1,\"hasLeak\":false,\"reason\":null}]}";
    given(promptLoader.loadPrompt(anyString())).willReturn("mock prompt");
    given(claudeClient.getGeneratedText(anyString(), anyString())).willReturn(mockResponse);

    List<QuestionVerifyResult> results =
        quizQualityGuard.verifyWithLlm(
            List.of(new QuizQualityGuard.QuestionInspectItem(1, "질문 내용", "REQUIRED")));

    assertThat(results).hasSize(1);
    assertThat(results.get(0).hasLeak()).isFalse();
  }

  @Test
  @DisplayName("LLM 검증기 예외 발생 또는 비정상 JSON 수신 시 경량 정규식 검사 결과로 안전 Fallback된다")
  void verifyWithLlm_FallsBackToRegex_WhenExceptionOrMalformedJson() {
    given(promptLoader.loadPrompt(anyString())).willReturn("mock prompt");
    given(claudeClient.getGeneratedText(anyString(), anyString()))
        .willThrow(new RuntimeException("LLM Timeout or Connection Failure"));

    List<QuestionVerifyResult> results =
        quizQualityGuard.verifyWithLlm(
            List.of(
                new QuizQualityGuard.QuestionInspectItem(
                    1, "스프링 트랜잭션 (REQUIRED) 옵션의 역할은?", "REQUIRED")));

    assertThat(results).hasSize(1);
    assertThat(results.get(0).hasLeak()).isTrue();
    assertThat(results.get(0).reason()).isEqualTo("Regex fallback match");
  }
}
