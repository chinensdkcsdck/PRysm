# PR12：结果聚合与回写 GitHub PR 报告

## 本 PR 做了什么

本 PR 新增结果聚合与 GitHub PR 回写能力，用于消费 `ReviewExecutionInput`、`RuleEngineResult` 和 `LlmReviewResult`，合并规则检查和 LLM Review 的问题结果，完成去重、排序、Markdown 评论生成，并将最终审查结果回写到 GitHub PR。

新增内容：

- 新增 `ReviewResultAggregator`，作为 PR12 的结果聚合入口
- 新增 `ReviewAggregationResult`，承载聚合后的 findings 和统计信息
- 新增 `ReviewFindingDeduplicator`，对规则和 LLM 的重复 finding 去重
- 新增 `ReviewFindingSorter`，对最终 finding 做稳定排序
- 新增 `ReviewCommentRenderer`，生成 GitHub PR 总评论 Markdown
- 新增 `GithubPullRequestCommentClient`，调用 GitHub issue comment API 回写 PR
- 扩展 `PrReviewRunner`，串起 PR10、PR11 和 PR12 的完整执行链路
- 新增 PR12 相关单元测试

## 输入输出边界

输入：

- `ReviewExecutionInput`
- `RuleEngineResult`
- `LlmReviewResult`

输出：

- `ReviewAggregationResult`
- GitHub PR 评论回写动作

问题结果继续统一使用：

- `ReviewFinding`

PR12 不关心规则引擎和 LLM 的内部实现，只消费它们已经产出的统一 finding 结构。

## 和 PR8 / PR10 / PR11 的对接方式

PR12 对接的是上游已经稳定产出的执行输入和执行结果。

具体行为：

- 读取 `ReviewExecutionInput.getPrContext()` 用于定位 GitHub PR
- 读取 `RuleEngineResult.getFindings()` 和 `RuleEngineResult.getSummary()`
- 读取 `LlmReviewResult.getFindings()` 和 `LlmReviewResult.getSummary()`
- 合并两类 `ReviewFinding`
- 去除重复 finding
- 按严重级别、文件路径、行号和来源排序
- 渲染一条 Markdown 总评论
- 调用 GitHub API 写回 PR

PR12 不重新选择文件，不重新做文件过滤，不重新分配上下文预算，也不重新组装 prompt。

## 和后续 PR 的边界

本 PR 只实现总评论回写，不实现 inline review comment。

原因：

- 当前 `ReviewFinding` 已包含文件路径和行号，足够生成稳定总评论
- GitHub inline comment 需要 diff position、commit sha 等更严格定位信息
- 总评论可以先保证完整演示链路闭环

后续如果需要行内评论，可以在本 PR 的聚合结果和渲染能力基础上继续扩展。

## 新增类

### `com.hdg.prysm.result.ReviewResultAggregator`

PR12 的结果聚合入口。

职责：

- 校验 `ReviewExecutionInput`、`RuleEngineResult` 和 `LlmReviewResult`
- 合并规则 finding 和 LLM finding
- 调用 `ReviewFindingDeduplicator` 去重
- 调用 `ReviewFindingSorter` 排序
- 输出 `ReviewAggregationResult`

### `com.hdg.prysm.result.ReviewAggregationResult`

聚合结果对象。

包含：

- 聚合后的 `List<ReviewFinding>`
- 规则 finding 数量
- LLM finding 数量
- 去重数量
- 规则摘要
- LLM 摘要

### `com.hdg.prysm.result.ReviewFindingDeduplicator`

finding 去重器。

去重依据：

- `filePath`
- `line` / `startLine`
- `ruleId` / `title`
- `title`

去重策略：

- 同一位置、同一规则或标题的问题只保留一条
- 规则来源优先于 LLM 来源
- 保持结果顺序稳定

### `com.hdg.prysm.result.ReviewFindingSorter`

finding 排序器。

排序规则：

```text
CRITICAL > HIGH > MEDIUM > LOW > INFO
filePath ASC
line/startLine ASC
source rank ASC
title ASC
```

规则来源优先于 LLM 来源，保证确定性结果优先展示。

### `com.hdg.prysm.comment.ReviewCommentRenderer`

Markdown 评论生成器。

职责：

