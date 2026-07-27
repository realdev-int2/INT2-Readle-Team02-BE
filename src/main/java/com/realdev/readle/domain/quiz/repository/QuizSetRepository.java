package com.realdev.readle.domain.quiz.repository;

import com.realdev.readle.domain.quiz.entity.QuizSet;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizSetRepository extends JpaRepository<QuizSet, Long> {
  Optional<QuizSet> findBySourceValidationId(Long sourceValidationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0")})
  Optional<QuizSet> findForUpdateBySourceValidationId(Long sourceValidationId);
}
