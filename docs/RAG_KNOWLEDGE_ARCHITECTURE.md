# QA 面试知识库实施说明

## 定位

系统知识库只接收 QA 型技术面试 PDF。知识检索以结构化题目 `rag.interview_questions` 为唯一检索存储，不再维护通用文本 chunk 知识库。

文档仍支持三种作用域：`GLOBAL`、`POSITION`、`PRIVATE`。旧文档的作用域和文档元数据保持不变。

## 入库流程

1. PDF 文本清洗并识别 Question Boundary。
2. 按“当前问题 → 下一问题前”形成完整 QA 单元，不按固定字符数切块。
3. LLM 只负责结构化抽取和标注 `skill/topic/difficulty/keywords/has_code`，并保留完整 `reference_answer`。
4. Embedding 输入使用技能、主题、问题和关键词，不把完整答案加入向量文本。
5. QA 及其向量写入 `rag.interview_questions`；文档元数据写入 `rag.documents`。
6. 如果无法得到完整 QA 单元，上传明确失败，不再回退到 800+120 字符切块。

## 检索流程

基础知识阶段调用 `search_interview_questions()`，对 `rag.interview_questions` 做向量召回与关键词召回，过滤已问问题和已跳过主题，再进行 rerank 和最终阈值筛选。

`search_interview_knowledge` 工具保留，但底层同样调用 `search_interview_questions()`，因此系统不存在第二套通用 chunk 检索路径。

## 数据表

保留：
- `rag.documents`：PDF 文档元数据、作用域、状态与统计。
- `rag.interview_questions`：完整问题、参考答案、技能/主题/难度、metadata 和 embedding。
- `rag.question_retrieval_logs`：面试题召回、得分、最终选择日志。

已删除旧版 `rag.chunks` 与 `rag.retrieval_logs`。启动迁移会使用 `DROP TABLE IF EXISTS` 清理旧安装中的这两张遗留表。

## 关键配置

```dotenv
RAG_CANDIDATE_K=10
RAG_FINAL_SCORE_THRESHOLD=0.45
RAG_RERANK_ENABLED=true
RAG_RERANK_MODEL=qwen3.7-text-rerank
```

`rag_chunk_size`、`rag_chunk_overlap`、`rag_top_k`、`rag_score_threshold` 已删除。关闭 rerank 时，面试题检索仍使用向量、关键词与倒数排名融合结果。