package com.realdev.readle.domain.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.domain.content.entity.ContentValidation;
import com.realdev.readle.domain.content.entity.ValidationMethod;
import com.realdev.readle.domain.content.entity.ValidationStatus;
import com.realdev.readle.domain.content.repository.ContentValidationRepository;
import com.realdev.readle.domain.quiz.dto.ClaudeQuizResponseDto;
import com.realdev.readle.domain.quiz.dto.response.QuizCreateResponse;
import com.realdev.readle.domain.quiz.entity.QuestionType;
import com.realdev.readle.domain.quiz.entity.QuizChoice;
import com.realdev.readle.domain.quiz.entity.QuizQuestion;
import com.realdev.readle.domain.quiz.entity.QuizSet;
import com.realdev.readle.domain.quiz.entity.QuizSetStatus;
import com.realdev.readle.domain.quiz.exception.QuizErrorCode;
import com.realdev.readle.domain.quiz.repository.QuizChoiceRepository;
import com.realdev.readle.domain.quiz.repository.QuizQuestionRepository;
import com.realdev.readle.domain.quiz.repository.QuizSetRepository;
import com.realdev.readle.domain.tag.service.TagService;
import com.realdev.readle.global.exception.CustomException;
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
  private final TagService tagService;

  private final TransactionTemplate transactionTemplate;

  public QuizCreateResponse createQuizSet(Long sourceValidationId) {
    ContentValidation validation =
        contentValidationRepository
            .findByIdWithContent(sourceValidationId)
            .orElseThrow(
                () ->
                    new CustomException(
                        QuizErrorCode.SOURCE_VALIDATION_NOT_FOUND, "존재하지 않는 검증 ID입니다."));

    // 1. 초기 QuizSet 레코드 생성 및 저장 (Transaction 분리)
    QuizSet quizSet =
        transactionTemplate.execute(
            status -> {
              QuizSet existing =
                  quizSetRepository.findBySourceValidationId(sourceValidationId).orElse(null);
              if (existing != null) {
                if (existing.getStatus() == QuizSetStatus.FAILED) {
                  existing.retry();
                  return quizSetRepository.saveAndFlush(existing);
                } else {
                  throw new CustomException(
                      QuizErrorCode.QUIZ_GENERATION_FAILED,
                      "이미 해당 콘텐츠에 대한 퀴즈 생성 요청이 진행 중이거나 완료되었습니다.");
                }
              }

              // Detached 객체 대신 Managed 객체를 재조회하여 사용
              ContentValidation managedValidation =
                  contentValidationRepository
                      .findByIdWithContent(sourceValidationId)
                      .orElseThrow(
                          () ->
                              new CustomException(
                                  QuizErrorCode.SOURCE_VALIDATION_NOT_FOUND, "존재하지 않는 검증 ID입니다."));

              boolean bypassAvailable =
                  managedValidation.getStatus() == ValidationStatus.REJECTED
                      && managedValidation.getValidationMethod() == ValidationMethod.AI;

              // Validation 상태 분기: PASSED 또는 bypassAvailable 허용
              if (managedValidation.getStatus() != ValidationStatus.PASSED && !bypassAvailable) {
                throw new CustomException(QuizErrorCode.VALIDATION_NOT_PASSED);
              }
              boolean isBypassed = bypassAvailable;

              QuizSet newQuizSet;
              try {
                newQuizSet =
                    QuizSet.create(managedValidation.getContent(), managedValidation, isBypassed);
                return quizSetRepository.saveAndFlush(newQuizSet);
              } catch (DataIntegrityViolationException e) {
                throw new CustomException(
                    QuizErrorCode.QUIZ_GENERATION_FAILED,
                    "이미 해당 콘텐츠에 대한 퀴즈 생성 요청이 진행 중이거나 완료되었습니다.",
                    e);
              }
            });

    try {
      // 2. AI Prompt 생성 및 호출 (Non-Transactional)
      // Fetch Join으로 가져온 validation에서 지연 로딩 예외 없이 본문 조회
      String articleText =
          validation.getContent().getRawText() != null
              ? validation.getContent().getRawText()
              : validation.getContent().getExtractedText();

      if (articleText == null || articleText.isBlank()) {
        throw new CustomException(
            QuizErrorCode.EMPTY_SOURCE_TEXT_FOR_QUIZ, "퀴즈를 생성할 본문 텍스트가 존재하지 않습니다.");
      }

      // 방어: </source_content> 인젝션 치환 (대소문자·공백 무관하게 처리)
      articleText = articleText.replaceAll("(?i)</\\s*source_content\\s*>", "< /source_content>");

      boolean hasCode =
          articleText.contains("{")
              || articleText.contains("=")
              || articleText.contains(";")
              || articleText.contains("public")
              || articleText.contains("function");

      String additionalRule = "";
      if (!hasCode) {
        additionalRule = "본문에 코드가 없으므로 code_blank 유형의 문제는 생성하지 마세요.";
      }

      String systemPrompt =
          promptLoader.loadPrompt("quiz-gen-prompt.txt", Map.of("additional_rule", additionalRule));
      String userPrompt = "<source_content>\n" + articleText + "\n</source_content>";

      String jsonResponse = claudeClient.getGeneratedText(systemPrompt, userPrompt);
      ClaudeQuizResponseDto parsedResponse = parseAndValidate(jsonResponse);

      // 3. 문제 및 선택지 엔티티 저장 및 완료 (Transaction 분리)
      return transactionTemplate.execute(
          status -> {
            QuizSet activeQuizSet = quizSetRepository.findById(quizSet.getId()).orElseThrow();

            int orderNo = 1;
            for (ClaudeQuizResponseDto.ClaudeQuizDto quizDto : parsedResponse.getQuizzes()) {
              QuestionType type;
              try {
                type = QuestionType.valueOf(quizDto.getType().toUpperCase());
              } catch (IllegalArgumentException e) {
                throw new CustomException(
                    QuizErrorCode.QUIZ_GENERATION_FAILED,
                    "알 수 없는 문제 유형입니다: " + quizDto.getType(),
                    e);
              }

              // 사후 검증: 본문에 코드가 없는데 CODE_BLANK 유형의 문제가 생성된 경우 건너뜀
              if (!hasCode && type == QuestionType.CODE_BLANK) {
                log.warn("본문에 코드가 없으므로 CODE_BLANK 유형의 문제를 스킵합니다: {}", quizDto.getQuestion());
                continue;
              }

              // SHORT_ANSWER / CODE_BLANK는 정답이 null이거나 공백이면 거부
              if (type != QuestionType.MULTIPLE_CHOICE) {
                if (quizDto.getAnswer() == null || quizDto.getAnswer().isBlank()) {
                  throw new CustomException(
                      QuizErrorCode.QUIZ_GENERATION_FAILED,
                      type.name() + " 문제의 정답(answer)이 비어있습니다.");
                }
              }

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
                  throw new CustomException(
                      QuizErrorCode.QUIZ_GENERATION_FAILED, "객관식 문제에 선택지가 없습니다.");
                }

                int correctChoiceCount = 0;
                int choiceOrderNo = 1;
                for (String optionText : quizDto.getOptions()) {
                  boolean isCorrect = String.valueOf(choiceOrderNo - 1).equals(quizDto.getAnswer());
                  if (isCorrect) correctChoiceCount++;
                  QuizChoice choice =
                      QuizChoice.create(question, choiceOrderNo++, optionText, isCorrect);
                  quizChoiceRepository.save(choice);
                }
                if (correctChoiceCount != 1) {
                  throw new CustomException(
                      QuizErrorCode.QUIZ_GENERATION_FAILED, "객관식 문제의 정답 개수가 1개가 아닙니다.");
                }
              }
            }

            int generatedQuestionCount = orderNo - 1;
            if (generatedQuestionCount < 1) {
              throw new CustomException(
                  QuizErrorCode.QUIZ_GENERATION_FAILED, "AI가 생성한 퀴즈 문항이 없습니다.");
            }
            activeQuizSet.complete(generatedQuestionCount);
            tagService.saveContentTags(activeQuizSet.getContent(), parsedResponse.getTags());

            return QuizCreateResponse.from(activeQuizSet);
          });

    } catch (Exception e) {
      transactionTemplate.execute(
          status -> {
            QuizSet activeQuizSet = quizSetRepository.findById(quizSet.getId()).orElse(null);
            if (activeQuizSet != null) {
              activeQuizSet.fail();
              quizSetRepository.save(activeQuizSet);
            }
            return null;
          });
      log.error("퀴즈 생성 실패: {}", e.getMessage(), e);
      if (e instanceof CustomException) {
        throw (CustomException) e;
      }
      throw new CustomException(QuizErrorCode.QUIZ_GENERATION_FAILED, "퀴즈 생성 중 오류가 발생했습니다.", e);
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
        throw new CustomException(QuizErrorCode.QUIZ_GENERATION_FAILED, "퀴즈 목록이 비어있습니다.");
      }
      if (response.getQuizzes().size() < 1 || response.getQuizzes().size() > 5) {
        throw new CustomException(
            QuizErrorCode.QUIZ_GENERATION_FAILED, "생성된 문제 수가 1~5개 범위를 벗어납니다.");
      }
      if (response.getTags() == null
          || response.getTags().isEmpty()
          || response.getTags().size() < 1
          || response.getTags().size() > 3) {
        throw new CustomException(
            QuizErrorCode.QUIZ_GENERATION_FAILED, "생성된 태그 수가 1~3개 범위를 벗어나거나 비어있습니다.");
      }

      return response;
    } catch (JsonProcessingException e) {
      throw new CustomException(QuizErrorCode.QUIZ_GENERATION_FAILED, "AI 응답 JSON 파싱에 실패했습니다.", e);
    }
  }
}
