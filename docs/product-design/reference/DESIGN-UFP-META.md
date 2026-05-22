# UFP Meta 模块设计

> 基于 NTA `sfp-meta` 分析，适配 ZhiYu UFP 平台。
> 本文档为设计参考，暂不启动开发。

## 1. 概述

`ufp-meta` 是 UFP 统一基础平台的元数据管理模块。对应 NTA 项目 `sfp-meta`，原为网络设备元数据管理平台，ZhiYu 场景下需大幅裁剪，仅保留与业务无关的通用能力。

## 2. NTA sfp-meta 分析摘要

NTA `sfp-meta` 采用四层架构，共 339 个 Java 文件：

```
sfp-meta/
├── sfp-meta-api/                     # API 契约层
│   ├── sfp-meta-basic-api            #   常量、错误码、缓存 Key
│   ├── sfp-meta-device-api           #   设备模型 + 16 个 Service 接口
│   ├── sfp-meta-dict-api             #   字典数据模型 + Service
│   ├── sfp-meta-location-api         #   地理位置模型 + Service
│   ├── sfp-meta-logging-api          #   操作日志模型 + Service
│   └── sfp-meta-manage-api           #   管理域（13 个子领域）
│       ├── ability/                  #     设备能力范围
│       ├── desens/                   #     数据脱敏
│       ├── device/                   #     设备管理 V1 API
│       ├── discern/                  #     数据识别（内容/字段）
│       ├── indicator/                #     监控指标
│       ├── node/                     #     资源节点
│       ├── pipeline/                 #     数据通道（Kafka/JDBC/ES/Redis/RocketMQ/HTTP/NED）
│       ├── protect/                  #     受保护项
│       ├── region/                   #     地理区域
│       ├── safe/                     #     数据安全（分类/分级/字段规则/脱敏模板）
│       └── schedule/                 #     通用调度配置
├── sfp-meta-component/               # 组件层（Controller + Service + Repository）
│   ├── sfp-meta-device-component     #   设备 CRUD 组件（16 个 Controller）
│   ├── sfp-meta-dict-component       #   字典数据组件
│   ├── sfp-meta-location-component   #   地理位置组件
│   └── sfp-meta-logging-component    #   操作日志组件
├── sfp-meta-service/                  # 服务实现层
│   └── sfp-meta-manage-service       #   Spring Boot 应用，全部 manage-api 实现
└── sfp-meta-service-configuration/    # 配置模块
```

### 2.1 核心能力矩阵

| 能力域 | NTA 用途 | ZhiYu 是否需要 | 说明 |
|--------|---------|:---:|------|
| **设备管理** | 网络设备 CRUD（Device/Type/Model/Vendor/Group/SNMP/Interface） | ❌ | 网络流量分析专属，ZhiYu 无此场景 |
| **数据通道 (Pipeline)** | Kafka/JDBC/ES/Redis/RocketMQ/HTTP/NED 数据管道配置 | ❌ | 网络设备数据采集管道，ZhiYu 不需要 |
| **数据安全** | 数据分类分级（DataSafeClassify/Grade/Field/FieldRule）+ 脱敏（Desens/FieldDesens） | ⚠️ 参考 | 分类分级框架可参考，脱敏算法可复用 |
| **数据识别** | DataContentDiscern / DataFieldDiscern | ❌ | 网络流量数据字段识别，ZhiYu 无此场景 |
| **调度管理** | Cron 调度 + 执行历史 + 节点绑定 | ⚠️ 参考 | 通用调度框架可参考，ZhiYu 已有 XXL-JOB（P1） |
| **监控指标** | MonIndicator + Collection + Instruct | ❌ | 设备指标采集，ZhiYu 使用 Micrometer + Prometheus |
| **地理区域** | Region（区域层级树） | ❌ | 设备物理区域管理，ZhiYu 无此场景 |
| **资源节点** | ResourceNode（计算节点注册） | ❌ | 数据采集节点管理，ZhiYu 无此场景 |
| **设备能力** | DeviceAbilityScope | ❌ | 设备采集能力范围，ZhiYu 无此场景 |
| **受保护项** | ProtectedItem | ❌ | 设备保护项，ZhiYu 无此场景 |
| **地理位置** | Location / LocationWorld | ❌ | 设备地理位置，ZhiYu 无此场景 |
| **字典管理** | DictData / DictValue | ✅ 保留 | 通用数据字典，任何平台都需要 |
| **操作日志** | OperationLog | ✅ 保留 | 通用审计日志，ZhiYu 已有 auth_operation_log |

