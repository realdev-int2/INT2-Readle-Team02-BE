package com.realdev.readle.domain.quiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.domain.quiz.dto.response.ClaudeQualityVerifyResponseDto;
import com.realdev.readle.domain.quiz.dto.response.ClaudeQualityVerifyResponseDto.QuestionVerifyResult;
import com.realdev.readle.global.infrastructure.ai.ClaudeClient;
import com.realdev.readle.global.infrastructure.prompt.PromptLoader;
import com.realdev.readle.global.util.JsonExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class QuizQualityGuard {

  private static final Logger log = LoggerFactory.getLogger(QuizQualityGuard.class);
  private static final Pattern PARENTHESES_PATTERN = Pattern.compile("\\(([^)]+)\\)");
  private static final Set<String> WHITELIST =
      Set.of(
          "JAVA", "SQL", "SPRING", "API", "HTML", "CSS", "HTTP", "REST", "JSON", "XML", "A", "B",
          "C", "D", "0", "1", "2", "3", "4", "5", "TRUE", "FALSE");

  private final PromptLoader promptLoader;
  private final ClaudeClient claudeClient;
  private final ObjectMapper objectMapper;

  public QuizQualityGuard(
      PromptLoader promptLoader, ClaudeClient claudeClient, ObjectMapper objectMapper) {
    this.promptLoader = promptLoader;
    this.claudeClient = claudeClient;
    this.objectMapper = objectMapper;
  }

  public boolean checkRegexLeak(String questionText, String answerText) {
    if (questionText == null || answerText == null) {
      return false;
    }

    String normQuestion = questionText.trim();
    String normAnswer = answerText.trim();
    if (normAnswer.isEmpty()) {
      return false;
    }

    // 1. 괄호 내용 검사
    Matcher matcher = PARENTHESES_PATTERN.matcher(normQuestion);
    while (matcher.find()) {
      String inside = matcher.group(1).trim();
      if (inside.equalsIgnoreCase(normAnswer)) {
        return true;
      }
      if (isAcronymMatch(normAnswer, inside) || isAcronymMatch(inside, normAnswer)) {
        return true;
      }
    }

    // 2. 약어 <-> 풀네임 1:1 직결 노출 검사
    if (isAcronymMatch(normAnswer, normQuestion)) {
      return true;
    }

    // 3. 정답 단어 한국어 조사 경계 서브스트링 노출 검사 (화이트리스트 / 단어 길이 제외)
    String upperAnswer = normAnswer.toUpperCase();
    if (normAnswer.length() >= 3 && !WHITELIST.contains(upperAnswer)) {
      String regex =
          "(?i)\\b("
              + Pattern.quote(normAnswer)
              + ")(?:는|은|가|이|를|을|의|에|에서|으로|로|과|와|\\b|\\(|\\)|\\s)";
      Pattern pattern = Pattern.compile(regex);
      if (pattern.matcher(normQuestion).find()) {
        return true;
      }
    }

    return false;
  }

  private boolean isAcronymMatch(String acronymCandidate, String fullTextCandidate) {
    String cleanAcronym = acronymCandidate.replaceAll("[^a-zA-Z]", "");
    if (cleanAcronym.length() < 2 || cleanAcronym.length() > 6) {
      return false;
    }
    if (!cleanAcronym.equals(cleanAcronym.toUpperCase())) {
      return false; // 약어는 대문자 패턴 기준
    }

    // fullTextCandidate에서 영문 단어들의 첫 글자(Initial) 조합 추출
    String[] words = fullTextCandidate.split("[\\s\\-_/]+");
    StringBuilder initials = new StringBuilder();
    for (String w : words) {
      String cleanWord = w.replaceAll("[^a-zA-Z]", "");
      if (!cleanWord.isEmpty()) {
        initials.append(Character.toUpperCase(cleanWord.charAt(0)));
      }
    }

    return initials.toString().contains(cleanAcronym);
  }

  public List<QuestionVerifyResult> verifyWithLlm(List<QuestionInspectItem> items) {
    if (items == null || items.isEmpty()) {
      return List.of();
    }

    try {
      String quizzesJson = objectMapper.writeValueAsString(items);
      String systemPrompt =
          promptLoader.loadPrompt("quiz-quality-verifier.txt", Map.of("quizzesJson", quizzesJson));

      CompletableFuture<String> future =
          CompletableFuture.supplyAsync(
              () ->
                  claudeClient.getGeneratedText(
                      systemPrompt, "Inspect the provided quiz questions for answer leakage."));
      String responseText = future.get(7, TimeUnit.SECONDS);

      String cleanJson = JsonExtractor.extractJson(responseText);
      ClaudeQualityVerifyResponseDto dto =
          objectMapper.readValue(cleanJson, ClaudeQualityVerifyResponseDto.class);
      return dto != null && dto.results() != null ? dto.results() : List.of();
    } catch (Exception e) {
      log.warn("[QUIZ_QUALITY_GUARD] LLM 검증기 연동 중 예외 발생. 경량 정규식 결과로 대체합니다: {}", e.getMessage());
      List<QuestionVerifyResult> fallback = new ArrayList<>();
      for (QuestionInspectItem item : items) {
        boolean leak = checkRegexLeak(item.question(), item.answer());
        fallback.add(
            new QuestionVerifyResult(item.index(), leak, leak ? "Regex fallback match" : null));
      }
      return fallback;
    }
  }

  public record QuestionInspectItem(int index, String question, String answer) {}
}
