export interface TemplateInfo {
  id: number
  name: string
  category?: string
  thumbnailUrl?: string
  downloadUrl?: string
  description?: string
  createTime?: string
  exampleKey?: string
  builtIn?: boolean
}

export interface Education {
  uid: string
  school: string
  major: string
  degree: string
  period: string
  details: string
}

export interface Project {
  uid: string
  name: string
  role: string
  period: string
  tech: string
  description: string
}

export interface Experience {
  uid: string
  company: string
  role: string
  period: string
  location: string
  description: string
}

export interface ResumeContent {
  personal: { name: string; targetRole: string; phone: string; email: string; location: string; website: string; avatar?: string }
  summary: string
  education: Education[]
  projects: Project[]
  experiences: Experience[]
  skills: string
  awards: string
}

const example = (
  key: string,
  personal: ResumeContent['personal'],
  summary: string,
  education: Omit<Education, 'uid'>[],
  projects: Omit<Project, 'uid'>[],
  experiences: Omit<Experience, 'uid'>[],
  skills: string,
  awards: string
): ResumeContent => ({
  personal,
  summary,
  education: education.map((item, index) => ({ uid: `${key}-edu-${index}`, ...item })),
  projects: projects.map((item, index) => ({ uid: `${key}-project-${index}`, ...item })),
  experiences: experiences.map((item, index) => ({ uid: `${key}-experience-${index}`, ...item })),
  skills,
  awards
})

