package com.bank.assistant.tool.runtime;

import java.util.Map;

/**
 * Tool Interface - 工具接口
 * 
 * 所有具体工具实现必须实现此接口。
 * 
 * 设计原则：
 * 1. 单一职责：每个工具只做一件事
 * 2. 无状态：工具本身不保存状态，状态由外部管理
 * 3. 可测试：易于单元测试和 Mock
 * 4. 可观测：执行过程可监控、可追踪
 * 
 * @author Bank AI Assistant Team
 * @since 2026-06-25
 */
public interface Tool {
    
    /**
     * 获取工具定义
     * 
     * @return 工具元数据
     */
    ToolDefinition getDefinition();
    
    /**
     * 执行工具
     * 
     * @param parameters 参数 Map（key: 参数名, value: 参数值）
     * @param context    执行上下文（包含用户信息、会话ID等）
     * @return 执行结果
     */
    ToolExecutionResult execute(Map<String, Object> parameters, ExecutionContext context);
    
    /**
     * 执行上下文
     * 封装工具执行时需要的上下文信息
     */
    interface ExecutionContext {
        
        /**
         * 获取当前用户 ID
         */
        String getUserId();
        
        /**
         * 获取当前用户角色
         */
        String getUserRole();
        
        /**
         * 获取会话 ID
         */
        String getSessionId();
        
        /**
         * 获取请求 ID（用于链路追踪）
         */
        String getRequestId();
        
        /**
         * 获取额外上下文数据
         */
        Object getAttribute(String key);
        
        /**
         * 设置额外上下文数据
         */
        void setAttribute(String key, Object value);
    }
}
