# 企业级银行 AI Assistant — 整体系统架构设计（深度版）

> 阶段：第一阶段
> 主题：从企业架构视角建立 AI Assistant 系统蓝图
> 定位：Tech Lead 架构教学 + 工程落地指导

---

## 写在前面：为什么架构设计阶段不写代码

很多 Java 工程师转型 AI 开发时，第一个冲动是：

> "让我先把 Spring AI 的 Demo 跑起来，再慢慢加功能。"

这在 Demo 阶段没问题。但如果目标是构建一个**企业级 AI 应用系统**，这种方式会导致：

1. **代码结构很快失控** — Prompt、Tool 调用、Memory、安全逻辑散落在 Controller 和 Service 中
2. **无法演进** — 想加 RAG、Workflow 时，发现原有结构不支持
3. **不可治理** — 出了问题不知道是 Prompt 的问题、模型的问题还是 Tool 的问题
4. **不合规** — 银行场景要求审计追溯，后补成本极高

所以，我们第一步不是写代码，而是建立**系统蓝图**。这个蓝图决定了：

- 后续每个阶段的工程边界
- 每个模块的职责和接口
- 哪些东西必须在第一天就坚持
- 哪些东西可以后面再补

---

## 第一部分：核心认知转换 — 从 Java 后端到 AI 工程

### 1.1 两种系统的本质差异

在传统 Java 后端，系统是这样的：

```
HTTP Request → Controller → Service → Repository → Database → Response
```

一切都是确定性的：你写的代码决定了系统行为。输入 A 一定得到输出 B。

在 AI 应用中，系统变成了这样：

```
User Message
 → Context 构建（Prompt + Memory + RAG Context + Tool Schema）
 → LLM 推理（不确定性：可能返回文本，也可能返回 Tool Call）
 → Tool 调度（可能 0 次、1 次、多次）
 → 多轮循环（ReAct: Reason → Act → Observe → Reason...）
 → State 流转（Workflow 状态机）
 → Streaming Response
```

**核心差异总结：**

| 维度 | 传统 Java 后端 | AI 应用系统 |
|------|---------------|-------------|
| 调用链 | 确定性：Controller → Service → DAO | 不确定性：可能多轮 LLM 推理、多次 Tool 调用 |
| 业务逻辑 | 100% 写在代码中 | 一部分在代码中，一部分由 Prompt + 模型能力共同决定 |
| 状态管理 | 主要关注事务一致性 | 额外关注对话状态、Agent 状态、Workflow 状态 |
| 输入校验 | 参数来自前端，格式可控 | 参数来自 LLM 生成，可能编造、遗漏、格式错误 |
| 权限控制 | 用户直接调接口，权限在网关/拦截器 | LLM 代用户调 Tool，权限必须在 Tool Runtime 后置校验 |
| 输出 | 结构化 JSON | 可能是自然语言、结构化数据、Tool Call 的混合 |
| 可观测性 | QPS、延迟、错误率 | 额外关注 Token 消耗、Prompt 版本、Tool 调用链、幻觉率 |
| 成本 | CPU/内存/带宽 | 额外有 LLM Token 费用，每次调用都在烧钱 |

### 1.2 AI 工程师的核心能力模型

作为从 Java 后端转型的 AI 工程师，你需要建立以下能力：

```
┌─────────────────────────────────────────────────┐
│              AI 工程师能力模型                     │
│                                                  │
│  [1] AI Runtime 思维                              │
│      理解 Prompt → Model → Tool → State 的编排流程 │
│                                                  │
│  [2] Agent 思维                                   │
│      理解 ReAct、Tool Calling、多轮推理循环         │
│                                                  │
│  [3] Context 思维                                 │
│      理解 Token 是有限的，Context 需要精心构造      │
│                                                  │
│  [4] Tool Runtime 思维                            │
│      理解 LLM 生成的参数不可信，Tool 必须受控执行    │
│                                                  │
│  [5] State 思维                                   │
│      理解对话状态、Workflow 状态需要持久化管理        │
│                                                  │
│  [6] Prompt Engineering 思维                      │
│      理解 Prompt 是 AI 应用的"业务逻辑"，需要版本化  │
│                                                  │
│  [7] Safety 思维                                  │
│      理解 AI 系统的安全边界与传统系统完全不同         │
└─────────────────────────────────────────────────┘
```

### 1.3 本项目公式

```
AI Assistant
  = Conversation Runtime      （会话管理）
  + Agent Runtime             （Agent 编排）
  + Tool Runtime              （工具管控执行）
  + RAG Runtime               （知识检索注入）
  + Workflow Runtime           （状态流程管控）
  + Prompt Manager            （模型行为配置）
  + Memory Manager            （上下文分层管理）
  + Model Gateway             （模型接入调度）
  + Safety Guard              （安全护栏）
  + AI Observability          （可观测可审计）
```

---

## 第二部分：系统总体分层架构

### 2.1 架构全景图

```
┌──────────────────────────────────────────────────────────┐
│                     Client Layer                          │
│          Web App / Mobile / 企业微信 / 内部 Portal          │
└─────────────────────────┬────────────────────────────────┘
                          │ SSE Streaming / REST API
┌─────────────────────────▼────────────────────────────────┐
│                  API / BFF Layer                           │
│                                                          │
│  认证 │ 限流 │ 路由 │ 审计入口 │ 权限校验 │ 会话管理         │
│  traceId 注入 │ conversationId 管理 │ UserContext 构建     │
└─────────────────────────┬────────────────────────────────┘
                          │
┌─────────────────────────▼────────────────────────────────┐
│               AI Application Layer                        │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐   │
│  │ Conversation │  │    Agent     │  │   Workflow    │   │
│  │   Service    │  │   Manager    │  │   Manager     │   │
│  └──────┬───────┘  └──────┬───────┘  └───────┬───────┘   │
│         │                 │                   │           │
│  ┌──────▼─────────────────▼───────────────────▼────────┐  │
│  │              AI Runtime（核心引擎）                    │  │
│  │                                                     │  │
│  │  Prompt Builder     │ Context Manager │ Memory Mgr  │  │
│  │  Model Router       │ Token Manager   │ Guard Rails │  │
│  │  Streaming Handler  │ Fallback Mgr    │ Safety Guard│  │
│  └──────────────────────────┬──────────────────────────┘  │
│                             │                             │
│  ┌──────────────────────────▼──────────────────────────┐  │
│  │           Tool Runtime（工具执行层）                  │  │
│  │  Tool Registry │ Tool Policy │ Parameter Validator  │  │
│  │  Tool Executor │ Permission Check │ Audit Logger    │  │
│  │  Result Adapter │ Timeout Control │ Retry Handler   │  │
│  └──────────────────────────┬──────────────────────────┘  │
│                             │                             │
│  ┌──────────────────────────▼──────────────────────────┐  │
│  │            RAG Runtime（知识检索层）                   │  │
│  │  Document Ingestion │ Chunking │ Embedding           │  │
│  │  Retriever │ Rerank │ Context Compressor │ Citation  │  │
│  └─────────────────────────────────────────────────────┘  │
└──────────────────────────┬───────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────┐
│              Business Service Layer                        │
│                                                          │
│  HR Service │ Points Service │ Leave Service              │
│  Timesheet Service │ Knowledge Service │ ...              │
└──────────────────────────┬───────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────┐
│              Infrastructure Layer                          │
│                                                          │
│  PostgreSQL │ pgvector │ Redis │ MQ │ File Storage        │
└──────────────────────────────────────────────────────────┘
```

