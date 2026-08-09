# 1️⃣ 빌드 단계 (Gradle 빌드 수행)
FROM amazoncorretto:21 AS builder

# 작업 디렉토리 설정
WORKDIR /app

# 프로젝트 소스 코드 복사
COPY . .

# gradlew가 xargs를 요구하는데, amazoncorretto 베이스 이미지(Amazon Linux 2023)에는
# findutils가 기본 포함돼 있지 않아 "xargs is not available"로 빌드 자체가 실패함.
RUN dnf install -y findutils && dnf clean all

# Gradle 캐시 최적화 및 빌드 실행
RUN ./gradlew clean bootJar

# 2️⃣ 실행 단계 (최종 컨테이너)
FROM amazoncorretto:21
WORKDIR /app

# 빌드된 JAR 파일만 복사
COPY --from=builder /app/build/libs/*.jar app.jar


# Explicit heap cap sized against docker-compose's mem_limit (OCI Always Free, 1GB host).
# ExitOnOutOfMemoryError kills the container on OOM instead of leaving it in a degraded
# zombie state, so restart: always can bring it back up.
CMD ["java", "-XX:MaxRAMPercentage=70.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
