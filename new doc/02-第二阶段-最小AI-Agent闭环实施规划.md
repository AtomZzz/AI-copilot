# 第二阶段：最小 AI Agent 闭环 — 实施规划

> 阶段：第二阶段  
> 主题：从 0 到 1 跑通最小 AI Agent 闭环  
> 目标：用户 → Agent → Tool → 结构化结果 → Streaming 返回  
> 日期：2026-06-24

---

## 一、本阶段目标

### 核心目标

实现一个**完整的最小 AI Agent 闭环**，验证以下能力：

```
用户提问："帮我查一下我的积分余额"
 ↓
API 层接收请求 + 构建 UserContext
 ↓
AI Runtime 构造 Prompt（System + Tools + Memory + User Message）
 ↓
LLM 推理并选择 Tool（point.balance.query）
 ↓
Tool Runtime 校验权限和参数
 ↓
执行 Tool → 返回结构化结果 { balance: 1250 }
 ↓
LLM 生成自然语言回答
 ↓
SSE Streaming 推送给前端
 ↓
记录会话和 Tool 审计日志
```

### 优先功能（按顺序实现）

1. ✅ **查询积分余额**（`point.balance.query`）
   - 验证 Tool Calling 最小闭环
   - 验证 Structured Output
   - 验证 Streaming 输出

2. 🔜 **查询积分历史**（`point.history.query`）
   - 验证列表型结构化返回
   - 验证复杂参数的 Tool Calling

3. 🔜 **假期申请**（`leave.application.submit`）
   - 验证缺槽追问（Slot Filling）
   - 验证参数校验
   - 验证 Human-in-the-Loop（用户确认）

### 为什么先做这三个？

| 功能 | 验证的能力 | 复杂度 |
|------|-----------|--------|
| 查积分余额 | Tool Calling 基础闭环 | ⭐ 低 |
| 查积分历史 | 列表型结构化返回 | ⭐⭐ 中 |
| 假期申请 | 缺槽追问 + 用户确认 | ⭐⭐⭐ 高 |

这种渐进式实现确保：
- 每一步都能独立验证
- 问题容易定位
- 不会过早陷入复杂业务逻辑

---

## 二、技术选型确认

### LLM 选择

**主模型：阿里云百炼 - 通义千问 Qwen-Plus**

| 维度 | 选择 | 理由 |
|------|------|------|
| 模型名称 | `qwen-plus` | 性能与成本平衡，Tool Calling 能力足够 |
| API Key | `sk-892bf6a65ad244f49126a27a9421ccf8` | 已提供 |
| 备选模型 | `qwen-max` | 如果 qwen-plus 效果不佳可升级 |
| 其他可用 | `qwen3.7-plus`, `glm-5` 等 | 后续可根据需要切换 |

**为什么选择 qwen-plus？**
- Tool Calling 准确率在中文场景下表现良好
- 免费额度充足（1,000,000 Token）
- 延迟较低，适合频繁调试
- 如果后续发现不足，可以无缝切换到 qwen-max

### 框架组合

| 功能模块 | 框架选择 | 原因 |
|----------|----------|------|
| 基础 Chat / Tool Calling | **Spring AI** | Spring 生态集成好，配置简单 |
| Agent 编排 | **LangChain4j** | Agent API 更丰富 |
| RAG（第三阶段） | **LangChain4j** | Retriever 工具链完善 |
| Workflow（第四阶段） | **LangGraph4j** | 状态图引擎 |

### 数据库配置

```yaml
# PostgreSQL
spring.datasource.url=jdbc:postgresql://192.168.179.128:5432/bank_ai_assistant
spring.datasource.username=root
spring.datasource.password=123456

# Redis
spring.data.redis.host=192.168.179.128
spring.data.redis.port=6379
# 无密码
```

### API Key 管理

采用 **.env 文件 + Spring Boot 加载** 方式：

```bash
# .env 文件（不提交到 Git）
DASHSCOPE_API_KEY=sk-892bf6a65ad244f49126a27a9421ccf8
```

```yaml
# application.yml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
```

---

## 三、项目结构预览

