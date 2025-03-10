# 1️⃣ 빌드 단계 (Gradle 빌드 수행)
FROM amazoncorretto:21 AS builder

# 작업 디렉토리 설정
WORKDIR /app

# 프로젝트 소스 코드 복사
COPY . .

# Gradle 캐시 최적화 및 빌드 실행
RUN ./gradlew clean bootJar

# 2️⃣ 실행 단계 (최종 컨테이너)
FROM amazoncorretto:21
WORKDIR /app

# 빌드된 JAR 파일만 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# Spring Boot 실행
CMD ["java", "-jar", "/app/app.jar"]
