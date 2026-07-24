package com.realdev.readle.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@Configuration
@Profile({"local", "dev"})
@RequiredArgsConstructor
public class DummyDataInit {

  private final JdbcTemplate jdbcTemplate;

  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    try {
      Integer count =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM member WHERE email = 'test@test.com'", Integer.class);
      if (count != null && count == 0) {
        log.info("로컬 테스트를 위한 더미 데이터(Member, Content, ContentValidation) 초기화를 진행합니다.");

        org.springframework.jdbc.support.KeyHolder keyHolder =
            new org.springframework.jdbc.support.GeneratedKeyHolder();

        jdbcTemplate.update(
            connection -> {
              java.sql.PreparedStatement ps =
                  connection.prepareStatement(
                      "INSERT INTO member (uuid, email, nickname, oauth_provider, oauth_id, created_at, updated_at) "
                          + "VALUES ('test-uuid-1234', 'test@test.com', '테스터', 'GOOGLE', 'test-oauth-id', NOW(), NOW())",
                      java.sql.Statement.RETURN_GENERATED_KEYS);
              return ps;
            },
            keyHolder);

        Long memberId = keyHolder.getKey().longValue();

        jdbcTemplate.update(
            connection -> {
              java.sql.PreparedStatement ps =
                  connection.prepareStatement(
                      "INSERT INTO content (member_id, title, input_type, raw_text, crawl_status, created_at, updated_at) "
                          + "VALUES (?, '테스트 제목', 'TEXT', '스프링 프레임워크와 의존성 주입(DI)에 대한 본문입니다. 객체지향의 특징을 잘 보여주며, 빈(Bean) 스코프에 대한 내용도 포함합니다.', 'NOT_APPLICABLE', NOW(), NOW())",
                      java.sql.Statement.RETURN_GENERATED_KEYS);
              ps.setLong(1, memberId);
              return ps;
            },
            keyHolder);

        Long contentId = keyHolder.getKey().longValue();

        jdbcTemplate.update(
            "INSERT INTO content_validation (content_id, validation_method, status, validation_score, created_at) "
                + "VALUES (?, 'AI', 'PASSED', 100.0, NOW())",
            contentId);

        log.info("더미 데이터 초기화 완료! 이제 POST /api/quizzes API부터 바로 호출 가능합니다.");
      }
    } catch (DataAccessException e) {
      log.warn("더미 데이터 초기화 중 스킵 (테이블 미생성 상태 또는 이미 존재함): {}", e.getMessage());
    }
  }
}
