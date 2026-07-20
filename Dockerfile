FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 spring \
    && useradd --system --uid 10001 --gid spring spring

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

USER spring

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/api/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "/app/app.jar"]
