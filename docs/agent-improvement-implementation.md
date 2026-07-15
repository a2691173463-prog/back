# Agent 七项优化实施说明

## 1. 显式 LangGraph 工作流

模拟面试不再只是一次普通模型调用，而是显式状态图：

```text
START -> interviewer -> tools -> interviewer -> END
```

`interviewer` 负责决定回答或调用工具，`tools` 负责执行受控业务查询。工具执行后回到面试官节点，由模型结合工具结果继续生成问题。

## 2. 面试状态与阶段管理

新增 `InterviewState`，除 `messages` 外还保存：

- `interview_stage`：opening、project、fundamentals、scenario
- `question_count`：已完成的问题轮数
- `current_skill`、`covered_skills`：当前和已覆盖技能
- `resume_loaded`、`profile_loaded`：简历和画像是否已经加载
- `transcript`：面向业务展示的原始问答记录
- `total_model_calls`：模型调用次数

面试官 Prompt 会根据轮数切换阶段。工具也按状态动态绑定，避免每轮把全部工具都暴露给模型。

## 3. Tool Calling 与 Command 状态更新

三个工具均改成异步 Tool Calling：

- `get_resume_summary`
- `get_user_weak_skills`
- `search_question_bank`

工具不让模型传入 `userId` 或 `resumeId`，这两个值由可信的 `InterviewContext` 注入，避免模型越权查询其他用户数据。

工具使用 `Command(update=...)` 同时返回 `ToolMessage` 并更新状态。例如简历工具成功后写入 `resume_loaded=true`，后续节点不再重复查询简历。

Python 不直接访问 MySQL。工具通过带内部密钥的 Java 只读接口读取业务数据，数据库权限和领域规则仍由 Spring Boot 管理。

## 4. Checkpointer 恢复与记录导出

FastAPI 使用异步 `AsyncSqliteSaver`，以 `interviewId` 作为 `thread_id` 保存每轮图状态。

继续面试时，Java 会优先从 Checkpointer 导出的 `transcript` 恢复前端问答；旧 Redis 记录作为兼容回退。结束面试时，如果 Redis 历史不存在，也可以使用 Checkpointer 中的原始问答完成评估。

业务记录和模型状态仍有明确边界：

- Checkpointer：Agent 消息、工具调用、阶段和执行快照。
- Redis：限流、能量值、锁以及旧版完整问答兼容缓存。
- MySQL：已完成面试、评分、简历和用户画像。

## 5. Python 异步 I/O 链路

以下链路已统一改为异步：

- 模型流：`model.astream()`
- LangGraph：`graph.astream()`、`aget_state()`、`aupdate_state()`
- 工具 HTTP：共享 `httpx.AsyncClient`
- Checkpointer：`aiosqlite` + `AsyncSqliteSaver`
- FastAPI SSE：异步生成器

共享 HTTP Client 使用连接池和 Keep-Alive，避免每次工具调用重新建立连接。应用启动和关闭时统一创建、释放连接资源。

## 6. 结构化 SSE 事件

Python 向 Java 输出结构化事件：

```json
{"type":"token","content":"..."}
{"type":"tool_start","tool":"get_resume_summary"}
{"type":"tool_done","tool":"get_resume_summary"}
{"type":"state","stage":"project","question_count":1}
{"type":"done"}
```

Java 只把 `token` 内容写入完整问答记录，同时把工具进度转发给前端。前端可以显示“正在读取简历”“正在查询历史薄弱项”等状态，不会把工具 JSON 混入 AI 回答。

## 7. 可重复压测与可观测性

项目新增确定性的 OpenAI 兼容 Mock 模型、同步基线服务和异步压测客户端，可以在不消耗真实模型额度的情况下复测成功率、吞吐量、TTFT 和总延迟。

本轮实际数据和结论见 `docs/concurrency-benchmark-report.md`。测试结果没有证明 async 自动提高吞吐，因此简历中不应虚构性能提升；它目前带来的主要价值是统一异步 I/O、降低长期占用工作线程的必要性，并为后续连接数、背压和资源利用率优化提供基础。

LangSmith 继续用于查看节点顺序、模型调用、工具参数、工具结果和耗时。压测负责性能回归，LangSmith 负责 Agent 执行链路定位，两者职责不同。

## 当前工作流

```text
Vue
  -> Spring Boot 创建/恢复 interview_record
  -> Python 按 thread_id 恢复 Checkpointer
  -> interviewer 根据 State 选择阶段与可用工具
  -> tools 通过 Java 内部接口查询简历/画像/题库
  -> interviewer 流式生成下一道问题
  -> Python 结构化 SSE -> Java -> Vue
  -> Java 保存业务问答
  -> 结束时 Python 评估，Java 写 MySQL 并更新用户画像
```

## 面试表述

> 我把原来 Java 中直接拼上下文调用模型的逻辑迁移成了 Python FastAPI + LangGraph。面试过程由显式状态图驱动，Checkpointer 按 interviewId 恢复状态，模型按阶段动态选择简历、历史薄弱项和题库工具。工具只通过受保护的 Java 内部接口访问业务数据，Python 不直连 MySQL。模型流、工具 HTTP 和 Checkpointer 使用统一异步链路，Java 继续负责鉴权、业务记录、Redis、RabbitMQ 和 MySQL。为了避免凭感觉宣称性能提升，我还建立了固定延迟 Mock 压测，记录成功率、吞吐和 P95 延迟，并如实保留未出现吞吐提升的结果。
