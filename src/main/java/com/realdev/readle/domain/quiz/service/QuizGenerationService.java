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
import com.realdev.readle.domain.quiz.exception.QuizGenerationException;
import com.realdev.readle.domain.quiz.exception.ValidationNotPassedException;
import com.realdev.readle.domain.quiz.repository.QuizChoiceRepository;
import com.realdev.readle.domain.quiz.repository.QuizQuestionRepository;
import com.realdev.readle.domain.quiz.repository.QuizSetRepository;
import com.realdev.readle.global.infrastructure.ai.ClaudeClient;
import com.realdev.readle.global.infrastructure.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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

    @Transactional
    public QuizCreateResponse createQuizSet(Long sourceValidationId) {
        ContentValidation validation = contentValidationRepository.findById(sourceValidationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 검증 ID입니다."));

        // 우회 생성(bypass) 플래그 여부 확인 로직
        // 요구사항: PASSED이거나 우회 생성(REJECTED 상태에서 Bypass) 가능
        boolean isBypassed = false;
        if (validation.getStatus() != ValidationStatus.PASSED) {
            // MVP 요건에 맞게, 여기서 우회를 위한 외부 파라미터나 상태값을 판단할 수 있습니다.
            // 일단 현재 설계상 PASSED가 아니면 생성 불가 원칙 (Bypass 플래그는 여기서 별도 판단 필요)
            // 기본 방어로 FAILED나 PENDING은 차단합니다.
            if (validation.getStatus() == ValidationStatus.FAILED || validation.getStatus() == ValidationStatus.PENDING) {
                throw new ValidationNotPassedException("해당 콘텐츠는 퀴즈 생성이 불가능한 상태입니다.");
            }
            // REJECTED인 경우 isBypassed를 true로 둡니다.
            isBypassed = true;
        }

        // 1. 초기 QuizSet 레코드 생성 및 저장 (Unique Constraint로 동시성 방어)
        QuizSet quizSet;
        try {
            quizSet = QuizSet.create(validation.getContent(), validation, isBypassed);
            quizSet = quizSetRepository.saveAndFlush(quizSet); // flush to trigger constraint check
        } catch (DataIntegrityViolationException e) {
            throw new QuizGenerationException("이미 해당 콘텐츠에 대한 퀴즈 생성 요청이 진행 중이거나 완료되었습니다.");
        }

        // 2. AI Prompt 생성 및 호출
        String articleText = validation.getContent().getRawText() != null ? 
                             validation.getContent().getRawText() : 
                             validation.getContent().getExtractedText();
        
        // 방어 로직: 텍스트가 없는 경우
        if (articleText == null || articleText.isBlank()) {
            throw new ValidationNotPassedException("퀴즈를 생성할 본문 텍스트가 존재하지 않습니다.");
        }
        
        // Dynamic Rule: 코드가 본문에 없으면 code_blank 제외
        String additionalRule = "";
        if (!articleText.contains("{") && !articleText.contains("=") && !articleText.contains(";") && !articleText.contains("public") && !articleText.contains("function")) {
             additionalRule = "본문에 코드가 없으므로 code_blank 유형의 문제는 생성하지 마세요.";
        }

        String systemPrompt = promptLoader.loadPrompt("quiz-gen-prompt.txt", Map.of(
                "additional_rule", additionalRule
        ));
        
        // XML 태그로 감싸서 인젝션 방어
        String userPrompt = "<source_content>\n" + articleText + "\n</source_content>";

        try {
            String jsonResponse = claudeClient.getGeneratedText(systemPrompt, userPrompt);
            
            // 3. JSON Parsing 및 2차 정합성 검증
            ClaudeQuizResponseDto parsedResponse = parseAndValidate(jsonResponse);

            // 4. 문제 및 선택지 엔티티 저장
            int orderNo = 1;
            for (ClaudeQuizResponseDto.ClaudeQuizDto quizDto : parsedResponse.getQuizzes()) {
                QuestionType type = QuestionType.valueOf(quizDto.getType().toUpperCase());
                
                QuizQuestion question = QuizQuestion.create(
                        quizSet,
                        orderNo++,
                        type,
                        quizDto.getQuestion(),
                        quizDto.getCodeSnippet(),
                        type == QuestionType.MULTIPLE_CHOICE ? null : quizDto.getAnswer(),
                        null,
                        null
                );
                quizQuestionRepository.save(question);

                if (type == QuestionType.MULTIPLE_CHOICE) {
                    if (quizDto.getOptions() == null || quizDto.getOptions().isEmpty()) {
                        throw new QuizGenerationException("객관식 문제에 선택지가 없습니다.");
                    }
                    int choiceOrderNo = 1;
                    for (String optionText : quizDto.getOptions()) {
                        // 정답 인덱스가 answer 필드에 문자열 숫자로 옴 (0부터 시작)
                        boolean isCorrect = String.valueOf(choiceOrderNo - 1).equals(quizDto.getAnswer());
                        QuizChoice choice = QuizChoice.create(
                                question,
                                choiceOrderNo++,
                                optionText,
                                isCorrect
                        );
                        quizChoiceRepository.save(choice);
                    }
                }
            }

            // 5. 완료 처리
            quizSet.complete(parsedResponse.getQuizzes().size());
            return QuizCreateResponse.from(quizSet);
            
        } catch (Exception e) {
            quizSet.fail();
            log.error("퀴즈 생성 실패: {}", e.getMessage(), e);
            throw new QuizGenerationException("퀴즈 생성 중 오류가 발생했습니다.", e);
        }
    }

    private ClaudeQuizResponseDto parseAndValidate(String jsonResponse) {
        try {
            // 마크다운 블록 제거 처리 (혹시 모를 ```json 태그)
            if (jsonResponse.startsWith("```json")) {
                jsonResponse = jsonResponse.substring(7);
                if (jsonResponse.endsWith("```")) {
                    jsonResponse = jsonResponse.substring(0, jsonResponse.length() - 3);
                }
            }
            jsonResponse = jsonResponse.trim();

            ClaudeQuizResponseDto response = objectMapper.readValue(jsonResponse, ClaudeQuizResponseDto.class);

            if (response.getQuizzes() == null || response.getQuizzes().isEmpty()) {
                throw new QuizGenerationException("퀴즈 목록이 비어있습니다.");
            }
            if (response.getQuizzes().size() < 1 || response.getQuizzes().size() > 5) {
                throw new QuizGenerationException("생성된 문제 수가 1~5개 범위를 벗어납니다.");
            }

            return response;
        } catch (JsonProcessingException e) {
            throw new QuizGenerationException("AI 응답 JSON 파싱에 실패했습니다.", e);
        }
    }
}
