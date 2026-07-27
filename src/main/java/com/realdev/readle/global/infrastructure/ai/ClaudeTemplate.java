package com.realdev.readle.global.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.util.JsonExtractor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeTemplate {

  private static final String AI_RETRIES = "readle.ai.client.retries";
  private static final String AI_REQUESTS = "readle.ai.client.requests";

  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  /** 1. 퀴즈 생성 등 순수 동기 호출 */
  public <T> T executeSync(
      Supplier<String> apiCall,
      Class<T> responseType,
      Consumer<T> responseValidator,
      Function<Throwable, RuntimeException> errorMapper) {
    try {
      String rawResponse = apiCall.get();
      T response = parseResponse(rawResponse, responseType, errorMapper);
      responseValidator.accept(response);
      return response;
    } catch (RuntimeException e) {
      throw errorMapper.apply(e);
    }
  }

  /** 2. 콘텐츠 검증 등 동기식 루프 재시도 및 CompletableFuture(내부 타임아웃) 사용 */
  public <T> T executeWithSyncRetry(
      Function<Integer, String> apiCall,
      Class<T> responseType,
      Consumer<T> responseValidator,
      int maxAttempts,
      long retryDelayMs,
      long timeoutSeconds,
      Executor executor,
      String purpose,
      Function<Throwable, RuntimeException> errorMapper) {

    if (maxAttempts <= 0) {
      throw errorMapper.apply(new IllegalArgumentException("maxAttempts는 0보다 커야 합니다."));
    }

    Throwable lastException = null;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      final int currentAttempt = attempt;
      try {
        log.info("[AI_TEMPLATE] AI 호출 시도 ({}/{}) - purpose: {}", attempt, maxAttempts, purpose);

        String rawText =
            callWithTimeout(() -> apiCall.apply(currentAttempt), timeoutSeconds, executor, purpose);
        T response = parseResponse(rawText, responseType, errorMapper);
        responseValidator.accept(response);
        return response;

      } catch (RuntimeException e) {
        log.warn(
            "[AI_TEMPLATE] AI 호출 처리 실패 (시도: {}/{}). 사유: {}", attempt, maxAttempts, e.getMessage());
        lastException = e;

        if (attempt < maxAttempts) {
          Counter.builder(AI_RETRIES).tag("purpose", purpose).register(meterRegistry).increment();
          sleepBeforeRetry(retryDelayMs);
        }
      }
    }

    log.error("[AI_TEMPLATE] AI 호출 최종 실패 - purpose: {}", purpose, lastException);
    throw errorMapper.apply(lastException);
  }

  /** 3. 퀴즈 채점 등 완전 비동기 체이닝 기반 재시도 */
  public <T> CompletableFuture<T> executeAsyncWithRetry(
      Function<Integer, String> apiCall,
      Class<T> responseType,
      Consumer<T> responseValidator,
      int attempt,
      int maxAttempts,
      Duration timeout,
      Executor executor,
      String purpose,
      Function<Throwable, RuntimeException> errorMapper) {

    Timer.Sample sample = Timer.start(meterRegistry);
    CompletableFuture<T> task;

    try {
      task =
          supplyInterruptiblyAsync(
              () -> {
                String rawResponse = apiCall.apply(attempt);
                T response = parseResponse(rawResponse, responseType, errorMapper);
                responseValidator.accept(response);
                return response;
              },
              executor);
    } catch (RejectedExecutionException e) {
      task = CompletableFuture.failedFuture(e);
    }

    CompletableFuture<T> submittedTask = task;
    CompletableFuture<T> timedTask =
        submittedTask.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);

    timedTask.whenComplete(
        (result, error) ->
            sample.stop(
                Timer.builder(AI_REQUESTS)
                    .tags(
                        "purpose",
                        purpose,
                        "outcome",
                        error == null ? "success" : (isTimeout(error) ? "timeout" : "failure"))
                    .register(meterRegistry)));

    return timedTask.exceptionallyCompose(
        ex -> {
          submittedTask.cancel(true);
          if (attempt < maxAttempts) {
            Counter.builder(AI_RETRIES).tag("purpose", purpose).register(meterRegistry).increment();
            log.warn("[AI_TEMPLATE] AI 비동기 호출 실패. 재시도를 진행합니다. 시도: {}/{}", attempt, maxAttempts, ex);
            return executeAsyncWithRetry(
                apiCall,
                responseType,
                responseValidator,
                attempt + 1,
                maxAttempts,
                timeout,
                executor,
                purpose,
                errorMapper);
          } else {
            log.error("[AI_TEMPLATE] AI 비동기 호출 최종 실패.", ex);
            Throwable actualError =
                (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
            CompletableFuture<T> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(errorMapper.apply(actualError));
            return failedFuture;
          }
        });
  }

  private String callWithTimeout(
      Supplier<String> apiCall, long timeoutSeconds, Executor executor, String purpose) {
    Timer.Sample sample = Timer.start(meterRegistry);
    String outcome = "failure";
    CompletableFuture<String> future = null;

    try {
      future = supplyInterruptiblyAsync(apiCall, executor);
      String rawText = future.get(timeoutSeconds, TimeUnit.SECONDS);
      outcome = "success";
      return rawText;
    } catch (InterruptedException e) {
      if (future != null) future.cancel(true);
      Thread.currentThread().interrupt();
      throw new RuntimeException("AI 호출 중 인터럽트가 발생했습니다.", e);
    } catch (TimeoutException e) {
      if (future != null) future.cancel(true);
      outcome = "timeout";
      throw new CustomException(GlobalErrorCode.AI_TIMEOUT, "AI 호출 시간이 초과되었습니다.", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (isTimeout(cause)) {
        outcome = "timeout";
        throw new CustomException(GlobalErrorCode.AI_TIMEOUT, "AI 호출 시간이 초과되었습니다.", cause);
      }
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw new RuntimeException("AI 호출 중 오류가 발생했습니다.", cause);
    } catch (RuntimeException e) {
      throw e;
    } finally {
      sample.stop(
          Timer.builder(AI_REQUESTS)
              .tags("purpose", purpose, "outcome", outcome)
              .register(meterRegistry));
    }
  }

  private <T> T parseResponse(
      String rawResponse,
      Class<T> responseType,
      Function<Throwable, RuntimeException> errorMapper) {
    try {
      String cleanJson = JsonExtractor.extractJson(rawResponse);
      if (cleanJson == null || cleanJson.isEmpty()) {
        throw new CustomException(GlobalErrorCode.AI_PARSING_ERROR, "AI 응답에서 유효한 JSON을 찾을 수 없습니다.");
      }
      return objectMapper.readValue(cleanJson, responseType);
    } catch (JsonProcessingException e) {
      throw errorMapper.apply(e);
    } catch (RuntimeException e) {
      throw errorMapper.apply(e);
    }
  }

  private void sleepBeforeRetry(long delayMs) {
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  private boolean isTimeout(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof TimeoutException
          || current instanceof SocketTimeoutException
          || current instanceof HttpTimeoutException) {
        return true;
      }
      // 무한 루프 방지를 위해 자기 자신이 원인인 경우 종료
      if (current.getCause() == current) {
        break;
      }
      current = current.getCause();
    }
    return false;
  }

  private <T> CompletableFuture<T> supplyInterruptiblyAsync(
      Supplier<T> supplier, Executor executor) {
    class InterruptibleFuture extends CompletableFuture<T> {
      private Thread executingThread;

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = super.cancel(mayInterruptIfRunning);
        if (cancelled && mayInterruptIfRunning) {
          synchronized (this) {
            if (executingThread != null) {
              executingThread.interrupt();
            }
          }
        }
        return cancelled;
      }

      synchronized void setExecutingThread(Thread thread) {
        this.executingThread = thread;
      }
    }

    InterruptibleFuture future = new InterruptibleFuture();

    executor.execute(
        () -> {
          future.setExecutingThread(Thread.currentThread());
          try {
            if (!future.isCancelled()) {
              future.complete(supplier.get());
            }
          } catch (Throwable ex) {
            future.completeExceptionally(ex);
          } finally {
            future.setExecutingThread(null);
            Thread.interrupted(); // Clear interrupt flag
          }
        });

    return future;
  }
}
