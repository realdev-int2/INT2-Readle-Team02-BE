package com.realdev.readle;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("flyway")
@DisplayName("Flyway 마이그레이션 및 스키마 검증 테스트")
class SchemaValidationTest {

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("Flyway 마이그레이션 스크립트가 JPA 엔티티 스키마와 일치한다")
  void flywayMigrationMatchesEntitySchema() {
    // Given & When:
    // @SpringBootTest가 컨텍스트를 로딩하면서 다음을 자동으로 수행:
    // 1. @ActiveProfiles("test")에 따라 application-test.yaml 설정을 사용.
    // 2. @TestPropertySource에 의해 Flyway가 활성화되고, V1__init_schema.sql 스크립트를 실행.
    // 3. Hibernate의 ddl-auto=validate 설정에 따라, 생성된 DB 스키마와 JPA 엔티티 모델을 비교.

    // Then:
    // 컨텍스트 로딩이 예외 없이 성공하면, 스키마가 일치하는 것으로 검증 완료.
  }

  @Test
  @DisplayName("V2는 member를 변경하지 않고 OAuth authorization-state 테이블을 추가한다")
  void v2AddsOnlyOauthAuthorizationState() throws Exception {
    try (var connection = dataSource.getConnection()) {
      var state = connection.getMetaData().getTables(null, null, "OAUTH_AUTHORIZATION_STATE", null);
      var returnTo =
          connection.getMetaData().getColumns(null, null, "OAUTH_AUTHORIZATION_STATE", "RETURN_TO");
      var email = connection.getMetaData().getColumns(null, null, "MEMBER", "EMAIL");

      assertThat(state.next()).isTrue();
      assertThat(returnTo.next()).isTrue();
      assertThat(returnTo.getInt("COLUMN_SIZE")).isEqualTo(2048);
      assertThat(email.next()).isTrue();
      assertThat(email.getInt("NULLABLE")).isEqualTo(java.sql.DatabaseMetaData.columnNullable);
    }
  }
}
