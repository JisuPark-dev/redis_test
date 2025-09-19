package com.study.redis_test.service;

import com.study.redis_test.dto.ConcurrentTestRequest;
import com.study.redis_test.dto.ConcurrentTestResult;
import com.study.redis_test.dto.ProductPlainResponse;
import com.study.redis_test.entity.ProductPlain;
import com.study.redis_test.repository.ProductPlainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoLockProductService {
    
    private final ProductPlainRepository productRepository;
    
    @Transactional
    public ProductPlainResponse updateStockWithoutLock(Long id, Integer quantity) {
        ProductPlain product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
        
        // 의도적으로 딜레이 추가 (race condition 재현을 위해)
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        int newStock = product.getStockQty() + quantity;
        if (newStock < 0) {
            throw new IllegalArgumentException("재고가 부족합니다. 현재 재고: " + product.getStockQty());
        }
        
        product.setStockQty(newStock);
        ProductPlain updatedProduct = productRepository.save(product);
        
        log.debug("락 없이 재고 업데이트: 상품ID={}, 변경량={}, 최종재고={}", 
                id, quantity, updatedProduct.getStockQty());
        
        return ProductPlainResponse.from(updatedProduct);
    }
    
    public ConcurrentTestResult testConcurrentStockUpdate(Long id, ConcurrentTestRequest request) {
        long startTime = System.currentTimeMillis();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> errors = new ArrayList<>();
        
        ExecutorService executor = Executors.newFixedThreadPool(request.getConcurrentCount());
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < request.getConcurrentCount(); i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    updateStockWithoutLock(id, request.getQuantity());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    synchronized (errors) {
                        errors.add(e.getMessage());
                    }
                }
            }, executor);
            futures.add(future);
        }
        
        // 모든 스레드 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        
        // 최종 재고 확인
        ProductPlain finalProduct = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
        
        return ConcurrentTestResult.builder()
                .totalRequests(request.getConcurrentCount())
                .successCount(successCount.get())
                .failCount(failCount.get())
                .totalExecutionTimeMs(endTime - startTime)
                .finalStockQty(finalProduct.getStockQty())
                .errors(errors)
                .lockType("NO_LOCK")
                .build();
    }
}