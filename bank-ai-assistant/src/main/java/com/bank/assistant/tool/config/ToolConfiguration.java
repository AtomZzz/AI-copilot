package com.bank.assistant.tool.config;

import com.bank.assistant.tool.points.PointBalanceQueryTool;
import com.bank.assistant.tool.runtime.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Tool Configuration - 工具配置
 * 
 * 职责：
 * 1. 在应用启动时注册所有工具到 ToolRegistry
 * 2. 使用 CommandLineRunner 确保在 Spring 容器初始化完成后执行
 * 
 * 为什么这样设计？
 * - 集中管理工具注册逻辑
 * - 便于后续动态扩展（从数据库加载工具配置等）
 * - 启动时一次性注册，避免重复注册
 * 
 * @author Bank AI Assistant Team
 * @since 2026-06-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolConfiguration implements CommandLineRunner {
    
    private final ToolRegistry toolRegistry;
    private final PointBalanceQueryTool pointBalanceQueryTool;
    
    @Override
    public void run(String... args) {
        log.info("Registering tools...");
        
        // 注册积分查询工具
        toolRegistry.register(pointBalanceQueryTool);
        
        // TODO: 后续在这里注册更多工具
        // toolRegistry.register(leaveQueryTool);
        // toolRegistry.register(workTimeQueryTool);
        // toolRegistry.register(approvalSubmitTool);
        
        log.info("Tools registered successfully. Total: {}", toolRegistry.size());
    }
}
