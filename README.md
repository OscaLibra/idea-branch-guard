# Branch Guard · IDEA 分支提交守卫插件

> 在 IDEA 内提交代码时校验当前 Git 分支，**非 merge 提交**若不符合规则则直接阻断并提示，避免"提交到 main 才发现要走 MR"。

---

## 文档导航

| 文档 | 内容 | 适合谁看 |
|---|---|---|
| [00 · 可行性分析](docs/00-可行性分析.md) | 技术可行性、API 证据、4 种方案对比、风险、工作量 | 决策者、评审人 |
| [01 · 需求规格说明书](docs/01-需求规格说明书.md) | 目标、13 条功能需求、决策表、配置项、验收标准 | 需求方、测试 |
| [02 · 开发设计文档](docs/02-开发设计文档.md) | 架构、类设计、时序、完整 Java 实现、构建发布、测试 | 开发 |

---

## 一分钟结论

| 问题 | 答案 |
|---|---|
| 能做吗？ | **能。** 平台有官方扩展点 `com.intellij.checkinHandlerFactory`，返回 `ReturnResult.CANCEL` 即可中止提交 |
| 要调 git 命令行吗？ | **不需要。** `GitRepository#getCurrentBranch()` / `#getState()` 直接读 IDE 内存模型 |
| merge 怎么识别？ | `repo.getState() == GitRepository.State.MERGING`，与状态栏 "Merging…" 同源 |
| 用 Java 还是 Kotlin？ | **Java 25。** 新版 `CommitCheck` 是 Kotlin `suspend` 接口，Java 不友好，本方案用稳定版 `CheckinHandler`；语言级别跟随平台（2026.2 要求 25） |
| 用什么构建？ | **Maven 3.9 + IDEA 自带 JBR 25。** JetBrains 官方只支持 Gradle，Maven 构建链需自建（antrun 编译 + assembly 打包），见开发文档 §2.1、§8 |
| 支持哪些 IDE？ | **2026.2 起**（用本机 2026.2.1 SDK + JBR 25 编译，class 69；兼容旧版需换 2023.3.8 SDK 重编） |
| 工作量？ | MVP **7 人日**，含 buffer ≈ 8.5 人日 |
| 最大风险？ | 非模态提交流程下弹窗行为，需装包后人工验证 |
| 弹窗能单独设语言吗？ | 能。设置页「弹窗语言」可选 **跟随 IDE / 中文 / English**，与 IDE 界面语言解耦 |

---

## 当前状态

工程骨架已落地并通过构建验证（2026-10-20）：

| 项 | 结果 |
|---|---|
| `mvn clean package` | ✅ BUILD SUCCESS（JDK 25 / JBR 25.0.3，SDK 2026.2.1） |
| 单元测试 | ✅ 19 个用例全绿（`rule` 包：8 + 11） |
| 产物 | `target/branch-guard-1.0.2.zip`（31 KB，`branch-guard/lib/branch-guard-1.0.2.jar`） |
| 待办 | 装包后在真实 IDE 验证提交拦截、设置页、`idea.log` 无 `PluginException` |

### 构建

```bash
# 用 IDEA 自带的 JBR 25（本机默认 mvn 跑在 JDK 8 上）
export JAVA_HOME="D:/Apps/JetBrains/IntelliJ IDEA 2026.2.1/jbr"
mvn clean package
```

### 安装

`Settings → Plugins → ⚙ → Install Plugin from Disk` → 选 `target/branch-guard-1.0.2.zip` → 重启 IDE。

---

## 规则模式（一个开关，两种语义）

需求原句"检查是否是指定分支"存在两种业务解释，插件同时实现，切换下拉即可：

```text
白名单 WHITELIST（默认）        黑名单 BLACKLIST（保护主干）
只允许在 dev / feature/* 提交    禁止在 main / release/* 直接提交
        │                                │
        ├─ feature/login  ✅             ├─ main           ❌ 阻断
        └─ main           ❌ 阻断        └─ feature/login  ✅
```

两种模式下，**merge 提交默认放行**（可关闭）。

---

## 插件行为

```text
开发者点 Commit
      │
      ▼
┌─────────────────────────────┐
│ Branch Guard 校验            │
│  · 当前在哪个分支？           │
│  · 这是 merge 提交吗？        │
│  · 规则命中吗？               │
└──────────────┬──────────────┘
               │
      ┌────────┴────────┐
      ▼                 ▼
   通过 → 正常提交    不通过 → 弹窗阻断（提交不产生）
                              [查看规则设置] [取消提交]
```

阻断提示示例：

```
Branch Guard 已阻止本次提交

以下仓库的当前分支不允许直接提交：
  • D:\work\order-service — 分支 release/2.3（受保护分支，禁止直接提交）

合并（merge）提交不受此限制。
建议：切换到 feature/* 分支后重新提交，或通过合并方式更新该分支。
```

---

## 与 Git hook、服务端保护分支的关系

| 手段 | 拦截时机 | 能否绕过 |
|---|---|---|
| **本插件** | 点提交瞬间（体验最好） | 可禁用插件 |
| Git pre-commit hook | `git commit` 时 | IDEA 2025.1+ 可跳过 hooks |
| 服务端保护分支 | `git push` 时 | 不可绕过 |

推荐 **插件 + 服务端保护分支** 组合：插件负责"让开发者少犯错"，服务端负责"确保错不了"。

---

## 下一步

1. 评审 [需求文档 §13 待确认问题](docs/01-需求规格说明书.md#13-待确认问题)（Q1 决定默认模式）；
2. 执行 [开发文档 §12.3 POC 清单](docs/02-开发设计文档.md#123-poc-验证清单开发前置1-人日)（1 人日）；
3. POC 通过后按 [开发文档 §8](docs/02-开发设计文档.md#8-详细实现) 落地实现。
