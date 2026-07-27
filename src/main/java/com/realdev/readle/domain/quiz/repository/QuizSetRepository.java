package com.realdev.readle.domain.quiz.repository;

import com.realdev.readle.domain.quiz.entity.QuizSet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizSetRepository extends JpaRepository<QuizSet, Long> {
  Optional<QuizSet> findBySourceValidationId(Long sourceValidationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<QuizSet> findForUpdateBySourceValidationId(Long sourceValidationId);
}
