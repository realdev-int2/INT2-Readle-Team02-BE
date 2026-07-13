package com.realdev.readle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class ReadleApplication {

  public static void main(String[] args) {
    SpringApplication.run(ReadleApplication.class, args);
  }
}
