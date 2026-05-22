# ZhiYu 安全测试与数据合规

> **⚠️ 当前实现状态：本文档为安全设计规格，对应代码尚未开始编写。** `ufp-common` 中无安全过滤器、无 JWT 工具类、无密码哈希工具。CI 安全工具链也仅配置了基础 SpotBugs，FindSecBugs 插件和 OWASP Dependency-Check 尚未接入。个保法（PIPL）清单中标记为 `[x]` 的项目前无法验证。以下内容作为开发时的安全需求参考。

> 本文档补充 [设计规格](superpowers/specs/2026-05-17-zhiyu-backend-design.md) §11 的安全设计和 [TEST-PLAN.md](TEST-PLAN.md) 的测试计划，聚焦安全测试方案、依赖漏洞管理和数据合规（中国 PIPL）要求。

## 1. 安全测试计划

### 1.1 测试矩阵

| 测试类型 | 工具 | 频率 | 执行者 | CI 集成 |
|---------|------|:----:|--------|:-------:|
| SAST (静态代码分析) | SpotBugs + FindSecBugs | 每次 PR | CI | ✅ |
| SCA (依赖扫描) | OWASP Dependency-Check | 每日 + 每次 PR | CI | ✅ |
| DAST (动态扫描) | OWASP ZAP | 每迭代一次 | 安全工程师 | ❌(手动) |
| 密钥扫描 | truffleHog / Gitleaks | 每次 PR | CI | ✅ |
| 渗透测试 | 人工 + Burp Suite | 每季度 + 大版本前 | 外聘安全团队 | ❌ |
| 认证模块专项 | 人工（基于 OWASP ASVS V2） | 每迭代 | QA | ❌ |
| API Fuzzing | RESTler / Postman Fuzzer | 每迭代 | QA | ❌ |

### 1.2 SAST — SpotBugs 配置

```xml
<!-- pom.xml: spotbugs-maven-plugin 配置 -->
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.8.6</version>
    <configuration>
        <effort>Max</effort>
        <threshold>Low</threshold>
        <plugins>
            <plugin>
                <groupId>com.h3xstream.findsecbugs</groupId>
                <artifactId>findsecbugs-plugin</artifactId>
                <version>1.13.0</version>
            </plugin>
        </plugins>
    </configuration>
    <executions>
        <execution>
            <goals><goal>check</goal></goals>
        </execution>
    </executions>
</plugin>
```

**关注规则：**
- SQL 注入检测 (`SQL_INJECTION`)
- 硬编码密码 (`HARD_CODE_PASSWORD`)
- XSS 污染 (`XSS_SERVLET`)
- 路径穿越 (`PATH_TRAVERSAL_IN`)
- SSL 主机名校验关闭 (`WEAK_HOSTNAME_VERIFIER`)
- 弱加密算法 (`WEAK_MESSAGE_DIGEST`)

### 1.3 SCA — 依赖漏洞扫描

```xml
<!-- pom.xml: OWASP Dependency-Check -->
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>10.0.4</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>  <!-- CVSS ≥7 构建失败 -->
        <formats><format>HTML</format><format>JSON</format></formats>
        <suppressionFiles>
            <suppressionFile>owasp-suppressions.xml</suppressionFile>
        </suppressionFiles>
    </configuration>
    <executions>
        <execution>
            <goals><goal>aggregate</goal></goals>
        </execution>
    </executions>
</plugin>
```

**前端：**
```bash
# CI 中
npm audit --audit-level=high
# 或使用 GitHub Dependabot / Renovate 自动提 PR
```

**扫描频率：**
- CI: 每次 `mvn verify` 执行
- 定时: 每日凌晨 5:00 全量扫描 (Woodpecker CI cron)
- 发现 CVE ≥ 7 → 立即修复或添加抑制规则 + 说明理由

### 1.4 DAST — OWASP ZAP 基线扫描

```bash
# ZAP 基线扫描命令 (在 test 环境执行)
docker run -t owasp/zap2docker-stable zap-baseline.py \
  -t https://zhiyu-test.example.com \
  -z "-config api.disablekey=true" \
  -r zap-report.html

# 在 CI 脚本中每天凌晨执行一次（仅 test 环境）
```

**扫描范围：**
- 所有公开端点 (`/auth/**`)
- 所有认证端点 (`/user/**`, `/sub/**`)
- Admin API (`/api/v1/admin/**`) — 需手动注入 Auth Cookie

