package com.bank.assistant.tool.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tool Registry - 工具注册中心
 * 
 * 职责：
 * 1. 管理所有已注册的工具
 * 2. 提供工具查询能力
 * 3. 线程安全（使用 ConcurrentHashMap）
 * 
 * 为什么需要 Registry？
 * - AI 不能直接调用任意工具，必须通过注册中心验证
 * - 便于统一管理工具的元数据（名称、描述、参数等）
 * - 支持动态注册和注销工具（未来扩展）
 * 
 * @author Bank AI Assistant Team
 * @since 2026-06-25
 */
@Slf4j
@Component
public class ToolRegistry {
    
    /**
     * 工具存储：key=工具名称, value=工具实例
     * 使用 ConcurrentHashMap 保证线程安全
     */
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    
    /**
     * 注册工具
     * 
     * @param tool 工具实例
     * @throws IllegalArgumentException 如果工具名称已存在或为空
     */
    public void register(Tool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("Tool cannot be null");
        }
        
        ToolDefinition definition = tool.getDefinition();
        if (definition == null || definition.getName() == null || definition.getName().isEmpty()) {
            throw new IllegalArgumentException("Tool definition or name cannot be null/empty");
        }
        
        String toolName = definition.getName();
        
        // 检查是否已存在同名工具
        if (tools.containsKey(toolName)) {
            log.warn("Tool '{}' is already registered, it will be replaced", toolName);
        }
        
        tools.put(toolName, tool);
        log.info("Tool registered: {} - {}", toolName, definition.getDescription());
    }
    
    /**
     * 根据名称获取工具
     * 
     * @param toolName 工具名称
     * @return 工具实例，如果不存在返回 null
     */
    public Tool getTool(String toolName) {
        return tools.get(toolName);
    }
    
    /**
     * 检查工具是否存在
     * 
     * @param toolName 工具名称
     * @return true=存在, false=不存在
     */
    public boolean hasTool(String toolName) {
        return tools.containsKey(toolName);
    }
    
    /**
     * 获取所有已注册的工具名称
     * 
     * @return 工具名称列表
     */
    public java.util.Set<String> getAllToolNames() {
        return tools.keySet();
    }
    
    /**
     * 获取工具数量
     * 
     * @return 已注册工具数量
     */
    public int size() {
        return tools.size();
    }
    
    /**
     * 注销工具
     * 
     * @param toolName 工具名称
     * @return true=成功注销, false=工具不存在
     */
    public boolean unregister(String toolName) {
        Tool removed = tools.remove(toolName);
        if (removed != null) {
            log.info("Tool unregistered: {}", toolName);
            return true;
        }
        return false;
    }
}