### 2.2 为什么这样分层？每一层的职责边界

#### Gateway / BFF 层

**职责**：流量入口，统一处理认证、限流、审计、权限、会话管理。

**与传统后端的差异**：
- 传统 BFF 主要做数据聚合和接口适配
- AI BFF 额外需要：
  - 构建 `UserContext`（用户身份、角色、权限、部门）
  - 注入 `traceId`、`conversationId`、`messageId`
  - 管理 SSE 连接和流式推送
  - 审计日志的入口记录

**为什么 UserContext 在这一层构建？**
因为 UserContext 是安全上下文的起点。它必须来自认证系统（JWT/SSO），而不是 AI Runtime 自己猜。AI Runtime 可以**使用** UserContext，但不能**创建** UserContext。

#### AI Application 层

**职责**：AI 用例的业务编排层。管理会话、调度 Agent、启动 Workflow。

**核心模块**：
- **Conversation Service**：管理会话生命周期、消息收发、SSE 事件分发
- **Agent Manager**：决定使用哪个 Agent 处理当前请求
- **Workflow Manager**：管理有状态的业务流程

**为什么 Conversation Service 不直接调 LLM？**
因为一次用户请求可能触发复杂的内部事件链：

```
User Message
 → Prompt Build → Model Thinking → Tool Call Request
 → Tool Result → Model Follow-up → Tool Call Again
 → Model Final Answer → Memory Update → Audit Log
```

如果这些逻辑堆在 Conversation Service 中，它会变成一个不可维护的"AI 大杂烩"。Conversation Service 只负责**会话管理**，AI 推理交给 AI Runtime。

#### AI Runtime 层（系统大脑）

**职责**：AI 应用的统一编排引擎。接收用户输入 + 业务上下文，构造 Prompt，调用模型，调度 Tool，管理 Memory，输出 Streaming。

**为什么 AI Runtime 必须独立？**

在传统 Java 后端，`Controller → Service → DAO` 是确定性调用链。但在 AI 应用中：
- LLM 可能决定调用 0 个、1 个或多个 Tool
- 可能触发多轮推理循环（ReAct）
- 每次推理的 Token 消耗不同
- 每次推理的延迟不同
- 可能触发 Guard Rails 拦截
- 可能需要 Model Fallback

这些**不确定性**要求我们把"AI 推理编排"独立成一个专门的 Runtime，而不是散落在业务代码中。

#### Tool Runtime 层

**职责**：连接 AI 与业务系统的受控执行层。

**为什么 Tool Runtime 不放在 AI Runtime 里面？**
因为 Tool Runtime 有自己的关注点：
- Tool 注册与发现
- Tool 权限策略
- Tool 参数校验
- Tool 执行与超时控制
- Tool 结果适配
- Tool 审计日志

这些关注点与 AI Runtime 的 Prompt 构建、模型调度是不同的。分离后，Tool Runtime 可以独立演进（比如加新 Tool、改权限策略），不影响 AI Runtime。

#### RAG Runtime 层

**职责**：企业知识的检索、注入和引用管理。

独立原因同 Tool Runtime — 它有自己的关注点：文档入库、切片策略、Embedding、检索、Rerank、权限过滤、引用溯源。

#### Business Service 层

**职责**：具体业务逻辑实现。与传统后端基本一致。

**关键差异**：Business Service 不再直接被 Controller 调用，而是通过 Tool Runtime 间接调用。这意味着 Business Service 需要适配 Tool 的调用模式。

#### Infrastructure 层

**职责**：存储、缓存、向量检索、消息队列。

与传统后端相比，多了 **pgvector**（向量存储和检索）。

### 2.3 架构核心原则（底线清单）

从第一天起，以下原则不可妥协：

1. **Controller 不直接调用 LLM** — 必须通过 AI Runtime
2. **Business Service 不直接拼 Prompt** — Prompt 由 Prompt Manager 统一管理
3. **Tool 不直接信任 LLM 参数** — Tool Runtime 必须校验
4. **RAG 不直接把检索结果无脑塞 Prompt** — 需要权限过滤和 Token 控制
5. **Workflow 不交给模型自由发挥** — 关键状态由 Runtime 管理
6. **所有模型输入输出、工具调用、检索结果必须可观测可审计**
7. **权限判断由系统代码执行，绝不交给模型**
8. **写操作必须 Human-in-the-Loop** — LLM 不能直接提交假期申请

---

## 第三部分：AI Runtime 深度架构

### 3.1 AI Runtime 的定位

AI Runtime 不是一个框架类，也不是 `ChatClient` 的简单包装。

它是 AI 应用的**统一编排层** — 相当于传统应用中的"应用服务器 + 调度器 + 策略引擎"。

### 3.2 AI Runtime 内部模块