**退出码判断：**
- 0: 无告警 → 通过
- 1: 含风险但无 FAIL 级别的 → 告警邮件，不阻塞
- 2+: 1 个以上 FAIL → 阻塞发布

### 1.5 密钥扫描

```yaml
# .github/workflows/secret-scan.yml
name: Secret Scan
on: [push, pull_request]
jobs:
  scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0  # 全历史，检测历史提交中的密钥
      - uses: gitleaks/gitleaks-action@v2
        with:
          config-path: .gitleaks.toml
```

`.gitleaks.toml` 中配置：
- JWT 密钥模式
- 阿里云 AccessKey 模式
- 微信 AppSecret 模式
- 数据库连接字符串模式

### 1.6 认证模块专项测试

基于 OWASP ASVS V2 的测试清单：

| ID | 测试项 | 预期 |
|----|--------|------|
| ASVS-2.1.1 | BCrypt cost factor ≥ 12 | ✅ |
| ASVS-2.1.2 | 密码最小长度 8，最大 128 | ✅ |
| ASVS-2.1.3 | 密码强度检测（至少 3/4 字符类）| ✅ |
| ASVS-2.2.1 | 注册后发送邮箱验证 | ✅ |
| ASVS-2.2.2 | 账号锁定（5 次失败 → 15 分钟）| ✅ |
| ASVS-2.2.3 | 登录失败消息一致（不透露是否存在用户）| 需验证 |
| ASVS-2.3.1 | TOTP 启用后登录验证 | ✅ |
| ASVS-2.3.2 | 恢复码为随机 8 位、一次性 | ✅ |
| ASVS-2.4.1 | Refresh Token 轮换 + 盗用检测 | ✅ |
| ASVS-2.4.2 | 登出后 Token 进入黑名单 | ✅ |
| ASVS-2.5.1 | 密码重置 Token 15 分钟有效、一次性 | ✅ |
| ASVS-2.5.2 | 密码重置后清除所有活跃会话 | ✅ |
| ASVS-2.6.1 | 第三方 OAuth state 参数验证 (防 CSRF) | ✅ |

### 1.7 渗透测试范围（季度）

```
测试范围:
1. 认证绕过 (认证接口 + Token 接口)
2. 授权提升 (scope=LIMITED 不能访问 FULL 接口)
3. 参数污染 (重复参数、缺失参数、类型错误)
4. JWT 攻击 (None alg、弱密钥、过期 token)
5. IDOR (用户 A 能否访问用户 B 的数据)
6. 越权 (CS 角色能否执行 ADMIN 操作)
7. 支付欺诈 (修改支付金额、伪造回调签名、重放攻击)
8. 限流绕过 (X-Forwarded-For 篡改、分布式暴力破解)
9. SSRF (外部 URL 输入、Webhook 回调地址)
10. 文件上传 (恶意文件类型、超大文件、路径穿越)
```

---

## 2. 密钥管理策略

### 2.0 默认账户与密码初始化

所有密码由 `deploy/scripts/ensure-secrets.sh` 在首次部署时自动生成，使用 `openssl rand -hex` 产生密码学安全的随机值。生成后的密码持久化到 `deploy/secrets/<env>/passwords.env`（已加入 `.gitignore`），后续部署复用同一密码。

**严禁使用上游软件默认密码部署到任何环境。**

| 组件 | 默认用户名 | 密码来源 | 生成命令 | 熵 |
|------|----------|---------|---------|:--:|
| MySQL root | `root`（MySQL 内置，不可更改） | ensure-secrets.sh | `openssl rand -hex 24` | 192-bit |
| MySQL 应用用户 | `zhiyu`（`MYSQL_USER` env var） | ensure-secrets.sh | `openssl rand -hex 16` | 128-bit |
| Redis | 无用户名（仅密码认证） | ensure-secrets.sh | `openssl rand -hex 16` | 128-bit |
| Nacos | `nacos`（Nacos 默认，不可更改） | ensure-secrets.sh | `openssl rand -hex 12` | 96-bit |
| Nacos 服务间认证 | `NACOS_IDENTITY_KEY` + `NACOS_IDENTITY_VALUE` | ensure-secrets.sh | `openssl rand -hex 16` | 128-bit |
| Grafana | `admin`（Grafana 默认，不可更改） | ensure-secrets.sh | `openssl rand -hex 12` | 96-bit |
| JWT 签名密钥 | N/A（RSA 密钥对） | ensure-secrets.sh | `openssl genpkey -algorithm RSA 2048` | ~112-bit |