export const resumeExamples: Record<string, ResumeContent> = {
  java: example(
    'java',
    { name: '陈同学', targetRole: 'Java 后端开发工程师', phone: '138****7621', email: 'chen.student@example.com', location: '杭州', website: 'github.com/example-java' },
    '计算机相关专业硕士在读，具备 Java 后端项目与实习经验。熟悉 Spring Boot、MySQL、Redis 和消息队列，能够围绕并发、缓存一致性与接口性能完成问题定位和方案落地。',
    [{ school: '华东理工大学', major: '计算机技术', degree: '硕士', period: '2025.09 - 2028.06', details: '专业前 20%；核心课程：高级数据库、分布式系统、算法设计。' }],
    [
      { name: 'AI 模拟面试与简历诊断平台', role: '后端负责人', period: '2026.02 - 2026.06', tech: 'Spring Boot / MySQL / Redis / RabbitMQ / Python', description: '• 设计 Java 业务服务与 Python Agent 服务的 HTTP 协作边界，统一鉴权、超时与错误处理。\n• 使用 Redis 管理热点数据和短期状态，引入消息队列异步处理面试结果，将接口平均响应时间降低约 38%。\n• 基于 MySQL 保存面试记录、用户画像与简历草稿，完成索引设计和慢查询排查。' },
      { name: '校园二手交易平台', role: '核心开发', period: '2025.10 - 2025.12', tech: 'Spring Boot / MyBatis-Plus / Redis / JWT', description: '• 完成商品、订单、收藏和用户模块，使用 JWT 与拦截器实现登录态校验。\n• 对商品详情增加缓存与随机过期时间，压测下数据库查询量下降约 55%。' }
    ],
    [{ company: '某企业数字化研发中心', role: 'Java 开发实习生', period: '2026.06 - 2026.09', location: '杭州', description: '• 参与内部工单系统迭代，独立完成查询、导出和权限校验接口。\n• 补充 20 余个单元测试并修复空指针与重复提交问题，协助完善接口日志和异常码规范。' }],
    'Java：熟悉集合、异常、线程池及 JVM 基础。\n框架：Spring Boot、Spring MVC、MyBatis-Plus。\n中间件：MySQL、Redis、RabbitMQ；了解 Docker、Nginx。',
    '校级一等奖学金；全国大学生计算机设计大赛省级二等奖。'
  ),
  agent: example(
    'agent',
    { name: '林同学', targetRole: 'AI Agent 开发工程师', phone: '186****4108', email: 'lin.agent@example.com', location: '上海', website: 'github.com/example-agent' },
    '人工智能方向硕士在读，具备 Python、LangChain 与 LangGraph 项目经验。能够完成工具调用、状态管理、RAG 检索和 LangSmith 可观测链路，并理解 Agent 服务与 Java 业务系统的工程协作方式。',
    [{ school: '南京理工大学', major: '人工智能', degree: '硕士', period: '2025.09 - 2028.06', details: '研究方向：大语言模型应用与信息检索；GPA 3.7/4.0。' }],
    [
      { name: '面向校招的智能面试 Agent', role: 'Agent 开发', period: '2026.03 - 2026.07', tech: 'Python / FastAPI / LangGraph / LangSmith / Qwen', description: '• 使用 LangGraph 构建 interviewer、tools 节点及条件路由，通过 PostgreSQL checkpointer 按 thread_id 保存工作状态。\n• 封装简历摘要、薄弱技能与用户知识库检索 Tool Calling，并使用 pgvector 完成 PDF 切片和向量召回。\n• 接入 LangSmith 跟踪模型耗时、工具参数和 Token 消耗，基于测试集迭代提示词。' },
      { name: '课程知识库问答系统', role: '独立开发', period: '2025.11 - 2026.01', tech: 'LangChain / Milvus / BGE / FastAPI', description: '• 完成 PDF 清洗、分块、向量化和混合检索，加入文档来源引用。\n• 通过重排与查询改写将人工评测 Top-3 召回率从 73% 提升至 86%。' }
    ],
    [{ company: '某智能应用实验室', role: '算法工程实习生', period: '2026.07 - 2026.10', location: '上海', description: '• 维护企业知识助手评测脚本，整理 200 余条问答样本并分析失败类型。\n• 优化长上下文裁剪策略，使单轮平均输入 Token 下降约 24%。' }],
    'Python：熟悉异步编程、Pydantic、FastAPI。\nAgent：LangChain、LangGraph、Tool Calling、Checkpointer、LangSmith。\nRAG：文本分块、Embedding、向量检索、重排与评测。',
    '研究生学业奖学金；校级人工智能应用挑战赛一等奖。'
  ),
  frontend: example(
    'frontend',
    { name: '周同学', targetRole: '前端开发工程师', phone: '177****9280', email: 'zhou.web@example.com', location: '深圳', website: 'github.com/example-web' },
    '具备 Vue 3 与 TypeScript 项目经验，关注组件复用、交互状态和性能优化。能够独立完成从接口联调、响应式适配到工程构建的完整开发流程。',
    [{ school: '广东工业大学', major: '软件工程', degree: '本科', period: '2022.09 - 2026.06', details: 'GPA 3.6/4.0；主修 Web 开发、数据结构、计算机网络。' }],
    [
      { name: '个人知识管理工作台', role: '前端负责人', period: '2025.09 - 2025.12', tech: 'Vue 3 / TypeScript / Pinia / Vite', description: '• 抽象表格、筛选器和编辑弹窗等 12 个业务组件，减少约 30% 重复代码。\n• 使用路由懒加载和分包优化，首屏资源体积下降 27%。\n• 完成 390px 至 1440px 响应式适配，并补充关键交互的异常与空状态。' },
      { name: '实时协作文档 Demo', role: '独立开发', period: '2025.04 - 2025.06', tech: 'Vue 3 / WebSocket / Node.js', description: '• 实现在线状态、光标同步和内容自动保存，处理断线重连与消息去重。\n• 使用虚拟列表优化长文档评论区，滚动帧率保持稳定。' }
    ],
    [{ company: '某互联网产品团队', role: '前端开发实习生', period: '2025.12 - 2026.03', location: '深圳', description: '• 参与运营后台需求迭代，完成 8 个页面及接口联调。\n• 修复移动端兼容问题并补充埋点，协助定位两项线上交互异常。' }],
    '前端：HTML5、CSS3、JavaScript、TypeScript、Vue 3、Pinia。\n工程化：Vite、ESLint、Git；了解 Vitest、Playwright。\n基础：熟悉浏览器渲染、事件循环、HTTP 与常见性能优化。',
    '蓝桥杯 Web 应用开发省级二等奖；校级优秀毕业设计。'
  ),
  qa: example(
    'qa',
    { name: '王同学', targetRole: '测试开发工程师', phone: '159****3362', email: 'wang.qa@example.com', location: '北京', website: 'github.com/example-qa' },
    '软件工程本科，具备接口自动化、性能测试和测试平台开发经历。能够从需求风险、用例设计、自动化执行到缺陷复盘形成闭环，并使用代码提升测试效率。',
    [{ school: '北京信息科技大学', major: '软件工程', degree: '本科', period: '2022.09 - 2026.06', details: '专业前 15%；核心课程：软件测试、操作系统、数据库系统。' }],
    [{ name: '接口自动化测试平台', role: '核心开发', period: '2025.08 - 2025.11', tech: 'Python / Pytest / FastAPI / MySQL', description: '• 支持环境、用例、参数化数据和测试报告管理，覆盖登录、订单等 6 个业务模块。\n• 接入 GitLab CI 后将核心接口回归时间从约 90 分钟缩短至 18 分钟。\n• 增加失败重试、响应断言和敏感字段脱敏，降低偶发失败对结果的干扰。' }],
    [{ company: '某电商质量保障部', role: '测试开发实习生', period: '2025.11 - 2026.02', location: '北京', description: '• 参与订单链路测试，设计 120 余条功能与异常用例，提交并跟踪 18 个有效缺陷。\n• 使用 JMeter 完成活动接口压测，协助定位数据库连接池配置问题。' }],
    '测试：测试理论、接口测试、性能测试、缺陷管理。\n开发：Python、Java 基础、SQL、Shell。\n工具：Pytest、JMeter、Postman、GitLab CI、Docker。',
    '全国大学生软件测试大赛省级二等奖。'
  ),
  data: example(
    'data',
    { name: '赵同学', targetRole: '数据分析师', phone: '135****5816', email: 'zhao.data@example.com', location: '广州', website: 'github.com/example-data' },
    '统计学本科，具备 SQL、Python 数据处理和可视化经验。能够围绕业务问题搭建指标、完成分析并输出可执行建议，重视数据口径与结论可解释性。',
    [{ school: '暨南大学', major: '统计学', degree: '本科', period: '2022.09 - 2026.06', details: '专业前 10%；主修回归分析、时间序列、数据库与数据挖掘。' }],
    [{ name: '用户留存与转化分析', role: '数据分析', period: '2025.09 - 2025.11', tech: 'Python / SQL / Pandas / Tableau', description: '• 基于 30 万条行为日志建立注册、激活、留存和付费漏斗，统一事件与用户口径。\n• 通过分群和路径分析发现新手引导流失节点，提出页面与触达策略建议。\n• 使用 A/B 测试评估改版效果，实验组次日留存提升 6.8 个百分点。' }],
    [{ company: '某零售数据团队', role: '数据分析实习生', period: '2025.12 - 2026.03', location: '广州', description: '• 维护经营日报与 15 项核心指标，使用 SQL 自动化取数。\n• 梳理异常波动排查流程，将日报产出时间从 50 分钟缩短至 15 分钟。' }],
    '数据：SQL、Python、Pandas、NumPy、Excel。\n分析：漏斗分析、留存分析、用户分群、A/B 测试。\n可视化：Tableau、Power BI；了解常用统计模型。',
    '全国大学生市场调查与分析大赛省级一等奖。'
  ),
  algorithm: example(
    'algorithm',
    { name: '孙同学', targetRole: '算法工程师（NLP）', phone: '188****2054', email: 'sun.nlp@example.com', location: '南京', website: 'github.com/example-nlp' },
    '自然语言处理方向硕士，具备文本分类、信息抽取与大模型应用经验。熟悉 PyTorch 和 Transformers，能够完成数据处理、训练评估、误差分析与推理服务部署。',
    [{ school: '东南大学', major: '计算机科学与技术', degree: '硕士', period: '2024.09 - 2027.06', details: '研究方向：自然语言处理；以学生一作投稿中文核心会议论文 1 篇。' }],
    [{ name: '面向客服对话的意图识别', role: '算法负责人', period: '2025.03 - 2025.08', tech: 'PyTorch / Transformers / BERT / FastAPI', description: '• 清洗并标注 1.8 万条多轮对话，建立标签体系与数据质量检查脚本。\n• 通过类别重采样、对抗训练和阈值调优，将 Macro-F1 从 0.84 提升至 0.90。\n• 使用 ONNX Runtime 部署推理接口，单条平均延迟降低约 31%。' }],
    [{ company: '某人工智能研究院', role: 'NLP 算法实习生', period: '2025.09 - 2025.12', location: '南京', description: '• 参与实体抽取模型迭代，完成数据分析、训练和错误样本归因。\n• 编写批量评测工具并沉淀实验记录模板，提高模型版本对比效率。' }],
    '语言与框架：Python、PyTorch、Transformers、scikit-learn。\n方向：文本分类、序列标注、Embedding、RAG。\n工程：FastAPI、Docker、Linux、Git；了解 ONNX Runtime。',
    '研究生国家奖学金；CCF BDCI 竞赛区域赛二等奖。'
  ),
  devops: example(
    'devops',
    { name: '何同学', targetRole: '运维开发工程师', phone: '136****7493', email: 'he.ops@example.com', location: '成都', website: 'github.com/example-ops' },
    '计算机专业本科，具备 Linux、容器化和自动化运维项目经验。能够编写脚本和服务提升发布、监控与故障处理效率，理解常见网络与系统问题。',
    [{ school: '电子科技大学', major: '计算机科学与技术', degree: '本科', period: '2022.09 - 2026.06', details: '主修操作系统、计算机网络、云计算；专业前 20%。' }],
    [{ name: '轻量级服务发布与监控平台', role: '独立开发', period: '2025.07 - 2025.10', tech: 'Go / Docker / Prometheus / Grafana / Nginx', description: '• 实现服务构建、容器发布、健康检查与版本回滚，支持 10 余个实验室服务。\n• 接入 Prometheus 指标与告警规则，覆盖 CPU、内存、接口错误率和证书到期。\n• 将人工发布步骤由 9 步减少至 3 步，平均发布时间缩短约 60%。' }],
    [{ company: '某云计算技术团队', role: '运维开发实习生', period: '2025.11 - 2026.02', location: '成都', description: '• 维护测试环境和 CI 流水线，处理磁盘、网络与容器异常。\n• 编写巡检脚本并完善值班手册，累计关闭 30 余项环境问题。' }],
    '系统：Linux、Shell、计算机网络、常见故障排查。\n云原生：Docker、Kubernetes 基础、Nginx。\n监控与开发：Prometheus、Grafana、Go、Python、GitLab CI。',
    '华为 ICT 大赛省级二等奖；校级优秀学生干部。'
  ),
  product: example(
    'product',
    { name: '许同学', targetRole: '产品经理', phone: '187****6175', email: 'xu.pm@example.com', location: '上海', website: 'portfolio.example.com/xu' },
    '具备用户研究、需求分析、原型设计和数据复盘经历，能够将业务目标拆解为可验证的产品方案。曾负责校园产品从调研到上线的完整过程，重视协作与结果量化。',
    [{ school: '上海大学', major: '信息管理与信息系统', degree: '本科', period: '2022.09 - 2026.06', details: '专业前 15%；主修产品设计、数据分析、管理信息系统。' }],
    [{ name: '校园活动发现小程序', role: '产品负责人', period: '2025.03 - 2025.07', tech: 'Axure / Figma / SQL / 墨刀', description: '• 访谈 26 名学生并回收 312 份问卷，定位活动信息分散和报名提醒缺失两项核心问题。\n• 输出需求文档、交互原型和埋点方案，协调 4 名开发成员完成两个版本上线。\n• 上线 6 周累计获得 1800 余名用户，活动报名转化率由 21% 提升至 34%。' }],
    [{ company: '某本地生活平台', role: '产品实习生', period: '2025.09 - 2025.12', location: '上海', description: '• 协助商家后台需求迭代，完成竞品分析、需求说明和验收。\n• 根据客服工单归纳高频问题，推动表单校验与提示优化，相关咨询量下降约 18%。' }],
    '产品：需求分析、用户访谈、流程设计、PRD、原型与项目推进。\n数据：SQL 基础、Excel、漏斗与留存分析。\n工具：Figma、Axure、墨刀、XMind、Jira。',
    '全国大学生电子商务创新创意创业挑战赛省级二等奖。'
  )
}

