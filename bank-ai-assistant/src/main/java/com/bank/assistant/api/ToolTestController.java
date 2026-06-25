package com.bank.assistant.api;

import com.bank.assistant.tool.runtime.Tool;
import com.bank.assistant.tool.runtime.ToolExecutionResult;
import com.bank.assistant.tool.runtime.ToolExecutor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool Test Controller - 工具测试接口
 * 
 * 用于手动测试 Tool Runtime 功能
 * 
 * @author Bank AI Assistant Team
 * @since 2026-06-25
 */
@Slf4j
@RestController
@RequestMapping("/api/test/tool")
@RequiredArgsConstructor
public class ToolTestController {
    
    private final ToolExecutor toolExecutor;
    
    /**
     * 测试积分查询工具
     * 
     * 请求示例：
     * POST /api/test/tool/point-balance
     * {
     *   "userId": "user123"
     * }
     */
    @PostMapping("/point-balance")
    public ToolExecutionResult testPointBalance(@RequestBody PointBalanceRequest request) {
        
        log.info("Testing point balance query for user: {}", request.getUserId());
        
        // 构建参数
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", request.getUserId());
        
        // 构建执行上下文（模拟）
        Tool.ExecutionContext context = createMockContext(request.getUserId());
        
        // 执行工具
        return toolExecutor.execute("point.balance.query", parameters, context);
    }
    
    /**
     * 创建 Mock 执行上下文
     */
    private Tool.ExecutionContext createMockContext(String userId) {
        return new Tool.ExecutionContext() {
            private final Map<String, Object> attributes = new HashMap<>();
            
            @Override
            public String getUserId() {
                return userId;
            }
            
            @Override
            public String getUserRole() {
                return "EMPLOYEE";
            }
            
            @Override
            public String getSessionId() {
                return "test-session-" + System.currentTimeMillis();
            }
            
            @Override
            public String getRequestId() {
                return "test-request-" + System.currentTimeMillis();
            }
            
            @Override
            public Object getAttribute(String key) {
                return attributes.get(key);
            }
            
            @Override
            public void setAttribute(String key, Object value) {
                attributes.put(key, value);
            }
        };
    }
    
    /**
     * 请求 DTO
     */
    @Data
    public static class PointBalanceRequest {
        private String userId;
    }
}