**强制 fail-closed 原则：** 所有 `secretKeyRef` 不得使用 `optional: true`。若 K8s Secret 缺失或 key 不存在，Pod 必须 CrashLoopBackOff，禁止在无密码状态下启动。

**文件系统权限：**

| 文件 | 权限 | 用户:属组 | 说明 |
|------|:----:|-----------|------|
| `deploy/*.sh` | `755` | owner:group | 可执行脚本，所有人可读+执行，仅 owner 可写 |
| `deploy/scripts/*.sh` | `755` | owner:group | 同上 |
| `deploy/**/*.yaml` | `644` | owner:group | K8s 清单，所有人可读，仅 owner 可写 |
| `deploy/envs/*.env` | `644` | owner:group | 环境变量模板（不含敏感信息），所有人可读 |
| `deploy/secrets/<env>/passwords.env` | `600` | owner:group | **含明文密码**，仅 owner 可读写 |
| `deploy/secrets/<env>/jwt-private.pem` | `600` | owner:group | **JWT 签名私钥**，仅 owner 可读写 |
| `deploy/secrets/<env>/jwt-public.pem` | `644` | owner:group | JWT 验签公钥，所有人可读 |
| `bootstrap/*.sh` | `755` | owner:group | 离线部署脚本，所有人可读+执行 |
| `deploy/secrets/<env>/` (目录) | `700` | owner:group | 密钥目录，仅 owner 可访问 |

> `ensure-secrets.sh` 在生成密码文件和 JWT 密钥时自动设置上述权限。未在表中的文件默认为 `644`（非可执行）或 `755`（可执行脚本）。

**密码流转路径：**
```
ensure-secrets.sh → deploy/secrets/<env>/passwords.env → K8s Secret → Pod env var
                    （持久化 + gitignored + chmod 600）   （base64）      （明文注入容器）
```

### 2.1 密钥分类

| 密钥类型 | 存储位置 | 轮换周期 | 轮换方式 |
|---------|---------|:------:|---------|
| JWT 私钥 (RS256) | K8s Secret | 180 天 | 生成新密钥对 → 更新 Secret → 滚动重启 Pod |
| 数据库密码 | K8s Secret | 90 天 | 更新 DB 密码 → 更新 Secret → 滚动重启 |
| Redis 密码 | K8s Secret | 90 天 | 同上 |
| 微信 AppSecret | K8s Secret | 按微信平台规定 | 在微信开放平台重置 → 更新 Secret |
| 支付宝私钥 | K8s Secret | 365 天 | 生成新密钥对 → 更新支付宝平台公钥 → 更新 Secret |
| Apple Shared Secret | K8s Secret | 按需要 | 在 App Store Connect 生成 → 更新 Secret |
| OSS AccessKey | K8s Secret | 90 天 | 创建新 RAM 用户 → 更新 Secret → 删除旧用户 |
| 备份加密密钥 | K8s Secret | 365 天 | 生成新密钥 → 重新加密备份 |

### 2.2 JWT 密钥轮换流程

```
1. 生成新密钥对：
   openssl genrsa -out private_key_new.pem 2048
   openssl rsa -in private_key_new.pem -pubout -out public_key_new.pem

2. 更新 K8s Secret:
   kubectl create secret generic jwt-keys \
     --from-file=private_key_new.pem \
     --from-file=public_key_new.pem \
     -n release --dry-run=client -o yaml | kubectl apply -f -

3. 滚动重启（逐个 Pod 更新）:
   kubectl rollout restart deployment/zhiyu-backend -n release

4. 旧密钥保留 24 小时（兼容旧 token）后移除
```

---

## 3. 数据合规 (中国 PIPL)

### 3.1 数据分类分级

| 数据类别 | 级别 | 示例 | 存储 | 加密 | 保留期 |
|---------|:----:|------|------|:--:|:------:|
| 普通业务数据 | L1 | 昵称、头像、套餐名称 | MySQL + OSS | ❌ | 永久 |
| 个人标识数据 | L2 | 用户名、脱敏后 email/phone | MySQL | ❌(表级) | 用户注销后30天 |
| 个人敏感数据 | L3 | 完整邮箱、手机号、密码 hash | MySQL | ✅(TDE) | 用户注销后30天 |
| 认证凭证 | L4 | TOTP secret、refresh_token | Redis + MySQL | ✅(应用层) | 按需/过期 |
| 支付财务数据 | L4 | 支付记录、退款记录 | MySQL | ✅(TDE) | 按法律 ≥5年 |
| 审计日志 | L3 | 登录日志、操作审计 | MySQL | ✅(TDE) | ≥6个月(网安法) |

