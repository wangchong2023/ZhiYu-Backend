# 智宇平台后端开发红线手册（DEVELOPER REDLINES）

> [!IMPORTANT]
> **本手册为智宇平台后端研发的最高开发红线。**
> 所有加入本仓库的增量开发人员、平台工程师及核心架构师均必须严格无条件遵守本手册规定的各项规范。本手册在 PR 静态分析门禁和代码人工评审（Code Review）中具有最高优先权，旨在确保系统在面对多业务演进、高并发迭代时的极致可读性与高维护性。

---

## 一、 代码注释开发红线（硬性绝对约束）

为了彻底杜绝代码库的“文档真空”与“黑盒技术债”，平台架构组规定：**新增核心业务代码必须包含完善的中文文件头、规范的 Javadoc 类/方法头与关键的过程注释。**

### 1. 强制中文文件头 (File Header)
所有新增的 Java 类、接口、枚举或 Record，必须在 package 声明前包含规范的中文版权文件头。
*   **格式要求**：包含版权所属、公司名称、作者标识、版本号以及该文件的**核心业务功能定义**。
*   **严禁**：直接以空文件头或单纯自动生成的无意义信息提交。

### 2. 标准中文 Javadoc (Class & Method Javadoc)
核心业务层（如 Controller、Service、Mapper、BOM/DTO 领域对象等）的所有 `public` / `protected` 类与方法必须声明规范的中文 Javadoc。
*   **类头要求**：详尽解释该类的业务边界和在微服务拓扑中所处的位置。
*   **方法头要求**：必须通过标准的 `@param`、`@return` 和 `@throws` 说明每一个入参的物理含义、返回值的期望结构、以及防御性边界条件下会被引发的业务/技术异常。
*   **Swagger 注解不平替**：Controller 层的 `@Operation`、`@Parameter` 等 Swagger 表现层注解**绝对无法平替** Javadoc 规范，两者必须共存。

### 3. 复杂逻辑中文过程注释 (Inline Process Comments)
对于包含流式计算、并发控制（如 Redis 分布式锁、原子计数器）、复杂的数据库状态机流转等逻辑复杂的关键代码块，必须在方法内部追加详尽的中文单行/多行过程注释。
*   **标准**：达到“让非本模块的开发人员在 10 毫秒内彻底读懂算法每一步骤的业务意图”的极高清晰度。

---

## 二、 代码评审（Reviewer）一票否决权（Veto Power）

> [!CAUTION]
> **对于完全不带注释、逻辑复杂的 PR，Reviewer 拥有一票否决权，强制拒绝 Merge！**

在人工代码审查（PR Review）节点，审查人员必须秉持最高质量卡点原则：
1.  **红线否决**：只要发现新增的业务代码类没有 Javadoc 类头，或关键的公开方法没有书写包含入参/出参中文解释的方法头，或者方法内核心过程毫无注释，**Reviewer 必须直接给出 `Changes Requested` 状态，投出否决票，驳回合并请求**。
2.  **整改后提交**：被驳回的 PR 必须在补充高标准的中文注释、确保本地静态代码扫描 100% 通过后，方可重新请求 Review。
3.  **不妥协原则**：绝不接受“后续再补齐注释”、“工期紧凑临时放行”等妥协性辩词，注释质量即是平台可用性的第一保障。

---

## 三、 本地及 CI 自动化卡点机制

我们已经在 `backend/checkstyle.xml` 中将 Javadoc 强校验模块全面唤醒激活，通过本地门禁进行首层物理阻断：

### 1. 校验规则集
*   **`JavadocType`**：强制卡点所有公共类、接口和枚举的 Javadoc 完整度。
*   **`JavadocMethod`**：强制卡点所有 `public` / `protected` 方法的 Javadoc 完整度（除 `@Override`、`@Bean` 等已实现/注入类方法外）。

### 2. 存量代码平滑排除升级（Suppression）
为了确保存量 300 多个老旧文件的正常编译与小步快跑，我们在 `backend/checkstyle-suppressions.xml` 中进行了精准的否定前瞻排除：
*   **机制**：除了已经通过完美重构的标杆核心类（如 `GenericService`、`ICacheOperate`、`UserIdentityController`·、`SubscriptionController`、`OrderService`）强制参与 Javadoc 卡点外，其余老代码临时放行。
*   **演进**：后续各业务组在重构或新增模块时，**必须将新增的文件/包名追加进 `checkstyle-suppressions.xml` 的白名单正则中**，实施滚动治理。

### 3. 本地与 CI 校验命令
开发人员在本地提交代码前，必须执行以下命令进行自检卡点：
```bash
./mvnw -f backend/pom.xml checkstyle:check
```
只有在终端打印 **`BUILD SUCCESS`** 且 **`0 violations`** 时，才允许推送至远程，否则 Woodpecker CI 流水线将在 `validate` 阶段直接予以阻断！
