package com.realdev.readle.domain.quiz.service;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.repository.MemberRepository;
import com.realdev.readle.domain.quiz.dto.request.QuizSubmitRequest;
import com.realdev.readle.domain.quiz.dto.response.QuizAttemptResultResponse;
import com.realdev.readle.domain.quiz.dto.response.QuizDetailResponse;
import com.realdev.readle.domain.quiz.dto.response.QuizSubmitResponse;
import com.realdev.readle.domain.quiz.entity.AttemptStatus;
import com.realdev.readle.domain.quiz.entity.QuestionType;
import com.realdev.readle.domain.quiz.entity.QuizAnswer;
import com.realdev.readle.domain.quiz.entity.QuizAttempt;
import com.realdev.readle.domain.quiz.entity.QuizChoice;
import com.realdev.readle.domain.quiz.entity.QuizQuestion;
import com.realdev.readle.domain.quiz.entity.QuizResult;
import com.realdev.readle.domain.quiz.entity.QuizSet;
import com.realdev.readle.domain.quiz.exception.QuizErrorCode;
import com.realdev.readle.domain.quiz.repository.QuizAnswerRepository;
import com.realdev.readle.domain.quiz.repository.QuizAttemptRepository;
import com.realdev.readle.domain.quiz.repository.QuizChoiceRepository;
import com.realdev.readle.domain.quiz.repository.QuizQuestionRepository;
import com.realdev.readle.domain.quiz.repository.QuizResultRepository;
import com.realdev.readle.domain.quiz.repository.QuizSetRepository;
import com.realdev.readle.domain.tag.repository.ContentTagRepository;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizSolveService {

  private final QuizSetRepository quizSetRepository;
  private final QuizAttemptRepository quizAttemptRepository;
  private final QuizQuestionRepository quizQuestionRepository;
  private final QuizChoiceRepository quizChoiceRepository;
  private final QuizAnswerRepository quizAnswerRepository;
  private final QuizResultRepository quizResultRepository;
  private final MemberRepository memberRepository;
  private final ContentTagRepository contentTagRepository;
  private final QuizAiGradingService quizAiGradingService;
  private final TransactionTemplate transactionTemplate;

  @Transactional
  public QuizAttempt startQuiz(Long quizSetId, String memberUuid) {
    QuizSet quizSet =
        quizSetRepository
            .findById(quizSetId)
            .orElseThrow(() -> new CustomException(QuizErrorCode.QUIZ_NOT_FOUND));

    if (quizSet.getStatus() != com.realdev.readle.domain.quiz.entity.QuizSetStatus.COMPLETED) {
      throw new CustomException(QuizErrorCode.QUIZ_NOT_COMPLETED);
    }

    if (!quizSet.getContent().getMember().getUuid().equals(memberUuid)) {
      throw new CustomException(GlobalErrorCode.FORBIDDEN, "해당 퀴즈에 대한 접근 권한이 없습니다.");
    }

    Member member =
        memberRepository
            .findByUuid(memberUuid)
            .orElseThrow(() -> new CustomException(GlobalErrorCode.NOT_FOUND, "존재하지 않는 사용자입니다."));

    QuizAttempt attempt = QuizAttempt.createInProgress(quizSet, member);
    return quizAttemptRepository.save(attempt);
  }

  @Transactional(readOnly = true)
  public QuizDetailResponse getQuizAttemptDetail(Long attemptId, String memberUuid) {
    QuizAttempt attempt =
        quizAttemptRepository
            .findById(attemptId)
            .orElseThrow(() -> new CustomException(QuizErrorCode.ATTEMPT_NOT_FOUND));

    if (!attempt.getMember().getUuid().equals(memberUuid)) {
      throw new CustomException(GlobalErrorCode.FORBIDDEN, "해당 풀이 정보에 대한 접근 권한이 없습니다.");
    }

    List<QuizQuestion> questions =
        quizQuestionRepository.findByQuizSetOrderByOrderNoAsc(attempt.getQuizSet());
    List<QuizChoice> allChoices = quizChoiceRepository.findByQuizQuestionIn(questions);

    return QuizDetailResponse.of(attempt, questions, allChoices);
  }

  public QuizSubmitResponse submitAnswers(
      Long attemptId, String memberUuid, QuizSubmitRequest request) {

    // [가드레일 선행 검증] 비관적 락 및 상태 전이(GRADING) 트랜잭션 전에 입력값 유효성 전면 검사
    QuizAttempt preAttempt =
        quizAttemptRepository
            .findWithDetailsById(attemptId)
            .orElseThrow(() -> new CustomException(QuizErrorCode.ATTEMPT_NOT_FOUND));

    if (!preAttempt.getMember().getUuid().equals(memberUuid)) {
      throw new CustomException(GlobalErrorCode.FORBIDDEN, "해당 풀이 정보에 대한 권한이 없습니다.");
    }

    if (preAttempt.getStatus() != com.realdev.readle.domain.quiz.entity.AttemptStatus.IN_PROGRESS) {
      throw new CustomException(QuizErrorCode.ATTEMPT_ALREADY_SUBMITTED);
    }

    List<QuizQuestion> questions =
        quizQuestionRepository.findByQuizSetOrderByOrderNoAsc(preAttempt.getQuizSet());

    if (questions.size() != request.getAnswers().size()) {
      throw new CustomException(QuizErrorCode.INVALID_ANSWER_COUNT);
    }

    Map<Long, QuizSubmitRequest.AnswerRequest> answerMap = new HashMap<>();
    for (QuizSubmitRequest.AnswerRequest a : request.getAnswers()) {
      if (answerMap.containsKey(a.getQuestionId())) {
        throw new CustomException(
            GlobalErrorCode.INVALID_INPUT, "중복된 문제 ID가 존재합니다: " + a.getQuestionId());
      }
      answerMap.put(a.getQuestionId(), a);
    }

    // 주관식/빈칸용 본문 텍스트 확인 (트랜잭션 시작 전 차단)
    String articleText = "";
    if (questions.stream().anyMatch(q -> q.getQuestionType() != QuestionType.MULTIPLE_CHOICE)) {
      String raw = preAttempt.getQuizSet().getContent().getRawText();
      String extracted = preAttempt.getQuizSet().getContent().getExtractedText();
      articleText = raw != null ? raw : (extracted != null ? extracted : "");

      if (articleText.isBlank()) {
        throw new CustomException(QuizErrorCode.EMPTY_ARTICLE_TEXT);
      }
    }

    // 선택지 조회 및 주관식 가드레일 사전 검증 수행
    Map<Long, QuizChoice> choiceMap = new HashMap<>();
    for (QuizQuestion question : questions) {
      QuizSubmitRequest.AnswerRequest answerReq = answerMap.get(question.getId());
      if (answerReq == null) {
        throw new CustomException(QuizErrorCode.INVALID_ANSWER_FORMAT);
      }

      if (question.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
        if (answerReq.getSubmittedChoiceId() == null) {
          throw new CustomException(QuizErrorCode.INVALID_ANSWER_FORMAT);
        }
        QuizChoice choice =
            quizChoiceRepository
                .findById(answerReq.getSubmittedChoiceId())
                .orElseThrow(
                    () -> new CustomException(GlobalErrorCode.NOT_FOUND, "존재하지 않는 선택지입니다."));
        if (!choice.getQuizQuestion().getId().equals(question.getId())) {
          throw new CustomException(QuizErrorCode.INVALID_ANSWER_FORMAT);
        }
        choiceMap.put(question.getId(), choice);
      } else {
        String answerText = answerReq.getSubmittedAnswerText();
        if (answerText == null || answerText.trim().isEmpty()) {
          throw new CustomException(QuizErrorCode.INVALID_ANSWER_FORMAT);
        }
        // 주관식/빈칸 답안 검증 (트랜잭션 시작 전 차단)
        if (answerText.length() > 100) {
          throw new CustomException(QuizErrorCode.INVALID_ANSWER_FORMAT);
        }
        if (answerText.matches("(?is).*(이전 지시 무시|시스템 프롬프트|system prompt|ignore previous).*")) {
          throw new CustomException(QuizErrorCode.INVALID_ANSWER_FORMAT);
        }
      }
    }

    // 1. Transaction 1: 비관적 락 획득 및 GRADING 상태 변경 (Race Condition 차단)
    QuizAttempt lockedAttempt =
        transactionTemplate.execute(
            status -> {
              QuizAttempt attempt =
                  quizAttemptRepository
                      .findByIdForUpdate(attemptId)
                      .orElseThrow(() -> new CustomException(QuizErrorCode.ATTEMPT_NOT_FOUND));

              if (!attempt.getMember().getUuid().equals(memberUuid)) {
                throw new CustomException(GlobalErrorCode.FORBIDDEN, "해당 풀이 정보에 대한 권한이 없습니다.");
              }

              try {
                attempt.markAsGrading();
              } catch (IllegalStateException e) {
                throw new CustomException(QuizErrorCode.ATTEMPT_ALREADY_SUBMITTED);
              }
              return attempt;
            });

    List<QuizAnswer> staticAnswers = new ArrayList<>();
    List<CompletableFuture<QuizAiGradingService.AiEvaluationResult>> aiTasks = new ArrayList<>();

    for (QuizQuestion question : questions) {
      QuizSubmitRequest.AnswerRequest answerReq = answerMap.get(question.getId());
      if (question.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
        QuizChoice choice = choiceMap.get(question.getId());
        staticAnswers.add(
            QuizAnswer.createForChoice(lockedAttempt, question, choice, choice.getIsCorrect()));
      } else {
        String answerText = answerReq.getSubmittedAnswerText();
        if (isStaticMatch(question.getCorrectAnswer(), answerText)) {
          staticAnswers.add(
              QuizAnswer.createForWritten(lockedAttempt, question, answerText, true, null));
        } else {
          aiTasks.add(quizAiGradingService.gradeAnswerAsync(question, answerText, articleText));
        }
      }
    }

    // 2. Non-Transactional: 비동기 채점 대기
    List<QuizAnswer> aiAnswers = new ArrayList<>();
    if (!aiTasks.isEmpty()) {
      CompletableFuture.allOf(aiTasks.toArray(new CompletableFuture[0])).join();
      for (CompletableFuture<QuizAiGradingService.AiEvaluationResult> taskFuture : aiTasks) {
        QuizAiGradingService.AiEvaluationResult aiResult = taskFuture.join();
        aiAnswers.add(
            QuizAnswer.createForWritten(
                lockedAttempt,
                aiResult.question(),
                aiResult.submittedAnswer(),
                aiResult.isCorrect(),
                aiResult.aiFeedback()));
      }
    }

    // 3. Transaction 2: 최종 저장 및 SUBMITTED 처리
    try {
      return transactionTemplate.execute(
          status -> {
            QuizAttempt activeAttempt = quizAttemptRepository.findById(attemptId).orElseThrow();

            quizAnswerRepository.saveAll(staticAnswers);
            quizAnswerRepository.saveAll(aiAnswers);

            int correctCount =
                (int)
                    Stream.concat(staticAnswers.stream(), aiAnswers.stream())
                        .filter(QuizAnswer::getIsCorrect)
                        .count();

            activeAttempt.submit();

            int solveDurationSeconds =
                (int)
                    java.time.Duration.between(
                            activeAttempt.getStartedAt(), activeAttempt.getSubmittedAt())
                        .getSeconds();

            QuizResult result =
                QuizResult.create(
                    activeAttempt, correctCount, questions.size(), solveDurationSeconds);
            quizResultRepository.save(result);

            return QuizSubmitResponse.from(result, staticAnswers, aiAnswers);
          });
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(QuizErrorCode.ATTEMPT_ALREADY_SUBMITTED);
    }
  }

  @Transactional(readOnly = true)
  public QuizAttemptResultResponse getAttemptResult(String memberUuid, Long attemptId) {
    QuizAttempt quizAttempt =
        quizAttemptRepository
            .findById(attemptId)
            .orElseThrow(() -> new CustomException(QuizErrorCode.ATTEMPT_NOT_FOUND));

    validateAttemptAccess(quizAttempt, memberUuid);

    QuizResult quizResult =
        quizResultRepository
            .findByQuizAttemptId(attemptId)
            .orElseThrow(
                () ->
                    new CustomException(
                        GlobalErrorCode.SERVER_ERROR, "해당 시도의 채점 결과 데이터가 존재하지 않습니다."));

    return buildAttemptResult(quizAttempt, quizResult);
  }

  @Transactional(readOnly = true)
  public QuizAttemptResultResponse getResultReport(String memberUuid, Long reportId) {
    QuizResult quizResult =
        quizResultRepository
            .findById(reportId)
            .orElseThrow(() -> new CustomException(QuizErrorCode.RESULT_REPORT_NOT_FOUND));
    QuizAttempt quizAttempt = quizResult.getQuizAttempt();

    validateAttemptAccess(quizAttempt, memberUuid);
    return buildAttemptResult(quizAttempt, quizResult);
  }

  private void validateAttemptAccess(QuizAttempt quizAttempt, String memberUuid) {
    if (!quizAttempt.getMember().getUuid().equals(memberUuid)) {
      throw new CustomException(QuizErrorCode.FORBIDDEN_ACCESS);
    }

    if (quizAttempt.getStatus() != AttemptStatus.SUBMITTED) {
      throw new CustomException(QuizErrorCode.ATTEMPT_NOT_SUBMITTED);
    }
  }

  private QuizAttemptResultResponse buildAttemptResult(
      QuizAttempt quizAttempt, QuizResult quizResult) {
    Long attemptId = quizAttempt.getId();
    List<QuizAnswer> quizAnswers =
        quizAnswerRepository.findByQuizAttemptIdWithQuestionAndChoice(attemptId);

    Long quizSetId = quizAttempt.getQuizSet().getId();
    String title = quizAttempt.getQuizSet().getContent().getTitle();
    Long contentId = quizAttempt.getQuizSet().getContent().getId();
    List<String> tags =
        contentTagRepository.findByContentIdWithTag(contentId).stream()
            .map(ct -> ct.getTag().getName())
            .toList();

    return QuizAttemptResultResponse.from(
        quizResult, quizAnswers, title, tags, quizSetId, attemptId);
  }

  private boolean isStaticMatch(String correct, String submitted) {
    if (correct == null || submitted == null) {
      return false;
    }
    String normalizedCorrect = correct.trim().toLowerCase().replaceAll("\\s+", " ");
    String normalizedSubmitted = submitted.trim().toLowerCase().replaceAll("\\s+", " ");
    return normalizedCorrect.equals(normalizedSubmitted);
  }
}
