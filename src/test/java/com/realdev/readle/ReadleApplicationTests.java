package com.realdev.readle;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {"spring.cloud.aws.s3.enabled=false", "anthropic.claude.api-key=mock-test-key"})
class ReadleApplicationTests {

  @Test
  void contextLoads() {}
}
