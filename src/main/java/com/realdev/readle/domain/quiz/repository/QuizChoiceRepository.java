package com.realdev.readle.domain.quiz.repository;

import com.realdev.readle.domain.quiz.entity.QuizChoice;
import com.realdev.readle.domain.quiz.entity.QuizQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizChoiceRepository extends JpaRepository<QuizChoice, Long> {
  List<QuizChoice> findByQuizQuestionIn(List<QuizQuestion> questions);

  List<QuizChoice> findByQuizQuestionInAndIsCorrectTrue(List<QuizQuestion> questions);

  @Modifying(clearAutomatically = true)
  @Query(
      "DELETE FROM QuizChoice qc WHERE qc.quizQuestion.id IN (SELECT qq.id FROM QuizQuestion qq"
          + " WHERE qq.quizSet.id = :quizSetId)")
  void deleteByQuizSetId(@Param("quizSetId") Long quizSetId);
}
