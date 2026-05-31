# PRysm

PRysm 是一个基于 GitHub Pull Request 的 AI 代码评审助手。它通过 GitHub Actions 自动获取 PR 变更，结合规则检查和大模型分析生成审查结果，并把评论回写到 PR 页面，帮助开发者更快发现风险代码、理解变更影响并获得可执行的 Review 建议。

本项目对应“AI PR Review 助手”方向，重点解决 PR 审查中信息分散、人工定位风险慢、Review 建议不稳定的问题。PRysm 不替代人工 Review，而是把重复性的变更梳理、风险提示和建议生成前置到 PR 页面。

## 核心能力

- 自动读取 GitHub PR 上下文、变更文件和 patch。
- 为变更代码提取邻近代码片段，补充 PR 标题、正文和 commit message。
- 按文件类型、优先级和上下文预算筛选进入模型的内容。
- 使用内置规则识别确定性风险，例如合并冲突标记和调试输出。
- 调用 OpenAI 兼容接口生成变更总结、风险说明和 Review 建议。
- 先输出快速审查评论，再用深度审查结果更新同一条评论。
- 支持跨仓库复用 workflow，接入仓库只需要配置一个 workflow 和一个模型密钥。

## 效果展示

Demo 视频：

```text
https://www.bilibili.com/video/BV1CCVU65EHm/
```

演示 PR：

```text
https://github.com/Remohdg/PRysm-test/pull/2
```

该 PR 在 `README.md` 中故意加入 Git 合并冲突标记后，PRysm 自动回写了审查评论。评论内容包括：

- 审查概览：发现问题数、规则结果数、模型结果数和去重数量。
- 变更总结：说明本次 PR 修改了哪些内容。
- 规则摘要：说明内置规则命中的问题数量。
- 风险代码：定位到 `README.md` 中的合并冲突标记，并给出严重级别、来源、规则 ID、说明和建议。
- Review 建议：给出合并前需要处理的操作建议。

这个示例同时展示了确定性规则和 LLM 审查的组合效果：内置规则识别到了明确的冲突标记，模型又补充了一条自然语言风险说明。

## 免配置体验

如果只是想体验 PRysm，不需要自己配置模型密钥，可以使用演示仓库：

```text
https://github.com/Remohdg/PRysm-test
```

体验步骤：

1. Fork 演示仓库到自己的 GitHub 账号。
2. 在 fork 后的仓库中新建分支。
3. 修改任意文件并提交，用于观察 PRysm 的总结和建议能力。
4. 如果想触发内置规则，可以新建 `src/ReviewSmokeTest.java`，加入一行 `System.out.println("debug");`。
5. 从自己的 fork 向 `Remohdg/PRysm-test:main` 创建 Pull Request。
6. 等待 GitHub Actions 运行，PR 页面会出现 PRysm 审查评论。

这种方式使用的是演示仓库中已经配置好的 `LLM_API_KEY`。体验者不需要配置密钥，也不能从 workflow 或 PR 日志中读取到密钥。

为什么需要 fork 演示仓库：GitHub 公共仓库通常不会允许陌生人直接向 `main` 分支提交代码。fork 后从自己的仓库向演示仓库提交 PR，是公开项目里最常见、也最安全的体验方式。

## 正式接入自己的仓库

在目标仓库中新增文件：

```text
.github/workflows/prysm-review.yml
```

内容可以直接复制本仓库的 [docs/examples/prysm-review.yml](docs/examples/prysm-review.yml)，也可以使用下面这一份：

```yaml
name: Prysm PR Review

on:
  pull_request_target:
    types: [opened, synchronize, reopened]

permissions:
  contents: read
  pull-requests: write
  issues: write

jobs:
  review:
    uses: Remohdg/PRysm-hdg/.github/workflows/prysm-review-reusable.yml@main
    secrets:
      LLM_API_KEY: ${{ secrets.LLM_API_KEY }}
```

然后在目标仓库配置模型密钥：

```text
Settings -> Secrets and variables -> Actions -> Repository secrets -> New repository secret
```

新增：

```text
Name: LLM_API_KEY
Secret: 你的模型 API Key
```

配置完成后，后续 PR 会自动触发 PRysm 审查并在 PR 评论区回写结果。

## 配置项

