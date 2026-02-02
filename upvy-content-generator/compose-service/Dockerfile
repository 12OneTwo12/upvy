# =============================================================================
# Upvy Compose Service - Dockerfile
# =============================================================================
# FFmpeg 기반 영상 합성 서비스
# 비주얼 클립 + 오디오 + 자막 → 최종 숏폼 영상
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build
# -----------------------------------------------------------------------------
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Gradle 캐싱을 위해 의존성 파일만 먼저 복사
COPY build.gradle.kts settings.gradle.kts ./

# 의존성 다운로드 (캐싱)
RUN gradle dependencies --no-daemon || true

# 소스 코드 복사 및 빌드
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# -----------------------------------------------------------------------------
# Stage 2: Runtime
# -----------------------------------------------------------------------------
# ARM64 (Apple Silicon) 호환을 위해 Debian 기반 이미지 사용
FROM eclipse-temurin:17-jre

WORKDIR /app

# FFmpeg 설치 (영상 합성 핵심)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 임시 디렉토리 생성
RUN mkdir -p /tmp/compose && chmod 777 /tmp/compose

# 빌드된 JAR 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 비루트 사용자로 실행 (보안)
# GID/UID 1000이 이미 존재할 수 있으므로 다른 ID 사용
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup appuser && \
    chown -R appuser:appgroup /app /tmp/compose

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8084/actuator/health || exit 1

# 포트 노출
EXPOSE 8084

# 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
