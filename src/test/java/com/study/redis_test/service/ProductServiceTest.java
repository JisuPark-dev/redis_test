package com.study.redis_test.service;

import com.study.redis_test.dto.ProductCreateRequest;
import com.study.redis_test.dto.ProductResponse;
import com.study.redis_test.entity.Product;
import com.study.redis_test.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        ProductCreateRequest createRequest = ProductCreateRequest.builder()
                .sku("TEST-SKU-001")
                .name("테스트 상품")
                .priceKrw(10000)
                .stockQty(100)
                .build();
        
        ProductResponse response = productService.createProduct(createRequest);
        testProduct = productRepository.findById(response.getId()).orElseThrow();
    }

    @Test
    @DisplayName("낙관적 락 충돌 시 재시도하여 모든 요청이 성공한다")
    void testOptimisticLockingWithRetry() throws InterruptedException {
        int threadCount = 10;
        int decrementAmount = -1;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    productService.updateStock(testProduct.getId(), decrementAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("실패: " + e.getMessage());
                }
            }, executor);
            futures.add(future);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        
        Product finalProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(finalProduct.getStockQty()).isEqualTo(100 - threadCount);
    }
    
    @Test
    @DisplayName("재고가 0이 될 때까지 모든 요청이 재시도를 통해 성공한다")
    void testRetryUntilStockIsZero() throws InterruptedException {
        Product smallStockProduct = productRepository.save(Product.builder()
                .sku("TEST-SKU-002")
                .name("재고 적은 상품")
                .priceKrw(5000)
                .stockQty(20)
                .build());
        
        int threadCount = 20;
        int decrementAmount = -1;
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    productService.updateStock(smallStockProduct.getId(), decrementAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            }, executor);
            futures.add(future);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        
        Product finalProduct = productRepository.findById(smallStockProduct.getId()).orElseThrow();
        
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(finalProduct.getStockQty()).isEqualTo(0);
    }
}