| 配置 | 是否必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `LLM_API_KEY` | 是 | 无 | 模型服务 API Key，通过 GitHub Actions Repository secret 配置。 |
| `llm-api-base-url` | 否 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | OpenAI 兼容接口地址。 |
| `llm-model` | 否 | `qwen-plus` | 审查使用的模型名称。 |
| `prysm-release-repository` | 否 | `Remohdg/PRysm-hdg` | 下载 `prysm.jar` 的 release 仓库。 |
| `prysm-release-tag` | 否 | `latest` | 下载指定 release 版本；默认使用最新 release。 |

## 模型配置

PRysm 默认使用千问 DashScope 的 OpenAI 兼容接口：

```text
LLM_API_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_MODEL=qwen-plus
```

如果只配置 `LLM_API_KEY`，默认按千问 DashScope key 使用。因此默认情况下需要填写千问 DashScope 的 API Key。

它不是只能使用千问。只要模型服务兼容 OpenAI Chat Completions API，就可以在 workflow 中覆盖接口地址和模型名：

```yaml
jobs:
  review:
    uses: Remohdg/PRysm-hdg/.github/workflows/prysm-review-reusable.yml@main
    with:
      llm-api-base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      llm-model: qwen-plus
    secrets:
      LLM_API_KEY: ${{ secrets.LLM_API_KEY }}
```

`LLM_API_KEY` 必须和配置的 `llm-api-base-url` 对应，不要把密钥写进代码、README、workflow 或 PR 描述。

## 工作流程

当前版本中，用户通过创建或更新 GitHub Pull Request 来指定审查对象。PRysm 会从 GitHub Actions 事件中自动解析仓库、PR 编号和访问 token，不需要用户手动输入 PR URL。

PRysm 在一次 PR 审查中会按下面的顺序执行：

1. 从 GitHub Actions 环境中解析仓库、PR 编号和 token。
2. 调用 GitHub API 获取 PR diff、变更文件、PR 标题正文和 commit message。
3. 为变更文件补充代码片段，并按上下文预算筛选要送入模型的内容。
4. 运行内置规则，先识别确定性问题。
5. 调用大模型生成变更总结、风险代码说明和 Review 建议。
6. 将快速审查结果写入 PR 评论，深度审查完成后更新同一条评论。

## 架构设计

```text
GitHub Actions
    |
    v
PR 上下文解析
    |
    v
GitHub API 获取 diff、文件、PR 元数据
    |
    v
代码片段构建 + 上下文增强 + 文件筛选
    |
    v
上下文预算控制
    |
    +--> 内置规则引擎
    |
    +--> LLM 审查引擎
    |
    v
结果聚合、去重、排序
    |
    v
PR 评论渲染与回写
```

主要模块：

- `context`：解析 GitHub Actions 中的 PR 运行上下文。
- `github`：封装 GitHub API 访问、diff 获取和评论回写。
- `review`、`enrichment`：构建待审查文件内容，并补充 PR 标题、正文和 commit message。
- `selection`、`budget`：筛选适合进入模型的文件，并控制上下文长度。
- `rule`：运行确定性内置规则，减少明显问题完全依赖模型判断。
- `llm`：调用 OpenAI 兼容模型，并解析模型输出。
- `result`、`quality`：聚合、去重、排序并过滤低质量结果。
- `comment`：把最终审查结果渲染为 PR 评论。
- `trace`：输出审查链路摘要，便于定位失败步骤和性能问题。

## 审查结果

PRysm 的评论包含：

- 审查概览
- 变更总结
- 规则摘要
- 风险代码
- Review 建议

快速审查会优先返回明显风险，深度审查完成后会更新同一条评论。没有明确风险时，PRysm 会给出无风险说明，最终是否合并仍由人工决定。

## 题目要求对应

| 题目要求 | PRysm 当前实现 |
| --- | --- |
| 用户指定 GitHub PR | 用户创建或更新 PR 后，PRysm 从 GitHub Actions 事件中自动解析目标 PR。 |
| PR 变更总结 | 基于 PR diff、标题、正文和 commit message 生成变更总结。 |
| 风险代码识别 | 结合内置规则和 LLM 审查输出风险代码，并标注文件、行号、严重级别和规则来源。 |
| Review 建议生成 | 针对发现的问题生成可执行建议，并在 PR 评论中集中展示。 |
| 上下文理解 | 为变更代码补充邻近片段，并引入 PR 元数据和 commit message。 |
| 误报控制 | 使用规则结果、模型结果去重、质量门控和严重级别排序，降低重复和低质量建议。 |
| 漏报控制 | 对明确风险使用内置规则兜底，对语义风险交给 LLM 分析。 |
| 响应速度 | 先写入快速审查评论，再执行深度审查并更新同一条评论。 |
| 使用体验 | 使用 GitHub Actions 自动触发，结果直接回写到 PR 页面；跨仓库接入只需复制 workflow 并配置密钥。 |