```
C:\atom\data\IdeaProjects\AI-copilot\bank-ai-assistant/
├── pom.xml                           # Maven 依赖配置
├── .env                              # 环境变量（不提交 Git）
├── .gitignore                        # Git 忽略配置
└── src/main/java/com/bank/assistant/
    ├── AssistantApplication.java     # Spring Boot 启动类
    │
    ├── api/                          # API 接入层
    │   ├── controller/
    │   │   └── ChatController.java   # REST + SSE 接口
    │   ├── dto/
    │   │   ├── ChatRequest.java      # 请求 DTO
    │   │   └── ChatResponse.java     # 响应 DTO
    │   └── sse/
    │       └── SseEmitterFactory.java # SSE 事件工厂
    │
    ├── application/                  # 应用层
    │   └── conversation/
    │       ├── ConversationService.java    # 会话管理服务
    │       └── SessionContext.java         # 会话上下文
    │
    ├── airuntime/                    # AI Runtime 核心引擎
    │   ├── agent/
    │   │   ├── AgentOrchestrator.java      # Agent 编排器
    │   │   └── ReActAgent.java             # ReAct Agent 实现
    │   ├── prompt/
    │   │   ├── PromptManager.java          # Prompt 管理器
    │   │   └── templates/                  # Prompt 模板
    │   │       └── system-prompt.txt
    │   ├── context/
    │   │   └── ContextBuilder.java         # Context 构建器
    │   └── streaming/
    │       └── StreamingHandler.java       # Streaming 事件处理
    │
    ├── toolruntime/                  # Tool Runtime
    │   ├── registry/
    │   │   ├── ToolRegistry.java           # Tool 注册中心
    │   │   └── ToolDefinition.java         # Tool 定义
    │   ├── policy/
    │   │   ├── ToolPolicy.java             # Tool 策略
    │   │   └── PermissionChecker.java      # 权限检查器
    │   ├── dispatcher/
    │   │   └── ToolDispatcher.java         # Tool 调度器
    │   ├── validator/
    │   │   ── ParameterValidator.java     # 参数校验器
    │   ├── executor/
    │   │   └── ToolExecutor.java           # Tool 执行器
    │   ── tools/
    │       ├── points/
    │       │   ├── PointBalanceTool.java   # 查询积分余额
    │       │   └── PointHistoryTool.java   # 查询积分历史（后续）
    │       └── leave/
    │           └── LeaveSubmitTool.java    # 提交假期申请（后续）
    │
    ├── domain/                       # 业务领域模型
    │   ├── points/
    │   │   ├── PointBalance.java
    │   │   └── PointRecord.java
    │   └── user/
    │       └── UserContext.java
    │
    └── infrastructure/               # 基础设施
        ├── persistence/              # 数据库
        │   └── ConversationRepository.java
        └── cache/                    # Redis
            └── MemoryRepository.java
```

---

## 四、实施步骤详解

### Step 1: 项目初始化（Spring Initializr）

**操作方式：**
1. IDEA U → File → New → Project
2. 选择 "Spring Initializr"
3. 配置：
   - Project: Maven
   - Language: Java
   - Packaging: Jar
   - Java Version: 21
   - Spring Boot: 3.3.x
   - Group: com.bank
   - Artifact: assistant
   - Package name: com.bank.assistant

4. Dependencies（初始选择）：
   - Spring Web
   - Spring Data JPA
   - PostgreSQL Driver
   - Spring Data Redis
   - Lombok
   - Validation

5. 生成后，手动添加 AI 相关依赖（见 Step 2）

---

### Step 2: 依赖配置（pom.xml）

**需要添加的核心依赖：**

```xml
<!-- Spring AI BOM -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0-M4</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Spring AI Core + Alibaba DashScope -->
<dependencies>
    <!-- Spring AI Chat Model -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-chat</artifactId>
    </dependency>
    
    <!-- Alibaba DashScope (通义千问) -->
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-ai-alibaba-starter</artifactId>
    </dependency>
    
    <!-- LangChain4j Core -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>0.35.0</version>
    </dependency>
    
    <!-- LangChain4j DashScope Adapter -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-dashscope</artifactId>
        <version>0.35.0</version>
    </dependency>
    
    <!-- Jackson for JSON processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

---

### Step 3: 环境配置（application.yml + .env）

**.env 文件：**
```bash
DASHSCOPE_API_KEY=sk-892bf6a65ad244f49126a27a9421ccf8
```

**application.yml：**
```yaml
server:
  port: 8080

