# major 태그(예: amazoncorretto:21)는 Amazon이 같은 이름표로 다른 이미지를 계속 재발행하는
# 움직이는 타겟이라, 어느 날 갑자기 내용물이 바뀌어 빌드가 깨질 수 있음(findutils가 빠지며 실제로 발생).
# patch 버전까지 고정해 재현성을 확보하되, 다이제스트까지는 안 박아서(과함, 보안 패치 자동 반영 포기) 절충함.
# 갱신 시점: Corretto 21의 다음 patch 릴리즈로 의도적으로 올릴 때만 이 태그를 수동으로 변경할 것.
# 1️⃣ 빌드 단계 (Gradle 빌드 수행)
# BUILDPLATFORM pins this stage to the runner's native arch: the JAR is platform-independent
# bytecode, so building it once natively and copying it into each target image avoids running
# Gradle under QEMU emulation for arm64 (that made the multi-arch build ~10x slower: 152s → 1522s).
FROM --platform=$BUILDPLATFORM amazoncorretto:21.0.12 AS builder

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
FROM amazoncorretto:21.0.12
WORKDIR /app

# 빌드된 JAR 파일만 복사
COPY --from=builder /app/build/libs/*.jar app.jar


# Explicit heap cap sized against docker-compose's mem_limit (AWS EC2 t4g.small, 2GiB host).
# ExitOnOutOfMemoryError kills the container on OOM instead of leaving it in a degraded
# zombie state, so restart: always can bring it back up.
CMD ["java", "-XX:MaxRAMPercentage=70.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
