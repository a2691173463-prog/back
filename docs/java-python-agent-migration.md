# Java + Python Agent 改造说明

## 一句话介绍

本项目从“Spring Boot 直接调用大模型”改造成“Java 主业务服务 + Python Agent 服务”的双服务架构。Java 负责用户、简历、题库、Redis 业务记录、RabbitMQ、MySQL、权限和 SSE 对外接口；Python FastAPI 服务负责大模型调用、LangGraph Checkpointer、Tool Calling、多模态简历诊断、流式面试回复和评估生成。

## 当前架构

```text
Vue 前端
  |
  | REST / SSE
  v
Spring Boot 后端
  | 用户鉴权、简历上传、Redis 业务记录、RabbitMQ、MySQL、SSE 转发
  |
  | HTTP
  v
Python FastAPI Agent Service
  | 显式 StateGraph、Checkpointer、Tool Calling、多模态、结构化结果
  |
  | 内部只读工具 HTTP
  v
Spring Boot 内部 Agent API
  | 简历、历史薄弱技能、题库
  |
  v
Qwen / DeepSeek / OpenAI-compatible API
```

当前面试图不是仅调用 `create_agent`，而是显式定义为：

```text
START
  -> interviewer（调用绑定工具的大模型）
       -> 没有 tool_calls：END
       -> 存在 tool_calls：tools
                            -> interviewer
```

`interviewer` 与 `tools` 的每次状态变化都由 Checkpointer 按 `thread_id` 保存。

## 改造前

改造前，Java 后端直接承担模型调用和 Prompt 拼接：

- Java 手动构造 `messages`
- Java 直接通过 HTTP 调用大模型接口
- 简历诊断、模拟面试、摘要、评估 Prompt 分散在 Java 业务代码里
- Redis 保存面试短期上下文、完整历史、系统提示词和摘要
- Java 解析模型返回结果并写入 MySQL

这种方式链路短，但 AI 编排和业务代码耦合较重，后续引入 LangChain、Tool Calling、RAG 或 LangGraph 时会让 Java 服务越来越臃肿。

## 改造后

当前 Java 侧只保留一个 AI 抽象：

- `AiClient`：业务层依赖的模型能力接口
- `PythonAgentClient`：唯一实现，通过 HTTP 调用 Python Agent 服务

已移除 Java 直连模型 fallback：

- 删除 `QwenApiClient`
- 删除 `AiClientRouter`
- 删除 `AiConfig`
- 删除 `ai.provider` 和 `ai.qwen` 配置

当前配置只保留 Python Agent 地址：

```yaml
ai:
  python-agent:
    base-url: http://localhost:8000
    timeout-seconds: 120
```

## 当前工作流

### 1. 简历上传与诊断

1. 前端上传 PDF 到 Spring Boot。
2. Java 保存文件和简历记录，投递 RabbitMQ 异步任务。
3. `ResumeMqConsumer` 解析 PDF 文本，并提取首页图片。
4. Java 调用 `AiClient.diagnoseResume(...)`。
5. `PythonAgentClient` 请求 Python 的 `/resume/diagnose`。
6. Python 组合文本和图片，调用多模态模型生成 JSON 诊断结果。
7. Java 接收结果并写回 MySQL。
8. 前端轮询简历状态，展示优势、缺点和优化建议。

### 2. 初始化模拟面试

1. 用户点击“基于简历开启 AI 模拟面试”。
2. Java 校验登录态、能量值和限流。
3. Java 创建 `interview_record`，生成本轮基础系统指令。
4. Java 调用 Python `/agent/threads` 初始化 Agent 线程。
5. Python 使用 `interviewId` 作为 `thread_id`，把系统消息和欢迎语写入 SQLite Checkpointer。
6. Java 把完整业务历史写入 Redis，前端进入聊天界面。

### 3. 面试对话

1. 用户在前端发送回答。
2. 前端通过 `EventSource` 连接 Java `/api/interview/chat`。
3. Java 只发送本轮回答、`interviewId`、`userId` 和 `resumeId`。
4. Python 根据 `thread_id` 从 Checkpointer 恢复此前 Agent 状态。
5. 模型按需选择 `get_resume_summary`、`get_user_weak_skills` 或 `search_question_bank`。
6. Python 工具通过带共享密钥的内部 HTTP 接口读取 Java 业务数据。
7. 工具结果返回 Agent，模型结合对话状态生成下一道问题。
8. LangGraph 把本轮消息、工具调用和结果保存为新 checkpoint。
9. Java 将 Python SSE 流转发给前端，并把用户与 AI 消息写入 Redis 完整业务历史。

### 4. 结束面试与评估

1. 用户点击“结束面试并评估”。
2. Java 从 Redis 读取完整面试对话。
3. Java 调用 `AiClient.evaluateInterview(...)`。
4. Python `/interview/evaluate` 根据完整对话生成 JSON 评估报告。
5. Java 解析总分、评价文本和技能画像。
6. Java 写入 `interview_record`，并更新用户能力画像。
7. Java 删除 Python checkpoint，并清理本轮 Redis 缓存。
8. 前端展示评估报告和历史对话。

