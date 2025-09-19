package com.study.redis_test.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConcurrentTestRequest {
    
    @NotNull(message = "수량은 필수입니다")
    private Integer quantity;
    
    @NotNull(message = "동시 요청 수는 필수입니다")
    @Min(value = 1, message = "동시 요청 수는 1 이상이어야 합니다")
    private Integer concurrentCount;
}