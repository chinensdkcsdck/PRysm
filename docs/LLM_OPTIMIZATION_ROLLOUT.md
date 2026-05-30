# LLM 优化灰度切换说明

## 目标

本方案用于对比三项 LLM Review 优化相对 baseline 的耗时收益：

1. `max-output-tokens`：限制模型最大输出长度
2. `fast-path`：小 PR / 文档 PR 使用快模型
3. `compact-prompt`：压缩 prompt 和 output schema

所有优化默认关闭，合入后默认行为与原流程一致。

## 实验组

建议每轮只开启一个优化，避免多个优化同时开启后无法归因。

```text
baseline
exp_1_max_output_tokens
exp_2_fast_path
exp_3_compact_prompt
combined
```

## GitHub Actions 配置

必需 Repository Secret：

```text
LLM_API_KEY
```

可选 Repository Variables：

```text
LLM_API_BASE_URL
LLM_MODEL
PRYSM_OPT_GROUP
PRYSM_OPT_ROLLOUT_PERCENT
PRYSM_OPT_MAX_OUTPUT_TOKENS_ENABLED
PRYSM_LLM_MAX_OUTPUT_TOKENS
PRYSM_OPT_FAST_PATH_ENABLED
PRYSM_OPT_FAST_PATH_MODE
PRYSM_LLM_FAST_MODEL
PRYSM_OPT_COMPACT_PROMPT_ENABLED
```

默认值：

```text
PRYSM_OPT_GROUP=baseline
PRYSM_OPT_ROLLOUT_PERCENT=0
PRYSM_OPT_MAX_OUTPUT_TOKENS_ENABLED=false
PRYSM_LLM_MAX_OUTPUT_TOKENS=800
PRYSM_OPT_FAST_PATH_ENABLED=false
PRYSM_OPT_FAST_PATH_MODE=fast_model
PRYSM_LLM_FAST_MODEL=qwen-turbo
PRYSM_OPT_COMPACT_PROMPT_ENABLED=false
```

## 实验运行方式

### Baseline

```text
PRYSM_OPT_GROUP=baseline
PRYSM_OPT_MAX_OUTPUT_TOKENS_ENABLED=false
PRYSM_OPT_FAST_PATH_ENABLED=false
PRYSM_OPT_COMPACT_PROMPT_ENABLED=false
```

### 优化一：限制模型输出长度

```text
PRYSM_OPT_GROUP=exp_1_max_output_tokens
PRYSM_OPT_MAX_OUTPUT_TOKENS_ENABLED=true
PRYSM_LLM_MAX_OUTPUT_TOKENS=800
PRYSM_OPT_FAST_PATH_ENABLED=false
PRYSM_OPT_COMPACT_PROMPT_ENABLED=false
```

对比：

```text
baseline.llm_review.durationMs
exp_1_max_output_tokens.llm_review.durationMs
```

### 优化二：小 PR / 文档 PR 快模型

```text
PRYSM_OPT_GROUP=exp_2_fast_path
PRYSM_OPT_MAX_OUTPUT_TOKENS_ENABLED=false
PRYSM_OPT_FAST_PATH_ENABLED=true
PRYSM_OPT_FAST_PATH_MODE=fast_model
PRYSM_LLM_FAST_MODEL=qwen-turbo
PRYSM_OPT_COMPACT_PROMPT_ENABLED=false
```

命中规则：

```text
changedFiles <= 2
additions + deletions <= 30
且文件路径属于 README*、*.md、docs/**、*.yml、*.yaml、.github/workflows/**
```

### 优化三：压缩 prompt

```text
PRYSM_OPT_GROUP=exp_3_compact_prompt
PRYSM_OPT_MAX_OUTPUT_TOKENS_ENABLED=false
PRYSM_OPT_FAST_PATH_ENABLED=false
PRYSM_OPT_COMPACT_PROMPT_ENABLED=true
```

对比：

```text
originalPromptCharacters
compactPromptCharacters
promptCharactersSaved
promptCompactRatio
llm_review.durationMs
```

### 组合实验

```text
PRYSM_OPT_GROUP=combined
PRYSM_OPT_MAX_OUTPUT_TOKENS_ENABLED=true
PRYSM_OPT_FAST_PATH_ENABLED=true
PRYSM_OPT_COMPACT_PROMPT_ENABLED=true
```

组合实验只能看整体收益，不用于判断单个优化贡献。

## Trace 字段

`llm_review` span 会输出：

```json
{
  "optimizationGroup": "baseline",
  "modelName": "qwen-plus",
  "effectiveModel": "qwen-plus",
  "maxOutputTokensEnabled": false,
  "maxOutputTokens": 800,
  "fastPathEnabled": false,
  "fastPathMatched": false,
  "fastPathReason": null,
  "compactPromptEnabled": false,
  "originalPromptCharacters": 1815,
  "compactPromptCharacters": 1815,
  "promptCharactersSaved": 0,
  "promptCompactRatio": 1.0,
  "promptCharacters": 1815,
  "estimatedPromptTokens": 454,
  "llmFindings": 2
}
```

summary 会输出：

```json
{
  "optimizationGroup": "baseline",
  "modelName": "qwen-plus",
  "effectiveModel": "qwen-plus",
  "totalDurationMs": 16482,
  "promptCharacters": 1815,
  "estimatedPromptTokens": 454,
  "llmFindings": 2,
  "commentWritten": true
}
```

## 对比表

建议每轮实验记录：

```text
| group | totalDurationMs | llmDurationMs | promptChars | effectiveModel | findings | commentWritten |
|---|---:|---:|---:|---|---:|---|
| baseline | 16482 | 14731 | 1815 | qwen-plus | 2 | true |
| exp_1_max_output_tokens | 12000 | 10200 | 1815 | qwen-plus | 2 | true |
| exp_2_fast_path | 6500 | 5000 | 1815 | qwen-turbo | 2 | true |
| exp_3_compact_prompt | 13500 | 11700 | 1200 | qwen-plus | 2 | true |
```

## 回滚

运行时回滚：

```text
PRYSM_OPT_MAX_OUTPUT_TOKENS_ENABLED=false
PRYSM_OPT_FAST_PATH_ENABLED=false
PRYSM_OPT_COMPACT_PROMPT_ENABLED=false
PRYSM_OPT_GROUP=baseline
```

代码回滚：

- 可按单个提交 revert
- 三个优化互相独立，默认关闭时不改变现有行为
