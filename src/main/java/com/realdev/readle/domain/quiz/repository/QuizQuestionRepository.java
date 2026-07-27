package com.realdev.readle.domain.quiz.repository;

import com.realdev.readle.domain.quiz.entity.QuizQuestion;
import com.realdev.readle.domain.quiz.entity.QuizSet;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {
  List<QuizQuestion> findByQuizSetOrderByOrderNoAsc(QuizSet quizSet);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM QuizQuestion qq WHERE qq.quizSet.id = :quizSetId")
  void deleteByQuizSetId(@Param("quizSetId") Long quizSetId);
}
