# Branch Guard（分支守卫）

> IntelliJ IDEA 插件：在**提交那一刻**校验当前 Git 分支，不符合规则的提交直接阻断并给出引导——把"提交错分支"的问题挡在 push 之前，而不是等到 push 被服务端拒绝才发现。

| | |
|---|---|
| 最新版本 | [v1.0.3](https://github.com/OscaLibra/idea-branch-guard/releases/latest) |
| 适用 IDE | IntelliJ IDEA **2026.2+**（Community / Ultimate，build 262+） |
| 构建方式 | Maven 3.9 + JDK 25（IDEA 自带 JBR 即可） |
| 开发者 | JiangLingyun |

---

## 为什么需要它

团队使用「主干保护 + MR 评审」流程时，服务端保护规则只在 **push 时**才生效：

```text
在 main 上误提交 ──→ 本地历史已污染 ──→ push 被拒 ──→ reset / 重建分支（10-30 分钟）
```

Branch Guard 把校验提前到 **commit 的瞬间**：

```text
在 main 上点提交 ──→ 立即弹窗阻断 ──→ 仓库无任何变化，切个分支就好
```

---

## 功能特性

- **提交前拦截**：Commit / Commit and Push / Amend 全覆盖，命中规则即中止，工作区与仓库状态不变
- **双规则模式**：
  - 白名单：只有列出的分支可以提交（如 `dev`、`feature/*`）
  - 黑名单：列出的分支禁止直接提交（如 `main`、`master`、`release/*`）
- **通配符匹配**：`*` 匹配任意字符（含 `/`），`?` 匹配单个字符
- **合并豁免**：merge 提交自动放行（可关闭），解决"合并回主干也被拦"的问题
- **多仓库支持**：项目含多个 Git 仓库时逐个判定，任一违规整单拦截，弹窗列出全部违规仓库
- **部分提交支持**：只校验本次勾选文件所属的仓库
- **分离 HEAD 拦截**：不在任何分支上时默认阻止提交，防止提交丢失
- **规则随仓库共享**：配置存放在 `.idea/branch-guard.xml`，随代码走 MR 评审
- **弹窗语言独立配置**：IDE 界面是英文也能用中文弹窗
- **fail-open 设计**：插件自身异常时放行提交并记录日志，绝不添乱

> 说明：插件是**防误操作护栏**，不是安全管控——被禁用即失效。建议与服务端保护分支（GitLab/GitHub 的 protected branch）配合使用。

---

## 安装

1. 从 [Releases](https://github.com/OscaLibra/idea-branch-guard/releases) 下载 `branch-guard-x.y.z.zip`
2. IDEA → `Settings → Plugins → ⚙ → Install Plugin from Disk` → 选择 zip
3. 重启 IDEA

> 需要 IntelliJ IDEA **2026.2 及以上**。

---

## 使用指南

### 打开设置

`Settings → Branch Guard`

### 配置项

| 配置 | 说明 | 默认值 |
|---|---|---|
| 启用提交前分支校验 | 总开关，关闭后完全不介入 | 开 |
| 规则模式 | 白名单 / 黑名单（见下） | 白名单 |
| 分支规则 | 每行一条，支持通配符，`#` 开头为注释 | `dev`、`develop`、`feature/*`、`hotfix/*` |
| 允许合并（merge）提交 | 处于合并状态时跳过校验 | 开 |
| 拦截分离 HEAD | 不在任何分支上时阻止提交 | 开 |
| 自定义提示语 | 追加在弹窗顶部的自定义文案 | 空 |
| 弹窗语言 | 跟随 IDE / 中文 / English | 跟随 IDE |

> 规则保存到 `<项目>/.idea/branch-guard.xml`。**这个文件建议随仓库提交**，规则变更就能和其他代码一样走 MR 评审。

### 规则怎么写

```text
# 白名单模式：以下分支允许直接提交，其余全部拦截
dev
develop
feature/*        # 匹配 feature/login、feature/a/b 等
hotfix/*

# 黑名单模式：以下分支禁止直接提交，其余放行
main
master
release/*
```

匹配规则：

- 每行一条，大小写敏感，按**字面量 + 通配符**匹配
- `*` 匹配任意字符（包括 `/`），`?` 匹配单个字符，其余字符按字面量处理
- 规则列表为空 = 不拦截（避免"装了就锁死"）

### 拦截效果

在受保护/不允许的分支上点提交时：

```text
分支守卫已阻止本次提交

以下仓库的当前分支不允许直接提交：
• spot-server — 分支 main（受保护分支，禁止直接提交）

合并（merge）提交不受此限制。
请切换到允许的分支，或通过合并方式更新该分支。

[ 查看规则设置 ]  [ 取消提交 ]
```

- 点「取消提交」：提交中止，`git log` 无新提交，改动原样保留
- 点「查看规则设置」：直接跳到插件配置页

---

## 常见问题

**Q：拦截不生效？**
按顺序检查：① 设置里是否勾选「启用」；② 当前分支是否真的在规则范围内；③ 本次提交是否为合并提交（默认豁免）；④ `Help → Show Log in Explorer` 搜索 `Branch Guard` 看日志（拦截、放行、异常都有记录）。

**Q：命令行 `git commit` 会被拦吗？**
不会。插件只在 IDE 内生效。命令行场景请用 Git pre-commit hook 或服务端保护分支兜底。

**Q：它是安全管控手段吗？**
不是。本地插件可被禁用，它定位是"让开发者少犯错"；强约束请配置在服务端（保护分支 / push rule）。

**Q：为什么我的分支没在设置页的规则里也被拦/放行了？**
确认当前会话是否重启过 IDEA——1.0.1 之前的版本存在"重启后不拦截"的问题，请升级到 **1.0.3+**。

---

## 从源码构建

```bash
# 用 IDEA 自带的 JBR（本机无需另装 JDK）
export JAVA_HOME="D:/Apps/JetBrains/IntelliJ IDEA 2026.2.1/jbr"

git clone https://github.com/OscaLibra/idea-branch-guard.git
cd idea-branch-guard
mvn clean package        # 产物：target/branch-guard-<版本>.zip
```

- `pom.xml` 中 `<idea.home>` 指向本地 IDEA 安装目录（编译 classpath 来源），CI 可用 `-Didea.home=...` 覆盖
- 单元测试：`mvn test`（规则层为纯 Java，不依赖 IDE）
- 兼容性验证：`java -jar verifier-cli.jar check-plugin target/branch-guard-x.y.z.zip <IDE路径>`

---

## 项目文档

| 文档 | 内容 |
|---|---|
| [可行性分析](docs/00-可行性分析.md) | 技术方案对比、API 证据、风险与工作量 |
| [需求规格说明书](docs/01-需求规格说明书.md) | 功能需求、规则决策表、验收标准 |
| [开发设计文档](docs/02-开发设计文档.md) | 架构、关键实现、踩坑记录、发布流程 |

## 项目结构

```text
src/main/java/com/acme/branchguard/
├── rule/       规则层（纯 Java，零 IDE 依赖，可单测）
├── commit/     平台接入层（提交拦截、仓库解析）
└── settings/   配置层（持久化 + 设置页）
```

---

## License

内部工具，未设置开源协议；如需对外分发请先补充协议。
