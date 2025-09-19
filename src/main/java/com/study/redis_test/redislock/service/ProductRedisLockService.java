package com.study.redis_test.redislock.service;

import com.study.redis_test.dto.*;
import com.study.redis_test.entity.ProductPlain;
import com.study.redis_test.repository.ProductPlainRepository;
import com.study.redis_test.service.RedisLockService;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductRedisLockService {
    
    private final ProductPlainRepository productRepository;
    private final RedisLockService redisLockService;
    
    @Transactional
    public ProductPlainResponse createProduct(ProductCreateRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new IllegalArgumentException("이미 존재하는 SKU입니다: " + request.getSku());
        }
        
        ProductPlain product = ProductPlain.builder()
                .sku(request.getSku())
                .name(request.getName())
                .priceKrw(request.getPriceKrw())
                .stockQty(request.getStockQty())
                .build();
                
        ProductPlain savedProduct = productRepository.save(product);
        return ProductPlainResponse.from(savedProduct);
    }
    
    public ProductPlainResponse getProductById(Long id) {
        ProductPlain product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
        return ProductPlainResponse.from(product);
    }
    
    public ProductPlainResponse getProductBySku(String sku) {
        ProductPlain product = productRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + sku));
        return ProductPlainResponse.from(product);
    }
    
    public List<ProductPlainResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductPlainResponse::from)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public ProductPlainResponse updateProduct(Long id, ProductUpdateRequest request) {
        String lockKey = "product:lock:" + id;
        
        return redisLockService.executeWithLock(lockKey, () -> {
            ProductPlain product = productRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
            
            product.setName(request.getName());
            product.setPriceKrw(request.getPriceKrw());
            product.setStockQty(request.getStockQty());
            
            ProductPlain updatedProduct = productRepository.save(product);
            return ProductPlainResponse.from(updatedProduct);
        });
    }
    
    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다: " + id);
        }
        productRepository.deleteById(id);
    }
    
    @Transactional
    public ProductPlainResponse updateStock(Long id, Integer quantity) {
        String lockKey = "product:stock:lock:" + id;
        
        return redisLockService.executeWithLock(lockKey, () -> {
            ProductPlain product = productRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
            
            int newStock = product.getStockQty() + quantity;
            if (newStock < 0) {
                throw new IllegalArgumentException("재고가 부족합니다. 현재 재고: " + product.getStockQty());
            }
            
            product.setStockQty(newStock);
            ProductPlain updatedProduct = productRepository.save(product);
            return ProductPlainResponse.from(updatedProduct);
        });
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
                    updateStock(id, request.getQuantity());
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
                .lockType("REDIS_DISTRIBUTED_LOCK")
                .build();
    }
}