```
┌─────────────────────────────────────────────────────┐
│                    AI Runtime                        │
│                                                     │
│  ┌────────────────┐  ┌─────────────────────────┐    │
│  │ Context Builder │  │     Prompt Manager      │    │
│  │  组装系统上下文   │  │  模板管理 │ 版本控制     │    │
│  └────────┬───────┘  └────────────┬────────────┘    │
│           │                       │                 │
│  ┌────────▼───────────────────────▼──────────────┐  │
│  │              Agent Runtime                     │  │
│  │  ReAct Loop │ Tool Calling │ Structured Output │  │
│  └────────┬──────────────────────────────────────┘  │
│           │                                         │
│  ┌────────▼──────────┐  ┌───────────────────────┐   │
│  │   Model Gateway    │  │    Memory Manager     │   │
│  │  Router │ Fallback  │  │  短期 │ 长期 │ 摘要   │   │
│  │  Retry  │ CostTrack │  └───────────────────────┘   │
│  └────────┬──────────┘                               │
│           │                                         │
│  ┌────────▼──────────┐  ┌───────────────────────┐   │
│  │ Streaming Handler  │  │     Safety Guard      │   │
│  │  SSE Event 推送    │  │  输入过滤 │ 输出脱敏   │   │
│  └───────────────────┘  └───────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

### 3.3 一次完整请求的 Runtime 数据流

以用户输入"帮我查一下我的积分余额"为例：

```
[Step 1] 请求进入
│  API 层构建 UserContext { userId, employeeNo, roles, permissions }
│  生成 traceId, conversationId, messageId
│
▼
[Step 2] Conversation Service
│  创建 Turn Context（本次对话轮次的运行时上下文）
│  从 Memory Manager 加载最近 N 轮对话历史
│
▼
[Step 3] Context Builder
│  加载 System Prompt（Assistant 身份、规则、安全边界）
│  注入 UserContext（角色、部门、可用能力）
│  注入 Memory（最近对话历史，可能带摘要）
│  注入可用 Tool 列表（根据用户权限过滤后的工具集）
│  注入 RAG Context（如果检测到知识类意图）
│
▼
[Step 4] Prompt Assembler
│  按顺序组装完整 Prompt：
│    System Instruction → Security Policy → Role Context
│    → Task Instruction → Tool Definitions → Memory Context
│    → RAG Context → User Message
│  Token 预估 → 如果超限则裁剪（截断历史/压缩 RAG Context）
│
▼
[Step 5] Model Router
│  根据任务类型选择模型（简单查询用轻量模型，复杂推理用强力模型）
│  检查主模型可用性 → 不可用则 Fallback
│
▼
[Step 6] LLM 推理（Streaming）
│  模型开始 Streaming 输出...
│  → 如果模型输出 Tool Call：进入 [Step 7]
│  → 如果模型输出文本：跳到 [Step 9]
│
▼
[Step 7] Tool Dispatch（Tool Runtime 处理）
│  解析 Tool Call: toolName = "point.balance.query"
│  检查 Tool 是否存在 → ✅
│  检查用户权限 → point:balance:read → ✅
│  校验参数 → employeeNo 强制使用当前登录用户（忽略 LLM 可能编造的值）
│  执行 Tool → 调用积分系统
│  记录审计日志
│  返回结构化 Tool Result: { balance: 1250, lastUpdated: "2026-06-24" }
│
▼
[Step 8] Tool Result → LLM 二次推理
│  将 Tool Result 注入当前 Context
│  LLM 生成自然语言回答："您的积分余额为 1250 分，最后更新时间为..."
│  （如果 LLM 需要调用更多 Tool → 回到 Step 7，形成 ReAct Loop）
│
▼
[Step 9] Response 输出
│  SSE Streaming 推送给前端
│  Memory Manager 保存本轮对话
│  Observability 记录完整 trace：
│    Prompt 版本、Token 消耗、Tool 调用链、延迟、模型名称
│
▼
[Step 10] 完成
```

### 3.4 关键设计决策

#### 为什么 Context Builder 和 Prompt Assembler 分开？

**Context Builder** 负责"收集原材料" — 从各个来源（Memory、RAG、Tool Registry、UserContext）收集信息。

**Prompt Assembler** 负责"按配方组装" — 根据模板和规则，把原材料拼装成最终的 Prompt。

分离的好处：
- Context Builder 可以并行加载不同来源的数据
- Prompt Assembler 可以独立做 Token 预算和裁剪策略
- 后续可以做 A/B 测试（同样的 Context，不同的组装策略）

#### 为什么 Agent Runtime 在 AI Runtime 内部？

Agent Runtime 是 AI Runtime 的一种**执行模式**。AI Runtime 可能选择：
- 直接回答（不走 Agent）
- 走 Agent 模式（ReAct Loop）
- 走 Workflow 模式（状态机）

这个决策本身是 AI Runtime 的职责，不应该暴露给上层。

#### Demo / 基础 / 企业级实现差异

| 层级 | AI Runtime 实现 | 问题 |
|------|----------------|------|
| Demo 级 | Controller 直接调 `ChatClient.prompt().call()` | 无 Context 管理、无 Tool 权限、无审计 |
| 基础实现 | 封装一个 `AssistantService` 做简单 Prompt + Tool 调用 | 有基本结构，但缺少 Token 管理、Fallback、安全护栏 |
| 企业级实现 | 完整 AI Runtime：Context Builder + Prompt Assembler + Model Gateway + Safety Guard + Observability | 复杂度高，但可治理、可演进 |

---

## 第四部分：Tool Runtime 深度架构

### 4.1 Tool 的企业级定义

在企业 AI 系统中，Tool **不是**一个简单的 Java 方法。

Tool 是"**模型可以请求调用，但必须由系统 Runtime 审核和执行的受控业务能力**"。

一个企业级 Tool 至少包含：

```
Tool {
  toolName           // 工具标识，例如 "point.balance.query"
  description        // 给模型看的工具描述（影响模型是否选择调用）
  inputSchema        // 参数 Schema（JSON Schema）
  outputSchema       // 返回结构定义
  permissionPolicy   // 权限策略（哪些角色/权限可以调用）
  riskLevel          // 风险等级（LOW / MEDIUM / HIGH）
  confirmationPolicy // 确认策略（是否需要用户确认）
  idempotencyPolicy  // 幂等策略
  timeoutMs          // 超时时间
  retryPolicy        // 重试策略
  auditPolicy        // 审计策略
  category           // 分类（HR / Points / Leave / Timesheet）
  executor           // 实际执行器
}
```

### 4.2 Tool Runtime 数据流

```
LLM 生成 Tool Call { toolName: "point.balance.query", args: { employeeNo: "EMP001" } }
│
▼
[1] Tool Registry
│   查找 toolName 是否存在 → 存在
│   加载 Tool Definition + Policy
│
▼
[2] Permission Check
│   检查 UserContext.permissions 是否包含 "point:balance:read"
│   强制 employeeNo = UserContext.employeeNo（忽略 LLM 传入值）
│
▼
[3] Parameter Validation
│   JSON Schema 校验参数格式
│   业务规则校验（例如日期范围是否合理）
│
▼
[4] Confirmation Check（写操作才需要）
│   如果 confirmationPolicy = REQUIRED：
│     暂停执行，向用户展示操作详情
│     等待用户确认
│     用户取消则终止并通知 LLM
│
▼
[5] Tool Execution
│   调用 Business Adapter → 积分系统
│   超时控制（timeoutMs = 3000）
│   失败重试（retryPolicy = 2 次）
│
▼
[6] Result Adapter
│   将 Java 对象转为 LLM 可理解的结构化文本
│   例如：{ "balance": 1250, "currency": "points", "lastUpdated": "2026-06-24" }
│
▼
[7] Audit Log
│   记录：who(用户) + when(时间) + what(toolName) + args(参数) + result(结果) + latency(耗时)
│
▼
[8] 返回 Tool Result 给 Agent Runtime
```

### 4.3 读操作 vs 写操作 — 最关键分类

| 类型 | 示例 | 确认策略 | 权限要求 | 审计级别 |
|------|------|----------|----------|----------|
| 读操作（Read Tool） | 查积分余额、查积分历史、查制度 | 不需要 | 只读权限 | 标准 |
| 写操作（Write Tool） | 提交假期、提交 OT、填工时 | **必须确认** | 写权限 | 增强 |

**为什么写操作必须 Human-in-the-Loop？**

LLM 可能：
- 误解用户意图（用户说"我想请假"，但没说请几天，LLM 自己猜了 3 天）
- 编造参数（日期格式错误、假期类型搞混）
- 在不合适的时机提交（用户还在讨论，没做最终决定）

企业级系统不能让 LLM 直接代替用户做决策。正确流程是：

```
用户说："帮我请个假"
 → LLM 抽取参数：假期类型=年假, 开始=7月1日, 结束=7月3日
 → Tool Runtime 识别这是写操作
 → 向用户展示："即将提交年假申请：7月1日 - 7月3日（共3天），确认提交？"
 → 用户确认 → 执行
 → 用户取消/修改 → 返回 LLM 继续对话
