package com.study.redis_test.redislock.controller;

import com.study.redis_test.dto.*;
import com.study.redis_test.redislock.service.ProductRedisLockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/redis-lock/products")
@RequiredArgsConstructor
public class ProductRedisLockController {
    
    private final ProductRedisLockService productService;
    
    @PostMapping
    public ResponseEntity<ProductPlainResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        ProductPlainResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ProductPlainResponse> getProduct(@PathVariable Long id) {
        ProductPlainResponse response = productService.getProductById(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/sku/{sku}")
    public ResponseEntity<ProductPlainResponse> getProductBySku(@PathVariable String sku) {
        ProductPlainResponse response = productService.getProductBySku(sku);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    public ResponseEntity<List<ProductPlainResponse>> getAllProducts() {
        List<ProductPlainResponse> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ProductPlainResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        ProductPlainResponse response = productService.updateProduct(id, request);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
    
    @PatchMapping("/{id}/stock")
    public ResponseEntity<ProductPlainResponse> updateStock(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> request) {
        Integer quantity = request.get("quantity");
        if (quantity == null) {
            return ResponseEntity.badRequest().build();
        }
        
        ProductPlainResponse response = productService.updateStock(id, quantity);
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
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
        if (e.getMessage() != null && e.getMessage().contains("Could not acquire lock")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "다른 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
    }
}