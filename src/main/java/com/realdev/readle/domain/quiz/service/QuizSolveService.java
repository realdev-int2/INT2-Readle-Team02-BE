package com.realdev.readle.domain.quiz.service;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.repository.MemberRepository;
import com.realdev.readle.domain.quiz.dto.QuizDetailResponse;
import com.realdev.readle.domain.quiz.dto.QuizSubmitRequest;
import com.realdev.readle.domain.quiz.dto.QuizSubmitResponse;
import com.realdev.readle.domain.quiz.entity.QuestionType;
import com.realdev.readle.domain.quiz.entity.QuizAnswer;
import com.realdev.readle.domain.quiz.entity.QuizAttempt;
import com.realdev.readle.domain.quiz.entity.QuizChoice;
import com.realdev.readle.domain.quiz.entity.QuizQuestion;
import com.realdev.readle.domain.quiz.entity.QuizResult;
import com.realdev.readle.domain.quiz.entity.QuizSet;
import com.realdev.readle.domain.quiz.repository.QuizAnswerRepository;
import com.realdev.readle.domain.quiz.repository.QuizAttemptRepository;
import com.realdev.readle.domain.quiz.repository.QuizChoiceRepository;
import com.realdev.readle.domain.quiz.repository.QuizQuestionRepository;
import com.realdev.readle.domain.quiz.repository.QuizResultRepository;
import com.realdev.readle.domain.quiz.repository.QuizSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Transactional
    public Long startQuiz(Long quizSetId, Long memberId) {
        QuizSet quizSet = quizSetRepository.findById(quizSetId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 퀴즈 세트입니다."));

        // 권한 검증: quizSet의 content 작성자와 memberId가 일치하는지 확인
        if (!quizSet.getContent().getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("해당 퀴즈에 대한 접근 권한이 없습니다.");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        QuizAttempt attempt = QuizAttempt.createInProgress(quizSet, member);
        quizAttemptRepository.save(attempt);

        return attempt.getId();
    }

    @Transactional(readOnly = true)
    public QuizDetailResponse getQuizAttemptDetail(Long attemptId, Long memberId) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 풀이 시도입니다."));

        if (!attempt.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("해당 풀이 정보에 대한 접근 권한이 없습니다.");
        }

        List<QuizQuestion> questions = quizQuestionRepository.findByQuizSetOrderByOrderNoAsc(attempt.getQuizSet());
        List<QuizChoice> allChoices = quizChoiceRepository.findByQuizQuestionIn(questions);

        return QuizDetailResponse.of(attempt, questions, allChoices);
    }

    @Transactional
    public QuizSubmitResponse submitAnswers(Long attemptId, Long memberId, QuizSubmitRequest request) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 풀이 시도입니다."));

        if (!attempt.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("해당 풀이 정보에 대한 권한이 없습니다.");
        }

        if (attempt.getStatus() != com.realdev.readle.domain.quiz.entity.AttemptStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("이미 제출이 완료되었거나 진행 중이 아닌 풀이입니다.");
        }

        List<QuizQuestion> questions = quizQuestionRepository.findByQuizSetOrderByOrderNoAsc(attempt.getQuizSet());

        // 문제 수 확인
        if (questions.size() != request.getAnswers().size()) {
            throw new IllegalArgumentException("답안의 개수가 퀴즈 세트의 문제 개수와 일치하지 않습니다.");
        }

        Map<Long, QuizSubmitRequest.AnswerRequest> answerMap = request.getAnswers().stream()
                .collect(Collectors.toMap(QuizSubmitRequest.AnswerRequest::getQuestionId, a -> a));

        int correctCount = 0;

        for (QuizQuestion question : questions) {
            QuizSubmitRequest.AnswerRequest answerReq = answerMap.get(question.getId());
            if (answerReq == null) {
                throw new IllegalArgumentException("문제 ID " + question.getId() + "에 대한 답안이 누락되었습니다.");
            }

            if (question.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
                if (answerReq.getSubmittedChoiceId() == null) {
                    throw new IllegalArgumentException("객관식 문제는 선택지 ID를 제출해야 합니다.");
                }

                QuizChoice choice = quizChoiceRepository.findById(answerReq.getSubmittedChoiceId())
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 선택지입니다."));

                if (!choice.getQuizQuestion().getId().equals(question.getId())) {
                    throw new IllegalArgumentException("선택한 답안이 해당 문제에 속하지 않습니다.");
                }

                boolean isCorrect = choice.getIsCorrect();
                if (isCorrect) {
                    correctCount++;
                }

                QuizAnswer quizAnswer = QuizAnswer.createForChoice(attempt, question, choice, isCorrect);
                quizAnswerRepository.save(quizAnswer);

            } else {
                // 주관식/코드 빈칸 로직
                if (answerReq.getSubmittedAnswerText() == null || answerReq.getSubmittedAnswerText().trim().isEmpty()) {
                    throw new IllegalArgumentException("주관식 또는 코드 빈칸 문제는 텍스트 답안을 제출해야 합니다.");
                }

                // TODO: AI 채점 로직 호출 (현재는 Mocking 처리. 실제로는 Batch AI 채점 연동 필요)
                boolean isCorrect = evaluateWrittenAnswerMock(question, answerReq.getSubmittedAnswerText());
                String aiFeedback = isCorrect ? null : "오답입니다. (Mock AI 피드백)";

                if (isCorrect) {
                    correctCount++;
                }

                QuizAnswer quizAnswer = QuizAnswer.createForWritten(attempt, question, answerReq.getSubmittedAnswerText(), isCorrect, aiFeedback);
                quizAnswerRepository.save(quizAnswer);
            }
        }

        attempt.submit();

        int solveDurationSeconds = (int) java.time.Duration.between(attempt.getStartedAt(), attempt.getSubmittedAt()).getSeconds();

        QuizResult result = QuizResult.create(attempt, correctCount, questions.size(), solveDurationSeconds);
        quizResultRepository.save(result);

        return QuizSubmitResponse.from(result);
    }

    // AI 채점을 대체하는 임시 메서드. 향후 Claude API 하이브리드 채점 로직으로 대체 예정
    private boolean evaluateWrittenAnswerMock(QuizQuestion question, String submittedAnswer) {
        // 임시 채점 로직: 정답(correct_answer)과 공백 무시/대소문자 무시 일치 여부로 채점
        if (question.getCorrectAnswer() == null) {
            return false;
        }
        String correct = question.getCorrectAnswer().replaceAll("\\s+", "").toLowerCase();
        String submitted = submittedAnswer.replaceAll("\\s+", "").toLowerCase();
        return submitted.contains(correct);
    }
}
