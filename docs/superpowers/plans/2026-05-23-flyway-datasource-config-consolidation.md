# Flyway/DataSource Config Consolidation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove explicit Flyway `url`/`user`/`password` from profile YAMLs so Flyway auto-inherits DataSource properties, eliminating redundant env vars.

**Architecture:** Delete 3 lines from each of 2 YAML files, then clean up 4 keys across the remote ConfigMap and Secret. Spring Boot Flyway auto-configuration handles the rest.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Flyway 10.18.2

---

### Task 1: Remove explicit Flyway config from application-dev.yml

**Files:**
- Modify: `backend/zhiyu-server/src/main/resources/application-dev.yml:57-60`

- [ ] **Step 1: Delete flyway url, user, password lines**

Edit `backend/zhiyu-server/src/main/resources/application-dev.yml` — in the `spring.flyway` block, remove `url`, `user`, `password`.

Before:
```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration
    url: jdbc:mysql://${DB_HOST:10.211.55.4}:${DB_PORT:30306}/zhiyu_dev?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    user: ${DB_USERNAME:zhiyu}
    password: ${DB_PASSWORD:zhiyu123}
    baseline-on-migrate: false
```

After:
```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
```

- [ ] **Step 2: Commit**

```bash
git add backend/zhiyu-server/src/main/resources/application-dev.yml
git commit -m "refactor: remove explicit Flyway url/user/password from dev profile"
```

---

### Task 2: Remove explicit Flyway config from application-release.yml

**Files:**
- Modify: `backend/zhiyu-server/src/main/resources/application-release.yml:64-70`

- [ ] **Step 1: Delete flyway url, user, password lines**

Edit `backend/zhiyu-server/src/main/resources/application-release.yml` — in the `spring.flyway` block, remove `url`, `user`, `password`.

Before:
```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration
    url: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME:zhiyu}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=true&allowPublicKeyRetrieval=false
    user: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

After:
```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration
```

- [ ] **Step 2: Commit**

```bash
git add backend/zhiyu-server/src/main/resources/application-release.yml
git commit -m "refactor: remove explicit Flyway url/user/password from release profile"
```

---

### Task 3: Build JAR and deploy to remote

- [ ] **Step 1: Build the JAR**

```bash
mvn -f backend/pom.xml clean package -DskipTests -q
```

- [ ] **Step 2: Sync and deploy via deploy-to-remote.sh**

```bash
./deploy/deploy-to-remote.sh
```

- [ ] **Step 3: Wait for pod to become Ready**

```bash
ssh -i ~/.ssh/id_ed25519 parallels@10.211.55.4 \
  "sudo kubectl wait --for=condition=Ready pod -l app=zhiyu-backend -n zhiyu-dev --timeout=180s"
```

---

### Task 4: Clean up remote ConfigMap

- [ ] **Step 1: Remove stale keys from ConfigMap**

```bash
ssh -i ~/.ssh/id_ed25519 parallels@10.211.55.4 \
  "sudo kubectl patch configmap zhiyu-backend-config -n zhiyu-dev --type json -p '[
    {\"op\": \"remove\", \"path\": \"/data/DB_USERNAME\"},
    {\"op\": \"remove\", \"path\": \"/data/SPRING_FLYWAY_URL\"},
    {\"op\": \"remove\", \"path\": \"/data/SPRING_FLYWAY_VALIDATE_ON_MIGRATE\"}
  ]'"
```

- [ ] **Step 2: Verify ConfigMap no longer contains stale keys**

```bash
ssh -i ~/.ssh/id_ed25519 parallels@10.211.55.4 \
  "sudo kubectl get configmap zhiyu-backend-config -n zhiyu-dev -o jsonpath='{.data}' | python3 -m json.tool"
```

Expected: `DB_USERNAME`, `SPRING_FLYWAY_URL`, `SPRING_FLYWAY_VALIDATE_ON_MIGRATE` not present.

---

### Task 5: Clean up remote Secret

- [ ] **Step 1: Remove stale key from Secret**

```bash
ssh -i ~/.ssh/id_ed25519 parallels@10.211.55.4 \
  "sudo kubectl patch secret zhiyu-backend-secret -n zhiyu-dev --type json -p '[
    {\"op\": \"remove\", \"path\": \"/data/DB_PASSWORD\"}
  ]'"
```

- [ ] **Step 2: Verify Secret no longer contains DB_PASSWORD**

```bash
ssh -i ~/.ssh/id_ed25519 parallels@10.211.55.4 \
  "sudo kubectl get secret zhiyu-backend-secret -n zhiyu-dev -o jsonpath='{.data}' | python3 -c \"import json,sys; d=json.load(sys.stdin); print([k for k in d.keys()])\""
```

Expected: `DB_PASSWORD` not in the key list.

---

### Task 6: Rolling restart and verify

- [ ] **Step 1: Restart the deployment**

```bash
ssh -i ~/.ssh/id_ed25519 parallels@10.211.55.4 \
  "sudo kubectl rollout restart deployment zhiyu-backend -n zhiyu-dev"
```

- [ ] **Step 2: Wait for new pod to become Ready**

```bash
ssh -i ~/.ssh/id_ed25519 parallels@10.211.55.4 \
  "sudo kubectl wait --for=condition=Ready pod -l app=zhiyu-backend -n zhiyu-dev --timeout=180s"
```

- [ ] **Step 3: Verify health endpoint**

```bash
ssh -i ~/.ssh/id_ed25519 parallels@10.211.55.4 \
  "sudo kubectl exec -n zhiyu-dev deployment/zhiyu-backend -- wget -q -O- http://localhost:8080/actuator/health"
```

Expected: `{"status":"UP","groups":["liveness","readiness"]}`

- [ ] **Step 4: Verify Flyway migrations still intact**

```bash
ssh -i ~/.ssh/id_ed25519 parallels@10.211.55.4 \
  'sudo kubectl exec -n zhiyu-dev mysql-0 -c mysql -- mysql -u root -p"6f04b91bab3c9a1dfa92af04de4a73a2a981f1d98a843a0a" zhiyu_dev -e "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank"'
```

Expected: All versions 1.0.0 through 1.7.0 with `success=1`.

- [ ] **Step 5: Commit final state**

```bash
git add -A
git commit -m "chore: clean up remote K8s config after Flyway consolidation"
```