### 2.2 NTA 数据通道（Pipeline）设计

Pipeline 是 NTA sfp-meta 的核心概念，用于配置网络设备数据的采集、传输、处理管道：

```
Pipeline (数据通道)
├── 引擎类型: MySQL / Kafka / ElasticSearch / ClickHouse / FileBeat / FTP / HBase / Hive ...
├── 配置信息: JDBC URL / Bootstrap Server / Topic / Index ...
├── 连通性状态: 指定资源节点下的连接测试结果
└── PipelineFilter (过滤器链)
    ├── 过滤源类型: 设备采集 / 文件导入 / API 推送
    ├── 解析模板: Regex / JSON / XML / CSV
    └── 内容类型: Syslog / NetFlow / SNMP Trap / 自定义
```

**ZhiYu 不需要此能力**，数据管道逻辑完全属于网络流量分析领域。

### 2.3 NTA 数据安全（DataSafe）设计

NTA 的数据安全模块包含完整的数据分类分级 + 脱敏体系：

```
DataSafeTpl (安全模板)
  └── DataSafeClassify (分类: 个人信息/网络标识/设备配置...)
       └── DataSafeGrade (分级: L1公开/L2内部/L3秘密/L4机密)
            └── DataSafeField (字段定义)
                 ├── DataSafeFieldRule (匹配规则: 正则/字典/复合)
                 └── DataSafeFieldDesens (脱敏配置: 掩码/哈希/替换/加密)
                      └── DataDesens / DataDesensConfig (脱敏算法执行)
```

**ZhiYu 可参考**：
- 分类分级的层次结构设计
- 脱敏算法（掩码/哈希/替换/加密）的实现模式
- 但 ZhiYu 的数据安全需求远简单于 NTA（无网络包内容识别场景）

## 3. ufp-meta 模块规划（精简版）

NTA `sfp-meta` 339 个 Java 文件中，仅约 5% 对 ZhiYu 有参考价值。规划为轻量级模块：

```
ufp-meta/
├── ufp-meta-api                      # API 契约层
│   ├── ufp-meta-basic-api            #   常量、错误码
│   ├── ufp-meta-dict-api             #   字典模型 + Service 接口
│   └── ufp-meta-logging-api          #   操作日志模型 + Service 接口
├── ufp-meta-component                # 组件层
│   ├── ufp-meta-dict-component       #   字典 CRUD（Controller + Service + Repository）
│   └── ufp-meta-logging-component    #   操作日志查询（Controller + Service + Repository）
└── ufp-meta-server                   # 服务实现（可选独立部署，也可合并到 zhiyu-admin）
```

### 3.1 ufp-meta-dict（字典管理）

**定位**：通用数据字典，供所有业务模块查询。

**模型**（精简自 NTA DictData/DictValue）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `dict_id` | BIGINT PK | 字典主键 |
| `dict_category` | VARCHAR(50) | 字典分类（如 `subscription_status`、`payment_method`） |
| `dict_code` | VARCHAR(100) | 字典编码 |
| `dict_label` | VARCHAR(200) | 字典显示名称 |
| `dict_value` | VARCHAR(500) | 字典值 |
| `dict_value_type` | TINYINT | 值类型：1=字符串, 2=整数, 3=JSON |
| `dict_sort` | INT | 排序 |
| `dict_enable` | TINYINT | 启用状态 |
| `dict_desc` | VARCHAR(500) | 描述 |

