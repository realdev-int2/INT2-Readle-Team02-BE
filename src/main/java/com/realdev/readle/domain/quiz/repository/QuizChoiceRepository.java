package com.realdev.readle.domain.quiz.repository;

import com.realdev.readle.domain.quiz.entity.QuizChoice;
import com.realdev.readle.domain.quiz.entity.QuizQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizChoiceRepository extends JpaRepository<QuizChoice, Long> {
  List<QuizChoice> findByQuizQuestionIn(List<QuizQuestion> questions);

  List<QuizChoice> findByQuizQuestionInAndIsCorrectTrue(List<QuizQuestion> questions);
}
