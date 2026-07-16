package com.realdev.readle.domain.quiz.repository;

import com.realdev.readle.domain.quiz.entity.QuizAnswer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {

  @Query(
      "SELECT qa FROM QuizAnswer qa "
          + "JOIN FETCH qa.quizQuestion qq "
          + "LEFT JOIN FETCH qa.submittedChoice "
          + "WHERE qa.quizAttempt.id = :attemptId "
          + "ORDER BY qq.orderNo ASC")
  List<QuizAnswer> findByQuizAttemptIdWithQuestionAndChoice(@Param("attemptId") Long attemptId);
}
