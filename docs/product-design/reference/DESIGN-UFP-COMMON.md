# UFP Common 模块设计

> 基于 NTA `sfp-commons` 分析，适配 ZhiYu UFP 平台。
> 本文档为设计参考，暂不启动开发。

## 1. 概述

`ufp-common` 是 UFP 统一基础平台的共享基础设施模块，提供各业务模块通用的工具类、注解、模型和自动配置。对应 NTA 项目 `sfp-commons`（10 个子模块，排除 flink 相关）。

## 2. NTA sfp-commons 分析摘要

| NTA 子模块 | 功能定位 | ZhiYu 是否需要 | 说明 |
|-----------|---------|:---:|------|
| `metadata` | 基础注解/模型/异常/接口定义 | ✅ 保留 | 核心共享层，所有模块依赖 |
| `utils` | 综合工具集（加解密/IP/Excel/PDF/SNMP/SSH/NED） | ⚠️ 裁剪 | IP/网络设备/NED 等网络流量分析工具不需要 |
| `text` | 文本解析 DSL 引擎（70+ 表达式执行器） | ⚠️ 裁剪 | 日志解析功能非当前需求，核心表达式引擎可保留 |
| `api-fit` | HTTP/SSH/Telnet/SNMP 统一客户端抽象 | ⚠️ 精简 | HTTP 客户端保留，SSH/Telnet/SNMP 不需要 |
| `generic` | Spring Boot 自动配置中心（`@EnableSfp`） | ✅ 保留 | 核心装配模块，按需启用组件 |
| `mybatis` | MyBatis 扩展（分页方言/SQL 解析） | ✅ 保留 | 替代为 MyBatis-Plus 等价功能 |
| `captcha` | 验证码图片生成（Patchca 内核） | ✅ 保留 | 注册/登录场景需要 |
| `service-client` | Feign 服务间调用客户端框架 | ✅ 保留 | 微服务间调用 |
| `discovery` | Nacos 服务发现适配 | ✅ 保留 | 依赖 Nacos |
| `dynamic-datasource` | 运行时多数据源路由 | ✅ 保留 | `zhiyu` + `ufp_auth` 双库需要 |

## 3. ufp-common 子模块规划

```
ufp-common/
├── ufp-common-core          # 基础模型、注解、异常、常量（对应 metadata）
├── ufp-common-util          # 通用工具类（裁剪版 utils）
├── ufp-common-captcha       # 验证码生成
├── ufp-common-mybatis       # ORM 扩展（适配 MyBatis-Plus）
├── ufp-common-api           # HTTP 客户端抽象（精简版 api-fit）
├── ufp-common-autoconfigure # Spring Boot 自动配置（对应 generic）
├── ufp-common-feign         # Feign 客户端框架（对应 service-client）
├── ufp-common-discovery     # Nacos 服务发现适配
└── ufp-common-datasource    # 动态数据源路由
```

### 3.1 ufp-common-core

**定位**：零业务依赖的基石模块，所有其他模块的共同依赖。

**内容**：
- 统一响应模型 `R<T>`（替代 NTA `Response<T>`）
- 分页模型 `PageResult<T>`、`PageQuery`
- 业务异常体系 `BusinessException`、`ValidationException`
- 基础标记注解 `@UfpController`、`@UfpService`
- 错误码常量 `ResultCode`
- 校验分组标记接口（`Insert`、`Update`、`Delete`、`Query`）

### 3.2 ufp-common-util

**定位**：通用工具类，从 NTA `utils` 中裁剪。

**保留**：
- 加解密：AES、RSA、BCrypt 适配（复用 Hutool + Commons Codec）
- 集合/反射/Bean 拷贝工具（复用 Hutool）
- 日期时间工具
- IP 地址工具（IPv4/IPv6 校验）
- Spring 上下文工具

**移除**（非 ZhiYu 范围）：
- SNMP/Syslog 客户端（网络设备管理）
- NED 设备驱动客户端（Huawei/H3C/Hillstone）
- 自适应基数树（ART）IP 查找（流量分析用）
- Selenium 浏览器自动化
- Excel/PDF 生成（POI/iText，除非后台有导出需求）

### 3.3 ufp-common-captcha

**定位**：验证码图片生成。

**功能**：
- 可配置字符长度（默认 4-6）
- 可配置强度等级（1-5，控制扭曲/干扰线/噪点密度）
- 支持数字/字母/混合字符集
- 可配置图片尺寸、背景色、字体

