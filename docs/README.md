# ZhiYu-Backend 文档索引

> 本文档是 `docs/` 目录的中心索引入口，按产品生命周期阶段组织所有设计文档。

## 阅读顺序（新人 Onboarding）

1. [PRD.md](product-design/PRD.md) — 了解产品要做什么
2. [ARCHITECTURE.md](product-design/ARCHITECTURE.md) — 理解系统架构决策
3. [DEVELOPMENT-STANDARDS.md](dev-test/DEVELOPMENT-STANDARDS.md) — 掌握编码规范
4. [DATABASE.md](product-design/DATABASE.md) — 熟悉数据模型
5. [API-SPEC.md](product-design/API-SPEC.md) — 查阅 API 接口
6. [DEPLOYMENT.md](deploy-ops/DEPLOYMENT.md) — 搭建开发/生产环境

---

## 产品与设计 `product-design/`

产品需求、架构决策、数据模型、接口规格、前端/App 设计、UFP 模块规划。

| 文档 | 描述 | 状态 |
|------|------|------|
| [PRD.md](product-design/PRD.md) | 产品需求文档：功能范围、用户故事、P0/P1/P2 优先级 | ✅ 已完成 |
| [ARCHITECTURE.md](product-design/ARCHITECTURE.md) | 4+1 视图、L0-L2 分层、时序设计、部署拓扑 | ✅ 已完成 |
| [ARCHITECTURE-UFP.md](product-design/ARCHITECTURE-UFP.md) | UFP 平台模块架构设计（平台级，不绑定 ZhiYu） | ✅ 已完成 |
| [ADR.md](product-design/ADR.md) | 架构决策记录（ADR-001 ~ ADR-013） | ✅ 已完成 |
| [API-SPEC.md](product-design/API-SPEC.md) | REST API 接口规格：请求/响应格式、校验规则、错误码 | ✅ 已完成 |
| [DATABASE.md](product-design/DATABASE.md) | 数据库设计：37 张表 DDL、ER 关系、索引、Flyway 迁移（`zhiyu` + `ufp_auth` 双库） | ✅ 已完成 |
| [FRONTEND-DESIGN.md](product-design/FRONTEND-DESIGN.md) | 管理后台前端设计：12+ 页面组件树、状态管理、API 类型生成 | ⚠️ 设计完成，代码为空 |
| [APP-DESIGN.md](product-design/APP-DESIGN.md) | iOS/Android 原生 App 设计：SwiftUI + Jetpack Compose 页面规格 | ⚠️ 设计完成，代码未开始 |
| [DESIGN-UFP-COMMON.md](product-design/reference/DESIGN-UFP-COMMON.md) | UFP 共享基础设施：9 个子模块（基于 NTA `sfp-commons`） | 🔒 暂不启动 |
| [DESIGN-UFP-AUTH.md](product-design/reference/DESIGN-UFP-AUTH.md) | UFP 认证授权：OIDC Provider、RBAC、JWT RS256（基于 NTA `sfp-auth`） | 🔒 暂不启动 |
| [DESIGN-UFP-META.md](product-design/reference/DESIGN-UFP-META.md) | UFP 元数据管理：精简至字典管理（基于 NTA `sfp-meta`） | 🔒 暂不启动 |

## 开发与测试 `dev-test/`

编码规范、安全合规、测试策略。

| 文档 | 描述 | 状态 |
|------|------|------|
| [DEVELOPMENT-STANDARDS.md](dev-test/DEVELOPMENT-STANDARDS.md) | 编码规范：分层架构、API 约定、错误码、日志、测试策略 | ✅ 已完成 |
| [SECURITY.md](dev-test/SECURITY.md) | 安全测试方案：OWASP、依赖漏洞扫描、PIPL 合规清单 | ⚠️ 设计完成，代码未实现 |
| [TEST-PLAN.md](dev-test/TEST-PLAN.md) | 测试计划：130+ 用例、覆盖率阈值、单元/集成/E2E 策略 | ⚠️ 设计完成，代码未实现 |

## 部署与运维 `deploy-ops/`

基础设施配置、CI/CD 流水线、部署指南、监控告警、限流策略。

| 文档 | 描述 | 状态 |
|------|------|------|
| [INFRASTRUCTURE.md](deploy-ops/INFRASTRUCTURE.md) | Nacos 配置中心、Redis Key 规范、MySQL 部署架构 | ✅ 已完成 |
| [DEPLOYMENT.md](deploy-ops/DEPLOYMENT.md) | 部署指南：bootstrap、离线打包、K8s 部署、环境变量 | ✅ 已完成 |
| [CI-CD.md](deploy-ops/CI-CD.md) | CI/CD 流水线：Woodpecker CI、Docker 构建、K8s 部署清单 | ✅ 已完成 |
| [OPS.md](deploy-ops/OPS.md) | 运维手册：SLO/SLI、Grafana 监控、告警规则、灾备方案 | ⚠️ 设计完成，待上线验证 |
| [RATE-LIMITING.md](deploy-ops/RATE-LIMITING.md) | 接口限流设计：三层架构、Sentinel + Redis + Caffeine | ⚠️ 设计完成，代码未实现 |

## 状态说明

| 标记 | 含义 |
|------|------|
| ✅ 已完成 | 设计与实现对齐，可投入生产 |
| ⚠️ 设计完成，代码未实现 | 文档已就绪，等待开发 |
| ⚠️ 设计完成，待上线验证 | 代码已实现，待生产环境验证 |
| 🔒 暂不启动 | 设计参考，明确延后 |

## 其他资源

| 目录 | 内容 |
|------|------|
| [prototypes/](prototypes/) | 管理后台 HTML 原型 |
| [superpowers/specs/](superpowers/specs/) | 原始设计规格（`2026-05-17-zhiyu-backend-design.md`） |

> Nacos 配置文件（`feature-flags.yml` 等）已移至 [`deploy/nacos-config/`](../deploy/nacos-config/)，属于部署制品而非设计文档。
