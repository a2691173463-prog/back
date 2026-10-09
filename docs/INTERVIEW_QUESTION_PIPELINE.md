# 面试题编排与 RAG 题库索引

## 改造后的流程

```text
简历/画像并行加载
  -> 提取完整项目上下文 + 独立技术栈摘要
  -> 项目深挖：职责、实现、问题解决（最多三问）
  -> 固定阶段过渡
  -> 技能规划 Query
  -> interview_questions 混合检索并重排 Top3
  -> 模型选择一道并轻量改写
  -> 单问题校验
  -> 记录来源、参考答案、分数和命中
```

基础问答阶段不会向模型注入完整简历，只使用技术栈摘要、最近回答中的技术关键词、已覆盖主题和已问题目。项目经历只在前三道项目深挖题中使用。独立 `scenario` 阶段已移除；工程边界问题可作为项目深挖的“问题解决”维度，但不会无限追问。

## 题目索引

上传文档时，系统从明确的问答段落中抽取题目与参考答案，写入 `rag.interview_questions`。历史文档可执行：

```powershell
Invoke-RestMethod -Method Post http://127.0.0.1:8000/knowledge/questions/reindex
```

检索记录位于 `rag.question_retrieval_logs`。被最终采用的候选会标记 `selected=true`，并保存实际询问文本；题目表同步累计 `retrieval_count` 和 `last_retrieved_at`。

## LangSmith 前后对比

准备两份同结构 JSONL，每行至少包含：

```json
{"stage":"fundamentals","stage_question_number":1,"expected_skill":"Redis","expected_topic":"缓存穿透","question":"什么是缓存穿透，常见治理方式有哪些？","source_type":"RAG","source_question_id":"uuid"}
```

在旧版本和新版本上使用同一批输入，各导出一份结果，然后运行：

```powershell
uv run python benchmarks/interview_quality_eval.py `
  --before benchmarks/results/interview-before.jsonl `
  --after benchmarks/results/interview-after.jsonl `
  --output benchmarks/results/interview-quality-report.json
```

启用 `LANGSMITH_TRACING=true` 后，LangSmith Tracing 中会出现 `interview-rag-before-after-evaluation`。比较指标包括单问题合规率、主题相关性、来源可追溯率、重复率和阶段越界率。真实相关性还应在 LangSmith Dataset 中人工标注一批 `expected_skill`/`expected_topic`，避免只凭关键词指标下结论。
