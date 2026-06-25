package com.bank.assistant.tool.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool Executor - 工具执行器
 * 
 * 职责：
 * 1. 接收工具调用请求
 * 2. 从 Registry 查找工具
 * 3. 执行权限检查（如果需要）
 * 4. 执行参数校验（如果需要）
 * 5. 调用工具并返回结果
 * 6. 处理超时和重试
 * 
 * 为什么需要 Executor？
 * - 统一工具调用入口，便于监控和审计
 * - 集中处理横切关注点（权限、校验、超时、重试）
 * - 解耦 AI 层和具体工具实现
 * 
 * @author Bank AI Assistant Team
 * @since 2026-06-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {
    
    private final ToolRegistry toolRegistry;
    
    /**
     * 执行工具
     * 
     * @param toolName   工具名称
     * @param parameters 参数 Map
     * @param context    执行上下文
     * @return 执行结果
     */
    public ToolExecutionResult execute(String toolName, Map<String, Object> parameters, 
                                       Tool.ExecutionContext context) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: 查找工具
            log.debug("Looking up tool: {}", toolName);
            Tool tool = toolRegistry.getTool(toolName);
            
            if (tool == null) {
                log.error("Tool not found: {}", toolName);
                return ToolExecutionResult.failure(
                    "Tool '" + toolName + "' not found", 
                    "TOOL_NOT_FOUND"
                );
            }
            
            ToolDefinition definition = tool.getDefinition();
            
            // Step 2: 权限检查（如果需要）
            if (Boolean.TRUE.equals(definition.getRequiresAuth())) {
                log.debug("Performing auth check for tool: {}", toolName);
                // TODO: 后续实现权限检查逻辑
                // if (!permissionChecker.check(context.getUserId(), toolName)) {
                //     return ToolExecutionResult.failure("Permission denied", "PERMISSION_DENIED");
                // }
            }
            
            // Step 3: 参数校验（如果有参数定义）
            if (definition.getParameters() != null && !definition.getParameters().isEmpty()) {
                log.debug("Validating parameters for tool: {}", toolName);
                // TODO: 后续实现参数校验逻辑
                // ToolExecutionResult validationResult = parameterValidator.validate(parameters, definition.getParameters());
                // if (!validationResult.getSuccess()) {
                //     return validationResult;
                // }
            }
            
            // Step 4: 执行工具（带超时和重试）
            log.debug("Executing tool: {} with parameters: {}", toolName, parameters);
            ToolExecutionResult result = executeWithRetry(tool, parameters, context, definition);
            
            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(executionTime);
            
            log.info("Tool execution completed: {} - success={}, time={}ms", 
                     toolName, result.getSuccess(), executionTime);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Tool execution failed: {} - error={}", toolName, e.getMessage(), e);
            
            return ToolExecutionResult.builder()
                    .success(false)
                    .error("Internal error: " + e.getMessage())
                    .errorCode("INTERNAL_ERROR")
                    .executionTimeMs(executionTime)
                    .build();
        }
    }
    
    /**
     * 带重试的执行逻辑
     */
    private ToolExecutionResult executeWithRetry(Tool tool, Map<String, Object> parameters,
                                                  Tool.ExecutionContext context, 
                                                  ToolDefinition definition) {
        
        int maxRetries = definition.getRetryCount() != null ? definition.getRetryCount() : 2;
        int timeoutMs = definition.getTimeoutMs() != null ? definition.getTimeoutMs() : 3000;
        
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                // TODO: 后续实现超时控制
                // 可以使用 CompletableFuture 或 ExecutorService 实现超时
                
                ToolExecutionResult result = tool.execute(parameters, context);
                
                // 如果成功，直接返回
                if (Boolean.TRUE.equals(result.getSuccess())) {
                    result.setRetryCount(attempt);
                    return result;
                }
                
                // 如果失败且还有重试机会，记录日志并重试
                log.warn("Tool execution failed (attempt {}/{}): {} - {}", 
                        attempt + 1, maxRetries + 1, 
                        tool.getDefinition().getName(), 
                        result.getError());
                
                lastException = new RuntimeException(result.getError());
                
            } catch (Exception e) {
                lastException = e;
                log.warn("Tool execution exception (attempt {}/{}): {} - {}", 
                        attempt + 1, maxRetries + 1, 
                        tool.getDefinition().getName(), 
                        e.getMessage());
            }
            
            // 如果不是最后一次尝试，等待一段时间再重试
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(500 * (attempt + 1)); // 递增等待时间
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return ToolExecutionResult.failure("Interrupted during retry", "INTERRUPTED");
                }
            }
        }
        
        // 所有重试都失败了
        return ToolExecutionResult.failure(
            "Tool execution failed after " + (maxRetries + 1) + " attempts: " + lastException.getMessage(),
            "EXECUTION_FAILED"
        );
    }
}
