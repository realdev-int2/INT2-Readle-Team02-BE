package com.realdev.readle.domain.quiz.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class QuizAsyncConfig {

  @Bean(name = "gradingExecutor")
  public Executor gradingExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // EC2 4GB 환경 및 I/O 바운드 작업임을 고려한 스레드 풀 설정
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(50);
    // 큐가 가득 차면 제출한 스레드(톰캣 워커 스레드)에서 직접 실행하여 배압(Backpressure) 제어
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setThreadNamePrefix("grading-");
    executor.initialize();
    return executor;
  }
}