## Checkpointer 与 Redis 的边界

- Checkpointer 保存 Agent 短期状态：消息、工具调用、工具结果和每轮执行快照。
- Redis 保存业务侧完整聊天记录，并继续承担限流、分布式锁和能量值等职责。
- MySQL 保存已完成面试、评分、简历和用户能力画像。
- Java 不再读取 Redis 滑动窗口并重复拼接给模型。

当前使用 SQLite Checkpointer，适合单机开发。多实例部署时应切换 PostgreSQL 或 Redis Checkpointer。

需要注意：LangGraph 与 Checkpointer 不是互相替代的关系。LangGraph 定义和执行状态图，Checkpointer 负责持久化图状态；如果移除 Checkpointer，图仍能执行，但服务重启后无法恢复多轮面试状态，也无法获得完整的 checkpoint 历史。

## 当前 Tool Calling

- `get_resume_summary()`：`resumeId` 由运行时上下文注入，模型不能修改。
- `get_user_weak_skills()`：`userId` 由运行时上下文注入，模型不能修改。
- `search_question_bank(keyword)`：模型只负责选择检索关键词。

前两个工具对模型显示为空参数工具，避免越权读取其他用户或简历。Python 不直连 MySQL，业务数据仍由 Java 掌控。

## Java 和 Python 分工

Java 负责稳定业务闭环：

- 登录和权限
- 简历上传和记录管理
- RabbitMQ 异步任务
- Redis 会话、限流、分布式锁
- MySQL 业务数据落库
- SSE 对外连接
- 能量值扣减和用户能力画像更新

Python 负责 AI 能力：

- 文本模型调用
- 多模态模型调用
- 流式输出
- Prompt 编排
- 简历诊断
- 面试摘要
- 面试评估
- LangGraph Checkpointer
- Tool Calling 与工具执行循环
- 后续 RAG 和更复杂的面试状态机

## LangSmith 可观测性

LangSmith tracing 只包裹模拟面试 LangGraph，不会默认追踪一次性的简历多模态诊断，因此不会把 Base64 图片作为该图的 trace 输入。启用后可以查看：

- `interviewer` 与 `tools` 节点执行顺序
- 模型输入输出、token 和耗时
- Tool Calling 参数、结果和异常
- 同一 `thread_id` 下的 Agent 调用链

配置位于 `agent-service/.env`：

```dotenv
LANGSMITH_TRACING=true
LANGSMITH_API_KEY=你的LangSmithKey
LANGSMITH_PROJECT=interview-agent-service
```

工具结果可能包含简历文本，因此演示时建议使用测试简历，并控制 LangSmith 项目的访问权限和数据保留策略。

## LangGraph Studio 本地调试

项目现在提供两个 Python 启动入口：

```text
start.ps1
  -> FastAPI 8000
  -> 提供给 Java 后端调用
  -> 使用项目自己的 SQLite Checkpointer

start-studio.ps1
  -> LangGraph Agent Server 2024
  -> 读取 langgraph.json
  -> 用于 Studio 图形化调试、线程测试和节点观察
```

Studio 地址：

```text
https://smith.langchain.com/studio/?baseUrl=http://127.0.0.1:2024
```

`langgraph.json` 不是使用 LangGraph 框架的必要条件，而是 LangGraph CLI、Agent Server、Studio 和部署系统用于发现图的配置文件。因此此前 FastAPI 中已经能运行 LangGraph，只是没有配置 CLI/Studio 入口。

## 面试时可以这样讲

项目早期版本由 Spring Boot 直接拼接完整上下文并调用大模型。为了降低业务与 AI 编排的耦合，我把模型层迁移为 Python FastAPI + LangGraph Agent。现在用 interviewId 作为 thread_id，通过 Checkpointer 持久化多轮状态，Java 每轮只发送本次回答；模型还能按需调用简历、薄弱技能和题库三个只读工具。工具通过受保护的 Java 内部接口获取数据，Python 不直连业务数据库。Java 继续负责鉴权、Redis、RabbitMQ、MySQL 和 SSE，因此既保留了 Java 的工程化能力，也具备继续扩展 RAG 和 Agent 工作流的基础。

## 后续优化计划

1. 加入 Checkpointer 消息裁剪或摘要中间件，控制长对话 token。
2. 把 Java 中剩余的面试系统 Prompt 迁移到 Python `prompts.py`。
3. 用 Pydantic 定义简历诊断、面试评估和技能画像的结构化输出。
4. 在当前显式 LangGraph 工具循环上继续增加面试阶段、难度调整和结束判断节点。
5. 接入 pgvector 做题库与技术资料 RAG，把检索封装为新工具。
6. 基于现有 LangSmith trace 建立固定测试集和 Agent 回归评估。
