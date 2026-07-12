package com.realdev.readle;

import org.springframework.boot.SpringApplication;

public class TestReadleApplication {

  public static void main(String[] args) {
    SpringApplication.from(ReadleApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
