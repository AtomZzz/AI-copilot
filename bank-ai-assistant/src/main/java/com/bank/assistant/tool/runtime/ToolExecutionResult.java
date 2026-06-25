package com.bank.assistant.tool.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tool Execution Result - 工具执行结果
 * 
 * 标准化的工具执行返回格式，包含：
 * - success: 是否成功
 * - data: 成功时的返回数据
 * - error: 失败时的错误信息
 * - metadata: 元数据（执行时间、重试次数等）
 * 
 * @author Bank AI Assistant Team
 * @since 2026-06-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionResult {
    
    /**
     * 是否执行成功
     */
    private Boolean success;
    
    /**
     * 成功时的返回数据（JSON 字符串）
     */
    private String data;
    
    /**
     * 失败时的错误信息
     */
    private String error;
    
    /**
     * 错误码
     */
    private String errorCode;
    
    /**
     * 执行耗时（毫秒）
     */
    private Long executionTimeMs;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 创建成功结果
     */
    public static ToolExecutionResult success(String data, Long executionTimeMs) {
        return ToolExecutionResult.builder()
                .success(true)
                .data(data)
                .executionTimeMs(executionTimeMs)
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static ToolExecutionResult failure(String error, String errorCode) {
        return ToolExecutionResult.builder()
                .success(false)
                .error(error)
                .errorCode(errorCode)
                .build();
    }
}
