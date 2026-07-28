package com.realdev.readle.domain.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.realdev.readle.domain.content.entity.ContentValidation;
import com.realdev.readle.domain.content.entity.ValidationMethod;
import com.realdev.readle.domain.content.entity.ValidationStatus;
import com.realdev.readle.domain.content.repository.ContentValidationRepository;
import com.realdev.readle.domain.quiz.dto.ClaudeQuizResponseDto;
import com.realdev.readle.domain.quiz.dto.response.ClaudeQualityVerifyResponseDto;
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
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.infrastructure.ai.ClaudeClient;
import com.realdev.readle.global.infrastructure.ai.ClaudeTemplate;
import com.realdev.readle.global.infrastructure.prompt.PromptLoader;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizGenerationService {

  private static final String QUIZ_GENERATION = "readle.quiz.generation";
  private static final String QUIZ_GENERATION_RETRIES = "readle.quiz.generation.retries";
  private static final String CONTENT_VALIDATION_BYPASSES = "readle.content.validation.bypasses";

  private final ContentValidationRepository contentValidationRepository;
  private final QuizSetRepository quizSetRepository;
  private final QuizQuestionRepository quizQuestionRepository;
  private final QuizChoiceRepository quizChoiceRepository;
  private final ClaudeClient claudeClient;
  private final ClaudeTemplate claudeTemplate;
  private final PromptLoader promptLoader;
  private final TagService tagService;
  private final MeterRegistry meterRegistry;
  private final QuizQualityGuard quizQualityGuard;

  private final TransactionTemplate transactionTemplate;

  @Qualifier("claudeCallExecutor") private final Executor claudeCallExecutor;

  public QuizCreateResponse createQuizSet(Long sourceValidationId) {
    Timer.Sample sample = Timer.start(meterRegistry);
    ContentValidation validation;
    QuizSetStart quizSetStart;
    try {
      validation =
          contentValidationRepository
              .findByIdWithContent(sourceValidationId)
              .orElseThrow(
                  () ->
                      new CustomException(
                          QuizErrorCode.SOURCE_VALIDATION_NOT_FOUND, "존재하지 않는 검증 ID입니다."));

      // 1. 초기 QuizSet 레코드 생성 및 저장 (Transaction 분리)
      quizSetStart =
          transactionTemplate.execute(
              status -> {
                QuizSet existing;
                try {
                  existing =
                      quizSetRepository
                          .findForUpdateBySourceValidationId(sourceValidationId)
                          .orElse(null);
                } catch (org.springframework.dao.PessimisticLockingFailureException e) {
                  throw new CustomException(
                      QuizErrorCode.QUIZ_GENERATION_IN_PROGRESS,
                      "이미 해당 콘텐츠에 대한 퀴즈 생성 요청이 진행 중이거나 완료되었습니다.",
                      e);
                }

                if (existing != null) {
                  if (existing.getStatus() == QuizSetStatus.FAILED) {
                    existing.retry();
                    quizChoiceRepository.deleteByQuizSetId(existing.getId());
                    quizQuestionRepository.deleteByQuizSetId(existing.getId());
                    return new QuizSetStart(quizSetRepository.saveAndFlush(existing), true);
                  } else {
                    throw new CustomException(
                        QuizErrorCode.QUIZ_GENERATION_IN_PROGRESS,
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
                                    QuizErrorCode.SOURCE_VALIDATION_NOT_FOUND,
                                    "존재하지 않는 검증 ID입니다."));

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
                  return new QuizSetStart(quizSetRepository.saveAndFlush(newQuizSet), false);
                } catch (DataIntegrityViolationException e) {
                  throw new CustomException(
                      QuizErrorCode.QUIZ_GENERATION_IN_PROGRESS,
                      "이미 해당 콘텐츠에 대한 퀴즈 생성 요청이 진행 중이거나 완료되었습니다.",
                      e);
                }
              });
    } catch (RuntimeException e) {
      sample.stop(Timer.builder(QUIZ_GENERATION).tag("outcome", "failure").register(meterRegistry));
      throw e;
    }
    QuizSet quizSet = quizSetStart.quizSet();
    if (quizSetStart.retry()) {
      Counter.builder(QUIZ_GENERATION_RETRIES).register(meterRegistry).increment();
    }
    String outcome = "failure";
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

      ClaudeQuizResponseDto parsedResponse =
          claudeTemplate.executeSync(
              () -> claudeClient.getGeneratedText(systemPrompt, userPrompt),
              ClaudeQuizResponseDto.class,
              res -> validateGeneratedQuizRules(res),
              60L,
              claudeCallExecutor,
              QUIZ_GENERATION,
              this::mapToQuizGenerationException);

      // 퀴즈 사후 품질 검증 (정답 노출 스크리닝 및 단건 타겟 재생성)
      sanitizeAndFilterQuizzes(parsedResponse, articleText);

      // 3. 문제 및 선택지 엔티티 저장 및 완료 (Transaction 분리)
      QuizCreateResponse response =
          transactionTemplate.execute(
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
                      boolean isCorrect =
                          String.valueOf(choiceOrderNo - 1).equals(quizDto.getAnswer());
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
      outcome = "success";
      if (Boolean.TRUE.equals(quizSet.getIsBypassed())) {
        Counter.builder(CONTENT_VALIDATION_BYPASSES).register(meterRegistry).increment();
      }
      return response;

    } catch (RuntimeException e) {
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
    } finally {
      sample.stop(Timer.builder(QUIZ_GENERATION).tag("outcome", outcome).register(meterRegistry));
    }
  }

  private record QuizSetStart(QuizSet quizSet, boolean retry) {}

  private void validateGeneratedQuizRules(ClaudeQuizResponseDto response) {
    if (response.getQuizzes() == null || response.getQuizzes().isEmpty()) {
      throw new CustomException(QuizErrorCode.QUIZ_GENERATION_FAILED, "퀴즈 목록이 비어있습니다.");
    }
    if (response.getQuizzes().size() > 5) {
      throw new CustomException(QuizErrorCode.QUIZ_GENERATION_FAILED, "생성된 문제 수가 1~5개 범위를 벗어납니다.");
    }
    if (response.getTags() == null
        || response.getTags().isEmpty()
        || response.getTags().size() > 3) {
      throw new CustomException(
          QuizErrorCode.QUIZ_GENERATION_FAILED, "생성된 태그 수가 1~3개 범위를 벗어나거나 비어있습니다.");
    }
  }

  private RuntimeException mapToQuizGenerationException(Throwable e) {
    Throwable cause = (e instanceof CustomException && e.getCause() != null) ? e.getCause() : e;

    if (e instanceof CustomException) {
      if (((CustomException) e).getErrorCode() == GlobalErrorCode.AI_PARSING_ERROR) {
        return new CustomException(
            QuizErrorCode.QUIZ_GENERATION_FAILED, "AI 응답 JSON 파싱에 실패했습니다.", cause);
      }
      return (CustomException) e;
    }
    if (e instanceof TimeoutException) {
      return new CustomException(QuizErrorCode.QUIZ_TIMEOUT, "AI 타임아웃이 발생했습니다.", cause);
    }
    if (e instanceof JsonProcessingException || e.getCause() instanceof JsonProcessingException) {
      return new CustomException(
          QuizErrorCode.QUIZ_GENERATION_FAILED, "AI 응답 JSON 파싱에 실패했습니다.", cause);
    }
    return new CustomException(QuizErrorCode.QUIZ_GENERATION_FAILED, "퀴즈 생성 중 오류가 발생했습니다.", cause);
  }

  private void sanitizeAndFilterQuizzes(ClaudeQuizResponseDto parsedResponse, String articleText) {
    if (parsedResponse == null || parsedResponse.getQuizzes() == null) {
      return;
    }

    List<ClaudeQuizResponseDto.ClaudeQuizDto> quizList = parsedResponse.getQuizzes();
    boolean[] hasLeak = new boolean[quizList.size()];
    List<QuizQualityGuard.QuestionInspectItem> nonLeakingItems = new ArrayList<>();

    // 1차: 모든 문항 0ms 정규식 스크리닝
    for (int i = 0; i < quizList.size(); i++) {
      ClaudeQuizResponseDto.ClaudeQuizDto quizDto = quizList.get(i);
      String answerText = getAnswerText(quizDto);
      if (quizQualityGuard.checkRegexLeak(quizDto.getQuestion(), answerText)) {
        hasLeak[i] = true;
      } else {
        nonLeakingItems.add(
            new QuizQualityGuard.QuestionInspectItem(i + 1, quizDto.getQuestion(), answerText));
      }
    }

    // 2차: 정규식 통과 문항들에 대해 1회 배치 LLM 의미 검증기 호출
    if (!nonLeakingItems.isEmpty()) {
      List<ClaudeQualityVerifyResponseDto.QuestionVerifyResult> verifyResults =
          quizQualityGuard.verifyWithLlm(nonLeakingItems);
      if (verifyResults != null) {
        for (ClaudeQualityVerifyResponseDto.QuestionVerifyResult res : verifyResults) {
          if (res.hasLeak() && res.index() >= 1 && res.index() <= quizList.size()) {
            hasLeak[res.index() - 1] = true;
          }
        }
      }
    }

    // 3차: 누출 탐지 문항 단건 타겟 재생성 또는 세트 제외 처리
    List<ClaudeQuizResponseDto.ClaudeQuizDto> validQuizzes = new ArrayList<>();
    for (int i = 0; i < quizList.size(); i++) {
      ClaudeQuizResponseDto.ClaudeQuizDto quizDto = quizList.get(i);
      if (hasLeak[i]) {
        log.warn(
            "[QUIZ_QUALITY_GUARD] 문항 본문에 정답/힌트 누출이 탐지되었습니다. 단건 타겟 재생성을 시도합니다: {}",
            quizDto.getQuestion());

        ClaudeQuizResponseDto.ClaudeQuizDto regenerated = retrySingleQuestion(quizDto, articleText);
        if (regenerated != null) {
          validQuizzes.add(regenerated);
        } else {
          log.error(
              "[QUIZ_QUALITY_GUARD] 단건 재생성 2회 실패로 해당 문항을 세트에서 제외합니다: {}", quizDto.getQuestion());
        }
      } else {
        validQuizzes.add(quizDto);
      }
    }

    parsedResponse.setQuizzes(validQuizzes);
  }

  private String getAnswerText(ClaudeQuizResponseDto.ClaudeQuizDto quizDto) {
    if ("MULTIPLE_CHOICE".equalsIgnoreCase(quizDto.getType())) {
      try {
        int idx = Integer.parseInt(quizDto.getAnswer());
        if (quizDto.getOptions() != null && idx >= 0 && idx < quizDto.getOptions().size()) {
          return quizDto.getOptions().get(idx);
        }
      } catch (Exception e) {
        // Fallback to raw answer string
      }
    }
    return quizDto.getAnswer();
  }

  private ClaudeQuizResponseDto.ClaudeQuizDto retrySingleQuestion(
      ClaudeQuizResponseDto.ClaudeQuizDto targetQuiz, String articleText) {
    String answerText = getAnswerText(targetQuiz);

    String cleanPrevQuestion =
        targetQuiz.getQuestion() != null
            ? targetQuiz.getQuestion().replaceAll("(?i)</?\\s*previous_question\\s*>", "")
            : "";
    String cleanPrevAnswer =
        answerText != null ? answerText.replaceAll("(?i)</?\\s*previous_answer\\s*>", "") : "";

    String hintRule =
        "이전 생성 문항 본문에 정답 또는 정답의 풀네임/유사 표현이 유출되었습니다.\n"
            + "<previous_question>\n"
            + cleanPrevQuestion
            + "\n</previous_question>\n"
            + "<previous_answer>\n"
            + cleanPrevAnswer
            + "\n</previous_answer>\n"
            + "위 문항 본문에서 정답 단어 및 풀네임/약어 암시 표현을 완벽히 제거하고 순수 문맥 문제로 단건 재구성하세요.";

    String systemPrompt =
        promptLoader.loadPrompt("quiz-gen-prompt.txt", Map.of("additional_rule", hintRule));
    String userPrompt =
        "<source_content>\n" + articleText + "\n</source_content>\nTarget question count: 1";

    for (int retry = 0; retry < 2; retry++) {
      try {
        ClaudeQuizResponseDto parsed =
            claudeTemplate.executeSync(
                () -> claudeClient.getGeneratedText(systemPrompt, userPrompt),
                ClaudeQuizResponseDto.class,
                res -> validateGeneratedQuizRules(res),
                60L,
                claudeCallExecutor,
                QUIZ_GENERATION,
                this::mapToQuizGenerationException);
        if (parsed != null && parsed.getQuizzes() != null && !parsed.getQuizzes().isEmpty()) {
          ClaudeQuizResponseDto.ClaudeQuizDto candidate = parsed.getQuizzes().get(0);
          String candidateAnswer = getAnswerText(candidate);

          boolean regexLeak =
              quizQualityGuard.checkRegexLeak(candidate.getQuestion(), candidateAnswer);
          if (!regexLeak) {
            List<ClaudeQualityVerifyResponseDto.QuestionVerifyResult> verifyResults =
                quizQualityGuard.verifyWithLlm(
                    List.of(
                        new QuizQualityGuard.QuestionInspectItem(
                            1, candidate.getQuestion(), candidateAnswer)));
            if (verifyResults == null
                || verifyResults.isEmpty()
                || !verifyResults.get(0).hasLeak()) {
              log.info("[QUIZ_QUALITY_GUARD] 타겟 단건 재생성 및 재검증 성공");
              return candidate;
            }
          }
        }
      } catch (Exception e) {
        log.warn(
            "[QUIZ_QUALITY_GUARD] 타겟 단건 재생성 중 예외 발생 (retry={}): {}", retry + 1, e.getMessage());
      }
    }
    return null;
  }
}