spring:
  application:
    name: bank-ai-assistant
  
  # PostgreSQL
  datasource:
    url: jdbc:postgresql://192.168.179.128:5432/bank_ai_assistant
    username: root
    password: 123456
    driver-class-name: org.postgresql.Driver
  
  # JPA
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  
  # Redis
  data:
    redis:
      host: 192.168.179.128
      port: 6379
  
  # Spring AI - DashScope
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-plus
          temperature: 0.7
          max-tokens: 2000

# 自定义配置
assistant:
  ai:
    system-prompt: classpath:prompts/system-prompt.txt
    memory:
      short-term-ttl: 3600  # 短期记忆 TTL（秒）
      max-history-turns: 10  # 最大历史轮数
  tool:
    timeout-ms: 3000  # Tool 执行超时
    retry-count: 2    # Tool 失败重试次数
```

---

### Step 4: 基础设施层（PostgreSQL + Redis）

**需要创建的表：**

```sql
-- 会话表
CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 消息表
CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id UUID REFERENCES conversations(id),
    role VARCHAR(20) NOT NULL,  -- 'user' or 'assistant'
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tool 调用审计表
CREATE TABLE tool_calls (
    id UUID PRIMARY KEY,
    conversation_id UUID REFERENCES conversations(id),
    tool_name VARCHAR(100) NOT NULL,
    parameters JSONB,
    result JSONB,
    status VARCHAR(20) NOT NULL,  -- 'success' or 'failed'
    latency_ms INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Redis Key 设计：**

```
memory:short:{conversationId} → List<Message> (TTL: 3600s)
session:{conversationId} → SessionContext
```

---

### Step 5: Tool Runtime 基础框架

#### 5.1 ToolDefinition（工具定义）

```java
@Data
@Builder
public class ToolDefinition {
    private String name;           // 工具名称
    private String description;    // 工具描述（给模型看）
    private Class<?> inputSchema;  // 输入参数 Schema
    private Class<?> outputSchema; // 输出结果 Schema
    private ToolCategory category; // 工具分类
    private RiskLevel riskLevel;   // 风险等级
    private boolean requiresConfirmation; // 是否需要用户确认
}

public enum ToolCategory {
    POINTS, LEAVE, TIMESHEET, KNOWLEDGE
}

public enum RiskLevel {
    LOW, MEDIUM, HIGH
}
```

#### 5.2 ToolPolicy（工具策略）

```java
@Data
@Builder
public class ToolPolicy {
    private String toolName;
    private Set<String> requiredPermissions;  // 所需权限
    private int timeoutMs;                     // 超时时间
    private int retryCount;                    // 重试次数
    private ConfirmationStrategy confirmation; // 确认策略
}

public enum ConfirmationStrategy {
    NEVER,      // 不需要确认（读操作）
    ALWAYS,     // 总是需要确认（写操作）
    CONDITIONAL // 条件确认
}
```

#### 5.3 ToolRegistry（工具注册中心）

```java
@Component
public class ToolRegistry {
    private final Map<String, ToolDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, ToolPolicy> policies = new ConcurrentHashMap<>();
    
    public void register(ToolDefinition definition, ToolPolicy policy) {
        definitions.put(definition.getName(), definition);
        policies.put(policy.getToolName(), policy);
    }
    
    public ToolDefinition getDefinition(String toolName) {
        return definitions.get(toolName);
    }
    
    public ToolPolicy getPolicy(String toolName) {
        return policies.get(toolName);
    }
    
    public List<ToolDefinition> getAllDefinitions() {
        return new ArrayList<>(definitions.values());
    }
}
```

#### 5.4 ToolDispatcher（工具调度器）

```java
@Component
@Slf4j
public class ToolDispatcher {
    private final ToolRegistry toolRegistry;
    private final PermissionChecker permissionChecker;
    private final ParameterValidator parameterValidator;
    private final ToolExecutor toolExecutor;
    
    public ToolResult dispatch(String toolName, Object parameters, UserContext userContext) {
        // 1. 查找工具定义
        ToolDefinition definition = toolRegistry.getDefinition(toolName);
        if (definition == null) {
            throw new ToolNotFoundException("Tool not found: " + toolName);
        }
        
        // 2. 权限检查
        ToolPolicy policy = toolRegistry.getPolicy(toolName);
        permissionChecker.check(userContext, policy);
        
        // 3. 参数校验
        parameterValidator.validate(definition.getInputSchema(), parameters);
        
        // 4. 确认检查（写操作）
        if (policy.getConfirmation() == ConfirmationStrategy.ALWAYS) {
            // 返回需要确认的信号
            return ToolResult.requiresConfirmation(definition, parameters);
        }
        
        // 5. 执行工具
        return toolExecutor.execute(definition, parameters, userContext);
    }
}
```

---

### Step 6: 第一个 Tool 实现 — point.balance.query

#### 6.1 输入参数

```java
@Data
@Builder
public class PointBalanceQueryInput {
    // 注意：实际执行时会强制使用 UserContext.employeeNo
    // 这里只是 Schema 定义，让模型知道需要传什么
    @JsonPropertyDescription("员工工号")
    private String employeeNo;
}
```

#### 6.2 输出结果

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointBalanceOutput {
    private Integer balance;           // 当前余额
    private LocalDateTime lastUpdated; // 最后更新时间
    private String currency;           // 货币单位（points）
}
```

#### 6.3 Tool 实现

```java
@Component
@Slf4j
public class PointBalanceTool implements ToolExecutor {
    
    @Override
    public ToolResult execute(Object input, UserContext userContext) {
        log.info("Executing point.balance.query for employee: {}", userContext.getEmployeeNo());
        
        // 模拟调用积分系统（后续替换为真实 API）
        PointBalanceOutput result = PointBalanceOutput.builder()
            .balance(1250)
            .lastUpdated(LocalDateTime.now())
            .currency("points")
            .build();
        
        return ToolResult.success(result);
    }
}
```

#### 6.4 注册 Tool

```java
@Configuration
public class ToolConfiguration {
    
    @Bean
    public CommandLineRunner initTools(ToolRegistry toolRegistry, 
                                       PointBalanceTool pointBalanceTool) {
        return args -> {
            // 注册 point.balance.query
            ToolDefinition balanceDef = ToolDefinition.builder()
                .name("point.balance.query")
                .description("查询员工积分余额。返回当前余额和最后更新时间。")
                .inputSchema(PointBalanceQueryInput.class)
                .outputSchema(PointBalanceOutput.class)
                .category(ToolCategory.POINTS)
                .riskLevel(RiskLevel.LOW)
                .requiresConfirmation(false)
                .build();
            
            ToolPolicy balancePolicy = ToolPolicy.builder()
                .toolName("point.balance.query")
                .requiredPermissions(Set.of("points:balance:read"))
                .timeoutMs(3000)
                .retryCount(2)
                .confirmation(ConfirmationStrategy.NEVER)
                .build();
            
            toolRegistry.register(balanceDef, balancePolicy);
            
            log.info("Registered tool: point.balance.query");
        };
    }
}
```

---

### Step 7: AI Runtime 基础框架

#### 7.1 PromptManager（Prompt 管理器）

```java
@Component
@Slf4j
public class PromptManager {
    
    @Value("${assistant.ai.system-prompt}")
    private Resource systemPromptResource;
    
    public String loadSystemPrompt() throws IOException {
        return new String(systemPromptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
    
    public String buildFullPrompt(SystemPromptContext context) {
        StringBuilder prompt = new StringBuilder();
        
        // Layer 1: System Prompt
        prompt.append(context.getSystemPrompt()).append("\n\n");
        
        // Layer 2: Role Context
        prompt.append("当前用户信息：\n")
              .append("- 员工工号: ").append(context.getUserContext().getEmployeeNo()).append("\n")
              .append("- 部门: ").append(context.getUserContext().getDepartment()).append("\n\n");
        
        // Layer 3: Tool Definitions
        prompt.append("可用工具列表：\n");
        for (ToolDefinition tool : context.getAvailableTools()) {
            prompt.append("- ").append(tool.getName()).append(": ")
                  .append(tool.getDescription()).append("\n");
        }
        prompt.append("\n");
        
        // Layer 4: Memory Context
        if (!context.getMemory().isEmpty()) {
            prompt.append("最近对话历史：\n");
            for (Message msg : context.getMemory()) {
                prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            prompt.append("\n");
        }
        
        // Layer 5: User Message
        prompt.append("用户问题：").append(context.getUserMessage());
        
        return prompt.toString();
    }
}
```

#### 7.2 ContextBuilder（Context 构建器）

```java
@Component
public class ContextBuilder {
    
    private final MemoryRepository memoryRepository;
    private final ToolRegistry toolRegistry;
    
    public SystemPromptContext buildContext(ChatRequest request, UserContext userContext) {
        // 1. 加载系统 Prompt
        String systemPrompt = promptManager.loadSystemPrompt();
        
        // 2. 加载短期记忆
        List<Message> memory = memoryRepository.getShortTermMemory(request.getConversationId());
        
        // 3. 获取可用工具（根据权限过滤）
        List<ToolDefinition> availableTools = filterToolsByPermission(
            toolRegistry.getAllDefinitions(), 
            userContext.getPermissions()
        );
        
        return SystemPromptContext.builder()
            .systemPrompt(systemPrompt)
            .userContext(userContext)
            .memory(memory)
            .availableTools(availableTools)
            .userMessage(request.getMessage())
            .build();
    }
}
```

#### 7.3 AgentOrchestrator（Agent 编排器）

```java
@Component
@Slf4j
public class AgentOrchestrator {
    
    private final ChatClient chatClient;
    private final ContextBuilder contextBuilder;
    private final ToolDispatcher toolDispatcher;
    private final PromptManager promptManager;
    
    public Flux<ChatEvent> orchestrate(ChatRequest request, UserContext userContext) {
        return Flux.create(sink -> {
            try {
                // 1. 构建 Context
                SystemPromptContext context = contextBuilder.buildContext(request, userContext);
                
                // 2. 构造完整 Prompt
                String fullPrompt = promptManager.buildFullPrompt(context);
                
                // 3. 调用 LLM（Streaming）
                chatClient.prompt(fullPrompt)
                    .stream()
                    .chatResponse()
                    .doOnNext(response -> {
                        // 4. 检查是否有 Tool Call
                        if (hasToolCall(response)) {
                            // 5. 执行 Tool
                            ToolResult toolResult = executeToolCall(response, userContext);
                            
                            // 6. 将 Tool Result 注入 Context，再次调用 LLM
                            String followUpPrompt = buildFollowUpPrompt(context, toolResult);
                            chatClient.prompt(followUpPrompt)
                                .stream()
                                .content()
                                .doOnNext(content -> sink.next(ChatEvent.message(content)))
                                .doOnComplete(() -> sink.complete())
                                .subscribe();
                        } else {
                            // 7. 直接返回文本
                            String content = response.getResult().getOutput().getText();
                            sink.next(ChatEvent.message(content));
                            sink.complete();
                        }
                    })
                    .doOnError(error -> {
                        log.error("Agent orchestration failed", error);
                        sink.error(error);
                    })
                    .subscribe();
                    
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }
}
```

---

### Step 8: API 层（REST + SSE）

#### 8.1 ChatController

```java
@RestController
@RequestMapping("/api/v1/chat")
@Slf4j
public class ChatController {
    
    private final ConversationService conversationService;
    
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, 
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatResponse>> chat(
            @RequestBody ChatRequest request,
            @AuthenticationPrincipal UserContext userContext) {
        
        log.info("Received chat request from user: {}", userContext.getEmployeeNo());
        
        return conversationService.process(request, userContext)
            .map(event -> ServerSentEvent.<ChatResponse>builder()
                .event(event.getType())
                .data(event.getData())
                .build());
    }
}
```

#### 8.2 ConversationService

```java
@Service
@Slf4j
public class ConversationService {
    
    private final AgentOrchestrator agentOrchestrator;
    private final ConversationRepository conversationRepository;
    
    public Flux<ChatEvent> process(ChatRequest request, UserContext userContext) {
        // 1. 保存用户消息
        saveUserMessage(request, userContext);
        
        // 2. 调用 Agent
        return agentOrchestrator.orchestrate(request, userContext)
            .doOnNext(event -> {
                // 3. 记录事件
                logEvent(event, userContext);
            })
            .doOnComplete(() -> {
                // 4. 更新会话
                updateConversation(request.getConversationId());
            });
    }
}
```

---

## 五、测试验证计划

### 测试用例 1：查询积分余额

**请求：**
```http
POST /api/v1/chat
Content-Type: application/json

{
  "conversationId": "conv-001",
  "message": "帮我查一下我的积分余额"
}
```

**期望响应（SSE Stream）：**
```
event: message
data: {"type": "thinking", "content": "正在查询您的积分余额..."}

event: message
data: {"type": "tool_call", "tool": "point.balance.query", "status": "executing"}

event: message
data: {"type": "tool_result", "tool": "point.balance.query", "result": {"balance": 1250}}

event: message
data: {"type": "message", "content": "您的积分余额为 1250 分，最后更新时间为 2026-06-24 10:30:00。"}

event: done
data: {"conversationId": "conv-001", "messageId": "msg-001"}
```

### 测试用例 2：权限拒绝

**场景：** 用户没有 `points:balance:read` 权限

**期望响应：**
```
event: error
data: {"code": "PERMISSION_DENIED", "message": "您没有权限查询积分余额"}
```

### 测试用例 3：Tool 执行失败

**场景：** 积分系统超时或返回错误

**期望响应：**
```
event: error
data: {"code": "TOOL_EXECUTION_FAILED", "message": "查询积分余额失败，请稍后重试"}
```

---

## 六、风险与应对

### 风险 1：通义千问 Tool Calling 不准确

**现象：** 模型生成的 Tool Name 或 Parameters 格式错误

**应对：**
1. 优化 Tool Description（更清晰、更具体）
2. 使用 Structured Output 强制 JSON 格式
3. 在后端做参数补全和校验
4. 如果仍然不行，切换到 qwen-max

### 风险 2：Streaming 输出不稳定

**现象：** SSE 连接断开或数据不完整

**应对：**
1. 增加超时时间
2. 实现心跳机制
3. 前端实现重连逻辑
4. 记录完整 Trace 便于排查

### 风险 3：Token 消耗过快

**现象：** 免费额度快速用完

**应对：**
1. 限制 Memory 长度（Sliding Window）
2. 裁剪 RAG Context
3. 监控 Token 消耗（第五阶段实现）
4. 必要时切换到更轻量的模型

---

## 七、后续演进方向

完成最小闭环后，下一步可以：

1. **增加更多 Tool**
   - point.history.query（查询积分历史）
   - leave.application.submit（提交假期申请）
   - timesheet.fill（填写工时）

2. **引入 RAG**（第三阶段）
   - 企业知识库
   - Embedding + pgvector
   - 检索增强生成

3. **升级为 LangGraph4j**（第四阶段）
   - 显式状态机
   - Conditional Edge
   - Checkpoint 持久化

4. **企业级增强**（第五阶段）
   - AI Observability
   - Token 优化
   - Model Fallback
   - 审计日志

---

## 八、开始实施

现在，请回复 **"开始实施"**，我将带你逐步完成上述所有步骤的代码实现。

我们会按照以下顺序进行：
1. 项目初始化（Spring Initializr）
2. 依赖配置
3. 环境配置
4. 基础设施层
5. Tool Runtime 基础框架
6. 第一个 Tool 实现
7. AI Runtime 基础框架
8. API 层
9. 端到端测试

每一步我都会：
- 先解释设计原理
- 再展示代码实现
- 最后说明如何验证

准备好了吗？
