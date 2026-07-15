# AI 模拟面试系统

这是一个面向校招场景的 AI 模拟面试平台。项目采用 Java + Python 的分层架构：Java 后端负责用户、简历、题库、面试记录等业务，Python 服务负责大模型调用、LangGraph 工作流、Tool Calling、Checkpointer 和 LangSmith 链路追踪。

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
- Agent 服务：Python 3.13、FastAPI、LangChain、LangGraph、SQLite Checkpointer、LangSmith
- 模型接口：兼容 OpenAI Chat Completions 协议的模型服务

## 本地启动

### 1. 基础设施

启动 MySQL、Redis 和 RabbitMQ，然后执行 [back/sql/schema.sql](back/sql/schema.sql) 初始化数据库。

Java 后端通过环境变量读取本地敏感配置。在 PowerShell 中设置：

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的数据库密码"
$env:AI_INTERNAL_SECRET="请替换为随机共享密钥"
```

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