```

### 4.4 初始 Tool 选择（第二阶段）

| Tool | 类型 | 风险等级 | 确认 | 选择原因 |
|------|------|----------|------|----------|
| `point.balance.query` | 查询型 | LOW | 否 | 最小闭环验证 Tool Calling |
| `point.history.query` | 查询型 | LOW | 否 | 验证列表型结构化返回 |
| `leave.application.submit` | 提交型 | MEDIUM | 是 | 引入缺槽追问、参数校验、用户确认 |

为什么先选这三个：
- 覆盖查询型和提交型两类核心模式
- 复杂度可控，不会过早陷入全量业务系统集成
- 能验证 Tool Calling 的完整生命周期

### 4.5 Tool Runtime 分层

```
Tool Runtime
├── Tool Registry          # 工具注册中心（发现和管理可用工具）
├── Tool Definition        # 给模型看的工具描述和参数 Schema
├── Tool Policy            # 给系统看的权限、风险、确认、审计规则
├── Parameter Validator    # 校验模型生成的参数是否合规
├── Tool Executor          # 执行工具调用的应用层入口
├── Result Adapter         # 将业务结果转为 LLM 可理解的格式
└── Business Adapter       # 对接真实业务系统（积分、HR、OA 等）
```

**为什么 Tool Definition 和 Tool Policy 分开？**

- **Tool Definition** 是给 LLM 看的：description、inputSchema。它影响 LLM 是否选择调用这个 Tool、怎么生成参数。
- **Tool Policy** 是给系统看的：权限、风险等级、确认策略、审计策略。它对 LLM 不可见。

这种分离确保了 LLM 只能看到"它能做什么"，而看不到"系统怎么管控它"。

### 4.6 Demo / 基础 / 企业级差异

| 层级 | 特征 | 风险 |
|------|------|------|
| Demo 级 | 用 `@Tool` 注解暴露一个 Java 方法 | 无权限、无审计、无确认、无参数校验 |
| 基础实现 | 有 Tool 注册和参数 DTO | 仍缺少策略治理（权限、确认、审计） |
| 企业级实现 | Tool Definition + Policy + Validator + Executor + Audit | 可治理、可审计、可演进 |

---

## 第五部分：RAG 架构深度设计

### 5.1 RAG 的定位

RAG（Retrieval-Augmented Generation）不是"把文档丢进向量库然后搜索"。

它是一套**知识运行时**：管理企业知识的入库、检索、注入和引用。

```
AI Assistant = LLM 推理能力 + 企业知识（RAG 注入）
```

没有 RAG，LLM 只能靠训练数据回答问题，无法回答企业内部制度。

### 5.2 RAG 双流程架构

#### 离线流程（文档入库）

```
PDF / Word / Confluence / Wiki
│
▼
[1] Document Parser（文档解析）
│   提取文本、表格、图片描述
│   保留文档结构（章节、条款、标题层级）
│
▼
[2] Smart Chunking（智能切片）
│   不是固定长度切割！
│   银行制度文档有严格结构（章 → 节 → 条 → 款）
│   需要 Semantic Chunking + 结构感知切片
│   每个 Chunk 保留：
│     - 所属文档 ID
│     - 所属章节
│     - 上下文标题链
│     - 权限元数据
│
▼
[3] Embedding（向量化）
│   调用 Embedding Model 将 Chunk 转为向量
│   同时保存原始文本（用于检索后展示）
│
▼
[4] Indexing（索引入库）
│   向量 → pgvector
│   元数据 → PostgreSQL
│   包含：documentId, chunkId, title, department,
│         visibility, effectiveDate, version, tags, sourceUri
```

#### 在线流程（查询检索）

```
用户提问："加班审批流程是什么？"
│
▼
[1] Query Rewriting（查询改写）
│   优化查询，提升召回率
│   例如扩展为："加班审批流程 加班申请审批步骤 OT approval process"
│
▼
[2] Permission Filter（权限过滤）
│   基于 UserContext.dataScopes 过滤候选文档
│   普通员工不能检索到管理层专有制度
│
▼
[3] Hybrid Retrieval（混合检索）
│   向量检索（语义相似度）+ 关键词检索（精确匹配）
│   从 pgvector 检索 Top-K 相关 Chunk
│
▼
[4] Rerank（重排序）
│   对检索结果重排序，提升精确度
│   银行场景对精确度要求极高（制度条文不能答错）
│
▼
[5] Context Compression（上下文压缩）
│   控制注入 Prompt 的 Token 数量
│   去除冗余 Chunk、保留核心信息
│
▼
[6] Prompt Injection（注入 Prompt）
│   将筛选后的 Chunk 注入 Prompt 的 RAG Context 区域
│   明确标注来源信息
│
▼
[7] LLM 生成回答（带引用来源）
│   回答中标注：[来源：员工手册 v3.2 第5章第3条]
│
▼
[8] 记录检索 Trace
│   保存本次检索的 query、命中 chunk、rerank 结果
```

### 5.3 为什么银行场景必须做权限过滤

企业知识不是完全公开的：
- 普通员工只能看员工手册、通用制度
- HR 可以看详细的人事规则
- 管理者可以看审批政策
- 风控/审计岗位可以看特定合规文件

**错误做法**：先全库向量检索，再把最相似内容给模型。

**正确做法**：先结合权限范围过滤候选文档，再做向量检索和 Rerank。

### 5.4 向量库选型

| 方案 | 优点 | 缺点 | 当前阶段选择 |
|------|------|------|-------------|
| pgvector | 与 PG 同库、运维简单、事务/权限方便 | 超大规模不如专用向量库 | ✅ 首选 |
| Milvus | 大规模向量检索能力强 | 运维复杂度高 | 后期如需可迁移 |
| Elasticsearch Hybrid | 关键词检索强 | 向量能力需额外投入 | 视场景补充 |

选择 pgvector 的核心原因：与 PostgreSQL 同库，事务一致性好，权限管理方便，适合银行内部系统的运维要求。

### 5.5 RAG 企业级要求

1. 回答必须带引用来源（银行审计需要）
2. 检索必须做权限过滤（合规要求）
3. 文档版本必须可追溯（制度更新后旧版本不能丢失）
4. Prompt 中注入的上下文必须控制 Token（否则成本爆炸）
5. 无命中时必须明确说"不知道"，**不能编造**
6. 必须记录每次回答用了哪些 Chunk（可追溯）

---

## 第六部分：Memory 架构深度设计

### 6.1 Memory 的定位

传统后端是**无状态**的 — 每次 HTTP 请求独立处理。

AI 对话是**有状态**的：
- 用户说"帮我请假" → 下一句"下周一" → 再下一句"年假"
- 这三句话是一个上下文，LLM 必须理解"下周一请年假"

Memory 是对话状态和用户上下文的**分层管理系统**。

### 6.2 Memory 分层

```
┌─────────────────────────────────────────────────────────┐
│                    Memory Manager                        │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Turn Context（请求级，内存中）                     │    │
│  │ 当前轮输入、Tool 调用结果、临时推理状态              │    │
│  │ 生命周期：单次请求                                 │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Short-term Memory（会话级，Redis）                │    │
│  │ 最近 N 轮对话消息                                  │    │
│  │ Sliding Window 策略 / Summary 压缩策略             │    │
│  │ 生命周期：会话期间（TTL 自动过期）                   │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Conversation Summary（会话摘要，PostgreSQL）       │    │
│  │ 长会话压缩为摘要                                   │    │
│  │ 生命周期：长期                                     │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │ User Profile Memory（用户级，PostgreSQL）          │    │
│  │ 用户偏好、常用查询模式、部门特征                     │    │
│  │ 生命周期：长期，需授权                              │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Workflow State（流程级，PostgreSQL）               │    │
│  │ 未完成表单字段、审批状态、流程节点                    │    │
│  │ 生命周期：流程完成前                                │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### 6.3 为什么不能无脑保存和注入所有历史