## 运行方式

PRysm 主要运行在 GitHub Actions 中。release 中已提供 `prysm.jar`，workflow 会优先下载该 jar 运行；如果 release jar 不可用，会 fallback 到 Maven 构建。

Release jar：

```text
https://github.com/Remohdg/PRysm-hdg/releases/latest/download/prysm.jar
```

本地调试时可以执行：

```powershell
.\mvnw.cmd test
.\mvnw.cmd -q -DskipTests package
```

## 技术栈与依赖

- Java 21
- Spring Boot 4
- Maven
- GitHub Actions
- GitHub REST API
- OpenAI 兼容 Chat Completions API
- Lombok

第三方依赖主要用于 Web/JSON、测试、配置处理和构建打包。PR 获取、上下文组织、规则引擎、模型调用、结果聚合和评论渲染等核心逻辑由本项目实现。

## PR 提交规范

为保持开发过程可追溯，本项目按 PR 方式提交功能和修复：

- 每个 PR 尽量只做一件事。
- PR 标题和描述应说明功能描述、实现思路和测试方式。
- 开发周期内保持持续 commit 和 PR 记录，避免最后一次性导入全部代码。
- 如果复用历史代码片段或外部实现，应在 PR 描述中说明来源。

## 常见问题

**为什么评论失败 403？**

通常是 workflow 权限不足，或 PR 事件无法使用有写权限的 token。正式接入时需要使用 `pull_request_target`，并配置 `pull-requests: write` 和 `issues: write`。

**为什么不把 API Key 写进 workflow？**

workflow、README 和代码都会进入仓库历史，公开仓库里写入 API Key 等同于泄露。应使用 GitHub Actions Repository secrets 配置 `LLM_API_KEY`。

**别人 fork 演示仓库后能读取演示仓库的密钥吗？**

不能。GitHub Actions secret 会以受保护变量注入到运行环境中，不会出现在 fork 仓库、workflow 文件或普通日志里。

**只能审查 fork PR 吗？**

不是。演示仓库建议 fork 是因为公开仓库通常不允许陌生人直接 push。正式接入自己的仓库后，仓库内分支 PR 和 fork PR 都可以触发审查。

**只能用千问吗？**

不是。默认配置面向千问 DashScope，但可以改成其他 OpenAI 兼容接口。API Key、base URL 和模型名需要对应同一个模型服务。

## 限制与未来扩展

- 当前主要支持 GitHub Pull Request，后续可扩展 GitLab、Gitee 等平台。
- 当前以 PR 总评论为主，后续可扩展到具体代码行的 review comment。
- 当前内置规则覆盖合并冲突标记、调试输出等确定性问题，后续可扩展安全规则、测试覆盖规则和项目自定义规则。
- 当前通过 GitHub Actions secret 配置模型密钥，后续可演进为 GitHub App 模式，降低单仓库接入成本并提供更细的权限控制。
- 大 diff 会进行上下文预算控制，可能跳过部分低优先级文件；后续可加入分批审查和增量缓存。

## 安全说明

本项目的跨仓库 workflow 使用 `pull_request_target`，用于支持 fork PR 的评论回写。workflow 会将待审查 PR 代码 checkout 到独立目录，并使用已发布的 PRysm jar 或可信仓库源码运行，避免直接执行不可信 PR 中的脚本。

使用者仍需注意：

- 不要把真实 API Key 提交到仓库。
- 只在可信仓库中配置 `LLM_API_KEY`。
- PRysm 评论是辅助审查结果，不能替代人工 Review。

## 项目状态

当前版本已经具备 GitHub PR 自动审查、PR 评论回写、跨仓库 workflow 复用、release jar 运行、规则与 LLM 联合审查等核心能力。后续主要完善方向是更多规则覆盖、行级评论和 GitHub App 化。
