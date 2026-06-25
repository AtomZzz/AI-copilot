package com.bank.assistant.tool.points;

import com.bank.assistant.tool.runtime.Tool;
import com.bank.assistant.tool.runtime.ToolDefinition;
import com.bank.assistant.tool.runtime.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Point Balance Query Tool - 积分余额查询工具
 * 
 * 功能：查询用户的积分余额
 * 
 * Demo 级实现：返回 Mock 数据
 * TODO: 后续演进为基础级（调用真实 API）和企业级（完整缓存、熔断策略）
 * 
 * @author Bank AI Assistant Team
 * @since 2026-06-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointBalanceQueryTool implements Tool {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 工具定义
     */
    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("point.balance.query")
            .description("查询用户的积分余额。返回当前可用积分总数、最近变动记录等信息。适用于用户询问'我有多少积分'、'查看我的积分'等场景。")
            .parameters("{\"type\":\"object\",\"properties\":{\"userId\":{\"type\":\"string\",\"description\":\"用户ID\"}},\"required\":[\"userId\"]}")
            .requiresAuth(true)  // 需要权限检查（只能查自己的积分）
            .isSafe(true)        // 只读操作，安全
            .timeoutMs(3000)
            .retryCount(2)
            .build();
    
    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }
    
    @Override
    public ToolExecutionResult execute(Map<String, Object> parameters, ExecutionContext context) {
        
        try {
            // Step 1: 提取参数
            String userId = (String) parameters.get("userId");
            
            if (userId == null || userId.isEmpty()) {
                return ToolExecutionResult.failure(
                    "Missing required parameter: userId", 
                    "MISSING_PARAMETER"
                );
            }
            
            log.debug("Querying point balance for user: {}", userId);
            
            // Step 2: 权限验证（Demo 级：简单验证用户只能查自己的积分）
            if (!userId.equals(context.getUserId())) {
                log.warn("Permission denied: user {} tried to query points for user {}", 
                        context.getUserId(), userId);
                return ToolExecutionResult.failure(
                    "You can only query your own points", 
                    "PERMISSION_DENIED"
                );
            }
            
            // Step 3: 查询积分（Demo 级：返回 Mock 数据）
            // TODO: 后续替换为真实的积分服务 API 调用
            Map<String, Object> mockData = queryMockPointBalance(userId);
            
            // Step 4: 转换为 JSON 字符串
            String resultJson = objectMapper.writeValueAsString(mockData);
            
            log.info("Point balance queried successfully for user {}: {}", userId, resultJson);
            
            return ToolExecutionResult.success(resultJson, null);
            
        } catch (Exception e) {
            log.error("Failed to query point balance for user: {}", 
                     parameters.get("userId"), e);
            
            return ToolExecutionResult.failure(
                "Internal error: " + e.getMessage(), 
                "INTERNAL_ERROR"
            );
        }
    }
    
    /**
     * Mock 数据：模拟积分查询结果
     * 
     * TODO: 后续替换为真实的数据库或 API 调用
     */
    private Map<String, Object> queryMockPointBalance(String userId) {
        
        Map<String, Object> result = new HashMap<>();
        
        // 根据用户 ID 生成不同的 Mock 数据（便于测试）
        int basePoints = userId.hashCode() % 10000 + 1000; // 1000-11000 之间的随机数
        
        result.put("userId", userId);
        result.put("totalPoints", basePoints);
        result.put("availablePoints", basePoints - 200); // 假设 200 积分已冻结
        result.put("frozenPoints", 200);
        result.put("currency", "POINTS");
        result.put("lastUpdateTime", System.currentTimeMillis());
        
        // 最近变动记录
        result.put("recentTransactions", java.util.Arrays.asList(
            createTransaction("EARN", 500, "签到奖励", System.currentTimeMillis() - 86400000),
            createTransaction("SPEND", -100, "兑换礼品", System.currentTimeMillis() - 172800000),
            createTransaction("EARN", 300, "消费返积分", System.currentTimeMillis() - 259200000)
        ));
        
        return result;
    }
    
    /**
     * 创建交易记录
     */
    private Map<String, Object> createTransaction(String type, int amount, String description, long timestamp) {
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("type", type);
        transaction.put("amount", amount);
        transaction.put("description", description);
        transaction.put("timestamp", timestamp);
        return transaction;
    }
}
