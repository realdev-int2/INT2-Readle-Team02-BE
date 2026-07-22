package com.realdev.readle.domain.quiz.repository;

import com.realdev.readle.domain.quiz.entity.QuizAttempt;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

  @EntityGraph(attributePaths = {"member", "quizSet", "quizSet.content"})
  Optional<QuizAttempt> findWithDetailsById(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT q FROM QuizAttempt q WHERE q.id = :id")
  Optional<QuizAttempt> findByIdForUpdate(@Param("id") Long id);
}
