# ============================================================
# ZhiYu-Backend — 多阶段 Docker 构建
#
# 构建:
#   docker build -t zhiyu-backend:latest .
#
# 多架构:
#   docker buildx build --platform linux/amd64,linux/arm64 -t zhiyu-backend:latest --push .
#
# 运行:
#   docker run -p 8080:8080 -e NACOS_SERVER_ADDR=nacos:8848 zhiyu-backend:latest
#
# 支持架构: linux/amd64, linux/arm64
# ============================================================

# ── Stage 1: 编译 (always on build host arch) ────────────────
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build
COPY backend/pom.xml .
COPY backend/zhiyu-common/pom.xml zhiyu-common/
COPY backend/zhiyu-auth/pom.xml zhiyu-auth/
COPY backend/zhiyu-user/pom.xml zhiyu-user/
COPY backend/zhiyu-subscription/pom.xml zhiyu-subscription/
COPY backend/zhiyu-admin/pom.xml zhiyu-admin/
COPY backend/zhiyu-server/pom.xml zhiyu-server/

RUN mvn dependency:go-offline -B -q

COPY backend/ .
RUN mvn package -DskipTests -B -q -pl zhiyu-server -am

# 利用 Spring Boot 分层 JAR 拆包
RUN java -Djarmode=layertools -jar zhiyu-server/target/zhiyu-server-*.jar extract --destination /extracted

# ── Stage 2: 运行时 (target arch) ────────────────────────────
FROM --platform=$TARGETPLATFORM eclipse-temurin:21-jre-alpine

ARG TARGETARCH

LABEL org.opencontainers.image.title="zhiyu-backend"
LABEL org.opencontainers.image.description="ZhiYu-Backend application runtime"
LABEL org.opencontainers.image.source="https://github.com/zhiyu/zhiyu-backend"
LABEL org.opencontainers.image.architecture="${TARGETARCH}"

ARG SPRING_PROFILES_ACTIVE=dev

ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}

RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    addgroup -S zhiyu && adduser -S zhiyu -G zhiyu

WORKDIR /app

COPY --from=builder --chown=zhiyu:zhiyu /extracted/dependencies/ ./
COPY --from=builder --chown=zhiyu:zhiyu /extracted/spring-boot-loader/ ./
COPY --from=builder --chown=zhiyu:zhiyu /extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=zhiyu:zhiyu /extracted/application/ ./

USER zhiyu

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=60s \
  CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dcsp.sentinel.log.dir=/tmp", \
  "org.springframework.boot.loader.launch.JarLauncher"]
