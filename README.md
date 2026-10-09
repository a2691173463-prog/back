# AI 模拟面试系统

这是一个面向校招场景的 AI 模拟面试平台。项目采用 Java + Python 的分层架构：Java 后端负责用户、简历、知识文档上传和面试记录等业务，Python 服务负责大模型调用、LangGraph 工作流、Tool Calling、RAG、Checkpointer 和 LangSmith 链路追踪。

## 项目结构

```text
.
├── front/          Vue 3 + TypeScript 前端
├── back/           Spring Boot 业务后端
├── agent-service/  FastAPI + LangChain/LangGraph Agent 服务
└── docs/           架构、迁移和测试文档
```

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Element Plus、Pinia
- 业务后端：Java 17、Spring Boot 3、MyBatis-Plus、MySQL、Redis、RabbitMQ
- Agent 服务：Python 3.13、FastAPI、LangChain、LangGraph、PostgreSQL Checkpointer、pgvector + pg_trgm 混合 RAG、Qwen 重排序、LangSmith
- 模型接口：兼容 OpenAI Chat Completions 协议的模型服务

## 本地启动

### 1. 基础设施

启动 MySQL、Redis、RabbitMQ 和 PostgreSQL/pgvector，然后执行 [back/sql/schema.sql](back/sql/schema.sql) 初始化 MySQL 业务库。PostgreSQL 可通过 `infra/postgres/compose.yml` 启动，向量表和 Checkpointer 表由 Python 服务初始化。

Java 后端会自动读取 `back/.env`。首次运行时创建本地配置：

```powershell
cd back
Copy-Item .env.example .env
# 编辑 .env，填写数据库密码
```

也可以继续使用 PowerShell 环境变量；系统环境变量的优先级高于 `.env`。

### 2. Python Agent 服务

```powershell
cd agent-service
Copy-Item .env.example .env
# 编辑 .env，填写模型 API Key，并让 INTERNAL_AGENT_SECRET 与 Java 端保持一致
uv sync
uv run uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

如需使用 LangGraph Studio 调试工作流：

```powershell
uv run langgraph dev --port 2024
```

### 3. Java 后端

```powershell
cd back
mvn spring-boot:run
```

默认地址为 `http://localhost:8080`。

### 4. 前端

```powershell
cd front
npm install
npm run dev
```

## 配置安全

`.env`、数据库快照、上传的简历、依赖目录和构建产物均不会提交到 Git。仓库只保留 `.env.example` 作为配置模板，请不要把真实 API Key、数据库密码或用户简历提交到公开仓库。

更多实现细节见 [docs](docs) 目录。

简历模板在线制作、云端草稿和 AI 优化的迁移步骤见
[简历模板第二至第四阶段实施说明](docs/resume-template-stage-2-4.md)。

## 功能变更记录

同一天的修改按本地时间（UTC+08:00）分节点追加；已有节点只保留，不覆盖。

### 2026-10-09

#### 20:40

- 补齐异步链路：RabbitMQ 监听回调中的数据库读取与领取任务也交给有界执行器，避免监听线程等待 JDBC；增加该行为的自动化测试。
- 使用最终构建再次预热并完成端到端验证：12/12 成功，单消费者 `prefetch=3`，模型最大并发 3，RabbitMQ Ready/Unacked 均为 0。与同步版预热结果相比，3.708 秒对 3.939 秒（异步版慢约 6.23%）；这是固定 750 ms Mock AI 的一次小样本测试，不代表真实模型环境的普遍性能结论。完整结果见 [简历 MQ 基准测试](back/benchmarks/README.md)。

#### 20:15

- 在 GitHub 分支 [`codex/backup-sync-resume-20261009`](https://github.com/a2691173463-prog/back/tree/codex/backup-sync-resume-20261009) 保存改造前的完整同步版本（提交 `0e8158f`）。
- 简历解析改为一个 RabbitMQ 监听消费者、`prefetch=3`：PDF 处理交给有界线程池，Java 到 Python 使用异步 HTTP，Python 诊断端点使用异步模型调用；返回的 `CompletableFuture` 完成后才 ACK，失败仍走延迟重试或死信队列。
- 新增 Java/Python 异步自动化测试及同步/异步同机 Mock AI 对比脚本。预热后 12 份简历：同步版 3.708 秒、异步版 3.727 秒，均 12/12 成功、模型最大并发 3；异步版 RabbitMQ Ready/Unacked 均为 0。另验证文件永久失败入 DLQ、模型前两次 503 后第三次重试成功。结果见 [简历 MQ 基准测试](back/benchmarks/README.md)。

### 2026-09-18

#### 20:30

- 补充 RAG 题目技能标签配置：新增“计算机网络”和“操作系统”的关键词集合，并将两类规则置于 Java 并发之前，避免进程/线程类操作系统题被误标为 Java 并发。
- 新增标签规则回归测试，覆盖 TCP 三次握手、进程与线程区别及 Java `synchronized` 线程安全题的分类边界。

#### 19:13

- 最终面试评估由“每个技能一次模型调用”改为“一次结构化调用返回全部分项评分”，增加 45 秒独立超时且关闭叠加重试；总分继续由 Python 根据分项分数确定性计算。
- 新增问题与候选人回答的严格配对：结束面试时未回答的最后一道题保留在对话历史中但不参与评分，评估维度只来自已有有效回答，不再自动补充或重复评价无证据技能。
- 统一评估技能语义：项目追问归入“项目工程能力”，RAG 题库中的“通用”标签改用计划技能（如 MyBatis），同时保留原始 `source_skill` 便于追踪；修复 `Java/JVM/通用` 重复计分问题。
- 后端新增 `evaluation_skills` JSON 持久化及自动补列，前端将总体摘要和分项能力卡片分开展示；若最后一道题尚未回答，结束确认框会明确提示该题不计分。
- 修正问题末尾 `。？` 的标点清洗，并补充未回答题排除、单次评估调用和技能去重的回归测试。

#### 16:19

- 将项目追问和 RAG 未命中兜底题从 Pydantic Tool Calling 改为普通文本流式输出；问题的技能、主题和覆盖维度由 Python 本地组装，避免结构化流式解析长时间等待。
- 为面试问题使用独立的 30 秒模型超时并关闭 SDK/图节点叠加重试；模型失败时直接返回当前考察维度对应的本地兜底问题。
- 基础题去重改为同时使用知识库题目 ID、基础题主题、题目文本相似度和技能提问次数；修复已覆盖主题只写入查询文本、没有真正传给候选过滤器的问题。

#### 15:33 前（原记录，精确时间未记录）

- 修复项目追问切换到基础题时只显示过渡语、实际问题未送达前端的问题；过渡内容现在会明确显示“第一题”，并保证问题后缀继续流式输出。
- 基础题选题改为确定性的技能轮转：按简历技术栈、岗位技能和训练模式薄弱项生成技能计划，优先覆盖不同技能，再逐轮提升难度，避免连续围绕 JVM 出题。
- RAG 检索新增“精确技能 + 难度”和“精确技能”两级检索，只有精确技能没有可用题目时才回退到技能族和宽泛检索。
- 移除面试进行中的同步 LLM 上下文摘要调用，改为仅裁剪工作消息窗口；完整 `transcript` 仍保留用于最终评估，减少中途数十秒等待和额外模型调用。
- 修正模型调用计数：知识库直接命中的基础题不再误计为一次模型调用。
- 新增阶段切换流式输出、确定性技能轮转和无模型上下文裁剪的回归测试。
