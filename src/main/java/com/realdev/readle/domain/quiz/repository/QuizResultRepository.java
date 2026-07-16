package com.realdev.readle.domain.quiz.repository;

import com.realdev.readle.domain.quiz.entity.QuizResult;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizResultRepository extends JpaRepository<QuizResult, Long> {

  Optional<QuizResult> findByQuizAttemptId(Long attemptId);
}
