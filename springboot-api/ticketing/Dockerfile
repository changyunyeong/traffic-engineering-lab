FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Gradle Wrapper 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# 의존성 다운로드 (캐싱)
RUN ./gradlew dependencies --no-daemon || true

# 소스 코드 복사 및 빌드
COPY src src
RUN ./gradlew bootJar --no-daemon

# 런타임 스테이지
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 보안 및 최적화
RUN apk add --no-cache curl && \
    addgroup -g 1000 spring && \
    adduser -u 1000 -G spring -s /bin/sh -D spring

# JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 소유권 변경
RUN chown spring:spring app.jar
USER spring:spring

# Health Check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]