### 3.4 ufp-common-mybatis

**定位**：MyBatis-Plus 增强适配层。

**功能**（适配 ZhiYu MyBatis-Plus 生态）：
- 通用 BaseMapper/BaseService 封装
- 分页插件自动配置
- 自动填充（`createdAt`/`updatedAt`）MetaObjectHandler
- 逻辑删除配置
- JSON 类型处理器注册

### 3.5 ufp-common-api

**定位**：HTTP 客户端抽象层。

**功能**（精简自 NTA api-fit）：
- OkHttp 连接池封装
- 模板化 HTTP 请求（GET/POST/PUT/DELETE）
- 基础重试策略
- 响应反序列化

### 3.6 ufp-common-autoconfigure

**定位**：Spring Boot 自动配置装配中心。

**功能**：
- `@EnableUfp` 统一开关注解
- MVC 全局响应包装（Controller 返回自动包裹 `R<T>`）
- 全局异常处理（`@RestControllerAdvice`）
- Jackson 序列化配置（日期格式、时区、Long 精度）
- SpringDoc/Knife4j OpenAPI 自动配置
- 健康检查端点自动装配

### 3.7 ufp-common-feign

**定位**：微服务间 Feign 调用框架。

**功能**：
- Bearer Token 自动传递（Feign RequestInterceptor）
- 统一错误解码器
- 服务发现集成（Nacos）
- 基础重试策略

### 3.8 ufp-common-discovery

**定位**：Nacos 服务发现适配。

**功能**：
- NacosDiscoveryClient 封装
- 服务列表健康过滤

### 3.9 ufp-common-datasource

**定位**：多数据源动态路由。

**功能**：
- `@DS("dbKey")` 注解切换数据源
- AOP 切面自动路由
- HikariCP 连接池管理
- `zhiyu` + `ufp_auth` 双数据源默认配置

## 4. 模块依赖图

```
ufp-common-core         (叶子)
    ↑
ufp-common-util         (依赖 core)
    ↑
ufp-common-captcha      (依赖 core + util)
ufp-common-mybatis      (依赖 core + util)
ufp-common-api          (依赖 core + util)
    ↑
ufp-common-feign        (依赖 core + util + api)
ufp-common-discovery    (依赖 core)
ufp-common-datasource   (依赖 core)
    ↑
ufp-common-autoconfigure (聚合所有上述模块)
```

## 5. 与 NTA 的关键差异

| 维度 | NTA sfp-commons | ZhiYu ufp-common |
|------|----------------|-------------------|
| ORM | MyBatis + 自定义分页 | MyBatis-Plus 3.5.10 |
| HTTP 客户端 | OkHttp + 自研模板引擎 | OkHttp / RestClient (Spring 6) |
| 验证码 | Patchca 内核 | 可选替换为 Hutool CaptchaUtil |
| 缓存 | Redisson + Caffeine | 保持，Caffeine 本地 + Redis 远程 |
| 事件总线 | 自研 EventBus | Spring ApplicationEvent |
| 分布式调度 | XXL-JOB + Quartz | 按需引入（P1） |
| 分布式文件 | FastDFS + HDFS | MinIO / OSS（P2） |
| 可观测性 | OpenTelemetry + Micrometer | Micrometer + Prometheus |

## 6. 实施建议

1. **先建 core + util**：最小可用子集，其余模块按需增量
2. **mybatis 直接适配 MyBatis-Plus**：不照搬 NTA 自定义分页方案
3. **autoconfigure 渐进装配**：用 `@ConditionalOnClass` 控制组件按需加载
4. **无需迁移 text 解析引擎**：ZhiYu 场景不需要日志/流量文本解析 DSL
5. **无需迁移 NED/SNMP/Syslog**：网络设备管理不在产品范围内

## 相关文档

- [DESIGN-UFP-AUTH.md](DESIGN-UFP-AUTH.md) — UFP 认证授权（依赖 ufp-common 基础设施）
- [DESIGN-UFP-META.md](DESIGN-UFP-META.md) — UFP 元数据管理（同样基于 NTA 分析）
- [DEVELOPMENT-STANDARDS.md](../../dev-test/DEVELOPMENT-STANDARDS.md) — 编码规范与模块结构约定
- [ARCHITECTURE.md](../ARCHITECTURE.md) — 系统架构与模块依赖方向
