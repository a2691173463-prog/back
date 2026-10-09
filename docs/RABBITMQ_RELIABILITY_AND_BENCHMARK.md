# 简历解析 RabbitMQ 可靠性与并发优化报告

## 1. 改造目标

简历解析包含 PDF 读取、图片渲染和大模型调用，单次处理时间较长。原实现只有
一个消费者，消息体仅包含 `resumeId`，并且缺少发布确认、延迟重试、死信队列
和数据库与消息的一致性保障。

本次改造目标：

1. 提升多个简历任务同时到达时的处理能力。
2. 避免 RabbitMQ 或网络短暂异常造成任务丢失。
3. 对临时故障进行有限次数的延迟重试。
4. 将永久失败任务送入死信队列，便于排查和人工补偿。
5. 保证简历记录成功写入 MySQL 后，解析任务最终一定有机会发布。
6. 建立可重复执行的本地并发与故障测试。

## 2. 改造前流程

```text
上传简历
  -> MySQL 保存 resume(status=0)
  -> 直接发送 Long resumeId
  -> resume.parse.queue
  -> 单消费者
  -> PDF 解析 + 调用 Python AI
  -> MySQL 更新结果
```

主要风险：

- MySQL 保存成功、RabbitMQ 发送失败时，会留下永远不再处理的待解析记录。
- 生产者不知道消息是否被 Broker 接收、是否成功路由到队列。
- 消费异常被捕获后直接返回，相当于正常 ACK，失败消息无法恢复。
- 单消费者使耗时的大模型请求完全串行。
- 默认 `prefetch=250` 会让一个消费者提前占有大量消息，不利于任务公平分配。
- Java 序列化消息跨语言、可读性和安全性较差，不便于后续 Python 消费。

## 3. 改造后流程

```text
上传简历事务
  -> 同一事务写 resume + resume_parse_outbox
  -> 事务提交后立即尝试发布
  -> 发布失败由 Outbox 定时任务继续补偿
  -> resume.exchange
  -> resume.parse.queue
  -> 3~6 个消费者，prefetch=1
  -> 原子抢占 status: 0 -> 3
  -> PDF 解析 + Python AI
     -> 成功：status=1，ACK
     -> 临时失败：2s/5s 延迟重试队列，ACK 原消息
     -> 永久失败或重试耗尽：status=2，Reject
  -> resume.dlx
  -> resume.parse.dlq
```

### 3.1 并发与背压

- 初始消费者数：`3`
- 最大消费者数：`6`
- 每个消费者预取：`1`
- 消费确认：Spring AMQP `AUTO`
- 拒绝消息默认不重新入原队列，避免无限热循环

一个消费者处理一份简历时，其他消费者可以继续处理其他用户的任务。
`prefetch=1` 使 RabbitMQ 只把下一条任务交给当前空闲的消费者。

### 3.2 生产者可靠性

- 消息使用 JSON，包含 `messageId`、`resumeId`、`attempt`、`createdAt`。
- 消息标记为持久化。
- 开启 correlated publisher confirm。
- 开启 mandatory 和 publisher returns，识别无法路由的消息。
- 发布失败最多尝试 3 次，并使用短退避。

Publisher confirm 表示 Broker 已接收消息；return 表示交换机找不到匹配队列。
两者解决的问题不同，必须同时使用。

### 3.3 Transactional Outbox

上传接口在同一个 MySQL 事务内写入：

- `resume`
- `resume_parse_outbox`

事务提交后立即尝试发布。若 RabbitMQ 暂时不可用，Outbox 仍保留为待发送状态，
定时任务会重新发布。发布成功后状态更新为已发送。

这避免了数据库提交成功、消息发送失败形成的数据与消息不一致。它提供的是
“至少一次”投递，因此消费者仍需要幂等处理。

### 3.4 消费幂等

消费者通过条件更新原子抢占任务：

```sql
UPDATE resume
SET status = 3
WHERE id = ? AND status = 0;
```

只有一个消费者能够把状态从待处理改为处理中。已成功或已失败的任务再次投递时
会被直接跳过，避免重复调用模型和重复覆盖诊断结果。

### 3.5 延迟重试与死信

- 第一次临时失败：进入 2 秒 TTL 重试队列。
- 第二次临时失败：进入 5 秒 TTL 重试队列。
- 第三次仍失败：标记失败并 Reject，进入 DLQ。
- 文件不存在、PDF 无法读取等永久错误：不调用模型，直接进入 DLQ。

重试消息仍保留同一个 `messageId`，并递增 `attempt`，方便日志追踪完整生命周期。

