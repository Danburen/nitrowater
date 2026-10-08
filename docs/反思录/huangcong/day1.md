# Day 1 反思录 · huangcong

> 日期：2026-10-07（上午）｜ 项目：NitroWater（Gradle 多模块 / SSO 身份中心）
> 关键词：工程基线、认证核心迁移、SSO 先行、编译驱动迁移

## 一、今天的反思区

### 1. 今天做了什么
- **工程基线（M0）**：确认三模块职责（`core` 共享库 / `account` SSO / `server` 业务），修掉 Gradle wrapper 下载超时与悬空的 `waterfun-core` 依赖，`gradlew build` 全绿。
- **需求与设计**：产出 PRD v1.0，对三子方案（wtools 合并 / 演练场后端化 / 认证迁移+全站 SSO）做可行性评估，并给出总体结论与执行顺序。
- **迁移策略拍板**：认证相关类**整包复制**进 `nitrowater-core`，core 删启动类变**纯库**（`bootJar` 关闭）；业务实体 / 云 SDK / 业务 Controller 一律不拷，砍掉处用 `TODO(SSO)` 标记。
- **编译驱动迁移**：批量复制 → 包重命名（`org.waterfun.*` → `cn.nitrowater.core.lib`）→ 编译驱动砍引用（删业务 DTO、exception 整树迁入、补依赖）。`compileJava` 从 101 个错误收敛到 0，`gradlew build` 20 个任务 BUILD SUCCESSFUL。
- **项目理解 + 缺口盘点**：梳理 `nitrowater-account` 落地 `/api/auth/**` 的 9 项缺口与 S1–S5 分步；PRD 升到 v1.2。

### 2. 收获与认识
- **先基线、后功能**：先把构建、模块职责、依赖理顺，后面每一步都可独立验证，返工成本最低。
- **"编译驱动"迁移法有效**：不预先猜依赖，让编译器逐个暴露缺失；比人工盘点快，且不容易漏。
- **纯库边界要早定**：core 不部署、只做共享库；Gradle `implementation` 依赖不传递 → 消费方各自声明依赖。

### 3. 踩坑与教训
- **IDE 包重命名不会改字符串字面量**：`@EntityScan` / `@EnableJpaRepositories` 是字符串，迁移后必须手动同步，否则启动报"缺 bean"。
- 迁移不是"复制粘贴"：业务耦合要显式砍掉并留标记，方便日后回溯恢复点。

### 4. 下一步计划（当天下午）
- `nitrowater-account` 落地：依赖 / 启动扫描 / 基础设施 / DTO 与控制器 / 配置，先跑通 `/api/auth/**`。

## 二、对话档案

### 对话 1 · 迁移顺序怎么定？
**我**：三块（wtools、演练场、SSO）先做哪个？
**AI**：三者互相支撑——SSO 给演练场提供身份，wtools 给演练场提供前端壳。建议顺序 `工程基线 → SSO Phase 1 →（wtools ∥ 演练场）→ SSO Phase 2`。其中 SSO 先行最优：上游已有一份 603 行的成熟迁移方案，执行成本已知、风险最低。
**结论**：拍板 **SSO 先行**。

### 对话 2 · 认证代码要整包复制吗？
**我**：认证这块是全拷还是挑着拷？
**AI**：建议认证类**整包复制**到 core 以便未来复用，core 删启动类变纯库；业务实体 / 云 SDK / 业务 Controller 不拷；砍引用处用 `TODO(SSO)` 标记。库依赖用 Option B（消费方各自声明）。
**结论**：采纳，`cn.nitrowater.core.lib` 收束，`gradlew build` 全绿。