- 生成审查摘要
- 展示规则 finding 数、LLM finding 数和去重数量
- 展示规则摘要和 LLM 摘要
- 按文件分组展示 finding
- 展示严重级别、标题、行号、来源、规则 ID、详情和修复建议
- 空结果时生成稳定的无问题摘要

评论示例：

```markdown
## PRysm Review Result

Found 1 issue(s). Rule findings: 1, LLM findings: 0, duplicates removed: 0.

### src/App.java

- **[HIGH] Merge conflict marker found** (line 12)
  - Source: `builtin` / Rule: `BUILTIN_CONFLICT_MARKER`
  - Detail: The changed code contains a merge conflict marker.
  - Suggestion: Resolve the marker.
```

### `com.hdg.prysm.github.GithubPullRequestCommentClient`

GitHub PR 评论回写客户端。

职责：

- 从 `PrContext` 读取 owner、repository 和 pull request number
- 从运行环境读取 `GITHUB_TOKEN`
- 使用 `prysm.github.api-base-url`
- 调用 GitHub issue comment API
- 在缺少 token、非 2xx 响应、网络失败或请求中断时给出明确失败原因

调用接口：

```text
POST /repos/{owner}/{repo}/issues/{pull_number}/comments
```

请求头：

```text
Accept: application/vnd.github+json
Authorization: Bearer <GITHUB_TOKEN>
X-GitHub-Api-Version: 2022-11-28
Content-Type: application/json
```

## Runner 集成

`PrReviewRunner` 在组装出 `ReviewExecutionInput` 后继续执行：

```text
RuleEngineRunner.run(executionInput)
LlmReviewRunner.run(executionInput)
ReviewResultAggregator.aggregate(executionInput, ruleResult, llmResult)
ReviewCommentRenderer.render(aggregationResult)
GithubPullRequestCommentClient.createComment(prContext, commentBody)
```

新增配置：

```yaml
prysm:
  comment:
    enabled: true
```

当 `prysm.comment.enabled=false` 时，Runner 仍会完成规则检查、LLM Review、聚合和评论渲染，但不会写回 GitHub PR。

## 降级策略

如果规则结果为空：

```text
继续处理 LLM 结果。
```

如果 LLM 结果为空：

```text
继续处理规则结果。
```

如果两类结果都为空：

```text
生成 “No actionable findings were reported.” 的稳定评论。
```

如果评论回写关闭：

```text
Pull request comment writing is disabled.
```

如果缺少 `GITHUB_TOKEN`：

```text
GITHUB_TOKEN must be configured to write pull request comments
```

如果 GitHub 回写失败：

```text
GitHub pull request comment failed with status <status>
```

## 测试覆盖

新增测试覆盖：

- 规则结果和 LLM 结果可以合并
- 重复 finding 可以去重
- 规则来源 finding 优先于 LLM 来源 finding
- finding 按严重级别、文件路径和行号稳定排序
- 空结果可以生成稳定聚合结果
- Markdown 评论包含摘要、文件分组、来源、规则 ID、详情和建议
- 空 finding 时生成无问题评论
- GitHub 评论 URI 拼接正确
- GitHub 评论请求使用 POST
- 请求携带 Authorization、Accept 和 Content-Type header
- 缺少 `GITHUB_TOKEN` 时不会调用 GitHub API
- 非 2xx GitHub 响应明确失败
- Runner 正确串起 PR10、PR11 和 PR12 链路
- Runner 关闭或非 GitHub Actions 环境下不会触发 PR12

## 本 PR 不做什么

- 不重新解析 hunk
- 不重新读取工作区文件
- 不做文件过滤
- 不做优先级排序
- 不做上下文预算分配
- 不组装 prompt 上下文
- 不直接调用模型客户端
- 不直接执行单个规则引擎
- 不实现 GitHub inline review comment
- 不把 `GITHUB_TOKEN` 写入项目文件

## 测试结果

本地 PR12 相关测试命令：

```powershell
.\mvnw.cmd -U -Dtest="ReviewResultAggregatorTest,ReviewCommentRendererTest,GithubPullRequestCommentClientTest,PrReviewRunnerTest" test
```

结果：

```text
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

全量测试命令：

```powershell
.\mvnw.cmd -U test
```

结果：

```text
Tests run: 89, Failures: 0, Errors: 0, Skipped: 2
BUILD SUCCESS
```