export const builtInResumeTemplates: TemplateInfo[] = [
  { id: -101, name: 'AI Agent 校招完整案例', category: 'AI Agent', exampleKey: 'agent', builtIn: true, description: '硕士在读案例，包含 LangGraph、工具调用、RAG 与算法实习的完整写法。', createTime: '2026-07-08' },
  { id: -102, name: 'Vue 前端校招完整案例', category: '前端开发', exampleKey: 'frontend', builtIn: true, description: '本科校招案例，突出组件复用、工程优化、响应式适配与前端实习。', createTime: '2026-07-07' },
  { id: -103, name: '测试开发校招完整案例', category: '测试开发', exampleKey: 'qa', builtIn: true, description: '覆盖接口自动化、性能测试、CI 和电商测试实习的完整案例。', createTime: '2026-07-06' },
  { id: -104, name: '数据分析校招完整案例', category: '数据分析', exampleKey: 'data', builtIn: true, description: '以留存分析和经营数据实习为主线，展示指标与量化结果的写法。', createTime: '2026-07-05' },
  { id: -105, name: 'NLP 算法硕士完整案例', category: '算法岗', exampleKey: 'algorithm', builtIn: true, description: '包含论文、模型训练、误差分析、推理部署和算法实习经历。', createTime: '2026-07-04' },
  { id: -106, name: '运维开发校招完整案例', category: '运维开发', exampleKey: 'devops', builtIn: true, description: '围绕容器发布、监控告警、自动化脚本和云计算实习组织内容。', createTime: '2026-07-03' },
  { id: -107, name: 'Java 后端硕士完整案例', category: 'Java 后端', exampleKey: 'java', builtIn: true, description: 'Java 与 AI 项目结合的完整样例，突出缓存、消息队列、数据库和工程协作。', createTime: '2026-07-02' },
  { id: -108, name: '产品经理校招完整案例', category: '产品经理', exampleKey: 'product', builtIn: true, description: '从用户调研、需求设计到上线复盘的完整产品经理案例。', createTime: '2026-07-01' }
]

const resolveExampleKey = (template: TemplateInfo) => {
  if (template.exampleKey && resumeExamples[template.exampleKey]) return template.exampleKey
  const text = `${template.name} ${template.category || ''}`
  if (/Agent|大模型|人工智能/i.test(text)) return 'agent'
  if (/前端|Vue|React/i.test(text)) return 'frontend'
  if (/测试|QA/i.test(text)) return 'qa'
  if (/数据分析|数据运营/i.test(text)) return 'data'
  if (/算法|NLP|机器学习/i.test(text)) return 'algorithm'
  if (/运维|DevOps|云计算/i.test(text)) return 'devops'
  if (/PM|产品/i.test(text)) return 'product'
  return 'java'
}

export const getResumeExample = (template: TemplateInfo): ResumeContent => {
  const source = resumeExamples[resolveExampleKey(template)]
  return JSON.parse(JSON.stringify(source)) as ResumeContent
}