- **Token 成本**：每多注入 1000 Token 的历史，每次调用就多烧钱
- **干扰模型**：无关历史会干扰 LLM 判断
- **信息过期**：历史中可能有过期信息
- **安全风险**：可能把敏感信息带入不相关请求
- **注入攻击**：多轮对话可能发生 Prompt Injection 污染

### 6.4 Memory 数据流

```
用户发起新消息
│
▼
[1] 加载 Short-term Memory（最近 N 轮）
│
▼
[2] 加载 Conversation Summary（如果会话较长）
│
▼
[3] 加载当前未完成 Workflow State（如果有）
│
▼
[4] 根据当前任务选择相关 Memory
│   不是所有 Memory 都需要注入！
│   例如：用户问积分余额，不需要注入假期申请的 Workflow State
│
▼
[5] 构造 Prompt Context
│
▼
[6] 模型生成结果
│
▼
[7] 提取需要保存的状态
│   保存本轮消息
│   更新 Summary（如果需要）
│   更新 Workflow State（如果有）
```

### 6.5 阶段实施策略

| 阶段 | Memory 能力 | 原因 |
|------|------------|------|
| 第二阶段（最小闭环） | Turn Context + Short-term（最近消息） | 先验证 Agent 闭环 |
| 第三阶段（RAG） | 加入 RAG Context 管理 | 知识检索需要上下文 |
| 第四阶段（Workflow） | 加入 Workflow State 持久化 | 状态机需要持久化 |
| 第五阶段（增强） | 加入 Summary + User Profile | Token 优化和个性化 |

---

## 第七部分：Workflow 架构深度设计

### 7.1 Workflow 的定位

Workflow 处理**确定性较强、状态明确、风险较高**的业务流程。

例如：假期申请、OT 申请、工时填写、审批辅助。

这些场景不能完全交给 Agent 自由推理，因为涉及：
- 表单字段（必须完整）
- 业务规则（必须遵守）
- 用户确认（必须执行）
- 审批流（必须流转）
- 审计（必须记录）
- 状态持久化（不能丢失）

### 7.2 Agent 与 Workflow 的边界

```
Agent 理解用户想做什么    → 意图识别、参数抽取、缺槽追问
Workflow 管理接下来必须怎么做 → 状态流转、规则校验、确认提交
Tool 执行最终业务动作      → 调用业务系统
```

| 能力 | Agent 适合 | Workflow 适合 |
|------|-----------|---------------|
| 用户意图理解 | ✅ | ❌ |
| 参数抽取 | ✅ | 部分 |
| 缺槽追问 | ✅ | ✅ |
| 状态流转 | ❌ 不适合作为唯一来源 | ✅ |
| 审批提交 | 需要 Runtime 控制 | ✅ |
| 审计 | 需要系统记录 | ✅ |

### 7.3 假期申请状态机示例

```
START
  │
  ▼
COLLECT_LEAVE_TYPE        ← 收集假期类型（年假/病假/事假...）
  │
  ▼
COLLECT_DATE_RANGE        ← 收集起止日期
  │
  ▼
COLLECT_REASON            ← 收集事由（如果需要）
  │
  ▼
VALIDATE_POLICY           ← 校验假期政策（余额够不够、日期冲不冲突）
  │
  ▼
USER_CONFIRM              ← 向用户展示申请详情，等待确认
  │
  ├── 用户确认 → SUBMIT_APPLICATION → COMPLETED
  │
  └── 用户取消/修改 → 回到相应节点
```