**Service 接口**：

```java
public interface IDictDataService {
    List<DictDataDto> listByCategory(String category);       // 按分类查询
    Map<String, String> mapByCategory(String category);      // 按分类返回 code→label 映射
    void refreshCache();                                      // 刷新本地缓存
}
```

> 注：ZhiYu 当前使用枚举类管理字典（如 `SubscriptionStatusEnum`），无需数据库字典表。ufp-meta-dict 仅在需要运行时动态管理字典项时引入。

### 3.2 ufp-meta-logging（操作日志）

**定位**：通用操作审计日志。

**ZhiYu 现状**：已在 `ufp_auth` 库中设计了 `auth_operation_log` 表（V1.4.0），包含操作人、模块、动作、目标、请求参数、响应结果、IP、耗时等字段，与 NTA OperationLog 功能等价。**无需重复建设。**

### 3.3 不引入的能力

以下 NTA sfp-meta 能力明确不引入 ZhiYu：

| NTA 能力 | 不引入原因 |
|----------|-----------|
| 设备管理（16 张表） | 网络设备管理，ZhiYu 无此产品场景 |
| 数据通道（Pipeline + Filter） | 网络数据采集管道，ZhiYu 无此产品场景 |
| 数据安全（分类分级 + 脱敏） | ZhiYu 数据安全需求简单，PII 保护已通过 API 脱敏实现 |
| 数据识别（Discern） | 网络流量字段识别，ZhiYu 无此产品场景 |
| 调度管理（Schedule） | ZhiYu 按需引入 XXL-JOB（P1 路线图） |
| 监控指标（MonIndicator） | ZhiYu 使用 Micrometer + Prometheus + Grafana |
| 区域/节点/位置 | 设备物理位置管理，ZhiYu 无此产品场景 |

## 4. 模块依赖图

```
ufp-common-core
    ↑
ufp-meta-api (basic + dict + logging)
    ↑
ufp-meta-component (dict + logging)
    ↑
zhiyu-admin (引入 dict 组件，供后台字典管理)
```

## 5. 与 NTA 的关键差异

| 维度 | NTA sfp-meta | ZhiYu ufp-meta |
|------|-------------|----------------|
| Java 文件数 | 339 | ~15（仅字典） |
| 数据库表 | 40+（设备/管道/安全/调度/区域/节点） | 1（字典数据） |
| 独立部署 | 1 个 Spring Boot 应用 | 不独立部署，合并到 zhiyu-admin |
| 核心价值 | 网络设备元数据管理 | 运行时动态字典 |
| 复杂度 | 极高（多领域交叉） | 极低（单一字典 CRUD） |

## 6. 实施建议

1. **优先级极低（P3）**：ZhiYu 当前使用枚举类管理固定字典完全满足需求
2. **仅在有运行时动态字典需求时启动**：如运营需要在不发版的情况下新增/修改字典项
3. **操作日志不需要**：`auth_operation_log` 已覆盖审计需求
4. **不照搬 NTA**：NTA sfp-meta 339 个文件中 ~95% 为网络设备管理专属逻辑，无需迁移
5. **如引入，建议合并到 zhiyu-admin**：字典管理属于后台管理功能，无需独立微服务

## 相关文档

- [DESIGN-UFP-COMMON.md](DESIGN-UFP-COMMON.md) — UFP 共享基础设施（ufp-meta 的底层依赖）
- [DESIGN-UFP-AUTH.md](DESIGN-UFP-AUTH.md) — UFP 认证授权（操作日志已在 auth_operation_log 覆盖）
- [DATABASE.md](../DATABASE.md) — 数据库设计（auth_operation_log 表结构）
- [DEVELOPMENT-STANDARDS.md](../../dev-test/DEVELOPMENT-STANDARDS.md) — 编码规范
