package com.study.redis_test.controller;

import com.study.redis_test.dto.ConcurrentTestRequest;
import com.study.redis_test.dto.ConcurrentTestResult;
import com.study.redis_test.dto.ProductPlainResponse;
import com.study.redis_test.service.NoLockProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/no-lock/products")
@RequiredArgsConstructor
public class NoLockProductController {
    
    private final NoLockProductService productService;
    
    @PatchMapping("/{id}/stock")
    public ResponseEntity<ProductPlainResponse> updateStock(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> request) {
        Integer quantity = request.get("quantity");
        if (quantity == null) {
            return ResponseEntity.badRequest().build();
        }
        
        ProductPlainResponse response = productService.updateStockWithoutLock(id, quantity);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{id}/concurrent-test")
    public ResponseEntity<ConcurrentTestResult> testConcurrentStockUpdate(
            @PathVariable Long id,
            @Valid @RequestBody ConcurrentTestRequest request) {
        ConcurrentTestResult result = productService.testConcurrentStockUpdate(id, request);
        return ResponseEntity.ok(result);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }
}