> TDE = MySQL Transparent Data Encryption (或应用层 AES-256-GCM 加密)

### 3.2 PIPL 合规检查清单

- [x] **告知-同意原则**: 注册时明确展示隐私政策 + 勾选同意
- [x] **最小必要原则**: 仅收集完成服务所需的最少个人信息
- [x] **目的限制**: email 仅用于账户验证和通知，不用于营销（除非用户单独同意）
- [x] **数据删除权**: 用户注销后 30 天内可恢复，之后永久匿名化
- [x] **数据可携权**: 用户可导出个人数据 (CSV)
- [ ] **数据出境评估**: 如使用 Google/Apple OAuth，用户 openid/email 传输至境外 → 需做个人信息保护影响评估 (PIA)
- [ ] **敏感个人信息单独同意**: 如收集人脸/指纹 (WebAuthn 仅存公钥，不触发生物信息法律约束，需确认)
- [x] **数据安全事件通知**: 发现泄露后 72 小时内通知网信办 + 受影响用户
- [x] **处理记录**: `audit_log` 记录所有个人信息的收集、使用、删除操作
- [ ] **数据保护官 (DPO)**: 需指定联系人并公布联系方式

### 3.3 个人信息保护影响评估 (PIA)

需对以下场景进行 PIA（上线前完成）：

| 场景 | 涉及数据 | 风险等级 | 评估状态 |
|------|---------|:------:|:------:|
| 第三方登录（微信/QQ/Google/Apple） | openid, email, 昵称, 头像 | 中 | 待评估 |
| 邮箱验证码发送（阿里云邮件推送） | email | 低 | 待评估 |
| 短信发送（阿里云 SMS） | 手机号 | 中 | 待评估 |
| 支付处理（微信/支付宝/Apple/Google） | 支付记录 | 中 | 待评估 |
| 推送通知（FCM/APNs/个推） | device_token | 低 | 待评估 |
| IP 地理位置（GeoLite2 离线库） | IP 地址 | 低 | ✅ 离线处理，不出境 |

### 3.4 数据安全事件响应

```
发现泄露
  ├── 1h 内: 通知 DPO + CTO
  ├── 4h 内: 确定泄露范围和影响用户数
  ├── 24h 内: 修复漏洞 + 重置受影响凭证
  ├── 72h 内: 向网信办报告 + 通知受影响用户
  └── 1w 内: 事后复盘 + 修订安全策略
```

通知用户内容模板：
```
"ZhiYu 安全通知:
我们发现 [时间] 存在安全事件，可能影响了您的 [数据类型]。
我们已采取以下措施: [措施]。
建议您: [用户应做的操作，如修改密码]。
如有疑问请联系: privacy@zhiyu.app"
```

### 3.5 日志数据脱敏

```java
// logback-spring.xml 中配置脱敏转换器
// 所有日志中的以下模式自动替换:
// 手机号: 138****1234
// 邮箱:   u***@domain.com
// IP:     192.168.1.***
// Token:  eyJ... (仅保留前 8 字符)
```

---

## 4. 安全 CI 检查清单

| 检查项 | 工具 | 阶段 | 阻塞发布? |
|--------|------|------|:--------:|
| 静态代码分析 | SpotBugs + FindSecBugs | PR | ✅ |
| 依赖漏洞扫描 | OWASP DC (CVSS ≥ 7) | PR + 每日 | ✅ |
| 密钥泄漏扫描 | Gitleaks | PR | ✅ |
| 前端依赖审计 | `npm audit --audit-level=high` | PR | ✅ |
| ZAP 基线扫描 | OWASP ZAP | test 环境 | ❌(告警) |
| Docker 镜像扫描 | Trivy / Grype | 构建 | ⚠️(Medium+) |
| 渗透测试 | 人工 | 季度 | ✅(Block 级) |

---

## 5. 安全检查表（发布前勾选）

每次生产发布前，安全工程师确认：

- [ ] 所有 P0/P1 依赖漏洞已修复或已评估风险
- [ ] SpotBugs 无新增 HIGH 级别告警
- [ ] 认证模块无已知绕过方式
- [ ] 支付相关代码变更已在沙箱环境验证
- [ ] 配置变更（支付/安全相关）已 review
- [ ] 密钥未到期（JWT 私钥 / DB 密码 / 第三方 AppSecret）
- [ ] ZAP 基线扫描无新增 FAIL 告警
- [ ] 渗透测试(季度) Block 级问题已修复
