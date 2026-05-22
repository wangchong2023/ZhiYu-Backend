# Flyway/DataSource 环境变量命名统一

## 问题

`application-dev.yml` 和 `application-release.yml` 为 Flyway 显式配置了
`url` / `user` / `password`，引用的环境变量占位符是 `${DB_USERNAME}` /
`${DB_PASSWORD}`，与 DataSource 使用的 `SPRING_DATASOURCE_USERNAME` /
`SPRING_DATASOURCE_PASSWORD` 不同。K8s ConfigMap/Secret 必须同时注入两套
变量，冗余且曾导致部署失败（Flyway 回退到 YAML 默认密码 `zhiyu123`）。

## 方案

移除 Flyway 显式配置，让 Flyway 自动从 DataSource 继承连接信息。
Spring Boot 的 Flyway 自动配置默认行为即如此。

## 改动

### 本地 YAML

- `application-dev.yml`：删除 `spring.flyway.url`、`spring.flyway.user`、`spring.flyway.password`
- `application-release.yml`：同上

### 远程 K8s ConfigMap

移除不再需要的 key：`DB_USERNAME`、`SPRING_FLYWAY_URL`、
`SPRING_FLYWAY_VALIDATE_ON_MIGRATE`

### 远程 K8s Secret

移除不再需要的 key：`DB_PASSWORD`

## 效果

DataSource 和 Flyway 共享同一套凭据，ConfigMap 从 12 key → 9 key。
