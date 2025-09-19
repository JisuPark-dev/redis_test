package com.study.redis_test.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConcurrentTestResult {
    
    private Integer totalRequests;
    private Integer successCount;
    private Integer failCount;
    private Long totalExecutionTimeMs;
    private Integer finalStockQty;
    private List<String> errors;
    private String lockType;
}