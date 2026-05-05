# Agent Server 项目面试与架构整理

> 本文档整理自结合 `agent-server` 项目代码的多轮架构讲解与面试问答，涵盖：多 Agent 协作设计、主 Agent 编排、A2A 协议通信、SOP 检索、记忆机制等核心模块。

---

## 目录

- [一、接单异常多 Agent 协作流程设计](#一接单异常多-agent-协作流程设计)
- [二、核心模块在项目中的代码落地](#二核心模块在项目中的代码落地)
- [三、项目记忆机制详解](#三项目记忆机制详解)

---

## 一、接单异常多 Agent 协作流程设计

> 场景：**交易单（Trade Order）→ 履约单（Fulfillment Order）** 的接单环节出现异常，需要自动化诊断 → 定位根因 → 给出处置方案 → 触达用户/客服。

### 1.1 场景对齐

- **交易单（Trade Order）**：用户下单支付后产生，归属交易域，记录"买什么、付了多少"
- **履约单（Fulfillment Order）**：交易单支付成功后，由履约系统"接单"生成的执行单，记录"怎么发货、谁来送、什么时候到"

**接单异常常见 case**：

| 异常类型 | 典型现象 |
|---------|---------|
| 接单超时 | 交易单已支付 N 分钟，履约单还没生成 |
| 接单失败 | MQ 消息丢失、履约系统宕机、幂等键冲突 |
| 数据不一致 | 交易单状态=已支付，履约单状态=未创建（脑裂） |
| 库存/资源校验失败 | 接单时发现库存不足、仓库无货、运力不足 |
| 路由失败 | 找不到合适的仓/骑手/快递公司 |
| 重复接单 | 同一交易单生成了多条履约单 |
| 逆向冲突 | 用户已申请退款，但履约单还在接 |

**特点**：链路长、跨多个域（交易/履约/库存/物流/营销/资金）、根因分散，非常适合多 Agent 自动化。

### 1.2 多 Agent 分工设计

按业务域（DDD 限界上下文）切分，**一个域 = 一个子 Agent**：

```
                    ┌─────────────────────┐
                    │   Router Agent      │  ← 主 Agent（项目里的 ReActAgent）
                    │  （意图识别+编排）   │
                    └──────────┬──────────┘
                               │
        ┌──────────┬──────────┼──────────┬──────────┬──────────┐
        ▼          ▼          ▼          ▼          ▼          ▼
   ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
   │ Trade   │ │Fulfill- │ │Inventory│ │Logistic │ │ MQ/Msg  │ │ Action  │
   │ Agent   │ │ment Ag. │ │ Agent   │ │ Agent   │ │ Agent   │ │ Agent   │
   └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘
   交易域查询  履约域查询  库存域查询   物流/运力   消息链路    处置执行
                               │
                    ┌──────────┴──────────┐
                    │  Knowledge Agent    │  ← 横切：所有 Agent 共用
                    │（SOP 知识库检索）    │     复用 RagService + L.O.C.A.L MCP
                    └─────────────────────┘
```

**子 Agent 职责矩阵**：

| 子 Agent | 持有的工具 | 典型动作 |
|---------|-----------|---------|
| **Trade Agent** | 交易域 API、支付流水查询 | 查交易单状态、支付时间、是否退款中 |
| **Fulfillment Agent** | 履约域 API、履约单 DB | 查履约单是否生成、状态机当前节点、卡在哪 |
| **Inventory Agent** | 库存中心 API | 查商品/SKU 的实时库存、占用情况、仓库分布 |
| **Logistic Agent** | 运力调度、骑手/快递 API | 查运力是否可达、配送范围、运单状态 |
| **MQ/Msg Agent** | MQ console、链路追踪 | 查接单消息是否发出/消费、TraceId 全链路 |
| **Action Agent** | 写操作工具 | 重投消息、手工接单、关单退款、通知客服 |
| **Knowledge Agent** | RAG 检索 | 查"XX 异常码 SOP""历史相似 case 处理方案" |

> **关键设计**：**读 Agent 和写 Agent 严格分开**。Action Agent 是唯一有写权限的，所有变更动作都收口到它，便于审计和权限管控。

### 1.3 Router Agent 如何「根据上下文动态分配任务」

#### 决策依据 = 4 类上下文

| 上下文 | 项目里的来源 | 路由怎么用 |
|-------|------------|-----------|
| **订单基础信息** | 用户输入 + Trade Agent 第一步查询 | 订单类型决定走哪条履约链路 |
| **异常信号特征** | 异常码、错误日志关键词、卡单时长 | 不同特征映射不同子 Agent 优先级 |
| **历史会话** | `Session` 短期记忆 | 已经查过的 Agent 不重复调，避免循环 |
| **SOP 知识** | `RagService` 检索结果 | SOP 直接告诉路由 Agent 调度顺序 |

#### 三段式路由策略

```
用户："订单 12345 支付了 10 分钟还没发货"
  │
  ▼
┌──────────────────────────────────────────────┐
│ Stage 1: 规则路由（确定性场景）               │
│  - 命中"订单号 \d+" → 必调 Trade Agent       │
│  - 命中"未发货/未接单" → 必调 Fulfillment    │
└──────────────────────────────────────────────┘
  │
  ▼ Trade Agent + Fulfillment Agent 并行返回
┌──────────────────────────────────────────────┐
│ Stage 2: SOP 路由（基于知识库）               │
│  根据返回的异常码，RAG 检索对应 SOP：        │
│  "ERR_FULFILL_TIMEOUT 接单超时" SOP 说：     │
│   1. 先查 MQ 消息是否消费                    │
│   2. 再查库存是否充足                        │
│   3. 最后查运力                              │
│  → 路由 Agent 按 SOP 顺序调用子 Agent        │
└──────────────────────────────────────────────┘
  │
  ▼ 走完 SOP 仍未定位
┌──────────────────────────────────────────────┐
│ Stage 3: LLM 兜底路由（开放式推理）           │
│  把所有已收集信息 + 子 Agent skills 给 LLM   │
│  让 LLM 自主决定下一步调用哪个 Agent         │
└──────────────────────────────────────────────┘
  │
  ▼ 根因明确
┌──────────────────────────────────────────────┐
│ Action Agent 执行处置                         │
│  - 重投 MQ / 手工接单 / 退款 / 通知客服      │
└──────────────────────────────────────────────┘
```

#### 完整执行示例

```
用户："订单 12345 支付了 10 分钟还没发货"

【Step 1】规则路由 → 并行调用
  ├─ Trade Agent: 订单已支付，付款时间 10:30，状态正常
  └─ Fulfillment Agent: 履约单未生成 ❌

【Step 2】Router Agent 通过 RAG 检索 SOP
  → RagService.search("履约单未生成 已支付")
  → 返回 SOP："优先排查 MQ → 库存 → 路由"

【Step 3】按 SOP 串行调用
  ├─ MQ Agent: 接单消息已发送，但消费失败，重试 3 次后进死信队列
  → 根因定位：MQ 消费失败

【Step 4】RAG 再次检索处置方案
  → "死信消息处理 SOP"：核对幂等键 → 重投 → 监控

【Step 5】Action Agent 执行
  → 重投死信消息
  → 监听 30s，确认履约单已生成
  → 通知用户："已为您重新派单，预计 X 分钟内发货"
```

整个过程 Router Agent **没有任何硬编码 if-else**，所有决策都来自 SOP 知识库 + 子 Agent 实时上下文 + LLM 推理。

### 1.4 编排模式

| 模式 | 适用场景 | 项目实现 |
|-----|---------|---------|
| **串行（Sequential）** | 有依赖关系，A 的输出是 B 的输入 | 主 Agent 在一轮 ReAct 里依次 tool_call |
| **并行（Parallel）** | 无依赖，可同时查 | `AgentHub` 异步调用 + `EventCenter` 聚合 |
| **DAG（Plan-Execute）** | 复杂任务，先规划再执行 | `Task` 持久化整张 DAG，分布式执行 |

接单异常通常是 **并行 + 聚合**。

### 1.5 工程加分项（履约场景特有）

- **幂等性**：履约场景重投消息、重接单是高频操作，每个 Action 必须带幂等键（订单号 + 操作类型 + 时间窗）
- **状态机校验**：Fulfillment Agent 查到的状态必须经过状态机校验后才能让 Action Agent 操作
- **逆向冲突检测**：调用 Action Agent 前必须再查一次 Trade Agent 确认订单没在退款中
- **链路追踪**：所有子 Agent 调用带同一个 TraceId（来自 Session）
- **降级策略**：MQ Agent 超时不阻塞主流程，标记为"未确认"继续往下走
- **AB / 灰度**：Action Agent 的写操作建议走灰度发布，先 1% 流量自动处置
- **审计追溯**：项目里的 `Event` 抽象天然适合做审计

### 1.6 面试回答模板

> "这个场景核心难点是**链路长 + 跨域多 + 根因分散**。我会按业务域拆 1 个 Router Agent + 6 个子 Agent：交易、履约、库存、物流、消息链路、处置执行，再加一个横切的 Knowledge Agent 做 SOP 检索。
>
> 路由 Agent 的动态分配靠**三段式策略**：规则路由处理确定性入口，**SOP 路由**根据异常码从知识库拉到 SOP 后按 SOP 顺序调度子 Agent，LLM 兜底处理 SOP 未覆盖的开放场景。
>
> 上下文来源是 4 块：订单基础信息、异常信号特征、Session 历史、RAG 知识库。子 Agent 之间通过 A2A 协议异步通信，结果走事件总线聚合到主 Agent。
>
> 工程上履约场景特别要关注：**幂等键、状态机校验、逆向冲突检测、TraceId 全链路、写操作灰度、Event 审计追溯**。"

---

## 二、核心模块在项目中的代码落地

### 2.1 主 Agent 编排：`ReActAgent` + `Task`

#### 核心文件

| 文件 | 职责 |
|------|------|
| `src/main/java/com/agent/core/agent/react/ReActAgent.java` | 主 Agent 实现，跑完整的 ReAct 五阶段流水线 |
| `src/main/java/com/agent/core/agent/react/IntentPlanner.java` | 意图识别 + 生成 plan |
| `src/main/java/com/agent/core/agent/react/TaskExecutor.java` | 按 plan 执行每个 step |
| `src/main/java/com/agent/model/task/Task.java` | Task 模型（含 `TaskStep` 内部类） |
| `src/main/java/com/agent/core/task/TaskManagerImpl.java` | Task 持久化（内存缓存 + MySQL） |
| `src/main/java/com/agent/persist/entity/TaskEntity.java` + `TaskMapper.java` | DB 实体 + MyBatis 映射 |
| `src/main/resources/schema.sql` | `t_task` 表 DDL |

#### 关键调用链

```
ReActAgent.processInput(Event)            ← 入口
  ├─ PerceptionArea.perceive()            ← 感知归一化
  ├─ CognitionArea.cognize()              ← 内部调 IntentPlanner
  │    └─ IntentPlanner.execute()
  │         ├─ recognizeIntent()          ← LLM 识别意图
  │         └─ generatePlan()             ← 生成 Task + List<TaskStep>
  ├─ MotorArea.execute()                  ← 复杂任务多轮执行
  │    └─ TaskExecutor 按 step 调度
  ├─ SelfEvaluationArea.evaluate()        ← 自评，最多重试 2 次
  └─ ExpressionArea.express()
       └─ eventCenter.publishOutput(...)  ← 输出回前端
```

#### Task 持久化（双层存储）

```java
// src/main/java/com/agent/core/task/TaskManagerImpl.java
private final Map<String, Task> taskCache = new ConcurrentHashMap<>();  // 内存缓存
private final TaskMapper taskMapper;                                     // MySQL

@Override
public void createTask(Task task) {
    taskCache.put(task.getTaskId(), task);
    taskMapper.insert(toEntity(task));   // ← 落库
}
```

#### Task 模型结构

```java
public class Task {
    private String taskId;
    private String sessionId;       // ← 一个 Session 对应多个 Task
    private TaskStatus status;       // PENDING/RUNNING/COMPLETED/FAILED/CANCELLED
    private String intent;           // 识别出的意图
    private List<TaskStep> steps;    // ← plan 的每一步

    public static class TaskStep {
        private int index;
        private String type;         // MCP_CALL / LLM_CALL / SUB_AGENT_CALL
        private String target;       // 要调用的工具/Agent 名
        private Map<String, Object> input;
        private TaskStatus status;
        private Object result;
    }
}
```

> ⚠️ `IntentPlanner.recognizeIntent()` 当前是 TODO 占位（默认返回 "CHAT"），生产里需要接 LLM。

### 2.2 子 Agent 通信：A2A 协议 + `EventCenter`

#### 核心文件

| 文件 | 职责 |
|------|------|
| `src/main/java/com/agent/core/multiagent/AgentHub.java` | 子 Agent 注册中心 + 调度入口 |
| `src/main/java/com/agent/core/multiagent/SubAgentProxy.java` | 子 Agent 代理（封装 AgentCard + A2AClient） |
| `src/main/java/com/agent/core/multiagent/a2a/AgentCard.java` | A2A Agent 名片（能力描述） |
| `src/main/java/com/agent/core/multiagent/a2a/A2AMessage.java` | A2A 消息协议体 |
| `src/main/java/com/agent/core/multiagent/a2a/A2AClient.java` | A2A 协议客户端（HTTP + SSE） |
| `src/main/java/com/agent/core/event/EventCenter.java` | 事件总线接口 |
| `src/main/java/com/agent/core/event/RedisEventCenter.java` | Redis Pub/Sub 实现 |

#### A2A 协议两种调用方式

```java
// src/main/java/com/agent/core/multiagent/a2a/A2AClient.java

// 同步调用：POST /tasks/send
public A2AMessage sendTask(String input, Map<String, Object> metadata) { ... }

// 流式调用：POST /tasks/sendSubscribe，SSE 推送
public CompletableFuture<Void> sendTaskStreaming(
    String input,
    Map<String, Object> metadata,
    Consumer<A2AMessage> chunkConsumer
) { ... }

// Agent 发现：GET /.well-known/agent.json
public static AgentCard discoverAgentCard(String baseUrl) { ... }
```

子 Agent 必须暴露 3 个 HTTP 端点：`/tasks/send`、`/tasks/sendSubscribe`、`/.well-known/agent.json`。

#### EventCenter 的「回流」机制

```java
// src/main/java/com/agent/core/event/RedisEventCenter.java

private static final String INPUT_TOPIC_PREFIX  = "agent:event:input:";
private static final String OUTPUT_TOPIC_PREFIX = "agent:event:output:";

// 子 Agent 异步执行完，把结果当作 input 事件发给主 Agent
publishInput(sessionId, event);
  → Redis 发到 topic = "agent:event:input:" + sessionId
  → 主 Agent 所在 Pod 通过 subscribeInput 收到
  → ReActAgent.onInputEvent(event) 处理
```

**为什么用 Redis Pub/Sub**：Agent 实例和 WebSocket 连接可能不在同一台机器（分布式场景），通过 Redis 做事件广播，保证跨 Pod 通信。

### 2.3 SOP 检索：`RagService` + `McpKnowledgeService`

#### 核心文件

| 文件 | 职责 |
|------|------|
| `src/main/java/com/agent/core/rag/RagService.java` | RAG 主流程编排 |
| `src/main/java/com/agent/core/rag/McpKnowledgeService.java` | 调 L.O.C.A.L MCP 的 `searchDocChunk` |
| `src/main/java/com/agent/core/rag/HybridRetrievalService.java` | PGVector 本地检索（备选模式） |
| `src/main/java/com/agent/core/rag/QueryRewriteService.java` | LLM 改写 query 成多个子问题 |
| `src/main/java/com/agent/core/rag/RerankService.java` | LLM rerank |
| `src/main/java/com/agent/core/rag/tool/KnowledgeSearchTool.java` | 把 RAG 包装成 Agent 工具 |
| `src/main/java/com/agent/core/rag/tool/RagToolRegistrar.java` | 把 KnowledgeSearchTool 注册给 Agent |

#### 主 Agent 调 RAG 的两条路径

**路径 A：作为「工具」给 Agent 自主调用**（推荐）

```
RagToolRegistrar 启动时把 KnowledgeSearchTool 注册到工具列表
  → Agent 的 system prompt 里就会出现这个工具
  → LLM 决定调用 → MotorArea 执行 tool_call → 拿到检索结果
```

**路径 B：业务代码直接同步调用**

```java
@Autowired
private McpKnowledgeService mcpKnowledgeService;

List<McpDocChunk> sopChunks = mcpKnowledgeService.searchDocChunks(
    "履约单接单超时 异常码 ERR_FULFILL_TIMEOUT 处理SOP", 20
);
```

#### `RagService.query()` 完整流水线

```java
public RagResult query(String query, String tenantId) {
    // Step 1: Query Rewrite
    List<String> subQueries = queryRewriteService.rewriteQuery(query);

    // Step 2-4: 检索（两种模式二选一）
    if (isMcpMode()) {
        // ★ MCP 模式：调 L.O.C.A.L MCP，向量化+召回+rerank 全在 MCP 侧
        List<McpDocChunk> mcpChunks = mcpKnowledgeService.searchWithMultipleQueries(
            subQueries, ragProperties.getMcp().getTopK());
        knowledgeContext = assembleMcpContext(mcpChunks);
    } else {
        // PGVector 模式：本地混合检索 + LLM rerank
        List<Document> retrieved = hybridRetrievalService.retrieve(subQueries, tenantId);
        List<Document> reranked = rerankService.rerank(query, retrieved);
        knowledgeContext = assembleContext(reranked);
    }

    // Step 5-6: Prompt 拼接 + LLM 总结
    return generateAnswer(query, knowledgeContext);
}
```

模式切换开关：`application.yml` 里的 `rag.mode = mcp` 或 `pgvector`。

### 2.4 接单异常排查的完整代码流转

```
用户消息 "订单 12345 接单异常"
  │
  ▼
AgentWebSocketHandler                                    ← protocol/ws/
  → eventCenter.publishInput(sessionId, USER_MESSAGE)    ← RedisEventCenter
  │
  ▼ Redis Pub/Sub topic: agent:event:input:{sessionId}
  │
  ▼
ReActAgent.onInputEvent(event)                           ← core/agent/react/
  → processInput(event)
       │
       ├─ CognitionArea + IntentPlanner
       │   → intent="OPERATE"，生成 Task：
       │     [step1: SUB_AGENT_CALL → trade-agent]
       │     [step2: SUB_AGENT_CALL → fulfillment-agent]
       │     [step3: LLM_CALL → 检索 SOP]
       │     [step4: SUB_AGENT_CALL → action-agent]
       │
       ├─ TaskManagerImpl.createTask(task)               ← 持久化到 t_task
       │
       └─ MotorArea.execute()
            ├─ step1+2 并行：
            │   AgentHub.getSubAgent("trade-agent")
            │     → SubAgentProxy.client.sendTask(...)   ← A2AClient HTTP
            │     → 子 Agent 执行完，回写 EventCenter
            │
            ├─ step3 调 RAG：
            │   KnowledgeSearchTool.search(...)
            │     → RagService.query("接单超时 SOP")
            │       → McpKnowledgeService.searchDocChunks(...)  ← L.O.C.A.L MCP
            │
            └─ step4 调 action-agent：
                A2AClient.sendTask(...)                  ← 写操作（重投/退款）
       │
       └─ ExpressionArea.express()
            → eventCenter.publishOutput(...)
```

---

## 三、项目记忆机制详解

### 3.1 整体架构：两套机制并存

```
┌─────────────────────────────────────────────────────────────┐
│                     Agent 记忆系统                           │
├─────────────────────────────┬───────────────────────────────┤
│   1. 三层记忆体系（认知层）  │  2. Segment 思维链（执行层）  │
│   MemoryManager             │  SegmentContext               │
│   ├─ Sensory（感知记忆）    │  ├─ currentSegments（当前轮） │
│   ├─ ShortTerm（短期记忆）  │  ├─ historySegments（历史轮） │
│   └─ LongTerm（长期记忆）   │  └─ Segment（原子单位）        │
└─────────────────────────────┴───────────────────────────────┘
         ↓ 跨层流动                    ↓ 时间线追溯
   promote 操作                  archive 当前轮
```

| 机制 | 关注点 | 类比 |
|------|-------|------|
| **三层记忆**（MemoryManager） | "记什么、能保存多久" | 大脑的记忆分层 |
| **Segment 思维链**（SegmentContext） | "想了什么、按什么顺序" | 思维过程的录像带 |

### 3.2 三层记忆体系（Atkinson-Shiffrin 模型）

代码位置：`src/main/java/com/agent/core/agent/memory/`

```java
// MemoryType.java
public enum MemoryType {
    SENSORY,      // 感知记忆：最易失，捕捉环境瞬时状态
    SHORT_TERM,   // 短期记忆：会话级别，容量有限
    LONG_TERM     // 长期记忆：持久化，知识/经验/偏好/会话摘要
}
```

#### 第 1 层：`SensoryMemory`（感知记忆）

**特点**：全局唯一、最易失、随时被覆盖

**存什么**：
- `currentPageUrl` — 当前页面 URL
- `currentPageTitle` — 当前页面标题
- `pageContext` — 页面上下文
- `userActions` — 最近的用户操作
- `capturedAt` — 捕获时间

**典型场景**：用户在浏览器里切换页面、点了某个按钮，这些"瞬时环境信号"先进 Sensory，**只要不被提升就会被下次新的感知覆盖**。

#### 第 2 层：`ShortTermMemory`（短期/工作记忆）

**特点**：**按 Session 隔离**、容量有限、伴随会话生命周期

**存什么**：
- `segmentMemories` — 本会话的 Segment 记忆消息（用户输入、思考、工具调用等）
- `entityMemories` — 本会话识别出的实体（订单号、用户ID、商品名等）

```java
// MemoryManager.java
private final ConcurrentHashMap<String, ShortTermMemory> shortTermPool;

public ShortTermMemory getShortTermMemory(String sessionId) {
    return shortTermPool.computeIfAbsent(sessionId, sid ->
            ShortTermMemory.builder().sessionId(sid).build());
}
```

**典型场景**：一次会话里"上下文连续"，比如用户说"那个订单 12345 怎么样了？"过了几轮又问"它现在状态呢？"，靠的就是 ShortTerm 里存的实体和 segment。

#### 第 3 层：`LongTermMemory`（长期记忆）

**特点**：全局共享、持久化、跨 Session 复用

**存什么**（4 个子类目）：

```java
private List<KnowledgePoint> knowledgePoints;          // 知识点
private List<ExecutionExperience> executionExperiences; // 执行经验（成败案例）
private Map<String, UserPreference> userPreferences;    // 用户偏好
private List<HistoryChatSummary> chatSummaries;         // 历史会话摘要
```

**典型场景**：
- 知识点：通过 RAG 学到的某个 SOP，写入长期记忆备用
- 执行经验：上次"重投 MQ"成功了，记下来作为下次决策依据
- 用户偏好：用户偏好简短回答 / 喜欢中文输出
- 历史会话摘要：上一个 session 的总结，新 session 启动时可以"想起来"

### 3.3 跨层晋升（Promotion）

```
SensoryMemory ──promoteToShortTerm()──> ShortTermMemory
ShortTermMemory ──promoteToLongTerm()──> LongTermMemory
```

#### 晋升 1：感知 → 短期

```java
public SegmentMemoryMessage promoteToShortTerm(SensoryMemory sensory) {
    // 1. 把感知信息（页面+上下文+操作）拼成一个 content 字符串
    StringBuilder sb = new StringBuilder();
    sb.append("[Page] ").append(sensory.getCurrentPageTitle());
    sb.append(" (").append(sensory.getCurrentPageUrl()).append(")");
    if (sensory.getPageContext() != null) sb.append("\n[Context] ").append(sensory.getPageContext());
    if (sensory.getUserActions() != null) sb.append("\n[Actions] ").append(...);

    // 2. 包装成一个 SegmentMemoryMessage，类型 = "SENSORY_CAPTURE"
    SegmentMemoryMessage segment = SegmentMemoryMessage.builder()
            .memoryType(MemoryType.SHORT_TERM)
            .content(sb.toString())
            .segmentType("SENSORY_CAPTURE")
            .role("system")
            .build();

    // 3. 加入对应 session 的短期记忆
    getShortTermMemory(sessionId).addSegment(segment);

    // 4. ★ 关键：晋升后立刻让感知失效
    sensory.invalidate();
}
```

**核心理念**：**Sensory 一旦被提升就立即失效**，避免重复使用。模拟大脑"注意力转移后旧的感知就会消失"。

#### 晋升 2：短期 → 长期

```java
public void promoteToLongTerm(String sessionId) {
    ShortTermMemory stm = shortTermPool.get(sessionId);

    // 1. 把短期记忆里所有 segment 拼成一个"会话摘要"
    StringBuilder summaryBuilder = new StringBuilder();
    List<String> keys = new ArrayList<>();
    for (SegmentMemoryMessage seg : stm.getSegmentMemories()) {
        summaryBuilder.append("[").append(seg.getRole()).append("] ")
                      .append(seg.getContent()).append("\n");
        if (seg.getSegmentType() != null) keys.add(seg.getSegmentType());
    }
    for (EntityMemoryMessage ent : stm.getEntityMemories()) {
        if (ent.getEntityType() != null) keys.add(ent.getEntityType());
    }

    // 2. 包装成一个 HistoryChatSummary 写入长期记忆
    LongTermMemory.HistoryChatSummary summary = LongTermMemory.HistoryChatSummary.builder()
            .sessionId(sessionId)
            .userId(userId)
            .summary(summaryBuilder.toString())
            .keys(keys)   // ← 检索关键字
            .build();
    longTermMemory.addChatSummary(summary);

    // 3. ★ 清空短期记忆，释放容量
    stm.clear();
}
```

**核心理念**：会话结束/超出容量时，把短期记忆**总结成摘要**写入长期记忆，**释放短期容量**。模拟大脑"睡眠时把白天的记忆整理归档"。

### 3.4 三层记忆的「统一上下文输出」

```java
public Map<String, Object> buildMemoryContext() {
    Map<String, Object> context = new HashMap<>();
    context.put("sensory",   /* 感知层快照 */);
    context.put("shortTerm", /* sessionId → ShortTermMemory.toMap() */);
    context.put("longTerm",  /* knowledgePoints + experiences + preferences + summaries */);
    return context;
}
```

这个统一上下文会被注入到 **PromptAssembler / SystemPromptBuilder**，成为 LLM prompt 的一部分。这就是 Agent "记得住" 的本质 — **每次推理时把三层记忆都放进 prompt**。

### 3.5 Segment 思维链（Chain-of-Thought）

代码位置：`src/main/java/com/agent/core/agent/segment/`

#### 设计目标

Segment 体系不是"记忆"，是 **"思维过程的录像带"**，记录 Agent 的每一步思考、每一次工具调用、每一个中间结果。输出长这样：

```
[USER_INPUT]: 订单 12345 接单异常
[THOUGHT]: 用户在询问订单异常，需要先查交易状态
[TOOL_CALL]: query_trade_order {"orderId": "12345"}
[TOOL_RESULT]: {"status": "PAID", "amount": 99.9}
[THOUGHT]: 交易正常，再查履约
[TOOL_CALL]: query_fulfillment {"orderId": "12345"}
[TOOL_RESULT]: {"status": "NOT_CREATED"}
[EXPRESS]: 订单支付成功但履约单未生成，建议重投 MQ 消息
```

#### 三个核心类

**`Segment` — 原子单位**

```java
public class Segment {
    private String id;                    // UUID
    private SegmentType type;             // USER_INPUT / THOUGHT / TOOL_CALL / TOOL_RESULT / EXPRESS / ERROR ...
    private String content;
    private Long timestamp;
    private Integer roundIndex;           // 属于第几轮
    private Map<String, Object> metadata;

    public String toPromptFormat() { ... }  // 渲染成 "[TYPE]: content"
}
```

**`SegmentType` — 类型枚举**

USER_INPUT、THOUGHT、CODE、CODE_RESULT、TOOL_CALL、TOOL_RESULT、ROUND、EXPRESS、ERROR、SENSORY_CAPTURE 等。

**`SegmentContext` — 时间线管理器**

```java
public class SegmentContext {
    private String sessionId;
    private List<Segment> currentSegments;   // 当前轮（活跃）
    private List<Segment> historySegments;   // 历史轮（已归档）
    private int currentRound;
}
```

**两段式结构**的设计意图：
- **currentSegments**：当前正在进行的 ReAct 循环里的 segment（可能还会增减）
- **historySegments**：已完成的轮次（只读，用于追溯）

#### 核心操作

```java
// 1. 添加 segment（自动打上当前轮号）
public void addSegment(Segment segment) {
    if (segment.getRoundIndex() == null) {
        segment.setRoundIndex(currentRound);
    }
    currentSegments.add(segment);
}

// 2. 归档当前轮（一轮 ReAct 结束后调用）
public void archiveCurrentRound() {
    historySegments.addAll(currentSegments);
    currentSegments = new ArrayList<>();
    currentRound++;
}

// 3. 拼装成 LLM 用的 user prompt（历史 + 当前一起拼）
public String buildUserPrompt() {
    return Stream.concat(historySegments.stream(), currentSegments.stream())
                 .map(Segment::toPromptFormat)
                 .collect(Collectors.joining("\n"));
}

// 4. 按类型筛选（比如只看 TOOL_CALL）
public List<Segment> getSegmentsByType(SegmentType type) { ... }

// 5. 取最近 N 个（用于 Token 限额裁剪）
public List<Segment> getLastNSegments(int n) { ... }
```

#### 在 ReActAgent 里的真实使用

```java
public void processInput(Event event) {
    // 1. 感知输入
    String userInput = perceptionArea.perceive(event);
    segmentContext.addSegment(SegmentBuilder.userInput(userInput));

    // 2. 认知
    CognitionResult cognition = cognitionArea.cognize(...);
    segmentContext.addSegment(SegmentBuilder.thought(cognition.getPlan()));

    // 3. 代码执行
    if (cognition.getGeneratedCode() != null) {
        segmentContext.addSegment(SegmentBuilder.code(cognition.getGeneratedCode()));
        Object result = codeSandbox.execute(...);
        segmentContext.addSegment(SegmentBuilder.codeResult(result));
    }

    // 4. 复杂任务多轮执行
    if (cognition.isComplexTask()) {
        motorArea.execute(...);   // 内部追加 ROUND/TOOL_CALL/TOOL_RESULT 等 segment
    }

    // 5. 自评 + 重试（最多 2 次）
    while (!selfEvaluationArea.evaluate(...) && retry