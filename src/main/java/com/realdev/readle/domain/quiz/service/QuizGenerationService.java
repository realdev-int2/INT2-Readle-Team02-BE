package com.realdev.readle.domain.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.domain.content.entity.ContentValidation;
import com.realdev.readle.domain.content.entity.ValidationStatus;
import com.realdev.readle.domain.content.repository.ContentValidationRepository;
import com.realdev.readle.domain.quiz.dto.ClaudeQuizResponseDto;
import com.realdev.readle.domain.quiz.dto.QuizCreateResponse;
import com.realdev.readle.domain.quiz.entity.QuestionType;
import com.realdev.readle.domain.quiz.entity.QuizChoice;
import com.realdev.readle.domain.quiz.entity.QuizQuestion;
import com.realdev.readle.domain.quiz.entity.QuizSet;
import com.realdev.readle.domain.quiz.entity.QuizSetStatus;
import com.realdev.readle.domain.quiz.exception.QuizGenerationException;
import com.realdev.readle.domain.quiz.exception.ValidationNotPassedException;
import com.realdev.readle.domain.quiz.repository.QuizChoiceRepository;
import com.realdev.readle.domain.quiz.repository.QuizQuestionRepository;
import com.realdev.readle.domain.quiz.repository.QuizSetRepository;
import com.realdev.readle.global.infrastructure.ai.ClaudeClient;
import com.realdev.readle.global.infrastructure.prompt.PromptLoader;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizGenerationService {

  private final ContentValidationRepository contentValidationRepository;
  private final QuizSetRepository quizSetRepository;
  private final QuizQuestionRepository quizQuestionRepository;
  private final QuizChoiceRepository quizChoiceRepository;
  private final ClaudeClient claudeClient;
  private final PromptLoader promptLoader;
  private final ObjectMapper objectMapper;

  private final TransactionTemplate transactionTemplate;

  public QuizCreateResponse createQuizSet(Long sourceValidationId) {
    ContentValidation validation =
        contentValidationRepository
            .findById(sourceValidationId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 검증 ID입니다."));

    boolean isBypassedTemp = false;
    if (validation.getStatus() != ValidationStatus.PASSED) {
      if (validation.getStatus() == ValidationStatus.FAILED
          || validation.getStatus() == ValidationStatus.PENDING) {
        throw new ValidationNotPassedException("해당 콘텐츠는 퀴즈 생성이 불가능한 상태입니다.");
      }
      isBypassedTemp = true;
    }
    final boolean isBypassed = isBypassedTemp;

    // 1. 초기 QuizSet 레코드 생성 및 저장 (Transaction 분리)
    QuizSet quizSet = transactionTemplate.execute(status -> {
      QuizSet existing = quizSetRepository.findBySourceValidationId(sourceValidationId).orElse(null);
      if (existing != null) {
        if (existing.getStatus() == QuizSetStatus.FAILED) {
          quizSetRepository.delete(existing);
          quizSetRepository.flush();
        } else {
          throw new QuizGenerationException("이미 해당 콘텐츠에 대한 퀴즈 생성 요청이 진행 중이거나 완료되었습니다.");
        }
      }

      QuizSet newQuizSet;
      try {
        newQuizSet = QuizSet.create(validation.getContent(), validation, isBypassed);
        return quizSetRepository.saveAndFlush(newQuizSet);
      } catch (DataIntegrityViolationException e) {
        throw new QuizGenerationException("이미 해당 콘텐츠에 대한 퀴즈 생성 요청이 진행 중이거나 완료되었습니다.");
      }
    });

    try {
      // 2. AI Prompt 생성 및 호출 (Non-Transactional)
      String articleText =
          validation.getContent().getRawText() != null
              ? validation.getContent().getRawText()
              : validation.getContent().getExtractedText();

      if (articleText == null || articleText.isBlank()) {
        throw new ValidationNotPassedException("퀴즈를 생성할 본문 텍스트가 존재하지 않습니다.");
      }

      // 방어: </source_content> 인젝션 치환
      articleText = articleText.replace("</source_content>", "< /source_content>");

      String additionalRule = "";
      if (!articleText.contains("{")
          && !articleText.contains("=")
          && !articleText.contains(";")
          && !articleText.contains("public")
          && !articleText.contains("function")) {
        additionalRule = "본문에 코드가 없으므로 code_blank 유형의 문제는 생성하지 마세요.";
      }

      String systemPrompt =
          promptLoader.loadPrompt("quiz-gen-prompt.txt", Map.of("additional_rule", additionalRule));
      String userPrompt = "<source_content>\n" + articleText + "\n</source_content>";

      String jsonResponse = claudeClient.getGeneratedText(systemPrompt, userPrompt);
      ClaudeQuizResponseDto parsedResponse = parseAndValidate(jsonResponse);

      // 3. 문제 및 선택지 엔티티 저장 및 완료 (Transaction 분리)
      return transactionTemplate.execute(status -> {
        QuizSet activeQuizSet = quizSetRepository.findById(quizSet.getId()).orElseThrow();
        
        int orderNo = 1;
        for (ClaudeQuizResponseDto.ClaudeQuizDto quizDto : parsedResponse.getQuizzes()) {
          QuestionType type = QuestionType.valueOf(quizDto.getType().toUpperCase());

          QuizQuestion question =
              QuizQuestion.create(
                  activeQuizSet,
                  orderNo++,
                  type,
                  quizDto.getQuestion(),
                  quizDto.getCodeSnippet(),
                  type == QuestionType.MULTIPLE_CHOICE ? null : quizDto.getAnswer(),
                  null,
                  null);
          quizQuestionRepository.save(question);

          if (type == QuestionType.MULTIPLE_CHOICE) {
            if (quizDto.getOptions() == null || quizDto.getOptions().isEmpty()) {
              throw new QuizGenerationException("객관식 문제에 선택지가 없습니다.");
            }
            
            int correctChoiceCount = 0;
            int choiceOrderNo = 1;
            for (String optionText : quizDto.getOptions()) {
              boolean isCorrect = String.valueOf(choiceOrderNo - 1).equals(quizDto.getAnswer());
              if (isCorrect) correctChoiceCount++;
              QuizChoice choice = QuizChoice.create(question, choiceOrderNo++, optionText, isCorrect);
              quizChoiceRepository.save(choice);
            }
            if (correctChoiceCount != 1) {
              throw new QuizGenerationException("객관식 문제의 정답 개수가 1개가 아닙니다.");
            }
          }
        }

        activeQuizSet.complete(parsedResponse.getQuizzes().size());
        return QuizCreateResponse.from(activeQuizSet);
      });

    } catch (Exception e) {
      transactionTemplate.execute(status -> {
        QuizSet activeQuizSet = quizSetRepository.findById(quizSet.getId()).orElse(null);
        if (activeQuizSet != null) {
          activeQuizSet.fail();
          quizSetRepository.save(activeQuizSet);
        }
        return null;
      });
      log.error("퀴즈 생성 실패: {}", e.getMessage(), e);
      throw new QuizGenerationException("퀴즈 생성 중 오류가 발생했습니다.", e);
    }
  }

  private ClaudeQuizResponseDto parseAndValidate(String jsonResponse) {
    try {
      if (jsonResponse.startsWith("```json")) {
        jsonResponse = jsonResponse.substring(7);
        if (jsonResponse.endsWith("```")) {
          jsonResponse = jsonResponse.substring(0, jsonResponse.length() - 3);
        }
      }
      jsonResponse = jsonResponse.trim();

      ClaudeQuizResponseDto response =
          objectMapper.readValue(jsonResponse, ClaudeQuizResponseDto.class);

      if (response.getQuizzes() == null || response.getQuizzes().isEmpty()) {
        throw new QuizGenerationException("퀴즈 목록이 비어있습니다.");
      }
      if (response.getQuizzes().size() < 1 || response.getQuizzes().size() > 5) {
        throw new QuizGenerationException("생성된 문제 수가 1~5개 범위를 벗어납니다.");
      }
      if (response.getTags() == null || response.getTags().isEmpty() || response.getTags().size() < 1 || response.getTags().size() > 3) {
        throw new QuizGenerationException("생성된 태그 수가 1~3개 범위를 벗어나거나 비어있습니다.");
      }

      return response;
    } catch (JsonProcessingException e) {
      throw new QuizGenerationException("AI 응답 JSON 파싱에 실패했습니다.", e);
    }
  }
}
