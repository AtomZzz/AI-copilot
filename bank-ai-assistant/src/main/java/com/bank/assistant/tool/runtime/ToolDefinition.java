package com.bank.assistant.tool.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tool Definition - 工具定义
 * 
 * 每个工具都需要明确定义其元数据，包括：
 * - name: 工具名称（唯一标识）
 * - description: 工具描述（AI 理解工具用途的关键）
 * - parameters: 参数定义（用于参数校验）
 * - requiresAuth: 是否需要权限检查
 * - isSafe: 是否为安全操作（只读操作为 true，写操作为 false）
 * 
 * @author Bank AI Assistant Team
 * @since 2026-06-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    
    /**
     * 工具名称（唯一标识）
     * 命名规范：{domain}.{action}.{resource}
     * 例如：point.balance.query, leave.apply.submit
     */
    private String name;
    
    /**
     * 工具描述
     * 这是 AI 理解工具用途的关键，需要清晰、简洁地描述：
     * - 工具的功能
     * - 适用场景
     * - 返回值含义
     */
    private String description;
    
    /**
     * 参数定义（JSON Schema 格式）
     * 用于运行时参数校验
     */
    private String parameters;
    
    /**
     * 是否需要权限检查
     * true: 执行前需要验证用户权限
     * false: 无需权限检查（如公开信息查询）
     */
    @Builder.Default
    private Boolean requiresAuth = true;
    
    /**
     * 是否为安全操作（只读）
     * true: 只读操作，可以直接执行
     * false: 写操作，需要二次确认
     */
    @Builder.Default
    private Boolean isSafe = true;
    
    /**
     * 超时时间（毫秒）
     * 默认 3000ms
     */
    @Builder.Default
    private Integer timeoutMs = 3000;
    
    /**
     * 重试次数
     * 默认 2 次
     */
    @Builder.Default
    private Integer retryCount = 2;
}
