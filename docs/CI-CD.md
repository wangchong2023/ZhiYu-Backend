# ZhiYu CI/CD、部署与集成设计

> 本文档定义 ZhiYu-Backend 的持续集成/持续部署流程、Docker/K8s 部署规格和模块间集成策略。运维操作细节见 [OPS.md](OPS.md)，部署拓扑见 [ARCHITECTURE.md §5](ARCHITECTURE.md#5-部署架构)。

## 1. CI/CD 流水线总览

```
Git Push ──▶ GitHub Actions ──▶ ┌─────────────────────────────────────────────┐
                                │ Stage 1: Build & Test        (~6 min)       │
                                │  ├─ Checkout + Cache restore                 │
                                │  ├─ Maven compile                           │
                                │  ├─ Unit tests (Surefire)                   │
                                │  ├─ Integration tests (Failsafe)            │
                                │  ├─ JaCoCo coverage check (≥80%)            │
                                │  ├─ Checkstyle + SpotBugs                   │
                                │  ├─ OWASP Dependency Check                  │
                                │  └─ Gitleaks secret scan                    │
                                └───────────┬─────────────────────────────────┘
                                            │ (main branch only)
                                ┌───────────┴─────────────────────────────────┐
                                │ Stage 2: Package             (~2 min)       │
                                │  ├─ Maven package (skip tests)              │
                                │  ├─ Docker build (multi-stage)              │
                                │  ├─ Trivy image scan                       │
                                │  └─ Push to registry                       │
                                └───────────┬─────────────────────────────────┘
                                            │
                                ┌───────────┴─────────────────────────────────┐
                                │ Stage 3: Deploy to Test      (~5 min)       │
                                │  ├─ deploy.sh dev deploy --dry-run          │
                                │  ├─ deploy.sh dev deploy (ConfigMap/Secret  │
                                │  │   + Deployment + HPA/PDB/NetworkPolicy)  │
                                │  ├─ kubectl port-forward + E2E curl 测试    │
                                │  │   (/actuator/health + /api/v1/auth/*)    │
                                │  └─ ZAP baseline scan (daily cron)          │
                                └───────────┬─────────────────────────────────┘
                                            │ (release branch only)
                                ┌───────────┴─────────────────────────────────┐
                                │ Stage 4: Deploy to Release   (~10 min)      │
                                │  ├─ deploy.sh release deploy --dry-run      │
                                │  ├─ deploy.sh release deploy (ConfigMap/     │
                                │  │   Secret + HPA/PDB/NetworkPolicy/SA)     │
                                │  ├─ Argo Rollouts 金丝雀 (20%→40%→100%)     │
                                │  │   每步 AnalysisTemplate 健康检查          │
                                │  ├─ CI promote 推进 / abort 异常回滚         │
                                │  └─ Post-deploy health monitor (30 min)     │
                                └─────────────────────────────────────────────┘
```

---

## 2. 分支策略与触发规则

| 分支 | 触发事件 | Build | Test | Package | Deploy Test | Deploy Release |
|------|---------|:----:|:----:|:-------:|:-----------:|:-------------:|
| `feature/*` | push | ✅ | ✅ | ❌ | ❌ | ❌ |
| PR → `main` | pull_request | ✅ | ✅ | ✅ | ❌ | ❌ |
| `main` | push (merge) | ✅ | ✅ | ✅ | ✅ (自动) | ❌ |
| `release/*` | push | ✅ | ✅ | ✅ | ✅ (自动) | ✅ (手动审批) |
| `main` | schedule (每日 5:00) | ✅ | ✅ | ✅ | ✅ | ❌ |

**分支命名规范：**
- `feature/<description>` — 功能开发
- `fix/<issue-id>` — Bug 修复
- `release/v<major>.<minor>.<patch>` — 发布分支
- `hotfix/<description>` — 紧急修复（从 main 分叉）

---

## 3. GitHub Actions Workflow

### 3.1 主流水线 (`.github/workflows/ci.yml`)

```yaml
name: CI Pipeline

on:
  push:
    branches: [main, 'release/**']
  pull_request:
    branches: [main]
  schedule:
    - cron: '0 21 * * *'  # 每日 5:00 CST

env:
  REGISTRY: registry.cn-hangzhou.aliyuncs.com
  IMAGE_NAME: zhiyu/zhiyu-backend
  JAVA_VERSION: '21'

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    services:
      # Testcontainers 自动提供 MySQL + Redis，无需声明额外 service
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'corretto'
          cache: 'maven'

      - name: Cache Maven packages
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: maven-${{ hashFiles('**/pom.xml') }}
          restore-keys: maven-

      - name: Compile
        run: ./mvnw compile -T 1C

      - name: Unit Tests
        run: ./mvnw test -T 1C

      - name: Integration Tests
        run: ./mvnw verify -Dskip.unit.tests=true

      - name: JaCoCo Coverage Check
        run: ./mvnw jacoco:check

      - name: Checkstyle
        run: ./mvnw checkstyle:check

      - name: SpotBugs
        run: ./mvnw spotbugs:check

      - name: OWASP Dependency Check
        run: ./mvnw dependency-check:aggregate

      - name: Gitleaks Secret Scan
        uses: gitleaks/gitleaks-action@v2
        with:
          config-path: .gitleaks.toml

      - name: Upload Coverage Report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: coverage-report
          path: '**/target/site/jacoco/'

  package:
    needs: build-and-test
    if: github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/heads/release/')
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set short SHA
        run: echo "SHORT_SHA=$(git rev-parse --short=7 HEAD)" >> $GITHUB_ENV

      - name: Package JAR
        run: ./mvnw package -DskipTests -T 1C

      - name: Build Docker Image
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Trivy Image Scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ steps.meta.outputs.version }}
          format: sarif
          output: trivy-results.sarif
          severity: HIGH,CRITICAL

      - name: Login to Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ secrets.ALIYUN_ACR_USERNAME }}
          password: ${{ secrets.ALIYUN_ACR_PASSWORD }}

  deploy-test:
    needs: package
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    runs-on: ubuntu-latest
    environment: test
    steps:
      - uses: actions/checkout@v4
      - name: Configure kubectl
        uses: aliyun/ack-kubeconfig@v1
        with:
          access-key-id: ${{ secrets.ALIYUN_ACCESS_KEY_ID }}
          access-key-secret: ${{ secrets.ALIYUN_ACCESS_KEY_SECRET }}
          cluster-id: ${{ secrets.ACK_CLUSTER_ID }}

      - name: Dry-run validation
        run: |
          source deploy/envs/dev.env
          export DOCKER_TAG="${GITHUB_SHA::8}"
          ./deploy/deploy.sh dev deploy --dry-run

      - name: Deploy (ConfigMap + Secret + HPA + PDB + NetworkPolicy)
        run: |
          source deploy/envs/dev.env
          export DOCKER_TAG="${GITHUB_SHA::8}"
          ./deploy/deploy.sh dev deploy

      - name: E2E Smoke Tests
        run: |
          kubectl port-forward -n zhiyu-dev svc/zhiyu-backend 8080:8080 &
          sleep 5
          curl -f http://localhost:8080/actuator/health
          curl -f -H "Content-Type: application/json" \
            -d '{"username":"smoketest"}' \
            http://localhost:8080/api/v1/auth/check-availability

  deploy-release:
    needs: package
    if: startsWith(github.ref, 'refs/heads/release/') && github.event_name == 'push'
    runs-on: ubuntu-latest
    environment:
      name: release
      url: https://api.zhiyu.app
    steps:
      - name: Install Argo Rollouts CLI
        run: |
          curl -sLO https://github.com/argoproj/argo-rollouts/releases/latest/download/kubectl-argo-rollouts-linux-amd64
          chmod +x kubectl-argo-rollouts-linux-amd64
          sudo mv kubectl-argo-rollouts-linux-amd64 /usr/local/bin/kubectl-argo-rollouts

      # Argo Rollouts 金丝雀发布: 20%→40%→100%，每步自动分析健康状态
      # 详见 deploy/rollout.yaml 和 OPS.md §5
```

### 3.2 前端 CI (`.github/workflows/frontend-ci.yml`)

```yaml
name: Frontend CI

on:
  pull_request:
    paths: ['admin-web/**']

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: admin-web
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: admin-web/package-lock.json
      - run: npm ci
      - run: npm run lint
      - run: npm run test -- --coverage
      - run: npm run build
      - run: npx @axe-core/cli --exit --stdout src/
      - name: Upload to OSS
        if: github.ref == 'refs/heads/main'
        run: |
          # 构建产物上传至 OSS，CDN 自动刷新
          npx ossutil cp dist/ oss://zhiyu-admin-web/ --recursive
```

---

### 3.3 Woodpecker CI (本地 kubeadm 环境)

Woodpecker v3.4.0 运行在本地 Docker Compose 中，与 Gitea 集成。流水线配置位于 `.woodpecker.yml`。

**访问地址：**

| 服务 | 地址 | 说明 |
|------|------|------|
| Gitea | `http://192.168.0.105:3000` | Git 仓库 + OAuth 认证 |
| Woodpecker Server | `http://localhost:8000` | CI/CD 控制台 |
| Woodpecker gRPC | `localhost:9000` | Agent 通信 |
| Nexus Maven | `http://192.168.0.105:8081` | 私有 Maven 制品库 |

> Gitea 和 Nexus 使用宿主机 LAN IP (`192.168.0.105`)，同时兼容浏览器访问和 Docker 容器内通信。若 IP 变更需同步更新 `woodpecker/bin/docker-compose.yml` 中的 `WOODPECKER_GITEA_URL` 和 `backend/.mvn/settings.xml` 中的 Nexus URL。

**架构：**

```
Git Push → Gitea (192.168.0.105:3000)
              │
              ▼ (OAuth)
       Woodpecker Server (localhost:8000)
              │
              ▼ (gRPC :9000)
       Woodpecker Agent (Docker socket)
              │
              ▼
       Docker 容器执行流水线步骤
              │          │
              ▼          ▼ (Maven 依赖缓存)
       SSH 远端部署    Nexus (192.168.0.105:8081)
```

**流水线阶段：**

| 阶段 | 触发条件 | 步骤 |
|------|---------|------|
| 静态检查 | 所有 push / PR | Checkstyle, SpotBugs |
| 单元测试 | 所有 push / PR | `mvn test`, JaCoCo 覆盖率 |
| 集成测试 | main push | `mvn verify` (Testcontainers) |
| Maven 打包 | main push | `mvn package -DskipTests` |
| Docker 构建 | main push | `docker build -f Dockerfile.kubeadm` |
| 镜像扫描 | main push | Trivy HIGH/CRITICAL |
| 密钥扫描 | 所有 push / PR | Gitleaks |
| 部署 | main push | SSH → `deploy.sh kubeadm all` |
| E2E 冒烟 | main push | `/actuator/health` + `/api/v1/auth/check-availability` |

**关键差异（vs GitHub Actions）：**

| 项目 | GitHub Actions | Woodpecker (本地) |
|------|:---:|:---:|
| 运行器 | GitHub 托管 `ubuntu-24.04` | 本地 Docker 容器 |
| 基础镜像 | `actions/setup-java` | `maven:3.9-eclipse-temurin-21` |
| Docker 构建 | `docker/build-push-action` | Docker CLI + socket 挂载 |
| 镜像推送 | 阿里云 ACR | 本地 `docker build` (离线) |
| 部署目标 | ACK (阿里云 K8s) | kubeadm 单节点 (10.211.55.4) |
| 部署方式 | kubectl + ACK 凭证 | SSH → `deploy.sh` |
| Secrets | GitHub Secrets | Woodpecker UI `secrets` |
| 制品存储 | GitHub Artifacts | 本地文件系统 |
| Maven 加速 | `actions/cache@v4` on `~/.m2` | 本地 Nexus 代理缓存 |

**Secrets 配置（Woodpecker UI → 仓库 → Settings → Secrets）：**

| Secret | 说明 |
|--------|------|
| `ssh_user` | 远端 kubeadm 节点 (10.211.55.4) SSH 用户名 |
| `ssh_password` | 远端 kubeadm 节点 (10.211.55.4) SSH 密码 |

**Cron 作业配置（Woodpecker UI → 仓库 → Settings → Cron Jobs）：**

| 名称 | 表达式 | 触发流水线 |
|------|--------|-----------|
| `nightly-scan` | `0 21 * * *` (每日 05:00 CST) | OWASP + Trivy fs |

---

## 4. Docker 构建

### 4.1 多阶段 Dockerfile

```dockerfile
# Dockerfile (项目根目录) — JAR 在本地预编译，Docker 仅 COPY + 构建镜像
FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.title="zhiyu-backend"
ARG SPRING_PROFILES_ACTIVE=dev

RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    addgroup -S zhiyu && adduser -S zhiyu -G zhiyu

WORKDIR /app
# 分层 JAR 在本地预提取（java -Djarmode=layertools -jar ... extract），Docker 仅 COPY
COPY --chown=zhiyu:zhiyu target/extracted/dependencies/ ./
COPY --chown=zhiyu:zhiyu target/extracted/spring-boot-loader/ ./
COPY --chown=zhiyu:zhiyu target/extracted/snapshot-dependencies/ ./
COPY --chown=zhiyu:zhiyu target/extracted/application/ ./

USER zhiyu
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
```

分层 JAR 顺序 (dependencies → spring-boot-loader → snapshot-dependencies → application) 确保：
- 业务代码改动时仅重建 `application/` 层
- 依赖变更时仅重建 `dependencies/` 和 `snapshot-dependencies/` 层
- 充分利用 Docker layer cache，加速 CI 构建

### 4.2 JVM 配置（按环境）

所有环境统一使用 ZGC + `-XX:MaxRAMPercentage=75.0`，堆内存自动适配 K8s `resources.limits`。不需要手动设置 `-Xms` / `-Xmx`。

| 环境 | K8s CPU Request/Limit | K8s Mem Request/Limit | GC | 堆内存（自动） |
|------|:---------------------:|:---------------------:|-----|:----------------:|
| dev | 250m / 500m | 256Mi / 512Mi | ZGC | ~384Mi |
| staging | 500m / 1000m | 512Mi / 1Gi | ZGC | ~768Mi |
| release | 1000m / 2000m | 1Gi / 2Gi | ZGC | ~1.5Gi |

`JAVA_OPTS` 通过 ConfigMap 注入：`-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError`

### 4.3 镜像标签策略

| 标签 | 用途 | 示例 |
|------|------|------|
| `<short_sha>` | 可追溯的精确版本（CI 自动生成） | `a1b2c3d4` |
| `<branch>` | 分支最新版本 | `main` |
| `latest` | 当前最新稳定版本（main 分支） | `latest` |
| `v<major>.<minor>.<patch>` | 语义化发布版本（手动 tag） | `v1.0.0` |

CI 通过 `docker/metadata-action` 的 `type=sha` + `type=ref,event=branch` + `type=raw,value=latest` 自动生成标签。

---

## 5. K8s 部署清单

### 5.1 Deployment

```yaml
# k8s/release/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zhiyu-backend
  namespace: release
  labels:
    app: zhiyu-backend
spec:
  replicas: 4
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: zhiyu-backend
  template:
    metadata:
      labels:
        app: zhiyu-backend
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      terminationGracePeriodSeconds: 30
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchLabels:
                    app: zhiyu-backend
                topologyKey: kubernetes.io/hostname
      containers:
        - name: zhiyu-backend
          image: registry.cn-hangzhou.aliyuncs.com/zhiyu/zhiyu-backend:latest
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
              protocol: TCP
          envFrom:
            - configMapRef:
                name: zhiyu-backend-config
            - secretRef:
                name: zhiyu-backend-secrets
          env:
            - name: JDK_JAVA_OPTIONS
              value: >-
                -XX:+UseZGC
                -XX:MaxRAMPercentage=75.0
                -XX:+ExitOnOutOfMemoryError
                -Djava.security.egd=file:/dev/./urandom
          resources:
            requests:
              cpu: 1000m
              memory: 2048Mi
            limits:
              cpu: 2000m
              memory: 4096Mi
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 15
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 10
          lifecycle:
            preStop:
              exec:
                command: ["/bin/sh", "-c", "sleep 15"]
```

### 5.2 Service

```yaml
# k8s/release/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: zhiyu-backend
  namespace: release
spec:
  type: ClusterIP
  sessionAffinity: ClientIP
  sessionAffinityConfig:
    clientIP:
      timeoutSeconds: 300
  ports:
    - name: http
      port: 8080
      targetPort: 8080
      protocol: TCP
  selector:
    app: zhiyu-backend
```

### 5.3 HorizontalPodAutoscaler

```yaml
# k8s/release/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: zhiyu-backend
  namespace: release
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: zhiyu-backend
  minReplicas: 2
  maxReplicas: 12
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Percent
          value: 50
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 120
```

### 5.4 ConfigMap（业务配置示例）

```yaml
# k8s/release/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: zhiyu-backend-config
  namespace: release
data:
  application-release.yml: |
    spring:
      profiles:
        active: release
    zhiyu:
      quota:
        free:
          daily-chat: 10
          file-upload-mb: 5
        lite:
          daily-chat: 200
          file-upload-mb: 50
          image-gen-daily: 20
        pro:
          daily-chat: -1
          file-upload-mb: 500
          image-gen-daily: 200
      security:
        login-max-attempts: 5
        login-lock-minutes: 15
        bcrypt-cost-factor: 12
      payment:
        order-expire-minutes: 30
        refund-window-days: 7
```

### 5.5 Secret（密钥引用）

```yaml
# k8s/release/secret.yaml — 手动创建，不提交 Git
# kubectl create secret generic zhiyu-backend-secrets \
#   --from-file=jwt-private-key.pem \
#   --from-literal=DB_PASSWORD='xxx' \
#   --from-literal=REDIS_PASSWORD='xxx' \
#   --from-literal=WECHAT_APP_SECRET='xxx' \
#   --from-literal=ALIPAY_PRIVATE_KEY='xxx' \
#   -n release
---
apiVersion: v1
kind: Secret
metadata:
  name: zhiyu-backend-secrets
  namespace: release
type: Opaque
# 实际值通过 kubectl create secret 或 SealedSecret 注入
```

### 5.6 Kustomize 目录结构

```
k8s/
├── base/
│   ├── kustomization.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   └── hpa.yaml
├── overlays/
│   ├── test/
│   │   ├── kustomization.yaml
│   │   ├── configmap.yaml
│   │   └── ingress.yaml
│   ├── staging/
│   │   ├── kustomization.yaml
│   │   ├── configmap.yaml
│   │   └── ingress.yaml
│   └── release/
│       ├── kustomization.yaml
│       ├── configmap.yaml
│       ├── ingress.yaml
│       └── hpa-patch.yaml
```

### 5.7 Ingress

```yaml
# k8s/release/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: zhiyu-backend
  namespace: release
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: "10m"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "60"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "60"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - zhiyu.example.com
        - api.zhiyu.example.com
      secretName: zhiyu-tls
  rules:
    - host: api.zhiyu.example.com
      http:
        paths:
          - path: /api/v1/
            pathType: Prefix
            backend:
              service:
                name: zhiyu-backend
                port:
                  number: 8080
          - path: /actuator/health
            pathType: Exact
            backend:
              service:
                name: zhiyu-backend
                port:
                  number: 8080
    - host: zhiyu.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: admin-web
                port:
                  number: 80
```

### 5.8 NetworkPolicy

```yaml
# k8s/release/network-policy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: zhiyu-backend
  namespace: release
spec:
  podSelector:
    matchLabels:
      app: zhiyu-backend
  policyTypes:
    - Ingress
    - Egress
  ingress:
    # 仅允许来自 Ingress Controller 的流量
    - from:
        - namespaceSelector:
            matchLabels:
              name: ingress-nginx
          podSelector:
            matchLabels:
              app.kubernetes.io/name: ingress-nginx
      ports:
        - protocol: TCP
          port: 8080
    # 允许 Prometheus scrape
    - from:
        - namespaceSelector:
            matchLabels:
              name: monitoring
      ports:
        - protocol: TCP
          port: 8080
  egress:
    # 允许访问 MySQL RDS
    - to:
        - ipBlock:
            cidr: 10.0.1.0/24  # RDS 子网
      ports:
        - protocol: TCP
          port: 3306
    # 允许访问 Redis Sentinel
    - to:
        - ipBlock:
            cidr: 10.0.2.0/24  # Redis 子网
      ports:
        - protocol: TCP
          port: 6379
    # 允许访问 Nacos
    - to:
        - podSelector:
            matchLabels:
              app: nacos
      ports:
        - protocol: TCP
          port: 8848
        - protocol: TCP
          port: 9848
    # 允许访问外部 API（支付/推送/邮件/短信）
    - to:
        - ipBlock:
            cidr: 0.0.0.0/0
            except:
              - 10.0.0.0/8
              - 172.16.0.0/12
              - 192.168.0.0/16
      ports:
        - protocol: TCP
          port: 443
    # DNS 查询
    - to:
        - namespaceSelector: {}
          podSelector:
            matchLabels:
              k8s-app: kube-dns
      ports:
        - protocol: UDP
          port: 53
```

### 5.9 PodSecurityContext

```yaml
# deployment.yaml 中的 securityContext 段
spec:
  template:
    spec:
      # Pod 级安全上下文
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        runAsGroup: 1000
        fsGroup: 1000
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: zhiyu-backend
          # 容器级安全上下文
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop:
                - ALL
            # /tmp 需要可写（JVM heap dump, 临时文件）
            # 通过 emptyDir 挂载可写目录
          volumeMounts:
            - name: tmp
              mountPath: /tmp
            - name: logs
              mountPath: /app/logs
      volumes:
        - name: tmp
          emptyDir:
            sizeLimit: 512Mi
        - name: logs
          emptyDir:
            sizeLimit: 2Gi
```

### 5.10 Namespace 资源配额

```yaml
# k8s/release/namespace.yaml
---
apiVersion: v1
kind: Namespace
metadata:
  name: release
  labels:
    environment: production
    app: zhiyu
---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: zhiyu-quota
  namespace: release
spec:
  hard:
    requests.cpu: "16"
    requests.memory: "32Gi"
    limits.cpu: "32"
    limits.memory: "64Gi"
    persistentvolumeclaims: "5"
    count/secrets: "30"
    count/configmaps: "20"
---
apiVersion: v1
kind: LimitRange
metadata:
  name: zhiyu-limits
  namespace: release
spec:
  limits:
    - type: Container
      default:
        cpu: 1000m
        memory: 2048Mi
      defaultRequest:
        cpu: 500m
        memory: 1024Mi
      max:
        cpu: 4000m
        memory: 8192Mi
      min:
        cpu: 100m
        memory: 256Mi
    - type: Pod
      max:
        cpu: 8000m
        memory: 16Gi
```

### 5.11 备份 CronJob

```yaml
# k8s/release/cronjob-db-backup.yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: db-backup
  namespace: release
spec:
  schedule: "0 20 * * *"  # 每日凌晨 4:00 CST
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 7
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      backoffLimit: 2
      template:
        spec:
          securityContext:
            runAsNonRoot: true
            runAsUser: 999  # mysqlbackup user
          containers:
            - name: backup
              image: registry.cn-hangzhou.aliyuncs.com/zhiyu/db-backup:latest
              env:
                - name: DB_HOST
                  valueFrom:
                    secretKeyRef:
                      name: zhiyu-backend-secrets
                      key: DB_HOST
                - name: DB_PASSWORD
                  valueFrom:
                    secretKeyRef:
                      name: zhiyu-backend-secrets
                      key: DB_PASSWORD
                - name: BACKUP_BUCKET
                  value: oss://zhiyu-backups
              command:
                - /bin/sh
                - -c
                - |
                  DATE=$(date +%Y%m%d_%H%M%S)
                  mysqldump -h $DB_HOST -u zhiyu -p$DB_PASSWORD zhiyu > /backup/zhiyu_${DATE}.sql
                  # 上传至 OSS
                  ossutil cp /backup/zhiyu_${DATE}.sql $BACKUP_BUCKET/db/zhiyu_${DATE}.sql
                  # 清理本地临时文件
                  rm /backup/zhiyu_${DATE}.sql
              volumeMounts:
                - name: backup-tmp
                  mountPath: /backup
          volumes:
            - name: backup-tmp
              emptyDir:
                sizeLimit: 5Gi
          restartPolicy: OnFailure
```

### 5.12 Docker 镜像安全加固

除多阶段构建和 `readOnlyRootFilesystem` 外，生产镜像还应满足：

```dockerfile
# Dockerfile (项目根目录) 中的安全措施

# 基础镜像选择
FROM eclipse-temurin:21-jre-alpine  # 选 JRE 而非 JDK，减少攻击面

# 创建非 root 用户
RUN addgroup -S zhiyu && adduser -S zhiyu -G zhiyu

# 设置文件权限 — 分层 JAR，减少单层变更范围
COPY --from=builder --chown=zhiyu:zhiyu /extracted/dependencies/ ./
COPY --from=builder --chown=zhiyu:zhiyu /extracted/spring-boot-loader/ ./
COPY --from=builder --chown=zhiyu:zhiyu /extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=zhiyu:zhiyu /extracted/application/ ./

# 用户切换（必须在 COPY 之后）
USER zhiyu

# K8s 安全上下文增强:
# - readOnlyRootFilesystem: true → Deployment volumeMounts 处理 /tmp
# - runAsNonRoot: true, runAsUser: 1000
# - seccompProfile: RuntimeDefault
# - allowPrivilegeEscalation: false
# - capabilities.drop: [ALL]
```

**镜像安全门禁（Trivy）：**

| 检查项 | 阈值 | CI 行为 |
|--------|:----:|---------|
| CRITICAL 漏洞 | 0 | **阻塞构建** |
| HIGH 漏洞 | ≤ 2 | ⚠️ 告警不阻塞 |
| MEDIUM 漏洞 | ≤ 5 | 记录 |
| 基础镜像类型 | `alpine` / `distroless` | ⚠️ 告警 |

---

## 6. 环境管理

### 6.1 环境配置差异

| 配置项 | kubeadm (本机) | test (ACK) | dev (ACK) | staging (ACK) | release (ACK) |
|--------|:---:|-----------|---------|---------|---------|
| K8s 运行时 | kubeadm + containerd | ACK | ACK | ACK | ACK |
| MySQL | K8s StatefulSet | RDS | K8s StatefulSet | RDS | RDS |
| Redis | K8s StatefulSet | Sentinel | K8s StatefulSet | Sentinel | Sentinel |
| Nacos | ❌ 禁用 | 外部集群 | K8s Deployment | 外部集群 | 外部集群 |
| Monitoring | K8s (Prometheus + Grafana) | ❌ 未部署 | ❌ 未部署 | 阿里云 Prometheus | 阿里云 Prometheus |
| Registry | ❌ (`SKIP_PUSH=true`) | ACR | ACR | ACR | ACR |
| Image Import | `ctr image import` | `docker push` | `docker push` | `docker push` | `docker push` |
| **应用配置** | **dev** | **test** | **staging** | **release** |  |
| DB 连接 | K8s 内 MySQL | RDS MySQL | RDS MySQL | RDS MySQL |  |
| Log Level | DEBUG | DEBUG | INFO | WARN |  |
| 限流 QPS | 取消限制 | 取消限制 | 与生产相同 | 按 OPS.md §11 |  |
| 验证码发送 | Mock（不实际发送） | Mock（不实际发送） | Mock（仅白名单） | 实际发送 |  |
| 支付 | 沙箱模式 | 沙箱模式 | 沙箱模式 | 生产模式 |  |

### 6.2 环境部署检查清单

部署前确认：

- [ ] 数据库迁移（Flyway）已执行且成功
- [ ] ConfigMap 中的配置值与目标环境匹配
- [ ] Secret 中的所有密钥已更新且未过期
- [ ] HPA min/max 副本数与目标环境匹配
- [ ] Ingress 域名指向正确
- [ ] 监控面板已加载新版本指标

---

## 7. 集成策略

### 7.1 Maven 模块间集成

```
                    ┌─────────────────────┐
                    │    zhiyu-server      │
                    │  (Spring Boot 装配)   │
                    │  @SpringBootApplication │
                    └──────────┬──────────┘
                               │ 注入
              ┌────────────────┼────────────────┐
              │                │                │
    ┌─────────┴─────┐  ┌──────┴──────┐  ┌─────┴──────────┐
    │ zhiyu-admin    │  │ zhiyu-      │  │ zhiyu-user     │
    │ AdminUserService│  │ subscription│  │ UserService    │
    └───────┬────────┘  └──────┬──────┘  └───────┬────────┘
            │                  │                  │
            │           ┌──────┴──────┐           │
            │           │  zhiyu-auth │           │
            │           │ TokenService│           │
            │           └──────┬──────┘           │
            │                  │                  │
            └──────────────────┼──────────────────┘
                               │
                     ┌─────────┴─────────┐
                     │   zhiyu-common     │
                     │ (Utils, Exceptions, │
                     │  Security Filter,   │
                     │  ApiResponse)       │
                     └────────────────────┘
```

**集成规则：**
- 模块仅暴露 Service 接口，不暴露 Mapper 或实现类
- 上层模块通过 `@RequiredArgsConstructor` 构造器注入下层 Service
- 跨模块事务：上层 `@Transactional` 管理跨模块调用
- Maven `maven-enforcer-plugin` 强制依赖方向（`enforce-dependency-convergence`）

### 7.2 API 合约测试

模块间通过 Spring Cloud Contract 或手写合约测试确保接口兼容性：

```java
// Contract test example — auth module
@SpringBootTest(classes = AuthTestConfig.class)
class AuthServiceContractTest {

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("TokenService.refreshToken: 有效 refreshToken 返回新 token 对")
    void refreshToken_ValidToken_ReturnsNewPair() {
        // Arrange
        String oldToken = authService.login("testuser", "SecureP@ss1").getRefreshToken();
        // Act
        TokenPair pair = authService.refreshToken(oldToken, "device-001");
        // Assert
        assertNotNull(pair.getAccessToken());
        assertNotNull(pair.getRefreshToken());
        assertNotEquals(oldToken, pair.getRefreshToken());
    }

    @Test
    @DisplayName("TokenService.refreshToken: 重用已轮换的 token 抛出盗用异常")
    void refreshToken_ReusedToken_ThrowsTheftException() {
        // Arrange
        String original = authService.login("testuser", "P@ssword1").getRefreshToken();
        String newToken = authService.refreshToken(original, "device-001").getRefreshToken();
        // Act & Assert
        assertThrows(TokenTheftException.class, () -> {
            authService.refreshToken(original, "device-001"); // 重放旧 token
        });
    }
}
```

### 7.3 外部系统集成测试

| 外部系统 | 集成测试方式 | 配置 |
|---------|------------|------|
| MySQL | Testcontainers `MySQLContainer` | 真实 MySQL 8.0 |
| Redis | Testcontainers `RedisContainer` | 真实 Redis 7.x |
| 微信支付 | Mock Server (WireMock) | `WECHAT_API_URL=http://localhost:8090` |
| 支付宝 | Mock Server (WireMock) | `ALIPAY_API_URL=http://localhost:8091` |
| 邮件/短信 | Mock（记录调用而非实际发送） | `@MockBean` |
| Apple/Google IAP | Mock（返回验证成功的 JSON） | WireMock |

**Testcontainers 共享策略：**
```java
// 全局单例的容器实例，所有集成测试共享，加速启动
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("zhiyu_test")
        .withReuse(true);  // 跨测试类复用

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379)
        .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

---

## 8. 制品管理

### 8.1 制品清单

| 制品 | 存储位置 | 保留策略 |
|------|---------|---------|
| JAR 包 (`zhiyu-server-*.jar`) | GitHub Release Assets | 最近 10 个版本 |
| Docker 镜像 | 阿里云 ACR | 30 天 + latest 永久 |
| Maven 依赖缓存 | 本地 Nexus (`http://192.168.0.105:8081`) | 由 Nexus 管理 |
| JaCoCo 覆盖率报告 | GitHub Actions Artifacts | 90 天 |
| OWASP DC 报告 | GitHub Actions Artifacts | 30 天 |
| Trivy 扫描报告 | GitHub Security Tab | 永久 |
| Flyway 迁移历史 | DB 表 `flyway_schema_history` | 永久 |

### 8.2 Nexus Maven 私有制品仓库

本地 Nexus Repository v3 用于缓存 Maven Central 依赖，加速 CI/CD 流水线和本地构建。

**访问方式：**
- 控制台：`http://192.168.0.105:8081` | 账号 `admin` / `admin123`
- Maven 仓库地址：`http://192.168.0.105:8081/repository/maven-public/`

**仓库结构：**

| 仓库 | 类型 | 说明 |
|------|------|------|
| `maven-aliyun` | proxy | 代理阿里云 Maven 镜像（国内优先） |
| `maven-central` | proxy | 代理 Maven Central（海外兜底） |
| `maven-releases` | hosted | 项目内部 Release 制品 |
| `maven-snapshots` | hosted | 项目内部 Snapshot 制品 |
| `maven-public` | group | 组合以上所有仓库的统一入口 |

**Maven 配置** (`backend/.mvn/maven.config` + `backend/.mvn/settings.xml`) 已自动将 Nexus 设为镜像源。

**CI/CD 流水线中的 Maven 步骤会通过 `maven-public` 拉取依赖**，首次请求触发 Nexus 缓存下载，后续请求命中缓存（无需重复访问外部网络）。

### 8.3 Maven 版本管理

```xml
<!-- pom.xml: 版本号集中管理 -->
<properties>
    <revision>1.0.0-SNAPSHOT</revision>
    <changelist>-SNAPSHOT</changelist>

    <!-- 内部模块 -->
    <zhiyu-common.version>${revision}</zhiyu-common.version>

    <!-- 关键依赖版本 -->
    <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
    <mybatis-plus.version>3.5.7</mybatis-plus.version>
    <jjwt.version>0.12.6</jjwt.version>
</properties>
```

**发布时：** `mvn versions:set -DnewVersion=1.0.0 -DremoveSnapshot=true`

---

## 9. 流水线优化

### 9.1 编译加速

| 手段 | 效果 | 配置 |
|------|------|------|
| Maven 并行编译 | -40% 时间 | `-T 1C` (每 CPU 核心一个线程) |
| Maven 离线模式 (package) | -30% 时间 | `-o` (放依赖已缓存后) |
| GitHub Actions 缓存 | 跳过依赖下载 | `actions/cache@v4` on `~/.m2` |
| Testcontainers 容器复用 | 测试启动 -20s | `withReuse(true)` |
| Gradle (备选) | 增量编译 + 构建缓存 | 后续评估替代 Maven |

### 9.2 并行 Job 执行

```
ci.yml:
  ┌────────────┐
  │ build-and-  │ ──▶ package ──▶ deploy-test ──▶ deploy-release
  │   test      │
  └────────────┘
        │
        └── (并行) ──▶ 上传覆盖率报告 (always)

frontend-ci.yml:
  ┌────────────┐    ┌────────────┐
  │ lint + test│    │ axe a11y   │  (并行)
  └────────────┘    └────────────┘
        │
        └── build ──▶ upload OSS
```

### 9.3 增量构建

只改文档/配置文件时跳过完整 CI：
- `docs/**` 变更 → 跳过 build，仅运行 markdown lint
- `k8s/**` 变更 → 仅运行 `kubeval` 校验
- `*.md` 变更 → 跳过全部 CI (在 `paths-ignore` 中)

---

> **引用索引**：
> - [OPS.md §5](OPS.md#5-发布流程) — 发布流程操作步骤
> - [ARCHITECTURE.md §5](ARCHITECTURE.md#5-部署架构) — K8s 拓扑
> - [DEVELOPMENT-STANDARDS.md §11](DEVELOPMENT-STANDARDS.md#11-ci-检查清单) — CI 检查清单
> - [SECURITY.md §1](SECURITY.md#1-安全测试计划) — 安全扫描 CI 集成
> - [TEST-PLAN.md](TEST-PLAN.md) — E2E 测试场景
