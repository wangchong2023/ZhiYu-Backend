# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project: ZhiYu-Backend

AI-native application platform backend. Java 21 + Spring Boot 3.3.x + Spring Cloud Alibaba, Maven multi-module, MySQL 8.0 + Redis 7, deployed on Alibaba Cloud ACK (K8s).

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (Eclipse Temurin) |
| Framework | Spring Boot 3.3.7 + Spring Cloud 2023.0.3 + Spring Cloud Alibaba 2023.0.1.0 |
| Build | Maven 3.9+ (multi-module, mvnw wrapper) |
| ORM | MyBatis-Plus 3.5.10 |
| DB Migration | Flyway 10.18.2 |
| Database | MySQL 8.0 (RDS) |
| Cache | Redis 7 (Sentinel) |
| Config | Nacos 2.x |
| Auth | JWT RS256 + BCrypt + TOTP + WebAuthn |
| Rate Limit | Sentinel |
| Test | JUnit 5 + Mockito + Testcontainers |
| CI/CD | GitHub Actions → Alibaba Cloud ACR → ACK |
| Monitoring | Prometheus + Grafana + Loki |

## Module Layout

```
zhiyu-backend/
├── pom.xml                    # Parent POM (version BOM + plugin management)
├── zhiyu-common/              # Shared: utils, exceptions, filters, DTOs, i18n
├── zhiyu-auth/                # Auth: register, login, JWT, captcha, password reset
├── zhiyu-user/                # User: profile, devices, TOTP, WebAuthn, account deletion
├── zhiyu-subscription/        # Subscription: plans, orders, payments, quotas, refunds
├── zhiyu-admin/               # Admin: admin auth, RBAC, user mgmt, audit logs
├── zhiyu-server/              # Entry point: Spring Boot app, Flyway migrations, assembly
│   └── src/main/resources/db/migration/  # V1.0.0 ~ V1.2.0
├── docs/                      # Full design docs (PRD, API-SPEC, ARCHITECTURE, etc.)
└── deploy/                    # K8s manifests (app/, infra/, monitoring/), Dockerfiles, envs, scripts
```

Dependency direction (one-way, no cycles):
```
server → admin → subscription → user → auth → common
```

## Quick Commands

```bash
# Build + unit tests
./mvnw clean test

# Full tests (integration tests need Docker)
./mvnw clean verify

# Start dev server
./mvnw spring-boot:run -pl zhiyu-server -Dspring.profiles.active=dev

# Package
./mvnw clean package -DskipTests

# Docker build (multi-stage, amd64/arm64 多架构)
docker build -t zhiyu-backend:latest .
docker buildx build --platform linux/amd64,linux/arm64 -t zhiyu-backend:latest --push .

# Docker build (pre-extracted layers — kubeadm 离线)
docker build -t zhiyu-backend:latest -f deploy/docker/Dockerfile.kubeadm .

# 离线打包（含所有依赖镜像）
./deploy/scripts/offline-pack.sh kubeadm

# K8s deploy (all-in-one)
./deploy/deploy.sh dev all        # Alibaba Cloud ACK
./deploy/deploy.sh test all       # 测试环境
./deploy/deploy.sh kubeadm all    # 本地 kubeadm 集群

# 仅部署（不编译，使用预编译 JAR）
./deploy/deploy.sh kubeadm build deploy

# Deploy monitoring stack
./deploy/deploy.sh kubeadm monitoring  # Prometheus + Grafana + kube-state-metrics + node-exporter

# Lint (Checkstyle + SpotBugs)
./mvnw checkstyle:check spotbugs:check
```

## Key Conventions

- **Constructor injection only** — Lombok `@RequiredArgsConstructor`, no `@Autowired` fields
- **Controller thin layer** — never inject Mapper in Controller, never write business logic
- **Entity → Resp DTO** via MapStruct Converter, never expose Entity directly
- **`@Transactional(rollbackFor = Exception.class)`** always, readonly queries marked explicitly
- **Error codes** in i18n: `messages.properties` (EN fallback) + `messages_zh_CN.properties`
- **API envelope**: `{ "code": 0, "message": "success", "data": {...}, "requestId": "uuid", "timestamp": 1716019200 }`
- **Test naming**: `*Test.java` (unit, Surefire), `*IT.java` (integration, Failsafe)
- **Immutable data** — create new objects, never mutate existing ones

## Module-specific Rules

- `zhiyu-common`: zero business deps, only 3rd-party libraries
- `zhiyu-auth` → `zhiyu-server`: cross-module calls only via Service interface injection
- `zhiyu-server`: no business code, only Spring Boot entry + Flyway + assembly
- Mapper interfaces NEVER cross module boundaries

## CodeGraph

This project has `.codegraph/` initialized. Use `codegraph_search`, `codegraph_context`, `codegraph_callers`, and `codegraph_callees` for code exploration.

## Documentation Map

| Doc | Content |
|-----|---------|
| `docs/PRD.md` | Product requirements, user stories, KPIs, P0/P1/P2 scope |
| `docs/ARCHITECTURE.md` | ADRs (10 decisions), sequence diagrams, deployment topology |
| `docs/API-SPEC.md` | Complete API specs with request/response schemas |
| `docs/DATABASE.md` | Full DDL, ER relationships, index design |
| `docs/DEVELOPMENT-STANDARDS.md` | Coding standards, package layout, error codes, naming |
| `docs/SECURITY.md` | Security testing, OWASP, PIPL compliance |
| `docs/TEST-PLAN.md` | Test cases per module (unit + integration + e2e) |
| `docs/CI-CD.md` | GitHub Actions pipeline, branch strategy, K8s deployment |
| `docs/OPS.md` | SLO/SLI, Grafana dashboards, alerting, DRP |
| `docs/INFRASTRUCTURE.md` | Nacos config, Redis keys, MySQL schema |
| `docs/RATE-LIMITING.md` | 3-layer rate limiting architecture |
| `docs/APP-DESIGN.md` | iOS/Android native app design specs |
| `docs/FRONTEND-DESIGN.md` | Admin web frontend component tree and states |