**为什么状态必须持久化？**

- 用户可能刷新页面
- 服务可能重启
- 模型可能重试
- 审批可能需要等待上级

如果状态只存在 Prompt 里或内存里，这些情况下流程会丢失。

### 7.4 LangGraph4j 引入时机

**第四阶段再引入 LangGraph4j。**

原因：
- 第一阶段：完成架构认知
- 第二阶段：先跑通最小 Agent + Tool（用 Spring AI / LangChain4j 的基础能力）
- 第三阶段：接入 RAG
- 第四阶段：再把状态机和 Agent Loop **显式图化**

这符合企业落地节奏：**先闭环，再治理，再图化和编排**。

LangGraph4j 的核心价值：
- **StateGraph**：显式定义状态节点和转换
- **Conditional Edge**：根据 State 动态决定路径
- **Checkpoint**：状态持久化，支持断点恢复
- **循环支持**：Agent 可以多次调用 Tool（不是一次性的）

### 7.5 Workflow 企业级要求

1. 状态必须持久化
2. 状态流转必须可审计
3. 高风险动作必须用户确认
4. 模型不能绕过状态机直接提交
5. 失败后可重试、可恢复
6. 支持人工介入

---

## 第八部分：Prompt 管理设计

### 8.1 Prompt 的定位

Prompt 是 AI 应用的"**行为配置 + 运行指令 + 上下文组装协议**"。

在传统后端，业务逻辑写在 Java 代码中。在 AI 应用中，相当一部分"业务逻辑"写在 Prompt 中：
- System Prompt 定义了 Assistant 的身份和行为边界
- Tool Description 影响了 LLM 选择哪个工具
- RAG Context 的注入方式影响了回答质量
- Safety Instruction 决定了模型的安全行为

### 8.2 Prompt 分层结构

```
┌─────────────────────────────────────────────────────┐
│                 Prompt 分层结构                       │
│                                                     │
│  Layer 1: System Prompt                              │
│    Assistant 身份、行为边界、安全规则                   │
│                                                     │
│  Layer 2: Security Policy                            │
│    禁止回答的问题、敏感信息处理规则                      │
│                                                     │
│  Layer 3: Role / Permission Context                  │
│    用户角色、可用能力（不注入敏感信息）                   │
│                                                     │
│  Layer 4: Task Instruction                           │
│    本轮任务说明（由 Agent Runtime 动态生成）             │
│                                                     │
│  Layer 5: Tool Definitions                           │
│    可用工具列表、参数说明、调用约束                      │
│                                                     │
│  Layer 6: Memory Context                             │
│    历史摘要、当前流程状态                               │
│                                                     │
│  Layer 7: RAG Context                                │
│    检索到的知识片段（带来源标注）                        │
│                                                     │
│  Layer 8: User Message                               │
│    用户原始输入                                       │
└─────────────────────────────────────────────────────┘
```

### 8.3 Prompt 构造顺序与 Token 预算

Prompt 的构造不是简单拼接，需要考虑 Token 预算：

```
可用 Token 总量（例如 8000）
  - System Prompt         ≈ 500 tokens
  - Security Policy       ≈ 200 tokens
  - Role Context          ≈ 100 tokens
  - Tool Definitions      ≈ 800 tokens（工具越多越贵）
  - Memory Context        ≈ 500 tokens（需要裁剪策略）
  - RAG Context           ≈ 1000 tokens（需要压缩策略）
  - User Message          ≈ 200 tokens
  = 预留输出空间           ≈ 4700 tokens
```

**当 Token 超限时，裁剪优先级：**
1. 先裁剪 Memory（截断更早的历史）
2. 再裁剪 RAG Context（只保留 Top Chunk）
3. 最后考虑精简 Tool Definitions

### 8.4 Prompt 管理方式

```
┌─────────────────────────────────────────────────────┐
│                Prompt Manager                        │
│                                                     │
│  模板化管理：                                         │
│  - System Prompt Template                           │
│  - Tool Description Template                        │
│  - RAG Context Template                             │
│  - Confirmation Template                            │
│                                                     │
│  变量注入：                                          │
│  - {{user_name}}, {{user_role}}                     │
│  - {{available_tools}}                              │
│  - {{rag_context}}                                  │
│  - {{conversation_history}}                         │
│  - {{workflow_state}}                               │
│                                                     │
│  版本管理：                                          │
│  - Prompt v1.0 / v1.1 / v2.0                       │
│  - 效果评估（Tool 调用准确率、回答质量）                │
│  - A/B Testing（后续）                               │
│                                                     │
│  存储方式：                                          │
│  - 初期：文件 + classpath 资源                       │
│  - 后期：数据库 + Prompt Registry                    │
└─────────────────────────────────────────────────────┘
```

### 8.5 为什么 Prompt 需要版本管理

改了 Prompt 可能影响：
- 回答质量
- Tool 调用准确率
- Token 消耗
- 安全行为

Prompt 是 AI 应用的"业务逻辑"，必须像管理代码一样管理 Prompt。

### 8.6 Prompt 企业级风险

| 风险 | 示例 | 控制方式 |
|------|------|----------|
| Prompt 漂移 | 修改提示词导致 Tool 调用异常 | 版本管理 + 回归测试 |
| Prompt Injection | 文档中诱导模型忽略系统规则 | RAG 内容隔离 + 安全指令 |
| Token 膨胀 | 注入过多历史和文档 | Context 选择 + 摘要 + 压缩 |
| 输出不稳定 | 格式不符合后端解析 | Structured Output + Schema 校验 |

---

## 第九部分：Model Gateway 设计

### 9.1 Model Gateway 的定位

屏蔽不同模型供应商差异，提供统一的模型调用接口。

### 9.2 职责

```
┌─────────────────────────────────────────────────────┐
│                 Model Gateway                        │
│                                                     │
│  ChatModelClient      ← 对话模型调用                  │
│  EmbeddingModelClient ← 向量化模型调用                │
│  RerankModelClient    ← 重排序模型调用                │
│                                                     │
│  ModelRouter          ← 根据任务类型选模型             │
│  FallbackPolicy       ← 主模型不可用时切换             │
│  RetryPolicy          ← 失败重试（指数退避）           │
│  RateLimiter          ← 限流（保护配额）               │
│  CostTracker          ← Token 消耗和费用统计           │
└─────────────────────────────────────────────────────┘
```

### 9.3 Model Router 策略

