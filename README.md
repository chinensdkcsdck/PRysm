# PRysm

## 跨仓库接入

其他仓库只需要添加一个 workflow，就可以在 PR 触发时调用 Prysm 审查并回写评论。

```yaml
name: Prysm PR Review

on:
  pull_request:
    types: [opened, synchronize, reopened]

permissions:
  contents: read
  pull-requests: read
  issues: write

jobs:
  review:
    uses: Remohdg/PRysm-hdg/.github/workflows/prysm-review-reusable.yml@main
    secrets:
      LLM_API_KEY: ${{ secrets.LLM_API_KEY }}
```

目标仓库需要在 `Settings -> Secrets and variables -> Actions` 中配置：

```text
LLM_API_KEY
```

默认模型配置为千问兼容接口：

```text
LLM_API_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_MODEL=qwen-plus
```

如需覆盖模型配置，可以在调用 workflow 时传入：

```yaml
with:
  llm-api-base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  llm-model: qwen-plus
```
Workflow smoke test for PR review comment writeback.

Optimization defaults smoke test.

Test2 branch smoke test.
