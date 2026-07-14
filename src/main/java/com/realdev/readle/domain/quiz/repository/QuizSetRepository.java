package com.realdev.readle.domain.quiz.repository;

import com.realdev.readle.domain.quiz.entity.QuizSet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizSetRepository extends JpaRepository<QuizSet, Long> {
  Optional<QuizSet> findBySourceValidationId(Long sourceValidationId);
}