| 任务类型 | 推荐模型 | 原因 |
|----------|----------|------|
| 简单查询（查积分） | 轻量模型（如 GPT-4o-mini） | 简单 Tool Calling，省 Token |
| 复杂推理（多步工作流） | 强力模型（如 GPT-4o） | 需要强推理能力 |
| RAG 回答 | 中等模型 | 主要靠检索内容，模型只需整合 |
| Embedding | 专用 Embedding 模型 | 与 Chat 模型不同 |

### 9.4 阶段实施

当前阶段：先简单封装 Spring AI / LangChain4j 的 ChatClient。

后续演进为完整的 Model Gateway，支持多模型、Fallback、Cost Tracking。

---

## 第十部分：权限与安全设计

### 10.1 安全边界全景

银行场景，安全是**第一优先级**。

```
┌─────────────────────────────────────────────────────────┐
│                   AI Safety Layer                        │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │ [1] Input Guard Rails（输入安全）                   │  │
│  │   - 敏感信息过滤（卡号、身份证、密码）                 │  │
│  │   - Prompt Injection 检测                          │  │
│  │   - 意图边界检查（只处理业务相关请求）                 │  │
│  │   - 恶意输入检测                                   │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │ [2] Authorization（授权控制）                       │  │
│  │   - 基于角色的 Tool 权限（RBAC）                    │  │
│  │   - 数据行级权限（只能查自己的积分）                  │  │
│  │   - 写操作二次确认（Human-in-the-Loop）              │  │
│  │   - RAG 检索权限过滤                                │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │ [3] Output Guard Rails（输出安全）                  │  │
│  │   - 敏感信息脱敏（回答中不能包含他人隐私）             │  │
│  │   - 合规检查（不输出违规内容）                        │  │
│  │   - 幻觉检测（关键业务回答需要置信度评估）             │  │
│  │   - 格式化校验（Structured Output 是否符合 Schema）   │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │ [4] Audit Trail（审计追溯）                         │  │
│  │   - 每次对话完整记录                                │  │
│  │   - 每次 Tool 调用记录（参数 + 结果）                │  │
│  │   - 每次 LLM 推理记录（Prompt + Response + Token）   │  │
│  │   - 异常事件记录（安全拦截、权限拒绝）                │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 10.2 UserContext — 权限上下文的起点

每次请求进入 AI Runtime 时，必须携带权限上下文：

```
UserContext {
  userId          // 用户 ID
  employeeNo      // 员工号
  department      // 部门
  roles           // 角色列表
  permissions     // 权限列表
  dataScopes      // 数据范围
  locale          // 语言偏好
  tenantId        // 租户 ID
}
```

**关键原则**：
- 模型可以看到低敏信息（角色、部门、可用能力）
- 真正的权限判断**必须由系统代码执行**，不能交给模型
- Tool 执行时强制使用 UserContext 中的身份信息，忽略 LLM 传入的身份参数

### 10.3 Tool 权限控制示例

```
用户问："帮我查一下张三的积分余额"

→ LLM 请求调用 point.balance.query { employeeNo: "张三的工号" }
→ Tool Runtime 检查：
   1. 用户是否有 point:balance:read 权限 → ✅
   2. 但 employeeNo 强制使用当前登录用户
   3. 不允许查询他人积分
→ Tool 返回自己的积分
→ LLM 回答："这是您的积分余额...（注：您只能查询自己的积分）"
```

### 10.4 审计设计

需要审计的事件（不是普通 debug log）：

- 用户消息
- 模型响应
- Prompt 版本
- 模型名称和版本
- Token 消耗（input + output）
- Tool Call 请求（toolName + 参数）
- Tool 执行结果
- RAG 命中的 Chunk
- 用户确认动作
- Workflow 状态流转
- 安全拦截事件

审计日志要支持：
- **问题追溯**：AI 答错了，能查到当时的完整上下文
- **合规检查**：银行监管要求的所有操作可追溯
- **成本分析**：Token 消耗统计和成本归因
- **质量评估**：Tool 调用准确率、回答满意度
- **安全事件调查**：Prompt Injection 尝试、越权调用尝试

---

## 第十一部分：AI Observability 设计

### 11.1 为什么 AI 需要独立的可观测性

传统服务关注：QPS、latency、error rate、DB 慢查询、JVM 指标。

AI 应用**额外**关注：

| 指标 | 说明 |
|------|------|
| Prompt 版本 | 当前用的哪个版本的 Prompt |
| Input Token | 输入消耗了多少 Token |
| Output Token | 输出消耗了多少 Token |
| Total Cost | 本次调用花了多少钱 |
| Model Latency | 模型响应延迟 |
| Tool Call Count | 调用了多少次 Tool |
| Tool Failure Rate | Tool 失败率 |
| RAG Hit Rate | RAG 命中率 |
| Agent Loop Count | Agent 循环了多少次 |
| User Confirm Rate | 用户确认率 |
| Safety Block Rate | 安全拦截率 |

### 11.2 Trace 结构

一次请求形成完整 trace：

```
ConversationTrace
├── TurnTrace（每一轮对话）
│   ├── PromptBuildTrace（Prompt 构造过程）
│   │   ├── Context Load Time
│   │   ├── Prompt Version
│   │   └── Token Count
│   ├── ModelCallTrace（模型调用）
│   │   ├── Model Name
│   │   ├── Input Tokens
│   │   ├── Output Tokens
│   │   ├── Latency
│   │   └── Finish Reason
│   ├── ToolCallTrace（Tool 调用）
│   │   ├── Tool Name
│   │   ├── Parameters
│   │   ├── Result
│   │   ├── Latency
│   │   └── Permission Check Result
│   ├── RetrievalTrace（RAG 检索）
│   │   ├── Query
│   │   ├── Hit Chunks
│   │   └── Rerank Score
│   ├── WorkflowTrace（Workflow 状态流转）
│   │   ├── From State
│   │   ├── To State
│   │   └── Trigger
│   ├── SafetyTrace（安全事件）
│   │   ├── Input Check Result
│   │   └── Output Check Result
│   └── StreamingTrace（流式输出）
│       ├── First Token Latency
│       └── Total Streaming Time
```

**当用户说"AI 答错了"时，我们能回答：**
- 当时用户问了什么
- 系统构造了什么 Prompt（用了哪个版本）
- 用了哪个模型
- 检索到了哪些文档
- 调用了哪些 Tool，Tool 返回了什么
- 模型最终为什么这样回答

---

## 第十二部分：工程模块与包结构

### 12.1 推荐工程结构

```
bank-ai-assistant/
├── api/                        # API 接入层
│   ├── controller/             # REST Controller
│   ├── dto/                    # 请求/响应 DTO
│   ├── sse/                    # SSE Streaming 推送
│   └── filter/                 # 认证、限流、trace 过滤器
│
├── application/                # 应用层（用例编排）
│   ├── conversation/           # 会话管理
│   └── session/                # 会话上下文
│
├── airuntime/                  # AI Runtime 核心引擎
│   ├── agent/                  # Agent 编排（ReAct、Tool Calling）
│   ├── prompt/                 # Prompt 模板管理
│   ├── context/                # Context 构建器
│   ├── memory/                 # 对话记忆管理
│   ├── model/                  # Model Gateway、Router、Fallback
│   ├── streaming/              # Streaming 事件处理
│   └── guardrails/             # AI 安全护栏
│
├── toolruntime/                # Tool Runtime
│   ├── registry/               # Tool 注册中心
│   ├── policy/                 # Tool 权限、风险、确认策略
│   ├── dispatcher/             # Tool 调度器
│   ├── validator/              # 参数校验器
│   ├── executor/               # Tool 执行器
│   ├── adapter/                # 业务系统适配器
│   └── tools/                  # 具体 Tool 实现
│       ├── points/             # 积分相关 Tool
│       └── leave/              # 假期相关 Tool
│
├── rag/                        # RAG 引擎
│   ├── ingestion/              # 文档入库
│   ├── chunking/               # 文档切片
│   ├── embedding/              # 向量化
│   ├── retrieval/              # 检索 & Rerank
│   └── store/                  # 向量存储
│
├── workflow/                   # Workflow 编排
│   ├── engine/                 # Workflow 引擎
│   ├── state/                  # 状态管理
│   └── flows/                  # 具体流程定义
│
├── security/                   # 权限与安全
│   ├── auth/                   # 认证
│   ├── permission/             # 权限
│   ├── audit/                  # 审计
│   └── safety/                 # AI 安全
│
├── observability/              # 可观测性
│   ├── trace/                  # Trace
│   ├── metrics/                # Metrics
│   └── audit/                  # 审计日志
│
├── domain/                     # 业务领域模型
│   ├── points/                 # 积分领域
│   ├── leave/                  # 假期领域
│   └── user/                   # 用户领域
│
└── infrastructure/             # 基础设施
    ├── persistence/            # 数据库
    ├── cache/                  # Redis
    └── external/               # 外部系统适配
