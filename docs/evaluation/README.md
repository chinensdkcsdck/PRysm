# PRysm 离线效果评测

评测数据由人工标注结果和同一批 PR 的系统预测组成。`sample-dataset.json` 只演示格式，不代表项目真实准确率。

每个样本包含一个稳定编号、人工确认的问题、系统预测的问题以及本次模型调用的输入和输出 Token。人工复核时要给同一个根因分配相同的问题标识，避免同文件、同类别、相近行号的不同缺陷被错误合并。评测时问题标识、文件和类别相同，且行号误差不超过指定范围才算命中；一个标准答案最多只能被一个预测命中。

先构建项目，再运行评测：

```powershell
.\mvnw.cmd -DskipTests package
java -jar target\Prysm-0.0.1-SNAPSHOT.jar `
  --prysm.runner.enabled=false `
  --prysm.evaluation.enabled=true `
  --prysm.evaluation.dataset=docs/evaluation/sample-dataset.json `
  --prysm.evaluation.output=target/evaluation-report.json
```

报告包含 TP、FP、FN、Precision、Recall、F1、精确位置命中率、重复预测率和每个有效问题的 Token 成本。正式评测时应使用人工复核的真实 PR 数据替换示例，并固定模型、提示词、规则版本和行号容差。