## 4. 并发测试

### 4.1 测试方法

- 测试对象：RabbitMQ 到 MySQL 最终状态的完整消费链路。
- 测试任务：12 份临时简历记录。
- AI 依赖：本地确定性 Mock 服务，每次固定延迟 750ms。
- 前后使用同一台机器、同一份 PDF、同一个 RabbitMQ 和 MySQL。
- 测试数据名称以 `__mq_benchmark__` 开头，测试完成后自动清理。
- 不调用真实大模型，不消耗 API 额度。

结果文件：

- `back/benchmarks/results/before.json`
- `back/benchmarks/results/after.json`

### 4.2 测试结果

| 指标 | 改造前 | 改造后 | 变化 |
| --- | ---: | ---: | ---: |
| 任务数 | 12 | 12 | 相同 |
| 消费者数 | 1 | 3 | 3 倍 |
| Prefetch | 250 | 每消费者 1 | 公平分配 |
| 总耗时 | 14.158s | 6.009s | 降低 57.6% |
| 吞吐量 | 0.848 task/s | 1.997 task/s | 提升 135.5% |
| AI 最大并发 | 1 | 3 | 3 倍 |
| 成功任务 | 12 | 12 | 100% |

吞吐提升没有达到理论 3 倍，是因为 PDF 解析、图片渲染、应用启动后的资源竞争、
数据库更新和 RabbitMQ 调度也占用时间。该结果仍证明耗时 AI 调用已经从串行改为
3 路并行。

这组数据是本地 Mock 基准，不应直接当作真实模型生产吞吐。真实上限还受到模型
API 限流、网络延迟、机器 CPU/内存和 PDF 大小影响。

## 5. 故障测试

### 5.1 临时模型故障

Mock AI 前两次固定返回失败，第三次恢复。

| 指标 | 结果 |
| --- | ---: |
| 最终成功 | 1 |
| AI 请求次数 | 3 |
| 总耗时 | 9.407s |
| 预期延迟 | 2s + 5s |

结论：两级延迟重试生效，任务没有因临时故障丢失，也没有立即连续重试压垮下游。

### 5.2 永久文件故障

发送一条文件路径不存在的任务。

| 指标 | 结果 |
| --- | ---: |
| 最终失败 | 1 |
| AI 请求次数 | 0 |
| DLQ 新增消息 | 1 |

结论：永久错误不会浪费模型调用，消息被拒绝后能够进入死信队列。

### 5.3 Outbox 集成

直接插入 3 条待发送 Outbox，由定时任务发布。

| 指标 | 结果 |
| --- | ---: |
| 简历解析成功 | 3/3 |
| Outbox 已发送 | 3/3 |
| AI 最大并发 | 3 |

结论：数据库记录、Outbox 补偿发布、RabbitMQ 消费和最终状态更新链路完整。

## 6. 部署与运行

首次应用改造时执行：

```powershell
mysql -u root -p interview_db < .\back\sql\resume_mq_reliability.sql
```

环境变量放在 `back/.env`：

```properties
DB_USERNAME=root
DB_PASSWORD=your-password
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
```

如果 RabbitMQ 中已经存在没有 DLX 参数的旧 `resume.parse.queue`，需先确认队列
没有待处理消息，再删除旧队列并重启后端，让 Spring 按新参数重新声明。不要在
有 Ready 或 Unacked 消息时删除队列。

建议监控：

- `resume.parse.queue` 的 Ready、Unacked 和消费者数
- `resume.parse.dlq` 的新增数量
- `resume_parse_outbox` 中长期处于 pending/failed 的记录
- 解析成功率、P95/P99 耗时、重试率
- Python AI 服务的并发数、超时率和限流响应

## 7. 面试表达

可以这样概括：

> 简历解析包含 PDF 渲染和大模型调用，原来单消费者串行处理，而且数据库写入与
> 消息发送之间存在一致性窗口。我将消息改为可追踪的 JSON 任务，引入 Publisher
> Confirm、Return、持久化消息、Transactional Outbox、消费者幂等、两级延迟重试
> 和死信队列；同时把消费者从 1 调整为 3，设置 prefetch=1。使用固定 750ms 的
> Mock AI 做 12 任务端到端测试后，总耗时从 14.158 秒降到 6.009 秒，吞吐提升
> 135.5%，并验证了模型短暂失败可恢复、永久失败会进入 DLQ。

需要主动说明：并发不是越高越好。生产环境应结合 Python 服务处理能力和模型 API
限流逐步调节消费者数量，否则只是把 RabbitMQ 中的排队转移成 Python 或模型端的
拥塞。