```

### 12.2 阶段实施建议

**当前阶段**：先使用单体 Spring Boot 工程 + 清晰的 package 分层。

原因：
- 学习和迭代成本低
- 方便快速跑通 AI 闭环
- 仍然保留未来拆模块的边界

等到 Tool、RAG、Workflow 稳定后，再考虑 Maven 多模块拆分。

---

## 第十三部分：技术权衡总结

| 设计点 | 选择 | 原因 | 风险 | 演进方向 |
|--------|------|------|------|----------|
| Agent 框架 | Spring AI + LangChain4j | Java 生态最成熟 | LangChain4j 迭代快，API 可能变 | 后期引入 LangGraph4j |
| 向量库 | pgvector | 与 PG 同库，运维简单 | 超大规模性能不足 | 可迁移 Milvus |
| 短期记忆 | Redis | 热数据快，TTL 自动过期 | Redis 宕机丢失会话 | Redis Sentinel/Cluster |
| 长期存储 | PostgreSQL | 事务、权限、持久化 | — | — |
| 流式输出 | SSE | 简单、HTTP 友好 | 单向通信 | 可加 WebSocket |
| 工具权限 | RBAC + Tool 级策略注解 | 银行合规要求 | 权限模型复杂度 | 细粒度 ABAC |
| Prompt 管理 | 文件模板 + Prompt Manager | 初期简单 | 缺少版本管理平台 | Prompt Registry |
| 模型接入 | Spring AI ChatClient | 初期简单 | 无 Fallback | 完整 Model Gateway |

---

## 第十四部分：开发路线图

| 阶段 | 内容 | 核心能力 | 状态 |
|------|------|----------|------|
| **第一阶段** | 项目整体架构设计 | 系统蓝图、模块划分、设计原则 | ✅ 当前 |
| **第二阶段** | 最小 AI Agent 闭环 | Tool Calling + Prompt + Streaming + Context | ⏳ 下一步 |
| **第三阶段** | RAG 能力 | 文档切片 + Embedding + 向量检索 + 引用 | 🔜 待开始 |
| **第四阶段** | AI Runtime 升级 | LangGraph4j + StateGraph + Checkpoint | 🔜 待开始 |
| **第五阶段** | 企业级增强 | Observability + 安全 + Token 优化 + 审计 | 🔜 待开始 |
| **第六阶段** | Multi-Agent | Planner + HR + Knowledge + Workflow Agent | 🔜 待开始 |

### 第二阶段预览（下一步要做的）

跑通最小闭环：

```
用户提问："帮我查积分余额"
 → AI Runtime 构造 Prompt（System + Tools + Memory + User Message）
 → 模型选择 point.balance.query Tool
 → Tool Runtime 校验权限和参数
 → 模拟积分系统返回结构化结果
 → 模型生成自然语言回复
 → SSE 流式返回
 → 记录会话和 Tool 审计
```

优先功能：
1. 查询积分余额（验证 Tool Calling 最小闭环）
2. 查询积分历史（验证列表型结构化返回）
3. 假期申请（验证缺槽追问、参数校验、用户确认）

---

## 本阶段结论

本项目的企业级架构核心是：

```
以 Conversation 为入口
以 AI Runtime 为核心
以 Tool Runtime 管控业务动作
以 RAG Runtime 管控知识注入
以 Workflow Runtime 管控状态流程
以 Prompt Manager 管控模型行为
以 Security 和 Observability 贯穿全链路
```

AI 应用的复杂度不在"怎么调用模型"，而在：

- 怎么管理 **State**（对话状态、Agent 状态、Workflow 状态）
- 怎么控制 **Context**（Token 有限，Context 需要精心构造）
- 怎么约束 **Tool**（LLM 生成的参数不可信，Tool 必须受控执行）
- 怎么注入 **Knowledge**（权限过滤、Token 控制、引用溯源）
- 怎么治理 **Prompt**（版本管理、效果评估、安全防护）
- 怎么保障 **权限**（系统代码判断，不交给模型）
- 怎么审计 **模型行为**（全链路 Trace，可追溯可合规）
- 怎么让系统 **可持续演进**（从 Tool Calling 到 Multi-Agent）
