package com.realdev.readle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@Tag("flyway")
@DisplayName("Flyway 마이그레이션 및 스키마 검증 테스트")
class SchemaValidationTest {

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
}
